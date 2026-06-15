#!/usr/bin/env bash
set -euo pipefail

BOOTSTRAP_SERVER="${BOOTSTRAP_SERVER:-localhost:9092}"

until docker exec fraud-kafka kafka-topics.sh --bootstrap-server "${BOOTSTRAP_SERVER}" --list >/dev/null 2>&1; do
  echo "waiting for kafka..."
  sleep 2
done

echo "kafka is ready"
