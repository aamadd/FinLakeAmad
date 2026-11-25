package com.amad.finlake.jobs;

import com.amad.finlake.ingestion.AmadIngestionService;
import com.amad.finlake.ingestion.ExtractionRequest;
import com.amad.finlake.storage.AmadManifest;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class AmadJobOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(AmadJobOrchestrator.class);

    private final AmadJobRepository repo;
    private final AmadIngestionService ingestion;
    private final ExecutorService workers = Executors.newFixedThreadPool(2);
    private final Map<String, AtomicBoolean> cancelFlags = new ConcurrentHashMap<>();

    public AmadJobOrchestrator(AmadJobRepository repo, AmadIngestionService ingestion) {
        this.repo = repo;
        this.ingestion = ingestion;
    }

    public AmadJobRecord submit(ExtractionRequest req) {
        String jobId = "amad-job-" + UUID.randomUUID();
        String zone = (req.destinationPrefix() == null || req.destinationPrefix().isBlank()) ? "curated" : req.destinationPrefix();
        String destination = String.format("%s/paysim/%s/", zone, req.table());
        AmadJobRecord initial = new AmadJobRecord(
                jobId, AmadJobStatus.PENDING, req, 0, 0, 0, destination, null,
                Instant.now(), null, null, null
        );
        repo.create(initial);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        cancelFlags.put(jobId, cancelled);
        workers.submit(() -> runJob(jobId, req, cancelled));
        return initial;
    }

    public AmadJobRecord get(String jobId) {
        return repo.findById(jobId).orElseThrow(() -> new NoSuchElementException("Unknown job: " + jobId));
    }

    public AmadManifest getResult(String jobId) {
        AmadJobRecord job = get(jobId);
        if (job.status() != AmadJobStatus.SUCCEEDED || job.manifest() == null) {
            throw new IllegalArgumentException("Result unavailable while job status is " + job.status());
        }
        return job.manifest();
    }

    public List<AmadJobRecord> list(int limit, int offset) {
        return repo.list(limit, offset);
    }

    public AmadJobRecord retry(String jobId) {
        AmadJobRecord existing = get(jobId);
        if (existing.status() == AmadJobStatus.RUNNING || existing.status() == AmadJobStatus.PENDING) {
            throw new IllegalArgumentException("Cannot retry an active job");
        }
        return submit(existing.request());
    }

    public AmadJobRecord cancel(String jobId) {
        AmadJobRecord existing = get(jobId);
        if (existing.status() == AmadJobStatus.SUCCEEDED || existing.status() == AmadJobStatus.FAILED || existing.status() == AmadJobStatus.CANCELLED) {
            return existing;
        }
        cancelFlags.computeIfAbsent(jobId, k -> new AtomicBoolean(true)).set(true);
        repo.markCancelled(jobId);
        return get(jobId);
    }

    private void runJob(String jobId, ExtractionRequest req, AtomicBoolean cancelled) {
        if (cancelled.get()) {
            repo.markCancelled(jobId);
            return;
        }
        repo.markRunning(jobId);
        AtomicLong cumulativeRows = new AtomicLong(0);
        try {
            AmadManifest manifest = ingestion.execute(jobId, req, cancelled::get, (batchRows, files, bytes) -> {
                long rows = cumulativeRows.addAndGet(batchRows);
                repo.updateProgress(jobId, rows, files, bytes);
            });
            repo.markSucceeded(jobId, manifest);
            log.info("job_id={} run_id={} status=SUCCEEDED rows={}", jobId, manifest.runId(), manifest.rowCount());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            repo.markCancelled(jobId);
            log.info("job_id={} status=CANCELLED", jobId);
        } catch (Exception ex) {
            String safeMsg = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            repo.markFailed(jobId, safeMsg);
            log.warn("job_id={} status=FAILED error={}", jobId, safeMsg);
        } finally {
            cancelFlags.remove(jobId);
        }
    }

    @PreDestroy
    public void shutdown() {
        workers.shutdownNow();
    }
}
