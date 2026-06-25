# V2 PaySim Data Provenance and Raw Data Protection

## 문제

V2에서는 Kaggle PaySim synthetic 금융 거래 데이터를 사용해 더 현실적인 거래 분포와 fraud label 기반 검증 흐름을 만들 계획입니다.

하지만 원본 CSV와 processed 전체 결과를 repository에 넣는 순간 두 가지 문제가 생깁니다. 첫째, 재현 절차가 아니라 결과 파일 자체에 의존하게 됩니다. 둘째, PaySim은 synthetic dataset이지만 `nameOrig`, `nameDest`, 금액, 잔액 흐름처럼 account-like identifier와 거래 패턴을 포함하므로 운영 시스템 설계 관점에서는 민감 데이터처럼 다뤄야 합니다.

## 초기 설계

데이터 위치를 세 가지로 분리했습니다.

- `data/raw`: Kaggle 원본 CSV 위치
- `data/processed`: 전처리 전체 산출물 위치
- `data/samples`: 후속 Phase에서 작은 sample만 제한적으로 허용하는 위치

raw와 processed는 Git에 올리지 않습니다. sample은 100~1,000건 이하, raw identifier 미포함, hashed identifier 사용, 작은 파일이라는 조건을 만족할 때만 허용합니다.

## 구현

V2 Phase 1에서는 실제 전처리 script를 만들지 않았습니다. 대신 전처리 전에 repository-level guardrail을 먼저 만들었습니다.

- `data/raw/.gitkeep`
- `data/processed/.gitkeep`
- `data/samples/.gitkeep`
- `.gitignore` data policy
- `scripts/data/README.md`
- `scripts/data/check-data-policy.sh`
- `make data-policy-check`

`check-data-policy.sh`는 tracked 또는 staged 상태의 `data/` 파일을 검사합니다. `data/raw`와 `data/processed`에는 `.gitkeep` 외 파일이 있으면 실패하고, `data/samples`에는 `.jsonl`, `.csv`, `.gitkeep`만 허용합니다. sample 파일은 1MB를 넘으면 실패하도록 시작했습니다.

## 발견한 문제

`.gitkeep` allowlist가 빠지면 디렉터리 자체를 커밋할 수 없습니다. 반대로 sample allowlist를 너무 넓히면 raw CSV를 sample 위치에 넣는 실수를 막기 어렵습니다.

또 하나의 문제는 PaySim이 synthetic이라는 이유로 identifier-like field를 가볍게 볼 수 있다는 점입니다. 실제 개인정보는 아니더라도, 이상거래 탐지 시스템에서 account-like identifier와 거래 흐름은 운영 데이터와 같은 기준으로 다루는 편이 안전합니다.

## 변경한 설계

raw와 processed는 Git에서 차단하고, sample만 제한적으로 허용하는 방향으로 정리했습니다.

README에는 명령과 링크만 두고, 상세 판단은 provenance 문서와 scripts/data README로 분리했습니다. `make final-check`에는 data policy check를 연결해 최종 점검에서 data guardrail이 빠지지 않도록 했습니다.

## 남은 한계

Git ignore와 shell check만으로 sample 내부의 raw identifier를 완벽히 검출할 수는 없습니다. 파일 크기 기준도 모든 위험을 설명하지 못합니다.

Phase 2~4에서는 preprocessing, rejected row handling, sample generation, identifier hashing enforcement를 구현해야 합니다. 그 단계에서 sample 내부 필드 검증과 checksum 기반 재현성 기록을 보강합니다.
