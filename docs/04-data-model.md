# Data Model

## 1. PostgreSQL 저장 대상

PostgreSQL은 조회, 감사, 운영 판단의 기준 저장소입니다.

- 거래 이벤트 접수 기록
- 이상거래 탐지 결과
- 탐지 rule 설정
- Kafka 처리 감사 로그
- DLQ 이벤트 상태
- 재처리 이력

## 2. 핵심 테이블 초안

### transaction_event_receipts

- `id`: 접수 기록 ID
- `event_id`: 거래 이벤트 ID
- `schema_version`: 이벤트 스키마 버전
- `user_id`: 사용자 ID
- `account_id`: 계좌 ID
- `event_type`: 거래 유형
- `amount`: 거래 금액. Phase 3에서는 `BigDecimal`과 PostgreSQL `numeric(19,2)`를 사용
- `currency`: 통화
- `merchant_id`: 가맹점 ID
- `device_id`: 기기 ID
- `location`: 거래 위치
- `event_time`: 거래 발생 시각
- `received_at`: API 접수 시각
- `trace_id`: 요청 추적 ID
- `status`: 접수/발행 상태. `RECEIVED`, `PUBLISHED`, `PUBLISH_FAILED`
- `publish_error_message`: Kafka 발행 실패 시 원인 요약. nullable. raw payload는 저장하지 않고 500자 이내로 제한합니다.
- `created_at`: row 생성 시각
- `updated_at`: row 수정 시각

### fraud_detection_results

- `id`: 탐지 결과 ID
- `event_id`: 원본 이벤트 ID, unique. 같은 Kafka event 재소비 시 중복 탐지 결과 생성을 막는 최종 방어선
- `trace_id`: 요청과 Consumer 처리 추적 ID
- `user_id`: 사용자 ID
- `account_id`: 계좌 ID. Phase 5 local admin 조회용으로 저장하며 로그에는 원문을 남기지 않음
- `risk_score`: 위험 점수. 0~100 check constraint
- `risk_level`: `LOW`, `MEDIUM`, `HIGH`
- `decision`: `APPROVE`, `REVIEW`, `BLOCK`
- `matched_rules`: 매칭된 rule 목록. comma-separated text로 저장하고 API에서 배열로 변환
- `skipped_rules`: Redis 장애 등으로 실행하지 못한 rule 목록. Phase 6에서는 Redis 의존 rule skip을 기록
- `degraded`: Redis Sliding Window를 사용할 수 없어 일부 rule을 생략했는지 여부
- `reason`: Rule Engine v1 판단 요약
- `detected_at`: 탐지 완료 시각
- `created_at`: row 생성 시각
- `updated_at`: row 수정 시각

인덱스:

- `idx_fraud_results_user_id_created_at`
- `idx_fraud_results_risk_level_created_at`
- `idx_fraud_results_decision_created_at`
- `idx_fraud_results_trace_id`

Phase 6에서는 `skipped_rules`와 `degraded`를 추가했습니다. `rule_results`처럼 rule별 상세 결과를 구조화해 저장하는 작업은 후속 확장 후보로 둡니다.

### event_processing_logs

- `id`: 처리 로그 ID
- `event_id`: 원본 이벤트 ID
- `trace_id`: event trace ID
- `user_id`: 사용자 ID
- `topic`: Kafka topic
- `partition_no`: Kafka partition
- `offset_no`: Kafka offset
- `consumer_group_id`: 처리한 Consumer group ID
- `status`: 처리 상태
- `error_message`: 실패 사유. raw payload는 저장하지 않음
- `received_at`: API가 이벤트를 접수한 시각
- `processed_at`: Consumer가 processing log 저장을 완료한 시각
- `created_at`: row 생성 시각
- `updated_at`: row 수정 시각

### dead_letter_events

Phase 9에서 Consumer 처리 실패 이벤트를 운영자가 조회/재처리/폐기할 수 있도록 추가한 테이블입니다. runtime migration은 schema owner인 `app-api`에만 둡니다. `app-consumer`는 runtime Flyway를 실행하지 않고, 테스트 검증용 migration만 별도로 가집니다.

