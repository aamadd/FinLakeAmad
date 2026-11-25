# FinLakeAmad — Architecture

```mermaid
flowchart LR
    CSV["PaySim CSV"] --> MSSQL["amad-mssql<br/>SQL Server 2022"]
    CSV --> PG["amad-postgres<br/>PostgreSQL 16"]
    MSSQL --> CP["amad-control-plane<br/>Spring Boot 3.3.5 (phase 2+)"]
    PG --> CP
    CP -->|"CSV / Parquet"| MINIO["amad-minio<br/>bucket amad-lake"]
    CP -->|"register tables"| HMS["amad-metastore<br/>Hive 4.0.0"]
    HMS --> HMSDB["amad-metastore-db<br/>PostgreSQL 16"]
    TRINO["amad-trino<br/>catalog amad_lake"] --> HMS
    TRINO --> MINIO
    UI["amad-console<br/>Next.js 15 (phase 7)"] --> CP
```

## Phase 0 decisions
| Decision | Reason |
|---|---|
| Custom metastore image (`infra/hive-metastore/Dockerfile`) downloading the PostgreSQL JDBC, `hadoop-aws` and AWS SDK jars at build time | `apache/hive:4.0.0` ships none of them; no binaries committed |
| `${env.VAR}` in `metastore-site.xml`, `${ENV:VAR}` in Trino catalog | Keeps every credential in `.env` |
| `alpine/minio:RELEASE.2025-10-15T17-29-55Z` | Official `minio/minio` release tags are no longer pullable |
| Bucket bootstrap with `amazon/aws-cli:2.17.0` | MinIO image has no `mc`; S3 API is enough |
| Trino native S3 filesystem (`fs.native-s3.enabled`) | Legacy `hive.s3.*` properties are deprecated in Trino 452 |
| All images pinned, all services healthchecked, named volumes, single bridge network `finlake-amad-net` | Reproducibility (cahier §9) |

## Storage layout (cahier §8)
```
s3://amad-lake/
  raw/paysim/transactions/ingest_date=YYYY-MM-DD/run_id=<uuid>/...
  staging/<run_id>/...
  curated/paysim/transactions/event_date=YYYY-MM-DD/...
  manifests/paysim/transactions/<run_id>.json
```
