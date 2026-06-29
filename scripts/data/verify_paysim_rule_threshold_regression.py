#!/usr/bin/env python3
"""Verify Phase 9 PaySim rule/threshold regression evidence with fixtures."""

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


evaluate = load_module("evaluate_paysim_replay_results", SCRIPT_DIR / "evaluate_paysim_replay_results.py")

RAW_IDENTIFIER_PATTERN = re.compile(r"\b[CM]\d{3,}\b")
FORBIDDEN_TEXT = ("requestBody", "responseBody", "token", "authToken", "authorization", "nameOrig", "nameDest")
REQUIRED_FIELDS = {
    "reportSchemaVersion",
    "evaluationContractVersion",
    "evaluationPolicyVersion",
    "mappingPolicyVersion",
    "ruleVersion",
    "thresholdVersion",
    "thresholdPolicy",
    "mediumRiskThreshold",
    "highRiskThreshold",
    "reviewCandidateEvents",
    "reviewCandidateRate",
    "blockedCandidateEvents",
    "blockedCandidateRate",
    "actionDecisionDistribution",
    "operatorWorkloadSummary",
    "replayNativeTypeDistribution",
    "evaluatedNativeTypeDistribution",
    "excludedByType",
    "missingResultTreatment",
}


class ContractError(Exception):
    pass


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text("\n".join(json.dumps(row, sort_keys=True) for row in rows) + "\n", encoding="utf-8")


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.write_text(json.dumps(value, sort_keys=True), encoding="utf-8")


def assert_sensitive_free(value: dict[str, Any], source: str) -> None:
    text = json.dumps(value, sort_keys=True)
    for forbidden in FORBIDDEN_TEXT:
        if forbidden in text:
            raise ContractError(f"{source} contains forbidden text: {forbidden}")
    if RAW_IDENTIFIER_PATTERN.search(text):
        raise ContractError(f"{source} contains raw PaySim identifier pattern")


def assert_required_fields(report: dict[str, Any]) -> None:
    missing = sorted(REQUIRED_FIELDS - set(report))
    if missing:
        raise ContractError(f"report missing required fields: {', '.join(missing)}")


def verify_baseline(report: dict[str, Any]) -> None:
    assert_required_fields(report)
    expected = {
        "reportSchemaVersion": "2026-06-v2-phase9",
        "evaluationContractVersion": "v2-phase9-evaluation-contract-v1",
        "evaluationPolicyVersion": "evaluation-policy-v1",
        "mappingPolicyVersion": "paysim-native-mapping-v1",
        "ruleVersion": "rule-v2-baseline-v1",
        "thresholdVersion": "threshold-v1",
        "totalLabels": 4,
        "evaluatedEvents": 3,
        "missingResults": 0,
        "excludedReplayRejected": 1,
        "truePositiveEvents": 1,
        "falsePositiveEvents": 1,
        "trueNegativeEvents": 1,
        "missedFraudEvents": 0,
        "reviewCandidateEvents": 2,
        "blockedCandidateEvents": 1,
        "actionDecisionDistribution": {"ALLOW": 1, "BLOCK": 1, "REVIEW": 1},
        "evaluatedNativeTypeDistribution": {"CASH_OUT": 1, "PAYMENT": 1, "TRANSFER": 1},
        "excludedByType": {"DEBIT": 1},
    }
    for key, expected_value in expected.items():
        actual = report.get(key)
        if actual != expected_value:
            raise ContractError(f"{key} expected {expected_value}, got {actual}")
    metrics = report["metrics"]
    if metrics["precision"] != 0.5 or metrics["recall"] != 1.0 or round(metrics["f1Score"], 6) != 0.666667:
        raise ContractError(f"unexpected baseline metrics: {metrics}")
    if report["reviewCandidateRate"] != 2 / 3 or report["blockedCandidateRate"] != 1 / 3:
        raise ContractError("unexpected workload rates")
    if report["missingResultTreatment"] != "missing_results_excluded_from_denominator":
        raise ContractError("missing result default policy changed")
    assert_sensitive_free(report, "baseline report")


