@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.keymapper.ui

import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.keymapper.data.AppSettings
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.injection.Activation
import com.example.keymapper.injection.ActivationState
import com.example.keymapper.presentation.SettingsViewModel
import com.example.keymapper.service.MappingService
import com.example.keymapper.util.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val StatusGreen = Color(0xFF55D6A4)
private val StatusAmber = Color(0xFFF4BD66)

/** Painel de controle. Os indicadores refletem o serviço, a permissão de sobreposição e o estado real do Shizuku. */
@Composable
fun HomeScreen(viewModel: SettingsViewModel, tick: Int, onOpenSettings: (Int) -> Unit, onOpenEditor: () -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val cfg by viewModel.settings.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }
    val statusKey = tick + refresh
    var activationState by remember { mutableStateOf(ActivationState.SHIZUKU_STOPPED) }
    LaunchedEffect(statusKey) {
        activationState = withContext(Dispatchers.IO) { Activation.state(ctx) }
    }
    val overlayGranted = remember(statusKey) { Settings.canDrawOverlays(ctx) }
    val serviceRunning = remember(statusKey) { MappingService.running }
    val detectionSupported = remember(statusKey) { MappingService.detectionSupported }
    val lastDetectedPackage = remember(statusKey) { MappingService.detectedPackage }
    var showActivationSheet by remember { mutableStateOf(false) }
    val activationSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val shizukuConnected = activationState == ActivationState.READY

    fun refreshLater() {
        scope.launch {
            delay(650)
            refresh++
        }
    }

    fun openOverlaySettings() {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            )
        } catch (_: Exception) {
            Toast.makeText(ctx, "Abra as Configurações do Android e permita 'Exibir sobre outros apps'.", Toast.LENGTH_LONG).show()
        }
    }

    fun ensureService(): Boolean {
        if (!overlayGranted) {
            Toast.makeText(ctx, "Permita a sobreposição de tela primeiro.", Toast.LENGTH_LONG).show()
            openOverlaySettings()
            return false
        }
        if (!shizukuConnected) {
            Toast.makeText(
                ctx,
                "Shizuku desconectado: o serviço pode abrir, mas a injeção de toques não ficará disponível até a autorização.",
                Toast.LENGTH_LONG
            ).show()
        }
        if (!serviceRunning) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, MappingService::class.java))
            } catch (e: Exception) {
                Toast.makeText(ctx, "Não foi possível iniciar o serviço: ${e.localizedMessage ?: "erro desconhecido"}", Toast.LENGTH_LONG).show()
                return false
            }
        }
        return true
    }

    fun openTargetApp(pkg: String) {
        Haptics.tick(view)
        if (!ensureService()) return
        val launchIntent = ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (launchIntent == null) {
            Toast.makeText(ctx, "Esse aplicativo não está instalado ou não possui uma tela de inicialização.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val displayId = DisplayHelper(ctx).target().displayId
            val launchOptions = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            ctx.startActivity(launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), launchOptions.toBundle())
            refreshLater()
        } catch (e: Exception) {
            Toast.makeText(ctx, "Falha ao abrir o aplicativo: ${e.localizedMessage ?: "verifique a tela selecionada"}", Toast.LENGTH_LONG).show()
        }
    }

    fun startServiceFromPanel() {
        Haptics.tick(view)
        if (ensureService()) refreshLater()
    }

    fun stopServiceFromPanel() {
        Haptics.tick(view)
        runCatching {
            ctx.startService(Intent(ctx, MappingService::class.java).setAction(MappingService.ACTION_STOP))
        }.onFailure {
            Toast.makeText(ctx, "Não foi possível interromper o serviço.", Toast.LENGTH_SHORT).show()
        }
        refreshLater()
    }

    fun handlePermissions() {
        Haptics.tick(view)
        when {
            !overlayGranted -> openOverlaySettings()
            activationState == ActivationState.NEEDS_PERMISSION -> {
                Activation.requestPermission()
                refreshLater()
            }
            shizukuConnected -> Toast.makeText(ctx, "Permissões necessárias já concedidas.", Toast.LENGTH_SHORT).show()
            else -> {
                Toast.makeText(ctx, "Abra a ativação via Wi-Fi para iniciar o Shizuku.", Toast.LENGTH_LONG).show()
                showActivationSheet = true
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(11.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("K", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("KeyMapper", style = MaterialTheme.typography.titleLarge)
                            Text("CONSOLE DE CONTROLE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { Haptics.tick(view); refresh++ }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Atualizar status do serviço")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            val wideLayout = maxWidth >= 760.dp
            Column(
                Modifier.widthIn(max = 1180.dp).fillMaxWidth().align(Alignment.TopCenter)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (wideLayout) 20.dp else 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                DashboardHero(
                    serviceRunning = serviceRunning,
                    shizukuConnected = shizukuConnected,
                    overlayGranted = overlayGranted,
                    onOpenEditor = { Haptics.tick(view); onOpenEditor() },
                    onOpenSettings = { Haptics.tick(view); onOpenSettings(0) },
                    onActivation = { Haptics.tick(view); showActivationSheet = true }
                )

                if (wideLayout) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            AppLaunchPanel(
                                context = ctx,
                                settings = cfg,
                                onAutomaticChange = { enabled -> Haptics.tick(view); viewModel.update { it.copy(onlyInGame = enabled) } },
                                onLaunch = ::openTargetApp,
                                onChooseApps = { Haptics.tick(view); onOpenSettings(4) }
                            )
                            AccessPanel(
                                overlayGranted = overlayGranted,
                                activationState = activationState,
                                shizukuConnected = shizukuConnected,
                                onPermissions = ::handlePermissions,
                                onActivation = { Haptics.tick(view); showActivationSheet = true }
                            )
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            SystemStatusPanel(
                                serviceRunning = serviceRunning,
                                shizukuConnected = shizukuConnected,
                                overlayGranted = overlayGranted,
                                activationState = activationState,
                                automaticDetection = cfg.onlyInGame,
                                detectionSupported = detectionSupported,
                                lastDetectedPackage = lastDetectedPackage,
                                onStart = ::startServiceFromPanel,
                                onStop = ::stopServiceFromPanel
                            )
                            QuickSettingsPanel(onOpenSettings = { Haptics.tick(view); onOpenSettings(0) }, onOpenApps = { Haptics.tick(view); onOpenSettings(4) })
                        }
                    }
                } else {
                    SystemStatusPanel(
                        serviceRunning = serviceRunning,
                        shizukuConnected = shizukuConnected,
                        overlayGranted = overlayGranted,
                        activationState = activationState,
                        automaticDetection = cfg.onlyInGame,
                        detectionSupported = detectionSupported,
                        lastDetectedPackage = lastDetectedPackage,
                        onStart = ::startServiceFromPanel,
                        onStop = ::stopServiceFromPanel
                    )
                    AppLaunchPanel(
                        context = ctx,
                        settings = cfg,
                        onAutomaticChange = { enabled -> Haptics.tick(view); viewModel.update { it.copy(onlyInGame = enabled) } },
                        onLaunch = ::openTargetApp,
                        onChooseApps = { Haptics.tick(view); onOpenSettings(4) }
                    )
                    AccessPanel(
                        overlayGranted = overlayGranted,
                        activationState = activationState,
                        shizukuConnected = shizukuConnected,
                        onPermissions = ::handlePermissions,
                        onActivation = { Haptics.tick(view); showActivationSheet = true }
                    )
                    QuickSettingsPanel(onOpenSettings = { Haptics.tick(view); onOpenSettings(0) }, onOpenApps = { Haptics.tick(view); onOpenSettings(4) })
                }
            }
        }
    }

    if (showActivationSheet) {
        ModalBottomSheet(onDismissRequest = { showActivationSheet = false }, sheetState = activationSheetState) {
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Ativação via Wi-Fi", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "O KeyMapper usa o Shizuku para os recursos privilegiados. A ativação do Shizuku pode precisar ser repetida após reiniciar o celular.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StatusPill(
                    text = if (shizukuConnected) "Shizuku conectado" else "Shizuku ${activationStateLabel(activationState).lowercase()}",
                    ready = shizukuConnected
                )
                SetupStep(1, "Instale e abra o Shizuku", "Instale o Shizuku pela fonte oficial e abra o aplicativo ao menos uma vez.")
                SetupStep(2, "Ative as opções do desenvolvedor", "Em Configurações › Sobre o telefone › Informações do software, toque sete vezes em Número da versão.")
                SetupStep(3, "Ative a depuração sem fio", "Nas Opções do desenvolvedor, ative Depuração sem fio e escolha parear com código.")
                SetupStep(4, "Conclua o pareamento no Shizuku", "Use o código de pareamento solicitado pelo próprio Shizuku e toque em Iniciar.")
                SetupStep(5, "Volte ao KeyMapper", "Atualize o status. O estado conectado será exibido quando o serviço estiver disponível.")
                Button(onClick = { openDeveloperSettings(ctx) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Abrir opções do desenvolvedor")
                }
                OutlinedButton(
                    onClick = {
                        val intent = ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                        if (intent != null) ctx.startActivity(intent)
                        else Toast.makeText(ctx, "Shizuku não está instalado.", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Abrir Shizuku") }
                OutlinedButton(onClick = { refresh++; }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Text("  Atualizar status")
                }
                OutlinedButton(
                    onClick = {
                        Thread {
                            val success = Activation.startShizukuViaRoot()
                            android.os.Handler(ctx.mainLooper).post {
                                Toast.makeText(ctx, if (success) "Shizuku iniciado via root." else "Root indisponível.", Toast.LENGTH_SHORT).show()
                                refresh++
                            }
                        }.start()
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) { Text("Tentar ativação via root") }
            }
        }
    }
}

@Composable
private fun DashboardHero(
    serviceRunning: Boolean,
    shizukuConnected: Boolean,
    overlayGranted: Boolean,
    onOpenEditor: () -> Unit,
    onOpenSettings: () -> Unit,
    onActivation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(8.dp)) {}
                Text("ESPAÇO DE TRABALHO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                StatusPill(
                    text = when {
                        serviceRunning && shizukuConnected && overlayGranted -> "Tudo pronto"
                        serviceRunning -> "Serviço ativo"
                        else -> "Configuração"
                    },
                    ready = serviceRunning && shizukuConnected && overlayGranted
                )
            }
            Text("Mapeamento de entrada", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Organize teclas, mouse e controles por aplicativo. O editor usa uma área de referência; não é uma captura ao vivo do jogo.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenEditor, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Abrir editor", maxLines = 1)
                }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.weight(1f).height(48.dp)) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Configurar", maxLines = 1)
                }
            }
            TextButton(onClick = onActivation, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(17.dp))
                Text("  Ativação e permissões")
            }
        }
    }
}

