# Troubleshooting Log

개발 중 설계 변경 또는 문제 해결이 발생하면 아래 형식으로 기록합니다.

## 기록 형식

### 문제 제목

#### 초기 설계

#### 발생한 문제

#### 재현 방법

#### 원인 분석

#### 변경한 설계

#### 개선 결과

#### 남은 한계

#### 다시 설계한다면

---

## 후보 1. Partition Key 변경

### 초기 설계

`eventId`를 partition key로 사용합니다.

### 발생 가능한 문제

같은 `userId`의 거래 이벤트가 여러 partition에 분산되어 사용자별 거래 순서가 깨질 수 있습니다.

### 변경 방향

`userId`를 partition key로 사용합니다.

### 확인할 지표

- 사용자별 이벤트 순서
- partition별 lag
- hot partition 발생 여부

---

## 후보 2. Auto Commit에서 Manual Ack로 변경

### 초기 설계

Kafka consumer auto commit을 사용합니다.

### 발생 가능한 문제

DB 저장 전 offset이 commit되면 Consumer 장애 시 처리되지 않은 이벤트가 유실된 것처럼 보일 수 있습니다.

### 변경 방향

처리 성공 후 manual ack를 수행합니다.

### 확인할 지표

- Consumer 재시작 후 재처리 여부
- 중복 fraud_result 생성 여부
- missing event count

---

## 후보 3. Redis INCR + TTL에서 ZSET Sliding Window로 변경

### 초기 설계

`userId`별 INCR + TTL로 최근 거래 횟수를 계산합니다.

### 발생 가능한 문제

고정 윈도우 경계에서 탐지 정확도가 흔들릴 수 있습니다.

### 변경 방향

ZSET에 `eventTime`을 score로 저장하고 sliding window 방식으로 최근 거래 수를 계산합니다.

### 확인할 지표

- velocity rule 탐지 정확도
- Redis command latency
- 오래된 이벤트 제거 여부

---

## 후보 4. DLQ payload 원문 저장에서 masked payload + payload_hash로 변경

### 초기 설계

DLQ 이벤트에 실패 payload 원문을 저장합니다.

### 발생 가능한 문제

DLQ는 운영자 조회와 장애 분석 대상이므로 accountId, deviceId, ipAddress 등 민감정보가 장기간 노출될 수 있습니다.

### 변경 방향

DLQ에는 masked payload와 `payload_hash`를 저장하고, 원문 payload 접근은 별도 권한과 감사 로그가 필요하도록 설계합니다.

### 확인할 지표

- DLQ 조회 응답의 민감정보 노출 여부
- payload_hash 저장 여부
- `dead_letter_events` 상태와 재처리 시도 기록 여부
- 별도 audit history 필요 여부

---

## 후보 5. API latency만 보다가 Consumer Lag을 핵심 SLI로 추가

### 초기 설계

API p95 latency만 주요 응답성 지표로 봅니다.

### 발생 가능한 문제

API가 빠르게 응답해도 Consumer Lag이 증가하면 이상거래 탐지는 지연됩니다.

### 변경 방향

API latency, Consumer Lag, detection latency, DLQ count를 함께 핵심 지표로 봅니다.

### 확인할 지표

- API p95/p99 latency
- Consumer Lag
- detection latency
- DLQ count

---

## 후보 6. userId key로 인한 hot partition 발생과 대응

### 초기 설계

사용자별 순서 보장을 위해 `userId`를 partition key로 사용합니다.

### 발생 가능한 문제

특정 userId에 이벤트가 몰리면 일부 partition lag이 증가할 수 있습니다.

### 변경 방향

초기에는 userId key를 유지하고 hot partition을 측정합니다. key 전략 변경은 사용자별 순서 보장 영향까지 함께 검토합니다.

### 확인할 지표

- partition별 lag
- partition별 message count
- hot userId 부하 테스트 결과

---

## 후보 7. unsupported schemaVersion을 임의 변환하지 않고 DLT로 이동

### 초기 설계

Consumer가 이벤트 payload를 가능한 형태로 변환해 처리합니다.

### 발생 가능한 문제

지원하지 않는 schemaVersion을 임의로 처리하면 잘못된 탐지 결과가 생성될 수 있습니다.

### 변경 방향

지원하지 않는 schemaVersion은 DLT로 보내고 운영자가 재처리 가능 여부를 판단합니다.

### 확인할 지표

- unsupported schemaVersion DLT count
- schema compatibility test 결과
- DLQ failure_reason 분포

---

## 후보 8. Redis 장애 시 전체 실패가 아니라 degraded mode로 전환

### 초기 설계

Redis 장애 시 Consumer 처리를 실패로 봅니다.

### 발생 가능한 문제

Redis 장애가 전체 탐지 중단으로 이어질 수 있습니다.

### 변경 방향

단건 기반 rule은 계속 수행하고 Redis 기반 rule만 SKIPPED 처리합니다. FraudResult에는 `degraded=true`를 기록합니다.

### 확인할 지표

- degraded result count
- skipped rule count
- Redis error count

---

## 후보 9. Outbox Pattern 제외 후 한계와 향후 도입 조건 정리

### 초기 설계

API Server는 Kafka publish 성공 이후 `ACCEPTED`를 반환하고 Outbox Pattern은 구현하지 않습니다.

### 발생 가능한 문제

API 접수 기록과 Kafka 발행 원자성이 필요한 요구가 생기면 현재 구조만으로는 감사 기준이 부족할 수 있습니다.

### 변경 방향

초기 범위에서는 제외하되, 감사 대상 접수 기록이 필요해지면 `transaction_event_intake`와 `outbox_events` 테이블, Outbox Publisher를 추가합니다.

### 확인할 지표

- Kafka publish failure count
- accepted event count와 Kafka append count 비교
- outbox pending count, if implemented

---

## Phase 12. Duplicate Replay와 k6 failure 기준

### 문제 상황

Duplicate replay 시나리오는 의도적으로 같은 `eventId`를 반복 발행합니다. 이때 API가 duplicate를 `409 CONFLICT`로 반환하면 k6 기본 지표에서는 실패 요청처럼 보일 수 있습니다.

### 판단

Duplicate replay에서는 2xx만 성공으로 보지 않고, 프로젝트 정책상 허용되는 duplicate response를 별도 check로 처리합니다. 최종 판단 기준은 `fraud_detection_results.event_id` unique constraint와 fraud result count 1건 유지 여부입니다.

### 트레이드오프

`http_req_failed` 지표만 보면 실패율이 높아 보일 수 있습니다. 따라서 duplicate scenario는 consistency 결과와 함께 해석합니다.

---

## Phase 12. Redis Down Load 후 Redis 복구 누락 위험

### 문제 상황

Redis down load는 의도적으로 Redis container를 중지한 상태에서 이벤트를 유입합니다. 테스트가 중간에 실패하면 Redis가 중지된 채로 남아 이후 테스트 결과를 왜곡할 수 있습니다.

### 변경한 설계

`scripts/load_tests/run_redis_down_load.sh`는 `trap cleanup EXIT`로 종료 시 Redis start를 시도합니다. Runbook에는 테스트 후 `docker compose -f infra/docker-compose.yml ps redis` 확인을 추가했습니다.

### 남은 한계

Docker 데몬 장애나 compose project 손상처럼 `docker compose start redis` 자체가 실패하는 경우에는 수동 복구가 필요합니다.

---

## Phase 12. 로컬 부하 테스트 결과 해석 한계

## V2 Phase 4. PaySim committed sample이 default-local salt로 생성될 수 있는 문제

- 문제: shared/committed sample manifest가 `hashSaltSource=default-local`로 남으면 누구나 같은 repository-local salt로 pseudonym을 재생성할 수 있습니다.
- 원인: Phase 3에서는 strict sample target을 권장했지만 committed manifest를 shell data policy에서 직접 차단하지 않았습니다.
- 대응: `generate_paysim_samples.py --require-non-default-salt`, `make generate-paysim-sample-strict`, `validate_paysim_outputs.py --require-non-default-salt`, `make validate-paysim-strict`를 유지하고 `check-data-policy.sh`가 committed sample manifest의 default-local salt source를 실패시킵니다.
- 검증: strict option fixture test와 `make data-policy-check`로 확인합니다.
- 남은 한계: full processed output이 없는 CI에서는 strict full validation/sample generation을 실행하지 않습니다.

## V2 Phase 4. Manifest 또는 report에 salt 값 자체가 기록될 수 있는 문제

- 문제: `hashSaltValue`, `saltValue`, `salt`, `rawSalt` 같은 field가 report/manifest에 들어가면 pseudonymization secret이 repository나 logs에 남을 수 있습니다.
- 원인: salt source와 salt value의 경계가 명확하지 않으면 metadata 확장 중 secret field가 추가될 수 있습니다.
- 대응: Python validator/sampler의 forbidden salt field scan을 강화하고, data policy check가 sample manifest의 salt value field를 차단합니다. 허용 metadata는 `hashSaltSource`, `hashAlgorithm`, `hashIdPrefixLength`로 제한합니다.
- 검증: `test_validate_paysim_outputs.py`, `test_generate_paysim_samples.py`, `make data-policy-check`.
- 남은 한계: Git에 이미 들어간 secret은 이 check만으로 제거되지 않으므로 별도 secret rotation과 history cleanup이 필요합니다.

## V2 Phase 4. Hash ID format이 느슨하면 downstream grouping이 깨질 수 있는 문제

- 문제: `userId`, `accountId`, `destinationAccountId`가 prefix만 맞고 hash 길이/문자 집합이 흔들리면 replay, Kafka key, user grouping, Rule V2 검증이 같은 기준을 공유하지 못합니다.
- 원인: Phase 3 validator는 `U-`, `A-`, `D-` prefix만 확인했습니다.
- 대응: validator와 sampler가 `^U-[0-9a-f]{16}$`, `^A-[0-9a-f]{16}$`, `^D-[0-9a-f]{16}$`를 검증하고 preprocessing report에 `HMAC-SHA256`과 prefix length 16을 기록합니다.
- 검증: invalid prefix, short hash, uppercase hex fixture test를 추가했습니다.
- 남은 한계: 16 hex prefix는 collision 가능성이 0이 아니므로 Phase 5 replay와 full dataset 검증에서 collision evidence를 별도로 확인합니다.

## V2 Phase 4. Shell data policy가 hashSaltSource까지 막을 수 있는 문제

- 문제: salt leakage scan을 너무 넓게 만들면 정상 manifest field인 `hashSaltSource`까지 차단해 committed sample policy가 실행 불가능해질 수 있습니다.
- 원인: field name에 `salt`가 포함되어 있다는 이유만으로 모든 salt 관련 key를 차단하면 source metadata와 secret value를 구분하지 못합니다.
- 대응: shell check는 `hashSaltValue`, `saltValue`, `salt`, `rawSalt` 같은 value field만 차단하고 `hashSaltSource`는 허용합니다. 별도 check로 `hashSaltSource=default-local`만 차단합니다.
- 검증: committed sample manifest에 `hashSaltSource=env:PAYSIM_HASH_SALT`가 있는 상태에서 `make data-policy-check`를 통과해야 합니다.
- 남은 한계: shell check는 JSON schema validator가 아니므로 정밀 검증은 Python scripts가 담당합니다.

## V2 Phase 4. Raw identifier leakage regex 오탐 위험

- 문제: raw identifier leakage regex가 너무 넓으면 정상 `trace-paysim-000001`이나 deterministic `eventId`를 raw PaySim identifier로 오탐할 수 있습니다.
- 원인: 단순 숫자 포함 문자열 또는 모든 대문자/숫자 조합을 차단하면 정상 synthetic event metadata와 충돌합니다.
- 대응: raw PaySim identifier pattern을 `[CM][0-9]{3,}` 계열로 제한하고, shell check는 주변 문자를 함께 보는 portable regex를 사용합니다.
- 검증: validation/sample fixture와 committed sample policy check로 정상 eventId/traceId를 허용하면서 `C12345`, `M12345`, `U-C12345`는 차단합니다.
- 남은 한계: raw identifier와 같은 모양의 다른 텍스트도 차단될 수 있으므로 sample/manifest에는 불필요한 free-form text를 넣지 않습니다.

## V2 Phase 5. Replay payload에 label field가 섞일 수 있는 문제

- 문제: `isFraud`, `isFlaggedFraud`, `sourceFlaggedFraud`가 HTTP payload로 들어가면 Rule Engine이 평가 label을 볼 수 있는 구조가 됩니다.
- 원인: PaySim events JSONL과 labels JSONL을 같은 data workflow에서 다루기 때문입니다.
- 대응: replay script는 events JSONL만 input으로 받고 label field를 payload validation에서 거부합니다.
- 검증: `test_replay_paysim_events.py` label leakage test와 dry-run replay.
- 남은 한계: label 기반 filtering/evaluation은 후속 단계에서 별도 command로 분리해야 합니다.

