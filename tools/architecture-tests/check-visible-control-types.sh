#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
EDITOR="$ROOT/app/src/main/java/com/example/keymapper/ui/MappingEditorScreen.kt"
SERVICE="$ROOT/app/src/main/java/com/example/keymapper/service/MappingService.kt"
SETTINGS="$ROOT/app/src/main/java/com/example/keymapper/ui/SettingsScreen.kt"
POLICY="$ROOT/app/src/main/java/com/example/keymapper/domain/MappingEditorPolicy.kt"
CONFIG="$ROOT/app/src/main/java/com/example/keymapper/overlay/ConfigPanelView.kt"
ENGINE="$ROOT/app/src/main/java/com/example/keymapper/mapping/MappingEngine.kt"
MODEL="$ROOT/app/src/main/java/com/example/keymapper/data/HudButton.kt"
for file in "$EDITOR" "$SERVICE" "$SETTINGS" "$POLICY" "$CONFIG" "$ENGINE" "$MODEL"; do
  [[ -s "$file" ]] || { echo "FAIL: arquivo obrigatório ausente/vazio: $file" >&2; exit 1; }
done

# Main library order and distinct control routes.
editor_library="$(sed -n '/WorkspacePanel("Biblioteca HUD"/,/WorkspacePanel("Avançado"/p' "$EDITOR")"
for token in 'ControlLibraryTile("Tap"' 'ControlLibraryTile("Hold"' 'ControlLibraryTile("Fire"' 'ControlLibraryTile("WASD / Stick"' 'ControlLibraryTile("Aim / Sight"' 'ControlLibraryTile("Swipe"'; do
  grep -Fq "$token" <<<"$editor_library" || { echo "FAIL: ferramenta principal ausente: $token" >&2; exit 1; }
done
grep -Fq 'ControlLibraryTile("Macro"' "$EDITOR" || { echo 'FAIL: ferramenta Macro sequencial ausente da seção Avançado.' >&2; exit 1; }
fire_line="$(grep -n 'ControlLibraryTile("Fire"' <<<"$editor_library" | head -1 | cut -d: -f1)"
tap_line="$(grep -n 'ControlLibraryTile("Tap"' <<<"$editor_library" | head -1 | cut -d: -f1)"
hold_line="$(grep -n 'ControlLibraryTile("Hold"' <<<"$editor_library" | head -1 | cut -d: -f1)"
(( tap_line < hold_line && hold_line < fire_line )) || { echo 'FAIL: Fire não está na terceira posição.' >&2; exit 1; }
grep -Fq 'onAddControl(Kind.FIRE, Mode.HOLD, "Fire")' "$EDITOR"
grep -Fq 'onAddControl(Kind.SWIPE, Mode.HOLD, "Swipe")' "$EDITOR"
grep -Fq 'onAddControl(Kind.STANDARD, Mode.RAPID, "Rapid Tap")' "$EDITOR"
grep -Fq 'onAddControl(Kind.MACRO, Mode.HOLD, "Macro")' "$EDITOR"

# WASD/Stick must be honest about reusing the existing virtual movement zone.
grep -Fq 'o backend ainda' "$EDITOR"
grep -Fq 'onOpenSettings(2)' "$EDITOR"

# The controls must have real creation/configuration/engine routes.
grep -Fq 'Kind.SWIPE -> buildSwipe(b)' "$CONFIG"
grep -Fq 'keyBlock(b, 1, "Tecla ou botão de ativação"' "$CONFIG"
grep -Fq 'Destino X (%)' "$CONFIG"
grep -Fq 'Destino Y (%)' "$CONFIG"
grep -Fq 'Duração (ms)' "$CONFIG"
grep -Fq 'Kind.SWIPE -> swipeKey(b, down)' "$ENGINE"
grep -Fq 'Kind.MACRO -> macroKey(b, s, down)' "$ENGINE"
grep -Fq 'Kind.FIRE -> buildFire(b)' "$CONFIG"
grep -Fq 'Kind.FIRE -> {' "$ENGINE"
grep -Fq 'private fun buildFire(b: HudButton)' "$CONFIG"
grep -Fq 'private fun buildAim(b: HudButton)' "$CONFIG"

# Combo cannot silently fall back to a standard tap when it has no targets.
grep -Fq 'if (b.isCombo || b.macro.isNotEmpty())' "$ENGINE"
grep -Fq 'if (b.macro.isEmpty()) return@Runnable' "$ENGINE"
grep -Fq 'isCombo = true' "$CONFIG"
grep -Fq 'isCombo = kind != Kind.MACRO && isCombo' "$MODEL"
grep -Fq 'fun isComboTargetEligible' "$POLICY"
grep -Fq 'private fun buildSwipe(b: HudButton)' "$CONFIG"
grep -Fq 'Desativar Rapid Tap' "$CONFIG"
grep -Fq 'val isCombo = b.optBoolean' "$MODEL"
grep -Fq 'put("isCombo", isCombo)' "$MODEL"

# Advanced feature disclosure must be truthful; macro is implemented, analog gamepad is not.
grep -Fq 'Macro sequencial' "$EDITOR"
grep -Fq 'Entrada analógica física de gamepad ainda não implementada' "$EDITOR"
grep -Fq 'Scroll' "$EDITOR"

# Shared user-facing labels are asserted by tests and the settings screen.
grep -Fq 'fun controlDisplayLabel(kind: Int, mode: Int, isCombo: Boolean = false)' "$POLICY"
grep -Fq 'b.kind == Kind.SWIPE -> "Swipe (arrastar)"' "$SETTINGS"
grep -Fq 'libraryKeepsTapHoldFireAndSightAsDistinctUserFacingTools' "$ROOT/app/src/test/java/com/example/keymapper/domain/MappingEditorPolicyTest.kt"
grep -Fq 'swipeProgressAndInterpolationAreDeterministic' "$ROOT/app/src/test/java/com/example/keymapper/domain/ControlBehaviorPolicyTest.kt"
echo 'PASS: biblioteca Tap/Hold/Fire/WASD+Stick/Aim/Swipe/Macro, configurações, Combo, Rapid Tap e motor têm rotas verificáveis.'
