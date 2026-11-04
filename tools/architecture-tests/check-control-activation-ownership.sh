#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY="$ROOT/app/src/main/java/com/example/keymapper/domain/ControlActivationPolicy.kt"
ENGINE="$ROOT/app/src/main/java/com/example/keymapper/mapping/MappingEngine.kt"
TOUCH="$ROOT/app/src/main/java/com/example/keymapper/injection/TouchInjector.kt"
TEST="$ROOT/app/src/test/java/com/example/keymapper/domain/ControlActivationPolicyTest.kt"
for file in "$POLICY" "$ENGINE" "$TOUCH" "$TEST"; do
  [[ -s "$file" ]] || { echo "FAIL: arquivo de propriedade de contato ausente: $file" >&2; exit 1; }
done
grep -Fq 'private val pressOwnership = ControlPressOwnership()' "$ENGINE"
grep -Fq 'private val aimActivations = AimActivationRegistry()' "$ENGINE"
grep -Fq 'private fun pressOwned' "$ENGINE"
grep -Fq 'private fun releaseOwned' "$ENGINE"
grep -Fq 'PressSourceKind.FIRE_LINK_AIM' "$ENGINE"
grep -Fq 'PressSourceKind.AIM_FIRE' "$ENGINE"
grep -Fq 'PressSourceKind.COMBO' "$ENGINE"
grep -Fq 'fireKey(target, down, comboSource)' "$ENGINE"
grep -Fq 'linkAim(target, s, down, comboSource)' "$ENGINE"
grep -Fq 'aimKey(target, s, down, comboSource)' "$ENGINE"
grep -Fq 'ToggleActivation(b.id, source)' "$ENGINE"
grep -Fq 'f.kind != Kind.FIRE' "$ENGINE"
grep -Fq 'pressOwnership.clear()' "$ENGINE"
grep -Fq 'aimActivations.clear()' "$ENGINE"
grep -Fq 'fun isReleasePending' "$TOUCH"
grep -Fq 'transientTapCannotReleaseAConcurrentHold' "$TEST"
grep -Fq 'physicalInputAndTwoCombosHaveIndependentOwnership' "$TEST"
echo 'PASS: contato compartilhado possui ownership por origem; Fire/Aim e soltura são independentes e testáveis.'
