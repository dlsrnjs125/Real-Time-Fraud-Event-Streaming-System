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
- `accountId`: 원문 전체 기록 금지
- `deviceId`: 원문 전체 기록 금지
- `ipAddress`: 원문 전체 기록 금지
- `location`: 필요한 최소 범위만 기록

로그에는 Kafka payload 전체를 그대로 남기지 않습니다.

## 4. Kafka Payload 기준

Kafka payload에는 Consumer가 탐지에 필요한 정보만 포함합니다.

초기 payload에는 `userId`, `accountId`, `deviceId`, `location` 같은 식별자가 포함될 수 있습니다. 운영 환경으로 확장할 경우 다음을 검토합니다.

- accountId tokenization
- deviceId hashing
- userId pseudonymization
- ipAddress masking
- payload encryption 또는 topic-level ACL

## 5. DLQ Payload 위험

DLQ는 실패한 원본 payload가 저장될 수 있으므로 개인정보 저장소처럼 변질될 위험이 있습니다.

기준:

- DLQ payload 원문 노출은 local/admin 범위로 제한
- 운영 조회 화면에는 payload hash와 마스킹 payload를 우선 노출
- 원문 payload 접근은 별도 권한과 감사 로그 필요
- discard와 reprocess 모두 reason 기록 필수
- DLQ retention은 topic retention과 DB retention을 함께 관리

## 6. Local Docker Compose 계정

Docker Compose의 기본 계정은 로컬 개발 전용입니다.

- PostgreSQL `fraud/fraud`
- Grafana `admin/admin`
- Redis no-auth
- Kafka PLAINTEXT listener

운영 환경에서는 기본 계정과 plaintext 설정을 사용하지 않습니다.

## 7. Actuator Endpoint

초기 로컬 환경에서는 `health`, `info`, `prometheus` endpoint만 노출합니다.

운영 환경에서는 다음 기준을 적용합니다.

- public network에 Actuator 직접 노출 금지
- management port 또는 internal network 분리
- 필요한 endpoint만 allowlist
- heapdump, env, beans 같은 민감 endpoint 비활성화

## 8. Prometheus와 Grafana

Prometheus와 Grafana는 로컬 검증용으로 기본 설정을 사용합니다.

운영 환경에서는 다음을 적용합니다.

- Grafana 기본 계정 변경
- dashboard 접근 권한 분리
- metric label에 accountId, deviceId, raw userId 등 고카디널리티 민감값 사용 금지
- Prometheus retention과 접근 권한 관리

## 9. 보안 구현 제외 범위

초기 Phase에서는 OAuth2, API Gateway, Service Discovery, Kubernetes secret 관리를 구현하지 않습니다.

이유는 현재 범위의 핵심이 Kafka Consumer 처리 지연, Retry/DLT, 재처리 안정성, 장애 재현이기 때문입니다.

다만 운영 환경으로 확장할 경우 인증/인가, secret 관리, network policy, audit log는 별도 Phase로 추가합니다.
