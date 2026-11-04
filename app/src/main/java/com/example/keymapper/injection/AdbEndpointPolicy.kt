package com.example.keymapper.injection

import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface

/** Validação dos dados de endpoint ADB e consultas fixas. Não aceita comandos fornecidos pelo usuário. */
object AdbEndpointPolicy {
    fun normalizeHost(raw: String): String? {
        val candidate = raw.trim().removePrefix("[").removeSuffix("]")
        if (candidate.isEmpty() || candidate.length > 96 || candidate.any(Char::isWhitespace)) return null

        val ipv4Parts = candidate.split('.')
        if (ipv4Parts.size == 4 && ipv4Parts.all { part ->
                part.isNotEmpty() && part.length <= 3 && part.all(Char::isDigit) &&
                    part.toIntOrNull()?.let { it in 0..255 } == true
            }) {
            return ipv4Parts.joinToString(".") { it.toInt().toString() }
        }

        // O caractere ':' garante que InetAddress trate o valor como literal IPv6, sem DNS.
        if (!candidate.contains(':')) return null
        val addressPart = candidate.substringBefore('%')
        val zonePart = candidate.substringAfter('%', "")
        if (!addressPart.matches(Regex("[0-9a-fA-F:.]+"))) return null
        if ('%' in candidate && !zonePart.matches(Regex("[A-Za-z0-9_.-]{1,32}"))) return null

        val isIpv6 = runCatching { InetAddress.getByName(addressPart) is Inet6Address }.getOrDefault(false)
        return candidate.takeIf { isIpv6 }
    }

    fun parsePort(raw: String): Int? = raw.trim().toIntOrNull()?.takeIf { it in 1..65535 }

    fun normalizePairingCode(raw: String): String? = raw.trim().takeIf { it.matches(Regex("\\d{6}")) }

    /** ADB Wireless Debugging pairing is available from Android 11 (API 30). */
    fun supportsWirelessPairing(sdkInt: Int): Boolean = sdkInt >= 30

    /**
     * ADB diagnostics may target another device on the LAN. Only snapshots reached through
     * an address assigned to this phone may be used as local display fallbacks.
     */
    fun isLocalInterfaceAddress(rawHost: String): Boolean = runCatching {
        val target = InetAddress.getByName(rawHost.trim().removePrefix("[").removeSuffix("]"))
        if (target.isLoopbackAddress) return@runCatching true
        val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching false
        val it = interfaces.asSequence().iterator()
        while (it.hasNext()) {
            val networkInterface = it.next()
            val addresses = networkInterface.inetAddresses
            while (addresses.hasMoreElements()) {
                if (addresses.nextElement().address.contentEquals(target.address)) return@runCatching true
            }
        }
        false
    }.getOrDefault(false)

    /** Lista fixa de consultas somente leitura usadas pelo diagnóstico ADB direto. */
    val diagnosticCommands: List<String> = listOf(
        "id",
        "getprop ro.product.manufacturer",
        "getprop ro.product.model",
        "getprop ro.build.version.sdk",
        "wm size",
        "wm density"
    )

    /**
     * Prefere Override size, mas uma linha de override malformada/zerada não impede
     * o fallback para Physical size.
     */
    fun parseWmSize(output: String): Pair<Int, Int>? {
        val lines = output.lineSequence().toList()
        fun parseLine(line: String?): Pair<Int, Int>? {
            val match = line?.let { Regex("(\\d+)\\s*x\\s*(\\d+)").find(it) } ?: return null
            val width = match.groupValues[1].toIntOrNull() ?: return null
            val height = match.groupValues[2].toIntOrNull() ?: return null
            return (width to height).takeIf { width in 240..16384 && height in 240..16384 }
        }
        return parseLine(lines.firstOrNull { it.contains("Override size:", ignoreCase = true) })
            ?: parseLine(lines.firstOrNull { it.contains("Physical size:", ignoreCase = true) })
            ?: parseLine(lines.firstOrNull { it.contains("size:", ignoreCase = true) })
    }

    /** Prefere Override density, mas usa Physical density se o override for inválido. */
    fun parseWmDensity(output: String): Int? {
        val lines = output.lineSequence().toList()
        fun parseLine(line: String?): Int? = line
            ?.let { Regex("(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            ?.takeIf { it in 80..1000 }
        return parseLine(lines.firstOrNull { it.contains("Override density:", ignoreCase = true) })
            ?: parseLine(lines.firstOrNull { it.contains("Physical density:", ignoreCase = true) })
            ?: parseLine(lines.firstOrNull { it.contains("density:", ignoreCase = true) })
    }
}
