#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "${SCRIPT_DIR}/common.sh"

API_METRICS_URL="${API_METRICS_URL:-${API_BASE_URL}/actuator/prometheus}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-fraud-postgres}"
POSTGRES_DB="${POSTGRES_DB:-fraud}"
POSTGRES_USER="${POSTGRES_USER:-fraud}"
ADMIN_TOKEN="${ADMIN_TOKEN:-local-admin-token}"
DRILL_OPERATOR="${DRILL_OPERATOR:-local-dlt-drill}"
DRILL_REASON="${DRILL_REASON:-local DLT admin drill discard}"
KEEP_DLT_DRILL_EVIDENCE="${KEEP_DLT_DRILL_EVIDENCE:-true}"
DLT_ID=""
DRILL_EVENT_ID=""
RESPONSE_FILE=""

log() {
  echo "[Phase 17] $*"
}

fail() {
  echo "[Phase 17] FAIL: $*" >&2
  exit 1
}

require_command curl
require_command docker

api_metric_value() {
  local metric="$1"
  local label_match="${2:-}"

  curl -fsS "$API_METRICS_URL" \
    | awk -v name="$metric" -v label="$label_match" '
        $1 ~ ("^" name "(\\{|$)") && (label == "" || index($1, label) > 0) {sum += $2}
        END {print sum + 0}
      '
}

sql_escape() {
  printf "%s" "$1" | sed "s/'/''/g"
}

psql_query() {
  local sql="$1"
  docker exec "$POSTGRES_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "$sql"
}

seed_pending_dlt_event() {
  local event_id="$1"
  local trace_id="$2"
  local source_offset="$3"
  local payload_json

  payload_json="$(
    printf '{"schemaVersion":"v1","eventId":"%s","userId":"user-dlt-drill","accountId":"acc-dlt-drill","eventType":"PAYMENT","amount":120000,"currency":"KRW","merchantId":"merchant-dlt-drill","deviceId":"device-dlt-drill","location":"SEOUL","eventTime":"2026-06-22T10:00:00Z","receivedAt":"2026-06-22T10:00:01Z","traceId":"%s"}' \
      "$event_id" \
      "$trace_id"
  )"

  psql_query "
    insert into dead_letter_events (
      event_id,
      trace_id,
      user_id,
      source_topic,
      source_partition,
      source_offset,
      dlt_topic,
      failure_stage,
      error_type,
      error_message,
      payload_json,
      status,
      reprocess_attempts,
      created_at,
      updated_at
    ) values (
      '$(sql_escape "$event_id")',
      '$(sql_escape "$trace_id")',
      'user-dlt-drill',
      'local-admin-drill',
      0,
      ${source_offset},
      'transaction-events-dlt',
      'LOCAL_ADMIN_DRILL',
      'LOCAL_DRILL',
      'synthetic local DLT admin operation drill event',
      '$(sql_escape "$payload_json")',
      'PENDING',
      0,
      now(),
      now()
    )
    returning id
  "
}

discard_dlt_event() {
  local dlt_id="$1"
  local status

  RESPONSE_FILE="$(mktemp "${TMPDIR:-/tmp}/phase17-dlt-discard-response.XXXXXX")"

  status="$(
    curl -sS -o "$RESPONSE_FILE" -w "%{http_code}" \
      -X POST "${API_BASE_URL}/api/v1/admin/dlq-events/${dlt_id}/discard" \
      -H "Content-Type: application/json" \
      -H "X-Admin-Token: ${ADMIN_TOKEN}" \
      -d "{
        \"operatorId\": \"${DRILL_OPERATOR}\",
        \"reason\": \"${DRILL_REASON}\"
      }"
  )"

  if [ "$status" != "200" ]; then
    fail "expected DLT discard HTTP 200, got ${status}. Response saved to ${RESPONSE_FILE}"
  fi

  assert_contains "$(cat "$RESPONSE_FILE")" '"status":"DISCARDED"' "expected DLT discard response status DISCARDED"
}

