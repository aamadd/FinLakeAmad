package com.amad.finlake.sources;

import com.amad.finlake.sources.ProbeModels.CreateSourceRequest;
import com.amad.finlake.sources.ProbeModels.SourceProbeRequest;
import com.amad.finlake.sources.ProbeModels.SourceProbeResponse;
import com.amad.finlake.sources.ProbeModels.SourceSummary;
import com.amad.finlake.sources.ProbeModels.TableSchemaResponse;
import com.amad.finlake.sources.ProbeModels.TableSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/sources")
public class AmadSourceController {

    private final SourceRegistryService registry;

    public AmadSourceController(SourceRegistryService registry) {
        this.registry = registry;
    }

    @GetMapping
    public List<SourceSummary> list() {
        return registry.listSources();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SourceSummary register(@Valid @RequestBody CreateSourceRequest request) {
        return registry.register(request);
    }

    @PostMapping("/probe")
    public SourceProbeResponse probe(@Valid @RequestBody SourceProbeRequest request) {
        return registry.probe(request);
    }

    @PostMapping("/{id}/test")
    public SourceProbeResponse test(@PathVariable String id) {
        return registry.testSource(id);
    }

    @GetMapping("/{id}/tables")
    public List<TableSummary> tables(@PathVariable String id) {
        return registry.listTables(id);
    }

    @GetMapping("/{id}/tables/{table}/schema")
    public TableSchemaResponse schema(
            @PathVariable String id,
            @PathVariable String table,
            @RequestParam(required = false) String schema
    ) {
        return registry.describeTable(id, schema, table);
    }
}
