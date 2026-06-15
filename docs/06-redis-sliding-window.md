# Redis Sliding Window

## 1. Redis 역할

Redis는 사용자별 최근 거래 패턴을 빠르게 계산하기 위한 단기 상태 저장소입니다. 최종 탐지 결과나 감사 기준 데이터는 PostgreSQL에 저장합니다.

## 2. Sliding Window 방식

사용자별 ZSET을 사용합니다.

- Key: `fraud:velocity:{userId}`
- Score: `eventTime` epoch milliseconds
- Value: `eventId`

처리 흐름:

1. 현재 거래 이벤트를 ZSET에 추가합니다.
2. window 범위 밖의 오래된 이벤트를 제거합니다.
3. window 내부 이벤트 수를 계산합니다.
4. 기준 횟수를 초과하면 velocity rule을 매칭합니다.

## 3. INCR + TTL을 기본으로 쓰지 않는 이유

INCR + TTL 방식은 구현이 단순하지만 고정 윈도우 경계에서 탐지 정확도가 흔들릴 수 있습니다. ZSET 기반 sliding window는 최근 N초 기준을 더 정확하게 계산할 수 있습니다.

## 4. Redis 장애 모드

### NORMAL

- Redis 사용 가능
- 모든 rule 수행

### DEGRADED

- Redis 사용 불가
- amount rule 등 단건 기반 rule만 수행
- Redis 기반 rule은 `SKIPPED`로 기록
- `fraud_result.degraded=true` 저장

## 5. 측정 항목

- Redis command latency
- Redis 장애 중 처리된 이벤트 수
- `degraded=true` 탐지 결과 수
- Redis 기반 rule skipped count

## 6. TTL and Cleanup

- 각 이벤트 처리 시 window 범위 밖 데이터를 제거합니다.
- ZSET key에는 window보다 긴 TTL을 둡니다.
- 예: window = 60초, key TTL = 10분
- 오래된 userId의 key가 무기한 남지 않도록 합니다.

## 7. Clock Skew 기준

`eventTime`이 `receivedAt`보다 과도하게 미래인 경우 Redis window 계산이 왜곡될 수 있습니다.

허용 가능한 clock skew를 초과하면 validation failure 또는 DLT 대상으로 분류합니다. 이 기준은 `docs/10-failure-scenarios.md`의 Future eventTime 시나리오와 함께 검증합니다.
