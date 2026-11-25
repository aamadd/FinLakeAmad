package com.amad.finlake;

import com.amad.finlake.ingestion.SimulatedTimeMapper;
import com.amad.finlake.ingestion.TransactionRow;
import com.amad.finlake.storage.CompressionCodec;
import com.amad.finlake.storage.CsvFileWriter;
import com.amad.finlake.storage.DuckDbBatchExporter;
import com.amad.finlake.storage.ParquetFileWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
