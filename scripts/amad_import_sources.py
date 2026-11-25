#!/usr/bin/env python3
"""FinLakeAmad Phase 1 — Bounded-memory import of PaySim CSV into SQL Server and PostgreSQL."""
from __future__ import annotations

import argparse
import csv
import os
import subprocess
import sys
import tempfile
from abc import ABC, abstractmethod
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CSV = ROOT / "data" / "raw" / "amad_paysim.csv"


def load_dotenv(path: Path) -> dict[str, str]:
    env: dict[str, str] = {}
    if not path.is_file():
        return env
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        k, v = line.split("=", 1)
        env[k.strip()] = v.strip()
    return env


def run(cmd: list[str], *, input_bytes: bytes | None = None) -> str:
    res = subprocess.run(cmd, input=input_bytes, capture_output=True, check=False)
    if res.returncode != 0:
        raise RuntimeError(
            f"Command failed ({res.returncode}): {' '.join(cmd)}\n"
            f"stdout: {res.stdout.decode(errors='replace')}\n"
            f"stderr: {res.stderr.decode(errors='replace')}"
        )
    return res.stdout.decode(errors="replace").strip()


class SourceLoader(ABC):
    """SOLID interface for loading bounded-memory CSV batches into a relational source."""

    name: str

    @abstractmethod
    def apply_schema(self) -> None: ...

    @abstractmethod
    def truncate(self) -> None: ...

    @abstractmethod
    def load_chunk(self, chunk_path: Path) -> None: ...

    @abstractmethod
    def verify_counts(self) -> tuple[int, int, int]: ...


class MssqlLoader(SourceLoader):
    name = "amad-mssql"

    def __init__(self, container: str, sa_password: str, schema_sql: Path) -> None:
        self.container = container
        self.sa_password = sa_password
        self.schema_sql = schema_sql

    def _sqlcmd(self, query: str | None = None, *, file_in_container: str | None = None) -> str:
        cmd = [
            "docker", "exec", self.container,
            "/opt/mssql-tools18/bin/sqlcmd", "-S", "localhost", "-U", "sa",
            "-P", self.sa_password, "-C", "-b",
        ]
        if file_in_container:
            cmd += ["-i", file_in_container]
        else:
            cmd += ["-d", "amad_source", "-h", "-1", "-W", "-Q", f"SET NOCOUNT ON; {query}"]
        return run(cmd)

    def apply_schema(self) -> None:
        run(["docker", "cp", str(self.schema_sql), f"{self.container}:/tmp/amad-mssql-schema.sql"])
        self._sqlcmd(file_in_container="/tmp/amad-mssql-schema.sql")

    def truncate(self) -> None:
        self._sqlcmd("TRUNCATE TABLE dbo.amad_transactions;")

    def load_chunk(self, chunk_path: Path) -> None:
        remote = f"/tmp/{chunk_path.name}"
        run(["docker", "cp", str(chunk_path), f"{self.container}:{remote}"])
        try:
            run([
                "docker", "exec", self.container,
                "/opt/mssql-tools18/bin/bcp", "amad_source.dbo.amad_transactions", "in", remote,
                "-S", "localhost", "-U", "sa", "-P", self.sa_password,
                "-u", "-c", "-t,", "-r\n", "-b", "50000",
            ])
        finally:
            run(["docker", "exec", "-u", "0", self.container, "rm", "-f", remote])

    def verify_counts(self) -> tuple[int, int, int]:
        out = self._sqlcmd("SELECT COUNT(*), ISNULL(MIN(ingest_row_id),0), ISNULL(MAX(ingest_row_id),0) FROM dbo.amad_transactions;")
        parts = out.splitlines()[-1].split()
        return int(parts[0]), int(parts[1]), int(parts[2])


