package com.amad.finlake.storage;

import java.time.Instant;
import java.util.List;

public record AmadManifest(
        String runId,
        String jobId,
        String sourceId,
        String schema,
        String table,
        StorageFormat format,
        CompressionCodec compression,
        List<String> partitionBy,
        long rowCount,
        long totalBytes,
        List<ObjectEntry> objects,
        String simulationEpoch,
        Instant createdAtUtc
) {
    public record ObjectEntry(String key, long bytes, String sha256) {}
}
