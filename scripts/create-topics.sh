#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP_SERVER="${BOOTSTRAP_SERVER:-localhost:9092}"
PARTITIONS="${PARTITIONS:-6}"
REPLICATION_FACTOR="${REPLICATION_FACTOR:-1}"

topics=(
  "transaction-events"
  "fraud-risk-events"
  "fraud-alert-events"
  "transaction-events.retry"
  "transaction-events.dlt"
)

for topic in "${topics[@]}"; do
  docker exec fraud-kafka kafka-topics.sh \
    --bootstrap-server "${BOOTSTRAP_SERVER}" \
    --create \
    --if-not-exists \
    --topic "${topic}" \
    --partitions "${PARTITIONS}" \
    --replication-factor "${REPLICATION_FACTOR}"
done

docker exec fraud-kafka kafka-topics.sh \
  --bootstrap-server "${BOOTSTRAP_SERVER}" \
  --list
