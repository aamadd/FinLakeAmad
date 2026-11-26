---
name: amad-control-jobs
description: Build FinLakeAmad REST endpoints and reliable asynchronous extraction job lifecycle.
---
# FinLakeAmad API & Job Orchestration Skill

## Required endpoints
- `GET /`, `GET /health`
- `GET /sources`, `POST /sources`, `POST /sources/probe`, `POST /sources/{id}/test`
- `GET /sources/{id}/tables`, `GET /sources/{id}/tables/{table}/schema`
- `POST /storage/jobs`, `GET /storage/jobs`, `GET /storage/jobs/{id}`, `GET /storage/jobs/{id}/result`, `POST /storage/jobs/{id}/retry`, `POST /storage/jobs/{id}/cancel`
- `POST /catalog/register`, `GET /catalog/tables`, `POST /catalog/query`

## Rules
- Use Spring Boot 3.3.5 + Jakarta Validation + `springdoc-openapi` (`/docs`, `/openapi.json`).
- `POST /storage/jobs` returns `202 Accepted` and runs asynchronously via `AmadJobOrchestrator`.
- Persist job state (`PENDING`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCELLED`) in `amad-postgres` (`public.amad_storage_jobs`).
- Never leak credentials or stack traces from `AmadExceptionHandler`.