## V2 Phase 5. receivedAt을 client가 보내 server time policy가 깨지는 문제

- 문제: replay payload가 `receivedAt`을 보내면 app-api가 접수 시각을 생성한다는 정책이 흐려집니다.
- 원인: normalized runtime event와 Kafka message schema를 혼동할 수 있습니다.
- 대응: replay script는 `receivedAt`이 있으면 HTTP 요청을 보내지 않고 rejected로 집계합니다.
- 검증: receivedAt validation failure fixture test.
- 남은 한계: app-api DTO에 향후 `receivedAt`이 추가되면 replay contract도 다시 검토해야 합니다.

## V2 Phase 5. eventId preserve replay의 duplicate/idempotency 결과

- 문제: PaySim eventId는 deterministic이므로 같은 API/DB에 반복 replay하면 `409 DUPLICATE_TRANSACTION_EVENT`가 발생할 수 있습니다.
- 원인: app-api가 `transaction_event_receipts.event_id` unique constraint로 중복 접수를 막습니다.
- 대응: preserve mode는 duplicate/idempotency 검증용으로 유지하고, 409는 `httpDuplicateOrConflict`로 집계합니다.
- 검증: 409 aggregation fixture test.
- 남은 한계: 409 response body를 자세히 해석하지 않고 duplicate/conflict bucket으로 집계합니다.

## V2 Phase 5. eventId prefix를 무분별하게 쓰면 idempotency 검증이 어려워지는 문제

- 문제: 매번 prefix를 바꾸면 중복 replay 방어를 검증하기 어렵습니다.
- 원인: prefix mode는 collision 회피에는 유용하지만 preserve replay와 목적이 다릅니다.
- 대응: `idempotency-mode=prefix`일 때만 `--event-id-prefix`를 허용하고, prefix가 없으면 실패합니다.
- 검증: prefix mode fixture test.
- 남은 한계: prefix naming convention은 운영 정책이 아니라 local replay convention입니다.

## V2 Phase 5. app-api 미기동 시 불명확한 replay 실패

- 문제: app-api가 꺼져 있으면 Python HTTP error가 그대로 노출되어 어떤 집계가 실패했는지 알기 어렵습니다.
- 원인: connection refused와 timeout을 replay report로 분류하지 않으면 실패 evidence가 남지 않습니다.
- 대응: connection refused는 `connectionError`, timeout은 `timeout`으로 집계하고 bounded failure summary를 남깁니다.
- 검증: timeout fixture test, actual replay는 app-api 실행 중일 때만 수동 수행합니다.
- 남은 한계: 네트워크 DNS 오류와 connection refused는 같은 connection error bucket입니다.

## V2 Phase 5. Replay report에 request/response body나 token이 남을 수 있는 문제

- 문제: report가 payload 전체나 Authorization token을 저장하면 raw identifier, token, label leakage 위험이 커집니다.
- 원인: troubleshooting 편의로 request/response body를 그대로 저장하고 싶어질 수 있습니다.
- 대응: report는 aggregate count, dropped fields, sampled eventIds, bounded failure reason만 저장합니다. Token, request body, response body는 저장하지 않습니다.
- 검증: token/request body/raw identifier absence fixture test.
- 남은 한계: endpoint URL 자체에 secret query parameter를 넣는 사용 방식은 금지해야 합니다.

## V2 Phase 5. Rate limit 없이 replay하면 local API/Kafka/DB를 압박하는 문제

- 문제: sample/full JSONL을 빠르게 replay하면 local Kafka/PostgreSQL 부하가 replay script 의도보다 커질 수 있습니다.
- 원인: JSONL replay는 단순 loop로 구현하면 무제한 POST가 됩니다.
- 대응: `--rate-per-second`를 필수 양수 값으로 검증하고 요청 간 sleep을 둡니다.
- 검증: non-positive rate fixture test.
- 남은 한계: rate limit은 단일 process 기준이며 distributed replay coordination은 제공하지 않습니다.

## V2 Phase 5. PaySim native eventType이 current app-api enum과 충돌하는 문제

- 문제: PaySim sample에는 `CASH_OUT`, `CASH_IN`, `DEBIT`이 포함될 수 있지만 current app-api enum은 `PAYMENT`, `TRANSFER`, `WITHDRAWAL`, `DEPOSIT`만 지원합니다.
- 원인: Phase 5에서는 Java DTO/API enum을 변경하지 않고 replay pipeline만 구현하기로 했습니다.
- 대응: replay script 기본값은 `--event-type-policy current-api`이며 unsupported native type을 HTTP 전송 전에 `UNSUPPORTED_EVENT_TYPE_FOR_CURRENT_API`로 rejected 처리합니다.
- 검증: dry-run에서 first 100 sample 기준 `CASH_OUT=14`, `DEBIT=10`이 `unsupportedEventTypes`에 집계되었습니다.
- 남은 한계: `--event-type-policy preserve`는 native type을 사전 rejected로 집계하지 않습니다. Current app-api가 거부하면 `unsupportedEventTypes`가 아니라 HTTP 4xx/client error로 기록되며, PaySim native type 의미를 보존한 actual replay는 Phase 7 이후 API DTO 확장 또는 Rule V2 contract와 함께 처리합니다.

## V2 Phase 5. Retry outcome count 해석이 모호해지는 문제

- 문제: retry 중간 timeout/5xx를 final outcome counter에 함께 누적하면 한 이벤트가 timeout과 success에 동시에 잡힌 것처럼 보일 수 있습니다.
- 원인: attempt-level count와 event-level final outcome count가 섞일 수 있습니다.
- 대응: `httpSuccess`, `timeout`, `connectionError` 등은 final outcome으로 유지하고, retry attempt는 `retryTimeoutAttempts`, `retryServerErrorAttempts`, `retryConnectionErrorAttempts`로 분리했습니다.
- 검증: 5xx 후 retry success fixture에서 final success만 증가하고 serverError final count는 증가하지 않습니다.
- 남은 한계: retry latency 분포는 Phase 5 report에 포함하지 않습니다.

## V2 Phase 5. Connection error retry 정책

- 문제: app-api가 꺼져 있을 때 connection error를 기본 retry하면 replay가 느려지고 expected failure evidence가 흐려질 수 있습니다.
- 원인: connection refused는 일시 네트워크 오류일 수도 있지만 대부분 local app-api 미기동입니다.
- 대응: 기본 retry는 timeout/5xx에만 적용하고 connection error retry는 `--retry-connection-error`를 명시한 경우에만 수행합니다.
- 검증: connection error no-retry와 opt-in retry fixture test를 추가했습니다.
- 남은 한계: app-api startup 대기 기능은 별도 health wait script가 더 적절합니다.

## V2 Phase 5. Replay token 옵션과 Admin token 정책 혼동

- 문제: replay 대상은 일반 transaction ingest API인데 `admin-token`이라는 이름을 쓰면 Phase 14의 `X-Admin-Token` 정책과 혼동될 수 있습니다.
- 원인: 인증 옵션이 필요한 배포 환경 후보와 current local ingest API 정책이 섞였습니다.
- 대응: 옵션을 `--auth-token`, `--auth-token-env`로 일반화하고 Authorization bearer header 사용 여부만 `authUsed`로 기록합니다. Local ingest API는 token 없이 동작합니다.
- 검증: token value가 report에 남지 않는 fixture test.
- 남은 한계: 운영 인증 방식이 생기면 replay auth option은 해당 gateway/API policy에 맞춰 다시 조정해야 합니다.

## V2 Phase 5. Dataset validation과 replay validation 기준 차이

- 문제: Phase 2/3 processed validation을 통과한 row가 replay 단계에서 reject될 수 있습니다.
- 원인: dataset normalization 기준과 current app-api request contract가 다릅니다. 예를 들어 preprocessing은 PaySim amount `>= 0`을 허용할 수 있지만 app-api는 `amount > 0`을 요구합니다.
- 대응: replay validation은 current app-api contract 기준으로 `amount <= 0`을 rejected 처리한다고 문서화했습니다.
- 검증: replay script amount validation과 app-api DTO contract 확인. `amount="0"`과 `amount="-1"` fixture가 `INVALID_AMOUNT`로 rejected 되는지 확인했습니다.
- 남은 한계: API DTO가 V2 native PaySim fields를 받도록 확장되면 replay validation 기준도 함께 갱신해야 합니다.

## V2 Phase 5. Invalid JSONL을 row-level reject로 오해할 수 있는 문제

- 문제: replay input JSONL 자체가 깨진 경우를 `payloadRejected`로 볼지 script failure로 볼지 해석이 모호할 수 있습니다.
- 원인: JSON parse failure는 정상 object row를 읽기 전에 발생하므로 per-row replay validation catch보다 앞에서 중단됩니다.
- 대응: invalid JSONL은 input corruption으로 간주해 replay를 실패시키고, row-level `payloadRejected`는 정상 JSON object row가 replay contract를 위반한 경우에만 집계한다고 문서화했습니다.
- 검증: `read_jsonl()` parse failure path와 replay validation catch 위치 확인.
- 남은 한계: invalid JSONL을 line-level reject로 계속 진행하는 tolerant mode는 Phase 5 범위에 포함하지 않습니다.

## V2 Phase 6. Replay는 성공했지만 detection result가 없는 문제

- 문제: app-api replay는 성공했지만 Consumer 처리 실패, lag, Redis/DB 오류, 또는 export 누락으로 detection result가 없을 수 있습니다.
- 원인: replay success와 fraud detection result persistence는 서로 다른 비동기 단계입니다.
- 대응: evaluation 기본값은 missing result를 denominator에 포함합니다. Fraud label에 result가 없으면 FN, non-fraud label에 result가 없으면 TN으로 계산하고 `missingResults`를 증가시킵니다.
- 검증: fixture test에서 missing fraud result는 FN, missing non-fraud result는 TN으로 집계되는지 확인했습니다. Report에는 `missingResultTreatment`와 missing result warning을 기록합니다.
- 남은 한계: missing result의 원인이 Consumer lag인지 export 누락인지는 evaluation script만으로 판별하지 않습니다.

## V2 Phase 6. Label sidecar를 replay payload나 result export에 섞을 수 있는 문제

- 문제: `isFraud`가 replay payload나 detection result export에 들어가면 evaluation leakage가 발생합니다.
- 원인: label sidecar와 runtime event가 같은 eventId를 공유하므로 수동 export/merge 과정에서 label field가 섞일 수 있습니다.
- 대응: evaluator는 detection result export에 `isFraud`, `isFlaggedFraud`, `sourceFlaggedFraud`가 있으면 실패합니다. Label sidecar는 evaluation input으로만 문서화했습니다.
- 검증: fixture test에서 result file에 `isFraud`가 있으면 `EvaluationError`가 발생하는지 확인했습니다.
- 남은 한계: 외부 DB/API export 도구가 label을 섞지 않는지는 별도 review와 export contract 검증이 필요합니다.

## V2 Phase 6. eventId prefix replay 결과가 label과 join되지 않는 문제

- 문제: prefix replay를 사용하면 detection result eventId가 `local-smoke-paysim-...` 형태가 되어 original PaySim label eventId와 join되지 않을 수 있습니다.
- 원인: replay collision 회피용 prefix는 API/DB 저장 eventId를 바꾸지만 label sidecar는 원본 deterministic eventId를 유지합니다.
- 대응: evaluator에 `--event-id-prefix`를 추가해 detection result eventId에서 prefix를 제거한 뒤 join합니다. Replay report에 `eventIdPrefix`가 있으면 기본값으로 사용할 수 있습니다.
- 검증: fixture test에서 `local-smoke-paysim-1` 결과가 `paysim-1` label과 join되는지 확인했습니다. Report에는 `matchedResults`, `unmatchedResults`와 unmatched result warning을 기록합니다.
- 남은 한계: prefix가 여러 번 중첩된 결과나 임의 eventId rewrite는 지원하지 않습니다.

## V2 Phase 6. Replay rejected event를 denominator에 포함하는 문제

