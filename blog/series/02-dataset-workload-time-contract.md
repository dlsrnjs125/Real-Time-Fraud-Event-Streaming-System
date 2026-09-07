# 600만 행 데이터셋은 6,000 EPS가 아니다

대용량 CSV를 준비하면 대용량 처리 실험도 준비된 것처럼 보인다. 실제로는 데이터셋이 정하는 것과 부하가 정하는 것이 다르다.

```text
Dataset volume != Runtime event velocity
```

PaySim 수백만 행은 입력 후보의 양을 말한다. 같은 파일도 10 EPS로 천천히 재생할 수 있고, 300 EPS burst로 보낼 수 있으며, 하루치 과거 이벤트를 30초에 압축할 수도 있다. 이 차이를 분리하지 않으면 데이터가 크다는 사실을 시스템 처리량으로 잘못 인용하게 된다.

## 먼저 세 개의 계약으로 나눴다

### 데이터 계약

PaySim은 synthetic data다. V2에서 다음 경계를 만들었다.

- raw/full dataset은 Git에 넣지 않는다.
- 정규화 결과와 label sidecar를 분리한다.
- 식별자는 local salt를 사용하는 HMAC으로 변환한다.
- CI는 작은 fixture와 safe sample로 계약을 검증한다.
- full replay와 evaluation은 local/manual evidence로 구분한다.

이 계약은 개인정보 위험과 재현성을 동시에 다룬다. 원본을 숨겼다는 이유로 어떤 변환을 했는지 알 수 없어져도 안 되고, 재현성을 이유로 raw identifier를 저장소에 남겨도 안 된다.

### workload 계약

V3의 모든 실행은 JSON manifest로 입력 모양을 고정한다. manifest에는 `workloadId`, version, driver, target EPS, duration, event count, user distribution, event-time mode 같은 실행 의미가 들어간다.

V3에서 사용한 workload는 목적이 서로 다르다.

| 종류 | 바꾸는 것 | 확인하는 것 |
|---|---|---|
| Capacity | EPS stage | Lag이 계속 자라는 지점 |
| Stateful density | 사용자 수 | 같은 EPS에서 window 크기 비용 |
| Partition skew | partition affinity | hot partition의 로컬 ceiling |
| Redelivery | 실패 주입 위치 | 중복 전달 후 상태 안정성 |
| Lateness | eventTime offset | live window 수용·제외 정책 |
| Catch-up | pre-ingress age | 외부 누적과 내부 지연 분리 |
| Historical replay | topic/group/namespace | live와 replay의 논리 격리 |

`scripts/data/validate_v3_workload_manifest.py`와 schema는 필수 필드뿐 아니라 workload 역할별 조건도 검사한다. 실험 코드보다 실험의 의미가 먼저 버전 관리되는 구조다.

### 시간 계약

한 이벤트에는 하나의 시간이 아니라 여러 경계가 있다.

```text
eventTime
  -> receivedAt
  -> Kafka CreateTime
  -> Consumer processing start
  -> detectedAt
```

각 차이는 다른 지연을 뜻한다.

- `receivedAt - eventTime`: 시스템 진입 전부터 가진 event age
- `Consumer start - Kafka CreateTime`: producer publish 이후 Consumer가 받기까지의 지연
- Consumer service time: processing log, duplicate check, Redis, rule, sink를 포함한 처리 시간
- `detectedAt - receivedAt`: 접수 뒤 탐지 완료까지의 시간

Kafka `CreateTime` 기반 값은 producer-to-consumer delay이지 broker queue time만은 아니다. source 쪽 `sourceSentAt`을 저장하지 않기 때문에 event age를 source network latency라고 부를 수도 없다.

## PaySim의 `step`을 5분 window로 오해하지 않기

PaySim 전처리는 `step`을 한 시간 단위 `eventTime`으로 변환한다. 같은 step 안의 row들은 같은 timestamp를 갖고, 그 한 시간 안의 실제 순서는 알 수 없다.

따라서 PaySim에서 같은 사용자의 row가 반복된다는 사실만으로 “5분 동안 N건”이라고 말할 수 없다. Phase 2는 5분 Redis window 안의 밀도를 synthetic workload가 직접 만들게 했다.

```text
Corpus fact: 한 source step 안에 같은 사용자의 이벤트가 몇 건 있는가
Experiment fact: 실행 중 5분 window에 사용자당 몇 건을 넣었는가
```

두 사실을 같은 표에 섞지 않는 것이 중요했다.

## 같은 부하라는 말의 조건

Before/After 비교는 이름만 같은 시나리오로는 부족하다. 최소한 다음 fingerprint가 맞아야 한다.

- commit SHA와 dirty 여부
- workload ID/version, seed, target EPS, duration, event count
- partition 수와 Consumer concurrency
- Redis window/TTL/namespace
- driver 종류: HTTP k6인지 direct Kafka producer인지
- PostgreSQL·Redis clean state
- accepted run과 discarded run 구분

Phase 1의 concurrency 1과 6 비교는 같은 51,000-event workload를 사용했다. Phase 2는 양쪽 모두 100 EPS, 120초, 12,000건, 같은 금액을 유지하고 사용자 수만 1,000명에서 100명으로 바꿨다. 이런 통제가 있어야 차이를 특정 변수와 연결할 수 있다.

## 실패한 실행도 계약의 일부다

V3 evidence에는 dropped iteration, 잘못된 stage 지속 시간, 공유 Redis instance 때문에 왜곡된 memory 비교 같은 폐기 실행이 남아 있다. 실패한 실행을 지우지 않은 이유는 좋은 숫자만 선택했다는 의심보다, 어떤 기준으로 결과를 탈락시켰는지를 설명하는 편이 더 재현 가능하기 때문이다.

accepted run의 기준은 대체로 다음과 같았다.

1. 설정한 event count가 모두 발행되었다.
2. HTTP failure와 dropped iteration을 확인했다.
3. final Consumer Lag이 0으로 돌아왔다.
4. receipt/result/processing-log 수가 일치했다.
5. clean-state와 실행 fingerprint가 남아 있다.

## 이 계약으로 말할 수 있는 것

“PaySim으로 대용량 성능을 검증했다”는 표현은 너무 넓다. 정확한 표현은 다음과 같다.

> PaySim과 synthetic event generator를 입력 재료로 사용하고, 별도로 버전 관리한 HTTP workload를 로컬 Docker 환경에서 실행해 특정 EPS·분포·시간 조건의 동작을 측정했다.

데이터셋은 재료이고, workload는 실험 조건이며, timestamp는 원인을 나누는 좌표다. 셋을 분리하고 나서야 성능 수치가 문장이 되었다.
