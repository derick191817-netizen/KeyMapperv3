package com.example.keymapper.injection

/** Nome único do shared library produzido pelo CMake e carregado no processo Shizuku. */
internal object NativeBackendLibrary {
    const val LOAD_NAME = "keymapper_evdev"
    const val FILE_NAME = "libkeymapper_evdev.so"
}
