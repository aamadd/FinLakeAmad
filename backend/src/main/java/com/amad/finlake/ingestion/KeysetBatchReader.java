package com.amad.finlake.ingestion;

import com.amad.finlake.common.SqlIdentifierValidator;
import com.amad.finlake.sources.AmadSourceConnector;
import com.amad.finlake.sources.SourceConfig;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Bounded-memory O(1) keyset reader using `ingest_row_id > ? ORDER BY ingest_row_id ASC`.
 * Never uses OFFSET pagination.
 *
 * <p>Incremental watermark support (`ingest_row_id` or `step`):
 * PaySim is a static synthetic dataset without `updated_at` or CDC tombstones.
 * `ingest_row_id` is strictly monotonic (1..6,362,620), whereas `step` is a coarse-grained
 * hourly bucket (1..743) shared by many transactions. When `watermarkColumn = "step"`,
 * we still paginate via `ingest_row_id > ?` combined with `step > ?` to preserve O(1) memory
 * and avoid skipping or duplicating rows within the same `step`.</p>
 */
@Component
public class KeysetBatchReader {

    private static final Set<String> ALLOWED_WATERMARK_COLUMNS = Set.of("ingest_row_id", "step");

    private final SimulatedTimeMapper timeMapper;

    public KeysetBatchReader(SimulatedTimeMapper timeMapper) {
        this.timeMapper = timeMapper;
    }

    public long streamBatches(
            SourceConfig config,
            AmadSourceConnector connector,
            String schema,
            String table,
            int batchSize,
            long maxRows,
            BooleanSupplier cancelled,
            Consumer<List<TransactionRow>> batchConsumer
    ) throws SQLException {
        return streamBatches(config, connector, schema, table, batchSize, maxRows,
                "ingest_row_id", null, cancelled, batchConsumer);
    }

    public long streamBatches(
            SourceConfig config,
            AmadSourceConnector connector,
            String schema,
            String table,
            int batchSize,
            long maxRows,
            String watermarkColumn,
            Long watermarkValue,
            BooleanSupplier cancelled,
            Consumer<List<TransactionRow>> batchConsumer
    ) throws SQLException {
        String validWatermark = validateWatermarkColumn(watermarkColumn);
        String qs = connector.quoteIdentifier(SqlIdentifierValidator.requireValid(schema, "schema"));
        String qt = connector.quoteIdentifier(SqlIdentifierValidator.requireValid(table, "table"));
        boolean filterByStep = "step".equals(validWatermark) && watermarkValue != null;
        String sql = buildKeysetQuery(qs, qt, validWatermark, watermarkValue);

        long totalRead = 0;
        long lastId = (!filterByStep && watermarkValue != null && watermarkValue > 0) ? watermarkValue : 0L;
        try (Connection conn = connector.openJdbcConnection(config);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            while (!cancelled.getAsBoolean()) {
                int fetchLimit = maxRows > 0 ? (int) Math.min(batchSize, maxRows - totalRead) : batchSize;
                if (fetchLimit <= 0) {
                    break;
                }
                int paramIdx = 1;
                if (filterByStep) {
                    ps.setLong(paramIdx++, watermarkValue);
                }
                ps.setLong(paramIdx++, lastId);
                ps.setInt(paramIdx, fetchLimit);

                List<TransactionRow> batch = new ArrayList<>(Math.min(fetchLimit, 8192));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        long rowId = rs.getLong(1);
                        int step = rs.getInt(2);
                        batch.add(new TransactionRow(
                                rowId,
                                step,
                                rs.getString(3),
                                rs.getBigDecimal(4),
                                rs.getString(5),
                                rs.getBigDecimal(6),
                                rs.getBigDecimal(7),
                                rs.getString(8),
                                rs.getBigDecimal(9),
                                rs.getBigDecimal(10),
                                rs.getBoolean(11),
                                rs.getBoolean(12),
                                timeMapper.toSimulatedInstant(step),
                                timeMapper.toSimulatedDate(step)
                        ));
                        lastId = rowId;
                    }
                }
                if (batch.isEmpty()) {
                    break;
                }
                totalRead += batch.size();
                batchConsumer.accept(batch);
                if (batch.size() < fetchLimit) {
                    break;
                }
            }
        }
        return totalRead;
    }

    public static String validateWatermarkColumn(String watermarkColumn) {
        String col = (watermarkColumn == null || watermarkColumn.isBlank()) ? "ingest_row_id" : watermarkColumn.trim();
        if (!ALLOWED_WATERMARK_COLUMNS.contains(col)) {
            throw new IllegalArgumentException("Unsupported watermarkColumn: " + col + " (allowed: " + ALLOWED_WATERMARK_COLUMNS + ")");
        }
        return col;
    }

    public static String buildKeysetQuery(String quotedSchema, String quotedTable, String watermarkColumn, Long watermarkValue) {
        String validCol = validateWatermarkColumn(watermarkColumn);
        boolean filterByStep = "step".equals(validCol) && watermarkValue != null;
        String whereClause = filterByStep
                ? "WHERE step > ? AND ingest_row_id > ?"
                : "WHERE ingest_row_id > ?";
        return "SELECT ingest_row_id, step, type, amount, nameOrig, oldbalanceOrg, newbalanceOrig, " +
               "nameDest, oldbalanceDest, newbalanceDest, isFraud, isFlaggedFraud " +
               "FROM " + quotedSchema + "." + quotedTable + " " + whereClause + " ORDER BY ingest_row_id ASC " +
               "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";
    }
}
