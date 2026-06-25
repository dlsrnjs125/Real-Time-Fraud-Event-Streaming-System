# Security and Privacy

## 1. 목표

이 문서는 금융 거래 이벤트를 다루는 시스템에서 초기 설계 단계부터 보안과 개인정보 위험을 명확히 하기 위한 기준입니다.

초기 구현은 로컬 개발과 장애 재현을 목표로 하지만, DLQ, 로그, metric, 운영자 API가 민감정보 저장소로 변질되지 않도록 제한을 둡니다.

## 2. Admin API 접근 기준

초기 구현에서는 admin API를 local-only로 제한합니다.

운영 환경 가정에서는 다음 기능에 인증과 인가가 필요합니다.

- DLQ 이벤트 조회
- DLQ 재처리
- DLQ 폐기
- FraudResult 운영 조회
- Rule 설정 변경

DLQ 재처리와 폐기는 관리자 권한이 필요하며, 요청마다 `operatorId`, `reason`, `requestedAt`을 남깁니다.

## 3. 로그 마스킹 기준

구조화 로그에는 장애 분석에 필요한 식별자만 남깁니다.

- `eventId`: 기록 가능
- `traceId`: 기록 가능
- `userId`: 테스트 데이터에서는 기록 가능, 운영 가정에서는 hash 또는 masking
- `accountId`: 마지막 4자리만 기록
- `deviceId`: hash 처리
- `ipAddress`: 마지막 octet masking
- `location`: 국가 단위까지만 기록
- `raw payload`: 기본적으로 로그 금지
- `DLQ payload`: 저장 시 민감 필드 masking 또는 `payload_hash` 병행

로그에는 Kafka payload 전체를 그대로 남기지 않습니다.

예시:

```text
accountId = acc-1234567890
log       = acc-******7890

deviceId  = device-abcdef123456
log       = sha256(deviceId)

ipAddress = 192.168.10.25
log       = 192.168.10.xxx
```

## 4. Field Sensitivity Matrix

| Field | 민감도 | DB 저장 | 로그 | Metric Label | 비고 |
|---|---|---|---|---|---|
| `eventId` | 낮음 | 가능 | 가능 | 금지 또는 제한 | 추적 키, 고카디널리티 주의 |
| `traceId` | 낮음 | 가능 | 가능 | 금지 또는 제한 | 요청 추적, metric label 사용 주의 |
| `userId` | 중간 | 가능 | hash/masking | 금지 | 운영 가정에서는 pseudonymization |
| `accountId` | 높음 | 가능 | masking | 금지 | 마지막 4자리만 |
| `deviceId` | 높음 | 가능 | hash | 금지 | 기기 식별자 |
| `ipAddress` | 높음 | 선택 | masking | 금지 | 마지막 octet masking |
| `location` | 중간 | 국가 단위 | 국가 단위 | 국가 단위만 | 세부 위치 금지 |
| `amount` | 중간 | 가능 | 제한 | bucket만 | 원문 금액 label 금지 |
| `rawPayload` | 높음 | 제한 | 금지 | 금지 | DLQ 주의 |

## 5. Kafka Payload 기준

Kafka payload에는 Consumer가 탐지에 필요한 정보만 포함합니다.

초기 payload에는 `userId`, `accountId`, `deviceId`, `location` 같은 식별자가 포함될 수 있습니다. 운영 환경으로 확장할 경우 다음을 검토합니다.

- accountId tokenization
- deviceId hashing
- userId pseudonymization
- ipAddress masking
- payload encryption 또는 topic-level ACL

## 6. DLQ Payload 위험

DLQ는 실패한 원본 payload가 저장될 수 있으므로 개인정보 저장소처럼 변질될 위험이 있습니다.

기준:

- DLQ payload 원문 노출은 local/admin 범위로 제한
- 운영 조회 화면에는 payload hash와 마스킹 payload를 우선 노출
- 원문 payload 접근은 별도 권한과 감사 로그 필요
- discard와 reprocess 모두 reason 기록 필수
- DLQ retention은 topic retention과 DB retention을 함께 관리
- 재처리에는 내부 저장 payload를 사용하되 로그에는 출력하지 않음

초기에는 DLT로 이동한 이벤트 payload를 그대로 저장하는 구조를 고려할 수 있습니다. 그러나 DLQ는 장애 분석 과정에서 운영자 조회 대상이 되고 보존 기간도 길어질 수 있어 민감정보 노출 위험이 있습니다.

