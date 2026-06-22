# Kafka Topic Design

## 1. Kafka 선택 이유

Kafka를 선택한 이유는 다음과 같습니다.

1. 거래 이벤트가 지속적으로 대량 유입됩니다.
2. 탐지, 저장, 알림, 통계 처리를 서로 분리해야 합니다.
3. Consumer 장애 시에도 이벤트를 재처리할 수 있어야 합니다.
4. Consumer Lag을 통해 실시간 처리 지연을 관측할 수 있어야 합니다.
5. 특정 사용자/계좌 기준 순서 보장이 필요합니다.

## 2. Kafka를 이벤트 로그로 보는 이유

이 시스템에서 Kafka는 단순 메시지 큐가 아니라 거래 이벤트 로그의 역할을 합니다.

API Server는 거래 이벤트를 `transaction-events` topic에 append하고, Fraud Consumer, Audit Consumer, Alert Consumer, Analytics Consumer는 같은 이벤트 로그를 각자의 목적에 맞게 독립적으로 소비할 수 있습니다.

이 구조에서는 Consumer 하나가 실패해도 이벤트 로그가 남아 있으므로, 장애 복구 후 마지막 commit offset 이후부터 다시 처리할 수 있습니다.

## 3. Topic 목록

| Topic | Producer | Consumer | Key | 목적 |
|---|---|---|---|---|
| `transaction-events` | app-api | app-consumer | userId | 거래 이벤트 원본 |
| `fraud-risk-events` | app-consumer | future consumers | userId | 이상거래 탐지 결과 |
| `fraud-alert-events` | app-consumer | notification worker | userId | 알림 대상 이벤트 |
| `transaction-events.retry` | app-consumer | app-consumer | userId | 일시 실패 재처리 |
| `transaction-events-dlt` | app-consumer | app-api admin flow | eventId | Phase 9 DLT envelope 보관과 수동 재처리 기준 |

## 4. Topic 분리 기준

- 원본 이벤트와 탐지 결과 이벤트를 분리합니다.
- 탐지 결과와 알림 대상 이벤트를 분리합니다.
- 일시 실패 재처리와 최종 실패 보관을 분리합니다.
- 후속 Consumer가 원본 이벤트 로그를 독립적으로 소비할 수 있게 합니다.

## 5. Consumer Group 분리 기준

Consumer group은 처리 목적별로 분리합니다.

- Fraud Consumer Group: 이상거래 탐지
- Audit Consumer Group: 감사 로그 저장
- Alert Consumer Group: 알림 대상 처리
- Analytics Consumer Group: 통계/분석

같은 이벤트 로그를 공유하더라도 Consumer group이 다르면 offset이 독립적으로 관리됩니다.

## 6. Partition Key Decision

### 선택: `userId`

기본 partition key는 `userId`로 합니다.

이상거래 탐지는 사용자별 최근 거래 패턴을 기반으로 합니다. Kafka는 partition 내부 순서만 보장하므로, 동일 사용자의 이벤트 순서가 중요하면 같은 사용자의 이벤트가 같은 partition으로 들어가야 합니다.

### 대안 비교

| Key | 장점 | 단점 | 선택 여부 |
|---|---|---|---|
| `eventId` | 이벤트가 비교적 균등 분산됨 | 같은 사용자의 이벤트가 여러 partition에 흩어져 순서 보장이 어려움 | 선택하지 않음 |
| `userId` | 사용자별 이벤트가 같은 partition에 들어가 velocity/location/device rule 계산에 유리함 | 특정 사용자 이벤트가 몰리면 hot partition 가능 | 선택 |

### 선택 이유

- 동일 사용자의 이벤트가 같은 partition으로 들어갑니다.
- 사용자별 거래 순서가 유지됩니다.
- velocity rule, location rule, device rule 계산이 안정적입니다.

### 남은 리스크: Hot Partition

- 특정 사용자의 이벤트가 몰리면 hot partition이 발생할 수 있습니다.
- partition별 lag 편차가 커질 수 있습니다.

### 측정 지표

- partition별 message count
- partition별 consumer lag
- userId 집중 부하 시 처리 지연
- hot partition 발생 여부

## 7. Offset Commit 정책

Consumer는 이벤트를 읽은 직후가 아니라 처리 성공 후 offset을 commit합니다.

처리 완료 기준:

- FraudResult 저장 성공
- EventProcessingLog 저장 성공
- 필요한 후속 이벤트 발행 성공
- 그 이후 manual ack 수행

