# Redis ZSET 기반 Sliding Window 탐지

> Status: Draft
> 이 글은 구현과 측정 결과가 추가되면서 갱신됩니다.

## 문제

사용자별 최근 N초 거래 횟수를 정확히 계산하려면 고정 윈도우보다 sliding window가 적합합니다.

## 초기 설계

Redis ZSET에 `eventTime`을 score로 저장하고 window 밖 이벤트를 제거합니다.

## 구현

`UserVelocityRepository`에서 사용자별 velocity window를 관리합니다.

## 측정 또는 재현

Redis command latency와 velocity rule 탐지 결과를 확인합니다.

## 발견한 문제

작성 예정입니다.

## 변경한 설계

작성 예정입니다.

## 남은 한계

Redis는 단기 상태 저장소이며 최종 정합성 기준으로 사용하지 않습니다.
