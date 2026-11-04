#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
OUT="${TMPDIR:-/tmp}/keymapper-evdev-batch-recovery-test"
CXX="${CXX:-g++}"
"$CXX" -std=c++17 -Wall -Wextra -Werror -I"$ROOT/app/src/main/cpp" \
  "$ROOT/tools/native-tests/evdev_batch_recovery_test.cpp" -o "$OUT"
"$OUT"
echo "PASS: evdev JNI batch recovery host tests"
