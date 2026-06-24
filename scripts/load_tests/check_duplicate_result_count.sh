#!/usr/bin/env bash
set -euo pipefail

EVENT_ID="${1:?eventId is required}"
EXPECTED="${EXPECTED:-1}"

case "${EVENT_ID}" in
  *[!a-zA-Z0-9._:-]*)
    echo "invalid eventId: ${EVENT_ID}" >&2
    exit 1
    ;;
esac

COUNT="$(docker exec fraud-postgres psql -U fraud -d fraud -tAc \
  "select count(*) from fraud_detection_results where event_id='${EVENT_ID}'")"

if [ "${COUNT}" != "${EXPECTED}" ]; then
  echo "Expected fraud result count ${EXPECTED}, got ${COUNT}" >&2
  exit 1
fi

echo "Duplicate replay consistency PASS: eventId=${EVENT_ID}, count=${COUNT}"
