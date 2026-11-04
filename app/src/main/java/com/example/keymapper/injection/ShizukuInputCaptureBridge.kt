package com.example.keymapper.injection

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import com.example.keymapper.BuildConfig
import rikka.shizuku.Shizuku
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Mantém o UserService privilegiado vivo durante a sessão de mapeamento.
 * O processo remoto lê evdev por JNI; este bridge só transporta lotes compactos via AIDL.
 */
object ShizukuInputCaptureBridge : VirtualTouchOutput {
    private const val TAG = "KeyMapperEvdevBridge"
    private const val BIND_TIMEOUT_MS = 15_000L
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "keymapper-evdev-bridge").apply { isDaemon = true }
    }
    private val active = AtomicBoolean(false)
    private val sessionEpoch = SessionEpoch()
    private val virtualTouchRecoveryRunning = AtomicBoolean(false)
    @Volatile private var lastVirtualTouchRecoveryAtMs = 0L

    @Volatile private var remote: IKeyMapperShellService? = null
    @Volatile private var onEvents: ((LongArray) -> Unit)? = null
    @Volatile private var onState: ((Int, Int, Boolean, String) -> Unit)? = null
    @Volatile private var onTouchOutputState: ((Boolean, String) -> Unit)? = null
    @Volatile private var virtualTouchReady = false
    private var args: Shizuku.UserServiceArgs? = null
    private var connection: ServiceConnection? = null
    private var binding = false
    private var timeout: Runnable? = null
    private var reconnectTask: Runnable? = null
    private var reconnectAttempts = 0
    private var binderReceivedListenerRegistered = false
    private var binderDeadListenerRegistered = false
    private var permissionResultListenerRegistered = false
    @Volatile private var appContext: Context? = null
    @Volatile private var captureStarted = false
    // Accessed on the main thread; serializes start/restart requests while Binder work is pending.
    private var captureStarting = false
    private var requestedEnabled = true

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        main.post {
            if (!active.get()) return@post
            reconnectAttempts = 0
            cancelReconnect()
            if (remote == null && !binding && connection == null) restartFromSavedRequest()
        }
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        main.post {
            if (!active.get()) return@post
            resetConnectionForRebind("Servidor Shizuku desconectado")
            publishVirtualTouchState(false, "Servidor Shizuku desconectado; uinput indisponível")
            onState?.invoke(0, 0, false, "Servidor Shizuku desconectado; aguardando reconexão")
        }
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            main.post {
                if (active.get() && remote == null && !binding && connection == null) {
                    reconnectAttempts = 0
                    cancelReconnect()
                    restartFromSavedRequest()
                }
            }
        }
    }

    @Volatile private var inputCallback: IKeyMapperInputCallback? = null

    /** Cada Binder recebe um callback associado à sessão que o criou. */
    private fun createInputCallback(sessionToken: Long): IKeyMapperInputCallback =
        object : IKeyMapperInputCallback.Stub() {
            override fun onInputEvents(records: LongArray) {
                if (active.get() && sessionEpoch.isCurrent(sessionToken) && records.isNotEmpty()) {
                    onEvents?.invoke(records)
                }
            }

            override fun onCaptureState(
                mouseDevices: Int,
                keyboardDevices: Int,
                enabled: Boolean,
                detail: String?
            ) {
                if (active.get() && sessionEpoch.isCurrent(sessionToken)) {
                    onState?.invoke(
                        mouseDevices.coerceAtLeast(0), keyboardDevices.coerceAtLeast(0), enabled,
                        detail ?: "Backend nativo sem detalhes"
                    )
                }
            }
        }

    fun start(
        context: Context,
        events: (LongArray) -> Unit,
        state: (Int, Int, Boolean, String) -> Unit,
        initiallyEnabled: Boolean = true,
        touchOutputState: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        main.post {
            appContext = context.applicationContext
            onEvents = events
            onState = state
            onTouchOutputState = touchOutputState
            requestedEnabled = initiallyEnabled
            inputCallback = createInputCallback(sessionEpoch.begin())
            active.set(true)
            registerLifecycleListeners()
            val connected = remote
            if (connected != null) {
                touchOutputState(virtualTouchReady, if (virtualTouchReady) "uinput touchscreen pronto" else "uinput ainda indisponível; InputManager será usado")
                if (captureStarted) setEnabled(initiallyEnabled)
                else startRemoteCapture(connected, initiallyEnabled)
                return@post
            }
            // Um vínculo conectado, mas temporariamente sem Binder, pode reconectar sozinho.
            // Não cria um segundo UserService enquanto a conexão antiga ainda está viva.
            if (binding || connection != null) return@post
            val binderReady = runCatching { Shizuku.pingBinder() }
                .onFailure { Log.w(TAG, "Falha consultando o Binder Shizuku", it) }
                .getOrDefault(false)
            if (!binderReady) {
                state(0, 0, false, "Shizuku não está ativo; captura Android como fallback; aguardando Binder")
                return@post
            }
            val permissionGranted = runCatching { Shizuku.checkSelfPermission() }
                .onFailure { Log.w(TAG, "Falha consultando a permissão Shizuku", it) }
                .getOrDefault(android.content.pm.PackageManager.PERMISSION_DENIED) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!permissionGranted) {
                state(0, 0, false, "Shizuku sem autorização; captura Android como fallback")
                return@post
            }
            val serverVersion = runCatching { Shizuku.getVersion() }
                .onFailure { Log.w(TAG, "Falha consultando a versão do servidor Shizuku", it) }
                .getOrDefault(0)
            if (serverVersion < 10) {
                state(0, 0, false, "Servidor Shizuku desatualizado; atualize para usar captura nativa")
                return@post
            }

            val app = context.applicationContext
            val userArgs = Shizuku.UserServiceArgs(ComponentName(app, KeyMapperShellService::class.java))
                .daemon(false)
                .processNameSuffix("keymapper-native-input")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)
            val conn = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (!active.get() || connection !== this) return
                    if (binder == null) {
                        captureStarting = false
                        captureStarted = false
                        timeout?.let(main::removeCallbacks)
                        timeout = null
                        abandonBinding(userArgs, this)
                        state(0, 0, false, "UserService conectado sem Binder; fallback Android ativo")
                        scheduleReconnect("UserService conectado sem Binder")
                        return
                    }
                    val service = IKeyMapperShellService.Stub.asInterface(binder)
                    remote = service
                    // A conexão Binder, sozinha, não comprova que a captura evdev iniciou.
                    // O orçamento de retries só será zerado após startInputCapture() retornar true.
                    cancelReconnect()
                    binding = false
                    timeout?.let(main::removeCallbacks)
                    timeout = null
                    startRemoteCapture(service, requestedEnabled)
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    if (connection !== this) return
                    remote = null
                    captureStarted = false
                    captureStarting = false
                    binding = false
                    timeout?.let(main::removeCallbacks)
                    timeout = null
                    publishVirtualTouchState(false, "Processo privilegiado desconectado; uinput indisponível")
                    if (active.get()) onState?.invoke(0, 0, false,
                        "Processo privilegiado desconectado; fallback Android ativo")
                    // Não desassocia aqui: o Android pode reconectar o mesmo vínculo quando
                    // o processo remoto voltar. Um novo start() também pode solicitar outro bind.
                }

                override fun onBindingDied(name: ComponentName?) {
                    if (connection !== this) return
                    resetConnectionForRebind("Vínculo Shizuku morreu")
                    publishVirtualTouchState(false, "Vínculo Shizuku morreu; uinput indisponível")
                    if (active.get()) {
                        onState?.invoke(0, 0, false, "Vínculo Shizuku morreu; tentando restabelecer captura")
                        scheduleReconnect("Vínculo Shizuku morreu")
                    }
                }

                override fun onNullBinding(name: ComponentName?) {
                    if (connection !== this) return
                    resetConnectionForRebind("UserService retornou vínculo vazio")
                    publishVirtualTouchState(false, "Shizuku retornou vínculo vazio; uinput indisponível")
                    if (active.get()) {
                        onState?.invoke(0, 0, false, "UserService retornou vínculo vazio; tentando novamente")
                        scheduleReconnect("UserService retornou vínculo vazio")
                    }
                }
            }
            args = userArgs
            connection = conn
            active.set(true)
            binding = true
            val timeoutTask = Runnable {
                if (binding && remote == null && connection === conn) {
                    captureStarting = false
                    captureStarted = false
                    publishVirtualTouchState(false, "Tempo limite ao iniciar UserService; uinput indisponível")
                    abandonBinding(userArgs, conn)
                    onState?.invoke(0, 0, false,
                        "Tempo limite ao iniciar serviço nativo; captura Android permanece como fallback")
                    scheduleReconnect("Tempo limite ao iniciar UserService")
                }
            }
            timeout = timeoutTask
            main.postDelayed(timeoutTask, BIND_TIMEOUT_MS)
            try {
                Shizuku.bindUserService(userArgs, conn)
            } catch (t: Throwable) {
                binding = false
                timeout?.let(main::removeCallbacks)
                timeout = null
                publishVirtualTouchState(false, "Falha vinculando UserService: ${t.javaClass.simpleName}")
                abandonBinding(userArgs, conn)
                Log.e(TAG, "Falha vinculando UserService nativo", t)
                state(0, 0, false, "Não foi possível iniciar UserService: ${t.javaClass.simpleName}: ${t.message}")
                scheduleReconnect("Falha vinculando UserService")
            }
        }
    }

    private fun registerLifecycleListeners() {
        // Registre cada listener de forma independente. Se uma chamada falhar por Binder
        // indisponível, as demais continuam e uma chamada futura a start() tenta somente
        // os listeners que ainda não foram registrados.
        if (!binderReceivedListenerRegistered) {
            binderReceivedListenerRegistered = runCatching {
                Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
                true
            }.onFailure {
                Log.w(TAG, "Não foi possível registrar reconexão do Binder Shizuku", it)
            }.getOrDefault(false)
        }
        if (!binderDeadListenerRegistered) {
            binderDeadListenerRegistered = runCatching {
                Shizuku.addBinderDeadListener(binderDeadListener)
                true
            }.onFailure {
                Log.w(TAG, "Não foi possível registrar queda do Binder Shizuku", it)
            }.getOrDefault(false)
        }
        if (!permissionResultListenerRegistered) {
            permissionResultListenerRegistered = runCatching {
                Shizuku.addRequestPermissionResultListener(permissionResultListener)
                true
            }.onFailure {
                Log.w(TAG, "Não foi possível observar alteração da permissão Shizuku", it)
            }.getOrDefault(false)
        }
    }

    private fun unregisterLifecycleListeners() {
        if (binderReceivedListenerRegistered) {
            runCatching { Shizuku.removeBinderReceivedListener(binderReceivedListener) }
            binderReceivedListenerRegistered = false
        }
        if (binderDeadListenerRegistered) {
            runCatching { Shizuku.removeBinderDeadListener(binderDeadListener) }
            binderDeadListenerRegistered = false
        }
        if (permissionResultListenerRegistered) {
            runCatching { Shizuku.removeRequestPermissionResultListener(permissionResultListener) }
            permissionResultListenerRegistered = false
        }
    }

    private fun restartFromSavedRequest() {
        val context = appContext ?: return
        val events = onEvents ?: return
        val state = onState ?: return
        val touchState: (Boolean, String) -> Unit = onTouchOutputState ?: { _, _ -> }
        start(context, events, state, requestedEnabled, touchState)
    }

    private fun cancelReconnect() {
        reconnectTask?.let(main::removeCallbacks)
        reconnectTask = null
    }

    private fun scheduleReconnect(reason: String) {
        if (!active.get() || reconnectTask != null) return
        if (!UserServiceReconnectPolicy.shouldRetry(reconnectAttempts)) {
            onState?.invoke(0, 0, false, "$reason; tentativas automáticas esgotadas; aguardando nova solicitação de captura")
            return
        }
        val delay = UserServiceReconnectPolicy.delayMillis(reconnectAttempts + 1)
        reconnectAttempts++
        val task = Runnable {
            reconnectTask = null
            if (active.get() && remote == null && !binding && connection == null) restartFromSavedRequest()
        }
        reconnectTask = task
        Log.w(TAG, "$reason; nova tentativa de vínculo em ${delay}ms (${reconnectAttempts}/${UserServiceReconnectPolicy.MAX_AUTOMATIC_RETRIES})")
        main.postDelayed(task, delay)
    }

    private fun resetConnectionForRebind(reason: String) {
        val oldArgs = args
        val oldConnection = connection
        remote = null
        args = null
        connection = null
        binding = false
        captureStarted = false
        captureStarting = false
        timeout?.let(main::removeCallbacks)
        timeout = null
        if (oldArgs != null && oldConnection != null) {
            runCatching { Shizuku.unbindUserService(oldArgs, oldConnection, true) }
                .onFailure { Log.w(TAG, "Falha removendo vínculo antigo durante $reason", it) }
        }
    }

    private fun handleRemoteFailure(service: IKeyMapperShellService, reason: String) {
        main.post {
            if (!active.get() || remote !== service) return@post
            resetConnectionForRebind(reason)
            publishVirtualTouchState(false, "$reason; uinput indisponível")
            onState?.invoke(0, 0, false, "$reason; aguardando reconexão do UserService")
            scheduleReconnect(reason)
        }
    }

    /** Inicia/reinicia o leitor no mesmo Binder, evitando bind duplicado após falha de JNI. */
    private fun startRemoteCapture(service: IKeyMapperShellService, enabled: Boolean) {
        requestedEnabled = enabled
        if (captureStarting) return
        val sessionToken = sessionEpoch.currentToken()
        val callback = inputCallback ?: return
        if (!active.get() || !sessionEpoch.isCurrent(sessionToken)) return
        captureStarting = true
        virtualTouchRecoveryRunning.set(true)
        worker.execute {
            val result = runCatching { service.startInputCapture(callback, enabled) }
            // A saída touchscreen é sondada separadamente: a captura evdev pode falhar e
            // ainda assim o sistema pode permitir uinput, ou o inverso.
            val touchOpen = runCatching { service.openVirtualTouch(32) }
            val touchDetailResult = runCatching { service.virtualTouchStatus() }
            val touchDetail = touchDetailResult.getOrElse {
                "Não foi possível consultar uinput: ${it.javaClass.simpleName}: ${it.message}"
            }
            main.post {
                // Um worker da sessão anterior não pode liberar o lock de recuperação de uma
                // sessão nova. Só o proprietário ainda válido limpa o seu estado compartilhado.
                if (!active.get() || !sessionEpoch.isCurrent(sessionToken) || remote !== service) return@post
                virtualTouchRecoveryRunning.set(false)
                publishVirtualTouchState(touchOpen.getOrDefault(false), touchDetail, sessionToken)
                captureStarting = false
                captureStarted = result.getOrDefault(false)
                reconnectAttempts = UserServiceReconnectPolicy.afterCaptureStart(
                    reconnectAttempts,
                    captureStarted
                )
                if (touchOpen.exceptionOrNull() is RemoteException ||
                    touchDetailResult.exceptionOrNull() is RemoteException
                ) {
                    handleRemoteFailure(service, "Binder falhou ao consultar touchscreen virtual")
                    return@post
                }
                if (captureStarted) {
                    // Uma pausa/retomada pode ter chegado enquanto startInputCapture aguardava Binder.
                    val latestEnabled = requestedEnabled
                    if (latestEnabled != enabled) setEnabled(latestEnabled)
                } else {
                    val error = result.exceptionOrNull()
                    onState?.invoke(
                        0, 0, false,
                        "Backend evdev não iniciou${error?.let { ": ${it.javaClass.simpleName}: ${it.message}" } ?: "; captura Android como fallback"}"
                    )
                    if (error is RemoteException) handleRemoteFailure(service, "Binder falhou ao iniciar captura")
                }
            }
        }
    }

    private fun publishVirtualTouchState(
        ready: Boolean,
        detail: String,
        sessionToken: Long = sessionEpoch.currentToken()
    ) {
        if (!active.get() || !sessionEpoch.isCurrent(sessionToken)) return
        virtualTouchReady = ready
        val callback = onTouchOutputState ?: return
        main.post {
            if (active.get() && sessionEpoch.isCurrent(sessionToken) && onTouchOutputState === callback) {
                callback(ready, detail)
            }
        }
    }

    override fun isAvailable(): Boolean {
        val service = remote
        val available = active.get() && service != null && virtualTouchReady
        if (!available && active.get() && service != null) requestVirtualTouchRecovery(service)
        return available
    }

    /** Reabre o touchscreen virtual após falha do descritor, com serialização e cooldown. */
    private fun requestVirtualTouchRecovery(service: IKeyMapperShellService) {
        if (!active.get() || remote !== service || virtualTouchReady) return
        val sessionToken = sessionEpoch.currentToken()
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastVirtualTouchRecoveryAtMs < 5_000L) return
        if (!virtualTouchRecoveryRunning.compareAndSet(false, true)) return
        lastVirtualTouchRecoveryAtMs = now
        worker.execute {
            val opened = runCatching { service.openVirtualTouch(32) }
            val detailResult = runCatching { service.virtualTouchStatus() }
            val detail = detailResult.getOrElse {
                "Falha consultando uinput após recuperação: ${it.javaClass.simpleName}: ${it.message}"
            }
            main.post {
                if (!active.get() || !sessionEpoch.isCurrent(sessionToken) || remote !== service) return@post
                virtualTouchRecoveryRunning.set(false)
                if (opened.exceptionOrNull() is RemoteException || detailResult.exceptionOrNull() is RemoteException) {
                    handleRemoteFailure(service, "Binder falhou ao recuperar touchscreen virtual")
                    return@post
                }
                publishVirtualTouchState(
                    opened.getOrDefault(false),
                    if (opened.getOrDefault(false)) detail else "Recuperação uinput falhou: $detail"
                )
            }
        }
    }

    override fun sendFrame(
        actionMasked: Int,
        actionIndex: Int,
        pointerCount: Int,
        pointerIds: IntArray,
        pointerXs: FloatArray,
        pointerYs: FloatArray
    ): Boolean {
        val service = remote ?: return false
        val sessionToken = sessionEpoch.currentToken()
        if (!active.get() || !sessionEpoch.isCurrent(sessionToken) || !virtualTouchReady) return false
        return try {
            val accepted = service.sendVirtualTouchFrame(
                actionMasked, actionIndex, pointerCount, pointerIds, pointerXs, pointerYs
            )
            if (!active.get() || !sessionEpoch.isCurrent(sessionToken) || remote !== service) return false
            if (!accepted && !runCatching { service.isVirtualTouchOpen() }.getOrDefault(false)) {
                val detail = runCatching { service.virtualTouchStatus() }
                    .getOrDefault("uinput deixou de estar aberto")
                publishVirtualTouchState(false, detail, sessionToken)
            }
            accepted
        } catch (t: Throwable) {
            Log.w(TAG, "Falha no transporte AIDL do touchscreen uinput", t)
            publishVirtualTouchState(false, "Transporte uinput caiu: ${t.javaClass.simpleName}: ${t.message}", sessionToken)
            if (t is RemoteException) handleRemoteFailure(service, "Binder falhou no touchscreen virtual")
            false
        }
    }

    override fun releaseAll(): Boolean {
        val service = remote ?: return false
        val sessionToken = sessionEpoch.currentToken()
        if (!active.get() || !sessionEpoch.isCurrent(sessionToken) || !virtualTouchReady) return false
        return try {
            val released = service.releaseVirtualTouch()
            if (!active.get() || !sessionEpoch.isCurrent(sessionToken) || remote !== service) return false
            if (!runCatching { service.isVirtualTouchOpen() }.getOrDefault(false)) {
                val detail = runCatching { service.virtualTouchStatus() }
                    .getOrDefault("uinput deixou de estar aberto")
                publishVirtualTouchState(false, detail, sessionToken)
            }
            released
        } catch (t: Throwable) {
            Log.w(TAG, "Falha liberando contatos uinput via AIDL", t)
            publishVirtualTouchState(false, "Não foi possível liberar uinput: ${t.javaClass.simpleName}", sessionToken)
            if (t is RemoteException) handleRemoteFailure(service, "Binder falhou ao liberar touchscreen virtual")
            false
        }
    }

    /** Remove um ServiceConnection que não entregou Binder utilizável ou excedeu o timeout. */
    private fun abandonBinding(userArgs: Shizuku.UserServiceArgs, conn: ServiceConnection) {
        binding = false
        if (connection === conn) {
            connection = null
            args = null
            remote = null
        }
        runCatching { Shizuku.unbindUserService(userArgs, conn, true) }
            .onFailure { Log.w(TAG, "Falha limpando UserService sem Binder", it) }
    }

    fun setEnabled(enabled: Boolean) {
        main.post {
            requestedEnabled = enabled
            val service = remote
            if (service == null) {
                // Após esgotar a rodada automática, uma nova solicitação explícita de captura
                // pode iniciar outra rodada limitada. Não reabre o serviço quando a captura
                // está sendo desativada, nem cria vínculo duplicado enquanto há um ativo.
                if (enabled && active.get() && !binding && connection == null && reconnectTask == null) {
                    val binderReady = runCatching { Shizuku.pingBinder() }
                        .onFailure { Log.w(TAG, "Falha verificando Binder para nova tentativa", it) }
                        .getOrDefault(false)
                    val permissionGranted = binderReady && runCatching {
                        Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
                    }.onFailure { Log.w(TAG, "Falha verificando permissão para nova tentativa", it) }
                        .getOrDefault(false)
                    val serverVersion = if (binderReady && permissionGranted) {
                        runCatching { Shizuku.getVersion() }
                            .onFailure { Log.w(TAG, "Falha verificando versão Shizuku para nova tentativa", it) }
                            .getOrDefault(0)
                    } else {
                        0
                    }
                    val shouldRetry = UserServiceReconnectPolicy.shouldAttemptExplicitRetry(
                        active = active.get(),
                        requestedEnabled = enabled,
                        binding = binding,
                        hasConnection = connection != null,
                        retryScheduled = reconnectTask != null,
                        binderReady = binderReady,
                        permissionGranted = permissionGranted,
                        serverVersion = serverVersion
                    )
                    if (shouldRetry) {
                        reconnectAttempts = 0
                        restartFromSavedRequest()
                    }
                }
                return@post
            }
            if (!captureStarted || captureStarting) {
                startRemoteCapture(service, enabled)
                return@post
            }
            worker.execute {
                try {
                    service.setInputCaptureEnabled(enabled)
                } catch (e: RemoteException) {
                    Log.w(TAG, "Falha alterando estado da captura nativa", e)
                    handleRemoteFailure(service, "Binder falhou ao alterar captura")
                } catch (t: Throwable) {
                    Log.e(TAG, "Erro alterando estado da captura nativa", t)
                    if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
                        handleRemoteFailure(service, "UserService deixou de responder")
                    }
                }
            }
        }
    }

    fun stop() {
        // Invalidate immediately on the caller thread. Binder callbacks arriving before the
        // main-thread cleanup is queued must already be rejected.
        sessionEpoch.invalidate()
        active.set(false)
        main.post {
            // A start queued before this cleanup must not leave its callback generation valid.
            sessionEpoch.invalidate()
            active.set(false)
            cancelReconnect()
            unregisterLifecycleListeners()
            timeout?.let(main::removeCallbacks)
            timeout = null
            val service = remote
            val userArgs = args
            val conn = connection
            remote = null
            args = null
            connection = null
            binding = false
            captureStarted = false
            captureStarting = false
            requestedEnabled = false
            virtualTouchReady = false
            virtualTouchRecoveryRunning.set(false)
            lastVirtualTouchRecoveryAtMs = 0L
            inputCallback = null
            onEvents = null
            onState = null
            onTouchOutputState = null
            appContext = null
            reconnectAttempts = 0
            worker.execute {
                runCatching { service?.stopInputCapture() }
                if (userArgs != null && conn != null) {
                    main.post {
                        runCatching { Shizuku.unbindUserService(userArgs, conn, true) }
                            .onFailure { Log.w(TAG, "Falha liberando UserService nativo", it) }
                    }
                }
            }
        }
    }
}
