# Blog Draft Series

이 폴더는 실시간 이상거래 탐지 시스템 개발 과정을 기술 기록 형태로 정리하기 위한 초안 공간입니다.

글은 홍보 문구보다 문제, 설계, 구현, 측정, 변경 기록을 중심으로 작성합니다.

> Status: Draft
> 이 시리즈는 구현과 측정 결과가 추가되면서 갱신됩니다.

## 시리즈

1. [실시간 이상거래 탐지 시스템의 문제 정의](01-domain-problem.md)
2. [Spring Boot API와 Kafka Consumer를 분리한 이유](02-api-consumer-separation.md)
3. [Kafka Topic과 Partition Key 설계](03-kafka-topic-partition-key.md)
4. [거래 이벤트 스키마와 PostgreSQL 감사 모델 설계](04-event-schema-audit-model.md)
5. [Consumer Offset Commit과 재처리 전략](05-offset-commit-reprocessing.md)
6. [Redis ZSET 기반 Sliding Window 탐지](06-redis-sliding-window.md)
7. [Retry/DLT와 운영자 재처리 API 설계](07-retry-dlt-reprocessing-api.md)
8. [Consumer Lag과 Detection Latency 관측](08-lag-detection-latency.md)
9. [k6로 대량 거래 이벤트 부하 재현](09-k6-load-test.md)
10. [Consumer 장애와 Redis 장애 실험](10-consumer-redis-failure.md)
11. [Partition Hot Spot과 userId key의 트레이드오프](11-hot-partition-tradeoff.md)
12. [최종 결과와 설계 변경 기록](12-result-and-design-changes.md)
13. [DLT 재처리 이후, 운영 가능한 이상거래 탐지 시스템으로 마무리하기](13-phase-10-final-readiness.md)
14. [실시간 이상거래 탐지 시스템 최종 운영 준비도 점검](14-final-readiness-review.md)
15. [k6 부하 테스트로 이상거래 탐지 시스템의 한계를 측정하기](15-load-test-evidence-and-performance-review.md)
16. [Phase 13 부하/장애 테스트 evidence 정리](16-load-test-evidence-and-performance-review.md)
17. [운영자 API 보호와 Audit Log로 재처리 기능을 안전하게 만들기](17-operational-security-and-audit-log.md)
18. [V2 PaySim 데이터 출처와 원본 데이터 보호 기준 세우기](18-v2-paysim-data-provenance-and-raw-data-protection.md)
19. [V2 PaySim CSV를 replay 가능한 runtime event로 정규화하기](19-v2-paysim-preprocessing-normalization.md)
20. [V2 PaySim 데이터 Python Toolchain 분리하기](20-v2-data-python-toolchain-bootstrap.md)
21. [V2 PaySim validation, rejected analysis, safe sampling](21-v2-paysim-validation-rejected-sampling.md)
22. [V2 PaySim identifier hash policy](22-v2-paysim-identifier-hash-policy.md)
23. [V2 PaySim replay pipeline](23-v2-paysim-replay-pipeline.md)
24. [V2 PaySim replay evaluation baseline](24-v2-paysim-replay-evaluation-baseline.md)
25. [V2 Phase 7 - PaySim Replay Evaluation을 운영 Evidence로 바꾸기](25-v2-paysim-replay-evaluation-evidence.md)
26. [V2 Phase 8 - PaySim Native Type Replay Contract 정리](26-v2-paysim-native-type-replay-contract.md)
27. [V2 Phase 9 - Threshold를 올리거나 낮추기 전에 Regression Evidence부터 만들기](27-v2-rule-threshold-regression-evidence.md)
28. [V2 Phase 10 - README를 줄이고 Evidence를 정리한 이유](28-v2-final-readiness-and-readme-slimdown.md)
29. [V2 Phase 11 - Rule Version Drift를 Evidence Gate로 막기](29-v2-rule-version-integration-evidence.md)
30. [V2 Phase 12 - report-level ruleVersion에서 per-result ruleVersion으로](30-v2-result-rule-version-propagation-evidence.md)
31. [V2 Phase 13 - ruleVersion을 운영자가 확인할 수 있게 만든 이유](31-v2-rule-version-observability-evidence.md)
32. [V2 Phase 14 - ruleVersion 변경 전에 확인해야 할 것들](32-v2-rule-version-change-runbook.md)
33. [V2 Phase 15 - 기능을 더 만들지 않고 evidence를 닫은 이유](33-v2-final-portfolio-summary.md)

## 글 구조

각 글은 아래 흐름을 기본으로 합니다.

- 문제
- 초기 설계
- 구현
- 측정 또는 재현
- 발견한 문제
- 변경한 설계
- 남은 한계
