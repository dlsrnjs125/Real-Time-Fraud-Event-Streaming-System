#!/usr/bin/env python3
"""Verify Phase 11 PaySim ruleVersion contract between app-consumer and evaluator."""

from __future__ import annotations

import importlib.util
import json
import re
import sys
import tempfile
from pathlib import Path
from types import SimpleNamespace
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_DIR = ROOT / "scripts" / "data"
JAVA_RULE_VERSION_SOURCE = (
    ROOT
    / "app-consumer"
    / "src"
    / "main"
    / "java"
    / "com"
    / "example"
    / "fraud"
    / "consumer"
    / "rule"
    / "FraudRuleVersions.java"
)
ACTIVE_RULE_VERSION_CONSTANT = "ACTIVE_RULE_VERSION"
EXPECTED_REPORT_SCHEMA_VERSION = "2026-06-v2-phase11"
JAVA_STRING_CONSTANT_PATTERN = re.compile(
    r'public\s+static\s+final\s+String\s+([A-Z0-9_]+)\s*=\s*([^;]+)\s*;'
)

if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from paysim_evaluation_policy import RULE_VERSION, RULE_VERSIONS, validate_rule_version  # noqa: E402


class ContractError(Exception):
    pass


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


evaluate = load_module("evaluate_paysim_replay_results", SCRIPT_DIR / "evaluate_paysim_replay_results.py")


def java_rule_version_constants() -> dict[str, str]:
    if not JAVA_RULE_VERSION_SOURCE.exists():
        raise ContractError(f"Java rule version source missing: {JAVA_RULE_VERSION_SOURCE}")
    text = JAVA_RULE_VERSION_SOURCE.read_text(encoding="utf-8")
    constants: dict[str, str] = {}
    for name, expression in JAVA_STRING_CONSTANT_PATTERN.findall(text):
        expression = expression.strip()
        if expression.startswith('"') and expression.endswith('"'):
            constants[name] = expression.strip('"')
        elif expression in constants:
            constants[name] = constants[expression]
    return constants


def java_rule_version() -> str:
    constants = java_rule_version_constants()
    if ACTIVE_RULE_VERSION_CONSTANT not in constants:
        raise ContractError(f"Java active rule version constant missing: {ACTIVE_RULE_VERSION_CONSTANT}")
    value = constants[ACTIVE_RULE_VERSION_CONSTANT]
    if not value or not value.startswith("rule-v2-"):
        raise ContractError(f"unexpected Java ruleVersion format: {value}")
    return value


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text("\n".join(json.dumps(row, sort_keys=True) for row in rows) + "\n", encoding="utf-8")


def evaluate_fixture(root: Path, results: list[dict[str, Any]], rule_version: str = RULE_VERSION) -> dict[str, Any]:
    labels = root / "labels.jsonl"
    result_path = root / "results.jsonl"
    output = root / "report.json"
    write_jsonl(
        labels,
        [
            {"eventId": "paysim-1", "isFraud": True, "sourceFlaggedFraud": False, "sourceStep": 1, "sourceType": "TRANSFER"},
            {"eventId": "paysim-2", "isFraud": False, "sourceFlaggedFraud": False, "sourceStep": 1, "sourceType": "PAYMENT"},
        ],
    )
    write_jsonl(result_path, results)
    args = SimpleNamespace(
        labels=labels,
        results=result_path,
        replay_report=None,
        output=output,
        positive_risk_level=None,
        threshold_version="threshold-v1",
        rule_version=rule_version,
        evaluation_policy_version="evaluation-policy-v1",
        event_id_prefix=None,
        exclude_replay_rejected=True,
        include_missing_results=False,
        force=True,
        strict=True,
    )
    return evaluate.evaluate(args)


def verify_policy_matches_java(java_version: str) -> None:
    if RULE_VERSION != java_version:
        raise ContractError(f"Python RULE_VERSION expected {java_version}, got {RULE_VERSION}")
    if RULE_VERSIONS != {java_version}:
        raise ContractError(f"Python RULE_VERSIONS expected {{{java_version}}}, got {RULE_VERSIONS}")
    validate_rule_version(java_version)
    try:
        validate_rule_version("rule-v2-drift-v1")
    except ValueError:
        return
    raise ContractError("unsupported ruleVersion did not fail-fast")