처리 완료 전 commit하면 Consumer 장애 시 이벤트가 처리되지 않았는데도 처리된 것으로 보일 수 있습니다.

## 8. Retry/DLT 흐름

```text
transaction-events
-> app-consumer
-> 일시 실패: transaction-events.retry
-> 처리 불가: transaction-events-dlt
-> 운영자 확인
-> 재처리 가능: transaction-events 재발행
-> 재처리 불가: DISCARDED 또는 FAILED_PERMANENT
```

DLT 재처리 시 원본 `eventId`를 유지하고, `eventId` unique constraint로 중복 FraudResult 생성을 방어합니다.

Phase 9의 구현 topic은 `transaction-events-dlt`입니다. DLT record key는 `eventId`를 사용합니다. 정상 거래 처리의 partition key는 사용자별 순서를 위해 `userId`이지만, DLT 이벤트는 운영자가 원본 이벤트 단위로 조회/재처리하므로 `eventId`가 더 직접적인 식별자입니다.

DLT value는 다음 정보를 담은 envelope JSON입니다.

- `eventId`, `traceId`, `userId`
- `sourceTopic`, `sourcePartition`, `sourceOffset`
- `failureStage`, `errorType`, `errorMessage`
- 원본 `TransactionEventMessage` payload
- `failedAt`

`errorMessage`에는 stacktrace 전체를 넣지 않습니다. payload에는 현재 synthetic identifier만 사용하지만, 운영 환경 확장 시 payload masking과 보존 기간 정책이 필요합니다.

## 9. Topic Retention Policy

| Topic | Retention | Cleanup | 이유 |
|---|---:|---|---|
| `transaction-events` | 3d | delete | 원본 이벤트 재처리 가능 기간 |
| `fraud-risk-events` | 7d | delete | 후속 분석/알림 재처리 |
| `fraud-alert-events` | 7d | delete | 알림 실패 재처리 |
| `transaction-events.retry` | 1d | delete | 일시 실패 재처리 전용 |
| `transaction-events-dlt` | 14d | delete | Phase 9 DLT envelope와 수동 재처리 |

초기 retention 값은 로컬 검증과 장애 재현을 위한 기준값입니다. 운영 환경에서는 이벤트 재처리 정책, 개인정보 보관 기준, 저장 비용을 함께 고려해 조정합니다.

## 10. Retry Topic Naming

초기 구현에서는 retry topic을 `transaction-events.retry` 하나로 고정합니다.

추후 retry backoff 구간을 세분화해야 할 경우 Spring Kafka `RetryTopicConfiguration`의 naming strategy를 사용합니다. 문서와 실제 topic 이름이 달라지지 않도록 topic 이름은 `KafkaTopicNames` 상수와 topic 생성 스크립트에서 함께 관리합니다.

## 11. 초기 Partition 수

초기 partition 수는 6개로 설정합니다.

로컬 환경에서 consumer concurrency 1, 2, 3, 6을 비교하기 위한 기준값입니다. partition 수가 너무 적으면 consumer scale out 실험이 어렵고, 너무 많으면 로컬 리소스 대비 관리 비용이 커집니다.

consumer concurrency는 1에서 3, 6으로 변경하며 Consumer Lag 회복 시간을 비교합니다. partition 수보다 큰 concurrency는 처리량 증가가 제한될 수 있으므로 초기 측정 대상에서 제외합니다.

## 12. Consumer Lag을 핵심 지표로 보는 이유

API가 빠르게 응답하더라도 Consumer Lag이 계속 증가하면 이상거래 탐지가 늦어집니다.

따라서 이 시스템은 API p95 latency뿐 아니라 Consumer Lag, detection latency, DLQ count를 함께 봅니다. Consumer Lag은 비동기 탐지 파이프라인의 실제 병목을 드러내는 핵심 지표입니다.

## 13. Local Kafka Listener 설정

현재 Kafka advertised listener는 host에서 Spring Boot 애플리케이션을 실행하는 로컬 개발 흐름에 맞춰 `localhost:9092`로 둡니다.

추후 `app-api`와 `app-consumer`를 Docker Compose 서비스로 포함할 경우 내부 listener(`kafka:9092`)와 외부 listener(`localhost:9092`)를 분리합니다.

## 14. Schema Version

Kafka는 이벤트가 topic에 남기 때문에 스키마 변경 기준이 필요합니다.

초기 schemaVersion은 `v1`로 둡니다. Consumer는 지원하지 않는 `schemaVersion`을 처리하지 않고 DLT로 보냅니다.
