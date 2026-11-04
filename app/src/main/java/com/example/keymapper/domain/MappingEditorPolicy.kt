package com.example.keymapper.domain

import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.Mode
import com.example.keymapper.data.MacroStep
import com.example.keymapper.data.MacroStepPolicy

/** Regras puras do editor, separadas da View para permitir regressão determinística. */
object MappingEditorPolicy {
    data class KeyBinding(val controlId: Int, val label: String, val code: Int)

    /** Labels compartilhados pela prévia, tabela e painel de propriedades. */
    fun controlKindLabel(kind: Int): String = when (kind) {
        Kind.FIRE -> "Fire"
        Kind.AIM -> "Aim / Sight"
        Kind.SWIPE -> "Swipe"
        Kind.MACRO -> "Macro"
        else -> "Controle"
    }

    /** Nome funcional mostrado na biblioteca/tabela; Tap e Hold continuam usando o motor padrão. */
    fun controlDisplayLabel(kind: Int, mode: Int, isCombo: Boolean = false): String = when {
        isCombo -> "Combo"
        else -> when (kind) {
            Kind.FIRE -> "Fire"
            Kind.AIM -> "Aim / Sight"
            Kind.SWIPE -> "Swipe"
            Kind.MACRO -> "Macro"
            else -> when (mode) {
                Mode.TAP -> "Tap"
                Mode.RAPID -> "Rapid Tap"
                Mode.TOGGLE -> "Alternar"
                Mode.DOUBLE -> "Duplo toque"
                else -> "Hold"
            }
        }
    }

    /** Combo não pode incluir a si mesmo, outro Combo, Swipe cancelável ou Rapid Tap contínuo. */
    fun isComboTargetEligible(ownerId: Int, target: HudButton): Boolean =
        target.id != ownerId &&
            !target.isCombo && target.macro.isEmpty() &&
            target.kind != Kind.SWIPE &&
            target.kind != Kind.MACRO &&
            !(target.kind == Kind.STANDARD && target.mode == Mode.RAPID)

    fun isMacroTargetEligible(ownerId: Int, action: Int, target: HudButton): Boolean =
        target.id != ownerId && MacroStepPolicy.isTargetEligible(action, target)

    fun normalizedMacroSteps(steps: List<MacroStep>, ownerId: Int, buttons: List<HudButton>): List<MacroStep> =
        MacroStepPolicy.normalizeSteps(steps, ownerId, buttons)

    fun normalizedMacroRepeatCount(value: Int): Int = MacroStepPolicy.repeatCount(value)

    /** Fire sugere M1, mas nunca toma uma associação existente nem um atalho global do serviço. */
    fun defaultFireBinding(editKey: Int, pauseKey: Int, bindings: Iterable<KeyBinding>): Int =
        if (findConflict(-1, Int.MIN_VALUE, bindings) == null &&
            !conflictsWithGlobalShortcut(-1, editKey, pauseKey)
        ) -1 else 0

    /** Retorna a associação conflitante, ignorando o próprio controle e códigos não atribuídos. */
    fun findConflict(
        candidateCode: Int,
        targetControlId: Int,
        bindings: Iterable<KeyBinding>
    ): KeyBinding? {
        if (candidateCode == 0) return null
        return bindings.firstOrNull { it.controlId != targetControlId && it.code == candidateCode }
    }

    /** Perfis serializam no máximo [capacity] controles; nunca cria IDs negativos ou duplicados. */
    fun conflictsWithGlobalShortcut(candidateCode: Int, editKey: Int, pauseKey: Int): Boolean =
        candidateCode != 0 && (candidateCode == editKey || candidateCode == pauseKey)

    /** Finds a bound control in any loaded profile that would be shadowed by a global shortcut. */
    fun findGlobalShortcutConflict(
        editKey: Int,
        pauseKey: Int,
        bindings: Iterable<KeyBinding>
    ): KeyBinding? = bindings.firstOrNull {
        it.code != 0 && (it.code == editKey || it.code == pauseKey)
    }

    fun nextAvailableId(existingIds: Iterable<Int>, existingCount: Int, capacity: Int = 128): Int? {
        if (capacity <= 0 || existingCount >= capacity) return null
        val used = existingIds.filter { it >= 0 }.toHashSet()
        return (0..capacity).firstOrNull { it !in used }
    }

    /** Um toque em um alvo Canvas só é um clique se o dedo terminar próximo do ponto inicial. */
    fun isTap(startX: Float, startY: Float, endX: Float, endY: Float, slop: Float): Boolean {
        if (!startX.isFinite() || !startY.isFinite() || !endX.isFinite() || !endY.isFinite() || !slop.isFinite()) return false
        val dx = endX - startX
        val dy = endY - startY
        val safeSlop = slop.coerceAtLeast(0f)
        return dx * dx + dy * dy <= safeSlop * safeSlop
    }

    fun normalizedPosition(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0.5f

    /** Clamps a control center so its visual radius stays inside the normalized play area. */
    fun boundedCenter(value: Float, marginFraction: Float): Float {
        val margin = if (marginFraction.isFinite()) marginFraction.coerceIn(0f, 0.5f) else 0f
        return normalizedPosition(value).coerceIn(margin, 1f - margin)
    }
}
