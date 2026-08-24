import copy
import importlib.util
import json
import sys
import unittest
from pathlib import Path


SCRIPT_PATH = Path(__file__).with_name("validate_v3_workload_manifest.py")
SPEC = importlib.util.spec_from_file_location("validate_v3_workload_manifest", SCRIPT_PATH)
validator = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules["validate_v3_workload_manifest"] = validator
SPEC.loader.exec_module(validator)

WORKLOAD_DIR = Path(__file__).parents[2] / "load-test" / "workloads" / "v3"
MANIFEST_PATH = WORKLOAD_DIR / "normal-baseline-v1.json"


class ValidateV3WorkloadManifestTest(unittest.TestCase):
    def setUp(self):
        self.manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))

    def test_committed_normal_manifest_is_valid_and_seed_is_deterministic(self):
        validator.validate_manifest(self.manifest)
        first = json.dumps(self.manifest, sort_keys=True)
        second = json.dumps(json.loads(MANIFEST_PATH.read_text(encoding="utf-8")), sort_keys=True)
        self.assertEqual(first, second)
        self.assertEqual(310519, self.manifest["randomSeed"])
        self.assertEqual(50, self.manifest["userCardinality"])

    def test_committed_phase1_manifests_are_valid(self):
        for filename in [
            "capacity-discovery-v1.json",
            "knee-confirmation-v1.json",
            "backlog-recovery-v1.json",
        ]:
            with self.subTest(filename=filename):
                manifest = json.loads((WORKLOAD_DIR / filename).read_text(encoding="utf-8"))
                validator.validate_manifest(manifest)

    def test_committed_phase2_stateful_manifests_are_valid(self):
        for filename in [
            "state-size-baseline-v1.json",
            "state-size-high-density-v1.json",
        ]:
            with self.subTest(filename=filename):
                manifest = json.loads((WORKLOAD_DIR / filename).read_text(encoding="utf-8"))
                validator.validate_manifest(manifest)

    def test_committed_phase3_partition_manifests_are_valid(self):
        for filename in [
            "partition-balanced-v1.json",
            "partition-skew-hot-p2-v1.json",
        ]:
            with self.subTest(filename=filename):
                manifest = json.loads((WORKLOAD_DIR / filename).read_text(encoding="utf-8"))
                validator.validate_manifest(manifest)

    def test_committed_phase4_stateful_redelivery_manifest_is_valid(self):
        manifest = json.loads((WORKLOAD_DIR / "stateful-redelivery-v1.json").read_text(encoding="utf-8"))

        validator.validate_manifest(manifest)
        profile = manifest["statefulWindowProfile"]
        self.assertEqual(3, profile["redeliveryDrillTargetIndex"])
        self.assertEqual(4, profile["redeliveryDrillNextIndex"])
        self.assertEqual(5, profile["expectedNextEventTransactionCount"])
        self.assertEqual(500000, profile["expectedNextEventAmountSum"])
        self.assertEqual(30, profile["expectedNextEventRiskScore"])
        self.assertEqual("RAPID_TRANSACTION_COUNT", profile["expectedNextEventMatchedRule"])

    def test_committed_phase5_late_out_of_order_manifest_is_valid(self):
        manifest = json.loads((WORKLOAD_DIR / "late-out-of-order-v1.json").read_text(encoding="utf-8"))

        validator.validate_manifest(manifest)
        self.assertEqual("LATE_OUT_OF_ORDER", manifest["workloadRole"])
        self.assertEqual("CONTROLLED_LATENESS", manifest["eventTimeMode"])
        self.assertEqual("5m", manifest["latenessProfile"]["allowedLateness"])
        self.assertEqual(50, manifest["latenessProfile"]["expectedTooLateEvents"])
        self.assertEqual(250, manifest["latenessProfile"]["expectedAcceptedLateEvents"])

    def test_committed_phase6_source_delay_manifests_are_valid(self):
        manifests = {
            "organic-burst-v1.json": ("ORGANIC_BURST", "REBASE_TO_ARRIVAL", "NORMAL", 0),
            "catch-up-burst-v1.json": ("CATCH_UP_BURST", "CONTROLLED_LATENESS", "BATCH_CATCHUP", 270),
        }

        for filename, expected in manifests.items():
            with self.subTest(filename=filename):
                manifest = json.loads((WORKLOAD_DIR / filename).read_text(encoding="utf-8"))
                validator.validate_manifest(manifest)
                expected_role, expected_mode, expected_source_profile, expected_delay_seconds = expected
                self.assertEqual(expected_role, manifest["workloadRole"])
                self.assertEqual("HTTP_SOURCE_EMULATOR", manifest["driverType"])
                self.assertEqual(expected_mode, manifest["eventTimeMode"])
                self.assertEqual(expected_source_profile, manifest["sourceProfile"])
                self.assertEqual(expected_delay_seconds, manifest["sourceDelayProfile"]["expectedSourceDelaySeconds"])
                self.assertEqual(9000, manifest["sourceDelayProfile"]["expectedAcceptedEvents"])
                self.assertEqual(0, manifest["sourceDelayProfile"]["expectedTooLateEvents"])

    def test_phase6_organic_and_catch_up_manifests_keep_paired_runtime_shape(self):
        organic = json.loads((WORKLOAD_DIR / "organic-burst-v1.json").read_text(encoding="utf-8"))
        catch_up = json.loads((WORKLOAD_DIR / "catch-up-burst-v1.json").read_text(encoding="utf-8"))

        validator.validate_manifest(organic)
        validator.validate_manifest(catch_up)

        for field in ["targetEps", "duration", "eventLimit", "userCardinality"]:
            with self.subTest(field=field):
                self.assertEqual(organic[field], catch_up[field])
        self.assertEqual(
            organic["sourceDelayProfile"]["eventAmount"],
            catch_up["sourceDelayProfile"]["eventAmount"],
        )
        self.assertEqual("REBASE_TO_ARRIVAL", organic["eventTimeMode"])
        self.assertEqual("CONTROLLED_LATENESS", catch_up["eventTimeMode"])
        self.assertEqual(0, organic["sourceDelayProfile"]["expectedSourceDelaySeconds"])
        self.assertEqual(270, catch_up["sourceDelayProfile"]["expectedSourceDelaySeconds"])

    def test_rejects_unsupported_driver(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["driverType"] = "UNKNOWN"
        with self.assertRaisesRegex(validator.ManifestError, "schema validation failed.*driverType"):
            validator.validate_manifest(invalid)

    def test_rejects_unsupported_event_time_mode(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["eventTimeMode"] = "COPY_SOURCE_TIME"
        with self.assertRaisesRegex(validator.ManifestError, "schema validation failed.*eventTimeMode"):
            validator.validate_manifest(invalid)

    def test_rejects_workload_id_that_only_schema_validates(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["workloadId"] = "Invalid_Workload_Id"
        with self.assertRaisesRegex(validator.ManifestError, "schema validation failed.*workloadId"):
            validator.validate_manifest(invalid)

    def test_rejects_normal_workload_that_preserves_historical_time(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["eventTimeMode"] = "PRESERVE_SOURCE_TIME"
        with self.assertRaisesRegex(validator.ManifestError, "requires REBASE_TO_ARRIVAL"):
            validator.validate_manifest(invalid)

    def test_rejects_partition_skew_without_partition_affinity(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["workloadRole"] = "PARTITION_SKEW"
        with self.assertRaisesRegex(validator.ManifestError, "requires PARTITION_AFFINITY"):
            validator.validate_manifest(invalid)

    def test_rejects_partition_skew_with_user_concentration(self):
        invalid = json.loads((WORKLOAD_DIR / "partition-skew-hot-p2-v1.json").read_text(encoding="utf-8"))
        invalid["targetUserConcentration"] = {"topUserShare": 0.5}

        with self.assertRaisesRegex(validator.ManifestError, "must not use targetUserConcentration"):
            validator.validate_manifest(invalid)

    def test_rejects_partition_skew_with_wrong_affinity_strategy(self):
        invalid = json.loads((WORKLOAD_DIR / "partition-skew-hot-p2-v1.json").read_text(encoding="utf-8"))
        invalid["partitionAffinityStrategy"] = "UNHASHED_ROUND_ROBIN"

        with self.assertRaisesRegex(validator.ManifestError, "unsupported partitionAffinityStrategy"):
            validator.validate_manifest(invalid)

    def test_rejects_partition_distribution_with_non_contiguous_partitions(self):
        invalid = json.loads((WORKLOAD_DIR / "partition-skew-hot-p2-v1.json").read_text(encoding="utf-8"))
        invalid["targetPartitionDistribution"] = {
            "0": 0.5,
            "2": 0.5,
        }

        with self.assertRaisesRegex(validator.ManifestError, "contiguous partitions"):
            validator.validate_manifest(invalid)

    def test_rejects_non_integer_seed(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["randomSeed"] = 1.5
        with self.assertRaisesRegex(validator.ManifestError, "randomSeed"):
            validator.validate_manifest(invalid)

    def test_rejects_invalid_user_cardinality(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["userCardinality"] = 0
        with self.assertRaisesRegex(validator.ManifestError, "userCardinality"):
            validator.validate_manifest(invalid)

    def test_rejects_stage_event_limit_drift(self):
        invalid = json.loads((WORKLOAD_DIR / "capacity-discovery-v1.json").read_text(encoding="utf-8"))
        invalid["eventLimit"] = invalid["eventLimit"] + 1
        with self.assertRaisesRegex(validator.ManifestError, "eventLimit must equal"):
            validator.validate_manifest(invalid)

    def test_rejects_non_stage_event_limit_drift(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["eventLimit"] = invalid["eventLimit"] + 1
        with self.assertRaisesRegex(validator.ManifestError, "eventLimit must equal targetEps"):
            validator.validate_manifest(invalid)

    def test_rejects_stage_target_eps_drift(self):
        invalid = json.loads((WORKLOAD_DIR / "capacity-discovery-v1.json").read_text(encoding="utf-8"))
        invalid["targetEps"] = 999
        with self.assertRaisesRegex(validator.ManifestError, "targetEps must equal"):
            validator.validate_manifest(invalid)

    def test_rejects_stateful_window_profile_on_non_stateful_workload(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["statefulWindowProfile"] = {
            "runtimeWindow": "5m",
            "expectedEventsPerUserInWindow": 3,
            "expectedMaxEventsPerUserInWindow": 3,
            "eventAmount": 250000,
            "expectedAmountSumPerUserInWindow": 750000,
        }

        with self.assertRaisesRegex(validator.ManifestError, "only allowed"):
            validator.validate_manifest(invalid)

    def test_rejects_lateness_profile_on_non_late_workload(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["latenessProfile"] = {
            "allowedLateness": "5m",
            "tooLateAge": "10m",
            "eventAmount": 100000,
            "buckets": [
                {"name": "ON_TIME", "lateness": "0s"},
                {"name": "TOO_LATE", "lateness": "10m"},
            ],
            "expectedTooLateEvents": 75,
            "expectedAcceptedLateEvents": 75,
        }

        with self.assertRaisesRegex(validator.ManifestError, "latenessProfile is only allowed"):
            validator.validate_manifest(invalid)

    def test_rejects_source_delay_profile_on_non_phase6_workload(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["sourceDelayProfile"] = {
            "allowedLateness": "5m",
            "sourceDelay": "0s",
            "eventAmount": 100000,
            "expectedSourceDelaySeconds": 0,
            "expectedAcceptedEvents": invalid["eventLimit"],
            "expectedTooLateEvents": 0,
        }

        with self.assertRaisesRegex(validator.ManifestError, "sourceDelayProfile is only allowed"):
            validator.validate_manifest(invalid)

    def test_rejects_catch_up_source_delay_expected_count_drift(self):
        invalid = json.loads((WORKLOAD_DIR / "catch-up-burst-v1.json").read_text(encoding="utf-8"))
        invalid["sourceDelayProfile"]["expectedAcceptedEvents"] = 8999

        with self.assertRaisesRegex(validator.ManifestError, "expectedAcceptedEvents"):
            validator.validate_manifest(invalid)

    def test_rejects_organic_burst_with_non_zero_source_delay(self):
        invalid = json.loads((WORKLOAD_DIR / "organic-burst-v1.json").read_text(encoding="utf-8"))
        invalid["sourceDelayProfile"]["sourceDelay"] = "1s"
        invalid["sourceDelayProfile"]["expectedSourceDelaySeconds"] = 1

        with self.assertRaisesRegex(validator.ManifestError, "sourceDelay must match"):
            validator.validate_manifest(invalid)

    def test_rejects_late_out_of_order_without_lateness_profile(self):
        invalid = json.loads((WORKLOAD_DIR / "late-out-of-order-v1.json").read_text(encoding="utf-8"))
        invalid["latenessProfile"] = None

        with self.assertRaisesRegex(validator.ManifestError, "requires latenessProfile"):
            validator.validate_manifest(invalid)

    def test_rejects_late_out_of_order_expected_too_late_drift(self):
        invalid = json.loads((WORKLOAD_DIR / "late-out-of-order-v1.json").read_text(encoding="utf-8"))
        invalid["latenessProfile"]["expectedTooLateEvents"] = 49

        with self.assertRaisesRegex(validator.ManifestError, "expectedTooLateEvents"):
            validator.validate_manifest(invalid)

    def test_rejects_monotonic_out_of_order_pattern(self):
        invalid = json.loads((WORKLOAD_DIR / "late-out-of-order-v1.json").read_text(encoding="utf-8"))
        invalid["latenessProfile"]["outOfOrderPattern"] = [
            "ON_TIME",
            "LATE_30S",
            "LATE_2M",
        ]

        with self.assertRaisesRegex(validator.ManifestError, "non-monotonic"):
            validator.validate_manifest(invalid)

    def test_rejects_duplicate_out_of_order_pattern_bucket(self):
        invalid = json.loads((WORKLOAD_DIR / "late-out-of-order-v1.json").read_text(encoding="utf-8"))
        invalid["latenessProfile"]["outOfOrderPattern"] = [
            "ON_TIME",
            "LATE_2M",
            "LATE_2M",
        ]

        with self.assertRaisesRegex(validator.ManifestError, "bucket names must be unique"):
            validator.validate_manifest(invalid)

    def test_rejects_stateful_window_duration_larger_than_runtime_window(self):
        invalid = json.loads((WORKLOAD_DIR / "state-size-baseline-v1.json").read_text(encoding="utf-8"))
        invalid["duration"] = "10m"
        invalid["eventLimit"] = invalid["targetEps"] * 10 * 60

        with self.assertRaisesRegex(validator.ManifestError, "duration must fit inside runtimeWindow"):
            validator.validate_manifest(invalid)

    def test_rejects_stateful_window_density_drift(self):
        invalid = json.loads((WORKLOAD_DIR / "state-size-baseline-v1.json").read_text(encoding="utf-8"))
        invalid["statefulWindowProfile"]["expectedEventsPerUserInWindow"] = 999

        with self.assertRaisesRegex(validator.ManifestError, "expectedEventsPerUserInWindow"):
            validator.validate_manifest(invalid)

    def test_rejects_stateful_redelivery_target_at_first_event(self):
        invalid = json.loads((WORKLOAD_DIR / "stateful-redelivery-v1.json").read_text(encoding="utf-8"))
        invalid["statefulWindowProfile"]["redeliveryDrillTargetIndex"] = 0
        invalid["statefulWindowProfile"]["redeliveryDrillNextIndex"] = 1
        invalid["statefulWindowProfile"]["expectedNextEventTransactionCount"] = 2

        with self.assertRaisesRegex(validator.ManifestError, "redeliveryDrillTargetIndex must be at least 3"):
            validator.validate_manifest(invalid)

    def test_rejects_stateful_redelivery_next_event_amount_drift(self):
        invalid = json.loads((WORKLOAD_DIR / "stateful-redelivery-v1.json").read_text(encoding="utf-8"))
        invalid["statefulWindowProfile"]["expectedNextEventAmountSum"] = 200000

        with self.assertRaisesRegex(validator.ManifestError, "expectedNextEventAmountSum"):
            validator.validate_manifest(invalid)


if __name__ == "__main__":
    unittest.main()
