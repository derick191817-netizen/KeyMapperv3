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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import com.example.keymapper.data.DEFAULT_GAMES
import com.example.keymapper.data.Kind
import com.example.keymapper.data.displayName
import com.example.keymapper.data.Mode
import com.example.keymapper.data.Rot
import com.example.keymapper.presentation.SettingsViewModel
import com.example.keymapper.diagnostics.CrashReporter
import com.example.keymapper.diagnostics.InputDiagnostics
import com.example.keymapper.data.keyLabel
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.display.DisplayUtils
import com.example.keymapper.injection.Activation
import com.example.keymapper.injection.ActivationState
import com.example.keymapper.input.CalibrationAssistant
import com.example.keymapper.input.CaptureView
import com.example.keymapper.input.PointerSpeed
import com.example.keymapper.service.MappingService
import com.example.keymapper.util.Haptics
import kotlinx.coroutines.Dispatchers
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
    TabDef("Jogos", Icons.Filled.Menu),
    TabDef("Sistema", Icons.Filled.Settings)
)

private data class Hw(
    val name: String, val hz: Int, val ramGb: Int, val res: String, val dpi: Int,
    val rates: List<Int>, val dex: Boolean, val ratio: Float, val external: Boolean
)

private fun readHw(ctx: Context): Hw {
    val dh = DisplayHelper(ctx)
    val ext = dh.external()
    val disp = dh.target()
    val inf = dh.info(disp)
    val rates = dh.rates(disp)
    val mi = ActivityManager.MemoryInfo()
    (ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
    val gb = (mi.totalMem / (1024.0 * 1024.0 * 1024.0)).roundToInt()
    val name = Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL
    return Hw(
        name, rates.maxOrNull() ?: disp.refreshRate.roundToInt(), gb,
        "${inf.width}x${inf.height}", inf.densityDpi, rates,
        DisplayUtils.isDex(ctx, inf) || ext != null, inf.ratio, ext != null
    )
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
    var tab by remember(initialTab) { mutableIntStateOf(initialTab) }
    var confirmReset by remember { mutableStateOf(false) }

    val hw = remember(tick.intValue) { readHw(ctx) }
    var state by remember { mutableStateOf(ActivationState.SHIZUKU_STOPPED) }
    LaunchedEffect(tick.intValue) { state = withContext(Dispatchers.IO) { Activation.state(ctx) } }
    val running = remember(tick.intValue) { MappingService.running }
    val overlayOk = remember(tick.intValue) { Settings.canDrawOverlays(ctx) }

    val d = draft ?: stored
    val conflict = d.editKey == d.pauseKey
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
            viewModel.save(out)
            draft = null
            layoutReset = false
            Haptics.tick(view)
            val r = snack.showSnackbar("Configurações salvas", "Desfazer", duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) viewModel.save(prev)
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
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { restoreDefaults() }, modifier = Modifier.weight(1.3f)) {
                        Text("Restaurar padrões")
                    }
                    OutlinedButton(onClick = { cancel() }, enabled = dirty, modifier = Modifier.weight(1f)) {
                        Text("Cancelar")
                    }
                    Button(onClick = { save() }, enabled = dirty && !conflict, modifier = Modifier.weight(1f)) {
                        Text("Salvar")
                    }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // Navegação horizontal: mantém os controles legíveis em telas estreitas e no DeX.
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TABS.forEachIndexed { i, t ->
                    NavigationRailItem(
                        selected = tab == i,
                        onClick = {
                            tab = i
                            Haptics.tick(view)
                        },
                        icon = { Icon(t.icon, contentDescription = t.title, modifier = Modifier.size(22.dp)) },
                        label = { Text(t.title, style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                        alwaysShowLabel = true
                    )
                }
            }
            HorizontalDivider()
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp)) {
                when (tab) {
                    0 -> PointerTab(d, edit)
                    1 -> AimTab(d, edit)
                    2 -> AppearanceTab(d, edit)
                    3 -> DisplayTab(d, edit, hw)
                    4 -> GamesTab(ctx, d, edit, state, tick.intValue)
                    else -> {
                        PerfTab(d, edit, hw)
                        KeysTab(d, edit, conflict)
                        SystemTab(ctx, d, edit, hw, state, overlayOk, running) { confirmReset = true }
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
                            viewModel.save(AppSettings())
                            draft = null
                            layoutReset = false
                            snack.showSnackbar("Reset total concluído")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Resetar tudo") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } }
        )
    }
}

// ---------------------------------------------------------------- cabeçalho

