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
        long maxRows,
        String watermarkColumn,
        Long watermarkValue,
        Boolean idempotent
) {
    public ExtractionRequest(
            String sourceId,
            String schema,
            String table,
            StorageFormat format,
            CompressionCodec compression,
            int batchSize,
            List<String> partitionBy,
            String destinationPrefix,
            long maxRows
    ) {
        this(sourceId, schema, table, format, compression, batchSize, partitionBy, destinationPrefix, maxRows, null, null, null);
    }

    public List<String> normalizedPartitions() {
        return partitionBy == null ? List.of() : partitionBy;
    }

    public String effectiveWatermarkColumn() {
        return (watermarkColumn == null || watermarkColumn.isBlank()) ? "ingest_row_id" : watermarkColumn.trim();
    }

    public boolean isIdempotent() {
        return Boolean.TRUE.equals(idempotent);
    }
}
