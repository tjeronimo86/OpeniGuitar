package de.tonyapps.guitar.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import de.tonyapps.guitar.audio.GuitarEngine
import de.tonyapps.guitar.audio.Tone
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Alle Einstellungen, als Compose-State und dauerhaft gespeichert. */
object Settings {
    private lateinit var p: SharedPreferences
    private val all = ArrayList<Loadable>()

    private interface Loadable { fun load() }

    private class BoolPref(val key: String, val def: Boolean) : ReadWriteProperty<Any?, Boolean>, Loadable {
        val st = mutableStateOf(def)
        init { all.add(this) }
        override fun load() { st.value = p.getBoolean(key, def) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = st.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            st.value = value; p.edit().putBoolean(key, value).apply(); pushToEngine()
        }
    }

    private class FloatPref(val key: String, val def: Float) : ReadWriteProperty<Any?, Float>, Loadable {
        val st = mutableFloatStateOf(def)
        init { all.add(this) }
        override fun load() { st.floatValue = p.getFloat(key, def) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = st.floatValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            st.floatValue = value; p.edit().putFloat(key, value).apply(); pushToEngine()
        }
    }

    private class IntPref(val key: String, val def: Int) : ReadWriteProperty<Any?, Int>, Loadable {
        val st = mutableIntStateOf(def)
        init { all.add(this) }
        override fun load() { st.intValue = p.getInt(key, def) }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = st.intValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            st.intValue = value; p.edit().putInt(key, value).apply(); pushToEngine()
        }
    }

    private class StringPref(val key: String, val def: String) : ReadWriteProperty<Any?, String>, Loadable {
        val st = mutableStateOf(def)
        init { all.add(this) }
        override fun load() { st.value = p.getString(key, def) ?: def }
        override fun getValue(thisRef: Any?, property: KProperty<*>) = st.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            st.value = value; p.edit().putString(key, value).apply(); pushToEngine()
        }
    }

    // Klang
    private var toneName by StringPref("toneName", Tone.ACOUSTIC.name)
    var drive by FloatPref("drive", .6f)
    var driveOn by BoolPref("driveOn", false)
    var chorus by FloatPref("chorus", .5f)
    var chorusOn by BoolPref("chorusOn", false)
    var delay by FloatPref("delayLevel", .5f)
    var delayOn by BoolPref("delayOn", false)
    var delayMs by IntPref("delayMs", 380)
    var delayFeedback by FloatPref("delayFeedback", .35f)
    var reverb by FloatPref("reverb", .25f)
    var reverbOn by BoolPref("reverbOn", true)
    var wahOn by BoolPref("wahOn", false)
    var wahPos by FloatPref("wahPos", .5f)
    var volume by FloatPref("volume", .8f)

    /** Neigungseffekt: off, up, down, wah. */
    var tilt by StringPref("tilt", "off")
    var tiltSens by FloatPref("tiltSens", .5f)

    // Griffbrett
    var tuningId by StringPref("tuning", "standard")
    var customTuning by StringPref("customTuning", "40,45,50,55,59,64")
    var a4 by IntPref("a4", 440)
    var frets by IntPref("frets", 5)
    var margin by FloatPref("margin", .065f)
    var lefty by BoolPref("lefty", false)
    var portrait by BoolPref("portrait", false)
    var showNotes by BoolPref("showNotes", true)
    var tapPlays by BoolPref("tapPlays", true)
    var bending by BoolPref("bending", true)
    var openHammer by BoolPref("openHammer", false)
    var pullOffOpen by BoolPref("pullOffOpen", false)
    var haptics by BoolPref("haptics", true)
    var position by IntPref("position", 0)

    // Stimmgeraet und App
    var autoMode by BoolPref("autoMode", true)
    var chromatic by BoolPref("chromatic", false)
    var screen by StringPref("screen", "guitar")

    /** Aktueller Wert des Neigungssensors 0 … 1 (nicht gespeichert). */
    @Volatile private var tiltValue = 0f

    fun init(context: Context) {
        p = context.getSharedPreferences("guitar", Context.MODE_PRIVATE)
        for (x in all) x.load()
        pushToEngine()
    }

    var tone: Tone
        get() = Tone.entries.firstOrNull { it.name == toneName } ?: Tone.ACOUSTIC
        set(v) { toneName = v.name }

    private fun pushToEngine() {
        if (!::p.isInitialized) return
        GuitarEngine.tone = tone
        GuitarEngine.drive = if (driveOn) drive else 0f
        GuitarEngine.chorus = if (chorusOn) chorus else 0f
        GuitarEngine.delay = if (delayOn) delay else 0f
        GuitarEngine.delaySeconds = delayMs / 1000f
        GuitarEngine.delayFeedback = delayFeedback
        GuitarEngine.reverb = if (reverbOn) reverb else 0f
        GuitarEngine.volume = volume
        applyTilt(tiltValue)
    }

    /** Vom Griffbrett mit dem Sensorwert aufgerufen. */
    fun applyTilt(v: Float) {
        tiltValue = v
        GuitarEngine.whammyCents = when (tilt) {
            "up" -> v * 200f
            "down" -> -v * 1200f
            else -> 0f
        }
        GuitarEngine.wah = when {
            !wahOn && tilt != "wah" -> -1f
            tilt == "wah" -> v
            else -> wahPos
        }
    }

    val tuning: Tuning
        get() = if (tuningId == "custom") Tuning("custom", de.tonyapps.guitar.R.string.t_custom, customNotes()) else tuningById(tuningId)

    fun customNotes(): IntArray {
        val parts = customTuning.split(",").mapNotNull { it.trim().toIntOrNull() }
        return if (parts.size == 6) parts.toIntArray() else intArrayOf(40, 45, 50, 55, 59, 64)
    }

    /** Saiten auf dem Griffbrett: eigene Saiten des Instruments oder die Gitarrenstimmung. */
    val boardNotes: IntArray get() = tone.notes ?: tuning.notes

    /** Tonhoehe einer Saite auf dem Griffbrett in Hertz, mit Bund und Ziehen in Cent. */
    fun stringFreq(string: Int, fret: Int, bendCents: Float = 0f): Float =
        midiToFreq(boardNotes[string] + fret + bendCents / 100f, a4.toFloat())

    /** Tonhoehe einer leeren Gitarrensaite (Stimmgeraet). */
    fun guitarFreq(string: Int): Float = midiToFreq(tuning.notes[string].toFloat(), a4.toFloat())

    fun applyPreset(preset: Preset) {
        tone = preset.tone
        driveOn = preset.drive > 0f
        if (preset.drive > 0f) drive = preset.drive
        chorusOn = preset.chorus > 0f
        if (preset.chorus > 0f) chorus = preset.chorus
        delayOn = preset.delay > 0f
        if (preset.delay > 0f) delay = preset.delay
        reverbOn = preset.reverb > 0f
        if (preset.reverb > 0f) reverb = preset.reverb
        wahOn = preset.wah
        tilt = preset.tilt
    }

    fun restoreDefaults() {
        p.edit()
            .remove("toneName").remove("drive").remove("driveOn").remove("chorus").remove("chorusOn")
            .remove("delayLevel").remove("delayOn").remove("delayMs").remove("delayFeedback")
            .remove("reverb").remove("reverbOn").remove("wahOn").remove("wahPos").remove("volume")
            .remove("tilt").remove("tiltSens").remove("margin").remove("frets").remove("tapPlays")
            .remove("bending").remove("openHammer").remove("pullOffOpen")
            .apply()
        for (x in all) x.load()
        pushToEngine()
    }

    const val MAX_FRET = 22
}

