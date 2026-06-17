# API Design

## 1. API 설계 원칙

API는 거래 이벤트를 빠르게 접수하고, 비동기 Consumer 처리 결과를 운영자가 추적할 수 있게 하는 계약입니다.

이 문서의 API는 초기 구현 기준입니다. Admin API는 인증/인가가 붙기 전까지 local/development-only로 취급합니다.

원칙:

- Controller는 요청 검증, 응답 매핑, HTTP status 변환만 담당합니다.
- Kafka 발행, DLQ 재처리, 조회 조합은 application service에서 수행합니다.
- Fraud rule 실행, Redis sliding window 계산, Consumer offset 관리는 `app-consumer` 책임입니다.
- 운영 조회 API는 PostgreSQL에 저장된 결과와 로그를 read-only로 조회합니다.
- 응답에는 가능한 한 `traceId`를 포함해 API 요청과 Consumer 로그를 연결합니다.
- 민감할 수 있는 `accountId`, `deviceId`, raw payload는 admin 응답과 로그에서 원문 노출을 피합니다.

## 2. 공통 응답 규칙

시간 값은 ISO-8601 offset datetime을 사용합니다.

예시:

```json
"2026-06-15T10:30:01+09:00"
```

목록 API는 다음 공통 형태를 사용합니다.

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0
}
```

초기 pagination은 `page`, `size` 기반으로 정의합니다. 정렬은 기본적으로 최신 데이터 우선입니다.

## 3. 공통 Error Response

```json
{
  "code": "INVALID_TRANSACTION_EVENT",
  "message": "amount must be greater than zero",
  "traceId": "trace-20260615-000001"
}
```

대표 error code:

| Code | HTTP status | 의미 |
|---|---:|---|
| `INVALID_TRANSACTION_EVENT` | 400 | 거래 이벤트 요청 validation 실패 |
| `UNSUPPORTED_SCHEMA_VERSION` | 400 | 지원하지 않는 schema version |
| `DUPLICATE_TRANSACTION_EVENT` | 409 | 중복 eventId 접수 |
| `DATABASE_WRITE_FAILED` | 500 | 접수 기록 저장 실패 |
| `EVENT_NOT_FOUND` | 404 | eventId 기준 데이터 없음 |
| `FRAUD_RESULT_NOT_FOUND` | 404 | 탐지 결과 없음 |
| `DLQ_EVENT_NOT_FOUND` | 404 | DLQ 이벤트 없음 |
| `DLQ_EVENT_NOT_REPROCESSABLE` | 409 | 현재 상태에서 재처리 불가 |
| `KAFKA_PUBLISH_FAILED` | 503 | Kafka 발행 실패 |
| `INTERNAL_ERROR` | 500 | 분류되지 않은 서버 오류 |

Validation failure는 재시도해도 성공하지 않는 입력 오류입니다. Kafka publish 실패는 일시 장애 가능성이 있으므로 503으로 구분합니다.

## 4. TraceId 정책

- 요청 header에 `X-Trace-Id`가 있으면 해당 값을 사용합니다.
- 없으면 `app-api`가 새 `traceId`를 생성합니다.
- `traceId`는 API response, Kafka event message, structured log, PostgreSQL receipt/log/result에 전파합니다.
- `traceId`는 보안 토큰이 아니며 인증 목적으로 사용하지 않습니다.

## 5. Transaction Event API

### POST `/api/v1/transactions/events`

거래 이벤트를 검증하고 접수 기록을 저장한 뒤 `transaction-events` topic으로 발행합니다.

Request:

```json
{
  "eventId": "evt-20260615-000001",
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
  "status": "PUBLISHED",
  "receivedAt": "2026-06-15T10:30:01+09:00",
  "traceId": "trace-20260615-000001"
}
```

Validation 기준:

- `eventId`, `userId`, `accountId`, `eventType`, `amount`, `currency`, `eventTime`은 필수입니다.
- `eventId`, `userId`, `accountId`, `currency`는 blank 값을 허용하지 않습니다.
- `amount`는 0보다 커야 하며 Java 구현에서는 `BigDecimal`을 사용합니다.
- 초기 통화는 `KRW`만 허용합니다.
- `eventTime`이 `receivedAt`보다 5분을 초과해 미래이면 validation failure로 처리합니다.
- `merchantId`, `deviceId`, `location`은 rule 확장을 위해 선택 값으로 둘 수 있습니다.

처리 흐름:

1. 요청 validation
2. `traceId`, `eventId`, `receivedAt`, `schemaVersion` 결정
3. `transaction_event_receipts` 저장
4. Kafka key를 `userId`로 사용해 `transaction-events` 발행
5. Kafka publish 성공 시 `ACCEPTED` 반환

초기 구현은 Outbox를 사용하지 않습니다. 따라서 DB transaction과 Kafka publish는 원자적으로 묶이지 않습니다. DB 저장 성공 후 Kafka publish 실패가 발생할 수 있고, 반대로 Kafka publish 성공 후 `PUBLISHED` 상태 저장 또는 DB commit이 실패할 수도 있습니다. 이 경우 receipt status와 재발행/감사 보정 정책은 별도 Phase에서 보강합니다.

Phase 3 동작:

- request DTO validation을 수행합니다.
- validation 실패 시 `ErrorResponse`를 반환합니다.
- 중복 `eventId`는 `409 CONFLICT`와 `DUPLICATE_TRANSACTION_EVENT`로 처리합니다.
- Kafka publish 실패는 receipt를 `PUBLISH_FAILED`로 남기고 `503 SERVICE_UNAVAILABLE`과 `KAFKA_PUBLISH_FAILED`로 처리합니다.
- `PUBLISH_FAILED` 상태의 동일 `eventId` 재요청도 Phase 3에서는 `409 CONFLICT`로 막습니다.
- 유효한 요청은 receipt 저장과 Kafka publish 성공 후 `202 Accepted`를 반환합니다.
- Consumer 처리 상태 변경은 수행하지 않습니다.

### GET `/api/v1/transactions/events/{eventId}`

거래 이벤트 접수 기록과 현재 처리 상태를 조회합니다.

Response:

```json
{
  "eventId": "evt-20260615-000001",
  "userId": "user-1001",
  "eventType": "PAYMENT",
  "amount": 1500000,
  "currency": "KRW",
  "status": "ACCEPTED",
  "eventTime": "2026-06-15T10:30:00+09:00",
  "receivedAt": "2026-06-15T10:30:01+09:00",
  "traceId": "trace-20260615-000001"
}
```

`accountId`와 `deviceId`는 초기 운영 조회 응답에서 원문을 기본 노출하지 않습니다. 운영 조회가 필요하면 `maskedAccountId` 또는 내부 admin API에서 제한적으로 제공하는 방향으로 별도 정의합니다.

## 6. Fraud Result Admin API

### GET `/api/v1/admin/fraud-results`

이상거래 탐지 결과 목록을 조회합니다.

Query parameter:

| 이름 | 예시 | 설명 |
|---|---|---|
| `riskLevel` | `HIGH` | `LOW`, `MEDIUM`, `HIGH` |
| `degraded` | `true` | Redis 장애 등으로 일부 rule이 생략된 결과 |
| `from` | `2026-06-15T00:00:00+09:00` | 탐지 시각 시작 |
| `to` | `2026-06-15T23:59:59+09:00` | 탐지 시각 종료 |
| `ruleCode` | `VELOCITY` | matched 또는 skipped rule code |
| `page` | `0` | page index |
| `size` | `20` | page size |

Response:

```json
{
  "items": [
    {
      "eventId": "evt-20260615-000001",
      "userId": "user-1001",
      "riskLevel": "HIGH",
      "riskScore": 75,
      "matchedRuleCodes": ["HIGH_AMOUNT", "VELOCITY"],
      "skippedRuleCodes": [],
      "degraded": false,
      "detectedAt": "2026-06-15T10:30:02+09:00",
      "detectionLatencyMs": 1000,
      "traceId": "trace-20260615-000001"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

### GET `/api/v1/admin/fraud-results/{eventId}`

특정 이벤트의 탐지 상세를 조회합니다.

Response:

```json
{
  "eventId": "evt-20260615-000001",
  "userId": "user-1001",
  "riskLevel": "HIGH",
  "riskScore": 75,
  "matchedRuleCodes": ["HIGH_AMOUNT", "VELOCITY"],
  "skippedRuleCodes": [],
  "degraded": false,
  "ruleResults": [
    {
      "ruleCode": "HIGH_AMOUNT",
      "matched": true,
      "skipped": false,
      "score": 40,
      "reason": "amount >= 1000000 KRW"
    },
    {
      "ruleCode": "VELOCITY",
      "matched": true,
      "skipped": false,
      "score": 35,
      "reason": "5 transactions within 60 seconds"
    }
  ],
  "eventTime": "2026-06-15T10:30:00+09:00",
  "receivedAt": "2026-06-15T10:30:01+09:00",
  "detectedAt": "2026-06-15T10:30:02+09:00",
  "detectionLatencyMs": 1000,
  "endToEndLatencyMs": 2000,
  "traceId": "trace-20260615-000001"
}
```

이 API는 “왜 HIGH인가”, “어떤 rule이 생략됐는가”, “Redis 장애로 degraded 되었는가”, “탐지 지연이 얼마인가”를 설명하는 기준 API입니다.

## 7. Fraud Rule Admin API

### GET `/api/v1/admin/fraud-rules`

현재 활성화된 rule 목록과 기본 조건을 조회합니다.

Response:

```json
{
  "items": [
    {
      "ruleCode": "HIGH_AMOUNT",
      "enabled": true,
      "score": 40,
      "condition": "amount >= 1000000 KRW",
      "requiresRedis": false
    },
    {
      "ruleCode": "VELOCITY",
      "enabled": true,
      "score": 35,
      "condition": "5 transactions within 60 seconds",
      "requiresRedis": true
    }
  ]
}
```

Rule 설정 변경 API는 초기 범위에서 제외합니다. `PATCH /api/v1/admin/fraud-rules/{ruleCode}`는 Phase 13+ 후보입니다.

## 8. DLQ Admin API

### GET `/api/v1/admin/dlq-events`

DLQ에 저장된 실패 이벤트를 조회합니다.

Query parameter:

| 이름 | 예시 | 설명 |
|---|---|---|
| `status` | `DLQ_PENDING` | DLQ 상태 |
| `from` | `2026-06-15T00:00:00+09:00` | 생성 시각 시작 |
| `to` | `2026-06-15T23:59:59+09:00` | 생성 시각 종료 |
| `page` | `0` | page index |
| `size` | `20` | page size |

Response:

```json
{
  "items": [
    {
      "dlqId": 1,
      "eventId": "evt-20260615-000001",
      "originalTopic": "transaction-events",
      "originalPartition": 2,
      "originalOffset": 1532,
      "failureReason": "unsupported schemaVersion: v2",
      "status": "DLQ_PENDING",
      "payloadHash": "sha256:...",
      "createdAt": "2026-06-15T10:31:00+09:00",
      "updatedAt": "2026-06-15T10:31:00+09:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

Raw payload는 기본 응답에 포함하지 않습니다. 재처리 가능성 판단에는 `payloadHash`, 실패 원인, 원본 topic/partition/offset, 상태를 사용합니다.

### POST `/api/v1/admin/dlq-events/{dlqId}/reprocess`

재처리 가능한 DLQ 이벤트를 `transaction-events`로 다시 발행합니다. 원본 `eventId`는 유지해야 합니다.

Request:

```json
{
  "operatorId": "local-admin",
  "reason": "schema mapping fixed and payload is reprocessable"
}
```

Response:

```json
{
  "dlqId": 1,
  "status": "REPROCESSING",
  "reprocessAttemptId": "attempt-20260615-000001",
  "traceId": "trace-20260615-000002"
}
```

재처리 시 `fraud_results.event_id` unique constraint와 application-level duplicate handling으로 중복 FraudResult 생성을 막습니다.

### PATCH `/api/v1/admin/dlq-events/{dlqId}/discard`

재처리 불가능한 DLQ 이벤트를 폐기 상태로 변경합니다.

Request:

```json
{
  "operatorId": "local-admin",
  "reason": "invalid payload cannot be safely reprocessed"
}
```

Response:

```json
{
  "dlqId": 1,
  "status": "DISCARDED",
  "traceId": "trace-20260615-000003"
}
```

## 9. Event Processing Log API

### GET `/api/v1/admin/events/{eventId}/processing-log`

Consumer 처리 로그를 조회합니다.

Response:

```json
{
  "eventId": "evt-20260615-000001",
  "logs": [
    {
      "topic": "transaction-events",
      "partitionNo": 2,
      "offsetNo": 1532,
      "status": "COMPLETED",
      "startedAt": "2026-06-15T10:30:01+09:00",
      "completedAt": "2026-06-15T10:30:02+09:00",
      "processingLatencyMs": 1000,
      "errorMessage": null
    }
  ]
}
```

이 API는 Consumer 장애, 중복 처리, DLT 이동 여부를 이벤트 단위로 확인하기 위한 troubleshooting API입니다.

## 10. Operational Metrics Summary API

### GET `/api/v1/admin/operations/summary`

Prometheus/Grafana가 주 관측 수단이지만, 초기 local 검증에서는 DB 집계 기반 요약 API도 사용할 수 있습니다.

Response:

```json
{
  "from": "2026-06-15T00:00:00+09:00",
  "to": "2026-06-15T23:59:59+09:00",
  "acceptedEventCount": 10000,
  "fraudResultCount": 9980,
  "highRiskCount": 120,
  "dlqPendingCount": 3,
  "degradedResultCount": 25,
  "duplicateSkippedCount": 7
}
```

이 API는 SLO의 최종 근거가 아니라 개발 중 검증 보조 수단입니다. Consumer Lag과 latency percentile은 Prometheus metric을 기준으로 확인합니다.

## 11. Actuator and Monitoring Endpoint

`app-api`:

- `GET /actuator/health`
- `GET /actuator/prometheus`

`app-consumer`:

- `GET /actuator/health`
- `GET /actuator/prometheus`

Actuator endpoint는 business API versioning 규칙(`/api/v1`)을 따르지 않습니다.

## 12. 인증/인가 범위

초기 Phase에서는 인증/인가를 구현하지 않습니다. 대신 다음 제한을 문서화합니다.

- Admin API는 local/development-only입니다.
- 운영 환경 노출 전 인증/인가, audit logging, 접근 제어가 필요합니다.
- DLQ payload와 민감 식별자 원문 조회는 기본 API에서 제공하지 않습니다.

## 13. OpenAPI 문서화 기준

Phase 2부터 Swagger UI 또는 OpenAPI JSON으로 API 계약을 확인할 수 있어야 합니다.

문서화 기준:

- endpoint path, method, summary
- request schema
- response schema
- validation error example
- 주요 HTTP status
- admin API local-only 설명
- `traceId` header/response 정책

OpenAPI 계약은 DTO와 함께 관리합니다. 문서와 DTO가 다르면 DTO 변경 또는 문서 변경을 같은 작업에서 맞춥니다.

Local endpoint:

- Swagger UI: `/swagger-ui/index.html`
- OpenAPI JSON: `/v3/api-docs`

Phase 2 OpenAPI 설명에는 contract-only 또는 implementation pending 성격을 명시합니다. 구현되지 않은 Kafka publish, DB lookup, DLQ reprocessing을 완료된 기능처럼 표현하지 않습니다.

## 14. Phase별 구현 범위

| Phase | API 범위 | 완료 기준 |
|---:|---|---|
| Phase 2 | DTO, validation, error response, OpenAPI 설정, contract skeleton controller | Swagger UI에서 계약 확인, validation/error 테스트 |
| Phase 3 | `POST /api/v1/transactions/events`, `GET /api/v1/transactions/events/{eventId}` | receipt 저장, Kafka publish, `userId` key 확인 |
| Phase 5 | `GET /api/v1/admin/fraud-results`, `GET /api/v1/admin/fraud-results/{eventId}` | 기본 LOW 결과 조회, duplicate eventId 방어 확인 |
| Phase 6 | fraud result 상세의 `ruleResults`, `matchedRuleCodes` | AmountRule/RiskScore 결과 조회 가능 |
| Phase 7 | fraud result 상세의 `skippedRuleCodes`, `degraded` | Redis 장애 시 degraded 결과 조회 가능 |
| Phase 9 | DLQ 조회/재처리/폐기 API, processing log API | DLQ metadata 저장, 재처리 history, 중복 FraudResult 미생성 |
| Phase 10 | operational summary, Actuator/Prometheus custom metrics | API/Consumer/DLQ/Redis 지표 확인 |

Phase 번호는 `docs/13-development-roadmap.md`와 함께 유지합니다.
