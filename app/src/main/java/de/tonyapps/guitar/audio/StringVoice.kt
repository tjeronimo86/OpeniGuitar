package de.tonyapps.guitar.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Eine Saite als Karplus-Strong-Modell: Verzoegerungsleitung mit Verlustfilter.
 * Die Leselaenge ist gebrochen und gleitet, dadurch klappen Hammer-on, Slide und Ziehen
 * ohne neuen Anschlag. Das Verlustfilter ist so berechnet, dass Grundton und Abklingzeit
 * genau stimmen (Phasenlaufzeit wird von der Leitungslaenge abgezogen).
 */
internal class StringVoice(private val sr: Float) {
    private val buf = FloatArray(SIZE)
    private val excite = FloatArray(SIZE)
    private var w = 0
    private var period = 200f
    private var target = 200f
    private val glide = 1f - exp(-1f / (0.006f * sr))

    private var s = .5f          // Gewicht des Verlustfilters
    private var rho = 1f         // zusaetzlicher Verlust je Umlauf
    private var lossDelay = .5f  // Phasenlaufzeit des Verlustfilters in Samples
    private var prev = 0f
    private var t60 = 6f
    private var sBase = .3f
    private var dampGain = 1f

    private var active = false
    private var quiet = 0

    /** Huellkurve fuer die Saitenanimation. */
    var level = 0f
        private set

    fun pluck(freq: Float, velocity: Float, tone: Tone, rnd: Random) {
        target = sr / freq
        period = target
        t60 = tone.t60
        sBase = tone.sBase
        dampGain = 1f
        updateLoss()

        val n = min(SIZE - 8, period.roundToInt() + 4)
        // Rauschen, je nach Anschlag mehr oder weniger gedaempft
        val bright = (tone.pick * (.55f + .45f * velocity)).coerceIn(.08f, 1f)
        var lp = 0f
        for (i in 0 until n) {
            lp += bright * ((rnd.nextFloat() * 2f - 1f) - lp)
            excite[i] = lp
        }
        // Anschlagstelle: Kammfilter loescht Obertoene mit Knoten an dieser Stelle
        val pos = max(1, (period * tone.pickPos).roundToInt())
        for (i in n - 1 downTo pos) excite[i] -= excite[i - pos]
        var mean = 0f
        for (i in 0 until n) mean += excite[i]
        mean /= n
        var peak = 1e-6f
        for (i in 0 until n) peak = max(peak, abs(excite[i] - mean))
        val scale = .55f * velocity / peak
        for (i in 0 until n) {
            val idx = (w - n + i) and MASK
            buf[idx] = buf[idx] * .3f + (excite[i] - mean) * scale
        }
        prev = 0f
        active = true
        quiet = 0
    }

    /** Neue Tonhoehe ohne Anschlag (Bundwechsel, Ziehen). */
    fun glideTo(freq: Float) {
        target = sr / freq
        updateLoss()
    }

    /** Saite abdaempfen, z. B. wenn der Finger losgelassen wird. */
    fun damp(seconds: Float) {
        val f = sr / target
        dampGain = 10f.pow(-3f / (seconds * f))
    }

    private fun updateLoss() {
        val f = sr / target
        // Hohe Toene klingen auf der echten Gitarre kuerzer
        val t = max(.5f, t60 * (110f / f).pow(.4f))
        val g = 10f.pow(-3f / (t * f))
        val w0 = 2f * PI.toFloat() * f / sr
        val c = 1f - cos(w0)
        // |H|^2 = 1 - 2 S (1-S) c. Benoetigtes S fuer genau g, hoechstens sBase.
        val q = (1f - g * g) / (2f * max(c, 1e-9f))
        val sNeeded = if (q >= .25f) .5f else (1f - sqrt(1f - 4f * q)) / 2f
        s = min(sBase, sNeeded)
        val mag = sqrt(max(1e-9f, 1f - 2f * s * (1f - s) * c))
        rho = min(1f, g / mag)
        lossDelay = atan2(s * sin(w0), 1f - s + s * cos(w0)) / w0
    }

    fun render(out: FloatArray, frames: Int) {
        if (!active) {
            level *= .8f
            return
        }
        var peak = 0f
        for (i in 0 until frames) {
            if (period != target) {
                period += (target - period) * glide
                if (abs(target - period) < 1e-4f) period = target
            }
            val pf = w - (period - lossDelay) + SIZE
            val ip = floor(pf).toInt()
            val fr = pf - ip
            val xm1 = buf[(ip - 1) and MASK]
            val x0 = buf[ip and MASK]
            val x1 = buf[(ip + 1) and MASK]
            val x2 = buf[(ip + 2) and MASK]
            // Kubische Hermite-Interpolation
            val c1 = .5f * (x1 - xm1)
            val c2 = xm1 - 2.5f * x0 + 2f * x1 - .5f * x2
            val c3 = .5f * (x2 - xm1) + 1.5f * (x0 - x1)
            val x = ((c3 * fr + c2) * fr + c1) * fr + x0
            val y = ((1f - s) * x + s * prev) * rho * dampGain
            prev = x
            buf[w] = y
            w = (w + 1) and MASK
            out[i] += y
            val a = abs(y)
            if (a > peak) peak = a
        }
        level = max(peak, level * .82f)
        if (peak < 2e-4f) {
            quiet += frames
            if (quiet > sr * .3f) {
                active = false
                buf.fill(0f)
            }
        } else {
            quiet = 0
        }
    }

    companion object {
        const val SIZE = 8192
        const val MASK = SIZE - 1
    }
}
