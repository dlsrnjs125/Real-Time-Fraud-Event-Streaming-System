# API Design

## 1. 거래 이벤트 접수

### POST `/api/v1/transactions/events`

거래 이벤트를 검증하고 `transaction-events` topic으로 발행합니다.

Request:

```json
{
  "userId": "user-1001",
  "accountId": "acc-3001",
  "amount": 1500000,
  "merchantId": "merchant-777",
  "deviceId": "device-abc",
  "location": "KR",
  "eventTime": "2026-06-15T10:30:00+09:00"
}
```

Response:

```json
{
  "eventId": "evt-20260615-000001",
  "status": "ACCEPTED",
  "traceId": "trace-..."
}
```

## 2. DLQ 조회

### GET `/api/v1/admin/dlq-events`

DLQ에 쌓인 실패 이벤트를 조회합니다.

## 3. DLQ 재처리

### POST `/api/v1/admin/dlq-events/{dlqId}/reprocess`

재처리 가능한 DLQ 이벤트를 `transaction-events`로 다시 발행합니다.

## 4. DLQ 폐기

### PATCH `/api/v1/admin/dlq-events/{dlqId}/discard`

재처리 불가능한 DLQ 이벤트를 폐기 상태로 변경합니다.

## 5. 운영 조회

향후 탐지 결과, 처리 지연, rule 매칭 결과, degraded mode 발생 여부를 조회하는 API를 추가합니다.
