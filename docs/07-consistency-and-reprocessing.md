# Consistency and Reprocessing

## 1. Offset Commit 기준

Consumer는 이벤트를 읽은 직후가 아니라 처리 완료 후 offset을 commit합니다.

처리 완료 기준:

- FraudResult 저장 성공
- EventProcessingLog 저장 성공
- 필요한 후속 이벤트 발행 성공
- 그 이후 acknowledgment 수행

## 2. 설정 기준

- `enable-auto-commit=false`
- `AckMode.MANUAL` 또는 `AckMode.MANUAL_IMMEDIATE`
- `eventId` 기준 idempotency 보장

## 3. Consumer 장애 시 동작

Consumer가 죽어도 Kafka topic에 이벤트는 남아 있습니다. 처리 완료 전에 offset을 commit하지 않았다면 Consumer 재시작 후 마지막 commit 이후 메시지부터 다시 소비할 수 있습니다.

중복 처리를 방어하기 위해 다음 기준을 둡니다.

- `fraud_results.event_id` unique
- 처리 전 `eventId` 처리 여부 확인
- 중복 이벤트는 `duplicate_processed`로 로그 기록 후 skip

## 4. DLQ 재처리

DLQ 이벤트는 자동으로 무한 재처리하지 않습니다. 운영자 API를 통해 실패 원인을 확인하고, 재처리 가능한 이벤트만 명시적으로 재처리합니다.

상태:

- `DLQ_PENDING`
- `REPROCESSING`
- `REPROCESSED`
- `DISCARDED`
- `FAILED_PERMANENT`

## 5. 재처리 원칙

- 재처리 시 원본 `eventId`를 유지합니다.
- 재처리 요청마다 `reprocessAttemptId`를 부여합니다.
- `eventId` unique constraint로 중복 탐지 결과를 방어합니다.
