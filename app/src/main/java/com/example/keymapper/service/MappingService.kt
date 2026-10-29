import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
package com.example.keymapper.service

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.content.pm.PackageManager
import android.graphics.RectF
import android.hardware.display.DisplayManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.keymapper.MainActivity
import com.example.keymapper.KeyMapperApp
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.AppProfile
import com.example.keymapper.data.SettingsRepository
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.display.DisplayUtils
import com.example.keymapper.display.ScreenInfo
import com.example.keymapper.injection.ForegroundWatcher
import com.example.keymapper.injection.ShizukuInjector
import com.example.keymapper.injection.TouchInjector
import com.example.keymapper.mapping.MappingEngine
import com.example.keymapper.overlay.HudView
import com.example.keymapper.overlay.OverlayManager
import com.example.keymapper.util.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/**
 * Serviço de mapeamento em foreground, iniciado explicitamente pelo usuário.
 * Não mantém wakelock nem solicita reinicialização automática após encerramento pelo sistema.
 *
 * Também reage a: monitor conectado/desconectado (DeX), rotação, mudança de densidade,
 * mouse/teclado removido ou plugado (solta os toques e continua em modo toque) e,
 * se escolhido, ao jogo em primeiro plano (overlay só aparece no jogo selecionado).
 */
class MappingService : Service(), HudView.Host {

