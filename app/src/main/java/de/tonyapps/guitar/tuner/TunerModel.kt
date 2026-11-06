package de.tonyapps.guitar.tuner

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import de.tonyapps.guitar.data.Settings
import de.tonyapps.guitar.data.cents
import de.tonyapps.guitar.data.freqToMidi
import de.tonyapps.guitar.data.midiToFreq
import de.tonyapps.guitar.data.noteName
import kotlin.math.abs
import kotlin.math.roundToInt

/** Auswertung der Messungen: Saite finden, Cent glaetten, "gestimmt" erkennen. */
class TunerModel {
    /** Gemessene Frequenz, null bei Stille. */
    var freq by mutableStateOf<Float?>(null)
        private set
    var cents by mutableFloatStateOf(0f)
        private set
    /** Erkannte bzw. gewaehlte Saite, -1 im chromatischen Modus. */
    var string by mutableIntStateOf(-1)
        private set
    var label by mutableStateOf("")
        private set
    /** Saite im manuellen Modus. */
    var manual by mutableIntStateOf(0)
    var tuned by mutableStateOf(setOf<Int>())
        private set
    /** Verlauf der Cent-Werte, neuester zuletzt; NaN bei Stille. */
    val history = mutableStateListOf<Float>()

    private val recent = ArrayList<Float>()
    private var silent = 0
    private var steady = 0

    fun reset() {
        tuned = emptySet()
    }

    fun select(s: Int) {
        manual = s
        Settings.autoMode = false
        steady = 0
    }

    /** True, wenn eine Saite gerade als gestimmt erkannt wurde. */
    fun onResult(r: PitchDetector.Result): Boolean {
        val f0 = r.freq
        if (f0 == null || r.clarity < .82f) {
            silent++
            if (silent > 6) {
                freq = null
                steady = 0
                recent.clear()
                push(Float.NaN)
            }
            return false
        }
        silent = 0
        // Median der letzten drei Werte gegen Ausreisser (Oktavspruenge beim Anschlag)
        if (recent.isNotEmpty() && abs(f0 / recent.last() - 1f) > .06f) recent.clear()
        recent.add(f0)
        if (recent.size > 3) recent.removeAt(0)
        val f = recent.sorted()[recent.size / 2]

        val a4 = Settings.a4.toFloat()
        var c: Float
        if (Settings.chromatic) {
            val midi = freqToMidi(f, a4)
            val n = midi.roundToInt()
            c = (midi - n) * 100f
            label = noteName(n)
            string = -1
        } else {
            val notes = Settings.tuning.notes
            val idx = if (Settings.autoMode) {
                notes.indices.minBy { abs(cents(f, midiToFreq(notes[it].toFloat(), a4))) }
            } else {
                manual
            }
            c = cents(f, midiToFreq(notes[idx].toFloat(), a4))
            label = noteName(notes[idx])
            if (idx != string) steady = 0
            string = idx
        }
        c = if (freq == null || abs(c - cents) > 25f) c else cents + (c - cents) * .35f
        cents = c
        freq = f
        push(c.coerceIn(-60f, 60f))

        if (abs(c) < 4f) {
            steady++
            if (steady == 10 && string >= 0 && string !in tuned) {
                tuned = tuned + string
                return true
            }
        } else {
            steady = 0
            // Deutlich verstimmt: Haken wieder weg
            if (abs(c) > 15f && string in tuned) tuned = tuned - string
        }
        return false
    }

    private fun push(v: Float) {
        history.add(v)
        if (history.size > 90) history.removeAt(0)
    }
}
