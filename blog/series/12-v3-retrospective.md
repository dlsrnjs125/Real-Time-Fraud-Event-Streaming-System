# 회고: 거래 이벤트 한 건에서 설명 가능한 스트림 시스템까지

이 프로젝트를 시작할 때 그리고 있던 장면은 단순했다. 거래 이벤트가 API로 들어오고, Kafka를 거쳐 Consumer가 이상거래를 판단한다. 여기에 Redis와 PostgreSQL을 붙이면 실시간 탐지 시스템의 큰 틀이 만들어질 것이라고 생각했다.

돌아보면 가장 어려웠던 일은 기술을 연결하는 것이 아니었다. 이벤트가 중복되거나 늦게 도착하고, 특정 partition에 몰리고, Redis가 실패하고, 과거 데이터를 다시 흘릴 때도 “무슨 일이 일어났는지” 설명 가능한 기준을 세우는 일이었다.

Core/V1부터 V3 Phase 7까지 이어진 과정은 기능 추가의 연속이라기보다 시스템을 보는 질문이 세 번 바뀐 과정이었다.

## 출발점: API 성공 뒤에 숨은 시간을 다루고 싶었다

동기 API만 보면 응답이 빠른지가 가장 먼저 보인다. 하지만 이상거래 탐지는 응답 뒤에도 계속된다.

```text
request accepted
  -> Kafka published
  -> Consumer delivered
  -> user state updated
  -> rules evaluated
  -> result persisted
  -> offset acknowledged
```

이 흐름에서 API가 202를 반환한 순간과 fraud result가 저장된 순간은 다르다. 그래서 app-api와 app-consumer를 실행 단위로 분리했다. 완전한 MSA를 택하지는 않았다. 한 저장소와 공통 DB를 유지하면서, 장애와 확장과 지표의 경계만 분리하는 쪽이 현재 문제에 더 맞았다.

Kafka도 “써보고 싶은 기술”이 아니라 두 시간을 분리하기 위한 선택이었다. API가 이벤트를 받아들이는 속도와 Consumer가 상태를 계산하는 속도가 다를 때 Kafka가 그 차이를 backlog로 드러내게 했다.

## 첫 번째 완성: 잘 처리하는 것보다 잃지 않는 것

Core/V1에서 가장 오래 붙잡은 것은 정상 흐름이 아니었다. Consumer가 DB 저장 전에 죽거나, 저장한 뒤 ack 전에 죽거나, Redis가 응답하지 않을 때 무엇이 남는지를 먼저 정했다.

그 결과 몇 가지 원칙이 프로젝트의 바닥이 됐다.

- auto commit을 끄고 required processing 뒤 manual ack한다.
- 같은 `eventId`의 FraudResult는 PostgreSQL unique constraint로 막는다.
- 같은 Kafka 위치는 `(topic, partition, offset)` processing log로 추적한다.
- Redis가 실패하면 stateful rule을 skipped로 기록한 degraded result를 남긴다.
- DLT는 보관으로 끝내지 않고 reprocess/discard 상태와 audit을 둔다.
- 재처리해도 원본 `eventId`를 유지한다.

여기서 배운 것은 manual ack가 exactly-once를 만들어 주지 않는다는 점이다. ack를 늦추면 silent loss의 가능성은 줄지만 redelivery 가능성은 남는다. 결국 애플리케이션의 duplicate fast path와 DB constraint가 함께 재전달을 받아들여야 했다.

처음에는 “중복 row가 없는가”가 idempotency의 완료 조건이었다. V3에서 Redis state와 다음 이벤트의 판단까지 검사하면서 이 기준은 더 넓어졌다.

## 두 번째 전환: 동작하는 rule에서 비교 가능한 rule로

Rule Engine을 구현한 뒤에는 탐지 결과를 어떻게 평가할지가 문제가 됐다. 실제 금융 데이터를 사용할 수 없었기 때문에 PaySim synthetic data를 선택했지만, CSV를 가져와 replay하는 것만으로는 평가가 재현되지 않았다.

V2에서는 모델 성능을 높이는 대신 데이터와 평가 계약을 먼저 만들었다.

