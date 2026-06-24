# Phase 12. k6 부하 테스트로 이상거래 탐지 시스템의 한계를 측정하기

## 1. 이번 Phase에서 풀려는 문제

Phase 12의 목적은 기능을 더 추가하는 것이 아니라, 이미 구현한 이벤트 접수와 Consumer 처리 흐름이 부하와 장애 조건에서 어떻게 흔들리는지 설명 가능한 evidence를 남기는 것이다.

API가 202를 빠르게 반환해도 Consumer가 밀리거나 Redis 장애 중 degraded 결과가 저장되지 않으면 이 시스템의 핵심 목표를 만족했다고 보기 어렵다.

## 2. 기능 검증과 부하 검증은 왜 다른가

기능 테스트는 특정 입력에 대해 예상 응답과 저장 결과가 맞는지 확인한다. 부하 테스트는 같은 기능이 반복 호출될 때 latency, error rate, degraded metric, duplicate 방어가 어떤 패턴으로 변하는지 본다.

이 프로젝트에서는 API latency만 보지 않고 Kafka, Consumer, Redis, PostgreSQL 결과를 함께 해석해야 한다.

## 3. Normal Load 시나리오

Normal Load는 일반적인 이벤트 유입 상황을 재현한다. 각 요청은 unique `eventId`를 사용하며, `POST /api/v1/transactions/events`가 안정적으로 이벤트를 접수하는지 확인한다.

결과는 `docs/22-load-test-results.md`에 p50/p95/p99, request rate, error rate, status code distribution으로 기록한다.

## 4. Peak Load 시나리오

Peak Load는 순간 유입량이 증가하는 상황을 재현한다. 일부 latency 상승은 자연스러운 결과일 수 있지만, 5xx가 발생하면 Kafka publish, DB receipt 저장, JVM resource, Docker resource limit 중 어느 지점이 병목 후보인지 기록한다.

## 5. Duplicate Replay 시나리오

Duplicate Replay는 같은 `eventId`를 반복해서 보낸다. 이 시나리오는 의도적으로 중복을 만들기 때문에 409 응답을 단순 실패로 해석하지 않는다.

최종 기준은 `fraud_detection_results.event_id` unique constraint가 중복 FraudResult 생성을 막고, fraud result count가 1건으로 유지되는지 여부다.

## 6. Redis Down Load 시나리오

Redis Down Load는 Redis container를 중지한 상태에서 이벤트를 유입한다. 기대 동작은 전체 처리가 조용히 성공한 척하는 것이 아니라, Redis 의존 rule을 skipped 처리하고 `degraded=true` 결과와 metric을 남기는 것이다.

실행 script는 종료 시 Redis start를 시도하지만, 테스트 후 Redis container 상태를 직접 확인해야 한다.

## 7. p50, p95, p99를 어떻게 해석했는가

p50은 일반적인 요청 경험을, p95와 p99는 tail latency를 본다. 금융 이벤트 탐지 시스템에서는 평균보다 tail latency가 더 중요할 수 있다. 짧은 지연이 누적되면 Consumer Lag과 detection latency로 이어질 수 있기 때문이다.

실제 수치는 측정 전까지 쓰지 않는다. 측정 결과가 생기면 local machine, duration, request count, Docker resource 조건과 함께 기록한다.

## 8. metric과 API 결과를 함께 본 이유

k6는 API 요청 관점의 결과를 잘 보여준다. 하지만 Redis down 중 Consumer가 degraded mode를 기록했는지, duplicate result가 저장되지 않았는지는 Prometheus metric과 PostgreSQL 조회가 필요하다.

따라서 Phase 12 결과는 k6 summary와 Actuator/Prometheus, admin API 또는 DB 조회 결과를 함께 본다.

## 9. 테스트 결과와 병목 후보

테스트 결과는 `docs/22-load-test-results.md`에 기록한다. 아직 측정하지 않은 값은 `TBD`로 둔다.

병목 후보는 API validation, Kafka publish, Consumer rule execution, Redis latency, PostgreSQL insert/unique constraint conflict로 나누어 기록한다.

## 10. 이번 Phase의 한계

Consumer Lag metric과 Grafana dashboard는 아직 후속 Observability Hardening 범위다. 로컬 Docker Compose 결과는 운영 환경의 절대 성능을 의미하지 않는다.

무거운 k6 테스트는 기본 CI에 넣지 않는다. CI는 빠른 build/test/config gate로 유지하고, 부하 테스트는 로컬 evidence 절차로 분리한다.

## 11. 다음 Phase에서 보완할 점

다음 단계에서는 Consumer Lag, DLT pending/reprocess/discard metric, Grafana dashboard, alert rule 후보를 연결한다. 이후 반복 가능한 load/failure evidence를 쌓아 threshold를 조정한다.
