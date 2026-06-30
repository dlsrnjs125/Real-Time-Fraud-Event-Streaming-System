# Blog Image Plan

이미지는 모든 글에 넣지 않습니다. 문제 재현, 성능 측정, 운영 evidence를 설명하는 데 필요한 경우만 사용합니다.

| Series | Image | Source | Purpose | Required |
|---|---|---|---|---|
| 1 | 전체 아키텍처 다이어그램 | Mermaid or draw.io | API, Kafka, Consumer, Redis, PostgreSQL 흐름 설명 | Yes |
| 3 | Consumer processing sequence | Mermaid | manual ack와 processing log 순서 설명 | Yes |
| 4 | Redis degraded mode evidence | Grafana or log screenshot | Redis 장애 시 degraded 처리 설명 | Yes |
| 5 | DLT reprocess/discard flow | sequence diagram or API response | 운영자 조작과 audit log 설명 | Yes |
| 6 | Consumer Lag / detection latency dashboard | Grafana | API latency와 detection latency 분리 설명 | Yes |
| 7 | k6 result summary | k6 output or Grafana | normal/peak/duplicate/redis-down 비교 | Yes |
| 8 | PaySim preprocessing pipeline | Mermaid | raw -> processed -> sample -> replay 흐름 | Optional |
| 10 | ruleVersion traceability flow | Mermaid | active -> stored -> evaluation 연결 | Yes |
| 11 | runbook checklist | docs table screenshot or markdown table | pre/post/hold/rollback 기준 설명 | Optional |

## Boundaries

- Do not add images that only decorate the post.
- Do not capture raw/full PaySim data.
- Do not show secrets, admin tokens, local credentials, account identifiers, or device identifiers.
- Prefer diagrams for architecture/process flow and screenshots only for measured or operational evidence.