assert_audit_log() {
  local dlt_id="$1"
  local count
  local metadata

  count="$(
    psql_query "
      select count(*)
      from admin_audit_logs
      where action = 'DLT_DISCARD'
        and result = 'SUCCESS'
        and target_id = '$(sql_escape "$dlt_id")'
        and actor = '$(sql_escape "$DRILL_OPERATOR")'
    "
  )"

  if [ "$count" != "1" ]; then
    fail "expected one successful DLT_DISCARD audit log for DLT id ${dlt_id}, got ${count}"
  fi

  metadata="$(
    psql_query "
      select coalesce(metadata_json, '')
      from admin_audit_logs
      where action = 'DLT_DISCARD'
        and target_id = '$(sql_escape "$dlt_id")'
      order by created_at desc
      limit 1
    "
  )"

  case "$metadata" in
    *payload_json*|*payloadJson*|*accountId*|*deviceId*|*local-admin-token*)
      fail "audit metadata contains sensitive or raw payload fields"
      ;;
  esac
}

assert_dlt_status() {
  local dlt_id="$1"
  local status

  status="$(psql_query "select status from dead_letter_events where id = ${dlt_id}")"
  if [ "$status" != "DISCARDED" ]; then
    fail "expected DLT row ${dlt_id} status DISCARDED, got ${status}"
  fi
}

cleanup_drill_rows() {
  local dlt_id="$1"
  local event_id="$2"

  psql_query "
    delete from admin_audit_logs
    where action = 'DLT_DISCARD'
      and target_id = '$(sql_escape "$dlt_id")'
      and actor = '$(sql_escape "$DRILL_OPERATOR")'
  " >/dev/null

  psql_query "
    delete from dead_letter_events
    where id = ${dlt_id}
      and event_id = '$(sql_escape "$event_id")'
  " >/dev/null
}

cleanup_on_exit() {
  local exit_status=$?

  if [ "$KEEP_DLT_DRILL_EVIDENCE" != "true" ] && [ -n "$DLT_ID" ] && [ -n "$DRILL_EVENT_ID" ]; then
    log "Cleaning up drill DB rows for DLT id ${DLT_ID}"
    cleanup_drill_rows "$DLT_ID" "$DRILL_EVENT_ID" || true
  fi

  if [ "$exit_status" -eq 0 ] && [ -n "$RESPONSE_FILE" ]; then
    rm -f "$RESPONSE_FILE"
  fi
}

trap cleanup_on_exit EXIT

log "DLT admin operation drill started"
log "This drill seeds a synthetic PENDING DLT row, then verifies real Admin discard API, audit log, and operation metric."
log "It does not exercise the Consumer failure-path DLT publish flow."
log "KEEP_DLT_DRILL_EVIDENCE=${KEEP_DLT_DRILL_EVIDENCE}"

require_http_ok "${API_BASE_URL}/actuator/health"
docker exec "$POSTGRES_CONTAINER" pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB" >/dev/null \
  || fail "PostgreSQL container is not ready: ${POSTGRES_CONTAINER}"

DISCARDED_BEFORE="$(api_metric_value "fraud_dlt_discarded_total" 'result="success"')"
EVENT_SUFFIX="$(date +%s)-$$-${RANDOM:-0}"
DRILL_EVENT_ID="dlt-drill-${EVENT_SUFFIX}"
DRILL_TRACE_ID="trace-${DRILL_EVENT_ID}"
SOURCE_OFFSET="$(date +%s)$$"

log "Seeding synthetic PENDING DLT row: ${DRILL_EVENT_ID}"
DLT_ID="$(
  seed_pending_dlt_event "$DRILL_EVENT_ID" "$DRILL_TRACE_ID" "$SOURCE_OFFSET" \
    | awk '/^[[:space:]]*[0-9]+[[:space:]]*$/ {print $1; exit}'
)"
if [ -z "$DLT_ID" ]; then
  fail "failed to seed pending DLT row"
fi

log "Calling Admin discard API for DLT id ${DLT_ID}"
discard_dlt_event "$DLT_ID"

log "Checking DLT row status and audit log"
assert_dlt_status "$DLT_ID"
assert_audit_log "$DLT_ID"

DISCARDED_AFTER="$(api_metric_value "fraud_dlt_discarded_total" 'result="success"')"
assert_metric_increased 'fraud_dlt_discarded_total{result="success"}' "$DISCARDED_BEFORE" "$DISCARDED_AFTER"

log "DLT admin operation drill PASS"
log "Evidence: DLT id ${DLT_ID} discarded, audit log recorded, fraud_dlt_discarded_total increased."
