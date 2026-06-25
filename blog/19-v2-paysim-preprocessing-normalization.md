# V2 PaySim Preprocessing Normalization

## 문제

Kaggle PaySim을 V2 검증에 쓰려면 raw CSV를 그대로 저장하거나 replay하면 안 됩니다.

PaySim row에는 거래 feature와 fraud label이 함께 들어 있습니다. 이 상태로 JSON payload를 만들면 Consumer가 정답 label을 볼 수 있고, offline evaluation과 online replay의 경계가 무너집니다. 또한 `nameOrig`, `nameDest` 같은 account-like identifier가 output이나 rejected row에 섞일 위험도 있습니다.

## 초기 설계

Phase 2의 입력은 `data/raw/PS_20174392719_1491204439457_log.csv`입니다.

출력은 네 가지로 분리했습니다.

- `paysim-events.jsonl`: API/Kafka replay용 runtime event
- `paysim-labels.jsonl`: offline evaluation용 label sidecar
- `paysim-rejected.jsonl`: 변환 실패 row 요약
- `paysim-validation-report.json`: 처리 결과와 provenance

raw CSV와 processed full output은 Git에 커밋하지 않습니다. Phase 1에서 만든 data policy guardrail을 그대로 사용합니다.

## 구현

이번 Phase에서 두 개의 script를 추가했습니다.

- `scripts/data/download_paysim_dataset.py`
- `scripts/data/prepare_paysim_dataset.py`

download helper는 optional local tool입니다. KaggleHub cache에 dataset을 받은 뒤 raw CSV를 `data/raw`로 복사합니다. CI에서는 실행하지 않습니다.

preprocessing script는 Python stdlib `csv.DictReader`로 streaming 처리합니다. pandas full load를 사용하지 않고, 한 row씩 읽어 runtime event와 label sidecar를 씁니다.

identifier는 salt를 HMAC key처럼 사용해 deterministic pseudonym으로 변환합니다. salt 값 자체는 report나 log에 남기지 않습니다.

Makefile target도 추가했습니다.

```bash
make download-paysim
make prepare-paysim
make prepare-paysim-smoke
make test-data-scripts
```

## 트러블슈팅

KaggleHub는 repository 내부가 아니라 cache path를 반환합니다. 그래서 helper는 cache 안에서 CSV를 찾고, 명시적인 raw path로 복사하도록 했습니다.

PaySim CSV는 수백 MB 규모입니다. 전체 파일을 DataFrame으로 올리는 방식은 전처리 목적보다 메모리 사용량이 큽니다. 그래서 streaming conversion만 사용했습니다.

가장 중요한 문제는 label leakage입니다. `isFraud`, `isFlaggedFraud`는 runtime event에 넣지 않고 label sidecar에만 씁니다. runtime event와 label sidecar는 `eventId`로만 연결합니다.

rejected row도 raw row 전체를 dump하지 않습니다. 오류 분석에는 덜 편하지만, raw identifier가 output에 남는 위험을 줄입니다.

## 검증

실제 Kaggle CSV 없이 작은 fixture로 unittest를 작성했습니다.

검증한 내용:

- 정상 row가 events/labels로 분리됨
- runtime event에 label과 raw identifier가 포함되지 않음
- label sidecar에 evaluation label이 기록됨
- invalid amount row가 rejected로 이동함
- `fail-fast` policy가 invalid row에서 실패함
- deterministic eventId와 hash id가 생성됨
- Decimal 값이 JSON 문자열로 기록됨
- validation report에 input checksum, counts, output file 경로가 기록됨
- NaN/Infinity 같은 non-finite decimal이 reject됨
- raw identifier가 events/labels/rejected/report 어디에도 남지 않음

검증 명령:

```bash
make test-data-scripts
make data-policy-check
make ci-check
```

## 남은 한계

sample generation은 Phase 3에서 구현합니다.

identifier hashing은 이번 Phase에서 최소 구현했지만, salt policy와 sample 검증은 Phase 4에서 강화합니다.

API replay는 Phase 5, Rule Engine V2는 Phase 6에서 다룹니다.
