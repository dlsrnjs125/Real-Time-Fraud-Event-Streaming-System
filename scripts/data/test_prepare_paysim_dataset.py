import csv
import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("prepare_paysim_dataset.py")
SPEC = importlib.util.spec_from_file_location("prepare_paysim_dataset", SCRIPT_PATH)
prepare = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules["prepare_paysim_dataset"] = prepare
SPEC.loader.exec_module(prepare)


HEADER = [
    "step",
    "type",
    "amount",
    "nameOrig",
    "oldbalanceOrg",
    "newbalanceOrig",
    "nameDest",
    "oldbalanceDest",
    "newbalanceDest",
    "isFraud",
    "isFlaggedFraud",
]


def write_csv(path, rows):
    with path.open("w", encoding="utf-8", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=HEADER)
        writer.writeheader()
        writer.writerows(rows)


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


class PreparePaySimDatasetTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.input = self.root / "paysim.csv"
        self.output_dir = self.root / "processed"

    def tearDown(self):
        self.temp_dir.cleanup()

    def run_prepare(self, rows, *extra_args):
        write_csv(self.input, rows)
        command = [
            sys.executable,
            str(SCRIPT_PATH),
            "--input",
            str(self.input),
            "--output-dir",
            str(self.output_dir),
            "--hash-salt",
            "unit-test-salt",
            "--force",
            *extra_args,
        ]
        return subprocess.run(command, text=True, capture_output=True, check=False)

    def test_normal_rows_are_split_into_events_and_labels(self):
        result = self.run_prepare(
            [
                {
                    "step": "1",
                    "type": "TRANSFER",
                    "amount": "9839.64",
                    "nameOrig": "C123",
                    "oldbalanceOrg": "170136.00",
                    "newbalanceOrig": "160296.36",
                    "nameDest": "M456",
                    "oldbalanceDest": "0.00",
                    "newbalanceDest": "9839.64",
                    "isFraud": "1",
                    "isFlaggedFraud": "0",
                },
                {
                    "step": "2",
                    "type": "CASH_OUT",
                    "amount": "100.00",
                    "nameOrig": "C123",
                    "oldbalanceOrg": "160296.36",
                    "newbalanceOrig": "160196.36",
                    "nameDest": "M789",
                    "oldbalanceDest": "0.00",
                    "newbalanceDest": "100.00",
                    "isFraud": "0",
                    "isFlaggedFraud": "1",
                },
            ]
        )

        self.assertEqual(0, result.returncode, result.stderr)
        events = read_jsonl(self.output_dir / "paysim-events.jsonl")
        labels = read_jsonl(self.output_dir / "paysim-labels.jsonl")
        report = json.loads((self.output_dir / "paysim-validation-report.json").read_text())

        self.assertEqual("paysim-000000001", events[0]["eventId"])
        self.assertEqual("trace-paysim-000000001", events[0]["traceId"])
        self.assertEqual("2026-01-01T01:00:00Z", events[0]["eventTime"])
        self.assertEqual("9839.64", events[0]["amount"])
        self.assertEqual("170136.00", events[0]["balanceFeatures"]["oldBalanceOrig"])
        self.assertEqual(1, events[0]["balanceFeatures"]["sourceStep"])
        self.assertNotIn("isFraud", events[0])
        self.assertNotIn("isFlaggedFraud", events[0])
        self.assertNotIn("nameOrig", json.dumps(events[0]))
        self.assertNotIn("nameDest", json.dumps(events[0]))
        self.assertNotIn("receivedAt", events[0])

        self.assertEqual(
            {
                "eventId": "paysim-000000001",
                "isFraud": True,
                "sourceFlaggedFraud": False,
                "sourceStep": 1,
                "sourceType": "TRANSFER",
            },
            labels[0],
        )
        self.assertEqual(2, report["totalRows"])
        self.assertEqual(2, report["acceptedRows"])
        self.assertEqual(1, report["fraudRows"])
        self.assertEqual(1, report["flaggedFraudRows"])
        self.assertIn("inputSha256", report)
        self.assertIn("events", report["outputFiles"])

    def test_invalid_amount_is_rejected_with_safe_record(self):
        result = self.run_prepare(
            [
                {
                    "step": "1",
                    "type": "TRANSFER",
                    "amount": "-1.00",
                    "nameOrig": "C123",
                    "oldbalanceOrg": "10.00",
                    "newbalanceOrig": "9.00",
                    "nameDest": "M456",
                    "oldbalanceDest": "0.00",
                    "newbalanceDest": "1.00",
                    "isFraud": "0",
                    "isFlaggedFraud": "0",
                }
            ]
        )

        self.assertEqual(0, result.returncode, result.stderr)
        rejected = read_jsonl(self.output_dir / "paysim-rejected.jsonl")
        self.assertEqual("INVALID_AMOUNT", rejected[0]["reason"])
        self.assertEqual("TRANSFER", rejected[0]["rawType"])
        rejected_text = json.dumps(rejected[0])
        self.assertNotIn("C123", rejected_text)
        self.assertNotIn("M456", rejected_text)

    def test_fail_fast_policy_exits_on_invalid_row(self):
        result = self.run_prepare(
            [
                {
                    "step": "1",
                    "type": "TRANSFER",
                    "amount": "-1.00",
                    "nameOrig": "C123",
                    "oldbalanceOrg": "10.00",
                    "newbalanceOrig": "9.00",
                    "nameDest": "M456",
                    "oldbalanceDest": "0.00",
                    "newbalanceDest": "1.00",
                    "isFraud": "0",
                    "isFlaggedFraud": "0",
                }
            ],
            "--reject-policy",
            "fail-fast",
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("INVALID_AMOUNT", result.stderr)

    def test_hash_ids_are_deterministic(self):
        raw = "C123"
        salt = "unit-test-salt"
        expected = hashlib.sha256(f"{raw}{salt}".encode("utf-8")).hexdigest()[:16]
        result = self.run_prepare(
            [
                {
                    "step": "1",
                    "type": "PAYMENT",
                    "amount": "10.00",
                    "nameOrig": raw,
                    "oldbalanceOrg": "20.00",
                    "newbalanceOrig": "10.00",
                    "nameDest": "M456",
                    "oldbalanceDest": "0.00",
                    "newbalanceDest": "10.00",
                    "isFraud": "0",
                    "isFlaggedFraud": "0",
                }
            ]
        )

        self.assertEqual(0, result.returncode, result.stderr)
        event = read_jsonl(self.output_dir / "paysim-events.jsonl")[0]
        self.assertEqual(f"U-{expected}", event["userId"])
        self.assertEqual(f"A-{expected}", event["accountId"])


if __name__ == "__main__":
    unittest.main()
