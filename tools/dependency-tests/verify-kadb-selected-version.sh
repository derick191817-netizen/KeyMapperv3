#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"
OUTPUT="$(mktemp)"
GRADLEW_BIN="${GRADLEW_BIN:-./gradlew}"
trap 'rm -f "$OUTPUT"' EXIT

run_insight() {
  "$GRADLEW_BIN" --no-daemon :app:dependencyInsight \
    --dependency "$1" \
    --configuration debugCompileClasspath \
    --console=plain | tee -a "$OUTPUT"
}

run_insight 'com.flyfishxu:kadb-android'
if ! grep -Fq 'com.flyfishxu:kadb-android:2.1.4' "$OUTPUT"; then
  echo 'ERRO: a resolução efetiva não selecionou kadb-android 2.1.4.' >&2
  exit 1
fi
if grep -Fq 'com.flyfishxu:kadb-android:2.1.5' "$OUTPUT"; then
  echo 'ERRO: a variante Kadb 2.1.5 (Java 25 / class major 69) reapareceu.' >&2
  exit 1
fi

for module in bcprov bcpkix bcutil; do
  artifact="org.bouncycastle:${module}-jdk18on"
  run_insight "$artifact"
  if ! grep -Fq "${artifact}:1.86" "$OUTPUT"; then
    echo "ERRO: a resolução efetiva não selecionou ${artifact}:1.86." >&2
    exit 1
  fi
  if grep -Fq "${artifact}:1.84" "$OUTPUT"; then
    echo "ERRO: dependência vulnerável ${artifact}:1.84 reapareceu." >&2
    exit 1
  fi
done

printf '%s\n' 'PASS: Kadb Android 2.1.4 e Bouncy Castle 1.86 foram selecionados pelo Gradle.'
