#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CXX="${CXX:-g++}"
if [[ -z "${JAVA_HOME:-}" ]]; then
  if ! command -v javac >/dev/null 2>&1; then
    echo "JAVA_HOME or javac is required for JNI header validation" >&2
    exit 2
  fi
  JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
fi
"$CXX" -std=c++17 -fsyntax-only -Wall -Wextra -Werror=return-type \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  "$ROOT/app/src/main/cpp/evdev_capture.cpp"
"$CXX" -std=c++17 -fsyntax-only -Wall -Wextra -Werror=return-type \
  -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
  "$ROOT/app/src/main/cpp/uinput_touch.cpp"
echo "PASS: evdev_capture.cpp and uinput_touch.cpp syntax"
