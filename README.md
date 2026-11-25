# FinLakeAmad

Local, Docker-Compose-only financial transaction data lake built on the synthetic **PaySim** mobile-money dataset
(see [`docs/CAHIER_DES_CHARGES.md`](docs/CAHIER_DES_CHARGES.md)).

```
PaySim CSV -> SQL Server / PostgreSQL -> amad-control-plane (Spring Boot 3.3.5, Java 21)
           -> CSV/Parquet -> MinIO (amad-lake) -> Hive Metastore -> Trino (catalog amad_lake) -> amad-console (Next.js 15)
```

## Status
| Phase | Scope | State |
|---|---|---|
| 0 | Repo skeleton, infrastructure stack (SQL Server, PostgreSQL, MinIO, Hive Metastore, Trino) | done |
| 1 | PaySim download, checksum, import into SQL Server + PostgreSQL (`ingest_row_id`) | done |
| 2 | `amad-control-plane`: sources, connection test, schema exploration | done |
| 3 | Ingestion engine: keyset batches, CSV/Parquet, staging -> curated, manifests | done |
| 4 | Catalog registration + Trino queries | planned |
| 5 | Jobs API (`/storage/jobs`), states, retry, cancel | planned |
| 6 | Benchmarks + report | planned |
| 7 | `amad-console` UI (optional per cahier §2.2) | planned |

## Quick start (Phase 0)
```powershell
Copy-Item .env.example .env   # then replace every CHANGE_ME
docker compose up -d --build
./scripts/amad-check-infra.ps1
```

| Service | Host port | Purpose |
|---|---|---|
| amad-mssql | 1433 | Primary PaySim source |
| amad-postgres | 5435 | Secondary relational source |
| amad-minio | 9100 / 9101 (console) | Object storage, bucket `amad-lake` |
| amad-metastore-db | 5436 | Hive Metastore persistence |
| amad-metastore | 9083 | Hive Metastore Thrift |
| amad-trino | 8088 | SQL engine, catalog `amad_lake` |

## Git workflow
`main` holds only verified phases. Each phase/feature is developed on `feature/amad-<topic>` and merged after its
acceptance checks pass.
