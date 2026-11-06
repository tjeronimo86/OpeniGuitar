package de.tonyapps.guitar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
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
import kotlin.random.Random

private val Inlays = intArrayOf(3, 5, 7, 9, 15, 17, 19, 21)
private val Doubles = intArrayOf(12)

/** Saite 0 = tiefe E, von unten nach oben gezeichnet wie auf dem Original. */
private val ThickDp = floatArrayOf(4.6f, 3.9f, 3.2f, 2.5f, 2.0f, 1.6f)

@Composable
fun GuitarScreen(onTuner: () -> Unit, onOptions: () -> Unit) {
    ScreenMode(landscape = true)
    var effects by remember { mutableStateOf(false) }
    var scrollMode by remember { mutableStateOf(false) }
    val lefty = Settings.lefty
    val frets = Settings.frets
    val maxPos = Settings.MAX_FRET - frets
    LaunchedEffect(maxPos) { if (Settings.position > maxPos) Settings.position = maxPos }
    val pos by animateFloatAsState(Settings.position.toFloat(), spring(dampingRatio = .9f, stiffness = 500f), label = "pos")

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val w = maxWidth
        Fretboard(pos, scrollMode, Modifier.fillMaxSize())

        // Pfeile zum Verschieben: einer am Sattel (Richtung Kopf), einer am Korpus
        val bodyStart = w * BODY
        val down = { Settings.position = (Settings.position - 1).coerceIn(0, maxPos) }
        val up = { Settings.position = (Settings.position + 1).coerceIn(0, maxPos) }
        Arrow(
            left = !lefty,
            enabled = Settings.position > 0,
            modifier = Modifier
                .align(if (lefty) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 4.dp),
            onClick = down,
        )
        Arrow(
            left = lefty,
            enabled = Settings.position < maxPos,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = if (lefty) w - bodyStart - 40.dp else bodyStart + 6.dp),
            onClick = up,
        )

        if (Settings.position > 0) {
            Text(
                stringResource(R.string.fret_n, Settings.position + 1),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = .8f),
                modifier = Modifier
                    .align(if (lefty) Alignment.TopEnd else Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = .55f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        // Reiter am Korpus
        Column(
            Modifier
                .align(if (lefty) Alignment.CenterStart else Alignment.CenterEnd)
                .fillMaxHeight()
                .width(46.dp)
                .padding(vertical = 6.dp, horizontal = 3.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SideTab(stringResource(R.string.tab_effects), effects, Modifier.weight(1f)) { effects = !effects }
            SideTab(stringResource(R.string.tab_scroll), scrollMode, Modifier.weight(1f)) { scrollMode = !scrollMode }
            SideTab(stringResource(R.string.tab_tuner), false, Modifier.weight(1f), onTuner)
            SideTab(stringResource(R.string.tab_options), false, Modifier.weight(1f), onOptions)
        }

        EffectsPanel(effects, lefty) { effects = false }
    }
}

/** Anteil der Breite, ab dem der Korpus beginnt. */
private const val BODY = .76f

@Composable
private fun Arrow(left: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(width = 34.dp, height = 64.dp)
            .alpha(if (enabled) 1f else .3f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF6E6E6E).copy(alpha = .75f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        ChevronGlyph(Color.White, left = left, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SideTab(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF4A4D50), Color(0xFF2E3033))))
            .border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
    ) {
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

/** Masse im "Rechtshaender"-Koordinatensystem; Linkshaender wird gespiegelt. */
private class Geo(val w: Float, val h: Float, val frets: Int) {
    val nutW = w * .022f
    val x0 = w * .055f
    val body = w * BODY
    val fretW = (body - x0) / frets
    val margin = h * .065f
    val spacing = (h - 2 * margin) / 5f

    fun rowY(string: Int) = margin + (5 - string) * spacing
    fun stringAt(y: Float): Int = 5 - ((y - margin) / spacing).roundToInt().coerceIn(0, 5)
    fun fretX(fret: Int, pos: Float) = x0 + (fret - pos) * fretW
    fun nutX(pos: Float) = x0 - pos * fretW

    /** Bund unter dem Finger, 0 = links vom Sattel. */
    fun fretAt(x: Float, pos: Float): Int = (floor((x - x0) / fretW + pos).toInt() + 1).coerceIn(0, Settings.MAX_FRET)
}

private class Board {
    val touches = HashMap<Long, Touch>()
    var dirty = true
    var geo: Geo? = null
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
}

@Composable
private fun Fretboard(position: Float, scrollMode: Boolean, modifier: Modifier) {
    val view = LocalView.current
    val board = remember { Board() }
    val pos by rememberUpdatedState(position)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    var frame by remember { mutableLongStateOf(0L) }

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

    Canvas(
        modifier.pointerInput(scrollMode) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    val geo = board.geo ?: continue
                    for (ch in event.changes) {
                        val id = ch.id.value
                        val x = if (Settings.lefty) size.width - ch.position.x else ch.position.x
                        val y = ch.position.y
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
                                        val t = Touch(ZONE_FRET, s, fret, y, x, y)
                                        board.touches[id] = t
                                        if (Settings.tapPlays) pluck(s, .9f) else GuitarEngine.glide(s, board.freq(s))
                                    }
                                    else -> {
                                        board.touches[id] = Touch(ZONE_BODY, s, 0, y, x, y)
                                        pluck(s, .85f + Random.nextFloat() * .15f)
                                    }
                                }
                            }
                            ch.changedToUp() -> {
                                val t = board.touches.remove(id)
                                if (t != null && t.zone == ZONE_FRET) {
                                    val s = t.string
                                    when {
                                        board.heldFret(s) != null -> GuitarEngine.glide(s, board.freq(s)) // Pull-off
                                        Settings.releaseMutes -> GuitarEngine.damp(s, .12f)
                                        else -> GuitarEngine.glide(s, board.freq(s))
                                    }
                                }
                                if (t != null && t.zone == ZONE_SCROLL) {
                                    Settings.position = Settings.position.coerceIn(0, Settings.MAX_FRET - Settings.frets)
                                }
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
                                            if (abs(y - rowY) > geo.spacing * .95f) {
                                                // Auf eine andere Saite gerutscht
                                                val old = t.string
                                                t.string = geo.stringAt(y)
                                                t.downY = y
                                                t.bend = 0f
                                                if (board.heldFret(old) != null) {
                                                    GuitarEngine.glide(old, board.freq(old))
                                                } else if (Settings.releaseMutes) {
                                                    GuitarEngine.damp(old, .12f)
                                                }
                                                if (Settings.tapPlays) pluck(t.string, .85f) else GuitarEngine.glide(t.string, board.freq(t.string))
                                            } else {
                                                val fret = geo.fretAt(x, p).coerceAtLeast(1)
                                                // Ziehen: Finger quer zur Saite schieben, bis zu einem Ganzton
                                                val bend = ((abs(y - t.downY) - geo.spacing * .08f) / (geo.spacing * .6f)).coerceIn(0f, 1f) * 200f
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
        val geo = board.geo?.takeIf { it.w == size.width && it.h == size.height && it.frets == Settings.frets }
            ?: Geo(size.width, size.height, Settings.frets).also { board.geo = it }
        val key = (size.width.toLong() shl 20) xor size.height.toLong()
        if (board.grainKey != key) {
            board.grain = makeGrain(size.width, size.height)
            board.grainKey = key
        }
        val lefty = Settings.lefty
        scale(if (lefty) -1f else 1f, 1f, pivot = center) {
            drawBoard(geo, board, position)
        }
        drawLabels(geo, board, position, lefty, measurer, density.density)
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
    val r = g.spacing * .2f
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
            dot(cx, g.margin + 1.5f * g.spacing)
            dot(cx, g.margin + 3.5f * g.spacing)
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
    for (s in 0 until 6) {
        val y = g.rowY(s)
        val tw = ThickDp[s].dp.toPx()
        val held = board.heldFret(s)
        val from = if (held != null) g.fretX(held.fret, pos) else max(0f, nutRight)
        val amp = min(GuitarEngine.level(s) * g.spacing * .3f, g.spacing * .2f)
        val wound = s <= 2
        val brush = Brush.verticalGradient(
            listOf(Color(0xFF6F6F6F), Color(0xFFF5F5F5), Color(0xFF8A8A8A), Color(0xFF4A4A4A)),
            y - tw / 2,
            y + tw / 2,
        )
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
                    val yy = y + sign * amp * kotlin.math.sin(Math.PI.toFloat() * t)
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
                val cx = g.x0 + (t.fret - .5f - pos) * g.fretW
                val cy = g.rowY(t.string) + (t.y - t.downY).coerceIn(-g.spacing * .6f, g.spacing * .6f)
                val rr = g.spacing * .36f
                drawCircle(Color(0x66000000), rr * 1.08f, Offset(cx, cy + 3f))
                drawCircle(Color.White.copy(alpha = .88f), rr, Offset(cx, cy))
            }
            ZONE_BODY -> drawCircle(Color.White.copy(alpha = .25f), g.spacing * .32f, Offset(t.x, t.y), style = Stroke(2.dp.toPx()))
            else -> {}
        }
    }
}

