# FinLakeAmad — Quality & Reconciliation Report (§14)

## 1. Dataset Source Integrity (PaySim)
- **Dataset SHA-256**: `16910f90577b0d981bf8ff289714510bb89bc71bff7d3f220f024e287e4eea6b`
- **Schema**: 11 PaySim columns + technical monotonic `ingest_row_id` (`1..6,362,620`), enriched with `event_time_simulated` and `event_date` (`YYYY-MM-DD`).
- **Constraints**: Zero invented KYC/merchant/currency fields; no production ML fraud model.

## 2. Unit & Integration Test Results (`mvn -B test package`)
Exécuté dans `Dockerfile.backend` (`maven:3.9-eclipse-temurin-21`) :

| Test Suite | Tests Run | Failures | Errors | Skipped | Scope |
|---|---:|---:|---:|---:|---|
| `IngestionAndStorageTest` | 4 | 0 | 0 | 0 | `SimulatedTimeMapper`, DuckDB Parquet/CSV (`NONE`/`SNAPPY`/`ZSTD`), `KeysetBatchReader` watermark SQL, `staging/` cleanup on failure |
| `SqlIdentifierValidatorTest` | 2 | 0 | 0 | 0 | SQL identifier allowlist & injection rejection |
| `SourceConnectorTest` | 3 | 0 | 0 | 0 | MSSQL & PostgreSQL JDBC URLs and quoting |
| `JobOrchestratorTest` | 2 | 0 | 0 | 0 | Async job lifecycle, retry, sanitized failure |
| `CatalogRegistrarTest` | 4 | 0 | 0 | 0 | Hive Parquet DDL, Iceberg DDL, Iceberg schema evolution & Time Travel SQL, read-only SQL guard |
| **Total** | **15** | **0** | **0** | **0** | **BUILD SUCCESS** |

## 3. End-to-End Reconciliation (Live Stack)
| Layer / Catalog | Table / Scope | Observed Row Count | Status |
|---|---|---|---|
| Hive Parquet (`amad_lake`, MSSQL source) | `finlake_bench_mssql_pq_zstd_part_b10k.amad_transactions` | `50,000` | Reconciled |
| Hive Parquet (`amad_lake`, PostgreSQL source) | `finlake_bench_pg_pq_zstd_part_b10k.amad_transactions` | `50,000` | Reconciled |
| Apache Iceberg (`amad_iceberg`, bench) | `finlake_iceberg.amad_transactions_bench_*` | `50,000` | Reconciled |
| Apache Iceberg (`amad_iceberg`, multi-snapshot) | `finlake_iceberg.amad_transactions` | `150,000` (7 snapshots) | Verified |

## 4. P7 Bonus Functional Checks (Observed)
- **Iceberg Time Travel (`FOR VERSION AS OF 7693319929701346342`)** : `row_count = 25,000`, `min_row_id = 1`, `max_row_id = 25,000`, `total_amount = 3,305,985,234.05`.
- **Iceberg Schema Evolution (`ADD COLUMN amad_audit_tag VARCHAR`)** : `15` colonnes après évolution (`amad_audit_tag` présent).
- **Incremental Watermark (`step > 10`, `maxRows = 1000`, `idempotent = true`)** : `1,000` lignes extraites (`watermarkStart = 10`, `watermarkEnd = 11`).
- **Staging Cleanup (`POST /ingestion/cleanup-staging`)** : purge vérifiée des objets orphelins sous `staging/`.
- **Frontend `/iceberg`** : `next build` réussi, route `/iceberg` servie en `HTTP 200`.
