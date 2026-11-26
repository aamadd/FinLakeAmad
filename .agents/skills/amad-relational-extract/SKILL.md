---
name: amad-relational-extract
description: Implement safe, bounded-memory SQL Server and PostgreSQL discovery and batch extraction for FinLakeAmad.
---
# FinLakeAmad Relational Ingestion Skill

## Mission
Implement source discovery, 4-step connection probing, schema inspection, and restartable bounded-memory batch extraction across `amad-mssql` and `amad-postgres`.

## Rules
- Never concatenate untrusted identifiers into SQL. Validate schema/table names via `SqlIdentifierValidator` and quote identifiers per dialect (`AmadSourceConnector.quoteIdentifier`).
- Use parameterized `PreparedStatement` values for all data values.
- Extract in deterministic chunks using keyset pagination (`WHERE ingest_row_id > ? ORDER BY ingest_row_id ASC`); never use `OFFSET` pagination on large tables.
- Keep memory bounded; write each batch to a staging object before advancing.
- Make retries idempotent using a run-specific `staging/<run_id>/` prefix and promote only after completion.
- Never log passwords, JDBC credentials, or raw financial rows.
