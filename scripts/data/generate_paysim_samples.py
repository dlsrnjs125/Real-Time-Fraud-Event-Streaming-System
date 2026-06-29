#!/usr/bin/env python3
"""Generate commit-safe PaySim samples from processed outputs."""

from __future__ import annotations

import argparse
import json
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any


DEFAULT_EVENTS = Path("data/processed/paysim-events.jsonl")
DEFAULT_LABELS = Path("data/processed/paysim-labels.jsonl")
DEFAULT_REPORT = Path("data/processed/paysim-validation-report.json")
DEFAULT_OUTPUT_DIR = Path("data/samples")
MAX_SAMPLE_SIZE = 1000
MAX_SAMPLE_BYTES = 1048576
SCRIPT_VERSION = "v2-phase-4"
EXPECTED_HASH_ALGORITHM = "HMAC-SHA256"
EXPECTED_HASH_PREFIX_LENGTH = 16
LABEL_FIELDS = {"isFraud", "isFlaggedFraud", "sourceFlaggedFraud"}
RAW_FIELDS = {"nameOrig", "nameDest"}
RAW_IDENTIFIER_PATTERN = re.compile(r"\b[CM]\d{3,}\b")
HASH_ID_PATTERNS = {
    "userId": re.compile(r"^U-[0-9a-f]{16}$"),
    "accountId": re.compile(r"^A-[0-9a-f]{16}$"),
    "destinationAccountId": re.compile(r"^D-[0-9a-f]{16}$"),
}
SALT_VALUE_FIELDS = {"hashSaltValue", "saltValue", "salt", "rawSalt"}


class SampleError(Exception):
    pass


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Generate safe PaySim sample JSONL files.")
    parser.add_argument("--events", type=Path, default=DEFAULT_EVENTS)
    parser.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    parser.add_argument("--report", type=Path, default=DEFAULT_REPORT)
    parser.add_argument("--output-dir", type=Path, default=DEFAULT_OUTPUT_DIR)
    parser.add_argument("--sample-size", type=int, default=MAX_SAMPLE_SIZE)
    parser.add_argument("--strategy", choices=("head", "balanced"), default="balanced")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--require-non-default-salt", action="store_true")
    return parser.parse_args()


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def read_jsonl(path: Path):
    with path.open("r", encoding="utf-8") as file:
        for line_no, line in enumerate(file, start=1):
            stripped = line.strip()
            if not stripped:
                continue
            try:
                value = json.loads(stripped)
            except json.JSONDecodeError as exc:
                raise SampleError(f"{path.name}:{line_no}: invalid JSON") from exc
            if not isinstance(value, dict):
                raise SampleError(f"{path.name}:{line_no}: row must be object")
            yield value


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8") as file:
        for row in rows:
            file.write(json.dumps(row, ensure_ascii=False, sort_keys=True))
            file.write("\n")


def scan_forbidden(value: Any, path: str, *, forbid_label_fields: bool = False, forbid_salt_fields: bool = False) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if key in RAW_FIELDS:
                raise SampleError(f"{child_path} must not contain raw identifier fields")
            if forbid_label_fields and key in LABEL_FIELDS:
                raise SampleError(f"{child_path} must not contain label fields")
            if forbid_salt_fields and (key in SALT_VALUE_FIELDS or ("salt" in key.lower() and key != "hashSaltSource")):
                raise SampleError(f"{child_path} must not contain salt values")
            scan_forbidden(child, child_path, forbid_label_fields=forbid_label_fields, forbid_salt_fields=forbid_salt_fields)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            scan_forbidden(child, f"{path}[{index}]", forbid_label_fields=forbid_label_fields, forbid_salt_fields=forbid_salt_fields)
    elif isinstance(value, str) and RAW_IDENTIFIER_PATTERN.search(value):
        raise SampleError(f"{path} must not contain raw PaySim identifiers")


def load_report(path: Path, require_non_default_salt: bool) -> dict[str, Any]:
    try:
        report = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise SampleError("report must be valid JSON") from exc
    if not isinstance(report, dict):
        raise SampleError("report must be an object")
    if report.get("hashAlgorithm") != EXPECTED_HASH_ALGORITHM:
        raise SampleError("report.hashAlgorithm must be HMAC-SHA256")
    if report.get("hashIdPrefixLength") != EXPECTED_HASH_PREFIX_LENGTH:
        raise SampleError("report.hashIdPrefixLength must be 16")
    if not isinstance(report.get("hashSaltSource"), str) or not report.get("hashSaltSource"):
        raise SampleError("report.hashSaltSource must be present")
    if require_non_default_salt and report.get("hashSaltSource") == "default-local":
        raise SampleError("--require-non-default-salt requires non-default hashSaltSource")
    scan_forbidden(report, "report", forbid_salt_fields=True)
    return report


def choose_ids_head(events_path: Path, sample_size: int) -> list[str]:
    selected: list[str] = []
    for event in read_jsonl(events_path):
        selected.append(event["eventId"])
        if len(selected) >= sample_size:
            break
    return selected


def choose_ids_balanced(labels_path: Path, sample_size: int) -> list[str]:
    fraud_ids: list[str] = []
    non_fraud_ids: list[str] = []
    for label in read_jsonl(labels_path):
        event_id = label["eventId"]
        if label.get("isFraud") is True and len(fraud_ids) < sample_size:
            fraud_ids.append(event_id)
        elif label.get("isFraud") is False and len(non_fraud_ids) < sample_size:
            non_fraud_ids.append(event_id)
        if len(fraud_ids) >= sample_size and len(non_fraud_ids) >= sample_size:
            break
    selected = fraud_ids[:sample_size]
    remaining = sample_size - len(selected)
    selected.extend(non_fraud_ids[:remaining])
    return selected


