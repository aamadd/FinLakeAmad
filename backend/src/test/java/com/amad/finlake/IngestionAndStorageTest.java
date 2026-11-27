package com.amad.finlake;

import com.amad.finlake.ingestion.AmadIngestionService;
import com.amad.finlake.ingestion.ExtractionRequest;
import com.amad.finlake.ingestion.KeysetBatchReader;
import com.amad.finlake.ingestion.SimulatedTimeMapper;
import com.amad.finlake.ingestion.TransactionRow;
import com.amad.finlake.sources.AmadSourceConnector;
import com.amad.finlake.sources.SourceConfig;
import com.amad.finlake.sources.SourceEngine;
import com.amad.finlake.sources.SourceRegistryService;
import com.amad.finlake.storage.AmadObjectStore;
import com.amad.finlake.storage.CompressionCodec;
import com.amad.finlake.storage.CsvFileWriter;
import com.amad.finlake.storage.DuckDbBatchExporter;
import com.amad.finlake.storage.ParquetFileWriter;
import com.amad.finlake.storage.StorageFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IngestionAndStorageTest {

    @Test
    void simulatedTimeMapperDerivesExpectedUtcAndDate() {
        var mapper = new SimulatedTimeMapper(Instant.parse("2026-01-01T00:00:00Z"));
        assertEquals(Instant.parse("2026-01-01T01:00:00Z"), mapper.toSimulatedInstant(1));
        assertEquals(LocalDate.of(2026, 1, 2), mapper.toSimulatedDate(25));
    }

    @Test
    void parquetAndCsvWritersProduceReadableFiles(@TempDir Path tempDir) throws Exception {
        var exporter = new DuckDbBatchExporter();
        var parquetWriter = new ParquetFileWriter(exporter);
        var csvWriter = new CsvFileWriter(exporter);

        var rows = List.of(
                new TransactionRow(1L, 1, "PAYMENT", new BigDecimal("125.5000"),
                        "C100", new BigDecimal("500.0000"), new BigDecimal("374.5000"),
                        "M200", BigDecimal.ZERO, BigDecimal.ZERO, false, false,
                        Instant.parse("2026-01-01T01:00:00Z"), LocalDate.of(2026, 1, 1)),
                new TransactionRow(2L, 25, "TRANSFER", new BigDecimal("900.0000"),
                        "C101", new BigDecimal("1000.0000"), new BigDecimal("100.0000"),
                        "C202", new BigDecimal("10.0000"), new BigDecimal("910.0000"), true, false,
                        Instant.parse("2026-01-02T01:00:00Z"), LocalDate.of(2026, 1, 2))
        );

        for (CompressionCodec codec : CompressionCodec.values()) {
            Path pq = tempDir.resolve("test_" + codec.name() + ".parquet");
            parquetWriter.writeBatch(rows, pq, codec);
            assertTrue(Files.size(pq) > 0);
            try (var conn = DriverManager.getConnection("jdbc:duckdb:");
                 var st = conn.createStatement();
                 var rs = st.executeQuery("SELECT COUNT(*) FROM read_parquet('" + pq.toString().replace("\\", "/") + "')")) {
                assertTrue(rs.next());
                assertEquals(2L, rs.getLong(1));
            }
        }

        Path csv = tempDir.resolve("test.csv");
        csvWriter.writeBatch(rows, csv, CompressionCodec.NONE);
        assertEquals(3, Files.readAllLines(csv).size());
    }

    @Test
    void keysetQueryBuildsWatermarkClausesSafely() {
        assertEquals("ingest_row_id", KeysetBatchReader.validateWatermarkColumn(null));
        assertEquals("step", KeysetBatchReader.validateWatermarkColumn("step"));
        assertThrows(IllegalArgumentException.class, () -> KeysetBatchReader.validateWatermarkColumn("amount"));

        String byId = KeysetBatchReader.buildKeysetQuery("[dbo]", "[amad_transactions]", "ingest_row_id", 100L);
        assertTrue(byId.contains("WHERE ingest_row_id > ? ORDER BY ingest_row_id ASC"));

        String byStep = KeysetBatchReader.buildKeysetQuery("[dbo]", "[amad_transactions]", "step", 10L);
        assertTrue(byStep.contains("WHERE step > ? AND ingest_row_id > ? ORDER BY ingest_row_id ASC"));
    }

    @Test
    void ingestionCleansUpStagingEvenWhenPromotionFails() throws Exception {
        var sources = Mockito.mock(SourceRegistryService.class);
        var connector = Mockito.mock(AmadSourceConnector.class);
        var reader = Mockito.mock(KeysetBatchReader.class);
        var store = Mockito.mock(AmadObjectStore.class);
        var mapper = new SimulatedTimeMapper(Instant.parse("2026-01-01T00:00:00Z"));
        var writer = new ParquetFileWriter(new DuckDbBatchExporter());

        var cfg = new SourceConfig("amad-mssql", "Primary SQL Server", SourceEngine.MSSQL, "amad-mssql", 1433, "amad_source", "sa", "pw", false);
        when(sources.getConfig("amad-mssql")).thenReturn(cfg);
        when(sources.getConnector(SourceEngine.MSSQL)).thenReturn(connector);
        when(connector.defaultSchema()).thenReturn("dbo");

        var sampleRow = new TransactionRow(1L, 1, "PAYMENT", new BigDecimal("10.0000"),
                "C1", new BigDecimal("20.0000"), new BigDecimal("10.0000"),
                "M1", BigDecimal.ZERO, BigDecimal.ZERO, false, false,
                Instant.parse("2026-01-01T01:00:00Z"), LocalDate.of(2026, 1, 1));

        doAnswer(inv -> {
            @SuppressWarnings("unchecked")
            Consumer<List<TransactionRow>> consumer = inv.getArgument(9);
            consumer.accept(List.of(sampleRow));
            return 1L;
        }).when(reader).streamBatches(any(), any(), anyString(), anyString(), anyInt(), anyLong(), anyString(), any(), any(), any());

        doThrow(new IllegalStateException("Simulated MinIO copy failure"))
                .when(store).copyObject(anyString(), anyString());

        var service = new AmadIngestionService(sources, reader, store, mapper, List.of(writer));
        var req = new ExtractionRequest("amad-mssql", "dbo", "amad_transactions",
                StorageFormat.PARQUET, CompressionCodec.SNAPPY, 1000, List.of("event_date"), "curated", 1,
                "ingest_row_id", 0L, true);

        assertThrows(IllegalStateException.class, () -> service.execute("job-1", req, () -> false, null));
        verify(store, atLeastOnce()).deleteObject(startsWith("staging/"));
    }
}
