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
        value = {
            "eventId": event_id,
            "riskLevel": risk_level,
            "riskScore": 0,
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
            "positive_risk_level": "MEDIUM",
            "event_id_prefix": None,
            "exclude_replay_rejected": True,
            "include_missing_results": False,
            "force": True,
            "strict": True,
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
        self.assertEqual(1, report["fraudLabeledEvents"])
        self.assertEqual(1, report["detectedFraudEvents"])
        self.assertEqual(0, report["missedFraudEvents"])
        self.assertEqual(0, report["falsePositiveEvents"])
        self.assertEqual(1, report["truePositiveEvents"])
        self.assertEqual(1, report["trueNegativeEvents"])
        self.assertEqual(0, report["misclassifiedEvents"])
        self.assertEqual(0, report["unmatchedResultEvents"])
        self.assertEqual(0, report["evaluationExcludedRecords"])
        self.assertEqual(0, report["failedRecords"])
        self.assertEqual(0, report["invalidRecords"])
        self.assertEqual("2026-06-v2-phase7", report["reportSchemaVersion"])
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
            positive_risk_level="HIGH",
        )
        self.assertEqual(1, report["confusionMatrix"]["truePositive"])
        self.assertEqual(1, report["confusionMatrix"]["falseNegative"])

    def test_missing_fraud_result_is_excluded_by_default(self):
        report = self.evaluate_fixture([self.label("paysim-1", True)], [])
        self.assertEqual(1, report["missingResults"])
        self.assertEqual(0, report["evaluatedEvents"])
        self.assertEqual(0, report["confusionMatrix"]["falseNegative"])

    def test_missing_fraud_result_is_false_negative_when_included(self):
        report = self.evaluate_fixture([self.label("paysim-1", True)], [], include_missing_results=True)
        self.assertEqual(1, report["missingResults"])
        self.assertEqual(1, report["confusionMatrix"]["falseNegative"])

    def test_missing_non_fraud_result_is_true_negative_when_included(self):
        report = self.evaluate_fixture([self.label("paysim-1", False)], [], include_missing_results=True)
        self.assertEqual(1, report["missingResults"])
        self.assertEqual(1, report["confusionMatrix"]["trueNegative"])

    def test_missing_result_can_be_excluded_from_denominator(self):
        report = self.evaluate_fixture([self.label("paysim-1", True)], [], include_missing_results=False)
        self.assertEqual(1, report["missingResults"])
        self.assertEqual(0, report["evaluatedEvents"])
        self.assertIsNone(report["metrics"]["accuracy"])

    def test_event_id_prefix_is_removed_for_join(self):
        report = self.evaluate_fixture(
            [self.label("paysim-1", True)],
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
