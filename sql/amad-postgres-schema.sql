-- FinLakeAmad: PostgreSQL source schema for PaySim transactions.
-- Unquoted column names match SQL Server case-insensitively when queried without quotes.
CREATE TABLE IF NOT EXISTS public.amad_transactions (
    ingest_row_id  BIGINT         PRIMARY KEY,
    step           INTEGER        NOT NULL,
    type           VARCHAR(20)    NOT NULL,
    amount         NUMERIC(19,4)  NOT NULL,
    nameOrig       VARCHAR(64)    NOT NULL,
    oldbalanceOrg  NUMERIC(19,4)  NOT NULL,
    newbalanceOrig NUMERIC(19,4)  NOT NULL,
    nameDest       VARCHAR(64)    NOT NULL,
    oldbalanceDest NUMERIC(19,4)  NOT NULL,
    newbalanceDest NUMERIC(19,4)  NOT NULL,
    isFraud        SMALLINT       NOT NULL,
    isFlaggedFraud SMALLINT       NOT NULL
);
