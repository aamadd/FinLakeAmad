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
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Bounded-memory O(1) keyset reader using `ingest_row_id > ? ORDER BY ingest_row_id ASC`.
 * Never uses OFFSET pagination.
 */
@Component
public class KeysetBatchReader {

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
        String qs = connector.quoteIdentifier(SqlIdentifierValidator.requireValid(schema, "schema"));
        String qt = connector.quoteIdentifier(SqlIdentifierValidator.requireValid(table, "table"));
        String sql = "SELECT ingest_row_id, step, type, amount, nameOrig, oldbalanceOrg, newbalanceOrig, " +
                     "nameDest, oldbalanceDest, newbalanceDest, isFraud, isFlaggedFraud " +
                     "FROM " + qs + "." + qt + " WHERE ingest_row_id > ? ORDER BY ingest_row_id ASC " +
                     "OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY";

        long totalRead = 0;
        long lastId = 0;
        try (Connection conn = connector.openJdbcConnection(config);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            while (!cancelled.getAsBoolean()) {
                int fetchLimit = maxRows > 0 ? (int) Math.min(batchSize, maxRows - totalRead) : batchSize;
                if (fetchLimit <= 0) {
                    break;
                }
                ps.setLong(1, lastId);
                ps.setInt(2, fetchLimit);
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
}
