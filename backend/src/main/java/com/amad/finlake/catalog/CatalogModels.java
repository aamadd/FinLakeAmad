package com.amad.finlake.catalog;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

public final class CatalogModels {
    private CatalogModels() {}

    public record RegisterTableRequest(
            String schema,
            @NotBlank String table,
            String location,
            boolean partitionedByEventDate
    ) {}

    public record RegisterTableResponse(
            String catalog,
            String schema,
            String table,
            String location,
            boolean partitionedByEventDate,
            long rowCount
    ) {}

    public record QueryRequest(
            @NotBlank String sql,
            @Min(1) @Max(1000) Integer limit
    ) {}

    public record QueryResponse(
            List<String> columns,
            List<Map<String, Object>> rows,
            int rowCount,
            long durationMs
    ) {}
}
