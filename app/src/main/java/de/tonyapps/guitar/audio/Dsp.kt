package de.tonyapps.guitar.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Instrument mit Klangcharakter und Saiten (MIDI-Noten, tiefste zuerst). */
enum class Tone(
    val sBase: Float,     // Daempfung der Obertoene im Saitenmodell (0 hell … 0,5 dumpf)
    val t60: Float,       // Abklingzeit der tiefen Saiten in Sekunden
    val pick: Float,      // Helligkeit des Anschlags
    val pickPos: Float,   // Anschlagstelle relativ zur Saitenlaenge
    val baseDrive: Float, // eingebaute Verzerrung
    val notes: IntArray?, // eigene Saiten; null = Gitarre mit gewaehlter Stimmung
    val nylon: Boolean,   // Nylonsaiten (Aussehen)
) {
    ACOUSTIC(.30f, 7f, .78f, .14f, 0f, null, false),
    CLEAN(.16f, 10f, .62f, .11f, 0f, null, false),
    ROCK(.18f, 13f, .68f, .10f, .55f, null, false),
    NYLON(.48f, 4.5f, .34f, .18f, 0f, null, true),
    MUTED(.5f, .28f, .4f, .2f, 0f, null, false),
    BASS(.42f, 9f, .45f, .16f, 0f, intArrayOf(28, 33, 38, 43), false),
    UKULELE(.44f, 2.6f, .42f, .2f, 0f, intArrayOf(67, 60, 64, 69), true),
}

/** Biquad nach Robert Bristow-Johnson, Transposed Direct Form II. */
internal class Biquad {
    private var b0 = 1f
    private var b1 = 0f
    private var b2 = 0f
    private var a1 = 0f
    private var a2 = 0f
    private var z1 = 0f
    private var z2 = 0f

    fun process(x: Float): Float {
        val y = b0 * x + z1
        z1 = b1 * x - a1 * y + z2
        z2 = b2 * x - a2 * y
        return y
    }

    fun setBandpass(sr: Float, f: Double, q: Double) {
        val w = 2 * PI * f / sr
        val alpha = sin(w) / (2 * q)
        val c = cos(w)
        set(alpha, 0.0, -alpha, 1 + alpha, -2 * c, 1 - alpha)
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na0: Double, na1: Double, na2: Double): Biquad {
        b0 = (nb0 / na0).toFloat(); b1 = (nb1 / na0).toFloat(); b2 = (nb2 / na0).toFloat()
        a1 = (na1 / na0).toFloat(); a2 = (na2 / na0).toFloat()
        return this
    }

