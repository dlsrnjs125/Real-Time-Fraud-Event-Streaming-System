import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("validate_paysim_outputs.py")
VALID_TYPES = ["TRANSFER", "CASH_OUT", "PAYMENT", "CASH_IN", "DEBIT"]


def write_jsonl(path, rows):
    path.write_text("\n".join(json.dumps(row, sort_keys=True) for row in rows) + ("\n" if rows else ""), encoding="utf-8")


class ValidatePaySimOutputsTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.events_path = self.root / "paysim-events.jsonl"
        self.labels_path = self.root / "paysim-labels.jsonl"
        self.rejected_path = self.root / "paysim-rejected.jsonl"
        self.report_path = self.root / "paysim-validation-report.json"
        self.write_fixture()

    def tearDown(self):
        self.temp_dir.cleanup()

    def event(self, event_id="paysim-000000001", trace_id="trace-paysim-000000001", event_type="TRANSFER", **overrides):
        value = {
            "eventId": event_id,
            "userId": "U-abcdef1234567890",
            "accountId": "A-abcdef1234567890",
            "destinationAccountId": "D-fedcba0987654321",
            "eventType": event_type,
            "amount": "100.00",
            "currency": "KRW",
            "eventTime": "2026-01-01T01:00:00Z",
            "traceId": trace_id,
            "schemaVersion": "v2-paysim",
            "source": "PAYSIM",
            "balanceFeatures": {
                "sourceStep": 1,
                "oldBalanceOrig": "1000.00",
                "newBalanceOrig": "900.00",
                "oldBalanceDest": "0.00",
                "newBalanceDest": "100.00",
            },
        }
        value.update(overrides)
        return value

    def label(self, event_id="paysim-000000001", is_fraud=True, event_type="TRANSFER", **overrides):
        value = {
            "eventId": event_id,
            "isFraud": is_fraud,
            "sourceFlaggedFraud": False,
            "sourceStep": 1,
            "sourceType": event_type,
        }
        value.update(overrides)
        return value

    def rejected(self, **overrides):
        value = {
            "rowNumber": 9,
            "reason": "INVALID_AMOUNT",
            "rawType": "TRANSFER",
            "message": "amount must be >= 0",
        }
        value.update(overrides)
        return value

    def report(self, **overrides):
        value = {
            "scriptVersion": "v2-phase-2",
            "datasetSlug": "ealaxi/paysim1",
            "rawFileName": "PS_20174392719_1491204439457_log.csv",
            "inputPath": "data/raw/PS_20174392719_1491204439457_log.csv",
            "inputSha256": "a" * 64,
            "baseTime": "2026-01-01T00:00:00Z",
            "startedAt": "2026-01-01T00:00:00Z",
            "finishedAt": "2026-01-01T00:00:01Z",
            "totalRows": 1,
            "acceptedRows": 1,
            "rejectedRows": 0,
            "fraudRows": 1,
            "flaggedFraudRows": 0,
            "eventTypeCounts": {"TRANSFER": 1, "CASH_OUT": 0, "PAYMENT": 0, "CASH_IN": 0, "DEBIT": 0},
            "outputFiles": {
                "events": str(self.events_path),
                "labels": str(self.labels_path),
                "rejected": str(self.rejected_path),
            },
            "hashSaltSource": "env:PAYSIM_HASH_SALT",
        }
        value.update(overrides)
        return value

    def write_fixture(self, events=None, labels=None, rejected=None, report=None):
        write_jsonl(self.events_path, events if events is not None else [self.event()])
        write_jsonl(self.labels_path, labels if labels is not None else [self.label()])
        write_jsonl(self.rejected_path, rejected if rejected is not None else [])
        self.report_path.write_text(json.dumps(report if report is not None else self.report(), sort_keys=True), encoding="utf-8")

    def run_validate(self, *extra_args):
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT_PATH),
                "--events",
                str(self.events_path),
                "--labels",
                str(self.labels_path),
                "--rejected",
                str(self.rejected_path),
                "--report",
                str(self.report_path),
                *extra_args,
            ],
            text=True,
            capture_output=True,
            check=False,
        )

    def assert_fails_with(self, expected):
        result = self.run_validate()
        self.assertNotEqual(0, result.returncode)
        self.assertIn(expected, result.stderr)

    def test_valid_outputs_pass(self):
        result = self.run_validate()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("PASS: PaySim output validation passed", result.stdout)

    def test_event_label_fields_fail(self):
        for field in ("isFraud", "isFlaggedFraud", "sourceFlaggedFraud"):
            self.write_fixture(events=[self.event(**{field: True})])
            self.assert_fails_with("label fields")

    def test_raw_identifier_fields_and_values_fail(self):
        self.write_fixture(events=[self.event(nameOrig="hidden")])
        self.assert_fails_with("raw identifier fields")
        self.write_fixture(events=[self.event(userId="U-C12345")])
        self.assert_fails_with("raw PaySim identifiers")
        self.write_fixture(labels=[self.label(sourceType="M12345")])
        self.assert_fails_with("raw PaySim identifiers")

    def test_event_label_count_mismatch_fails(self):
        self.write_fixture(labels=[])
        self.assert_fails_with("labels are missing eventIds")

    def test_unknown_label_event_id_fails(self):
        self.write_fixture(labels=[self.label(event_id="paysim-unknown")])
        self.assert_fails_with("does not exist in events")

    def test_report_count_mismatch_fails(self):
        self.write_fixture(report=self.report(acceptedRows=2))
        self.assert_fails_with("acceptedRows must match event line count")
        self.write_fixture(report=self.report(fraudRows=0))
        self.assert_fails_with("fraudRows must match labels")
        self.write_fixture(rejected=[self.rejected()], report=self.report(rejectedRows=0, totalRows=1))
        self.assert_fails_with("rejectedRows must match rejected line count")
        self.write_fixture(report=self.report(eventTypeCounts={"TRANSFER": 0, "CASH_OUT": 0, "PAYMENT": 0, "CASH_IN": 0, "DEBIT": 0}))
        self.assert_fails_with("eventTypeCounts must match events")
        self.write_fixture(report=self.report(eventTypeCounts={"TRANSFER": 1}))
        self.assert_fails_with("eventTypeCounts must contain all valid event types")

    def test_reject_ratio_threshold_fails(self):
        self.write_fixture(rejected=[self.rejected()], report=self.report(totalRows=2, rejectedRows=1))
        result = self.run_validate("--max-reject-ratio", "0.10")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("reject ratio", result.stderr)

    def test_non_finite_amount_fails(self):
        self.write_fixture(events=[self.event(amount="NaN")])
        self.assert_fails_with("finite decimal")
        self.write_fixture(events=[self.event(amount="Infinity")])
        self.assert_fails_with("finite decimal")

    def test_missing_event_field_fails(self):
        event = self.event()
        del event["amount"]
        self.write_fixture(events=[event])
        self.assert_fails_with("missing event fields")

    def test_missing_balance_source_step_fails(self):
        event = self.event()
        del event["balanceFeatures"]["sourceStep"]
        self.write_fixture(events=[event])
        self.assert_fails_with("missing balance fields")

    def test_report_salt_value_and_bad_sha_fail(self):
        self.write_fixture(report=self.report(hashSaltValue="secret"))
        self.assert_fails_with("salt values")
        self.write_fixture(report=self.report(inputSha256="bad"))
        self.assert_fails_with("64 hex")


if __name__ == "__main__":
    unittest.main()
