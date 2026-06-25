# Phase 14. 운영자 API 보호와 Audit Log로 재처리 기능을 안전하게 만들기

## 1. 이번 Phase에서 풀려는 문제

Phase 13까지는 DLT 조회, 재처리, 폐기 흐름과 부하/장애 evidence를 정리했습니다. 하지만 운영자 API가 local/dev 환경에서 완전히 공개된 것처럼 보일 수 있었고, 재처리/폐기 액션을 누가 왜 수행했는지 저장하는 감사 근거가 부족했습니다.

Phase 14의 목표는 운영자 API 보호와 감사 가능성의 최소 구현입니다. 완전한 IAM 시스템이 아니라 local/dev 환경에서 위험한 운영 API를 기본적으로 막고, DLT 조치 이력을 남기는 데 집중했습니다.

## 2. DLT 재처리 API가 위험한 이유

DLT 재처리는 실패한 이벤트를 다시 `transaction-events`로 넣는 기능입니다. 잘못 사용하면 같은 이벤트를 반복 재발행하거나, 실패 원인이 해결되지 않은 상태에서 Kafka 부하를 만들 수 있습니다.

따라서 재처리 API는 단순한 편의 기능이 아니라 운영 조작입니다. 누가 요청했는지, 어떤 DLT id를 대상으로 했는지, 성공했는지 실패했는지, 실패했다면 왜 실패했는지 남겨야 합니다.

## 3. X-Admin-Token을 선택한 이유와 한계

이번 Phase에서는 `/api/v1/admin/**`에 `X-Admin-Token` header를 요구하도록 했습니다. token은 `ADMIN_API_TOKEN` 환경 변수 또는 `security.admin.token` 설정으로 주입합니다.

이 선택은 범위 조절입니다. Spring Security OAuth2, JWT, RBAC까지 구현하면 인증 시스템 자체가 중심 과제가 됩니다. Phase 14에서는 운영자 API가 완전히 공개된 상태가 아니며, 나중에 더 강한 인증/인가로 교체할 수 있는 구조를 남기는 데 집중했습니다.

한계도 명확합니다. `X-Admin-Token`은 production-grade 인증/인가가 아닙니다. 운영 환경에서는 JWT/OAuth2, ADMIN role, RBAC, IP allowlist, token rotation, gateway rate limit이 필요합니다.

## 4. Audit Log를 남긴 이유

DLT reprocess/discard는 상태를 바꾸고, 경우에 따라 Kafka에 메시지를 다시 발행합니다. 이 액션은 사후 설명 가능성이 중요합니다.

Phase 14에서는 `admin_audit_logs` 테이블을 추가했습니다. DLT reprocess/discard 성공과 실패를 모두 저장하고, actor, action, target id, eventId, traceId, result, reason, 최소 metadata를 남깁니다.

## 5. audit log에 저장하지 않은 정보

감사 로그는 많은 정보를 담을수록 좋은 저장소가 아닙니다. 운영자가 확인해야 하는 최소 사실만 남기고, 권한 유출이나 민감정보 노출 가능성이 있는 값은 제외합니다.

저장하지 않는 정보:

- `X-Admin-Token`
- DLT `payload_json` 전체
- request body 전체
- accountId, deviceId
- stacktrace 전체

## 6. max reprocess attempts를 추가한 이유

Phase 9에는 `reprocess_attempts`가 있었지만 최대 횟수 제한은 없었습니다. Phase 14에서는 기본 max attempts를 3으로 두고, `reprocess_attempts >= maxAttempts`이면 `409 MAX_REPROCESS_ATTEMPTS_EXCEEDED`를 반환합니다.

이때 Kafka publish는 호출하지 않습니다. 반복 실패 이벤트를 계속 재발행하는 상황을 막기 위한 최소 방어입니다.

## 7. audit log와 상태 변경을 같은 transaction으로 묶은 이유

운영자 액션은 상태 변경과 감사 기록이 함께 설명 가능해야 합니다. 상태 변경은 성공했는데 audit log가 없다면, 누가 왜 조치했는지 설명할 수 없습니다.

그래서 성공 경로에서는 상태 변경과 audit 저장을 같은 transaction 경계에 둡니다. Kafka publish 실패처럼 실패 상태를 보존해야 하는 경우에는 `REPROCESS_FAILED`와 FAILED audit을 남긴 뒤 API에 실패를 반환합니다.

## 8. 테스트와 검증 결과

Phase 14에서는 다음을 테스트했습니다.

- admin token 없음 또는 오류 시 `401 UNAUTHORIZED_ADMIN_API`
- 올바른 token으로 DLT 목록 조회 가능
- 일반 transaction ingest API는 admin token 없이 validation 흐름까지 도달
- DLT reprocess 성공/실패 audit log 저장
- DLT discard 성공/실패 audit log 저장
- max attempts 초과 시 `409`와 Kafka publish 미호출
- audit metadata에 admin token과 원본 payload 전체가 저장되지 않음

검증 명령:

```bash
./gradlew :app-api:test
```

## 9. 이번 Phase의 한계

이번 Phase는 local/dev 보안 guardrail입니다.

- JWT/OAuth2/RBAC는 구현하지 않았습니다.
- audit log 조회 API는 없습니다.
- Gateway/Nginx/API Gateway rate limit은 없습니다.
- IP allowlist와 token rotation은 없습니다.
- batch reprocess와 관리자 승인 workflow는 없습니다.

## 10. 다음 Phase에서 보완할 점

후속 운영 보안 Phase에서는 인증/인가를 실제 운영 모델로 확장해야 합니다.

- JWT/OAuth2/RBAC 기반 Admin role
- audit log 조회/필터링 API와 접근 권한
- gateway-level rate limit과 IP allowlist
- DLT batch reprocess, cooldown, 관리자 승인 workflow
- DLT pending/reprocess/discard metric과 alert rule
