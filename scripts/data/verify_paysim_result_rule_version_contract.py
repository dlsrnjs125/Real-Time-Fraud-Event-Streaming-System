#!/usr/bin/env python3
"""Verify Phase 12 per-result ruleVersion coverage and strict-mode semantics."""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
from pathlib import Path
from types import SimpleNamespace
from typing import Any


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_DIR = ROOT / "scripts" / "data"
EXPECTED_REPORT_SCHEMA_VERSION = "2026-06-v2-phase12"

if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from paysim_evaluation_policy import RULE_VERSION  # noqa: E402


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


def write_jsonl(path: Path, rows: list[dict[str, Any]]) -> None:
    path.write_text("\n".join(json.dumps(row, sort_keys=True) for row in rows) + "\n", encoding="utf-8")


def evaluate_fixture(
        root: Path,
        results: list[dict[str, Any]],
        require_per_result_rule_version: bool = False,
) -> dict[str, Any]:
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
        rule_version=RULE_VERSION,
        evaluation_policy_version="evaluation-policy-v1",
        event_id_prefix=None,
        exclude_replay_rejected=True,
        include_missing_results=False,
        force=True,
        strict=True,
        require_per_result_rule_version=require_per_result_rule_version,
    )
    return evaluate.evaluate(args)


def verify_present_fixture(root: Path) -> None:
    report = evaluate_fixture(
        root,
        [
            {"eventId": "paysim-1", "riskLevel": "HIGH", "riskScore": 85, "ruleVersion": RULE_VERSION, "ruleCodes": ["AMOUNT_THRESHOLD"]},
            {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 0, "ruleVersion": RULE_VERSION, "ruleCodes": []},
        ],
        require_per_result_rule_version=True,
    )
    if report["reportSchemaVersion"] != EXPECTED_REPORT_SCHEMA_VERSION:
        raise ContractError(f"unexpected reportSchemaVersion: {report['reportSchemaVersion']}")
    if not report["requirePerResultRuleVersion"]:
        raise ContractError("strict per-result ruleVersion flag was not reported")
    if report["ruleVersionReadiness"] != "per_result_verified":
        raise ContractError(f"unexpected readiness for full coverage: {report['ruleVersionReadiness']}")
    if report["ruleVersionCoverage"]["coverageRate"] != 1.0:
        raise ContractError(f"unexpected full coverage: {report['ruleVersionCoverage']}")
    if report["ruleVersionDistribution"] != {RULE_VERSION: 2}:
        raise ContractError(f"unexpected distribution: {report['ruleVersionDistribution']}")


def verify_legacy_missing_fixture(root: Path) -> None:
    report = evaluate_fixture(
        root,
        [
            {"eventId": "paysim-1", "riskLevel": "HIGH", "riskScore": 85, "ruleCodes": ["AMOUNT_THRESHOLD"]},
            {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 0, "ruleCodes": []},
        ],
    )
    if report["ruleVersionReadiness"] != "contract_level_only":
        raise ContractError(f"unexpected legacy readiness: {report['ruleVersionReadiness']}")
    if report["ruleVersionDistribution"] != {}:
        raise ContractError("legacy missing ruleVersion should not create distribution keys")
    if report["ruleVersionCoverage"]["resultsWithoutRuleVersion"] != 2:
        raise ContractError(f"unexpected legacy coverage: {report['ruleVersionCoverage']}")

    try:
        evaluate_fixture(
            root,
            [
                {"eventId": "paysim-1", "riskLevel": "HIGH", "riskScore": 85, "ruleCodes": []},
                {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 0, "ruleCodes": []},
            ],
            require_per_result_rule_version=True,
        )
    except evaluate.EvaluationError:
        return
    raise ContractError("strict per-result ruleVersion mode did not fail on legacy missing rows")


def verify_mixed_fixture(root: Path) -> None:
    report = evaluate_fixture(
        root,
        [
            {"eventId": "paysim-1", "riskLevel": "HIGH", "riskScore": 85, "ruleVersion": RULE_VERSION, "ruleCodes": []},
            {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 0, "ruleCodes": []},
        ],
    )
    if report["ruleVersionReadiness"] != "contract_level_with_partial_per_result_coverage":
        raise ContractError(f"unexpected mixed readiness: {report['ruleVersionReadiness']}")
    if report["ruleVersionDistribution"] != {RULE_VERSION: 1}:
        raise ContractError(f"mixed missing ruleVersion polluted distribution: {report['ruleVersionDistribution']}")
    if "None" in report["ruleVersionDistribution"] or None in report["ruleVersionDistribution"]:
        raise ContractError("missing ruleVersion must not be represented as a distribution key")


def verify_mismatch_fixture(root: Path) -> None:
    try:
        evaluate_fixture(
            root,
            [
                {"eventId": "paysim-1", "riskLevel": "HIGH", "riskScore": 85, "ruleVersion": "rule-v2-drift-v1", "ruleCodes": []},
                {"eventId": "paysim-2", "riskLevel": "LOW", "riskScore": 0, "ruleVersion": RULE_VERSION, "ruleCodes": []},
            ],
        )
    except evaluate.EvaluationError:
        return
    raise ContractError("mismatched per-result ruleVersion did not fail-fast")


def main() -> int:
    try:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            verify_present_fixture(root)
            verify_legacy_missing_fixture(root)
            verify_mixed_fixture(root)
            verify_mismatch_fixture(root)
    except ContractError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2
    print("PASS: PaySim per-result rule version contract verified")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
