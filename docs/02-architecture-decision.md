# Architecture Decision

## 1. 선택한 구조

Spring Boot 기반 API Server와 Kafka Consumer Worker를 분리합니다. 내부 코드는 `app-api`, `app-consumer`, `app-common`으로 나누고, 인프라는 Docker Compose로 구성합니다.

## 2. 선택한 이유

API Server와 Fraud Consumer는 처리 특성이 다릅니다.

API Server는 거래 이벤트를 검증하고 Kafka에 발행한 뒤 빠르게 응답해야 합니다. Fraud Consumer는 Kafka 이벤트를 소비하고, Redis 조회, Rule Engine 실행, PostgreSQL 저장, 후속 이벤트 발행을 수행합니다.

두 처리를 동기 흐름으로 묶으면 이상거래 탐지 지연이 API 응답 지연으로 전파됩니다. 따라서 API와 Consumer를 실행 단위로 분리합니다.

## 3. 고려한 대안

### 3.1 단일 Spring Boot 애플리케이션

장점:

- 구현이 단순합니다.
- 로컬 실행이 쉽습니다.
- 트랜잭션 관리가 단순합니다.

단점:

- API 지연과 Consumer 지연을 분리해 관측하기 어렵습니다.
- Consumer 장애가 전체 애플리케이션에 영향을 줄 수 있습니다.
- Consumer만 독립 확장하기 어렵습니다.

### 3.2 완전한 MSA

장점:

- 서비스별 독립 배포가 가능합니다.
- 서비스별 장애 격리가 가능합니다.
- 도메인 소유권이 명확합니다.

단점:

- API Gateway, 인증, 서비스 간 통신, 데이터 분리 관리가 필요합니다.
- 분산 트랜잭션 문제가 발생합니다.
- 현재 범위에서는 Kafka 이벤트 처리보다 서비스 분리 자체가 중심이 될 수 있습니다.

## 4. 최종 결정

완전한 MSA가 아니라 API Server와 Consumer Worker를 실행 단위로 분리합니다. DB는 하나의 PostgreSQL을 사용하되, 테이블 소유권을 `transaction`, `fraud`, `audit`, `dlq` 영역으로 구분합니다.

## 5. 장점

- API latency와 Consumer processing latency를 분리해 측정할 수 있습니다.
- Consumer 장애가 API 접수 기능에 직접 전파되지 않습니다.
- Consumer만 독립적으로 scale out할 수 있습니다.
- Kafka Lag, Retry, DLT, 재처리 흐름을 검증할 수 있습니다.

## 6. 단점

- 이벤트 스키마 호환성 관리가 필요합니다.
- 공통 모듈 변경 시 app-api와 app-consumer 모두 영향을 받을 수 있습니다.
- DB를 공유하므로 완전한 서비스 독립성은 없습니다.

## 7. 향후 확장

1. `notification-service`
2. `fraud-detection-service`
3. `admin-service`
4. `auth-service`

## 8. 패키지 경계

`app-api`는 Controller -> Service -> Producer 흐름을 기준으로 패키지를 나눕니다.

- `transaction`: 거래 이벤트 접수 API
- `admin`: DLQ, 탐지 결과 운영 조회 API
- `kafka`: Kafka producer 설정과 발행 어댑터
- `support.exception`: API 예외 처리
- `support.logging`: traceId, eventId 로깅 지원

`app-consumer`는 Listener -> Application -> Rule/Repository 흐름을 기준으로 패키지를 나눕니다.

- `kafka`: Kafka listener, consumer 설정, error handler
- `application`: 탐지 use case와 결과 저장 orchestration
- `rule`: FraudRule, AmountRule, VelocityRule, NewDeviceRule
- `redis`: 사용자별 최근 거래 상태 저장소
- `persistence`: FraudResult, EventProcessingLog, DLQ entity
- `metrics`: Consumer 처리 지연과 탐지 지표
- `support.exception`: Consumer 예외 처리
- `support.logging`: topic, partition, offset 로깅 지원
