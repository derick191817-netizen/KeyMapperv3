package com.example.keymapper.injection

import kotlinx.coroutines.flow.Flow

/** Contract between the wireless-ADB UI and any endpoint-discovery implementation. */
interface WirelessAdbDiscovery : AutoCloseable {
    val updates: Flow<WirelessAdbDiscoverySnapshot>
    fun start()
    override fun close()
}
