#!/usr/bin/env bash
set -euo pipefail

COMPOSE_FILE="${COMPOSE_FILE:-infra/docker-compose.yml}"
K6_SCRIPT="${K6_SCRIPT:-load-test/k6/scenarios/redis-down-load.js}"
CONSUMER_METRICS_URL="${CONSUMER_METRICS_URL:-http://localhost:8081/actuator/prometheus}"

wait_for_redis() {
  for _ in 1 2 3 4 5; do
    if docker exec fraud-redis redis-cli ping >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done

  echo "Redis did not become ready after restart" >&2
  return 1
}

metric_value() {
  local metric="$1"
  local body

  if ! body="$(curl -fsS "${CONSUMER_METRICS_URL}" 2>/dev/null)"; then
    echo "metrics endpoint unavailable" >&2
    return 1
  fi

  awk -v name="${metric}" '$1 ~ ("^" name "(\\{|$)") { sum += $2 } END { print sum + 0 }' <<<"${body}"
}

assert_metric_increased() {
  local metric="$1"
  local before="$2"
  local after="$3"

  awk -v before="${before}" -v after="${after}" 'BEGIN { exit !(after > before) }' || {
    echo "Expected ${metric} to increase. before=${before}, after=${after}" >&2
    exit 1
  }
}

wait_for_metric_increase() {
  local metric="$1"
  local before="$2"
  local value

  for _ in 1 2 3 4 5 6 7 8 9 10; do
    value="$(metric_value "${metric}")"
    if awk -v before="${before}" -v after="${value}" 'BEGIN { exit !(after > before) }'; then
      echo "${value}"
      return 0
    fi
    sleep 1
  done

  echo "${value}"
  return 0
}

cleanup() {
  docker compose -f "${COMPOSE_FILE}" start redis >/dev/null 2>&1 || true
  wait_for_redis || true
}

trap cleanup EXIT

BEFORE_REDIS_DEGRADED="$(metric_value fraud_redis_window_degraded_total)"
BEFORE_DETECTION_DEGRADED="$(metric_value fraud_detection_degraded_total)"
BEFORE_RULE_SKIPPED="$(metric_value fraud_rule_skipped_total)"

docker compose -f "${COMPOSE_FILE}" stop redis
k6 run "${K6_SCRIPT}"

AFTER_REDIS_DEGRADED="$(wait_for_metric_increase fraud_redis_window_degraded_total "${BEFORE_REDIS_DEGRADED}")"
AFTER_DETECTION_DEGRADED="$(wait_for_metric_increase fraud_detection_degraded_total "${BEFORE_DETECTION_DEGRADED}")"
AFTER_RULE_SKIPPED="$(metric_value fraud_rule_skipped_total)"

assert_metric_increased "fraud_redis_window_degraded_total" "${BEFORE_REDIS_DEGRADED}" "${AFTER_REDIS_DEGRADED}"
assert_metric_increased "fraud_detection_degraded_total" "${BEFORE_DETECTION_DEGRADED}" "${AFTER_DETECTION_DEGRADED}"

echo "fraud_redis_window_degraded_total before=${BEFORE_REDIS_DEGRADED}, after=${AFTER_REDIS_DEGRADED}"
echo "fraud_detection_degraded_total before=${BEFORE_DETECTION_DEGRADED}, after=${AFTER_DETECTION_DEGRADED}"
echo "fraud_rule_skipped_total before=${BEFORE_RULE_SKIPPED}, after=${AFTER_RULE_SKIPPED}"
