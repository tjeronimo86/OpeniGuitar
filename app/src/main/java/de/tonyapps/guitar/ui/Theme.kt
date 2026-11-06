package de.tonyapps.guitar.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.cos
import kotlin.math.sin

val Bg = Color(0xFF16191C)
val Card = Color(0xFF22262A)
val CardHi = Color(0xFF2C3136)
val Green = Color(0xFF34C77B)
val Orange = Color(0xFFF5A524)
val Red = Color(0xFFF0524F)
val Dim = Color.White.copy(alpha = .55f)
val LinkBlue = Color(0xFF0A84FF)

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Bildschirmausrichtung und Systemleisten fuer einen Bildschirm festlegen. */
@Composable
fun ScreenMode(landscape: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(landscape) {
        val activity = context.findActivity()
        activity?.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = false
            if (landscape) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose { }
    }
}

fun tick(view: View, strong: Boolean = false) {
    val type = when {
        strong && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
        strong -> HapticFeedbackConstants.LONG_PRESS
        else -> HapticFeedbackConstants.KEYBOARD_TAP
    }
    view.performHapticFeedback(type)
}

@Composable
fun AppSwitch(checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Green,
            checkedBorderColor = Green,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = CardHi,
            uncheckedBorderColor = CardHi,
        ),
    )
}

/** Kopfzeile mit Titel und "Fertig". */
@Composable
fun SheetHeader(title: String, done: String, onDone: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
        Text(
            done,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = LinkBlue,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onDone).padding(8.dp),
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        fontSize = 13.sp,
        color = Dim,
        modifier = Modifier.padding(start = 6.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
fun Group(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Card),
        content = content,
    )
}

// ---- Eigene, schlichte Symbole (keine Icon-Bibliothek noetig) ----

@Composable
fun RefreshGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val stroke = s * .1f
        val r = s * .34f
        val c = Offset(size.width / 2, size.height / 2)
        drawArc(color, -60f, 300f, false, Offset(c.x - r, c.y - r), Size(2 * r, 2 * r), style = Stroke(stroke, cap = StrokeCap.Round))
        val a = Math.toRadians(-60.0)
        val tip = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat())
        val p = Path().apply {
            moveTo(tip.x - s * .2f, tip.y - s * .04f)
            lineTo(tip.x + s * .02f, tip.y + s * .02f)
            lineTo(tip.x + s * .02f, tip.y - s * .22f)
        }
        drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@Composable
fun ChevronGlyph(color: Color, left: Boolean = false, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            if (left) {
                moveTo(w * .65f, h * .18f); lineTo(w * .3f, h * .5f); lineTo(w * .65f, h * .82f)
            } else {
                moveTo(w * .35f, h * .18f); lineTo(w * .7f, h * .5f); lineTo(w * .35f, h * .82f)
            }
        }
        drawPath(p, color, style = Stroke(size.minDimension * .12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** Plektrum. */
@Composable
fun PickGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val p = Path().apply {
            moveTo(w * .5f, h * .95f)
            cubicTo(w * .32f, h * .78f, w * .08f, h * .4f, w * .1f, h * .24f)
            cubicTo(w * .12f, h * .06f, w * .35f, h * .04f, w * .5f, h * .04f)
            cubicTo(w * .65f, h * .04f, w * .88f, h * .06f, w * .9f, h * .24f)
            cubicTo(w * .92f, h * .4f, w * .68f, h * .78f, w * .5f, h * .95f)
            close()
        }
        drawPath(p, color, style = Stroke(size.minDimension * .09f, join = StrokeJoin.Round))
    }
}

/** Stimmgabel. */
@Composable
fun ForkGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val st = Stroke(size.minDimension * .09f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val p = Path().apply {
            moveTo(w * .32f, h * .06f)
            lineTo(w * .32f, h * .5f)
            cubicTo(w * .32f, h * .66f, w * .68f, h * .66f, w * .68f, h * .5f)
            lineTo(w * .68f, h * .06f)
            moveTo(w * .5f, h * .62f)
            lineTo(w * .5f, h * .95f)
        }
        drawPath(p, color, style = st)
    }
}

/** Zahnrad. */
@Composable
fun GearGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val s = size.minDimension
        val outer = s * .46f
        val inner = s * .34f
        val p = Path()
        val teeth = 8
        for (i in 0 until teeth * 2) {
            val a0 = Math.PI * 2 * i / (teeth * 2) - Math.PI / (teeth * 2)
            val a1 = Math.PI * 2 * (i + 1) / (teeth * 2) - Math.PI / (teeth * 2)
            val r = if (i % 2 == 0) outer else inner
            val x0 = c.x + r * cos(a0).toFloat()
            val y0 = c.y + r * sin(a0).toFloat()
            val x1 = c.x + r * cos(a1).toFloat()
            val y1 = c.y + r * sin(a1).toFloat()
            if (i == 0) p.moveTo(x0, y0) else p.lineTo(x0, y0)
            p.lineTo(x1, y1)
        }
        p.close()
        drawPath(p, color, style = Stroke(s * .08f, join = StrokeJoin.Round))
        drawCircle(color, s * .13f, c, style = Stroke(s * .08f))
    }
}
