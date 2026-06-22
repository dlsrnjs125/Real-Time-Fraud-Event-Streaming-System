# Phase 8. Redis Down과 Consumer Restart 장애 Drill로 복구 가능성 검증하기

## 1. 이번 Phase에서 풀려는 문제

Phase 7까지는 Redis Sliding Window와 metric foundation을 구현하고 검증했다. Phase 8의 목표는 장애가 실제로 발생했을 때 Consumer가 어떤 증거를 남기며 계속 처리하거나 복구되는지 확인하는 것이다.

이번 Phase는 새로운 fraud rule을 추가하는 단계가 아니라, Redis down, Consumer restart, Kafka unavailable 상황을 재현 가능한 drill과 runbook으로 정리하는 단계다.

## 2. Redis 장애를 전체 실패로 보지 않은 이유

Redis는 최근 거래 패턴 탐지를 위한 단기 상태 저장소다. 최종 정합성 저장소는 PostgreSQL이고, 중복 FraudResult 방어도 `fraud_detection_results.event_id` unique constraint가 담당한다.

따라서 Redis가 내려갔다고 Consumer ack를 막으면 Kafka Lag이 증가하고 Redis와 무관한 이벤트 처리까지 멈출 수 있다. Phase 8에서는 Redis 장애를 degraded mode로 처리하고, Redis 의존 rule은 skipped 처리한 뒤 stateless rule 결과를 저장하는 흐름을 검증했다.

## 3. Redis Down Drill 자동화

`scripts/failure_drills/redis_down_drill.sh`는 다음 순서로 동작한다.

- Redis stop
- synthetic transaction event 발행
- fraud result 조회 retry
- `degraded=true` 확인
- `RAPID_TRANSACTION_COUNT`, `WINDOW_AMOUNT_SUM` skipped rule 확인
- Prometheus degraded/skipped/latency metric 증가 확인
- Redis restart
- recovery event 발행 후 `degraded=false` 확인

이 drill은 Redis 장애가 Consumer 전체 실패로 번지지 않고, 운영자가 API와 metric으로 상태를 확인할 수 있는지 검증한다.

## 4. Consumer Restart Drill에서 확인한 것

Consumer가 중지된 동안에도 app-api는 Kafka에 이벤트를 publish할 수 있다. 이 이벤트는 Consumer가 재시작될 때 처리되어야 하고, 같은 `eventId`가 중복 FraudResult를 만들면 안 된다.

현재 app-consumer는 Docker Compose service가 아니라 로컬 Gradle process로 실행된다. 그래서 `consumer_restart_drill.sh`는 app-consumer를 먼저 중지하라는 precondition을 확인하고, 이벤트 발행 후 사용자가 `make consumer`로 재시작하면 fraud result와 processing log를 확인한다.

## 5. Kafka 장애는 왜 Runbook으로 분리했는가

Kafka broker stop/start는 producer timeout, topic metadata, Consumer reconnect, 다른 로컬 workflow에 영향을 준다. 자동 script를 기본 failure drill에 포함하면 테스트보다 환경 파괴 위험이 커질 수 있다.

그래서 Phase 8에서는 `scripts/failure_drills/kafka_unavailable_drill.md`로 분리했다. 이 runbook은 Kafka stop 중 API가 성공 응답을 반환하지 않는지, Kafka 복구 후 topic 조회와 Consumer reconnect가 가능한지 확인한다.

## 6. PASS/FAIL 기준을 어떻게 정의했는가

Redis down drill의 PASS 기준은 단순히 script가 끝나는 것이 아니다.

- degraded fraud result가 저장된다.
- Redis 의존 rule이 skippedRules에 남는다.
- degraded/skipped/latency metric이 증가한다.
- Redis 복구 후 신규 이벤트가 정상 non-degraded 결과로 처리된다.

Consumer restart drill의 PASS 기준은 재시작 후 fraud result와 processing log가 조회되고, duplicate result가 생성되지 않는 것이다.

## 7. metric, log, API 조회를 함께 본 이유

Metric은 추세와 alert에 좋지만 단일 이벤트의 처리 결과를 증명하지는 못한다. API 조회는 특정 eventId의 결과를 확인할 수 있지만 장애 추세를 보여주지 못한다. 로그는 traceId/eventId/topic/partition/offset을 따라갈 수 있지만 운영 대시보드처럼 집계되지는 않는다.

Phase 8 drill은 이 세 가지를 함께 본다. 장애 대응에서 필요한 것은 하나의 신호가 아니라 서로 보완되는 evidence다.

## 8. 테스트와 검증 결과

실행 대상:

```bash
make ci-check
make redis-integration-test
make failure-drill-redis
bash -n scripts/failure_drills/*.sh
```

검증 기준:

- `make ci-check` 통과
- `make redis-integration-test` 통과
- shell syntax check 통과
- Redis integration test 통과
- Redis down drill에서 degraded result와 metric 증가 확인
- Consumer restart drill은 DB row count 1건 검증을 포함
- Consumer restart drill 절차 문서화
- Kafka unavailable runbook 작성

## 9. 이번 Phase의 한계

Retry/DLT 자동 복구는 아직 구현하지 않았다. Kafka unavailable drill은 자동화하지 않고 runbook으로 남겼다. Consumer restart drill도 app-consumer가 로컬 Gradle process인 현재 구조 때문에 완전 자동화가 아니다.

Grafana dashboard, alert rule, k6 기반 장애 부하 테스트도 후속 Phase 범위다.

## 10. 다음 Phase에서 보완할 점

다음 단계에서는 Retry/DLT와 DLQ 저장/조회/재처리 흐름을 구현해 transient failure와 unrecoverable failure를 분리한다. 이후 Observability 단계에서 Consumer Lag, detection latency, DLQ count, Redis degraded count를 dashboard와 alert로 연결하고, Load/Failure Test 단계에서 k6 기반 장애 부하 검증을 수행한다.
