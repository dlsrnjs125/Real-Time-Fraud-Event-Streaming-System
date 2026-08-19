import argparse
import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("profile_paysim_v3.py")
sys.path.insert(0, str(SCRIPT_PATH.parent))
SPEC = importlib.util.spec_from_file_location("profile_paysim_v3", SCRIPT_PATH)
profile = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules["profile_paysim_v3"] = profile
SPEC.loader.exec_module(profile)


class ProfilePaySimV3Test(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.output = Path(self.temp_dir.name) / "profile.json"
        self.fixture = Path(__file__).with_name("fixtures") / "paysim-v3-profile.csv"

    def tearDown(self):
        self.temp_dir.cleanup()

    def run_profile(self):
        args = argparse.Namespace(
            input=self.fixture,
            output=self.output,
            dataset_slug="fixture/paysim-v3",
            base_time="2026-01-01T00:00:00Z",
            hash_salt="fixture-private-salt",
            hash_salt_env="PAYSIM_HASH_SALT",
            limit=None,
            generated_at="2026-08-19T00:00:00Z",
            force=True,
        )
        return profile.process(args)

    def test_fixture_profile_contract(self):
        report = self.run_profile()

        self.assertEqual(5, report["totalEvents"])
        self.assertEqual(5, report["acceptedRows"])
        self.assertEqual(1, report["rejectedRows"])
        self.assertEqual(3, report["uniqueUsers"])
        self.assertEqual({"p50": 1, "p95": 3, "p99": 3, "max": 3}, report["eventsPerUser"])
        self.assertEqual(0.6, report["top1PercentTrafficShare"])
        self.assertEqual(
            {"p50": 1, "p95": 2, "p99": 2, "max": 2},
            report["eventsPerUserPerSourceStep"],
        )
        self.assertEqual(2, report["maximumEventsPerSourceStepPerUser"])
        self.assertEqual(0.33333333, report["usersWith2PlusEventsPerSourceStepRatio"])
        self.assertEqual(0.0, report["usersWith5PlusEventsPerSourceStepRatio"])
        self.assertEqual(0.2, report["fraudRatio"])
        self.assertEqual(
            {"CASH_OUT": {"count": 1, "ratio": 0.2}, "PAYMENT": {"count": 2, "ratio": 0.4}, "TRANSFER": {"count": 2, "ratio": 0.4}},
            report["nativeTransactionTypeDistribution"],
        )
        self.assertEqual(
            {"PAYMENT": {"count": 2, "ratio": 0.4}, "TRANSFER": {"count": 2, "ratio": 0.4}, "WITHDRAWAL": {"count": 1, "ratio": 0.2}},
            report["normalizedTransactionTypeDistribution"],
        )
        self.assertEqual(
            report["normalizedTransactionTypeDistribution"],
            report["transactionTypeDistribution"],
        )
        self.assertEqual({"p50": "30.00", "p95": "50.00", "p99": "50.00"}, report["amount"])
        self.assertEqual({"1": 3, "2": 1, "3": 1}, report["timeStepDistribution"])
        self.assertEqual(3, report["peakTimeStepCount"])
        self.assertEqual(0.6, report["sameUserRepeatRate"])
        self.assertEqual("1h", report["sourceTimeResolution"])
        self.assertEqual({"INVALID_AMOUNT": 1}, report["rejectedReasonDistribution"])

    def test_output_is_deterministic_and_does_not_expose_raw_identifiers_or_salt(self):
        first = self.run_profile()
        first_text = self.output.read_text(encoding="utf-8")
        second = self.run_profile()
        second_text = self.output.read_text(encoding="utf-8")

        self.assertEqual(first, second)
        self.assertEqual(first_text, second_text)
        self.assertEqual(first, json.loads(first_text))
        expected = (Path(__file__).with_name("fixtures") / "paysim-v3-profile-expected.json").read_text(
            encoding="utf-8"
        )
        self.assertEqual(expected, first_text)
        for sensitive_value in ("C100", "C200", "C300", "M100", "fixture-private-salt"):
            self.assertNotIn(sensitive_value, first_text)

    def test_nearest_rank_quantile_offsets(self):
        self.assertEqual(0, profile.quantile_offset(1, 0.99))
        self.assertEqual(2, profile.quantile_offset(5, 0.50))
        self.assertEqual(4, profile.quantile_offset(5, 0.95))


if __name__ == "__main__":
    unittest.main()
