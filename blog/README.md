# Blog Drafts

이 폴더는 실시간 이상거래 탐지 시스템 개발 과정을 기술 기록 형태로 정리하기 위한 초안 공간입니다.

글은 홍보 문구보다 문제, 설계, 구현, 측정, 변경 기록을 중심으로 작성합니다.

> Status: Draft
> 현재 파일들은 내부 draft입니다. 다음 단계에서 본문과 이미지를 정리할 때, 아래 발행 후보 흐름으로 압축합니다.

## Recommended Published Series

| No. | Topic | Draft Sources | Main Question |
|---:|---|---|---|
| 1 | Kafka 기반 이상거래 탐지 시스템을 만든 이유 | `01`, `02`, `03` | API와 Consumer를 왜 분리했는가 |
| 2 | 이벤트 스키마와 감사 저장 모델 | `04-event-schema-audit-model.md`, `04-consumer-manual-ack-processing-log.md` | `eventId`, `traceId`, `userId` partition key를 어떻게 잡았는가 |
| 3 | Consumer manual ack와 재처리 가능성 | `05-offset-commit-reprocessing.md` | offset commit 시점을 어디에 둘 것인가 |
| 4 | Redis sliding window와 degraded mode | `06`, `07`, `10` | Redis 장애를 탐지 실패로 볼 것인가, degraded 결과로 남길 것인가 |
| 5 | DLT 재처리 API와 운영자 조작 보호 | `07-retry-dlt-reprocessing-api.md`, `17` | 재처리/폐기를 어떻게 감사 가능하게 만들 것인가 |
| 6 | Consumer Lag과 Detection Latency 관측 | `08-lag-detection-latency.md` | API latency와 detection latency를 왜 분리했는가 |
| 7 | k6 부하/장애 테스트로 한계 측정 | `09`, `15`, `16` | peak, duplicate, Redis down에서 어떤 지표를 볼 것인가 |
| 8 | PaySim 데이터를 replay 가능한 이벤트로 바꾸기 | `18` through `23` | raw data를 커밋하지 않으면서 재현성을 어떻게 남길 것인가 |
| 9 | PaySim replay evaluation을 evidence로 만들기 | `24` through `28` | precision/recall을 과장하지 않으려면 무엇을 기록해야 하는가 |
| 10 | ruleVersion 추적성 설계 | `29` through `31` | Java/Python drift와 active/stored version 혼동을 어떻게 막을 것인가 |
| 11 | ruleVersion 변경 runbook과 evidence closure | `32`, `33` | hold/rollback readiness와 automatic rollback을 어떻게 구분할 것인가 |

## Draft Archive

The current numbered files remain as source material for the next blog cleanup step. They are not the final publication order.

Core drafts: `01` through `17`

V2 PaySim and ruleVersion drafts: `18` through `33`

Historical duplicate-topic drafts kept for now:

- `04-consumer-manual-ack-processing-log.md`
- `05-fraud-result-rule-engine.md`
- `07-redis-integration-test-and-metrics.md`
- `08-failure-drill-consumer-recovery.md`
- `09-retry-dlt-reprocessing.md`

## 글 구조

각 글은 아래 흐름을 기본으로 합니다.

- 문제
- 초기 설계
- 구현
- 측정 또는 재현
- 발견한 문제
- 변경한 설계
- 남은 한계

## Image Planning

이미지와 다이어그램은 다음 단계에서 별도로 정리합니다. 현재 단계에서는 본문 재작성이나 이미지 생성을 진행하지 않습니다.