따라서 DLQ에는 `payload_hash`와 masked payload를 저장하고, 재처리 시 필요한 최소 데이터만 내부적으로 사용하도록 설계합니다.

## 7. Redis Key and Value 기준

Redis key에는 synthetic `userId`와 `eventId`만 사용합니다. 실명, 이메일, 전화번호, 카드번호, 계좌번호 등 직접 식별자는 key나 value에 저장하지 않습니다.

Phase 6 Redis Hash에는 sliding window 계산에 필요한 `amount`, `currency`, `eventTime`, `userId`만 저장합니다. Redis는 단기 상태 저장소이므로 TTL을 적용하고, 운영 환경에서는 Redis 접근 권한, 네트워크 격리, key scan 제한을 함께 적용합니다.

현재 local test data의 `userId`는 synthetic identifier입니다. 운영 환경에서 실제 사용자를 식별할 수 있는 값이라면 Redis key와 log 모두 hash 또는 pseudonymization을 적용합니다.

## 8. Local Docker Compose 계정

Docker Compose의 기본 계정은 로컬 개발 전용입니다.

- PostgreSQL `fraud/fraud`
- Grafana `admin/admin`
- Redis no-auth
- Kafka PLAINTEXT listener

운영 환경에서는 기본 계정과 plaintext 설정을 사용하지 않습니다.

## 9. Actuator Endpoint

초기 로컬 환경에서는 `health`, `info`, `prometheus` endpoint만 노출합니다.

운영 환경에서는 다음 기준을 적용합니다.

- public network에 Actuator 직접 노출 금지
- management port 또는 internal network 분리
- 필요한 endpoint만 allowlist
- heapdump, env, beans 같은 민감 endpoint 비활성화
- Prometheus scrape 대상은 내부 네트워크로 제한

## 10. Prometheus와 Grafana

Prometheus와 Grafana는 로컬 검증용으로 기본 설정을 사용합니다.

운영 환경에서는 다음을 적용합니다.

- Grafana 기본 계정 변경
- dashboard 접근 권한 분리
- metric label에 accountId, deviceId, raw userId 등 고카디널리티 민감값 사용 금지
- Prometheus retention과 접근 권한 관리

Phase 7 metric tag에는 `eventId`, `traceId`, `userId`, `accountId`를 포함하지 않습니다. 이 값들은 고유성이 높아 cardinality 폭증을 유발할 수 있으며, 운영 환경에서는 식별자 노출 위험도 있습니다. Metric은 집계용이고, 개별 이벤트 추적은 structured log의 `traceId`/`eventId`로 수행합니다. 운영 환경에서는 `traceId`/`eventId` 로그 접근 권한과 보존 기간도 별도로 통제합니다. Metric은 `rule`, `mode`, `result` 수준의 낮은 cardinality tag만 사용합니다.

## 11. 보안 구현 제외 범위

초기 Phase에서는 OAuth2, API Gateway, Service Discovery, Kubernetes secret 관리를 구현하지 않습니다.

이유는 현재 범위의 핵심이 Kafka Consumer 처리 지연, Retry/DLT, 재처리 안정성, 장애 재현이기 때문입니다.

다만 운영 환경으로 확장할 경우 인증/인가, secret 관리, network policy, audit log는 별도 Phase로 추가합니다.

## 12. Failure Drill 데이터 기준

Phase 8 failure drill script는 운영 데이터가 아닌 synthetic `eventId`, `userId`, `accountId`, `deviceId`만 사용합니다. 장애 drill 실행 중에도 카드번호, 실명, 이메일, 전화번호 등 직접 식별 개인정보를 payload, Redis key, log, metric tag에 포함하지 않습니다.

운영 환경에서 failure drill을 수행할 경우 별도 sandbox 환경과 권한 제한이 필요합니다. DB password, API token, 운영 credential은 script에 하드코딩하지 않고 환경 변수 또는 secret 관리 체계를 사용합니다. Script output에는 민감정보를 출력하지 않습니다.

## 13. 보안 위험과 설계 연결

admin API, Actuator, DLQ payload, 로그 마스킹은 접근통제 실패, 암호화 실패, 보안 로깅/모니터링 실패 같은 일반적인 웹 애플리케이션 보안 위험과 직접 연결됩니다.

이 프로젝트에서는 초기부터 다음을 설계 기준으로 둡니다.

- 관리자 기능은 local-only에서 시작하고 운영 가정에서는 인증/인가를 요구합니다.
- 민감정보 원문은 로그와 metric label에 남기지 않습니다.
- DLQ 조회와 재처리에는 작업자와 사유를 남깁니다.
- 접속 기록과 운영자 조치 이력은 감사 대상으로 취급합니다.