- `id`: DLT 이벤트 ID
- `event_id`: 원본 이벤트 ID. 같은 eventId로 여러 실패 이력이 생길 수 있으므로 unique로 두지 않음
- `trace_id`: 원본 이벤트 trace ID
- `user_id`: 원본 이벤트 user ID
- `source_topic`: 원본 Kafka topic
- `source_partition`: 원본 Kafka partition
- `source_offset`: 원본 Kafka offset
- `dlt_topic`: 발행한 DLT topic. Phase 9 기본값은 `transaction-events-dlt`
- `failure_stage`: `RULE_ENGINE_ERROR`, `UNKNOWN_ERROR` 등 실패 구간
- `error_type`: 예외 class 요약
- `error_message`: 예외 메시지 요약. stacktrace 전체는 저장하지 않음
- `payload_json`: 원본 `TransactionEventMessage` JSON. 운영 환경에서는 masking과 보존 기간 정책 필요
- `status`: `PENDING`, `REPROCESSING`, `REPROCESSED`, `DISCARDED`, `REPROCESS_FAILED`
- `reprocess_attempts`: 재처리 시도 횟수
- `last_reprocessed_at`: 마지막 재처리 시각
- `discarded_at`: 폐기 시각
- `discard_reason`: 폐기 사유. 폐기 시 필수
- `created_at`: DLT 저장 시각
- `updated_at`: 상태 변경 시각

제약조건:

- `(source_topic, source_partition, source_offset)` unique
- `status` check constraint
- `reprocess_attempts >= 0`

인덱스:

- `idx_dlt_event_id`
- `idx_dlt_status_created_at`
- `idx_dlt_trace_id`
- `idx_dlt_failure_stage_created_at`

상태 전이:

- `PENDING -> REPROCESSING -> REPROCESSED`
- `PENDING -> REPROCESSING -> REPROCESS_FAILED`
- `PENDING -> DISCARDED`
- `REPROCESS_FAILED -> REPROCESSING`
- `REPROCESS_FAILED -> DISCARDED`

`REPROCESSED`와 `DISCARDED`는 종료 상태입니다. 종료 상태에서 재처리 또는 폐기를 다시 요청하면 `409 Conflict`로 응답합니다.

### reprocessing_history

별도 `reprocessing_history` 테이블은 현재 구현하지 않았습니다. Phase 14부터 DLT reprocess/discard 운영자 조치는 `admin_audit_logs`에 저장합니다. `dead_letter_events`는 현재 상태와 attempts를, `admin_audit_logs`는 누가 어떤 조치를 요청했고 성공/실패했는지를 기록합니다.

### admin_audit_logs

Phase 14에서 운영자 조치 감사 가능성을 위해 추가한 테이블입니다.

- `id`: audit log ID
- `actor`: 운영자 식별자. Phase 14에서는 request body의 self-claimed `operatorId`
- `action`: `DLT_REPROCESS`, `DLT_DISCARD`
- `target_type`: 현재는 `DLT_EVENT`
- `target_id`: DLT event id
- `request_id`: Phase 14에서는 request-id 수집 체계가 없어 null. 추후 gateway/request-id 표준화 시 저장
- `trace_id`: 요청 trace ID
- `result`: `SUCCESS`, `FAILED`
- `reason`: 운영자가 입력한 사유. 최대 500자 기준으로 저장
- `metadata_json`: eventId, 상태, attempts, maxAttempts, 결과 사유 같은 최소 metadata
- `created_at`: audit row 생성 시각

인덱스:

- `idx_admin_audit_actor_created_at`
- `idx_admin_audit_action_created_at`
- `idx_admin_audit_target`
- `idx_admin_audit_trace_id`

저장하지 않는 정보:

- admin token
- DLT `payload_json` 전체
- request body 전체
- accountId, deviceId 같은 민감 식별자
- stacktrace 전체

Phase 14에서는 audit log 저장까지만 구현하고, audit log 검색/필터링 API는 후속 운영 보안 Phase로 분리합니다.

## 3. 중복 방어 기준

`fraud_detection_results.event_id`에 unique constraint를 둡니다. 재처리로 같은 이벤트가 다시 들어와도 중복 탐지 결과를 만들지 않습니다. 최종 중복 방어는 Consumer 코드가 아니라 PostgreSQL `event_id` unique constraint가 담당합니다. Consumer의 `existsByEventId()` 확인은 불필요한 insert 시도를 줄이기 위한 fast path입니다.

추가 unique constraint:

- `transaction_event_receipts(event_id)`
- `event_processing_logs(topic, partition_no, offset_no)`
- `dead_letter_events(source_topic, source_partition, source_offset)`

`event_id`는 DLT 조회를 위한 index로 둡니다. Kafka 실패 이벤트의 원천 식별자는 `source_topic`, `source_partition`, `source_offset`이 더 정확합니다.

Phase 4의 `event_processing_logs`는 `event_id` unique constraint를 두지 않습니다. 같은 `eventId`가 재소비 또는 재처리 실험에서 여러 번 관측될 수 있으므로, 중복 processing log 방어 기준은 `(topic, partition_no, offset_no)`로 둡니다.

## 4. 시간 기준

