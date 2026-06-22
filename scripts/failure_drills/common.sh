#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080}"
CONSUMER_METRICS_URL="${CONSUMER_METRICS_URL:-http://localhost:8081/actuator/prometheus}"
CONSUMER_HEALTH_URL="${CONSUMER_HEALTH_URL:-http://localhost:8081/actuator/health}"
COMPOSE_FILE="${COMPOSE_FILE:-infra/docker-compose.yml}"
EVENT_POLL_ATTEMPTS="${EVENT_POLL_ATTEMPTS:-30}"
EVENT_POLL_INTERVAL_SECONDS="${EVENT_POLL_INTERVAL_SECONDS:-1}"

log() {
  echo "[Phase 8] $*"
}

fail() {
  echo "[Phase 8] FAIL: $*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "required command not found: $1"
}

require_http_ok() {
  local url="$1"
  curl -fsS "$url" >/dev/null || fail "HTTP endpoint is not ready: $url"
}

event_time_now_utc() {
  date -u +"%Y-%m-%dT%H:%M:%SZ"
}

post_transaction_event() {
  local event_id="$1"
  local user_id="$2"
  local account_id="$3"
  local amount="$4"
  local location="${5:-SEOUL}"
  local event_time
  local status

  event_time="$(event_time_now_utc)"
  status="$(
    curl -sS -o /tmp/phase8-post-response.json -w "%{http_code}" \
      -X POST "${API_BASE_URL}/api/v1/transactions/events" \
      -H "Content-Type: application/json" \
      -d "{
        \"eventId\": \"${event_id}\",
        \"userId\": \"${user_id}\",
        \"accountId\": \"${account_id}\",
        \"amount\": ${amount},
        \"currency\": \"KRW\",
        \"merchantId\": \"merchant-phase8\",
        \"deviceId\": \"device-phase8\",
        \"location\": \"${location}\",
        \"eventType\": \"PAYMENT\",
        \"eventTime\": \"${event_time}\"
      }"
  )"

  if [ "$status" != "202" ]; then
    cat /tmp/phase8-post-response.json >&2 || true
    fail "expected transaction intake HTTP 202, got ${status}"
  fi
}

get_fraud_result() {
  local event_id="$1"
  curl -sS "${API_BASE_URL}/api/v1/admin/events/${event_id}/fraud-result" || true
}

wait_for_fraud_result_matching() {
  local event_id="$1"
  local pattern="$2"
  local result=""

  for _ in $(seq 1 "$EVENT_POLL_ATTEMPTS"); do
    result="$(get_fraud_result "$event_id")"
    if echo "$result" | grep -q "$pattern"; then
      echo "$result"
      return 0
    fi
    sleep "$EVENT_POLL_INTERVAL_SECONDS"
  done

  echo "$result" >&2
  fail "fraud result for ${event_id} did not match pattern: ${pattern}"
}

get_processing_log() {
  local event_id="$1"
  curl -sS "${API_BASE_URL}/api/v1/admin/events/${event_id}/processing-log" || true
}

assert_contains() {
  local value="$1"
  local pattern="$2"
  local message="$3"

  echo "$value" | grep -q "$pattern" || fail "$message"
}

assert_metric_present() {
  local pattern="$1"

  curl -fsS "$CONSUMER_METRICS_URL" | grep -q "$pattern" \
    || fail "metric not found from consumer prometheus endpoint: $pattern"
}

metric_value() {
  local metric="$1"

  curl -fsS "$CONSUMER_METRICS_URL" \
    | awk -v name="$metric" '$1 ~ ("^" name "(\\{|$)") {sum += $2} END {print sum + 0}'
}

assert_metric_increased() {
  local metric="$1"
  local before="$2"
  local after="$3"

  awk -v before="$before" -v after="$after" 'BEGIN { exit !(after > before) }' \
    || fail "expected ${metric} to increase, before=${before}, after=${after}"
}

fraud_result_row_count() {
  local event_id="$1"
  local db_container="${POSTGRES_CONTAINER:-fraud-postgres}"
  local db_user="${POSTGRES_USER:-fraud}"
  local db_name="${POSTGRES_DB:-fraud}"

  case "$event_id" in
    *[!a-zA-Z0-9._:-]*)
      fail "invalid event_id for SQL check: ${event_id}"
      ;;
  esac

  docker exec "$db_container" psql -U "$db_user" -d "$db_name" \
    -tAc "select count(*) from fraud_detection_results where event_id='${event_id}'"
}

wait_for_redis_ready() {
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    if docker exec fraud-redis redis-cli ping >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  fail "Redis did not become ready"
}
