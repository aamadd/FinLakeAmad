package com.amad.finlake.sources;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record SourceConfig(
        String id,
        String name,
        SourceEngine engine,
        String host,
        int port,
        String database,
        String username,
        @JsonIgnore String password,
        boolean tlsEnabled
) {}
