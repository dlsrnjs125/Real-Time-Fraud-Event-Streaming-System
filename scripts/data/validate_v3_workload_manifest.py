#!/usr/bin/env python3
"""Validate the CI-safe subset of the V3 workload manifest contract."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any

import jsonschema

SCHEMA_PATH = Path(__file__).parents[2] / "load-test" / "workloads" / "v3" / "workload-manifest.schema.json"


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
    if role == "USER_SKEW" and manifest["targetUserConcentration"] is None:
        raise ManifestError("USER_SKEW requires targetUserConcentration")


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
    parser.add_argument("manifest", type=Path)
    args = parser.parse_args()
    try:
        manifest = load_and_validate(args.manifest)
    except ManifestError as exc:
        print(f"ERROR: {exc}")
        return 1
    print(f"Valid V3 workload manifest: {manifest['workloadId']} {manifest['workloadVersion']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
