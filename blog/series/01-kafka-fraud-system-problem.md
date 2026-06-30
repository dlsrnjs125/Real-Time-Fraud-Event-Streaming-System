# Kafka 기반 이상거래 탐지 시스템을 만든 이유

Status: Planned consolidation

Source drafts:

- [01-domain-problem](../drafts/01-domain-problem.md)
- [02-api-consumer-separation](../drafts/02-api-consumer-separation.md)
- [03-kafka-topic-partition-key](../drafts/03-kafka-topic-partition-key.md)

Focus:

- API와 Consumer를 분리한 이유
- Kafka를 이벤트 backbone으로 선택한 이유
- `userId` partition key와 사용자별 순서 보장
