package com.amad.finlake.catalog;

import com.amad.finlake.catalog.CatalogModels.QueryRequest;
import com.amad.finlake.catalog.CatalogModels.QueryResponse;
import com.amad.finlake.catalog.CatalogModels.RegisterTableRequest;
import com.amad.finlake.catalog.CatalogModels.RegisterTableResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/catalog")
public class AmadCatalogController {

    private final AmadCatalogRegistrar catalog;

    public AmadCatalogController(AmadCatalogRegistrar catalog) {
        this.catalog = catalog;
    }

    @PostMapping("/register")
    public RegisterTableResponse register(@Valid @RequestBody RegisterTableRequest request) {
        return catalog.registerCuratedTransactions(
                request.schema(),
                request.table(),
                request.location(),
                request.partitionedByEventDate()
        );
    }

    @GetMapping("/tables")
    public List<String> tables(@RequestParam(required = false, defaultValue = "finlake_curated") String schema) {
        return catalog.listTables(schema);
    }

    @PostMapping("/query")
    public QueryResponse query(@Valid @RequestBody QueryRequest request) {
        int limit = request.limit() == null ? 100 : request.limit();
        return catalog.executeReadOnlyQuery(request.sql(), limit);
    }
}
