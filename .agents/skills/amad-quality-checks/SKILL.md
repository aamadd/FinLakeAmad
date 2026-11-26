---
name: amad-quality-checks
description: Validate source data, ingestion outputs, API contracts, and analytical query correctness for FinLakeAmad.
---
# FinLakeAmad Data Quality & Testing Skill

## Rules
- Validate PaySim CSV via `scripts/amad_dataset.py` (11 columns, allowed types, quarantine malformed rows, record SHA-256).
- Verify dual-source row counts and `ingest_row_id` continuity in `amad-mssql` and `amad-postgres`.
- Run `mvn test` inside `Dockerfile.backend` build (`SqlIdentifierValidatorTest`, `SourceConnectorTest`, `IngestionAndStorageTest`, `CatalogRegistrarTest`, `JobOrchestratorTest`).
- Verify infrastructure health with `scripts/amad-check-infra.ps1`.
