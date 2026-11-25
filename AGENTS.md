# FinLakeAmad — Agent Instructions

Source of truth: `docs/CAHIER_DES_CHARGES.md`. Naming not defined by the cahier uses the `amad` prefix.

## Workflow
1. Work phase by phase (see README status table), one `feature/amad-<topic>` branch per phase.
2. Before a phase: state scope, files, assumptions, acceptance checks.
3. Verify (build, tests, running stack) before claiming anything works; merge to `main` only when green.
4. Update docs whenever behavior, config, API, schema or architecture changes.

## Constraints
- Docker Compose only; Java 21 + Spring Boot 3.3.5; Next.js 15 + TypeScript; SQL Server; PostgreSQL; MinIO; Hive Metastore; Trino.
- No Kubernetes, Spark, Kafka, Airflow, cloud services or paid APIs.
- Secrets only in `.env` (gitignored); `.env.example` holds placeholders.
- PaySim is synthetic; never invent demographics, merchants, currencies or invoices. No ML fraud model.
- Bounded-memory extraction (keyset pagination on `ingest_row_id`, never OFFSET).

## Code principles
- SOLID: one module per feature (sources, ingestion, storage, catalog, jobs); depend on interfaces
  (e.g. `SourceConnector`, `ObjectStore`, `FileWriter`, `CatalogRegistrar`) so SQL Server/PostgreSQL and
  CSV/Parquet are interchangeable implementations.
- DRY: shared config, naming and error handling live in one `common` module; no copy-pasted per-source logic.
