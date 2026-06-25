# Fraud Case Management

## 1. Purpose

V2에서는 위험도가 높은 이벤트를 Fraud Case로 생성합니다.

Fraud Case는 실제 금융 제재가 아니라 운영자 검토와 감사 추적을 위한 관리 단위입니다.

## 2. Case Creation Policy

| Risk Level | Action Type | Case Creation |
|---|---|---|
| LOW | NO_ACTION | 생성하지 않음 |
| MEDIUM | REVIEW_CANDIDATE | 생성하지 않음 |
| HIGH | HOLD_TRANSACTION_CANDIDATE | 생성 |
| CRITICAL | BLOCK_TRANSACTION_CANDIDATE | 생성 |
| CRITICAL | ACCOUNT_RISK_FLAG | 동일 event case에 metadata로 연결 |

같은 `eventId`에 대해 case는 중복 생성하지 않습니다.

초기 V2에서 MEDIUM은 `REVIEW_CANDIDATE` action decision만 남기고 case 자동 생성은 후속으로 둡니다. PaySim replay 시 case 폭증을 막기 위한 범위 제한입니다.

## 3. Data Model Proposal

### fraud_cases

| Column | Type | Notes |
|---|---|---|
| `id` | bigint | primary key |
| `event_id` | varchar(100) | unique |
| `user_id` | varchar(100) | synthetic/user key |
| `fraud_result_id` | bigint | fraud result reference |
| `case_status` | varchar(50) | OPEN/IN_REVIEW/... |
| `risk_level` | varchar(30) | MEDIUM/HIGH/CRITICAL |
| `assigned_to` | varchar(100) | nullable, Phase 14 actor model caveat applies |
| `review_reason` | text | case creation reason |
| `resolution` | text | operator resolution note |
| `created_at` | timestamp | created time |
| `updated_at` | timestamp | updated time |

Case status:

- `OPEN`
- `IN_REVIEW`
- `RESOLVED_APPROVED`
- `RESOLVED_BLOCKED`
- `DISMISSED`

## 4. API Proposal

### GET `/api/v1/admin/fraud-cases`

Header:

```http
X-Admin-Token: <token>
```

Query:

```text
status=OPEN&page=0&size=20
```

Response:

```json
{
  "items": [
    {
      "caseId": 1,
      "eventId": "paysim-000001",
      "riskLevel": "CRITICAL",
      "caseStatus": "OPEN",
      "reviewReason": "Balance drain and block candidate action created",
      "createdAt": "2026-01-01T01:00:03Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1
}
```

### GET `/api/v1/admin/fraud-cases/{caseId}`

단건 case 상세를 조회합니다.

Header:

```http
X-Admin-Token: <token>
```

Response:

```json
{
  "caseId": 1,
  "eventId": "paysim-000001",
  "userId": "U-9f1a3c21e2b0",
  "riskLevel": "CRITICAL",
  "caseStatus": "OPEN",
  "reviewReason": "Balance drain and block candidate action created",
  "resolution": null,
  "createdAt": "2026-01-01T01:00:03Z",
  "updatedAt": "2026-01-01T01:00:03Z"
}
```

### POST `/api/v1/admin/fraud-cases/{caseId}/resolve`

Header:

```http
X-Admin-Token: <token>
```

Request:

```json
{
  "operatorId": "operator-001",
  "resolution": "RESOLVED_BLOCKED",
  "reason": "Confirmed suspicious balance drain"
}
```

Policy:

- `operatorId` 필수
- `reason` 필수
- 이미 resolved 상태이면 `409 Conflict`
- 실제 계좌 정지가 아니라 case resolution만 수행
- audit log 필수

## 5. Audit Actions

기존 Phase 14 `admin_audit_logs`를 재사용합니다.

추가 action 후보:

- `FRAUD_CASE_OPEN`
- `FRAUD_CASE_RESOLVE`
- `FRAUD_ACTION_DECISION_CREATED`

운영자 조치 audit metadata에는 다음만 저장합니다.

- case id
- eventId
- previous status
- next status
- risk level
- reason summary

저장하지 않는 정보:

- admin token
- raw PaySim row
- full payload
- account-like identifier 전체가 불필요하게 포함된 metadata

## 6. Actor Model Caveat

Phase 14의 `operatorId`는 local/dev audit 추적을 위한 self-claimed field입니다.

V2 case resolution에서도 동일 한계를 유지합니다. 운영 환경에서는 JWT subject, SSO user id, RBAC principal을 actor로 사용해야 합니다.

## 7. Idempotency and Reprocessing

- 같은 `eventId`의 duplicate replay가 중복 case를 만들면 안 됩니다.
- `fraud_cases.event_id` unique constraint를 둡니다.
- DLT reprocessing은 원본 `eventId`를 유지하므로 case duplication 방어가 가능합니다.

## 8. Completion Criteria

- Action decision에 따라 case 생성 여부 결정
- Fraud case list API 설계 반영
- Fraud case detail API 설계 반영
- Fraud case resolve API 설계 반영
- resolved 상태 재처리 방어 정책 문서화
- audit log action 확장 문서화
- 실제 금융 제재 미수행 원칙 문서화
