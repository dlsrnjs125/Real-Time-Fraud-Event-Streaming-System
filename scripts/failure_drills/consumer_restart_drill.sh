#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

CONSUMER_RESTART_WAIT_SECONDS="${CONSUMER_RESTART_WAIT_SECONDS:-90}"

require_command curl
require_command docker

EVENT_ID="${EVENT_ID:-evt-phase8-consumer-restart-$(date +%s)}"

log "Consumer restart drill started"
log "This repository runs app-consumer as a local Gradle process, not as a Docker Compose service."
log "Precondition: stop app-consumer before running this script, then restart it with 'make consumer' when prompted."

require_http_ok "${API_BASE_URL}/actuator/health"

if curl -fsS "$CONSUMER_HEALTH_URL" >/dev/null 2>&1; then
  fail "app-consumer is still reachable. Stop it first, then rerun this drill."
fi

log "Publishing event while consumer is stopped: ${EVENT_ID}"
post_transaction_event "$EVENT_ID" "user-phase8-consumer-restart" "acc-phase8-consumer-restart" "10000"

log "Start app-consumer now in another terminal: make consumer"
log "Waiting up to ${CONSUMER_RESTART_WAIT_SECONDS}s for fraud result"

EVENT_POLL_ATTEMPTS="$CONSUMER_RESTART_WAIT_SECONDS"
RESULT="$(wait_for_fraud_result_matching "$EVENT_ID" "\"eventId\":\"${EVENT_ID}\"")"
assert_contains "$RESULT" '"degraded":false' "expected non-degraded result after consumer restart"

PROCESSING_LOG="$(get_processing_log "$EVENT_ID")"
assert_contains "$PROCESSING_LOG" '"logs":\[' "expected processing log response"
assert_contains "$PROCESSING_LOG" '"status":"PROCESSED"' "expected processed processing log"

ROW_COUNT="$(fraud_result_row_count "$EVENT_ID")"
if [ "$ROW_COUNT" != "1" ]; then
  fail "expected exactly one fraud_detection_results row for ${EVENT_ID}, got ${ROW_COUNT}"
fi

log "Consumer restart drill PASS"
log "Idempotency evidence: fraud_detection_results row count for ${EVENT_ID} is 1."