## 14. Phase 9 DLT Payload 기준

DLT payload에는 원본 이벤트 일부가 저장되므로 운영 환경에서는 payload masking, 접근 권한 분리, 보존 기간 정책이 필요합니다. Phase 9에서는 synthetic identifier 기반 이벤트만 사용하며, 카드번호, 실명, 이메일, 전화번호 등 직접 식별자는 저장하지 않습니다.

Phase 9에서는 DLT payload 저장 경로를 별도 sanitizer 메서드로 분리했습니다. 현재 프로젝트는 synthetic identifier만 사용하므로 필드 마스킹은 적용하지 않았지만, `errorMessage`는 500자로 길이를 제한하고 null/blank message는 예외 class 이름으로 대체합니다. Stacktrace 전체는 DLT topic payload나 `dead_letter_events.error_message`에 저장하지 않습니다.

운영 확장 시 카드번호, 계좌번호, 이메일, 전화번호 등 직접 식별자는 DLT 저장 전에 제거하거나 masking/redaction 해야 합니다. DLT topic payload와 `dead_letter_events.payload_json`은 운영자 복구에 필요한 최소 범위로 제한해야 하며, 운영 환경 노출 전 관리자 인증/인가와 감사 로그를 추가해야 합니다.

## 15. Phase 11 Readiness Review 기준

Phase 11 기준으로 보안 관련 구현과 후속 후보를 다음처럼 분리합니다.

현재 문서화/구현된 기준:

- Admin API는 local/development-only로 취급합니다.
- DLT 조회, 재처리, 폐기에는 운영 환경에서 인증/인가가 필요하다고 명시합니다.
- DLT payload 저장 경로는 sanitizer 메서드로 분리되어 있습니다.
- `errorMessage`는 길이를 제한하고 stacktrace 전체를 저장하지 않습니다.
- Redis key/value에는 synthetic identifier만 사용합니다.
- Metric tag에는 `eventId`, `traceId`, `userId`, `accountId`, `deviceId`를 넣지 않습니다.
- Failure drill은 synthetic data만 사용합니다.

후속 운영 보안 고도화 후보:

- Admin API 인증/인가
- 재처리/폐기 audit log와 요청자 기록 강화
- DLT payload masking/redaction 정책의 실제 운영 데이터 적용
- DLQ payload 원문 접근 권한 분리
- Redis 인증, 네트워크 격리, key scan 제한
- Grafana/Prometheus 접근 권한과 retention 정책

## 16. Phase 12 Load Test 데이터 기준

Phase 12 k6 load test는 synthetic `eventId`, `userId`, `accountId`, `deviceId`, `merchantId`만 사용합니다. 부하 테스트 payload에는 카드번호, 실명, 이메일, 전화번호 등 직접 식별 개인정보를 포함하지 않습니다.

테스트 결과와 로그에도 실제 개인정보를 저장하지 않습니다. `load-test/k6/results/`에 생성되는 raw result 파일은 git에 커밋하지 않고, 문서에는 집계 결과와 병목 후보만 기록합니다.

`API_BASE_URL`은 로컬 app-api 기본값인 `http://localhost:8080`을 사용합니다. 운영 환경이나 외부 공유 환경 URL을 대상으로 k6 부하 테스트를 실행하지 않습니다.

Redis down load 이후에는 Redis container 상태를 반드시 확인합니다. Redis 장애를 의도적으로 만드는 테스트는 로컬 Docker Compose 환경에서만 수행합니다.

## 18. Phase 14 Admin API Protection

Phase 14에서는 `/api/v1/admin/**` API에 `X-Admin-Token` 기반 최소 보호를 추가했습니다. 이는 local/dev 환경에서 운영자 API가 완전히 공개되는 것을 막기 위한 장치이며, production-grade 인증/인가는 아닙니다.

기준:

- Admin API 요청은 `X-Admin-Token` header가 필요합니다.
- token 값은 `security.admin.token` 설정 또는 `ADMIN_API_TOKEN` 환경 변수로 주입합니다.
- local 기본값은 개발 편의를 위한 `local-admin-token`이며 운영 환경에서는 기본값 사용을 금지합니다.
- 기본값 `local-admin-token`이 활성화되면 app-api startup warning log를 남깁니다.
- token이 없거나 틀리면 `401 UNAUTHORIZED_ADMIN_API`로 응답합니다.
- 일반 transaction ingest API는 admin token 없이 동작합니다.
- admin token은 log, metric tag, audit payload에 저장하지 않습니다.

