package de.tonyapps.guitar.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Klangerzeugung fuer sechs Saiten. Ein eigener Thread rechnet in kleinen Bloecken und
 * schreibt in einen AudioTrack mit niedriger Latenz. Befehle aus der Oberflaeche laufen
 * ueber eine Warteschlange, damit der Audio-Thread nie warten muss.
 */
object GuitarEngine {
    @Volatile var tone = Tone.ACOUSTIC
    @Volatile var drive = 0f
    @Volatile var chorus = 0f
    @Volatile var delay = 0f
    @Volatile var reverb = .25f
    @Volatile var volume = .8f

    private class Command(val type: Int, val string: Int, val freq: Float, val value: Float)

    private const val PLUCK = 0
    private const val GLIDE = 1
    private const val DAMP = 2

    private val queue = ConcurrentLinkedQueue<Command>()
    private val levels = FloatArray(6)

    private var thread: Thread? = null
    @Volatile private var running = false

    /** Huellkurve einer Saite fuer die Animation (0 … ~0,6). */
    fun level(string: Int): Float = levels[string]

    fun pluck(string: Int, freq: Float, velocity: Float = .9f) {
        queue.add(Command(PLUCK, string, freq, velocity))
    }

    fun glide(string: Int, freq: Float) {
        queue.add(Command(GLIDE, string, freq, 0f))
    }

    fun damp(string: Int, seconds: Float = .12f) {
        queue.add(Command(DAMP, string, 0f, seconds))
    }

    @Synchronized
    fun start() {
        if (running) return
        thread?.join(500)
        running = true
        thread = Thread({ run() }, "GuitarAudio").also { it.start() }
    }

    @Synchronized
    fun stop() {
        running = false
    }

    private fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val sr = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC).takeIf { it in 22050..96000 } ?: 48000
        val frames = 192
        val track = try {
            val min = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sr)
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build(),
                )
                .setBufferSizeInBytes(max(min, frames * 2 * 4 * 2))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
        } catch (e: Exception) {
            Log.e("GuitarEngine", "AudioTrack", e)
            running = false
            return
        }
        val synth = Synth(sr.toFloat())
        val out = FloatArray(frames * 2)
        try {
            track.play()
            while (running) {
                while (true) {
                    val c = queue.poll() ?: break
                    when (c.type) {
                        PLUCK -> synth.pluck(c.string, c.freq, c.value)
                        GLIDE -> synth.glide(c.string, c.freq)
                        DAMP -> synth.damp(c.string, c.value)
                    }
                }
                synth.render(out, frames)
                for (i in 0 until 6) levels[i] = synth.level(i)
                track.write(out, 0, out.size, AudioTrack.WRITE_BLOCKING)
            }
        } catch (e: Exception) {
            Log.e("GuitarEngine", "render", e)
        } finally {
            runCatching { track.stop() }
            track.release()
            for (i in 0 until 6) levels[i] = 0f
        }
    }

    /** Die eigentliche Rechnung; lebt nur im Audio-Thread. */
    private class Synth(private val sr: Float) {
        private val voices = Array(6) { StringVoice(sr) }
        private val rnd = Random(System.nanoTime())
        private var eqTone: Tone? = null
        private var eq: Array<Biquad> = emptyArray()
        private val cab = arrayOf(
            Biquad.peak(sr, 110.0, 1.0, 3.0),
            Biquad.peak(sr, 1600.0, 1.2, 2.0),
            Biquad.lowpass(sr, 4200.0, .9),
            Biquad.lowpass(sr, 5500.0, .6),
        )
        private val chorusFx = Chorus(sr)
        private val echo = Echo(sr)
        private val hall = Reverb(sr)
        private val mono = FloatArray(1024)
        private val outL = FloatArray(1024)
        private val outR = FloatArray(1024)
        private var driveNow = 0f
        private var chorusNow = 0f

        fun level(i: Int) = voices[i].level

        fun pluck(s: Int, f: Float, v: Float) = voices[s].pluck(f, v, tone, rnd)
        fun glide(s: Int, f: Float) = voices[s].glideTo(f)
        fun damp(s: Int, sec: Float) = voices[s].damp(sec)

        fun render(out: FloatArray, frames: Int) {
            val t = tone
            if (t != eqTone) {
                eqTone = t
                eq = toneEq(sr, t)
            }
            mono.fill(0f, 0, frames)
            for (v in voices) v.render(mono, frames)

            val targetDrive = min(1f, t.baseDrive + drive * (1f - t.baseDrive * .5f))
            val targetChorus = chorus * .7f
            // Pro Block sanft nachfuehren, damit Regler nicht knacken
            val dStep = (targetDrive - driveNow) / frames
            val cStep = (targetChorus - chorusNow) / frames
            for (i in 0 until frames) {
                var x = mono[i] * .42f
                for (b in eq) x = b.process(x)
                driveNow += dStep
                val d = driveNow
                if (d > .01f) {
                    val gain = 1f + 80f * d * d
                    var y = (tanh(x * gain + .15f) - tanh(.15f)) * .3f
                    for (b in cab) y = b.process(y)
                    val mix = min(1f, d * 6f)
                    x = x * (1f - mix) + y * mix
                }
                chorusNow += cStep
                chorusFx.process(x, chorusNow, outL, outR, i)
            }
            echo.process(outL, outR, frames, delay)
            hall.process(outL, outR, frames, reverb)

            val vol = volume * 1.25f
            for (i in 0 until frames) {
                out[2 * i] = limit(outL[i] * vol)
                out[2 * i + 1] = limit(outR[i] * vol)
            }
        }

        private fun limit(x: Float): Float {
            val a = abs(x)
            if (a <= .7f) return x
            val y = .7f + .3f * tanh((a - .7f) / .3f)
            return if (x < 0) -y else y
        }
    }
}
