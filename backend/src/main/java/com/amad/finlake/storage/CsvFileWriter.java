package com.amad.finlake.storage;

import com.amad.finlake.ingestion.TransactionRow;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class CsvFileWriter implements AmadFileWriter {

    private final DuckDbBatchExporter exporter;

    public CsvFileWriter(DuckDbBatchExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public StorageFormat format() {
        return StorageFormat.CSV;
    }

    @Override
    public String contentType() {
        return "text/csv; charset=utf-8";
    }

    @Override
    public String fileExtension() {
        return ".csv";
    }

    @Override
    public void writeBatch(List<TransactionRow> rows, Path outputFile, CompressionCodec codec) {
        exporter.export(rows, outputFile, "FORMAT CSV, HEADER true");
    }
}
