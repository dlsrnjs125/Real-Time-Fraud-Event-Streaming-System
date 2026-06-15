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
