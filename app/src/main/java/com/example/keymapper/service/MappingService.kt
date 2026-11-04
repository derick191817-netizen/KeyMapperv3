package com.example.keymapper.service

import android.app.ActivityOptions
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import com.example.keymapper.diagnostics.InputDiagnostics
import com.example.keymapper.diagnostics.InputBackendProbe
import com.example.keymapper.diagnostics.RestartDiagnostics
import com.example.keymapper.KeyMapperApp
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.AppProfile
import com.example.keymapper.data.Kind
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.SettingsRepository
import com.example.keymapper.domain.PerformancePolicy
import com.example.keymapper.domain.MappingEditorPolicy
import com.example.keymapper.display.DisplayHelper
import com.example.keymapper.display.DisplayUtils
import com.example.keymapper.display.ScreenInfo
import com.example.keymapper.display.ScreenGeometryPolicy
import com.example.keymapper.injection.Activation
import com.example.keymapper.injection.ForegroundWatcher
import com.example.keymapper.injection.ShizukuInjector
import com.example.keymapper.injection.ShizukuShellBridge
import com.example.keymapper.injection.ShizukuInputCaptureBridge
import com.example.keymapper.injection.TouchInjector
import com.example.keymapper.input.InputDeviceManager
import com.example.keymapper.input.LinuxEvdevEvent
import com.example.keymapper.input.LinuxInputKeyMapper
import com.example.keymapper.input.PhysicalInputState
import com.example.keymapper.input.NativeMouseReportAggregator
import com.example.keymapper.input.NativeInputBatchLossGuard
import com.example.keymapper.input.InputState
import com.example.keymapper.input.InputStateMachine
import com.example.keymapper.mapping.MappingEngine
import com.example.keymapper.overlay.HudView
import com.example.keymapper.overlay.OverlayManager
import com.example.keymapper.util.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

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
        const val ACTION_BEGIN_EDIT = "com.example.keymapper.BEGIN_EDIT"
        const val ACTION_ADD_STANDARD_CONTROL = "com.example.keymapper.ADD_STANDARD_CONTROL"
        const val ACTION_CONFIG_STANDARD_BUTTON = "com.example.keymapper.CONFIG_STANDARD_BUTTON"
        const val EXTRA_BUTTON_ID = "com.example.keymapper.extra.BUTTON_ID"

        @Volatile
        var running = false

        /** Perfil efetivamente carregado pelo serviço; distinto do perfil apenas selecionado na UI. */
        @Volatile
        var activeProfilePackageSnapshot: String? = null
            private set

        /** Diagnóstico mostrado na tela inicial: último app em primeiro plano e se a detecção funciona. */
        @Volatile
        var detectedPackage: String? = null

        @Volatile
        var detectionSupported = false
        private const val CHANNEL = "mapping"
        private const val NOTIF_ID = 1
        private val startRequestPending = AtomicBoolean(false)

        /** Deduplicates concurrent start requests from the UI and boot receiver. */
        fun requestStart(context: Context): Boolean {
            if (running) return false
            if (!startRequestPending.compareAndSet(false, true)) return false
            return try {
                androidx.core.content.ContextCompat.startForegroundService(
                    context.applicationContext,
                    Intent(context.applicationContext, MappingService::class.java)
                )
                RestartDiagnostics.recordEvent(context, "requestStart aceito pelo Android")
                true
            } catch (t: Throwable) {
                startRequestPending.set(false)
                RestartDiagnostics.recordEvent(context, "requestStart falhou: ${t.javaClass.simpleName}: ${t.message}")
                throw t
            }
        }
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

    /** False quando o display explicitamente selecionado não existe; bloqueia injeção no display antigo. */
    @Volatile
    private var displayTargetValid = true

    @Volatile
    private var virtualTouchDescription = "uinput ainda não sondado; InputManager permanece como fallback"
    // Estado publicado pelo leitor privilegiado; o campo é atualizado no looper principal.
    private var nativeCaptureEnabled = false

    @Volatile
    private var areaRect = RectF(0f, 0f, 1920f, 1080f)
    private var touch: TouchInjector? = null
    private var shizukuInjector: ShizukuInjector? = null
    @Volatile
    private var engine: MappingEngine? = null
    private var overlay: OverlayManager? = null
    private var watcher: ForegroundWatcher? = null
    @Volatile
    private var editing = false
    @Volatile
    private var paused = false
    /** Prevents duplicate setup attempts while DataStore emits rapid configuration updates. */
    private var setupInProgress = false
    /** A failed setup shuts the service down instead of leaving a half-initialized foreground service. */
    private var setupFailed = false
    // Evita que ACTION_DOWN repetidos de uma tecla de atalho alternem editor/pausa várias vezes.
    private val heldServiceShortcuts = HashSet<Int>()
    // Estado independente da captura MotionEvent: o backend evdev tem IDs negativos próprios.
    // Todos os acessos acontecem em processNativeInputBatch/looper principal.
    private val nativePhysicalInputState = PhysicalInputState()
    // REL_X/REL_Y são um único vetor de câmera por SYN_REPORT; estado separado por periférico.
    // Acessado somente no looper principal, inclusive quando um relatório cruza dois lotes JNI.
    private val nativeMouseReportAggregator = NativeMouseReportAggregator()
    // Perda de callback JNI invalida transições DOWN/UP e relatórios relativos parcialmente lidos.
    private val nativeInputBatchLossGuard = NativeInputBatchLossGuard()
    @Volatile
    private var gameFocused = true // desconhecido = ativo

    private val screenChanged = Runnable { handleScreenChange() }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = scheduleScreenCheck()
        override fun onDisplayRemoved(displayId: Int) = scheduleScreenCheck()
        override fun onDisplayChanged(displayId: Int) = scheduleScreenCheck()
    }

    private val inputListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            updateInputDeviceDiagnostics()
            val added = android.view.InputDevice.getDevice(deviceId)
            val addedMouse = added?.let {
                it.supportsSource(android.view.InputDevice.SOURCE_MOUSE) ||
                    it.supportsSource(android.view.InputDevice.SOURCE_MOUSE_RELATIVE)
            } == true
            if (addedMouse && !editing && !paused && overlay?.quickMenuOpen != true) {
                overlay?.setCapture(true)
            }
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            updateInputDeviceDiagnostics()
            // Limpa somente o estado físico do deviceId removido. A agregação por dispositivo
            // mantém a tecla/botão lógico pressionado se outro periférico ainda o estiver usando.
            overlay?.releasePhysicalInputs(deviceId)
            // Não solta entradas de outros periféricos: o capturador agrega estados por deviceId.
            // Descarta somente deltas/scroll ainda pendentes da origem que acabou de sair.
            engine?.onInputDeviceRemoved(deviceId)
            val remaining = InputDeviceManager.snapshot()
            if (!remaining.hasMouse) {
                InputStateMachine.transition(InputState.DISCONNECTED, "último mouse desconectado")
            }
        }

        override fun onInputDeviceChanged(deviceId: Int) { updateInputDeviceDiagnostics() }
    }

    override fun onCreate() {
        super.onCreate()
        RestartDiagnostics.recordEvent(this, "MappingService.onCreate")
        Log.i("KeyMapperLifecycle", "MappingService.onCreate pid=${android.os.Process.myPid()}")
        try {
            repo = (application as KeyMapperApp).settingsRepository
            displays = DisplayHelper(this)
            createChannel()
            val n = buildNotification()
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIF_ID, n)
            }
            // Public state becomes active only after Android confirms foreground promotion.
            running = true
            startRequestPending.set(false)
            RestartDiagnostics.recordEvent(this, "startForeground confirmado; serviço ativo")
        } catch (t: Throwable) {
            // A foreground-service start failure must not leave a half-started service around.
            running = false
            startRequestPending.set(false)
            RestartDiagnostics.recordEvent(this, "foreground falhou: ${t.javaClass.simpleName}: ${t.message}")
            Log.e("KeyMapperLifecycle", "Falha fatal ao promover MappingService para foreground; encerrando sem loop de retry", t)
            stopSelf()
            return
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
                    ShizukuInputCaptureBridge.setEnabled(shouldNativeCaptureBeEnabled())
                    Haptics.enabled = effective.haptics
                    displays.useExternal = effective.useExternalDisplay
                    recomputeArea()
                    if (touch == null && !setupFailed) {
                        gameFocused = !s.onlyInGame
                        setup()
                    } else if (hzChanged) {
                        rebuild()
                    } else {
                        overlay?.refresh()
                    }
                    updateWatcher()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (e: Exception) {
                    Log.e("KeyMapper", "Falha ao aplicar uma atualização de configuração; serviço preservado", e)
                    ui.post { Toast.makeText(this@MappingService, "Falha ao aplicar configurações. Consulte o diagnóstico.", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    private fun updateInputDeviceDiagnostics() {
        val devices = try { InputDeviceManager.snapshot().devices } catch (t: Throwable) {
            Log.w("KeyMapper", "Falha enumerando dispositivos de entrada", t)
            emptyList()
        }
        InputDiagnostics.setInputDevices(
            if (devices.isEmpty()) "Nenhum dispositivo listado" else devices.joinToString("; ") { device ->
                "${device.name}#${device.id} [mouse=${device.mouse}, keyboard=${device.keyboard}, virtual=${device.virtual}, external=${device.external}]"
            }
        )
    }


    private fun shouldNativeCaptureBeEnabled(): Boolean =
        cfg.captureWindow && !paused && !editing && (!cfg.onlyInGame || gameFocused) &&
            overlay?.quickMenuOpen != true

    /**
     * Limpa bookkeeping nativo quando o motor já está recebendo um releaseAll global.
     * Também libera atalhos de serviço que poderiam ter ficado retidos durante a pausa.
     */
    private fun clearNativePhysicalInputState() {
        nativeMouseReportAggregator.clear()
        val released = nativePhysicalInputState.clear()
        released.keyCodes.forEach { heldServiceShortcuts.remove(it) }
    }

    /** Libera somente as teclas/botões globais que deixaram de ser mantidos após remover um device. */
    private fun releaseNativePhysicalDevice(deviceId: Int) {
        nativeMouseReportAggregator.drop(deviceId)
        val released = nativePhysicalInputState.clearDevice(deviceId)
        val buttonBits = intArrayOf(1, 2, 4, 8, 16)
        for (bit in buttonBits) {
            if ((released.buttonMask and bit) != 0) engine?.onMouseButton(bit, false)
        }
        released.keyCodes.forEach { handleKey(it, false) }
    }

    /** Reset global sincronizado com MappingEngine.releaseAll(). */
    private fun releaseAllMappedInputs(target: MappingEngine? = engine) {
        clearNativePhysicalInputState()
        target?.releaseAll()
    }

    /** Pausa o motor e limpa estado físico nativo antes de alternar perfil/foco do jogo. */
    private fun pauseAndReleaseMappedInputs() {
        engine?.paused = true
        releaseAllMappedInputs()
    }

    /** Recebe lotes evdev do processo privilegiado; registros têm 6 longs cada. */
    private fun handleNativeInputBatch(records: LongArray) {
        if (records.isEmpty() || records.size % LinuxEvdevEvent.RECORD_FIELDS != 0) return
        if (!running || paused || editing || (cfg.onlyInGame && !gameFocused)) return

        // Mantém o despacho no looper principal nesta versão: o mesmo lote pode acionar
        // atalhos globais e atualizar overlays. Não alteramos a latência do movimento relativo.
        var containsKeyboardInput = false
        var cursor = 0
        while (cursor + LinuxEvdevEvent.RECORD_FIELDS <= records.size) {
            val deviceClass = records[cursor].toInt()
            val type = records[cursor + 1].toInt()
            if ((deviceClass and LinuxEvdevEvent.DEVICE_KEYBOARD) != 0 &&
                (type == LinuxEvdevEvent.EV_KEY || type == LinuxEvdevEvent.EV_SYN)
            ) {
                containsKeyboardInput = true
                break
            }
            cursor += LinuxEvdevEvent.RECORD_FIELDS
        }

        if (!ui.post { processNativeInputBatch(records) }) {
            // O lote pode conter DOWN/UP ou parte de um SYN_REPORT. O próximo lote aceito fará
            // releaseAll e limpará o agregador antes de consumir eventos novos.
            nativeInputBatchLossGuard.markBatchDropped()
            Log.w("KeyMapperInput", "Lote evdev descartado: fila principal indisponível; recuperação pendente")
            InputDiagnostics.recordInputQueueDrop(mouse = !containsKeyboardInput)
        }
    }

    private fun processNativeInputBatch(records: LongArray) {
        if (nativeInputBatchLossGuard.consumeRecoveryRequired()) {
            Log.w("KeyMapperInput", "Recuperando estado após perda de lote evdev: liberando entradas e deltas pendentes")
            overlay?.releasePhysicalInputs()
            releaseAllMappedInputs()
        }
        if (!running || paused || editing || (cfg.onlyInGame && !gameFocused)) return
        var cursorDx = 0f
        var cursorDy = 0f

        // Fecha um vetor X/Y por relatório evdev. Isso garante um único timestamp e um único
        // cálculo de timeScale para os dois eixos, inclusive se o SYN_REPORT vier no lote JNI
        // seguinte. O flush antes de tecla/botão/scroll mantém a ordem de controle observável.
        fun flushMouseReport(deviceId: Int, reportTimeNanos: Long = 0L) {
            val sample = nativeMouseReportAggregator.flush(deviceId, reportTimeNanos) ?: return
            engine?.onDelta(sample.dx, sample.dy, sample.eventTimeNanos, deviceId)
            cursorDx += sample.dx
            cursorDy += sample.dy
        }

        var index = 0
        while (index + LinuxEvdevEvent.RECORD_FIELDS <= records.size) {
            if (!running || paused || editing || (cfg.onlyInGame && !gameFocused)) break
            val deviceClass = records[index].toInt()
            val type = records[index + 1].toInt()
            val code = records[index + 2].toInt()
            val value = records[index + 3].toInt()
            val deviceId = records[index + 4].toInt()
            val eventTimeNanos = records[index + 5].coerceAtLeast(0L)
            index += LinuxEvdevEvent.RECORD_FIELDS

            if (type == LinuxEvdevEvent.EV_SYN && code == LinuxEvdevEvent.SYN_DROPPED) {
                // A perda de sincronização invalida somente o relatório parcial e o estado deste
                // periférico. Não deixe deltas anteriores reaparecerem após o próximo SYN_REPORT.
                nativeMouseReportAggregator.drop(deviceId)
                engine?.onInputDeviceRemoved(deviceId)
                releaseNativePhysicalDevice(deviceId)
                overlay?.releasePhysicalInputs(deviceId)
                continue
            }
            when (type) {
                LinuxEvdevEvent.EV_SYN -> if (
                    code == LinuxEvdevEvent.SYN_REPORT &&
                    (deviceClass and LinuxEvdevEvent.DEVICE_MOUSE) != 0
                ) {
                    flushMouseReport(deviceId, eventTimeNanos)
                }
                LinuxEvdevEvent.EV_REL -> if ((deviceClass and LinuxEvdevEvent.DEVICE_MOUSE) != 0) {
                    when (code) {
                        LinuxEvdevEvent.REL_X, LinuxEvdevEvent.REL_Y ->
                            nativeMouseReportAggregator.add(deviceId, code, value, eventTimeNanos)
                        LinuxEvdevEvent.REL_WHEEL, LinuxEvdevEvent.REL_WHEEL_HI_RES -> {
                            flushMouseReport(deviceId)
                            LinuxEvdevEvent.verticalScrollDetents(code, value)?.let {
                                engine?.onMouseScroll(0f, it, deviceId)
                            }
                        }
                        LinuxEvdevEvent.REL_HWHEEL, LinuxEvdevEvent.REL_HWHEEL_HI_RES -> {
                            flushMouseReport(deviceId)
                            LinuxEvdevEvent.horizontalScrollDetents(code, value)?.let {
                                engine?.onMouseScroll(it, 0f, deviceId)
                            }
                        }
                    }
                }
                LinuxEvdevEvent.EV_KEY -> {
                    // Um relatório HID composto pode intercalar movimento e botão/tecla. Fechar
                    // o vetor aqui preserva a ordem antes da transição de controle correspondente.
                    flushMouseReport(deviceId)
                    if (value != 0 && value != 1) continue
                    val down = value == 1
                    if ((deviceClass and LinuxEvdevEvent.DEVICE_MOUSE) != 0) {
                        val button = LinuxEvdevEvent.mouseButtonMask(code)
                        if (button != null && nativePhysicalInputState.updateButtonCode(deviceId, code, button, down)) {
                            engine?.onMouseButton(button, down)
                        }
                    }
                    if ((deviceClass and LinuxEvdevEvent.DEVICE_KEYBOARD) != 0) {
                        val keyCode = LinuxInputKeyMapper.toAndroidKeyCode(code)
                        if (keyCode != null) {
                            val changed = if (down) {
                                nativePhysicalInputState.pressKey(deviceId, keyCode)
                            } else {
                                nativePhysicalInputState.releaseKey(deviceId, keyCode)
                            }
                            if (changed) handleKey(keyCode, down)
                        }
                    }
                }
            }
        }
        if (cursorDx != 0f || cursorDy != 0f) {
            val dx = cursorDx
            val dy = cursorDy
            ui.post {
                if (running && !paused && !editing) overlay?.onNativeMouseDelta(dx, dy)
            }
        }
    }

    /** Cria um controle genérico de tecla no centro e abre as propriedades reais do HUD. */
    private fun addStandardControl() {
        if (overlay?.canOpenConfig() != true || !running) {
            Toast.makeText(this, "Abra o aplicativo do perfil e aguarde o HUD ficar disponível.", Toast.LENGTH_LONG).show()
            return
        }
        val current = cfg
        val id = MappingEditorPolicy.nextAvailableId(current.buttons.map { it.id }, current.buttons.size)
        if (id == null) {
            Toast.makeText(this, "Limite de 128 controles por perfil atingido.", Toast.LENGTH_SHORT).show()
            return
        }
        val control = HudButton(
            id = id,
            key = 0,
            x = 0.5f,
            y = 0.5f,
            name = "Tecla",
            kind = Kind.STANDARD
        )
        cfg = current.copy(buttons = current.buttons + control)
        recomputeArea()
        persist()
        overlay?.refresh()
        setEditing(true)
        overlay?.openConfig(id)
    }

    private fun setup() {
        RestartDiagnostics.recordEvent(this, "setup iniciado")
        if (setupInProgress || setupFailed || touch != null) {
            Log.d("KeyMapperLifecycle", "setup ignorado: inProgress=$setupInProgress failed=$setupFailed touch=${touch != null}")
            return
        }
        setupInProgress = true
        try {
            refreshInfo()
            updateInputDeviceDiagnostics()
            val injector = ShizukuInjector()
            shizukuInjector = injector
            val inputManagerReady = injector.isReady()
            val userServiceReady = injector.canBindUserService()
            if (!inputManagerReady && !userServiceReady) {
                val activation = Activation.state(applicationContext)
                InputDiagnostics.setPermissionStatus(Activation.guide(activation, Build.VERSION.SDK_INT))
                throw SecurityException(Activation.guide(activation, Build.VERSION.SDK_INT))
            }
            scope.launch(Dispatchers.IO) {
                val localReport = runCatching { InputBackendProbe.inspect(applicationContext, inputManagerReady) }
                    .getOrElse { "SONDAGEM LOCAL DO APP\nFalha ao consultar metadados: ${it.javaClass.simpleName}: ${it.message}" }
                InputDiagnostics.setBackendProbe(localReport)
                if (userServiceReady) {
                    // A mesma verificação é repetida no UserService, sob UID shell/root.
                    // Isso evita inferir acesso evdev/uinput a partir das permissões do UID do app.
                    ShizukuShellBridge.inspectRuntime(applicationContext) { privilegedReport ->
                        InputDiagnostics.setBackendProbe("$localReport\n\n$privilegedReport")
                    }
                }
            }
            InputDiagnostics.setBackend(
                "Captura: evdev/Pointer Capture conforme permissões; saída touchscreen: uinput opcional " +
                    "no display principal e InputManager como fallback/rota DeX."
            )
            InputDiagnostics.setPermissionStatus(
                when {
                    inputManagerReady -> "${injector.identityDescription()}; InputManager/INJECT_EVENTS disponíveis. uinput é sondado separadamente; aceitação do transporte não prova consumo pelo destino."
                    userServiceReady -> "${injector.identityDescription()}; UserService disponível sem INJECT_EVENTS. Toques podem usar uinput se a ROM permitir; InputManager e mouse relativo ficam indisponíveis."
                    else -> "Shizuku indisponível"
                }
            )

            val t = TouchInjector(
                injector = injector,
                displayId = { if (displayTargetValid) info.displayId else -1 },
                displaySize = { info.width to info.height },
                virtualTouchOutput = ShizukuInputCaptureBridge
            )
            touch = t
            t.onFailure = {
                ui.post {
                    Toast.makeText(
                        this,
                        "Shizuku desconectado. Reative o Shizuku para usar mouse/teclado.",
                        Toast.LENGTH_LONG
                    ).show()
                    releaseAllMappedInputs()
                }
            }
            val e = MappingEngine(t, { areaRect }, { cfg })
            engine = e
            e.onAimChanged = { ui.post { overlay?.refresh() } }
            overlay = OverlayManager(
                this, displays, this,
                refreshHz = { cfg.refreshHz },
                onDelta = { dx, dy, eventTimeNanos, deviceId -> if (!editing) e.onDelta(dx, dy, eventTimeNanos, deviceId) },
                onButton = { bit, down -> if (!editing) e.onMouseButton(bit, down) },
                onKey = { code, down -> handleKey(code, down) },
                onScroll = { horizontal, vertical, deviceId -> if (!editing) e.onMouseScroll(horizontal, vertical, deviceId) },
                onCaptureStateChanged = { captured ->
                    if (!captured) releaseAllMappedInputs(e)
                },
                onCaptureRequestChanged = { requested ->
                    ShizukuInputCaptureBridge.setEnabled(requested && shouldNativeCaptureBeEnabled())
                }
            )
            overlay?.setNativeCapturePending(true)
            watcher = ForegroundWatcher { pkg -> onForeground(pkg) }
            displays.register(displayListener, ui)
            (getSystemService(INPUT_SERVICE) as InputManager).registerInputDeviceListener(inputListener, ui)
            if (!rebuild()) {
                throw IllegalStateException("Não foi possível anexar as janelas do KeyMapper ao display selecionado")
            }
            val nativeInitiallyEnabled = shouldNativeCaptureBeEnabled()
            ShizukuInputCaptureBridge.start(
                applicationContext,
                events = ::handleNativeInputBatch,
                state = { mouseCount, keyboardCount, enabled, detail ->
                    ui.post {
                        val wasNativeCaptureEnabled = nativeCaptureEnabled
                        nativeCaptureEnabled = enabled
                        // Binder/worker pode encerrar sem conseguir entregar SYN_DROPPED por device.
                        // A transição ativa -> inativa é um reset global seguro do bookkeeping nativo.
                        if (wasNativeCaptureEnabled && !enabled) releaseAllMappedInputs()
                        overlay?.setNativeCaptureState(mouseCount > 0, keyboardCount > 0, enabled)
                        InputDiagnostics.setBackend(
                            "Captura evdev: mouse=$mouseCount, teclado=$keyboardCount, ativa=$enabled. " +
                                "Saída touchscreen: $virtualTouchDescription. $detail. " +
                                "Fallback Pointer Capture permanece disponível quando necessário."
                        )
                    }
                },
                initiallyEnabled = nativeInitiallyEnabled,
                touchOutputState = { available, detail ->
                    virtualTouchDescription = if (available) {
                        "$detail; uinput automático somente no display principal (ID 0); DeX externo usa InputManager"
                    } else {
                        "$detail; InputManager via Shizuku será usado"
                    }
                    InputDiagnostics.setBackend("Saída touchscreen: $virtualTouchDescription")
                }
            )
            RestartDiagnostics.recordEvent(this, "setup concluído display=${info.displayId}; evdev solicitado=$nativeInitiallyEnabled")
            Log.i("KeyMapperLifecycle", "setup concluído; profile=${activeProfilePackage ?: "global"} display=${info.displayId}")
        } catch (e: Exception) {
            setupFailed = true
            RestartDiagnostics.recordEvent(this, "setup falhou: ${e.javaClass.simpleName}: ${e.message}")
            Log.e("KeyMapperLifecycle", "Falha na inicialização; limpando recursos e encerrando serviço para impedir estado parcial", e)
            runCatching { displays.unregister(displayListener) }
            runCatching { (getSystemService(INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(inputListener) }
            runCatching { watcher?.shutdown() }
            watcher = null
            runCatching { overlay?.hide() }
            overlay = null
            ShizukuInputCaptureBridge.stop()
            runCatching { releaseAllMappedInputs() }
            engine = null
            touch?.onFailure = null
            runCatching { touch?.shutdown() }
            touch = null
            runCatching { shizukuInjector?.close() }
            shizukuInjector = null
            InputDiagnostics.setBackend("Serviço encerrado: ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
            ui.post {
                Toast.makeText(this, "O KeyMapper não conseguiu inicializar. O serviço foi encerrado para evitar reinicializações repetidas.", Toast.LENGTH_LONG).show()
            }
            stopSelf()
        } finally {
            setupInProgress = false
        }
    }

    // ---- jogo em primeiro plano ----

    private val totalRamGb: Int by lazy {
        runCatching {
            val manager = getSystemService(ActivityManager::class.java)
            val memory = ActivityManager.MemoryInfo()
            manager.getMemoryInfo(memory)
            (memory.totalMem / (1024L * 1024L * 1024L)).toInt().coerceAtLeast(1)
        }.getOrDefault(4)
    }

    private fun settingsForPackage(base: AppSettings, packageName: String?): AppSettings {
        val profileApplied = if (packageName.isNullOrBlank()) base
            else base.profiles[packageName]?.applyTo(base) ?: base
        return profileApplied.copy(
            schedulingSlackMs = PerformancePolicy.extraSlackMs(profileApplied.perfMode, totalRamGb)
        )
    }

    private fun updateWatcher() {
        val w = watcher ?: return
        if (cfg.onlyInGame) {
            w.start()
        } else {
            w.stop()
            val hadProfile = activeProfilePackage != null
            if (hadProfile) pauseAndReleaseMappedInputs()
            activeProfilePackage = null
            activeProfilePackageSnapshot = null
            cfg = settingsForPackage(baseCfg, null)
            recomputeArea()
            if (!gameFocused) {
                gameFocused = true
                rebuild()
                ShizukuInputCaptureBridge.setEnabled(shouldNativeCaptureBeEnabled())
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
                pauseAndReleaseMappedInputs()
                gameFocused = true
                activeProfilePackage = pkg
                activeProfilePackageSnapshot = pkg
                cfg = settingsForPackage(baseCfg, pkg)
                recomputeArea()
                rebuild(preserveEnginePause = true)
                ShizukuInputCaptureBridge.setEnabled(shouldNativeCaptureBeEnabled())
                if (!paused) engine?.resumeAfterConfigurationChange()
            } else {
                activeProfilePackage = pkg
                activeProfilePackageSnapshot = pkg
                cfg = settingsForPackage(baseCfg, pkg)
                recomputeArea()
                overlay?.refresh()
            }
        } else if (gameFocused) {
            pauseAndReleaseMappedInputs()
            gameFocused = false
            activeProfilePackage = null
            activeProfilePackageSnapshot = null
            cfg = settingsForPackage(baseCfg, null)
            recomputeArea()
            ShizukuInputCaptureBridge.setEnabled(false)
            overlay?.hide()
        }
    }

    // ---- tela / DeX ----

    private fun refreshInfo() {
        try {
            val target = displays.target()
            val updated = displays.info(target)
            if (updated.width <= 0 || updated.height <= 0 || updated.displayId != target.displayId) {
                throw IllegalStateException("Métricas inválidas para o display selecionado")
            }
            info = updated
            displayTargetValid = true
        } catch (e: Exception) {
            // Mantém a última geometria para diagnóstico/layout, mas invalida o destino de entrada.
            // Assim, um monitor removido nunca faz o evento migrar para o display principal.
            displayTargetValid = false
            Log.w("KeyMapper", "Display selecionado indisponível; injeção suspensa até reconexão", e)
            InputDiagnostics.setBackend("Captura: Pointer Capture; saída suspensa: display selecionado indisponível")
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
            val change = ScreenGeometryPolicy.classify(
                ScreenGeometryPolicy.Snapshot(old.displayId, old.width, old.height, old.densityDpi),
                ScreenGeometryPolicy.Snapshot(cur.displayId, cur.width, cur.height, cur.densityDpi)
            )
            when (change) {
                ScreenGeometryPolicy.Change.REINITIALIZE ->
                    rebuild() // outro monitor ou densidade: recria as janelas com o contexto correto
                ScreenGeometryPolicy.Change.RESIZE ->
                    rebuild(preserveEnginePause = true, preserveEditing = true) // rotação/resize preserva o estado da sessão
                ScreenGeometryPolicy.Change.NONE -> overlay?.refresh()
            }
        } catch (e: Exception) {
            Log.w("KeyMapper", "Falha ao atualizar a área após mudança de tela/DeX", e)
        }
    }

    private fun rebuild(
        preserveEnginePause: Boolean = false,
        preserveEditing: Boolean = false
    ): Boolean {
        val ov = overlay ?: return false
        val wasEditing = editing
        refreshInfo()
        releaseAllMappedInputs()
        editing = preserveEditing && wasEditing
        if (!preserveEnginePause) {
            paused = false
            engine?.paused = false
        }
        if (cfg.onlyInGame && !gameFocused) {
            ov.hide()
            return true
        }
        val overlayReady = ov.show() // restaura o overlay no monitor atual
        if (!overlayReady) {
            paused = true
            engine?.paused = true
            releaseAllMappedInputs()
            val reason = if (android.provider.Settings.canDrawOverlays(this)) {
                "Não foi possível anexar as janelas do KeyMapper ao display selecionado"
            } else {
                "Permissão Exibir sobre outros apps foi revogada"
            }
            InputStateMachine.transition(InputState.ERROR, reason, force = true)
            InputDiagnostics.setBackend("Mapeamento interrompido: $reason")
            ui.post {
                Toast.makeText(this, "$reason. Reative a permissão e inicie o mapeamento novamente.", Toast.LENGTH_LONG).show()
            }
            if (touch != null) stopSelf()
            return false
        }
        // A nova instância de HudView começa fora do modo edição; reaplica o estado após show().
        if (editing) {
            ov.setEditing(true)
        } else if (paused) {
            ov.setCapture(false)
        }
        return true
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
        // Capture immutable snapshots before launching: never read mutable service state later.
        val packageName = activeProfilePackage
        val snapshot = cfg
        val baseSnapshot = baseCfg
        (application as KeyMapperApp).persistenceScope.launch {
            try {
                if (packageName != null) {
                    repo.saveProfile(packageName, AppProfile.fromSettings(snapshot), selected = false)
                } else {
                    repo.save(snapshot.copy(
                        profiles = baseSnapshot.profiles,
                        selectedProfilePackage = baseSnapshot.selectedProfilePackage
                    ))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                Log.e("KeyMapper", "Falha ao persistir as configurações", e)
                ui.post {
                    if (running) Toast.makeText(
                        this@MappingService,
                        "Não foi possível salvar as configurações.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
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
        releaseAllMappedInputs()
        overlay?.setEditing(on)
    }

    // ---- menu rápido ----

    override fun openQuickMenu() {
        val ov = overlay ?: return
        if (ov.quickMenuOpen) {
            ov.closeQuickMenu()
            return
        }
        releaseAllMappedInputs()
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
        releaseAllMappedInputs()
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
        startRequestPending.set(false)
        RestartDiagnostics.recordEvent(this, "onStartCommand startId=$startId action=${intent?.action ?: "(null)"} flags=$flags")
        Log.i("KeyMapperLifecycle", "onStartCommand startId=$startId action=${intent?.action ?: "(null)"} flags=$flags")
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            ACTION_EDIT -> setEditing(!editing)
            ACTION_BEGIN_EDIT -> {
                if (overlay?.canOpenConfig() == true) setEditing(true)
                else Toast.makeText(this, "O HUD ainda não está disponível no display selecionado.", Toast.LENGTH_LONG).show()
            }
            ACTION_ADD_STANDARD_CONTROL -> addStandardControl()
            ACTION_CONFIG_STANDARD_BUTTON -> {
                val id = intent?.getIntExtra(EXTRA_BUTTON_ID, -1) ?: -1
                val target = cfg.buttons.firstOrNull { it.id == id && it.kind == Kind.STANDARD }
                if (target == null || overlay?.canOpenConfig() != true) {
                    Toast.makeText(this, "Este controle não está disponível no perfil ativo.", Toast.LENGTH_LONG).show()
                } else {
                    overlay?.openConfig(id)
                }
            }
        }
        // Não ressuscitar o motor automaticamente após encerramento pelo sistema.
        // O usuário pode reativá-lo explicitamente; autoStart é tratado pelo receiver.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        RestartDiagnostics.recordEvent(this, "MappingService.onDestroy setupFailed=$setupFailed")
        Log.i("KeyMapperLifecycle", "MappingService.onDestroy pid=${android.os.Process.myPid()} setupFailed=$setupFailed")
        running = false
        startRequestPending.set(false)
        activeProfilePackageSnapshot = null
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
        ShizukuInputCaptureBridge.stop()
        try {
            overlay?.hide()
        } catch (e: Exception) {
            Log.w("KeyMapper", "Falha ao ocultar overlay durante o encerramento", e)
        }
        overlay = null
        releaseAllMappedInputs()
        engine = null
        touch?.onFailure = null
        touch?.shutdown()
        touch = null
        runCatching { shizukuInjector?.close() }
        shizukuInjector = null
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