```text
raw PaySim
  -> provenance와 commit guardrail
  -> deterministic normalization
  -> HMAC identifier policy
  -> safe sample / local full data 분리
  -> replay report
  -> detection result export
  -> evaluation report
```

precision과 recall 앞에 denominator를 두었다. missing result, rejected row, unsupported native type을 조용히 LOW나 정상 처리로 섞지 않았다. Java Consumer의 active rule, DB에 저장된 result ruleVersion, Python evaluator가 기대하는 version도 각각 분리했다.

이 과정에서 “숫자를 계산할 수 있다”와 “숫자를 비교할 수 있다”가 다르다는 것을 배웠다. 같은 입력, 같은 mapping, 같은 threshold, 같은 version인지 확인할 수 없으면 좋아진 precision도 설명할 수 없다.

PaySim 결과를 실제 금융 fraud 성능이라고 부르지 않은 것도 중요한 결정이었다. V2가 만든 것은 production accuracy가 아니라, rule 변경을 같은 synthetic contract 위에서 재실행할 수 있는 기반이다.

## 세 번째 전환: 기능 목록에서 스트림의 물리적 행동으로

V3를 시작할 때는 이미 API, Consumer, Redis rule, DLT, replay 도구가 있었다. 하지만 “고부하에서도 잘 동작한다”는 말을 뒷받침할 수는 없었다.

그래서 기능을 더 붙이기 전에 프로젝트 질문을 세 축으로 다시 정리했다.

| 축 | 확인하고 싶었던 것 |
|---|---|
| Throughput | 어느 입력률부터 Lag이 자라고, overload 뒤 얼마나 회복하는가 |
| Stateful Processing | 사용자별 recent state가 커질수록 Redis 비용이 어떻게 변하는가 |
| Event Freshness | 이벤트가 시스템 진입 전부터 늦었는지, 내부에서 밀렸는지 구분할 수 있는가 |

Dataset volume과 runtime velocity를 나누고, workload manifest와 accepted-run 조건을 만들었다. eventTime, receivedAt, Kafka CreateTime, Consumer start, detectedAt도 하나의 latency로 합치지 않았다.

V3 Phase 0의 5 EPS·150건 baseline을 capacity 결과로 쓰지 않은 것이 이 전환을 잘 보여준다. 그 실행은 metric 배선과 count 정합성만 확인했다. 성능은 다음 phase부터 별도 workload로 측정했다.

## 예상과 달랐던 네 장면

### 1. 300 EPS의 병목은 API나 DB connection이 아니었다

concurrency 1에서는 300 EPS stage에 peak Lag 17,857이 생겼다. API transaction p99는 10.30ms, Kafka publish wait p99는 2.20ms, Hikari pending은 0이었다. 한 listener가 여섯 partition을 모두 맡은 topology가 우선 원인이었다.

같은 51,000건을 concurrency 6으로 다시 실행하자 peak Lag은 86으로 줄었다. 코드를 대규모로 최적화하기 전에 topology 설정 하나가 더 중요한 병목이었다.

동시에 concurrency가 늘었다고 모든 latency가 낮아진 것은 아니었다. Consumer service p99는 오히려 증가했다. 처리량 개선과 단건 latency 개선을 같은 말로 쓰면 안 된다는 것도 확인했다.

### 2. Redis에서는 memory보다 command scaling이 먼저 보였다

100 EPS와 12,000건을 고정하고 사용자 수를 1,000명에서 100명으로 줄였다. 사용자별 최대 window는 12건에서 120건으로 커졌다.

clean Redis memory delta는 high-density 쪽이 더 크지 않았다. 대신 HGET/event가 6.5에서 60.5로 늘었고 Redis p95는 6.20ms에서 32.84ms, Consumer p95는 13.18ms에서 38.47ms로 올랐다.

“ZSET이 커지면 메모리가 먼저 문제일 것”이라는 예상보다, window member마다 metadata를 읽는 O(window size) 경로가 더 분명한 후보였다. 이 결과 덕분에 pipeline, batch read, aggregate state 같은 후속 최적화도 비교 기준을 갖게 됐다.

### 3. Consumer를 더 띄워도 hot partition은 나뉘지 않았다

