#!/bin/sh

set -eu

MAX_SAMPLE_BYTES=1048576

fail() {
  echo "FAIL: $1" >&2
  exit 1
}

tracked_files() {
  git ls-files --cached -- data
}

is_allowed_sample() {
  case "$1" in
    data/samples/.gitkeep|data/samples/paysim-events-sample.jsonl|data/samples/paysim-labels-sample.jsonl|data/samples/paysim-sample-manifest.json)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

check_sample_size() {
  file="$1"

  size=$(git cat-file -s ":$file" 2>/dev/null || wc -c < "$file" | tr -d ' ')
  if [ "$size" -gt "$MAX_SAMPLE_BYTES" ]; then
    fail "sample file is larger than 1MB: $file"
  fi
}

check_staged_content() {
  file="$1"
  pattern="$2"
  message="$3"

  if git show ":$file" 2>/dev/null | grep -E "$pattern" >/dev/null; then
    fail "$message: $file"
  fi
}

check_sample_content() {
  file="$1"

  case "$file" in
    data/samples/paysim-events-sample.jsonl)
      check_staged_content "$file" '"(isFraud|isFlaggedFraud|sourceFlaggedFraud|nameOrig|nameDest)"|[CM][0-9]+' "sample event file contains label or raw identifier leakage"
      ;;
    data/samples/paysim-labels-sample.jsonl)
      check_staged_content "$file" '"(nameOrig|nameDest)"|[CM][0-9]+' "sample label file contains raw identifier leakage"
      ;;
    data/samples/paysim-sample-manifest.json)
      check_staged_content "$file" '"(hashSaltValue|saltValue|nameOrig|nameDest)"|[CM][0-9]+' "sample manifest contains raw identifier or salt leakage"
      ;;
  esac
}

tracked_files | while IFS= read -r file; do
  case "$file" in
    data/raw/.gitkeep|data/processed/.gitkeep)
      ;;
    data/raw/*)
      fail "raw data file must not be committed: $file"
      ;;
    data/processed/*)
      fail "processed data file must not be committed: $file"
      ;;
    data/samples/*)
      case "$file" in
        data/samples/*.csv|data/samples/raw*|data/samples/full*|data/samples/*processed*)
          fail "sample file name is not allowed: $file"
          ;;
      esac
      if ! is_allowed_sample "$file"; then
        fail "sample file extension is not allowed: $file"
      fi
      check_sample_size "$file"
      check_sample_content "$file"
      ;;
  esac
done

echo "PASS: data policy check passed"
