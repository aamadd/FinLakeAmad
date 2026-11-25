package com.amad.finlake.jobs;

import com.amad.finlake.ingestion.ExtractionRequest;
import com.amad.finlake.storage.AmadManifest;

import java.time.Instant;

public record AmadJobRecord(
        String jobId,
        AmadJobStatus status,
        ExtractionRequest request,
        long rowsProcessed,
        int filesWritten,
        long bytesWritten,
        String destination,
        String errorMessage,
        Instant createdAt,
        Instant startedAt,
        Instant finishedAt,
        AmadManifest manifest
) {}
