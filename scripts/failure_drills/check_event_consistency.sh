#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

require_command curl
require_command docker

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <eventId>" >&2
  exit 1
fi

EVENT_ID="$1"

log "Checking event consistency: ${EVENT_ID}"

RESULT="$(get_fraud_result "$EVENT_ID")"
assert_contains "$RESULT" "\"eventId\":\"${EVENT_ID}\"" "fraud result not found for ${EVENT_ID}"

PROCESSING_LOG="$(get_processing_log "$EVENT_ID")"
assert_contains "$PROCESSING_LOG" '"logs":\[' "processing log response missing logs array"

ROW_COUNT="$(fraud_result_row_count "$EVENT_ID")"
if [ "$ROW_COUNT" != "1" ]; then
  fail "expected exactly one fraud_detection_results row for ${EVENT_ID}, got ${ROW_COUNT}"
fi

echo "Fraud result:"
echo "$RESULT"
echo
echo "Processing log:"
echo "$PROCESSING_LOG"
echo
echo "fraud_detection_results row count: $ROW_COUNT"
echo
log "Event consistency check PASS"
