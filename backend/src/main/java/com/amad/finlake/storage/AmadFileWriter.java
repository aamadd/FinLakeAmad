package com.amad.finlake.storage;

import com.amad.finlake.ingestion.TransactionRow;

import java.nio.file.Path;
import java.util.List;

public interface AmadFileWriter {

    StorageFormat format();

    String contentType();

    String fileExtension();

    void writeBatch(List<TransactionRow> rows, Path outputFile, CompressionCodec codec);
}
