package com.example.keymapper.injection

import android.view.InputEvent

interface Injector {
    fun inject(event: InputEvent): Boolean

    /** true se dá para injetar agora (usado para retomar sozinho depois de uma queda do Shizuku). */
    fun isReady(): Boolean = true
}
