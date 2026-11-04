#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
POLICY="$ROOT/app/src/main/java/com/example/keymapper/injection/SessionStartPolicy.kt"
BRIDGE="$ROOT/app/src/main/java/com/example/keymapper/injection/ShizukuInputCaptureBridge.kt"
TEST="$ROOT/app/src/test/java/com/example/keymapper/injection/SessionStartPolicyTest.kt"

for file in "$POLICY" "$BRIDGE" "$TEST"; do
  test -s "$file" || { echo "FAIL: arquivo ausente/vazio: $file" >&2; exit 1; }
done

grep -q 'SessionStartPolicy.shouldCreateFreshSession(active.get(), inputCallback != null)' "$BRIDGE"
grep -q 'inputCallback = createInputCallback(sessionEpoch.begin())' "$BRIDGE"
# O início de uma sessão precisa ser condicionado pela política, não incondicional.
start_block="$(sed -n '/fun start(/,/^    private fun registerLifecycleListeners/p' "$BRIDGE")"
policy_line="$(printf '%s\n' "$start_block" | grep -n 'SessionStartPolicy.shouldCreateFreshSession' || true)"
epoch_line="$(printf '%s\n' "$start_block" | grep -n 'sessionEpoch.begin()' || true)"
test -n "$policy_line" && test -n "$epoch_line" || { echo "FAIL: criação de epoch não está protegida pela política" >&2; exit 1; }
policy_number="${policy_line%%:*}"
epoch_number="${epoch_line%%:*}"
(( epoch_number > policy_number )) || { echo "FAIL: epoch deve ser criado depois da decisão de política" >&2; exit 1; }
printf '%s\n' "$start_block" | grep -q 'if (SessionStartPolicy.shouldCreateFreshSession(active.get(), inputCallback != null)) {' || {
  echo "FAIL: criação de callback não está dentro da condição idempotente" >&2; exit 1;
}

echo "PASS: start() reutiliza o callback da sessão ativa e só cria novo epoch para lifecycle novo/recuperado."
