# FinLakeAmad — Trino & Hive Metastore Queries (Phase 4)

Uploading Parquet objects to MinIO alone does **not** make them queryable in SQL: tables must be registered in `amad-metastore` (Hive Metastore) under Trino's `amad_lake` catalog. Plain Hive external Parquet tables do **not** provide ACID guarantees.

## Register via REST API
```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8000/catalog/register `
  -ContentType 'application/json' `
  -Body '{"schema":"finlake_curated","table":"amad_transactions","partitionedByEventDate":true}' | ConvertTo-Json
```

## Query via REST API or Trino CLI
```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8000/catalog/query `
  -ContentType 'application/json' `
  -Body '{"sql":"SELECT type, COUNT(*) AS cnt, ROUND(SUM(amount),2) AS amt FROM amad_lake.finlake_curated.amad_transactions GROUP BY type ORDER BY cnt DESC","limit":20}' | ConvertTo-Json -Depth 4
```

See [sql/amad-trino-ddl.sql](../sql/amad-trino-ddl.sql) and [sql/amad-analytical-queries.sql](../sql/amad-analytical-queries.sql).
