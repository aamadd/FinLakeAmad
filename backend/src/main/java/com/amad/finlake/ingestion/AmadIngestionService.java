package com.amad.finlake.ingestion;

import com.amad.finlake.sources.AmadSourceConnector;
import com.amad.finlake.sources.SourceConfig;
import com.amad.finlake.sources.SourceRegistryService;
import com.amad.finlake.storage.AmadFileWriter;
import com.amad.finlake.storage.AmadManifest;
import com.amad.finlake.storage.AmadManifest.ObjectEntry;
import com.amad.finlake.storage.AmadObjectStore;
import com.amad.finlake.storage.StorageFormat;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class AmadIngestionService {

    private final SourceRegistryService sources;
    private final KeysetBatchReader reader;
    private final AmadObjectStore objectStore;
    private final SimulatedTimeMapper timeMapper;
    private final Map<StorageFormat, AmadFileWriter> writers;

    public AmadIngestionService(
            SourceRegistryService sources,
            KeysetBatchReader reader,
            AmadObjectStore objectStore,
            SimulatedTimeMapper timeMapper,
            List<AmadFileWriter> writerList
    ) {
        this.sources = sources;
        this.reader = reader;
        this.objectStore = objectStore;
        this.timeMapper = timeMapper;
        this.writers = writerList.stream().collect(Collectors.toMap(AmadFileWriter::format, Function.identity()));
    }

    public AmadManifest execute(
            String jobId,
            ExtractionRequest req,
            BooleanSupplier cancelled,
            ProgressListener progress
    ) throws Exception {
        validatePartitions(req.normalizedPartitions());
        String watermarkCol = KeysetBatchReader.validateWatermarkColumn(req.effectiveWatermarkColumn());
        SourceConfig cfg = sources.getConfig(req.sourceId());
        AmadSourceConnector connector = sources.getConnector(cfg.engine());
        AmadFileWriter writer = writers.get(req.format());
        if (writer == null) {
            throw new IllegalArgumentException("Unsupported format: " + req.format());
        }

        String schema = (req.schema() == null || req.schema().isBlank()) ? connector.defaultSchema() : req.schema();
        String runId = UUID.randomUUID().toString();
        String requestedZone = (req.destinationPrefix() == null || req.destinationPrefix().isBlank())
                ? (req.format() == StorageFormat.CSV ? "raw" : "curated")
                : req.destinationPrefix().replaceAll("^/+|/+$", "");
        final String zone = (req.format() == StorageFormat.CSV && "curated".equalsIgnoreCase(requestedZone))
                ? "raw"
                : requestedZone;
        boolean partitionByDate = req.normalizedPartitions().contains("event_date");
        String ingestDate = LocalDate.now(ZoneOffset.UTC).toString();

        List<StagedObject> staged = new ArrayList<>();
        AtomicInteger partSeq = new AtomicInteger(0);
        AtomicLong totalBytes = new AtomicLong(0);
        AtomicLong maxWatermark = new AtomicLong(req.watermarkValue() == null ? 0L : req.watermarkValue());
        Path tmpDir = Files.createTempDirectory("amad_ingest_" + runId + "_");

        try {
            long rowsRead = reader.streamBatches(
                    cfg, connector, schema, req.table(), req.batchSize(), req.maxRows(),
                    watermarkCol, req.watermarkValue(), cancelled, batch -> {
                        long batchMax = "step".equals(watermarkCol)
                                ? batch.stream().mapToLong(TransactionRow::step).max().orElse(0L)
                                : batch.stream().mapToLong(TransactionRow::ingestRowId).max().orElse(0L);
                        maxWatermark.accumulateAndGet(batchMax, Math::max);

                        if (partitionByDate) {
                            Map<LocalDate, List<TransactionRow>> byDate = batch.stream()
                                    .collect(Collectors.groupingBy(TransactionRow::eventDate));
                            for (var entry : byDate.entrySet()) {
                                String relPath = "event_date=" + entry.getKey() + "/part-" + String.format("%05d", partSeq.incrementAndGet()) + writer.fileExtension();
                                staged.add(stageFile(tmpDir, runId, zone, req, relPath, entry.getValue(), writer, totalBytes));
                            }
                        } else {
                            String relPath = "part-" + String.format("%05d", partSeq.incrementAndGet()) + writer.fileExtension();
                            staged.add(stageFile(tmpDir, runId, zone, req, relPath, batch, writer, totalBytes));
                        }
                        if (progress != null) {
                            progress.onBatch(batch.size(), staged.size(), totalBytes.get());
                        }
                    });

            if (cancelled.getAsBoolean()) {
                throw new InterruptedException("Extraction cancelled");
            }

            if (req.isIdempotent() && req.watermarkValue() == null && !"raw".equals(zone)) {
                String targetPrefix = String.format("%s/paysim/%s/", zone, req.table());
                deletePrefix(targetPrefix);
            }

            List<ObjectEntry> promoted = new ArrayList<>(staged.size());
            for (StagedObject s : staged) {
                String targetKey = "raw".equals(zone)
                        ? String.format("raw/paysim/%s/ingest_date=%s/run_id=%s/%s", req.table(), ingestDate, runId, s.relativePath)
                        : String.format("%s/paysim/%s/%s", zone, req.table(), s.relativePath);
                objectStore.copyObject(s.stagingKey, targetKey);
                promoted.add(new ObjectEntry(targetKey, s.bytes, s.sha256));
            }

            Long watermarkEnd = rowsRead > 0 ? maxWatermark.get() : req.watermarkValue();
            AmadManifest manifest = new AmadManifest(
                    runId, jobId, req.sourceId(), schema, req.table(), req.format(), req.compression(),
                    req.normalizedPartitions(), rowsRead, totalBytes.get(), promoted,
                    timeMapper.epoch().toString(), Instant.now(),
                    watermarkCol, req.watermarkValue(), watermarkEnd
            );
            String manifestKey = String.format("manifests/paysim/%s/%s.json", req.table(), runId);
            objectStore.putJson(manifestKey, manifest);
            return manifest;
        } finally {
            cleanupStaging(staged);
            deleteDirectoryQuietly(tmpDir);
        }
    }

    public int cleanupOrphanedStaging() {
        return deletePrefix("staging/");
    }

    private int deletePrefix(String prefix) {
        List<String> keys = objectStore.listKeys(prefix);
        int deleted = 0;
        for (String key : keys) {
            try {
                objectStore.deleteObject(key);
                deleted++;
            } catch (Exception ignored) {}
        }
        return deleted;
    }

    private StagedObject stageFile(
            Path tmpDir, String runId, String zone, ExtractionRequest req, String relPath,
            List<TransactionRow> rows, AmadFileWriter writer, AtomicLong totalBytes
    ) {
        try {
            Path localFile = tmpDir.resolve("part_" + System.nanoTime() + writer.fileExtension());
            writer.writeBatch(rows, localFile, req.compression());
            long bytes = Files.size(localFile);
            String sha256 = sha256(localFile);
            String stagingKey = String.format("staging/%s/%s/%s", runId, zone, relPath);
            objectStore.putFile(stagingKey, localFile, writer.contentType());
            Files.deleteIfExists(localFile);
            totalBytes.addAndGet(bytes);
            return new StagedObject(stagingKey, relPath, bytes, sha256);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to stage batch file", ex);
        }
    }

    private void cleanupStaging(List<StagedObject> staged) {
        for (StagedObject s : staged) {
            try {
                objectStore.deleteObject(s.stagingKey);
            } catch (Exception ignored) {}
        }
    }

    private static void validatePartitions(List<String> partitionBy) {
        for (String col : partitionBy) {
            if ("nameOrig".equalsIgnoreCase(col) || "nameDest".equalsIgnoreCase(col)) {
                throw new IllegalArgumentException("Partitioning by high-cardinality account ID is forbidden: " + col);
            }
            if (!"event_date".equals(col)) {
                throw new IllegalArgumentException("Unsupported partition column: " + col + " (allowed: event_date)");
            }
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (var in = Files.newInputStream(file)) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                }
            }
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception ex) {
            throw new IOException("SHA-256 failed", ex);
        }
    }

    private static void deleteDirectoryQuietly(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }

    private record StagedObject(String stagingKey, String relativePath, long bytes, String sha256) {}

    @FunctionalInterface
    public interface ProgressListener {
        void onBatch(long batchRows, int filesWritten, long totalBytes);
    }
}
