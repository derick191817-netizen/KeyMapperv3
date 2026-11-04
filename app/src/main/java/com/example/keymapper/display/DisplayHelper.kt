package com.example.keymapper.display

import android.content.Context
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.view.Display
import android.view.WindowManager
import com.example.keymapper.injection.WirelessAdbSnapshotStore
import com.example.keymapper.injection.AdbEndpointPolicy
import kotlin.math.abs
import kotlin.math.roundToInt

/** Detecta o monitor externo (DeX) e as taxas de atualização suportadas. */
class DisplayHelper(private val ctx: Context) {
    // DisplayManager is normally present on Android phones, but do not let a missing
    // or temporarily unavailable system service crash settings or service startup.
    private val dm: DisplayManager? = runCatching {
        ctx.getSystemService(DisplayManager::class.java)
    }.getOrNull()

    /** Só usa o monitor externo (DeX) se o usuário ligou essa opção; o padrão é a tela do aparelho. */
    @Volatile
    var useExternal = false

    /** Monitor externo de verdade (HDMI/DeX/Wi-Fi), ignorando displays virtuais do sistema. */
    fun external(): Display? = runCatching {
        dm?.displays?.firstOrNull { d ->
            runCatching {
                d.displayId != Display.DEFAULT_DISPLAY && d.state != Display.STATE_OFF &&
                    ((d.flags and Display.FLAG_PRESENTATION) != 0 ||
                        Regex("hdmi|dex|external|displayport|wireless|cast", RegexOption.IGNORE_CASE)
                            .containsMatchIn(d.name ?: ""))
            }.getOrDefault(false)
        }
    }.getOrNull()

    /**
     * Retorna o display explicitamente selecionado. Se o usuário escolheu monitor externo
     * e ele foi desconectado, falha em vez de redirecionar silenciosamente para a tela interna.
     */
    fun target(): Display {
        val manager = dm ?: throw IllegalStateException("Serviço de displays indisponível")
        val defaultDisplay = manager.getDisplay(Display.DEFAULT_DISPLAY)
            ?: throw IllegalStateException("Display principal indisponível")
        val externalDisplay = if (useExternal) external() else null
        val selectedId = DisplayTargetPolicy.resolve(useExternal, defaultDisplay.displayId, externalDisplay?.displayId)
            ?: throw IllegalStateException("Monitor externo selecionado não está disponível")
        return if (selectedId == defaultDisplay.displayId) defaultDisplay
        else externalDisplay ?: throw IllegalStateException("Monitor externo foi desconectado")
    }

    @Suppress("DEPRECATION")
    fun size(d: Display): Point {
        val p = Point()
        d.getRealSize(p)
        return p
    }

    /** Modos com a mesma resolução da tela atual. */
    fun modes(d: Display): List<Display.Mode> = runCatching {
        val cur = d.mode
        d.supportedModes.filter {
            it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight &&
                it.refreshRate.isFinite() && it.refreshRate > 0f
        }
    }.getOrDefault(emptyList())

    fun rates(d: Display): List<Int> = modes(d)
        .mapNotNull { mode -> mode.refreshRate.takeIf { it.isFinite() && it > 0f }?.roundToInt() }
        .filter { it in 1..1000 }
        .distinct()
        .sorted()

    fun bestMode(d: Display): Display.Mode? = modes(d).maxByOrNull { it.refreshRate }

    /** hz <= 0: o maior suportado; senão o modo mais próximo de [hz]. */
    fun pickMode(d: Display, hz: Int): Display.Mode? {
        val validModes = modes(d)
        return if (hz <= 0) validModes.maxByOrNull { it.refreshRate }
        else validModes.minByOrNull { abs(it.refreshRate - hz) }
    }

    fun windowContext(d: Display): Context {
        val dc = ctx.createDisplayContext(d)
        return if (Build.VERSION.SDK_INT >= 30)
            dc.createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
        else dc
    }

    /** Métricas reais (WindowMetrics) e densidade do monitor [d]; nunca lança exceção. */
    fun info(d: Display): ScreenInfo {
        val nativeInfo = try {
            DisplayUtils.info(windowContext(d), d)
        } catch (e: Exception) {
            DisplayUtils.fallback(ctx, d)
        }
        return applyAdbFallback(nativeInfo, d)
    }

    /**
     * wm size/density descrevem a tela primária. Só podem preencher métricas ausentes
     * do display principal; nunca substituem métricas nativas válidas nem as do DeX externo.
     */
    private fun applyAdbFallback(native: ScreenInfo, display: Display): ScreenInfo {
        val snapshot = WirelessAdbSnapshotStore.latest

        val adbMetrics = snapshot?.let {
            AdbDisplayFallbackPolicy.Values(
                it.widthPx ?: 0,
                it.heightPx ?: 0,
                it.densityDpi ?: 0
            )
        }
        val snapshotAge = snapshot?.let { System.currentTimeMillis() - it.capturedAtEpochMs } ?: Long.MAX_VALUE
        val resolved = AdbDisplayFallbackPolicy.resolve(
            native = AdbDisplayFallbackPolicy.Values(native.width, native.height, native.densityDpi),
            adb = adbMetrics,
            isPrimaryDisplay = display.displayId == Display.DEFAULT_DISPLAY,
            isLocalEndpoint = snapshot?.let { it.isLocalEndpoint && AdbEndpointPolicy.isLocalInterfaceAddress(it.host) } == true,
            snapshotAgeMs = snapshotAge
        )
        val resolvedDensity = if (native.densityDpi > 0 && native.density.isFinite() && native.density > 0f) {
            native.density
        } else if (resolved.densityDpi > 0) {
            resolved.densityDpi / 160f
        } else {
            native.density.takeIf { it.isFinite() && it > 0f } ?: 1f
        }
        return native.copy(
            width = resolved.width,
            height = resolved.height,
            densityDpi = resolved.densityDpi,
            density = resolvedDensity
        )
    }

    fun register(l: DisplayManager.DisplayListener, h: Handler?) {
        runCatching { dm?.registerDisplayListener(l, h) }
    }

    fun unregister(l: DisplayManager.DisplayListener) {
        runCatching { dm?.unregisterDisplayListener(l) }
    }
}
