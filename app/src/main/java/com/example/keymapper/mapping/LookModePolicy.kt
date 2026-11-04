package com.example.keymapper.mapping

import com.example.keymapper.data.Rot

/** Seleciona o backend de visão sem inferir capacidade de entrada pelo nome do pacote. */
internal object LookModePolicy {
    fun resolve(globalMode: Int, buttonMode: Int, forceNativeRelative: Boolean): Int {
        val selected = if (buttonMode >= 0) buttonMode else globalMode
        // OFF sempre tem precedência: uma opção de backend não pode reativar a câmera desabilitada.
        if (selected == Rot.OFF) return Rot.OFF
        if (forceNativeRelative || selected == Rot.INFINITE) return Rot.INFINITE
        if (selected == Rot.NONSTOP) return Rot.NONSTOP
        // AUTO é deliberadamente conservador: troca UP/DOWN somente quando o contato chega à margem.
        // NONSTOP é uma estratégia distinta: sobrepõe os contatos durante a troca e depende do jogo.
        if (selected == Rot.AUTO) return Rot.CENTER
        return if (selected == Rot.CENTER) Rot.CENTER else Rot.OFF
    }
}
