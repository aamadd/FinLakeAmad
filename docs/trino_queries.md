# FinLakeAmad — Trino, Hive Metastore & Apache Iceberg Queries (Phases 4 & P7)

Uploading Parquet objects to MinIO alone does **not** make them queryable in SQL: tables must be registered in `amad-metastore` (Hive Metastore) under Trino's `amad_lake` (Hive) or `amad_iceberg` (Iceberg) catalog. Plain Hive external Parquet tables do **not** provide ACID guarantees, whereas Apache Iceberg provides atomic snapshots, schema evolution, and time travel.

## 1. Hive External Parquet (`amad_lake`)
```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8000/catalog/register `
  -ContentType 'application/json' `
  -Body '{"schema":"finlake_curated","table":"amad_transactions","partitionedByEventDate":true}' | ConvertTo-Json
```

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8000/catalog/query `
  -ContentType 'application/json' `
  -Body '{"sql":"SELECT type, COUNT(*) AS cnt, ROUND(SUM(amount),2) AS amt FROM amad_lake.finlake_curated.amad_transactions GROUP BY type ORDER BY cnt DESC","limit":20}' | ConvertTo-Json -Depth 4
```

## 2. Apache Iceberg (`amad_iceberg`) — P7 Bonus

### Register & Ingest Batches (creating snapshots)
```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8000/catalog/iceberg/register `
  -ContentType 'application/json' `
  -Body '{"schema":"finlake_iceberg","table":"amad_transactions","partitionedByEventDate":true}' | ConvertTo-Json

Invoke-RestMethod -Method Post -Uri http://localhost:8000/catalog/iceberg/ingest `
  -ContentType 'application/json' `
  -Body '{"sourceSchema":"finlake_curated","sourceTable":"amad_transactions","targetSchema":"finlake_iceberg","targetTable":"amad_transactions","minIngestRowId":0,"maxIngestRowId":25000}' | ConvertTo-Json
```

### List Snapshots (`$snapshots`)
```powershell
Invoke-RestMethod -Uri "http://localhost:8000/catalog/iceberg/snapshots?schema=finlake_iceberg&table=amad_transactions" | ConvertTo-Json -Depth 4
```

### Schema Evolution (`ADD COLUMN`)
```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8000/catalog/iceberg/evolve-schema `
  -ContentType 'application/json' `
  -Body '{"schema":"finlake_iceberg","table":"amad_transactions","columnName":"amad_audit_tag","columnType":"VARCHAR"}' | ConvertTo-Json -Depth 4
```

### Time Travel (`FOR VERSION AS OF` / `FOR TIMESTAMP AS OF`)
```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8000/catalog/iceberg/time-travel `
  -ContentType 'application/json' `
  -Body '{"schema":"finlake_iceberg","table":"amad_transactions","snapshotId":1234567890}' | ConvertTo-Json -Depth 4
```

See [sql/amad-trino-ddl.sql](../sql/amad-trino-ddl.sql) and [sql/amad-analytical-queries.sql](../sql/amad-analytical-queries.sql).