balanced workload에서는 여섯 partition이 비슷하게 일했다. 같은 300 EPS 중 60%를 P2에 보낸 workload에서는 P2만 즉시 Lag 12,554가 남고 다른 partition은 0이 됐다.

concurrency를 8로 높여도 partition은 6개라 두 Consumer가 idle이었다. 하나의 hot partition을 같은 group의 두 Consumer가 나눠 처리할 수도 없었다.

`userId` key는 사용자별 순서를 얻는 대신 skew 위험을 만든다. 이 trade-off를 문장으로만 알고 있을 때와 실제 partition Lag으로 본 뒤의 이해는 달랐다.

### 4. 같은 burst라도 사건이 시작된 시간은 달랐다

Organic과 Catch-up은 모두 300 EPS, 9,000건이었다. Organic의 ingress age p95는 0.239초, Catch-up은 270.127초였다.

arrival rate만 보면 같은 burst지만 한쪽은 지금 만들어진 트래픽이고, 다른 쪽은 시스템에 들어오기 전에 이미 4분 30초가 지난 이벤트였다. Kafka 전달과 Consumer·Redis·sink latency를 따로 남기면서 upstream event age를 내부 병목으로 오해하지 않을 수 있었다.

## 장애 실험이 정상 흐름의 의미를 다시 정의했다

가장 기억에 남는 실험은 가장 높은 EPS가 아니라 E3에서 실패를 주입한 20건짜리 redelivery drill이었다.

Redis update 전, Redis update 후 result 저장 전, result 저장 후 ack 전이라는 세 지점에서 각각 실패시켰다. 다음 이벤트 E4가 정확히 rapid-transaction threshold에 닿도록 설계했다.

세 경우 모두 E4의 window count는 5, risk score는 30, level은 MEDIUM, decision은 REVIEW였다. ZSET member를 `eventId`로 둔 선택과 result duplicate precheck가 다음 이벤트의 판단까지 지켰다.

이때 idempotency의 정의가 바뀌었다.

```text
이전: 중복 FraudResult row가 없다
이후: Redis state와 후속 rule decision도 안정적이다
```

정상 처리 코드를 이해하는 가장 좋은 방법 중 하나가 중간에 멈춰 보는 것이라는 사실도 배웠다.

## 이벤트 시간을 다루며 포기한 것과 지킨 것

늦은 이벤트를 모두 받으면 live window가 과거 상태로 오염되고, 모두 버리면 audit가 사라진다. 그래서 5분 allowed lateness를 경계로 역할을 나눴다.

- 4분 59초 late event는 eventTime score로 Redis state에 반영한다.
- 10분 late event는 live Redis state에서 제외한다.
- stateful rule은 skipped지만 non-stateful rule은 실행한다.
- durable result에는 degraded와 reason을 남긴다.

이 정책은 watermark나 historical correction을 구현하지 않는다. 과거 판단을 다시 계산하지도 않는다. 대신 live state의 의미와 도착 사실을 모두 잃지 않는 범위를 선택했다.

24시간 historical replay는 아예 다른 경로로 보냈다. live/replay의 topic, Consumer group, Redis namespace를 분리하고 mode 설정이 섞이면 startup에서 거부했다. concurrent run에서 두 group의 Lag은 0으로 돌아왔고 Redis namespace collision도 0이었다.

다만 PostgreSQL과 Redis server는 물리적으로 공유했다. logical isolation을 physical isolation이라고 부르지 않은 이유다.

## 잘된 선택보다 잘못된 실행을 남기는 일이 더 어려웠다

V3 evidence에는 accepted run만큼 discarded run도 자주 등장한다.

- k6 dropped iteration 때문에 event count가 모자랐다.
- sandbox network 제한으로 HTTP 요청이 전부 실패했다.
- logical Redis DB만 나눠 memory 비교가 오염됐다.
- replay rerun 전에 이전 Consumer Lag이 남아 있었다.
- histogram bucket이 270초 event age를 제대로 표현하지 못했다.

이 결과를 지우고 다시 실행하면 문서는 깨끗해 보인다. 하지만 왜 다음 run을 믿어도 되는지는 설명할 수 없게 된다.

