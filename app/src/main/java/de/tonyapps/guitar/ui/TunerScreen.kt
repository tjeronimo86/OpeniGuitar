package de.tonyapps.guitar.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import de.tonyapps.guitar.R
import de.tonyapps.guitar.audio.GuitarEngine
import de.tonyapps.guitar.data.Settings
import de.tonyapps.guitar.data.noteName
import de.tonyapps.guitar.tuner.PitchDetector
import de.tonyapps.guitar.tuner.TunerModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun TunerScreen(onGuitar: () -> Unit, onOptions: () -> Unit, onTunings: () -> Unit) {
    ScreenMode(landscape = false)
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current
    val model = remember { TunerModel() }

    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    var granted by remember { mutableStateOf(hasMic()) }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.RECORD_AUDIO) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasMic() }

    // Mikrofon nur, solange die App im Vordergrund ist
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val detector = PitchDetector()
            withContext(Dispatchers.Default) {
                detector.run(context) { result ->
                    withContext(Dispatchers.Main) {
                        if (model.onResult(result) && Settings.haptics) tick(view, strong = true)
                    }
                }
            }
        }
    }
    // Stimmung gewechselt: Haken zuruecksetzen
    LaunchedEffect(Settings.tuningId, Settings.a4) { model.reset() }

    val bars = WindowInsets.systemBars.asPaddingValues()
    Column(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .padding(top = bars.calculateTopPadding(), bottom = bars.calculateBottomPadding()),
    ) {
        // Kopf
        Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = Color.White)) { append("Openi") }
                    withStyle(SpanStyle(color = Green)) { append("Guitar") }
                },
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.auto).uppercase(), fontSize = 13.sp, color = if (Settings.chromatic) Dim.copy(alpha = .3f) else Color.White)
            Spacer(Modifier.width(10.dp))
            AppSwitch(Settings.autoMode, enabled = !Settings.chromatic) { Settings.autoMode = it }
        }
        Column(
            Modifier
                .padding(start = 12.dp, top = 6.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onTunings)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.guitar_6), fontSize = 20.sp, color = Color.White)
                Spacer(Modifier.width(6.dp))
                ChevronGlyph(Color.White, modifier = Modifier.size(16.dp))
            }
            Text(
                if (Settings.chromatic) stringResource(R.string.chromatic) else stringResource(Settings.tuning.nameRes),
                fontSize = 15.sp,
                color = Dim,
            )
        }

        // Anzeige
        Box(Modifier.fillMaxWidth().weight(1f)) {
            Gauge(model, Modifier.fillMaxSize())
            Text("♭", fontSize = 34.sp, color = Dim, modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 10.dp))
            Text("♯", fontSize = 34.sp, color = Dim, modifier = Modifier.align(Alignment.TopEnd).padding(end = 22.dp, top = 10.dp))
            val hint = when {
                !granted -> ""
                model.freq == null -> stringResource(if (Settings.chromatic || Settings.autoMode) R.string.hint_play else R.string.hint_play_string)
                abs(model.cents) < 4f -> stringResource(R.string.hint_in_tune)
                model.cents < 0 -> stringResource(R.string.hint_up)
                else -> stringResource(R.string.hint_down)
            }
            Text(
                hint,
                fontSize = 16.sp,
                color = if (model.freq != null && abs(model.cents) < 4f) Green else Dim,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp),
            )
            if (!granted) {
                Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.mic_needed), color = Color.White, fontSize = 16.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        stringResource(R.string.mic_allow),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Green)
                            .clickable {
                                if (asked) {
                                    val intent = Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
                                    context.startActivity(intent)
                                } else {
                                    launcher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                    )
                }
            }
        }

        // Kopfplatte
        Headstock(model, Modifier.fillMaxWidth().weight(1.15f))

        BottomBar(selected = 1, onGuitar = onGuitar, onTuner = {}, onOptions = onOptions)
    }
}