def verify_strict_policy(report: dict[str, Any]) -> None:
    if report["thresholdVersion"] != "threshold-strict-v1":
        raise ContractError("strict report did not use threshold-strict-v1")
    if report["reviewCandidateEvents"] != 1 or report["blockedCandidateEvents"] != 0:
        raise ContractError("strict threshold should reduce candidate workload in fixture")
    if report["metrics"]["recall"] != 0.0 or report["missedFraudEvents"] != 1:
        raise ContractError("strict threshold should show missed fraud risk in fixture")


def main() -> int:
    with tempfile.TemporaryDirectory() as temp_dir:
        root = Path(temp_dir)
        labels = root / "labels.jsonl"
        results = root / "results.jsonl"
        replay_report = root / "replay-report.json"
        baseline_output = root / "baseline-report.json"
        strict_output = root / "strict-report.json"
        write_jsonl(
            labels,
            [
                {"eventId": "paysim-1", "isFraud": True, "sourceFlaggedFraud": False, "sourceStep": 1, "sourceType": "TRANSFER"},
                {"eventId": "paysim-2", "isFraud": False, "sourceFlaggedFraud": False, "sourceStep": 1, "sourceType": "PAYMENT"},
                {"eventId": "paysim-3", "isFraud": False, "sourceFlaggedFraud": False, "sourceStep": 1, "sourceType": "CASH_OUT"},
                {"eventId": "paysim-4", "isFraud": True, "sourceFlaggedFraud": False, "sourceStep": 1, "sourceType": "DEBIT"},
            ],
        )
        write_jsonl(
            results,
            [
                {"eventId": "paysim-1", "riskLevel": "MEDIUM", "riskScore": 60, "ruleCodes": ["AMOUNT_RULE"]},
                {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 20, "ruleCodes": []},
                {"eventId": "paysim-3", "riskLevel": "HIGH", "riskScore": 85, "ruleCodes": ["VELOCITY_RULE"]},
            ],
        )
        write_json(
            replay_report,
            {
                "mappingPolicyVersion": "paysim-native-mapping-v1",
                "mappingMetadataPolicy": "required_for_phase8_paysim_native_contract",
                "missingMappingMetadata": 0,
                "payloadRejected": 1,
                "failures": [{"eventId": "paysim-4", "reason": "UNSUPPORTED_NATIVE_TYPE:DEBIT", "rowNumber": 4}],
                "inputNativeTypeDistribution": {"TRANSFER": 1, "PAYMENT": 1, "CASH_OUT": 1, "DEBIT": 1},
                "inputNormalizedTypeDistribution": {"TRANSFER": 1, "PAYMENT": 1, "WITHDRAWAL": 1, "DEBIT": 1},
                "inputTypeSupportLevelDistribution": {"production-supported": 2, "replay-supported": 1, "unsupported": 1},
                "excludedByType": {"DEBIT": 1},
            },
        )
        base_args = SimpleNamespace(
            labels=labels,
            results=results,
            replay_report=replay_report,
            output=baseline_output,
            positive_risk_level="MEDIUM",
            threshold_version="threshold-v1",
            rule_version="rule-v2-baseline-v1",
            evaluation_policy_version="evaluation-policy-v1",
            event_id_prefix=None,
            exclude_replay_rejected=True,
            include_missing_results=False,
            force=True,
            strict=True,
        )
        baseline_report = evaluate.evaluate(base_args)
        verify_baseline(baseline_report)

        strict_args = SimpleNamespace(**{**base_args.__dict__, "threshold_version": "threshold-strict-v1", "output": strict_output})
        strict_report = evaluate.evaluate(strict_args)
        verify_strict_policy(strict_report)
    print("PASS: PaySim rule threshold regression contract verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
