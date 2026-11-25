package com.amad.finlake;

import com.amad.finlake.ingestion.AmadIngestionService;
import com.amad.finlake.ingestion.ExtractionRequest;
import com.amad.finlake.jobs.AmadJobOrchestrator;
import com.amad.finlake.jobs.AmadJobRecord;
import com.amad.finlake.jobs.AmadJobRepository;
import com.amad.finlake.jobs.AmadJobStatus;
import com.amad.finlake.storage.AmadManifest;
import com.amad.finlake.storage.CompressionCodec;
import com.amad.finlake.storage.StorageFormat;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class JobOrchestratorTest {

    @Test
    void submitTransitionsToSucceededAndSupportsRetry() throws Exception {
        var repo = new InMemoryJobRepo();
        var ingestion = Mockito.mock(AmadIngestionService.class);
        when(ingestion.execute(any(), any(), any(), any())).thenAnswer(inv -> {
            String jobId = inv.getArgument(0);
            return new AmadManifest("run-" + jobId, jobId, "amad-mssql", "dbo", "amad_transactions",
                    StorageFormat.PARQUET, CompressionCodec.SNAPPY, List.of("event_date"),
                    100L, 2048L, List.of(), "2026-01-01T00:00:00Z", Instant.now());
        });

        var orchestrator = new AmadJobOrchestrator(repo, ingestion);
        var req = new ExtractionRequest("amad-mssql", "dbo", "amad_transactions",
                StorageFormat.PARQUET, CompressionCodec.SNAPPY, 1000, List.of("event_date"), "curated", 100);

        AmadJobRecord submitted = orchestrator.submit(req);
        assertEquals(AmadJobStatus.PENDING, submitted.status());

        // Wait briefly for worker thread
        for (int i = 0; i < 40 && orchestrator.get(submitted.jobId()).status() != AmadJobStatus.SUCCEEDED; i++) {
            Thread.sleep(25);
        }
        assertEquals(AmadJobStatus.SUCCEEDED, orchestrator.get(submitted.jobId()).status());
        assertEquals(100L, orchestrator.getResult(submitted.jobId()).rowCount());

        AmadJobRecord retried = orchestrator.retry(submitted.jobId());
        assertNotEquals(submitted.jobId(), retried.jobId());
        orchestrator.shutdown();
    }

    @Test
    void failedJobExposesSanitizedErrorAndBlocksResult() throws Exception {
        var repo = new InMemoryJobRepo();
        var ingestion = Mockito.mock(AmadIngestionService.class);
        when(ingestion.execute(any(), any(), any(), any())).thenThrow(new IllegalStateException("Table missing"));

        var orchestrator = new AmadJobOrchestrator(repo, ingestion);
        var req = new ExtractionRequest("amad-postgres", "public", "missing",
                StorageFormat.CSV, CompressionCodec.NONE, 1000, List.of(), "raw", 10);

        AmadJobRecord submitted = orchestrator.submit(req);
        for (int i = 0; i < 40 && orchestrator.get(submitted.jobId()).status() != AmadJobStatus.FAILED; i++) {
            Thread.sleep(25);
        }
        AmadJobRecord failed = orchestrator.get(submitted.jobId());
        assertEquals(AmadJobStatus.FAILED, failed.status());
        assertEquals("Table missing", failed.errorMessage());
        assertThrows(IllegalArgumentException.class, () -> orchestrator.getResult(submitted.jobId()));
        orchestrator.shutdown();
    }

    private static final class InMemoryJobRepo implements AmadJobRepository {
        private final Map<String, AmadJobRecord> store = new ConcurrentHashMap<>();

        @Override public void create(AmadJobRecord job) { store.put(job.jobId(), job); }
        @Override public void markRunning(String id) { store.computeIfPresent(id, (k, v) -> new AmadJobRecord(v.jobId(), AmadJobStatus.RUNNING, v.request(), v.rowsProcessed(), v.filesWritten(), v.bytesWritten(), v.destination(), null, v.createdAt(), Instant.now(), null, null)); }
        @Override public void updateProgress(String id, long r, int f, long b) { store.computeIfPresent(id, (k, v) -> new AmadJobRecord(v.jobId(), v.status(), v.request(), r, f, b, v.destination(), null, v.createdAt(), v.startedAt(), null, null)); }
        @Override public void markSucceeded(String id, AmadManifest m) { store.computeIfPresent(id, (k, v) -> new AmadJobRecord(v.jobId(), AmadJobStatus.SUCCEEDED, v.request(), m.rowCount(), m.objects().size(), m.totalBytes(), v.destination(), null, v.createdAt(), v.startedAt(), Instant.now(), m)); }
        @Override public void markFailed(String id, String err) { store.computeIfPresent(id, (k, v) -> new AmadJobRecord(v.jobId(), AmadJobStatus.FAILED, v.request(), v.rowsProcessed(), v.filesWritten(), v.bytesWritten(), v.destination(), err, v.createdAt(), v.startedAt(), Instant.now(), null)); }
        @Override public void markCancelled(String id) { store.computeIfPresent(id, (k, v) -> new AmadJobRecord(v.jobId(), AmadJobStatus.CANCELLED, v.request(), v.rowsProcessed(), v.filesWritten(), v.bytesWritten(), v.destination(), null, v.createdAt(), v.startedAt(), Instant.now(), null)); }
        @Override public Optional<AmadJobRecord> findById(String id) { return Optional.ofNullable(store.get(id)); }
        @Override public List<AmadJobRecord> list(int limit, int offset) { return store.values().stream().skip(offset).limit(limit).toList(); }
    }
}
