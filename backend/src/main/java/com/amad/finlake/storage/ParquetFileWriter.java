package com.amad.finlake.storage;

import com.amad.finlake.ingestion.TransactionRow;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.List;

@Component
public class ParquetFileWriter implements AmadFileWriter {

    private final DuckDbBatchExporter exporter;

    public ParquetFileWriter(DuckDbBatchExporter exporter) {
        this.exporter = exporter;
    }

    @Override
    public StorageFormat format() {
        return StorageFormat.PARQUET;
    }

    @Override
    public String contentType() {
        return "application/vnd.apache.parquet";
    }

    @Override
    public String fileExtension() {
        return ".parquet";
    }

    @Override
    public void writeBatch(List<TransactionRow> rows, Path outputFile, CompressionCodec codec) {
        exporter.export(rows, outputFile, "FORMAT PARQUET, COMPRESSION '" + codec.duckDbCodec() + "'");
    }
}