class PostgresLoader(SourceLoader):
    name = "amad-postgres"

    def __init__(self, container: str, user: str, db: str, schema_sql: Path) -> None:
        self.container = container
        self.user = user
        self.db = db
        self.schema_sql = schema_sql

    def _psql(self, sql: str) -> str:
        return run(["docker", "exec", "-i", self.container, "psql", "-U", self.user, "-d", self.db, "-v", "ON_ERROR_STOP=1", "-tA", "-c", sql])

    def apply_schema(self) -> None:
        run(["docker", "exec", "-i", self.container, "psql", "-U", self.user, "-d", self.db, "-v", "ON_ERROR_STOP=1"],
            input_bytes=self.schema_sql.read_bytes())

    def truncate(self) -> None:
        self._psql("TRUNCATE TABLE public.amad_transactions;")

    def load_chunk(self, chunk_path: Path) -> None:
        remote = f"/tmp/{chunk_path.name}"
        run(["docker", "cp", str(chunk_path), f"{self.container}:{remote}"])
        try:
            self._psql(f"\\copy public.amad_transactions FROM '{remote}' WITH (FORMAT csv)")
        finally:
            run(["docker", "exec", "-u", "0", self.container, "rm", "-f", remote])

    def verify_counts(self) -> tuple[int, int, int]:
        out = self._psql("SELECT COUNT(*), COALESCE(MIN(ingest_row_id),0), COALESCE(MAX(ingest_row_id),0) FROM public.amad_transactions;")
        c, mn, mx = out.splitlines()[-1].split("|")
        return int(c), int(mn), int(mx)


def stream_csv_chunks(csv_path: Path, batch_size: int, tmp_dir: Path):
    """Yield bounded-memory chunk files with deterministic `ingest_row_id` prepended."""
    with csv_path.open("r", newline="", encoding="utf-8") as fin:
        reader = csv.reader(fin)
        next(reader)  # skip header
        row_id = 0
        chunk_idx = 0
        buf: list[list[str | int]] = []
        for row in reader:
            row_id += 1
            buf.append([row_id, *row])
            if len(buf) >= batch_size:
                chunk_idx += 1
                yield _flush_chunk(tmp_dir, chunk_idx, buf)
                buf.clear()
        if buf:
            chunk_idx += 1
            yield _flush_chunk(tmp_dir, chunk_idx, buf)


def _flush_chunk(tmp_dir: Path, idx: int, rows: list[list[str | int]]) -> Path:
    p = tmp_dir / f"amad_chunk_{idx:05d}.csv"
    with p.open("w", newline="\n", encoding="utf-8") as fout:
        csv.writer(fout, lineterminator="\n").writerows(rows)
    return p


def main() -> int:
    p = argparse.ArgumentParser(description="Import PaySim CSV into SQL Server and PostgreSQL")
    p.add_argument("--csv", type=Path, default=DEFAULT_CSV)
    p.add_argument("--batch-size", type=int, default=100_000)
    p.add_argument("--targets", nargs="+", choices=["mssql", "postgres"], default=["mssql", "postgres"])
    args = p.parse_args()

    if not args.csv.is_file():
        print(f"[ERROR] CSV not found: {args.csv}. Run scripts/amad_dataset.py first.", file=sys.stderr)
        return 1

    env = {**load_dotenv(ROOT / ".env"), **os.environ}
    loaders: list[SourceLoader] = []
    if "mssql" in args.targets:
        loaders.append(MssqlLoader("amad-mssql", env["AMAD_MSSQL_SA_PASSWORD"], ROOT / "sql" / "amad-mssql-schema.sql"))
    if "postgres" in args.targets:
        loaders.append(PostgresLoader("amad-postgres", env["AMAD_PG_USER"], env["AMAD_PG_DB"], ROOT / "sql" / "amad-postgres-schema.sql"))

    for loader in loaders:
        loader.apply_schema()
        loader.truncate()

    with tempfile.TemporaryDirectory(prefix="amad_import_") as td:
        tmp_dir = Path(td)
        for chunk in stream_csv_chunks(args.csv, args.batch_size, tmp_dir):
            for loader in loaders:
                loader.load_chunk(chunk)
            chunk.unlink()

    for loader in loaders:
        count, min_id, max_id = loader.verify_counts()
        print(f"[OK] {loader.name}: rows={count}, ingest_row_id=[{min_id}..{max_id}]")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
