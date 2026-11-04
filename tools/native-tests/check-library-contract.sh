#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
KOTLIN="$ROOT/app/src/main/java/com/example/keymapper/injection/NativeBackendLibrary.kt"
SERVICE="$ROOT/app/src/main/java/com/example/keymapper/injection/KeyMapperShellService.kt"
PROBE="$ROOT/app/src/main/java/com/example/keymapper/diagnostics/InputBackendProbe.kt"
CMAKE="$ROOT/app/src/main/cpp/CMakeLists.txt"

[[ -f "$KOTLIN" && -f "$SERVICE" && -f "$PROBE" && -f "$CMAKE" ]]
LOAD_NAME="$(sed -nE 's/^[[:space:]]*const val LOAD_NAME = "([^"]+)"[[:space:]]*$/\1/p' "$KOTLIN")"
FILE_NAME="$(sed -nE 's/^[[:space:]]*const val FILE_NAME = "([^"]+)"[[:space:]]*$/\1/p' "$KOTLIN")"
[[ -n "$LOAD_NAME" && -n "$FILE_NAME" ]]
[[ "$FILE_NAME" == "lib${LOAD_NAME}.so" ]]
grep -Eq "add_library\(${LOAD_NAME}[[:space:]]+SHARED[[:space:]]+evdev_capture\.cpp[[:space:]]+uinput_touch\.cpp\)" "$CMAKE"
grep -Fq 'System.loadLibrary(NativeBackendLibrary.LOAD_NAME)' "$SERVICE"
grep -Fq 'NativeBackendLibrary.FILE_NAME' "$PROBE"
if grep -RInE 'libkeymapper_input\.so|System\.loadLibrary\("keymapper_input"\)' "$ROOT/app/src"; then
  echo "Legacy native library identifier remains in app source" >&2
  exit 1
fi
echo "PASS: CMake target, Kotlin loadLibrary name, packaged filename and diagnostic probe are consistent"
