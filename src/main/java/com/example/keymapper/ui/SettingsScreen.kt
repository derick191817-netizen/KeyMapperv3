@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.keymapper.ui

import android.app.ActivityManager
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.Kind
import com.example.keymapper.data.displayName
import com.example.keymapper.data.Mode
import com.example.keymapper.data.Rot
import com.example.keymapper.presentation.SettingsViewModel
import com.example.keymapper.diagnostics.CrashReporter
import com.example.keymapper.diagnostics.RestartDiagnostics
import com.example.keymapper.diagnostics.InputDiagnostics
import com.example.keymapper.data.keyLabel
import com.example.keymapper.domain.MappingEditorPolicy
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.display.DisplayUtils
import com.example.keymapper.injection.Activation
import com.example.keymapper.injection.ActivationState
import com.example.keymapper.input.CalibrationAssistant
import com.example.keymapper.input.CaptureView
import com.example.keymapper.input.InputDeviceManager
import com.example.keymapper.input.PointerSpeed
import com.example.keymapper.service.MappingService
import com.example.keymapper.util.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private typealias Edit = ((AppSettings) -> AppSettings) -> Unit

private class TabDef(val title: String, val icon: ImageVector)

private val TABS = listOf(
    TabDef("Mouse", Icons.Filled.Place),
    TabDef("Mira", Icons.Filled.PlayArrow),
    TabDef("Aparência", Icons.Filled.Edit),
    TabDef("Tela / DeX", Icons.Filled.Info),
    TabDef("Apps", Icons.Filled.Menu),
    TabDef("Sistema", Icons.Filled.Settings)
)

private data class Hw(
    val name: String, val hz: Int, val ramGb: Int, val res: String, val dpi: Int,
    val rates: List<Int>, val dex: Boolean, val ratio: Float, val external: Boolean
)

