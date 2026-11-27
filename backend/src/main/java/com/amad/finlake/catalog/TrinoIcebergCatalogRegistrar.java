package com.amad.finlake.catalog;

import com.amad.finlake.catalog.CatalogModels.IcebergIngestResponse;
import com.amad.finlake.catalog.CatalogModels.IcebergSchemaEvolutionResponse;
import com.amad.finlake.catalog.CatalogModels.IcebergSnapshot;
import com.amad.finlake.catalog.CatalogModels.QueryResponse;
import com.amad.finlake.catalog.CatalogModels.RegisterTableResponse;
import com.amad.finlake.common.AmadProperties;
import com.amad.finlake.common.SqlIdentifierValidator;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** SOLID Iceberg catalog registrar backed by Trino's `amad_iceberg` connector. */
@Service
public class TrinoIcebergCatalogRegistrar extends AbstractTrinoCatalogRegistrar {

    public static final String CATALOG = "amad_iceberg";
    private static final String DEFAULT_SCHEMA = "finlake_iceberg";
    private static final Set<String> ALLOWED_EVOLUTION_TYPES = Set.of(
            "VARCHAR", "BIGINT", "INTEGER", "DOUBLE", "BOOLEAN", "DECIMAL(19,4)", "TIMESTAMP(6)"
    );

    public TrinoIcebergCatalogRegistrar(AmadProperties props) {
        super(props, CATALOG, DEFAULT_SCHEMA);
    }

