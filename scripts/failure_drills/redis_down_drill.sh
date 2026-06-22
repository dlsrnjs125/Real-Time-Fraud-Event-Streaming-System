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
REDIS_DEGRADED_BEFORE="0"
DETECTION_DEGRADED_BEFORE="0"
RULE_SKIPPED_BEFORE="0"
REDIS_LATENCY_COUNT_BEFORE="0"

log "Redis down drill started"
log "Checking app-api and app-consumer endpoints"
require_http_ok "${API_BASE_URL}/actuator/health"
require_http_ok "${CONSUMER_HEALTH_URL}"

REDIS_DEGRADED_BEFORE="$(metric_value "fraud_redis_window_degraded_total")"
DETECTION_DEGRADED_BEFORE="$(metric_value "fraud_detection_degraded_total")"
RULE_SKIPPED_BEFORE="$(metric_value "fraud_rule_skipped_total")"
REDIS_LATENCY_COUNT_BEFORE="$(metric_value "fraud_redis_window_record_latency_seconds_count")"

log "Stopping Redis"
docker compose -f "$COMPOSE_FILE" stop redis >/dev/null

log "Publishing degraded-mode event: ${DEGRADED_EVENT_ID}"
post_transaction_event "$DEGRADED_EVENT_ID" "user-phase8-redis-down" "acc-phase8-redis-down" "10000"

log "Waiting for degraded fraud result"
RESULT="$(wait_for_fraud_result_matching "$DEGRADED_EVENT_ID" '"degraded":true')"
assert_contains "$RESULT" "RAPID_TRANSACTION_COUNT" "expected RAPID_TRANSACTION_COUNT in skippedRules"
assert_contains "$RESULT" "WINDOW_AMOUNT_SUM" "expected WINDOW_AMOUNT_SUM in skippedRules"

log "Checking degraded metrics"
assert_metric_increased \
  "fraud_redis_window_degraded_total" \
  "$REDIS_DEGRADED_BEFORE" \
  "$(metric_value "fraud_redis_window_degraded_total")"
assert_metric_increased \
  "fraud_detection_degraded_total" \
  "$DETECTION_DEGRADED_BEFORE" \
  "$(metric_value "fraud_detection_degraded_total")"
assert_metric_increased \
  "fraud_rule_skipped_total" \
  "$RULE_SKIPPED_BEFORE" \
  "$(metric_value "fraud_rule_skipped_total")"
assert_metric_increased \
  "fraud_redis_window_record_latency_seconds_count" \
  "$REDIS_LATENCY_COUNT_BEFORE" \
  "$(metric_value "fraud_redis_window_record_latency_seconds_count")"

log "Restarting Redis"
docker compose -f "$COMPOSE_FILE" start redis >/dev/null
wait_for_redis_ready

log "Publishing recovery event: ${RECOVERY_EVENT_ID}"
post_transaction_event "$RECOVERY_EVENT_ID" "user-phase8-redis-recovery" "acc-phase8-redis-recovery" "10000"

log "Waiting for non-degraded fraud result after Redis recovery"
RECOVERY_RESULT="$(wait_for_fraud_result_matching "$RECOVERY_EVENT_ID" '"degraded":false')"
assert_contains "$RECOVERY_RESULT" "\"eventId\":\"${RECOVERY_EVENT_ID}\"" "expected recovery fraud result eventId"

log "Redis down drill PASS"
