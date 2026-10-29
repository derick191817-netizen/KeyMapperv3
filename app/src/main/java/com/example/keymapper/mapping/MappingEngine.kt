package com.example.keymapper.mapping

import android.graphics.RectF
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import com.example.keymapper.data.AppSettings
import com.example.keymapper.data.HudButton
import com.example.keymapper.data.Kind
import com.example.keymapper.data.Mode
import com.example.keymapper.data.Rot
import com.example.keymapper.injection.TouchInjector
import com.example.keymapper.input.PointerSpeed
import com.example.keymapper.domain.SensitivityEngine
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

/**
 * Converte teclas/mouse em toques (tudo roda na thread do TouchInjector).
 *
 *  - TIRO (FIRE): segurar contínuo (DOWN ao apertar, UP só ao soltar a tecla) ou toque único.
 *  - MIRA (AIM): segurar ou suspender (toggle).
 *  - CÂMERA, 3 modos de rotação (Rot):
 *      CENTRO   : ao passar da borda, o dedo virtual solta (UP) e toca (DOWN) no centro NA MESMA CHAMADA
 *                 (TouchInjector.swap), sem pausa, e continua aplicando os deltas a partir do centro.
 *      INFINITO : canvas virtual com safe-wrap e injeção de deltas de mouse relativos, sem UP de toque.
 *      DESLIGADO: a mira para na borda.
 *    AUTOMÁTICO escolhe INFINITO em shooters 3D conhecidos e CENTRO nos demais.
 *
 * Posições são frações da ÁREA DO JOGO (RectF em pixels do monitor).
 */
