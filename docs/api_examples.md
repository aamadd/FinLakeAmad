# FinLakeAmad — REST API Examples (Phase 2)

OpenAPI JSON: `http://localhost:8000/openapi.json`  
Swagger UI: `http://localhost:8000/docs`

```powershell
# 1. Health check (SQL Server, PostgreSQL, MinIO, Trino)
Invoke-RestMethod http://localhost:8000/health | ConvertTo-Json -Depth 5

# 2. List configured sources
Invoke-RestMethod http://localhost:8000/sources | ConvertTo-Json

# 3. Run 4-step connectivity test on SQL Server and PostgreSQL
Invoke-RestMethod -Method Post http://localhost:8000/sources/amad-mssql/test | ConvertTo-Json -Depth 5
Invoke-RestMethod -Method Post http://localhost:8000/sources/amad-postgres/test | ConvertTo-Json -Depth 5

# 4. List tables and inspect schema
Invoke-RestMethod http://localhost:8000/sources/amad-mssql/tables | ConvertTo-Json
Invoke-RestMethod "http://localhost:8000/sources/amad-mssql/tables/amad_transactions/schema?schema=dbo" | ConvertTo-Json -Depth 5
Invoke-RestMethod "http://localhost:8000/sources/amad-postgres/tables/amad_transactions/schema?schema=public" | ConvertTo-Json -Depth 5

# 5. Submit async storage job (202 Accepted), poll status, read result
$job = Invoke-RestMethod -Method Post -Uri http://localhost:8000/storage/jobs -ContentType 'application/json' -Body '{"sourceId":"amad-mssql","schema":"dbo","table":"amad_transactions","format":"PARQUET","compression":"ZSTD","batchSize":10000,"partitionBy":["event_date"],"maxRows":20000}'
Invoke-RestMethod "http://localhost:8000/storage/jobs/$($job.jobId)" | ConvertTo-Json
Invoke-RestMethod "http://localhost:8000/storage/jobs/$($job.jobId)/result" | ConvertTo-Json -Depth 4
```