private fun readHw(ctx: Context): Hw {
    val name = runCatching {
        Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL
    }.getOrDefault("Dispositivo Android")
    val ramGb = runCatching {
        val manager = ctx.getSystemService(ActivityManager::class.java)
            ?: throw IllegalStateException("ActivityManager indisponível")
        val memory = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(memory)
        (memory.totalMem / (1024.0 * 1024.0 * 1024.0)).roundToInt().coerceAtLeast(1)
    }.getOrDefault(4)

    // A leitura do display é apenas informativa nesta tela. Uma falha transitória
    // de DeX/DisplayManager deve produzir campos indisponíveis, não derrubar a UI.
    val displayData = runCatching {
        val helper = DisplayHelper(ctx)
        val display = helper.target()
        val info = helper.info(display)
        val rates = runCatching { helper.rates(display) }.getOrDefault(emptyList())
        val refresh = rates.maxOrNull()
            ?: info.refreshRate.takeIf { it.isFinite() && it > 0f }?.roundToInt()
            ?: 60
        val size = if (info.width > 0 && info.height > 0) "${info.width}x${info.height}" else "Indisponível"
        val dpi = info.densityDpi.takeIf { it in 80..1000 } ?: 0
        val ratio = info.ratio.takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
        Hw(
            name, refresh, ramGb, size, dpi, rates,
            runCatching { DisplayUtils.isDex(ctx, info) }.getOrDefault(false),
            ratio, runCatching { helper.external() != null }.getOrDefault(false)
        )
    }.getOrElse {
        Hw(name, 60, ramGb, "Indisponível", 0, emptyList(), false, 16f / 9f, false)
    }
    return displayData
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, tick: MutableIntState, onBack: () -> Unit = {}, initialTab: Int = 0) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val stored by viewModel.settings.collectAsState()
    var draft by remember { mutableStateOf<AppSettings?>(null) }
    var layoutReset by remember { mutableStateOf(false) }
    var tab by remember(initialTab) { mutableIntStateOf(initialTab.coerceIn(0, TABS.lastIndex)) }
    var confirmReset by remember { mutableStateOf(false) }

    val hw = remember(tick.intValue) { readHw(ctx) }
    var state by remember { mutableStateOf(ActivationState.SHIZUKU_STOPPED) }
    LaunchedEffect(tick.intValue) { state = withContext(Dispatchers.IO) { Activation.state(ctx) } }
    val running = remember(tick.intValue) { MappingService.running }
    val overlayOk = remember(tick.intValue) { Settings.canDrawOverlays(ctx) }

    val d = draft ?: stored
    val allBindings = buildList {
        d.buttons.forEach { add(MappingEditorPolicy.KeyBinding(it.id, it.displayName(), it.key)) }
        d.profiles.values.forEach { profile ->
            profile.buttons.forEach { add(MappingEditorPolicy.KeyBinding(it.id, it.displayName(), it.key)) }
        }
    }
    val globalBindingConflict = MappingEditorPolicy.findGlobalShortcutConflict(d.editKey, d.pauseKey, allBindings)
    val conflict = d.editKey == d.pauseKey || globalBindingConflict != null
    val dirty = draft != null && draft != stored
    val edit: Edit = { f -> draft = f(draft ?: stored) }

    fun save() {
        val prev = stored
        val cur = draft ?: return
        // posições do HUD são editadas pelo overlay; preserva o que está salvo
        val out = if (layoutReset) cur else cur.copy(
            buttons = prev.buttons, joyX = prev.joyX, joyY = prev.joyY,
            lookX = prev.lookX, lookY = prev.lookY
        )
        scope.launch {
            try {
                viewModel.saveAndAwait(out)
                draft = null
                layoutReset = false
                Haptics.tick(view)
                val r = snack.showSnackbar("Configurações gravadas", "Desfazer", duration = SnackbarDuration.Long)
                if (r == SnackbarResult.ActionPerformed) viewModel.saveAndAwait(prev)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                snack.showSnackbar("Falha ao gravar as configurações. Os dados ainda não foram confirmados.")
            }
        }
    }

    fun cancel() {
        draft = null
        layoutReset = false
        Haptics.tick(view)
    }

    fun restoreDefaults() {
        val prevDraft = draft
        val prevReset = layoutReset
        draft = AppSettings()
        layoutReset = true
        scope.launch {
            val r = snack.showSnackbar("Padrões carregados. Toque em Salvar para aplicar.", "Desfazer")
            if (r == SnackbarResult.ActionPerformed) {
                draft = prevDraft
                layoutReset = prevReset
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = { Header(hw, state, running, dirty, onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 430.dp) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { cancel() }, enabled = dirty, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                                Button(onClick = { save() }, enabled = dirty && !conflict, modifier = Modifier.weight(1f)) { Text("Salvar") }
                            }
                            TextButton(onClick = { restoreDefaults() }, modifier = Modifier.fillMaxWidth()) { Text("Restaurar padrões") }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { restoreDefaults() }, modifier = Modifier.weight(1.3f)) { Text("Restaurar padrões") }
                            OutlinedButton(onClick = { cancel() }, enabled = dirty, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                            Button(onClick = { save() }, enabled = dirty && !conflict, modifier = Modifier.weight(1f)) { Text("Salvar") }
                        }
                    }
                }
            }
        }
    ) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            val desktopSettings = maxWidth >= 760.dp
            if (desktopSettings) {
                Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        modifier = Modifier.width(116.dp).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(22.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    ) {
                        NavigationRail(
                            containerColor = Color.Transparent,
                            header = {
                                Text(
                                    "AJUSTES",
                                    modifier = Modifier.padding(top = 18.dp, bottom = 14.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            TABS.forEachIndexed { i, t ->
                                NavigationRailItem(
                                    selected = tab == i,
                                    onClick = { tab = i; Haptics.tick(view) },
                                    icon = { Icon(t.icon, contentDescription = t.title) },
                                    label = { Text(t.title, maxLines = 1) },
                                    alwaysShowLabel = true
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.42f),
                        shape = RoundedCornerShape(22.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
                    ) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp)) {
                            SettingsTabBody(tab, d, edit, ctx, state, tick.intValue, hw, conflict, globalBindingConflict, overlayOk, running) { confirmReset = true }
                        }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TABS.forEachIndexed { i, t ->
                                FilterChip(
                                    selected = tab == i,
                                    onClick = { tab = i; Haptics.tick(view) },
                                    label = { Text(t.title, maxLines = 1) },
                                    leadingIcon = { Icon(t.icon, contentDescription = null, modifier = Modifier.size(17.dp)) },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.34f),
                        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
                    ) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 14.dp)) {
                            SettingsTabBody(tab, d, edit, ctx, state, tick.intValue, hw, conflict, globalBindingConflict, overlayOk, running) { confirmReset = true }
                        }
                    }
                }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset total") },
            text = { Text("Apaga todas as configurações e o layout do HUD e volta ao padrão de fábrica. Isso é aplicado na hora.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmReset = false
                        scope.launch {
                            try {
                                viewModel.saveAndAwait(AppSettings())
                                draft = null
                                layoutReset = false
                                snack.showSnackbar("Reset total gravado")
                            } catch (t: Throwable) {
                                if (t is kotlinx.coroutines.CancellationException) throw t
                                snack.showSnackbar("Falha ao gravar o reset. As configurações anteriores foram preservadas no armazenamento.")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Resetar tudo") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SettingsTabBody(
    tab: Int,
    d: AppSettings,
    edit: Edit,
    ctx: Context,
    state: ActivationState,
    tick: Int,
    hw: Hw,
    conflict: Boolean,
    globalBindingConflict: MappingEditorPolicy.KeyBinding?,
    overlayOk: Boolean,
    running: Boolean,
    onReset: () -> Unit
) {
    when (tab) {
        0 -> PointerTab(d, edit)
        1 -> AimTab(d, edit)
        2 -> AppearanceTab(d, edit)
        3 -> DisplayTab(d, edit, hw)
        4 -> AppsTab(ctx, d, edit, state, tick)
        else -> {
            PerfTab(d, edit, hw)
            KeysTab(d, edit, conflict, globalBindingConflict)
            SystemTab(ctx, d, edit, hw, state, overlayOk, running, onReset)
        }
    }
}

// ---------------------------------------------------------------- cabeçalho

@Composable
private fun Header(hw: Hw, state: ActivationState, running: Boolean, dirty: Boolean, onBack: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 14.dp, top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("‹  Painel") }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(if (running) "●  SERVIÇO ATIVO" else "○  SERVIÇO PARADO", modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.padding(11.dp).size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text("Centro de configurações", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "${hw.name} · ${hw.res} · ${hw.hz} Hz · ${hw.dpi} dpi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Android ${Build.VERSION.RELEASE} · Shizuku ${stateLabel(state)}${if (dirty) " · ALTERAÇÕES PENDENTES" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dirty) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (hw.dex) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f), modifier = Modifier.fillMaxWidth()) {
                    Text("Modo desktop detectado · layout adaptável", modifier = Modifier.padding(horizontal = 18.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

private fun stateLabel(s: ActivationState) = when (s) {
    ActivationState.READY -> "ativo"
    ActivationState.NEEDS_PERMISSION -> "sem permissão Shizuku"
    ActivationState.INPUT_PERMISSION_MISSING -> "INJECT_EVENTS ausente"
    ActivationState.INPUT_SERVICE_UNAVAILABLE -> "InputManager indisponível"
    ActivationState.SHIZUKU_STOPPED -> "parado"
    ActivationState.SHIZUKU_MISSING -> "não instalado"
}

// ---------------------------------------------------------------- abas

@Composable
private fun PointerTab(d: AppSettings, edit: Edit) {
    Section("Velocidade do ponteiro (como no Windows)")
    PointerSpeedRow(d.pointerSpeed) { v -> edit { it.copy(pointerSpeed = v) } }
    SwitchRow(
        "Melhorar precisão do ponteiro",
        "Liga uma aceleração suave (aproximação do Windows): movimentos lentos ficam mais precisos e os rápidos aceleram. Desligado = 1:1 puro.",
        d.epp
    ) { v -> edit { it.copy(epp = v) } }

    Section("Presets")
    Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { edit { it.copy(sensX = 1f, sensY = 1f, curve = 0, pointerSpeed = 6, epp = false, lookIntervalMs = 4, deadzone = 0f) } },
            modifier = Modifier.weight(1f)
        ) { Text("Padrão 1:1") }
        OutlinedButton(
            onClick = { edit { it.copy(sensX = 1.6f, sensY = 1.6f, curve = 1, exponent = 1.4f) } },
            modifier = Modifier.weight(1f)
        ) { Text("Mira rápida") }
        OutlinedButton(
            onClick = { edit { it.copy(sensX = 0.7f, sensY = 0.7f, curve = 0) } },
            modifier = Modifier.weight(1f)
        ) { Text("Preciso") }
    }

    Section("Sensibilidade")
    SliderRow(
        "Sensibilidade global", "Multiplica todos os botões, inclusive os de mira.",
        d.globalSens, 0.2f, 3f, 0.05f, absMin = 0f, absMax = 1000f
    ) { v -> edit { it.copy(globalSens = v) } }
    SliderRow(
        "Eixo X (horizontal)", "Velocidade horizontal da câmera. 1.0 = igual ao mouse. Digite até 1000.",
        d.sensX, 0.1f, 5f, 0.05f, absMin = 0f, absMax = 1000f
    ) { v -> edit { it.copy(sensX = v) } }
    SliderRow(
        "Eixo Y (vertical)", "Velocidade vertical da câmera. Em geral um pouco menor que a horizontal.",
        d.sensY, 0.1f, 5f, 0.05f, absMin = 0f, absMax = 1000f
    ) { v -> edit { it.copy(sensY = v) } }
    SliderRow(
        "Zona morta (deadzone)", "Ignora micro-movimentos do mouse para estabilizar a mira. 0 = desligado.",
        d.deadzone, 0f, 5f, 0.1f, "%.1f", absMin = 0f, absMax = 100f
    ) { v -> edit { it.copy(deadzone = v) } }
    SwitchRow("Inverter eixo Y", "Mouse para cima = mira para baixo.", d.invertY) { v -> edit { it.copy(invertY = v) } }

    Section("Curva de resposta")
    DropdownRow(
        "Curva", "Como a velocidade do mouse vira velocidade da mira.",
        listOf(0 to "Linear (1:1)", 1 to "Exponencial", 2 to "Customizada", 3 to "Desacelerar"), d.curve
    ) { v -> edit { it.copy(curve = v) } }
    if (d.curve == 1 || d.curve == 3) {
        SliderRow("Expoente", "Intensidade da curva. Maior = efeito mais forte.", d.exponent, 1f, 3f, 0.1f, "%.1f", absMin = 1f, absMax = 10f) { v ->
            edit { it.copy(exponent = v) }
        }
    }
    if (d.curve == 2) {
        SliderRow("Joelho", "Velocidade a partir da qual o ganho muda.", d.knee, 1f, 30f, 1f, "%.0f", absMin = 1f, absMax = 200f) { v ->
            edit { it.copy(knee = v) }
        }
        SliderRow("Ganho acima do joelho", "Multiplicador depois do joelho.", d.gain, 0.5f, 3f, 0.1f, "%.1f", absMin = 0f, absMax = 20f) { v ->
            edit { it.copy(gain = v) }
        }
    }
    Section("Calibração")
    SliderRow(
        "Distância alvo (%)", "Quanto da tela um movimento de ida do mouse deve percorrer.",
        d.calibTarget * 100f, 20f, 100f, 5f, "%.0f", absMin = 5f, absMax = 100f
    ) { v -> edit { it.copy(calibTarget = v / 100f) } }
    Calibrator(d.calibTarget,
        onX = { v -> edit { it.copy(sensX = v.coerceIn(0.1f, 1000f)) } },
        onY = { v -> edit { it.copy(sensY = v.coerceIn(0.1f, 1000f)) } }
    )
}

@Composable
private fun PointerSpeedRow(level: Int, onChange: (Int) -> Unit) {
    val view = LocalView.current
    ListItem(
        headlineContent = { Text("Selecione a velocidade do ponteiro") },
        supportingContent = {
            Text("Escala de 1 a 11 do Painel de Controle do Windows. O meio (6) é o padrão de fábrica: movimento 1:1 com o mouse.")
        },
        trailingContent = { Text(PointerSpeed.label(level), style = MaterialTheme.typography.titleSmall) },
        colors = transparent
    )
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Lento", style = MaterialTheme.typography.labelSmall)
        Slider(
            value = level.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(1, 11)) },
            onValueChangeFinished = { Haptics.tick(view) },
            valueRange = 1f..11f,
            steps = 9,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        )
        Text("Rápido", style = MaterialTheme.typography.labelSmall)
    }
    TextButton(
        onClick = {
            onChange(PointerSpeed.DEFAULT)
            Haptics.tick(view)
        },
        modifier = Modifier.padding(horizontal = 8.dp)
    ) { Text("Restaurar padrão (6)") }
    HorizontalDivider()
}

@Composable
private fun Calibrator(target: Float, onX: (Float) -> Unit, onY: (Float) -> Unit) {
    val ctx = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val assistant = remember { CalibrationAssistant() }
    var cap by remember { mutableStateOf<CaptureView?>(null) }
    var msg by remember { mutableStateOf("") }
    var isCalibrating by remember { mutableStateOf(false) }

    Text(
        "Toque em um botão e, durante 4 s, faça UM movimento de ida com o mouse na distância que deve " +
            "equivaler ao alvo. O app calcula a sensibilidade e coloca no rascunho (salve para aplicar).",
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall
    )
    AndroidView(
        factory = { c ->
            CaptureView(c, { dx, dy, _, _ -> assistant.feed(dx, dy) }, { _, _ -> }, { _, _ -> }).also { cap = it }
        },
        modifier = Modifier.size(1.dp)
    )
    val captureView = cap
    DisposableEffect(captureView) {
        val viewToStop = captureView
        onDispose { runCatching { viewToStop?.stop() } }
    }

    fun run(horizontal: Boolean) {
        if (isCalibrating) return
        val v = cap
        if (v == null) {
            msg = "Captura de entrada ainda não está disponível. Feche e reabra esta tela."
            return
        }
        if (!target.isFinite() || target <= 0f) {
            msg = "Alvo de calibração inválido. Revise a configuração e tente novamente."
            return
        }
        val dh = DisplayHelper(ctx)
        val size = try {
            dh.size(dh.target())
        } catch (error: Exception) {
            msg = "Monitor indisponível: ${error.message ?: error.javaClass.simpleName}"
            return
        }
        val axisPixels = (if (horizontal) size.x else size.y).takeIf { it > 0 }
        if (axisPixels == null) {
            msg = "Não foi possível obter as dimensões do monitor selecionado."
            return
        }
        val targetPixels = axisPixels.toFloat() * target
        if (!targetPixels.isFinite() || targetPixels <= 0f) {
            msg = "A distância de calibração calculada é inválida."
            return
        }

        assistant.reset()
        msg = "Mova o mouse agora..."
        isCalibrating = true
        Haptics.tick(view)
        try {
            v.start()
        } catch (error: Exception) {
            isCalibrating = false
            msg = "Não foi possível iniciar a captura: ${error.message ?: error.javaClass.simpleName}"
            return
        }
        scope.launch {
            try {
                delay(4000)
                val suggestion = if (horizontal) assistant.suggestX(targetPixels) else assistant.suggestY(targetPixels)
                if (suggestion == null) {
                    msg = "Nenhum movimento detectado. Tente de novo."
                } else {
                    msg = "Sensibilidade sugerida: ${String.format(Locale.US, "%.2f", suggestion)}"
                    if (horizontal) onX(suggestion) else onY(suggestion)
                    Haptics.tick(view)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                msg = "Falha na calibração: ${error.message ?: error.javaClass.simpleName}"
            } finally {
                runCatching { v.stop() }
                isCalibrating = false
            }
        }
    }

    Row(
        Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = { run(true) }, enabled = !isCalibrating, modifier = Modifier.weight(1f)) {
            Text(if (isCalibrating) "Calibrando..." else "Calibrar X")
        }
        Button(onClick = { run(false) }, enabled = !isCalibrating, modifier = Modifier.weight(1f)) {
            Text(if (isCalibrating) "Calibrando..." else "Calibrar Y")
        }
    }
    if (msg.isNotEmpty()) Text(msg, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun DisplayTab(d: AppSettings, edit: Edit, hw: Hw) {
    Section("Monitor detectado")
    InfoRow("Monitor externo", if (hw.external) "Detectado" else "Não detectado")
    if (hw.external) {
        SwitchRow(
            "Usar o monitor externo (DeX)",
            "Desligado (recomendado): overlay e toques ficam na tela do aparelho. Ligue só se o jogo estiver rodando no monitor DeX.",
            d.useExternalDisplay
        ) { v -> edit { it.copy(useExternalDisplay = v) } }
    }
    InfoRow("Resolução", hw.res)
    InfoRow("Densidade", "${hw.dpi} dpi")
    InfoRow("Proporção", String.format(Locale.US, "%.2f:1", hw.ratio))
    InfoRow("Taxa de atualização", "${hw.hz} Hz")
    if (hw.dex) {
        Text(
            "Modo DeX detectado. Configurações adaptadas: posição dos toques e escala da interface são recalculadas sozinhas para este monitor.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }

    Section("Perfil Modo DeX")
    ActionRow(
        "Preset Modo DeX", "Botões menores (80%), interface maior (125%), sensibilidade global 0,85 e áreas reduzidas. Pode repetir sem acumular.",
        "Aplicar"
    ) { edit { DisplayUtils.applyDexPreset(it) } }

    Section("Área do jogo")
    DropdownRow(
        "Modo", "Onde o jogo desenha dentro do monitor. Os botões são posicionados dentro desta área.",
        listOf(0 to "Tela inteira", 1 to "Proporção fixa (barras laterais/superiores)", 2 to "Janela (modo janela do DeX)"), d.areaMode
    ) { v -> edit { it.copy(areaMode = v) } }
    if (d.areaMode == 1) {
        DropdownRow(
            "Proporção do jogo", "Se o jogo não preenche o monitor 21:9, escolha a proporção dele; a área é centralizada.",
            listOf(1.3333f to "4:3", 1.7778f to "16:9", 2f to "18:9", 2.1667f to "19,5:9", 2.2222f to "20:9", 2.3333f to "21:9"),
            nearestRatio(d.areaRatio)
        ) { v -> edit { it.copy(areaRatio = v) } }
    }
    if (d.areaMode == 2) {
        SliderRow("Posição X (%)", "Borda esquerda da janela do jogo.", d.areaX * 100f, 0f, 95f, 0.5f, "%.1f", absMin = 0f, absMax = 95f) { v ->
            edit { it.copy(areaX = v / 100f) }
        }
        SliderRow("Posição Y (%)", "Borda superior da janela do jogo.", d.areaY * 100f, 0f, 95f, 0.5f, "%.1f", absMin = 0f, absMax = 95f) { v ->
            edit { it.copy(areaY = v / 100f) }
        }
        SliderRow("Largura (%)", "Largura da janela do jogo.", d.areaW * 100f, 10f, 100f, 0.5f, "%.1f", absMin = 5f, absMax = 100f) { v ->
            edit { it.copy(areaW = v / 100f) }
        }
        SliderRow("Altura (%)", "Altura da janela do jogo.", d.areaH * 100f, 10f, 100f, 0.5f, "%.1f", absMin = 5f, absMax = 100f) { v ->
            edit { it.copy(areaH = v / 100f) }
        }
        Text(
            "No modo janela o app não consegue medir a janela do jogo sozinho: ajuste aqui até o contorno azul tracejado do HUD (modo edição) coincidir com ela.",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }

    Section("Interface")
    SliderRow(
        "Escala da interface", "Tamanho da bolha, menus e textos no overlay. Aumente em monitores grandes (DeX 4K).",
        d.uiScale, 0.5f, 3f, 0.05f, absMin = 0.5f, absMax = 4f
    ) { v -> edit { it.copy(uiScale = v) } }
}

private fun nearestRatio(r: Float): Float {
    val opts = listOf(1.3333f, 1.7778f, 2f, 2.1667f, 2.2222f, 2.3333f)
    return opts.minByOrNull { abs(it - r) } ?: 1.7778f
}

@Composable
private fun AppsTab(ctx: Context, d: AppSettings, edit: Edit, state: ActivationState, tick: Int) {
    var search by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    val apps = remember(tick) {
        runCatching {
            val pm = ctx.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(launcher, 0)
                .mapNotNull { resolve ->
                    val info = resolve.activityInfo?.applicationInfo ?: return@mapNotNull null
                    val pkg = info.packageName
                    if (pkg == ctx.packageName) return@mapNotNull null
                    val label = runCatching { pm.getApplicationLabel(info).toString() }.getOrDefault(pkg)
                    Triple(pkg, label, info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM != 0)
                }
                .distinctBy { it.first }
                .sortedBy { it.second.lowercase(Locale.getDefault()) }
        }.getOrDefault(emptyList())
    }
    Section("Ativação por aplicativo")
    SwitchRow(
        "Ativar automaticamente",
        "Liga o mapeamento quando um dos aplicativos selecionados estiver em primeiro plano. Requer detecção via Shizuku.",
        d.onlyInGame
    ) { v -> edit { it.copy(onlyInGame = v) } }
    InfoRow(
        "Detecção do aplicativo",
        if (state != ActivationState.READY) "indisponível: Shizuku não está autorizado/conectado"
        else if (MappingService.detectionSupported) "disponível" else "aguardando o serviço"
    )
    InfoRow("Último pacote detectado", MappingService.detectedPackage ?: "—")
    InfoRow("Selecionados", d.games.size.toString())
    OutlinedTextField(
        value = search,
        onValueChange = { search = it },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        label = { Text("Buscar jogo ou aplicativo") },
        singleLine = true
    )
    SwitchRow("Mostrar apps do sistema", "Inclui aplicativos do sistema que expõem um ícone de inicialização.", showSystem) { showSystem = it }
    if (apps.isEmpty()) {
        Text("Não foi possível listar aplicativos iniciáveis neste dispositivo.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
    } else {
        val filtered = apps.filter { (pkg, label, system) ->
            (showSystem || !system) && (label.contains(search, true) || pkg.contains(search, true))
        }
        if (filtered.isEmpty()) Text("Nenhum resultado para essa busca.", modifier = Modifier.padding(16.dp))
        filtered.forEach { (pkg, label, _) ->
            ListItem(
                headlineContent = { Text(label, maxLines = 1) },
                supportingContent = { Text(pkg, maxLines = 1) },
                trailingContent = {
                    Switch(
                        checked = pkg in d.games,
                        onCheckedChange = { enabled ->
                            edit { current ->
                                val next = if (enabled) (current.games + pkg).distinct() else current.games - pkg
                                current.copy(games = next)
                            }
                        }
                    )
                },
                colors = transparent
            )
            HorizontalDivider()
        }
    }
    Text(
        "Os aplicativos selecionados compartilham atualmente as configurações e o layout de controles globais. A seleção não garante que todo app aceite eventos injetados: isso depende do Android, do método de entrada e do próprio aplicativo.",
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun HudTab(d: AppSettings, edit: Edit) {
    Section("Estilo dos botões")
    DropdownRow(
        "Visual do HUD", "Clássico: círculos escuros. Neon: brilho e texto colorido. BlueStacks: quadrados translúcidos. Minimalista: só a tecla. Ícones: símbolos de tiro, mira, pulo.",
        listOf(0 to "Clássico", 1 to "Neon", 2 to "BlueStacks", 3 to "Minimalista", 4 to "Ícones"), d.hudStyle
    ) { v -> edit { it.copy(hudStyle = v) } }
    SwitchRow("Mostrar HUD durante o jogo", "Desligado: nada é desenhado (menos sobreposição). O mapeamento continua.", d.showHud) { v ->
        edit { it.copy(showHud = v) }
    }
    SliderRow("Opacidade geral (%)", "Multiplica a opacidade de todos os botões.", d.hudAlpha * 100f, 20f, 100f, 5f, "%.0f", absMin = 5f, absMax = 100f) { v ->
        edit { it.copy(hudAlpha = v / 100f) }
    }
    SliderRow("Tamanho global dos botões (%)", "Multiplica o tamanho de todos os botões de uma vez. 100 = tamanhos individuais.", d.buttonScale * 100f, 30f, 200f, 5f, "%.0f", absMin = 10f, absMax = 400f) { v ->
        edit { it.copy(buttonScale = v / 100f) }
    }

    Section("Mouse virtual e retículo")
    SwitchRow(
        "Mostrar retículo durante a captura",
        "O retículo acompanha os deltas relativos do mouse. Ao cruzar um limite do display, ele reancora no ponto de referência e continua com o excedente; a posição lógica não é limitada pelas dimensões da tela. Aparece quando a captura evdev ou Pointer Capture está ativa.",
        d.cursorVisible
    ) { v -> edit { it.copy(cursorVisible = v) } }
    DropdownRow(
        "Tipo do retículo", "Escolha o formato do indicador fixo da visão.",
        listOf(0 to "Cruz", 1 to "Anel", 2 to "Ponto", 3 to "Seta"), d.cursorType
    ) { v -> edit { it.copy(cursorType = v) } }
    DropdownRow(
        "Cor do cursor", "Escolha uma cor de alto contraste.",
        listOf(
            0xFF00E5FF.toInt() to "Ciano",
            0xFFFF3B30.toInt() to "Vermelho",
            0xFF39FF14.toInt() to "Verde",
            0xFFFFFFFF.toInt() to "Branco",
            0xFFFFD60A.toInt() to "Amarelo",
            0xFFFF4DFF.toInt() to "Magenta"
        ), d.cursorColor
    ) { v -> edit { it.copy(cursorColor = v) } }
    SliderRow(
        "Tamanho do retículo (dp)", "Ajuste o tamanho do indicador visual da visão.",
        d.cursorSize, 8f, 64f, 2f, "%.0f", absMin = 8f, absMax = 64f
    ) { v -> edit { it.copy(cursorSize = v) } }

    Section("Grade")
    DropdownRow(
        "Encaixe na grade", "Ao arrastar um botão no modo edição, ele encaixa nas divisões da área do jogo.",
        listOf(0 to "Desligada", 8 to "8 x 8", 10 to "10 x 10", 16 to "16 x 16", 20 to "20 x 20", 32 to "32 x 32"), d.gridSnap
    ) { v -> edit { it.copy(gridSnap = v) } }

    Section("Áreas")
    SliderRow("Raio do joystick (WASD)", "Fração do menor lado da área do jogo.", d.joyR, 0.03f, 0.3f, 0.01f, absMin = 0.01f, absMax = 0.5f) { v ->
        edit { it.copy(joyR = v) }
    }
    Text(
        "Posição, tamanho, opacidade, tipo e teclas de cada botão: use \"Editar HUD\" (painel inicial) ou a bolha de engrenagem, e toque na engrenagem do botão.",
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun AppearanceTab(d: AppSettings, edit: Edit) {
    HudTab(d, edit)
    Section("Tema e interface")
    DropdownRow(
        "Tema do aplicativo", "Cores da interface do app.",
        listOf(0 to "Escuro padrão", 1 to "AMOLED (preto)", 2 to "Vermelho (MSI)", 3 to "Verde (BlueStacks)", 4 to "Azul"), d.theme
    ) { v -> edit { it.copy(theme = v) } }
    SliderRow(
        "Escala da interface", "Tamanho da bolha, menus e textos no overlay. Aumente em monitores grandes (DeX 4K).",
        d.uiScale, 0.5f, 3f, 0.05f, absMin = 0.5f, absMax = 4f
    ) { v -> edit { it.copy(uiScale = v) } }
    SwitchRow("Feedback tátil", "Vibração curta ao confirmar uma mudança.", d.haptics) { v -> edit { it.copy(haptics = v) } }
}

@Composable
private fun AimTab(d: AppSettings, edit: Edit) {
    Section("Mira e câmera")
    SliderRow(
        "Sensibilidade global", "Multiplica todos os botões, inclusive os de mira. 1.0 = sem mudança.",
        d.globalSens, 0.2f, 3f, 0.05f, absMin = 0f, absMax = 1000f
    ) { v -> edit { it.copy(globalSens = v) } }
    SliderRow(
        "Tamanho da área de câmera", "Define o raio visual e de interação da zona Sight no HUD/editor. Não altera o limite físico de injeção nem o intervalo de recentralização do toque.",
        d.lookR, 0.05f, 0.9f, 0.01f, absMin = 0.02f, absMax = 1f
    ) { v -> edit { it.copy(lookR = v) } }
    SliderRow(
        "Agrupamento da câmera (ms)", "Cada amostra é transformada pela sensibilidade usando seu timestamp antes da agregação. Este intervalo agrupa somente a saída já calibrada; o injetor limita movimentos a cerca de 120 Hz. Valores menores não garantem 250 Hz.",
        d.lookIntervalMs.toFloat(), 1f, 16f, 1f, "%.0f", absMin = 1f, absMax = 20f
    ) { v -> edit { it.copy(lookIntervalMs = v.toInt()) } }
    val continuousLookEnabled = d.lookMode != Rot.OFF
    val selectedContinuousMode = when {
        d.relativeMouse || d.lookMode == Rot.INFINITE -> Rot.INFINITE
        d.lookMode == Rot.NONSTOP -> Rot.NONSTOP
        else -> Rot.AUTO // CENTER é um valor legado com a mesma política segura.
    }
    SwitchRow(
        "Movimento contínuo da câmera",
        "Define o padrão global. Cada controle Sight/Aim pode sobrescrever a estratégia nas propriedades. Desligado, o contato fica limitado à área.",
        continuousLookEnabled
    ) { enabled -> edit {
        it.copy(
            lookMode = if (enabled) {
                if (it.lookMode == Rot.OFF) Rot.AUTO else it.lookMode
            } else Rot.OFF,
            relativeMouse = false
        )
    } }
    if (continuousLookEnabled) {
        DropdownRow(
            "Modo de recentralização",
            "Automático usa UP/DOWN e oferece maior compatibilidade. Nonstop mantém pelo menos um contato ativo: cria o novo ponteiro antes de soltar o antigo; alguns jogos interpretam isso como multitouch. Relativo nativo não injeta toques, mas só funciona se o destino aceitar SOURCE_MOUSE_RELATIVE.",
            listOf(
                Rot.AUTO to "Automático (maior compatibilidade)",
                Rot.NONSTOP to "Nonstop (handover sem zero contatos)",
                Rot.INFINITE to "Mouse relativo nativo (sem reancoragem)"
            ),
            selectedContinuousMode
        ) { v -> edit { it.copy(lookMode = v, relativeMouse = false) } }
        Text(
            when (selectedContinuousMode) {
                Rot.NONSTOP -> "Nonstop: o motor envia POINTER_DOWN no novo ponto antes de POINTER_UP no antigo. Não há intervalo sem contato, mas o jogo recebe temporariamente dois ponteiros; teste a compatibilidade do jogo."
                Rot.INFINITE -> "Relativo nativo: envia SOURCE_MOUSE_RELATIVE sem coordenadas absolutas de tela. Não depende de recentralização, mas o jogo precisa aceitar essa origem de mouse."
                else -> "Automático: ao atingir a margem, move o contato até a borda, envia UP e cria outro DOWN no ponto de referência. Preserva o excedente, mas alguns jogos podem perceber a transição."
            },
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall
        )
    } else {
        Text(
            "Limite ativo: a visão para na margem da área. Ative o movimento contínuo para usar recentralização ou entrada relativa.",
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall
        )
    }
    if (continuousLookEnabled && selectedContinuousMode != Rot.INFINITE) {
        SliderRow(
            "Manter contato da câmera após inatividade (ms)",
            "Padrão 0: mantém o contato até desativar ou pausar, evitando recomeçar o gesto após pequenas pausas. Valores positivos liberam o toque depois desse tempo sem movimento; o próximo movimento inicia um novo contato no ponto de referência.",
            d.lookIdleMs.toFloat(), 0f, 3000f, 100f, "%.0f", absMin = 0f, absMax = 60000f
        ) { v -> edit { it.copy(lookIdleMs = v.toInt()) } }
    }
    Section("Botões de mira")
    val aims = d.buttons.filter { it.kind == Kind.AIM }
    if (aims.isEmpty()) {
        Text(
            "Nenhum botão de mira. No modo edição, toque em + e mude o tipo do botão para Mira.",
            modifier = Modifier.padding(16.dp)
        )
    }
    aims.forEach { b ->
        InfoRow(
            "${b.displayName()} (${keyLabel(b.key)})",
            "X ${(b.aimSensX * 100).roundToInt()}% / Y ${(b.aimSensY * 100).roundToInt()}% • " +
                (if (b.aimToggle) "suspender" else "segurar")
        )
    }
    Text(
        "Sensibilidade X/Y, suspender (toggle) e deadzone de cada mira ficam na engrenagem do botão, no jogo (modo edição).",
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun PerfTab(d: AppSettings, edit: Edit, hw: Hw) {
    val low = hw.ramGb < 4
    val delay = when (d.perfMode) {
        1 -> 0
        2 -> 5
        else -> if (low) 5 else 0
    }
    Section("Taxa de atualização")
    DropdownRow(
        "Taxa do overlay", "Pede ao sistema esta taxa para a janela do HUD. O sistema pode limitar.",
        listOf(0 to "Automático (máximo)") + hw.rates.map { it to "$it Hz" }, d.refreshHz
    ) { v -> edit { it.copy(refreshHz = v) } }
    InfoRow("Taxas suportadas", hw.rates.joinToString(" / ") { "$it Hz" }.ifEmpty { "—" })

    Section("Modo de desempenho")
    DropdownRow(
        "Modo", "Atualiza a folga do motor sem alterar a sensibilidade ou a ordem dos eventos.",
        listOf(0 to "Equilibrado", 1 to "Alta performance", 2 to "Economia"), d.perfMode
    ) { v -> edit { it.copy(perfMode = v) } }
    InfoRow("Folga efetiva do motor", "$delay ms adicionais")
    InfoRow("Prioridade da injeção", "URGENT_DISPLAY (máxima)")
    InfoRow("RAM detectada", "${hw.ramGb} GB" + if (low && d.perfMode == 0) " (folga automática de 5 ms)" else "")
    Text(
        "Alta performance remove a folga mesmo em aparelhos fracos; use se não houver travamentos. " +
            "Economia mantém 5 ms de folga sempre. O desenho do overlay usa aceleração por GPU.",
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
private fun KeysTab(
    d: AppSettings, edit: Edit, conflict: Boolean, shortcutConflict: MappingEditorPolicy.KeyBinding?
) {
    val fKeys = (0..11).map { (KeyEvent.KEYCODE_F1 + it) to "F${it + 1}" }
    // Inclui o valor atual se veio de um perfil antigo (o padrão de pausa é NUM3, não F1-F12).
    // Sem isso, DropdownRow recebia um valor selected que não existia nas opções e ficava ambíguo.
    val editKeyOptions = (listOf(d.editKey to keyLabel(d.editKey)) + fKeys.filterNot { it.first == d.editKey })
    val pauseKeyOptions = (listOf(d.pauseKey to keyLabel(d.pauseKey)) + fKeys.filterNot { it.first == d.pauseKey })
    Section("Atalhos globais")
    DropdownRow("Editar HUD", "Liga/desliga o modo de edição.", editKeyOptions, d.editKey) { v -> edit { it.copy(editKey = v) } }
    DropdownRow("Pausar mapeamento", "Libera o mouse e os toques.", pauseKeyOptions, d.pauseKey) { v -> edit { it.copy(pauseKey = v) } }
    if (conflict) {
        val conflictMessage = if (d.editKey == d.pauseKey) {
            "Os dois atalhos globais não podem usar a mesma tecla."
        } else {
            val binding = requireNotNull(shortcutConflict)
            "${keyLabel(binding.code)} está atribuída a '${binding.label}' em um perfil. " +
                "Troque o atalho global ou a associação desse controle."
        }
        Text(
            conflictMessage,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
    }
    Section("Mapeamentos atuais")
    InfoRow("Movimento", "W A S D")
    InfoRow("Câmera", "Mouse")
    d.buttons.forEach { b ->
        val kind = when {
            b.kind == Kind.AIM -> if (b.aimToggle) "mira (suspender)" else "mira (segurar)"
            b.kind == Kind.FIRE -> if (b.mode == Mode.TAP) "tiro (toque único)" else "tiro (segurar contínuo)"
            b.macro.isNotEmpty() -> "padrão (macro: ${b.macro.size} botões)"
            b.mode == Mode.TOGGLE -> "padrão (toggle)"
            b.mode == Mode.TAP -> "padrão (toque)"
            else -> "padrão (segurar)"
        }
        InfoRow("${b.displayName()} (${keyLabel(b.key)})", kind)
    }
}

@Composable
private fun SystemTab(
    ctx: Context, d: AppSettings, edit: Edit, hw: Hw, state: ActivationState,
    overlayOk: Boolean, running: Boolean, onReset: () -> Unit
) {
    val version = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (e: Exception) {
        "?"
    }
    Section("Diagnóstico de tela preta")
    Text(
        "Se o jogo ficar preto ao ligar o mapeamento, teste desligando uma opção por vez (o serviço reconstrói as janelas na hora):",
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall
    )
    SwitchRow(
        "Janela de captura (mouse e teclado)",
        "Janela 1x1 focável que recebe teclado e solicita Pointer Capture. Ao ativá-la, ela toma o foco da janela; o jogo pode perder foco, pausar ou apresentar tela preta. Pointer Capture é restrito à janela solicitante, não é captura global. Desative se o jogo não tolerar essa troca de foco.",
        d.captureWindow
    ) { v -> edit { it.copy(captureWindow = v) } }
    SwitchRow(
        "Janela do HUD (tela cheia transparente)",
        "Desenha os botões sobre o jogo. Desligada, só ficam a bolha e a captura.",
        d.hudWindow
    ) { v -> edit { it.copy(hudWindow = v) } }
    SwitchRow(
        "Modo compatível (sem aceleração por GPU)",
        "Desenha as janelas por software, evitando conflito de GPU com o jogo.",
        d.safeOverlay
    ) { v -> edit { it.copy(safeOverlay = v) } }

    Section("Inicialização")
    SwitchRow("Iniciar ao ligar o celular", "Sobe o serviço depois do boot.", d.autoStart) { v -> edit { it.copy(autoStart = v) } }

    Section("Diagnóstico")
    val inputDevices = InputDeviceManager.snapshot()
    InfoRow("Estado do mapeamento", InputDiagnostics.healthHint(inputDevices.hasMouse))
    InfoRow("Entrada recente", InputDiagnostics.summary())
    InfoRow("Periféricos reconhecidos", inputDevices.summary())
    InfoRow("Teclado físico", if (inputDevices.hasPhysicalKeyboard) "detectado" else "não detectado")
    InfoRow("Mouse físico", if (inputDevices.hasMouse) "detectado" else "não detectado")
    InfoRow("Ambiente de tela", when { hw.dex -> "modo desktop reportado pelo Android"; hw.external -> "monitor externo; DeX não confirmado"; else -> "tela do dispositivo" })
    InfoRow("Versão do app", version)
    InfoRow("Aparelho", hw.name)
    InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    InfoRow("Camada privilegiada", stateLabel(state))
    ActionRow("Compartilhar Configurações", "Exporta as configurações atuais em JSON", "Compartilhar") {
        shareText(
            ctx,
            "KeyMapper — Configurações",
            d.toJson(),
            "Compartilhar configurações"
        )
    }
    ActionRow("Compartilhar Log de Erro", "Modelo, Android, Shizuku, serviço e último crash", "Compartilhar") {
        val text = buildString {
            appendLine("RELATÓRIO DE DIAGNÓSTICO — KeyMapper $version")
            appendLine("Aparelho: ${hw.name}")
            appendLine("Fabricante: ${Build.MANUFACTURER}")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Tela: ${hw.res} @ ${hw.dpi} dpi, ${hw.hz} Hz | RAM: ${hw.ramGb} GB | modo desktop: ${hw.dex} | monitor externo: ${hw.external}")
            appendLine("Periféricos: ${inputDevices.summary()}")
            appendLine("Shizuku: ${stateLabel(state)} | Overlay permitido: $overlayOk | Serviço ativo: $running")
            appendLine("App version: $version")
            appendLine()
            appendLine("Estado do mapeamento: ${InputDiagnostics.healthHint(inputDevices.hasMouse)}")
            appendLine(InputDiagnostics.report())
            appendLine()
            appendLine("ÚLTIMO CRASH REGISTRADO")
            appendLine(CrashReporter.lastCrash(ctx))
            appendLine()
            appendLine("HISTÓRICO DE ENCERRAMENTO REGISTRADO PELO ANDROID")
            appendLine(CrashReporter.processExitHistory(ctx))
            appendLine()
            appendLine("DIAGNÓSTICO DE REINICIALIZAÇÃO")
            appendLine(RestartDiagnostics.report(ctx))
        }
        shareText(ctx, "KeyMapper — Relatório de erro", text, "Compartilhar relatório de erro")
    }

    Section("Zona de perigo")
    Button(
        onClick = onReset,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) { Text("Reset total") }
}

// ---------------------------------------------------------------- componentes

private fun shareText(ctx: Context, subject: String, text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }
    try {
        ctx.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(ctx, "Não foi possível abrir o menu de compartilhamento.", Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun Section(title: String) {
    Row(
        Modifier.fillMaxWidth().padding(start = 3.dp, top = 20.dp, end = 3.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Surface(
            modifier = Modifier.width(4.dp).height(18.dp),
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(50)
        ) {}
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private val transparent @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun SettingsRowSurface(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f))
    ) { content() }
}

@Composable
private fun InfoRow(title: String, value: String) {
    SettingsRowSurface {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            trailingContent = { Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) },
            colors = transparent
        )
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val view = LocalView.current
    SettingsRowSurface {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = {
                Switch(checked = checked, onCheckedChange = {
                    Haptics.tick(view)
                    onChange(it)
                })
            },
            colors = transparent
        )
    }
}

@Composable
private fun ActionRow(title: String, sub: String, label: String, onClick: () -> Unit) {
    val view = LocalView.current
    SettingsRowSurface {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = {
                OutlinedButton(onClick = {
                    Haptics.tick(view)
                    onClick()
                }) { Text(label) }
            },
            colors = transparent
        )
    }
}

@Composable
private fun <T> DropdownRow(
    title: String, sub: String, options: List<Pair<T, String>>, selected: T,
    placeholder: String = "?", onSelect: (T) -> Unit
) {
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    SettingsRowSurface {
        ListItem(
            headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
            supportingContent = { Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = {
                Box {
                    TextButton(onClick = { open = true }) {
                        Text(options.firstOrNull { it.first == selected }?.second ?: placeholder, maxLines = 2)
                    }
                    DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                        options.forEach { (value, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                Haptics.tick(view)
                                onSelect(value)
                                open = false
                            })
                        }
                    }
                }
            },
            colors = transparent
        )
    }
}

/** Slider com [−] [+] e campo numérico: digite o valor exato (até [absMax], além do alcance do slider). */
@Composable
private fun SliderRow(
    title: String, sub: String, value: Float, min: Float, max: Float, step: Float,
    fmt: String = "%.2f", absMin: Float = min, absMax: Float = max, onChange: (Float) -> Unit
) {
    val view = LocalView.current
    var text by remember { mutableStateOf(String.format(Locale.US, fmt, value)) }
    LaunchedEffect(value) {
        val p = text.replace(',', '.').toFloatOrNull()
        if (p == null || abs(p - value) > 0.001f) text = String.format(Locale.US, fmt, value)
    }
    SettingsRowSurface {
        Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
            ListItem(
                headlineContent = { Text(title, style = MaterialTheme.typography.bodyLarge) },
                supportingContent = { Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                trailingContent = {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { t ->
                            text = t
                            t.replace(',', '.').toFloatOrNull()?.let { onChange(it.coerceIn(absMin, absMax)) }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.width(104.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                },
                colors = transparent
            )
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        onChange((value - step).coerceIn(absMin, absMax))
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(36.dp)
                ) { Text("−") }
                Slider(
                    value = value.coerceIn(min, max),
                    onValueChange = { onChange(((it / step).roundToInt() * step).coerceIn(absMin, absMax)) },
                    onValueChangeFinished = { Haptics.tick(view) },
                    valueRange = min..max,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                OutlinedButton(
                    onClick = {
                        Haptics.tick(view)
                        onChange((value + step).coerceIn(absMin, absMax))
                    },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.size(36.dp)
                ) { Text("+") }
            }
        }
    }
}
