package com.example.keymapper.injection

/** Application-owned endpoint model. No Android or provider-library types. */
data class DiscoveredAdbEndpoint(
    val name: String,
    val host: String,
    val port: Int
)

data class WirelessAdbDiscoverySnapshot(
    val pairingEndpoints: List<DiscoveredAdbEndpoint> = emptyList(),
    val connectionEndpoints: List<DiscoveredAdbEndpoint> = emptyList()
)
