#!/usr/bin/env python3
"""Normalize PaySim CSV into runtime events, label sidecar, rejects, and report."""

from __future__ import annotations

import argparse
import csv
import hashlib
import hmac
import json
import os
import sys
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any


SCRIPT_VERSION = "v2-phase-4"
DEFAULT_INPUT = Path("data/raw/PS_20174392719_1491204439457_log.csv")
DEFAULT_OUTPUT_DIR = Path("data/processed")
DEFAULT_BASE_TIME = "2026-01-01T00:00:00Z"
DEFAULT_DATASET_SLUG = "ealaxi/paysim1"
DEFAULT_HASH_SALT_ENV = "PAYSIM_HASH_SALT"
DEFAULT_LOCAL_SALT = "local-dev-paysim-salt"
HASH_PREFIX_LENGTH = 16
HASH_ALGORITHM = "HMAC-SHA256"
VALID_TYPES = {"PAYMENT", "TRANSFER", "CASH_OUT", "CASH_IN", "DEBIT"}
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


class RowRejected(Exception):
    def __init__(self, reason: str, message: str) -> None:
        super().__init__(message)
        self.reason = reason
        self.message = message


@dataclass(frozen=True)
class OutputPaths:
    events: Path
    labels: Path
    rejected: Path
    report: Path


@dataclass
class Counters:
    total_rows: int = 0
    accepted_rows: int = 0
    rejected_rows: int = 0
    fraud_rows: int = 0
    flagged_fraud_rows: int = 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Prepare PaySim normalized JSONL outputs.")
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--base-time", default=DEFAULT_BASE_TIME)
    parser.add_argument("--dataset-slug", default=DEFAULT_DATASET_SLUG)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--reject-policy", choices=("row-level", "fail-fast"), default="row-level")
    parser.add_argument("--hash-salt")
    parser.add_argument("--hash-salt-env", default=DEFAULT_HASH_SALT_ENV)
    parser.add_argument("--require-non-default-salt", action="store_true")
    parser.add_argument("--force", action="store_true")
    return parser.parse_args()


def parse_base_time(value: str) -> datetime:
    normalized = value.replace("Z", "+00:00")
    try:
        parsed = datetime.fromisoformat(normalized)
    except ValueError as exc:
        raise SystemExit(f"ERROR: invalid --base-time: {value}") from exc
    if parsed.tzinfo is None:
        parsed = parsed.replace(tzinfo=timezone.utc)
    return parsed.astimezone(timezone.utc)


