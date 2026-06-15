# Kafka Topic과 Partition Key 설계

## 문제

이상거래 탐지는 사용자별 최근 거래 순서에 영향을 받습니다. Kafka는 partition 내부 순서만 보장하므로 key 선택이 중요합니다.

## 초기 설계

`transaction-events`, `fraud-risk-events`, `fraud-alert-events`, `transaction-events.retry`, `transaction-events.dlt`를 사용합니다. 기본 key는 `userId`입니다.

## 구현

topic 생성 스크립트에서 partition 수와 retention 정책을 관리합니다.

## 측정 또는 재현

consumer concurrency 1, 3, 6을 비교하고 partition별 lag을 확인합니다.

## 발견한 문제

작성 예정입니다.

## 변경한 설계

작성 예정입니다.

## 남은 한계

특정 userId에 이벤트가 몰리면 hot partition이 발생할 수 있습니다.
