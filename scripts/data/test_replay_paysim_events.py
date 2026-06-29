import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest import mock


SCRIPT_PATH = Path(__file__).with_name("replay_paysim_events.py")
SPEC = importlib.util.spec_from_file_location("replay_paysim_events", SCRIPT_PATH)
replay = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules["replay_paysim_events"] = replay
SPEC.loader.exec_module(replay)


def write_jsonl(path, rows):
    path.write_text("\n".join(json.dumps(row, sort_keys=True) for row in rows) + ("\n" if rows else ""), encoding="utf-8")


class ReplayPaySimEventsTest(unittest.TestCase):
    def setUp(self):
        self.temp_dir = tempfile.TemporaryDirectory()
        self.root = Path(self.temp_dir.name)
        self.input = self.root / "events.jsonl"
        self.report = self.root / "report.json"

    def tearDown(self):
        self.temp_dir.cleanup()

    def event(self, **overrides):
        value = {
            "eventId": "paysim-000000001",
            "userId": "U-abcdef1234567890",
            "accountId": "A-abcdef1234567890",
            "destinationAccountId": "D-fedcba0987654321",
            "eventType": "TRANSFER",
            "amount": "100.00",
            "currency": "KRW",
            "eventTime": "2026-01-01T01:00:00Z",
            "traceId": "trace-paysim-000000001",
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

    def args(self, **overrides):
        value = {
            "input": self.input,
            "endpoint": "http://localhost:8080/api/v1/transactions/events",
            "max_events": None,
            "rate_per_second": 1000.0,
            "timeout_seconds": 3.0,
            "report_output": self.report,
            "dry_run": False,
            "force": True,
            "event_id_prefix": None,
            "idempotency_mode": "preserve",
            "event_type_policy": "current-api",
            "retry_count": 0,
            "retry_connection_error": False,
            "stop_on_error": False,
            "auth_token": None,
            "auth_token_env": "PAYSIM_REPLAY_AUTH_TOKEN",
        }
        value.update(overrides)
        return SimpleNamespace(**value)

    def test_valid_event_maps_to_api_request_and_drops_non_dto_fields(self):
        dropped = replay.Counter()
        payload, trace_id = replay.to_api_request(self.event(), self.args(), dropped)

        self.assertEqual("trace-paysim-000000001", trace_id)
        self.assertEqual("paysim-000000001", payload["eventId"])
        self.assertEqual("U-abcdef1234567890", payload["userId"])
        self.assertEqual("A-abcdef1234567890", payload["accountId"])
        self.assertEqual("TRANSFER", payload["eventType"])
        self.assertEqual("100.00", payload["amount"])
        self.assertNotIn("traceId", payload)
        self.assertNotIn("receivedAt", payload)
        self.assertEqual(1, dropped["balanceFeatures"])
        self.assertEqual(1, dropped["source"])
        self.assertEqual(1, dropped["schemaVersion"])
        self.assertEqual(1, dropped["destinationAccountId"])

    def test_native_mapping_fields_are_reported_for_dry_run(self):
        write_jsonl(
            self.input,
            [
                self.event(
                    eventType="WITHDRAWAL",
                    nativeEventType="CASH_OUT",
                    normalizedEventType="WITHDRAWAL",
                    typeSupportLevel="replay-supported",
                    typeMappingPolicyVersion="paysim-native-mapping-v1",
                )
            ],
        )

        report = replay.replay(self.args(dry_run=True))

        self.assertEqual(1, report["payloadAccepted"])
        self.assertEqual("paysim-native-mapping-v1", report["mappingPolicyVersion"])
        self.assertEqual({"paysim-native-mapping-v1": 1}, report["mappingPolicyVersions"])
        self.assertEqual({"CASH_OUT": 1}, report["nativeTypeDistribution"])
        self.assertEqual({"WITHDRAWAL": 1}, report["normalizedTypeDistribution"])
        self.assertEqual({"replay-supported": 1}, report["typeSupportLevelDistribution"])
        self.assertEqual({"CASH_OUT": 1}, report["inputNativeTypeDistribution"])
        self.assertEqual({"WITHDRAWAL": 1}, report["acceptedNormalizedTypeDistribution"])
        self.assertEqual({}, report["rejectedNativeTypeDistribution"])
        self.assertEqual({}, report["excludedByType"])
        self.assertEqual("required_for_phase8_paysim_native_contract", report["mappingMetadataPolicy"])
        self.assertEqual(0, report["missingMappingMetadata"])

    def test_paysim_row_without_mapping_metadata_is_counted(self):
        write_jsonl(self.input, [self.event()])

        report = replay.replay(self.args(dry_run=True))

        self.assertEqual(1, report["payloadAccepted"])
        self.assertEqual(1, report["missingMappingMetadata"])

    def assert_validation_fails(self, event, reason):
        with self.assertRaises(replay.PayloadValidationError) as context:
            replay.to_api_request(event, self.args(), replay.Counter())
        self.assertIn(reason, context.exception.reason)

    def test_label_fields_fail_validation(self):
        self.assert_validation_fails(self.event(isFraud=True), "LABEL_FIELD")

    def test_raw_identifier_fields_fail_validation(self):
        self.assert_validation_fails(self.event(nameOrig="hidden"), "RAW_IDENTIFIER_FIELD")
        self.assert_validation_fails(self.event(nameDest="hidden"), "RAW_IDENTIFIER_FIELD")

    def test_raw_identifier_pattern_fails_validation(self):
        self.assert_validation_fails(self.event(userId="U-C12345"), "RAW_IDENTIFIER_VALUE")

    def test_received_at_fails_validation(self):
        self.assert_validation_fails(self.event(receivedAt="2026-01-01T01:00:01Z"), "RECEIVED_AT_PRESENT")

    def test_invalid_hash_id_format_fails_validation(self):
        self.assert_validation_fails(self.event(userId="U-ABCDEF1234567890"), "INVALID_HASH_ID:userId")

    def test_unsupported_current_api_event_type_fails_before_http(self):
        self.assert_validation_fails(self.event(eventType="CASH_OUT"), "UNSUPPORTED_EVENT_TYPE_FOR_CURRENT_API")

    def test_normalized_event_type_mismatch_fails_validation(self):
        self.assert_validation_fails(
            self.event(
                eventType="TRANSFER",
                nativeEventType="CASH_OUT",
                normalizedEventType="WITHDRAWAL",
                typeMappingPolicyVersion="paysim-native-mapping-v1",
            ),
            "NATIVE_MAPPING_MISMATCH:CASH_OUT->TRANSFER",
        )

    def test_debit_native_type_cannot_be_smuggled_as_deposit(self):
        self.assert_validation_fails(
            self.event(
                eventType="DEPOSIT",
                nativeEventType="DEBIT",
                normalizedEventType="DEPOSIT",
                typeSupportLevel="replay-supported",
                typeMappingPolicyVersion="paysim-native-mapping-v1",
            ),
            "UNSUPPORTED_NATIVE_TYPE:DEBIT",
        )

    def test_native_mapping_requires_policy_version(self):
        self.assert_validation_fails(
            self.event(
                eventType="WITHDRAWAL",
                nativeEventType="CASH_OUT",
                normalizedEventType="WITHDRAWAL",
                typeSupportLevel="replay-supported",
            ),
            "UNSUPPORTED_MAPPING_POLICY_VERSION",
        )

    def test_type_support_level_mismatch_fails_validation(self):
        self.assert_validation_fails(
            self.event(
                eventType="WITHDRAWAL",
                nativeEventType="CASH_OUT",
                normalizedEventType="WITHDRAWAL",
                typeSupportLevel="production-supported",
                typeMappingPolicyVersion="paysim-native-mapping-v1",
            ),
            "TYPE_SUPPORT_LEVEL_MISMATCH:CASH_OUT",
        )

    def test_non_positive_amount_fails_replay_validation(self):
        self.assert_validation_fails(self.event(amount="0"), "INVALID_AMOUNT")
        self.assert_validation_fails(self.event(amount="-1"), "INVALID_AMOUNT")

    def test_event_type_policy_preserve_allows_native_paysim_type(self):
        payload, _trace_id = replay.to_api_request(
            self.event(eventType="CASH_OUT"),
            self.args(event_type_policy="preserve"),
            replay.Counter(),
        )
        self.assertEqual("CASH_OUT", payload["eventType"])

    def test_event_type_policy_preserve_is_dry_run_only(self):
        with self.assertRaises(replay.ReplayError):
            replay.validate_args(self.args(event_type_policy="preserve", dry_run=False))
        replay.validate_args(self.args(event_type_policy="preserve", dry_run=True))

    def test_idempotency_preserve_keeps_event_id(self):
        payload, _trace_id = replay.to_api_request(self.event(), self.args(idempotency_mode="preserve"), replay.Counter())
        self.assertEqual("paysim-000000001", payload["eventId"])

    def test_idempotency_prefix_adds_prefix(self):
        payload, _trace_id = replay.to_api_request(
            self.event(),
            self.args(idempotency_mode="prefix", event_id_prefix="local-smoke"),
            replay.Counter(),
        )
        self.assertEqual("local-smoke-paysim-000000001", payload["eventId"])

    def test_idempotency_prefix_requires_event_id_prefix(self):
        with self.assertRaises(replay.ReplayError):
            replay.validate_args(self.args(idempotency_mode="prefix", event_id_prefix=None))

    def test_dry_run_does_not_send_http_request(self):
        write_jsonl(self.input, [self.event()])

        with mock.patch.object(replay, "make_http_request") as http:
            report = replay.replay(self.args(dry_run=True))

        http.assert_not_called()
        self.assertEqual(1, report["payloadAccepted"])
        self.assertEqual(0, report["httpSuccess"])

    def test_http_2xx_response_counts_success(self):
        write_jsonl(self.input, [self.event()])
        with mock.patch.object(replay, "make_http_request", return_value=202):
            report = replay.replay(self.args())
        self.assertEqual(1, report["httpSuccess"])

    def test_http_409_response_counts_duplicate_or_conflict(self):
        write_jsonl(self.input, [self.event()])
        with mock.patch.object(replay, "make_http_request", return_value=409):
            report = replay.replay(self.args())
        self.assertEqual(1, report["httpDuplicateOrConflict"])
        self.assertEqual("HTTP_409", report["failures"][0]["reason"])

    def test_http_5xx_response_counts_server_error(self):
        write_jsonl(self.input, [self.event()])
        with mock.patch.object(replay, "make_http_request", return_value=500):
            report = replay.replay(self.args())
        self.assertEqual(1, report["httpServerError"])
        self.assertEqual("HTTP_500", report["failures"][0]["reason"])

    def test_timeout_counts_timeout(self):
        write_jsonl(self.input, [self.event()])
        with mock.patch.object(replay, "make_http_request", side_effect=TimeoutError()):
            report = replay.replay(self.args())
        self.assertEqual(1, report["timeout"])
        self.assertEqual("TIMEOUT", report["failures"][0]["reason"])

    def test_report_does_not_store_token_request_body_or_raw_identifier(self):
        write_jsonl(self.input, [self.event(userId="U-C12345")])
        report = replay.replay(self.args(dry_run=True, auth_token="secret-token"))
        text = json.dumps(report, sort_keys=True)

        self.assertNotIn("secret-token", text)
        self.assertNotIn("C12345", text)
        self.assertNotIn("requestBody", text)
        self.assertEqual(1, report["payloadRejected"])
        self.assertTrue(report["authUsed"])

    def test_unsupported_event_type_is_counted_in_report(self):
        write_jsonl(self.input, [self.event(eventType="CASH_OUT")])
        report = replay.replay(self.args(dry_run=True))
        self.assertEqual(1, report["payloadRejected"])
        self.assertEqual({"CASH_OUT": 1}, report["unsupportedEventTypes"])
        self.assertEqual({"CASH_OUT": 1}, report["excludedByType"])
        self.assertEqual({"CASH_OUT": 1}, report["rejectedNativeTypeDistribution"])
        self.assertEqual({}, report["acceptedNativeTypeDistribution"])

    def test_retry_success_keeps_final_outcome_event_level(self):
        write_jsonl(self.input, [self.event()])
        with mock.patch.object(replay, "make_http_request", side_effect=[500, 202]):
            report = replay.replay(self.args(retry_count=1))
        self.assertEqual(1, report["httpSuccess"])
        self.assertEqual(0, report["httpServerError"])
        self.assertEqual(1, report["retryAttempts"])
        self.assertEqual(1, report["retryServerErrorAttempts"])

    def test_connection_error_is_not_retried_by_default(self):
        write_jsonl(self.input, [self.event()])
        with mock.patch.object(replay, "make_http_request", side_effect=replay.urllib.error.URLError("refused")) as http:
            report = replay.replay(self.args(retry_count=2))
        self.assertEqual(1, http.call_count)
        self.assertEqual(1, report["connectionError"])
        self.assertEqual(0, report["retryConnectionErrorAttempts"])

    def test_connection_error_retry_requires_flag(self):
        write_jsonl(self.input, [self.event()])
        with mock.patch.object(replay, "make_http_request", side_effect=[replay.urllib.error.URLError("refused"), 202]) as http:
            report = replay.replay(self.args(retry_count=1, retry_connection_error=True))
        self.assertEqual(2, http.call_count)
        self.assertEqual(1, report["httpSuccess"])
        self.assertEqual(0, report["connectionError"])
        self.assertEqual(1, report["retryConnectionErrorAttempts"])

    def test_max_events_is_applied(self):
        write_jsonl(
            self.input,
            [
                self.event(eventId="paysim-000000001", traceId="trace-paysim-000000001"),
                self.event(eventId="paysim-000000002", traceId="trace-paysim-000000002"),
            ],
        )
        report = replay.replay(self.args(dry_run=True, max_events=1))
        self.assertEqual(1, report["totalRead"])

    def test_rate_per_second_must_be_positive(self):
        with self.assertRaises(replay.ReplayError):
            replay.validate_args(self.args(rate_per_second=0))


if __name__ == "__main__":
    unittest.main()
