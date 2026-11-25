package com.amad.finlake.catalog;

import com.amad.finlake.catalog.CatalogModels.QueryResponse;
import com.amad.finlake.catalog.CatalogModels.RegisterTableResponse;
import com.amad.finlake.common.AmadProperties;
import com.amad.finlake.common.SqlIdentifierValidator;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

@Service
public class TrinoHiveCatalogRegistrar implements AmadCatalogRegistrar {

    private static final String CATALOG = "amad_lake";
    private static final Pattern READ_ONLY_PREFIX = Pattern.compile("^(SELECT|WITH|SHOW|DESCRIBE|EXPLAIN)\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MUTATION_KEYWORD = Pattern.compile("\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|CALL|GRANT|REVOKE)\\b", Pattern.CASE_INSENSITIVE);

    private final AmadProperties props;

    public TrinoHiveCatalogRegistrar(AmadProperties props) {
        this.props = props;
    }

    @Override
    public RegisterTableResponse registerCuratedTransactions(
            String schema,
            String table,
            String location,
            boolean partitionedByEventDate
    ) {
        String validSchema = SqlIdentifierValidator.requireValid(
                (schema == null || schema.isBlank()) ? "finlake_curated" : schema, "schema");
        String validTable = SqlIdentifierValidator.requireValid(table, "table");
        String bucket = props.minio().bucket();
        String effectiveLocation = (location == null || location.isBlank())
                ? String.format("s3a://%s/curated/paysim/%s/", bucket, validTable)
                : sanitizeS3aLocation(location);

        String fullTable = CATALOG + "." + validSchema + "." + validTable;
        try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA IF NOT EXISTS " + CATALOG + "." + validSchema);
            st.execute(buildCreateTableSql(fullTable, effectiveLocation, partitionedByEventDate));
            if (partitionedByEventDate) {
                st.execute("CALL " + CATALOG + ".system.sync_partition_metadata('" + validSchema + "', '" + validTable + "', 'ADD')");
            }
            long count = 0;
            try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + fullTable)) {
                if (rs.next()) {
                    count = rs.getLong(1);
                }
            }
            return new RegisterTableResponse(CATALOG, validSchema, validTable, effectiveLocation, partitionedByEventDate, count);
        } catch (SQLException ex) {
            throw new IllegalStateException("Trino catalog registration failed: " + ex.getMessage(), ex);
        }
    }

    @Override
    public List<String> listTables(String schema) {
        String validSchema = SqlIdentifierValidator.requireValid(
                (schema == null || schema.isBlank()) ? "finlake_curated" : schema, "schema");
        List<String> tables = new ArrayList<>();
        try (Connection conn = openConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW TABLES FROM " + CATALOG + "." + validSchema)) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to list Trino tables: " + ex.getMessage(), ex);
        }
        return tables;
    }

    @Override
    public QueryResponse executeReadOnlyQuery(String sql, int limit) {
        String trimmed = validateReadOnlySql(sql);
        long start = System.currentTimeMillis();
        List<String> columns = new ArrayList<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection conn = openConnection();
             Statement st = conn.createStatement()) {
            st.setMaxRows(limit);
            try (ResultSet rs = st.executeQuery(trimmed)) {
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(meta.getColumnLabel(i));
                }
                while (rs.next() && rows.size() < limit) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(columns.get(i - 1), rs.getObject(i));
                    }
                    rows.add(row);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalArgumentException("Trino query failed: " + ex.getMessage(), ex);
        }
        return new QueryResponse(columns, rows, rows.size(), Math.max(1L, System.currentTimeMillis() - start));
    }

    public static String buildCreateTableSql(String fullTable, String location, boolean partitionedByEventDate) {
        String partitionClause = partitionedByEventDate ? ",\n    partitioned_by = ARRAY['event_date']" : "";
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
                    event_time_simulated TIMESTAMP,
                    event_date VARCHAR
                )
                WITH (
                    format = 'PARQUET',
                    external_location = '%s'%s
                )
                """.formatted(fullTable, location, partitionClause);
    }

    public static String validateReadOnlySql(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL query cannot be empty");
        }
        String trimmed = sql.trim().replaceAll(";+$", "");
        if (!READ_ONLY_PREFIX.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("Only read-only SELECT/WITH/SHOW/DESCRIBE/EXPLAIN queries are allowed");
        }
        String upper = trimmed.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("EXPLAIN") && MUTATION_KEYWORD.matcher(trimmed).find()) {
            throw new IllegalArgumentException("Mutating SQL keywords are not permitted on /catalog/query");
        }
        return trimmed;
    }

    private static String sanitizeS3aLocation(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("s3a://") || trimmed.contains("'")) {
            throw new IllegalArgumentException("Location must be a valid s3a:// URI");
        }
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }

    private Connection openConnection() throws SQLException {
        URI uri = URI.create(props.trino().endpoint());
        String jdbcUrl = String.format("jdbc:trino://%s:%d/%s", uri.getHost(), uri.getPort(), CATALOG);
        Properties p = new Properties();
        p.setProperty("user", "amad_analyst");
        return DriverManager.getConnection(jdbcUrl, p);
    }
}
