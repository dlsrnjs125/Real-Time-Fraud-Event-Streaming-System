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
            "retry_count": 0,
            "stop_on_error": False,
            "admin_token": None,
            "admin_token_env": "ADMIN_API_TOKEN",
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
        report = replay.replay(self.args(dry_run=True, admin_token="secret-token"))
        text = json.dumps(report, sort_keys=True)

        self.assertNotIn("secret-token", text)
        self.assertNotIn("C12345", text)
        self.assertNotIn("requestBody", text)
        self.assertEqual(1, report["payloadRejected"])
        self.assertTrue(report["authUsed"])

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
