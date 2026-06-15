# 거래 이벤트 스키마와 PostgreSQL 감사 모델 설계

## 문제

Kafka 이벤트는 재처리와 후속 Consumer 확장의 기준이고, PostgreSQL은 조회와 감사의 기준입니다.

## 초기 설계

거래 이벤트에는 `schemaVersion`, `eventId`, `userId`, `eventTime`, `receivedAt`, `traceId`를 포함합니다.

## 구현

`app-common`에 공통 이벤트 record를 둡니다. PostgreSQL에는 탐지 결과, 처리 로그, DLQ 메타데이터를 저장합니다.

## 측정 또는 재현

`eventTime`, `receivedAt`, `detectedAt`으로 ingest delay, detection latency, end-to-end latency를 계산합니다.

## 발견한 문제

작성 예정입니다.

## 변경한 설계

작성 예정입니다.

## 남은 한계

초기 구현에서는 Outbox Pattern을 적용하지 않습니다.
