#!/usr/bin/env python3
"""Replay PaySim normalized runtime events into the app-api intake endpoint."""

from __future__ import annotations

import argparse
import json
import os
import re
import socket
import sys
import time
import urllib.error
import urllib.request
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any


SCRIPT_VERSION = "v2-phase-5"
DEFAULT_INPUT = Path("data/samples/paysim-events-sample.jsonl")
DEFAULT_ENDPOINT = "http://localhost:8080/api/v1/transactions/events"
DEFAULT_REPORT_OUTPUT = Path("data/processed/paysim-replay-report.json")
DEFAULT_RATE_PER_SECOND = 10.0
DEFAULT_TIMEOUT_SECONDS = 3.0
DEFAULT_ADMIN_TOKEN_ENV = "ADMIN_API_TOKEN"
MAX_SAMPLE_EVENT_IDS = 10
MAX_FAILURES = 50

LABEL_FIELDS = {"isFraud", "isFlaggedFraud", "sourceFlaggedFraud"}
RAW_FIELDS = {"nameOrig", "nameDest"}
REQUIRED_FIELDS = {"eventId", "userId", "accountId", "destinationAccountId", "eventType", "amount", "currency", "eventTime", "traceId"}
REQUEST_FIELDS = {"eventId", "userId", "accountId", "eventType", "amount", "currency", "merchantId", "deviceId", "location", "eventTime"}
HASH_ID_PATTERNS = {
    "userId": re.compile(r"^U-[0-9a-f]{16}$"),
    "accountId": re.compile(r"^A-[0-9a-f]{16}$"),
    "destinationAccountId": re.compile(r"^D-[0-9a-f]{16}$"),
}
RAW_IDENTIFIER_PATTERN = re.compile(r"\b[CM]\d{3,}\b")


class ReplayError(Exception):
    pass


class PayloadValidationError(ReplayError):
    def __init__(self, reason: str, event_id: str | None = None) -> None:
        super().__init__(reason)
        self.reason = reason
        self.event_id = event_id


@dataclass
class FailureRecord:
    rowNumber: int
    reason: str
    eventId: str | None = None

    def as_dict(self) -> dict[str, Any]:
        value: dict[str, Any] = {"rowNumber": self.rowNumber, "reason": self.reason}
        if self.eventId:
            value["eventId"] = self.eventId
        return value


@dataclass
class ReplayStats:
    total_read: int = 0
    payload_accepted: int = 0
    payload_rejected: int = 0
    http_success: int = 0
    http_duplicate_or_conflict: int = 0
    http_client_error: int = 0
    http_server_error: int = 0
    timeout: int = 0
    connection_error: int = 0
    retry_attempts: int = 0
    dropped_fields: Counter[str] = field(default_factory=Counter)
    sample_event_ids: list[str] = field(default_factory=list)
    failures: list[FailureRecord] = field(default_factory=list)

    def add_failure(self, row_number: int, reason: str, event_id: str | None = None) -> None:
        if len(self.failures) < MAX_FAILURES:
            self.failures.append(FailureRecord(rowNumber=row_number, eventId=event_id, reason=reason))

    def add_sample_event_id(self, event_id: str) -> None:
        if len(self.sample_event_ids) < MAX_SAMPLE_EVENT_IDS:
            self.sample_event_ids.append(event_id)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Replay PaySim runtime events into app-api.")
    parser.add_argument("--input", type=Path, default=DEFAULT_INPUT)
    parser.add_argument("--endpoint", default=DEFAULT_ENDPOINT)
    parser.add_argument("--max-events", type=int)
    parser.add_argument("--rate-per-second", type=float, default=DEFAULT_RATE_PER_SECOND)
    parser.add_argument("--timeout-seconds", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--report-output", type=Path, default=DEFAULT_REPORT_OUTPUT)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--event-id-prefix")
    parser.add_argument("--idempotency-mode", choices=("preserve", "prefix"), default="preserve")
    parser.add_argument("--retry-count", type=int, default=0)
    parser.add_argument("--stop-on-error", action="store_true")
    parser.add_argument("--admin-token")
    parser.add_argument("--admin-token-env", default=DEFAULT_ADMIN_TOKEN_ENV)
    return parser.parse_args()


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def parse_started(value: str) -> datetime:
    return datetime.fromisoformat(value.replace("Z", "+00:00"))


