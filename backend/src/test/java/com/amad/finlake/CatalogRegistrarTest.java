package com.amad.finlake;

import com.amad.finlake.catalog.TrinoHiveCatalogRegistrar;
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
    void rejectsMutatingSqlOnReadOnlyEndpoint() {
        assertEquals("SELECT COUNT(*) FROM amad_lake.finlake_curated.amad_transactions",
                TrinoHiveCatalogRegistrar.validateReadOnlySql("SELECT COUNT(*) FROM amad_lake.finlake_curated.amad_transactions;"));
        assertThrows(IllegalArgumentException.class, () -> TrinoHiveCatalogRegistrar.validateReadOnlySql("DROP TABLE amad_transactions"));
        assertThrows(IllegalArgumentException.class, () -> TrinoHiveCatalogRegistrar.validateReadOnlySql("SELECT 1; DELETE FROM x"));
    }
}