- 문제: current app-api enum 미지원 type처럼 HTTP 요청 전 rejected된 event를 evaluation denominator에 포함하면 model/rule 성능처럼 metric이 왜곡됩니다.
- 원인: replay validation 단계에서 제외된 row는 Consumer와 Rule Engine에 도달하지 않습니다.
- 대응: replay report가 있으면 pre-HTTP rejected eventId를 denominator에서 제외합니다.
- 검증: fixture test에서 `UNSUPPORTED_EVENT_TYPE_FOR_CURRENT_API` failure event가 `excludedReplayRejected`로 제외되는지 확인했습니다. Report에는 `replayPayloadRejected`, `replayRejectedEventIdsAvailable`, `replayRejectedExclusionComplete`를 기록합니다.
- 남은 한계: Phase 5 replay report의 `failures`는 bounded summary이므로 모든 rejected eventId가 없을 수 있습니다. 이 경우 evaluator는 warning을 남기며, full exclusion이 필요하면 replay report contract 확장이 필요합니다.

## V2 Phase 6. Duplicate label/result eventId를 warning으로 넘기는 문제

- 문제: duplicate label eventId는 denominator를 왜곡하고, duplicate result eventId는 어떤 riskLevel을 metric에 사용할지 모호하게 만듭니다.
- 원인: evaluation 초기 구현은 duplicate eventId를 strict mode에서만 실패시키고 non-strict에서는 warning으로 넘길 수 있었습니다.
- 대응: duplicate label/result eventId는 strict 여부와 무관하게 항상 실패하도록 변경했고, Makefile evaluation target은 `--strict`를 기본으로 실행합니다.
- 검증: duplicate label/result fixture가 `strict=False`에서도 실패하는지 확인했습니다.
- 남은 한계: 중복 원인은 export SQL, admin API pagination, replay prefix 정책을 별도로 확인해야 합니다.

## V2 Phase 6. Metric denominator가 0일 때 division error가 나는 문제

- 문제: positive prediction이 없거나 evaluated event가 0이면 precision/recall/accuracy 계산에서 0으로 나누는 문제가 생깁니다.
- 원인: small sample이나 current rule output에서는 특정 denominator가 쉽게 0이 될 수 있습니다.
- 대응: denominator가 0이면 metric을 `null`로 기록하고 warning을 남깁니다.
- 검증: fixture test에서 denominator 0일 때 metric이 `null`인지 확인했습니다.
- 남은 한계: `null` metric은 "0점"이 아니라 계산 불가이므로 evidence 문서에서 별도로 해석해야 합니다.

## V2 Phase 6. 1,000건 sample metric을 전체 PaySim 성능처럼 과장하는 문제

- 문제: committed sample evaluation 결과를 전체 PaySim 성능 또는 production fraud model 성능처럼 해석할 수 있습니다.
- 원인: sample은 repository review와 pipeline validation을 위한 1,000건 이하 subset이며 class distribution도 balanced strategy 영향을 받습니다.
- 대응: docs와 blog에 sample metric은 pipeline validation evidence이며 대표 성능이 아니라고 기록했습니다.
- 검증: evidence plan과 roadmap limitation에 해당 기준을 추가했습니다.
- 남은 한계: full PaySim evaluation evidence는 local processed output과 detection result export가 준비된 뒤 별도 evidence 단계에서 기록해야 합니다.

### 문제 상황

로컬 Docker Compose 기반 k6 결과는 노트북 CPU, 메모리, Docker resource limit, JVM warmup, Kafka/DB/Redis container 상태에 크게 영향을 받습니다.

### 판단

Phase 12 결과는 절대 성능 수치가 아니라 병목 후보와 관측 절차를 설명하는 evidence로 사용합니다. 측정하지 않은 수치는 `TBD`로 두고 임의로 작성하지 않습니다.

### 남은 한계

운영 환경의 성능 기준은 별도 hardware, partition 수, Consumer 수, DB/Redis resource 기준에서 다시 산정해야 합니다.

---

## Phase 12. k6 threshold를 공격적으로 잡지 않은 이유

### 문제 상황

초기부터 매우 낮은 p95/p99 threshold를 적용하면 애플리케이션 병목보다 로컬 환경 흔들림 때문에 테스트가 불안정해질 수 있습니다.

### 판단

Normal load는 p95 500ms, p99 1000ms를 초기 목표로 두고, Peak load는 p95 1000ms, p99 2000ms를 기준으로 둡니다. 실패 시 threshold 자체를 숨기지 않고 원인과 병목 후보를 `docs/22-load-test-results.md`에 기록합니다.

### 다시 설계한다면

실제 측정 결과가 충분히 쌓인 뒤 scenario별 threshold를 재조정합니다.

---

## Phase 12. Consumer Lag metric 부재로 인한 한계

### 문제 상황

Phase 12 k6 script는 API latency와 request failure를 직접 측정할 수 있지만, Consumer Lag metric은 아직 dashboard/alert로 연결되지 않았습니다.

### 판단

현재는 Kafka UI, processing log, fraud result 조회, Redis degraded metric을 함께 사용해 비동기 처리 영향을 해석합니다. Consumer Lag metric과 Grafana dashboard는 후속 Observability Hardening 범위로 둡니다.

### 남은 한계

Consumer Lag 회복 시간과 partition별 hot spot은 별도 metric 연결 후 더 정확하게 측정해야 합니다.

---

## Phase 1. Gradle repository 정책 충돌

### 초기 설계

`settings.gradle`에서 dependency repository를 중앙 관리하고, root `build.gradle`의 `subprojects`에서도 `mavenCentral()`을 선언했습니다.

### 발생한 문제

`./gradlew clean build` 실행 시 `RepositoriesMode.FAIL_ON_PROJECT_REPOS` 정책과 subproject repository 선언이 충돌했습니다.

### 재현 방법

```bash
./gradlew clean build
```

### 원인 분석

settings-level repository 정책이 project-level repository 선언을 금지하는 상태에서 root build script가 repository를 중복 선언했습니다.

### 변경한 설계

repository 선언은 `settings.gradle`의 `dependencyResolutionManagement`로 고정하고, root `build.gradle`의 subproject repository 선언을 제거했습니다.

### 개선 결과

`./gradlew clean build`와 module test task가 통과했습니다.

### 남은 한계

현재 test source는 아직 없어서 test task는 `NO-SOURCE`로 통과합니다.

---

## Phase 1. Kafka Docker image tag와 CLI 경로 불일치

### 초기 설계

Kafka container image는 `bitnami/kafka:3.7`을 사용하고, topic script와 healthcheck는 `kafka-topics.sh`가 PATH에 있다고 가정했습니다.

### 발생한 문제

Docker image pull 단계에서 `bitnami/kafka:3.7` tag를 찾지 못했고, Apache Kafka image로 변경한 뒤에는 `kafka-topics.sh`가 PATH에 없어 healthcheck와 topic script가 실패했습니다.

### 재현 방법

```bash
docker compose -f infra/docker-compose.yml up -d
./scripts/create-topics.sh
```

### 원인 분석

로컬에서 사용할 image tag가 실제 registry tag와 맞지 않았고, `apache/kafka:3.7.0` image의 Kafka CLI는 `/opt/kafka/bin` 아래에 위치합니다.

### 변경한 설계

- Kafka image를 `apache/kafka:3.7.0`으로 변경했습니다.
- Docker Compose healthcheck와 topic 관련 scripts에서 `/opt/kafka/bin/kafka-topics.sh`를 명시적으로 사용하도록 수정했습니다.

### 개선 결과

Docker Compose 기동 후 Kafka container가 healthy 상태가 되었고, 다음 topic들이 생성되는 것을 확인했습니다.

```text
transaction-events
fraud-risk-events
fraud-alert-events
transaction-events.retry
transaction-events-dlt
```

### 남은 한계

로컬 환경은 single broker이므로 replication factor는 1입니다. 운영 환경 수준의 multi-broker replication은 문서 설계로만 유지합니다.

---

## Phase 1. Kafka advertised listener와 Kafka UI 연결

### 초기 설계

Kafka advertised listener를 `localhost:9092` 중심으로만 두었습니다.

### 발생한 문제

Docker network 내부의 Kafka UI가 `localhost:9092`를 사용하면 Kafka UI container 자기 자신을 바라보게 되어 broker 연결이 불안정해질 수 있습니다.

### 재현 방법

```bash
docker compose -f infra/docker-compose.yml up -d
docker compose -f infra/docker-compose.yml logs kafka-ui --tail=100
```

### 원인 분석

host에서 실행하는 Spring Boot 앱과 Docker network 내부 서비스가 Kafka broker를 바라보는 주소가 다릅니다.

### 변경한 설계

- host app용 external listener: `localhost:9092`
- Docker network 내부용 internal listener: `kafka:29092`
- Kafka UI bootstrap server: `kafka:29092`

### 개선 결과

Kafka, Kafka UI, topic 생성 script가 동일 Compose 환경에서 정상 동작했습니다.

### 남은 한계

향후 app-api/app-consumer를 Docker Compose 내부에서 실행하는 profile을 추가하면 Spring Boot Kafka bootstrap server도 internal listener를 사용하도록 profile 분리가 필요합니다.

---

## Phase 1. app-consumer health endpoint 검증 불가

### 초기 설계

`app-consumer`는 Kafka Consumer worker로 준비되어 있고 Actuator dependency와 `server.port: 8081` 설정을 가지고 있었습니다.

### 발생한 문제

`app-consumer`가 웹 애플리케이션으로 뜨지 않아 시작 직후 종료되었고, `/actuator/health` HTTP 검증을 수행할 수 없었습니다.

### 재현 방법

```bash
./gradlew :app-consumer:bootRun
curl http://localhost:8081/actuator/health
```

### 원인 분석

`app-consumer`에 `spring-boot-starter-web`이 없어 embedded web server가 생성되지 않았습니다. Actuator dependency만으로는 HTTP endpoint가 열리지 않습니다.

### 변경한 설계

Phase 1의 health endpoint 검증을 위해 `app-consumer`에 `spring-boot-starter-web`을 추가했습니다. 실제 Kafka listener 비즈니스 로직은 구현하지 않았습니다.

### 개선 결과

`app-consumer`가 8081 포트로 기동하고 `/actuator/health`가 `UP`을 반환했습니다. Prometheus도 `app-consumer` target을 `up`으로 scrape했습니다.

### 남은 한계

실제 Consumer processing, manual ack, EventProcessingLog 저장은 다음 Phase 이후에 구현해야 합니다.

---

## Phase 2. app-common test에서 AssertJ 의존성 누락

### 초기 설계

`app-common`은 공유 event schema와 enum만 포함하고, 테스트에서는 AssertJ assertion을 사용했습니다.

### 발생한 문제

`./gradlew test` 실행 시 `app-common` test compile 단계에서 AssertJ package를 찾지 못했습니다.

### 재현 방법

```bash
./gradlew test
```

### 원인 분석

`app-common`은 Spring Boot starter test를 사용하지 않고 `org.junit.jupiter:junit-jupiter`만 test dependency로 둡니다. 따라서 AssertJ는 test classpath에 포함되지 않았습니다.

### 변경한 설계

불필요한 test dependency를 추가하지 않고, `TransactionEventMessageTest`를 JUnit 기본 assertion으로 수정했습니다.

### 개선 결과

`./gradlew test`가 통과했습니다.

### 남은 한계

현재 `app-common` test는 event schema의 기본 필드 계약만 확인합니다. schema compatibility test는 Kafka producer/consumer 구현 이후 확장합니다.

---

## Phase 3. Kafka publish 실패 시 receipt 상태 보존

### 초기 상태

거래 이벤트 접수 흐름은 request validation, receipt 저장, Kafka publish, accepted response 순서로 설계했습니다.

### 발생한 문제

DB receipt 저장 후 Kafka publish가 실패하면 API는 실패를 반환해야 하지만, 운영 추적을 위해 실패한 receipt 상태도 남아야 합니다.

### 재현 방법

```bash
make test
```

`TransactionEventControllerTest`에서 Producer mock이 `KafkaPublishFailedException`을 던지도록 설정합니다.

### 원인 분석

receipt 저장과 Kafka publish를 하나의 원자적 transaction으로 묶는 Outbox Pattern은 Phase 3 범위가 아닙니다. 일반 runtime exception으로 rollback되면 `PUBLISH_FAILED` 상태도 사라질 수 있습니다.

### 수정 내용

`TransactionEventIntakeService`에서 Kafka publish 실패 시 receipt를 `PUBLISH_FAILED`로 변경하고 API는 `KAFKA_PUBLISH_FAILED` 503을 반환하도록 했습니다. 해당 exception은 transaction rollback 대상에서 제외했습니다.

### 검증 명령

```bash
make test
```

### 남은 한계

`PUBLISH_FAILED` receipt를 자동으로 재발행하는 outbox publisher는 아직 없습니다. 후속 hardening 후보로 둡니다.

---

## Phase 3. 중복 eventId와 idempotency 정책 분리

### 초기 상태

`eventId`는 transaction event의 중복 방어 기준입니다.

