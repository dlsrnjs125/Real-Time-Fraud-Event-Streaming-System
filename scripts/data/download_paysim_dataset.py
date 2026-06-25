#!/usr/bin/env python3
"""Download the PaySim dataset with kagglehub and copy the raw CSV locally."""

from __future__ import annotations

import argparse
import shutil
import sys
import csv
from pathlib import Path


DEFAULT_DATASET = "ealaxi/paysim1"
DEFAULT_FILE_NAME = "PS_20174392719_1491204439457_log.csv"
DEFAULT_TARGET = Path("data/raw") / DEFAULT_FILE_NAME
REQUIRED_COLUMNS = {
    "step",
    "type",
    "amount",
    "nameOrig",
    "oldbalanceOrg",
    "newbalanceOrig",
    "nameDest",
    "oldbalanceDest",
    "newbalanceDest",
    "isFraud",
    "isFlaggedFraud",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Download PaySim CSV into data/raw.")
    parser.add_argument("--dataset", default=DEFAULT_DATASET)
    parser.add_argument("--target", type=Path, default=DEFAULT_TARGET)
    parser.add_argument("--source-file", help="CSV file name inside the kagglehub cache.")
    parser.add_argument("--force", action="store_true", help="Overwrite target file if it exists.")
    return parser.parse_args()


def import_kagglehub():
    try:
        import kagglehub  # type: ignore
    except ImportError:
        print(
            "ERROR: kagglehub is not installed. Install it with: pip install kagglehub",
            file=sys.stderr,
        )
        raise SystemExit(1)
    return kagglehub


def find_csv(cache_path: Path, source_file: str | None) -> Path:
    if source_file:
        candidates = list(cache_path.rglob(source_file))
        if not candidates:
            raise SystemExit(f"ERROR: source file not found in kagglehub cache: {source_file}")
        if len(candidates) > 1:
            raise SystemExit(f"ERROR: multiple source files named {source_file} found in cache.")
        return candidates[0]

    default_candidates = list(cache_path.rglob(DEFAULT_FILE_NAME))
    if len(default_candidates) == 1:
        return default_candidates[0]
    if len(default_candidates) > 1:
        raise SystemExit(f"ERROR: multiple expected PaySim CSV files found: {DEFAULT_FILE_NAME}")

    raise SystemExit(
        f"ERROR: expected PaySim CSV not found: {DEFAULT_FILE_NAME}. "
        "Re-run with --source-file only after verifying the dataset contents."
    )


def validate_csv_header(path: Path) -> None:
    with path.open("r", encoding="utf-8", newline="") as file:
        reader = csv.DictReader(file)
        if reader.fieldnames is None:
            raise SystemExit(f"ERROR: CSV header parse failed: {path.name}")
        missing = sorted(REQUIRED_COLUMNS - set(reader.fieldnames))
        if missing:
            raise SystemExit(
                "ERROR: selected CSV does not match PaySim required columns. "
                f"Missing: {', '.join(missing)}"
            )


def main() -> int:
    args = parse_args()
    target: Path = args.target

    if target.exists() and not args.force:
        print(f"ERROR: target already exists. Use --force to overwrite: {target}", file=sys.stderr)
        return 1

    kagglehub = import_kagglehub()

    try:
        cache_path = Path(kagglehub.dataset_download(args.dataset))
    except Exception as exc:  # noqa: BLE001
        print(
            "ERROR: failed to download PaySim dataset with kagglehub. "
            "Check KaggleHub/Kaggle authentication and dataset access. "
            "Do not commit tokens or credentials.",
            file=sys.stderr,
        )
        print(f"Detail: {exc}", file=sys.stderr)
        return 1

    source = find_csv(cache_path, args.source_file)
    validate_csv_header(source)
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source, target)

    print(f"Dataset slug: {args.dataset}")
    print(f"Downloaded cache path: {cache_path}")
    print(f"Copied source file: {source.name}")
    print(f"Copied target path: {target}")
    print(f"File size bytes: {target.stat().st_size}")
    print("Raw CSV is ignored by Git and guarded by make data-policy-check.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
