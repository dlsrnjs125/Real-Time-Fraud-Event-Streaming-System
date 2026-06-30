import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace


SCRIPT_PATH = Path(__file__).with_name("evaluate_paysim_replay_results.py")
SPEC = importlib.util.spec_from_file_location("evaluate_paysim_replay_results", SCRIPT_PATH)
evaluate = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules["evaluate_paysim_replay_results"] = evaluate
SPEC.loader.exec_module(evaluate)


def write_jsonl(path, rows):
    path.write_text("\n".join(json.dumps(row, sort_keys=True) for row in rows) + ("\n" if rows else ""), encoding="utf-8")


class EvaluatePaySimReplayResultsTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.labels = self.root / "labels.jsonl"
        self.results = self.root / "results.jsonl"
        self.replay_report = self.root / "replay-report.json"
        self.output = self.root / "evaluation-report.json"

    def tearDown(self):
        self.temp_dir.cleanup()

    def label(self, event_id, is_fraud=False, **overrides):
        value = {
            "eventId": event_id,
            "isFraud": is_fraud,
            "sourceFlaggedFraud": False,
            "sourceStep": 1,
            "sourceType": "TRANSFER",
        }
        value.update(overrides)
        return value

    def result(self, event_id, risk_level="LOW", rule_codes=None, **overrides):
        default_scores = {"LOW": 0, "MEDIUM": 60, "HIGH": 85, "CRITICAL": 95}
        value = {
            "eventId": event_id,
            "riskLevel": risk_level,
            "riskScore": default_scores.get(risk_level, 0),
            "ruleCodes": rule_codes if rule_codes is not None else [],
            "detectedAt": "2026-01-01T00:00:01Z",
        }
        value.update(overrides)
        return value

    def replay(self, failures=None, **overrides):
        value = {
            "scriptVersion": "v2-phase-5",
            "eventIdPrefix": None,
            "payloadRejected": len(failures or []),
            "unsupportedEventTypes": {},
            "failures": failures or [],
        }
        value.update(overrides)
        return value

    def args(self, **overrides):
        value = {
            "labels": self.labels,
            "results": self.results,
            "replay_report": None,
            "output": self.output,
            "positive_risk_level": None,
            "threshold_version": "threshold-v1",
            "rule_version": "rule-v2-baseline-v1",
            "evaluation_policy_version": "evaluation-policy-v1",
            "event_id_prefix": None,
            "exclude_replay_rejected": True,
            "include_missing_results": False,
            "force": True,
            "strict": True,
            "require_per_result_rule_version": False,
        }
        value.update(overrides)
        return SimpleNamespace(**value)

    def write_fixture(self, labels, results, replay_report=None):
        write_jsonl(self.labels, labels)
        write_jsonl(self.results, results)
        if replay_report is not None:
            self.replay_report.write_text(json.dumps(replay_report, sort_keys=True), encoding="utf-8")

    def evaluate_fixture(self, labels, results, replay_report=None, **arg_overrides):
        self.write_fixture(labels, results, replay_report)
        if replay_report is not None and "replay_report" not in arg_overrides:
            arg_overrides["replay_report"] = self.replay_report
        return evaluate.evaluate(self.args(**arg_overrides))

    def test_perfect_prediction_has_full_metrics(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True), self.label("paysim-2", False)],
            [self.result("paysim-1", "MEDIUM"), self.result("paysim-2", "LOW")],
        )
        self.assertEqual(1.0, report["metrics"]["precision"])
        self.assertEqual(1.0, report["metrics"]["recall"])
        self.assertEqual(1.0, report["metrics"]["f1Score"])
        self.assertEqual(1.0, report["metrics"]["accuracy"])
        self.assertEqual(2, report["totalEvents"])
        self.assertEqual(1, report["totalFraudLabels"])
        self.assertEqual(1, report["totalNonFraudLabels"])
        self.assertEqual(1, report["fraudLabeledEvents"])
        self.assertEqual(1, report["evaluatedFraudLabeledEvents"])
        self.assertEqual(1, report["detectedFraudEvents"])
        self.assertEqual(0, report["missedFraudEvents"])
        self.assertEqual(0, report["evaluatedMissedFraudEvents"])
        self.assertEqual(0, report["falsePositiveEvents"])
        self.assertEqual(1, report["truePositiveEvents"])
        self.assertEqual(1, report["trueNegativeEvents"])
        self.assertEqual(0, report["missingFraudLabels"])
        self.assertEqual(0, report["missingNonFraudLabels"])
        self.assertEqual(0, report["misclassifiedEvents"])
        self.assertEqual(0, report["unmatchedResultEvents"])
        self.assertEqual(0, report["evaluationExcludedRecords"])
        self.assertEqual(0, report["failedRecords"])
        self.assertEqual(0, report["invalidRecords"])
        self.assertEqual("fail_fast_before_report_generation", report["recordFailurePolicy"])
        self.assertEqual("2026-06-v2-phase12", report["reportSchemaVersion"])
        self.assertEqual("v2-phase9-evaluation-contract-v1", report["evaluationContractVersion"])
        self.assertEqual("evaluation-policy-v1", report["evaluationPolicyVersion"])
        self.assertEqual("rule-v2-baseline-v1", report["ruleVersion"])
        self.assertEqual("threshold-v1", report["thresholdVersion"])
        self.assertEqual("MEDIUM", report["positiveRiskLevel"])
        self.assertEqual("MEDIUM", report["thresholdPolicy"]["positiveRiskLevelFallback"])
        self.assertEqual(["MEDIUM", "HIGH", "CRITICAL"], report["thresholdPolicy"]["reviewRiskLevelsFallback"])
        self.assertEqual(["HIGH", "CRITICAL"], report["thresholdPolicy"]["blockRiskLevelsFallback"])
        self.assertEqual(50, report["mediumRiskThreshold"])
        self.assertEqual(80, report["highRiskThreshold"])
        self.assertEqual(1, report["reviewCandidateEvents"])
        self.assertEqual(0.5, report["reviewCandidateRate"])
        self.assertEqual(0, report["blockedCandidateEvents"])
        self.assertEqual(0.0, report["blockedCandidateRate"])
        self.assertEqual({"ALLOW": 1, "REVIEW": 1}, report["actionDecisionDistribution"])
        self.assertEqual(
            {
                "resultsWithRiskScore": 2,
                "resultsWithoutRiskScore": 0,
                "coverageRate": 1.0,
                "coverageScope": "evaluated_results_only",
            },
            report["riskScoreCoverage"],
        )
        self.assertEqual(
            {
                "resultsWithRuleVersion": 0,
                "resultsWithoutRuleVersion": 2,
                "coverageRate": 0.0,
                "coverageScope": "evaluated_results_only",
                "ruleVersionSource": "per_result_when_present_otherwise_contract_level",
            },
            report["ruleVersionCoverage"],
        )
        self.assertFalse(report["requirePerResultRuleVersion"])
        self.assertEqual("contract_level_only", report["ruleVersionReadiness"])
        self.assertEqual({}, report["ruleVersionDistribution"])
        self.assertIn(
            "Some evaluated results do not include per-result ruleVersion; evaluation uses contract-level ruleVersion.",
            report["warnings"],
        )
        self.assertEqual("full_risk_score_coverage", report["thresholdRegressionReliability"])
        self.assertEqual(
            {"reviewCandidateRateWithinBudget": False, "blockedCandidateRateWithinBudget": True},
            report["operatorWorkloadSummary"]["budgetStatus"],
        )
        self.assertFalse(report["replayReportUsed"])

    def test_confusion_matrix_counts_tp_fp_tn_fn(self):
        report = self.evaluate_fixture(
            [
                self.label("paysim-1", True),
                self.label("paysim-2", False),
                self.label("paysim-3", False),
                self.label("paysim-4", True),
            ],
            [
                self.result("paysim-1", "HIGH"),
                self.result("paysim-2", "MEDIUM"),
                self.result("paysim-3", "LOW"),
                self.result("paysim-4", "LOW"),
            ],
        )
        self.assertEqual(
            {"truePositive": 1, "falsePositive": 1, "trueNegative": 1, "falseNegative": 1},
            report["confusionMatrix"],
        )
        self.assertEqual(0.5, report["metrics"]["precision"])
        self.assertEqual(0.5, report["metrics"]["recall"])
        self.assertEqual(0.5, report["metrics"]["f1Score"])
        self.assertEqual(2, report["misclassifiedEvents"])
        self.assertEqual(0, report["failedRecords"])

    def test_medium_or_higher_is_positive_by_default(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True), self.label("paysim-2", True), self.label("paysim-3", True)],
            [self.result("paysim-1", "MEDIUM"), self.result("paysim-2", "HIGH"), self.result("paysim-3", "CRITICAL")],
        )
        self.assertEqual(3, report["confusionMatrix"]["truePositive"])

    def test_high_threshold_treats_medium_as_negative(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True), self.label("paysim-2", True)],
            [self.result("paysim-1", "MEDIUM"), self.result("paysim-2", "HIGH")],
            threshold_version="threshold-strict-v1",
        )
        self.assertEqual(1, report["confusionMatrix"]["truePositive"])
        self.assertEqual(1, report["confusionMatrix"]["falseNegative"])
        self.assertEqual("threshold-strict-v1", report["thresholdVersion"])
        self.assertEqual("HIGH", report["positiveRiskLevel"])

    def test_threshold_policy_controls_risk_level_fallback_when_score_is_missing(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True), self.label("paysim-2", True), self.label("paysim-3", True)],
            [
                self.result("paysim-1", "MEDIUM", riskScore=None),
                self.result("paysim-2", "HIGH", riskScore=None),
                self.result("paysim-3", "CRITICAL", riskScore=None),
            ],
            threshold_version="threshold-strict-v1",
        )
        self.assertEqual({"ALLOW": 1, "BLOCK": 1, "REVIEW": 1}, report["actionDecisionDistribution"])
        self.assertEqual(2, report["confusionMatrix"]["truePositive"])
        self.assertEqual(1, report["confusionMatrix"]["falseNegative"])
        self.assertEqual(
            {
                "resultsWithRiskScore": 0,
                "resultsWithoutRiskScore": 3,
                "coverageRate": 0.0,
                "coverageScope": "evaluated_results_only",
            },
            report["riskScoreCoverage"],
        )
        self.assertEqual("partial_without_full_risk_score_coverage", report["thresholdRegressionReliability"])
        self.assertIn(
            "Some evaluated results do not include riskScore; threshold policy falls back to riskLevel-based decisions.",
            report["warnings"],
        )

    def test_positive_risk_level_must_match_threshold_policy_fallback(self):
        self.write_fixture([self.label("paysim-1")], [self.result("paysim-1")])
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args(threshold_version="threshold-strict-v1", positive_risk_level="MEDIUM"))

    def test_unknown_threshold_version_fails_fast(self):
        self.write_fixture([self.label("paysim-1")], [self.result("paysim-1")])
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args(threshold_version="unknown-threshold"))

    def test_unknown_rule_version_fails_fast(self):
        self.write_fixture([self.label("paysim-1")], [self.result("paysim-1")])
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args(rule_version="rule-v2-baseline-vl"))

    def test_result_rule_version_must_match_contract_rule_version(self):
        self.write_fixture(
            [self.label("paysim-1")],
            [self.result("paysim-1", ruleVersion="rule-v2-drift-v1")],
        )
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args())

    def test_result_rule_version_coverage_is_reported_when_present(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True), self.label("paysim-2", False)],
            [
                self.result("paysim-1", "HIGH", ruleVersion="rule-v2-baseline-v1"),
                self.result("paysim-2", "LOW", ruleVersion="rule-v2-baseline-v1"),
            ],
        )
        self.assertEqual(
            {
                "resultsWithRuleVersion": 2,
                "resultsWithoutRuleVersion": 0,
                "coverageRate": 1.0,
                "coverageScope": "evaluated_results_only",
                "ruleVersionSource": "per_result_when_present_otherwise_contract_level",
            },
            report["ruleVersionCoverage"],
        )
        self.assertEqual({"rule-v2-baseline-v1": 2}, report["ruleVersionDistribution"])
        self.assertEqual("per_result_verified", report["ruleVersionReadiness"])
        self.assertNotIn(
            "Some evaluated results do not include per-result ruleVersion; evaluation uses contract-level ruleVersion.",
            report["warnings"],
        )

    def test_mixed_per_result_rule_version_does_not_break_distribution(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True), self.label("paysim-2", False)],
            [
                self.result("paysim-1", "HIGH", ruleVersion="rule-v2-baseline-v1"),
                self.result("paysim-2", "LOW"),
            ],
        )
        self.assertEqual(1, report["ruleVersionCoverage"]["resultsWithRuleVersion"])
        self.assertEqual(1, report["ruleVersionCoverage"]["resultsWithoutRuleVersion"])
        self.assertEqual(0.5, report["ruleVersionCoverage"]["coverageRate"])
        self.assertEqual({"rule-v2-baseline-v1": 1}, report["ruleVersionDistribution"])
        self.assertEqual("contract_level_with_partial_per_result_coverage", report["ruleVersionReadiness"])
        self.assertIn(
            "Some evaluated results do not include per-result ruleVersion; evaluation uses contract-level ruleVersion.",
            report["warnings"],
        )

    def test_require_per_result_rule_version_fails_on_legacy_missing_rows(self):
        self.write_fixture(
            [self.label("paysim-1", True)],
            [self.result("paysim-1", "HIGH")],
        )

        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args(require_per_result_rule_version=True))

    def test_require_per_result_rule_version_passes_when_all_evaluated_rows_have_version(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True)],
            [self.result("paysim-1", "HIGH", ruleVersion="rule-v2-baseline-v1")],
            require_per_result_rule_version=True,
        )

        self.assertTrue(report["requirePerResultRuleVersion"])
        self.assertEqual("per_result_verified", report["ruleVersionReadiness"])
        self.assertEqual({"rule-v2-baseline-v1": 1}, report["ruleVersionDistribution"])

    def test_threshold_policy_changes_workload_summary(self):
        labels = [self.label("paysim-1", True), self.label("paysim-2", False), self.label("paysim-3", False)]
        results = [
            self.result("paysim-1", "MEDIUM", riskScore=60),
            self.result("paysim-2", "MEDIUM", riskScore=75),
            self.result("paysim-3", "HIGH", riskScore=85),
        ]
        baseline = self.evaluate_fixture(labels, results)
        strict = self.evaluate_fixture(labels, results, threshold_version="threshold-strict-v1")

        self.assertEqual(3, baseline["reviewCandidateEvents"])
        self.assertEqual(1, baseline["blockedCandidateEvents"])
        self.assertEqual(2, strict["reviewCandidateEvents"])
        self.assertEqual(0, strict["blockedCandidateEvents"])
        self.assertGreater(baseline["reviewCandidateRate"], strict["reviewCandidateRate"])

    def test_missing_fraud_result_is_excluded_by_default(self):
        report = self.evaluate_fixture([self.label("paysim-1", True)], [])
        self.assertEqual(1, report["missingResults"])
        self.assertEqual(1, report["totalFraudLabels"])
        self.assertEqual(0, report["evaluatedFraudLabeledEvents"])
        self.assertEqual(1, report["missingFraudLabels"])
        self.assertEqual(0, report["evaluatedEvents"])
        self.assertEqual(0, report["confusionMatrix"]["falseNegative"])

    def test_missing_fraud_result_is_false_negative_when_included(self):
        report = self.evaluate_fixture([self.label("paysim-1", True)], [], include_missing_results=True)
        self.assertEqual(1, report["missingResults"])
        self.assertEqual(1, report["missingFraudLabels"])
        self.assertEqual(1, report["evaluatedFraudLabeledEvents"])
        self.assertEqual(1, report["confusionMatrix"]["falseNegative"])

    def test_missing_non_fraud_result_is_true_negative_when_included(self):
        report = self.evaluate_fixture([self.label("paysim-1", False)], [], include_missing_results=True)
        self.assertEqual(1, report["missingResults"])
        self.assertEqual(1, report["missingNonFraudLabels"])
        self.assertEqual(1, report["confusionMatrix"]["trueNegative"])

    def test_missing_result_can_be_excluded_from_denominator(self):
        report = self.evaluate_fixture([self.label("paysim-1", True)], [], include_missing_results=False)
        self.assertEqual(1, report["missingResults"])
        self.assertEqual(0, report["evaluatedEvents"])
        self.assertIsNone(report["metrics"]["accuracy"])

    def test_event_id_prefix_is_removed_for_join(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True, sourceType="CASH_OUT")],
            [self.result("local-smoke-paysim-1", "HIGH")],
            event_id_prefix="local-smoke",
        )
        self.assertEqual(1, report["confusionMatrix"]["truePositive"])

    def test_duplicate_label_event_id_always_fails(self):
        self.write_fixture([self.label("paysim-1"), self.label("paysim-1")], [])
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args(strict=False))

    def test_duplicate_result_event_id_always_fails(self):
        self.write_fixture(
            [self.label("paysim-1")],
            [self.result("paysim-1", "LOW"), self.result("paysim-1", "HIGH")],
        )
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args(strict=False))

    def test_unsupported_risk_level_fails(self):
        self.write_fixture([self.label("paysim-1")], [self.result("paysim-1", "UNKNOWN")])
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args())

    def test_label_file_raw_identifier_field_fails(self):
        self.write_fixture([self.label("paysim-1", nameOrig="C12345")], [])
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args())

    def test_result_file_label_field_fails(self):
        self.write_fixture([self.label("paysim-1")], [self.result("paysim-1", "LOW", isFraud=False)])
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args())

    def test_replay_payload_rejected_event_is_excluded(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True), self.label("paysim-2", True)],
            [self.result("paysim-1", "LOW"), self.result("paysim-2", "HIGH")],
            self.replay(
                failures=[
                    {
                        "rowNumber": 1,
                        "eventId": "paysim-1",
                        "reason": "UNSUPPORTED_EVENT_TYPE_FOR_CURRENT_API:CASH_OUT",
                    }
                ]
            ),
        )
        self.assertEqual(1, report["excludedReplayRejected"])
        self.assertTrue(report["replayReportUsed"])
        self.assertEqual(1, report["replayPayloadRejected"])
        self.assertEqual(1, report["replayRejectedEventIdsAvailable"])
        self.assertTrue(report["replayRejectedExclusionComplete"])
        self.assertEqual(1, report["evaluationExcludedRecords"])
        self.assertEqual(1, report["evaluatedEvents"])
        self.assertEqual(1, report["confusionMatrix"]["truePositive"])

    def test_replay_rejected_exclusion_warns_when_bounded_failures_are_incomplete(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True), self.label("paysim-2", True), self.label("paysim-3", False)],
            [self.result("paysim-2", "HIGH")],
            self.replay(
                payloadRejected=3,
                failures=[
                    {
                        "rowNumber": 1,
                        "eventId": "paysim-1",
                        "reason": "UNSUPPORTED_EVENT_TYPE_FOR_CURRENT_API:CASH_OUT",
                    }
                ],
            ),
        )
        self.assertEqual(3, report["replayPayloadRejected"])
        self.assertEqual(1, report["replayRejectedEventIdsAvailable"])
        self.assertFalse(report["replayRejectedExclusionComplete"])
        self.assertTrue(any("payloadRejected is greater" in warning for warning in report["warnings"]))

    def test_replay_mapping_fields_are_propagated_to_evaluation_report(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True, sourceType="CASH_OUT")],
            [self.result("paysim-1", "HIGH")],
            self.replay(
                mappingPolicyVersion="paysim-native-mapping-v1",
                mappingPolicyVersions={"paysim-native-mapping-v1": 1},
                mappingMetadataPolicy="required_for_phase8_paysim_native_contract",
                missingMappingMetadata=2,
                inputNativeTypeDistribution={"CASH_OUT": 1, "DEBIT": 1},
                inputNormalizedTypeDistribution={"WITHDRAWAL": 1, "DEBIT": 1},
                inputTypeSupportLevelDistribution={"replay-supported": 1, "unsupported": 1},
                excludedByType={"DEBIT": 1},
            ),
        )

        self.assertEqual("paysim-native-mapping-v1", report["mappingPolicyVersion"])
        self.assertEqual("required_for_phase8_paysim_native_contract", report["mappingMetadataPolicy"])
        self.assertEqual(2, report["replayMissingMappingMetadata"])
        self.assertEqual("split_replay_input_and_evaluation_denominator", report["typeDistributionScope"])
        self.assertEqual("replay_report_input_scope", report["nativeTypeDistributionSource"])
        self.assertEqual({"CASH_OUT": 1, "DEBIT": 1}, report["nativeTypeDistribution"])
        self.assertEqual({"DEBIT": 1, "WITHDRAWAL": 1}, report["normalizedTypeDistribution"])
        self.assertEqual({"CASH_OUT": 1, "DEBIT": 1}, report["replayNativeTypeDistribution"])
        self.assertEqual({"CASH_OUT": 1}, report["evaluatedNativeTypeDistribution"])
        self.assertEqual({"WITHDRAWAL": 1}, report["evaluatedNormalizedTypeDistribution"])
        self.assertEqual({"DEBIT": 1}, report["excludedNativeTypeDistribution"])
        self.assertEqual({"DEBIT": 1}, report["excludedByType"])

    def test_missing_explicit_replay_report_fails(self):
        self.write_fixture([self.label("paysim-1")], [self.result("paysim-1")])
        with self.assertRaises(FileNotFoundError):
            evaluate.evaluate(self.args(replay_report=self.replay_report))

    def test_zero_metric_denominators_are_null(self):
        report = self.evaluate_fixture([self.label("paysim-1", True)], [], include_missing_results=False)
        self.assertIsNone(report["metrics"]["precision"])
        self.assertIsNone(report["metrics"]["recall"])
        self.assertIsNone(report["metrics"]["f1Score"])
        self.assertIsNone(report["metrics"]["falsePositiveRate"])
        self.assertIsNone(report["metrics"]["accuracy"])

    def test_report_does_not_store_raw_identifier_token_or_payload(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", False)],
            [self.result("paysim-1", "LOW")],
        )
        text = json.dumps(report, sort_keys=True)
        self.assertNotIn("C12345", text)
        self.assertNotIn("secret-token", text)
        self.assertNotIn("requestBody", text)
        self.assertNotIn("responseBody", text)
        self.assertNotIn("isFraud", text)

    def test_missing_result_warning_and_treatment_are_reported(self):
        report = self.evaluate_fixture([self.label("paysim-1", False)], [])
        self.assertEqual("missing_results_excluded_from_denominator", report["missingResultTreatment"])
        self.assertTrue(any("excluded from denominator" in warning for warning in report["warnings"]))

    def test_missing_result_include_policy_is_reported(self):
        report = self.evaluate_fixture([self.label("paysim-1", False)], [], include_missing_results=True)
        self.assertEqual("fraud_missing_as_FN_non_fraud_missing_as_TN", report["missingResultTreatment"])
        self.assertTrue(any("Missing detection results are included" in warning for warning in report["warnings"]))

    def test_unmatched_results_are_reported(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", False)],
            [self.result("local-paysim-1", "LOW")],
        )
        self.assertEqual(0, report["matchedResults"])
        self.assertEqual(1, report["unmatchedResults"])
        self.assertEqual(1, report["unmatchedResultEvents"])
        self.assertTrue(any("do not match any label" in warning for warning in report["warnings"]))

    def test_strict_label_contract_checks_sidecar_fields(self):
        self.write_fixture([self.label("paysim-1", sourceFlaggedFraud="false")], [])
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args(strict=True))
        self.write_fixture([self.label("paysim-1", sourceStep=-1)], [])
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args(strict=True))
        self.write_fixture([self.label("paysim-1", sourceType="")], [])
        with self.assertRaises(evaluate.EvaluationError):
            evaluate.evaluate(self.args(strict=True))

    def test_sample_event_ids_are_limited_to_ten(self):
        labels = [self.label(f"paysim-{index}", False) for index in range(20)]
        results = [self.result(f"paysim-{index}", "LOW") for index in range(20)]
        report = self.evaluate_fixture(labels, results)
        self.assertEqual(10, len(report["sampleEventIds"]))


if __name__ == "__main__":
    unittest.main()
