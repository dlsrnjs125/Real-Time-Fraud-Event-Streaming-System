# Phase 9. DLT와 수동 재처리로 실패 이벤트를 운영 가능하게 만들기

## 1. 이번 Phase에서 풀려는 문제

Consumer는 Kafka 이벤트를 처리하는 동안 Rule Engine 예외, 저장소 장애, 예상하지 못한 예외를 만날 수 있습니다. 모든 실패를 무한 재시도하면 Consumer Lag이 계속 증가하고, 어떤 이벤트가 왜 막혔는지 운영자가 설명하기 어렵습니다.

Phase 9의 목표는 실패 이벤트를 버리는 것이 아니라 운영자가 조회하고, 판단하고, 복구할 수 있는 상태로 격리하는 것입니다.

## 2. 실패 이벤트를 무한 재시도하지 않고 DLT로 격리한 이유

Rule Engine 예외처럼 같은 이벤트가 계속 같은 지점에서 실패할 가능성이 높은 경우에는 원본 topic에서 계속 재소비해도 처리량만 떨어집니다. 이런 이벤트는 `transaction-events-dlt`로 보내고 `dead_letter_events`에 저장한 뒤 원본 offset을 ack합니다.

이렇게 하면 정상 이벤트 처리는 계속 진행되고, 실패 이벤트는 운영자가 별도로 확인할 수 있습니다.

## 3. 어떤 실패를 DLT로 보내고, 어떤 실패는 no-ack으로 남겼는가

DLT 대상은 Rule Engine 예외와 같은 처리 불가능 이벤트입니다. Redis 장애는 Source of Truth 장애가 아니므로 degraded mode로 처리하고 DLT로 보내지 않습니다.

DB 저장 실패는 DLT가 아니라 no-ack 재소비로 남겼습니다. DLT metadata도 PostgreSQL에 저장하므로 DB 장애 상황에서는 DLT 저장 자체가 실패할 가능성이 높기 때문입니다.

## 4. dead_letter_events 테이블을 분리한 이유

DLT 이벤트는 fraud result나 processing log와 성격이 다릅니다. 원본 Kafka 위치, 실패 구간, 상태 전이, 재처리 횟수, 폐기 사유가 필요합니다.

따라서 `dead_letter_events`를 분리해 운영자가 실패 이벤트만 조회하고 조치할 수 있게 했습니다.

## 5. source topic/partition/offset unique constraint로 중복 DLT를 막은 이유

같은 Kafka record가 여러 번 DLT path에 들어올 수 있습니다. 이때 중복 저장 기준은 `eventId`가 아니라 Kafka의 원천 위치인 `source_topic`, `source_partition`, `source_offset`입니다.

같은 `eventId`라도 서로 다른 offset에서 여러 실패 이력이 생길 수 있으므로 `event_id` unique는 걸지 않았습니다.

## 6. 재처리 API의 상태 전이 설계

허용 전이는 다음과 같습니다.

```text
PENDING -> REPROCESSING -> REPROCESSED
PENDING -> REPROCESSING -> REPROCESS_FAILED
PENDING -> DISCARDED
REPROCESS_FAILED -> REPROCESSING
REPROCESS_FAILED -> DISCARDED
```

`REPROCESSED`와 `DISCARDED`는 종료 상태입니다. 종료 상태에서 재처리하거나 폐기하려 하면 `409 Conflict`로 막습니다.

같은 DLT row를 두 운영자가 동시에 처리하는 상황은 DB row lock으로 직렬화합니다. 재처리 Kafka publish가 실패하면 `REPROCESS_FAILED`로 상태를 남기고 HTTP 503을 반환합니다.

## 7. Kafka publish와 DB 상태 변경 사이의 atomicity 한계

Phase 9는 Kafka publish와 PostgreSQL 상태 변경을 완전한 atomic transaction으로 묶지 않았습니다. DLT 저장 후 DLT publish 실패, 재처리 publish 성공 후 DB 상태 변경 실패 같은 중간 상태가 가능합니다.

이번 Phase에서는 이 한계를 문서화하고, 후속 Phase에서 outbox 또는 reconciliation job으로 보완할 후보로 남겼습니다.

## 8. 테스트와 검증 결과

검증한 항목:

- DLT 저장 idempotency
- duplicate source offset 방어
- Rule Engine 예외 발생 시 DLT 저장/publish 후 ack
- DLT 저장 실패 시 no-ack
- Admin DLT 목록/단건 조회
- PENDING 재처리 성공
- PENDING 폐기 성공
- 종료 상태 재처리 시 409

실행한 명령:

```bash
./gradlew :app-api:test :app-consumer:test
```

## 9. 이번 Phase의 한계

- batch reprocess와 rate limit은 구현하지 않았습니다.
- 관리자 인증/인가와 audit log는 구현하지 않았습니다.
- Kafka publish와 DB update의 atomic transaction은 구현하지 않았습니다.
- DLT payload masking은 운영 확장 시 보강이 필요합니다.
- Grafana dashboard와 k6 부하 테스트는 이번 Phase 범위가 아닙니다.

## 10. 다음 Phase에서 보완할 점

- DLT count와 reprocess failure count metric 추가
- Grafana dashboard와 alert rule 연결
- batch reprocess, rate limit, audit log 추가
- outbox 또는 reconciliation job으로 중간 상태 보정