def validate_args(args: argparse.Namespace) -> None:
    if args.max_events is not None and args.max_events < 1:
        raise ReplayError("--max-events must be >= 1")
    if args.rate_per_second <= 0:
        raise ReplayError("--rate-per-second must be > 0")
    if args.timeout_seconds <= 0:
        raise ReplayError("--timeout-seconds must be > 0")
    if args.retry_count < 0:
        raise ReplayError("--retry-count must be >= 0")
    if args.idempotency_mode == "prefix" and not args.event_id_prefix:
        raise ReplayError("--idempotency-mode prefix requires --event-id-prefix")
    if args.idempotency_mode == "preserve" and args.event_id_prefix:
        raise ReplayError("--event-id-prefix requires --idempotency-mode prefix")
    if args.report_output.exists() and not args.force:
        raise ReplayError(f"report output already exists. Use --force: {args.report_output}")


def read_jsonl(path: Path):
    with path.open("r", encoding="utf-8") as file:
        for line_no, line in enumerate(file, start=1):
            stripped = line.strip()
            if not stripped:
                continue
            try:
                value = json.loads(stripped)
            except json.JSONDecodeError as exc:
                raise PayloadValidationError("INVALID_JSON") from exc
            if not isinstance(value, dict):
                raise PayloadValidationError("JSON_ROW_NOT_OBJECT")
            yield line_no, value


def scan_forbidden(value: Any, path: str = "event") -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            child_path = f"{path}.{key}"
            if key in RAW_FIELDS:
                raise PayloadValidationError(f"RAW_IDENTIFIER_FIELD:{key}", event_id=event_id_from(value))
            if key in LABEL_FIELDS:
                raise PayloadValidationError(f"LABEL_FIELD:{key}", event_id=event_id_from(value))
            scan_forbidden(child, child_path)
    elif isinstance(value, list):
        for index, child in enumerate(value):
            scan_forbidden(child, f"{path}[{index}]")
    elif isinstance(value, str) and RAW_IDENTIFIER_PATTERN.search(value):
        raise PayloadValidationError("RAW_IDENTIFIER_VALUE")


def event_id_from(value: Any) -> str | None:
    if isinstance(value, dict) and isinstance(value.get("eventId"), str):
        return value["eventId"]
    return None


def validate_event(event: dict[str, Any]) -> None:
    event_id = event_id_from(event)
    scan_forbidden(event)
    missing = sorted(REQUIRED_FIELDS - event.keys())
    if missing:
        raise PayloadValidationError(f"MISSING_FIELD:{','.join(missing)}", event_id=event_id)
    if "receivedAt" in event:
        raise PayloadValidationError("RECEIVED_AT_PRESENT", event_id=event_id)
    for field, pattern in HASH_ID_PATTERNS.items():
        if not isinstance(event.get(field), str) or not pattern.match(event[field]):
            raise PayloadValidationError(f"INVALID_HASH_ID:{field}", event_id=event_id)
    if not isinstance(event["eventId"], str) or not event["eventId"]:
        raise PayloadValidationError("INVALID_EVENT_ID", event_id=event_id)
    if not isinstance(event["traceId"], str) or not event["traceId"]:
        raise PayloadValidationError("INVALID_TRACE_ID", event_id=event_id)
    if not isinstance(event["eventType"], str) or not event["eventType"]:
        raise PayloadValidationError("INVALID_EVENT_TYPE", event_id=event_id)
    if event["currency"] != "KRW":
        raise PayloadValidationError("INVALID_CURRENCY", event_id=event_id)
    try:
        amount = Decimal(str(event["amount"]))
    except (InvalidOperation, ValueError) as exc:
        raise PayloadValidationError("INVALID_AMOUNT", event_id=event_id) from exc
    if not amount.is_finite() or amount <= 0:
        raise PayloadValidationError("INVALID_AMOUNT", event_id=event_id)
    if not isinstance(event["eventTime"], str) or not event["eventTime"]:
        raise PayloadValidationError("INVALID_EVENT_TIME", event_id=event_id)


