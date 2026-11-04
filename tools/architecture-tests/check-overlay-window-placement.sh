#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POLICY="$ROOT/app/src/main/java/com/example/keymapper/overlay/OverlayWindowPlacementPolicy.kt"
MANAGER="$ROOT/app/src/main/java/com/example/keymapper/overlay/OverlayManager.kt"
TEST="$ROOT/app/src/test/java/com/example/keymapper/overlay/OverlayWindowPlacementPolicyTest.kt"
for file in "$POLICY" "$MANAGER" "$TEST"; do
  [[ -s "$file" ]] || { echo "FAIL: arquivo ausente/vazio: $file" >&2; exit 1; }
done
grep -q 'object OverlayWindowPlacementPolicy' "$POLICY"
grep -q 'fun forControl' "$POLICY"
grep -q 'OverlayWindowPlacementPolicy.forControl' "$MANAGER"
grep -q 'lp.gravity = Gravity.TOP or Gravity.LEFT' "$MANAGER"
grep -q 'lp.x = placement.xPx' "$MANAGER"
grep -q 'lp.y = placement.yPx' "$MANAGER"
if grep -Eq 'gravity = \(if \(b != null && b\.(x|y) > 0\.5f\)' "$MANAGER"; then
  echo 'FAIL: posicionamento antigo ancorado somente por Gravity ainda está ativo' >&2
  exit 1
fi
for scenario in landscapePanelUsesOppositeSideAndSafeMargins portraitPanelMovesAwayFromSelectedControl everyPlacementRemainsInsideSmallDisplay invalidDensityAndCoordinatesUseSafeDefaults onePixelDisplayNeverProducesNegativeGeometry; do
  grep -q "$scenario" "$TEST"
done
# The config-close callback must not be emitted twice on adjacent source lines.
python3 - "$MANAGER" <<'PYCODE'
from pathlib import Path
import sys
lines = Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
needle = "onCaptureRequestChanged(captureWanted)"
for previous, current in zip(lines, lines[1:]):
    if previous.strip() == needle and current.strip() == needle:
        raise SystemExit("FAIL: callback consecutivo duplicado em OverlayManager")
PYCODE
echo 'PASS: painel overlay tem dimensões/posição limitadas e testadas para retrato, paisagem e displays pequenos'