### 발생한 문제

중복 `eventId` 요청을 기존 receipt replay처럼 반환할지, 명확한 conflict로 처리할지 결정이 필요했습니다.

### 재현 방법

```bash
make test
```

동일 `eventId`로 `POST /api/v1/transactions/events`를 두 번 호출합니다.

### 원인 분석

동일 body 비교와 replay response는 별도 idempotency 정책이 필요합니다. Phase 3은 idempotent replay 구현 단계가 아니라 Producer intake 구현 단계입니다.

### 수정 내용

Phase 3에서는 중복 `eventId`를 `409 CONFLICT`와 `DUPLICATE_TRANSACTION_EVENT`로 처리합니다.

### 검증 명령

```bash
make test
```

### 남은 한계

idempotent replay 정책은 필요성이 확인되면 별도 Phase에서 설계합니다.

---

## Phase 3. Outbox 미적용에 따른 양방향 불일치 가능성

### 초기 상태

Phase 3에서는 `transaction_event_receipts` 저장과 Kafka publish를 하나의 원자적 작업으로 묶는 Outbox Pattern을 구현하지 않습니다.

### 발생한 문제

다음 두 방향의 불일치 가능성이 남습니다.

- receipt 저장은 성공했지만 Kafka publish가 실패하는 경우
- Kafka publish는 성공했지만 `PUBLISHED` 상태 저장 또는 DB commit이 실패하는 경우

### 원인 분석

DB transaction과 Kafka publish는 서로 다른 시스템의 작업입니다. Outbox Pattern, Kafka transaction과 DB transaction 연계, 또는 발행 감사 테이블 기반 보정 작업이 없으면 완전한 원자성을 보장할 수 없습니다.

### 수정 내용

- Kafka publish 실패는 receipt status를 `PUBLISH_FAILED`로 남기고 API는 503을 반환합니다.
- Kafka publish 성공 후 DB commit 실패 가능성은 문서화하고 후속 hardening 대상으로 남깁니다.
- `PUBLISH_FAILED` receipt 재요청은 Phase 3에서 `409 CONFLICT`로 처리하며 자동 재발행은 지원하지 않습니다.

### 검증 명령

```bash
make test
```

### 남은 한계

Outbox publisher 또는 발행 감사 테이블 기반 보정 작업은 아직 없습니다.

---

## Phase 3. 시간 기준과 실패 메시지 저장 정책

### 초기 상태

Application clock은 server default timezone을 사용했고, Entity lifecycle timestamp는 `OffsetDateTime.now()`를 직접 호출했습니다. Kafka publish 실패 메시지는 receipt에 저장됩니다.

### 발생한 문제

- 서버 timezone에 따라 `receivedAt` 기준이 흔들릴 수 있습니다.
- Entity lifecycle timestamp와 service clock 기준이 완전히 같지 않습니다.
- 실패 메시지에 긴 외부 예외 메시지나 민감한 세부 정보가 섞일 수 있습니다.

### 수정 내용

- Application clock은 `Clock.systemUTC()`로 변경했습니다.
- `publish_error_message`는 raw payload를 저장하지 않는 요약 메시지로 제한하고 500자 이내로 truncate합니다.

### 남은 한계

Entity lifecycle timestamp는 아직 JPA callback 기준입니다. JPA auditing 또는 service clock 주입 방식으로 timestamp 기준을 통일하는 작업은 후속 개선 대상으로 둡니다.

---

## Phase 4-A. Consumer 구현 전 Minimum CI Gate 선반영

### 문제 상황

Phase 3까지는 로컬에서 `make test`, `make build`, `make final-check`를 직접 실행하며 검증했습니다. 하지만 Phase 4부터 Kafka Consumer, manual ack, processing log, 이후 Redis Sliding Window와 Retry/DLT가 추가될 예정이라 로컬 검증만으로는 회귀 버그를 안정적으로 막기 어렵다고 판단했습니다.

### 원인

Consumer 작업은 offset commit 시점, DB 저장 성공 여부, 중복 offset 처리처럼 작은 변경에도 동작이 달라질 수 있는 영역입니다. 자동화된 CI Gate가 없으면 main 브랜치에 깨진 코드가 들어갈 위험이 커집니다.

### 판단

Consumer 구현 전에 GitHub Actions 기반 최소 CI Gate를 먼저 추가했습니다. push와 pull request 시점에 `make ci-check`를 자동 실행하여 기본 회귀 검증을 자동화했습니다.

### 선택한 방식

초기 CI는 Java 17, Gradle cache, `./gradlew test`, `./gradlew assemble` 중심의 `make ci-check`로 구성했습니다. `workflow_dispatch`로 수동 재검증을 허용하고, workflow 권한은 `contents: read`로 제한했습니다. Docker Compose 기반 Kafka/PostgreSQL/Redis 통합 검증은 개발 속도와 안정성을 고려해 후속 Phase로 분리했습니다.

### 트레이드오프

초기 CI는 빠르고 안정적이지만 Kafka end-to-end 처리, Consumer Lag, Redis 장애, DLT 재처리까지 검증하지는 못합니다. 대신 후속 Phase에서 `ci-integration.yml` 또는 nightly workflow로 확장할 수 있도록 범위를 분리했습니다.

### 검증 기준

- push 시 GitHub Actions CI가 실행됩니다.
- unit test와 artifact assembly가 통과해야 합니다.
- CI 실패 시 main merge를 보류합니다.
- 인프라 의존 검증은 로컬 `make infra-up`, `make api`, `make consumer`로 별도 수행합니다.

## Phase 4. Consumer Manual Ack and Processing Log

### 문제 상황

Kafka Consumer에서 메시지를 처리했지만 offset commit 시점과 DB 저장 시점의 순서가 명확하지 않으면 processing log 누락 또는 중복 처리가 발생할 수 있습니다.

### 원인

auto commit을 사용하면 DB 저장 실패와 무관하게 offset이 commit될 수 있습니다. 반대로 DB 저장 성공 후 ack 직전에 Consumer가 죽으면 같은 offset이 재소비될 수 있습니다.

### 판단

Phase 4에서는 `enable-auto-commit=false`와 `AckMode.MANUAL_IMMEDIATE`를 사용하고, `event_processing_logs` 저장 성공 후 acknowledge를 수행합니다. 즉시 commit 의도를 코드에서 명확히 표현하기 위해 `MANUAL` 대신 `MANUAL_IMMEDIATE`를 선택했습니다.

Consumer가 처음 생성된 group ID로 시작될 때 이미 topic에 쌓인 미처리 이벤트를 읽을 수 있도록 local 설정은 `auto-offset-reset=earliest`로 둡니다.

### 대안

- auto commit 사용
- listener 진입 즉시 ack
- DB 저장 성공 후 ack

### 선택

DB 저장 성공 후 ack

### 트레이드오프

DB 저장은 성공했지만 ack 직전에 Consumer가 죽으면 같은 offset이 재소비될 수 있습니다. 이를 `(topic, partition_no, offset_no)` unique constraint와 service의 duplicate skip 정책으로 방어합니다.

Flyway migration은 `app-api`를 schema owner로 두고, app-consumer runtime은 Flyway를 실행하지 않고 JPA validate만 수행합니다. 같은 DB를 바라보는 두 애플리케이션이 각각 Flyway를 실행하면 checksum drift로 한쪽 서비스가 부팅 실패할 수 있기 때문입니다. app-consumer module test에서는 H2 검증을 위해 test resources에만 migration을 둡니다.

### 검증

- processing log 저장 성공 시 ack 호출 테스트
- 이미 처리된 offset 재소비 시 ack 호출 테스트
- 저장 실패 시 ack 미호출 테스트
- 동일 offset 재처리 시 duplicate log가 생성되지 않는지 검증
- 같은 eventId라도 offset이 다르면 별도 log가 생성되는지 검증
- eventId 조회 시 `processedAt desc` 정렬 검증

### 남은 한계

- Retry/DLT는 Phase 9 범위입니다.
- FraudResult 저장과 eventId 기준 idempotency는 Phase 5 이후 범위입니다.
- `FAILED` processing status는 Phase 4에서는 예약 상태입니다. DB 자체가 실패 로그를 저장할 수 없는 경우에는 ack하지 않고 재소비 가능성을 열어두며, 저장 가능한 business failure와 DLT 기록은 Phase 9에서 구체화합니다.

## Phase 5. Fraud Result 저장과 Consumer Ack 순서

### 문제 상황

Consumer가 메시지를 처리한 뒤 processing log와 fraud result를 모두 저장해야 했습니다. 하지만 어느 시점에 ack를 호출해야 하는지에 따라 탐지 결과 누락 또는 중복 저장 가능성이 달라졌습니다.

### 원인

ack를 먼저 호출하면 fraud result 저장 실패 시 Kafka 재소비가 불가능해질 수 있습니다. 반대로 fraud result 저장 후 ack 직전에 Consumer가 죽으면 같은 이벤트가 재소비될 수 있습니다.

### 판단

Phase 5에서는 processing log 저장, Rule Engine 평가, fraud result 저장이 모두 성공한 뒤 ack를 호출합니다. ack 직전 장애로 인한 재소비는 `event_id` unique constraint로 중복 저장을 방어합니다.

Phase 5에서는 processing log와 fraud result를 하나의 DB transaction으로 묶지 않고, Consumer 재소비와 idempotent 저장으로 복구 가능하게 설계했습니다. 따라서 processing log 저장 후 fraud result 저장 전에 장애가 발생하면 일시적으로 processing log만 존재할 수 있습니다. 이 경우 ack가 호출되지 않으므로 Kafka 재소비가 발생하고, processing log는 `(topic, partition_no, offset_no)` unique constraint로 duplicate skip되며, fraud result 저장을 다시 시도합니다.

### 선택

PostgreSQL `event_id` unique constraint를 최종 정합성 기준으로 두고, Consumer 로직은 duplicate event를 정상 처리 가능한 idempotent 흐름으로 구성했습니다.

`existsByEventId()`는 불필요한 insert 시도를 줄이기 위한 fast path이며, 최종 중복 방어는 PostgreSQL `event_id` unique constraint가 담당합니다. 동시 Consumer 또는 재소비 상황에서 race condition이 발생해도 unique constraint 충돌을 duplicate result로 처리합니다.

### 트레이드오프

eventId 기준으로 하나의 탐지 결과만 저장하므로, rule version이 바뀐 뒤 동일 event를 재평가하는 시나리오는 별도 result versioning이 필요합니다. 이는 후속 Phase에서 보완합니다.

`matched_rules`는 Phase 5에서 comma-separated text로 저장합니다. rule code rename 또는 과거 데이터에 남은 unknown rule code가 생기면 enum 변환 기반 조회가 깨질 수 있으므로, 후속 Phase에서 JSONB, rule code version, unknown rule code 응답 정책을 검토합니다.

## Phase 5. Rule Engine v1 분리와 Redis 제외

### 문제 상황

Listener에 rule 판단과 저장 로직을 직접 넣으면 ack 시점, rule 변경, 저장 idempotency가 한 메서드에 섞일 수 있었습니다.

### 판단

Listener는 orchestration만 담당하고, Rule Engine은 순수 rule 평가, FraudDetectionResultService는 저장과 idempotency만 담당하도록 분리했습니다.

Redis Sliding Window는 사용자 최근 거래 패턴을 보려면 필요하지만, Phase 5에서는 단건 이벤트 기반 rule만 먼저 구현했습니다. 이렇게 하면 Consumer ack와 result persistence의 정합성을 먼저 검증한 뒤 Redis 장애/degraded mode를 다음 단계에서 좁게 다룰 수 있습니다.

### 검증

- Rule Engine unit test
- fraud result duplicate eventId test
- fraud result 저장 실패 시 ack 미호출 test
- rule engine 예외 시 ack 미호출 test
- processing log 저장 실패 시 fraud result 저장 미시도 test

## Phase 6. Redis Sliding Window 장애 시 Consumer Ack 정책

### 문제 상황

Sliding Window 탐지를 위해 Redis를 호출하면 Redis 장애가 Consumer 처리 실패로 이어질 수 있었습니다. 이 경우 Redis가 잠깐 불안정해도 Kafka Lag이 증가하고 정상 이벤트 처리까지 지연될 수 있습니다.

### 원인

Redis는 최근 거래 횟수와 누적 금액을 빠르게 계산하기 위한 보조 상태 저장소입니다. 최종 fraud result 저장과 eventId 중복 방어 기준은 PostgreSQL `fraud_detection_results.event_id` unique constraint입니다.

### 판단

