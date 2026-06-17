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

### fraud_results

- `id`: 탐지 결과 ID
- `event_id`: 원본 이벤트 ID, unique
- `user_id`: 사용자 ID
- `risk_level`: `LOW`, `MEDIUM`, `HIGH`
- `risk_score`: 위험 점수
- `matched_rules`: 매칭된 rule 목록
- `skipped_rules`: Redis 장애 또는 fallback 실패로 실행하지 못한 rule 목록
- `rule_results`: ruleCode, score, matched, skipped, reason을 담는 JSONB 확장 후보
- `degraded`: Redis 장애 등으로 일부 rule이 생략되었는지 여부
- `detected_at`: 탐지 완료 시각

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

### dlq_events

- `id`: DLQ 이벤트 ID
- `event_id`: 원본 이벤트 ID
- `original_topic`: 원본 topic
- `original_partition`: 원본 partition
- `original_offset`: 원본 offset
- `payload`: 실패 payload
- `payload_hash`: 실패 payload hash
- `failure_reason`: 실패 원인
- `status`: `DLQ_PENDING`, `REPROCESSING`, `REPROCESSED`, `DISCARDED`, `FAILED_PERMANENT`
- `created_at`: DLQ 저장 시각
- `updated_at`: 상태 변경 시각

### reprocessing_history

- `id`: 재처리 이력 ID
- `dlq_id`: DLQ 이벤트 ID
- `operator_id`: 작업자 ID
- `action`: `REPROCESS` 또는 `DISCARD`
- `reason`: 재처리 또는 폐기 사유
- `result`: 처리 결과
- `reprocess_attempt_id`: 재처리 시도 ID
- `created_at`: 이력 생성 시각

## 3. 중복 방어 기준

`fraud_results.event_id`에 unique constraint를 둡니다. 재처리로 같은 이벤트가 다시 들어와도 중복 탐지 결과를 만들지 않습니다.

추가 unique constraint:

- `transaction_event_receipts(event_id)`
- `event_processing_logs(topic, partition_no, offset_no)`
- `dlq_events(original_topic, original_partition, original_offset)`

`event_id`는 DLQ 조회와 중복 방어를 위한 index로 둡니다. Kafka 실패 이벤트의 원천 식별자는 `original_topic`, `original_partition`, `original_offset`이 더 정확합니다.

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
| `fraud_results` | 장기 보존 대상 | 탐지 결과 조회와 감사 기준 |
| `dlq_events` | 상태 종료 후 일정 기간 보존 | 실패 원인 분석과 재처리 감사 |
| `reprocessing_history` | 감사 목적 보존 | 운영자 조치 이력 추적 |

보존 기간은 로컬 검증 기준입니다. 운영 환경에서는 법적 보존 기준, 개인정보 최소 보관 원칙, 저장 비용을 함께 고려해 조정합니다.