- `eventTime`: 실제 거래 발생 시각
- `receivedAt`: API 서버가 이벤트를 받은 시각
- `detectedAt`: Consumer가 이상거래 탐지를 완료한 시각

측정 기준:

- `ingest_delay = receivedAt - eventTime`
- `detection_latency = detectedAt - receivedAt`
- `end_to_end_latency = detectedAt - eventTime`

## 5. Outbox 결정

초기 구현에서는 API Server가 Kafka 발행 성공 이후 `ACCEPTED`를 반환합니다. 별도 transaction outbox는 구현하지 않습니다.

이유는 이 프로젝트의 핵심 범위가 API 접수 정합성보다 Consumer 처리 지연, 재처리, 장애 복구 검증이기 때문입니다.

다만 Phase 3부터 운영 추적성을 위해 `transaction_event_receipts`는 저장합니다. 기본 흐름은 request validation, `receivedAt` 생성, receipt 저장, Kafka publish, `ACCEPTED` 반환입니다.

이 결정의 한계:

- DB 저장 성공 후 Kafka publish 실패가 발생할 수 있습니다.
- Kafka publish 성공 후 `PUBLISHED` 상태 저장 또는 DB commit이 실패할 수 있습니다.
- 초기 구현에서는 이 상황을 Outbox로 자동 보정하지 않습니다.
- Kafka publish 실패는 receipt 상태를 `PUBLISH_FAILED`로 남기고 API 503으로 응답합니다.
- Kafka publish 성공 후 DB commit 실패가 발생하면 Kafka에는 이벤트가 존재하지만 receipt 상태가 반영되지 않을 수 있습니다.
- `PUBLISH_FAILED` receipt 자동 재발행은 후속 hardening 대상입니다.

향후 API 접수 기록과 Kafka 발행 원자성이 필요해지면 `outbox_events` 테이블과 outbox publisher를 추가합니다.

## 6. PostgreSQL Retention Policy

| 데이터 | 보존 기준 | 이유 |
|---|---|---|
| `event_processing_logs` | 30d 또는 실험 범위 내 보존 | Consumer 처리 추적과 장애 분석 |
| `fraud_detection_results` | 장기 보존 대상 | 탐지 결과 조회와 감사 기준 |
| `dead_letter_events` | 상태 종료 후 일정 기간 보존 | 실패 원인 분석과 재처리 감사 |
| `reprocessing_history` | 후속 구현 시 감사 목적 보존 | 운영자 조치 이력 추적 |

보존 기간은 로컬 검증 기준입니다. 운영 환경에서는 법적 보존 기준, 개인정보 최소 보관 원칙, 저장 비용을 함께 고려해 조정합니다.

## 7. V2 PaySim Data Model Planning

V2 PaySim 구현 전 설계 기준입니다. 아직 DB migration이나 Java schema가 구현된 상태는 아닙니다.

### Runtime event feature

V2 runtime event는 정답 label을 포함하지 않고, Rule V2에 필요한 balance feature만 typed optional field로 전달합니다.

결정:

- `TransactionBalanceFeatures` typed optional field를 추가합니다.
- generic `Map<String, Object> features`는 사용하지 않습니다.
- `isFraud`, `sourceFlaggedFraud`, `label`은 runtime event나 Kafka message에 포함하지 않습니다.
- PaySim label은 `paysim-labels.jsonl` sidecar에만 저장합니다.
- `paysim-labels.jsonl`에는 `eventId`, `isFraud`, `sourceFlaggedFraud`, `sourceStep`, `sourceType`만 저장하고 identifier는 포함하지 않습니다.
- normalized runtime event에는 `receivedAt`을 저장하지 않고 app-api가 접수 시 생성합니다.
- `currency`는 `KRW`로 두고 PaySim 출처는 `source=PAYSIM`으로 표현합니다.
- PaySim type 보존을 위해 `TransactionEventType`에는 `CASH_OUT`, `CASH_IN`, `DEBIT` 추가를 검토합니다.

후보 field:

```text
oldBalanceOrig
newBalanceOrig
oldBalanceDest
newBalanceDest
sourceStep
```

### V2 proposed tables

`fraud_action_decisions`:

- `unique(event_id, action_type)` 기준으로 중복 action을 방어합니다.
- CRITICAL event는 `BLOCK_TRANSACTION_CANDIDATE`, `ACCOUNT_RISK_FLAG` action row를 동시에 가질 수 있습니다.

`fraud_cases`:

- `event_id` unique 기준으로 case 중복 생성을 방어합니다.
- HIGH/CRITICAL 중심으로 case를 생성합니다.
- `ACCOUNT_RISK_FLAG`는 case table에 중복 저장하지 않고 action decision row로 유지합니다.
