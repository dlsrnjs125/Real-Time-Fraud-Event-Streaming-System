# Phase 17 Observability Dashboard Hardening

## Problem

Prometheus scrape configuration and Actuator Prometheus endpoints already existed, but Grafana opened with an empty state because datasource provisioning, dashboard provider configuration, and dashboard JSON were not tracked.

Phase 17 makes local observability evidence reproducible before adding blog screenshots. It does not claim production monitoring completeness.

## Implemented Scope

- Grafana Prometheus datasource provisioning: `infra/grafana/provisioning/datasources/prometheus.yml`
- Grafana dashboard provider: `infra/grafana/provisioning/dashboards/dashboard-provider.yml`
- Dashboard JSON: `infra/grafana/dashboards/fraud-observability.json`
- Prometheus local alert rule candidates: `infra/prometheus/rules/fraud-alerts.yml`
- Prometheus `rule_files` configuration and Docker Compose rule mount
- Consumer processing latency metric: `fraud.detection.processing.latency`
- DLT operation counters: `fraud.dlt.published.total`, `fraud.dlt.reprocess.requested.total`, `fraud.dlt.discarded.total`
- `make observability-check`
- HTTP request histogram bucket configuration for `app-api` and `app-consumer`
- DLT admin operation drill: `make failure-drill-dlt` and alias `make dlt-drill`

## Dashboard Panels

- Target Health
- API Requests by Status
- HTTP Request Rate by Job
- Redis Window Degraded Total
- Detection Degraded Total
- Rule Skipped Total
- Redis Window Record Latency
- Consumer Processing Latency for New Results
- DLT Operation Counters
- API p95 Latency

Only existing Actuator/Micrometer metric names are used. API status, HTTP request rate, and API p95 panels exclude `/actuator.*` traffic when the `uri` label is present so scrape/health traffic does not dominate business request evidence.

API p95 uses `http_server_requests_seconds_bucket`, enabled through `management.metrics.distribution.percentiles-histogram.http.server.requests=true`. The panel is kept because it becomes useful once histogram buckets are emitted.

DLT Operation Counters can show No data until a DLT publish/reprocess/discard operation actually occurs. `fraud.dlt.published.total` is a DLT envelope publish success counter, not a unique DLT backlog count.

Kafka Consumer Lag is not included because no real lag metric was confirmed in the current app code/config.

## DLT Admin Operation Drill

`make failure-drill-dlt` runs `scripts/failure_drills/dlt_admin_drill.sh`.

This drill is not meant to fill an empty Grafana panel with fake values. It verifies the real Admin DLT operation path for an event that is already isolated in DLT:

1. Check `app-api` health and PostgreSQL readiness.
2. Seed a synthetic `PENDING` row in `dead_letter_events`.
3. Call `POST /api/v1/admin/dlq-events/{id}/discard` with the local `X-Admin-Token`.
4. Verify the row transitions to `DISCARDED`.
5. Verify `admin_audit_logs` contains a successful `DLT_DISCARD` audit record.
6. Verify `fraud_dlt_discarded_total{result="success"}` increases from the app-api Prometheus endpoint.

The drill uses only synthetic identifiers and does not print the admin token or raw DLT payload. It validates Admin operation observability, not the full Consumer poison-message DLT publish path.

DLT publish and Admin operation signals are different:

- `fraud.dlt.published.total`: Consumer successfully published a DLT envelope after a processing failure.
- `fraud.dlt.reprocess.requested.total`: Admin reprocess operation result counter.
- `fraud.dlt.discarded.total`: Admin discard operation result counter.
- DLT backlog/status gauge: not implemented in Phase 17.

Current Phase 17 does not add a Consumer failure-path DLT publish drill because the available live DLT publish path is tied to rule-engine exceptions. Adding a magic event amount, userId, eventType, or other drill-only condition to production rule logic would make the evidence less honest. A Consumer DLT publish drill can be added later if a safe existing validation failure path or poison-event policy is defined.

## Alert Rules

Prometheus local alert candidates:

- `FraudApiDown`
- `FraudConsumerDown`
- `FraudRedisDegradedIncreased`
- `FraudRuleSkippedIncreased`

Alertmanager, Slack, PagerDuty, and automatic incident response are future work.

## Verification

Automated checks run in this phase:

```bash
make observability-check
make observability-rules-check
./gradlew :app-consumer:test --tests '*FraudConsumerMetricsTest' --tests '*DeadLetterEventServiceTest'
./gradlew :app-api:test --tests '*DeadLetterEventAdminApiTest'
```

Manual local evidence capture remains separate:

```bash
make infra-down
make infra-up
make topics
make api
make consumer
make failure-drill-dlt
make k6-duplicate
make k6-redis-down
```

Then inspect:

- `http://localhost:9090/targets`
- `http://localhost:9090/rules`
- `http://localhost:9090/alerts`
- `http://localhost:3000`

Expected Grafana path: Dashboards -> Fraud Event Streaming -> Fraud Event Streaming Observability.

## Troubleshooting Notes

- Empty Grafana dashboard state can be caused by missing provisioning files even when the Grafana container is healthy.
- Dashboard query names must match Prometheus-exposed snake_case names, not only Java dot names.
- If API p95 shows No data, first verify `http_server_requests_seconds_bucket` from `app-api` with `/actuator/prometheus`.
- If DLT Operation Counters show No data, generate a DLT publish/reprocess/discard operation or use DLT admin audit response evidence instead.
- Use `make failure-drill-dlt` when the goal is to verify Admin discard, audit log, and DLT operation counter evidence.
- Consumer Lag should not be shown through a fake panel. Add a panel only after Kafka client lag metric or Kafka exporter metric is actually exposed.
- Metric tags must avoid high-cardinality and sensitive values such as `eventId`, `traceId`, `userId`, `operatorId`, `reason`, account identifiers, device identifiers, and raw payload.

## Excluded

- Blog image files
- Grafana managed alerts
- Alertmanager or notification routing
- Production monitoring hardening
- Kafka Consumer Lag exporter integration
- Consumer failure-path DLT publish drill
- DLT backlog/status gauge
