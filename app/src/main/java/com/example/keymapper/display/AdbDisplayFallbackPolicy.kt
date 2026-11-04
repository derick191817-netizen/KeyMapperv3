package com.example.keymapper.display

/** Política pura para consumir wm size/density sem sobrescrever a fonte nativa do Android. */
object AdbDisplayFallbackPolicy {
    data class Values(val width: Int, val height: Int, val densityDpi: Int)

    fun resolve(
        native: Values,
        adb: Values?,
        isPrimaryDisplay: Boolean,
        isLocalEndpoint: Boolean,
        snapshotAgeMs: Long
    ): Values {
        if (!isPrimaryDisplay || !isLocalEndpoint || adb == null || snapshotAgeMs !in 0..MAX_SNAPSHOT_AGE_MS) return native
        return Values(
            width = native.width.takeIf(::isValidDimension) ?: adb.width.takeIf(::isValidDimension) ?: native.width,
            height = native.height.takeIf(::isValidDimension) ?: adb.height.takeIf(::isValidDimension) ?: native.height,
            densityDpi = native.densityDpi.takeIf(::isValidDensity)
                ?: adb.densityDpi.takeIf(::isValidDensity)
                ?: native.densityDpi
        )
    }

    private fun isValidDimension(value: Int): Boolean = value in 240..16384
    private fun isValidDensity(value: Int): Boolean = value in 80..1000

    private const val MAX_SNAPSHOT_AGE_MS = 15 * 60 * 1000L
}
