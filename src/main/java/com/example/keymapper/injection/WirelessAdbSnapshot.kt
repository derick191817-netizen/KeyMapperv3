package com.example.keymapper.injection

/** Dados obtidos de uma sessão ADB TLS autenticada. Nunca contém código de pareamento ou chave privada. */
data class WirelessAdbSnapshot(
    val host: String,
    val connectionPort: Int,
    val uid: Int?,
    val manufacturer: String,
    val model: String,
    val androidApi: Int?,
    val widthPx: Int?,
    val heightPx: Int?,
    val densityDpi: Int?,
    val isLocalEndpoint: Boolean,
    val capturedAtEpochMs: Long
) {
    fun report(): String = buildString {
        appendLine("ADB sem fio conectado — cliente independente do Shizuku")
        appendLine("Endpoint TLS: $host:$connectionPort")
        appendLine("Identidade remota: ${when (uid) {
            0 -> "root (UID 0)"
            2000 -> "shell/ADB (UID 2000)"
            null -> "UID não identificado"
            else -> "UID $uid"
        }}")
        appendLine("Fabricante: ${manufacturer.ifBlank { "indisponível" }}")
        appendLine("Modelo: ${model.ifBlank { "indisponível" }}")
        appendLine("Android API: ${androidApi ?: "indisponível"}")
        appendLine("Resolução principal: ${if (widthPx != null && heightPx != null) "${widthPx}x${heightPx}" else "indisponível"}")
        appendLine("Densidade principal: ${densityDpi?.let { "$it dpi" } ?: "indisponível"}")
        appendLine("Endpoint pertence a este aparelho: ${if (isLocalEndpoint) "sim" else "não — não será usado como fallback da tela local"}")
        appendLine("Capturado em: $capturedAtEpochMs (epoch ms)")
        append("Escopo: consultas fixas somente leitura. Sem terminal arbitrário, sem gravação em /dev/input e sem criação de dispositivo virtual.")
    }
}

/** Snapshot em memória. É consumido como fallback da tela principal, nunca sobrescreve métricas nativas válidas. */
object WirelessAdbSnapshotStore {
    @Volatile
    var latest: WirelessAdbSnapshot? = null
        private set

    fun update(snapshot: WirelessAdbSnapshot) {
        latest = snapshot
    }

    fun clear() {
        latest = null
    }
}
