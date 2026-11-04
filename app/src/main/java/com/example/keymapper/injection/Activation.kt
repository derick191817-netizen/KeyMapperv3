package com.example.keymapper.injection

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku
import rikka.shizuku.SystemServiceHelper

/** Estado real do caminho Shizuku -> InputManager, não apenas presença do app Shizuku. */
enum class ActivationState {
    READY,
    NEEDS_PERMISSION,
    INPUT_PERMISSION_MISSING,
    INPUT_SERVICE_UNAVAILABLE,
    SHIZUKU_STOPPED,
    SHIZUKU_MISSING
}


object ActivationPolicy {
    fun resolve(
        installed: Boolean,
        binderAlive: Boolean,
        appPermissionGranted: Boolean,
        remoteInjectionPermissionGranted: Boolean,
        inputServiceAvailable: Boolean
    ): ActivationState = when {
        !installed -> ActivationState.SHIZUKU_MISSING
        !binderAlive -> ActivationState.SHIZUKU_STOPPED
        !appPermissionGranted -> ActivationState.NEEDS_PERMISSION
        !remoteInjectionPermissionGranted -> ActivationState.INPUT_PERMISSION_MISSING
        !inputServiceAvailable -> ActivationState.INPUT_SERVICE_UNAVAILABLE
        else -> ActivationState.READY
    }
}

object Activation {
    const val REQUEST_CODE = 1001
    private const val TAG = "KeyMapperActivation"
    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    fun state(ctx: Context): ActivationState {
        val installed = try {
            ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
            true
        } catch (_: Exception) {
            false
        }
        if (!installed) return ActivationState.SHIZUKU_MISSING

        return try {
            val binderAlive = Shizuku.pingBinder()
            val appPermission = binderAlive &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            val remoteInjectionPermission = appPermission &&
                Shizuku.checkRemotePermission("android.permission.INJECT_EVENTS") == PackageManager.PERMISSION_GRANTED
            val inputServiceAvailable = remoteInjectionPermission &&
                SystemServiceHelper.getSystemService("input") != null
            ActivationPolicy.resolve(
                installed = installed,
                binderAlive = binderAlive,
                appPermissionGranted = appPermission,
                remoteInjectionPermissionGranted = remoteInjectionPermission,
                inputServiceAvailable = inputServiceAvailable
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Não foi possível validar a sessão privilegiada", t)
            ActivationState.SHIZUKU_STOPPED
        }
    }

    /** true significa que a solicitação foi enviada; o resultado chega pelo listener Shizuku. */
    fun requestPermission(): Boolean {
        return try {
            when {
                !Shizuku.pingBinder() -> false
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> true
                else -> {
                    Shizuku.requestPermission(REQUEST_CODE)
                    true
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Falha ao solicitar autorização do Shizuku", t)
            false
        }
    }

    fun guide(state: ActivationState, sdk: Int): String = when (state) {
        ActivationState.READY -> "Pronto: Shizuku, serviço InputManager e permissão INJECT_EVENTS disponíveis."
        ActivationState.NEEDS_PERMISSION -> "Shizuku ativo. Autorize o KeyMapper na janela de permissão do Shizuku."
        ActivationState.INPUT_PERMISSION_MISSING ->
            "A sessão Shizuku está autorizada, mas a identidade atual não recebeu INJECT_EVENTS. O KeyMapper não deve declarar injeção disponível. Verifique se o Shizuku foi iniciado como shell/ADB ou root e as limitações da ROM."
        ActivationState.INPUT_SERVICE_UNAVAILABLE ->
            "A sessão Shizuku está autorizada, mas o serviço Binder InputManager não está disponível nesta sessão."
        ActivationState.SHIZUKU_MISSING ->
            "Instale o Shizuku pela fonte oficial, abra-o e volte ao KeyMapper."
        ActivationState.SHIZUKU_STOPPED ->
            if (sdk >= 30)
                "Abra o Shizuku e inicie por Depuração sem fio: Opções do desenvolvedor › Depuração sem fio. O pareamento é concluído pelo próprio Shizuku e precisa ser repetido após reiniciar o celular."
            else
                "Android 10 ou inferior: conecte o aparelho a um computador e siga o procedimento oficial de inicialização ADB do Shizuku."
    }
}
