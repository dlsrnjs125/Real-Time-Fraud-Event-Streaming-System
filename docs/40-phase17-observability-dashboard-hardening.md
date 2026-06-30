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

## Dashboard Panels

- Target Health
- API Requests by Status
- API and Consumer Request Rate
- Redis Window Degraded Total
- Detection Degraded Total
- Rule Skipped Total
- Redis Window Record Latency
- Detection Processing Latency
- DLT Operation Counters
- API p95 Latency

Only existing Actuator/Micrometer metric names are used. Kafka Consumer Lag is not included because no real lag metric was confirmed in the current app code/config.

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
- Consumer Lag should not be shown through a fake panel. Add a panel only after Kafka client lag metric or Kafka exporter metric is actually exposed.
- Metric tags must avoid high-cardinality and sensitive values such as `eventId`, `traceId`, `userId`, `operatorId`, `reason`, account identifiers, device identifiers, and raw payload.

## Excluded

- Blog image files
- Grafana managed alerts
- Alertmanager or notification routing
- Production monitoring hardening
- Kafka Consumer Lag exporter integration
