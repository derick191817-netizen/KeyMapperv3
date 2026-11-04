package com.example.keymapper.data

import org.json.JSONObject

/** A single deterministic action in a sequential macro. Combo keeps using HudButton.macro. */
object MacroAction {
    const val WAIT = 0
    const val TAP = 1
    const val HOLD = 2
    const val SWIPE = 3
}

data class MacroStep(
    val action: Int,
    val targetId: Int = -1,
    val durationMs: Int = 250
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("action", action)
        put("targetId", targetId)
        put("durationMs", durationMs)
    }

    companion object {
        fun fromJson(json: JSONObject): MacroStep? {
            val action = json.optInt("action", -1)
            if (action !in MacroAction.WAIT..MacroAction.SWIPE) return null
            val rawTarget = json.optInt("targetId", -1)
            val target = if (rawTarget >= 0) rawTarget else -1
            val duration = json.optInt("durationMs", 250).coerceIn(10, 10_000)
            return MacroStep(action, target, duration)
        }
    }
}

/** Shared limits and target rules used by persistence, editor and engine. */
object MacroStepPolicy {
    const val MAX_STEPS = 32
    const val MAX_REPEATS = 20
    const val MIN_DURATION_MS = 10
    const val MAX_DURATION_MS = 10_000

    fun durationMs(value: Int): Int = value.coerceIn(MIN_DURATION_MS, MAX_DURATION_MS)
    fun durationMs(action: Int, value: Int): Int = when (action) {
        MacroAction.TAP -> 25
        MacroAction.SWIPE -> value.coerceIn(50, 2_000)
        else -> durationMs(value)
    }
    fun repeatCount(value: Int): Int = value.coerceIn(1, MAX_REPEATS)

    fun isTargetEligible(action: Int, target: HudButton): Boolean {
        if (target.isCombo || target.macro.isNotEmpty()) return false
        return when (action) {
            MacroAction.TAP, MacroAction.HOLD ->
                (target.kind == Kind.STANDARD || target.kind == Kind.FIRE) &&
                    target.mode != Mode.RAPID && target.mode != Mode.TOGGLE && target.mode != Mode.DOUBLE
            MacroAction.SWIPE -> target.kind == Kind.SWIPE
            MacroAction.WAIT -> false
            else -> false
        }
    }

    fun normalizeSteps(steps: List<MacroStep>, ownerId: Int, buttons: List<HudButton>): List<MacroStep> {
        val byId = buttons.associateBy { it.id }
        return steps.asSequence().take(MAX_STEPS).mapNotNull { step ->
            val duration = durationMs(step.action, step.durationMs)
            if (step.action == MacroAction.WAIT) {
                MacroStep(MacroAction.WAIT, -1, duration)
            } else {
                val target = byId[step.targetId] ?: return@mapNotNull null
                if (target.id == ownerId || !isTargetEligible(step.action, target)) return@mapNotNull null
                MacroStep(step.action, target.id, duration)
            }
        }.toList()
    }

    fun actionLabel(action: Int): String = when (action) {
        MacroAction.WAIT -> "Pausa"
        MacroAction.TAP -> "Toque"
        MacroAction.HOLD -> "Segurar"
        MacroAction.SWIPE -> "Arrastar"
        else -> "Ação inválida"
    }
}
/**
 * Cursor sequencial determinístico. Pertence à thread do executor; cada chamada retorna
 * exatamente um passo e nunca ultrapassa o limite de ciclos.
 */
class MacroSequenceCursor(steps: List<MacroStep>, repeatCount: Int) {
    private val safeSteps = steps.take(MacroStepPolicy.MAX_STEPS).toList()
    private val totalCycles = MacroStepPolicy.repeatCount(repeatCount)
    private var stepIndex = 0
    private var completedCycles = 0

    val isFinished: Boolean
        get() = safeSteps.isEmpty() ||
            completedCycles + (if (stepIndex >= safeSteps.size) 1 else 0) >= totalCycles

    fun nextStep(): MacroStep? {
        if (isFinished) return null
        if (stepIndex >= safeSteps.size) {
            completedCycles++
            stepIndex = 0
        }
        return safeSteps[stepIndex++]
    }
}

