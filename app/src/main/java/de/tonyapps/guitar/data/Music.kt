package de.tonyapps.guitar.data

import de.tonyapps.guitar.R
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/** Saite 0 ist die tiefe E-Saite, Saite 5 die hohe. Werte sind MIDI-Noten. */
data class Tuning(val id: String, val nameRes: Int, val notes: IntArray)

val TUNINGS = listOf(
    Tuning("standard", R.string.t_standard, intArrayOf(40, 45, 50, 55, 59, 64)),
    Tuning("drop_d", R.string.t_drop_d, intArrayOf(38, 45, 50, 55, 59, 64)),
    Tuning("half_down", R.string.t_half_down, intArrayOf(39, 44, 49, 54, 58, 63)),
    Tuning("full_down", R.string.t_full_down, intArrayOf(38, 43, 48, 53, 57, 62)),
    Tuning("drop_c", R.string.t_drop_c, intArrayOf(36, 43, 48, 53, 57, 62)),
    Tuning("dadgad", R.string.t_dadgad, intArrayOf(38, 45, 50, 55, 57, 62)),
    Tuning("open_g", R.string.t_open_g, intArrayOf(38, 43, 50, 55, 59, 62)),
    Tuning("open_d", R.string.t_open_d, intArrayOf(38, 45, 50, 54, 57, 62)),
    Tuning("open_e", R.string.t_open_e, intArrayOf(40, 47, 52, 56, 59, 64)),
)

fun tuningById(id: String): Tuning = TUNINGS.firstOrNull { it.id == id } ?: TUNINGS[0]

private val NAMES = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")

fun noteName(midi: Int): String = NAMES[((midi % 12) + 12) % 12]
fun noteWithOctave(midi: Int): String = noteName(midi) + (midi / 12 - 1)

fun midiToFreq(midi: Float, a4: Float): Float = a4 * 2.0.pow((midi - 69.0) / 12.0).toFloat()
fun freqToMidi(freq: Float, a4: Float): Float = (69.0 + 12.0 * ln(freq / a4.toDouble()) / ln(2.0)).toFloat()
fun cents(freq: Float, target: Float): Float = (1200.0 * ln(freq / target.toDouble()) / ln(2.0)).toFloat()

/** Kurzform der Stimmung, z. B. "E A D G B E". */
fun Tuning.label(): String = notes.joinToString(" ") { noteName(it) }

fun nearestNote(midi: Float): Int = midi.roundToInt()
