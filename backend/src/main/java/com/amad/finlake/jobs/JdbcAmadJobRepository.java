package com.amad.finlake.jobs;

import com.amad.finlake.common.AmadProperties;
import com.amad.finlake.ingestion.ExtractionRequest;
import com.amad.finlake.sources.PostgresSourceConnector;
import com.amad.finlake.sources.SourceConfig;
import com.amad.finlake.sources.SourceEngine;
import com.amad.finlake.storage.AmadManifest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class JdbcAmadJobRepository implements AmadJobRepository {

    private final PostgresSourceConnector pg;
    private final SourceConfig metaDb;
    private final ObjectMapper mapper;

    public JdbcAmadJobRepository(PostgresSourceConnector pg, AmadProperties props, ObjectMapper mapper) {
        this.pg = pg;
        var p = props.postgres();
        this.metaDb = new SourceConfig("meta", "Job Metadata", SourceEngine.POSTGRES,
                p.host(), p.port(), p.database(), p.username(), p.password(), false);
        this.mapper = mapper;
    }

    @PostConstruct
    public void initSchema() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS public.amad_storage_jobs (
                    job_id         VARCHAR(64) PRIMARY KEY,
                    status         VARCHAR(16) NOT NULL,
                    request_json   TEXT        NOT NULL,
                    rows_processed BIGINT      NOT NULL DEFAULT 0,
                    files_written  INTEGER     NOT NULL DEFAULT 0,
                    bytes_written  BIGINT      NOT NULL DEFAULT 0,
                    destination    VARCHAR(255),
                    error_message  TEXT,
                    created_at     TIMESTAMPTZ NOT NULL,
                    started_at     TIMESTAMPTZ,
                    finished_at    TIMESTAMPTZ,
                    manifest_json  TEXT
                )
                """;
        try (Connection c = pg.openJdbcConnection(metaDb); Statement st = c.createStatement()) {
            st.execute(ddl);
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to initialize amad_storage_jobs table", ex);
        }
    }

    @Override
    public void create(AmadJobRecord job) {
        String sql = "INSERT INTO public.amad_storage_jobs (job_id, status, request_json, rows_processed, files_written, bytes_written, destination, created_at) VALUES (?, ?, ?, 0, 0, 0, ?, ?)";
        executeUpdate(sql, ps -> {
            ps.setString(1, job.jobId());
            ps.setString(2, job.status().name());
            ps.setString(3, toJson(job.request()));
            ps.setString(4, job.destination());
            ps.setTimestamp(5, Timestamp.from(job.createdAt()));
        });
    }

    @Override
    public void markRunning(String jobId) {
        executeUpdate("UPDATE public.amad_storage_jobs SET status = 'RUNNING', started_at = ? WHERE job_id = ?", ps -> {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, jobId);
        });
    }

    @Override
    public void updateProgress(String jobId, long rowsProcessed, int filesWritten, long bytesWritten) {
        executeUpdate("UPDATE public.amad_storage_jobs SET rows_processed = ?, files_written = ?, bytes_written = ? WHERE job_id = ?", ps -> {
            ps.setLong(1, rowsProcessed);
            ps.setInt(2, filesWritten);
            ps.setLong(3, bytesWritten);
            ps.setString(4, jobId);
        });
    }

    @Override
    public void markSucceeded(String jobId, AmadManifest manifest) {
        executeUpdate(
                "UPDATE public.amad_storage_jobs SET status = 'SUCCEEDED', rows_processed = ?, files_written = ?, bytes_written = ?, finished_at = ?, manifest_json = ? WHERE job_id = ?",
                ps -> {
                    ps.setLong(1, manifest.rowCount());
                    ps.setInt(2, manifest.objects().size());
                    ps.setLong(3, manifest.totalBytes());
                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                    ps.setString(5, toJson(manifest));
                    ps.setString(6, jobId);
                });
    }

    @Override
    public void markFailed(String jobId, String sanitizedError) {
        executeUpdate("UPDATE public.amad_storage_jobs SET status = 'FAILED', error_message = ?, finished_at = ? WHERE job_id = ?", ps -> {
            ps.setString(1, sanitizedError);
            ps.setTimestamp(2, Timestamp.from(Instant.now()));
            ps.setString(3, jobId);
        });
    }

    @Override
    public void markCancelled(String jobId) {
        executeUpdate("UPDATE public.amad_storage_jobs SET status = 'CANCELLED', finished_at = ? WHERE job_id = ?", ps -> {
            ps.setTimestamp(1, Timestamp.from(Instant.now()));
            ps.setString(2, jobId);
        });
    }

    @Override
    public Optional<AmadJobRecord> findById(String jobId) {
        String sql = "SELECT job_id, status, request_json, rows_processed, files_written, bytes_written, destination, error_message, created_at, started_at, finished_at, manifest_json FROM public.amad_storage_jobs WHERE job_id = ?";
        try (Connection c = pg.openJdbcConnection(metaDb); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, jobId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to fetch job " + jobId, ex);
        }
    }

    @Override
    public List<AmadJobRecord> list(int limit, int offset) {
        String sql = "SELECT job_id, status, request_json, rows_processed, files_written, bytes_written, destination, error_message, created_at, started_at, finished_at, manifest_json FROM public.amad_storage_jobs ORDER BY created_at DESC LIMIT ? OFFSET ?";
        List<AmadJobRecord> out = new ArrayList<>();
        try (Connection c = pg.openJdbcConnection(metaDb); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setInt(2, offset);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(mapRow(rs));
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to list jobs", ex);
        }
        return out;
    }

    private AmadJobRecord mapRow(ResultSet rs) throws SQLException {
        return new AmadJobRecord(
                rs.getString(1),
                AmadJobStatus.valueOf(rs.getString(2)),
                fromJson(rs.getString(3), ExtractionRequest.class),
                rs.getLong(4),
                rs.getInt(5),
                rs.getLong(6),
                rs.getString(7),
                rs.getString(8),
                toInstant(rs.getTimestamp(9)),
                toInstant(rs.getTimestamp(10)),
                toInstant(rs.getTimestamp(11)),
                rs.getString(12) == null ? null : fromJson(rs.getString(12), AmadManifest.class)
        );
    }

    private void executeUpdate(String sql, SqlSetter setter) {
        try (Connection c = pg.openJdbcConnection(metaDb); PreparedStatement ps = c.prepareStatement(sql)) {
            setter.accept(ps);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Job metadata update failed", ex);
        }
    }

    private String toJson(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON serialization failed", ex);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception ex) {
            throw new IllegalStateException("JSON deserialization failed", ex);
        }
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    @FunctionalInterface
    private interface SqlSetter {
        void accept(PreparedStatement ps) throws SQLException;
    }
}