    companion object {
        const val ACTION_STOP = "com.example.keymapper.STOP"
        const val ACTION_EDIT = "com.example.keymapper.EDIT"

        @Volatile
        var running = false

        /** Diagnóstico mostrado na tela inicial: último app em primeiro plano e se a detecção funciona. */
        @Volatile
        var detectedPackage: String? = null

        @Volatile
        var detectionSupported = false
        private const val CHANNEL = "mapping"
        private const val NOTIF_ID = 1
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val ui = Handler(Looper.getMainLooper())
    private lateinit var repo: SettingsRepository
    private lateinit var displays: DisplayHelper

    @Volatile
    private var cfg = AppSettings()
    @Volatile
    private var baseCfg = AppSettings()
    @Volatile
    private var activeProfilePackage: String? = null

    @Volatile
    private var info: ScreenInfo = DisplayUtils.DEFAULT

    @Volatile
    private var areaRect = RectF(0f, 0f, 1920f, 1080f)
    private var touch: TouchInjector? = null
    private var engine: MappingEngine? = null
    private var overlay: OverlayManager? = null
    private var watcher: ForegroundWatcher? = null
    private var editing = false
    private var paused = false
    // Evita que ACTION_DOWN repetidos de uma tecla de atalho alternem editor/pausa várias vezes.
    private val heldServiceShortcuts = HashSet<Int>()
    private var gameFocused = true // desconhecido = ativo

    private val screenChanged = Runnable { handleScreenChange() }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = scheduleScreenCheck()
        override fun onDisplayRemoved(displayId: Int) = scheduleScreenCheck()
        override fun onDisplayChanged(displayId: Int) = scheduleScreenCheck()
    }

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            if (!editing && !paused && overlay?.quickMenuOpen != true) overlay?.setCapture(true)
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            engine?.releaseAll() // nada fica "preso" se o mouse/teclado for desconectado
        }

        override fun onInputDeviceChanged(deviceId: Int) {}
    }

    override fun onCreate() {
        super.onCreate()
        running = true
        repo = (application as KeyMapperApp).settingsRepository
        displays = DisplayHelper(this)
        createChannel()
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
        scope.launch {
            repo.flow.collect { s ->
                try {
                    baseCfg = s
                    val effective = settingsForPackage(s, activeProfilePackage)
                    val hzChanged = touch != null && (effective.refreshHz != cfg.refreshHz ||
                        effective.safeOverlay != cfg.safeOverlay || effective.captureWindow != cfg.captureWindow ||
                        effective.hudWindow != cfg.hudWindow || effective.useExternalDisplay != cfg.useExternalDisplay)
                    cfg = effective
                    Haptics.enabled = effective.haptics
                    displays.useExternal = effective.useExternalDisplay
                    recomputeArea()
                    if (touch == null) {
                        gameFocused = !s.onlyInGame // modo automático: começa DESLIGADO até o jogo abrir
                        setup()
                    } else if (hzChanged) {
                        rebuild()
                    } else {
                        overlay?.refresh()
                    }
                    updateWatcher()
                } catch (e: Exception) {
                    Log.e("KeyMapper", "Falha ao aplicar uma atualização de configuração; serviço preservado", e)
                    ui.post { Toast.makeText(this@MappingService, "Falha ao aplicar configurações. Consulte o diagnóstico.", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    private fun isShizukuReady(): Boolean = try {
        Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        Log.w("KeyMapper", "Não foi possível verificar o Shizuku", t)
        false
    }

    private fun setup() {
        refreshInfo()
        val injector = ShizukuInjector()
        if (!isShizukuReady()) {
            ui.post {
                Toast.makeText(
                    this,
                    "Shizuku desconectado ou sem permissão. Reative o Shizuku e conceda a permissão ao KeyMapper.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        // Não introduzimos pausas artificiais no caminho crítico. O injetor agrega apenas os
        // movimentos que excedem a cadência-alvo, sem bloquear a thread com sleep.
        val t = TouchInjector(injector, { info.displayId })
        touch = t
        t.onFailure = {
            // Callback vindo da thread de injeção: notificações e liberação sempre na UI thread.
            ui.post {
                Toast.makeText(
                    this,
                    "Shizuku desconectado. Reative o Shizuku para usar mouse/teclado.",
                    Toast.LENGTH_LONG
                ).show()
                engine?.releaseAll()
            }
        }
        val e = MappingEngine(t, { areaRect }, { cfg }, { detectedPackage })
        engine = e
        // O motor executa na thread de injeção; atualizações de Views ficam na main thread.
        e.onAimChanged = { ui.post { overlay?.refresh() } }
        overlay = OverlayManager(
            this, displays, this,
            refreshHz = { cfg.refreshHz },
            // Não registrar eventos individuais aqui: movimento pode chegar a 1000 Hz.
            // InputDiagnostics mantém contadores agregados sem inundar o logcat.
            onDelta = { dx, dy -> if (!editing) e.onDelta(dx, dy) },
            onButton = { bit, down -> if (!editing) e.onMouseButton(bit, down) },
            onKey = { code, down -> handleKey(code, down) }
        )
        watcher = ForegroundWatcher { pkg -> onForeground(pkg) }
        try {
            displays.register(displayListener, ui)
            (getSystemService(INPUT_SERVICE) as InputManager).registerInputDeviceListener(inputListener, ui)
            rebuild()
        } catch (e: Exception) {
            Log.e("KeyMapper", "Falha durante a inicialização do motor de mapeamento", e)
            try { displays.unregister(displayListener) } catch (cleanup: Exception) {
                Log.w("KeyMapper", "Falha ao limpar listener de tela após inicialização incompleta", cleanup)
            }
            try { (getSystemService(INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(inputListener) }
            catch (cleanup: Exception) { Log.w("KeyMapper", "Falha ao limpar listener de entrada após inicialização incompleta", cleanup) }
            try { overlay?.hide() } catch (cleanup: Exception) { Log.w("KeyMapper", "Falha ao limpar overlay após inicialização incompleta", cleanup) }
            overlay = null
            engine?.releaseAll()
            engine = null
            touch?.onFailure = null
            touch?.shutdown()
            touch = null
            watcher?.shutdown()
            watcher = null
            ui.post { Toast.makeText(this, "Não foi possível iniciar o mapeamento. Verifique permissões e logs.", Toast.LENGTH_LONG).show() }
        }
    }

    // ---- jogo em primeiro plano ----

    private fun settingsForPackage(base: AppSettings, packageName: String?): AppSettings {
        if (packageName.isNullOrBlank()) return base
        val profile = base.profiles[packageName] ?: return base
        return profile.applyTo(base)
    }

    private fun updateWatcher() {
        val w = watcher ?: return
        if (cfg.onlyInGame) {
            w.start()
        } else {
            w.stop()
            val hadProfile = activeProfilePackage != null
            if (hadProfile) engine?.pauseAndReleaseAll()
            activeProfilePackage = null
            cfg = baseCfg
            recomputeArea()
            if (!gameFocused) {
                gameFocused = true
                rebuild()
            } else if (hadProfile) {
                overlay?.refresh()
                if (!paused) engine?.resumeAfterConfigurationChange()
            }
        }
    }

    private fun onForeground(pkg: String?) {
        detectedPackage = pkg
        detectionSupported = watcher?.supported == true
        if (!cfg.onlyInGame) return
        if (pkg == null) return            // sem detecção: mantém o estado atual
        if (pkg == packageName) return     // telas internas: não troca o perfil ativo
        val active = baseCfg.games.contains(pkg)
        if (active) {
            val packageChanged = activeProfilePackage != pkg
            if (!gameFocused || packageChanged) {
                // Freeze synchronously before changing cfg; queued old-profile events are rejected.
                engine?.pauseAndReleaseAll()
                gameFocused = true
                activeProfilePackage = pkg
                cfg = settingsForPackage(baseCfg, pkg)
                recomputeArea()
                rebuild(preserveEnginePause = true)
                if (!paused) engine?.resumeAfterConfigurationChange()
            } else {
                activeProfilePackage = pkg
                cfg = settingsForPackage(baseCfg, pkg)
                recomputeArea()
                overlay?.refresh()
            }
        } else if (gameFocused) {
            engine?.pauseAndReleaseAll()
            gameFocused = false
            activeProfilePackage = null
            cfg = baseCfg
            recomputeArea()
            overlay?.hide()
        }
    }

    // ---- tela / DeX ----

    private fun refreshInfo() {
        info = try {
            displays.info(displays.target())
        } catch (e: Exception) {
            Log.w("KeyMapper", "Não foi possível atualizar as informações da tela; mantendo a última leitura válida", e)
            info
        }
        recomputeArea()
    }

    private fun recomputeArea() {
        areaRect = DisplayUtils.gameArea(info.width, info.height, cfg)
    }

    private fun scheduleScreenCheck() {
        ui.removeCallbacks(screenChanged)
        ui.postDelayed(screenChanged, 300) // agrupa a rajada de eventos de conexão/rotação
    }

    private fun handleScreenChange() {
        if (touch == null) return
        try {
            val old = info
            refreshInfo()
            val cur = info
            if (cur.displayId != old.displayId || cur.densityDpi != old.densityDpi) {
                rebuild() // DeX conectado/desconectado ou DPI diferente: refaz as janelas
            } else {
                overlay?.refresh() // rotação/redimensionamento: as janelas já acompanham
            }
        } catch (e: Exception) {
            Log.w("KeyMapper", "Falha ao atualizar a área após mudança de tela/DeX", e)
        }
    }

    private fun rebuild(preserveEnginePause: Boolean = false) {
        val ov = overlay ?: return
        refreshInfo()
        engine?.releaseAll()
        editing = false
        if (!preserveEnginePause) {
            paused = false
            engine?.paused = false
        }
        if (cfg.onlyInGame && !gameFocused) {
            ov.hide()
            return
        }
        ov.show() // restaura o overlay no monitor atual
    }

    private fun handleKey(code: Int, down: Boolean) {
        if (code == cfg.editKey || code == cfg.pauseKey) {
            if (down) {
                if (heldServiceShortcuts.add(code)) {
                    if (code == cfg.editKey) setEditing(!editing)
                    else if (!editing) togglePause()
                }
            } else {
                heldServiceShortcuts.remove(code)
            }
            return
        }
        if (editing) {
            if (down) overlay?.hud?.let { if (it.armed) it.addButton(code) }
            return
        }
        engine?.onKey(code, down)
    }

    // ---- HudView.Host ----

    override fun settings(): AppSettings = cfg

    override fun update(s: AppSettings) {
        cfg = s
        recomputeArea()
        overlay?.refresh()
    }

    override fun persist() {
        val packageName = activeProfilePackage
        val snapshot = cfg
        scope.launch {
            try {
                if (packageName != null) repo.saveProfile(packageName, AppProfile.fromSettings(snapshot), selected = false)
                else repo.save(snapshot.copy(profiles = baseCfg.profiles, selectedProfilePackage = baseCfg.selectedProfilePackage))
            } catch (t: Throwable) {
                Log.e("KeyMapper", "Falha ao persistir as configurações", t)
                ui.post { Toast.makeText(this@MappingService, "Não foi possível salvar as configurações.", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    override fun arm() {}

    override fun openConfig(id: Int) {
        overlay?.openConfig(id)
    }

    override fun area(): RectF = areaRect

    override fun uiDensity(): Float = DisplayUtils.uiDensity(info, cfg)

    override fun closeAll() {
        stopSelf()
    }

    override fun setEditing(on: Boolean) {
        editing = on
        engine?.releaseAll()
        overlay?.setEditing(on)
    }

    // ---- menu rápido ----

    override fun openQuickMenu() {
        val ov = overlay ?: return
        if (ov.quickMenuOpen) {
            ov.closeQuickMenu()
            return
        }
        engine?.releaseAll()
        ov.setCapture(false) // solta o mouse para poder usar o menu
        ov.openQuickMenu()
    }

    override fun quickMenuClosed() {
        if (!paused && !editing) overlay?.setCapture(true)
    }

    override fun isPaused(): Boolean = paused

    override fun activeAimId(): Int = engine?.aimId ?: -1

    override fun togglePause() {
        paused = !paused
        engine?.paused = paused
        engine?.releaseAll()
        // pausado: mouse liberado (a janela mantém o foco do teclado para o atalho de retomar)
        overlay?.setCapture(!paused && !editing && overlay?.quickMenuOpen != true)
    }

    override fun toggleHud() {
        cfg = cfg.copy(showHud = !cfg.showHud)
        persist()
        overlay?.refresh()
    }

    override fun openApp() {
        try {
            val i = Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            val opts = ActivityOptions.makeBasic().setLaunchDisplayId(info.displayId)
            startActivity(i, opts.toBundle())
        } catch (e: Exception) {
            Log.e("KeyMapper", "Não foi possível abrir a interface principal no monitor atual", e)
            ui.post { Toast.makeText(this, "Não foi possível abrir o KeyMapper nesta tela.", Toast.LENGTH_SHORT).show() }
        }
    }

    // ---- ciclo de vida ----

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_EDIT -> setEditing(!editing)
        }
        // Não ressuscitar o motor automaticamente após encerramento pelo sistema.
        // O usuário pode reativá-lo explicitamente; autoStart é tratado pelo receiver.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        heldServiceShortcuts.clear()
        ui.removeCallbacksAndMessages(null)
        scope.cancel()
        try {
            displays.unregister(displayListener)
        } catch (e: Exception) {
            Log.w("KeyMapper", "Falha ao remover listener de tela durante o encerramento", e)
        }
        try {
            (getSystemService(INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(inputListener)
        } catch (e: Exception) {
            Log.w("KeyMapper", "Falha ao remover listener de dispositivos durante o encerramento", e)
        }
        watcher?.shutdown()
        watcher = null
        try {
            overlay?.hide()
        } catch (e: Exception) {
            Log.w("KeyMapper", "Falha ao ocultar overlay durante o encerramento", e)
        }
        overlay = null
        engine?.releaseAll()
        engine = null
        touch?.onFailure = null
        touch?.shutdown()
        touch = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Mapeamento", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("KeyMapper ativo")
            .setContentText("Toque na bolha de engrenagem para o menu rápido")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build()
}
