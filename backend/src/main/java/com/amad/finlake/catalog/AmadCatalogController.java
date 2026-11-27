package com.amad.finlake.catalog;

import com.amad.finlake.catalog.CatalogModels.IcebergIngestRequest;
import com.amad.finlake.catalog.CatalogModels.IcebergIngestResponse;
import com.amad.finlake.catalog.CatalogModels.IcebergSchemaEvolutionRequest;
import com.amad.finlake.catalog.CatalogModels.IcebergSchemaEvolutionResponse;
import com.amad.finlake.catalog.CatalogModels.IcebergSnapshot;
import com.amad.finlake.catalog.CatalogModels.IcebergTimeTravelRequest;
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
    private final TrinoIcebergCatalogRegistrar iceberg;

    public AmadCatalogController(AmadCatalogRegistrar catalog, TrinoIcebergCatalogRegistrar iceberg) {
        this.catalog = catalog;
        this.iceberg = iceberg;
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

    @PostMapping("/iceberg/register")
    public RegisterTableResponse registerIceberg(@Valid @RequestBody RegisterTableRequest request) {
        return iceberg.registerCuratedTransactions(
                request.schema(),
                request.table(),
                request.location(),
                request.partitionedByEventDate()
        );
    }

    @PostMapping("/iceberg/ingest")
    public IcebergIngestResponse ingestIceberg(@RequestBody IcebergIngestRequest request) {
        return iceberg.ingestFromHive(
                request.sourceSchema(),
                request.sourceTable(),
                request.targetSchema(),
                request.targetTable(),
                request.minIngestRowId(),
                request.maxIngestRowId()
        );
    }

    @GetMapping("/iceberg/snapshots")
    public List<IcebergSnapshot> icebergSnapshots(
            @RequestParam(required = false, defaultValue = "finlake_iceberg") String schema,
            @RequestParam(required = false, defaultValue = "amad_transactions") String table
    ) {
        return iceberg.listSnapshots(schema, table);
    }

    @PostMapping("/iceberg/evolve-schema")
    public IcebergSchemaEvolutionResponse evolveIcebergSchema(@Valid @RequestBody IcebergSchemaEvolutionRequest request) {
        return iceberg.evolveSchemaAddColumn(
                request.schema(),
                request.table(),
                request.columnName(),
                request.columnType()
        );
    }

    @PostMapping("/iceberg/time-travel")
    public QueryResponse icebergTimeTravel(@Valid @RequestBody IcebergTimeTravelRequest request) {
        int limit = request.limit() == null ? 100 : request.limit();
        return iceberg.queryTimeTravel(
                request.schema(),
                request.table(),
                request.snapshotId(),
                request.timestampIso(),
                limit
        );
    }

    @PostMapping("/iceberg/query")
    public QueryResponse queryIceberg(@Valid @RequestBody QueryRequest request) {
        int limit = request.limit() == null ? 100 : request.limit();
        return iceberg.executeReadOnlyQuery(request.sql(), limit);
    }
}