def iso_z(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def decimal_from(row: dict[str, str], field: str, reason: str) -> Decimal:
    try:
        value = Decimal(row[field])
    except (InvalidOperation, KeyError) as exc:
        raise RowRejected(reason, f"{field} must be a decimal") from exc
    if not value.is_finite():
        raise RowRejected(reason, f"{field} must be a finite decimal")
    return value


def decimal_string(value: Decimal) -> str:
    return format(value, "f")


def parse_step(row: dict[str, str]) -> int:
    try:
        step = int(row["step"])
    except (ValueError, KeyError) as exc:
        raise RowRejected("INVALID_STEP", "step must be an integer") from exc
    if step < 0:
        raise RowRejected("INVALID_STEP", "step must be >= 0")
    return step


def parse_label(row: dict[str, str], field: str) -> bool:
    value = row.get(field)
    if value == "0":
        return False
    if value == "1":
        return True
    raise RowRejected("INVALID_LABEL", f"{field} must be 0 or 1")


def pseudonym_digest(raw: str, salt: str) -> str:
    return hmac.new(
        salt.encode("utf-8"),
        raw.encode("utf-8"),
        hashlib.sha256,
    ).hexdigest()[:HASH_PREFIX_LENGTH]


def hashed_id(prefix: str, raw: str, salt: str) -> str:
    digest = pseudonym_digest(raw, salt)
    return f"{prefix}-{digest}"


def resolve_salt(args: argparse.Namespace) -> tuple[str, str]:
    if args.hash_salt:
        return args.hash_salt, "cli"
    env_value = os.getenv(args.hash_salt_env)
    if env_value:
        return env_value, f"env:{args.hash_salt_env}"
    if args.require_non_default_salt:
        raise SystemExit("ERROR: --require-non-default-salt requires --hash-salt or a hash salt environment variable")
    return DEFAULT_LOCAL_SALT, "default-local"


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file:
        for chunk in iter(lambda: file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def output_paths(output_dir: Path) -> OutputPaths:
    return OutputPaths(
        events=output_dir / "paysim-events.jsonl",
        labels=output_dir / "paysim-labels.jsonl",
        rejected=output_dir / "paysim-rejected.jsonl",
        report=output_dir / "paysim-validation-report.json",
    )


def ensure_outputs(paths: OutputPaths, force: bool) -> None:
    existing = [path for path in (paths.events, paths.labels, paths.rejected, paths.report) if path.exists()]
    if existing and not force:
        names = ", ".join(str(path) for path in existing)
        raise SystemExit(f"ERROR: output file already exists. Use --force to overwrite: {names}")
    paths.events.parent.mkdir(parents=True, exist_ok=True)


def validate_header(fieldnames: list[str] | None) -> None:
    if fieldnames is None:
        raise SystemExit("ERROR: CSV header parse failed.")
    missing = sorted(REQUIRED_COLUMNS - set(fieldnames))
    if missing:
        raise SystemExit(f"ERROR: missing required columns: {', '.join(missing)}")


def normalize_row(
    row: dict[str, str],
    row_number: int,
    base_time: datetime,
    salt: str,
) -> tuple[dict[str, Any], dict[str, Any]]:
    step = parse_step(row)
    event_type = row.get("type", "")
    if event_type not in VALID_TYPES:
        raise RowRejected("INVALID_TYPE", "type is not supported")

    amount = decimal_from(row, "amount", "INVALID_AMOUNT")
    if amount < Decimal("0"):
        raise RowRejected("INVALID_AMOUNT", "amount must be >= 0")

    balances = {
        "oldBalanceOrig": decimal_from(row, "oldbalanceOrg", "INVALID_BALANCE"),
        "newBalanceOrig": decimal_from(row, "newbalanceOrig", "INVALID_BALANCE"),
        "oldBalanceDest": decimal_from(row, "oldbalanceDest", "INVALID_BALANCE"),
        "newBalanceDest": decimal_from(row, "newbalanceDest", "INVALID_BALANCE"),
    }

    name_orig = row.get("nameOrig", "")
    name_dest = row.get("nameDest", "")
    if not name_orig.strip() or not name_dest.strip():
        raise RowRejected("UNSUPPORTED_ROW", "nameOrig and nameDest must not be blank")

    is_fraud = parse_label(row, "isFraud")
    source_flagged = parse_label(row, "isFlaggedFraud")

    sequence = f"{row_number:09d}"
    event_id = f"paysim-{sequence}"
    event_time = base_time + timedelta(hours=step)
    origin_hash = pseudonym_digest(name_orig, salt)

    event = {
        "eventId": event_id,
        "userId": f"U-{origin_hash}",
        "accountId": f"A-{origin_hash}",
        "destinationAccountId": hashed_id("D", name_dest, salt),
        "eventType": event_type,
        "amount": decimal_string(amount),
        "currency": "KRW",
        "eventTime": iso_z(event_time),
        "traceId": f"trace-paysim-{sequence}",
        "schemaVersion": "v2-paysim",
        "source": "PAYSIM",
        "balanceFeatures": {
            "sourceStep": step,
            **{key: decimal_string(value) for key, value in balances.items()},
        },
    }
    label = {
        "eventId": event_id,
        "isFraud": is_fraud,
        "sourceFlaggedFraud": source_flagged,
        "sourceStep": step,
        "sourceType": event_type,
    }
    return event, label


def write_jsonl(file, value: dict[str, Any]) -> None:
    file.write(json.dumps(value, ensure_ascii=False, sort_keys=True))
    file.write("\n")


def rejected_record(row_number: int, reason: str, message: str, row: dict[str, str]) -> dict[str, Any]:
    return {
        "rowNumber": row_number,
        "reason": reason,
        "rawType": row.get("type"),
        "message": message,
    }


def process(args: argparse.Namespace) -> dict[str, Any]:
    if not args.input.exists():
        raise SystemExit(f"ERROR: input file not found: {args.input}")
    if args.limit is not None and args.limit < 0:
        raise SystemExit("ERROR: --limit must be >= 0")

    base_time = parse_base_time(args.base_time)
    salt, salt_source = resolve_salt(args)
    paths = output_paths(args.output_dir)
    ensure_outputs(paths, args.force)

    input_sha256 = sha256_file(args.input)
    counters = Counters()
    type_counts = {event_type: 0 for event_type in sorted(VALID_TYPES)}
    started_at = iso_z(datetime.now(timezone.utc))

    with args.input.open("r", encoding="utf-8-sig", newline="") as input_file:
        reader = csv.DictReader(input_file)
        validate_header(reader.fieldnames)

        with (
            paths.events.open("w", encoding="utf-8") as events_file,
            paths.labels.open("w", encoding="utf-8") as labels_file,
            paths.rejected.open("w", encoding="utf-8") as rejected_file,
        ):
            for row_number, row in enumerate(reader, start=1):
                if args.limit is not None and counters.total_rows >= args.limit:
                    break
                counters.total_rows += 1

                try:
                    event, label = normalize_row(row, row_number, base_time, salt)
                except RowRejected as exc:
                    counters.rejected_rows += 1
                    if args.reject_policy == "fail-fast":
                        raise SystemExit(f"ERROR: row {row_number}: {exc.reason}: {exc.message}") from exc
                    write_jsonl(rejected_file, rejected_record(row_number, exc.reason, exc.message, row))
                    continue

                counters.accepted_rows += 1
                if label["isFraud"]:
                    counters.fraud_rows += 1
                if label["sourceFlaggedFraud"]:
                    counters.flagged_fraud_rows += 1
                type_counts[event["eventType"]] += 1
                write_jsonl(events_file, event)
                write_jsonl(labels_file, label)

                if counters.total_rows % 100000 == 0:
                    print(f"processed rows: {counters.total_rows}", file=sys.stderr)

    finished_at = iso_z(datetime.now(timezone.utc))
    report = {
        "scriptVersion": SCRIPT_VERSION,
        "datasetSlug": args.dataset_slug,
        "rawFileName": args.input.name,
        "inputPath": str(args.input),
        "inputSha256": input_sha256,
        "baseTime": iso_z(base_time),
        "startedAt": started_at,
        "finishedAt": finished_at,
        "totalRows": counters.total_rows,
        "acceptedRows": counters.accepted_rows,
        "rejectedRows": counters.rejected_rows,
        "fraudRows": counters.fraud_rows,
        "flaggedFraudRows": counters.flagged_fraud_rows,
        "eventTypeCounts": type_counts,
        "hashAlgorithm": HASH_ALGORITHM,
        "hashIdPrefixLength": HASH_PREFIX_LENGTH,
        "hashSaltSource": salt_source,
        "outputFiles": {
            "events": str(paths.events),
            "labels": str(paths.labels),
            "rejected": str(paths.rejected),
        },
    }
    paths.report.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n")
    return report


def main() -> int:
    args = parse_args()
    report = process(args)
    print(
        "Prepared PaySim dataset: "
        f"accepted={report['acceptedRows']} rejected={report['rejectedRows']} "
        f"events={report['outputFiles']['events']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