폐기 이유를 남기면서 clean-state preflight, workload validation, event count, dropped iteration, final Lag, receipt/result/log 일치가 accepted 기준으로 자리 잡았다. 실패한 실행이 테스트 절차를 더 단단하게 만든 셈이다.

## 프로젝트 전체를 지나며 가장 잘했다고 생각하는 것

첫째, 구현 범위를 계속 줄여 말한 것이다. 이 시스템은 금융 원장도 아니고 settlement 시스템도 아니다. PaySim 평가가 실제 fraud accuracy를 보장하지 않고, local Docker 300 EPS가 production capacity를 뜻하지 않는다.

둘째, Redis를 final truth로 두지 않은 것이다. Redis 장애와 too-late 정책 모두 incomplete detection을 결과에 드러내고, durable 결과와 audit는 PostgreSQL에 남겼다.

셋째, 성능 개선 전에 관측 경계를 만든 것이다. API, Kafka delivery, processing log, duplicate check, Redis, rule, sink를 나누지 않았다면 concurrency 문제와 state-size 문제를 서로 다른 원인으로 설명하기 어려웠을 것이다.

넷째, replay를 단순 재발행 기능으로 끝내지 않은 것이다. 원본 eventId, source topic, Consumer group, state namespace가 함께 보존되어야 replay가 live를 건드리지 않는다.

## 여전히 아쉬운 부분

완료한 phase가 많아졌지만, 해결하지 않은 문제도 더 선명해졌다.

- 300 EPS 위의 다음 capacity knee는 아직 모른다.
- Redis O(window size) read path는 측정했지만 최적화 전후 비교는 하지 않았다.
- live와 replay는 논리적으로 분리됐지만 물리 자원과 우선순위는 공유한다.
- `sourceSentAt`이 없어 upstream processing과 network transport를 분리할 수 없다.
- DLT 상태와 Kafka publish 사이에 outbox 기반 원자성이 없다.
- automatic rollback과 deployment changelog는 구현하지 않았다.
- 실제 금융 데이터 기반 탐지 품질은 검증하지 않았다.

이 목록은 미완성의 변명이 아니라 다음 변경이 어떤 evidence를 필요로 하는지 알려 주는 backlog다.

## 다시 시작한다면 바꾸고 싶은 순서

처음부터 다시 만든다면 기능보다 계약을 조금 앞에 둘 것이다.

1. 이벤트 schema를 만들 때 timestamp의 소유자와 의미를 함께 정한다.
2. Kafka key를 선택할 때 ordering test와 skew workload를 같이 만든다.
3. Redis rule을 추가할 때 state-size metric과 redelivery test를 동시에 넣는다.
4. 부하 테스트 전에 workload manifest와 accepted/discarded 조건을 버전 관리한다.
5. total latency보다 stage latency와 partition Lag을 먼저 수집한다.
6. historical replay는 처음부터 live topic/group/namespace 밖에 둔다.
7. 최적화는 같은 부하의 baseline과 ops/event를 얻은 다음 시작한다.

## 이 프로젝트가 결국 남긴 것

처음에는 Kafka, Redis, PostgreSQL로 구성된 이상거래 탐지 시스템을 만들고 싶었다. 지금 남은 것은 그보다 조금 다른 결과다.

이벤트 한 건이 들어왔을 때 어느 경계를 지났는지 추적할 수 있고, 입력이 빨라졌을 때 어느 partition과 stage가 밀렸는지 구분할 수 있으며, 상태가 커지거나 이벤트가 늦거나 재전달될 때 결과의 의미가 어떻게 달라지는지 설명할 수 있는 실험 환경이 만들어졌다.

가장 큰 배움은 최고 TPS 숫자 하나가 아니었다.

> 같은 조건으로 다시 실행할 수 있고, 예상과 다른 결과를 숨기지 않으며, 시스템이 보장하는 것과 아직 모르는 것을 함께 말할 수 있어야 한다.

Core/V1은 이벤트를 잃지 않는 바닥을 만들었고, V2는 데이터와 rule 결과를 비교하는 계약을 만들었으며, V3는 부하·상태·시간 아래에서 그 시스템의 행동을 설명하는 언어를 만들었다. 이 세 층이 연결된 것이 이 프로젝트의 최종 결과다.
