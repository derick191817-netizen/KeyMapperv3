package com.example.keymapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.Text
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.keymapper.data.AppSettings
import com.example.keymapper.presentation.SettingsViewModel
import com.example.keymapper.ui.HomeScreen
import com.example.keymapper.ui.MappingEditorScreen
import com.example.keymapper.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private lateinit var settingsViewModel: SettingsViewModel
    private val tick = mutableIntStateOf(0)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val app = application as? KeyMapperApp
                ?: error("A classe Application KeyMapperApp não foi carregada")
            settingsViewModel = SettingsViewModel(app.settingsRepository)
            setContent {
                val s by settingsViewModel.settings.collectAsState()
                var screen by remember { mutableIntStateOf(0) }
                var settingsTab by remember { mutableIntStateOf(0) }
                BackHandler(enabled = screen != 0) { screen = 0 }
                MaterialTheme(colorScheme = schemeFor(s.theme), typography = KeyMapperTypography) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = MaterialTheme.colorScheme.background,
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 3.dp
                            ) {
                                NavigationBarItem(
                                    selected = screen == 0,
                                    onClick = { screen = 0 },
                                    icon = { androidx.compose.material3.Icon(Icons.Filled.Home, contentDescription = "Painel inicial") },
                                    label = { Text("Painel") },
                                    alwaysShowLabel = true
                                )
                                NavigationBarItem(
                                    selected = screen == 2,
                                    onClick = { screen = 2 },
                                    icon = { androidx.compose.material3.Icon(Icons.Filled.Edit, contentDescription = "Editor de controles") },
                                    label = { Text("Editor") },
                                    alwaysShowLabel = true
                                )
                                NavigationBarItem(
                                    selected = screen == 1,
                                    onClick = { screen = 1 },
                                    icon = { androidx.compose.material3.Icon(Icons.Filled.Settings, contentDescription = "Configurações") },
                                    label = { Text("Ajustes") },
                                    alwaysShowLabel = true
                                )
                            }
                        }
                    ) { contentPadding ->
                        Surface(
                            Modifier.padding(contentPadding).consumeWindowInsets(contentPadding).fillMaxSize(),
                            color = MaterialTheme.colorScheme.background
                        ) {
                            if (screen == 0) {
                                HomeScreen(settingsViewModel, tick.intValue, onOpenSettings = { tab ->
                                    settingsTab = tab
                                    screen = 1
                                }, onOpenEditor = { screen = 2 })
                            } else if (screen == 1) {
                                SettingsScreen(settingsViewModel, tick, onBack = { screen = 0 }, initialTab = settingsTab)
                            } else {
                                MappingEditorScreen(settingsViewModel, onBack = { screen = 0 }, onOpenSettings = { settingsTab = 0; screen = 1 })
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("KeyMapperStartup", "Falha ao inicializar a tela principal", t)
            if (::settingsViewModel.isInitialized) {
                runCatching { settingsViewModel.close() }
            }
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().padding(24.dp)) {
                            Text("KeyMapper não conseguiu iniciar a tela principal.", style = MaterialTheme.typography.titleLarge)
                            Text("O erro foi registrado no log do Android como KeyMapperStartup. Feche e tente abrir novamente; se persistir, envie o log de falha.", modifier = Modifier.padding(top = 12.dp))
                            Text("Tipo do erro: ${t.javaClass.simpleName}", modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::settingsViewModel.isInitialized) runCatching { settingsViewModel.close() }
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}

/** Identidade KeyMapper: grafite azulado com violeta e ciano como acentos. */
private fun schemeFor(theme: Int): ColorScheme = when (theme) {
    1 -> darkColorScheme(
        primary = Color(0xFF75E6D0), onPrimary = Color(0xFF06211D),
        primaryContainer = Color(0xFF123B35), onPrimaryContainer = Color(0xFFD2FFF6),
        secondary = Color(0xFF9FB8C7), onSecondary = Color(0xFF14232B),
        background = Color(0xFF050708), surface = Color(0xFF0B0F11),
        surfaceVariant = Color(0xFF151B1F), onBackground = Color(0xFFEAF0F2),
        onSurface = Color(0xFFEAF0F2), onSurfaceVariant = Color(0xFF9BAAB0),
        outline = Color(0xFF34434A), error = Color(0xFFFF6B72)
    )
    2 -> darkColorScheme(
        primary = Color(0xFFFF667A), onPrimary = Color(0xFF2A0610),
        primaryContainer = Color(0xFF421923), onPrimaryContainer = Color(0xFFFFD9DE),
        background = Color(0xFF101114), surface = Color(0xFF17191D),
        surfaceVariant = Color(0xFF24272D), onBackground = Color(0xFFF0F1F4),
        onSurface = Color(0xFFF0F1F4), onSurfaceVariant = Color(0xFFB1B5BE),
        outline = Color(0xFF3B4049), error = Color(0xFFFF6B72)
    )
    3 -> darkColorScheme(
        primary = Color(0xFF72E0A1), onPrimary = Color(0xFF062416),
        primaryContainer = Color(0xFF143B28), onPrimaryContainer = Color(0xFFD0FFE0),
        background = Color(0xFF0A100E), surface = Color(0xFF111916),
        surfaceVariant = Color(0xFF1D2923), onBackground = Color(0xFFEAF4EE),
        onSurface = Color(0xFFEAF4EE), onSurfaceVariant = Color(0xFFA4B7AB),
        outline = Color(0xFF34483B), error = Color(0xFFFF6B72)
    )
    4 -> darkColorScheme(
        primary = Color(0xFF78C8FF), onPrimary = Color(0xFF062033),
        primaryContainer = Color(0xFF15354B), onPrimaryContainer = Color(0xFFD6EEFF),
        background = Color(0xFF0A0F14), surface = Color(0xFF111820),
        surfaceVariant = Color(0xFF1D2933), onBackground = Color(0xFFEAF2F8),
        onSurface = Color(0xFFEAF2F8), onSurfaceVariant = Color(0xFFA4B5C2),
        outline = Color(0xFF354653), error = Color(0xFFFF6B72)
    )
    else -> darkColorScheme(
        primary = Color(0xFFA78BFA), onPrimary = Color(0xFF17102E),
        primaryContainer = Color(0xFF332653), onPrimaryContainer = Color(0xFFEDE5FF),
        secondary = Color(0xFF67DCEB), onSecondary = Color(0xFF06252B),
        tertiary = Color(0xFF91A4FF), onTertiary = Color(0xFF111A43),
        background = Color(0xFF090B10), surface = Color(0xFF11151C),
        surfaceVariant = Color(0xFF1B2029), onBackground = Color(0xFFEDF0F7),
        onSurface = Color(0xFFEDF0F7), onSurfaceVariant = Color(0xFFA5AFBF),
        outline = Color(0xFF343D4D), error = Color(0xFFFF737C)
    )
}

/** Hierarquia tipográfica compartilhada por todas as telas Compose. */
private val KeyMapperTypography = Typography(
    headlineSmall = TextStyle(fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.35).sp),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.35.sp)
)
