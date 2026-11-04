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
 * Bind temporário a um UserService Shizuku. O código executa sob UID shell/ADB (2000)
 * ou root (0), não sob o UID restrito do aplicativo. Somente operações diagnósticas
 * explicitamente definidas no AIDL são expostas; não existe executor de comandos arbitrários.
 */
object ShizukuShellBridge {
    private const val TAG = "KeyMapperShellBridge"
    private const val BIND_TIMEOUT_MS = 12_000L
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "keymapper-shizuku-probe").apply { isDaemon = true }
    }
    private val main = Handler(Looper.getMainLooper())

    fun inspectRuntime(context: Context, callback: (String) -> Unit) {
        val app = context.applicationContext
        val completed = AtomicBoolean(false)
        val bindingRequested = AtomicBoolean(false)
        val args = Shizuku.UserServiceArgs(
            ComponentName(app, KeyMapperShellService::class.java)
        )
            .daemon(false)
            .processNameSuffix("keymapper-shell-probe")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)

        lateinit var connection: ServiceConnection
        lateinit var timeout: Runnable

        fun finish(result: String) {
            if (!completed.compareAndSet(false, true)) return
            main.removeCallbacks(timeout)
            if (bindingRequested.get()) {
                runCatching { Shizuku.unbindUserService(args, connection, true) }
                    .onFailure { Log.w(TAG, "Falha ao liberar UserService de diagnóstico", it) }
            }
            main.post { callback(result) }
        }

        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder == null) {
                    finish("UserService do Shizuku conectou sem Binder válido.")
                    return
                }
                val remote = IKeyMapperShellService.Stub.asInterface(binder)
                worker.execute {
                    val report = try {
                        remote.inspectRuntime()
                    } catch (e: RemoteException) {
                        "Falha de transporte do UserService: ${e.javaClass.simpleName}: ${e.message}"
                    } catch (e: Throwable) {
                        "Falha na sondagem privilegiada: ${e.javaClass.simpleName}: ${e.message}"
                    }
                    finish(report)
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                finish("O processo privilegiado do Shizuku foi desconectado durante a sondagem.")
            }

            override fun onBindingDied(name: ComponentName?) {
                finish("O vínculo do UserService morreu. Reative o Shizuku e tente novamente.")
            }

            override fun onNullBinding(name: ComponentName?) {
                finish("O Shizuku retornou um vínculo vazio para o UserService.")
            }
        }

        timeout = Runnable {
            finish("Tempo limite de ${BIND_TIMEOUT_MS / 1000}s ao iniciar o UserService. Verifique Shizuku e sua autorização.")
        }
        main.postDelayed(timeout, BIND_TIMEOUT_MS)

        worker.execute {
            try {
                if (completed.get()) return@execute
                if (!Shizuku.pingBinder()) {
                    finish("Shizuku não está ativo. Inicie-o por Depuração sem fio ou root.")
                    return@execute
                }
                if (Shizuku.checkSelfPermission() != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    finish("O KeyMapper ainda não recebeu autorização do Shizuku.")
                    return@execute
                }
                if (Shizuku.getVersion() < 10) {
                    finish("A versão do servidor Shizuku é antiga demais para UserService. Atualize o Shizuku.")
                    return@execute
                }
                if (completed.get()) return@execute
                Shizuku.bindUserService(args, connection)
                bindingRequested.set(true)
                if (completed.get()) {
                    runCatching { Shizuku.unbindUserService(args, connection, true) }
                        .onFailure { Log.w(TAG, "Falha ao liberar UserService após timeout", it) }
                }
            } catch (t: Throwable) {
                finish("Não foi possível iniciar o UserService: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }
}