class MappingEngine(
    private val touch: TouchInjector,
    private val area: () -> RectF,
    private val cfg: () -> AppSettings,
    private val foreground: () -> String? = { null }   // app em primeiro plano (para o modo Automático)
) {
    private companion object {
        const val JOY = 0
        const val LOOK_A = 1
        const val LOOK_B = 30   // ids 2..29 são dos botões; 30 alterna com 1 ao trocar de dedo
        const val EDGE_MARGIN = 10f
        const val VIRTUAL_WRAP_LIMIT = 50_000f
        const val VIRTUAL_WRAP_SIZE = 100_000f
        val KNOWN_3D = setOf(
            "com.dts.freefireth", "com.dts.freefiremax",
            "com.tencent.ig", "com.pubg.imobile", "com.pubg.krmobile", "com.vng.pubgmobile", "com.rekoo.pubgm",
            "com.activision.callofduty.shooter"
        )
    }

    private val sens = SensitivityEngine()
    private val keys = HashSet<Int>()
    private val held = HashSet<Int>()      // todas as teclas/botões atualmente pressionados
    private val toggled = HashSet<Int>()
    // IDs de ponteiro Android são limitados a 0..31. IDs de botão são alocados sem colisão
    // e liberados junto com o contato; isso evita colisões após criar/apagar muitos controles.
    private val buttonPointerIds = HashMap<Int, Int>()
    private var joyActive = false
    private var lookActive = false
    private var lookId = LOOK_A
    // Canvas virtual independente da tela física. Estes valores NUNCA são usados como
    // coordenadas absolutas de touchscreen: o Android/jogo pode limitar coordenadas fora da
    // superfície. A injeção contínua usa deltas relativos via TouchInjector.
    private var virtualX = 5_000f
    private var virtualY = 5_000f
    // Posição física válida do contato de câmera.
    private var lx = 0f
    private var ly = 0f

    @Volatile
    var paused = false

    /** Chamado quando a mira ativa muda (o HUD redesenha a cruz de mira). */
    var onAimChanged: (() -> Unit)? = null

    private val releaseLook = Runnable { endLook() }

    // Câmera em lotes: os deltas do mouse (até 1000 Hz) são somados e enviados em intervalos FIXOS.
    private val lookLock = Any()
    private var pendX = 0f
    private var pendY = 0f
    private var flushScheduled = false

    @Volatile
    private var lastFlush = 0L
    private val flushLook = Runnable { flushLookNow() }

    /** Botão de mira ativo (-1 = nenhum). Ao mudar, o arrasto da câmera recomeça da posição da mira. */
    @Volatile
    var aimId = -1
        private set(value) {
            if (field != value) {
                field = value
                endLook()
                onAimChanged?.invoke()
            }
        }

    private fun pointerId(b: HudButton): Int? {
        buttonPointerIds[b.id]?.let { return it }
        val used = buttonPointerIds.values.toHashSet()
        val candidate = (2..29).firstOrNull { it !in used } ?: if (31 !in used) 31 else null
        if (candidate != null) buttonPointerIds[b.id] = candidate
        return candidate
    }

    fun onKey(code: Int, down: Boolean) {
        if (paused) return
        touch.handler.post { handleKey(code, down) }
    }

    fun onMouseButton(bit: Int, down: Boolean) {
        val code = when (bit) {
            MotionEvent.BUTTON_PRIMARY -> -1
            MotionEvent.BUTTON_SECONDARY -> -2
            else -> -3
        }
        onKey(code, down)
    }

    fun onDelta(dx: Float, dy: Float) {
        if (paused) return
        var schedule = false
        synchronized(lookLock) {
            pendX += dx
            pendY += dy
            if (!flushScheduled) {
                flushScheduled = true
                schedule = true
            }
        }
        if (schedule) {
            // primeiro movimento sai na hora; os seguintes são agrupados até o próximo intervalo
            val interval = cfg().lookIntervalMs.coerceIn(1, 20).toLong()
            val wait = (lastFlush + interval - SystemClock.uptimeMillis()).coerceAtLeast(0L)
            touch.handler.postDelayed(flushLook, wait)
        }
    }

    private fun flushLookNow() {
        var dx = 0f
        var dy = 0f
        synchronized(lookLock) {
            dx = pendX
            dy = pendY
            pendX = 0f
            pendY = 0f
            flushScheduled = false
        }
        lastFlush = SystemClock.uptimeMillis()
        if (paused || touch.broken || (dx == 0f && dy == 0f)) return // Shizuku caiu: não gasta CPU à toa
        handleLook(dx, dy)
    }

    /** Solta tudo (troca de monitor, mouse removido, pausa, modo edição, queda do Shizuku). */
    fun releaseAll() {
        touch.handler.post {
            touch.handler.removeCallbacks(releaseLook)
            touch.handler.removeCallbacks(flushLook)
            synchronized(lookLock) {
                pendX = 0f
                pendY = 0f
                flushScheduled = false
            }
            keys.clear()
            held.clear()
            toggled.clear()
            buttonPointerIds.clear()
            joyActive = false
            lookActive = false
            lookId = LOOK_A
            sens.reset()
            aimId = -1
            touch.releaseAll()
        }
    }

    // ---- teclas (botões e joystick: lógica inalterada) ----

    private fun handleKey(code: Int, down: Boolean) {
        val s = cfg()
        if (down) held.add(code) else held.remove(code)
        if (code == KeyEvent.KEYCODE_W || code == KeyEvent.KEYCODE_A ||
            code == KeyEvent.KEYCODE_S || code == KeyEvent.KEYCODE_D
        ) {
            if (down) keys.add(code) else keys.remove(code)
            updateJoy(s)
            return
        }
        for (b in s.buttons) {
            if (b.key != code) continue
            when (b.kind) {
                Kind.AIM -> aimKey(b, s, down)
                Kind.FIRE -> {
                    fireKey(b, down)
                    linkAim(b, s, down)
                }
                else -> standardKey(b, s, down)
            }
        }
    }

    /** TIRO: HOLD = DOWN ao apertar e UP só ao soltar a tecla; TAP = toque único. */
    private fun fireKey(b: HudButton, down: Boolean) {
        if (b.mode == Mode.TAP) {
            if (down) tap(b)
        } else {
            if (down) press(b) else release(b)
        }
    }

    /** PADRÃO: simula qualquer tecla. Com macro, aciona os botões escolhidos (e não o próprio). */
    private fun standardKey(b: HudButton, s: AppSettings, down: Boolean) {
        if (b.macro.isNotEmpty()) {
            for (id in b.macro) {
                val t = s.buttons.firstOrNull { it.id == id } ?: continue
                if (down) press(t) else if (!toggled.contains(t.id)) release(t)
            }
            return
        }
        val act = Runnable {
            when (b.mode) {
                Mode.TAP -> if (down) tap(b)
                Mode.DOUBLE -> if (down) doubleTap(b)
                Mode.TOGGLE -> if (down) {
                    if (toggled.remove(b.id)) release(b) else {
                        toggled.add(b.id)
                        press(b)
                    }
                }
                else -> if (down) press(b) else release(b)
            }
        }
        // "Tempo de resposta": atraso fixo (a ordem dos eventos é preservada, pois o atraso é igual para todos)
        val delay = b.delayMs.toLong().coerceIn(0L, 500L)
        if (delay > 0L) touch.handler.postDelayed(act, delay) else act.run()
    }

    /** Duplo toque: um aperto = dois toques curtos (intervalo fixo). */
    private fun doubleTap(b: HudButton) {
        tap(b)
        touch.handler.postDelayed({ tap(b) }, 90)
    }

    private fun press(b: HudButton) {
        val p = pointerId(b) ?: return
        if (touch.isDown(p)) return
        val a = area()
        touch.down(p, a.left + b.x * a.width(), a.top + b.y * a.height())
    }

    private fun release(b: HudButton) {
        val p = buttonPointerIds.remove(b.id) ?: return
        touch.up(p)
    }

    /** Toque curto: desce e solta sozinho. */
    private fun tap(b: HudButton) {
        val p = pointerId(b) ?: return
        if (touch.isDown(p)) return
        val a = area()
        touch.down(p, a.left + b.x * a.width(), a.top + b.y * a.height())
        touch.handler.postDelayed({
            touch.up(p)
            if (buttonPointerIds[b.id] == p) buttonPointerIds.remove(b.id)
        }, 25)
    }

    /** MIRA: segurar, ou suspender (toggle): um toque ativa e mantém, o próximo desativa. */
    private fun aimKey(b: HudButton, s: AppSettings, down: Boolean) {
        if (b.aimToggle) {
            if (!down) return                  // soltar a tecla não desativa
            tap(b)
            val on = aimId != b.id
            aimId = if (on) b.id else -1
            fireWithAim(b, s, on)
        } else {
            if (down) {
                press(b)
                aimId = b.id
            } else {
                release(b)
                if (aimId == b.id) aimId = -1
            }
            fireWithAim(b, s, down)
        }
    }

    /** "Atirar com a tecla de mira": a mira também aciona os botões ligados à tecla de tiro. */
    private fun fireWithAim(ab: HudButton, s: AppSettings, down: Boolean) {
        if (!ab.fireWithAim) return
        for (f in s.buttons) {
            if (f.id == ab.id || f.kind == Kind.AIM || f.key != ab.shootKey) continue
            if (f.mode == Mode.TAP) {
                if (down) tap(f)
            } else if (down) {
                press(f)
            } else {
                release(f)
            }
        }
    }

    /** "Mirar ao atirar": o tiro também segura o botão de mira escolhido. */
    private fun linkAim(b: HudButton, s: AppSettings, down: Boolean) {
        if (b.linkAimId < 0) return
        val a = s.buttons.firstOrNull { it.id == b.linkAimId && it.kind == Kind.AIM } ?: return
        if (down) {
            press(a)
            aimId = a.id
        } else {
            release(a)
            if (aimId == a.id) aimId = -1
        }
    }

    // ---- joystick (WASD) ----

    private fun updateJoy(s: AppSettings) {
        val vx = (if (keys.contains(KeyEvent.KEYCODE_D)) 1 else 0) - (if (keys.contains(KeyEvent.KEYCODE_A)) 1 else 0)
        val vy = (if (keys.contains(KeyEvent.KEYCODE_S)) 1 else 0) - (if (keys.contains(KeyEvent.KEYCODE_W)) 1 else 0)
        if (vx == 0 && vy == 0) {
            if (joyActive) {
                touch.up(JOY)
                joyActive = false
            }
            return
        }
        val a = area()
        val len = hypot(vx.toFloat(), vy.toFloat())
        val cx = a.left + s.joyX * a.width()
        val cy = a.top + s.joyY * a.height()
        val r = s.joyR * min(a.width(), a.height())
        if (!joyActive) {
            touch.down(JOY, cx, cy)
            joyActive = true
        }
        touch.move(JOY, cx + vx / len * r, cy + vy / len * r)
    }

    // ---- câmera (mouse) ----

    /** Modo efetivo: o da mira ativa (se tiver) ou o global; AUTOMÁTICO decide pelo app em primeiro plano. */
    private fun resolveLookMode(s: AppSettings, ab: HudButton?): Int {
        val sel = if (ab != null && ab.rotMode >= 0) ab.rotMode else s.lookMode
        if (sel != Rot.AUTO) return sel.coerceIn(Rot.CENTER, Rot.OFF)
        val pkg = foreground()
        return if (pkg != null && KNOWN_3D.contains(pkg)) Rot.INFINITE else Rot.CENTER
    }

    private fun handleLook(dx: Float, dy: Float) {
        val s = cfg()
        val ab = if (aimId >= 0) s.buttons.firstOrNull { it.id == aimId } else null
        if (ab != null && !ab.mouseAim) return                      // "Mouse aim" desligado
        val invert = s.invertY != (ab != null && ab.aimInvert)
        val ady = if (invert) -dy else dy
        val dz = if (ab != null) ab.aimDeadzone else s.deadzone
        if (hypot(dx, ady) < dz) return

        // Parâmetros globais de compatibilidade BS. Os valores de referência normalizam
        // a escala para que 69/16240 mantenham fator 1.0; a relação afeta o eixo Y.
        val bsMouseScale = (s.bsMouseSensitivity / 69f).coerceIn(0f, 2.9f)
        // Ajuste é uma razão calibrada em torno do padrão 16240 (= 1x), limitada para
        // impedir que valores fora da escala provoquem saltos de câmera.
        val bsFineTuneScale = (s.bsFineTune / 16240f).coerceIn(0.25f, 4f)
        val speed = PointerSpeed.mult(s.pointerSpeed) * s.globalSens * bsMouseScale * bsFineTuneScale
        val relationY = s.bsSensitivityRatio.coerceIn(0.01f, 10f)
        val ts = 4f / s.lookIntervalMs.coerceIn(1, 20)   // curvas calibradas para 4 ms
        if (ab != null) {
            val shooting = held.contains(ab.shootKey)
            // Escalas BS por botão: os padrões 69 e 16240 representam 1x.
            // Valores são normalizados uma única vez antes de entrar no motor.
            val mouse = (ab.aimMouseSens / 69f).coerceIn(0f, 2.9f)
            val zoom = ab.aimZoom.coerceAtLeast(1f)
            val general = (ab.aimGeneral / 16240f).coerceIn(0.0001f, 4f) * speed / zoom
            val sx = ab.aimSensX * (if (shooting) ab.shootSensX else 1f) * general
            val sy = ab.aimSensY * (if (shooting) ab.shootSensY else 1f) * general
            val level = ab.aimAccelLevel.coerceIn(0f, 10f)
            val curve = if (level > 0f) 1 else if (s.epp) 4 else 0
            sens.process(dx * mouse, ady * mouse, sx, sy, curve, 1f + level * 0.1f, s.knee, s.gain, ts)
        } else {
            val curve = if (s.epp && s.curve == 0) 4 else s.curve
            sens.process(dx, ady, s.sensX * speed, s.sensY * speed * relationY, curve, s.exponent, s.knee, s.gain, ts)
        }
        val ox = sens.outX
        val oy = sens.outY
        if (ox == 0 && oy == 0) return

        val effectiveLookMode = resolveLookMode(s, ab)
        if (s.relativeMouse || effectiveLookMode == Rot.INFINITE) {
            // Movimento contínuo real: envia deltas relativos, nunca posições absolutas fora
            // da tela. Só funciona em jogos/ambientes que aceitam SOURCE_MOUSE_RELATIVE.
            injectInfiniteDelta(ox.toFloat(), oy.toFloat())
            return
        }

        val a = area()
        val minX = a.left + EDGE_MARGIN
        val maxX = a.right - EDGE_MARGIN
        val minY = a.top + EDGE_MARGIN
        val maxY = a.bottom - EDGE_MARGIN
        // origem do arrasto = posição da mira (ou área OLHAR); fica sempre dentro dos limites reais da tela
        val fx = if (ab != null) (ab.aimPosX / 1000f).coerceIn(0f, 1f) else s.lookX
        val fy = if (ab != null) (ab.aimPosY / 1000f).coerceIn(0f, 1f) else s.lookY
        // Para a mira global, a posição X usa escala lógica 0..1000, mapeada para a largura da área.
        // Valores salvos no formato anterior (0..100) são migrados no AppSettings.fromJson().
        val cx = if (ab != null) {
            (a.left + fx * a.width()).coerceIn(minX, maxX)
        } else {
            (a.left + (s.bsLookPositionX.coerceIn(0, 1000) / 1000f) * a.width()).coerceIn(minX, maxX)
        }
        val cy = (a.top + fy * a.height()).coerceIn(minY, maxY)

        when (effectiveLookMode) {
            Rot.OFF -> lookClamped(s, cx, cy, ox, oy, minX, maxX, minY, maxY)
            else -> lookCenter(s, cx, cy, ox, oy, minX, maxX, minY, maxY)
        }
    }

    /** Acumula um canvas virtual com safe-wrap e envia apenas o delta relativo do frame. */
    private fun injectInfiniteDelta(dx: Float, dy: Float) {
        if (!dx.isFinite() || !dy.isFinite()) return
        virtualX += dx
        virtualY += dy
        if (virtualX > VIRTUAL_WRAP_LIMIT) virtualX -= VIRTUAL_WRAP_SIZE
        else if (virtualX < -VIRTUAL_WRAP_LIMIT) virtualX += VIRTUAL_WRAP_SIZE
        if (virtualY > VIRTUAL_WRAP_LIMIT) virtualY -= VIRTUAL_WRAP_SIZE
        else if (virtualY < -VIRTUAL_WRAP_LIMIT) virtualY += VIRTUAL_WRAP_SIZE
        touch.injectVirtualMove(dx, dy)
    }

    private fun beginLook(cx: Float, cy: Float) {
        if (!lookActive) {
            lx = cx
            ly = cy
            virtualX = cx
            virtualY = cy
            touch.down(lookId, lx, ly)
            lookActive = true
        }
    }

    private fun armRelease(delayMs: Long) {
        touch.handler.removeCallbacks(releaseLook)
        if (delayMs > 0L) touch.handler.postDelayed(releaseLook, delayMs)
    }

    private fun idleMs(s: AppSettings): Long =
        maxOf(s.releaseDelayMs.toLong(), s.lookIntervalMs.coerceIn(1, 20) * 3L + 4L)

    /**
     * MODO CENTRO (fallback seguro): a posição física lx/ly soma os deltas. Ao ultrapassar os limites reais da
     * tela: MOVE até a borda, UP na borda e DOWN no centro, tudo na mesma chamada (TouchInjector.swap),
     * e segue aplicando os deltas a partir do centro (o que passou do limite é levado junto).
     */
    private fun lookCenter(
        s: AppSettings, cx: Float, cy: Float, ox: Int, oy: Int,
        minX: Float, maxX: Float, minY: Float, maxY: Float
    ) {
        beginLook(cx, cy)
        lx += ox
        ly += oy
        if (lx < minX || lx > maxX || ly < minY || ly > maxY) {
            val ex = lx.coerceIn(minX, maxX)
            val ey = ly.coerceIn(minY, maxY)
            val next = if (lookId == LOOK_A) LOOK_B else LOOK_A
            touch.swap(lookId, next, ex, ey, cx, cy)
            lookId = next
            lx = (cx + (lx - ex)).coerceIn(minX, maxX)
            ly = (cy + (ly - ey)).coerceIn(minY, maxY)
        }
        touch.move(lookId, lx, ly)
        armRelease(idleMs(s))
    }

    /** MODO DESLIGADO: a mira para na borda da tela (sem trocar de dedo). */
    private fun lookClamped(
        s: AppSettings, cx: Float, cy: Float, ox: Int, oy: Int,
        minX: Float, maxX: Float, minY: Float, maxY: Float
    ) {
        beginLook(cx, cy)
        lx = (lx + ox).coerceIn(minX, maxX)
        ly = (ly + oy).coerceIn(minY, maxY)
        touch.move(lookId, lx, ly)
        armRelease(idleMs(s))
    }

    private fun endLook() {
        if (lookActive) {
            touch.up(lookId)
            lookActive = false
            lookId = LOOK_A
        }
        virtualX = 5_000f
        virtualY = 5_000f
        sens.reset()
    }
}
