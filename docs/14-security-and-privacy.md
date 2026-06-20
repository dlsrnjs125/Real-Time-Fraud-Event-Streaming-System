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
| `traceId` | 낮음 | 가능 | 가능 | 가능 | 요청 추적 |
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

## 11. 보안 구현 제외 범위

초기 Phase에서는 OAuth2, API Gateway, Service Discovery, Kubernetes secret 관리를 구현하지 않습니다.

이유는 현재 범위의 핵심이 Kafka Consumer 처리 지연, Retry/DLT, 재처리 안정성, 장애 재현이기 때문입니다.

다만 운영 환경으로 확장할 경우 인증/인가, secret 관리, network policy, audit log는 별도 Phase로 추가합니다.

## 12. 보안 위험과 설계 연결

admin API, Actuator, DLQ payload, 로그 마스킹은 접근통제 실패, 암호화 실패, 보안 로깅/모니터링 실패 같은 일반적인 웹 애플리케이션 보안 위험과 직접 연결됩니다.

이 프로젝트에서는 초기부터 다음을 설계 기준으로 둡니다.

- 관리자 기능은 local-only에서 시작하고 운영 가정에서는 인증/인가를 요구합니다.
- 민감정보 원문은 로그와 metric label에 남기지 않습니다.
- DLQ 조회와 재처리에는 작업자와 사유를 남깁니다.
- 접속 기록과 운영자 조치 이력은 감사 대상으로 취급합니다.
