package com.amad.finlake.jobs;

import com.amad.finlake.ingestion.ExtractionRequest;
import com.amad.finlake.storage.AmadManifest;
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
@RequestMapping("/storage/jobs")
public class AmadJobController {

    private final AmadJobOrchestrator orchestrator;

    public AmadJobController(AmadJobOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AmadJobRecord create(@Valid @RequestBody ExtractionRequest request) {
        return orchestrator.submit(request);
    }

    @GetMapping
    public List<AmadJobRecord> list(
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset
    ) {
        return orchestrator.list(Math.min(limit, 100), Math.max(offset, 0));
    }

    @GetMapping("/{id}")
    public AmadJobRecord status(@PathVariable String id) {
        return orchestrator.get(id);
    }

    @GetMapping("/{id}/result")
    public AmadManifest result(@PathVariable String id) {
        return orchestrator.getResult(id);
    }

    @PostMapping("/{id}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AmadJobRecord retry(@PathVariable String id) {
        return orchestrator.retry(id);
    }

    @PostMapping("/{id}/cancel")
    public AmadJobRecord cancel(@PathVariable String id) {
        return orchestrator.cancel(id);
    }
}
