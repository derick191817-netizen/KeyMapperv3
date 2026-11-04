package com.example.keymapper.domain

/** Identifica de forma estável quem mantém um contato lógico pressionado. */
data class PressSource(val kind: Int, val sourceId: Long = 0L)

object PressSourceKind {
    const val DIRECT = 0
    const val TRANSIENT_TAP = 1
    const val AIM_KEY = 2
    const val FIRE_LINK_AIM = 3
    const val AIM_FIRE = 4
    const val COMBO = 5
}

/**
 * Propriedade compartilhada de um toque contínuo. O contato deve descer no primeiro owner
 * e subir somente quando o último owner for removido.
 * A classe é pura e deve ser acessada pela thread serializada do MappingEngine.
 */
class ControlPressOwnership {
    data class AcquireResult(val accepted: Boolean, val firstOwner: Boolean)

    private val ownersByControl = HashMap<Int, LinkedHashSet<PressSource>>()

    fun acquire(controlId: Int, source: PressSource): AcquireResult {
        if (controlId < 0) return AcquireResult(accepted = false, firstOwner = false)
        val owners = ownersByControl.getOrPut(controlId) { LinkedHashSet() }
        if (!owners.add(source)) return AcquireResult(accepted = false, firstOwner = false)
        return AcquireResult(accepted = true, firstOwner = owners.size == 1)
    }

    /** Retorna true somente quando esta remoção libera o último owner do controle. */
    fun release(controlId: Int, source: PressSource): Boolean {
        val owners = ownersByControl[controlId] ?: return false
        if (!owners.remove(source)) return false
        if (owners.isEmpty()) {
            ownersByControl.remove(controlId)
            return true
        }
        return false
    }

    fun contains(controlId: Int, source: PressSource): Boolean =
        ownersByControl[controlId]?.contains(source) == true

    fun ownerCount(controlId: Int): Int = ownersByControl[controlId]?.size ?: 0

    fun clear() = ownersByControl.clear()
}

/**
 * Mantém a seleção de mira enquanto existir pelo menos uma origem ativa.
 * A mira ativada mais recentemente fica selecionada; ao sair, a anterior é restaurada.
 */
class AimActivationRegistry {
    private val sourcesByAim = LinkedHashMap<Int, LinkedHashSet<PressSource>>()

    fun activate(aimId: Int, source: PressSource): Boolean {
        if (aimId < 0) return false
        val sources = sourcesByAim.getOrPut(aimId) { LinkedHashSet() }
        if (!sources.add(source)) return false
        // A ativação mais recente passa a controlar a câmera.
        sourcesByAim.remove(aimId)
        sourcesByAim[aimId] = sources
        return true
    }

    fun deactivate(aimId: Int, source: PressSource): Boolean {
        val sources = sourcesByAim[aimId] ?: return false
        if (!sources.remove(source)) return false
        if (sources.isEmpty()) sourcesByAim.remove(aimId)
        return true
    }

    /** Alterna somente a origem indicada, sem desativar outras fontes da mesma mira. */
    fun toggle(aimId: Int, source: PressSource): Boolean {
        val active = sourcesByAim[aimId]?.contains(source) == true
        if (active) deactivate(aimId, source) else activate(aimId, source)
        return !active
    }

    fun contains(aimId: Int, source: PressSource): Boolean =
        sourcesByAim[aimId]?.contains(source) == true

    fun activeAimId(): Int = sourcesByAim.keys.lastOrNull() ?: -1

    fun clear() = sourcesByAim.clear()
}