def replay_event_id(event_id: str, idempotency_mode: str, event_id_prefix: str | None) -> str:
    if idempotency_mode == "prefix":
        if not event_id_prefix:
            raise ReplayError("--idempotency-mode prefix requires --event-id-prefix")
        return f"{event_id_prefix}-{event_id}"
    return event_id


def to_api_request(event: dict[str, Any], args: argparse.Namespace, dropped_fields: Counter[str]) -> tuple[dict[str, Any], str]:
    validate_event(event)
    request: dict[str, Any] = {}
    for key, value in event.items():
        if key == "traceId":
            continue
        if key in REQUEST_FIELDS:
            request[key] = value
        else:
            dropped_fields[key] += 1
    request["eventId"] = replay_event_id(event["eventId"], args.idempotency_mode, args.event_id_prefix)
    return request, event["traceId"]


def resolve_admin_token(args: argparse.Namespace) -> tuple[str | None, bool]:
    if args.admin_token:
        return args.admin_token, True
    env_value = os.getenv(args.admin_token_env)
    if env_value:
        return env_value, True
    return None, False


def make_http_request(endpoint: str, payload: dict[str, Any], trace_id: str, token: str | None, timeout_seconds: float) -> int:
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        endpoint,
        data=body,
        method="POST",
        headers={
            "Content-Type": "application/json",
            "X-Trace-Id": trace_id,
        },
    )
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=timeout_seconds) as response:
        return response.getcode()


def classify_http_status(status: int) -> str:
    if 200 <= status < 300:
        return "success"
    if status == 409:
        return "duplicate"
    if 400 <= status < 500:
        return "clientError"
    if 500 <= status < 600:
        return "serverError"
    return "clientError"


def record_status(stats: ReplayStats, status: int) -> str:
    category = classify_http_status(status)
    if category == "success":
        stats.http_success += 1
    elif category == "duplicate":
        stats.http_duplicate_or_conflict += 1
    elif category == "clientError":
        stats.http_client_error += 1
    else:
        stats.http_server_error += 1
    return category


def send_with_retry(
    endpoint: str,
    payload: dict[str, Any],
    trace_id: str,
    token: str | None,
    timeout_seconds: float,
    retry_count: int,
    stats: ReplayStats,
) -> str:
    attempts = retry_count + 1
    for attempt in range(attempts):
        try:
            status = make_http_request(endpoint, payload, trace_id, token, timeout_seconds)
            category = record_status(stats, status)
            if category == "serverError" and attempt < retry_count:
                stats.retry_attempts += 1
                continue
            return f"HTTP_{status}"
        except urllib.error.HTTPError as exc:
            category = record_status(stats, exc.code)
            if category == "serverError" and attempt < retry_count:
                stats.retry_attempts += 1
                continue
            return f"HTTP_{exc.code}"
        except TimeoutError:
            stats.timeout += 1
            if attempt < retry_count:
                stats.retry_attempts += 1
                continue
            return "TIMEOUT"
        except (socket.timeout, urllib.error.URLError) as exc:
            if isinstance(getattr(exc, "reason", None), socket.timeout):
                stats.timeout += 1
                if attempt < retry_count:
                    stats.retry_attempts += 1
                    continue
                return "TIMEOUT"
            stats.connection_error += 1
            if attempt < retry_count:
                stats.retry_attempts += 1
                continue
            return "CONNECTION_ERROR"


def maybe_sleep(last_sent_at: float | None, rate_per_second: float) -> float:
    if last_sent_at is None:
        return time.monotonic()
    interval = 1.0 / rate_per_second
    elapsed = time.monotonic() - last_sent_at
    if elapsed < interval:
        time.sleep(interval - elapsed)
    return time.monotonic()


