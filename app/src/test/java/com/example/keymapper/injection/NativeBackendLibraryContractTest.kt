package com.example.keymapper.injection

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeBackendLibraryContractTest {
    @Test fun loadNameAndPackagedFilenameAreConsistent() {
        assertEquals("keymapper_evdev", NativeBackendLibrary.LOAD_NAME)
        assertEquals("lib${NativeBackendLibrary.LOAD_NAME}.so", NativeBackendLibrary.FILE_NAME)
    }
}
