---
name: amad-sql-catalog
description: Configure Hive Metastore (`amad-metastore`) and Trino (`amad-trino`) to query FinLakeAmad objects in MinIO.
---
# FinLakeAmad Trino & Hive Catalog Skill

## Rules
- Configure `amad-metastore` (`infra/hive-metastore/`) and `amad-trino` (`infra/trino/catalog/amad_lake.properties`) consistently using `s3a://amad-lake/`.
- Register external Parquet tables with `TrinoHiveCatalogRegistrar` (`CREATE TABLE IF NOT EXISTS ... WITH (format='PARQUET', partitioned_by=ARRAY['event_date'])`) and sync partitions via `system.sync_partition_metadata`.
- Verify discovery, aggregations, joins, and `EXPLAIN` partition pruning.
- Plain Hive external Parquet tables do not provide ACID guarantees.
