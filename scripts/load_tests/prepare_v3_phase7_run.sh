#!/usr/bin/env bash
set -euo pipefail

# LOCAL EXPERIMENT ONLY.
# This script deletes only the replay Redis namespace and checks live/replay
# Consumer Lag before a V3 Phase 7 isolation run. Do not point it at shared or
# production Redis.

KAFKA_CONTAINER="${KAFKA_CONTAINER:-fraud-kafka}"
KAFKA_BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-localhost:9092}"
LIVE_KAFKA_CONSUMER_GROUP="${LIVE_KAFKA_CONSUMER_GROUP:-fraud-event-consumer}"
REPLAY_KAFKA_CONSUMER_GROUP="${REPLAY_KAFKA_CONSUMER_GROUP:-fraud-event-replay-consumer}"
REDIS_CONTAINER="${REDIS_CONTAINER:-fraud-redis}"
REDIS_DB="${REDIS_DB:-0}"
REPLAY_REDIS_NAMESPACE="${REPLAY_REDIS_NAMESPACE:-replay}"
REPLAY_REDIS_PATTERN="fraud:tx:${REPLAY_REDIS_NAMESPACE}:*"

if ! printf '%s' "${REPLAY_REDIS_NAMESPACE}" | grep -Eq '^[a-z0-9][a-z0-9_-]*$'; then
  echo "Invalid REPLAY_REDIS_NAMESPACE: ${REPLAY_REDIS_NAMESPACE}" >&2
  exit 1
fi
if [ "${REPLAY_REDIS_NAMESPACE}" = "live" ]; then
  echo "REPLAY_REDIS_NAMESPACE must not be live" >&2
  exit 1
fi

check_group_lag() {
  local group="$1"
  local describe_output
  describe_output="$(
    docker exec "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-consumer-groups.sh \
      --bootstrap-server "${KAFKA_BOOTSTRAP_SERVER}" \
      --describe \
      --group "${group}" 2>&1 || true
  )"

  if printf '%s\n' "${describe_output}" | grep -qi "does not exist"; then
    echo "Kafka consumer group ${group} does not exist yet; skipping pre-run lag gate."
    return 0
  fi

  local total_lag
  total_lag="$(
    printf '%s\n' "${describe_output}" | awk '
      NR > 1 && $1 != "" && $6 ~ /^[0-9]+$/ { lag += $6 }
      END { print lag + 0 }
    '
  )"
  if [ "${total_lag}" != "0" ]; then
    echo "V3 Phase 7 preflight failed: Consumer Lag for ${group} must be 0, got ${total_lag}" >&2
    printf '%s\n' "${describe_output}" >&2
    exit 1
  fi
  echo "Kafka pre-run Consumer Lag is 0 for ${group}."
}

echo "Preparing V3 Phase 7 historical replay isolation run"
echo "Scope: LOCAL EXPERIMENT ONLY. Only Redis keys matching ${REPLAY_REDIS_PATTERN} will be deleted."

check_group_lag "${LIVE_KAFKA_CONSUMER_GROUP}"
check_group_lag "${REPLAY_KAFKA_CONSUMER_GROUP}"

docker exec "${REDIS_CONTAINER}" redis-cli -n "${REDIS_DB}" ping >/dev/null
KEYS_DELETED="$(
  docker exec "${REDIS_CONTAINER}" sh -c \
    "redis-cli -n '${REDIS_DB}' --scan --pattern '${REPLAY_REDIS_PATTERN}' | xargs -r redis-cli -n '${REDIS_DB}' DEL" \
    | awk '{ total += $1 } END { print total + 0 }'
)"

echo "Deleted ${KEYS_DELETED} Redis keys from replay namespace ${REPLAY_REDIS_NAMESPACE}."
