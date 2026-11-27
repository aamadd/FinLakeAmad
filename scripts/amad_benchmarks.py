#!/usr/bin/env python3
"""FinLakeAmad Phase 6 & P7 Bonus — Reproducible storage, partitioning, compression, Hive vs Iceberg, and Trino query benchmarks."""
from __future__ import annotations

import argparse
import csv
import http.client
import json
import os
import platform
import statistics
import subprocess
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RESULTS_DIR = ROOT / "benchmarks" / "results"
REPORT_PATH = ROOT / "docs" / "benchmark_report.md"


@dataclass
class ExperimentSpec:
    name: str
    source_id: str
    schema: str
    table: str
    fmt: str
    compression: str
    batch_size: int
    partition_by: list[str]
    destination_prefix: str


def api_request(base_url: str, method: str, path: str, body: dict | None = None) -> dict:
    data = json.dumps(body).encode("utf-8") if body is not None else None
    headers = {"Content-Type": "application/json"} if body is not None else {}
    for attempt in range(4):
        req = urllib.request.Request(f"{base_url}{path}", data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=180) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            raise RuntimeError(f"HTTP {exc.code} on {method} {path}: {exc.read().decode(errors='replace')}") from exc
        except (ConnectionResetError, urllib.error.URLError, TimeoutError, http.client.HTTPException):
            if attempt == 3:
                raise
            time.sleep(1.0)
    raise RuntimeError(f"Unreachable retry loop on {method} {path}")


def run_job(base_url: str, spec: ExperimentSpec, max_rows: int) -> tuple[dict, float]:
    t0 = time.perf_counter()
    job = api_request(base_url, "POST", "/storage/jobs", {
        "sourceId": spec.source_id,
        "schema": spec.schema,
        "table": spec.table,
        "format": spec.fmt,
        "compression": spec.compression,
        "batchSize": spec.batch_size,
        "partitionBy": spec.partition_by,
        "destinationPrefix": spec.destination_prefix,
        "maxRows": max_rows,
        "idempotent": True,
    })
    job_id = job["jobId"]
    while True:
        status = api_request(base_url, "GET", f"/storage/jobs/{job_id}")
        if status["status"] == "SUCCEEDED":
            break
        if status["status"] in ("FAILED", "CANCELLED"):
            raise RuntimeError(f"Job {job_id} ended with {status['status']}: {status.get('errorMessage')}")
        time.sleep(1.0)
    elapsed = time.perf_counter() - t0
    manifest = api_request(base_url, "GET", f"/storage/jobs/{job_id}/result")
    return manifest, elapsed


def benchmark_trino(base_url: str, full_table: str, trials: int, query_endpoint: str = "/catalog/query") -> dict[str, dict[str, float]]:
    queries = {
        "full_count": f"SELECT COUNT(*) FROM {full_table}",
        "time_filter": f"SELECT COUNT(*) FROM {full_table} WHERE event_date = '2026-01-01' AND amount > 10000",
        "type_fraud_agg": f"SELECT type, isFraud, COUNT(*) AS cnt, ROUND(SUM(amount), 2) AS amt FROM {full_table} GROUP BY type, isFraud",
    }
    stats: dict[str, dict[str, float]] = {}
    for qname, sql in queries.items():
        api_request(base_url, "POST", query_endpoint, {"sql": sql, "limit": 100})
        latencies_ms: list[float] = []
        for _ in range(trials):
            t0 = time.perf_counter()
            api_request(base_url, "POST", query_endpoint, {"sql": sql, "limit": 100})
            latencies_ms.append((time.perf_counter() - t0) * 1000.0)
        stats[qname] = {
            "median_ms": round(statistics.median(latencies_ms), 2),
            "min_ms": round(min(latencies_ms), 2),
            "max_ms": round(max(latencies_ms), 2),
        }
    return stats


