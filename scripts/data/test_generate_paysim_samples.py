import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("generate_paysim_samples.py")


def write_jsonl(path, rows):
    path.write_text("\n".join(json.dumps(row, sort_keys=True) for row in rows) + ("\n" if rows else ""), encoding="utf-8")


def read_jsonl(path):
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line]


class GeneratePaySimSamplesTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.events_path = self.root / "paysim-events.jsonl"
        self.labels_path = self.root / "paysim-labels.jsonl"
        self.report_path = self.root / "paysim-validation-report.json"
        self.output_dir = self.root / "samples"
        self.write_fixture()

    def tearDown(self):
        self.temp_dir.cleanup()

    def event(self, index, event_type="TRANSFER", **overrides):
        suffix = f"{index:09d}"
        value = {
            "eventId": f"paysim-{suffix}",
            "userId": f"U-{index:016x}",
            "accountId": f"A-{index:016x}",
            "destinationAccountId": f"D-{index + 10:016x}",
            "eventType": event_type,
            "amount": "100.00",
            "currency": "KRW",
            "eventTime": "2026-01-01T01:00:00Z",
            "traceId": f"trace-paysim-{suffix}",
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

    def label(self, index, is_fraud=False, event_type="TRANSFER"):
        return {
            "eventId": f"paysim-{index:09d}",
            "isFraud": is_fraud,
            "sourceFlaggedFraud": False,
            "sourceStep": 1,
            "sourceType": event_type,
        }

    def report(self, **overrides):
        value = {
            "datasetSlug": "ealaxi/paysim1",
            "rawFileName": "PS_20174392719_1491204439457_log.csv",
            "inputSha256": "a" * 64,
            "hashSaltSource": "env:PAYSIM_HASH_SALT",
        }
        value.update(overrides)
        return value

    def write_fixture(self, events=None, labels=None, report=None):
        write_jsonl(self.events_path, events if events is not None else [self.event(i) for i in range(1, 6)])
        write_jsonl(
            self.labels_path,
            labels
            if labels is not None
            else [
                self.label(1, is_fraud=False),
                self.label(2, is_fraud=True),
                self.label(3, is_fraud=False),
                self.label(4, is_fraud=False),
                self.label(5, is_fraud=True),
            ],
        )
        self.report_path.write_text(json.dumps(report if report is not None else self.report(), sort_keys=True), encoding="utf-8")

    def run_generate(self, *extra_args):
        return subprocess.run(
            [
                sys.executable,
                str(SCRIPT_PATH),
                "--events",
                str(self.events_path),
                "--labels",
                str(self.labels_path),
                "--report",
                str(self.report_path),
                "--output-dir",
                str(self.output_dir),
                *extra_args,
            ],
            text=True,
            capture_output=True,
            check=False,
        )

    def test_sample_size_three_creates_events_labels_and_manifest(self):
        result = self.run_generate("--sample-size", "3", "--force")
        self.assertEqual(0, result.returncode, result.stderr)
        events = read_jsonl(self.output_dir / "paysim-events-sample.jsonl")
        labels = read_jsonl(self.output_dir / "paysim-labels-sample.jsonl")
        manifest = json.loads((self.output_dir / "paysim-sample-manifest.json").read_text())
        self.assertEqual(3, len(events))
        self.assertEqual({row["eventId"] for row in events}, {row["eventId"] for row in labels})
        self.assertEqual(3, manifest["sampleEvents"])
        self.assertEqual(3, manifest["sampleLabels"])

    def test_sample_size_over_limit_fails(self):
        result = self.run_generate("--sample-size", "1001", "--force")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("sample-size", result.stderr)

    def test_balanced_strategy_includes_fraud_first(self):
        result = self.run_generate("--sample-size", "2", "--strategy", "balanced", "--force")
        self.assertEqual(0, result.returncode, result.stderr)
        labels = read_jsonl(self.output_dir / "paysim-labels-sample.jsonl")
        self.assertTrue(any(row["isFraud"] for row in labels))
        self.assertEqual(2, sum(1 for row in labels if row["isFraud"]))

    def test_event_sample_has_no_label_or_received_at(self):
        result = self.run_generate("--sample-size", "3", "--force")
        self.assertEqual(0, result.returncode, result.stderr)
        text = (self.output_dir / "paysim-events-sample.jsonl").read_text()
        self.assertNotIn("isFraud", text)
        self.assertNotIn("isFlaggedFraud", text)
        self.assertNotIn("sourceFlaggedFraud", text)
        self.assertNotIn("receivedAt", text)

    def test_raw_identifier_pattern_fails(self):
        self.write_fixture(events=[self.event(1, userId="U-C12345")], labels=[self.label(1)])
        result = self.run_generate("--sample-size", "1", "--force")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("raw PaySim identifiers", result.stderr)

    def test_manifest_has_hash_salt_source_only(self):
        result = self.run_generate("--sample-size", "2", "--force")
        self.assertEqual(0, result.returncode, result.stderr)
        manifest = json.loads((self.output_dir / "paysim-sample-manifest.json").read_text())
        self.assertIn("hashSaltSource", manifest)
        self.assertNotIn("hashSaltValue", manifest)
        self.assertFalse(manifest["containsRuntimeLabels"])
        self.assertFalse(manifest["containsRawIdentifiers"])

    def test_existing_output_without_force_fails(self):
        first = self.run_generate("--sample-size", "2", "--force")
        self.assertEqual(0, first.returncode, first.stderr)
        second = self.run_generate("--sample-size", "2")
        self.assertNotEqual(0, second.returncode)
        self.assertIn("output file already exists", second.stderr)

    def test_require_non_default_salt_fails_for_default_local(self):
        self.write_fixture(report=self.report(hashSaltSource="default-local"))
        result = self.run_generate("--sample-size", "2", "--require-non-default-salt", "--force")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("non-default", result.stderr)

    def test_generated_files_are_under_one_mb(self):
        result = self.run_generate("--sample-size", "5", "--force")
        self.assertEqual(0, result.returncode, result.stderr)
        for name in ("paysim-events-sample.jsonl", "paysim-labels-sample.jsonl", "paysim-sample-manifest.json"):
            self.assertLess((self.output_dir / name).stat().st_size, 1048576)


if __name__ == "__main__":
    unittest.main()
