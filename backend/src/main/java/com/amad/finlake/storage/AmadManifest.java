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
        Instant createdAtUtc,
        String watermarkColumn,
        Long watermarkStart,
        Long watermarkEnd
) {
    public AmadManifest(
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
        this(runId, jobId, sourceId, schema, table, format, compression, partitionBy,
                rowCount, totalBytes, objects, simulationEpoch, createdAtUtc, null, null, null);
    }

    public record ObjectEntry(String key, long bytes, String sha256) {}
}