/** Zeiger mit Notenblase und Verlauf, der nach unten wegscrollt. */
@Composable
private fun Gauge(model: TunerModel, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val history = model.history
    val freq = model.freq
    val cents = model.cents
    val label = model.label
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val grid = 26.dp.toPx()
        // Raster
        var x = w / 2 % grid
        while (x < w) {
            drawLine(Color.White.copy(alpha = .05f), Offset(x, 0f), Offset(x, h), 1f)
            x += grid
        }
        var y = 0f
        while (y < h) {
            drawLine(Color.White.copy(alpha = .05f), Offset(0f, y), Offset(w, y), 1f)
            y += grid
        }
        drawLine(Color.White.copy(alpha = .16f), Offset(w / 2, 0f), Offset(w / 2, h), 1.5f)

        val r = 30.dp.toPx()
        val cy = r + 18.dp.toPx()
        val half = w / 2 - r - 12.dp.toPx()
        fun xOf(c: Float) = w / 2 + (c / 50f).coerceIn(-1f, 1f) * half

        // Verlauf: neueste Werte direkt unter der Blase, aeltere weiter unten
        val step = 3.2.dp.toPx()
        val top = cy + r + 14.dp.toPx()
        val n = history.size
        for (i in 0 until n) {
            val v = history[n - 1 - i]
            if (v.isNaN()) continue
            val yy = top + i * step
            if (yy > h - 30.dp.toPx()) break
            val col = when {
                abs(v) < 4f -> Green
                abs(v) < 15f -> Orange
                else -> Red
            }
            val fade = 1f - i / 90f
            drawLine(col.copy(alpha = fade), Offset(xOf(v), yy), Offset(xOf(v), yy + step * .6f), 2.2.dp.toPx(), StrokeCap.Round)
        }

        // Blase
        val bx = if (freq == null) w / 2 else xOf(cents)
        val inTune = freq != null && abs(cents) < 4f
        val ring = when {
            freq == null -> Color.White.copy(alpha = .7f)
            inTune -> Green
            abs(cents) < 15f -> Orange
            else -> Red
        }
        val tail = Path().apply {
            moveTo(bx - r * .32f, cy + r * .9f)
            lineTo(bx, cy + r + 10.dp.toPx())
            lineTo(bx + r * .32f, cy + r * .9f)
            close()
        }
        drawPath(tail, ring)
        drawCircle(if (inTune) Green else Bg, r, Offset(bx, cy))
        drawCircle(ring, r, Offset(bx, cy), style = Stroke(3.dp.toPx()))
        if (freq != null) {
            val style = TextStyle(color = Color.White, fontSize = with(density) { 24.dp.toSp() }, fontWeight = FontWeight.SemiBold)
            val t = measurer.measure(label, style)
            drawText(t, topLeft = Offset(bx - t.size.width / 2f, cy - t.size.height / 2f))
            val c = cents.roundToInt()
            val cs = measurer.measure(
                (if (c > 0) "+" else "") + c + " ct",
                TextStyle(color = Dim, fontSize = with(density) { 12.dp.toSp() }),
            )
            val side = if (bx < w / 2) bx + r + 8.dp.toPx() else bx - r - 8.dp.toPx() - cs.size.width
            drawText(cs, topLeft = Offset(side, cy - cs.size.height / 2f))
        }
    }
}

/** Gemeinsame Masse fuer Zeichnung und Knoepfe der Kopfplatte. */
private class HeadGeo(val w: Float, val h: Float) {
    val cx = w / 2
    val hw = min(w * .40f, h * .62f)
    val top = h * .03f
    val nutY = h * .84f
    val neckW = hw * .42f
    /** Reihe 0 oben … 2 unten. */
    fun postY(row: Int) = top + h * (.25f + .17f * row)
    fun postX(left: Boolean) = cx + (if (left) -1 else 1) * hw * .25f
    fun keyX(left: Boolean) = cx + (if (left) -1 else 1) * hw * .66f
    fun labelX(left: Boolean) = if (left) w * .11f else w * .89f
    fun slotX(string: Int) = cx - neckW / 2 + neckW * (string + .5f) / 6f

    /** Saite -> (links?, Reihe). Links von oben: D A E, rechts: G B E. */
    fun place(string: Int): Pair<Boolean, Int> = when (string) {
        0 -> true to 2
        1 -> true to 1
        2 -> true to 0
        3 -> false to 0
        4 -> false to 1
        else -> false to 2
    }
}

