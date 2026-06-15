# k6로 대량 거래 이벤트 부하 재현

> Status: Draft
> 이 글은 구현과 측정 결과가 추가되면서 갱신됩니다.

## 문제

설계가 맞는지 확인하려면 대량 거래 이벤트 유입 상황을 재현해야 합니다.

## 초기 설계

k6로 normal-load, peak-load, consumer-lag-test, redis-down-test를 작성합니다.

## 구현

`load-test/k6` 아래에 시나리오별 스크립트를 둡니다.

## 측정 또는 재현

API p50/p95/p99, publish success rate, Consumer Lag 최대값을 기록합니다.

## 발견한 문제

작성 예정입니다.

## 변경한 설계

작성 예정입니다.

## 남은 한계

초기 스크립트는 API 구현 전까지 404도 허용하는 smoke 성격입니다.
