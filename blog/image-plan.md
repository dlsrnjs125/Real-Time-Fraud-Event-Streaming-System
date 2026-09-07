# V3 Blog Image Plan

본문의 논리를 바꾸지 않는 장식 이미지는 추가하지 않습니다. 시각 자료는 여러 시계열의 관계, partition 편향, live/replay 격리처럼 텍스트만으로 비교하기 어려운 부분에만 사용합니다.

## 선택 원칙

- architecture와 처리 순서는 Mermaid로 충분하면 본문에 직접 둡니다.
- 측정 결과는 accepted run의 기존 `docs/evidence/` screenshot을 원본으로 사용합니다.
- 게시 플랫폼에 맞춰 복사할 때는 crop만 허용하고 수치나 축을 편집하지 않습니다.
- screenshot에는 synthetic ID만 사용하며 token, raw payload, accountId, deviceId, local salt를 노출하지 않습니다.
- discarded run 이미지를 결과 대표 이미지로 사용하지 않습니다.
- “No data” panel을 채우기 위해 임의 이벤트를 만든 screenshot은 사용하지 않습니다.

## 12편별 시각 자료 결정

| Series | 시각 자료 | 원본 | 상태/이유 |
|---:|---|---|---|
| 01 | API→Kafka→Consumer→state/sink 흐름 | 본문 Mermaid | 포함 |
| 02 | dataset/workload/time 분리 | 텍스트 표 | 별도 이미지 불필요 |
| 03 | stage observability map | 본문 Mermaid, `infra/grafana/dashboards/v3-stream-foundation.json` | Mermaid 포함; dashboard는 선택 |
| 04 | concurrency 1 vs 6 Lag | `docs/evidence/v3-phase1/01-before-lag-growth.png`, `05-after-lag-contained.png` | 본문 배치 완료 |
| 05 | high-density Redis latency | `docs/evidence/v3-phase2/02-pressure-window-latency.png` | 본문 배치 완료 |
| 06 | balanced vs hot P2 | `docs/evidence/v3-phase3/06-balanced-vs-hot-summary.png`, `04-hot-p2-partition-lag.png` | 본문 배치 완료 |
| 07 | redelivery Lag/latency | `docs/evidence/v3-phase4/07-grafana-lag-throughput.png` | 본문 배치 완료; 성능 수치가 아닌 drill 맥락으로 사용 |
| 08 | freshness cause counters | `docs/evidence/v3-phase5/07-grafana-event-freshness.png` | 본문 배치 완료; `No data` panel의 한계 명시 |
| 09 | organic vs catch-up delay attribution | `docs/evidence/v3-phase6/09-grafana-delay-attribution.png` | 본문 배치 완료 |
| 10 | live/replay isolation dashboard | `docs/evidence/v3-phase7/09-grafana-live-replay-isolation.png` | 본문 배치 완료 |
| 11 | safety-rail 흐름 | 필요 시 Mermaid | screenshot 불필요 |
| 12 | phase result map | 본문 표 | 별도 이미지 불필요 |

## 게시용 복사 이름

실제 게시 준비 시 필요한 파일만 `blog/images/`로 복사합니다. 기존 evidence 원본은 이동하거나 덮어쓰지 않습니다.

| 우선순위 | 게시용 파일명 | 출처 |
|---:|---|---|
| 1 | `04-phase1-lag-before-after.png` | Phase 1 before/after 이미지를 나란히 배치한 무수정 캡처 |
| 2 | `05-phase2-state-density.png` | Phase 2 baseline/pressure |
| 3 | `06-phase3-balanced-hot-p2.png` | Phase 3 summary |
| 4 | `08-phase5-event-freshness.png` | Phase 5 freshness dashboard |
| 5 | `09-phase6-delay-attribution.png` | Phase 6 delay attribution |
| 6 | `10-phase7-live-replay-isolation.png` | Phase 7 isolation dashboard |

저장소 안에서 읽을 때는 accepted evidence 원본을 상대 경로로 직접 연결합니다. 외부 게시 플랫폼이 `docs/` 상대 경로를 보존하지 못하는 경우에만 위 이름으로 `blog/images/`에 복사하고 링크를 교체합니다.

## 기존 `blog/images/`

기존 이미지는 Core/V2 시리즈에서 사용한 sanitized evidence입니다. 새 V3 본문에서는 억지로 재사용하지 않지만, 과거 기록 보존을 위해 삭제하지 않습니다. 새 시리즈 게시가 확정된 뒤 사용 여부를 별도로 판단합니다.
