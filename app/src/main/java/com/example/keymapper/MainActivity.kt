package com.example.keymapper

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import com.example.keymapper.presentation.SettingsViewModel
import com.example.keymapper.ui.HomeScreen
import com.example.keymapper.ui.MappingEditorScreen
import com.example.keymapper.ui.SettingsScreen

private data class AppDestination(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)
private val destinations = listOf(
    AppDestination("Painel", Icons.Filled.Home),
    AppDestination("Editor", Icons.Filled.Edit),
    AppDestination("Ajustes", Icons.Filled.Settings)
)

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
                val settings by settingsViewModel.settings.collectAsState()
                var screen by remember { mutableIntStateOf(0) }
                var settingsTab by remember { mutableIntStateOf(0) }
                BackHandler(enabled = screen != 0) { screen = 0 }

                MaterialTheme(
                    colorScheme = schemeFor(settings.theme),
                    typography = KeyMapperTypography,
                    shapes = KeyMapperShapes
                ) {
                    Box(Modifier.fillMaxSize()) {
                        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
                            val desktop = maxWidth >= 820.dp
                            if (desktop) {
                                Row(Modifier.fillMaxSize()) {
                                    Surface(
                                        modifier = Modifier.width(212.dp).fillMaxSize(),
                                        color = MaterialTheme.colorScheme.surface,
                                        tonalElevation = 2.dp
                                    ) {
                                        Column(
                                            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 14.dp)
                                        ) {
                                            Row(
                                                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = MaterialTheme.colorScheme.primaryContainer
                                                ) {
                                                    Text(
                                                        "KM",
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                                Column(Modifier.padding(start = 10.dp)) {
                                                    Text("KEYMAPPER", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                                    Text("STUDIO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            Spacer(Modifier.height(22.dp))
                                            Text(
                                                "WORKSPACE",
                                                modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            destinations.forEachIndexed { index, destination ->
                                                val selected = screen == index
                                                Row(
                                                    Modifier.fillMaxWidth()
                                                        .clip(RoundedCornerShape(11.dp))
                                                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f) else Color.Transparent)
                                                        .clickable { screen = index }
                                                        .padding(horizontal = 12.dp, vertical = 12.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    androidx.compose.material3.Icon(
                                                        destination.icon,
                                                        contentDescription = destination.label,
                                                        tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        destination.label,
                                                        modifier = Modifier.padding(start = 12.dp),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                Spacer(Modifier.height(5.dp))
                                            }
                                            Spacer(Modifier.weight(1f))
                                            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                                            Text(
                                                "INPUT WORKSPACE  ·  ANDROID / DeX",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Surface(
                                        modifier = Modifier.weight(1f).fillMaxSize(),
                                        color = MaterialTheme.colorScheme.background
                                    ) {
                                        ScreenContent(
                                            screen = screen,
                                            settingsTab = settingsTab,
                                            tickState = tick,
                                            viewModel = settingsViewModel,
                                            onSettingsTab = { settingsTab = it },
                                            onNavigate = { screen = it }
                                        )
                                    }
                                }
                            } else {
                                Column(Modifier.fillMaxSize()) {
                                    Surface(
                                        modifier = Modifier.weight(1f).fillMaxSize(),
                                        color = MaterialTheme.colorScheme.background
                                    ) {
                                        ScreenContent(
                                            screen = screen,
                                            settingsTab = settingsTab,
                                            tickState = tick,
                                            viewModel = settingsViewModel,
                                            onSettingsTab = { settingsTab = it },
                                            onNavigate = { screen = it }
                                        )
                                    }
                                    NavigationBar(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        tonalElevation = 8.dp
                                    ) {
                                        destinations.forEachIndexed { index, destination ->
                                            NavigationBarItem(
                                                selected = screen == index,
                                                onClick = { screen = index },
                                                icon = { androidx.compose.material3.Icon(destination.icon, contentDescription = destination.label) },
                                                label = { Text(destination.label) },
                                                alwaysShowLabel = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e("KeyMapperStartup", "Falha ao inicializar a tela principal", t)
            if (::settingsViewModel.isInitialized) runCatching { settingsViewModel.close() }
            setContent {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.fillMaxSize().padding(24.dp)) {
                            Text("KeyMapper não conseguiu iniciar a tela principal.", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "O erro foi registrado no log do Android como KeyMapperStartup. Feche e tente abrir novamente; se persistir, envie o log de falha.",
                                modifier = Modifier.padding(top = 12.dp)
                            )
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

@Composable
private fun ScreenContent(
    screen: Int,
    settingsTab: Int,
    tickState: MutableIntState,
    viewModel: SettingsViewModel,
    onSettingsTab: (Int) -> Unit,
    onNavigate: (Int) -> Unit
) {
    when (screen) {
        0 -> HomeScreen(
            viewModel,
            tickState.intValue,
            onOpenSettings = { tab -> onSettingsTab(tab); onNavigate(2) },
            onOpenEditor = { onNavigate(1) }
        )
        1 -> MappingEditorScreen(
            viewModel,
            onBack = { onNavigate(0) },
            onOpenSettings = { tab -> onSettingsTab(tab); onNavigate(2) }
        )
        else -> SettingsScreen(viewModel, tickState, onBack = { onNavigate(0) }, initialTab = settingsTab)
    }
}

/** KeyMapper Studio: grafite profundo, superfícies azul-ardósia e acento mint/ciano. */
private fun schemeFor(theme: Int): ColorScheme = when (theme) {
    1 -> darkColorScheme(
        primary = Color(0xFF77E6D1), onPrimary = Color(0xFF06231E),
        primaryContainer = Color(0xFF153E38), onPrimaryContainer = Color(0xFFD6FFF7),
        secondary = Color(0xFF9CBBC6), onSecondary = Color(0xFF12242A),
        tertiary = Color(0xFFB5A5FF), onTertiary = Color(0xFF201747),
        background = Color(0xFF05080A), surface = Color(0xFF0C1216),
        surfaceVariant = Color(0xFF172329), onBackground = Color(0xFFEAF3F5),
        onSurface = Color(0xFFEAF3F5), onSurfaceVariant = Color(0xFFA5B8BE),
        outline = Color(0xFF344951), error = Color(0xFFFF737C)
    )
    2 -> darkColorScheme(
        primary = Color(0xFFFF7187), onPrimary = Color(0xFF310A13),
        primaryContainer = Color(0xFF4B1D2B), onPrimaryContainer = Color(0xFFFFDCE3),
        secondary = Color(0xFFFFB4A9), onSecondary = Color(0xFF3A0B07),
        tertiary = Color(0xFFFFC66E), onTertiary = Color(0xFF2D1B00),
        background = Color(0xFF100A10), surface = Color(0xFF19121A),
        surfaceVariant = Color(0xFF2A1B28), onBackground = Color(0xFFF7EDF3),
        onSurface = Color(0xFFF7EDF3), onSurfaceVariant = Color(0xFFC6AEBF),
        outline = Color(0xFF51364C), error = Color(0xFFFF737C)
    )
    3 -> darkColorScheme(
        primary = Color(0xFF7FE0A7), onPrimary = Color(0xFF062417),
        primaryContainer = Color(0xFF17412B), onPrimaryContainer = Color(0xFFD6FFE3),
        secondary = Color(0xFFAEDBC0), onSecondary = Color(0xFF10281A),
        tertiary = Color(0xFFB8D67A), onTertiary = Color(0xFF202E08),
        background = Color(0xFF07100C), surface = Color(0xFF101A14),
        surfaceVariant = Color(0xFF1D2B22), onBackground = Color(0xFFEAF5ED),
        onSurface = Color(0xFFEAF5ED), onSurfaceVariant = Color(0xFFACC0B2),
        outline = Color(0xFF3B5342), error = Color(0xFFFF737C)
    )
    4 -> darkColorScheme(
        primary = Color(0xFF7BC8FF), onPrimary = Color(0xFF062239),
        primaryContainer = Color(0xFF173D59), onPrimaryContainer = Color(0xFFDCEFFF),
        secondary = Color(0xFFA9D8FF), onSecondary = Color(0xFF0E2940),
        tertiary = Color(0xFFB8B4FF), onTertiary = Color(0xFF211B55),
        background = Color(0xFF07101A), surface = Color(0xFF101B27),
        surfaceVariant = Color(0xFF1C2C3D), onBackground = Color(0xFFEAF3FC),
        onSurface = Color(0xFFEAF3FC), onSurfaceVariant = Color(0xFFADBECE),
        outline = Color(0xFF3B536A), error = Color(0xFFFF737C)
    )
    else -> darkColorScheme(
        primary = Color(0xFF8DEBD0), onPrimary = Color(0xFF06251F),
        primaryContainer = Color(0xFF17443D), onPrimaryContainer = Color(0xFFD8FFF5),
        secondary = Color(0xFF75C9EF), onSecondary = Color(0xFF062638),
        tertiary = Color(0xFFB8A6FF), onTertiary = Color(0xFF261A55),
        background = Color(0xFF080D13), surface = Color(0xFF101923),
        surfaceVariant = Color(0xFF1B2936), onBackground = Color(0xFFEAF2F7),
        onSurface = Color(0xFFEAF2F7), onSurfaceVariant = Color(0xFFAABBC8),
        outline = Color(0xFF35495B), error = Color(0xFFFF737C)
    )
}

private val KeyMapperShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val KeyMapperTypography = Typography(
    headlineSmall = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.45).sp),
    titleLarge = TextStyle(fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.25).sp),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
)
