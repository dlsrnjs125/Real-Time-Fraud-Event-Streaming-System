#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP_SERVER="${BOOTSTRAP_SERVER:-localhost:9092}"
PARTITIONS="${PARTITIONS:-6}"
REPLICATION_FACTOR="${REPLICATION_FACTOR:-1}"

create_topic() {
  local topic="$1"
  local retention_ms="$2"

  docker exec fraud-kafka /opt/kafka/bin/kafka-topics.sh \
    --bootstrap-server "${BOOTSTRAP_SERVER}" \
    --create \
    --if-not-exists \
    --topic "${topic}" \
    --partitions "${PARTITIONS}" \
    --replication-factor "${REPLICATION_FACTOR}" \
    --config "retention.ms=${retention_ms}" \
    --config "cleanup.policy=delete"
}

create_topic "transaction-events" "259200000"
create_topic "fraud-risk-events" "604800000"
create_topic "fraud-alert-events" "604800000"
create_topic "transaction-events.retry" "86400000"
create_topic "transaction-events.dlt" "1209600000"
create_topic "transaction-events-dlt" "1209600000"

docker exec fraud-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server "${BOOTSTRAP_SERVER}" \
  --list
