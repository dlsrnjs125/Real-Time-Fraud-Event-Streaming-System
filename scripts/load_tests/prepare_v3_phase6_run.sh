#!/usr/bin/env bash
set -euo pipefail

KAFKA_CONTAINER="${KAFKA_CONTAINER:-fraud-kafka}"
KAFKA_BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP_SERVER:-localhost:9092}"
KAFKA_CONSUMER_GROUP="${KAFKA_CONSUMER_GROUP:-fraud-event-consumer}"
REDIS_CONTAINER="${REDIS_CONTAINER:-fraud-redis}"
REDIS_DB="${REDIS_DB:-0}"

echo "Preparing V3 Phase 6 run isolation"
echo "Kafka consumer group: ${KAFKA_CONSUMER_GROUP}"
echo "Redis container/db: ${REDIS_CONTAINER}/${REDIS_DB}"

DESCRIBE_OUTPUT="$(
  docker exec "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server "${KAFKA_BOOTSTRAP_SERVER}" \
    --describe \
    --group "${KAFKA_CONSUMER_GROUP}" 2>&1 || true
)"

if printf '%s\n' "${DESCRIBE_OUTPUT}" | grep -qi "does not exist"; then
  echo "Kafka consumer group does not exist yet; skipping pre-run lag gate."
else
  TOTAL_LAG="$(
    printf '%s\n' "${DESCRIBE_OUTPUT}" | awk '
      NR > 1 && $1 != "" && $6 ~ /^[0-9]+$/ { lag += $6 }
      END { print lag + 0 }
    '
  )"
  if [ "${TOTAL_LAG}" != "0" ]; then
    echo "V3 Phase 6 preflight failed: Consumer Lag must be 0 before an accepted run, got ${TOTAL_LAG}" >&2
    printf '%s\n' "${DESCRIBE_OUTPUT}" >&2
    exit 1
  fi
  echo "Kafka pre-run Consumer Lag is 0."
fi

docker exec "${REDIS_CONTAINER}" redis-cli -n "${REDIS_DB}" ping >/dev/null
docker exec "${REDIS_CONTAINER}" redis-cli -n "${REDIS_DB}" FLUSHDB >/dev/null

echo "Redis DB ${REDIS_DB} flushed for clean Phase 6 source-delay run state."