def select_rows(path: Path, selected_ids: set[str]) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    for row in read_jsonl(path):
        if row.get("eventId") in selected_ids:
            rows.append(row)
    return rows


def ensure_outputs(events_path: Path, labels_path: Path, manifest_path: Path, force: bool) -> None:
    existing = [events_path, labels_path, manifest_path]
    present = [str(path) for path in existing if path.exists()]
    if present and not force:
        raise SampleError(f"output file already exists. Use --force: {', '.join(present)}")
    events_path.parent.mkdir(parents=True, exist_ok=True)


def validate_generated(
    events: list[dict[str, Any]],
    labels: list[dict[str, Any]],
    manifest: dict[str, Any],
    output_paths: list[Path],
) -> None:
    if len(events) > MAX_SAMPLE_SIZE:
        raise SampleError("sample events exceed 1000")
    event_ids = {event["eventId"] for event in events}
    label_ids = {label["eventId"] for label in labels}
    if event_ids != label_ids:
        raise SampleError("sample eventId set must match label eventId set")
    for event in events:
        scan_forbidden(event, "sampleEvent", forbid_label_fields=True)
        for field, pattern in HASH_ID_PATTERNS.items():
            if not isinstance(event.get(field), str) or not pattern.match(event[field]):
                raise SampleError(f"sampleEvent.{field} must match {pattern.pattern}")
        if "receivedAt" in event:
            raise SampleError("sampleEvent.receivedAt must not be present")
    for label in labels:
        scan_forbidden(label, "sampleLabel")
    scan_forbidden(manifest, "manifest", forbid_salt_fields=True)
    for path in output_paths:
        if path.exists() and path.stat().st_size > MAX_SAMPLE_BYTES:
            raise SampleError(f"sample file is larger than 1MB: {path}")


def generate(args: argparse.Namespace) -> dict[str, Any]:
    if args.sample_size < 1 or args.sample_size > MAX_SAMPLE_SIZE:
        raise SampleError("--sample-size must be between 1 and 1000")
    for path in (args.events, args.labels, args.report):
        if not path.exists():
            raise FileNotFoundError(str(path))

    events_output = args.output_dir / "paysim-events-sample.jsonl"
    labels_output = args.output_dir / "paysim-labels-sample.jsonl"
    manifest_output = args.output_dir / "paysim-sample-manifest.json"
    ensure_outputs(events_output, labels_output, manifest_output, args.force)

    report = load_report(args.report, args.require_non_default_salt)
    selected_ids = choose_ids_head(args.events, args.sample_size) if args.strategy == "head" else choose_ids_balanced(args.labels, args.sample_size)
    if not selected_ids:
        raise SampleError("no sample eventIds selected")
    selected_set = set(selected_ids)
    events = select_rows(args.events, selected_set)
    labels = select_rows(args.labels, selected_set)
    order = {event_id: index for index, event_id in enumerate(selected_ids)}
    events.sort(key=lambda event: order[event["eventId"]])
    labels.sort(key=lambda label: order[label["eventId"]])

    fraud_rows = sum(1 for label in labels if label.get("isFraud") is True)
    manifest = {
        "scriptVersion": SCRIPT_VERSION,
        "sourceDatasetSlug": report.get("datasetSlug"),
        "sourceRawFileName": report.get("rawFileName"),
        "sourceInputSha256": report.get("inputSha256"),
        "sourceEventsPath": str(args.events),
        "sourceLabelsPath": str(args.labels),
        "sampleStrategy": args.strategy,
        "sampleSizeRequested": args.sample_size,
        "sampleEvents": len(events),
        "sampleLabels": len(labels),
        "sampleFraudRows": fraud_rows,
        "sampleNonFraudRows": len(labels) - fraud_rows,
        "hashAlgorithm": report.get("hashAlgorithm"),
        "hashIdPrefixLength": report.get("hashIdPrefixLength"),
        "hashSaltSource": report.get("hashSaltSource"),
        "eventIdPolicy": "row-number-deterministic",
        "replayCollisionNote": "eventId is deterministic for idempotency tests; Phase 5 replay may add an event-id-prefix option when mixing datasets or rerunning into the same API/database.",
        "createdAt": iso_now(),
        "containsRuntimeLabels": False,
        "containsRawIdentifiers": False,
        "outputFiles": {
            "events": str(events_output),
            "labels": str(labels_output),
        },
    }
    validate_generated(events, labels, manifest, [events_output, labels_output, manifest_output])
    write_jsonl(events_output, events)
    write_jsonl(labels_output, labels)
    manifest_output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    validate_generated(events, labels, manifest, [events_output, labels_output, manifest_output])
    return manifest


def main() -> int:
    args = parse_args()
    try:
        manifest = generate(args)
    except FileNotFoundError as exc:
        print(f"FAIL: input file missing: {exc}", file=sys.stderr)
        return 2
    except SampleError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 1
    print("PASS: PaySim sample generation passed")
    print(
        f"events={manifest['sampleEvents']} labels={manifest['sampleLabels']} "
        f"fraud={manifest['sampleFraudRows']} strategy={manifest['sampleStrategy']}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
