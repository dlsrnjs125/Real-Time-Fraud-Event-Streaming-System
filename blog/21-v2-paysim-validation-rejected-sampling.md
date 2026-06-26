# V2 PaySim validation, rejected analysis, safe sampling

## 문제

전처리 output이 만들어졌다고 바로 replay나 Rule V2 입력으로 넘기면 위험합니다. Runtime event에 label이 섞였는지, raw identifier가 다시 들어왔는지, rejected row 비율이 비정상적으로 높은지, event와 label sidecar가 같은 eventId set을 갖는지 확인해야 합니다.

## 초기 설계

Phase 3는 Java runtime을 건드리지 않고 Python data helper 범위에만 머무릅니다.

- processed output validation script
- sample generation script
- sample manifest
- data policy check 강화
- fixture 기반 unittest

CI에서는 fixture test와 data policy check만 실행하고, full validation과 sample generation은 로컬 processed output이 있을 때 실행합니다.

## 구현

추가한 script:

- `scripts/data/validate_paysim_outputs.py`
- `scripts/data/generate_paysim_samples.py`

검증기는 events, labels, rejected, report를 함께 읽고 count, eventId join, label leakage, raw identifier leakage, reject ratio, report provenance를 확인합니다.

Sample generator는 `data/samples`에 JSONL sample과 manifest를 생성합니다. Phase 3에서는 CSV sample을 생성하지 않습니다. Manifest에는 input SHA-256과 hashSaltSource만 기록하고 salt 값 자체는 기록하지 않습니다.

## 트러블슈팅

Runtime event에 `isFraud`가 들어가면 online replay와 offline evaluation의 경계가 깨집니다. Validator는 event row에서 label field를 발견하면 실패합니다.

Raw identifier는 field 이름뿐 아니라 `C12345`, `M12345` 형태 값으로도 유입될 수 있습니다. Validator와 sample generator 모두 recursive scan을 수행합니다.

Rejected row가 많아도 preprocessing이 성공처럼 보일 수 있습니다. Phase 3는 reject ratio 기본 threshold를 0.01로 두고 초과 시 실패합니다.

CSV sample은 작은 파일이라도 raw PaySim column을 보존하기 쉽습니다. Phase 3 data policy는 CSV sample을 금지하고 JSONL sample과 manifest만 허용합니다.

Manifest를 허용하려다 `data/samples/*.json` 전체를 열면 full summary나 임의 JSON이 들어올 수 있습니다. 따라서 `*manifest*.json`만 제한적으로 허용합니다.

Balanced sampling은 deterministic first-N-per-class 방식입니다. Fraud row가 적으면 가능한 fraud row를 우선 포함하고 나머지는 non-fraud로 채웁니다.

## 검증

실행한 검증:

```bash
bash -n scripts/data/*.sh
make data-env
make test-data-scripts
make data-policy-check
make prepare-paysim-smoke
make validate-paysim
make generate-paysim-sample-strict
```

Smoke output 기준:

- events=1000
- labels=1000
- rejected=0
- fraud=9
- flagged=0
- rejectRatio=0.0000
- generated sample events=1000
- generated sample labels=1000

## 남은 한계

Full output validation은 CI에서 실행하지 않습니다. CI는 fixture 기반 data script tests만 실행합니다.

Replay eventId prefix와 replay 충돌 방지는 Phase 5에서 다룹니다.

Committed sample salt policy는 Phase 4에서 더 강하게 고정할 수 있습니다.

Java Rule Engine V2, Kafka replay, API/DB/Kafka schema 변경은 아직 구현하지 않았습니다.
