@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.keymapper.ui

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.keymapper.data.AppSettings
import com.example.keymapper.presentation.SettingsViewModel
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.injection.Activation
import com.example.keymapper.injection.ActivationState
import com.example.keymapper.service.MappingService
import com.example.keymapper.util.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Green = Color(0xFF4CAF50)
private val Red = Color(0xFFF44336)

/**
 * Painel inicial. O serviço pode ativar o mapeamento para qualquer aplicativo selecionado e compatível.
 * [onOpenSettings] recebe o índice da aba de configurações a abrir.
 */
@Composable
fun HomeScreen(viewModel: SettingsViewModel, tick: Int, onOpenSettings: (Int) -> Unit, onOpenEditor: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val cfg by viewModel.settings.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val key = tick + refresh
    var state by remember { mutableStateOf(ActivationState.SHIZUKU_STOPPED) }
    LaunchedEffect(key) { state = withContext(Dispatchers.IO) { Activation.state(ctx) } }
    val overlayOk = remember(key) { Settings.canDrawOverlays(ctx) }
    val running = remember(key) { MappingService.running }
    val detecting = remember(key) { MappingService.detectionSupported }
    val lastApp = remember(key) { MappingService.detectedPackage }
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val connected = state == ActivationState.READY

    fun refreshLater() {
        scope.launch {
            delay(700)
            refresh++
        }
    }

    fun openOverlaySettings() {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        } catch (_: Exception) {
            Toast.makeText(ctx, "Abra Configurações do Android e permita 'Exibir sobre outros apps'.", Toast.LENGTH_LONG).show()
        }
    }

    fun ensureService(): Boolean {
        if (!overlayOk) {
            Toast.makeText(ctx, "Permita a sobreposição de tela primeiro", Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return false
        }
        if (!connected) {
            Toast.makeText(
                ctx, "Shizuku desconectado: sem ele os toques não são injetados. Use \"Ativar via Wi-Fi\".",
                Toast.LENGTH_LONG
            ).show()
        }
        if (!running) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, MappingService::class.java))
            } catch (e: Exception) {
                Toast.makeText(ctx, "Não foi possível iniciar o serviço: ${e.localizedMessage ?: "erro desconhecido"}", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    fun startGame(pkg: String) {
        Haptics.tick(view)
        if (!ensureService()) return
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) {
            Toast.makeText(ctx, "Esse app não está instalado ou não pode ser iniciado.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val id = DisplayHelper(ctx).target().displayId
            val opts = ActivityOptions.makeBasic().setLaunchDisplayId(id)
            ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), opts.toBundle())
            refreshLater()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Falha ao abrir o jogo: ${e.localizedMessage ?: "verifique a tela selecionada"}", Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("KEYMAPPER", style = MaterialTheme.typography.titleMedium)
                        Text("CENTRAL DE CONTROLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = {
                    androidx.compose.material3.IconButton(onClick = {
                        Haptics.tick(view)
                        refresh++
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar status")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---- hero / visão geral ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.88f),
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    ).padding(18.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    if (connected && overlayOk) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    if (connected && overlayOk) "PRONTO PARA CONFIGURAR" else "CONFIGURAÇÃO NECESSÁRIA",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                        Text("Pronto para jogar?", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Gerencie o mapeamento, a mira e os recursos de entrada em um só lugar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { Haptics.tick(view); onOpenSettings(0) },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Configurar")
                            }
                            OutlinedButton(
                                onClick = { Haptics.tick(view); showSheet = true },
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                                Text("  Ativação")
                            }
                        }
                        OutlinedButton(
                            onClick = { Haptics.tick(view); onOpenEditor() },
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Abrir editor de controles")
                        }
                    }
                }
            }

            // ---- status ----
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Estado do sistema", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                if (running) "ATIVO" else "INATIVO",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                    StatusRow("Shizuku", connected, if (connected) "Conectado" else stateText(state))
                    StatusRow("Sobreposição de tela", overlayOk, if (overlayOk) "Permitida" else "Não permitida")
                    StatusRow("Serviço", running, if (running) "De prontidão" else "Desligado")
                    if (cfg.onlyInGame) {
                        StatusRow(
                            "Detecção do app aberto", detecting,
                            if (detecting) (lastApp ?: "ok") else "Aguardando / indisponível"
                        )
                    }
                }
            }

            // ---- jogos: um botão para cada ----
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Jogos e aplicativos selecionados", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (cfg.onlyInGame)
                            "Automático: o mapeamento liga quando um app selecionado abre e desliga ao sair dele."
                        else
                            "Manual: o overlay fica ligado enquanto o serviço estiver de prontidão.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Switch(checked = cfg.onlyInGame, onCheckedChange = { v ->
                            Haptics.tick(view)
                            viewModel.update { it.copy(onlyInGame = v) }
                        })
                        Text("Ligar e desligar automaticamente", modifier = Modifier.weight(1f))
                    }
                    if (cfg.games.isEmpty()) {
                        Text("Nenhum aplicativo selecionado.", style = MaterialTheme.typography.bodyMedium)
                    }
                    cfg.games.forEach { pkg ->
                        val label = appLabel(ctx, pkg)
                        Button(
                            onClick = { startGame(pkg) },
                            enabled = label != null,
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                            Text(
                                "  Abrir " + (label ?: "$pkg (não instalado)"),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = {
                            Haptics.tick(view)
                            onOpenSettings(4) // aba Jogos
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("Escolher jogos e apps") }
                    if (running) {
                        Button(
                            onClick = {
                                Haptics.tick(view)
                                ctx.startService(
                                    Intent(ctx, MappingService::class.java).setAction(MappingService.ACTION_STOP)
                                )
                                refreshLater()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Red),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) { Text("Parar serviço") }
                    }
                }
            }

            // ---- permissões ----
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Permissões", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "O app precisa de Sobreposição (HUD sobre o jogo) e da permissão do Shizuku (injetar os toques). " +
                            "Não usa Acessibilidade.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    StatusRow("Sobreposição", overlayOk, if (overlayOk) "Concedida" else "Pendente")
                    StatusRow(
                        "Permissão do Shizuku", connected,
                        if (connected) "Concedida" else if (state == ActivationState.NEEDS_PERMISSION) "Pendente" else "Shizuku desconectado"
                    )
                    OutlinedButton(
                        onClick = {
                            Haptics.tick(view)
                            when {
                                !overlayOk -> openOverlaySettings()
                                state == ActivationState.NEEDS_PERMISSION -> {
                                    Activation.requestPermission()
                                    refreshLater()
                                }
                                connected -> Toast.makeText(ctx, "Tudo concedido", Toast.LENGTH_SHORT).show()
                                else -> Toast.makeText(ctx, "Ative o Shizuku primeiro (botão abaixo)", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Lock, contentDescription = null)
                        Text("  Conceder Permissões")
                    }
                }
            }

            // ---- Wi-Fi ----
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ativação sem computador", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "No Android 11 ou superior dá para ativar o Shizuku pela depuração sem fio (Wi-Fi), sem PC. " +
                            "Precisa refazer depois de reiniciar o celular.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = {
                            Haptics.tick(view)
                            showSheet = true
                        },
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Filled.Build, contentDescription = null)
                        Text("  Ativar via Wi-Fi (Shizuku)")
                    }
                }
            }

            // ---- configurações ----
            OutlinedButton(
                onClick = {
                    Haptics.tick(view)
                    onOpenSettings(0)
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Settings, contentDescription = null)
                Text("  Todas as configurações", style = MaterialTheme.typography.titleMedium)
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            Column(
                Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Ativar via Wi-Fi (depuração sem fio)", style = MaterialTheme.typography.titleLarge)
                Surface(
                    color = (if (connected) Green else Red).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        if (connected) "Shizuku: Conectado" else "Shizuku: Desconectado (${stateText(state)})",
                        color = if (connected) Green else Red,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                Step(1, "Instale o Shizuku", "Pela Play Store ou GitHub, e abra o app uma vez.")
                Step(
                    2, "Libere as Opções do desenvolvedor",
                    "Configurações › Sobre o telefone › Informações do software › toque 7 vezes em \"Número da versão\"."
                )
                Step(
                    3, "Ligue a Depuração sem fio",
                    "Conecte ao Wi-Fi. Em Opções do desenvolvedor, ative \"Depuração sem fio\" e toque em " +
                        "\"Parear dispositivo com código de pareamento\". Aparece um código de 6 dígitos."
                )
                Step(
                    4, "Digite o código no Shizuku",
                    "Abra o Shizuku, escolha o pareamento por depuração sem fio e, quando a notificação aparecer, " +
                        "toque nela e digite o código de 6 dígitos. Depois toque em Iniciar."
                )
                Step(5, "Volte ao KeyMapper", "O status fica verde (Conectado). Toque em Atualizar status.")

                Button(
                    onClick = { openDeveloperSettings(ctx) },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Abrir Opções do desenvolvedor") }
                OutlinedButton(
                    onClick = {
                        val i = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (i != null) ctx.startActivity(i)
                        else Toast.makeText(ctx, "Shizuku não instalado", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Abrir Shizuku") }
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        refresh++
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("  Atualizar status")
                }
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        Thread {
                            val ok = Activation.startShizukuViaRoot()
                            android.os.Handler(ctx.mainLooper).post {
                                Toast.makeText(
                                    ctx, if (ok) "Shizuku iniciado via root" else "Root indisponível", Toast.LENGTH_SHORT
                                ).show()
                                refresh++
                            }
                        }.start()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Ativar via root (se o aparelho tiver root)") }
            }
        }
    }
}

/** Nome do app instalado, ou null se não estiver instalado (QUERY_ALL_PACKAGES permite ver todos). */
private fun appLabel(ctx: Context, pkg: String): String? = try {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (e: Exception) {
    null
}

private fun stateText(s: ActivationState) = when (s) {
    ActivationState.READY -> "Conectado"
    ActivationState.NEEDS_PERMISSION -> "Sem permissão"
    ActivationState.SHIZUKU_STOPPED -> "Parado"
    ActivationState.SHIZUKU_MISSING -> "Não instalado"
}

@Composable
private fun StatusRow(label: String, ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) Green else Red,
            modifier = Modifier.size(22.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
        Text(text, color = if (ok) Green else Red, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun Step(n: Int, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("$n", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun openDeveloperSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    } catch (e: ActivityNotFoundException) {
        // Opções do desenvolvedor ainda desativadas: abre "Sobre o telefone" para tocar 7x no número da versão
        try {
            ctx.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        } catch (e2: Exception) {
            Toast.makeText(ctx, "Abra Configurações › Sobre o telefone manualmente", Toast.LENGTH_LONG).show()
        }
    }
}
