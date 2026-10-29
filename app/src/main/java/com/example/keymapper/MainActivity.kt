package com.example.keymapper

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.keymapper.data.AppSettings
import com.example.keymapper.presentation.SettingsViewModel
import com.example.keymapper.ui.HomeScreen
import com.example.keymapper.ui.SettingsScreen

class MainActivity : ComponentActivity() {
    private lateinit var settingsViewModel: SettingsViewModel
    private val tick = mutableIntStateOf(0)
    private val notifPerm =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        settingsViewModel = SettingsViewModel((application as KeyMapperApp).settingsRepository)
        setContent {
            val s by settingsViewModel.settings.collectAsState()
            var screen by remember { mutableIntStateOf(0) }   // 0 = painel inicial, 1 = configurações
            var settingsTab by remember { mutableIntStateOf(0) }
            BackHandler(enabled = screen == 1) { screen = 0 }
            MaterialTheme(colorScheme = schemeFor(s.theme)) {
                Surface(Modifier.fillMaxSize()) {
                    if (screen == 0) {
                        HomeScreen(settingsViewModel, tick.intValue) { tab ->
                            settingsTab = tab
                            screen = 1
                        }
                    } else {
                        SettingsScreen(settingsViewModel, tick, onBack = { screen = 0 }, initialTab = settingsTab)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (::settingsViewModel.isInitialized) settingsViewModel.close()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}

/** Temas do app: 0 escuro padrão, 1 AMOLED, 2 vermelho (MSI), 3 verde (BlueStacks), 4 azul. */
private fun schemeFor(theme: Int): ColorScheme = when (theme) {
    1 -> darkColorScheme(
        primary = Color(0xFF80CBC4), onPrimary = Color.Black,
        background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF151515),
        onBackground = Color.White, onSurface = Color.White
    )
    2 -> darkColorScheme(
        primary = Color(0xFFFF5252), onPrimary = Color.White,
        primaryContainer = Color(0xFF5A1A1A), onPrimaryContainer = Color.White,
        background = Color(0xFF121212), surface = Color(0xFF1A1A1A)
    )
    3 -> darkColorScheme(
        primary = Color(0xFF66BB6A), onPrimary = Color.Black,
        primaryContainer = Color(0xFF1B3D1E), onPrimaryContainer = Color.White,
        background = Color(0xFF101614), surface = Color(0xFF17201C)
    )
    4 -> darkColorScheme(
        primary = Color(0xFF64B5F6), onPrimary = Color.Black,
        primaryContainer = Color(0xFF12304A), onPrimaryContainer = Color.White,
        background = Color(0xFF0F1419), surface = Color(0xFF161D26)
    )
    else -> darkColorScheme(
        primary = Color(0xFFB0BEC5), onPrimary = Color.Black,
        primaryContainer = Color(0xFF2B3338), onPrimaryContainer = Color.White,
        background = Color(0xFF121416), surface = Color(0xFF1B1E21)
    )
}