/** Notennamen auf den Fingern, ungespiegelt. */
private fun DrawScope.drawLabels(g: Geo, board: Board, pos: Float, lefty: Boolean, measurer: TextMeasurer, density: Float) {
    if (!Settings.showNotes) return
    val notes = Settings.tuning.notes
    for (t in board.touches.values) {
        if (t.zone != ZONE_FRET) continue
        val cx0 = g.x0 + (t.fret - .5f - pos) * g.fretW
        val cx = if (lefty) g.w - cx0 else cx0
        val cy = g.rowY(t.string) + (t.y - t.downY).coerceIn(-g.spacing * .6f, g.spacing * .6f)
        val style = TextStyle(color = Color(0xFF2A2A2A), fontSize = (g.spacing * .3f / density).sp, fontWeight = FontWeight.Bold)
        val label = noteName(notes[t.string] + t.fret)
        val m = measurer.measure(label, style)
        drawText(m, topLeft = Offset(cx - m.size.width / 2f, cy - m.size.height / 2f))
    }
}

// ---------------------------------------------------------------------------------------
// Effekte
// ---------------------------------------------------------------------------------------

@Composable
private fun EffectsPanel(open: Boolean, lefty: Boolean, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .45f))
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
            )
        }
        AnimatedVisibility(
            open,
            modifier = Modifier.align(if (lefty) Alignment.CenterStart else Alignment.CenterEnd),
            enter = slideInHorizontally { if (lefty) -it else it } + fadeIn(),
            exit = slideOutHorizontally { if (lefty) -it else it } + fadeOut(),
        ) {
            val bars = WindowInsets.displayCutout.asPaddingValues()
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .clip(RoundedCornerShape(topStart = if (lefty) 0.dp else 18.dp, bottomStart = if (lefty) 0.dp else 18.dp, topEnd = if (lefty) 18.dp else 0.dp, bottomEnd = if (lefty) 18.dp else 0.dp))
                    .background(Color(0xF21C1E21))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(bars)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            ) {
                SheetHeader(stringResource(R.string.tab_effects), stringResource(R.string.done), onClose)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.sound), fontSize = 13.sp, color = Dim)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToneChip(Tone.ACOUSTIC, R.string.tone_acoustic, Modifier.weight(1f))
                    ToneChip(Tone.NYLON, R.string.tone_nylon, Modifier.weight(1f))
                    ToneChip(Tone.CLEAN, R.string.tone_clean, Modifier.weight(1f))
                    ToneChip(Tone.ROCK, R.string.tone_rock, Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                FxSlider(stringResource(R.string.fx_drive), Settings.drive) { Settings.drive = it }
                FxSlider(stringResource(R.string.fx_chorus), Settings.chorus) { Settings.chorus = it }
                FxSlider(stringResource(R.string.fx_delay), Settings.delay) { Settings.delay = it }
                FxSlider(stringResource(R.string.fx_reverb), Settings.reverb) { Settings.reverb = it }
                FxSlider(stringResource(R.string.fx_volume), Settings.volume) { Settings.volume = it }
            }
        }
    }
}

@Composable
private fun ToneChip(tone: Tone, label: Int, modifier: Modifier) {
    val active = Settings.tone == tone
    Text(
        stringResource(label),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (active) Color.Black else Color.White,
        maxLines = 1,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Green else CardHi)
            .clickable { Settings.tone = tone }
            .padding(vertical = 9.dp),
    )
}

@Composable
private fun FxSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = Color.White, modifier = Modifier.width(92.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Green,
                inactiveTrackColor = CardHi,
            ),
        )
        Text("${(value * 100).roundToInt()}", fontSize = 13.sp, color = Dim, modifier = Modifier.width(34.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}