@Composable
private fun Header(hw: Hw, state: ActivationState, running: Boolean, dirty: Boolean, onBack: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp)) { Text("‹ Voltar ao painel") }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    "${hw.name} | ${hw.hz}Hz | ${hw.ramGb}GB RAM",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Tela ${hw.res} @ ${hw.dpi} dpi • Android ${Build.VERSION.RELEASE} • Shizuku: ${stateLabel(state)} • Serviço: ${if (running) "ligado" else "desligado"}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (dirty) {
                    Text(
                        "Alterações não salvas",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            if (hw.dex) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Modo DeX detectado. Configurações adaptadas",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

private fun stateLabel(s: ActivationState) = when (s) {
    ActivationState.READY -> "ativo"
    ActivationState.NEEDS_PERMISSION -> "sem permissão"
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
    SliderRow(
        "Soltar toque quando o mouse parar (ms)", "De 0 a 100 ms (padrão 0). Valores baixos soltam o dedo virtual mais rápido.",
        d.releaseDelayMs.toFloat(), 0f, 100f, 1f, "%.0f", absMin = 0f, absMax = 100f
    ) { v -> edit { it.copy(releaseDelayMs = v.toInt()) } }

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

    Text(
        "Toque em um botão e, durante 4 s, faça UM movimento de ida com o mouse na distância que deve " +
            "equivaler ao alvo. O app calcula a sensibilidade e coloca no rascunho (salve para aplicar).",
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall
    )
    AndroidView(
        factory = { c ->
            CaptureView(c, { dx, dy -> assistant.feed(dx, dy) }, { _, _ -> }, { _, _ -> }).also { cap = it }
        },
        modifier = Modifier.size(1.dp)
    )

    fun run(horizontal: Boolean) {
        val v = cap ?: return
        val dh = DisplayHelper(ctx)
        val size = dh.size(dh.target())
        assistant.reset()
        msg = "Mova o mouse agora..."
        Haptics.tick(view)
        v.start()
        scope.launch {
            delay(4000)
            v.stop()
            val px = (if (horizontal) size.x else size.y) * target
            val s = if (horizontal) assistant.suggestX(px) else assistant.suggestY(px)
            if (s == null) {
                msg = "Nenhum movimento detectado. Tente de novo."
            } else {
                msg = "Sensibilidade sugerida: ${String.format(Locale.US, "%.2f", s)}"
                if (horizontal) onX(s) else onY(s)
                Haptics.tick(view)
            }
        }
    }

    Row(
        Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(onClick = { run(true) }, modifier = Modifier.weight(1f)) { Text("Calibrar X") }
        Button(onClick = { run(false) }, modifier = Modifier.weight(1f)) { Text("Calibrar Y") }
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
private fun GamesTab(ctx: Context, d: AppSettings, edit: Edit, state: ActivationState, tick: Int) {
    val pm = ctx.packageManager
    fun appLabel(pkg: String): String = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg
    }

    var picker by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf("") }

    Section("Ativação automática")
    SwitchRow(
        "Ligar e desligar sozinho",
        "O mapeamento liga quando um dos apps abaixo abrir e desliga quando você sair dele.",
        d.onlyInGame
    ) { v -> edit { it.copy(onlyInGame = v) } }
    InfoRow(
        "Detecção do app aberto",
        if (state != ActivationState.READY) "indisponível (sem Shizuku)"
        else if (MappingService.detectionSupported) "funcionando" else "aguardando"
    )
    InfoRow("Último app detectado", MappingService.detectedPackage ?: "—")

    Section("Apps selecionados (um botão Iniciar para cada)")
    if (d.games.isEmpty()) {
        Text("Nenhum app selecionado.", modifier = Modifier.padding(16.dp))
    }
    d.games.forEach { pkg ->
        ListItem(
            headlineContent = { Text(appLabel(pkg)) },
            supportingContent = { Text(pkg) },
            trailingContent = {
                TextButton(onClick = { edit { it.copy(games = it.games - pkg) } }) { Text("Remover") }
            },
            colors = transparent
        )
        HorizontalDivider()
    }

    Section("Adicionar")
    Button(
        onClick = { picker = true },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(52.dp)
    ) { Text("Escolher entre todos os apps instalados") }
    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { edit { it.copy(games = (it.games + "com.dts.freefireth").distinct()) } },
            modifier = Modifier.weight(1f)
        ) { Text("Free Fire") }
        OutlinedButton(
            onClick = { edit { it.copy(games = (it.games + "com.dts.freefiremax").distinct()) } },
            modifier = Modifier.weight(1f)
        ) { Text("Free Fire MAX") }
    }
    ListItem(
        headlineContent = { Text("Pacote manual") },
        supportingContent = { Text("Se algum app não aparecer, digite o nome do pacote (ex.: com.empresa.jogo).") },
        colors = transparent
    )
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = manual,
            onValueChange = { manual = it.trim() },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = {
                if (manual.contains('.')) {
                    val pkg = manual
                    edit { it.copy(games = (it.games + pkg).distinct()) }
                    manual = ""
                }
            },
            modifier = Modifier.padding(start = 8.dp)
        ) { Text("Adicionar") }
    }

    if (picker) {
        AppPickerDialog(
            ctx = ctx,
            tick = tick,
            selected = d.games,
            onToggle = { pkg ->
                edit { cur ->
                    cur.copy(games = if (cur.games.contains(pkg)) cur.games - pkg else cur.games + pkg)
                }
            },
            onDismiss = { picker = false }
        )
    }
}

