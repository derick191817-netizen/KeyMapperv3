package com.example.keymapper.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.keymapper.injection.WirelessAdbClient
import com.flyfishxu.kadb.mdns.KadbMdnsAndroid
import com.flyfishxu.kadb.mdns.MdnsConfig
import com.flyfishxu.kadb.mdns.MdnsEndpoint
import com.flyfishxu.kadb.mdns.MdnsServiceType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
fun WirelessAdbDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember(context) { context.getSharedPreferences("wireless_adb_endpoint", android.content.Context.MODE_PRIVATE) }
    var host by remember(prefs) { mutableStateOf(prefs.getString("host", "") ?: "") }
    var pairingPort by remember(prefs) { mutableStateOf(prefs.getString("pairing_port", "") ?: "") }
    var pairingCode by remember { mutableStateOf("") }
    var connectionPort by remember(prefs) { mutableStateOf(prefs.getString("connection_port", "") ?: "") }
    var status by remember { mutableStateOf("O ADB direto é independente do Shizuku. O acesso desta tela é limitado a consultas de diagnóstico somente leitura.") }
    var report by remember { mutableStateOf("") }
    var pairing by remember { mutableStateOf(false) }
    var connecting by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    var clearingIdentity by remember { mutableStateOf(false) }
    var pairEndpoints by remember { mutableStateOf<List<MdnsEndpoint>>(emptyList()) }
    var connectEndpoints by remember { mutableStateOf<List<MdnsEndpoint>>(emptyList()) }
    val operationBusy = pairing || connecting || discovering || clearingIdentity

    LaunchedEffect(discovering) {
        if (!discovering) return@LaunchedEffect
        val discovery = KadbMdnsAndroid(
            context.applicationContext,
            MdnsConfig(serviceTypes = setOf(MdnsServiceType.TLS_CONNECT, MdnsServiceType.TLS_PAIRING))
        )
        var collector: Job? = null
        try {
            collector = launch {
                discovery.state.collect { state ->
                    pairEndpoints = state.pairDevices
                    // Não expõe ADB legado sem TLS como endpoint selecionável.
                    connectEndpoints = state.connectDevices.filter { it.serviceType == MdnsServiceType.TLS_CONNECT }
                }
            }
            discovery.start()
            status = "Procurando endpoints ADB TLS na rede local por até 12 segundos..."
            delay(12_000)
            status = "Busca concluída. Escolha o endpoint encontrado ou informe os dados exibidos na tela Depuração sem fio."
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            status = "Falha na descoberta mDNS: ${error.message ?: error.javaClass.simpleName}"
        } finally {
            collector?.cancel()
            runCatching { discovery.close() }
            discovering = false
        }
    }

    fun persistEndpoint() {
        prefs.edit()
            .putString("host", host.trim())
            .putString("pairing_port", pairingPort.trim())
            .putString("connection_port", connectionPort.trim())
            .apply()
    }

    fun selectEndpoint(endpoint: MdnsEndpoint, isPairing: Boolean) {
        host = endpoint.host
        if (isPairing) pairingPort = endpoint.port.toString() else connectionPort = endpoint.port.toString()
        persistEndpoint()
    }

    AlertDialog(
        onDismissRequest = { if (!operationBusy) onDismiss() },
        title = { Text("ADB sem fio direto") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 580.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Pareamento e conexão TLS próprios do KeyMapper. Não exige abrir o Shizuku. O pareamento autoriza esta instalação como cliente ADB no dispositivo de destino.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = {
                        pairEndpoints = emptyList()
                        connectEndpoints = emptyList()
                        discovering = true
                    },
                    enabled = !operationBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (discovering) CircularProgressIndicator(strokeWidth = 2.dp)
                    Text(if (discovering) "  Procurando dispositivos..." else "Procurar endpoints ADB TLS")
                }

                if (pairEndpoints.isNotEmpty()) {
                    Text("Endpoints para pareamento", style = MaterialTheme.typography.labelLarge)
                    pairEndpoints.take(8).forEach { endpoint ->
                        EndpointRow(endpoint, "Parear", enabled = !operationBusy) { selectEndpoint(endpoint, true) }
                    }
                }
                if (connectEndpoints.isNotEmpty()) {
                    Text("Endpoints TLS de conexão", style = MaterialTheme.typography.labelLarge)
                    connectEndpoints.take(8).forEach { endpoint ->
                        EndpointRow(endpoint, "Conectar", enabled = !operationBusy) { selectEndpoint(endpoint, false) }
                    }
                }

                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it; prefs.edit().putString("host", it.trim()).apply() },
                    label = { Text("IP do aparelho") },
                    placeholder = { Text("192.168.1.20") },
                    singleLine = true,
                    enabled = !operationBusy,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = pairingPort,
                        onValueChange = {
                            pairingPort = it.filter(Char::isDigit).take(5)
                            prefs.edit().putString("pairing_port", pairingPort).apply()
                        },
                        label = { Text("Porta de pareamento") },
                        singleLine = true,
                        enabled = !operationBusy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it.filter(Char::isDigit).take(6) },
                        label = { Text("Código de 6 dígitos") },
                        singleLine = true,
                        enabled = !operationBusy,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                OutlinedButton(
                    onClick = {
                        pairing = true
                        status = "Executando pareamento criptográfico TLS..."
                        scope.launch {
                            try {
                                status = WirelessAdbClient.pair(context, host, pairingPort, pairingCode)
                                pairingCode = ""
                                // O pareamento e a porta TLS de conexão são serviços diferentes.
                                discovering = true
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                status = "Pareamento não concluído: ${error.message ?: error.javaClass.simpleName}"
                            } finally {
                                pairingCode = ""
                                pairing = false
                            }
                        }
                    },
                    enabled = !operationBusy && Build.VERSION.SDK_INT >= 30,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (pairing) CircularProgressIndicator(strokeWidth = 2.dp)
                    Text(if (pairing) "  Pareando..." else "Parear KeyMapper como cliente ADB")
                }
                if (Build.VERSION.SDK_INT < 30) {
                    Text("Pareamento direto disponível a partir do Android 11 (API 30), que oferece TLS 1.3 nativo.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                OutlinedTextField(
                    value = connectionPort,
                    onValueChange = {
                        connectionPort = it.filter(Char::isDigit).take(5)
                        prefs.edit().putString("connection_port", connectionPort).apply()
                    },
                    label = { Text("Porta TLS de conexão") },
                    placeholder = { Text("Porta diferente da de pareamento") },
                    singleLine = true,
                    enabled = !operationBusy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        connecting = true
                        report = ""
                        status = "Conectando ao endpoint TLS e consultando os dados do dispositivo..."
                        scope.launch {
                            try {
                                report = WirelessAdbClient.inspect(context, host, connectionPort)
                                status = "Conexão ADB independente confirmada."
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                status = "Conexão não confirmada: ${error.message ?: error.javaClass.simpleName}"
                            } finally {
                                connecting = false
                            }
                        }
                    },
                    enabled = !operationBusy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (connecting) CircularProgressIndicator(strokeWidth = 2.dp)
                    Text(if (connecting) "  Testando conexão..." else "Testar conexão ADB")
                }

                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                    Text(status, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
                if (report.isNotBlank()) {
                    Surface(color = MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.medium) {
                        Text(report, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(
                    onClick = {
                        clearingIdentity = true
                        scope.launch {
                            try {
                                WirelessAdbClient.clearSavedIdentity(context)
                                status = "Identidade ADB local removida. Será necessário parear novamente na próxima conexão."
                                report = ""
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                status = "Não foi possível remover a identidade: ${error.message ?: error.javaClass.simpleName}"
                            } finally {
                                clearingIdentity = false
                            }
                        }
                    },
                    enabled = !operationBusy
                ) {
                    if (clearingIdentity) CircularProgressIndicator(strokeWidth = 2.dp)
                    Text(if (clearingIdentity) "  Removendo identidade..." else "Remover identidade ADB salva")
                }
                Text(
                    "Importante: parear e conectar prova que o ADB direto funciona. Esta primeira etapa usa o ADB para diagnóstico de sistema e fallback de métricas ausentes da tela principal; ela ainda não substitui o backend de entrada do motor nem confirma captura evdev/uinput.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !operationBusy) { Text("Fechar") }
        }
    )
}

@Composable
private fun EndpointRow(endpoint: MdnsEndpoint, action: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(endpoint.name, style = MaterialTheme.typography.bodyMedium)
                Text("${endpoint.host}:${endpoint.port}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(action, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}
