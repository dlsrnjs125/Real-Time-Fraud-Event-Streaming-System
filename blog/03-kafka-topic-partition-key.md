# Kafka Topic과 Partition Key 설계

> Status: Draft
> 이 글은 구현과 측정 결과가 추가되면서 갱신됩니다.

## 문제

이상거래 탐지는 사용자별 최근 거래 순서에 영향을 받습니다. Kafka는 partition 내부 순서만 보장하므로 key 선택이 중요합니다.

## 초기 설계

`transaction-events`, `fraud-risk-events`, `fraud-alert-events`, `transaction-events.retry`, `transaction-events-dlt`를 사용합니다. 기본 key는 `userId`입니다.

## 구현

topic 생성 스크립트에서 partition 수와 retention 정책을 관리합니다.

Phase 1 실행 검증에서는 topic 생성 스크립트가 문서의 topic 목록과 일치하는지 확인합니다. 로컬 Docker Compose 환경에서는 single broker 기준 replication factor 1을 사용하고, 운영 수준의 multi-broker replication은 설계 문서에만 남깁니다.

Phase 2에서는 `TransactionEventMessage`에 `schemaVersion`, `receivedAt`, `traceId`를 명시합니다. `schemaVersion`은 Consumer의 호환성 판단 기준이고, `receivedAt`은 detection latency 계산 기준이며, `traceId`는 API 요청과 Consumer 로그를 연결하는 추적 키입니다.

Phase 3 Producer는 `transaction-events` topic에 발행할 때 `userId`를 Kafka key로 사용합니다. 이 Phase에서는 Producer key 선택과 message contract를 검증하고, Consumer Lag 측정은 Consumer 구현 이후로 넘깁니다.

## 측정 또는 재현

consumer concurrency 1, 3, 6을 비교하고 partition별 lag을 확인합니다.

## 발견한 문제

작성 예정입니다.

## 변경한 설계

작성 예정입니다.

## 남은 한계

특정 userId에 이벤트가 몰리면 hot partition이 발생할 수 있습니다.
