@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.keymapper.ui

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.example.keymapper.data.AppProfile
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.Mode
import com.example.keymapper.data.keyLabel
import com.example.keymapper.presentation.SettingsViewModel
import kotlin.math.min
import kotlin.math.log10
import kotlin.math.pow

private data class EditorTool(val kind: Int, val title: String, val description: String)

@Composable
fun MappingEditorScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val selectedPackage = settings.selectedProfilePackage.ifBlank { settings.games.firstOrNull().orEmpty() }
    val profileSettings = settings.profiles[selectedPackage]?.applyTo(settings) ?: settings
    val profileButtons = profileSettings.buttons
    var draftButtons by remember(selectedPackage, profileButtons) { mutableStateOf(profileButtons) }
    var savedSnapshot by remember(selectedPackage, profileButtons) { mutableStateOf(profileButtons) }
    var profileSensX by remember(selectedPackage, profileSettings.sensX) { mutableStateOf(profileSettings.sensX.coerceIn(0.1f, 1000f)) }
    var profileSensY by remember(selectedPackage, profileSettings.sensY) { mutableStateOf(profileSettings.sensY.coerceIn(0.1f, 1000f)) }
    var profileGlobalSens by remember(selectedPackage, profileSettings.globalSens) { mutableStateOf(profileSettings.globalSens.coerceIn(0.1f, 10f)) }
    var savedSensX by remember(selectedPackage, profileSettings.sensX) { mutableStateOf(profileSettings.sensX.coerceIn(0.1f, 1000f)) }
    var savedSensY by remember(selectedPackage, profileSettings.sensY) { mutableStateOf(profileSettings.sensY.coerceIn(0.1f, 1000f)) }
    var savedGlobalSens by remember(selectedPackage, profileSettings.globalSens) { mutableStateOf(profileSettings.globalSens.coerceIn(0.1f, 10f)) }
    var selectedId by remember { mutableStateOf<Int?>(draftButtons.firstOrNull()?.id) }
    var activeTool by remember { mutableStateOf<EditorTool?>(null) }
    var showGrid by remember { mutableStateOf(true) }
    var showMobileProperties by remember { mutableStateOf(false) }
    val propertiesSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editorZoom by remember(selectedPackage) { mutableStateOf(1f) }
    var editorPanX by remember(selectedPackage) { mutableStateOf(0f) }
    var editorPanY by remember(selectedPackage) { mutableStateOf(0f) }
    val undoStack = remember { mutableStateListOf<List<HudButton>>() }
    val redoStack = remember { mutableStateListOf<List<HudButton>>() }
    var dragOrigin by remember { mutableStateOf<List<HudButton>?>(null) }
    val selected = draftButtons.firstOrNull { it.id == selectedId }
    val currentButtons by rememberUpdatedState(draftButtons)
    val currentTool by rememberUpdatedState(activeTool)
    val dirty = draftButtons != savedSnapshot || profileSensX != savedSensX || profileSensY != savedSensY || profileGlobalSens != savedGlobalSens
    val accent = MaterialTheme.colorScheme.primary

    fun recordUndo(snapshot: List<HudButton>) {
        if (undoStack.lastOrNull() != snapshot) {
            undoStack.add(snapshot.toList())
            if (undoStack.size > 50) undoStack.removeAt(0)
        }
        redoStack.clear()
    }
    fun replaceDraft(next: List<HudButton>, record: Boolean = true) {
        if (next == draftButtons) return
        if (record && dragOrigin == null) recordUndo(draftButtons)
        draftButtons = next
    }
    fun updateButton(id: Int, transform: (HudButton) -> HudButton) {
        replaceDraft(draftButtons.map { if (it.id == id) transform(it) else it })
    }
    fun addButton(kind: Int, x: Float = 0.5f, y: Float = 0.5f) {
        val id = ((draftButtons.maxOfOrNull { it.id } ?: -1) + 1).coerceAtLeast(0)
        val button = when (kind) {
            Kind.FIRE -> HudButton(id = id, key = -1, x = x, y = y, kind = Kind.FIRE, mode = Mode.HOLD, name = "Tiro")
            Kind.AIM -> HudButton(id = id, key = -2, x = x, y = y, kind = Kind.AIM, name = "Mira")
            else -> HudButton(id = id, key = KeyEvent.KEYCODE_E, x = x, y = y, name = "Tecla $id")
        }
        replaceDraft(draftButtons + button)
        selectedId = id
        activeTool = null
    }
    fun saveDraft() {
        val unique = draftButtons.distinctBy { it.id }
        val profileBase = settings.profiles[selectedPackage]?.applyTo(settings) ?: settings
        val updatedProfile = AppProfile.fromSettings(profileBase.copy(
            buttons = unique, sensX = profileSensX, sensY = profileSensY, globalSens = profileGlobalSens
        ))
        viewModel.saveProfile(selectedPackage, updatedProfile)
        savedSnapshot = unique
        draftButtons = unique
        savedSensX = profileSensX
        savedSensY = profileSensY
        savedGlobalSens = profileGlobalSens
    }
    fun undo() {
        if (undoStack.isEmpty()) return
        redoStack.add(draftButtons.toList())
        draftButtons = undoStack.removeAt(undoStack.lastIndex)
        selectedId = selectedId?.takeIf { id -> draftButtons.any { it.id == id } } ?: draftButtons.firstOrNull()?.id
        activeTool = null
    }
    fun redo() {
        if (redoStack.isEmpty()) return
        undoStack.add(draftButtons.toList())
        draftButtons = redoStack.removeAt(redoStack.lastIndex)
        selectedId = selectedId?.takeIf { id -> draftButtons.any { it.id == id } } ?: draftButtons.firstOrNull()?.id
        activeTool = null
    }
    fun beginMove() { if (dragOrigin == null) dragOrigin = draftButtons.toList() }
    fun finishMove() {
        val before = dragOrigin
        dragOrigin = null
        if (before != null && before != draftButtons) recordUndo(before)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Cabeçalho em duas faixas: as ações nunca são expulsas para fora da largura útil.
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null); Text("Voltar")
                }
                Column(Modifier.weight(1f)) {
                    Text("Editor de controles", style = MaterialTheme.typography.titleLarge)
                    Text("Área de referência 16:9 · não é captura ao vivo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                Surface(
                    color = if (dirty) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(9.dp)
                ) {
                    Text(if (dirty) "Não salvo" else "Salvo", Modifier.padding(horizontal = 9.dp, vertical = 7.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 440.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { undo() }, enabled = undoStack.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("↶ Desfazer") }
                            OutlinedButton(onClick = { redo() }, enabled = redoStack.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("↷ Refazer") }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(
                                onClick = {
                                    draftButtons = savedSnapshot
                                    profileSensX = savedSensX; profileSensY = savedSensY; profileGlobalSens = savedGlobalSens
                                    selectedId = savedSnapshot.firstOrNull()?.id; activeTool = null
                                    undoStack.clear(); redoStack.clear(); dragOrigin = null
                                },
                                enabled = dirty,
                                modifier = Modifier.weight(1f)
                            ) { Text("Cancelar") }
                            OutlinedButton(onClick = { saveDraft() }, enabled = dirty, modifier = Modifier.weight(1f)) { Text("Salvar") }
                            Button(onClick = { if (dirty) saveDraft(); onBack() }, modifier = Modifier.weight(1f)) { Text("Concluir") }
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(onClick = { undo() }, enabled = undoStack.isNotEmpty()) { Text("↶ Desfazer") }
                        OutlinedButton(onClick = { redo() }, enabled = redoStack.isNotEmpty()) { Text("↷ Refazer") }
                        TextButton(onClick = {
                            draftButtons = savedSnapshot
                            profileSensX = savedSensX; profileSensY = savedSensY; profileGlobalSens = savedGlobalSens
                            selectedId = savedSnapshot.firstOrNull()?.id; activeTool = null
                            undoStack.clear(); redoStack.clear(); dragOrigin = null
                        }, enabled = dirty) { Text("Cancelar alterações") }
                        OutlinedButton(onClick = { saveDraft() }, enabled = dirty) { Text("Salvar") }
                        Button(onClick = { if (dirty) saveDraft(); onBack() }) { Text("Concluir") }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Perfil:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            (settings.games.ifEmpty { listOf(selectedPackage) }).forEach { pkg ->
                FilterChip(
                    selected = pkg == selectedPackage,
                    onClick = {
                        if (!dirty) viewModel.selectProfile(pkg)
                    },
                    enabled = !dirty || pkg == selectedPackage,
                    label = { Text(pkg.substringAfterLast('.')) }
                )
            }
            Text(selectedPackage, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Card(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Sensibilidade do perfil · $selectedPackage", style = MaterialTheme.typography.labelMedium)
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val compactControls = maxWidth < 440.dp
                    if (compactControls) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.fillMaxWidth()) {
                                Text("Horizontal X: ${"%.2f".format(profileSensX)}", style = MaterialTheme.typography.labelSmall)
                                Slider(value = (((log10(profileSensX.toDouble()) + 1.0) / 4.0).toFloat()).coerceIn(0f, 1f), onValueChange = { profileSensX = 10f.pow(it * 4f - 1f) }, valueRange = 0f..1f)
                            }
                            Column(Modifier.fillMaxWidth()) {
                                Text("Vertical Y: ${"%.2f".format(profileSensY)}", style = MaterialTheme.typography.labelSmall)
                                Slider(value = (((log10(profileSensY.toDouble()) + 1.0) / 4.0).toFloat()).coerceIn(0f, 1f), onValueChange = { profileSensY = 10f.pow(it * 4f - 1f) }, valueRange = 0f..1f)
                            }
                            Column(Modifier.fillMaxWidth()) {
                                Text("Geral: ${"%.2f".format(profileGlobalSens)}", style = MaterialTheme.typography.labelSmall)
                                Slider(value = profileGlobalSens, onValueChange = { profileGlobalSens = it }, valueRange = 0.1f..10f)
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text("Horizontal X: ${"%.2f".format(profileSensX)}", style = MaterialTheme.typography.labelSmall)
                                Slider(value = (((log10(profileSensX.toDouble()) + 1.0) / 4.0).toFloat()).coerceIn(0f, 1f), onValueChange = { profileSensX = 10f.pow(it * 4f - 1f) }, valueRange = 0f..1f)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Vertical Y: ${"%.2f".format(profileSensY)}", style = MaterialTheme.typography.labelSmall)
                                Slider(value = (((log10(profileSensY.toDouble()) + 1.0) / 4.0).toFloat()).coerceIn(0f, 1f), onValueChange = { profileSensY = 10f.pow(it * 4f - 1f) }, valueRange = 0f..1f)
                            }
                            Column(Modifier.weight(1f)) {
                                Text("Geral: ${"%.2f".format(profileGlobalSens)}", style = MaterialTheme.typography.labelSmall)
                                Slider(value = profileGlobalSens, onValueChange = { profileGlobalSens = it }, valueRange = 0.1f..10f)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Zoom ${(editorZoom * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    Slider(value = editorZoom, onValueChange = { editorZoom = it }, valueRange = 0.75f..2f, modifier = Modifier.weight(1f))
                    TextButton(onClick = { editorZoom = 1f; editorPanX = 0f; editorPanY = 0f }) { Text("Restaurar") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Deslocamento horizontal", style = MaterialTheme.typography.labelSmall)
                        Slider(value = editorPanX, onValueChange = { editorPanX = it }, valueRange = -0.5f..0.5f)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Deslocamento vertical", style = MaterialTheme.typography.labelSmall)
                        Slider(value = editorPanY, onValueChange = { editorPanY = it }, valueRange = -0.5f..0.5f)
                    }
                }
            }
        }

        BoxWithConstraints(Modifier.fillMaxSize().padding(10.dp)) {
            val desktopLayout = maxWidth >= 850.dp
            if (desktopLayout) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToolPanel(
                        modifier = Modifier.width(132.dp).fillMaxHeight(), activeTool = activeTool,
                        onTool = { activeTool = it }
                    )
                    Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        EditorCanvas(
                            modifier = Modifier.weight(1f).fillMaxWidth(), buttons = draftButtons, selectedId = selectedId,
                            activeTool = activeTool, showGrid = showGrid, accent = accent, zoom = editorZoom, panX = editorPanX, panY = editorPanY,
                            onSelect = { selectedId = it }, onAdd = { kind, x, y -> addButton(kind, x, y) },
                            onMove = { id, x, y -> replaceDraft(draftButtons.map { if (it.id == id) it.copy(x = x, y = y) else it }, record = false) },
                            onResize = { id, size -> replaceDraft(draftButtons.map { if (it.id == id) it.copy(size = size.coerceIn(0.035f, 0.15f)) else it }, record = false) },
                            onMoveStart = { beginMove() }, onMoveEnd = { finishMove() }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${draftButtons.size} controles", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            FilterChip(selected = showGrid, onClick = { showGrid = !showGrid }, label = { Text(if (showGrid) "Grade: ligada" else "Grade: desligada") })
                            Text("  Coordenadas normalizadas (0–100%)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    PropertiesPanel(
                        modifier = Modifier.width(290.dp).fillMaxHeight(), selected = selected,
                        onUpdate = { id, transform -> updateButton(id, transform) },
                        onDelete = { id -> replaceDraft(draftButtons.filterNot { it.id == id }); selectedId = draftButtons.firstOrNull()?.id },
                        onReset = { id -> savedSnapshot.firstOrNull { it.id == id }?.let { original -> updateButton(id) { original } } }
                    )
                }
            } else {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToolPanel(
                        modifier = Modifier.fillMaxWidth(), activeTool = activeTool,
                        onTool = { activeTool = it }, horizontal = true
                    )
                    EditorCanvas(
                        modifier = Modifier.fillMaxWidth(), buttons = draftButtons, selectedId = selectedId,
                        activeTool = activeTool, showGrid = showGrid, accent = accent, zoom = editorZoom, panX = editorPanX, panY = editorPanY,
                        onSelect = { selectedId = it }, onAdd = { kind, x, y -> addButton(kind, x, y); showMobileProperties = true },
                        onMove = { id, x, y -> replaceDraft(draftButtons.map { if (it.id == id) it.copy(x = x, y = y) else it }, record = false) },
                        onResize = { id, size -> replaceDraft(draftButtons.map { if (it.id == id) it.copy(size = size.coerceIn(0.035f, 0.15f)) else it }, record = false) },
                        onMoveStart = { beginMove() }, onMoveEnd = { finishMove() }
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${draftButtons.size} controles", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.weight(1f))
                        FilterChip(selected = showGrid, onClick = { showGrid = !showGrid }, label = { Text("Grade") })
                    }
                    if (selected != null) {
                        OutlinedButton(
                            onClick = { showMobileProperties = true },
                            modifier = Modifier.fillMaxWidth().height(46.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Text("  Editar propriedades de ${selected.name}", maxLines = 1)
                        }
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Text(
                                "Toque em um marcador para selecioná-lo e abrir suas propriedades.",
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMobileProperties) {
        ModalBottomSheet(
            onDismissRequest = { showMobileProperties = false },
            sheetState = propertiesSheetState
        ) {
            PropertiesPanel(
                modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(horizontal = 12.dp),
                selected = selected,
                onUpdate = { id, transform -> updateButton(id, transform) },
                onDelete = { id ->
                    replaceDraft(draftButtons.filterNot { it.id == id })
                    selectedId = draftButtons.firstOrNull { it.id != id }?.id
                    showMobileProperties = false
                },
                onReset = { id -> savedSnapshot.firstOrNull { it.id == id }?.let { original -> updateButton(id) { original } } }
            )
        }
    }
}

@Composable
private fun ToolPanel(
    modifier: Modifier,
    activeTool: EditorTool?,
    onTool: (EditorTool?) -> Unit,
    horizontal: Boolean = false
) {
    val tools = listOf(
        EditorTool(Kind.STANDARD, "Tecla / toque", "Associa uma tecla a um ponto de toque"),
        EditorTool(Kind.FIRE, "Tiro", "Ação de tiro com tecla ou botão do mouse"),
        EditorTool(Kind.AIM, "Mira", "Controle de visão já suportado pelo motor")
    )
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
        if (horizontal) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tools.forEach { tool ->
                    FilterChip(selected = activeTool?.kind == tool.kind, onClick = { onTool(if (activeTool?.kind == tool.kind) null else tool) }, label = { Text(tool.title) }, leadingIcon = { Icon(Icons.Filled.Add, null) })
                }
                Text("Selecione uma ferramenta e toque na área para posicionar", Modifier.padding(horizontal = 6.dp, vertical = 12.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("FERRAMENTAS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                tools.forEach { tool ->
                    OutlinedButton(onClick = { onTool(if (activeTool?.kind == tool.kind) null else tool) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" ${tool.title}")
                    }
                    Text(tool.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (activeTool != null) Text("Ferramenta ativa: ${activeTool.title}. Clique na área central.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun EditorCanvas(
    modifier: Modifier,
    buttons: List<HudButton>,
    selectedId: Int?,
    activeTool: EditorTool?,
    showGrid: Boolean,
    accent: Color,
    zoom: Float,
    panX: Float,
    panY: Float,
    onSelect: (Int?) -> Unit,
    onAdd: (Int, Float, Float) -> Unit,
    onMove: (Int, Float, Float) -> Unit,
    onResize: (Int, Float) -> Unit,
    onMoveStart: () -> Unit,
    onMoveEnd: () -> Unit
) {
    val latestButtons by rememberUpdatedState(buttons)
    val latestTool by rememberUpdatedState(activeTool)
    Box(modifier.background(Color(0xFF080C10), RoundedCornerShape(12.dp)).padding(6.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                .pointerInput(zoom, panX, panY) {
                    detectTapGestures { p ->
                        val rect = gameRect(size.width.toFloat(), size.height.toFloat(), zoom, panX, panY)
                        if (p.x !in rect.left..rect.right || p.y !in rect.top..rect.bottom) return@detectTapGestures
                        val nx = ((p.x - rect.left) / rect.width).coerceIn(0f, 1f)
                        val ny = ((p.y - rect.top) / rect.height).coerceIn(0f, 1f)
                        val tool = latestTool
                        if (tool != null) {
                            onAdd(tool.kind, nx, ny)
                        } else {
                            onSelect(findButtonAt(latestButtons, nx, ny, rect.width, rect.height)?.id)
                        }
                    }
                }
                .pointerInput(zoom, panX, panY) {
                    var dragTargetId: Int? = null
                    var draggingResize = false
                    detectDragGestures(onDragStart = { p ->
                        val rect = gameRect(size.width.toFloat(), size.height.toFloat(), zoom, panX, panY)
                        val nx = ((p.x - rect.left) / rect.width).coerceIn(0f, 1f)
                        val ny = ((p.y - rect.top) / rect.height).coerceIn(0f, 1f)
                        val target = findButtonAt(latestButtons, nx, ny, rect.width, rect.height)
                        dragTargetId = target?.id
                        draggingResize = target?.let { b ->
                            val cx = rect.left + b.x.coerceIn(0f, 1f) * rect.width
                            val cy = rect.top + b.y.coerceIn(0f, 1f) * rect.height
                            val distance = kotlin.math.hypot((p.x - cx).toDouble(), (p.y - cy).toDouble()).toFloat()
                            val radius = min(rect.width, rect.height) * b.size.coerceIn(0.035f, 0.15f) * b.visualScale.coerceIn(0.5f, 2f) / 2f
                            distance >= radius * 0.72f
                        } ?: false
                        onSelect(dragTargetId)
                        if (dragTargetId != null) onMoveStart()
                    }, onDragEnd = { if (dragTargetId != null) onMoveEnd(); dragTargetId = null; draggingResize = false }, onDragCancel = { if (dragTargetId != null) onMoveEnd(); dragTargetId = null; draggingResize = false }, onDrag = { change, drag ->
                        change.consume()
                        val id = dragTargetId ?: return@detectDragGestures
                        val rect = gameRect(size.width.toFloat(), size.height.toFloat(), zoom, panX, panY)
                        val current = latestButtons.firstOrNull { it.id == id } ?: return@detectDragGestures
                        if (draggingResize) {
                            val cx = rect.left + current.x.coerceIn(0f, 1f) * rect.width
                            val cy = rect.top + current.y.coerceIn(0f, 1f) * rect.height
                            val distance = kotlin.math.hypot((change.position.x - cx).toDouble(), (change.position.y - cy).toDouble()).toFloat()
                            val divisor = min(rect.width, rect.height) * current.visualScale.coerceIn(0.5f, 2f) / 2f
                            if (divisor > 0f) onResize(id, distance / divisor)
                        } else {
                            onMove(id, (current.x + drag.x / rect.width).coerceIn(0f, 1f), (current.y + drag.y / rect.height).coerceIn(0f, 1f))
                        }
                    })
                }
        ) {
            val rect = gameRect(size.width, size.height, zoom, panX, panY)
            drawRoundRect(Color(0xFF111A21), Offset(rect.left, rect.top), Size(rect.width, rect.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f))
            drawRoundRect(Color(0xFF3D5361), Offset(rect.left, rect.top), Size(rect.width, rect.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f), style = Stroke(1.5f))
            if (showGrid) {
                for (i in 1..7) drawLine(Color(0x223D5361), Offset(rect.left + rect.width * i / 8f, rect.top), Offset(rect.left + rect.width * i / 8f, rect.bottom), 1f)
                for (i in 1..3) drawLine(Color(0x223D5361), Offset(rect.left, rect.top + rect.height * i / 4f), Offset(rect.right, rect.top + rect.height * i / 4f), 1f)
            }
            drawContext.canvas.nativeCanvas.apply {
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(112, 135, 148); textSize = 11f; textAlign = android.graphics.Paint.Align.CENTER }
                drawText("ÁREA DE REFERÊNCIA · 16:9", rect.center.x, rect.top + 16f, paint)
            }
            buttons.forEach { b ->
                val cx = rect.left + b.x.coerceIn(0f, 1f) * rect.width
                val cy = rect.top + b.y.coerceIn(0f, 1f) * rect.height
                val radius = min(rect.width, rect.height) * b.size.coerceIn(0.035f, 0.15f) * b.visualScale.coerceIn(0.5f, 2f) / 2f
                val isSelected = b.id == selectedId
                val color = if (isSelected) accent else when (b.kind) { Kind.FIRE -> Color(0xFFE58B64); Kind.AIM -> Color(0xFF77B6E8); else -> Color(0xFF9BAAB4) }
                if (b.kind == Kind.STANDARD) {
                    drawRoundRect(color.copy(alpha = 0.18f * b.opacity.coerceIn(0f, 1f)), Offset(cx-radius, cy-radius), Size(radius*2, radius*2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f))
                    drawRoundRect(color.copy(alpha = if (isSelected) 1f else b.opacity.coerceIn(0.15f, 1f)), Offset(cx-radius, cy-radius), Size(radius*2, radius*2), cornerRadius = androidx.compose.ui.geometry.CornerRadius(5f), style = Stroke(if (isSelected) 3f else 1.5f))
                } else {
                    drawCircle(color.copy(alpha = 0.22f * b.opacity.coerceIn(0f, 1f)), radius, Offset(cx, cy))
                    drawCircle(color.copy(alpha = if (isSelected) 1f else b.opacity.coerceIn(0.15f, 1f)), radius, Offset(cx, cy), style = Stroke(if (isSelected) 3f else 1.5f))
                }
                if (isSelected) {
                    drawCircle(color, radius + 4f, Offset(cx, cy), style = Stroke(1.5f))
                    // Alça de redimensionamento; a escala visual permanece separada do tamanho funcional.
                    drawCircle(color, 4.5f, Offset(cx + radius, cy + radius))
                }
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        this.color = android.graphics.Color.WHITE
                        textSize = (radius * 0.65f).coerceIn(9f, 17f)
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    }
                    val label = when (b.key) { -1 -> "LMB"; -2 -> "RMB"; -3 -> "MB3"; else -> runCatching { KeyEvent.keyCodeToString(b.key).removePrefix("KEYCODE_") }.getOrDefault("?") }
                    drawText(label.take(8), cx, cy + paint.textSize * 0.35f, paint)
                }
            }
        }
    }
}

private fun findButtonAt(buttons: List<HudButton>, x: Float, y: Float, width: Float, height: Float): HudButton? {
    val minSide = min(width, height)
    return buttons.map { b ->
        val dx = (b.x - x) * width
        val dy = (b.y - y) * height
        Triple(b, dx * dx + dy * dy, minSide * (b.size.coerceIn(0.035f, 0.15f) * b.visualScale.coerceIn(0.5f, 2f) / 2f + 0.025f))
    }.filter { it.second < it.third * it.third }.minByOrNull { it.second }?.first
}

@Composable
private fun PropertiesPanel(
    modifier: Modifier,
    selected: HudButton?,
    onUpdate: (Int, (HudButton) -> HudButton) -> Unit,
    onDelete: (Int) -> Unit,
    onReset: (Int) -> Unit
) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(12.dp)) {
        if (selected == null) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("PROPRIEDADES", style = MaterialTheme.typography.labelLarge)
                Text("Selecione um controle ou escolha uma ferramenta e toque na área de referência.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("PROPRIEDADES", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("ID ${selected.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = selected.name, onValueChange = { v -> onUpdate(selected.id) { it.copy(name = v.take(32)) } }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Tipo do controle", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(Kind.STANDARD to "Tecla", Kind.FIRE to "Tiro", Kind.AIM to "Mira").forEach { (kind, label) ->
                        FilterChip(selected = selected.kind == kind, onClick = { onUpdate(selected.id) { b -> b.copy(kind = kind, key = when (kind) { Kind.FIRE -> -1; Kind.AIM -> -2; else -> if (b.key < 0) KeyEvent.KEYCODE_E else b.key }) } }, label = { Text(label) })
                    }
                }
                Text("Comportamento", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val modes = if (selected.kind == Kind.FIRE) listOf(Mode.HOLD to "Segurar", Mode.TAP to "Toque") else listOf(Mode.HOLD to "Segurar", Mode.TAP to "Toque", Mode.TOGGLE to "Alternar", Mode.DOUBLE to "Duplo")
                    modes.forEach { (mode, label) -> FilterChip(selected = selected.mode == mode, onClick = { onUpdate(selected.id) { it.copy(mode = mode) } }, label = { Text(label) }) }
                }
                OutlinedTextField(
                    value = selected.key.toString(),
                    onValueChange = { raw -> raw.toIntOrNull()?.takeIf { it in -3..-1 || it in 1..KeyEvent.getMaxKeyCode() }?.let { key -> onUpdate(selected.id) { it.copy(key = key) } } },
                    label = { Text("Código da tecla / mouse") },
                    supportingText = { Text("Atual: ${keyLabel(selected.key)} · -1 esquerdo, -2 direito, -3 scroll") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Text("X: ${(selected.x * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = selected.x.coerceIn(0f, 1f), onValueChange = { v -> onUpdate(selected.id) { it.copy(x = v) } }, valueRange = 0f..1f)
                Text("Y: ${(selected.y * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = selected.y.coerceIn(0f, 1f), onValueChange = { v -> onUpdate(selected.id) { it.copy(y = v) } }, valueRange = 0f..1f)
                Text("Tamanho funcional: ${(selected.size * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = selected.size.coerceIn(0.035f, 0.15f), onValueChange = { v -> onUpdate(selected.id) { it.copy(size = v) } }, valueRange = 0.035f..0.15f)
                Text("Escala visual: ${(selected.visualScale * 100).toInt()}% · independente da posição", style = MaterialTheme.typography.bodySmall)
                Slider(value = selected.visualScale.coerceIn(0.5f, 2f), onValueChange = { v -> onUpdate(selected.id) { it.copy(visualScale = v) } }, valueRange = 0.5f..2f)
                Text("Opacidade visual: ${(selected.opacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                Slider(value = selected.opacity.coerceIn(0f, 1f), onValueChange = { v -> onUpdate(selected.id) { it.copy(opacity = v) } }, valueRange = 0f..1f)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { onReset(selected.id) }, modifier = Modifier.weight(1f)) { Text("Restaurar") }
                    OutlinedButton(onClick = { onDelete(selected.id) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Delete, null); Text(" Excluir") }
                }
            }
        }
    }
}

private fun gameRect(width: Float, height: Float, zoom: Float = 1f, panX: Float = 0f, panY: Float = 0f): androidx.compose.ui.geometry.Rect {
    val targetRatio = 16f / 9f
    val ratio = width / height.coerceAtLeast(1f)
    val base = if (ratio > targetRatio) {
        val w = height * targetRatio
        androidx.compose.ui.geometry.Rect((width - w) / 2f, 0f, (width + w) / 2f, height)
    } else {
        val h = width / targetRatio
        androidx.compose.ui.geometry.Rect(0f, (height - h) / 2f, width, (height + h) / 2f)
    }
    val scale = zoom.coerceIn(0.75f, 2f)
    val cx = base.center.x; val cy = base.center.y
    val w = base.width * scale; val h = base.height * scale
    val shiftX = panX.coerceIn(-0.5f, 0.5f) * width
    val shiftY = panY.coerceIn(-0.5f, 0.5f) * height
    return androidx.compose.ui.geometry.Rect(cx - w / 2f + shiftX, cy - h / 2f + shiftY, cx + w / 2f + shiftX, cy + h / 2f + shiftY)
}
