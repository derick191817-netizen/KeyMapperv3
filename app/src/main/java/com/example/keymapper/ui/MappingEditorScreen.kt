@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.keymapper.ui

import android.app.ActivityOptions
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.keymapper.data.AppProfile
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.Mode
import com.example.keymapper.data.displayName
import com.example.keymapper.data.keyLabel
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.injection.Activation
import com.example.keymapper.injection.ActivationState
import com.example.keymapper.presentation.SettingsViewModel
import com.example.keymapper.service.MappingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Workspace de configuração em estilo desktop/emulador.
 * A prévia é baseada nas coordenadas persistidas, enquanto o arraste é feito no HUD real.
 */
@Composable
fun MappingEditorScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val selectedPackage = settings.selectedProfilePackage.ifBlank { settings.games.firstOrNull().orEmpty() }
    val selectedButtons = if (settings.onlyInGame) {
        settings.profiles[selectedPackage]?.buttons ?: settings.buttons
    } else settings.buttons

    var overlayGranted by remember { mutableStateOf(AndroidSettings.canDrawOverlays(context)) }
    var serviceRunning by remember { mutableStateOf(MappingService.running) }
    var shizukuReady by remember { mutableStateOf(false) }
    var activeProfilePackage by remember { mutableStateOf(MappingService.activeProfilePackageSnapshot) }
    var selectedControlId by remember(selectedPackage, selectedButtons) {
        mutableStateOf(selectedButtons.firstOrNull { it.kind == Kind.STANDARD }?.id)
    }

    // Estado do serviço e do perfil muda sem necessariamente recriar esta tela (ex.: volta do app).
    LaunchedEffect(Unit) {
        while (true) {
            overlayGranted = AndroidSettings.canDrawOverlays(context)
            serviceRunning = MappingService.running
            activeProfilePackage = MappingService.activeProfilePackageSnapshot
            shizukuReady = withContext(Dispatchers.IO) {
                Activation.state(context) == ActivationState.READY
            }
            delay(1_200)
        }
    }

    val activeProfileMatches = !settings.onlyInGame || activeProfilePackage == selectedPackage
    val canEditProfile = serviceRunning && overlayGranted && activeProfileMatches
    val standardControls = selectedButtons.filter { it.kind == Kind.STANDARD }
    val selectedControl = selectedButtons.firstOrNull { it.id == selectedControlId }

    fun openOverlayPermission() {
        runCatching {
            context.startActivity(
                Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            Toast.makeText(context, "Não foi possível abrir as permissões de sobreposição.", Toast.LENGTH_LONG).show()
        }
    }

    fun openTarget() {
        if (!serviceRunning || !overlayGranted || selectedPackage.isBlank()) return
        val launch = context.packageManager.getLaunchIntentForPackage(selectedPackage)
        if (launch == null) {
            Toast.makeText(context, "Não encontrei um aplicativo iniciável para este perfil.", Toast.LENGTH_LONG).show()
            return
        }
        runCatching {
            // Respeita o display escolhido em DisplayHelper: essencial no Samsung DeX.
            val displayId = DisplayHelper(context).apply {
                useExternal = settings.useExternalDisplay
            }.target().displayId
            val options = ActivityOptions.makeBasic().setLaunchDisplayId(displayId)
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options.toBundle())
        }.onFailure {
            Toast.makeText(context, "Não foi possível abrir o aplicativo no display selecionado.", Toast.LENGTH_LONG).show()
        }
    }

    fun beginLiveEdit() {
        if (!canEditProfile) return
        runCatching {
            context.startService(Intent(context, MappingService::class.java).setAction(MappingService.ACTION_BEGIN_EDIT))
        }.onFailure {
            Toast.makeText(context, "Não foi possível abrir o editor visual.", Toast.LENGTH_LONG).show()
        }
    }

    fun addStandardControl() {
        if (!canEditProfile) return
        runCatching {
            context.startService(Intent(context, MappingService::class.java).setAction(MappingService.ACTION_ADD_STANDARD_CONTROL))
        }.onFailure {
            Toast.makeText(context, "Não foi possível adicionar o controle.", Toast.LENGTH_LONG).show()
        }
    }

    fun configureControl(id: Int) {
        if (!canEditProfile) return
        runCatching {
            context.startService(
                Intent(context, MappingService::class.java)
                    .setAction(MappingService.ACTION_CONFIG_STANDARD_BUTTON)
                    .putExtra(MappingService.EXTRA_BUTTON_ID, id)
            )
        }.onFailure {
            Toast.makeText(context, "Não foi possível abrir as propriedades deste controle.", Toast.LENGTH_LONG).show()
        }
    }

    fun moveStandardControl(id: Int, x: Float, y: Float) {
        if (!canEditProfile || !x.isFinite() || !y.isFinite()) return
        val safeX = x.coerceIn(0.02f, 0.98f)
        val safeY = y.coerceIn(0.02f, 0.98f)
        viewModel.update { current ->
            val sourceButtons = if (current.onlyInGame && selectedPackage.isNotBlank()) {
                current.profiles[selectedPackage]?.buttons ?: current.buttons
            } else current.buttons
            val updatedButtons = sourceButtons.map { button ->
                if (button.id == id && button.kind == Kind.STANDARD) button.copy(x = safeX, y = safeY)
                else button
            }
            if (current.onlyInGame && selectedPackage.isNotBlank()) {
                val baseProfile = current.profiles[selectedPackage] ?: AppProfile.fromSettings(current)
                current.copy(profiles = current.profiles + (selectedPackage to baseProfile.copy(buttons = updatedButtons)))
            } else {
                current.copy(buttons = updatedButtons)
            }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        WorkspaceHeader(
            packageName = selectedPackage,
            controlCount = standardControls.size,
            serviceRunning = serviceRunning,
            profileReady = activeProfileMatches,
            externalDisplaySelected = settings.useExternalDisplay,
            onBack = onBack
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wideWorkspace = maxWidth >= 760.dp
            val contentModifier = Modifier
                .widthIn(max = 1_520.dp)
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = if (wideWorkspace) 18.dp else 12.dp, vertical = 10.dp)

            if (wideWorkspace) {
                Row(
                    modifier = contentModifier.fillMaxHeight(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        Modifier.weight(0.82f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        WorkspaceReadinessPanel(
                            serviceRunning, overlayGranted, shizukuReady, activeProfileMatches,
                            selectedPackage, ::openOverlayPermission
                        )
                        WorkspaceActionsPanel(
                            canLaunch = serviceRunning && overlayGranted && selectedPackage.isNotBlank(),
                            canEdit = canEditProfile,
                            onLaunch = ::openTarget,
                            onBeginEdit = ::beginLiveEdit,
                            onAddControl = ::addStandardControl,
                            onOpenSettings = onOpenSettings
                        )
                        WorkflowPanel()
                    }
                    Column(
                        Modifier.weight(1.48f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LayoutPreviewPanel(
                            controls = standardControls,
                            selectedId = selectedControlId,
                            gridSnap = settings.gridSnap,
                            onSelect = { selectedControlId = it },
                            onMove = ::moveStandardControl
                        )
                        ControlTablePanel(
                            controls = selectedButtons,
                            selectedId = selectedControlId,
                            canConfigure = canEditProfile,
                            onSelect = { selectedControlId = it },
                            onConfigure = ::configureControl
                        )
                    }
                    Column(
                        Modifier.weight(0.82f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ControlPropertiesPanel(selectedControl, canEditProfile) {
                            selectedControl?.takeIf { it.kind == Kind.STANDARD }?.let { configureControl(it.id) }
                        }
                        EmulatorSettingsPanel(onOpenSettings)
                    }
                }
            } else {
                Column(
                    contentModifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    WorkspaceReadinessPanel(
                        serviceRunning, overlayGranted, shizukuReady, activeProfileMatches,
                        selectedPackage, ::openOverlayPermission
                    )
                    LayoutPreviewPanel(
                        controls = standardControls,
                        selectedId = selectedControlId,
                        gridSnap = settings.gridSnap,
                        onSelect = { selectedControlId = it },
                        onMove = ::moveStandardControl
                    )
                    ControlTablePanel(
                        controls = selectedButtons,
                        selectedId = selectedControlId,
                        canConfigure = canEditProfile,
                        onSelect = { selectedControlId = it },
                        onConfigure = ::configureControl
                    )
                    ControlPropertiesPanel(selectedControl, canEditProfile) {
                        selectedControl?.takeIf { it.kind == Kind.STANDARD }?.let { configureControl(it.id) }
                    }
                    WorkspaceActionsPanel(
                        canLaunch = serviceRunning && overlayGranted && selectedPackage.isNotBlank(),
                        canEdit = canEditProfile,
                        onLaunch = ::openTarget,
                        onBeginEdit = ::beginLiveEdit,
                        onAddControl = ::addStandardControl,
                        onOpenSettings = onOpenSettings
                    )
                    EmulatorSettingsPanel(onOpenSettings)
                    WorkflowPanel()
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun WorkspaceHeader(
    packageName: String,
    controlCount: Int,
    serviceRunning: Boolean,
    profileReady: Boolean,
    externalDisplaySelected: Boolean,
    onBack: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 2.dp) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 760.dp
            if (compact) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("KM", Modifier.padding(horizontal = 10.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text("KEYMAPPER STUDIO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("Editor de controles", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                "${packageName.ifBlank { "Perfil global" }} · $controlCount controles",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        OutlinedButton(onClick = onBack) { Text("Painel") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StateTag(if (serviceRunning && profileReady) "PERFIL ATIVO" else "AGUARDANDO", serviceRunning && profileReady)
                        InfoTag(if (externalDisplaySelected) "DEX / EXTERNO" else "TELA INTERNA")
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Text("KM", Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text("KEYMAPPER STUDIO  /  WORKSPACE", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        Text("Editor de controles", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${packageName.ifBlank { "Perfil global" }}  ·  $controlCount controles de tecla",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    StateTag(if (serviceRunning && profileReady) "PERFIL ATIVO" else "AGUARDANDO", serviceRunning && profileReady)
                    InfoTag(if (externalDisplaySelected) "DEX / EXTERNO" else "TELA INTERNA")
                    OutlinedButton(onClick = onBack) { Text("Painel") }
                }
            }
        }
    }
}

@Composable
private fun WorkspacePanel(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
private fun WorkspaceReadinessPanel(
    serviceRunning: Boolean,
    overlayGranted: Boolean,
    shizukuReady: Boolean,
    activeProfileMatches: Boolean,
    selectedPackage: String,
    onGrantOverlay: () -> Unit
) {
    WorkspacePanel("Estado do sistema", "Condições reais para ativar o mapeamento") {
        CompactStatus("Serviço", serviceRunning, if (serviceRunning) "Executando" else "Inativo")
        CompactStatus("Sobreposição", overlayGranted, if (overlayGranted) "Concedida" else "Pendente")
        CompactStatus("Shizuku", shizukuReady, if (shizukuReady) "Autorizado" else "Não confirmado")
        CompactStatus(
            "Perfil",
            activeProfileMatches,
            when {
                !serviceRunning -> "Serviço parado"
                activeProfileMatches -> if (selectedPackage.isBlank()) "Global" else "Carregado"
                else -> "Abra o app do perfil"
            }
        )
        if (!overlayGranted) {
            Button(onClick = onGrantOverlay, modifier = Modifier.fillMaxWidth()) {
                Text("Permitir sobreposição")
            }
        }
    }
}

@Composable
private fun CompactStatus(label: String, ready: Boolean, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ready) Color(0xFF62D6A8) else Color(0xFFF0B85D),
            modifier = Modifier.size(16.dp)
        )
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LayoutPreviewPanel(
    controls: List<HudButton>,
    selectedId: Int?,
    gridSnap: Int,
    onSelect: (Int) -> Unit,
    onMove: (Int, Float, Float) -> Unit
) {
    WorkspacePanel("Layout do perfil", "Prévia 16:9 · selecione e arraste os controles") {
        BoxWithConstraints(
            Modifier.fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(13.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF0B1722), Color(0xFF101F2B), Color(0xFF08121A))))
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), RoundedCornerShape(13.dp))
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx().coerceAtLeast(1f) }
            val heightPx = with(density) { maxHeight.toPx().coerceAtLeast(1f) }
            val dragRadiusPx = with(density) { 38.dp.toPx() }
            var previewPositions by remember(controls) {
                mutableStateOf(controls.associate { control ->
                    control.id to Offset(
                        control.x.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
                        control.y.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
                    )
                })
            }
            var draggingId by remember { mutableStateOf<Int?>(null) }
            val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
            val centerLine = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            Canvas(Modifier.fillMaxSize()) {
                for (i in 1..7) {
                    val x = size.width * i / 8f
                    val y = size.height * i / 8f
                    drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1f)
                    drawLine(grid, Offset(0f, y), Offset(size.width, y), 1f)
                }
                drawLine(centerLine, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1.2f)
                drawLine(centerLine, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1.2f)
            }
            if (controls.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("SEM CONTROLES PADRÃO", color = Color(0xFFA7BAC8), style = MaterialTheme.typography.labelMedium)
                }
            }
            Box(
                Modifier.fillMaxSize().pointerInput(controls, widthPx, heightPx) {
                    detectDragGestures(
                        onDragStart = { start ->
                            val candidate = controls.mapNotNull { control ->
                                val point = previewPositions[control.id] ?: Offset(control.x, control.y)
                                val dx = point.x * widthPx - start.x
                                val dy = point.y * heightPx - start.y
                                val distanceSquared = dx * dx + dy * dy
                                if (distanceSquared <= dragRadiusPx * dragRadiusPx) control.id to distanceSquared else null
                            }.minByOrNull { it.second }?.first
                            draggingId = candidate
                            candidate?.let(onSelect)
                        },
                        onDrag = { change, _ ->
                            val id = draggingId ?: return@detectDragGestures
                            var nextX = (change.position.x / widthPx).coerceIn(0.02f, 0.98f)
                            var nextY = (change.position.y / heightPx).coerceIn(0.02f, 0.98f)
                            val snap = gridSnap.coerceIn(0, 100)
                            if (snap > 0) {
                                nextX = (nextX * snap).roundToInt() / snap.toFloat()
                                nextY = (nextY * snap).roundToInt() / snap.toFloat()
                            }
                            val next = Offset(nextX.coerceIn(0.02f, 0.98f), nextY.coerceIn(0.02f, 0.98f))
                            previewPositions = previewPositions + (id to next)
                            change.consume()
                        },
                        onDragEnd = {
                            val id = draggingId
                            if (id != null) previewPositions[id]?.let { position ->
                                onMove(id, position.x, position.y)
                            }
                            draggingId = null
                        },
                        onDragCancel = {
                            draggingId = null
                            previewPositions = controls.associate { control ->
                                control.id to Offset(
                                    control.x.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f,
                                    control.y.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 0.5f
                                )
                            }
                        }
                    )
                }
            ) {
                controls.take(64).forEach { control ->
                    val markerSize = 34.dp
                    val position = previewPositions[control.id] ?: Offset(control.x, control.y)
                    Box(
                        Modifier.offset(
                            x = maxWidth * position.x - markerSize / 2,
                            y = maxHeight * position.y - markerSize / 2
                        )
                            .size(markerSize)
                            .clip(CircleShape)
                            .background(if (control.id == selectedId) MaterialTheme.colorScheme.primary else Color(0xFF193C50))
                            .border(1.dp, if (control.id == selectedId) Color.White else Color(0xFF79B9D0), CircleShape)
                            .clickable { onSelect(control.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (control.key == 0) "?" else keyLabel(control.key).take(4),
                            color = if (control.id == selectedId) MaterialTheme.colorScheme.onPrimary else Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Text(
            "Arraste um marcador para reposicionar a tecla. A posição é salva no perfil do aplicativo; o HUD ao vivo continua disponível para ajustes finos.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ControlTablePanel(
    controls: List<HudButton>,
    selectedId: Int?,
    canConfigure: Boolean,
    onSelect: (Int) -> Unit,
    onConfigure: (Int) -> Unit
) {
    WorkspacePanel("Mapeamentos", "Tabela de associações do perfil") {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f), RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CONTROLE", Modifier.weight(1.35f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("TECLA", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("MODO", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("AÇÃO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (controls.isEmpty()) {
            EmptyWorkspaceState("Nenhum mapeamento cadastrado", "Adicione um controle de tecla para criar a primeira associação.")
        } else {
            controls.take(128).forEach { control ->
                val standard = control.kind == Kind.STANDARD
                Row(
                    Modifier.fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (control.id == selectedId) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f) else Color.Transparent)
                        .clickable { onSelect(control.id) }
                        .padding(horizontal = 8.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Column(Modifier.weight(1.35f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(control.displayName(), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("ID ${control.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(keyLabel(control.key), Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(modeLabel(control.mode), Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (standard) {
                        OutlinedButton(
                            onClick = { onSelect(control.id); onConfigure(control.id) },
                            enabled = canConfigure,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Icon(Icons.Filled.Settings, contentDescription = "Configurar ${control.displayName()}", modifier = Modifier.size(15.dp))
                        }
                    } else {
                        Text("LEGADO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            }
        }
    }
}

@Composable
private fun ControlPropertiesPanel(control: HudButton?, canConfigure: Boolean, onConfigure: () -> Unit) {
    WorkspacePanel("Propriedades", "Selecione uma linha ou marcador da prévia") {
        if (control == null) {
            EmptyWorkspaceState("Nenhum controle selecionado", "Os parâmetros serão exibidos aqui.")
        } else if (control.kind != Kind.STANDARD) {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                    Text("Este é um controle especializado legado. Esta versão do workspace expõe edição apenas para associações padrão.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            PropertyLine("Nome", control.displayName())
            PropertyLine("Tecla", keyLabel(control.key))
            PropertyLine("Modo", modeLabel(control.mode))
            PropertyLine("Posição X", "${(control.x * 100).toInt()}%")
            PropertyLine("Posição Y", "${(control.y * 100).toInt()}%")
            PropertyLine("Tamanho", "${(control.size * 100).toInt()}%")
            Button(onClick = onConfigure, enabled = canConfigure, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(17.dp))
                Text("  Abrir propriedades completas")
            }
        }
    }
}

@Composable
private fun PropertyLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun WorkspaceActionsPanel(
    canLaunch: Boolean,
    canEdit: Boolean,
    onLaunch: () -> Unit,
    onBeginEdit: () -> Unit,
    onAddControl: () -> Unit,
    onOpenSettings: () -> Unit
) {
    WorkspacePanel("Ferramentas do editor", "Ações conectadas ao perfil ativo") {
        Button(onClick = onAddControl, enabled = canEdit, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Keyboard, contentDescription = null, modifier = Modifier.size(17.dp))
            Text("  Adicionar tecla")
        }
        OutlinedButton(onClick = onBeginEdit, enabled = canEdit, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(17.dp))
            Text("  Editar HUD ao vivo")
        }
        OutlinedButton(onClick = onLaunch, enabled = canLaunch, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Launch, contentDescription = null, modifier = Modifier.size(17.dp))
            Text("  Abrir aplicativo")
        }
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(17.dp))
            Text("  Configurações gerais")
        }
        if (!canEdit) {
            Text(
                "Para editar, inicie o serviço, conceda a sobreposição e deixe o aplicativo deste perfil em primeiro plano.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmulatorSettingsPanel(onOpenSettings: () -> Unit) {
    WorkspacePanel("Configuração do emulador", "Ajustes globais e de display") {
        Text("Atalhos, cursor, área de mapeamento, desempenho, DeX e compatibilidade ficam nas configurações gerais.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(17.dp))
            Text("  Abrir painel de ajustes")
        }
    }
}

@Composable
private fun WorkflowPanel() {
    WorkspacePanel("Fluxo de configuração", "Mesmo modelo operacional dos editores de emulador") {
        WorkflowLine("01", "Selecione o perfil", "As associações são isoladas por aplicativo.")
        WorkflowLine("02", "Adicione uma tecla", "O controle nasce no centro e abre as propriedades para vincular uma entrada.")
        WorkflowLine("03", "Ajuste o HUD", "Arraste os marcadores no aplicativo de destino e finalize pelo próprio HUD.")
        WorkflowLine("04", "Valide a entrada", "Confira captura, injeção e display no diagnóstico antes de considerar a configuração pronta.")
    }
}

@Composable
private fun WorkflowLine(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = RoundedCornerShape(7.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Text(number, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelMedium)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyWorkspaceState(title: String, description: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(Icons.Filled.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun modeLabel(mode: Int): String = when (mode) {
    Mode.TAP -> "Toque"
    Mode.TOGGLE -> "Alternar"
    Mode.DOUBLE -> "Duplo"
    else -> "Segurar"
}

@Composable
private fun InfoTag(label: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StateTag(label: String, ready: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = (if (ready) Color(0xFF55D6A4) else Color(0xFFF4BD66)).copy(alpha = 0.13f)
    ) {
        Text(
            label,
            Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (ready) Color(0xFF55D6A4) else Color(0xFFF4BD66)
        )
    }
}