@Composable
private fun SystemStatusPanel(
    serviceRunning: Boolean,
    shizukuConnected: Boolean,
    overlayGranted: Boolean,
    activationState: ActivationState,
    automaticDetection: Boolean,
    detectionSupported: Boolean,
    lastDetectedPackage: String?,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Estado do sistema", style = MaterialTheme.typography.titleMedium)
                    Text("Informações detectadas neste dispositivo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusPill(text = if (serviceRunning) "Ativo" else "Parado", ready = serviceRunning)
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
            StatusRow("Shizuku", shizukuConnected, if (shizukuConnected) "Conectado" else activationStateLabel(activationState))
            StatusRow("Sobreposição", overlayGranted, if (overlayGranted) "Permitida" else "Pendente")
            StatusRow("Serviço de mapeamento", serviceRunning, if (serviceRunning) "Em execução" else "Interrompido")
            if (automaticDetection) {
                StatusRow("Detecção de aplicativo", detectionSupported, if (detectionSupported) lastDetectedPackage ?: "Disponível" else "Aguardando serviço")
            }
            if (serviceRunning) {
                OutlinedButton(onClick = onStop, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Interromper serviço")
                }
            } else {
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("  Iniciar serviço")
                }
            }
        }
    }
}

@Composable
private fun AppLaunchPanel(
    context: Context,
    settings: AppSettings,
    onAutomaticChange: (Boolean) -> Unit,
    onLaunch: (String) -> Unit,
    onChooseApps: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Aplicativos e perfis", style = MaterialTheme.typography.titleMedium)
                    Text("${settings.games.size} selecionado(s)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onChooseApps) { Text("Gerenciar") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Ativação automática", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (settings.onlyInGame) "Detecta os apps selecionados em primeiro plano." else "O serviço fica sob controle manual.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = settings.onlyInGame, onCheckedChange = onAutomaticChange)
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
            if (settings.games.isEmpty()) {
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Nenhum aplicativo selecionado", style = MaterialTheme.typography.titleSmall)
                        Text("Escolha jogos ou aplicativos para associar à ativação automática.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onChooseApps, modifier = Modifier.fillMaxWidth()) { Text("Selecionar aplicativos") }
                    }
                }
            } else {
                settings.games.forEach { packageName ->
                    val label = appLabel(context, packageName)
                    val appIcon = remember(packageName) {
                        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (appIcon != null) {
                            AndroidView(
                                factory = { view -> ImageView(view).apply { scaleType = ImageView.ScaleType.FIT_CENTER } },
                                update = { imageView ->
                                    imageView.setImageDrawable(appIcon)
                                    imageView.contentDescription = "Ícone de ${label ?: packageName}"
                                },
                                modifier = Modifier.size(42.dp)
                            )
                        } else {
                            Surface(
                                modifier = Modifier.size(42.dp),
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        (label ?: packageName.substringAfterLast('.')).firstOrNull()?.uppercase() ?: "A",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        style = MaterialTheme.typography.titleMedium
                                    )
                                }
                            }
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(label ?: packageName.substringAfterLast('.'), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val profileLabel = if (settings.profiles.containsKey(packageName)) "Perfil personalizado" else "Perfil padrão"
                            Text(
                                if (label == null) "Não instalado ou não visível" else "$profileLabel · $packageName",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (label == null) StatusAmber else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        androidx.compose.material3.IconButton(onClick = { onLaunch(packageName) }, enabled = label != null) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Abrir ${label ?: packageName}", tint = if (label != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                OutlinedButton(onClick = onChooseApps, modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    Text("Editar lista de aplicativos")
                }
            }
        }
    }
}

