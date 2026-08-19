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

MANIFEST_PATH = Path(__file__).parents[2] / "load-test" / "workloads" / "v3" / "normal-baseline-v1.json"


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

    def test_rejects_unsupported_driver(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["driverType"] = "UNKNOWN"
        with self.assertRaisesRegex(validator.ManifestError, "unsupported driverType"):
            validator.validate_manifest(invalid)

    def test_rejects_unsupported_event_time_mode(self):
        invalid = copy.deepcopy(self.manifest)
        invalid["eventTimeMode"] = "COPY_SOURCE_TIME"
        with self.assertRaisesRegex(validator.ManifestError, "unsupported eventTimeMode"):
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


if __name__ == "__main__":
    unittest.main()
