package com.example.keymapper.injection

import android.content.Context
import android.os.Build
import com.flyfishxu.kadb.Kadb
import com.flyfishxu.kadb.cert.KadbCert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Cliente ADB sem fio independente do Shizuku.
 * Esta primeira integração só oferece pareamento, conexão TLS e consultas de diagnóstico
 * somente leitura. Não expõe um terminal nem aceita strings de comando vindas da interface.
 */
object WirelessAdbClient {
    private val operationMutex = Mutex()

    suspend fun pair(
        context: Context,
        hostInput: String,
        pairingPortInput: String,
        pairingCodeInput: String
    ): String = operationMutex.withLock {
        check(AdbEndpointPolicy.supportsWirelessPairing(Build.VERSION.SDK_INT)) {
            "O pareamento ADB moderno exige Android 11 (API 30) ou superior."
        }
        val host = AdbEndpointPolicy.normalizeHost(hostInput)
            ?: throw IllegalArgumentException("Informe um endereço IPv4 ou IPv6 válido do aparelho de destino.")
        val port = AdbEndpointPolicy.parsePort(pairingPortInput)
            ?: throw IllegalArgumentException("A porta de pareamento deve estar entre 1 e 65535.")
        val code = AdbEndpointPolicy.normalizePairingCode(pairingCodeInput)
            ?: throw IllegalArgumentException("O código de pareamento deve conter exatamente 6 números.")

        // A new pairing can point to a different device; invalidate any old local-display snapshot.
        WirelessAdbSnapshotStore.clear()
        withContext(Dispatchers.IO) {
            configureHostIdentity(context)
            Kadb.pair(host, port, code, "KeyMapper-${Build.MODEL.take(32)}")
            "Pareamento ADB concluído. Agora selecione o endpoint TLS de conexão na Depuração sem fio ou na descoberta mDNS."
        }
    }

    suspend fun inspect(
        context: Context,
        hostInput: String,
        connectionPortInput: String
    ): String = operationMutex.withLock {
        val host = AdbEndpointPolicy.normalizeHost(hostInput)
            ?: throw IllegalArgumentException("Informe um endereço IPv4 ou IPv6 válido do aparelho de destino.")
        val port = AdbEndpointPolicy.parsePort(connectionPortInput)
            ?: throw IllegalArgumentException("A porta de conexão deve estar entre 1 e 65535.")

        // The previous snapshot may belong to another IP or an expired ADB session.
        WirelessAdbSnapshotStore.clear()
        try {
            withContext(Dispatchers.IO) {
                configureHostIdentity(context)
                Kadb.create(host, port, connectTimeout = 7_000, socketTimeout = 7_000).use { client ->
                    val values = AdbEndpointPolicy.diagnosticCommands.map { command ->
                        val response = client.shell(command)
                        if (response.exitCode != 0) {
                            throw IOException("Consulta '$command' falhou (código ${response.exitCode}): ${response.allOutput.trim()}")
                        }
                        command to response.output.trim()
                    }.toMap()

                    val uid = Regex("uid=(\\d+)").find(values["id"].orEmpty())?.groupValues?.get(1)?.toIntOrNull()
                    val size = AdbEndpointPolicy.parseWmSize(values["wm size"].orEmpty())
                    val snapshot = WirelessAdbSnapshot(
                        host = host,
                        connectionPort = port,
                        uid = uid,
                        manufacturer = values["getprop ro.product.manufacturer"].orEmpty(),
                        model = values["getprop ro.product.model"].orEmpty(),
                        androidApi = values["getprop ro.build.version.sdk"].orEmpty().toIntOrNull(),
                        widthPx = size?.first,
                        heightPx = size?.second,
                        densityDpi = AdbEndpointPolicy.parseWmDensity(values["wm density"].orEmpty()),
                        isLocalEndpoint = AdbEndpointPolicy.isLocalInterfaceAddress(host),
                        capturedAtEpochMs = System.currentTimeMillis()
                    )
                    WirelessAdbSnapshotStore.update(snapshot)
                    snapshot.report()
                }
            }
        } catch (cancelled: CancellationException) {
            WirelessAdbSnapshotStore.clear()
            throw cancelled
        } catch (error: Exception) {
            WirelessAdbSnapshotStore.clear()
            throw error
        }
    }

    suspend fun clearSavedIdentity(context: Context) = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val store = AndroidKadbPrivateKeyStore(context.noBackupFilesDir)
            KadbCert.configure(store)
            KadbCert.clear()
            WirelessAdbSnapshotStore.clear()
        }
    }

    private fun configureHostIdentity(context: Context) {
        KadbCert.configure(AndroidKadbPrivateKeyStore(context.applicationContext.noBackupFilesDir))
        KadbCert.ensureReady()
    }
}
