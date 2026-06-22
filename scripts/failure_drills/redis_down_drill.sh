#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_command curl
require_command docker

cleanup() {
  docker compose -f "$COMPOSE_FILE" start redis >/dev/null 2>&1 || true
}
trap cleanup EXIT

EVENT_SUFFIX="$(date +%s)"
DEGRADED_EVENT_ID="evt-phase8-redis-down-${EVENT_SUFFIX}"
RECOVERY_EVENT_ID="evt-phase8-redis-recovery-${EVENT_SUFFIX}"

log "Redis down drill started"
log "Checking app-api and app-consumer endpoints"
require_http_ok "${API_BASE_URL}/actuator/health"
require_http_ok "${CONSUMER_HEALTH_URL}"

log "Stopping Redis"
docker compose -f "$COMPOSE_FILE" stop redis >/dev/null

log "Publishing degraded-mode event: ${DEGRADED_EVENT_ID}"
post_transaction_event "$DEGRADED_EVENT_ID" "user-phase8-redis-down" "acc-phase8-redis-down" "10000"

log "Waiting for degraded fraud result"
RESULT="$(wait_for_fraud_result_matching "$DEGRADED_EVENT_ID" '"degraded":true')"
assert_contains "$RESULT" "RAPID_TRANSACTION_COUNT" "expected RAPID_TRANSACTION_COUNT in skippedRules"
assert_contains "$RESULT" "WINDOW_AMOUNT_SUM" "expected WINDOW_AMOUNT_SUM in skippedRules"

log "Checking degraded metrics"
assert_metric_present "fraud_redis_window_degraded_total"
assert_metric_present "fraud_detection_degraded_total"
assert_metric_present "fraud_rule_skipped_total"

log "Restarting Redis"
docker compose -f "$COMPOSE_FILE" start redis >/dev/null
wait_for_redis_ready

log "Publishing recovery event: ${RECOVERY_EVENT_ID}"
post_transaction_event "$RECOVERY_EVENT_ID" "user-phase8-redis-recovery" "acc-phase8-redis-recovery" "10000"

log "Waiting for non-degraded fraud result after Redis recovery"
RECOVERY_RESULT="$(wait_for_fraud_result_matching "$RECOVERY_EVENT_ID" '"degraded":false')"
assert_contains "$RECOVERY_RESULT" "\"eventId\":\"${RECOVERY_EVENT_ID}\"" "expected recovery fraud result eventId"

log "Redis down drill PASS"