@Composable
private fun Headstock(model: TunerModel, modifier: Modifier) {
    val view = LocalView.current
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val geo = remember(wPx, hPx) { HeadGeo(wPx, hPx) }
        val active = if (Settings.chromatic) -1 else if (Settings.autoMode) model.string else model.manual
        Canvas(Modifier.fillMaxSize()) { drawHeadstock(geo, active) }

        val notes = Settings.tuning.notes
        val size = 54.dp
        val sizePx = with(density) { size.toPx() }
        for (s in 0 until 6) {
            val (left, row) = geo.place(s)
            val tuned = s in model.tuned
            val selected = s == active
            Box(
                Modifier
                    .offset { IntOffset((geo.labelX(left) - sizePx / 2).roundToInt(), (geo.postY(row) - sizePx / 2).roundToInt()) }
                    .size(size)
                    .clip(CircleShape)
                    .background(if (tuned) Green else Card)
                    .border(2.dp, if (selected && !tuned) Green else Color.Transparent, CircleShape)
                    .clickable {
                        model.select(s)
                        Settings.chromatic = false
                        GuitarEngine.pluck(s, Settings.stringFreq(s, 0), .8f)
                        if (Settings.haptics) tick(view)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(noteName(notes[s]), fontSize = 22.sp, color = Color.White, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

private val StringWidthsDp = floatArrayOf(3.2f, 2.7f, 2.2f, 1.6f, 1.3f, 1.0f)

private fun DrawScope.drawHeadstock(g: HeadGeo, active: Int) {
    val h = g.h
    val hw = g.hw
    val cx = g.cx

    // Mechaniken (hinter der Kopfplatte)
    for (s in 0 until 6) {
        val (left, row) = g.place(s)
        val y = g.postY(row)
        val kx = g.keyX(left)
        val sign = if (left) -1f else 1f
        val shaftEnd = cx + sign * hw * .44f
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFFE6E6E6), Color(0xFF8A8A8A)), y - hw * .03f, y + hw * .03f),
            topLeft = Offset(min(kx, shaftEnd), y - hw * .03f),
            size = Size(abs(shaftEnd - kx), hw * .06f),
        )
        val kw = hw * .2f
        val kh = hw * .27f
        drawRoundRect(
            Brush.linearGradient(
                listOf(Color(0xFFF7F7F7), Color(0xFF9C9C9C), Color(0xFF3E3E3E), Color(0xFFBDBDBD)),
                Offset(kx - kw / 2, y - kh / 2),
                Offset(kx + kw / 2, y + kh / 2),
            ),
            topLeft = Offset(kx - kw / 2, y - kh / 2),
            size = Size(kw, kh),
            cornerRadius = CornerRadius(kw * .45f, kw * .45f),
        )
    }

    // Umriss der Kopfplatte
    val top = g.top
    val shoulder = top + h * .1f
    val waist = g.nutY - h * .2f
    val outline = Path().apply {
        moveTo(cx, top + h * .02f)
        cubicTo(cx + hw * .16f, top + h * .02f, cx + hw * .26f, top + h * .07f, cx + hw * .36f, top + h * .02f)
        cubicTo(cx + hw * .44f, top, cx + hw * .52f, shoulder - h * .04f, cx + hw * .5f, shoulder)
        lineTo(cx + hw * .46f, waist)
        cubicTo(cx + hw * .44f, g.nutY - h * .09f, cx + g.neckW * .8f, g.nutY - h * .06f, cx + g.neckW / 2, g.nutY - h * .02f)
        lineTo(cx + g.neckW / 2, g.nutY)
        lineTo(cx - g.neckW / 2, g.nutY)
        lineTo(cx - g.neckW / 2, g.nutY - h * .02f)
        cubicTo(cx - g.neckW * .8f, g.nutY - h * .06f, cx - hw * .44f, g.nutY - h * .09f, cx - hw * .46f, waist)
        lineTo(cx - hw * .5f, shoulder)
        cubicTo(cx - hw * .52f, shoulder - h * .04f, cx - hw * .44f, top, cx - hw * .36f, top + h * .02f)
        cubicTo(cx - hw * .26f, top + h * .07f, cx - hw * .16f, top + h * .02f, cx, top + h * .02f)
        close()
    }
    drawPath(outline, Brush.verticalGradient(listOf(Color(0xFF8A4A26), Color(0xFF6B341A), Color(0xFF5A2A15)), top, g.nutY))
    clipPath(outline) {
        // Maserung
        var i = 0
        var x = cx - hw * .5f
        while (x < cx + hw * .5f) {
            val a = if (i % 3 == 0) .10f else .05f
            drawLine(Color.Black.copy(alpha = a), Offset(x, top), Offset(x + hw * .02f, g.nutY), 1.2f)
            x += hw * (.025f + (i * 37 % 11) / 400f)
            i++
        }
        // Abdeckung des Halsstabs
        val tw = g.neckW * .55f
        val cover = Path().apply {
            moveTo(cx, g.nutY - h * .26f)
            cubicTo(cx + tw * .6f, g.nutY - h * .26f, cx + tw * .5f, g.nutY - h * .1f, cx + tw / 2, g.nutY)
            lineTo(cx - tw / 2, g.nutY)
            cubicTo(cx - tw * .5f, g.nutY - h * .1f, cx - tw * .6f, g.nutY - h * .26f, cx, g.nutY - h * .26f)
            close()
        }
        drawPath(cover, Color(0xFF1B0F0B))
        drawCircle(Color(0xFF6E6E6E), 2.dp.toPx(), Offset(cx, g.nutY - h * .23f))
        drawCircle(Color(0xFF6E6E6E), 2.dp.toPx(), Offset(cx, g.nutY - h * .04f))
    }
    drawPath(outline, Color(0xFFF4EBDD), style = Stroke(2.6.dp.toPx()))

    // Hals und Sattel
    drawRect(Color(0xFF3A2216), Offset(cx - g.neckW / 2, g.nutY), Size(g.neckW, h - g.nutY))
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFFFFF8EA), Color(0xFFD8CBB4)), g.nutY, g.nutY + h * .02f),
        Offset(cx - g.neckW / 2 - 1.dp.toPx(), g.nutY),
        Size(g.neckW + 2.dp.toPx(), h * .02f),
    )

    // Saiten
    for (s in 0 until 6) {
        val (left, row) = g.place(s)
        val px = g.postX(left)
        val py = g.postY(row)
        val sx = g.slotX(s)
        val col = if (s == active) Color.White else Color(0xFFC9C9C9)
        val sw = StringWidthsDp[s].dp.toPx()
        drawLine(col, Offset(px, py), Offset(sx, g.nutY), sw, StrokeCap.Round)
        drawLine(col, Offset(sx, g.nutY), Offset(sx, h), sw)
    }

    // Wirbel
    for (s in 0 until 6) {
        val (left, row) = g.place(s)
        val c = Offset(g.postX(left), g.postY(row))
        val r = hw * .058f
        drawCircle(Color(0xFF2A2A2A), r * 1.15f, c)
        drawCircle(Brush.radialGradient(listOf(Color.White, Color(0xFFB8B8B8), Color(0xFF6A6A6A)), c + Offset(-r * .3f, -r * .3f), r * 1.4f), r, c)
    }
}

/** Untere Leiste: Gitarre, Stimmen, Optionen. */
@Composable
fun BottomBar(selected: Int, onGuitar: () -> Unit, onTuner: () -> Unit, onOptions: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF1C2024))
            .padding(vertical = 8.dp),
    ) {
        BarItem(stringResource(R.string.tab_guitar), selected == 0, onGuitar) { c, m -> PickGlyph(c, m) }
        BarItem(stringResource(R.string.tab_tuner), selected == 1, onTuner) { c, m -> ForkGlyph(c, m) }
        BarItem(stringResource(R.string.tab_options), selected == 2, onOptions) { c, m -> GearGlyph(c, m) }
    }
}

@Composable
private fun RowScope.BarItem(label: String, active: Boolean, onClick: () -> Unit, icon: @Composable (Color, Modifier) -> Unit) {
    val col = if (active) Green else Color.White.copy(alpha = .75f)
    Column(
        Modifier
            .weight(1f)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(col, Modifier.size(26.dp))
        Spacer(Modifier.height(4.dp))
        Text(label, fontSize = 13.sp, color = col)
    }
}