data class Preset(
    val nameRes: Int,
    val tone: Tone,
    val drive: Float = 0f,
    val chorus: Float = 0f,
    val delay: Float = 0f,
    val reverb: Float = .25f,
    val wah: Boolean = false,
    val tilt: String = "off",
)

val PRESETS = listOf(
    Preset(de.tonyapps.guitar.R.string.p_acoustic, Tone.ACOUSTIC, reverb = .25f),
    Preset(de.tonyapps.guitar.R.string.p_lead, Tone.CLEAN, drive = .75f, delay = .35f, reverb = .3f, wah = true, tilt = "up"),
    Preset(de.tonyapps.guitar.R.string.p_clean, Tone.CLEAN, chorus = .6f, reverb = .35f),
    Preset(de.tonyapps.guitar.R.string.p_rock, Tone.ROCK, drive = .3f, reverb = .15f),
    Preset(de.tonyapps.guitar.R.string.p_wah, Tone.CLEAN, drive = .5f, reverb = .2f, wah = true, tilt = "wah"),
    Preset(de.tonyapps.guitar.R.string.p_classical, Tone.NYLON, reverb = .35f),
    Preset(de.tonyapps.guitar.R.string.p_muted, Tone.MUTED, reverb = .1f),
    Preset(de.tonyapps.guitar.R.string.p_bass, Tone.BASS, reverb = 0f),
    Preset(de.tonyapps.guitar.R.string.p_ukulele, Tone.UKULELE, reverb = .2f),
)
