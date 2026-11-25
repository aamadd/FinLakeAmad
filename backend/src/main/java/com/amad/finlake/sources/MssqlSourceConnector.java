package com.amad.finlake.sources;

import org.springframework.stereotype.Component;

@Component
public class MssqlSourceConnector extends AbstractJdbcSourceConnector {

    @Override
    public SourceEngine engine() {
        return SourceEngine.MSSQL;
    }

    @Override
    public String defaultSchema() {
        return "dbo";
    }

    @Override
    public String buildJdbcUrl(SourceConfig config) {
        return String.format(
                "jdbc:sqlserver://%s:%d;databaseName=%s;encrypt=%s;trustServerCertificate=true;loginTimeout=4",
                config.host(), config.port(), config.database(), config.tlsEnabled()
        );
    }

    @Override
    public String quoteIdentifier(String validatedIdentifier) {
        return "[" + validatedIdentifier + "]";
    }

    @Override
    protected String rowCountQuery(String quotedSchema, String quotedTable) {
        return "SELECT COUNT_BIG(*) FROM " + quotedSchema + "." + quotedTable;
    }
}
