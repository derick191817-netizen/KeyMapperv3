#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
BRIDGE="$ROOT/app/src/main/java/com/example/keymapper/injection/ShizukuInputCaptureBridge.kt"
EPOCH="$ROOT/app/src/main/java/com/example/keymapper/injection/SessionEpoch.kt"
TEST="$ROOT/app/src/test/java/com/example/keymapper/injection/SessionEpochTest.kt"

for file in "$BRIDGE" "$EPOCH" "$TEST"; do
  test -s "$file" || { echo "ERRO: arquivo obrigatório ausente: $file" >&2; exit 1; }
done

grep -Fq 'private val sessionEpoch = SessionEpoch()' "$BRIDGE"
grep -Fq 'createInputCallback(sessionEpoch.begin())' "$BRIDGE"
grep -Fq 'sessionEpoch.isCurrent(sessionToken)' "$BRIDGE"
grep -Fq 'service.startInputCapture(callback, enabled)' "$BRIDGE"
grep -Fq 'sessionEpoch.invalidate()' "$BRIDGE"
# Assert that stop invalidates the callback epoch before it queues asynchronous cleanup.
python3 - "$BRIDGE" <<'PY'
import sys
from pathlib import Path
lines = Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
start = next(i for i, line in enumerate(lines) if line.strip() == "fun stop() {")
segment = lines[start:]
post = next(i for i, line in enumerate(segment) if "main.post {" in line)
if not any("sessionEpoch.invalidate()" in line for line in segment[:post]):
    raise SystemExit("ERRO: stop() não invalida a sessão antes do cleanup assíncrono")
if not any("active.set(false)" in line for line in segment[:post]):
    raise SystemExit("ERRO: stop() não desativa callbacks imediatamente")
PY
grep -Fq 'startingNewSessionRejectsOldCallbacks' "$TEST"
grep -Fq 'invalidationRejectsPreviousSession' "$TEST"
printf '%s\n' 'PASS: callbacks AIDL isolados por sessão; stop invalida a sessão antes do cleanup assíncrono.'
