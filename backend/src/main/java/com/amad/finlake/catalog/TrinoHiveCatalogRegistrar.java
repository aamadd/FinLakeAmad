package com.amad.finlake.catalog;

import com.amad.finlake.catalog.CatalogModels.RegisterTableResponse;
import com.amad.finlake.common.AmadProperties;
import com.amad.finlake.common.SqlIdentifierValidator;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@Service
@Primary
public class TrinoHiveCatalogRegistrar extends AbstractTrinoCatalogRegistrar {

    private static final String CATALOG = "amad_lake";

    public TrinoHiveCatalogRegistrar(AmadProperties props) {
        super(props, CATALOG, "finlake_curated");
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
            long count = querySingleLong(st, "SELECT COUNT(*) FROM " + fullTable);
            return new RegisterTableResponse(CATALOG, validSchema, validTable, effectiveLocation, partitionedByEventDate, count);
        } catch (SQLException ex) {
            throw new IllegalStateException("Trino catalog registration failed: " + ex.getMessage(), ex);
        }
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
}
