package com.example.keymapper.injection

import android.content.Context
import com.flyfishxu.kadb.mdns.KadbMdnsAndroid
import com.flyfishxu.kadb.mdns.MdnsConfig
import com.flyfishxu.kadb.mdns.MdnsEndpoint
import com.flyfishxu.kadb.mdns.MdnsServiceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Android adapter for the Kadb mDNS provider. The third-party API is contained here;
 * Compose and application logic consume only WirelessAdbDiscovery.
 */
class KadbWirelessAdbDiscovery(context: Context) : WirelessAdbDiscovery {
    private val delegate = KadbMdnsAndroid(
        context.applicationContext,
        MdnsConfig(
            serviceTypes = setOf(
                MdnsServiceType.TLS_CONNECT,
                MdnsServiceType.TLS_PAIRING
            )
        )
    )

    override val updates: Flow<WirelessAdbDiscoverySnapshot> = delegate.state.map { state ->
        WirelessAdbDiscoverySnapshot(
            pairingEndpoints = state.pairDevices.map { it.toApplicationEndpoint() },
            connectionEndpoints = state.connectDevices
                // Never expose legacy, non-TLS ADB discovery as a selectable connection.
                .filter { it.serviceType == MdnsServiceType.TLS_CONNECT }
                .map { it.toApplicationEndpoint() }
        )
    }

    override fun start() = delegate.start()

    override fun close() = delegate.close()

    private fun MdnsEndpoint.toApplicationEndpoint() = DiscoveredAdbEndpoint(
        name = name,
        host = host,
        port = port
    )
}

object WirelessAdbDiscoveryFactory {
    fun create(context: Context): WirelessAdbDiscovery = KadbWirelessAdbDiscovery(context)
}
