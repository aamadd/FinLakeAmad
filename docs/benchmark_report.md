# FinLakeAmad — Benchmark Report

## Environment & Methodology
- **Generated (UTC)**: `2026-11-25T20:56:21.448187+00:00`
- **Host**: `Windows-11-10.0.26300-SP0` (`16` logical CPUs)
- **Docker**: `Docker version 29.8.2, build 7fc2dff`
- **Dataset SHA-256**: `16910f90577b0d981bf8ff289714510bb89bc71bff7d3f220f024e287e4eea6b`
- **Rows per run**: `50,000` identical rows (`ingest_row_id` 1..50,000)
- **Trino trials**: 1 warm-up + `3` measured trials per query (median reported)

## Results

| Experiment | Source | Format | Codec | Partition | Batch | Ingest (s) | Objects | Total Bytes | Avg Obj (KB) | Trino Count (ms) | Trino Filter (ms) | Trino Agg (ms) |
|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| mssql-csv-none | amad-mssql | CSV | NONE | none | 10000 | 47.24 | 5 | 6,587,875 | 1286.7 | - | - | - |
| mssql-pq-none-nopart | amad-mssql | PARQUET | NONE | none | 10000 | 43.93 | 5 | 6,463,628 | 1262.4 | 308.22 | 312.26 | 476.9 |
| mssql-pq-snappy-part | amad-mssql | PARQUET | SNAPPY | event_date | 10000 | 44.08 | 5 | 2,424,242 | 473.5 | 184.86 | 266.4 | 157.54 |
| mssql-pq-zstd-part-b10k | amad-mssql | PARQUET | ZSTD | event_date | 10000 | 43.94 | 5 | 1,451,619 | 283.5 | 110.33 | 157.14 | 146.5 |
| mssql-pq-zstd-part-b50k | amad-mssql | PARQUET | ZSTD | event_date | 50000 | 45.52 | 1 | 1,438,415 | 1404.7 | 110.31 | 126.45 | 117.95 |
| pg-pq-zstd-part-b10k | amad-postgres | PARQUET | ZSTD | event_date | 10000 | 43.38 | 5 | 1,451,619 | 283.5 | 117.58 | 113.64 | 178.17 |

## Reproduction Command
```powershell
python scripts/amad_benchmarks.py --max-rows 50000 --trials 3
```