Phase 6에서는 Redis 장애를 전체 Consumer 실패가 아니라 탐지 민감도 저하로 처리합니다. Redis store가 예외를 만나면 `RecentTransactionWindowResult.degraded`를 반환하고, Rule Engine은 Redis 의존 rule을 `skipped_rules`에 기록한 뒤 stateless rule만 평가합니다.

이미 `fraud_detection_results`에 같은 `eventId` 결과가 존재하면 Redis window를 갱신하지 않고 duplicate fraud result로 ack합니다. 이는 동일 eventId의 conflict replay가 Redis Hash metadata를 덮어써 보조 상태를 오염시키는 일을 막기 위한 fast path입니다. 최종 중복 방어는 여전히 PostgreSQL unique constraint가 담당합니다.

### 선택

Redis 장애 시 fraud result를 `degraded=true`로 저장하고 ack를 허용합니다. PostgreSQL fraud result 저장 실패, processing log 저장 실패, Rule Engine 자체 예외는 기존처럼 ack하지 않습니다.

### 트레이드오프

Redis 장애 중에는 `RAPID_TRANSACTION_COUNT`, `WINDOW_AMOUNT_SUM` 탐지가 누락될 수 있습니다. 대신 Consumer가 중단되지 않고, 어떤 rule이 생략됐는지 운영 조회 API와 reason으로 확인할 수 있습니다.

### 검증

- Redis degraded window result가 들어와도 fraud result 저장 후 ack 호출
- duplicate fraud result fast path에서는 Redis store, Rule Engine, fraud result save 미호출
- fraud result 저장 실패 시 ack 미호출
- processing log 저장 실패 시 Redis store와 fraud result 저장 미호출
- Rule Engine에서 Redis degraded 시 stateful rule score 미반영과 skipped rule 기록

## Phase 6. eventTime 기준 window와 중복 eventId 처리

### 문제 상황

Sliding Window를 시스템 현재 시각 기준으로 계산하면 오래된 이벤트 재처리나 지연 소비 시 탐지 결과가 왜곡될 수 있습니다. 또한 같은 `eventId`가 재소비될 때 Redis count가 중복 증가할 수 있습니다.

### 판단

window 기준은 `eventTime`으로 두었습니다. Redis ZSET score는 `eventTime` epoch millis이고 member는 `eventId`입니다. 같은 eventId를 다시 기록하면 ZSET member가 update되므로 count 중복 증가를 완화할 수 있습니다.

### 선택

- user window key: `fraud:tx:user:{userId}:events`
- event metadata key: `fraud:tx:event:{eventId}`
- sorted set member: `eventId`
- hash metadata: `amount`, `currency`, `eventTime`, `userId`
- TTL: window보다 긴 10분

### 트레이드오프

Hash key 수가 늘어나지만 amount 합산을 위해 ZSET member parsing을 피할 수 있습니다. TTL을 짧게 유지해 오래된 key가 무기한 남지 않도록 했습니다.

### 검증

- 같은 eventId를 두 번 기록해도 Redis window count가 1로 유지되는 테스트
- window 밖 이벤트가 count/sum에서 제외되는 테스트
- Redis store 예외 시 degraded result 반환 테스트

## Phase 6. Redis multi-command 부분 실패와 window count 기준

### 문제 상황

Redis Sliding Window 저장은 Hash metadata 저장, ZSET 추가, cleanup, TTL 갱신, window 조회처럼 여러 명령으로 구성됩니다. 중간 실패가 발생하면 ZSET에는 eventId가 있지만 Hash amount가 없는 불완전 상태가 남을 수 있습니다.

### 판단

Phase 6에서 Redis Lua나 transaction까지 도입하면 구현 범위가 커집니다. 대신 Hash metadata를 먼저 저장한 뒤 ZSET에 추가하고, window 계산 시 amount metadata가 있는 eventId만 count와 amount sum에 포함합니다.

### 선택

- `HSET fraud:tx:event:{eventId}`를 먼저 수행
- 그 다음 `ZADD fraud:tx:user:{userId}:events`
- `ZRANGEBYSCORE` 결과 중 amount Hash가 없는 eventId는 count/sum에서 제외
- Redis 명령 예외는 degraded result로 반환

### 트레이드오프

완전한 원자성은 아닙니다. 다만 metadata 없는 ZSET member가 탐지 count를 부풀리는 문제를 막고, 실제 Redis transaction/Lua는 후속 hardening 범위로 남깁니다.

### 검증

- Hash metadata 저장 후 ZSET 추가 순서 검증
- metadata 없는 ZSET member가 count와 amount sum에서 제외되는지 검증
- TTL 갱신 호출 검증

## Phase 6. V4 migration의 H2 호환 문법

### 문제 상황

`fraud_detection_results`에 `skipped_rules`, `degraded` 컬럼을 추가하는 V4 migration이 PostgreSQL 문법으로는 자연스러웠지만 H2 테스트에서 syntax error가 발생했습니다.

### 원인

H2 PostgreSQL mode가 하나의 `alter table` 안에서 여러 `add column` 절을 comma로 연결하는 문법을 처리하지 못했습니다.

### 선택

`alter table ... add column` 문을 컬럼별로 분리했습니다. 이 방식은 PostgreSQL과 H2 테스트 환경 모두에서 동작합니다.

### 검증

- `./gradlew :app-consumer:test` 재실행 후 PASS
- `./gradlew :app-api:test` 실행 후 PASS

## Phase 7. Redis Integration Test를 기본 CI에서 분리한 이유

### 문제 상황

Redis Sliding Window는 실제 Redis 자료구조로 검증할 필요가 있었지만, Docker 기반 integration test를 기본 CI에 바로 포함하면 CI 시간이 증가하고 환경 의존성이 커질 수 있었습니다.

### 판단

Phase 7에서는 Redis integration test를 추가하되 기본 `make ci-check`와 분리했습니다. 기본 CI는 빠른 회귀 검증을 담당하고, Redis integration test는 `make redis-integration-test`로 필요할 때 실행합니다. 해당 target은 Docker Compose Redis readiness를 확인한 뒤 Gradle integration test를 실행합니다.

### 선택

Testcontainers Redis를 먼저 시도했지만 로컬 Docker Desktop provider API 호환 문제로 Testcontainers가 유효한 Docker environment를 찾지 못했습니다. Phase 7에서는 Docker Compose Redis를 띄운 뒤 실제 Redis에 연결하는 integration test로 전환했습니다. 테스트는 Redis database index `15`를 사용하고, 테스트 시작 전 해당 DB만 초기화합니다.

### 트레이드오프

기본 CI만으로는 실제 Redis ZSET/Hash 동작을 검증하지 못합니다. 대신 Docker가 가능한 로컬 또는 별도 workflow에서 `make redis-integration-test`를 실행해 실제 Redis 기준 검증을 수행할 수 있습니다.

### 검증

- `make redis-integration-test` PASS
- 실제 Redis DB 15 기준 ZSET/Hash 저장, duplicate eventId, TTL, cleanup, metadata exclusion 검증

## Phase 7. Metric tag에 고유 식별자를 넣지 않은 이유

### 문제 상황

Redis degraded, skipped rule, latency metric을 추가하면서 eventId, traceId, userId를 tag로 넣으면 개별 이벤트 추적은 쉬워질 수 있었습니다.

### 판단

Prometheus metric은 집계와 추세 관측을 위한 데이터입니다. eventId, traceId, userId는 cardinality가 높고 운영 환경에서는 식별자 노출 위험도 있습니다.

### 선택

Metric tag에는 낮은 cardinality의 `rule`만 사용합니다. 개별 이벤트 추적은 structured log의 `traceId`와 `eventId`로 수행합니다.

### 검증

- `fraud.rule.skipped.total{rule=RAPID_TRANSACTION_COUNT}` 형태로 skipped rule metric 확인
- eventId/userId/traceId tag 미사용 확인

## Phase 7. Redis latency 측정 위치

### 문제 상황

Redis command latency를 개별 HSET/ZADD/ZRANGE마다 측정할지, window store 호출 전체를 측정할지 결정해야 했습니다.

### 판단

현재 Phase의 목표는 Consumer가 Redis Sliding Window 처리에 얼마나 묶이는지 관측하는 것입니다. 따라서 Hash 저장, ZSET 갱신, cleanup, TTL, window 조회를 포함하는 `recordAndGetWindow` 전체 시간을 Timer로 측정했습니다.

### 선택

`fraud.redis.window.record.latency` Timer를 `RedisRecentTransactionWindowStore.recordAndGetWindow` 경계에 적용했습니다.

### 트레이드오프

개별 Redis command 병목은 아직 분리해 볼 수 없습니다. 필요하면 후속 Phase에서 command별 metric 또는 Redis client metric을 추가합니다.

## Phase 7. Degraded metric이 alert 후보가 되는 이유

### 문제 상황

Redis 장애는 Consumer 전체 실패가 아니라 degraded mode로 처리됩니다. 따라서 API/Consumer가 계속 정상처럼 보이더라도 Redis 기반 rule이 계속 skip될 수 있습니다.

### 판단

degraded count와 skipped rule count는 탐지 민감도 저하를 알려주는 핵심 신호입니다. 로그만으로는 추세와 alert를 만들기 어렵기 때문에 metric으로 집계합니다.

### 선택

- `fraud.redis.window.degraded.total`
- `fraud.detection.degraded.total`
- `fraud.rule.skipped.total`

위 metric을 추가하고 Prometheus/Grafana alert 후보로 문서화했습니다.

## Phase 8. Redis Down Drill에서 ack를 유지한 이유

### 문제 상황

Redis가 중단되면 Sliding Window 기반 stateful rule을 평가할 수 없습니다. 이때 ack를 막으면 Kafka Lag이 증가하고, Redis와 무관한 정상 이벤트까지 Consumer가 처리하지 못할 수 있습니다.

### 판단

Redis는 Source of Truth가 아니므로 Redis 장애는 degraded mode로 처리합니다. Redis 의존 rule은 skipped 처리하고, stateless rule만으로 fraud result를 저장한 뒤 ack를 유지합니다.

### 트레이드오프

Redis 장애 중에는 최근 거래 패턴 기반 탐지가 누락될 수 있습니다. 대신 이벤트 처리와 결과 저장은 유지되어 서비스 가용성을 확보합니다.

### 검증

`redis_down_drill.sh`는 Redis를 중지한 뒤 이벤트를 발행하고, fraud result의 `degraded=true`, `RAPID_TRANSACTION_COUNT`/`WINDOW_AMOUNT_SUM` skipped rule, Prometheus degraded/skipped/latency metric 증가를 확인합니다.

## Phase 8. Consumer 중지 중 발행된 메시지 확인 방법

### 문제 상황

app-consumer가 중지된 동안 app-api는 Kafka publish를 계속 성공할 수 있습니다. 이 경우 이벤트는 Kafka topic에 남고 fraud result는 Consumer 재시작 전까지 생성되지 않습니다.

### 판단

Consumer restart drill은 app-consumer를 먼저 중지한 상태에서 이벤트를 발행하고, app-consumer를 다시 시작한 뒤 fraud result와 processing log가 생성되는지 확인합니다.

### 검증 기준

- Consumer 중지 중 API publish는 `202 Accepted`
- Consumer 재시작 후 fraud result 조회 가능
- processing log에 `PROCESSED` 기록 존재
- 같은 `eventId`에 대한 `fraud_detection_results` row count가 1건

## Phase 8. Kafka Unavailable Drill을 Runbook으로 분리한 이유

### 문제 상황

Kafka broker stop/start는 topic metadata, producer timeout, Consumer reconnect, 로컬 smoke workflow에 모두 영향을 줍니다. 자동 script로 기본 target에 포함하면 로컬 개발 환경을 쉽게 깨뜨릴 수 있습니다.

### 판단

Phase 8에서는 Kafka unavailable을 markdown runbook으로 분리하고, 수동으로 API publish failure와 Consumer reconnect log를 확인합니다.

### 한계

Retry/DLT, DLQ 저장, 자동 reprocess는 이번 Phase 범위가 아닙니다. Kafka 장애 자동 복구 정책은 후속 Retry/DLT Phase에서 구현합니다.

## Phase 8. Drill PASS/FAIL 기준

### Redis Down PASS

- app-api와 app-consumer health endpoint가 drill 시작 전에 응답
- Redis stop 후 이벤트 발행 성공
- fraud result `degraded=true`
- Redis 의존 rule이 `skippedRules`에 포함
- `fraud_redis_window_degraded_total`, `fraud_detection_degraded_total`, `fraud_rule_skipped_total`, `fraud_redis_window_record_latency_seconds_count` 증가 확인
- Redis restart 후 신규 이벤트는 `degraded=false`

### Consumer Restart PASS

