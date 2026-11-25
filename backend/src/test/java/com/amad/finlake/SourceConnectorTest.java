package com.amad.finlake;

import com.amad.finlake.sources.MssqlSourceConnector;
import com.amad.finlake.sources.PostgresSourceConnector;
import com.amad.finlake.sources.SourceConfig;
import com.amad.finlake.sources.SourceEngine;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceConnectorTest {

    @Test
    void mssqlConnectorBuildsExpectedUrlAndQuotes() {
        var mssql = new MssqlSourceConnector();
        var cfg = new SourceConfig("s1", "SQL", SourceEngine.MSSQL, "amad-mssql", 1433, "amad_source", "sa", "secret", false);
        assertTrue(mssql.buildJdbcUrl(cfg).startsWith("jdbc:sqlserver://amad-mssql:1433;databaseName=amad_source;"));
        assertEquals("[amad_transactions]", mssql.quoteIdentifier("amad_transactions"));
    }

    @Test
    void postgresConnectorBuildsExpectedUrlAndQuotes() {
        var pg = new PostgresSourceConnector();
        var cfg = new SourceConfig("s2", "PG", SourceEngine.POSTGRES, "amad-postgres", 5432, "amad_source", "u", "secret", false);
        assertEquals("jdbc:postgresql://amad-postgres:5432/amad_source?sslmode=disable&connectTimeout=4", pg.buildJdbcUrl(cfg));
        assertEquals("\"amad_transactions\"", pg.quoteIdentifier("amad_transactions"));
    }

    @Test
    void unreachableHostFailsAtStepOneWithoutLeakingPassword() {
        var pg = new PostgresSourceConnector();
        var cfg = new SourceConfig("s3", "Bad", SourceEngine.POSTGRES, "127.0.0.1", 59999, "db", "u", "super_secret_pw", false);
        var res = pg.probe(cfg);
        assertFalse(res.connected());
        assertEquals("FAILED", res.steps().get(0).status());
        assertFalse(res.toString().contains("super_secret_pw"));
    }
}
