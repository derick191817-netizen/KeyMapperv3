#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY="$ROOT/app/src/main/java/com/example/keymapper/domain/HudPlacementPolicy.kt"
HUD="$ROOT/app/src/main/java/com/example/keymapper/overlay/HudView.kt"
TEST="$ROOT/app/src/test/java/com/example/keymapper/domain/HudPlacementPolicyTest.kt"

for file in "$POLICY" "$HUD" "$TEST"; do
  [[ -s "$file" ]] || { echo "FAIL: arquivo ausente/vazio: $file" >&2; exit 1; }
done

grep -q 'object HudPlacementPolicy' "$POLICY"
grep -q 'fun findPosition' "$POLICY"
grep -q 'import com.example.keymapper.domain.HudPlacementPolicy' "$HUD"
grep -q 'private fun findFreeButtonPosition' "$HUD"
grep -q 'HudPlacementPolicy.findPosition' "$HUD"

# Both creation and duplication must use the same collision-aware placement contract.
ADD_SECTION="$(sed -n '/fun addButton(key: Int)/,/private fun sizeDelta/p' "$HUD")"
DUP_SECTION="$(sed -n '/private fun duplicateSelected()/,/private fun findFreeButtonPosition/p' "$HUD")"
! grep -q 'if (kind == Kind.STANDARD)' <<<"$ADD_SECTION"
! grep -q 'if (original.kind == Kind.STANDARD)' <<<"$DUP_SECTION"
grep -q 'findFreeButtonPosition' <<<"$ADD_SECTION"
grep -q 'findFreeButtonPosition' <<<"$DUP_SECTION"
grep -q 'x = placement.x' <<<"$ADD_SECTION"
grep -q 'y = placement.y' <<<"$ADD_SECTION"
grep -q 'x = placement.x' <<<"$DUP_SECTION"
grep -q 'y = placement.y' <<<"$DUP_SECTION"

grep -q 'emptyCanvasUsesPreferredCenter' "$TEST"
grep -q 'placementMovesAwayFromOccupiedCenter' "$TEST"
grep -q 'candidateRadiusAlwaysRemainsInsideArea' "$TEST"

echo "PASS: Tecla/Tiro/Mira creation and duplication use collision-aware placement; contract tests exist"
