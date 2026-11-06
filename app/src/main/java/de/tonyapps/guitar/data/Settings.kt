package de.tonyapps.guitar.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import de.tonyapps.guitar.audio.GuitarEngine
import de.tonyapps.guitar.audio.Tone

/** Alle Einstellungen, als Compose-State und dauerhaft gespeichert. */
object Settings {
    private lateinit var p: SharedPreferences

    private val _tone = mutableStateOf(Tone.ACOUSTIC)
    private val _drive = mutableFloatStateOf(0f)
    private val _chorus = mutableFloatStateOf(0f)
    private val _delay = mutableFloatStateOf(0f)
    private val _reverb = mutableFloatStateOf(.25f)
    private val _volume = mutableFloatStateOf(.8f)
    private val _tuning = mutableStateOf("standard")
    private val _a4 = mutableIntStateOf(440)
    private val _frets = mutableIntStateOf(5)
    private val _lefty = mutableStateOf(false)
    private val _showNotes = mutableStateOf(true)
    private val _tapPlays = mutableStateOf(true)
    private val _releaseMutes = mutableStateOf(true)
    private val _haptics = mutableStateOf(true)
    private val _position = mutableIntStateOf(0)
    private val _autoMode = mutableStateOf(true)
    private val _chromatic = mutableStateOf(false)
    private val _screen = mutableStateOf("guitar")

    fun init(context: Context) {
        p = context.getSharedPreferences("guitar", Context.MODE_PRIVATE)
        _tone.value = Tone.entries.getOrElse(p.getInt("tone", 0)) { Tone.ACOUSTIC }
        _drive.floatValue = p.getFloat("drive", 0f)
        _chorus.floatValue = p.getFloat("chorus", 0f)
        _delay.floatValue = p.getFloat("delay", 0f)
        _reverb.floatValue = p.getFloat("reverb", .25f)
        _volume.floatValue = p.getFloat("volume", .8f)
        _tuning.value = p.getString("tuning", "standard") ?: "standard"
        _a4.intValue = p.getInt("a4", 440)
        _frets.intValue = p.getInt("frets", 5)
        _lefty.value = p.getBoolean("lefty", false)
        _showNotes.value = p.getBoolean("showNotes", true)
        _tapPlays.value = p.getBoolean("tapPlays", true)
        _releaseMutes.value = p.getBoolean("releaseMutes", true)
        _haptics.value = p.getBoolean("haptics", true)
        _position.intValue = p.getInt("position", 0)
        _autoMode.value = p.getBoolean("autoMode", true)
        _chromatic.value = p.getBoolean("chromatic", false)
        _screen.value = p.getString("screen", "guitar") ?: "guitar"
        pushToEngine()
    }

    private fun pushToEngine() {
        GuitarEngine.tone = tone
        GuitarEngine.drive = drive
        GuitarEngine.chorus = chorus
        GuitarEngine.delay = delay
        GuitarEngine.reverb = reverb
        GuitarEngine.volume = volume
    }

    var tone: Tone
        get() = _tone.value
        set(v) { _tone.value = v; p.edit().putInt("tone", v.ordinal).apply(); GuitarEngine.tone = v }
    var drive: Float
        get() = _drive.floatValue
        set(v) { _drive.floatValue = v; p.edit().putFloat("drive", v).apply(); GuitarEngine.drive = v }
    var chorus: Float
        get() = _chorus.floatValue
        set(v) { _chorus.floatValue = v; p.edit().putFloat("chorus", v).apply(); GuitarEngine.chorus = v }
    var delay: Float
        get() = _delay.floatValue
        set(v) { _delay.floatValue = v; p.edit().putFloat("delay", v).apply(); GuitarEngine.delay = v }
    var reverb: Float
        get() = _reverb.floatValue
        set(v) { _reverb.floatValue = v; p.edit().putFloat("reverb", v).apply(); GuitarEngine.reverb = v }
    var volume: Float
        get() = _volume.floatValue
        set(v) { _volume.floatValue = v; p.edit().putFloat("volume", v).apply(); GuitarEngine.volume = v }
    var tuningId: String
        get() = _tuning.value
        set(v) { _tuning.value = v; p.edit().putString("tuning", v).apply() }
    var a4: Int
        get() = _a4.intValue
        set(v) { _a4.intValue = v; p.edit().putInt("a4", v).apply() }
    var frets: Int
        get() = _frets.intValue
        set(v) { _frets.intValue = v; p.edit().putInt("frets", v).apply() }
    var lefty: Boolean
        get() = _lefty.value
        set(v) { _lefty.value = v; p.edit().putBoolean("lefty", v).apply() }
    var showNotes: Boolean
        get() = _showNotes.value
        set(v) { _showNotes.value = v; p.edit().putBoolean("showNotes", v).apply() }
    var tapPlays: Boolean
        get() = _tapPlays.value
        set(v) { _tapPlays.value = v; p.edit().putBoolean("tapPlays", v).apply() }
    var releaseMutes: Boolean
        get() = _releaseMutes.value
        set(v) { _releaseMutes.value = v; p.edit().putBoolean("releaseMutes", v).apply() }
    var haptics: Boolean
        get() = _haptics.value
        set(v) { _haptics.value = v; p.edit().putBoolean("haptics", v).apply() }
    var position: Int
        get() = _position.intValue
        set(v) { _position.intValue = v; p.edit().putInt("position", v).apply() }
    var autoMode: Boolean
        get() = _autoMode.value
        set(v) { _autoMode.value = v; p.edit().putBoolean("autoMode", v).apply() }
    var chromatic: Boolean
        get() = _chromatic.value
        set(v) { _chromatic.value = v; p.edit().putBoolean("chromatic", v).apply() }
    var screen: String
        get() = _screen.value
        set(v) { _screen.value = v; p.edit().putString("screen", v).apply() }

    val tuning: Tuning get() = tuningById(tuningId)

    /** Tonhoehe einer Saite in Hertz, mit Bund und Ziehen in Cent. */
    fun stringFreq(string: Int, fret: Int, bendCents: Float = 0f): Float =
        midiToFreq(tuning.notes[string] + fret + bendCents / 100f, a4.toFloat())

    const val MAX_FRET = 22
}
