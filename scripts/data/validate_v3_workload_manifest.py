#!/usr/bin/env python3
"""Validate the CI-safe subset of the V3 workload manifest contract."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

import jsonschema

SCHEMA_PATH = Path(__file__).parents[2] / "load-test" / "workloads" / "v3" / "workload-manifest.schema.json"
STATEFUL_WINDOW_ROLES = {"STATEFUL_WINDOW_SCALING", "STATEFUL_REDELIVERY"}


class ManifestError(ValueError):
    pass


def validate_distribution(distribution: dict[str, Any] | None, field: str) -> None:
    if distribution is None:
        return
    if not isinstance(distribution, dict) or not distribution:
        raise ManifestError(f"{field} must be null or a non-empty object")
    total = 0.0
    for key, value in distribution.items():
        if not isinstance(key, str) or not isinstance(value, (int, float)) or isinstance(value, bool):
            raise ManifestError(f"{field} must map string keys to numeric shares")
        if value < 0 or value > 1:
            raise ManifestError(f"{field} shares must be between 0 and 1")
        total += float(value)
    if abs(total - 1.0) > 0.000001:
        raise ManifestError(f"{field} shares must sum to 1")


def validate_manifest(manifest: dict[str, Any]) -> None:
    validate_schema(manifest)
    validate_distribution(manifest["targetPartitionDistribution"], "targetPartitionDistribution")
    validate_stages(manifest)

    mode = manifest["eventTimeMode"]
    role = manifest["workloadRole"]
    if role in {"NORMAL_CAPACITY", "ORGANIC_BURST"} and mode != "REBASE_TO_ARRIVAL":
        raise ManifestError(f"{role} requires REBASE_TO_ARRIVAL")
    if role == "HISTORICAL_REPLAY" and mode != "PRESERVE_SOURCE_TIME":
        raise ManifestError("HISTORICAL_REPLAY requires PRESERVE_SOURCE_TIME")
    if role in {"CATCH_UP_BURST", "LATE_OUT_OF_ORDER"} and mode != "CONTROLLED_LATENESS":
        raise ManifestError(f"{role} requires CONTROLLED_LATENESS")
    if role == "PARTITION_SKEW":
        if manifest["userDistribution"] != "PARTITION_AFFINITY":
            raise ManifestError("PARTITION_SKEW requires PARTITION_AFFINITY")
        if manifest["targetPartitionDistribution"] is None or not manifest["partitionAffinityStrategy"]:
            raise ManifestError("PARTITION_SKEW requires target distribution and affinity strategy")
        validate_partition_skew(manifest)
    if role == "USER_SKEW" and manifest["targetUserConcentration"] is None:
        raise ManifestError("USER_SKEW requires targetUserConcentration")
    if role in STATEFUL_WINDOW_ROLES:
        validate_stateful_window_profile(manifest)
    elif manifest["statefulWindowProfile"] is not None:
        raise ManifestError("statefulWindowProfile is only allowed for stateful workload roles")
    if role == "LATE_OUT_OF_ORDER":
        validate_lateness_profile(manifest)
    elif manifest["latenessProfile"] is not None:
        raise ManifestError("latenessProfile is only allowed for LATE_OUT_OF_ORDER workloads")


def validate_partition_skew(manifest: dict[str, Any]) -> None:
    if manifest["eventTimeMode"] != "REBASE_TO_ARRIVAL":
        raise ManifestError("PARTITION_SKEW requires REBASE_TO_ARRIVAL")
    if manifest["sourceProfile"] != "NORMAL":
        raise ManifestError("PARTITION_SKEW requires NORMAL sourceProfile")
    if manifest["targetUserConcentration"] is not None:
        raise ManifestError("PARTITION_SKEW must not use targetUserConcentration")
    if manifest["heavyUserRatio"] != 0:
        raise ManifestError("PARTITION_SKEW must keep heavyUserRatio at 0")
    if manifest["partitionAffinityStrategy"] != "KAFKA_MURMUR2_LOCAL_6_PARTITIONS":
        raise ManifestError("unsupported partitionAffinityStrategy for PARTITION_SKEW")

    distribution = manifest["targetPartitionDistribution"]
    partitions = sorted(int(partition) for partition in distribution.keys())
    expected_partitions = list(range(len(partitions)))
    if partitions != expected_partitions:
        raise ManifestError("targetPartitionDistribution must use contiguous partitions from 0")
    if len(partitions) != 6:
        raise ManifestError("KAFKA_MURMUR2_LOCAL_6_PARTITIONS requires exactly 6 partitions")


def validate_stateful_window_profile(manifest: dict[str, Any]) -> None:
    profile = manifest["statefulWindowProfile"]
    if profile is None:
        raise ManifestError("stateful workload roles require statefulWindowProfile")

    duration_seconds = parse_duration_seconds(manifest["duration"])
    runtime_window_seconds = parse_duration_seconds(profile["runtimeWindow"])
    if duration_seconds > runtime_window_seconds:
        raise ManifestError("stateful workload duration must fit inside runtimeWindow")

    expected_average = manifest["eventLimit"] / manifest["userCardinality"]
    expected_max = (manifest["eventLimit"] + manifest["userCardinality"] - 1) // manifest["userCardinality"]
    if abs(profile["expectedEventsPerUserInWindow"] - expected_average) > 0.000001:
        raise ManifestError("expectedEventsPerUserInWindow must equal eventLimit / userCardinality")
    if profile["expectedMaxEventsPerUserInWindow"] != expected_max:
        raise ManifestError("expectedMaxEventsPerUserInWindow must equal ceil(eventLimit / userCardinality)")

    expected_amount_sum = expected_average * profile["eventAmount"]
    if abs(profile["expectedAmountSumPerUserInWindow"] - expected_amount_sum) > 0.000001:
        raise ManifestError("expectedAmountSumPerUserInWindow must equal expectedEventsPerUserInWindow * eventAmount")

    if manifest["workloadRole"] == "STATEFUL_REDELIVERY":
        validate_stateful_redelivery_profile(manifest, profile)


def validate_stateful_redelivery_profile(manifest: dict[str, Any], profile: dict[str, Any]) -> None:
    required_fields = [
        "redeliveryDrillTargetIndex",
        "redeliveryDrillNextIndex",
        "expectedNextEventTransactionCount",
        "expectedNextEventAmountSum",
        "expectedNextEventRiskScore",
        "expectedNextEventMatchedRule",
    ]
    missing_fields = [field for field in required_fields if field not in profile]
    if missing_fields:
        raise ManifestError(f"STATEFUL_REDELIVERY requires {', '.join(missing_fields)}")

    target_index = profile["redeliveryDrillTargetIndex"]
    next_index = profile["redeliveryDrillNextIndex"]
    if target_index < 3:
        raise ManifestError("redeliveryDrillTargetIndex must be at least 3 for threshold-adjacent evidence")
    if target_index >= manifest["eventLimit"]:
        raise ManifestError("redeliveryDrillTargetIndex must be less than eventLimit")
    if next_index >= manifest["eventLimit"]:
        raise ManifestError("redeliveryDrillNextIndex must be less than eventLimit")
    if next_index <= target_index:
        raise ManifestError("redeliveryDrillNextIndex must be after redeliveryDrillTargetIndex")

    expected_next_count = next_index + 1
    if profile["expectedNextEventTransactionCount"] != expected_next_count:
        raise ManifestError("expectedNextEventTransactionCount must equal redeliveryDrillNextIndex + 1")

    expected_next_amount = expected_next_count * profile["eventAmount"]
    if abs(profile["expectedNextEventAmountSum"] - expected_next_amount) > 0.000001:
        raise ManifestError("expectedNextEventAmountSum must equal expectedNextEventTransactionCount * eventAmount")


def validate_lateness_profile(manifest: dict[str, Any]) -> None:
    profile = manifest["latenessProfile"]
    if profile is None:
        raise ManifestError("LATE_OUT_OF_ORDER requires latenessProfile")
    if manifest["sourceProfile"] not in {"SLOW_SOURCE", "BATCH_CATCHUP"}:
        raise ManifestError("LATE_OUT_OF_ORDER requires SLOW_SOURCE or BATCH_CATCHUP sourceProfile")
    if manifest["targetPartitionDistribution"] is not None or manifest["partitionAffinityStrategy"] is not None:
        raise ManifestError("LATE_OUT_OF_ORDER must not use partition affinity")
    if manifest["targetUserConcentration"] is not None or manifest["heavyUserRatio"] != 0:
        raise ManifestError("LATE_OUT_OF_ORDER must keep user distribution uniform")

    allowed_lateness_seconds = parse_duration_seconds(profile["allowedLateness"])
    too_late_age_seconds = parse_duration_seconds(profile["tooLateAge"])
    if too_late_age_seconds <= allowed_lateness_seconds:
        raise ManifestError("tooLateAge must be greater than allowedLateness")

    buckets = profile["buckets"]
    bucket_names = [bucket["name"] for bucket in buckets]
    if len(set(bucket_names)) != len(bucket_names):
        raise ManifestError("latenessProfile bucket names must be unique")

    too_late_bucket_count = 0
    accepted_late_bucket_count = 0
    for bucket in buckets:
        lateness_seconds = parse_non_negative_duration_seconds(bucket["lateness"])
        if lateness_seconds > allowed_lateness_seconds:
            too_late_bucket_count += 1
        else:
            accepted_late_bucket_count += 1

    full_cycles, remainder = divmod(manifest["eventLimit"], len(buckets))
    expected_too_late = too_late_bucket_count * full_cycles
    expected_accepted = accepted_late_bucket_count * full_cycles
    for bucket in buckets[:remainder]:
        lateness_seconds = parse_non_negative_duration_seconds(bucket["lateness"])
        if lateness_seconds > allowed_lateness_seconds:
            expected_too_late += 1
        else:
            expected_accepted += 1

    if profile["expectedTooLateEvents"] != expected_too_late:
        raise ManifestError("expectedTooLateEvents must match buckets, eventLimit, and allowedLateness")
    if profile["expectedAcceptedLateEvents"] != expected_accepted:
        raise ManifestError("expectedAcceptedLateEvents must match buckets, eventLimit, and allowedLateness")

    out_of_order_pattern = profile.get("outOfOrderPattern")
    if out_of_order_pattern is not None:
        unknown = [name for name in out_of_order_pattern if name not in bucket_names]
        if unknown:
            raise ManifestError("outOfOrderPattern must reference latenessProfile bucket names")
        pattern_lateness = [
            parse_non_negative_duration_seconds(bucket["lateness"])
            for name in out_of_order_pattern
            for bucket in buckets
            if bucket["name"] == name
        ]
        if pattern_lateness == sorted(pattern_lateness):
            raise ManifestError("outOfOrderPattern must contain a non-monotonic lateness sequence")


def validate_stages(manifest: dict[str, Any]) -> None:
    stages = manifest.get("stages")
    if stages is None:
        expected_events = manifest["targetEps"] * parse_duration_seconds(manifest["duration"])
        if abs(manifest["eventLimit"] - expected_events) > 0.000001:
            raise ManifestError("eventLimit must equal targetEps * duration seconds")
        return
    expected_events = 0
    max_stage_eps = 0
    for stage in stages:
        target_eps = stage["targetEps"]
        duration_seconds = parse_duration_seconds(stage["duration"])
        expected_events += int(target_eps * duration_seconds)
        max_stage_eps = max(max_stage_eps, target_eps)
    if manifest["eventLimit"] != expected_events:
        raise ManifestError("eventLimit must equal the sum of stage targetEps * duration seconds")
    if manifest["targetEps"] != max_stage_eps:
        raise ManifestError("targetEps must equal the maximum stage targetEps")


def parse_duration_seconds(value: str) -> int:
    match = re.fullmatch(r"([1-9][0-9]*)(s|m|h)", value)
    if match is None:
        raise ManifestError("duration must use a positive s, m, or h suffix")
    amount = int(match.group(1))
    unit = match.group(2)
    if unit == "s":
        return amount
    if unit == "m":
        return amount * 60
    return amount * 60 * 60


def parse_non_negative_duration_seconds(value: str) -> int:
    match = re.fullmatch(r"([0-9]+)(s|m|h)", value)
    if match is None:
        raise ManifestError("duration must use a non-negative s, m, or h suffix")
    amount = int(match.group(1))
    unit = match.group(2)
    if unit == "s":
        return amount
    if unit == "m":
        return amount * 60
    return amount * 60 * 60


def validate_schema(manifest: dict[str, Any]) -> None:
    try:
        schema = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
        jsonschema.Draft202012Validator.check_schema(schema)
        jsonschema.Draft202012Validator(schema).validate(manifest)
    except (OSError, json.JSONDecodeError, jsonschema.SchemaError) as exc:
        raise ManifestError(f"cannot load workload schema: {exc}") from exc
    except jsonschema.ValidationError as exc:
        path = ".".join(str(part) for part in exc.absolute_path)
        location = path or "manifest"
        raise ManifestError(f"schema validation failed at {location}: {exc.message}") from exc


def load_and_validate(path: Path) -> dict[str, Any]:
    try:
        manifest = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ManifestError(f"cannot read manifest: {exc}") from exc
    if not isinstance(manifest, dict):
        raise ManifestError("manifest root must be an object")
    validate_manifest(manifest)
    return manifest


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate a V3 workload manifest.")
    parser.add_argument("manifest", nargs="+", type=Path)
    args = parser.parse_args()
    for manifest_path in args.manifest:
        try:
            manifest = load_and_validate(manifest_path)
        except ManifestError as exc:
            print(f"ERROR: {manifest_path}: {exc}")
            return 1
        print(f"Valid V3 workload manifest: {manifest['workloadId']} {manifest['workloadVersion']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
