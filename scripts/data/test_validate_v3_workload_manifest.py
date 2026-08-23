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

    def test_rejects_stateful_window_duration_larger_than_runtime_window(self):
        invalid = json.loads((WORKLOAD_DIR / "state-size-baseline-v1.json").read_text(encoding="utf-8"))
        invalid["duration"] = "10m"

        with self.assertRaisesRegex(validator.ManifestError, "duration must fit inside runtimeWindow"):
            validator.validate_manifest(invalid)

    def test_rejects_stateful_window_density_drift(self):
        invalid = json.loads((WORKLOAD_DIR / "state-size-baseline-v1.json").read_text(encoding="utf-8"))
        invalid["statefulWindowProfile"]["expectedEventsPerUserInWindow"] = 999

        with self.assertRaisesRegex(validator.ManifestError, "expectedEventsPerUserInWindow"):
            validator.validate_manifest(invalid)


if __name__ == "__main__":
    unittest.main()
