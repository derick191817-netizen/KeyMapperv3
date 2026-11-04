package com.example.keymapper.injection

/** Pure endpoint validation and de-duplication, independent of Android and the provider library. */
object WirelessAdbDiscoveryPolicy {
    fun sanitize(
        endpoints: List<DiscoveredAdbEndpoint>,
        limit: Int = 8
    ): List<DiscoveredAdbEndpoint> {
        if (limit <= 0) return emptyList()
        val seen = HashSet<String>()
        return endpoints.mapNotNull { endpoint ->
            val host = endpoint.host.trim()
            val port = endpoint.port
            if (host.isEmpty() || host.any { it.isWhitespace() } || port !in 1..65535) {
                return@mapNotNull null
            }
            val key = "${host.lowercase()}:$port"
            if (!seen.add(key)) return@mapNotNull null
            endpoint.copy(name = endpoint.name.trim().ifEmpty { host }, host = host)
        }.take(limit)
    }
}
