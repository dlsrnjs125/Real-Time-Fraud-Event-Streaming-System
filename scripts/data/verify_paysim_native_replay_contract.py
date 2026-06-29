#!/usr/bin/env python3
"""Verify PaySim native type replay/evaluation contract with fixtures."""

from __future__ import annotations

import importlib.util
import json
import re
import sys
import tempfile
from pathlib import Path
from types import SimpleNamespace
from typing import Any


SCRIPT_DIR = Path(__file__).parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


replay = load_module("replay_paysim_events", SCRIPT_DIR / "replay_paysim_events.py")
evaluate = load_module("evaluate_paysim_replay_results", SCRIPT_DIR / "evaluate_paysim_replay_results.py")

RAW_IDENTIFIER_PATTERN = re.compile(r"\b[CM]\d{3,}\b")
FORBIDDEN_TEXT = ("requestBody", "responseBody", "token", "authToken", "authorization", "nameOrig", "nameDest")
REQUIRED_REPLAY_FIELDS = {
    "mappingMetadataPolicy",
    "missingMappingMetadata",
    "mappingPolicyVersion",
    "mappingPolicyVersions",
    "nativeTypeDistribution",
    "normalizedTypeDistribution",
    "typeSupportLevelDistribution",
    "inputNativeTypeDistribution",
    "acceptedNormalizedTypeDistribution",
    "rejectedNativeTypeDistribution",
    "excludedByType",
    "unsupportedEventTypes",
}
REQUIRED_EVALUATION_FIELDS = {
    "mappingMetadataPolicy",
    "replayMissingMappingMetadata",
    "mappingPolicyVersion",
    "replayNativeTypeDistribution",
    "evaluatedNativeTypeDistribution",
    "excludedNativeTypeDistribution",
    "excludedByType",
    "totalFraudLabels",
    "evaluatedFraudLabeledEvents",
    "missingFraudLabels",
    "evaluatedMissedFraudEvents",
}


class ContractError(Exception):
    pass


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text("\n".join(json.dumps(row, sort_keys=True) for row in rows) + "\n", encoding="utf-8")


def event(event_id: str, event_type: str, native_type: str | None = None, support_level: str = "production-supported") -> dict[str, Any]:
    native = native_type or event_type
    return {
        "eventId": event_id,
        "userId": "U-abcdef1234567890",
        "accountId": "A-abcdef1234567890",
        "destinationAccountId": "D-fedcba0987654321",
        "eventType": event_type,
        "nativeEventType": native,
        "normalizedEventType": event_type,
        "typeSupportLevel": support_level,
        "typeMappingPolicyVersion": "paysim-native-mapping-v1",
        "amount": "100.00",
        "currency": "KRW",
        "eventTime": "2026-01-01T01:00:00Z",
        "traceId": f"trace-{event_id}",
        "schemaVersion": "v2-paysim",
        "source": "PAYSIM",
        "balanceFeatures": {"sourceStep": 1},
    }


def assert_keys(value: dict[str, Any], required: set[str], source: str) -> None:
    missing = sorted(required - set(value))
    if missing:
        raise ContractError(f"{source} missing required keys: {', '.join(missing)}")


def assert_sensitive_free(value: dict[str, Any], source: str) -> None:
    text = json.dumps(value, sort_keys=True)
    for forbidden in FORBIDDEN_TEXT:
        if forbidden in text:
            raise ContractError(f"{source} contains forbidden text: {forbidden}")
    if RAW_IDENTIFIER_PATTERN.search(text):
        raise ContractError(f"{source} contains raw PaySim identifier pattern")


def verify_replay_report(report: dict[str, Any]) -> None:
    assert_keys(report, REQUIRED_REPLAY_FIELDS, "replay report")
    expected = {
        "mappingPolicyVersion": "paysim-native-mapping-v1",
        "mappingMetadataPolicy": "required_for_phase8_paysim_native_contract",
        "missingMappingMetadata": 0,
        "nativeTypeDistribution": {"CASH_OUT": 1, "DEBIT": 1, "PAYMENT": 1, "TRANSFER": 1},
        "normalizedTypeDistribution": {"PAYMENT": 1, "TRANSFER": 1, "WITHDRAWAL": 1},
        "typeSupportLevelDistribution": {"production-supported": 2, "replay-supported": 1},
        "inputNormalizedTypeDistribution": {"DEBIT": 1, "PAYMENT": 1, "TRANSFER": 1, "WITHDRAWAL": 1},
        "acceptedNormalizedTypeDistribution": {"PAYMENT": 1, "TRANSFER": 1, "WITHDRAWAL": 1},
        "rejectedNativeTypeDistribution": {"DEBIT": 1},
        "excludedByType": {"DEBIT": 1},
        "unsupportedEventTypes": {"DEBIT": 1},
        "payloadAccepted": 3,
        "payloadRejected": 1,
    }
    for key, expected_value in expected.items():
        actual = report.get(key)
        if actual != expected_value:
            raise ContractError(f"replay {key} expected {expected_value}, got {actual}")
    assert_sensitive_free(report, "replay report")


