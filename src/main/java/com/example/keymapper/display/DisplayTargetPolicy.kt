package com.example.keymapper.display

/** Seleciona o display sem redirecionar silenciosamente uma sessão externa para a tela interna. */
object DisplayTargetPolicy {
    /** Retorna null quando o display externo foi solicitado, mas ainda não está disponível. */
    fun resolve(useExternal: Boolean, defaultDisplayId: Int, externalDisplayId: Int?): Int? {
        if (defaultDisplayId < 0) return null
        if (!useExternal) return defaultDisplayId
        return externalDisplayId?.takeIf { it >= 0 && it != defaultDisplayId }
    }
}
