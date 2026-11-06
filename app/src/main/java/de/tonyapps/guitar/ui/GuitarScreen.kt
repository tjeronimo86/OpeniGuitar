package de.tonyapps.guitar.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import de.tonyapps.guitar.R
import de.tonyapps.guitar.audio.GuitarEngine
import de.tonyapps.guitar.audio.Tone
import de.tonyapps.guitar.data.Settings
import de.tonyapps.guitar.data.noteName
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private val Inlays = intArrayOf(3, 5, 7, 9, 15, 17, 19, 21)
private val Doubles = intArrayOf(12)

/** Anteil der Halslaenge, ab dem der Korpus beginnt. */
private const val BODY = .76f

/** Saitenstaerken in dp, tiefste Saite zuerst. */
private fun thickness(tone: Tone, n: Int): FloatArray = when (tone) {
    Tone.BASS -> floatArrayOf(6.2f, 5.4f, 4.6f, 3.8f)
    Tone.UKULELE -> floatArrayOf(2.4f, 2.8f, 2.6f, 2.2f)
    else -> floatArrayOf(4.6f, 3.9f, 3.2f, 2.5f, 2.0f, 1.6f)
}.let { if (it.size == n) it else FloatArray(n) { 3f } }

@Composable
fun GuitarScreen(onTuner: () -> Unit, onOptions: () -> Unit) {
    val portrait = Settings.portrait
    ScreenMode(landscape = !portrait, immersive = true)
    TiltSensor()
    var effects by remember { mutableStateOf(false) }
    var scrollMode by remember { mutableStateOf(false) }
    val lefty = Settings.lefty
    val maxPos = Settings.MAX_FRET - Settings.frets
    LaunchedEffect(maxPos) { if (Settings.position > maxPos) Settings.position = maxPos }
    val pos by animateFloatAsState(Settings.position.toFloat(), spring(dampingRatio = .9f, stiffness = 500f), label = "pos")
    val down = { Settings.position = (Settings.position - 1).coerceIn(0, maxPos) }
    val up = { Settings.position = (Settings.position + 1).coerceIn(0, maxPos) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        Fretboard(pos, scrollMode, Modifier.fillMaxSize())

        val badge: @Composable (Modifier) -> Unit = { m ->
            if (Settings.position > 0) {
                Text(
                    stringResource(R.string.fret_n, Settings.position + 1),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = .8f),
                    modifier = m
                        .padding(8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Black.copy(alpha = .55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        if (!portrait) {
            val w = maxWidth
            val bodyStart = w * BODY
            // Pfeile: einer am Sattel (Richtung Kopf), einer am Korpus
            Arrow(-1f * (if (lefty) -1 else 1), Settings.position > 0, Modifier.align(if (lefty) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 4.dp), down)
            Arrow(1f * (if (lefty) -1 else 1), Settings.position < maxPos, Modifier.align(Alignment.CenterStart).offset(x = if (lefty) w - bodyStart - 40.dp else bodyStart + 6.dp), up)
            badge(Modifier.align(if (lefty) Alignment.TopEnd else Alignment.TopStart))
            Column(
                Modifier
                    .align(if (lefty) Alignment.CenterStart else Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(46.dp)
                    .padding(vertical = 6.dp, horizontal = 3.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SideTab(stringResource(R.string.tab_effects), effects, true, Modifier.weight(1f)) { effects = !effects }
                SideTab(stringResource(R.string.tab_scroll), scrollMode, true, Modifier.weight(1f)) { scrollMode = !scrollMode }
                SideTab(stringResource(R.string.tab_tuner), false, true, Modifier.weight(1f), onTuner)
                SideTab(stringResource(R.string.tab_options), false, true, Modifier.weight(1f), onOptions)
            }
        } else {
            // Hochformat wie die spaetere iPhone-Version: Sattel oben, Knoepfe unten
            val h = maxHeight
            Arrow(-2f, Settings.position > 0, Modifier.align(Alignment.TopCenter).padding(top = 4.dp), down)
            Arrow(2f, Settings.position < maxPos, Modifier.align(Alignment.TopCenter).offset(y = h * BODY + 6.dp), up)
            badge(Modifier.align(Alignment.TopStart))
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SideTab(stringResource(R.string.tab_options), false, false, Modifier.weight(1f), onOptions)
                SideTab(stringResource(R.string.tab_scroll), scrollMode, false, Modifier.weight(1f)) { scrollMode = !scrollMode }
                SideTab(stringResource(R.string.tab_effects), effects, false, Modifier.weight(1f)) { effects = !effects }
                SideTab(stringResource(R.string.tab_tuner), false, false, Modifier.weight(1f), onTuner)
            }
        }

        EffectsPanel(effects, portrait, lefty) { effects = false }
    }
}

/**
 * Pfeil. dir: -1 links, 1 rechts, -2 hoch, 2 runter.
 */
@Composable
private fun Arrow(dir: Float, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val vertical = abs(dir) > 1.5f
    Box(
        modifier
            .size(width = if (vertical) 64.dp else 34.dp, height = if (vertical) 34.dp else 64.dp)
            .alpha(if (enabled) 1f else .3f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF6E6E6E).copy(alpha = .75f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val angle = when {
            dir < -1.5f -> 90f
            dir > 1.5f -> 90f
            else -> 0f
        }
        ChevronGlyph(Color.White, left = dir < -1.5f || (dir < 0 && dir > -1.5f), modifier = Modifier.size(24.dp).rotate(angle))
    }
}

@Composable
private fun SideTab(label: String, active: Boolean, vertical: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Box(
        modifier
            .let { if (vertical) it.fillMaxWidth() else it.fillMaxHeight() }
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF4A4D50), Color(0xFF2E3033))))
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (vertical) {
            Canvas(Modifier.fillMaxSize()) {
                val style = TextStyle(
                    color = if (active) Green else Color.White.copy(alpha = .85f),
                    fontSize = with(density) { 15.dp.toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
                val t = measurer.measure(label, style)
                rotate(-90f) {
                    drawText(t, topLeft = Offset(center.x - t.size.width / 2f, center.y - t.size.height / 2f))
                }
            }
        } else {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (active) Green else Color.White.copy(alpha = .85f), maxLines = 1)
        }
    }
}

/** Neigungssensor fuer Whammy und Wah, nur solange die App sichtbar ist. */
@Composable
private fun TiltSensor() {
    val context = LocalContext.current
    val mode = Settings.tilt
    LifecycleResumeEffect(mode) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            var base = Float.NaN
            var smooth = Float.NaN
            override fun onSensorChanged(e: SensorEvent) {
                val gy = e.values[1] / 9.81f
                smooth = if (smooth.isNaN()) gy else smooth + (gy - smooth) * .3f
                if (base.isNaN()) base = smooth
                val sens = .6f + Settings.tiltSens * 2.6f
                Settings.applyTilt((abs(smooth - base) * sens).coerceIn(0f, 1f))
            }

            override fun onAccuracyChanged(s: Sensor?, accuracy: Int) {}
        }
        if (mode != "off" && sensor != null) sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onPauseOrDispose {
            sm.unregisterListener(listener)
            Settings.applyTilt(0f)
        }
    }
}

// ---------------------------------------------------------------------------------------
// Griffbrett: Zeichnen und Mehrfinger-Bedienung
// ---------------------------------------------------------------------------------------

private const val ZONE_FRET = 0
private const val ZONE_BODY = 1
private const val ZONE_SCROLL = 2

private class Touch(
    val zone: Int,
    var string: Int,
    var fret: Int,
    var downY: Float,
    var x: Float,
    var y: Float,
) {
    var bend = 0f
    var startPos = 0f
    var startX = 0f
}

/**
 * Umrechnung zwischen Bildschirm und "Griffbrett-Koordinaten": dort liegt der Sattel immer links,
 * die tiefste Saite unten. Querformat, Hochformat und Linkshaender sind nur andere Abbildungen.
 */
private class Mapping(val sw: Float, val sh: Float, val portrait: Boolean, val lefty: Boolean) {
    val lw = if (portrait) sh else sw
    val lh = if (portrait) sw else sh

    fun toLogical(x: Float, y: Float): Offset = when {
        !portrait && !lefty -> Offset(x, y)
        !portrait -> Offset(sw - x, y)
        !lefty -> Offset(y, sw - x)
        else -> Offset(y, x)
    }

    fun toScreen(lx: Float, ly: Float): Offset = when {
        !portrait && !lefty -> Offset(lx, ly)
        !portrait -> Offset(sw - lx, ly)
        !lefty -> Offset(sw - ly, lx)
        else -> Offset(ly, lx)
    }
}

private class Geo(val w: Float, val h: Float, val frets: Int, val strings: Int, marginFrac: Float) {
    val nutW = w * .022f
    val x0 = w * .055f
    val body = w * BODY
    val fretW = (body - x0) / frets
    val margin = h * marginFrac
    val spacing = (h - 2 * margin) / (strings - 1)

    fun rowY(string: Int) = margin + (strings - 1 - string) * spacing
    fun stringAt(y: Float): Int = strings - 1 - ((y - margin) / spacing).roundToInt().coerceIn(0, strings - 1)
    fun fretX(fret: Int, pos: Float) = x0 + (fret - pos) * fretW
    fun nutX(pos: Float) = x0 - pos * fretW

    /** Bund unter dem Finger, 0 = links vom Sattel. */
    fun fretAt(x: Float, pos: Float): Int = (floor((x - x0) / fretW + pos).toInt() + 1).coerceIn(0, Settings.MAX_FRET)
}

private class Board {
    val touches = HashMap<Long, Touch>()
    var dirty = true
    var geo: Geo? = null
    var map: Mapping? = null
    var grain: List<Pair<Path, Color>> = emptyList()
    var grainKey = 0L

    fun heldFret(s: Int): Touch? {
        var best: Touch? = null
        for (t in touches.values) if (t.zone == ZONE_FRET && t.string == s && (best == null || t.fret > best.fret)) best = t
        return best
    }

    fun freq(s: Int): Float {
        val t = heldFret(s)
        return Settings.stringFreq(s, t?.fret ?: 0, t?.bend ?: 0f)
    }

    /** Finger von Saite s genommen: Pull-off, leere Saite oder abdaempfen. */
    fun release(s: Int) {
        when {
            heldFret(s) != null -> GuitarEngine.glide(s, freq(s))
            Settings.pullOffOpen -> GuitarEngine.glide(s, freq(s))
            else -> GuitarEngine.damp(s, .12f)
        }
    }
}

@Composable
private fun Fretboard(position: Float, scrollMode: Boolean, modifier: Modifier) {
    val view = LocalView.current
    val board = remember { Board() }
    val pos by rememberUpdatedState(position)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var frame by remember { mutableLongStateOf(0L) }
    val strings = Settings.boardNotes.size

    // Instrument gewechselt: alte Finger vergessen
    LaunchedEffect(strings) { board.touches.clear() }

    // Neu zeichnen, solange Saiten klingen oder Finger sich bewegen
    LaunchedEffect(Unit) {
        var lastPos = -1f
        while (true) {
            withFrameNanos { t ->
                var ringing = false
                for (i in 0 until 6) if (GuitarEngine.level(i) > .002f) ringing = true
                if (board.dirty || ringing || lastPos != pos) {
                    frame = t
                    board.dirty = false
                    lastPos = pos
                }
            }
        }
    }

    fun pluck(s: Int, velocity: Float) {
        GuitarEngine.pluck(s, board.freq(s), velocity)
        if (Settings.haptics) tick(view)
    }

    /** Bund gedrueckt: anschlagen oder Hammer-on. */
    fun press(s: Int, fret: Int, before: Touch?) {
        val ringing = GuitarEngine.level(s) > .02f
        val hammer = ringing && ((before != null && fret > before.fret) || (before == null && Settings.openHammer))
        if (Settings.tapPlays && !hammer) pluck(s, .9f) else GuitarEngine.glide(s, board.freq(s))
    }

    Canvas(
        modifier.pointerInput(scrollMode) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val geo = board.geo ?: continue
                    val map = board.map ?: continue
                    for (ch in event.changes) {
                        val id = ch.id.value
                        val l = map.toLogical(ch.position.x, ch.position.y)
                        val x = l.x
                        val y = l.y
                        val p = pos
                        when {
                            ch.changedToDown() -> {
                                val s = geo.stringAt(y)
                                val fret = geo.fretAt(x, p)
                                when {
                                    scrollMode -> board.touches[id] = Touch(ZONE_SCROLL, s, 0, y, x, y).also {
                                        it.startPos = p
                                        it.startX = x
                                    }
                                    x < geo.body && fret > 0 -> {
                                        val before = board.heldFret(s)
                                        board.touches[id] = Touch(ZONE_FRET, s, fret, y, x, y)
                                        press(s, fret, before)
                                    }
                                    else -> {
                                        board.touches[id] = Touch(ZONE_BODY, s, 0, y, x, y)
                                        pluck(s, .85f + Random.nextFloat() * .15f)
                                    }
                                }
                            }
                            ch.changedToUp() -> {
                                val t = board.touches.remove(id)
                                if (t != null && t.zone == ZONE_FRET) board.release(t.string)
                            }
                            ch.pressed && ch.positionChanged() -> {
                                val t = board.touches[id]
                                if (t != null) {
                                    t.x = x
                                    t.y = y
                                    when (t.zone) {
                                        ZONE_SCROLL -> {
                                            val target = (t.startPos - (x - t.startX) / geo.fretW).roundToInt()
                                            val clamped = target.coerceIn(0, Settings.MAX_FRET - Settings.frets)
                                            if (clamped != Settings.position) Settings.position = clamped
                                        }
                                        ZONE_BODY -> {
                                            // Ueber die Saiten streichen = Akkord anschlagen
                                            val s = geo.stringAt(y)
                                            if (s != t.string) {
                                                val dir = if (s > t.string) 1 else -1
                                                var k = t.string + dir
                                                while (true) {
                                                    pluck(k, .75f + Random.nextFloat() * .2f)
                                                    if (k == s) break
                                                    k += dir
                                                }
                                                t.string = s
                                            }
                                        }
                                        else -> {
                                            val rowY = geo.rowY(t.string)
                                            val limit = if (Settings.bending) .95f else .5f
                                            if (abs(y - rowY) > geo.spacing * limit) {
                                                // Auf eine andere Saite gerutscht
                                                val old = t.string
                                                val before = board.heldFret(geo.stringAt(y))
                                                t.string = geo.stringAt(y)
                                                t.downY = y
                                                t.bend = 0f
                                                t.fret = geo.fretAt(x, p).coerceAtLeast(1)
                                                board.release(old)
                                                press(t.string, t.fret, before)
                                            } else {
                                                val fret = geo.fretAt(x, p).coerceAtLeast(1)
                                                // Ziehen: Finger quer zur Saite schieben, bis zu einem Ganzton
                                                val bend = if (!Settings.bending) 0f else
                                                    ((abs(y - t.downY) - geo.spacing * .08f) / (geo.spacing * .6f)).coerceIn(0f, 1f) * 200f
                                                if (fret != t.fret || abs(bend - t.bend) > 1f) {
                                                    t.fret = fret
                                                    t.bend = bend
                                                    GuitarEngine.glide(t.string, board.freq(t.string))
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        ch.consume()
                    }
                    board.dirty = true
                }
            }
        },
    ) {
        @Suppress("UNUSED_VARIABLE")
        val f = frame
        val portrait = Settings.portrait
        val lefty = Settings.lefty
        val map = Mapping(size.width, size.height, portrait, lefty)
        board.map = map
        val geo = board.geo?.takeIf {
            it.w == map.lw && it.h == map.lh && it.frets == Settings.frets && it.strings == strings && it.margin == map.lh * Settings.margin
        } ?: Geo(map.lw, map.lh, Settings.frets, strings, Settings.margin).also { board.geo = it }
        val key = (map.lw.toLong() shl 20) xor map.lh.toLong()
        if (board.grainKey != key) {
            board.grain = makeGrain(map.lw, map.lh)
            board.grainKey = key
        }
        withTransform({
            when {
                !portrait && lefty -> scale(-1f, 1f, pivot = center)
                portrait && !lefty -> {
                    translate(left = size.width)
                    rotate(90f, pivot = Offset.Zero)
                }
                portrait -> {
                    scale(-1f, 1f, pivot = Offset.Zero)
                    rotate(90f, pivot = Offset.Zero)
                }
                else -> {}
            }
        }) {
            drawBoard(geo, board, position)
        }
        drawLabels(geo, map, board, position, measurer, density.density)
    }
}

private fun makeGrain(w: Float, h: Float): List<Pair<Path, Color>> {
    val rnd = Random(7)
    val colors = listOf(Color(0x24000000), Color(0x18FFD6B0), Color(0x30000000), Color(0x10FFFFFF))
    val paths = List(colors.size) { Path() }
    repeat(170) {
        val p = paths[rnd.nextInt(paths.size)]
        var y = rnd.nextFloat() * h
        var x = -20f
        p.moveTo(x, y)
        while (x < w + 20f) {
            x += 30f + rnd.nextFloat() * 50f
            y += (rnd.nextFloat() - .5f) * 3f
            p.lineTo(x, y)
        }
    }
    return paths.zip(colors)
}

private fun DrawScope.drawBoard(g: Geo, board: Board, pos: Float) {
    val w = g.w
    val h = g.h
    val nutRight = g.nutX(pos)
    val woodLeft = max(0f, nutRight)
    val woodRight = g.body + w * .035f
    val tone = Settings.tone

    // Holz
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFF3E2318), Color(0xFF4A2A1C), Color(0xFF331C12)), 0f, h),
        Offset(woodLeft, 0f),
        Size(woodRight - woodLeft, h),
    )
    clipRect(woodLeft, 0f, woodRight, h) {
        // Maserung wandert mit dem Hals
        val shift = -(pos * g.fretW) % w
        for ((path, color) in board.grain) {
            translate(left = shift) { drawPath(path, color, style = Stroke(1.6f)) }
            translate(left = shift + w) { drawPath(path, color, style = Stroke(1.6f)) }
        }
    }
    // Rand oben und unten etwas dunkler
    drawRect(Brush.verticalGradient(listOf(Color(0x66000000), Color.Transparent), 0f, h * .06f), Offset(woodLeft, 0f), Size(woodRight - woodLeft, h * .06f))
    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0x66000000)), h * .94f, h), Offset(woodLeft, h * .94f), Size(woodRight - woodLeft, h * .06f))

    // Einlagen
    val r = min(g.spacing * .2f, h * .045f)
    fun dot(cx: Float, cy: Float) {
        drawCircle(Color(0x55000000), r * 1.12f, Offset(cx, cy + 1.5f))
        drawCircle(
            Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFE4E2DE), Color(0xFFB9B6B0)), Offset(cx - r * .3f, cy - r * .3f), r * 1.5f),
            r,
            Offset(cx, cy),
        )
    }
    for (k in Inlays) {
        val cx = g.x0 + (k - .5f - pos) * g.fretW
        if (cx > woodLeft + r && cx < g.body) dot(cx, h / 2)
    }
    for (k in Doubles) {
        val cx = g.x0 + (k - .5f - pos) * g.fretW
        if (cx > woodLeft + r && cx < g.body) {
            dot(cx, h * .3f)
            dot(cx, h * .7f)
        }
    }

    // Bundstaebchen
    val fw = max(3f, w * .0105f)
    for (k in 1..Settings.MAX_FRET) {
        val x = g.fretX(k, pos)
        if (x < woodLeft - fw || x > g.body + fw) continue
        drawRect(Color(0x66000000), Offset(x + fw / 2, h * .012f), Size(fw * .6f, h * .976f))
        drawRect(
            Brush.horizontalGradient(listOf(Color(0xFF8C8C8C), Color(0xFFF4F4F4), Color(0xFFBDBDBD), Color(0xFF6D6D6D)), x - fw / 2, x + fw / 2),
            Offset(x - fw / 2, h * .012f),
            Size(fw, h * .976f),
        )
    }

    // Uebergang zum Korpus
    drawRect(
        Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), g.body - w * .03f, woodRight),
        Offset(g.body - w * .03f, 0f),
        Size(woodRight - g.body + w * .03f + 1f, h),
    )

    // Sattel
    if (nutRight > 0f) {
        drawRect(
            Brush.horizontalGradient(listOf(Color(0xFFD9CDB6), Color(0xFFFFFBF1), Color(0xFFE8DEC9)), nutRight - g.nutW, nutRight),
            Offset(nutRight - g.nutW, 0f),
            Size(g.nutW, h),
        )
        drawRect(Color(0x55000000), Offset(nutRight, 0f), Size(2f, h))
    }

    // Saiten
    val thick = thickness(tone, g.strings)
    for (s in 0 until g.strings) {
        val y = g.rowY(s)
        val tw = thick[s].dp.toPx()
        val held = board.heldFret(s)
        val from = if (held != null) g.fretX(held.fret, pos) else max(0f, nutRight)
        val amp = min(GuitarEngine.level(s) * g.spacing * .3f, g.spacing * .2f)
        // Nylon: Diskant hell und glatt; Gitarre: die drei tiefen umsponnen
        val nylonString = tone.nylon && (tone == Tone.UKULELE || s >= 3)
        val wound = !nylonString && (tone == Tone.BASS || s <= 2)
        val brush = if (nylonString) {
            Brush.verticalGradient(listOf(Color(0xFFBDB6A6), Color(0xFFFFFBF0), Color(0xFFCFC6B2)), y - tw / 2, y + tw / 2)
        } else if (tone.nylon) {
            Brush.verticalGradient(listOf(Color(0xFF8A7650), Color(0xFFF0DDB0), Color(0xFF9C8459), Color(0xFF5A4A2E)), y - tw / 2, y + tw / 2)
        } else {
            Brush.verticalGradient(listOf(Color(0xFF6F6F6F), Color(0xFFF5F5F5), Color(0xFF8A8A8A), Color(0xFF4A4A4A)), y - tw / 2, y + tw / 2)
        }
        // Schatten auf dem Holz
        drawLine(Color(0x88000000), Offset(0f, y + tw * .9f + 1.5f), Offset(w, y + tw * .9f + 1.5f), tw * .9f)
        if (amp > .5f) {
            drawLine(brush, Offset(0f, y), Offset(from, y), tw)
            // Schwingende Saite als Unschaerfe zwischen den beiden Auslenkungen
            for (sign in intArrayOf(-1, 1)) {
                val path = Path()
                val n = 24
                for (i in 0..n) {
                    val t = i / n.toFloat()
                    val xx = from + (w - from) * t
                    val yy = y + sign * amp * sin(Math.PI.toFloat() * t)
                    if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
                }
                drawPath(path, Color(0xFFE6E6E6).copy(alpha = .5f), style = Stroke(tw))
            }
        } else {
            drawLine(brush, Offset(0f, y), Offset(w, y), tw)
            if (wound) {
                drawLine(
                    Color(0x55000000),
                    Offset(0f, y),
                    Offset(w, y),
                    tw * .8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.4f * density, 1.2f * density)),
                )
            }
        }
    }

    // Finger
    for (t in board.touches.values) {
        when (t.zone) {
            ZONE_FRET -> {
                val c = fingerCenter(g, t, pos)
                val rr = min(g.spacing * .36f, h * .08f)
                drawCircle(Color(0x66000000), rr * 1.08f, Offset(c.x, c.y + 3f))
                drawCircle(Color.White.copy(alpha = .88f), rr, c)
            }
            ZONE_BODY -> drawCircle(Color.White.copy(alpha = .25f), g.spacing * .32f, Offset(t.x, t.y), style = Stroke(2.dp.toPx()))
            else -> {}
        }
    }
}

private fun fingerCenter(g: Geo, t: Touch, pos: Float): Offset = Offset(
    g.x0 + (t.fret - .5f - pos) * g.fretW,
    g.rowY(t.string) + (t.y - t.downY).coerceIn(-g.spacing * .6f, g.spacing * .6f),
)

/** Notennamen auf den Fingern, immer aufrecht. */
private fun DrawScope.drawLabels(g: Geo, map: Mapping, board: Board, pos: Float, measurer: TextMeasurer, density: Float) {
    if (!Settings.showNotes) return
    val notes = Settings.boardNotes
    for (t in board.touches.values) {
        if (t.zone != ZONE_FRET || t.string >= notes.size) continue
        val l = fingerCenter(g, t, pos)
        val c = map.toScreen(l.x, l.y)
        val style = TextStyle(color = Color(0xFF2A2A2A), fontSize = (min(g.spacing, g.h * .22f) * .3f / density).sp, fontWeight = FontWeight.Bold)
        val m = measurer.measure(noteName(notes[t.string] + t.fret), style)
        drawText(m, topLeft = Offset(c.x - m.size.width / 2f, c.y - m.size.height / 2f))
    }
}
