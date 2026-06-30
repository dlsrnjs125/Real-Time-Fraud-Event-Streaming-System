# V2 PaySim 데이터 Python Toolchain 분리하기

## 문제

프로젝트의 주 언어와 실행 환경은 Java/Spring Boot와 Gradle입니다. 그런데 V2 PaySim workflow에는 Kaggle dataset download와 CSV preprocessing을 위한 Python helper가 필요합니다.

처음에는 `pip install kagglehub`를 직접 실행하고 Makefile에서 `python3 scripts/data/...`를 호출하는 방식으로도 충분해 보였습니다. 하지만 이 방식은 개발자 global Python 환경에 의존하고, Java 프로젝트의 재현 가능한 실행 흐름과 잘 맞지 않습니다.

## 초기 설계

V2 Phase 2에서는 다음 범위에 집중했습니다.

- KaggleHub download helper
- streaming CSV preprocessing script
- fixture 기반 unittest
- raw/processed data commit guardrail

이때 Python dependency는 문서에서 수동 설치로 안내했습니다.

```bash
pip install kagglehub
```

## 발견한 문제

global pip 설치는 간단하지만 다음 문제가 있습니다.

- 개발자마다 Python version과 site-package 상태가 다릅니다.
- Java/Gradle 중심 프로젝트에서 Python dependency 경계가 흐려집니다.
- CI에서 어디까지 Python 환경을 준비해야 하는지 애매해집니다.
- Kaggle download까지 CI에 묶으면 인증, 네트워크, 대용량 파일 때문에 불안정해질 수 있습니다.

## 변경한 설계

Python toolchain을 PaySim data helper 전용으로 격리했습니다.

```text
scripts/data/requirements.txt
scripts/data/bootstrap-data-env.sh
.venv-data/
```

`make data-env`는 `.venv-data`를 만들고 `scripts/data/requirements.txt`를 설치합니다.

```bash
make data-env
```

그 뒤 data script target은 모두 같은 venv Python을 사용합니다.

```bash
make download-paysim
make prepare-paysim-smoke
make test-data-scripts
```

Java application runtime은 이 Python environment에 의존하지 않습니다. Python은 PaySim data acquisition과 preprocessing helper에만 사용합니다.

## CI 기준

CI에서는 다음까지만 실행합니다.

- `.venv-data` 생성
- fixture 기반 data script unittest
- data policy check
- Java test/assemble

CI에서는 Kaggle dataset download와 full preprocessing을 실행하지 않습니다. 외부 credential, network, large file I/O가 CI의 기본 pass/fail signal을 흐리지 않게 하기 위해서입니다.

## 검증

검증 명령은 다음과 같습니다.

```bash
bash -n scripts/data/bootstrap-data-env.sh
make data-env
make test-data-scripts
make data-policy-check
make ci-check
```

이번 검증에서는 KaggleHub download도 로컬에서 성공했습니다.

```bash
make download-paysim
make prepare-paysim-smoke
```

`make download-paysim`은 expected PaySim CSV를 ignored `data/raw` 경로로 복사했고, `make prepare-paysim-smoke`는 1,000 rows를 accepted 처리했습니다. raw CSV와 processed smoke output은 Git에 추가하지 않습니다.

## 남은 한계

Kaggle 인증은 여전히 사용자 로컬 환경에 필요합니다. token이나 credential은 Git, docs, logs, `.venv-data`에 저장하지 않습니다.

Full dataset download와 preprocessing은 CI에서 검증하지 않습니다. 필요한 경우 로컬 evidence로 별도 기록합니다.

이 toolchain은 Phase 3 validation/sampling 구현을 위한 실행 기반일 뿐입니다. Validation script, sample generation, replay pipeline, Java Rule Engine V2는 후속 Phase에서 별도로 구현합니다.
