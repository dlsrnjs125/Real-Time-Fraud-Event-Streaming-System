# k6 결과를 좋게 보이게 쓰지 않기

## p95와 실패율만 보면 오해가 생긴다

부하 테스트 결과는 쉽게 보기 좋은 숫자로 바뀐다. RPS와 p95만 남기면 API 접수 성능과 Consumer 처리 성능이 섞이고, 장애 상황에서 중복 결과가 생겼는지, Redis rule이 skipped 되었는지, DLT가 늘었는지 알기 어렵다. 그래서 k6 결과는 성능 홍보가 아니라 한계를 드러내는 evidence로 다뤘다.

k6는 주로 HTTP 요청 관점에서 응답 시간, 실패율, 요청 처리량을 보여준다. 이 지표는 API 접수 계층을 확인하는 데 유용하지만, 비동기 구조에서는 HTTP 응답이 빠르더라도 Consumer 처리와 `fraud_detection_result` 저장이 뒤에서 지연될 수 있다.

따라서 k6 결과만 보면 “접수는 성공했지만 탐지는 밀린 상황”을 놓칠 수 있다. 이 프로젝트에서는 k6 summary를 성능 결과의 전부로 보지 않고, Consumer Lag, detection latency, DLT count, duplicate result count, PostgreSQL 결과 row를 함께 확인해야 하는 evidence로 다뤘다.

## 시나리오를 나눠서 봐야 했던 이유

k6 시나리오는 normal load, peak load, duplicate storm, Redis down, hot partition을 나눠 둔다. 목표는 좋은 숫자를 만드는 것이 아니라 어떤 신호를 함께 봐야 하는지 고정하는 것이다.

시나리오를 나눈 이유는 결과를 더 좋아 보이게 만들기 위해서가 아니라 서로 다른 리스크를 분리해서 보기 위해서다. normal load는 기본 요청 흐름이 깨지지 않는지 확인하고, peak load는 트래픽 증가 시 API latency와 Consumer Lag이 어떻게 변하는지 보기 위한 시나리오다.

duplicate replay는 같은 `eventId`가 반복되어도 fraud result가 중복 생성되지 않는지 확인하기 위한 시나리오이고, redis-down load는 Redis 의존 rule이 실패했을 때 그 결과가 정상 성공처럼 숨겨지지 않고 degraded로 남는지 확인하기 위한 시나리오다.

## duplicate replay에서 http_req_failed가 높아 보이는 이유

duplicate storm에서는 API가 같은 eventId를 여러 번 받아도 최종 `FraudResult`가 하나인지 확인해야 한다. 이때 duplicate가 `409 CONFLICT`로 응답되면 k6의 기본 `http_req_failed`만 봤을 때 실패율이 높아 보일 수 있다. 하지만 프로젝트 정책상 허용된 duplicate response라면 최종 판단은 DB consistency와 함께 해야 한다.

![k6 duplicate replay summary](../images/07-k6-duplicate-replay-summary.png)

duplicate replay 시나리오에서는 같은 이벤트가 반복 요청되기 때문에 `http_req_failed`가 높게 보일 수 있다. 그래서 이 테스트는 HTTP failure rate만으로 실패 여부를 판단하지 않고, `accepted or duplicate` check가 통과했는지와 중복 결과가 추가 생성되지 않았는지를 함께 확인했다.

duplicate replay 시나리오는 Kafka나 Consumer가 exactly-once를 보장한다는 것을 증명하기 위한 테스트가 아니다. 오히려 같은 `eventId`가 다시 들어올 수 있다고 가정하고, application idempotency와 PostgreSQL unique constraint가 중복 결과 생성을 막는지 확인하는 시나리오다.

따라서 이 테스트에서는 HTTP 응답만 보면 부족하다. 같은 `eventId`에 대해 `fraud_detection_result`가 중복 생성되지 않았는지, duplicate 처리 로그나 결과 count가 기대한 기준으로 남았는지 함께 확인해야 한다.

Redis down에서는 error rate만 보면 부족하다. Redis 의존 rule이 skipped 되었는지, degraded result가 남았는지, Consumer가 계속 진행했는지를 같이 봐야 한다.

또한 API latency와 Consumer latency를 한 표에 섞으면 해석이 흐려진다. API가 빠른 것과 탐지가 빠른 것은 다르다.

k6 summary는 클라이언트 관점의 p95/p99, request failure, check 결과를 보여준다. Grafana dashboard는 같은 시간대에 서버가 어떤 status와 degraded metric을 기록했는지 확인하는 보조 evidence로 사용한다.

p95, RPS, error rate는 중요하지만 단독으로 시스템 정합성을 설명하지 못한다. 특히 이 프로젝트처럼 API 접수와 Consumer 탐지가 분리된 구조에서는 HTTP 성공이 곧 탐지 결과 저장 성공을 의미하지 않는다.

그래서 evidence는 k6 summary만이 아니라 DB에 저장된 fraud result, DLT 상태, duplicate result count, Redis degraded count, Consumer Lag, 관련 로그를 함께 포함해야 한다. 좋은 숫자만 남기면 어떤 장애 조건에서 무엇이 깨졌는지 설명할 수 없다.

## k6 summary와 Grafana dashboard를 같이 보는 방식

`load-test/k6/README.md`, `docs/22-load-test-results.md`, `docs/23-load-test-results.md`에 시나리오와 결과 기록 양식을 둔다. 측정값은 실제 evidence가 있을 때만 쓴다. 이 글에서는 새 수치를 만들지 않고 측정 항목과 해석 기준만 정리한다.