def build_report(args: argparse.Namespace, stats: ReplayStats, started_at: str, finished_at: str, auth_used: bool) -> dict[str, Any]:
    started_dt = parse_started(started_at)
    finished_dt = parse_started(finished_at)
    duration_seconds = max((finished_dt - started_dt).total_seconds(), 0.0)
    attempted = stats.http_success + stats.http_duplicate_or_conflict + stats.http_client_error + stats.http_server_error + stats.timeout + stats.connection_error
    events_per_second = attempted / duration_seconds if duration_seconds > 0 else 0.0
    return {
        "scriptVersion": SCRIPT_VERSION,
        "inputPath": str(args.input),
        "endpoint": args.endpoint,
        "startedAt": started_at,
        "finishedAt": finished_at,
        "dryRun": args.dry_run,
        "idempotencyMode": args.idempotency_mode,
        "eventIdPrefix": args.event_id_prefix,
        "maxEvents": args.max_events,
        "ratePerSecond": args.rate_per_second,
        "timeoutSeconds": args.timeout_seconds,
        "retryCount": args.retry_count,
        "stopOnError": args.stop_on_error,
        "authUsed": auth_used,
        "totalRead": stats.total_read,
        "payloadAccepted": stats.payload_accepted,
        "payloadRejected": stats.payload_rejected,
        "httpSuccess": stats.http_success,
        "httpDuplicateOrConflict": stats.http_duplicate_or_conflict,
        "httpClientError": stats.http_client_error,
        "httpServerError": stats.http_server_error,
        "timeout": stats.timeout,
        "connectionError": stats.connection_error,
        "retryAttempts": stats.retry_attempts,
        "durationSeconds": round(duration_seconds, 6),
        "eventsPerSecond": round(events_per_second, 6),
        "droppedFields": dict(sorted(stats.dropped_fields.items())),
        "sampleEventIds": stats.sample_event_ids,
        "failures": [failure.as_dict() for failure in stats.failures],
    }


def write_report(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def replay(args: argparse.Namespace) -> dict[str, Any]:
    validate_args(args)
    if not args.input.exists():
        raise FileNotFoundError(str(args.input))

    token, auth_used = resolve_admin_token(args)
    stats = ReplayStats()
    started_at = iso_now()
    last_sent_at: float | None = None

    for row_number, event in read_jsonl(args.input):
        if args.max_events is not None and stats.total_read >= args.max_events:
            break
        stats.total_read += 1
        event_id = event_id_from(event)
        try:
            payload, trace_id = to_api_request(event, args, stats.dropped_fields)
        except PayloadValidationError as exc:
            stats.payload_rejected += 1
            stats.add_failure(row_number, exc.reason, exc.event_id or event_id)
            if args.stop_on_error:
                break
            continue

        stats.payload_accepted += 1
        stats.add_sample_event_id(payload["eventId"])
        if args.dry_run:
            continue

        last_sent_at = maybe_sleep(last_sent_at, args.rate_per_second)
        reason = send_with_retry(args.endpoint, payload, trace_id, token, args.timeout_seconds, args.retry_count, stats)
        if reason != "HTTP_202" and not reason.startswith("HTTP_2"):
            stats.add_failure(row_number, reason, payload["eventId"])
            if args.stop_on_error:
                break

    finished_at = iso_now()
    report = build_report(args, stats, started_at, finished_at, auth_used)
    write_report(args.report_output, report)
    return report


def main() -> int:
    args = parse_args()
    try:
        report = replay(args)
    except FileNotFoundError as exc:
        print(f"FAIL: input file missing: {exc}", file=sys.stderr)
        return 2
    except (ReplayError, PayloadValidationError) as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2
    print("PASS: PaySim replay completed")
    print(
        f"read={report['totalRead']} accepted={report['payloadAccepted']} rejected={report['payloadRejected']} "
        f"success={report['httpSuccess']} duplicate={report['httpDuplicateOrConflict']} "
        f"clientError={report['httpClientError']} serverError={report['httpServerError']} "
        f"timeout={report['timeout']} connectionError={report['connectionError']} dryRun={report['dryRun']}"
    )
    print(f"report={args.report_output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
