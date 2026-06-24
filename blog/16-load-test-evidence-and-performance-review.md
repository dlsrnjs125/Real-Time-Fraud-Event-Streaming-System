# Phase 13. k6 부하 테스트로 이상거래 탐지 시스템의 한계를 측정하기

## 1. 이번 Phase에서 풀려는 문제

Phase 13의 목표는 기능을 새로 추가하는 것이 아니라, 이미 구현한 거래 이벤트 접수와 Consumer 처리 흐름이 부하와 장애 조건에서 어떤 지표로 설명되는지 정리하는 것입니다.

API가 빠르게 응답해도 Consumer Lag이 쌓이거나 Redis degraded result가 증가하면 실시간 이상거래 탐지 관점에서는 정상이라고 보기 어렵습니다.

## 2. 기능 검증과 부하 검증은 왜 다른가

기능 테스트는 특정 요청이 성공하는지 확인합니다. 부하 검증은 같은 흐름이 반복될 때 latency, error rate, degraded metric, duplicate 방어 결과가 어떻게 변하는지 확인합니다.

Phase 13에서는 k6 결과만 보지 않고 Prometheus/Actuator metric과 PostgreSQL consistency 결과를 함께 봅니다.

## 3. Smoke 시나리오

Smoke는 k6 실행 가능 여부와 API 이벤트 접수 경로를 1~3회 요청으로 빠르게 확인합니다. Smoke는 부하 측정이 아니라 연결 확인입니다.

`make k6-smoke`는 dedicated `load-test/k6/scenarios/smoke.js`를 실행합니다. Normal load script를 CLI option으로 덮어쓰지 않습니다.

## 4. Normal Load 시나리오

Normal Load는 일반적인 이벤트 유입 상황에서 API가 안정적으로 이벤트를 수신하는지 확인합니다.

주요 관측 지표는 request rate, `http_req_failed`, p50/p95/p99 latency, status code distribution입니다.

## 5. Peak Load 시나리오

Peak Load는 순간 유입 증가 시 API latency와 error rate가 어떻게 변하는지 확인합니다.

5xx가 발생하면 단순 실패로 숨기지 않고 Kafka publish, DB receipt 저장, Docker resource, JVM warmup 같은 병목 후보를 함께 기록합니다.

## 6. Duplicate Replay 시나리오

Duplicate Replay는 같은 `eventId`를 반복 유입시켜도 `fraud_detection_results`가 중복 저장되지 않는지 확인합니다.

API가 `409 CONFLICT`를 반환하는 것은 이 시나리오에서 의도된 결과일 수 있습니다. 최종 기준은 PostgreSQL fraud result count가 1건으로 유지되는지입니다.

## 7. Redis Down Load 시나리오

Redis Down Load는 Redis가 내려간 상태에서도 Consumer가 완전히 멈추지 않고 degraded mode로 처리하는지 확인합니다.

Redis stop/start는 k6 script가 아니라 shell script에서 처리합니다. 테스트 실패 후에도 Redis가 복구되도록 `trap`과 readiness check를 둡니다.

## 8. p50, p95, p99를 어떻게 해석했는가

p50은 일반적인 응답 흐름을, p95와 p99는 tail latency를 설명합니다.

로컬 Docker Compose 결과는 운영 capacity를 대표하지 않습니다. 따라서 Phase 13에서는 절대 성능 수치가 아니라 병목 후보와 측정 절차를 설명하는 evidence로 사용합니다.

## 9. metric과 API 결과를 함께 본 이유

API 결과는 접수 관점의 신호입니다. Consumer와 Redis metric은 비동기 탐지와 degraded mode의 신호입니다.

Redis down load에서는 `fraud_redis_window_degraded_total`, `fraud_detection_degraded_total`, `fraud_rule_skipped_total`을 함께 확인합니다.

## 10. 테스트 결과와 병목 후보

실제 측정 결과는 `docs/23-load-test-results.md`에 기록합니다.

병목 후보:

- API validation과 Kafka publish latency
- Kafka broker resource와 partition hot spot
- Consumer rule execution과 DB 저장 지연
- Redis sliding window command latency
- PostgreSQL unique constraint conflict와 connection pool

## 11. 이번 Phase의 한계

Consumer Lag metric과 Grafana dashboard screenshot evidence는 후속 Observability hardening 범위로 남겼습니다.

무거운 k6 부하 테스트는 기본 CI gate에 넣지 않았습니다. 부하 테스트는 로컬 Docker Compose 환경에서 명시적으로 실행합니다.

## 12. 다음 Phase에서 보완할 점

- Consumer Lag metric 연결
- detection latency dashboard 구성
- DLT pending/reprocess/discard metric 추가
- Grafana screenshot evidence 정리
- 반복 가능한 scheduled load test 또는 수동 evidence capture 절차 정리
