# Fraud Action Decision

## 1. Purpose

V2에서는 fraud detection result에서 끝나지 않고, risk level에 따라 후속 운영 조치 후보를 생성합니다.

핵심 흐름:

```text
Fraud Detection Result
-> Fraud Action Decision
-> Fraud Case
-> Admin Review
-> Audit Log
```

이 흐름은 실제 계좌 정지나 금융 제재를 자동 실행하기 위한 것이 아닙니다. 이상거래 탐지 결과를 운영자가 검토 가능한 조치 후보로 전환하기 위한 설계입니다.

## 2. Non-goal

V2에서 하지 않는 것:

- 실제 계좌 정지
- 실제 송금 취소
- 외부 금융기관 API 호출
- production-grade sanction workflow
- ML 기반 action policy

## 3. Action Policy

| Risk Level | Detection Decision | Action Type | Action Status | Notes |
|---|---|---|---|---|
| LOW | APPROVE | NO_ACTION | APPLIED | 정상 처리 |
| MEDIUM | REVIEW | CREATE_REVIEW_CASE | PENDING | 운영자 검토 case 생성 |
| HIGH | HOLD | HOLD_TRANSACTION | PENDING | 거래 보류 후보 |
| CRITICAL | BLOCK_CANDIDATE | BLOCK_TRANSACTION_CANDIDATE | REQUIRES_MANUAL_APPROVAL | 자동 차단 아님 |
| CRITICAL | BLOCK_CANDIDATE | ACCOUNT_RISK_FLAG | PENDING | 계정 위험 flag |

## 4. Why No Automatic Account Freeze

본 프로젝트는 이상거래 탐지 결과에 따라 실제 계좌 정지나 금융 제재를 자동 실행하지 않습니다.

`CRITICAL` 결과는 `BLOCK_TRANSACTION_CANDIDATE`와 `ACCOUNT_RISK_FLAG`로 기록하고, 운영자 검토와 audit log를 거쳐 후속 조치를 결정합니다.

이 설계는 다음 이유를 갖습니다.

- 오탐으로 인한 사용자 피해를 줄입니다.
- 금융 도메인의 설명 가능성과 감사 가능성을 확보합니다.
- Rule 기반 탐지는 deterministic하지만 완전한 진실이 아닙니다.
- PaySim label은 평가용이며 실제 운영 제재 근거가 아닙니다.

## 5. Data Model Proposal

### fraud_action_decisions

| Column | Type | Notes |
|---|---|---|
| `id` | bigint | primary key |
| `event_id` | varchar(100) | unique |
| `fraud_result_id` | bigint | fraud result reference |
| `risk_level` | varchar(30) | LOW/MEDIUM/HIGH/CRITICAL |
| `detection_decision` | varchar(50) | APPROVE/REVIEW/HOLD/BLOCK_CANDIDATE |
| `action_type` | varchar(80) | action policy result |
| `action_status` | varchar(50) | APPLIED/PENDING/REQUIRES_MANUAL_APPROVAL |
| `reason` | text | summarized reason |
| `created_at` | timestamp | created time |
| `updated_at` | timestamp | updated time |

Constraints:

- `event_id` unique
- `action_type` not null
- `action_status` not null
- valid enum check constraints

## 6. Idempotency

같은 `eventId`는 중복 `fraud_action_decisions` row를 만들면 안 됩니다.

기준:

- PostgreSQL `fraud_action_decisions.event_id` unique constraint
- Consumer duplicate path에서 이미 action decision이 있으면 skip
- DLT reprocessing 후에도 원본 `eventId` 유지

## 7. API Proposal

### GET `/api/v1/admin/events/{eventId}/action-decision`

Header:

```http
X-Admin-Token: <token>
```

Response:

```json
{
  "eventId": "paysim-000001",
  "riskLevel": "CRITICAL",
  "detectionDecision": "BLOCK_CANDIDATE",
  "actionType": "BLOCK_TRANSACTION_CANDIDATE",
  "actionStatus": "REQUIRES_MANUAL_APPROVAL",
  "reason": "Balance drain and transfer/cash-out pattern detected"
}
```

## 8. Audit

Action decision 생성은 운영자 직접 조치가 아니라 Consumer processing 결과입니다.

Audit 후보:

- `FRAUD_ACTION_DECISION_CREATED`
- target type: `EVENT`
- target id: `eventId`
- metadata: risk level, action type, action status

운영자 review와 case resolution은 `admin_audit_logs`에 별도 action으로 남깁니다.

## 9. Observability

Metric 후보:

- `fraud_action_decision_total{actionType, actionStatus}`
- `fraud_action_decision_latency`
- `fraud_case_created_total{riskLevel}`

Metric tag에는 `eventId`, `userId`, `accountId`를 넣지 않습니다.