    companion object {
        fun peak(sr: Float, f: Double, q: Double, db: Double): Biquad {
            val a = 10.0.pow(db / 40.0)
            val w = 2 * PI * f / sr
            val alpha = sin(w) / (2 * q)
            return Biquad().set(1 + alpha * a, -2 * cos(w), 1 - alpha * a, 1 + alpha / a, -2 * cos(w), 1 - alpha / a)
        }

        fun lowpass(sr: Float, f: Double, q: Double = .707): Biquad {
            val w = 2 * PI * f / sr
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            return Biquad().set((1 - c) / 2, 1 - c, (1 - c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }

        fun highpass(sr: Float, f: Double, q: Double = .707): Biquad {
            val w = 2 * PI * f / sr
            val alpha = sin(w) / (2 * q)
            val c = cos(w)
            return Biquad().set((1 + c) / 2, -(1 + c), (1 + c) / 2, 1 + alpha, -2 * c, 1 - alpha)
        }
    }
}

/** Korpus bzw. Tonabnehmer je Instrument. */
internal fun toneEq(sr: Float, tone: Tone): Array<Biquad> = when (tone) {
    Tone.ACOUSTIC -> arrayOf(
        Biquad.highpass(sr, 70.0),
        Biquad.peak(sr, 105.0, 1.4, 5.0),
        Biquad.peak(sr, 230.0, 1.6, 2.5),
        Biquad.peak(sr, 3000.0, .9, 2.5),
        Biquad.lowpass(sr, 9500.0),
    )
    Tone.NYLON -> arrayOf(
        Biquad.highpass(sr, 65.0),
        Biquad.peak(sr, 100.0, 1.3, 5.0),
        Biquad.peak(sr, 210.0, 1.5, 3.0),
        Biquad.lowpass(sr, 4200.0),
    )
    Tone.CLEAN -> arrayOf(
        Biquad.highpass(sr, 80.0),
        Biquad.peak(sr, 2600.0, 1.5, 4.0),
        Biquad.lowpass(sr, 6000.0, .8),
    )
    Tone.ROCK -> arrayOf(
        Biquad.highpass(sr, 90.0),
        Biquad.peak(sr, 700.0, .8, 3.0),
        Biquad.lowpass(sr, 6000.0),
    )
    Tone.MUTED -> arrayOf(
        Biquad.highpass(sr, 80.0),
        Biquad.peak(sr, 140.0, 1.2, 4.0),
        Biquad.lowpass(sr, 3200.0),
    )
    Tone.BASS -> arrayOf(
        Biquad.highpass(sr, 32.0),
        Biquad.peak(sr, 80.0, 1.0, 3.0),
        Biquad.peak(sr, 900.0, 1.0, 2.0),
        Biquad.lowpass(sr, 3500.0),
    )
    Tone.UKULELE -> arrayOf(
        Biquad.highpass(sr, 160.0),
        Biquad.peak(sr, 380.0, 1.4, 4.0),
        Biquad.peak(sr, 2500.0, 1.0, 2.0),
        Biquad.lowpass(sr, 7000.0),
    )
}

/** Wah: resonanter Bandpass, Pedalstellung 0 (zu) … 1 (offen). */
internal class Wah(private val sr: Float) {
    private val bp = Biquad()
    private var last = -1f
    fun process(x: Float, pos: Float): Float {
        if (abs(pos - last) > .002f) {
            last = pos
            val f = 380.0 * Math.pow(2200.0 / 380.0, pos.toDouble())
            bp.setBandpass(sr, f, 3.6)
        }
        return bp.process(x) * 2.2f
    }
}

/** Stereo-Chorus mit zwei gegenlaeufig modulierten Verzoegerungen. */
internal class Chorus(private val sr: Float) {
    private val buf = FloatArray(4096)
    private var w = 0
    private var phase = 0.0
    private val step = 2 * PI * .7 / sr

    private fun read(d: Float): Float {
        val pf = w - d + buf.size
        val i = floor(pf).toInt()
        val fr = pf - i
        val a = buf[i and 4095]
        val b = buf[(i + 1) and 4095]
        return a + (b - a) * fr
    }

    fun process(x: Float, wet: Float, outL: FloatArray, outR: FloatArray, i: Int) {
        buf[w] = x
        val base = .012f * sr
        val depth = .0028f * sr
        val l = read(base + depth * sin(phase).toFloat())
        val r = read(base + depth * cos(phase).toFloat())
        phase += step
        if (phase > 2 * PI) phase -= 2 * PI
        w = (w + 1) and 4095
        outL[i] = x + wet * l
        outR[i] = x + wet * r
    }
}

/** Stereo-Echo mit gedaempfter Rueckkopplung; rechts etwas spaeter fuer Breite. */
internal class Echo(private val sr: Float) {
    private val size = 1 shl 17
    private val mask = size - 1
    private val l = FloatArray(size)
    private val r = FloatArray(size)
    private var w = 0
    private var lpL = 0f
    private var lpR = 0f

    fun process(inL: FloatArray, inR: FloatArray, frames: Int, level: Float, seconds: Float, feedback: Float) {
        if (level < .005f) {
            // Leitung weiter fuellen, damit beim Einschalten nichts Altes kommt
            for (i in 0 until frames) {
                l[w] = 0f; r[w] = 0f
                w = (w + 1) and mask
            }
            return
        }
        val dl = (seconds * sr).toInt().coerceIn(1, size - 1)
        val dr = (seconds * 1.12f * sr).toInt().coerceIn(1, size - 1)
        val fb = feedback.coerceIn(0f, .9f)
        for (i in 0 until frames) {
            val yl = l[(w - dl) and mask]
            val yr = r[(w - dr) and mask]
            lpL += .4f * (yl - lpL)
            lpR += .4f * (yr - lpR)
            l[w] = inL[i] + lpR * fb
            r[w] = inR[i] + lpL * fb
            w = (w + 1) and mask
            inL[i] += level * yl
            inR[i] += level * yr
        }
    }
}

/** Freeverb (Jezar), 8 Kammfilter und 4 Allpaesse je Kanal. */
internal class Reverb(sr: Float) {
    private class Comb(n: Int) {
        val buf = FloatArray(n)
        var i = 0
        var store = 0f
        fun process(x: Float, feedback: Float, damp: Float): Float {
            val out = buf[i]
            store = out * (1 - damp) + store * damp
            buf[i] = x + store * feedback
            if (++i >= buf.size) i = 0
            return out
        }
    }

    private class Allpass(n: Int) {
        val buf = FloatArray(n)
        var i = 0
        fun process(x: Float): Float {
            val b = buf[i]
            buf[i] = x + b * .5f
            if (++i >= buf.size) i = 0
            return b - x
        }
    }

    private val scale = sr / 44100f
    private val combTune = intArrayOf(1116, 1188, 1277, 1356, 1422, 1491, 1557, 1617)
    private val allTune = intArrayOf(556, 441, 341, 225)
    // Arrays statt Listen: keine Iteratoren im Audio-Thread
    private val combL = Array(8) { Comb((combTune[it] * scale).roundToInt()) }
    private val combR = Array(8) { Comb(((combTune[it] + 23) * scale).roundToInt()) }
    private val apL = Array(4) { Allpass((allTune[it] * scale).roundToInt()) }
    private val apR = Array(4) { Allpass(((allTune[it] + 23) * scale).roundToInt()) }

    fun process(inL: FloatArray, inR: FloatArray, frames: Int, amount: Float) {
        if (amount < .005f) return
        val wet = amount * 1.1f
        for (i in 0 until frames) {
            val input = (inL[i] + inR[i]) * .015f
            var accL = 0f
            var accR = 0f
            for (c in combL) accL += c.process(input, .84f, .22f)
            for (c in combR) accR += c.process(input, .84f, .22f)
            for (a in apL) accL = a.process(accL)
            for (a in apR) accR = a.process(accR)
            inL[i] += accL * wet
            inR[i] += accR * wet
        }
    }
}
