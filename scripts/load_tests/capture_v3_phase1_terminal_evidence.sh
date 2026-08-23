#!/usr/bin/env bash
set -euo pipefail

OUT_DIR="${OUT_DIR:-docs/evidence/v3-phase1}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-fraud-postgres}"
POSTGRES_DATABASE="${POSTGRES_DATABASE:-fraud}"
LABEL="${1:?label is required: before or after}"

case "${LABEL}" in
  before|after)
    ;;
  *)
    echo "label must be before or after" >&2
    exit 1
    ;;
esac

mkdir -p "${OUT_DIR}"

ASSIGNMENT_FILE="${OUT_DIR}/03-consumer-assignment-before.txt"
if [ "${LABEL}" = "after" ]; then
  ASSIGNMENT_FILE="${OUT_DIR}/04-consumer-assignment-after.txt"
fi

{
  echo "# V3 Phase 1 Consumer Assignment (${LABEL})"
  echo
  echo "Captured at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
  echo
  docker exec fraud-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
    --bootstrap-server localhost:9092 \
    --describe \
    --group fraud-event-consumer
} > "${ASSIGNMENT_FILE}"

if [ "${LABEL}" = "after" ]; then
  {
    echo "# V3 Phase 1 Final Consistency Check"
    echo
    echo "Captured at: $(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    echo
    echo "## PostgreSQL row counts"
    docker exec "${POSTGRES_CONTAINER}" psql -U fraud -d "${POSTGRES_DATABASE}" -c \
      "select (select count(*) from transaction_event_receipts) receipts, \
              (select count(*) from fraud_detection_results) fraud_results, \
              (select count(*) from event_processing_logs) processing_logs;"
    echo
    echo "## Kafka consumer group lag"
    docker exec fraud-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
      --bootstrap-server localhost:9092 \
      --describe \
      --group fraud-event-consumer
  } > "${OUT_DIR}/06-final-consistency-check.txt"
fi

echo "Captured terminal evidence for ${LABEL} in ${OUT_DIR}"
