@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.keymapper.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.keymapper.injection.Activation
import com.example.keymapper.injection.ActivationState
import com.example.keymapper.presentation.SettingsViewModel
import com.example.keymapper.service.MappingService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The in-app editor intentionally does not offer a pretend 16:9 placement canvas.
 * Coordinates are edited only by the real TYPE_APPLICATION_OVERLAY HUD over the target app.
 */
@Composable
fun MappingEditorScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val selectedPackage = settings.selectedProfilePackage.ifBlank { settings.games.firstOrNull().orEmpty() }
    var overlayGranted by remember { mutableStateOf(AndroidSettings.canDrawOverlays(context)) }
    var serviceRunning by remember { mutableStateOf(MappingService.running) }
    var shizukuReady by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        overlayGranted = AndroidSettings.canDrawOverlays(context)
        serviceRunning = MappingService.running
        shizukuReady = withContext(Dispatchers.IO) { Activation.state(context) == ActivationState.READY }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(48.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Editor sobre o jogo real", style = MaterialTheme.typography.headlineSmall)
                Text("Posicionamento por sobreposição flutuante", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sem tela simulada", style = MaterialTheme.typography.titleMedium)
                Text(
                    "A posição dos controles deve ser ajustada com o jogo aberto ao fundo. Esta tela não desenha uma cópia do jogo nem grava posições de uma área fictícia.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Perfil selecionado", style = MaterialTheme.typography.labelLarge)
                Text(selectedPackage.ifBlank { "Nenhum aplicativo selecionado" }, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pré-requisitos", style = MaterialTheme.typography.titleMedium)
                StatusLine("Serviço de mapeamento", serviceRunning, if (serviceRunning) "Em execução" else "Inativo")
                StatusLine("Permissão de sobreposição", overlayGranted, if (overlayGranted) "Concedida" else "Necessária")
                StatusLine("Shizuku", shizukuReady, if (shizukuReady) "Autorizado" else "Não confirmado")
                if (!overlayGranted) {
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}"))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }.onFailure {
                                Toast.makeText(context, "Não foi possível abrir as permissões de sobreposição.", Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Conceder permissão de sobreposição") }
                }
                if (!serviceRunning || !overlayGranted) {
                    Text(
                        "Volte ao painel principal para iniciar o serviço e concluir as permissões antes de abrir o jogo.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Button(
            enabled = serviceRunning && overlayGranted && selectedPackage.isNotBlank(),
            onClick = {
                val launch = context.packageManager.getLaunchIntentForPackage(selectedPackage)
                if (launch == null) {
                    Toast.makeText(context, "Não encontrei um aplicativo iniciável para este perfil.", Toast.LENGTH_LONG).show()
                } else {
                    runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { Toast.makeText(context, "Não foi possível abrir o jogo selecionado.", Toast.LENGTH_LONG).show() }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = null)
            Text("  Abrir o jogo do perfil selecionado")
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Como editar os controles", style = MaterialTheme.typography.titleMedium)
                StepLine("1", "Volte ao painel e inicie o serviço de mapeamento.")
                StepLine("2", "Abra o jogo do perfil selecionado; não feche o jogo para configurar.")
                StepLine("3", "Toque na bolha flutuante do KeyMapper e escolha “Editar HUD”.")
                StepLine("4", "Toque em +, escolha Tecla/Tiro/Mira e use ABC para criar um controle sem associação inicial.")
                StepLine("5", "Arraste o marcador ou sua alça sobre o botão real do jogo; selecione Configurar para associar tecla e editar as propriedades.")
                StepLine("6", "Salve no painel do controle. As posições movidas são persistidas no perfil ativo.")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = null)
                Text("  Voltar ao painel")
            }
            Button(onClick = onOpenSettings, modifier = Modifier.weight(1f)) {
                androidx.compose.material3.Icon(Icons.Filled.Settings, contentDescription = null)
                Text("  Configurações")
            }
        }
        Spacer(Modifier.size(2.dp))
    }
}

@Composable
private fun StatusLine(label: String, ready: Boolean, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.material3.Icon(
            if (ready) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
        )
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepLine(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(28.dp)) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Text(number, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}
