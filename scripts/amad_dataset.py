#!/usr/bin/env python3
"""FinLakeAmad Phase 1 — Stage, generate (sample), checksum, and validate PaySim CSV."""
from __future__ import annotations

import argparse
import csv
import hashlib
import json
import random
import shutil
import sys
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RAW_DIR = ROOT / "data" / "raw"
DEFAULT_CSV = RAW_DIR / "amad_paysim.csv"
CHECKSUM_FILE = RAW_DIR / "checksums.sha256"
MANIFEST_FILE = RAW_DIR / "dataset_manifest.json"
QUARANTINE_FILE = RAW_DIR / "quarantine_malformed.csv"

EXPECTED_COLUMNS = [
    "step", "type", "amount", "nameOrig", "oldbalanceOrg", "newbalanceOrig",
    "nameDest", "oldbalanceDest", "newbalanceDest", "isFraud", "isFlaggedFraud",
]
ALLOWED_TYPES = {"CASH_IN", "CASH_OUT", "DEBIT", "PAYMENT", "TRANSFER"}
OFFICIAL_PAYSIM_SHA256 = "16910f90577b0d981bf8ff289714510bb89bc71bff7d3f220f024e287e4eea6b"


def sha256_file(path: Path, chunk_size: int = 8 * 1024 * 1024) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        while chunk := f.read(chunk_size):
            h.update(chunk)
    return h.hexdigest()


def generate_sample_csv(dest: Path, rows: int, seed: int) -> None:
    """Generate a reproducible PaySim-schema synthetic sample (no invented KYC fields)."""
    rng = random.Random(seed)
    types = ["PAYMENT", "TRANSFER", "CASH_OUT", "CASH_IN", "DEBIT"]
    weights = [0.34, 0.08, 0.35, 0.22, 0.01]
    dest.parent.mkdir(parents=True, exist_ok=True)
    with dest.open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(EXPECTED_COLUMNS)
        for i in range(1, rows + 1):
            step = 1 + ((i - 1) * 744) // max(rows, 1)
            tx_type = rng.choices(types, weights=weights, k=1)[0]
            amount = round(rng.uniform(1.0, 250000.0), 2)
            orig = f"C{100000000 + rng.randint(1, 50000)}"
            dest_prefix = "M" if tx_type == "PAYMENT" else "C"
            dest_id = f"{dest_prefix}{200000000 + rng.randint(1, 50000)}"
            old_org = round(amount + rng.uniform(0.0, 100000.0), 2) if tx_type != "CASH_IN" else round(rng.uniform(0.0, 50000.0), 2)
            new_org = round(old_org + amount, 2) if tx_type == "CASH_IN" else round(max(0.0, old_org - amount), 2)
            old_dst = 0.0 if dest_prefix == "M" else round(rng.uniform(0.0, 100000.0), 2)
            new_dst = 0.0 if dest_prefix == "M" else round(old_dst + amount, 2)
            is_fraud = 1 if (tx_type in {"TRANSFER", "CASH_OUT"} and rng.random() < 0.002) else 0
            is_flagged = 1 if (tx_type == "TRANSFER" and amount > 200000.0 and is_fraud) else 0
            w.writerow([step, tx_type, f"{amount:.2f}", orig, f"{old_org:.2f}", f"{new_org:.2f}",
                        dest_id, f"{old_dst:.2f}", f"{new_dst:.2f}", is_fraud, is_flagged])


def validate_csv(path: Path, quarantine: Path) -> tuple[int, int]:
    """Stream-validate PaySim CSV; quarantine malformed rows instead of silent drops."""
    valid = 0
    malformed = 0
    quarantine.parent.mkdir(parents=True, exist_ok=True)
    with path.open("r", newline="", encoding="utf-8") as fin, quarantine.open("w", newline="", encoding="utf-8") as fq:
        reader = csv.reader(fin)
        qwriter = csv.writer(fq)
        header = next(reader, None)
        if header != EXPECTED_COLUMNS:
            raise ValueError(f"Unexpected CSV header: {header!r}; expected {EXPECTED_COLUMNS!r}")
        qwriter.writerow(EXPECTED_COLUMNS + ["error"])
        for row in reader:
            err = _validate_row(row)
            if err:
                malformed += 1
                qwriter.writerow(row + [err])
            else:
                valid += 1
    if malformed == 0 and quarantine.exists():
        quarantine.unlink()
    return valid, malformed


def _validate_row(row: list[str]) -> str | None:
    if len(row) != 11:
        return f"col_count={len(row)}"
    try:
        step = int(row[0])
        if step < 0:
            return "negative_step"
        if row[1] not in ALLOWED_TYPES:
            return f"bad_type={row[1]}"
        for idx in (2, 4, 5, 7, 8):
            Decimal(row[idx])
        if not row[3] or not row[6]:
            return "empty_account"
        if row[9] not in ("0", "1") or row[10] not in ("0", "1"):
            return "bad_flag"
    except (ValueError, InvalidOperation) as exc:
        return str(exc)
    return None


def main() -> int:
    p = argparse.ArgumentParser(description="Prepare and validate PaySim CSV for FinLakeAmad")
    src = p.add_mutually_exclusive_group()
    src.add_argument("--local-path", type=Path, help="Path to an existing PaySim CSV")
    src.add_argument("--sample-rows", type=int, default=50000, help="Generate synthetic PaySim-schema sample (default: 50000)")
    p.add_argument("--seed", type=int, default=42)
    p.add_argument("--output", type=Path, default=DEFAULT_CSV)
    args = p.parse_args()

    if args.local_path:
        if not args.local_path.is_file():
            print(f"[ERROR] File not found: {args.local_path}", file=sys.stderr)
            return 1
        args.output.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(args.local_path, args.output)
        dataset_kind = "official-or-local"
    else:
        generate_sample_csv(args.output, args.sample_rows, args.seed)
        dataset_kind = f"synthetic-sample-{args.sample_rows}"

    digest = sha256_file(args.output)
    valid, malformed = validate_csv(args.output, QUARANTINE_FILE)
    CHECKSUM_FILE.write_text(f"{digest}  {args.output.name}\n", encoding="utf-8")
    manifest = {
        "dataset": "PaySim (synthetic mobile-money)",
        "kind": dataset_kind,
        "filename": args.output.name,
        "sha256": digest,
        "matches_official_kaggle_sha256": digest == OFFICIAL_PAYSIM_SHA256,
        "valid_rows": valid,
        "malformed_rows": malformed,
        "file_size_bytes": args.output.stat().st_size,
        "staged_at_utc": datetime.now(timezone.utc).isoformat(),
    }
    MANIFEST_FILE.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(manifest, indent=2))
    return 1 if malformed > 0 else 0


if __name__ == "__main__":
    raise SystemExit(main())
