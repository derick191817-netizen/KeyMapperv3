package com.example.keymapper.injection

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

enum class ActivationState { READY, NEEDS_PERMISSION, SHIZUKU_STOPPED, SHIZUKU_MISSING }

object Activation {
    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"

    fun state(ctx: Context): ActivationState {
        val installed = try {
            ctx.packageManager.getPackageInfo(SHIZUKU_PKG, 0)
            true
        } catch (e: Exception) {
            false
        }
        if (!installed) return ActivationState.SHIZUKU_MISSING
        return try {
            when {
                !Shizuku.pingBinder() -> ActivationState.SHIZUKU_STOPPED
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ActivationState.READY
                else -> ActivationState.NEEDS_PERMISSION
            }
        } catch (e: Throwable) {
            ActivationState.SHIZUKU_STOPPED
        }
    }

    fun requestPermission() {
        try {
            if (Shizuku.pingBinder()) Shizuku.requestPermission(1001)
        } catch (e: Throwable) {
        }
    }

    /** Root opcional: inicia o servidor do Shizuku via su. */
    fun startShizukuViaRoot(): Boolean = try {
        val p = Runtime.getRuntime().exec(
            arrayOf("su", "-c", "sh /sdcard/Android/data/$SHIZUKU_PKG/start.sh")
        )
        p.waitFor() == 0
    } catch (e: Throwable) {
        false
    }

    fun guide(state: ActivationState, sdk: Int): String = when (state) {
        ActivationState.READY -> "Pronto: Shizuku ativo e autorizado."
        ActivationState.NEEDS_PERMISSION -> "Shizuku ativo. Toque em \"Permissão Shizuku\" e autorize."
        ActivationState.SHIZUKU_MISSING ->
            "Instale o app Shizuku (Play Store ou GitHub), abra-o e volte aqui."
        ActivationState.SHIZUKU_STOPPED ->
            if (sdk >= 30)
                "Abra o Shizuku e inicie por Depuração sem fio: Opções do desenvolvedor › Depuração sem fio › Parear com código (6 dígitos). Com root, use \"Ativar via root\".\n" +
                    "Xiaomi (MIUI/HyperOS): ligue também \"Depuração USB (configurações de segurança)\" e desative a otimização de MIUI."
            else
                "Android 10 ou inferior: conecte ao PC e rode scripts/ativar.bat (Windows) ou ativar.sh (Mac/Linux). Repita após reiniciar o celular."
    }
}
