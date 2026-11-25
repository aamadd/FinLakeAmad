-- FinLakeAmad: Trino + Hive Metastore external Parquet table DDL.
-- Note: Plain Hive external Parquet tables do not provide ACID guarantees.

CREATE SCHEMA IF NOT EXISTS amad_lake.finlake_curated;

CREATE TABLE IF NOT EXISTS amad_lake.finlake_curated.amad_transactions (
    ingest_row_id        BIGINT,
    step                 INTEGER,
    type                 VARCHAR,
    amount               DECIMAL(19,4),
    nameOrig             VARCHAR,
    oldbalanceOrg        DECIMAL(19,4),
    newbalanceOrig       DECIMAL(19,4),
    nameDest             VARCHAR,
    oldbalanceDest       DECIMAL(19,4),
    newbalanceDest       DECIMAL(19,4),
    isFraud              BOOLEAN,
    isFlaggedFraud       BOOLEAN,
    event_time_simulated TIMESTAMP,
    event_date           VARCHAR
)
WITH (
    format = 'PARQUET',
    external_location = 's3a://amad-lake/curated/paysim/amad_transactions/',
    partitioned_by = ARRAY['event_date']
);

CALL amad_lake.system.sync_partition_metadata('finlake_curated', 'amad_transactions', 'ADD');
