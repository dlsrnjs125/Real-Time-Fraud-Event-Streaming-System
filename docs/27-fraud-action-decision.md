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
| MEDIUM | REVIEW | REVIEW_CANDIDATE | PENDING | 검토 후보. 초기 V2에서는 case 자동 생성 제외 |
| HIGH | HOLD_CANDIDATE | HOLD_TRANSACTION_CANDIDATE | REQUIRES_MANUAL_REVIEW | 거래 보류 후보 |
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
| `event_id` | varchar(100) | event id |
| `fraud_result_id` | bigint | fraud result reference |
| `risk_level` | varchar(30) | LOW/MEDIUM/HIGH/CRITICAL |
| `detection_decision` | varchar(50) | APPROVE/REVIEW/HOLD/BLOCK_CANDIDATE |
| `action_type` | varchar(80) | action policy result |
| `action_status` | varchar(50) | APPLIED/PENDING/REQUIRES_MANUAL_APPROVAL |
| `reason` | text | summarized reason |
| `created_at` | timestamp | created time |
| `updated_at` | timestamp | updated time |

Constraints:

- `unique(event_id, action_type)`
- `action_type` not null
- `action_status` not null
- valid enum check constraints

## 6. Idempotency

같은 `eventId`와 `actionType` 조합은 중복 `fraud_action_decisions` row를 만들면 안 됩니다.

기준:

- PostgreSQL `fraud_action_decisions(event_id, action_type)` unique constraint
- Consumer duplicate path에서 이미 같은 action decision이 있으면 skip
- DLT reprocessing 후에도 원본 `eventId` 유지

## 6.1 Versioning Boundary

V2에서는 `event_id + action_type` 기준으로 action decision을 생성합니다. CRITICAL 이벤트는 `BLOCK_TRANSACTION_CANDIDATE`와 `ACCOUNT_RISK_FLAG`처럼 서로 다른 status를 가진 action row를 여러 개 가질 수 있습니다.

제외 범위:

- Rule version 변경 후 동일 event를 재평가해 다른 action을 생성하는 시나리오
- action decision supersede history
- decision approval workflow

후속 구현 후보:

- `rule_version`
- `decision_version`
- `superseded_at`
- decision history table

## 6.2 Consistency and Failure Policy

Consumer는 V2 처리에서 `FraudResult`, `FraudActionDecision`, 필요한 `FraudCase` 생성을 하나의 DB transaction 안에서 처리하는 것을 기본 정책으로 둡니다.

정책:

- `FraudResult` 저장 성공 후 `ActionDecision` 생성이 실패하면 offset을 ack하지 않고 재소비를 유도합니다.
- `ActionDecision` 생성 성공 후 필요한 `FraudCase` 생성이 실패해도 offset을 ack하지 않습니다.
- PostgreSQL unique constraint는 replay와 중복 소비에서 최종 duplicate defense 역할을 합니다.

중간 상태 보강:

- 이미 `FraudResult`는 있지만 `ActionDecision`이 없는 duplicate replay path에서는 action decision/case backfill을 시도합니다.
- 별도 `v2-action-backfill` script 또는 reconciliation job은 후속 후보로 둡니다.

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
  "actions": [
    {
      "detectionDecision": "BLOCK_CANDIDATE",
      "actionType": "BLOCK_TRANSACTION_CANDIDATE",
      "actionStatus": "REQUIRES_MANUAL_APPROVAL",
      "reason": "Balance drain and transfer/cash-out pattern detected"
    },
    {
      "detectionDecision": "BLOCK_CANDIDATE",
      "actionType": "ACCOUNT_RISK_FLAG",
      "actionStatus": "PENDING",
      "reason": "Critical event requires account risk review"
    }
  ]
}
```

CRITICAL 이벤트는 `BLOCK_TRANSACTION_CANDIDATE`와 `ACCOUNT_RISK_FLAG` action row를 함께 반환할 수 있습니다.

## 8. Audit

Action decision 생성은 운영자 직접 조치가 아니라 Consumer processing 결과입니다.

V2 초기 구현에서는 Consumer가 자동 생성하는 ActionDecision을 `admin_audit_logs`에 모두 기록하지 않습니다.

정책:

- `fraud_action_decisions` table이 system decision record 역할을 합니다.
- ActionDecision 생성 count와 분포는 metrics/log/evidence summary로 관측합니다.
- 운영자가 fraud case를 resolve하거나 상태를 변경하는 경우에만 admin audit log를 남깁니다.

후속 후보:

- `FRAUD_ACTION_DECISION_CREATED`를 admin audit log가 아니라 domain event 또는 metric/log 후보로 둡니다.

## 9. Observability

Metric 후보:

- `fraud_action_decision_total{actionType, actionStatus}`
- `fraud_action_decision_latency`
- `fraud_case_created_total{riskLevel}`

Metric tag에는 `eventId`, `userId`, `accountId`를 넣지 않습니다.
