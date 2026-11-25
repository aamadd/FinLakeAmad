package com.amad.finlake.ingestion;

import com.amad.finlake.storage.AmadManifest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/ingestion")
public class AmadIngestionController {

    private final AmadIngestionService ingestionService;

    public AmadIngestionController(AmadIngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/extract")
    public AmadManifest extractSync(@Valid @RequestBody ExtractionRequest request) throws Exception {
        return ingestionService.execute("sync-" + UUID.randomUUID(), request, () -> false, null);
    }
}
