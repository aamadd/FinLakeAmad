package com.amad.finlake.sources;

import org.springframework.stereotype.Component;

@Component
public class PostgresSourceConnector extends AbstractJdbcSourceConnector {

    @Override
    public SourceEngine engine() {
        return SourceEngine.POSTGRES;
    }

    @Override
    public String defaultSchema() {
        return "public";
    }

    @Override
    public String buildJdbcUrl(SourceConfig config) {
        String ssl = config.tlsEnabled() ? "require" : "disable";
        return String.format(
                "jdbc:postgresql://%s:%d/%s?sslmode=%s&connectTimeout=4",
                config.host(), config.port(), config.database(), ssl
        );
    }

    @Override
    public String quoteIdentifier(String validatedIdentifier) {
        return "\"" + validatedIdentifier + "\"";
    }

    @Override
    protected String rowCountQuery(String quotedSchema, String quotedTable) {
        return "SELECT reltuples::bigint FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace " +
               "WHERE n.nspname = '" + quotedSchema.replace("\"", "") + "' AND c.relname = '" + quotedTable.replace("\"", "") + "'";
    }
}
