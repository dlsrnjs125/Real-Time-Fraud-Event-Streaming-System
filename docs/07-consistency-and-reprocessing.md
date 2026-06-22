# Consistency and Reprocessing

## 1. Phase 3 Producer Consistency

Phase 3에서는 `app-api`가 transaction event request를 validation한 뒤 `transaction_event_receipts`에 접수 기록을 저장하고 Kafka `transaction-events` topic으로 발행합니다.

정책:

- Kafka key는 `userId`입니다.
- `eventId`는 `transaction_event_receipts.event_id` unique constraint로 중복 접수를 방어합니다.
- 중복 `eventId`는 idempotent replay로 처리하지 않고 `409 CONFLICT`를 반환합니다.
- Kafka publish 성공 시 receipt status는 `PUBLISHED`입니다.
- Kafka publish 실패 시 receipt status는 `PUBLISH_FAILED`로 남기고 API는 `503 SERVICE_UNAVAILABLE`을 반환합니다.
- `PUBLISH_FAILED` 상태의 동일 `eventId` 재요청도 `409 CONFLICT`로 처리합니다.

한계:

- Phase 3에서는 Outbox Pattern을 구현하지 않습니다.
- DB transaction과 Kafka publish를 원자적으로 묶지 않습니다.
- DB receipt 저장 성공 후 Kafka publish 실패 가능성이 있습니다.
- Kafka publish 성공 후 `PUBLISHED` 상태 저장 또는 DB commit 실패 가능성이 있습니다.
- Kafka publish 성공 후 DB commit이 실패하면 Kafka에는 이벤트가 존재하지만 receipt 상태가 반영되지 않을 수 있습니다.
- `PUBLISH_FAILED` receipt를 자동 보정하는 outbox publisher는 향후 hardening 후보입니다.
- Kafka 발행 감사 테이블 또는 Outbox Pattern 기반 보정 작업은 후속 hardening 대상으로 둡니다.
- Consumer offset, retry, DLT, reprocessing은 이후 Phase에서 구현합니다.

## 2. Offset Commit 기준

Consumer는 이벤트를 읽은 직후가 아니라 처리 완료 후 offset을 commit합니다.

Phase 4 처리 완료 기준:

- EventProcessingLog 저장 성공
- 그 이후 acknowledgment 수행

Phase 5 이후 처리 완료 기준:

- FraudResult 저장 성공
- 필요한 후속 이벤트 발행 성공
- EventProcessingLog 저장 성공
- 그 이후 acknowledgment 수행

## 3. 설정 기준

- `enable-auto-commit=false`
- `AckMode.MANUAL_IMMEDIATE`
- `auto-offset-reset=earliest`로 commit 없는 local consumer group의 미처리 이벤트를 재소비
- Phase 4 Consumer group ID는 `fraud-event-consumer`
- Phase 4 중복 processing log 방어 기준은 `(topic, partition_no, offset_no)`
- FraudResult 중복 방어를 위한 `eventId` idempotency는 Phase 5 이후 구현

Phase 4에서는 processing log 저장 성공 후 application code가 acknowledge 호출 시점을 통제합니다. 즉시 commit 의도를 명확히 하기 위해 Spring Kafka container는 `MANUAL_IMMEDIATE` ack mode를 사용합니다.

## 4. Consumer 장애 시 동작

Consumer가 죽어도 Kafka topic에 이벤트는 남아 있습니다. 처리 완료 전에 offset을 commit하지 않았다면 Consumer 재시작 후 마지막 commit 이후 메시지부터 다시 소비할 수 있습니다.

중복 처리를 방어하기 위해 다음 기준을 둡니다.

- `event_processing_logs(topic, partition_no, offset_no)` unique
- `fraud_results.event_id` unique
- 처리 전 `eventId` 처리 여부 확인
- 중복 이벤트는 `duplicate_processed`로 로그 기록 후 skip

Phase 4에서는 같은 offset이 재소비되었고 이미 processing log가 있으면 duplicate log를 새로 만들지 않고 ack 가능합니다. 이 정책은 DB 저장 성공 후 ack 직전에 Consumer가 죽는 경우를 대비한 trade-off입니다. FraudResult가 아직 없으므로 eventId 기준 비즈니스 idempotency는 적용하지 않습니다.