def verify_evaluator_contract(java_version: str) -> None:
    with tempfile.TemporaryDirectory() as temp_dir:
        root = Path(temp_dir)
        report = evaluate_fixture(
            root,
            [
                {"eventId": "paysim-1", "riskLevel": "HIGH", "riskScore": 85, "ruleVersion": java_version, "ruleCodes": ["AMOUNT_THRESHOLD"]},
                {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 0, "ruleVersion": java_version, "ruleCodes": []},
            ],
        )
        if report["ruleVersion"] != java_version:
            raise ContractError(f"report ruleVersion expected {java_version}, got {report['ruleVersion']}")
        if report["reportSchemaVersion"] != EXPECTED_REPORT_SCHEMA_VERSION:
            raise ContractError(
                f"reportSchemaVersion expected {EXPECTED_REPORT_SCHEMA_VERSION}, got {report['reportSchemaVersion']}"
            )
        if report["thresholdVersion"] != "threshold-v1":
            raise ContractError("thresholdVersion should remain separate from ruleVersion")
        if report["ruleVersionCoverage"] != {
            "resultsWithRuleVersion": 2,
            "resultsWithoutRuleVersion": 0,
            "coverageRate": 1.0,
            "coverageScope": "evaluated_results_only",
            "ruleVersionSource": "per_result_when_present_otherwise_contract_level",
        }:
            raise ContractError(f"unexpected ruleVersion coverage: {report['ruleVersionCoverage']}")
        if report["ruleVersionDistribution"] != {java_version: 2}:
            raise ContractError(f"unexpected ruleVersion distribution: {report['ruleVersionDistribution']}")

        missing_report = evaluate_fixture(
            root,
            [
                {"eventId": "paysim-1", "riskLevel": "HIGH", "riskScore": 85, "ruleCodes": ["AMOUNT_THRESHOLD"]},
                {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 0, "ruleCodes": []},
            ],
        )
        if missing_report["ruleVersionCoverage"]["resultsWithoutRuleVersion"] != 2:
            raise ContractError("missing per-result ruleVersion should be counted")
        expected_warning = "Some evaluated results do not include per-result ruleVersion; evaluation uses contract-level ruleVersion."
        if expected_warning not in missing_report["warnings"]:
            raise ContractError("missing per-result ruleVersion warning was not reported")

        mixed_report = evaluate_fixture(
            root,
            [
                {"eventId": "paysim-1", "riskLevel": "HIGH", "riskScore": 85, "ruleVersion": java_version, "ruleCodes": ["AMOUNT_THRESHOLD"]},
                {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 0, "ruleCodes": []},
            ],
        )
        if mixed_report["ruleVersionCoverage"]["resultsWithRuleVersion"] != 1:
            raise ContractError("mixed per-result ruleVersion should count present versions")
        if mixed_report["ruleVersionCoverage"]["resultsWithoutRuleVersion"] != 1:
            raise ContractError("mixed per-result ruleVersion should count missing versions")
        if mixed_report["ruleVersionDistribution"] != {java_version: 1}:
            raise ContractError(f"unexpected mixed ruleVersion distribution: {mixed_report['ruleVersionDistribution']}")
        if expected_warning not in mixed_report["warnings"]:
            raise ContractError("mixed per-result ruleVersion warning was not reported")

        try:
            evaluate_fixture(
                root,
                [
                    {"eventId": "paysim-1", "riskLevel": "HIGH", "riskScore": 85, "ruleVersion": "rule-v2-drift-v1", "ruleCodes": []},
                    {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 0, "ruleVersion": java_version, "ruleCodes": []},
                ],
            )
        except evaluate.EvaluationError:
            return
        raise ContractError("mismatched per-result ruleVersion did not fail-fast")


def main() -> int:
    try:
        java_version = java_rule_version()
        verify_policy_matches_java(java_version)
        verify_evaluator_contract(java_version)
    except ContractError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2
    print("PASS: PaySim rule version contract verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
