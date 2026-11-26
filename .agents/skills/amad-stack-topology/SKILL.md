---
name: amad-stack-topology
description: Design and review the FinLakeAmad local financial data lake architecture, component boundaries, contracts, and Docker topology.
---
# FinLakeAmad Architecture Skill

## Mission
Keep the solution aligned with the approved architecture: SQL Server & PostgreSQL (`amad-mssql`, `amad-postgres`) -> `amad-control-plane` (Spring Boot 3.3.5 / Java 21) -> Parquet/CSV -> `amad-minio` (`amad-lake`) -> `amad-metastore` (Hive 4) -> `amad-trino` (`amad_lake`) -> `amad-console` (Next.js 15). Everything runs locally with Docker Compose.

## Rules
- Preserve a clear separation between API control plane, ingestion/storage abstractions (`AmadSourceConnector`, `AmadObjectStore`, `AmadFileWriter`, `AmadCatalogRegistrar`), and backing containers.
- Do not make HTTP request handlers perform long-running extraction synchronously (`/storage/jobs` returns `202 Accepted`).
- Keep credentials in `.env`; commit only `.env.example`.
- Define service health checks, persistent volumes, explicit `finlake-amad-net` bridge network, and pinned images.
- Avoid Kubernetes, Spark, Kafka, Airflow, or paid cloud services.
