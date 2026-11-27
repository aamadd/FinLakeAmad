# FinLakeAmad — Benchmark Report

## Environment & Methodology
- **Generated (UTC)**: `2026-11-27T17:04:02.829524+00:00`
- **Host**: `Windows-11-10.0.26300-SP0` (`16` logical CPUs)
- **Docker**: `Docker version 29.8.2, build 7fc2dff`
- **Dataset SHA-256**: `16910f90577b0d981bf8ff289714510bb89bc71bff7d3f220f024e287e4eea6b`
- **Rows per run**: `50,000` identical rows (`ingest_row_id` 1..50,000)
- **Trino trials**: 1 warm-up + `3` measured trials per query (median reported)

## Results

| Experiment | Source | Format | Codec | Partition | Batch | Ingest (s) | Objects | Total Bytes | Avg Obj (KB) | Trino Count (ms) | Trino Filter (ms) | Trino Agg (ms) |
|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| mssql-csv-none | amad-mssql | CSV | NONE | none | 10000 | 40.16 | 5 | 6,587,875 | 1286.7 | - | - | - |
| mssql-pq-none-nopart | amad-mssql | PARQUET | NONE | none | 10000 | 38.81 | 5 | 6,463,628 | 1262.4 | 60.12 | 70.26 | 61.29 |
| mssql-pq-snappy-part | amad-mssql | PARQUET | SNAPPY | event_date | 10000 | 38.93 | 5 | 2,424,242 | 473.5 | 72.92 | 89.76 | 78.43 |
| mssql-pq-zstd-part-b10k | amad-mssql | PARQUET | ZSTD | event_date | 10000 | 39.80 | 5 | 1,451,619 | 283.5 | 68.74 | 82.59 | 72.31 |
| mssql-pq-zstd-part-b50k | amad-mssql | PARQUET | ZSTD | event_date | 50000 | 39.81 | 1 | 1,438,415 | 1404.7 | 62.93 | 91.91 | 92.64 |
| pg-pq-zstd-part-b10k | amad-postgres | PARQUET | ZSTD | event_date | 10000 | 39.87 | 5 | 1,451,619 | 283.5 | 71.42 | 95.36 | 87.48 |
| mssql-iceberg-zstd-part | amad-mssql (via Hive Parquet) | ICEBERG_PARQUET | ZSTD | event_date | 10000 | 1.02 | 5 | 1,451,619 | 283.5 | 67.53 | 105.86 | 125.82 |

## Hive External Parquet vs Apache Iceberg

| Criterion | Hive External Parquet (`amad_lake`) | Apache Iceberg (`amad_iceberg`) |
|---|---|---|
| `SELECT COUNT(*)` median (ms) | 68.74 | 67.53 |
| Time-filter median (ms) | 82.59 | 105.86 |
| Type/Fraud aggregation median (ms) | 72.31 | 125.82 |
| ACID & Snapshot Isolation | No (`sync_partition_metadata` required) | Yes (atomic snapshot commits) |
| Schema Evolution (`ADD COLUMN`) | Manual external DDL coordination | Native metadata-only evolution |
| Time Travel (`FOR VERSION/TIMESTAMP AS OF`) | Not supported | Native via Iceberg snapshots |

## Reproduction Command
```powershell
python scripts/amad_benchmarks.py --max-rows 50000 --trials 3
```
