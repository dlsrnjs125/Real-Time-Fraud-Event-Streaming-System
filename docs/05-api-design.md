# API Design

## 1. 거래 이벤트 접수

### POST `/api/v1/transactions/events`

거래 이벤트를 검증하고 `transaction-events` topic으로 발행합니다.

Request:

```json
{
  "userId": "user-1001",
  "accountId": "acc-3001",
  "eventType": "PAYMENT",
  "amount": 1500000,
  "currency": "KRW",
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

초기에는 `app-api`가 운영자 조회를 위해 PostgreSQL read-only 접근을 가질 수 있습니다. 한 달 범위의 초기 구현에서는 이 방식이 단순합니다.

다만 Consumer 소유 데이터 처리 경계를 더 엄격하게 유지해야 하는 단계가 오면, `app-api`는 reprocess command topic에 명령을 발행하고 `app-consumer`가 이를 소비해 재처리하는 구조를 검토합니다.
