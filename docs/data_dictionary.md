# FinLakeAmad — PaySim Data Dictionary

Source: **PaySim** synthetic mobile-money simulator (`PS_20174392719_1491204439457_log.csv` or bounded local sample).

## Guardrails
- **Synthetic only**: `nameOrig` and `nameDest` are pseudonymous simulation IDs, never real customer/merchant profiles.
- **No invented KYC/currencies**: Do not attach currency codes (MAD/EUR/USD) or customer demographics not present in PaySim.
- **Simulated time**: `step` is a 1-hour simulation tick (`1..744`). Any derived timestamp (`event_time_simulated`, `event_date`) must be labeled as simulated.
- **Fraud label caveat**: Cancelled fraud transactions cause post-transaction balance fields to leak the cancellation outcome; never use `isFraud`/`isFlaggedFraud` or leaked balances as ML features in this project.

## Columns (`dbo.amad_transactions` / `public.amad_transactions`)

| Column | SQL Server | PostgreSQL | Description |
|---|---|---|---|
| `ingest_row_id` | `BIGINT` (PK) | `BIGINT` (PK) | Technical deterministic import sequence (`1..N`) for `O(1)` keyset pagination; not a source business key |
| `step` | `INT` | `INTEGER` | Simulated hour index (`1..744`) |
| `type` | `VARCHAR(20)` | `VARCHAR(20)` | `CASH_IN`, `CASH_OUT`, `DEBIT`, `PAYMENT`, `TRANSFER` |
| `amount` | `DECIMAL(19,4)` | `NUMERIC(19,4)` | Simulated transaction amount (unitless) |
| `nameOrig` | `VARCHAR(64)` | `VARCHAR(64)` | Pseudonymous origin account ID |
| `oldbalanceOrg` | `DECIMAL(19,4)` | `NUMERIC(19,4)` | Origin balance before transaction |
| `newbalanceOrig` | `DECIMAL(19,4)` | `NUMERIC(19,4)` | Origin balance after transaction |
| `nameDest` | `VARCHAR(64)` | `VARCHAR(64)` | Pseudonymous destination account ID |
| `oldbalanceDest` | `DECIMAL(19,4)` | `NUMERIC(19,4)` | Destination balance before transaction |
| `newbalanceDest` | `DECIMAL(19,4)` | `NUMERIC(19,4)` | Destination balance after transaction |
| `isFraud` | `BIT` | `SMALLINT` | Synthetic fraud label (`0`/`1`) |
| `isFlaggedFraud` | `BIT` | `SMALLINT` | Synthetic rule flag (`0`/`1`) |
