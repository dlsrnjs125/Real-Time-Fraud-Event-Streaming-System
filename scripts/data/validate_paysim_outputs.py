#!/usr/bin/env python3
"""Validate PaySim processed outputs before replay or sampling."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any


DEFAULT_EVENTS = Path("data/processed/paysim-events.jsonl")
DEFAULT_LABELS = Path("data/processed/paysim-labels.jsonl")
DEFAULT_REJECTED = Path("data/processed/paysim-rejected.jsonl")
DEFAULT_REPORT = Path("data/processed/paysim-validation-report.json")
DEFAULT_MAX_REJECT_RATIO = Decimal("0.01")
EXPECTED_HASH_ALGORITHM = "HMAC-SHA256"
EXPECTED_HASH_PREFIX_LENGTH = 16
EXPECTED_DATASET = "ealaxi/paysim1"
EXPECTED_RAW_FILE = "PS_20174392719_1491204439457_log.csv"
VALID_TYPES = {"TRANSFER", "CASH_OUT", "PAYMENT", "CASH_IN", "DEBIT"}
VALID_REJECT_REASONS = {
    "MISSING_REQUIRED_COLUMN",
    "INVALID_STEP",
    "INVALID_TYPE",
    "INVALID_AMOUNT",
    "INVALID_BALANCE",
    "INVALID_LABEL",
    "UNSUPPORTED_ROW",
}
EVENT_REQUIRED_FIELDS = {
    "eventId",
    "userId",
    "accountId",
    "destinationAccountId",
    "eventType",
    "amount",
    "currency",
    "eventTime",
    "traceId",
    "schemaVersion",
    "source",
    "balanceFeatures",
}
BALANCE_REQUIRED_FIELDS = {
    "sourceStep",
    "oldBalanceOrig",
    "newBalanceOrig",
    "oldBalanceDest",
    "newBalanceDest",
}
LABEL_REQUIRED_FIELDS = {
    "eventId",
    "isFraud",
    "sourceFlaggedFraud",
    "sourceStep",
    "sourceType",
}
REJECTED_REQUIRED_FIELDS = {"rowNumber", "reason", "rawType", "message"}
REPORT_REQUIRED_FIELDS = {
    "scriptVersion",
    "datasetSlug",
    "rawFileName",
    "inputPath",
    "inputSha256",
    "baseTime",
    "startedAt",
    "finishedAt",
    "totalRows",
    "acceptedRows",
    "rejectedRows",
    "fraudRows",
    "flaggedFraudRows",
    "eventTypeCounts",
    "outputFiles",
    "hashAlgorithm",
    "hashIdPrefixLength",
    "hashSaltSource",
}
LABEL_LEAKAGE_FIELDS = {"isFraud", "isFlaggedFraud", "sourceFlaggedFraud"}
RAW_IDENTIFIER_FIELDS = {"nameOrig", "nameDest"}
RAW_IDENTIFIER_PATTERN = re.compile(r"\b[CM]\d{3,}\b")
SHA256_PATTERN = re.compile(r"^[0-9a-fA-F]{64}$")
HASH_ID_PATTERNS = {
    "userId": re.compile(r"^U-[0-9a-f]{16}$"),
    "accountId": re.compile(r"^A-[0-9a-f]{16}$"),
    "destinationAccountId": re.compile(r"^D-[0-9a-f]{16}$"),
}
SALT_VALUE_FIELDS = {"hashSaltValue", "saltValue", "salt", "rawSalt"}


class ValidationError(Exception):
    pass


@dataclass
class ValidationSummary:
    events: int
    labels: int
    rejected: int
    fraud: int
    flagged: int
    reject_ratio: Decimal
    event_type_counts: Counter[str]

    def as_dict(self) -> dict[str, Any]:
        return {
            "events": self.events,
            "labels": self.labels,
            "rejected": self.rejected,
            "fraud": self.fraud,
            "flagged": self.flagged,
            "rejectRatio": format(self.reject_ratio, "f"),
            "eventTypeCounts": dict(self.event_type_counts),
        }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Validate PaySim processed output consistency.")
    parser.add_argument("--events", type=Path, default=DEFAULT_EVENTS)
    parser.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    parser.add_argument("--rejected", type=Path, default=DEFAULT_REJECTED)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--max-reject-ratio", default=str(DEFAULT_MAX_REJECT_RATIO))
    parser.add_argument("--summary-output", type=Path)
    parser.add_argument("--require-non-default-salt", action="store_true")
    return parser.parse_args()


def fail(message: str) -> None:
    raise ValidationError(message)


def require_inputs(paths: list[Path]) -> None:
    missing = [str(path) for path in paths if not path.exists()]
    if missing:
        raise FileNotFoundError(", ".join(missing))


def read_jsonl(path: Path):
    with path.open("r", encoding="utf-8") as file:
        for line_no, line in enumerate(file, start=1):
            stripped = line.strip()
            if not stripped:
                continue
            try:
                value = json.loads(stripped)
            except json.JSONDecodeError as exc:
                fail(f"{path.name}:{line_no}: invalid JSON")
            if not isinstance(value, dict):
                fail(f"{path.name}:{line_no}: JSON row must be an object")
            yield line_no, value


def decimal_value(value: Any, path: str) -> Decimal:
    if not isinstance(value, str):
        fail(f"{path} must be a decimal string")
    try:
        parsed = Decimal(value)
    except InvalidOperation as exc:
        fail(f"{path} must be a valid decimal")
    if not parsed.is_finite():
        fail(f"{path} must be a finite decimal")
    return parsed


def validate_utc_iso(value: Any, path: str) -> None:
    if not isinstance(value, str) or not value.endswith("Z"):
        fail(f"{path} must be ISO-8601 UTC ending with Z")
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError as exc:
        fail(f"{path} must be a valid ISO-8601 timestamp")
    if parsed.utcoffset() != timezone.utc.utcoffset(parsed):
        fail(f"{path} must be UTC")


def scan_forbidden(value: Any, path: str, *, forbid_label_fields: bool = False, forbid_salt_fields: bool = False) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if key in RAW_IDENTIFIER_FIELDS:
                fail(f"{child_path} must not contain raw identifier fields")
            if forbid_label_fields and key in LABEL_LEAKAGE_FIELDS:
                fail(f"{child_path} must not contain label fields")
            if forbid_salt_fields and (key in SALT_VALUE_FIELDS or ("salt" in key.lower() and key != "hashSaltSource")):
                fail(f"{child_path} must not contain salt values")
            scan_forbidden(child, child_path, forbid_label_fields=forbid_label_fields, forbid_salt_fields=forbid_salt_fields)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            scan_forbidden(child, f"{path}[{index}]", forbid_label_fields=forbid_label_fields, forbid_salt_fields=forbid_salt_fields)
    elif isinstance(value, str) and RAW_IDENTIFIER_PATTERN.search(value):
        fail(f"{path} must not contain raw PaySim identifiers")


def validate_events(path: Path) -> tuple[set[str], set[str], Counter[str], int]:
    event_ids: set[str] = set()
    trace_ids: set[str] = set()
    event_type_counts: Counter[str] = Counter()
    count = 0
    for line_no, event in read_jsonl(path):
        row = f"{path.name}:{line_no}"
        count += 1
        missing = EVENT_REQUIRED_FIELDS - event.keys()
        if missing:
            fail(f"{row}: missing event fields: {', '.join(sorted(missing))}")
        scan_forbidden(event, row, forbid_label_fields=True)

        event_id = event["eventId"]
        trace_id = event["traceId"]
        if not isinstance(event_id, str) or not event_id:
            fail(f"{row}: eventId must be a non-empty string")
        if event_id in event_ids:
            fail(f"{row}: duplicate eventId")
        event_ids.add(event_id)
        if not isinstance(trace_id, str) or not trace_id:
            fail(f"{row}: traceId must be a non-empty string")
        if trace_id in trace_ids:
            fail(f"{row}: duplicate traceId")
        trace_ids.add(trace_id)

        for field, pattern in HASH_ID_PATTERNS.items():
            if not isinstance(event[field], str) or not pattern.match(event[field]):
                fail(f"{row}: {field} must match {pattern.pattern}")
        if event.get("eventType") not in VALID_TYPES:
            fail(f"{row}: invalid eventType")
        amount = decimal_value(event["amount"], f"{row}.amount")
        if amount < 0:
            fail(f"{row}.amount must be >= 0")
        if event.get("currency") != "KRW":
            fail(f"{row}: currency must be KRW")
        if event.get("source") != "PAYSIM":
            fail(f"{row}: source must be PAYSIM")
        if event.get("schemaVersion") != "v2-paysim":
            fail(f"{row}: schemaVersion must be v2-paysim")
        if "receivedAt" in event:
            fail(f"{row}: receivedAt must not be present")
        validate_utc_iso(event["eventTime"], f"{row}.eventTime")

        balances = event["balanceFeatures"]
        if not isinstance(balances, dict):
            fail(f"{row}.balanceFeatures must be an object")
        missing_balance = BALANCE_REQUIRED_FIELDS - balances.keys()
        if missing_balance:
            fail(f"{row}: missing balance fields: {', '.join(sorted(missing_balance))}")
        if not isinstance(balances["sourceStep"], int) or balances["sourceStep"] < 0:
            fail(f"{row}.balanceFeatures.sourceStep must be a non-negative integer")
        for field in BALANCE_REQUIRED_FIELDS - {"sourceStep"}:
            decimal_value(balances[field], f"{row}.balanceFeatures.{field}")
        event_type_counts[event["eventType"]] += 1
    return event_ids, trace_ids, event_type_counts, count


def validate_labels(path: Path, event_ids: set[str]) -> tuple[set[str], int, int, int]:
    label_ids: set[str] = set()
    count = 0
    fraud = 0
    flagged = 0
    for line_no, label in read_jsonl(path):
        row = f"{path.name}:{line_no}"
        count += 1
        missing = LABEL_REQUIRED_FIELDS - label.keys()
        if missing:
            fail(f"{row}: missing label fields: {', '.join(sorted(missing))}")
        scan_forbidden(label, row)
        event_id = label["eventId"]
        if event_id in label_ids:
            fail(f"{row}: duplicate label eventId")
        label_ids.add(event_id)
        if event_id not in event_ids:
            fail(f"{row}: label eventId does not exist in events")
        if not isinstance(label["isFraud"], bool):
            fail(f"{row}: isFraud must be boolean")
        if not isinstance(label["sourceFlaggedFraud"], bool):
            fail(f"{row}: sourceFlaggedFraud must be boolean")
        if not isinstance(label["sourceStep"], int) or label["sourceStep"] < 0:
            fail(f"{row}: sourceStep must be a non-negative integer")
        if label["sourceType"] not in VALID_TYPES:
            fail(f"{row}: sourceType is invalid")
        fraud += 1 if label["isFraud"] else 0
        flagged += 1 if label["sourceFlaggedFraud"] else 0
    missing_labels = event_ids - label_ids
    if missing_labels:
        fail("labels are missing eventIds from events")
    return label_ids, count, fraud, flagged


def validate_rejected(path: Path) -> int:
    count = 0
    for line_no, rejected in read_jsonl(path):
        row = f"{path.name}:{line_no}"
        count += 1
        missing = REJECTED_REQUIRED_FIELDS - rejected.keys()
        if missing:
            fail(f"{row}: missing rejected fields: {', '.join(sorted(missing))}")
        scan_forbidden(rejected, row)
        if rejected["reason"] not in VALID_REJECT_REASONS:
            fail(f"{row}: rejected reason is not allowed")
    return count


def int_field(report: dict[str, Any], field: str) -> int:
    value = report.get(field)
    if not isinstance(value, int):
        fail(f"report.{field} must be an integer")
    return value


def validate_report(
    path: Path,
    *,
    events_count: int,
    labels_count: int,
    rejected_count: int,
    fraud_count: int,
    flagged_count: int,
    event_type_counts: Counter[str],
    max_reject_ratio: Decimal,
    require_non_default_salt: bool,
) -> ValidationSummary:
    try:
        report = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        fail("report must be valid JSON")
    if not isinstance(report, dict):
        fail("report must be an object")
    missing = REPORT_REQUIRED_FIELDS - report.keys()
    if missing:
        fail(f"report missing fields: {', '.join(sorted(missing))}")
    scan_forbidden(report, "report", forbid_salt_fields=True)

    if report["datasetSlug"] != EXPECTED_DATASET:
        fail("report.datasetSlug is invalid")
    if report["rawFileName"] != EXPECTED_RAW_FILE:
        fail("report.rawFileName is invalid")
    if not isinstance(report["inputSha256"], str) or not SHA256_PATTERN.match(report["inputSha256"]):
        fail("report.inputSha256 must be 64 hex characters")
    if report["hashAlgorithm"] != EXPECTED_HASH_ALGORITHM:
        fail("report.hashAlgorithm must be HMAC-SHA256")
    if report["hashIdPrefixLength"] != EXPECTED_HASH_PREFIX_LENGTH:
        fail("report.hashIdPrefixLength must be 16")
    if not isinstance(report["hashSaltSource"], str) or not report["hashSaltSource"]:
        fail("report.hashSaltSource must be present")
    if require_non_default_salt and report["hashSaltSource"] == "default-local":
        fail("--require-non-default-salt requires non-default hashSaltSource")

    accepted_rows = int_field(report, "acceptedRows")
    rejected_rows = int_field(report, "rejectedRows")
    total_rows = int_field(report, "totalRows")
    fraud_rows = int_field(report, "fraudRows")
    flagged_rows = int_field(report, "flaggedFraudRows")
    if total_rows == 0:
        fail("report.totalRows must be greater than 0")
    if accepted_rows != events_count:
        fail("report.acceptedRows must match event line count")
    if accepted_rows != labels_count:
        fail("report.acceptedRows must match label line count")
    if rejected_rows != rejected_count:
        fail("report.rejectedRows must match rejected line count")
    if total_rows != accepted_rows + rejected_rows:
        fail("report.totalRows must equal acceptedRows + rejectedRows")
    if fraud_rows != fraud_count:
        fail("report.fraudRows must match labels")
    if flagged_rows != flagged_count:
        fail("report.flaggedFraudRows must match labels")
    if not isinstance(report["eventTypeCounts"], dict):
        fail("report.eventTypeCounts must be an object")
    if set(report["eventTypeCounts"].keys()) != VALID_TYPES:
        fail("report.eventTypeCounts must contain all valid event types")
    report_type_sum = 0
    for event_type, value in report["eventTypeCounts"].items():
        if event_type not in VALID_TYPES:
            fail("report.eventTypeCounts contains invalid event type")
        if not isinstance(value, int):
            fail("report.eventTypeCounts values must be integers")
        report_type_sum += value
        if event_type_counts[event_type] != value:
            fail("report.eventTypeCounts must match events")
    if report_type_sum != accepted_rows:
        fail("report.eventTypeCounts sum must match acceptedRows")

    reject_ratio = Decimal(rejected_rows) / Decimal(total_rows)
    if reject_ratio > max_reject_ratio:
        fail("reject ratio exceeds max reject ratio")
    return ValidationSummary(
        events=events_count,
        labels=labels_count,
        rejected=rejected_count,
        fraud=fraud_count,
        flagged=flagged_count,
        reject_ratio=reject_ratio,
        event_type_counts=event_type_counts,
    )


def validate(args: argparse.Namespace) -> ValidationSummary:
    try:
        max_reject_ratio = Decimal(str(args.max_reject_ratio))
    except InvalidOperation as exc:
        raise ValueError("--max-reject-ratio must be decimal") from exc
    if max_reject_ratio < 0:
        raise ValueError("--max-reject-ratio must be >= 0")
    require_inputs([args.events, args.labels, args.rejected, args.report])
    event_ids, _trace_ids, event_type_counts, events_count = validate_events(args.events)
    _label_ids, labels_count, fraud_count, flagged_count = validate_labels(args.labels, event_ids)
    rejected_count = validate_rejected(args.rejected)
    summary = validate_report(
        args.report,
        events_count=events_count,
        labels_count=labels_count,
        rejected_count=rejected_count,
        fraud_count=fraud_count,
        flagged_count=flagged_count,
        event_type_counts=event_type_counts,
        max_reject_ratio=max_reject_ratio,
        require_non_default_salt=args.require_non_default_salt,
    )
    if args.summary_output:
        args.summary_output.parent.mkdir(parents=True, exist_ok=True)
        args.summary_output.write_text(json.dumps(summary.as_dict(), indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return summary


def main() -> int:
    args = parse_args()
    try:
        summary = validate(args)
    except FileNotFoundError as exc:
        print(f"FAIL: input file missing: {exc}", file=sys.stderr)
        return 2
    except ValueError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2
    except ValidationError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    print("PASS: PaySim output validation passed")
    print(
        f"events={summary.events} labels={summary.labels} rejected={summary.rejected} "
        f"fraud={summary.fraud} flagged={summary.flagged} "
        f"rejectRatio={summary.reject_ratio:.4f}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
