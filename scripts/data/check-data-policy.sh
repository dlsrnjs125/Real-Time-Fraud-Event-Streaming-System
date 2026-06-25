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
    data/samples/.gitkeep|data/samples/*.jsonl|data/samples/*.csv)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

check_sample_size() {
  file="$1"

  if [ ! -f "$file" ]; then
    return 0
  fi

  size=$(wc -c < "$file" | tr -d ' ')
  if [ "$size" -gt "$MAX_SAMPLE_BYTES" ]; then
    fail "sample file is larger than 1MB: $file"
  fi
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
      if ! is_allowed_sample "$file"; then
        fail "sample file extension is not allowed: $file"
      fi
      check_sample_size "$file"
      ;;
  esac
done

echo "PASS: data policy check passed"