def verify_evaluation_report(report: dict[str, Any]) -> None:
    assert_keys(report, REQUIRED_EVALUATION_FIELDS, "evaluation report")
    expected = {
        "mappingPolicyVersion": "paysim-native-mapping-v1",
        "mappingMetadataPolicy": "required_for_phase8_paysim_native_contract",
        "replayMissingMappingMetadata": 0,
        "nativeTypeDistribution": {"CASH_OUT": 1, "DEBIT": 1, "PAYMENT": 1, "TRANSFER": 1},
        "replayNativeTypeDistribution": {"CASH_OUT": 1, "DEBIT": 1, "PAYMENT": 1, "TRANSFER": 1},
        "evaluatedNativeTypeDistribution": {"CASH_OUT": 1, "PAYMENT": 1, "TRANSFER": 1},
        "evaluatedNormalizedTypeDistribution": {"PAYMENT": 1, "TRANSFER": 1, "WITHDRAWAL": 1},
        "excludedNativeTypeDistribution": {"DEBIT": 1},
        "excludedByType": {"DEBIT": 1},
        "totalLabels": 3,
        "evaluatedEvents": 3,
        "totalFraudLabels": 1,
        "evaluatedFraudLabeledEvents": 1,
        "missingFraudLabels": 0,
        "evaluatedMissedFraudEvents": 0,
    }
    for key, expected_value in expected.items():
        actual = report.get(key)
        if actual != expected_value:
            raise ContractError(f"evaluation {key} expected {expected_value}, got {actual}")
    assert_sensitive_free(report, "evaluation report")


def main() -> int:
    with tempfile.TemporaryDirectory() as temp_dir:
        root = Path(temp_dir)
        events = root / "events.jsonl"
        replay_report_path = root / "replay-report.json"
        labels = root / "labels.jsonl"
        results = root / "results.jsonl"
        evaluation_report_path = root / "evaluation-report.json"
        write_jsonl(
            events,
            [
                event("paysim-1", "PAYMENT"),
                event("paysim-2", "TRANSFER"),
                event("paysim-3", "WITHDRAWAL", native_type="CASH_OUT", support_level="replay-supported"),
                event("paysim-4", "DEBIT", support_level="unsupported"),
            ],
        )
        replay_args = SimpleNamespace(
            input=events,
            endpoint="http://localhost:8080/api/v1/transactions/events",
            max_events=None,
            rate_per_second=1000.0,
            timeout_seconds=3.0,
            report_output=replay_report_path,
            dry_run=True,
            force=True,
            event_id_prefix=None,
            idempotency_mode="preserve",
            event_type_policy="current-api",
            retry_count=0,
            retry_connection_error=False,
            stop_on_error=False,
            auth_token=None,
            auth_token_env="PAYSIM_REPLAY_AUTH_TOKEN",
        )
        replay_report = replay.replay(replay_args)
        verify_replay_report(replay_report)

        write_jsonl(
            labels,
            [
                {"eventId": "paysim-1", "isFraud": False, "sourceFlaggedFraud": False, "sourceStep": 1, "sourceType": "PAYMENT"},
                {"eventId": "paysim-2", "isFraud": False, "sourceFlaggedFraud": False, "sourceStep": 1, "sourceType": "TRANSFER"},
                {"eventId": "paysim-3", "isFraud": True, "sourceFlaggedFraud": False, "sourceStep": 1, "sourceType": "CASH_OUT"},
            ],
        )
        write_jsonl(
            results,
            [
                {"eventId": "paysim-1", "riskLevel": "LOW", "riskScore": 0, "ruleCodes": []},
                {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 0, "ruleCodes": []},
                {"eventId": "paysim-3", "riskLevel": "HIGH", "riskScore": 80, "ruleCodes": ["BALANCE_DRAIN"]},
            ],
        )
        evaluation_args = SimpleNamespace(
            labels=labels,
            results=results,
            replay_report=replay_report_path,
            output=evaluation_report_path,
            positive_risk_level=None,
            threshold_version="threshold-v1",
            rule_version="rule-v2-baseline-v1",
            evaluation_policy_version="evaluation-policy-v1",
            event_id_prefix=None,
            exclude_replay_rejected=True,
            include_missing_results=False,
            force=True,
            strict=True,
        )
        evaluation_report = evaluate.evaluate(evaluation_args)
        verify_evaluation_report(evaluation_report)
    print("PASS: PaySim native replay contract verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