- Consumer 중지 중 이벤트가 Kafka에 publish됨
- Consumer 재시작 후 fraud result와 processing log 조회 가능
- `fraud_detection_results` row count가 1건

### Kafka Unavailable PASS

- Kafka 중지 중 API가 publish 성공으로 응답하지 않음
- Kafka 복구 후 topic 조회와 Consumer reconnect가 가능
- 복구 후 신규 이벤트 처리 가능

## Phase 8. Metric만 보지 않고 API/DB 조회를 함께 보는 이유

Metric은 장애 추세를 보여주지만 단일 이벤트가 실제로 저장되었는지, 어떤 rule이 skipped 되었는지, processing log가 남았는지는 보장하지 않습니다. Phase 8 drill은 Prometheus metric 증가와 함께 fraud result API, processing log API, DB row count를 확인해 운영 증거를 남깁니다.

## Phase 9. 어떤 실패를 DLT로 보낼 것인가

### 문제 상황

Consumer 처리 중 모든 예외를 DLT로 보낼지, 일부는 ack하지 않고 Kafka 재소비에 맡길지 결정해야 했습니다.

### 판단

Rule Engine 예외처럼 payload 또는 처리 로직 문제로 같은 실패가 반복될 가능성이 큰 경우는 `transaction-events-dlt`로 격리합니다. Redis 장애는 Phase 6 정책대로 degraded mode로 처리하며 DLT 대상이 아닙니다.

### 트레이드오프

DLT로 보낸 이벤트는 원본 topic에서 ack되므로 무한 재소비를 막을 수 있습니다. 대신 운영자가 DLT 상태와 payload를 확인해 재처리 또는 폐기를 명시적으로 결정해야 합니다.

## Phase 9. DB 장애를 DLT로 보내지 않은 이유

### 문제 상황

Consumer 처리 중 processing log 또는 fraud result 저장이 실패했을 때 이 이벤트를 DLT로 보낼지, ack하지 않고 재소비되게 둘지 결정해야 했습니다.

### 판단

DB 장애는 DLT 저장 자체도 실패할 가능성이 높습니다. 따라서 DB 장애는 DLT로 보내기보다 ack하지 않고 Kafka 재소비를 유도하는 편이 더 안전하다고 판단했습니다.

### 트레이드오프

일시적인 DB 장애 동안 Kafka Lag이 증가할 수 있습니다. 대신 DLT 저장 실패로 이벤트를 잃거나 상태를 설명할 수 없는 상황을 피할 수 있습니다.

## Phase 9. DLT 저장과 Kafka publish 사이 atomicity 한계

### 문제 상황

DLT DB row 저장과 `transaction-events-dlt` publish, 재처리 상태 변경과 원본 topic publish는 서로 다른 시스템을 건드립니다.

### 판단

Phase 9에서는 outbox 또는 Kafka transaction을 도입하지 않고, 각 중간 상태를 운영 문서에 기록했습니다. DLT 저장 후 publish 실패는 ack하지 않아 원본 record 재소비를 유도합니다. 재처리 publish 실패는 `REPROCESS_FAILED`로 남기고 API는 `503 KAFKA_PUBLISH_FAILED`를 반환합니다.

### 남은 한계

Kafka publish 성공 후 DB 상태 변경 실패 같은 보정은 후속 Phase의 outbox/reconciliation 후보입니다.

## Phase 9. 재처리 API의 상태 전이 충돌

`PENDING`과 `REPROCESS_FAILED`만 재처리/폐기 가능합니다. `REPROCESSED`와 `DISCARDED`는 종료 상태이므로 다시 재처리하면 `409 DLT_STATE_CONFLICT`로 응답합니다.

이 정책은 운영자가 같은 DLT row를 반복 클릭하거나, 이미 폐기한 이벤트를 실수로 재발행하는 상황을 막기 위한 방어입니다.

Phase 9 보완에서는 재처리/폐기 조회에 `PESSIMISTIC_WRITE` row lock을 적용했습니다. 같은 DLT id에 대한 동시 상태 변경을 직렬화해 중복 publish 가능성을 줄입니다.

## Phase 9. duplicate DLT 저장 방어 기준

같은 Kafka record가 여러 번 DLT path로 들어올 수 있으므로 `(source_topic, source_partition, source_offset)` unique constraint를 둡니다. 같은 `eventId`라도 서로 다른 offset에서 여러 실패 이력이 생길 수 있으므로 `event_id` unique는 걸지 않습니다.

## Phase 9. DLT payload sanitizer를 둔 이유

### 문제 상황

DLT는 실패 이벤트를 보관하는 저장소이므로 원본 payload와 예외 메시지가 오래 남기 쉽습니다. 운영 데이터가 들어오면 account, device, 연락처 같은 직접 식별자가 함께 저장될 위험이 있습니다.

### 판단

Phase 9에서는 완전한 masking rule engine을 만들지는 않았지만, DLT 저장 경로를 `sanitizePayload`와 `sanitizeErrorMessage`로 분리했습니다. 현재 local payload는 synthetic identifier만 사용하므로 필드 마스킹은 적용하지 않습니다.

### 방어 기준

- `errorMessage`는 500자로 제한합니다.
- null 또는 blank error message는 예외 class 이름으로 대체합니다.
- stacktrace 전체는 저장하지 않습니다.
- 운영 확장 시 payload sanitizer에서 카드번호, 계좌번호, 이메일, 전화번호 등 직접 식별자를 제거합니다.

## Phase 9. 재처리 횟수 제한과 rate limit을 후속으로 남긴 이유

Phase 9에서는 `reprocess_attempts`를 기록하지만 최대 재처리 횟수 제한은 적용하지 않았습니다. 운영 환경에서는 동일 이벤트의 반복 재처리로 인한 Kafka 부하와 운영 실수를 막기 위해 maxAttempts, cooldown, 관리자 승인 정책을 추가해야 합니다.

재처리 API는 단건 수동 재처리를 기준으로 구현했습니다. 대량 재처리나 반복 호출에 따른 Kafka 부하를 막기 위한 rate limit, batch size 제한, cooldown 정책은 후속 운영 안정화 Phase에서 보완합니다.

## Phase 9. 관리자 인증/인가와 DLT metric을 후속으로 남긴 이유

Phase 9의 DLT Admin API는 운영자용 계약과 상태 전이 검증에 집중했습니다. 실제 운영 환경에서는 ADMIN 권한 기반 인증/인가, 재처리/폐기 audit log, 요청자 식별자, 변경 전후 상태 기록이 필수입니다.

DLT 상태는 DB와 Admin API로 조회 가능하게 만들었습니다. DLT pending count, reprocess failed count, discard count 기반 metric과 alert rule은 후속 Observability Phase에서 추가합니다.

## Phase 10 - DLT 재처리 이후 운영 완료 기준 정리

### Problem

DLT 재처리 기능은 구현되었지만, 재처리 API 성공만으로 운영 복구가 끝났다고 판단하면 Consumer Lag, duplicate FraudResult, processing log, DLQ 상태 전이 확인이 빠질 수 있습니다.

### Cause

Phase 9는 실패 이벤트를 격리하고 재처리하는 기능 구현에 집중했습니다. 운영자가 "복구 완료"라고 판단할 때 확인해야 하는 API, Consumer, Kafka, Redis, PostgreSQL 증거 기준은 별도 문서로 정리되어 있지 않았습니다.

### Decision

Phase 10에서는 신규 기능이나 아키텍처 변경을 추가하지 않고, final readiness checklist와 검증 evidence 기준을 문서화하는 것으로 범위를 제한했습니다.

### Action

`docs/19-phase-10-final-readiness.md`를 추가해 Phase 9까지의 구현 요약, 최종 체크리스트, 운영 관점별 검증 기준, 남은 한계와 후속 보완 후보를 정리했습니다. README는 Phase 10 문서 링크와 현재 구현 범위만 최소 수정했고, blog에는 DLT 재처리 이후 운영 검증 흐름을 초안으로 추가했습니다.

### Verification

다음 명령을 실행했습니다.

```bash
./gradlew clean build
./gradlew test
make test
make final-check
```

모두 PASS했습니다. 최초 `./gradlew clean build`는 sandbox가 `~/.gradle` lock file 생성을 막아 실패했지만, 승인된 로컬 권한으로 재실행한 결과 build/test는 성공했습니다.

### Lesson

DLT 재처리는 메시지를 다시 넣는 기능만으로 끝나지 않습니다. 재처리 후 중복 탐지 결과가 생기지 않았는지, DLQ 상태가 종료 상태로 전이됐는지, Consumer Lag이 회복됐는지, processing log와 감사 근거가 남았는지까지 확인할 수 있어야 운영 기능이라고 볼 수 있습니다.

## Phase 13. Duplicate Replay와 k6 failure 기준

### 문제 상황

Duplicate replay 시나리오는 의도적으로 같은 `eventId`를 반복 발행합니다. 이때 API가 duplicate를 `409 CONFLICT`로 반환하면 k6 기본 기준에서는 실패 요청처럼 보일 수 있습니다.

### 판단

Duplicate replay에서는 2xx만 성공으로 보지 않고, 프로젝트 정책상 허용되는 duplicate response를 별도 check로 처리합니다. 최종 판단은 `fraud_detection_results.event_id` unique constraint와 fraud result count 1건 유지 여부입니다.

### 트레이드오프

`http_req_failed` 지표만 보면 실패율이 높아 보일 수 있습니다. 따라서 duplicate scenario는 API response policy와 PostgreSQL consistency 결과를 함께 해석합니다.

## Phase 13. Redis Down Load 후 Redis 복구 누락 위험

### 문제 상황

Redis down load는 테스트 중 Redis container를 중지합니다. k6 실행이 실패하거나 사용자가 중간에 중단하면 Redis가 내려간 상태로 남아 이후 테스트와 로컬 개발에 영향을 줄 수 있습니다.

### 판단

Redis stop/start는 k6 script가 아니라 `scripts/load_tests/run_redis_down_load.sh`에서 처리합니다. script는 `trap`으로 cleanup을 등록하고, Redis start 이후 `redis-cli ping` readiness를 확인합니다.

### 트레이드오프

script가 Redis 복구를 시도하더라도 Docker daemon 문제나 container 상태 이상은 자동 복구하지 못할 수 있습니다. 실행 후에는 반드시 `docker compose -f infra/docker-compose.yml ps redis`로 상태를 확인합니다.

## Phase 13. 로컬 부하 테스트 결과 해석 한계

### 문제 상황

로컬 Docker Compose 기반 k6 결과는 노트북 CPU, 메모리, Docker resource limit, JVM warmup, Kafka/DB/Redis container 상태에 크게 영향을 받습니다.

### 판단

Phase 13 결과는 절대 성능 수치가 아니라 병목 후보와 관측 절차를 설명하는 evidence로 사용합니다. 측정하지 않은 수치는 `TBD`로 두고 임의로 작성하지 않습니다.

### 트레이드오프

로컬 결과는 재현이 쉽지만 운영 capacity를 대표하지 않습니다. 운영 SLO 산정은 별도 환경과 반복 측정이 필요합니다.

## Phase 13. k6 threshold를 공격적으로 잡지 않은 이유

### 문제 상황

초기 k6 threshold를 너무 낮게 잡으면 로컬 장비 상태나 JVM warmup만으로 테스트가 실패해 실제 병목 분석보다 환경 노이즈가 커질 수 있습니다.

### 판단

Normal load는 낮은 error rate와 p95/p99 기준을 두고, Peak/Redis down은 장애 영향 관찰을 허용하는 범위로 threshold를 둡니다.

### 트레이드오프

threshold가 느슨하면 성능 회귀를 강하게 막지는 못합니다. 대신 Phase 13에서는 반복 가능한 evidence 기록과 병목 후보 식별을 우선합니다.

## Phase 13. Consumer Lag metric 부재로 인한 한계

### 문제 상황

Phase 13 k6 script는 API latency와 request failure를 직접 측정할 수 있지만, Consumer Lag metric은 아직 dashboard/alert로 연결되지 않았습니다.

### 판단

현재는 Kafka UI, processing log, fraud result 조회, Redis degraded metric을 함께 사용해 비동기 처리 영향을 해석합니다. Consumer Lag metric과 Grafana dashboard는 후속 Observability hardening 범위로 둡니다.

### 남은 한계

API p95가 안정적이어도 Consumer backlog가 쌓일 수 있습니다. 이후 Phase에서는 Consumer Lag과 detection latency를 dashboard evidence로 연결해야 합니다.

## Phase 14. JWT/OAuth2 대신 X-Admin-Token을 선택한 이유

### 문제 상황

