package com.amad.finlake.sources;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;

public final class ProbeModels {
    private ProbeModels() {}

    public record SourceProbeRequest(
            @NotNull SourceEngine engine,
            @NotBlank String host,
            @Min(1) @Max(65535) int port,
            @NotBlank String database,
            @NotBlank String username,
            @NotNull String password,
            boolean tlsEnabled
    ) {}

    public record CreateSourceRequest(
            @NotBlank String id,
            @NotBlank String name,
            @NotNull SourceEngine engine,
            @NotBlank String host,
            @Min(1) @Max(65535) int port,
            @NotBlank String database,
            @NotBlank String username,
            @NotNull String password,
            boolean tlsEnabled
    ) {}

    public record ProbeStepResult(int step, String name, String status, long durationMs, String detail) {}

    public record SourceProbeResponse(
            boolean connected,
            SourceEngine engine,
            String serverVersion,
            List<ProbeStepResult> steps,
            List<TableSummary> tables,
            String errorHint
    ) {}

    public record SourceSummary(
            String id,
            String name,
            SourceEngine engine,
            String host,
            int port,
            String database,
            String status,
            String serverVersion,
            Instant lastTestedAt,
            String lastError
    ) {}

    public record TableSummary(String schema, String name, String type, long estimatedRows) {}

    public record ColumnSchema(int ordinal, String name, String dataType, boolean nullable) {}

    public record TableSchemaResponse(
            String sourceId,
            String schema,
            String table,
            List<ColumnSchema> columns
    ) {}
}
