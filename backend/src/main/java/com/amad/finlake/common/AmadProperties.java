package com.amad.finlake.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "amad")
public record AmadProperties(
        JdbcDefaults mssql,
        JdbcDefaults postgres,
        MinioConfig minio,
        TrinoConfig trino
) {
    public record JdbcDefaults(String host, int port, String database, String username, String password) {}
    public record MinioConfig(String endpoint, String bucket) {}
    public record TrinoConfig(String endpoint) {}
}
