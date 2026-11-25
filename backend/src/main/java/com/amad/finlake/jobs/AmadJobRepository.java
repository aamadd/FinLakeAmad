package com.amad.finlake.jobs;

import com.amad.finlake.storage.AmadManifest;

import java.util.List;
import java.util.Optional;

public interface AmadJobRepository {

    void create(AmadJobRecord job);

    void markRunning(String jobId);

    void updateProgress(String jobId, long rowsProcessed, int filesWritten, long bytesWritten);

    void markSucceeded(String jobId, AmadManifest manifest);

    void markFailed(String jobId, String sanitizedError);

    void markCancelled(String jobId);

    Optional<AmadJobRecord> findById(String jobId);

    List<AmadJobRecord> list(int limit, int offset);
}