## 5. Phase 9 DLT 저장 기준

DLT 이벤트는 자동으로 무한 재처리하지 않습니다. 운영자 API를 통해 실패 원인을 확인하고, 재처리 가능한 이벤트만 명시적으로 재처리합니다.

Phase 9에서 DLT로 보내는 실패:

- `RULE_ENGINE_ERROR`: Rule Engine 실행 중 예외
- `UNKNOWN_ERROR`: 후속 확장 후보

DLT로 보내지 않는 실패:

- Redis 장애: Phase 6 정책대로 degraded mode로 처리하고 skipped rule을 결과에 기록
- processing log 저장 실패: DB 장애 가능성이 높으므로 ack하지 않고 재소비
- fraud result 저장 실패: 최종 결과 저장소 장애이므로 ack하지 않고 재소비

DB 장애를 DLT로 보내지 않는 이유는 DLT metadata 저장도 같은 PostgreSQL에 의존하기 때문입니다. 이 경우 DLT 저장을 시도해도 실패할 가능성이 높으므로 offset을 commit하지 않고 Kafka 재소비를 유도하는 편이 더 안전합니다.

## 6. DLT Idempotency

`dead_letter_events(source_topic, source_partition, source_offset)` unique constraint가 같은 Kafka record의 DLT 중복 저장을 막습니다. 같은 `eventId`라도 서로 다른 offset에서 여러 실패 이력이 생길 수 있으므로 `event_id` unique constraint는 두지 않습니다.

Consumer는 DLT 저장 중 duplicate constraint가 발생하면 기존 row를 조회해 반환합니다. duplicate DLT path에서도 DLT publish 후 ack 가능하므로 원본 topic에서 무한 재소비되지 않습니다.

## 7. 재처리 원칙

- 재처리 시 원본 `eventId`를 유지합니다.
- 재처리는 `PENDING` 또는 `REPROCESS_FAILED` 상태에서만 허용합니다.
- 재처리 요청은 DB row lock을 잡고 status를 `REPROCESSING`으로 바꾸며 `reprocess_attempts`를 증가시킨 뒤 `transaction-events`로 원본 payload를 재발행합니다.
- Kafka publish 성공 시 `REPROCESSED`, 실패 시 `REPROCESS_FAILED`로 남기고 API는 `503 KAFKA_PUBLISH_FAILED`를 반환합니다.
- `REPROCESSED`와 `DISCARDED`는 종료 상태이며 재처리 요청 시 `409 Conflict`를 반환합니다.
- Consumer는 `fraud_detection_results.event_id` unique constraint와 duplicate fast path로 중복 FraudResult 생성을 막습니다.

## 8. PostgreSQL과 Kafka Publish 사이의 한계

Phase 9는 DB 상태 변경과 Kafka publish를 완전한 atomic transaction으로 묶지 않습니다.

예상 가능한 중간 상태:

- DLT DB 저장 성공 후 DLT topic publish 실패: Consumer 예외가 전파되어 ack하지 않으므로 원본 record가 재소비될 수 있습니다.
- reprocess status가 `REPROCESSING`으로 변경된 뒤 Kafka publish 실패: `REPROCESS_FAILED`로 상태를 남기고 `503`으로 호출자에게 실패를 알립니다.

동시성 기준:

- 운영자 재처리/폐기는 상태 전이가 핵심이므로 같은 DLT row에 대한 상태 변경은 `PESSIMISTIC_WRITE` row lock으로 직렬화합니다.
- 최종 FraudResult 중복 방어는 여전히 `fraud_detection_results.event_id` unique constraint가 담당합니다.
- Kafka publish 성공 후 DB 상태 변경 실패: 이번 Phase에서는 별도 outbox나 reconciliation job으로 보정하지 않습니다.

이 한계는 문서화된 trade-off입니다. 대량 batch reprocess, rate limit, outbox 기반 보정은 후속 Phase 후보입니다.

## 9. Admin API 범위

초기 구현에서는 admin API를 local-only로 둡니다. 운영 환경 가정에서는 DLT 조회, 재처리, 폐기 작업 모두 관리자 권한, 감사 로그, rate limit, payload 접근 통제가 필요합니다.
