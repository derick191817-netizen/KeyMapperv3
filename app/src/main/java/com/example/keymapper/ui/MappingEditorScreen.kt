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
import com.example.keymapper.domain.MappingEditorPolicy
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
 * A prévia representa todos os tipos e áreas do perfil; os parâmetros completos são abertos no HUD real.
 */
@Composable
fun MappingEditorScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenSettings: (Int) -> Unit
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
    var selectedControlId by remember(selectedPackage) {
        mutableStateOf(selectedButtons.firstOrNull()?.id)
    }
    // Reposicionar um controle muda a lista, mas não deve perder a seleção atual.
    // Só escolhe outro item quando o controle selecionado deixa de existir no perfil.
    LaunchedEffect(selectedPackage, selectedButtons.map { it.id }) {
        if (selectedButtons.none { it.id == selectedControlId }) {
            selectedControlId = selectedButtons.firstOrNull()?.id
        }
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
    val canEditProfile = serviceRunning && overlayGranted && shizukuReady && activeProfileMatches
    // A prévia deve representar exatamente a lista persistida, não apenas os controles padrão.
    val profileControls = selectedButtons
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

    fun addControl(kind: Int, mode: Int, name: String) {
        if (!canEditProfile) return
        runCatching {
            context.startService(
                Intent(context, MappingService::class.java)
                    .setAction(MappingService.ACTION_ADD_CONTROL)
                    .putExtra(MappingService.EXTRA_CONTROL_KIND, kind)
                    .putExtra(MappingService.EXTRA_CONTROL_MODE, mode)
                    .putExtra(MappingService.EXTRA_CONTROL_NAME, name)
            )
        }.onFailure {
            Toast.makeText(context, "Não foi possível criar este controle.", Toast.LENGTH_LONG).show()
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

    fun moveControl(id: Int, x: Float, y: Float) {
        if (!canEditProfile || !x.isFinite() || !y.isFinite()) return
        val safeX = x.coerceIn(0.02f, 0.98f)
        val safeY = y.coerceIn(0.02f, 0.98f)
        viewModel.update { current ->
            val sourceButtons = if (current.onlyInGame && selectedPackage.isNotBlank()) {
                current.profiles[selectedPackage]?.buttons ?: current.buttons
            } else current.buttons
            val updatedButtons = sourceButtons.map { button ->
                if (button.id == id) button.copy(x = safeX, y = safeY)
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
            controlCount = profileControls.size,
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
                            onAddControl = { kind, mode, name -> addControl(kind, mode, name) },
                            onOpenSettings = onOpenSettings
                        )
                        WorkflowPanel()
                    }
                    Column(
                        Modifier.weight(1.48f).fillMaxHeight().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        LayoutPreviewPanel(
                            controls = profileControls,
                            selectedId = selectedControlId,
                            gridSnap = settings.gridSnap,
                            joystickX = settings.joyX, joystickY = settings.joyY, joystickRadius = settings.joyR,
                            lookX = settings.lookX, lookY = settings.lookY, lookRadius = settings.lookR,
                            canMove = canEditProfile,
                            onSelect = { selectedControlId = it },
                            onMove = ::moveControl
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
                            selectedControl?.let { configureControl(it.id) }
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
                        controls = profileControls,
                        selectedId = selectedControlId,
                        gridSnap = settings.gridSnap,
                        joystickX = settings.joyX, joystickY = settings.joyY, joystickRadius = settings.joyR,
                        lookX = settings.lookX, lookY = settings.lookY, lookRadius = settings.lookR,
                        canMove = canEditProfile,
                        onSelect = { selectedControlId = it },
                        onMove = ::moveControl
                    )
                    ControlTablePanel(
                        controls = selectedButtons,
                        selectedId = selectedControlId,
                        canConfigure = canEditProfile,
                        onSelect = { selectedControlId = it },
                        onConfigure = ::configureControl
                    )
                    ControlPropertiesPanel(selectedControl, canEditProfile) {
                        selectedControl?.let { configureControl(it.id) }
                    }
                    WorkspaceActionsPanel(
                        canLaunch = serviceRunning && overlayGranted && selectedPackage.isNotBlank(),
                        canEdit = canEditProfile,
                        onLaunch = ::openTarget,
                        onBeginEdit = ::beginLiveEdit,
                        onAddControl = { kind, mode, name -> addControl(kind, mode, name) },
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
                            "${packageName.ifBlank { "Perfil global" }}  ·  $controlCount controles mapeados",
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
    canMove: Boolean,
    joystickX: Float, joystickY: Float, joystickRadius: Float,
    lookX: Float, lookY: Float, lookRadius: Float,
    onSelect: (Int) -> Unit,
    onMove: (Int, Float, Float) -> Unit
) {
    WorkspacePanel(
        "Layout do perfil",
        if (canMove) "Prévia 16:9 · selecione e arraste os controles"
        else "Prévia 16:9 · arraste disponível com serviço, Shizuku e perfil ativos"
    ) {
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

                // As áreas de WASD e FPS View também fazem parte do layout persistido.
                val minSide = size.minDimension
                val joyCenter = Offset(joystickX.coerceIn(0f, 1f) * size.width, joystickY.coerceIn(0f, 1f) * size.height)
                val joyRadius = (joystickRadius.takeIf { it.isFinite() } ?: 0.1f).coerceIn(0.01f, 0.5f) * minSide
                drawCircle(Color(0xFF56D69A).copy(alpha = 0.07f), joyRadius, joyCenter)
                drawCircle(Color(0xFF56D69A).copy(alpha = 0.8f), joyRadius, joyCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(1.6f))

                val lookCenter = Offset(lookX.coerceIn(0f, 1f) * size.width, lookY.coerceIn(0f, 1f) * size.height)
                val lookSafeRadius = (lookRadius.takeIf { it.isFinite() } ?: 0.35f).coerceIn(0.01f, 1f) * minSide
                drawCircle(Color(0xFF62B6F2).copy(alpha = 0.035f), lookSafeRadius, lookCenter)
                drawCircle(Color(0xFF62B6F2).copy(alpha = 0.42f), lookSafeRadius, lookCenter, style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f))
            }
            if (controls.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("SEM BOTÕES MAPEADOS", color = Color(0xFFA7BAC8), style = MaterialTheme.typography.labelMedium)
                }
            }
            Box(
                Modifier.fillMaxSize().pointerInput(controls, widthPx, heightPx, gridSnap, canMove) {
                    detectDragGestures(
                        onDragStart = { start ->
                            if (!canMove) {
                                draggingId = null
                                return@detectDragGestures
                            }
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
                            if (!canMove) return@detectDragGestures
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
                            if (id != null) previewPositions[id]?.let { position -> onMove(id, position.x, position.y) }
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
                controls.take(128).forEach { control ->
                    val markerSize = 34.dp
                    val position = previewPositions[control.id] ?: Offset(control.x, control.y)
                    val markerColor = when (control.kind) {
                        Kind.FIRE -> Color(0xFF9B3939)
                        Kind.AIM -> Color(0xFF176B54)
                        Kind.SWIPE -> Color(0xFF6650A4)
                        Kind.MACRO -> Color(0xFF98612D)
                        else -> Color(0xFF193C50)
                    }
                    Box(
                        Modifier.offset(
                            x = maxWidth * position.x - markerSize / 2,
                            y = maxHeight * position.y - markerSize / 2
                        )
                            .size(markerSize)
                            .clip(CircleShape)
                            .background(if (control.id == selectedId) MaterialTheme.colorScheme.primary else markerColor)
                            .border(1.dp, if (control.id == selectedId) Color.White else Color(0xFF79B9D0), CircleShape)
                            .clickable { onSelect(control.id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            when (control.kind) {
                                Kind.FIRE -> "F"
                                Kind.AIM -> "◎"
                                Kind.SWIPE -> "↗"
                                Kind.MACRO -> "M"
                                else -> when (control.mode) {
                                    Mode.TAP -> "T"
                                    Mode.RAPID -> "R"
                                    else -> if (control.key == 0) "H" else keyLabel(control.key).take(4)
                                }
                            },
                            color = if (control.id == selectedId) MaterialTheme.colorScheme.onPrimary else Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Spacer(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF56D69A)))
                Text("WASD", style = MaterialTheme.typography.labelSmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Spacer(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF62B6F2)))
                Text("FPS View", style = MaterialTheme.typography.labelSmall)
            }
        Text("F = Fire · ◎ = Aim · ↗ = Swipe · M = Macro · T/H = Tap/Hold", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "Arraste qualquer marcador para reposicionar. As áreas WASD/FPS View são referências visuais; para ajustar o raio e o comportamento, use as configurações do perfil.",
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
            Text("ATALHO", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("MODO", Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Text("AÇÃO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        if (controls.isEmpty()) {
            EmptyWorkspaceState("Nenhum mapeamento cadastrado", "Adicione um controle de tecla para criar a primeira associação.")
        } else {
            controls.take(128).forEach { control ->
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
                        Text("${MappingEditorPolicy.controlDisplayLabel(control.kind, control.mode, control.isCombo || control.macro.isNotEmpty()).uppercase()} · ID ${control.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(keyLabel(control.key), Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (control.kind == Kind.MACRO) "Sequência" else modeLabel(control.mode), Modifier.weight(0.8f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    OutlinedButton(
                        onClick = { onSelect(control.id); onConfigure(control.id) },
                        enabled = canConfigure,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Filled.Settings, contentDescription = "Configurar ${control.displayName()}", modifier = Modifier.size(15.dp))
                    }
                }
                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
            }
        }
    }
}

@Composable
private fun ControlPropertiesPanel(control: HudButton?, canConfigure: Boolean, onConfigure: () -> Unit) {
    WorkspacePanel("Propriedades", "Parâmetros do controle selecionado") {
        if (control == null) {
            EmptyWorkspaceState("Nenhum controle selecionado", "Selecione um marcador ou uma linha da tabela.")
        } else {
            PropertyLine("Tipo", MappingEditorPolicy.controlDisplayLabel(control.kind, control.mode, control.isCombo || control.macro.isNotEmpty()))
            PropertyLine("Nome", control.displayName())
            PropertyLine("Tecla", keyLabel(control.key))
            PropertyLine("Modo", if (control.kind == Kind.MACRO) "Sequência temporizada" else modeLabel(control.mode))
            PropertyLine("Posição X", "${(control.x * 100).toInt()}%")
            PropertyLine("Posição Y", "${(control.y * 100).toInt()}%")
            PropertyLine("Tamanho", "${(control.size * 100).toInt()}%")
            when (control.kind) {
                Kind.FIRE -> PropertyLine("Vínculo de mira", if (control.linkAimId >= 0) "Mira #${control.linkAimId}" else "Nenhum")
                Kind.AIM -> {
                    PropertyLine("Sensibilidade X/Y", "${control.aimSensX} / ${control.aimSensY}")
                    PropertyLine("Mouse move a câmera", if (control.mouseAim) "Sim" else "Não")
                    PropertyLine("Mira alternável", if (control.aimToggle) "Sim" else "Não")
                }
                Kind.SWIPE -> {
                    PropertyLine("Destino X/Y", "${(control.swipeEndX * 100).toInt()}% / ${(control.swipeEndY * 100).toInt()}%")
                    PropertyLine("Duração", "${control.swipeDurationMs} ms")
                }
            }
            if (control.kind == Kind.STANDARD && control.mode == Mode.RAPID) {
                PropertyLine("Intervalo", "${control.rapidIntervalMs} ms")
            }
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
    onAddControl: (Int, Int, String) -> Unit,
    onOpenSettings: (Int) -> Unit
) {
    WorkspacePanel("Biblioteca HUD", "Controles principais e áreas configuráveis do perfil") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ControlLibraryTile("Tap", "tap", "Toque único", Color(0xFFE0B84E), canEdit, Modifier.weight(1f)) {
                onAddControl(Kind.STANDARD, Mode.TAP, "Tap")
            }
            ControlLibraryTile("Hold", "hold", "Segurar", Color(0xFF42A5C5), canEdit, Modifier.weight(1f)) {
                onAddControl(Kind.STANDARD, Mode.HOLD, "Hold")
            }
            ControlLibraryTile("Fire", "fire", "Disparo dedicado · M1", Color(0xFFE05A63), canEdit, Modifier.weight(1f)) {
                onAddControl(Kind.FIRE, Mode.HOLD, "Fire")
            }
            ControlLibraryTile("WASD / Stick", "move", "Configurar zona virtual", Color(0xFF8E78D5), canEdit, Modifier.weight(1f)) {
                // A zona do perfil é a implementação virtual única de WASD; o backend ainda
                // não captura eixos analógicos HID de gamepads.
                onOpenSettings(2)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ControlLibraryTile("Aim / Sight", "aim", "Visão e câmera", Color(0xFF45B78A), canEdit, Modifier.weight(1.5f)) {
                onAddControl(Kind.AIM, Mode.HOLD, "Aim / Sight")
            }
            ControlLibraryTile("Swipe", "swipe", "Arrastar origem → destino", Color(0xFF8E78D5), canEdit, Modifier.weight(1.5f)) {
                onAddControl(Kind.SWIPE, Mode.HOLD, "Swipe")
            }
        }
        OutlinedButton(onClick = onBeginEdit, enabled = canEdit, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("  Editar HUD ao vivo")
        }
        if (!canEdit) {
            Text(
                "Para adicionar ou configurar controles, inicie o serviço, conceda a sobreposição e deixe o aplicativo deste perfil em primeiro plano.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    WorkspacePanel("Avançado", "Ferramentas adicionais e estado real de implementação") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            ControlLibraryTile("Combo", "combo", "Aciona vários controles juntos", Color(0xFFD18A49), canEdit, Modifier.weight(1f)) {
                onAddControl(Kind.STANDARD, Mode.HOLD, "Combo")
            }
            ControlLibraryTile("Rapid Tap", "rapid", "Repete até soltar · intervalo ajustável", Color(0xFFD18A49), canEdit, Modifier.weight(1f)) {
                onAddControl(Kind.STANDARD, Mode.RAPID, "Rapid Tap")
            }
            ControlLibraryTile("Macro", "macro", "Macro sequencial · pausas, toques, arrastos e cancelamento", Color(0xFFD18A49), canEdit, Modifier.weight(1f)) {
                onAddControl(Kind.MACRO, Mode.HOLD, "Macro")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Column(Modifier.fillMaxWidth().padding(7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Scroll", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Text("Global: roda vertical → Page Up/Down; horizontal → setas. Não é um controle HUD independente.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            "Movimento: zona virtual WASD. Entrada analógica física de gamepad ainda não implementada.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(onClick = onLaunch, enabled = canLaunch, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
            Text("  Abrir aplicativo")
        }
    }
}

@Composable
private fun ControlLibraryTile(
    title: String,
    glyph: String,
    description: String,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier.clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.48f else 0.20f))
            .border(1.dp, accent.copy(alpha = if (enabled) 0.58f else 0.22f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            Modifier.size(27.dp).clip(RoundedCornerShape(8.dp)).background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            ControlLibraryVectorIcon(glyph, accent)
        }
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ControlLibraryVectorIcon(kind: String, tint: Color) {
    Canvas(Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val c = Offset(w * 0.5f, h * 0.5f)
        val stroke = w * 0.075f
        val ring = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        when (kind) {
            "tap" -> {
                drawCircle(tint, radius = w * 0.18f, center = c)
                drawLine(tint, Offset(w * 0.70f, h * 0.28f), Offset(w * 0.88f, h * 0.10f), strokeWidth = stroke)
                drawLine(tint, Offset(w * 0.74f, h * 0.48f), Offset(w * 0.96f, h * 0.48f), strokeWidth = stroke)
            }
            "hold" -> {
                drawCircle(tint, radius = w * 0.29f, center = c, style = ring)
                drawCircle(tint, radius = w * 0.10f, center = c)
            }
            "fire" -> {
                val flame = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.52f, h * 0.04f)
                    cubicTo(w * 0.47f, h * 0.28f, w * 0.88f, h * 0.39f, w * 0.78f, h * 0.72f)
                    cubicTo(w * 0.70f, h * 0.98f, w * 0.29f, h * 0.99f, w * 0.19f, h * 0.73f)
                    cubicTo(w * 0.06f, h * 0.45f, w * 0.39f, h * 0.33f, w * 0.42f, h * 0.12f)
                    cubicTo(w * 0.44f, h * 0.35f, w * 0.66f, h * 0.44f, w * 0.56f, h * 0.67f)
                    cubicTo(w * 0.46f, h * 0.58f, w * 0.35f, h * 0.72f, w * 0.46f, h * 0.86f)
                    cubicTo(w * 0.61f, h * 0.82f, w * 0.66f, h * 0.66f, w * 0.59f, h * 0.53f)
                    close()
                }
                drawPath(flame, tint)
            }
            "move" -> {
                drawCircle(tint, radius = w * 0.25f, center = c, style = ring)
                drawLine(tint, Offset(c.x, h * 0.05f), Offset(c.x, h * 0.95f), strokeWidth = stroke)
                drawLine(tint, Offset(w * 0.05f, c.y), Offset(w * 0.95f, c.y), strokeWidth = stroke)
                drawCircle(tint, radius = w * 0.07f, center = c)
            }
            "aim" -> {
                drawCircle(tint, radius = w * 0.26f, center = c, style = ring)
                drawCircle(tint, radius = w * 0.06f, center = c)
                drawLine(tint, Offset(c.x, 0f), Offset(c.x, h * 0.20f), strokeWidth = stroke)
                drawLine(tint, Offset(c.x, h * 0.80f), Offset(c.x, h), strokeWidth = stroke)
                drawLine(tint, Offset(0f, c.y), Offset(w * 0.20f, c.y), strokeWidth = stroke)
                drawLine(tint, Offset(w * 0.80f, c.y), Offset(w, c.y), strokeWidth = stroke)
            }
            "swipe" -> {
                drawLine(tint, Offset(w * 0.12f, h * 0.82f), Offset(w * 0.78f, h * 0.18f), strokeWidth = stroke)
                drawLine(tint, Offset(w * 0.48f, h * 0.18f), Offset(w * 0.78f, h * 0.18f), strokeWidth = stroke)
                drawLine(tint, Offset(w * 0.78f, h * 0.18f), Offset(w * 0.78f, h * 0.48f), strokeWidth = stroke)
            }
            "macro" -> {
                drawCircle(tint, radius = w * 0.34f, center = c, style = ring)
                drawLine(tint, Offset(w * 0.50f, h * 0.18f), Offset(w * 0.50f, h * 0.50f), strokeWidth = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.73f, h * 0.50f), strokeWidth = stroke)
                drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.50f, h * 0.18f))
                drawCircle(tint, radius = w * 0.06f, center = Offset(w * 0.73f, h * 0.50f))
            }
            "combo" -> {
                val a = Offset(w * 0.22f, h * 0.22f)
                val b = Offset(w * 0.78f, h * 0.22f)
                val d = Offset(w * 0.50f, h * 0.78f)
                drawLine(tint, a, b, strokeWidth = stroke)
                drawLine(tint, a, d, strokeWidth = stroke)
                drawLine(tint, b, d, strokeWidth = stroke)
                drawCircle(tint, w * 0.13f, a)
                drawCircle(tint, w * 0.13f, b)
                drawCircle(tint, w * 0.13f, d)
            }
            "rapid" -> {
                drawCircle(tint, radius = w * 0.27f, center = c, style = ring)
                drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.50f, h * 0.25f), strokeWidth = stroke)
                drawLine(tint, Offset(w * 0.50f, h * 0.50f), Offset(w * 0.72f, h * 0.62f), strokeWidth = stroke)
            }
        }
    }
}

@Composable
private fun EmulatorSettingsPanel(onOpenSettings: (Int) -> Unit) {
    WorkspacePanel("Configuração do emulador", "Ajustes globais e de display") {
        Text("Atalhos, cursor, área de mapeamento, desempenho, DeX e compatibilidade ficam nas configurações gerais.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { onOpenSettings(2) }, modifier = Modifier.fillMaxWidth()) {
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
    Mode.RAPID -> "Rapid Tap"
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
