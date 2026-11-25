package com.amad.finlake.catalog;

import com.amad.finlake.catalog.CatalogModels.QueryResponse;
import com.amad.finlake.catalog.CatalogModels.RegisterTableResponse;

import java.util.List;

/** SOLID contract for registering lake tables in a query catalog and running read-only SQL. */
public interface AmadCatalogRegistrar {

    RegisterTableResponse registerCuratedTransactions(
            String schema,
            String table,
            String location,
            boolean partitionedByEventDate
    );

    List<String> listTables(String schema);

    QueryResponse executeReadOnlyQuery(String sql, int limit);
}
