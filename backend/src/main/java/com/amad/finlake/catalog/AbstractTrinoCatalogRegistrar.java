package com.amad.finlake.catalog;

import com.amad.finlake.catalog.CatalogModels.QueryResponse;
import com.amad.finlake.common.AmadProperties;
import com.amad.finlake.common.SqlIdentifierValidator;

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

/** Shared DRY base for Trino-backed catalog registrars (Hive and Iceberg). */
public abstract class AbstractTrinoCatalogRegistrar implements AmadCatalogRegistrar {

    private static final Pattern READ_ONLY_PREFIX = Pattern.compile("^(SELECT|WITH|SHOW|DESCRIBE|EXPLAIN)\\b.*", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern MUTATION_KEYWORD = Pattern.compile("\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|CALL|GRANT|REVOKE)\\b", Pattern.CASE_INSENSITIVE);

    protected final AmadProperties props;
    private final String catalog;
    private final String defaultSchema;

    protected AbstractTrinoCatalogRegistrar(AmadProperties props, String catalog, String defaultSchema) {
        this.props = props;
        this.catalog = catalog;
        this.defaultSchema = defaultSchema;
    }

    protected String catalogName() {
        return catalog;
    }

    protected String validSchemaOrDefault(String schema) {
        return SqlIdentifierValidator.requireValid(
                (schema == null || schema.isBlank()) ? defaultSchema : schema, "schema");
    }

    @Override
    public List<String> listTables(String schema) {
        String validSchema = validSchemaOrDefault(schema);
        List<String> tables = new ArrayList<>();
        try (Connection conn = openConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SHOW TABLES FROM " + catalog + "." + validSchema)) {
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to list Trino tables in " + catalog + ": " + ex.getMessage(), ex);
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

    protected long querySingleLong(Statement st, String sql) throws SQLException {
        try (ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    protected Connection openConnection() throws SQLException {
        URI uri = URI.create(props.trino().endpoint());
        String jdbcUrl = String.format("jdbc:trino://%s:%d/%s", uri.getHost(), uri.getPort(), catalog);
        Properties p = new Properties();
        p.setProperty("user", "amad_analyst");
        return DriverManager.getConnection(jdbcUrl, p);
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

    protected static String sanitizeS3aLocation(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("s3a://") || trimmed.contains("'")) {
            throw new IllegalArgumentException("Location must be a valid s3a:// URI");
        }
        return trimmed.endsWith("/") ? trimmed : trimmed + "/";
    }
}
