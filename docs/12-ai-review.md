# AI Review

## 1. 검토 목적

설계와 구현 과정에서 다음 항목을 반복 검토합니다.

- 핵심 문제가 코드와 문서에 일관되게 반영되었는가
- API와 Consumer의 책임 경계가 흐려지지 않았는가
- Kafka key, offset commit, DLQ, 재처리 정책이 테스트 가능한 형태인가
- Redis 장애 시 degraded mode가 명확히 드러나는가
- 관측 지표가 실제 장애 상황을 설명할 수 있는가

## 2. 초기 검토 항목

- 완전한 MSA로 과도하게 확장하지 않습니다.
- API Gateway, Service Discovery, OAuth2는 초기 범위에 넣지 않습니다.
- 거래 원장이나 승인 시스템은 구현 범위에서 제외합니다.
- README에는 구현 상태와 설계 의도를 분리해 기록합니다.
- 설계 변경은 `docs/11-troubleshooting-log.md`에 누적합니다.

## 3. 리뷰 질문

1. `userId` partition key로 사용자별 순서가 충분히 보장되는가?
2. hot partition이 발생했을 때 어떤 지표로 설명할 수 있는가?
3. 처리 성공 전 offset commit을 막고 있는가?
4. 중복 이벤트가 들어와도 FraudResult가 중복 생성되지 않는가?
5. Redis 장애가 원본 이벤트 유실로 이어지지 않는가?
6. DLQ 이벤트를 운영자가 안전하게 재처리할 수 있는가?
7. API latency와 detection latency를 분리해서 측정할 수 있는가?
