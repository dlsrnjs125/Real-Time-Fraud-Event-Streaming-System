# V2 PaySim Identifier Hash Policy

## 문제

PaySim은 synthetic dataset이지만 `nameOrig`, `nameDest`는 account-like identifier 형태를 갖습니다. 이 값이 sample, replay 준비 산출물, log, manifest에 남으면 실제 개인정보가 아니더라도 운영 시스템 설계 기준에서는 위험한 습관이 됩니다.

V2 Phase 3에서 safe sample generation을 추가했지만, 두 가지 경계가 더 필요했습니다.

- committed sample이 `default-local` salt로 생성될 수 있는 문제
- hash ID format과 salt metadata가 downstream replay/Rule 검증 기준으로 고정되지 않은 문제

## 초기 설계

전처리 단계에서 raw identifier는 HMAC-SHA256 기반 pseudonym으로 변환합니다.

```text
userId = U-<16 lowercase hex>
accountId = A-<16 lowercase hex>
destinationAccountId = D-<16 lowercase hex>
```

Report와 manifest에는 salt 값이 아니라 `hashSaltSource`만 남깁니다. `default-local` salt는 local smoke/debug 용도로만 허용하고, 공유 또는 커밋 목적이면 env salt를 사용합니다.

## 발견한 문제

Phase 3 기준에서는 sample generation strict target이 있었지만 committed manifest 자체를 `data-policy-check`에서 강하게 막지는 않았습니다.

또한 validator는 identifier prefix만 확인했습니다. Prefix만 확인하면 `U-C12345` 같은 raw-looking value나 uppercase/짧은 hash가 downstream에 섞일 수 있습니다. 이 상태에서는 Phase 5 replay에서 user grouping, idempotency, collision 검증이 흔들릴 수 있습니다.

## 개선

V2 Phase 4에서는 다음을 추가했습니다.

- `--require-non-default-salt`
- `make validate-paysim-strict`
- `make generate-paysim-sample-strict`
- validation script의 ID format validator
- report/manifest `hashAlgorithm=HMAC-SHA256`
- report/manifest `hashIdPrefixLength=16`
- sample manifest default-local 차단
- salt value field 차단
- sample manifest replay collision note

Shell data policy는 완전한 JSON validator가 아닙니다. 그래서 obvious mistake만 막고, 구조적 검증은 Python validator와 sampler가 담당하도록 나눴습니다.

## 검증

이번 Phase의 기본 검증은 fixture 기반입니다.

```bash
make test-data-scripts
make data-policy-check
make ci-check
```

로컬에 processed output이 있는 경우에만 strict full path를 실행합니다.

```bash
export PAYSIM_HASH_SALT="<local-private-salt-for-regeneration>"
make prepare-paysim-smoke
make validate-paysim-strict
make generate-paysim-sample-strict
make data-policy-check
```

`PAYSIM_HASH_SALT` 값은 Git, docs, manifest, report, log에 남기지 않습니다.

## 남은 한계

Full replay는 Phase 5 범위입니다. `eventId`는 row-number deterministic policy를 유지하므로 duplicate/idempotency test에는 유용하지만, 같은 API/DB에 여러 dataset이나 같은 sample을 섞어 넣을 때는 충돌할 수 있습니다.

Phase 5에서는 replay script와 함께 `--event-id-prefix` 옵션을 실제로 구현합니다.

HMAC pseudonymization은 완전한 익명화가 아닙니다. 같은 raw input과 같은 salt를 가진 사람은 같은 pseudonym을 만들 수 있으므로 salt는 secret처럼 관리해야 합니다.