운영 환경에서는 JWT/OAuth2, ADMIN role, RBAC, IP allowlist, audit log 조회 권한, gateway/Nginx/API Gateway rate limit을 추가해야 합니다.

## 19. Phase 14 Audit Log 기준

Phase 14에서는 DLT reprocess/discard 성공과 실패를 `admin_audit_logs`에 저장합니다.

저장하는 정보:

- actor: request body의 `operatorId`. Phase 14에서는 인증된 principal이 아니라 local/dev audit 기록을 위한 self-claimed field입니다.
- action: `DLT_REPROCESS`, `DLT_DISCARD`
- target: DLT event id
- request_id: Phase 14에서는 request-id 수집 체계가 없어 비워둡니다.
- trace_id
- result: `SUCCESS`, `FAILED`
- reason: 운영자가 입력한 사유
- metadata_json: eventId, 상태, attempts, maxAttempts, 결과 사유 같은 최소 정보

저장하지 않는 정보:

- `X-Admin-Token`
- 원본 request body 전체
- DLT `payload_json` 전체
- accountId, deviceId 같은 민감 식별자
- stacktrace 전체

인증 실패는 DB audit log에 저장하지 않고 structured log로만 남깁니다. 인증 실패마다 DB write를 수행하면 brute-force 상황에서 DB 부하가 커질 수 있기 때문입니다.

운영 환경에서는 audit actor를 request body 값이 아니라 JWT subject, SSO user id, RBAC principal처럼 인증된 사용자 식별자에서 가져와야 합니다. Gateway 또는 공통 filter에서 request id 표준이 생기면 `request_id`를 별도 저장하고, eventId는 계속 metadata로 유지합니다.

## 20. Rate Limit과 Abuse Prevention 한계

Phase 14의 abuse prevention은 단건 수동 조작 원칙, admin token 필수화, discard/reprocess reason validation, max reprocess attempts 정책에 한정합니다.

별도 in-memory rate limiter는 local single instance에서만 의미가 있고, production에서는 Gateway/Nginx/API Gateway rate limit과 관리자 승인 workflow가 더 적절합니다. 따라서 Phase 14에서는 rate limit을 문서화된 후속 과제로 남깁니다.

## 21. V2 PaySim Identifier Privacy

V2에서 사용할 PaySim은 synthetic dataset이지만 `nameOrig`, `nameDest`는 계좌형 식별자처럼 보입니다. 따라서 raw identifier를 API, log, metric tag, sample data, result evidence에 노출하지 않는 것을 기본 정책으로 둡니다.

전처리 단계에서는 다음처럼 deterministic hash identifier를 생성합니다.

```text
userId = U-{sha256(nameOrig + salt).substring(0, 16)}
accountId = A-{sha256(nameOrig + salt).substring(0, 16)}
destinationAccountId = D-{sha256(nameDest + salt).substring(0, 16)}
```

기준:

- 동일 원본 identifier는 동일 hash로 변환해 사용자별 Redis Sliding Window 계산을 유지합니다.
- sample data에는 raw `nameOrig`, `nameDest`를 포함하지 않습니다.
- salt는 운영 환경에서 secret으로 관리해야 합니다.
- PaySim label은 평가용으로만 사용하고 Rule Engine 입력으로 사용하지 않습니다.

## 17. Phase 13 Load and Failure Test 데이터 기준

Phase 13 k6 load/failure test는 synthetic `eventId`, `userId`, `accountId`, `deviceId`, `merchantId`만 사용합니다. 부하 테스트 payload에는 카드번호, 실명, 이메일, 전화번호 등 직접 식별 개인정보를 포함하지 않습니다.

테스트 결과와 로그에도 실제 개인정보를 저장하지 않습니다. `load-test/k6/results/`에 생성되는 raw result 파일은 git에 커밋하지 않고, 문서에는 집계 결과와 병목 후보만 기록합니다.

`API_BASE_URL`은 로컬 app-api 기본값인 `http://localhost:8080`을 사용합니다. 운영 환경이나 외부 공유 환경 URL을 대상으로 k6 부하 테스트를 실행하지 않습니다.

Redis down load 이후에는 Redis container 상태를 반드시 확인합니다. Redis 장애를 의도적으로 만드는 테스트는 로컬 Docker Compose 환경에서만 수행합니다.
