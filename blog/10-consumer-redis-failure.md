# Consumer 장애와 Redis 장애 실험

> Status: Draft
> 이 글은 구현과 측정 결과가 추가되면서 갱신됩니다.

## 문제

Consumer나 Redis 장애가 발생해도 이벤트 유실과 조용한 탐지 누락이 없어야 합니다.

## 초기 설계

Consumer 장애는 Kafka Lag 증가와 재소비로 확인합니다. Redis 장애는 degraded mode와 skipped rule로 기록합니다.

## 구현

장애 재현 명령과 기대 결과를 failure scenario 문서에 남깁니다.

## 측정 또는 재현

Consumer stop/start, Redis down/up 상황에서 lag 회복 시간과 degraded count를 측정합니다.

## 발견한 문제

작성 예정입니다.

## 변경한 설계

작성 예정입니다.

## 남은 한계

Redis 장애 중 일부 rule 정확도는 낮아질 수 있습니다.
