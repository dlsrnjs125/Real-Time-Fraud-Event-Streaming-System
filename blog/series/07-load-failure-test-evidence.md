# k6 부하/장애 테스트로 한계 측정

## 문제

부하 테스트 결과는 쉽게 과장된다. RPS나 p95 숫자만 쓰면 API 접수 성능과 Consumer 처리 성능이 섞이고, 장애 상황에서 어떤 데이터가 유실되었는지 설명하기 어렵다.

## 초기 설계

k6 시나리오는 normal load, peak load, duplicate storm, Redis down, hot partition을 나눠 둔다. 목표는 좋은 숫자를 만드는 것이 아니라 어떤 신호를 함께 봐야 하는지 고정하는 것이다.

## 실제로 막힌 지점

duplicate storm에서는 API가 같은 eventId를 여러 번 받아도 최종 `FraudResult`가 하나인지 확인해야 한다. Redis down에서는 error rate만 보면 부족하다. Redis 의존 rule이 skipped 되었는지, degraded result가 남았는지, Consumer가 계속 진행했는지를 같이 봐야 한다.

또한 API latency와 Consumer latency를 한 표에 섞으면 해석이 흐려진다. API가 빠른 것과 탐지가 빠른 것은 다르다.

## 확인한 증거

`load-test/k6/README.md`, `docs/22-load-test-results.md`, `docs/23-load-test-results.md`에 시나리오와 결과 기록 양식을 둔다. 측정값은 실제 evidence가 있을 때만 쓴다. 이 글에서는 아직 새 수치를 만들지 않고 측정 항목과 해석 기준만 정리한다.

## 바꾼 설계

각 시나리오별로 봐야 할 지표를 분리했다.

| Scenario | Primary Signals | Consistency Signals |
|---|---|---|
| normal load | API latency, publish success, detection latency | missing event count |
| peak load | Consumer Lag max, lag recovery time | DLT count |
| duplicate storm | duplicate skip count | `FraudResult` count per `eventId` |
| Redis down | degraded count, skipped rule count | Consumer continuation |
| hot partition | partition lag skew | user-level ordering impact |

## 검증

`make k6-smoke`, `make k6-normal`, `make k6-peak`, `make k6-duplicate-check`, `make k6-redis-down` 같은 명령은 문서에 기록되어 있다. 실제 실행 환경, duration, VU, event count, p50/p95/p99, bottleneck은 결과 문서에 evidence로 남겨야 한다.

## 남은 한계

이 테스트는 production capacity planning이 아니다. 로컬 Docker Compose, 개발 장비, synthetic workload에서 얻은 증거는 병목을 찾고 설계를 검증하는 데 쓰며, 운영 규모의 처리량 보장으로 해석하지 않는다.
