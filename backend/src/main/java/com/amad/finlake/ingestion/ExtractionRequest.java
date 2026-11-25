package com.amad.finlake.ingestion;

import com.amad.finlake.storage.CompressionCodec;
import com.amad.finlake.storage.StorageFormat;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ExtractionRequest(
        @NotBlank String sourceId,
        String schema,
        @NotBlank String table,
        @NotNull StorageFormat format,
        @NotNull CompressionCodec compression,
        @Min(100) @Max(500_000) int batchSize,
        List<String> partitionBy,
        String destinationPrefix,
        long maxRows
) {
    public List<String> normalizedPartitions() {
        return partitionBy == null ? List.of() : partitionBy;
    }
}