DLT 조회/재처리/폐기 API는 운영자 기능인데, Phase 13까지는 local/dev API가 공개 endpoint처럼 보일 수 있었습니다.

### 판단

Phase 14의 목표는 완전한 IAM 구현이 아니라 운영자 API 보호와 감사 가능성의 최소 구현입니다. 따라서 Spring Security OAuth2/JWT/RBAC까지 확장하지 않고 `X-Admin-Token` 기반 filter를 추가했습니다.

### 트레이드오프

`X-Admin-Token`은 production-grade 인증/인가가 아닙니다. 운영 환경에서는 JWT/OAuth2, ADMIN role, RBAC, IP allowlist, token rotation, gateway rate limit이 필요합니다.

## Phase 14. audit log 저장 실패 시 운영자 액션을 실패시킨 이유

### 문제 상황

DLT 재처리/폐기 API에서 실제 상태 변경은 성공했지만 audit log 저장이 실패할 수 있습니다.

### 판단

운영자 액션은 사후 추적 가능성이 중요하므로 상태 변경과 audit 저장을 같은 transaction 경계에 둡니다. audit log 저장에 실패하면 재처리/폐기 액션도 실패시키는 편이 감사 관점에서 안전하다고 판단했습니다.

### 트레이드오프

audit 저장소 장애가 운영자 액션을 막을 수 있습니다. 대신 "누가 어떤 조치를 했는지 설명할 수 없는 상태 변경"을 방지할 수 있습니다.

## Phase 14. admin token을 audit log에 저장하지 않은 이유

Admin token은 인증 수단이지 감사 metadata가 아닙니다. token을 audit log에 저장하면 DB 조회 권한만으로 운영자 API 호출 권한이 유출될 수 있습니다.

Phase 14 audit log에는 actor, action, target id, eventId, traceId, result, reason, 최소 metadata만 저장합니다. DLT payload 전체, request body 전체, accountId, deviceId, token 값은 저장하지 않습니다.

## Phase 14. operatorId를 self-claimed actor로 기록하는 한계

Phase 14의 `operatorId`는 인증된 사용자 식별자가 아니라 local/dev 환경에서 감사 로그를 남기기 위한 self-claimed field입니다. `X-Admin-Token`을 아는 사용자는 body에 임의의 `operatorId`를 넣을 수 있습니다.

이번 Phase에서는 이 한계를 문서화하고, 운영 환경에서는 JWT subject, SSO user id, RBAC principal을 audit actor로 사용하도록 후속 과제로 둡니다.

## Phase 14. default local admin token warning을 남기는 이유

`security.admin.token`은 local/dev 편의를 위해 `${ADMIN_API_TOKEN:local-admin-token}` 기본값을 둡니다. 하지만 운영 비슷한 환경에 기본 token이 그대로 배포되면 잘 알려진 token으로 Admin API가 보호되는 위험이 있습니다.

Phase 14에서는 profile 분리나 fail-fast까지 확장하지 않고, 기본값이 활성화되면 startup warning log를 남깁니다. 운영 환경에서는 반드시 `ADMIN_API_TOKEN`을 별도로 주입하고, 후속 보안 Phase에서는 profile별 설정 분리 또는 non-local fail-fast를 검토합니다.

## Phase 14. audit request_id를 비워두는 이유

Phase 14에는 gateway 또는 공통 request-id 수집 체계가 아직 없습니다. 따라서 `admin_audit_logs.request_id`에 eventId를 대신 넣지 않고 null로 둡니다.

eventId는 `metadata_json`에 저장합니다. 추후 gateway/request-id 표준화가 추가되면 `request_id`를 실제 HTTP request id 또는 command id로 채웁니다.

## Phase 14. max attempts를 자동 discard로 연결하지 않은 이유

`reprocess_attempts >= maxAttempts`이면 재처리는 막지만 status를 자동으로 `DISCARDED`로 바꾸지 않습니다.

자동 discard는 운영자가 실패 원인을 확인하기 전에 복구 후보를 종료 상태로 바꿀 수 있습니다. Phase 14에서는 반복 재처리를 막는 것과 명시적 폐기 결정을 분리했습니다. 운영자는 원인 확인 후 discard API로 폐기 사유를 남겨야 합니다.

## Phase 14. rate limit을 이번 Phase에서 제외한 이유

이번 Phase의 abuse prevention은 admin token, 단건 수동 조작, request validation, max attempts에 집중했습니다.

in-memory rate limiter는 local single instance에서만 의미가 있고, app-api가 scale out되면 우회될 수 있습니다. 운영 환경에서는 Gateway/Nginx/API Gateway rate limit, IP allowlist, 관리자 승인 workflow가 더 적절하므로 후속 운영 자동화 Phase로 남겼습니다.

## V2 Phase 1. raw CSV를 실수로 commit할 수 있는 문제

### 문제

Kaggle PaySim 원본 CSV를 `data/raw`에 내려받은 뒤 실수로 Git에 추가할 수 있습니다.

### 원인

원본 CSV는 repository 밖에서 받아야 하는 대용량 데이터지만, 명확한 디렉터리와 `.gitignore` 정책이 없으면 일반 프로젝트 파일처럼 취급될 수 있습니다.

### 대응

`data/raw/.gitkeep`만 커밋하고, `.gitignore`에서 `data/raw/*`를 무시하도록 설정했습니다. `scripts/data/check-data-policy.sh`는 tracked/staged 파일 중 `data/raw/.gitkeep` 외 raw 파일이 있으면 실패합니다.

### 검증

`git check-ignore -v data/raw/PS_20174392719_1491204439457_log.csv`로 raw CSV가 ignore되는 것을 확인합니다. `make data-policy-check`로 tracked/staged raw 파일이 없는지 확인합니다.

### 남은 한계

이미 강제로 commit된 raw 파일은 ignore만으로 제거되지 않습니다. 그런 경우 Git history 정리와 별도 보안 검토가 필요합니다.

## V2 Phase 1. processed JSONL 전체 결과가 repository에 들어갈 수 있는 문제

### 문제

전처리 결과인 `paysim-events.jsonl`, `paysim-labels.jsonl`, validation report가 크거나 account-like identifier를 포함할 수 있는데 repository에 들어갈 위험이 있습니다.

### 원인

processed output은 재현 가능한 산출물이어야 하지만, 구현 중에는 최종 결과 파일을 evidence처럼 커밋하려는 유혹이 생길 수 있습니다.

### 대응

`data/processed/.gitkeep`만 커밋하고, `.gitignore`에서 `data/processed/*`를 무시하도록 설정했습니다. data policy check는 `data/processed/.gitkeep` 외 tracked/staged 파일을 실패 처리합니다.

### 검증

`git check-ignore -v data/processed/paysim-events.jsonl`로 processed JSONL이 ignore되는 것을 확인합니다. `make data-policy-check`로 processed full output이 Git에 포함되지 않았는지 확인합니다.

### 남은 한계

processed output을 artifact storage에 보관하는 정책은 아직 없습니다. Phase 2 이후에는 validation report와 checksum을 통해 local 재현성을 확보합니다.

## V2 Phase 1. sample 허용 정책이 raw data commit 허용으로 오해될 수 있는 문제

### 문제

`data/samples`에서 `.jsonl`과 `.csv`를 허용하면, 이 위치가 raw CSV를 넣어도 되는 곳으로 오해될 수 있습니다.

### 원인

sample은 작은 검증용 산출물이지만 확장자만 보면 원본 CSV와 구분되지 않을 수 있습니다.

### 대응

`scripts/data/README.md`와 provenance 문서에 sample 조건을 명시했습니다. sample은 100~1,000건 이하, raw `nameOrig`/`nameDest` 미포함, hashed identifier 사용, 1MB 이하를 기준으로 둡니다. data policy check는 sample 파일이 1MB를 초과하면 실패합니다.

### 검증

임시 `data/samples/paysim-events-sample.jsonl` 파일이 ignore되지 않는 것을 확인한 뒤 삭제합니다. `make data-policy-check`로 sample 크기와 확장자 정책을 확인합니다.

### 남은 한계

Phase 1의 shell check는 sample 내부에 raw identifier가 섞였는지 완전하게 검출하지 못합니다. Phase 3~4에서 sample generation과 identifier hashing 검증을 추가해야 합니다.

## V2 Phase 2. KaggleHub cache path와 repository raw path 분리

### 문제

KaggleHub는 dataset을 repository 내부가 아니라 사용자 cache 경로에 다운로드합니다. 이 경로를 그대로 전처리 입력으로 사용하면 재현 명령과 data policy 기준이 흐려질 수 있습니다.

### 원인

KaggleHub의 `dataset_download`는 cache directory를 반환하며, 파일명이나 하위 경로는 dataset packaging에 따라 달라질 수 있습니다.

### 대응

`download_paysim_dataset.py`는 cache 내부에서 CSV를 찾은 뒤 `data/raw/PS_20174392719_1491204439457_log.csv`로 복사합니다. target file이 이미 있으면 기본적으로 덮어쓰지 않고, `--force`가 있을 때만 overwrite합니다.

### 검증

download helper는 CI에서 실행하지 않습니다. 대신 path 선택, overwrite 정책, source file 후보 처리 기준을 문서화하고, raw target은 `.gitignore`와 `make data-policy-check`로 보호합니다.

### 남은 한계

Kaggle 인증과 dataset 접근 가능 여부는 로컬 환경에 의존합니다. token이나 access credential은 repository, docs, logs에 남기지 않아야 합니다.

## V2 Phase 2. 대용량 CSV를 pandas full load로 처리할 때의 메모리 위험

### 문제

PaySim CSV는 수백 MB 규모이므로 pandas로 전체 파일을 한 번에 로딩하면 로컬 메모리 사용량이 커지고 CI나 노트북 환경에서 불안정해질 수 있습니다.

### 원인

전처리의 목표는 row 단위 normalization인데, full DataFrame 로딩은 필요한 작업보다 훨씬 큰 메모리 footprint를 만들 수 있습니다.

### 대응

`prepare_paysim_dataset.py`는 Python stdlib `csv.DictReader`를 사용해 한 row씩 읽고, events/labels/rejected JSONL에 바로 기록합니다.

### 검증

작은 fixture 기반 unittest로 streaming conversion contract를 검증했습니다. full Kaggle CSV는 CI에서 처리하지 않습니다.

### 남은 한계

실제 493MB급 CSV 처리 시간과 local disk I/O 특성은 Phase 2 CI에서 측정하지 않습니다. 필요하면 local evidence로 별도 기록합니다.

## V2 Phase 2. runtime event에 label이 섞일 위험

### 문제

`isFraud`나 `isFlaggedFraud`가 runtime event에 들어가면 online replay와 offline evaluation의 경계가 깨집니다.

### 원인

PaySim raw CSV에는 feature와 label이 같은 row에 들어 있으므로 단순 row dump 방식으로 JSONL을 만들면 Consumer가 정답 label을 볼 수 있습니다.

### 대응

전처리 script는 `paysim-events.jsonl`과 `paysim-labels.jsonl`을 분리합니다. runtime event에는 label, source flag, raw identifier, `receivedAt`을 쓰지 않습니다.

### 검증

`scripts/data/test_prepare_paysim_dataset.py`에서 runtime event에 `isFraud`, `isFlaggedFraud`, `nameOrig`, `nameDest`, `receivedAt`이 없는지 확인합니다.

### 남은 한계

후속 replay script도 label sidecar를 payload로 합치지 않도록 Phase 5에서 별도 검증해야 합니다.

## V2 Phase 2. rejected row에 raw identifier를 남길 위험

### 문제

변환 실패 row를 디버깅하려고 raw row 전체를 rejected output에 남기면 `nameOrig`, `nameDest`가 저장될 수 있습니다.

### 원인

전처리 오류 분석에는 원본 row 정보가 유용하지만, Phase 1의 raw data guardrail 취지와 충돌합니다.

### 대응

`paysim-rejected.jsonl`에는 `rowNumber`, `reason`, `rawType`, `message`만 기록합니다. raw row 전체와 raw account-like identifier는 기록하지 않습니다.

### 검증

invalid amount fixture test에서 rejected record에 raw identifier가 포함되지 않는 것을 확인했습니다.

### 남은 한계

더 정교한 rejected reason taxonomy와 reject ratio 정책은 Phase 3에서 강화합니다.

## V2 Data Toolchain. Java 프로젝트에서 global pip 설치를 요구하는 문제

### 문제

PaySim download helper를 실행하려면 KaggleHub가 필요하지만, README나 script가 `pip install kagglehub`를 직접 안내하면 개발자의 global Python 환경을 오염시킬 수 있습니다.

### 원인

