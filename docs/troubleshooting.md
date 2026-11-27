# FinLakeAmad — Guide de dépannage (`troubleshooting.md`)

Ce guide recense les diagnostics et résolutions courants pour la stack locale **FinLakeAmad** (Docker Compose).

---

## 1. Vérification globale de la stack

Exécuter le script de santé :
```powershell
./scripts/amad-check-infra.ps1
```
Ce script vérifie la santé des 6 services d'infrastructure (`amad-mssql`, `amad-postgres`, `amad-minio`, `amad-metastore-db`, `amad-metastore`, `amad-trino`), le bucket `amad-lake`, ainsi que la présence des catalogues `amad_lake` (Hive) et `amad_iceberg` (Iceberg) dans Trino.

---

## 2. Diagnostics par service

### `amad-mssql` (SQL Server 2022)
- **Symptôme** : `Connection refused` ou échec du healthcheck.
- **Cause fréquente** : Mot de passe `AMAD_MSSQL_SA_PASSWORD` non conforme à la politique SQL Server (complexité insuffisante) ou démarrage initial encore en cours (`start_period: 30s`).
- **Diagnostic** :
  ```powershell
  docker logs --tail 50 amad-mssql
  docker exec amad-mssql /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$env:AMAD_MSSQL_SA_PASSWORD" -C -Q "SELECT COUNT(*) FROM amad_source.dbo.amad_transactions"
  ```

### `amad-postgres` (PostgreSQL 16)
- **Symptôme** : Erreur d'authentification ou table absente.
- **Diagnostic** :
  ```powershell
  docker exec amad-postgres pg_isready -U amad_source -d amad_source
  ```
- **Résolution** : Vérifier `.env` (`AMAD_PG_USER`, `AMAD_PG_PASSWORD`, `AMAD_PG_DB`) et relancer `python scripts/amad_import_sources.py` si les tables sources n'ont pas encore été chargées.

### `amad-minio` & `amad-minio-init`
- **Symptôme** : `AccessDenied` ou `NoSuchBucket: amad-lake`.
- **Diagnostic** :
  ```powershell
  docker logs amad-minio-init
  ```
- **Résolution** : S'assurer que `AMAD_MINIO_ROOT_USER` et `AMAD_MINIO_ROOT_PASSWORD` sont identiques dans `.env` pour `amad-minio`, `amad-metastore`, `amad-trino` et `amad-control-plane`. En cas de fichiers temporaires résiduels sous `staging/`, appeler :
  ```powershell
  Invoke-RestMethod -Method Post -Uri http://localhost:8000/ingestion/cleanup-staging
  ```

### `amad-metastore` (Hive Metastore 4.0.0)
- **Symptôme** : Trino échoue avec `Failed to connect to metastore` ou `java.net.ConnectException: amad-metastore:9083`.
- **Diagnostic** :
  ```powershell
  docker logs --tail 80 amad-metastore
  ```
- **Résolution** : Vérifier que `amad-metastore-db` est `healthy` et que le schéma PostgreSQL du metastore a bien été initialisé au premier démarrage.

### `amad-trino` (Trino 452 — `amad_lake` & `amad_iceberg`)
- **Symptôme** : Catalogue `amad_iceberg` absent de `SHOW CATALOGS`.
- **Cause fréquente** : Le fichier `infra/trino/catalog/amad_iceberg.properties` a été ajouté après le démarrage du conteneur `amad-trino`.
- **Résolution** : Redémarrer Trino pour charger les nouveaux catalogues montés en lecture seule :
  ```powershell
  docker compose restart amad-trino
  ```
- **Symptôme** : `SELECT COUNT(*)` retourne `0` sur une table Hive partitionnée (`amad_lake`).
- **Résolution** : Les partitions Hive externes doivent être synchronisées après écriture MinIO via `CALL amad_lake.system.sync_partition_metadata('<schema>', '<table>', 'ADD')` (effectué automatiquement par `POST /catalog/register`).
- **Symptôme** : Erreur de précision `TIMESTAMP` lors d'un `INSERT` Hive -> Iceberg.
- **Résolution** : Trino Iceberg utilise `TIMESTAMP(6)` ; utiliser `CAST(event_time_simulated AS TIMESTAMP(6))` (géré automatiquement par `POST /catalog/iceberg/ingest`).

### `amad-control-plane` (Spring Boot 3.3.5 / Java 21)
- **Symptôme** : Échec de chargement JNI DuckDB (`UnsatisfiedLinkError`).
- **Cause** : Image JRE musl (Alpine) au lieu de glibc (`eclipse-temurin:21-jre`).
- **Résolution** : Toujours utiliser `Dockerfile.backend` basé sur `eclipse-temurin:21-jre` (glibc).
- **Symptôme** : Job bloqué ou échoué avec erreur SQL/MinIO.
- **Diagnostic** :
  ```powershell
  Invoke-RestMethod -Uri http://localhost:8000/storage/jobs | ConvertTo-Json -Depth 4
  docker logs --tail 100 amad-control-plane
  ```
