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

- `event_id`: 거래 이벤트 ID
- `user_id`: 사용자 ID
- `account_id`: 계좌 ID
- `amount`: 거래 금액
- `merchant_id`: 가맹점 ID
- `device_id`: 기기 ID
- `location`: 거래 위치
- `event_time`: 거래 발생 시각
- `trace_id`: 요청 추적 ID
- `received_at`: API 접수 시각

### fraud_results

- `id`: 탐지 결과 ID
- `event_id`: 원본 이벤트 ID, unique
- `user_id`: 사용자 ID
- `risk_level`: `LOW`, `MEDIUM`, `HIGH`
- `risk_score`: 위험 점수
- `matched_rules`: 매칭된 rule 목록
- `degraded`: Redis 장애 등으로 일부 rule이 생략되었는지 여부
- `detected_at`: 탐지 완료 시각

### event_processing_logs

- `id`: 처리 로그 ID
- `event_id`: 원본 이벤트 ID
- `topic`: Kafka topic
- `partition_no`: Kafka partition
- `offset_no`: Kafka offset
- `status`: 처리 상태
- `started_at`: 처리 시작 시각
- `completed_at`: 처리 완료 시각
- `error_message`: 실패 사유

### dlq_events

- `id`: DLQ 이벤트 ID
- `event_id`: 원본 이벤트 ID
- `topic`: 원본 topic
- `payload`: 실패 payload
- `failure_reason`: 실패 원인
- `status`: `DLQ_PENDING`, `REPROCESSING`, `REPROCESSED`, `DISCARDED`, `FAILED_PERMANENT`
- `created_at`: DLQ 저장 시각
- `updated_at`: 상태 변경 시각

## 3. 중복 방어 기준

`fraud_results.event_id`에 unique constraint를 둡니다. 재처리로 같은 이벤트가 다시 들어와도 중복 탐지 결과를 만들지 않습니다.
