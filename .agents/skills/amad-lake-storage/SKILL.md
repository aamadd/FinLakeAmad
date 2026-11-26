---
name: amad-lake-storage
description: Write, partition, compress, and publish analytical Parquet/CSV files to MinIO (`amad-lake`) safely.
---
# FinLakeAmad Parquet & MinIO Storage Skill

## Mission
Produce query-efficient, reproducible Parquet and CSV objects in `amad-minio` (`s3://amad-lake/`).

## Rules
- Use `DuckDbBatchExporter` (`ParquetFileWriter` / `CsvFileWriter`) supporting `NONE`, `SNAPPY`, and `ZSTD` compression.
- Write `event_date` as `VARCHAR` (`YYYY-MM-DD`) matching Hive/Trino DDL so unpartitioned and partitioned Parquet reads share consistent schema statistics.
- Stage under `staging/<run_id>/`, promote to `curated/paysim/<table>/` or `raw/paysim/<table>/`, and publish an immutable JSON manifest under `manifests/paysim/<table>/<run_id>.json`.
- Never partition by high-cardinality account IDs (`nameOrig`, `nameDest`).
