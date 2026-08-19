#!/usr/bin/env python3
"""Build a privacy-safe V3 PaySim corpus profile without loading the CSV into memory."""

from __future__ import annotations

import argparse
import csv
import json
import math
import sqlite3
import tempfile
from datetime import datetime, timezone
from decimal import Decimal
from pathlib import Path
from typing import Any

import prepare_paysim_dataset as prepare


PROFILE_SCHEMA_VERSION = "v3-phase0-profile-v1"
PROFILER_VERSION = "v3-phase0-v1"
IDENTIFIER_POLICY_VERSION = "paysim-hmac-sha256-prefix16-v1"
SOURCE_TIME_RESOLUTION = "1h"
QUANTILE_METHOD = "nearest-rank"
TOP_PERCENT_ROUNDING_RULE = "ceil(uniqueUsers * 0.01), minimum 1 when non-empty"
SOURCE_STEP_BOUNDARY_RULE = "PaySim integer step; preprocessing maps one step to one hour"
DEFAULT_OUTPUT = Path("data/processed/paysim-v3-profile.json")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Profile PaySim for V3 workload design.")
    parser.add_argument("--input", type=Path, default=prepare.DEFAULT_INPUT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--dataset-slug", default=prepare.DEFAULT_DATASET_SLUG)
    parser.add_argument("--base-time", default=prepare.DEFAULT_BASE_TIME)
    parser.add_argument("--hash-salt")
    parser.add_argument("--hash-salt-env", default=prepare.DEFAULT_HASH_SALT_ENV)
    parser.add_argument("--limit", type=int)
    parser.add_argument("--generated-at", help="Fixed ISO-8601 value for deterministic fixture verification.")
    parser.add_argument("--force", action="store_true")
    return parser.parse_args()


def ratio(numerator: int, denominator: int) -> float:
    if denominator == 0:
        return 0.0
    return round(numerator / denominator, 8)


def quantile_offset(count: int, quantile: float) -> int:
    if count <= 0:
        raise ValueError("quantile requires at least one value")
    return max(0, math.ceil(count * quantile) - 1)


def integer_quantiles(connection: sqlite3.Connection, query: str, count: int) -> dict[str, int]:
    if count == 0:
        return {"p50": 0, "p95": 0, "p99": 0, "max": 0}

    values = {}
    for name, quantile in (("p50", 0.50), ("p95", 0.95), ("p99", 0.99)):
        row = connection.execute(f"{query} LIMIT 1 OFFSET ?", (quantile_offset(count, quantile),)).fetchone()
        values[name] = int(row[0])
    values["max"] = int(connection.execute(f"SELECT MAX(value) FROM ({query})").fetchone()[0])
    return values


def amount_quantiles(connection: sqlite3.Connection, count: int) -> dict[str, str | None]:
    if count == 0:
        return {"p50": None, "p95": None, "p99": None}

    values: dict[str, str | None] = {}
    query = "SELECT amount_text FROM events ORDER BY amount_numeric, rowid"
    for name, quantile in (("p50", 0.50), ("p95", 0.95), ("p99", 0.99)):
        row = connection.execute(f"{query} LIMIT 1 OFFSET ?", (quantile_offset(count, quantile),)).fetchone()
        values[name] = str(row[0])
    return values


def initialize_database(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        PRAGMA journal_mode = OFF;
        PRAGMA synchronous = OFF;
        CREATE TABLE events (
            user_id TEXT NOT NULL,
            source_step INTEGER NOT NULL,
            amount_numeric REAL NOT NULL,
            amount_text TEXT NOT NULL,
            native_event_type TEXT NOT NULL,
            normalized_event_type TEXT NOT NULL
        );
        """
    )


def build_aggregates(connection: sqlite3.Connection) -> None:
    connection.executescript(
        """
        CREATE TABLE users AS
        SELECT user_id, COUNT(*) AS event_count
        FROM events
        GROUP BY user_id;
        CREATE INDEX users_quantile_idx ON users(event_count, user_id);

        CREATE TABLE user_steps AS
        SELECT user_id, source_step, COUNT(*) AS event_count
        FROM events
        GROUP BY user_id, source_step;
        CREATE INDEX user_steps_quantile_idx ON user_steps(event_count, user_id, source_step);
        """
    )


def generated_at(value: str | None) -> str:
    if value is None:
        return prepare.iso_z(datetime.now(timezone.utc))
    return prepare.iso_z(prepare.parse_base_time(value))


def process(args: argparse.Namespace) -> dict[str, Any]:
    if not args.input.exists():
        raise SystemExit(f"ERROR: input file not found: {args.input}")
    if args.limit is not None and args.limit < 0:
        raise SystemExit("ERROR: --limit must be >= 0")
    if args.output.exists() and not args.force:
        raise SystemExit(f"ERROR: output file already exists. Use --force to overwrite: {args.output}")

    base_time = prepare.parse_base_time(args.base_time)
    salt_args = argparse.Namespace(
        hash_salt=args.hash_salt,
        hash_salt_env=args.hash_salt_env,
        require_non_default_salt=False,
    )
    salt, _ = prepare.resolve_salt(salt_args)
    input_sha256 = prepare.sha256_file(args.input)

    total_rows = 0
    accepted_rows = 0
    rejected_rows = 0
    fraud_rows = 0
    rejected_reasons: dict[str, int] = {}

    with tempfile.TemporaryDirectory(prefix="paysim-v3-profile-") as temp_dir:
        database_path = Path(temp_dir) / "profile.sqlite3"
        connection = sqlite3.connect(database_path)
        initialize_database(connection)
        event_batch: list[tuple[str, int, float, str, str, str]] = []

        with args.input.open("r", encoding="utf-8-sig", newline="") as input_file:
            reader = csv.DictReader(input_file)
            prepare.validate_header(reader.fieldnames)
            for row_number, row in enumerate(reader, start=1):
                if args.limit is not None and total_rows >= args.limit:
                    break
                total_rows += 1
                try:
                    event, label = prepare.normalize_row(row, row_number, base_time, salt)
                except prepare.RowRejected as exc:
                    rejected_rows += 1
                    rejected_reasons[exc.reason] = rejected_reasons.get(exc.reason, 0) + 1
                    continue

                accepted_rows += 1
                fraud_rows += int(label["isFraud"])
                user_id = event["userId"]
                source_step = int(label["sourceStep"])
                amount = Decimal(event["amount"])
                event_batch.append(
                    (
                        user_id,
                        source_step,
                        float(amount),
                        event["amount"],
                        event["nativeEventType"],
                        event["normalizedEventType"],
                    )
                )
                if len(event_batch) >= 10000:
                    connection.executemany(
                        "INSERT INTO events VALUES (?, ?, ?, ?, ?, ?)",
                        event_batch,
                    )
                    connection.commit()
                    event_batch.clear()
                if total_rows % 100000 == 0:
                    print(f"profiled rows: {total_rows}")
        if event_batch:
            connection.executemany("INSERT INTO events VALUES (?, ?, ?, ?, ?, ?)", event_batch)
        connection.commit()
        build_aggregates(connection)
        connection.commit()

        unique_users = int(connection.execute("SELECT COUNT(*) FROM users").fetchone()[0])
        user_step_groups = int(connection.execute("SELECT COUNT(*) FROM user_steps").fetchone()[0])
        top_user_count = max(1, math.ceil(unique_users * 0.01)) if unique_users else 0
        top_user_events = 0
        if top_user_count:
            top_user_events = int(
                connection.execute(
                    "SELECT COALESCE(SUM(event_count), 0) FROM "
                    "(SELECT event_count FROM users ORDER BY event_count DESC, user_id LIMIT ?)",
                    (top_user_count,),
                ).fetchone()[0]
            )

        users_with_2 = int(
            connection.execute(
                "SELECT COUNT(DISTINCT user_id) FROM user_steps WHERE event_count >= 2"
            ).fetchone()[0]
        )
        users_with_5 = int(
            connection.execute(
                "SELECT COUNT(DISTINCT user_id) FROM user_steps WHERE event_count >= 5"
            ).fetchone()[0]
        )
        repeated_user_events = int(
            connection.execute(
                "SELECT COALESCE(SUM(event_count), 0) FROM users WHERE event_count >= 2"
            ).fetchone()[0]
        )

        native_type_rows = connection.execute(
            "SELECT native_event_type, COUNT(*) FROM events GROUP BY native_event_type ORDER BY native_event_type"
        ).fetchall()
        normalized_type_rows = connection.execute(
            "SELECT normalized_event_type, COUNT(*) FROM events "
            "GROUP BY normalized_event_type ORDER BY normalized_event_type"
        ).fetchall()
        normalized_type_distribution = {
            str(event_type): {"count": int(count), "ratio": ratio(int(count), accepted_rows)}
            for event_type, count in normalized_type_rows
        }
        time_rows = connection.execute(
            "SELECT source_step, COUNT(*) FROM events GROUP BY source_step ORDER BY source_step"
        ).fetchall()
        peak_time_step_count = max((int(row[1]) for row in time_rows), default=0)

        report = {
            "profileSchemaVersion": PROFILE_SCHEMA_VERSION,
            "profilerVersion": PROFILER_VERSION,
            "datasetSlug": args.dataset_slug,
            "rawFileName": args.input.name,
            "inputSha256": input_sha256,
            "acceptedRows": accepted_rows,
            "rejectedRows": rejected_rows,
            "totalEvents": accepted_rows,
            "uniqueUsers": unique_users,
            "eventsPerUser": integer_quantiles(
                connection,
                "SELECT event_count AS value FROM users ORDER BY event_count, user_id",
                unique_users,
            ),
            "top1PercentTrafficShare": ratio(top_user_events, accepted_rows),
            "eventsPerUserPerSourceStep": integer_quantiles(
                connection,
                "SELECT event_count AS value FROM user_steps ORDER BY event_count, user_id, source_step",
                user_step_groups,
            ),
            "maximumEventsPerSourceStepPerUser": int(
                connection.execute("SELECT COALESCE(MAX(event_count), 0) FROM user_steps").fetchone()[0]
            ),
            "usersWith2PlusEventsPerSourceStepRatio": ratio(users_with_2, unique_users),
            "usersWith5PlusEventsPerSourceStepRatio": ratio(users_with_5, unique_users),
            "nativeTransactionTypeDistribution": {
                str(event_type): {"count": int(count), "ratio": ratio(int(count), accepted_rows)}
                for event_type, count in native_type_rows
            },
            "normalizedTransactionTypeDistribution": normalized_type_distribution,
            "transactionTypeDistribution": normalized_type_distribution,
            "fraudRatio": ratio(fraud_rows, accepted_rows),
            "amount": amount_quantiles(connection, accepted_rows),
            "timeStepDistribution": {str(step): int(count) for step, count in time_rows},
            "peakTimeStepCount": peak_time_step_count,
            "sameUserRepeatRate": ratio(repeated_user_events, accepted_rows),
            "rejectedReasonDistribution": dict(sorted(rejected_reasons.items())),
            "identifierPolicyVersion": IDENTIFIER_POLICY_VERSION,
            "sourceTimeResolution": SOURCE_TIME_RESOLUTION,
            "quantileMethod": QUANTILE_METHOD,
            "topPercentRoundingRule": TOP_PERCENT_ROUNDING_RULE,
            "sourceStepBoundaryRule": SOURCE_STEP_BOUNDARY_RULE,
            "generatedAt": generated_at(args.generated_at),
        }
        connection.close()

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return report


def main() -> int:
    args = parse_args()
    report = process(args)
    print(
        "Profiled PaySim dataset: "
        f"accepted={report['acceptedRows']} rejected={report['rejectedRows']} output={args.output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
