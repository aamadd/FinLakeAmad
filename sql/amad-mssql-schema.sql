-- FinLakeAmad: SQL Server source schema for PaySim transactions.
IF DB_ID(N'amad_source') IS NULL
BEGIN
    CREATE DATABASE amad_source;
END
GO

USE amad_source;
GO

IF OBJECT_ID(N'dbo.amad_transactions', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.amad_transactions (
        ingest_row_id  BIGINT        NOT NULL,
        step           INT           NOT NULL,
        type           VARCHAR(20)   NOT NULL,
        amount         DECIMAL(19,4) NOT NULL,
        nameOrig       VARCHAR(64)   NOT NULL,
        oldbalanceOrg  DECIMAL(19,4) NOT NULL,
        newbalanceOrig DECIMAL(19,4) NOT NULL,
        nameDest       VARCHAR(64)   NOT NULL,
        oldbalanceDest DECIMAL(19,4) NOT NULL,
        newbalanceDest DECIMAL(19,4) NOT NULL,
        isFraud        BIT           NOT NULL,
        isFlaggedFraud BIT           NOT NULL,
        CONSTRAINT pk_amad_transactions PRIMARY KEY CLUSTERED (ingest_row_id)
    );
END
GO
