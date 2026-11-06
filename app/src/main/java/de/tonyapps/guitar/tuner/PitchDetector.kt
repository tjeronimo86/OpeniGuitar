package de.tonyapps.guitar.tuner

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Tonhoehe aus dem Mikrofon nach dem YIN-Verfahren (de Cheveigné & Kawahara).
 * Aufnahme mit 44,1 kHz, Analyse auf 22,05 kHz heruntergerechnet: reicht bis ~1,4 kHz
 * und spart drei Viertel der Rechenzeit.
 */
class PitchDetector {
    private val recRate = 44100
    private val rate = recRate / 2
    private val window = 1024
    private val tauMax = rate / 38        // tiefste Note ~38 Hz
    private val tauMin = rate / 1400      // hoechste Note ~1,4 kHz
    private val ring = FloatArray(window + tauMax)
    private val diff = FloatArray(tauMax + 2)

    /** Ergebnis: Frequenz oder null, dazu Lautstaerke (RMS) und Sicherheit. */
    class Result(val freq: Float?, val rms: Float, val clarity: Float)

    /** Laeuft, bis die Coroutine abgebrochen wird. */
    suspend fun run(context: Context, onResult: suspend (Result) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val unprocessed = audio.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val source = if (unprocessed) MediaRecorder.AudioSource.UNPROCESSED else MediaRecorder.AudioSource.VOICE_RECOGNITION
        val hop = 1024 // bei 44,1 kHz: ~23 ms, also gut 40 Messungen je Sekunde
        val min = AudioRecord.getMinBufferSize(recRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(source, recRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, max(min, hop * 8))
        } catch (e: SecurityException) {
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return
        }
        val pcm = ShortArray(hop)
        var last = 0f
        try {
            record.startRecording()
            while (currentCoroutineContext().isActive) {
                var got = 0
                while (got < hop) {
                    val n = record.read(pcm, got, hop - got)
                    if (n <= 0) return
                    got += n
                }
                // 2:1 heruntertakten mit kleinem Tiefpass, dann in den Ringpuffer schieben
                val half = hop / 2
                System.arraycopy(ring, half, ring, 0, ring.size - half)
                val base = ring.size - half
                for (i in 0 until half) {
                    val a = pcm[2 * i] / 32768f
                    val b = pcm[2 * i + 1] / 32768f
                    ring[base + i] = .25f * last + .5f * a + .25f * b
                    last = b
                }
                onResult(analyse())
            }
        } finally {
            runCatching { record.stop() }
            record.release()
        }
    }

    private fun analyse(): Result {
        var sum = 0f
        val start = ring.size - window
        for (i in start until ring.size) sum += ring[i] * ring[i]
        val rms = sqrt(sum / window)
        if (rms < .0025f) return Result(null, rms, 0f)

        // Differenzfunktion
        for (tau in 1..tauMax) {
            var s = 0f
            for (j in 0 until window) {
                val d = ring[j] - ring[j + tau]
                s += d * d
            }
            diff[tau] = s
        }
        // Kumulativ normiert
        diff[0] = 1f
        var running = 0f
        for (tau in 1..tauMax) {
            running += diff[tau]
            diff[tau] = if (running > 0f) diff[tau] * tau / running else 1f
        }
        var tau = -1
        var t = tauMin
        while (t < tauMax) {
            if (diff[t] < .15f) {
                while (t + 1 < tauMax && diff[t + 1] < diff[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau < 0) {
            var best = tauMin
            for (k in tauMin until tauMax) if (diff[k] < diff[best]) best = k
            if (diff[best] > .3f) return Result(null, rms, 0f)
            tau = best
        }
        val clarity = 1f - diff[tau]
        // Parabel durch die drei Punkte fuer Genauigkeit unter einem Sample
        var exact = tau.toFloat()
        if (tau > 1 && tau < tauMax) {
            val s0 = diff[tau - 1]
            val s1 = diff[tau]
            val s2 = diff[tau + 1]
            val den = s0 + s2 - 2f * s1
            if (den != 0f) exact += (s0 - s2) / (2f * den)
        }
        return Result(rate / exact, rms, clarity)
    }
}
