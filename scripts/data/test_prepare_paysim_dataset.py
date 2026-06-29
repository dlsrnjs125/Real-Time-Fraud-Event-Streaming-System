import csv
import hashlib
import hmac
import importlib.util
import json
import os
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

    def run_prepare_without_cli_salt(self, rows, *extra_args, env=None):
        write_csv(self.input, rows)
        command = [
            sys.executable,
            str(SCRIPT_PATH),
            "--input",
            str(self.input),
            "--output-dir",
            str(self.output_dir),
            "--force",
            *extra_args,
        ]
        run_env = os.environ.copy()
        run_env.pop("PAYSIM_HASH_SALT", None)
        if env:
            run_env.update(env)
        return subprocess.run(command, text=True, capture_output=True, check=False, env=run_env)

    def base_row(self, **overrides):
        row = {
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
        }
        row.update(overrides)
        return row

    def test_normal_rows_are_split_into_events_and_labels(self):
        result = self.run_prepare(
            [
                self.base_row(),
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
                "normalizedType": "TRANSFER",
                "sourceFlaggedFraud": False,
                "sourceStep": 1,
                "sourceType": "TRANSFER",
                "typeMappingPolicyVersion": "paysim-native-mapping-v1",
                "typeSupportLevel": "production-supported",
            },
            labels[0],
        )
        self.assertEqual("TRANSFER", events[0]["nativeEventType"])
        self.assertEqual("TRANSFER", events[0]["normalizedEventType"])
        self.assertEqual("paysim-native-mapping-v1", events[0]["typeMappingPolicyVersion"])
        self.assertEqual("production-supported", events[0]["typeSupportLevel"])
        self.assertEqual("CASH_OUT", events[1]["nativeEventType"])
        self.assertEqual("WITHDRAWAL", events[1]["eventType"])
        self.assertEqual("WITHDRAWAL", events[1]["normalizedEventType"])
        self.assertEqual("replay-supported", events[1]["typeSupportLevel"])
        self.assertEqual(2, report["totalRows"])
        self.assertEqual(2, report["acceptedRows"])
        self.assertEqual(1, report["fraudRows"])
        self.assertEqual(1, report["flaggedFraudRows"])
        self.assertIn("inputSha256", report)
        self.assertIn("events", report["outputFiles"])
        self.assertEqual("HMAC-SHA256", report["hashAlgorithm"])
        self.assertEqual(16, report["hashIdPrefixLength"])
        self.assertEqual("cli", report["hashSaltSource"])
        self.assertEqual("paysim-native-mapping-v1", report["mappingPolicyVersion"])
        self.assertEqual({"CASH_IN": 0, "CASH_OUT": 1, "DEBIT": 0, "PAYMENT": 0, "TRANSFER": 1}, report["nativeTypeDistribution"])
        self.assertEqual({"TRANSFER": 1, "WITHDRAWAL": 1}, report["normalizedTypeDistribution"])
        self.assertEqual({"production-supported": 1, "replay-supported": 1}, report["typeSupportLevelDistribution"])
        self.assertEqual({}, report["excludedByType"])

    def test_cash_in_maps_to_deposit_for_replay(self):
        result = self.run_prepare([self.base_row(type="CASH_IN", isFraud="0")])

        self.assertEqual(0, result.returncode, result.stderr)
        event = read_jsonl(self.output_dir / "paysim-events.jsonl")[0]
        self.assertEqual("CASH_IN", event["nativeEventType"])
        self.assertEqual("DEPOSIT", event["eventType"])
        self.assertEqual("replay-supported", event["typeSupportLevel"])

    def test_debit_is_explicitly_rejected_by_mapping_policy(self):
        result = self.run_prepare([self.base_row(type="DEBIT", isFraud="0")])

        self.assertEqual(0, result.returncode, result.stderr)
        events = read_jsonl(self.output_dir / "paysim-events.jsonl")
        rejected = read_jsonl(self.output_dir / "paysim-rejected.jsonl")
        report = json.loads((self.output_dir / "paysim-validation-report.json").read_text())
        self.assertEqual([], events)
        self.assertEqual("UNSUPPORTED_NATIVE_TYPE:DEBIT", rejected[0]["reason"])
        self.assertEqual({"DEBIT": 1}, report["excludedByType"])

    def test_invalid_amount_is_rejected_with_safe_record(self):
        result = self.run_prepare(
            [
                self.base_row(amount="-1.00", isFraud="0")
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
                self.base_row(amount="-1.00", isFraud="0")
            ],
            "--reject-policy",
            "fail-fast",
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("INVALID_AMOUNT", result.stderr)

    def test_hash_ids_are_deterministic(self):
        raw = "C123"
        salt = "unit-test-salt"
        expected = hmac.new(
            salt.encode("utf-8"),
            raw.encode("utf-8"),
            hashlib.sha256,
        ).hexdigest()[:16]
        result = self.run_prepare(
            [
                self.base_row(type="PAYMENT", amount="10.00", nameOrig=raw, isFraud="0")
            ]
        )

        self.assertEqual(0, result.returncode, result.stderr)
        event = read_jsonl(self.output_dir / "paysim-events.jsonl")[0]
        self.assertEqual(f"U-{expected}", event["userId"])
        self.assertEqual(f"A-{expected}", event["accountId"])
        self.assertRegex(event["userId"], r"^U-[0-9a-f]{16}$")
        self.assertRegex(event["accountId"], r"^A-[0-9a-f]{16}$")
        self.assertRegex(event["destinationAccountId"], r"^D-[0-9a-f]{16}$")

    def test_hash_ids_are_stable_and_distinguish_different_raw_identifiers(self):
        result = self.run_prepare(
            [
                self.base_row(nameOrig="C123", nameDest="M456"),
                self.base_row(nameOrig="C123", nameDest="M789", isFraud="0"),
                self.base_row(nameOrig="C999", nameDest="M456", isFraud="0"),
            ]
        )

        self.assertEqual(0, result.returncode, result.stderr)
        events = read_jsonl(self.output_dir / "paysim-events.jsonl")
        self.assertEqual(events[0]["userId"], events[1]["userId"])
        self.assertEqual(events[0]["accountId"], events[1]["accountId"])
        self.assertNotEqual(events[0]["userId"], events[2]["userId"])
        self.assertEqual(events[0]["destinationAccountId"], events[2]["destinationAccountId"])

    def test_default_local_salt_is_allowed_without_strict_flag(self):
        result = self.run_prepare_without_cli_salt([self.base_row()])

        self.assertEqual(0, result.returncode, result.stderr)
        report = json.loads((self.output_dir / "paysim-validation-report.json").read_text())
        self.assertEqual("default-local", report["hashSaltSource"])

    def test_require_non_default_salt_fails_without_salt(self):
        result = self.run_prepare_without_cli_salt([self.base_row()], "--require-non-default-salt")

        self.assertNotEqual(0, result.returncode)
        self.assertIn("require-non-default-salt", result.stderr)

    def test_env_salt_source_is_recorded_without_leaking_value(self):
        result = self.run_prepare_without_cli_salt(
            [self.base_row()],
            "--require-non-default-salt",
            env={"PAYSIM_HASH_SALT": "private-unit-test-salt"},
        )

        self.assertEqual(0, result.returncode, result.stderr)
        report_text = (self.output_dir / "paysim-validation-report.json").read_text()
        report = json.loads(report_text)
        self.assertEqual("env:PAYSIM_HASH_SALT", report["hashSaltSource"])
        self.assertNotIn("private-unit-test-salt", report_text)

    def test_missing_required_column_fails(self):
        with self.input.open("w", encoding="utf-8", newline="") as file:
            writer = csv.DictWriter(file, fieldnames=[field for field in HEADER if field != "amount"])
            writer.writeheader()
            row = self.base_row()
            del row["amount"]
            writer.writerow(row)

        result = subprocess.run(
            [
                sys.executable,
                str(SCRIPT_PATH),
                "--input",
                str(self.input),
                "--output-dir",
                str(self.output_dir),
                "--force",
            ],
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("missing required columns: amount", result.stderr)

    def test_utf8_bom_header_is_accepted(self):
        write_csv(self.input, [self.base_row()])
        original = self.input.read_bytes()
        self.input.write_bytes(b"\xef\xbb\xbf" + original)

        result = subprocess.run(
            [
                sys.executable,
                str(SCRIPT_PATH),
                "--input",
                str(self.input),
                "--output-dir",
                str(self.output_dir),
                "--hash-salt",
                "unit-test-salt",
                "--force",
            ],
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stderr)
        events = read_jsonl(self.output_dir / "paysim-events.jsonl")
        self.assertEqual("paysim-000000001", events[0]["eventId"])

    def test_invalid_label_type_and_blank_identifier_are_rejected(self):
        result = self.run_prepare(
            [
                self.base_row(isFraud="2"),
                self.base_row(type="UNKNOWN"),
                self.base_row(nameOrig=""),
                self.base_row(nameDest=" "),
            ]
        )

        self.assertEqual(0, result.returncode, result.stderr)
        rejected = read_jsonl(self.output_dir / "paysim-rejected.jsonl")
        self.assertEqual(
            ["INVALID_LABEL", "INVALID_TYPE", "UNSUPPORTED_ROW", "UNSUPPORTED_ROW"],
            [row["reason"] for row in rejected],
        )

    def test_non_finite_decimal_values_are_rejected(self):
        result = self.run_prepare(
            [
                self.base_row(amount="NaN"),
                self.base_row(oldbalanceOrg="Infinity"),
            ]
        )

        self.assertEqual(0, result.returncode, result.stderr)
        rejected = read_jsonl(self.output_dir / "paysim-rejected.jsonl")
        self.assertEqual(["INVALID_AMOUNT", "INVALID_BALANCE"], [row["reason"] for row in rejected])

    def test_limit_zero_writes_empty_outputs_and_report(self):
        result = self.run_prepare([self.base_row()], "--limit", "0")

        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([], read_jsonl(self.output_dir / "paysim-events.jsonl"))
        self.assertEqual([], read_jsonl(self.output_dir / "paysim-labels.jsonl"))
        report = json.loads((self.output_dir / "paysim-validation-report.json").read_text())
        self.assertEqual(0, report["totalRows"])
        self.assertEqual(0, report["acceptedRows"])

    def test_existing_output_without_force_fails(self):
        first = self.run_prepare([self.base_row()])
        self.assertEqual(0, first.returncode, first.stderr)

        write_csv(self.input, [self.base_row()])
        result = subprocess.run(
            [
                sys.executable,
                str(SCRIPT_PATH),
                "--input",
                str(self.input),
                "--output-dir",
                str(self.output_dir),
                "--hash-salt",
                "unit-test-salt",
            ],
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertNotEqual(0, result.returncode)
        self.assertIn("output file already exists", result.stderr)

    def test_raw_identifier_never_appears_in_outputs(self):
        result = self.run_prepare([self.base_row()])

        self.assertEqual(0, result.returncode, result.stderr)
        combined = ""
        for name in (
            "paysim-events.jsonl",
            "paysim-labels.jsonl",
            "paysim-rejected.jsonl",
            "paysim-validation-report.json",
        ):
            combined += (self.output_dir / name).read_text(encoding="utf-8")
        self.assertNotIn("C123", combined)
        self.assertNotIn("M456", combined)


if __name__ == "__main__":
    unittest.main()
