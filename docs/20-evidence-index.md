# Evidence Index

이 문서는 리뷰어가 Phase별 구현 evidence와 운영 검증 근거를 빠르게 찾기 위한 색인입니다.

## Phase Evidence Summary

| Phase | Evidence | Command / Check | Status |
|---|---|---|---|
| Phase 1 | Local infra scaffold validation | `docker compose config`, app health check | Roadmap/review docs에 검증 절차와 결과 기록 |
| Phase 2 | API contract and event schema | `make test`, OpenAPI smoke test | Roadmap/review docs에 검증 절차와 결과 기록 |
| Phase 3 | Transaction intake and Kafka producer | producer key test, receipt persistence test | Roadmap/review docs에 검증 절차와 결과 기록 |
| Phase 4 | Consumer manual ack and processing log | listener ack tests, manual replay drill | Roadmap/runbook에 검증 절차와 기대 결과 기록 |
| Phase 5 | Rule Engine and FraudResult idempotency | rule tests, duplicate eventId tests | Roadmap/review docs에 검증 절차와 결과 기록 |
| Phase 6 | Redis Sliding Window and degraded mode | Redis store tests, rule skip tests | Roadmap/review docs에 검증 절차와 결과 기록 |
| Phase 7 | Redis integration and metrics foundation | `make redis-integration-test` | Runbook 기준 검증 절차 기록 |
| Phase 8 | Failure drill and Consumer recovery | `make failure-drill-redis`, runbooks | Script/runbook 기준 검증 절차 기록 |
| Phase 9 | DLT store/query/reprocess/discard | Admin API tests, state transition tests | Test/review docs에 검증 절차와 결과 기록 |
| Phase 10 | Final readiness criteria | `make final-check`, docs review | Readiness 문서에 자동 검증 범위와 한계 기록 |
| Phase 11 | Final readiness review and documentation | docs link check, validation commands | 현재 PR에서 실행 결과 기록 |
| Phase 12 | Observability hardening | Prometheus/Grafana provisioning, alert rule, actuator metric check | `docs/08-observability.md` |
| Phase 13 | Load and failure test evidence | `make k6-smoke`, `make k6-normal`, `make k6-peak`, `make k6-duplicate-check`, `make k6-redis-down` | `docs/23-load-test-results.md` |
| Phase 14 | Operational security and audit evidence | Admin 401 test, DLT audit log test, max attempts test | `docs/14-security-and-privacy.md`, `docs/18-runbook.md` |
| V2 Planning | PaySim preprocessing-first workflow design evidence | documentation review only | `docs/24-kaggle-paysim-data-provenance.md` through `docs/30-v2-visualization.md` |

## CI / Build

| Evidence | Command | File/Link |
|---|---|---|
| Unit/Slice test | `make ci-check` | `.github/workflows/ci.yml`, `Makefile` |
| Redis integration test | `make redis-integration-test` | `docs/18-runbook.md` |
| Script syntax check | `make scripts-check` | `Makefile` |
| Docker Compose config | `make infra-config` | `infra/docker-compose.yml` |

## Consistency

| Evidence | Command | Expected |
|---|---|---|
| Duplicate fraud result prevention | API replay / consumer test | `fraud_detection_results` count = 1 |
| Duplicate processing log prevention | Consumer replay / test | `(topic, partition_no, offset_no)` unique |
| DLT duplicate prevention | source offset replay / test | `dead_letter_events` count = 1 per source offset |
| DLT reprocess idempotency | Admin API reprocess / test | original `eventId` preserved |

## Failure Drill

| Evidence | Command | Expected |
|---|---|---|
| Redis down drill | `make failure-drill-redis` | `degraded=true`, skipped rules present |
| Consumer restart drill | `make failure-drill-consumer` | result eventually stored |
| Kafka unavailable drill | manual runbook | non-2xx publish failure |
| Event consistency check | `scripts/failure_drills/check_event_consistency.sh <eventId>` | fraud result and processing log both exist |

## Observability

| Evidence | Command | Expected |
|---|---|---|
| Redis degraded metric | `curl http://localhost:8081/actuator/prometheus` | `fraud_redis_window_degraded_total` |
| Skipped rule metric | `curl http://localhost:8081/actuator/prometheus` | `fraud_rule_skipped_total` |
| Detection degraded metric | `curl http://localhost:8081/actuator/prometheus` | `fraud_detection_degraded_total` |
| Redis latency timer | `curl http://localhost:8081/actuator/prometheus` | `fraud_redis_window_record_latency_seconds_*` |

## Load Test Evidence

| Evidence | Command | File/Link |
|---|---|---|
| k6 scenario definitions | `find load-test/k6 -maxdepth 3 -type f` | `load-test/k6/README.md` |
| Normal load result | `make k6-normal` | `docs/22-load-test-results.md` |
| Peak load result | `make k6-peak` | `docs/22-load-test-results.md` |
| Duplicate replay consistency | `make k6-duplicate` | `docs/22-load-test-results.md` |
| Redis down degraded mode | `make k6-redis-down` | `docs/22-load-test-results.md` |
| Phase 13 load/failure evidence | `make k6-smoke`, `make k6-normal`, `make k6-peak`, `make k6-duplicate-check`, `make k6-redis-down` | `docs/23-load-test-results.md` |

## Documentation

| Evidence | File/Link | Purpose |
|---|---|---|
| Final readiness checklist | `docs/19-final-readiness-checklist.md` | 완료 항목과 follow-up 분리 |
| Phase 10 readiness | `docs/19-phase-10-final-readiness.md` | DLT 재처리 이후 운영 완료 기준 |
| Troubleshooting index | `docs/21-troubleshooting-index.md` | 긴 troubleshooting log의 빠른 탐색 |
| Runbook | `docs/18-runbook.md` | 장애 대응 절차 |
| Security/privacy | `docs/14-security-and-privacy.md` | 민감정보와 운영 보안 한계 |
| V2 PaySim provenance | `docs/24-kaggle-paysim-data-provenance.md` | synthetic dataset 출처와 raw data 미커밋 정책 |
| V2 result evidence plan | `docs/29-v2-result-evidence.md` | V2 구현 후 기록할 측정/평가 기준 |
| V2 visualization plan | `docs/30-v2-visualization.md` | V2 구현 후 생성할 chart/table 기준 |
