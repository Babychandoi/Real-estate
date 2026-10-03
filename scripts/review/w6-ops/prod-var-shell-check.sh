#!/usr/bin/env bash
# The production runbook uses a function so zsh and bash preserve every argument.
set -eu
snippet='bds_prod() { printf "<%s>\n" docker compose -p bds-production --profile edge "$@"; }; bds_prod ps "a b"'
expected=$(printf '<%s>\n' docker compose -p bds-production --profile edge ps 'a b')
for shell in bash zsh; do
  actual=$("$shell" -f -c "$snippet")
  [ "$actual" = "$expected" ] || { echo "FAIL $shell argument preservation"; exit 1; }
  echo "PASS $shell runbook function preserves arguments"
done