def run_iceberg_experiment(base_url: str, max_rows: int, trials: int, base_hive_row: dict) -> dict:
    iceberg_table = f"amad_transactions_bench_{int(time.time())}"
    t0 = time.perf_counter()
    api_request(base_url, "POST", "/catalog/iceberg/register", {
        "schema": "finlake_iceberg",
        "table": iceberg_table,
        "location": f"s3a://amad-lake/iceberg/{iceberg_table}/",
        "partitionedByEventDate": True,
    })
    ingest_resp = api_request(base_url, "POST", "/catalog/iceberg/ingest", {
        "sourceSchema": "finlake_bench_mssql_pq_zstd_part_b10k",
        "sourceTable": "amad_transactions",
        "targetSchema": "finlake_iceberg",
        "targetTable": iceberg_table,
        "minIngestRowId": 0,
        "maxIngestRowId": max_rows,
    })
    iceberg_write_s = time.perf_counter() - t0
    qstats = benchmark_trino(
        base_url,
        f"amad_iceberg.finlake_iceberg.{iceberg_table}",
        trials,
        query_endpoint="/catalog/iceberg/query",
    )
    return {
        "name": "mssql-iceberg-zstd-part",
        "source_id": "amad-mssql (via Hive Parquet)",
        "format": "ICEBERG_PARQUET",
        "compression": "ZSTD",
        "partition_by": ["event_date"],
        "batch_size": base_hive_row["batch_size"],
        "rows": ingest_resp["totalRows"],
        "ingest_seconds": round(iceberg_write_s, 3),
        "object_count": base_hive_row["object_count"],
        "total_bytes": base_hive_row["total_bytes"],
        "trino_count_median_ms": qstats["full_count"]["median_ms"],
        "trino_filter_median_ms": qstats["time_filter"]["median_ms"],
        "trino_agg_median_ms": qstats["type_fraud_agg"]["median_ms"],
    }


def collect_environment() -> dict:
    docker_ver = subprocess.run(["docker", "--version"], capture_output=True, text=True).stdout.strip()
    manifest_path = ROOT / "data" / "raw" / "dataset_manifest.json"
    dataset_sha = "unknown"
    if manifest_path.is_file():
        dataset_sha = json.loads(manifest_path.read_text(encoding="utf-8")).get("sha256", "unknown")
    return {
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "os": platform.platform(),
        "python": platform.python_version(),
        "cpu_count": os.cpu_count(),
        "docker": docker_ver,
        "dataset_sha256": dataset_sha,
    }


def write_report(env: dict, rows: list[dict], max_rows: int, trials: int) -> None:
    lines = [
        "# FinLakeAmad — Benchmark Report",
        "",
        "## Environment & Methodology",
        f"- **Generated (UTC)**: `{env['timestamp_utc']}`",
        f"- **Host**: `{env['os']}` (`{env['cpu_count']}` logical CPUs)",
        f"- **Docker**: `{env['docker']}`",
        f"- **Dataset SHA-256**: `{env['dataset_sha256']}`",
        f"- **Rows per run**: `{max_rows:,}` identical rows (`ingest_row_id` 1..{max_rows:,})",
        f"- **Trino trials**: 1 warm-up + `{trials}` measured trials per query (median reported)",
        "",
        "## Results",
        "",
        "| Experiment | Source | Format | Codec | Partition | Batch | Ingest (s) | Objects | Total Bytes | Avg Obj (KB) | Trino Count (ms) | Trino Filter (ms) | Trino Agg (ms) |",
        "|---|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|",
    ]
    for r in rows:
        part = ",".join(r["partition_by"]) if r["partition_by"] else "none"
        avg_kb = round((r["total_bytes"] / max(r["object_count"], 1)) / 1024.0, 1)
        lines.append(
            f"| {r['name']} | {r['source_id']} | {r['format']} | {r['compression']} | {part} | "
            f"{r['batch_size']} | {r['ingest_seconds']:.2f} | {r['object_count']} | {r['total_bytes']:,} | {avg_kb} | "
            f"{r['trino_count_median_ms'] or '-'} | {r['trino_filter_median_ms'] or '-'} | {r['trino_agg_median_ms'] or '-'} |"
        )

    hive_row = next((r for r in rows if r["name"] == "mssql-pq-zstd-part-b10k"), None)
    iceberg_row = next((r for r in rows if r["name"] == "mssql-iceberg-zstd-part"), None)
    if hive_row and iceberg_row:
        lines += [
            "",
            "## Hive External Parquet vs Apache Iceberg",
            "",
            "| Criterion | Hive External Parquet (`amad_lake`) | Apache Iceberg (`amad_iceberg`) |",
            "|---|---|---|",
            f"| `SELECT COUNT(*)` median (ms) | {hive_row['trino_count_median_ms']} | {iceberg_row['trino_count_median_ms']} |",
            f"| Time-filter median (ms) | {hive_row['trino_filter_median_ms']} | {iceberg_row['trino_filter_median_ms']} |",
            f"| Type/Fraud aggregation median (ms) | {hive_row['trino_agg_median_ms']} | {iceberg_row['trino_agg_median_ms']} |",
            "| ACID & Snapshot Isolation | No (`sync_partition_metadata` required) | Yes (atomic snapshot commits) |",
            "| Schema Evolution (`ADD COLUMN`) | Manual external DDL coordination | Native metadata-only evolution |",
            "| Time Travel (`FOR VERSION/TIMESTAMP AS OF`) | Not supported | Native via Iceberg snapshots |",
        ]

    lines += [
        "",
        "## Reproduction Command",
        "```powershell",
        f"python scripts/amad_benchmarks.py --max-rows {max_rows} --trials {trials}",
        "```",
        "",
    ]
    REPORT_PATH.write_text("\n".join(lines), encoding="utf-8")