/** Todos os apps com ícone na tela inicial (precisa de QUERY_ALL_PACKAGES no Android 11+). */
private fun loadApps(ctx: Context): List<Pair<String, String>> {
    val pm = ctx.packageManager
    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val found = try {
        pm.queryIntentActivities(q, 0)
    } catch (e: Exception) {
        emptyList()
    }
    return found.map { it.activityInfo.packageName }
        .distinct()
        .filter { it != ctx.packageName }
        .map { pkg ->
            pkg to (try {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
            } catch (e: Exception) {
                pkg
            })
        }
        .sortedBy { it.second.lowercase() }
}

@Composable
private fun AppPickerDialog(
    ctx: Context, tick: Int, selected: List<String>, onToggle: (String) -> Unit, onDismiss: () -> Unit
) {
    val apps = remember(tick) { loadApps(ctx) }
    var q by remember { mutableStateOf("") }
    val filtered = remember(q, apps) {
        if (q.isBlank()) apps
        else apps.filter { it.second.contains(q, ignoreCase = true) || it.first.contains(q, ignoreCase = true) }
    }
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), tonalElevation = 6.dp) {
            Column(Modifier.padding(16.dp).fillMaxWidth().height(540.dp)) {
                Text("Escolher apps (${apps.size} instalados)", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = q,
                    onValueChange = { q = it },
                    label = { Text("Buscar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
                LazyColumn(Modifier.weight(1f)) {
                    items(filtered, key = { it.first }) { app ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onToggle(app.first) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = selected.contains(app.first), onCheckedChange = { onToggle(app.first) })
                            Column(Modifier.weight(1f)) {
                                Text(app.second, style = MaterialTheme.typography.bodyLarge)
                                Text(app.first, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("Concluir") }
            }
        }
    }
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

    Section("Cursor virtual")
    SwitchRow(
        "Mostrar cursor virtual",
        "Exibe um indicador visual que acompanha o movimento do mouse durante a captura. Ele não recebe toques e não bloqueia o jogo.",
        d.cursorVisible
    ) { v -> edit { it.copy(cursorVisible = v) } }
    DropdownRow(
        "Tipo do cursor", "Escolha o formato do indicador.",
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
        "Tamanho do cursor (dp)", "Ajuste o tamanho do indicador visual.",
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
        "Raio da área de câmera", "Apenas visual: tamanho do círculo OLHAR no modo edição. O giro usa os limites reais da tela.",
        d.lookR, 0.05f, 0.9f, 0.01f, absMin = 0.02f, absMax = 1f
    ) { v -> edit { it.copy(lookR = v) } }
    SliderRow(
        "Atualização da câmera (ms)", "Intervalo fixo entre os toques enviados ao jogo. Menor = mais suave e responsivo (4 ms ≈ 250 Hz, 8 ms ≈ 120 Hz). Aumente só se o aparelho engasgar.",
        d.lookIntervalMs.toFloat(), 1f, 16f, 1f, "%.0f", absMin = 1f, absMax = 20f
    ) { v -> edit { it.copy(lookIntervalMs = v.toInt()) } }
    DropdownRow(
        "Modo de rotação da mira",
        "Como a câmera continua girando quando o mouse passa da borda. Cada botão de mira pode ter o seu (na engrenagem dele).",
        listOf(
            Rot.AUTO to "Automático (Infinito / Centro)",
            Rot.CENTER to "Somente Centro (jogos 2D/incompatíveis)",
            Rot.INFINITE to "Somente Infinito (jogos 3D)",
            Rot.OFF to "Desligado (para na borda)"
        ),
        d.lookMode
    ) { v -> edit { it.copy(lookMode = v) } }
    Text(
        when (d.lookMode) {
            Rot.AUTO -> "Automático: Infinito em Free Fire, PUBG e Call of Duty Mobile; Centro em qualquer outro app."
            Rot.INFINITE -> "Infinito: o cursor virtual continua acumulando o movimento, mas o toque injetado permanece dentro da tela e é reancorado na borda para evitar coordenadas inválidas."
            Rot.OFF -> "Desligado: a câmera para na borda da tela."
            else -> "Centro (padrão, seguro): ao passar da borda, solta e toca no centro na mesma hora, sem pausa."
        },
        modifier = Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodySmall
    )
    if (d.lookMode == Rot.INFINITE || d.lookMode == Rot.AUTO) {
        SliderRow(
            "Soltar o dedo da câmera após inatividade (ms)",
            "Cursor virtual: 0 mantém o gesto até soltar a mira ou pausar; valores positivos reancoram após inatividade.",
            d.lookIdleMs.toFloat(), 0f, 3000f, 100f, "%.0f", absMin = 0f, absMax = 60000f
        ) { v -> edit { it.copy(lookIdleMs = v.toInt()) } }
    }
    SwitchRow(
        "Mouse relativo nativo (experimental)",
        "Injeta o movimento como mouse (REL_X/REL_Y) em vez de arrasto de toque. Só funciona em jogos com suporte nativo a mouse; em jogos de toque, a câmera não se mexe.",
        d.relativeMouse
    ) { v -> edit { it.copy(relativeMouse = v) } }
    SliderRow(
        "Soltar toque quando o mouse parar (ms)", "De 0 a 100 ms (padrão 0). Valores baixos soltam o dedo virtual mais rápido.",
        d.releaseDelayMs.toFloat(), 0f, 100f, 1f, "%.0f", absMin = 0f, absMax = 100f
    ) { v -> edit { it.copy(releaseDelayMs = v.toInt()) } }

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
        "Modo", "Aplica ao reiniciar o serviço.",
        listOf(0 to "Equilibrado", 1 to "Alta performance", 2 to "Economia"), d.perfMode
    ) { v -> edit { it.copy(perfMode = v) } }
    InfoRow("Folga entre eventos", "$delay ms")
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
private fun KeysTab(d: AppSettings, edit: Edit, conflict: Boolean) {
    val fKeys = (0..11).map { (KeyEvent.KEYCODE_F1 + it) to "F${it + 1}" }
    Section("Atalhos globais")
    DropdownRow("Editar HUD", "Liga/desliga o modo de edição.", fKeys, d.editKey) { v -> edit { it.copy(editKey = v) } }
    DropdownRow("Pausar mapeamento", "Libera o mouse e os toques.", fKeys, d.pauseKey) { v -> edit { it.copy(pauseKey = v) } }
    if (conflict) {
        Text(
            "Os dois atalhos não podem ser a mesma tecla.",
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
        "Janela de 1 pixel que recebe o teclado e o mouse. Ela precisa de foco, e o foco sai do jogo (qualquer janela focável tira o foco, não importa o tamanho). Se desligar e a tela preta sumir, a causa é a perda de foco; sem ela só o toque na tela funciona.",
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
    InfoRow("Entrada recente", InputDiagnostics.summary())
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
            appendLine("Tela: ${hw.res} @ ${hw.dpi} dpi, ${hw.hz} Hz | RAM: ${hw.ramGb} GB | DeX: ${hw.dex}")
            appendLine("Shizuku: ${stateLabel(state)} | Overlay permitido: $overlayOk | Serviço ativo: $running")
            appendLine("App version: $version")
            appendLine()
            appendLine(InputDiagnostics.report())
            appendLine()
            appendLine("ÚLTIMO CRASH REGISTRADO")
            appendLine(CrashReporter.lastCrash(ctx))
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
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 4.dp)
    )
}

private val transparent @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun InfoRow(title: String, value: String) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Text(value, style = MaterialTheme.typography.bodyMedium) },
        colors = transparent
    )
    HorizontalDivider()
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val view = LocalView.current
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = {
                Haptics.tick(view)
                onChange(it)
            })
        },
        colors = transparent
    )
    HorizontalDivider()
}

@Composable
private fun ActionRow(title: String, sub: String, label: String, onClick: () -> Unit) {
    val view = LocalView.current
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = {
            OutlinedButton(onClick = {
                Haptics.tick(view)
                onClick()
            }) { Text(label) }
        },
        colors = transparent
    )
    HorizontalDivider()
}

@Composable
private fun <T> DropdownRow(
    title: String, sub: String, options: List<Pair<T, String>>, selected: T,
    placeholder: String = "?", onSelect: (T) -> Unit
) {
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
        trailingContent = {
            Box {
                TextButton(onClick = { open = true }) {
                    Text(options.firstOrNull { it.first == selected }?.second ?: placeholder)
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
    HorizontalDivider()
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
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(sub) },
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
                modifier = Modifier.width(104.dp)
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
        ) { Text("-") }
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
    HorizontalDivider()
}