    @Override
    public RegisterTableResponse registerCuratedTransactions(
            String schema,
            String table,
            String location,
            boolean partitionedByEventDate
    ) {
        String validSchema = validSchemaOrDefault(schema);
        String validTable = SqlIdentifierValidator.requireValid(table, "table");
        String bucket = props.minio().bucket();
        String schemaLocation = String.format("s3a://%s/iceberg/", bucket);
        String effectiveLocation = (location == null || location.isBlank())
                ? String.format("s3a://%s/iceberg/%s/", bucket, validTable)
                : sanitizeS3aLocation(location);

        String fullTable = CATALOG + "." + validSchema + "." + validTable;
        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + CATALOG + "." + validSchema +
                    " WITH (location = '" + schemaLocation + "')");
            st.execute(buildCreateTableSql(fullTable, effectiveLocation, partitionedByEventDate));
            long count = querySingleLong(st, "SELECT COUNT(*) FROM " + fullTable);
            return new RegisterTableResponse(CATALOG, validSchema, validTable, effectiveLocation, partitionedByEventDate, count);
        } catch (SQLException ex) {
            throw new IllegalStateException("Trino Iceberg registration failed: " + ex.getMessage(), ex);
        }
    }

    public IcebergIngestResponse ingestFromHive(
            String sourceSchema,
            String sourceTable,
            String targetSchema,
            String targetTable,
            Long minIngestRowId,
            Long maxIngestRowId
    ) {
        String srcSchema = SqlIdentifierValidator.requireValid(
                (sourceSchema == null || sourceSchema.isBlank()) ? "finlake_curated" : sourceSchema, "sourceSchema");
        String srcTable = SqlIdentifierValidator.requireValid(
                (sourceTable == null || sourceTable.isBlank()) ? "amad_transactions" : sourceTable, "sourceTable");
        String dstSchema = validSchemaOrDefault(targetSchema);
        String dstTable = SqlIdentifierValidator.requireValid(
                (targetTable == null || targetTable.isBlank()) ? "amad_transactions" : targetTable, "targetTable");

        String fullSource = "amad_lake." + srcSchema + "." + srcTable;
        String fullTarget = CATALOG + "." + dstSchema + "." + dstTable;

        StringBuilder where = new StringBuilder();
        if (minIngestRowId != null || maxIngestRowId != null) {
            where.append(" WHERE 1=1");
            if (minIngestRowId != null) {
                where.append(" AND ingest_row_id > ").append(minIngestRowId);
            }
            if (maxIngestRowId != null) {
                where.append(" AND ingest_row_id <= ").append(maxIngestRowId);
            }
        }

        String insertSql = """
                INSERT INTO %s (
                    ingest_row_id, step, type, amount, nameOrig, oldbalanceOrg, newbalanceOrig,
                    nameDest, oldbalanceDest, newbalanceDest, isFraud, isFlaggedFraud,
                    event_time_simulated, event_date
                )
                SELECT
                    ingest_row_id, step, type, amount, nameOrig, oldbalanceOrg, newbalanceOrig,
                    nameDest, oldbalanceDest, newbalanceDest, isFraud, isFlaggedFraud,
                    CAST(event_time_simulated AS TIMESTAMP(6)), event_date
                FROM %s%s
                """.formatted(fullTarget, fullSource, where);

        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
            long beforeCount = querySingleLong(st, "SELECT COUNT(*) FROM " + fullTarget);
            st.executeUpdate(insertSql);
            long afterCount = querySingleLong(st, "SELECT COUNT(*) FROM " + fullTarget);
            List<IcebergSnapshot> snapshots = fetchSnapshots(st, dstSchema, dstTable);
            String latestSnapshotId = snapshots.isEmpty() ? null : snapshots.get(0).snapshotId();
            return new IcebergIngestResponse(CATALOG, dstSchema, dstTable, afterCount - beforeCount, afterCount, latestSnapshotId);
        } catch (SQLException ex) {
            throw new IllegalStateException("Iceberg batch ingest failed: " + ex.getMessage(), ex);
        }
    }

    public List<IcebergSnapshot> listSnapshots(String schema, String table) {
        String validSchema = validSchemaOrDefault(schema);
        String validTable = SqlIdentifierValidator.requireValid(
                (table == null || table.isBlank()) ? "amad_transactions" : table, "table");
        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
            return fetchSnapshots(st, validSchema, validTable);
        } catch (SQLException ex) {
            String msg = ex.getMessage() == null ? "" : ex.getMessage();
            if (msg.contains("does not exist") || msg.contains("not found")) {
                return List.of();
            }
            throw new IllegalStateException("Unable to list Iceberg snapshots: " + msg, ex);
        }
    }

    public IcebergSchemaEvolutionResponse evolveSchemaAddColumn(
            String schema,
            String table,
            String columnName,
            String columnType
    ) {
        String validSchema = validSchemaOrDefault(schema);
        String validTable = SqlIdentifierValidator.requireValid(
                (table == null || table.isBlank()) ? "amad_transactions" : table, "table");
        String validCol = SqlIdentifierValidator.requireValid(columnName, "columnName");
        String normalizedType = validateColumnType(columnType);

        String fullTable = CATALOG + "." + validSchema + "." + validTable;
        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
            st.execute("ALTER TABLE " + fullTable + " ADD COLUMN IF NOT EXISTS " + validCol + " " + normalizedType);
            List<String> columns = new ArrayList<>();
            try (ResultSet rs = st.executeQuery("DESCRIBE " + fullTable)) {
                while (rs.next()) {
                    columns.add(rs.getString(1) + ":" + rs.getString(2));
                }
            }
            return new IcebergSchemaEvolutionResponse(CATALOG, validSchema, validTable, validCol, normalizedType, columns);
        } catch (SQLException ex) {
            throw new IllegalStateException("Iceberg schema evolution failed: " + ex.getMessage(), ex);
        }
    }

    public QueryResponse queryTimeTravel(
            String schema,
            String table,
            String snapshotId,
            String timestampIso,
            int limit
    ) {
        String sql = buildTimeTravelSql(schema, table, snapshotId, timestampIso);
        return executeReadOnlyQuery(sql, limit);
    }

    public static String buildTimeTravelSql(String schema, String table, String snapshotId, String timestampIso) {
        String validSchema = SqlIdentifierValidator.requireValid(
                (schema == null || schema.isBlank()) ? DEFAULT_SCHEMA : schema, "schema");
        String validTable = SqlIdentifierValidator.requireValid(
                (table == null || table.isBlank()) ? "amad_transactions" : table, "table");
        boolean hasVersion = snapshotId != null && !snapshotId.isBlank();
        boolean hasTimestamp = timestampIso != null && !timestampIso.isBlank();
        if (hasVersion == hasTimestamp) {
            throw new IllegalArgumentException("Provide exactly one of snapshotId or timestampIso");
        }
        String asOfClause;
        if (hasVersion) {
            long parsedId = Long.parseLong(snapshotId.trim());
            if (parsedId <= 0) {
                throw new IllegalArgumentException("snapshotId must be positive");
            }
            asOfClause = "FOR VERSION AS OF " + parsedId;
        } else {
            Instant parsed = Instant.parse(timestampIso.trim());
            String formatted = parsed.toString().replace('T', ' ').replace("Z", "") + " UTC";
            asOfClause = "FOR TIMESTAMP AS OF TIMESTAMP '" + formatted + "'";
        }
        String fullTable = CATALOG + "." + validSchema + "." + validTable;
        return "SELECT COUNT(*) AS row_count, MIN(ingest_row_id) AS min_row_id, MAX(ingest_row_id) AS max_row_id, " +
               "ROUND(SUM(amount), 2) AS total_amount FROM " + fullTable + " " + asOfClause;
    }

    public static String validateColumnType(String columnType) {
        if (columnType == null || columnType.isBlank()) {
            throw new IllegalArgumentException("columnType cannot be empty");
        }
        String normalized = columnType.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_EVOLUTION_TYPES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported Iceberg column type: " + columnType +
                    " (allowed: " + ALLOWED_EVOLUTION_TYPES + ")");
        }
        return normalized;
    }

    public static String buildCreateTableSql(String fullTable, String location, boolean partitionedByEventDate) {
        String partitionClause = partitionedByEventDate ? ",\n    partitioning = ARRAY['event_date']" : "";
        return """
                CREATE TABLE IF NOT EXISTS %s (
                    ingest_row_id BIGINT,
                    step INTEGER,
                    type VARCHAR,
                    amount DECIMAL(19,4),
                    nameOrig VARCHAR,
                    oldbalanceOrg DECIMAL(19,4),
                    newbalanceOrig DECIMAL(19,4),
                    nameDest VARCHAR,
                    oldbalanceDest DECIMAL(19,4),
                    newbalanceDest DECIMAL(19,4),
                    isFraud BOOLEAN,
                    isFlaggedFraud BOOLEAN,
                    event_time_simulated TIMESTAMP(6),
                    event_date VARCHAR
                )
                WITH (
                    format = 'PARQUET',
                    location = '%s'%s
                )
                """.formatted(fullTable, location, partitionClause);
    }

    private static List<IcebergSnapshot> fetchSnapshots(Statement st, String schema, String table) throws SQLException {
        List<IcebergSnapshot> snapshots = new ArrayList<>();
        String sql = "SELECT CAST(snapshot_id AS VARCHAR), CAST(committed_at AS VARCHAR), operation, json_format(CAST(summary AS JSON)) " +
                     "FROM " + CATALOG + "." + schema + ".\"" + table + "$snapshots\" ORDER BY committed_at DESC";
        try (ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                snapshots.add(new IcebergSnapshot(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getString(4)
                ));
            }
        }
        return snapshots;
    }
}
