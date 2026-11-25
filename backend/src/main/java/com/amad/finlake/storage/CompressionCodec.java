package com.amad.finlake.storage;

public enum CompressionCodec {
    NONE("UNCOMPRESSED"),
    SNAPPY("SNAPPY"),
    ZSTD("ZSTD");

    private final String duckDbCodec;

    CompressionCodec(String duckDbCodec) {
        this.duckDbCodec = duckDbCodec;
    }

    public String duckDbCodec() {
        return duckDbCodec;
    }
}
