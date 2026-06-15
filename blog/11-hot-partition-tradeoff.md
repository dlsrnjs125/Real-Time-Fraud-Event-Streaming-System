# Partition Hot Spot과 userId key의 트레이드오프

> Status: Draft
> 이 글은 구현과 측정 결과가 추가되면서 갱신됩니다.

## 문제

`userId` key는 사용자별 순서 보장에 유리하지만 특정 사용자 이벤트가 몰리면 hot partition이 생길 수 있습니다.

## 초기 설계

초기 key는 `userId`로 유지하고 partition별 lag과 message count를 측정합니다.

## 구현

hot partition 테스트 시나리오를 k6 또는 별도 producer로 재현합니다.

## 측정 또는 재현

특정 userId에 이벤트를 집중시켜 partition별 lag 편차를 확인합니다.

## 발견한 문제

작성 예정입니다.

## 변경한 설계

작성 예정입니다.

## 남은 한계

key 전략 변경은 사용자별 순서 보장과 처리량 사이의 trade-off를 다시 검증해야 합니다.
