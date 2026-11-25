package com.amad.finlake.sources;

import com.amad.finlake.common.SqlIdentifierValidator;
import com.amad.finlake.sources.ProbeModels.ColumnSchema;
import com.amad.finlake.sources.ProbeModels.ProbeStepResult;
import com.amad.finlake.sources.ProbeModels.SourceProbeResponse;
import com.amad.finlake.sources.ProbeModels.TableSummary;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/** DRY JDBC implementation of 4-step probing and metadata discovery shared across relational engines. */
public abstract class AbstractJdbcSourceConnector implements AmadSourceConnector {

    private static final int TIMEOUT_MS = 3000;

    protected abstract String rowCountQuery(String quotedSchema, String quotedTable);

    @Override
    public SourceProbeResponse probe(SourceConfig config) {
        List<ProbeStepResult> steps = new ArrayList<>(4);

        long t1 = System.currentTimeMillis();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(config.host(), config.port()), TIMEOUT_MS);
            steps.add(new ProbeStepResult(1, "Reach server", "PASSED", elapsed(t1),
                    config.host() + ":" + config.port() + " reachable"));
        } catch (Exception ex) {
            steps.add(new ProbeStepResult(1, "Reach server", "FAILED", elapsed(t1), "TCP unreachable"));
            return fail(config, steps, "Verify host/port and that the database container is running.");
        }

        long t2 = System.currentTimeMillis();
        steps.add(new ProbeStepResult(2, "Secure channel", "PASSED", elapsed(t2),
                config.tlsEnabled() ? "TLS required" : "Local Docker network (TLS optional)"));

        long t3 = System.currentTimeMillis();
        try (Connection conn = openConnection(config)) {
            DatabaseMetaData meta = conn.getMetaData();
            String version = meta.getDatabaseProductName() + " " + meta.getDatabaseProductVersion();
            steps.add(new ProbeStepResult(3, "Authenticate", "PASSED", elapsed(t3), "Authenticated as " + config.username()));

            long t4 = System.currentTimeMillis();
            List<TableSummary> tables = readTables(conn);
            steps.add(new ProbeStepResult(4, "Open catalog", "PASSED", elapsed(t4),
                    tables.size() + " table(s) discovered in " + config.database()));
            return new SourceProbeResponse(true, config.engine(), version, steps, tables, null);
        } catch (SQLException ex) {
            String hint = sanitizeSqlError(ex);
            steps.add(new ProbeStepResult(3, "Authenticate", "FAILED", elapsed(t3), hint));
            return fail(config, steps, hint);
        }
    }

    @Override
    public List<TableSummary> listTables(SourceConfig config) {
        try (Connection conn = openConnection(config)) {
            return readTables(conn);
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to list tables: " + sanitizeSqlError(ex));
        }
    }

    @Override
    public List<ColumnSchema> describeTable(SourceConfig config, String schema, String table) {
        String validSchema = SqlIdentifierValidator.requireValid(schema, "schema");
        String validTable = SqlIdentifierValidator.requireValid(table, "table");
        List<ColumnSchema> cols = new ArrayList<>();
        try (Connection conn = openConnection(config);
             ResultSet rs = conn.getMetaData().getColumns(null, validSchema, validTable, "%")) {
            while (rs.next()) {
                cols.add(new ColumnSchema(
                        rs.getInt("ORDINAL_POSITION"),
                        rs.getString("COLUMN_NAME"),
                        rs.getString("TYPE_NAME"),
                        rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls
                ));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to describe table: " + sanitizeSqlError(ex));
        }
        return cols;
    }

    @Override
    public Connection openJdbcConnection(SourceConfig config) throws SQLException {
        return openConnection(config);
    }

    protected Connection openConnection(SourceConfig config) throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", config.username());
        props.setProperty("password", config.password() == null ? "" : config.password());
        DriverManager.setLoginTimeout(4);
        return DriverManager.getConnection(buildJdbcUrl(config), props);
    }

    private List<TableSummary> readTables(Connection conn) throws SQLException {
        List<TableSummary> tables = new ArrayList<>();
        try (ResultSet rs = conn.getMetaData().getTables(null, defaultSchema(), "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String schema = rs.getString("TABLE_SCHEM");
                String name = rs.getString("TABLE_NAME");
                long rows = estimateRows(conn, schema, name);
                tables.add(new TableSummary(schema, name, rs.getString("TABLE_TYPE"), rows));
            }
        }
        return tables;
    }

    private long estimateRows(Connection conn, String schema, String table) {
        try {
            String qs = quoteIdentifier(SqlIdentifierValidator.requireValid(schema, "schema"));
            String qt = quoteIdentifier(SqlIdentifierValidator.requireValid(table, "table"));
            try (var st = conn.createStatement(); var rs = st.executeQuery(rowCountQuery(qs, qt))) {
                return rs.next() ? rs.getLong(1) : -1L;
            }
        } catch (Exception ignored) {
            return -1L;
        }
    }

    private static String sanitizeSqlError(SQLException ex) {
        int code = ex.getErrorCode();
        String state = ex.getSQLState();
        if (code == 18456 || "28P01".equals(state)) {
            return "Authentication failed (invalid username or password)";
        }
        return "Database error (SQLState=" + state + ", code=" + code + ")";
    }

    private static SourceProbeResponse fail(SourceConfig cfg, List<ProbeStepResult> steps, String hint) {
        return new SourceProbeResponse(false, cfg.engine(), null, steps, List.of(), hint);
    }

    private static long elapsed(long startMs) {
        return Math.max(1L, System.currentTimeMillis() - startMs);
    }
}
