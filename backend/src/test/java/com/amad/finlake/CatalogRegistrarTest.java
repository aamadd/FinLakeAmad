package com.amad.finlake;

import com.amad.finlake.catalog.AbstractTrinoCatalogRegistrar;
import com.amad.finlake.catalog.TrinoHiveCatalogRegistrar;
import com.amad.finlake.catalog.TrinoIcebergCatalogRegistrar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogRegistrarTest {

    @Test
    void buildsPartitionedExternalParquetDdl() {
        String ddl = TrinoHiveCatalogRegistrar.buildCreateTableSql(
                "amad_lake.finlake_curated.amad_transactions",
                "s3a://amad-lake/curated/paysim/amad_transactions/",
                true
        );
        assertTrue(ddl.contains("format = 'PARQUET'"));
        assertTrue(ddl.contains("partitioned_by = ARRAY['event_date']"));
        assertTrue(ddl.contains("event_date VARCHAR"));
    }

    @Test
    void buildsPartitionedIcebergDdl() {
        String ddl = TrinoIcebergCatalogRegistrar.buildCreateTableSql(
                "amad_iceberg.finlake_iceberg.amad_transactions",
                "s3a://amad-lake/iceberg/amad_transactions/",
                true
        );
        assertTrue(ddl.contains("format = 'PARQUET'"));
        assertTrue(ddl.contains("partitioning = ARRAY['event_date']"));
        assertTrue(ddl.contains("event_time_simulated TIMESTAMP(6)"));
        assertTrue(ddl.contains("location = 's3a://amad-lake/iceberg/amad_transactions/'"));
    }

    @Test
    void validatesIcebergColumnTypesAndTimeTravelSql() {
        assertEquals("VARCHAR", TrinoIcebergCatalogRegistrar.validateColumnType("varchar"));
        assertEquals("DECIMAL(19,4)", TrinoIcebergCatalogRegistrar.validateColumnType("decimal(19,4)"));
        assertThrows(IllegalArgumentException.class,
                () -> TrinoIcebergCatalogRegistrar.validateColumnType("VARCHAR; DROP TABLE x"));

        String byVersion = TrinoIcebergCatalogRegistrar.buildTimeTravelSql(
                "finlake_iceberg", "amad_transactions", "123456", null);
        assertTrue(byVersion.contains("FOR VERSION AS OF 123456"));

        String byTs = TrinoIcebergCatalogRegistrar.buildTimeTravelSql(
                "finlake_iceberg", "amad_transactions", null, "2026-11-27T12:00:00Z");
        assertTrue(byTs.contains("FOR TIMESTAMP AS OF TIMESTAMP '2026-11-27 12:00:00 UTC'"));

        assertThrows(IllegalArgumentException.class,
                () -> TrinoIcebergCatalogRegistrar.buildTimeTravelSql("finlake_iceberg", "amad_transactions", null, null));
        assertThrows(IllegalArgumentException.class,
                () -> TrinoIcebergCatalogRegistrar.buildTimeTravelSql("finlake_iceberg", "amad_transactions", "1", "2026-11-27T12:00:00Z"));
    }

    @Test
    void rejectsMutatingSqlOnReadOnlyEndpoint() {
        assertEquals("SELECT COUNT(*) FROM amad_lake.finlake_curated.amad_transactions",
                AbstractTrinoCatalogRegistrar.validateReadOnlySql("SELECT COUNT(*) FROM amad_lake.finlake_curated.amad_transactions;"));
        assertThrows(IllegalArgumentException.class, () -> AbstractTrinoCatalogRegistrar.validateReadOnlySql("DROP TABLE amad_transactions"));
        assertThrows(IllegalArgumentException.class, () -> AbstractTrinoCatalogRegistrar.validateReadOnlySql("SELECT 1; DELETE FROM x"));
    }
}