def main() -> int:
    p = argparse.ArgumentParser(description="Run reproducible FinLakeAmad benchmarks")
    p.add_argument("--api", default="http://localhost:8000")
    p.add_argument("--max-rows", type=int, default=50_000)
    p.add_argument("--trials", type=int, default=3)
    args = p.parse_args()

    matrix = [
        ExperimentSpec("mssql-csv-none", "amad-mssql", "dbo", "amad_transactions", "CSV", "NONE", 10_000, [], "bench_csv"),
        ExperimentSpec("mssql-pq-none-nopart", "amad-mssql", "dbo", "amad_transactions", "PARQUET", "NONE", 10_000, [], "bench_pq_none"),
        ExperimentSpec("mssql-pq-snappy-part", "amad-mssql", "dbo", "amad_transactions", "PARQUET", "SNAPPY", 10_000, ["event_date"], "bench_pq_snappy"),
        ExperimentSpec("mssql-pq-zstd-part-b10k", "amad-mssql", "dbo", "amad_transactions", "PARQUET", "ZSTD", 10_000, ["event_date"], "bench_pq_zstd_10k"),
        ExperimentSpec("mssql-pq-zstd-part-b50k", "amad-mssql", "dbo", "amad_transactions", "PARQUET", "ZSTD", 50_000, ["event_date"], "bench_pq_zstd_50k"),
        ExperimentSpec("pg-pq-zstd-part-b10k", "amad-postgres", "public", "amad_transactions", "PARQUET", "ZSTD", 10_000, ["event_date"], "bench_pg_zstd"),
    ]

    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    env = collect_environment()
    records: list[dict] = []

    for spec in matrix:
        manifest, ingest_s = run_job(args.api, spec, args.max_rows)
        row = {
            "name": spec.name,
            "source_id": spec.source_id,
            "format": spec.fmt,
            "compression": spec.compression,
            "partition_by": spec.partition_by,
            "batch_size": spec.batch_size,
            "rows": manifest["rowCount"],
            "ingest_seconds": round(ingest_s, 3),
            "object_count": len(manifest["objects"]),
            "total_bytes": manifest["totalBytes"],
            "trino_count_median_ms": None,
            "trino_filter_median_ms": None,
            "trino_agg_median_ms": None,
        }
        if spec.fmt == "PARQUET":
            schema_name = "finlake_bench_" + spec.name.replace("-", "_")
            loc = f"s3a://amad-lake/{spec.destination_prefix}/paysim/{spec.table}/"
            api_request(args.api, "POST", "/catalog/register", {
                "schema": schema_name,
                "table": spec.table,
                "location": loc,
                "partitionedByEventDate": bool(spec.partition_by),
            })
            qstats = benchmark_trino(args.api, f"amad_lake.{schema_name}.{spec.table}", args.trials)
            row["trino_count_median_ms"] = qstats["full_count"]["median_ms"]
            row["trino_filter_median_ms"] = qstats["time_filter"]["median_ms"]
            row["trino_agg_median_ms"] = qstats["type_fraud_agg"]["median_ms"]
        print(f"[OK] {spec.name}: ingest={row['ingest_seconds']}s bytes={row['total_bytes']} trino_agg={row['trino_agg_median_ms']}ms")
        records.append(row)

    base_hive_row = next(r for r in records if r["name"] == "mssql-pq-zstd-part-b10k")
    iceberg_row = run_iceberg_experiment(args.api, args.max_rows, args.trials, base_hive_row)
    print(f"[OK] {iceberg_row['name']}: write={iceberg_row['ingest_seconds']}s trino_agg={iceberg_row['trino_agg_median_ms']}ms")
    records.append(iceberg_row)

    json_path = RESULTS_DIR / "amad_benchmark_results.json"
    csv_path = RESULTS_DIR / "amad_benchmark_results.csv"
    json_path.write_text(json.dumps({"environment": env, "results": records}, indent=2) + "\n", encoding="utf-8")
    with csv_path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(records[0].keys()))
        w.writeheader()
        w.writerows(records)
    write_report(env, records, args.max_rows, args.trials)
    print(f"[OK] Wrote {json_path}, {csv_path}, and {REPORT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
