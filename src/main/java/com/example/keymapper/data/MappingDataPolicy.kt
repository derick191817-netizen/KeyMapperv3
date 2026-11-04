package com.example.keymapper.data

/** Normaliza IDs e referências em qualquer fronteira de persistência/importação de perfis. */
object MappingDataPolicy {
    const val MAX_CONTROLS = 128

    /**
     * Garante IDs únicos sem descartar controles válidos. Em caso de ID duplicado, mantém o ID
     * da primeira ocorrência e atribui à seguinte o menor ID livre. Referências legadas para o ID
     * duplicado continuam apontando à primeira ocorrência, a única interpretação não ambígua.
     * Referências quebradas são removidas para que o motor não execute controles inexistentes.
     */
    fun normalizeButtons(input: List<HudButton>): List<HudButton> {
        val source = input.take(MAX_CONTROLS)
        val reservedOriginalIds = source.mapNotNull { it.id.takeIf { id -> id >= 0 } }.toSet()
        val used = HashSet<Int>()
        var nextCandidate = 0
        val unique = source.map { button ->
            val normalizedId = if (button.id >= 0 && used.add(button.id)) {
                button.id
            } else {
                // Não rouba um ID válido que aparece mais tarde no perfil.
                while (nextCandidate in used || nextCandidate in reservedOriginalIds) nextCandidate++
                val allocated = nextCandidate
                used.add(allocated)
                nextCandidate++
                allocated
            }
            if (normalizedId == button.id) button else button.copy(id = normalizedId)
        }
        val ids = unique.mapTo(HashSet()) { it.id }
        val kinds = unique.associate { it.id to it.kind }
        return unique.map { button ->
            val safeMacro = button.macro.asSequence()
                .filter { it != button.id && it in ids }
                .distinct()
                .take(MAX_CONTROLS)
                .toList()
            val targetId = button.linkAimId
            val safeAimLink = targetId.takeIf {
                it >= 0 && it != button.id && kinds[it] == Kind.AIM
            } ?: -1
            if (safeMacro == button.macro && safeAimLink == button.linkAimId) button
            else button.copy(macro = safeMacro, linkAimId = safeAimLink)
        }
    }
}
