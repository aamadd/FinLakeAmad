-- FinLakeAmad: Analytical SQL queries for Trino (filters, aggregations, sorting, joins, EXPLAIN).

-- 1. Discovery & count
SHOW SCHEMAS FROM amad_lake;
SHOW TABLES FROM amad_lake.finlake_curated;
DESCRIBE amad_lake.finlake_curated.amad_transactions;
SELECT COUNT(*) AS total_rows FROM amad_lake.finlake_curated.amad_transactions;

-- 2. Aggregation by transaction type & simulated fraud label (no currency assumed)
SELECT
    type,
    isFraud,
    COUNT(*) AS tx_count,
    ROUND(SUM(amount), 2) AS total_amount,
    ROUND(AVG(amount), 2) AS avg_amount
FROM amad_lake.finlake_curated.amad_transactions
GROUP BY type, isFraud
ORDER BY total_amount DESC;

-- 3. Partition-pruned filter + top transactions
SELECT
    ingest_row_id,
    event_time_simulated,
    type,
    amount,
    nameOrig,
    nameDest
FROM amad_lake.finlake_curated.amad_transactions
WHERE event_date = '2026-01-01'
  AND type IN ('TRANSFER', 'CASH_OUT')
ORDER BY amount DESC
LIMIT 20;

-- 4. Derived account activity join (strictly observed PaySim IDs, zero invented KYC)
WITH senders AS (
    SELECT nameOrig AS account_id, COUNT(*) AS sent_count, SUM(amount) AS sent_total
    FROM amad_lake.finlake_curated.amad_transactions
    GROUP BY nameOrig
),
receivers AS (
    SELECT nameDest AS account_id, COUNT(*) AS recv_count, SUM(amount) AS recv_total
    FROM amad_lake.finlake_curated.amad_transactions
    GROUP BY nameDest
)
SELECT
    s.account_id,
    s.sent_count,
    r.recv_count,
    s.sent_total,
    r.recv_total
FROM senders s
JOIN receivers r ON s.account_id = r.account_id
ORDER BY s.sent_total DESC
LIMIT 20;

-- 5. Partition pruning verification
EXPLAIN
SELECT COUNT(*)
FROM amad_lake.finlake_curated.amad_transactions
WHERE event_date = '2026-01-01';
