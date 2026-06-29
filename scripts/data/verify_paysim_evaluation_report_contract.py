#!/usr/bin/env python3
"""Verify the Phase 7 PaySim evaluation report contract with local fixtures."""

from __future__ import annotations

import importlib.util
import json
import re
import sys
import tempfile
from pathlib import Path
from types import SimpleNamespace
from typing import Any


SCRIPT_PATH = Path(__file__).with_name("evaluate_paysim_replay_results.py")
SPEC = importlib.util.spec_from_file_location("evaluate_paysim_replay_results", SCRIPT_PATH)
evaluate = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules["evaluate_paysim_replay_results"] = evaluate
SPEC.loader.exec_module(evaluate)

RAW_IDENTIFIER_PATTERN = re.compile(r"\b[CM]\d{3,}\b")
FORBIDDEN_TEXT = ("requestBody", "responseBody", "token", "authToken", "authorization", "nameOrig", "nameDest")
REQUIRED_TOP_LEVEL_FIELDS = {
    "scriptVersion",
    "reportSchemaVersion",
    "recordFailurePolicy",
    "pipelineFailureCountingPolicy",
    "invalidRecordCountingPolicy",
    "totalEvents",
    "totalFraudLabels",
    "totalNonFraudLabels",
    "fraudLabeledEvents",
    "evaluatedFraudLabeledEvents",
    "detectedFraudEvents",
    "missedFraudEvents",
    "evaluatedMissedFraudEvents",
    "falsePositiveEvents",
    "truePositiveEvents",
    "trueNegativeEvents",
    "missingFraudLabels",
    "missingNonFraudLabels",
    "misclassifiedEvents",
    "unmatchedResultEvents",
    "evaluationExcludedRecords",
    "failedRecords",
    "invalidRecords",
    "missingResultTreatment",
    "confusionMatrix",
    "metrics",
    "riskLevelCounts",
    "ruleCodeCounts",
    "warnings",
}
REQUIRED_METRICS = {"precision", "recall", "f1Score", "falsePositiveRate", "falseNegativeRate", "accuracy"}
REQUIRED_MATRIX = {"truePositive", "falsePositive", "trueNegative", "falseNegative"}


class ContractError(Exception):
    pass


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text("\n".join(json.dumps(row, sort_keys=True) for row in rows) + "\n", encoding="utf-8")


def assert_contains_keys(container: dict[str, Any], required: set[str], source: str) -> None:
    missing = sorted(required - set(container))
    if missing:
        raise ContractError(f"{source} missing required keys: {', '.join(missing)}")


def verify_report(report: dict[str, Any], report_path: Path) -> None:
    assert_contains_keys(report, REQUIRED_TOP_LEVEL_FIELDS, "report")
    assert_contains_keys(report["metrics"], REQUIRED_METRICS, "report.metrics")
    assert_contains_keys(report["confusionMatrix"], REQUIRED_MATRIX, "report.confusionMatrix")
    if not report_path.exists():
        raise ContractError(f"report file was not created: {report_path}")
    text = report_path.read_text(encoding="utf-8")
    for forbidden in FORBIDDEN_TEXT:
        if forbidden in text:
            raise ContractError(f"report contains forbidden text: {forbidden}")
    if RAW_IDENTIFIER_PATTERN.search(text):
        raise ContractError("report contains raw PaySim identifier pattern")
    if report["metrics"]["f1Score"] is None:
        raise ContractError("fixture report should produce f1Score")
    if report["missingResultTreatment"] != "missing_results_excluded_from_denominator":
        raise ContractError("missing results should be excluded by default")
    if report["recordFailurePolicy"] != "fail_fast_before_report_generation":
        raise ContractError("record failure policy should document fail-fast behavior")
    if report["failedRecords"] != 0 or report["invalidRecords"] != 0:
        raise ContractError("fixture report should not report pipeline failed/invalid records")
    expected_values = {
        "totalLabels": 3,
        "totalFraudLabels": 2,
        "totalNonFraudLabels": 1,
        "totalResults": 2,
        "matchedResults": 2,
        "missingResults": 1,
        "missingFraudLabels": 1,
        "missingNonFraudLabels": 0,
        "evaluatedEvents": 2,
        "totalEvents": 2,
        "fraudLabeledEvents": 1,
        "evaluatedFraudLabeledEvents": 1,
        "truePositiveEvents": 1,
        "trueNegativeEvents": 1,
        "falsePositiveEvents": 0,
        "missedFraudEvents": 0,
        "evaluatedMissedFraudEvents": 0,
        "misclassifiedEvents": 0,
        "unmatchedResultEvents": 0,
        "evaluationExcludedRecords": 0,
    }
    for key, expected in expected_values.items():
        actual = report.get(key)
        if actual != expected:
            raise ContractError(f"{key} expected {expected}, got {actual}")


def main() -> int:
    with tempfile.TemporaryDirectory() as temp_dir:
        root = Path(temp_dir)
        labels = root / "labels.jsonl"
        results = root / "results.jsonl"
        output = root / "evaluation-report.json"
        write_jsonl(
            labels,
            [
                {"eventId": "paysim-1", "isFraud": True, "sourceFlaggedFraud": False, "sourceStep": 1, "sourceType": "TRANSFER"},
                {"eventId": "paysim-2", "isFraud": False, "sourceFlaggedFraud": False, "sourceStep": 1, "sourceType": "PAYMENT"},
                {"eventId": "paysim-3", "isFraud": True, "sourceFlaggedFraud": False, "sourceStep": 2, "sourceType": "CASH_OUT"},
            ],
        )
        write_jsonl(
            results,
            [
                {"eventId": "paysim-1", "riskLevel": "HIGH", "riskScore": 80, "ruleCodes": ["BALANCE_DRAIN"]},
                {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 0, "ruleCodes": []},
            ],
        )
        args = SimpleNamespace(
            labels=labels,
            results=results,
            replay_report=None,
            output=output,
            positive_risk_level="MEDIUM",
            event_id_prefix=None,
            exclude_replay_rejected=True,
            include_missing_results=False,
            force=True,
            strict=True,
        )
        report = evaluate.evaluate(args)
        verify_report(report, output)
    print("PASS: PaySim evaluation report contract verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
