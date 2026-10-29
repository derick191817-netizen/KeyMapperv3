package com.example.keymapper.service

import android.app.ActivityManager
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
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.keymapper.MainActivity
import com.example.keymapper.KeyMapperApp
import com.example.keymapper.data.AppSettings
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
 * Motor em foreground: START_STICKY + PARTIAL_WAKE_LOCK. Se o sistema encerrar o serviço,
 * ele reinicia e o overlay é restaurado no onCreate.
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
    private var wake: PowerManager.WakeLock? = null

    @Volatile
    private var cfg = AppSettings()

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
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wake = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "keymapper:mapping").apply { acquire() }

        scope.launch {
            repo.flow.collect { s ->
                val hzChanged = touch != null && (s.refreshHz != cfg.refreshHz ||
                    s.safeOverlay != cfg.safeOverlay || s.captureWindow != cfg.captureWindow ||
                    s.hudWindow != cfg.hudWindow || s.useExternalDisplay != cfg.useExternalDisplay)
                cfg = s
                Haptics.enabled = s.haptics
                displays.useExternal = s.useExternalDisplay
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
        // Aparelhos com menos de 4 GB de RAM: pequena folga entre eventos para evitar ANR
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val low = mi.totalMem < 4L * 1024 * 1024 * 1024
        val extra = when (cfg.perfMode) {
            1 -> 0L            // alta performance: sem folga
            2 -> 5L            // economia
            else -> if (low) 5L else 0L
        }

        val t = TouchInjector(injector, { info.displayId }, extra)
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
        e.onAimChanged = { overlay?.refresh() } // redesenha a cruz de mira
        overlay = OverlayManager(
            this, displays, this,
            refreshHz = { cfg.refreshHz },
            onDelta = { dx, dy ->
                Log.d("KeyMapper", "Mouse $dx,$dy")
                if (!editing) e.onDelta(dx, dy)
            },
            onButton = { bit, down ->
                Log.d("KeyMapper", "Botão do mouse $bit down=$down")
                if (!editing) e.onMouseButton(bit, down)
            },
            onKey = { code, down ->
                Log.d("KeyMapper", "Injetando tecla $code down=$down")
                handleKey(code, down)
            }
        )
        watcher = ForegroundWatcher { pkg -> onForeground(pkg) }
        displays.register(displayListener, ui)
        (getSystemService(INPUT_SERVICE) as InputManager).registerInputDeviceListener(inputListener, ui)
        rebuild()
    }

    // ---- jogo em primeiro plano ----

    private fun updateWatcher() {
        val w = watcher ?: return
        if (cfg.onlyInGame) {
            w.start()
        } else {
            w.stop()
            if (!gameFocused) {
                gameFocused = true
                rebuild()
            }
        }
    }

    private fun onForeground(pkg: String?) {
        detectedPackage = pkg
        detectionSupported = watcher?.supported == true
        if (!cfg.onlyInGame) return
        if (pkg == null) return            // sem detecção: mantém o estado atual
        if (pkg == packageName) return     // nas telas do próprio app: mantém o estado
        val active = cfg.games.contains(pkg) // liga ao abrir o jogo escolhido, desliga ao sair
        if (active == gameFocused) return
        gameFocused = active
        if (active) {
            rebuild()
        } else {
            engine?.releaseAll()
            overlay?.hide()
        }
    }

    // ---- tela / DeX ----

    private fun refreshInfo() {
        info = try {
            displays.info(displays.target())
        } catch (e: Exception) {
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
        }
    }

    private fun rebuild() {
        val ov = overlay ?: return
        refreshInfo()
        engine?.releaseAll()
        editing = false
        paused = false
        engine?.paused = false
        if (cfg.onlyInGame && !gameFocused) {
            ov.hide()
            return
        }
        ov.show() // restaura o overlay no monitor atual
    }

    private fun handleKey(code: Int, down: Boolean) {
        if (down && code == cfg.editKey) {
            setEditing(!editing)
            return
        }
        if (down && code == cfg.pauseKey && !editing) {
            togglePause()
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
        scope.launch { repo.save(cfg) }
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
        }
    }

    // ---- ciclo de vida ----

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_EDIT -> setEditing(!editing)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        ui.removeCallbacksAndMessages(null)
        scope.cancel()
        try {
            displays.unregister(displayListener)
        } catch (e: Exception) {
        }
        try {
            (getSystemService(INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(inputListener)
        } catch (e: Exception) {
        }
        watcher?.shutdown()
        watcher = null
        try { overlay?.hide() } catch (_: Throwable) { }
        overlay = null
        engine?.releaseAll()
        engine = null
        touch?.onFailure = null
        touch?.shutdown()
        touch = null
        wake?.takeIf { it.isHeld }?.release()
        wake = null
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
