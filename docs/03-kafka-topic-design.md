# Kafka Topic Design

## 1. Topic 목록

| Topic | Producer | Consumer | Key | 목적 |
|---|---|---|---|---|
| `transaction-events` | app-api | app-consumer | userId | 거래 이벤트 원본 |
| `fraud-risk-events` | app-consumer | future consumers | userId | 이상거래 탐지 결과 |
| `fraud-alert-events` | app-consumer | notification worker | userId | 알림 대상 이벤트 |
| `transaction-events.retry` | app-consumer | app-consumer | userId | 일시 실패 재처리 |
| `transaction-events.dlt` | app-consumer | admin reprocessor | userId | 처리 실패 이벤트 보관 |

## 2. Partition Key

기본 partition key는 `userId`로 합니다.

이상거래 탐지는 사용자별 최근 거래 패턴을 기반으로 합니다. 동일 사용자의 이벤트 순서가 중요하기 때문에 `eventId`가 아니라 `userId`를 key로 사용합니다.

## 3. userId key의 장점

- 동일 사용자의 이벤트가 같은 partition으로 들어갑니다.
- 사용자별 거래 순서가 유지됩니다.
- velocity rule, location rule, device rule 계산이 안정적입니다.

## 4. userId key의 단점

- 특정 사용자의 이벤트가 몰리면 hot partition이 발생할 수 있습니다.
- partition별 lag 편차가 커질 수 있습니다.

## 5. 측정 항목

- partition별 message count
- partition별 consumer lag
- userId 집중 부하 시 처리 지연
- hot partition 발생 여부

## 6. Topic Retention Policy

| Topic | Retention | Cleanup | 이유 |
|---|---:|---|---|
| `transaction-events` | 3d | delete | 원본 이벤트 재처리 가능 기간 |
| `fraud-risk-events` | 7d | delete | 후속 분석/알림 재처리 |
| `fraud-alert-events` | 7d | delete | 알림 실패 재처리 |
| `transaction-events.retry` | 1d | delete | 일시 실패 재처리 전용 |
| `transaction-events.dlt` | 14d | delete | 운영자 확인과 수동 재처리 |

초기 retention 값은 로컬 검증과 장애 재현을 위한 기준값입니다. 운영 환경에서는 이벤트 재처리 정책, 개인정보 보관 기준, 저장 비용을 함께 고려해 조정합니다.

## 7. Retry Topic Naming

초기 구현에서는 retry topic을 `transaction-events.retry` 하나로 고정합니다.

추후 retry backoff 구간을 세분화해야 할 경우 Spring Kafka `RetryTopicConfiguration`의 naming strategy를 사용합니다. 문서와 실제 topic 이름이 달라지지 않도록 topic 이름은 `KafkaTopicNames` 상수와 topic 생성 스크립트에서 함께 관리합니다.

## 8. 초기 Partition 수

초기 partition 수는 6개로 설정합니다.

로컬 환경에서 consumer concurrency 1, 2, 3, 6을 비교하기 위한 기준값입니다. partition 수가 너무 적으면 consumer scale out 실험이 어렵고, 너무 많으면 로컬 리소스 대비 관리 비용이 커집니다.

consumer concurrency는 1에서 3, 6으로 변경하며 Consumer Lag 회복 시간을 비교합니다. partition 수보다 큰 concurrency는 처리량 증가가 제한될 수 있으므로 초기 측정 대상에서 제외합니다.

## 9. Local Kafka Listener 설정

현재 Kafka advertised listener는 host에서 Spring Boot 애플리케이션을 실행하는 로컬 개발 흐름에 맞춰 `localhost:9092`로 둡니다.

추후 `app-api`와 `app-consumer`를 Docker Compose 서비스로 포함할 경우 내부 listener(`kafka:9092`)와 외부 listener(`localhost:9092`)를 분리합니다.

## 10. Schema Version

Kafka는 이벤트가 topic에 남기 때문에 스키마 변경 기준이 필요합니다.

초기 schemaVersion은 `v1`로 둡니다. Consumer는 지원하지 않는 `schemaVersion`을 처리하지 않고 DLT로 보냅니다.