@Composable
private fun AccessPanel(
    overlayGranted: Boolean,
    activationState: ActivationState,
    shizukuConnected: Boolean,
    onPermissions: () -> Unit,
    onActivation: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Acesso e permissões", style = MaterialTheme.typography.titleMedium)
            Text(
                "A sobreposição exibe o HUD; o Shizuku fornece a integração privilegiada usada para injetar eventos. Nenhum estado é presumido como concedido.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            StatusRow("Sobreposição de tela", overlayGranted, if (overlayGranted) "Concedida" else "Pendente")
            StatusRow(
                "Permissão Shizuku",
                shizukuConnected,
                if (shizukuConnected) "Conectado" else when (activationState) {
                    ActivationState.NEEDS_PERMISSION -> "Pendente"
                    else -> activationStateLabel(activationState)
                }
            )
            Button(onClick = onPermissions, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Verificar permissões")
            }
            OutlinedButton(onClick = onActivation, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                Icon(Icons.Filled.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Configurar ativação via Wi-Fi")
            }
        }
    }
}

@Composable
private fun QuickSettingsPanel(onOpenSettings: () -> Unit, onOpenApps: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Acesso rápido", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Mouse, mira e sistema")
            }
            OutlinedButton(onClick = onOpenApps, modifier = Modifier.fillMaxWidth().height(46.dp)) {
                Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("  Gerenciar aplicativos")
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, ready: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = (if (ready) StatusGreen else StatusAmber).copy(alpha = 0.14f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (ready) StatusGreen else StatusAmber,
            maxLines = 1
        )
    }
}

private fun activationStateLabel(state: ActivationState): String = when (state) {
    ActivationState.READY -> "Conectado"
    ActivationState.NEEDS_PERMISSION -> "Sem permissão"
    ActivationState.SHIZUKU_STOPPED -> "Parado"
    ActivationState.SHIZUKU_MISSING -> "Não instalado"
}

@Composable
private fun StatusRow(label: String, ok: Boolean, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = if (ok) "OK" else "Atenção",
            tint = if (ok) StatusGreen else StatusAmber,
            modifier = Modifier.size(20.dp)
        )
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            value,
            color = if (ok) StatusGreen else StatusAmber,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SetupStep(number: Int, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(30.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("$number", color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Nome exibido pelo Android; null identifica app ausente ou não visível para este processo. */
private fun appLabel(ctx: Context, pkg: String): String? = try {
    val pm = ctx.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
} catch (_: Exception) {
    null
}

private fun openDeveloperSettings(ctx: Context) {
    try {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_DEVICE_INFO_SETTINGS))
        } catch (_: Exception) {
            Toast.makeText(ctx, "Abra Configurações › Sobre o telefone manualmente.", Toast.LENGTH_LONG).show()
        }
    }
}
