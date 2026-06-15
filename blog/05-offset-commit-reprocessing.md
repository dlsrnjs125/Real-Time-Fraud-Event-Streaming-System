# Consumer Offset Commit과 재처리 전략

> Status: Draft
> 이 글은 구현과 측정 결과가 추가되면서 갱신됩니다.

## 문제

처리 완료 전에 offset을 commit하면 Consumer 장애 시 처리되지 않은 이벤트가 처리된 것처럼 보일 수 있습니다.

## 초기 설계

`enable-auto-commit=false`를 사용하고, 저장과 후속 발행이 끝난 뒤 manual ack를 수행합니다.

## 구현

Consumer 처리 로그와 `eventId` unique constraint로 중복 처리를 방어합니다.

## 측정 또는 재현

Consumer 중단 후 재시작해 미처리 이벤트가 다시 소비되는지 확인합니다.

## 발견한 문제

작성 예정입니다.

## 변경한 설계

작성 예정입니다.

## 남은 한계

Kafka 전달 보장은 비즈니스 exactly-once와 다르므로 DB 제약과 idempotency가 필요합니다.