## Redis down에서 error rate보다 degraded count가 중요한 이유

duplicate replay에서는 2xx만 성공으로 보지 않는다. API response policy상 허용되는 duplicate/conflict bucket과 `fraud_detection_results.event_id` unique constraint 결과를 함께 본다. Redis down에서는 `fraud_redis_window_degraded_total`, `fraud_detection_degraded_total`, `fraud_rule_skipped_total` 같은 지표가 error rate만큼 중요하다.

redis-down 시나리오는 Redis 장애를 해결했다는 증거가 아니다. Redis 의존 rule이 실행되지 못했을 때 그 사실을 정상 성공처럼 숨기지 않고 degraded 결과로 남기는지 확인하는 시나리오다.

이때는 HTTP 응답보다 `degraded=true`, skipped rule, Redis error log, Consumer Lag, DLT count를 함께 봐야 한다. Redis 장애가 길어지면 degraded 결과가 계속 쌓일 수 있으므로, 실제 운영에서는 degraded 비율과 Redis error에 대한 alert 기준이 필요하다.

초기에는 Consumer Lag metric이 dashboard evidence로 완전히 연결되지 않았기 때문에 Kafka UI, processing log, fraud result 조회를 함께 보는 임시 해석 경로를 남겼다. 이후 observability evidence에서는 `kafka_consumergroup_lag` 기반 Grafana panel을 추가해 Consumer backlog를 dashboard에서 확인할 수 있게 보강했다.

## 시나리오별로 봐야 하는 지표

각 시나리오별로 봐야 할 지표를 분리했다.

| Scenario | Primary Signals | Consistency Signals |
|---|---|---|
| normal load | API latency, publish success, detection latency | missing event count |
| peak load | Consumer Lag max, lag recovery time | DLT count |
| duplicate storm | duplicate skip count | `FraudResult` count per `eventId` |
| Redis down | degraded count, skipped rule count | Consumer continuation |
| hot partition | partition lag skew | user-level ordering impact |

이번 글에서는 k6 terminal summary를 클라이언트 관점 evidence로 사용하고, 같은 시간대의 Grafana/Prometheus 지표를 서버 관점 evidence로 함께 해석한다.

부하 테스트 이후에는 먼저 k6 summary로 HTTP latency와 error rate를 확인한다. 그다음 Consumer Lag을 보고 Consumer가 입력 속도를 따라가지 못하는지 확인한다. 이후 detection latency나 결과 저장 시각을 통해 탐지 완료가 늦어졌는지 본다.

마지막으로 Redis degraded count, retry/DLT count, PostgreSQL fraud result 중복 여부, processing log를 확인한다. 이 순서는 완성된 운영 자동화가 아니라, 부하 테스트 결과를 해석하기 위한 확인 기준이다.

아래 표는 실제 수치를 주장하는 결과표가 아니라, 시나리오별 결과를 기록할 때 사용한 해석 기준이다.

| Scenario | p95 | p99 | Error/Conflict 해석 | Consistency Result | 남은 한계 |
|---|---:|---:|---|---|---|
| duplicate storm | record with evidence | record with evidence | duplicate conflict는 허용 bucket | `FraudResult` 중복 여부 확인 | DB 조회 기반 수동 확인 여부 기록 |
| Redis down | record with evidence | record with evidence | Redis rule skipped | degraded result 기록 여부 확인 | 탐지 품질 저하와 alert 한계 기록 |

## 측정값을 남길 때 필요한 맥락

`make k6-smoke`, `make k6-normal`, `make k6-peak`, `make k6-duplicate-check`, `make k6-redis-down` 같은 명령은 문서에 기록되어 있다. 실제 실행 환경, duration, VU, event count, p50/p95/p99, bottleneck은 결과 문서에 evidence로 남겨야 한다.

블로그에 부하 테스트 결과를 남길 때는 숫자만 분리해서 쓰지 않으려고 했다. 실행 명령, 시나리오, 대상 환경, 함께 확인한 지표를 같이 남겨야 같은 결과를 다시 해석할 수 있다.

p95나 RPS를 기록하더라도 Consumer Lag, detection latency, DLT count, duplicate result count와 함께 봐야 한다. 측정하지 않은 production 성능을 주장하기보다, 어떤 조건에서 어떤 신호를 확인했고 어떤 한계를 발견했는지를 기록하는 쪽이 이 프로젝트의 목적에 맞다.

## 로컬 부하 테스트를 production capacity로 쓰지 않은 이유

이 테스트는 production capacity planning이 아니다. 로컬 Docker Compose, 개발 장비, synthetic workload에서 얻은 증거는 병목을 찾고 설계를 검증하는 데 쓰며, 운영 규모의 처리량 보장으로 해석하지 않는다.

이번 k6 테스트는 특정 로컬/개발 환경과 제한된 시나리오에서의 결과다. 실제 운영 트래픽, 장기 soak test, multi-node Kafka 구성, 실제 장애 전파까지 증명한 것은 아니다.

다만 이 테스트를 통해 API latency만으로는 비동기 탐지 시스템을 설명할 수 없다는 점과, 시나리오별로 Consumer Lag, detection latency, DLT, duplicate result, degraded count를 함께 봐야 한다는 기준을 남겼다. 이 글의 목표는 좋은 성능 수치가 아니라 한계를 설명 가능한 evidence로 남기는 것이다.