이 repository의 주 toolchain은 Java/Spring Boot와 Gradle입니다. Python은 PaySim data helper에만 필요한 보조 도구인데, global pip 설치를 요구하면 프로젝트 경계가 흐려지고 개발자별 환경 차이가 커집니다.

### 대응

`scripts/data/requirements.txt`와 `scripts/data/bootstrap-data-env.sh`를 추가하고, `make data-env`가 repository-local `.venv-data`를 생성하도록 했습니다. data script 관련 Makefile target은 `.venv-data/bin/python`을 사용합니다.

### 검증

`bash -n scripts/data/bootstrap-data-env.sh`, `make data-env`, `make test-data-scripts`로 venv 생성과 KaggleHub import, fixture test 실행을 확인합니다.

### 남은 한계

Kaggle 인증 자체는 사용자 로컬 환경에 남아야 합니다. token 값은 `.venv-data`, Git, docs, logs에 저장하지 않습니다.

## V2 Data Toolchain. Python interpreter와 package 상태 차이로 import 실패가 나는 문제

### 문제

개발자마다 `python3`가 가리키는 interpreter, pip site-package, 설치된 KaggleHub version이 달라 `download_paysim_dataset.py`의 import 결과가 달라질 수 있습니다.

### 원인

Makefile이 global `python3`를 직접 호출하면 Java 프로젝트의 Gradle workflow와 별개로 로컬 Python 상태에 의존합니다.

### 대응

`DATA_VENV_DIR ?= .venv-data`와 `DATA_PYTHON := $(DATA_VENV_DIR)/bin/python`을 Makefile에 추가했습니다. `download-paysim`, `prepare-paysim`, `prepare-paysim-smoke`, `test-data-scripts`는 모두 `data-env`에 의존합니다.

### 검증

`make data-python-check`는 실제 사용되는 Python executable과 KaggleHub import를 확인합니다. `make test-data-scripts`는 같은 venv Python으로 fixture 기반 unittest를 실행합니다.

### 남은 한계

Python version 자체는 로컬 `python3 -m venv` 가능 여부에 의존합니다. Python이 설치되어 있지 않으면 `PYTHON=/path/to/python3 make data-env`로 명시해야 합니다.

## V2 Data Toolchain. CI에서 Kaggle download까지 실행하면 불안정해지는 문제

### 문제

CI에서 Kaggle dataset download나 full preprocessing을 실행하면 인증, 네트워크, 대용량 파일 크기 때문에 CI가 불안정해질 수 있습니다.

### 원인

PaySim raw CSV는 로컬 재현용 대용량 외부 데이터입니다. CI가 외부 dataset 접근과 credential 설정에 의존하면 test signal이 흐려집니다.

### 대응

CI path인 `make ci-check`는 venv 생성, fixture 기반 `make test-data-scripts`, `make data-policy-check`까지만 실행합니다. `make download-paysim`과 full `make prepare-paysim`은 명시적으로 로컬에서만 실행합니다.

### 검증

`make ci-check`가 Kaggle download 없이 통과하는지 확인합니다. raw CSV와 processed full output은 `make data-policy-check`와 `.gitignore`로 커밋을 방지합니다.

### 남은 한계

실제 Kaggle 접근 가능 여부와 full dataset preprocessing 시간은 CI에서 검증하지 않습니다. 필요하면 로컬 evidence로 별도 기록합니다.

## V2 Data Toolchain. venv 디렉터리가 Git에 커밋될 수 있는 문제

### 문제

repository-local venv를 만들면 `.venv-data` 내부의 Python binary와 site-packages가 실수로 Git에 추가될 수 있습니다.

### 원인

venv는 프로젝트 하위 디렉터리에 생성되므로 `.gitignore`가 없으면 IDE나 수동 add 과정에서 추적될 수 있습니다.

### 대응

`.gitignore`에 `.venv/`, `venv/`, `.venv-data/`를 추가했습니다. raw CSV와 processed output guardrail은 그대로 유지했습니다.

### 검증

`git status --short`로 `.venv-data/`가 표시되지 않는지 확인하고, `make data-policy-check`로 data directory guardrail이 유지되는지 확인합니다.

### 남은 한계

이미 강제로 staged된 venv 파일은 수동으로 unstage해야 합니다. 커밋 전 `git status --short` 확인이 필요합니다.

## V2 Data Toolchain. CI에서 Python version이 암묵적으로 선택되는 문제

### 문제

`make ci-check`는 `make test-data-scripts`를 통해 `.venv-data`를 생성하므로 CI에서 Python이 필수입니다. GitHub hosted runner에 Python이 기본 설치되어 있더라도 version이 명시되지 않으면 toolchain 재현성이 낮아집니다.

### 원인

기존 workflow는 Java 17만 `actions/setup-java`로 명시하고, Python은 runner 기본값에 의존했습니다.

### 대응

GitHub Actions에 `actions/setup-python@v5`를 추가하고 Python `3.11`을 명시했습니다. pip cache는 `bootstrap-data-env.sh`가 `.venv-data/.pip-cache`를 사용하므로 `actions/setup-python`의 built-in pip cache는 사용하지 않습니다.

### 검증

`make ci-check`는 Java test/assemble 이후 venv 기반 data script test와 data policy check를 실행합니다. CI workflow syntax는 GitHub Actions가 PR에서 검증합니다.

### 남은 한계

CI는 Kaggle download와 full preprocessing을 실행하지 않습니다. 해당 검증은 Kaggle 인증이 준비된 로컬에서 수행합니다.

## V2 Data Toolchain. macOS system Python SSL과 urllib3 호환 경고

### 문제

macOS system Python으로 `.venv-data`를 만들었을 때 KaggleHub dependency가 `urllib3` v2를 설치하면 LibreSSL/OpenSSL 호환 경고가 출력될 수 있습니다.

### 원인

일부 macOS system Python은 SSL module이 LibreSSL 기반으로 빌드되어 있고, `urllib3` v2는 OpenSSL 1.1.1+ 환경을 기대합니다.

### 대응

`scripts/data/requirements.txt`에 `urllib3<2`를 임시로 고정했습니다. 이는 data helper bootstrap 로그를 안정화하기 위한 제한이며 Java runtime에는 영향을 주지 않습니다.

### 검증

`make data-env`와 `make test-data-scripts`가 경고 없이 통과하는지 확인합니다.

### 남은 한계

KaggleHub를 1.x 이상으로 올리거나 Python/OpenSSL runtime을 표준화할 때 이 제한을 재검토해야 합니다.

## V2 Phase 3. runtime event에 label이 섞일 수 있는 문제

### 문제

PaySim raw row에는 feature와 label이 함께 있으므로 runtime event JSONL에 `isFraud`, `isFlaggedFraud`, `sourceFlaggedFraud`가 섞일 수 있습니다.

### 원인

전처리나 sample 생성이 raw row dump에 가까워지면 Consumer가 정답 label을 볼 수 있는 구조가 됩니다.

### 대응

`validate_paysim_outputs.py`와 `generate_paysim_samples.py`에서 runtime event에 label field가 있으면 실패하도록 했습니다.

### 검증

fixture test에서 event에 label field를 넣으면 validation이 실패합니다. 실제 smoke output 기준 `make validate-paysim`도 통과했습니다.

### 남은 한계

Kafka replay script가 추가될 때도 label sidecar를 payload에 합치지 않는 검증이 필요합니다.

## V2 Phase 3. sample에 raw identifier가 다시 유입될 수 있는 문제

### 문제

sample은 Git에 커밋될 수 있으므로 raw `nameOrig`, `nameDest`나 `C12345`, `M12345` 형태 identifier가 포함되면 data guardrail이 약해집니다.

### 원인

작은 sample은 review 편의를 위해 커밋되기 쉬우며, raw identifier는 field 이름이 아니라 문자열 값 내부에도 나타날 수 있습니다.

### 대응

Phase 3 validator와 sample generator는 nested JSON을 recursive scan하며 raw identifier field와 raw PaySim identifier pattern을 차단합니다.

### 검증

fixture test에서 raw identifier field와 pattern을 주입하면 실패합니다. 생성된 sample manifest는 `containsRawIdentifiers=false`를 기록합니다.

### 남은 한계

정규식 기반 탐지는 PaySim의 대표 pattern 방어입니다. 새로운 raw identifier 형태가 생기면 allow/deny rule을 보강해야 합니다.

## V2 Phase 3. rejected row 비율이 높아도 preprocessing이 성공처럼 보일 수 있는 문제

### 문제

row-level reject 정책에서는 output file이 생성되므로 rejected row가 많아도 성공처럼 보일 수 있습니다.

### 원인

전처리 성공 여부와 데이터 품질 성공 여부는 다릅니다. rejected row 비율을 별도로 검증하지 않으면 품질 문제가 숨을 수 있습니다.

### 대응

`validate_paysim_outputs.py`는 `rejectedRows / totalRows`를 계산하고 기본 `--max-reject-ratio 0.01`을 초과하면 실패합니다.

### 검증

fixture test에서 threshold를 초과하는 rejected ratio를 만들면 validation이 실패합니다. smoke output은 rejected=0, rejectRatio=0.0000으로 통과했습니다.

### 남은 한계

reject ratio threshold는 초기 기준입니다. full dataset 분석 결과에 따라 Phase 4 이후 조정할 수 있습니다.

## V2 Phase 3. CSV sample이 raw column leakage를 만들 수 있는 문제

### 문제

CSV sample은 작아도 PaySim raw column인 `nameOrig`, `nameDest`, `isFraud`를 그대로 보존하기 쉽습니다.

### 원인

CSV는 raw dataset과 동일한 형태로 일부 row를 잘라내기 쉽고, schema boundary가 JSONL runtime event보다 약합니다.

### 대응

Phase 3 sample generator는 JSONL만 생성합니다. `.gitignore`와 `check-data-policy.sh`에서 `data/samples/*.csv` 허용을 제거했습니다.

### 검증

`make data-policy-check`가 통과했고, CSV sample은 allowlist 밖입니다.

### 남은 한계

CSV sample이 꼭 필요해지면 forbidden header와 raw identifier 검사를 구현한 뒤 별도 Phase에서 허용해야 합니다.

## V2 Phase 3. sample manifest 허용이 일반 JSON 허용으로 넓어질 수 있는 문제

### 문제

manifest를 커밋하려고 `data/samples/*.json` 전체를 허용하면 full validation summary나 임의 JSON이 data/samples에 들어올 수 있습니다.

### 원인

manifest도 JSON이고 validation summary도 JSON이므로 extension 기준 allowlist만으로는 경계가 넓습니다.

### 대응

`paysim-sample-manifest.json`만 제한적으로 허용하고 일반 `*.json`은 허용하지 않습니다. JSONL도 `paysim-events-sample.jsonl`, `paysim-labels-sample.jsonl`만 허용합니다.

### 검증

생성된 `paysim-sample-manifest.json`은 data policy check를 통과합니다. full/processed/raw 이름과 임의 JSONL sample은 policy check에서 실패하도록 했습니다.

### 남은 한계

manifest 내부 schema 검증은 sample generator가 수행합니다. 수동으로 manifest를 편집하면 review와 data-policy-check를 함께 확인해야 합니다.

## V2 Phase 3. head sample에는 fraud row가 없을 수 있는 문제

### 문제

PaySim fraud row는 sparse할 수 있어 단순 head sample에는 fraud case가 포함되지 않을 수 있습니다.

### 원인

head sampling은 row order를 보존하지만 label 분포를 보장하지 않습니다.

### 대응

`balanced` strategy를 기본값으로 두고 fraud label true event를 우선 포함한 뒤 non-fraud event로 sample size를 채웁니다.

### 검증

fixture test에서 balanced strategy가 fraud row를 우선 포함하는지 확인했습니다. 실제 smoke sample은 1,000 rows 중 fraud=9를 포함했습니다.

### 남은 한계

현재 balanced sampling은 deterministic first-N-per-class 방식입니다. Reservoir sampling이나 stratified ratio control은 후속 개선 후보입니다.

## V2 Phase 3. event/label line count가 같아도 eventId set이 다를 수 있는 문제

### 문제

events와 labels line count가 같아도 label이 다른 eventId를 가리키면 offline evaluation join이 깨집니다.

### 원인

count validation만으로는 sidecar join consistency를 보장할 수 없습니다.

### 대응

Validator와 sample generator 모두 eventId set equality를 확인합니다.

### 검증

fixture test에서 labels에 events에 없는 eventId를 넣으면 실패합니다. generated sample도 eventId set equality를 자체 검증합니다.

### 남은 한계

Full dataset에서는 eventId set을 메모리에 보관하므로 accepted row 수에 비례한 메모리 사용이 있습니다.
