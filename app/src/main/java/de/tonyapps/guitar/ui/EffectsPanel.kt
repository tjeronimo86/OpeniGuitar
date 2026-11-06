package de.tonyapps.guitar.ui

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tonyapps.guitar.R
import de.tonyapps.guitar.audio.Tone
import de.tonyapps.guitar.data.PRESETS
import de.tonyapps.guitar.data.Settings
import kotlin.math.roundToInt

private val PedalDrive = Color(0xFFE2B91B)
private val PedalWah = Color(0xFF2FA866)
private val PedalChorus = Color(0xFF8E6CEF)
private val PedalDelay = Color(0xFF2F6FEB)
private val PedalReverb = Color(0xFF1FA3A8)

val ToneNames = mapOf(
    Tone.ACOUSTIC to R.string.tone_acoustic,
    Tone.CLEAN to R.string.tone_clean,
    Tone.ROCK to R.string.tone_rock,
    Tone.NYLON to R.string.tone_nylon,
    Tone.MUTED to R.string.tone_muted,
    Tone.BASS to R.string.tone_bass,
    Tone.UKULELE to R.string.tone_ukulele,
)

/** Instrument, Presets und Effektpedale. Querformat: von der Seite, Hochformat: von unten. */
@Composable
fun EffectsPanel(open: Boolean, portrait: Boolean, lefty: Boolean, onClose: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = .45f))
                    .pointerInput(Unit) { detectTapGestures { onClose() } },
            )
        }
        val align = when {
            portrait -> Alignment.BottomCenter
            lefty -> Alignment.CenterStart
            else -> Alignment.CenterEnd
        }
        AnimatedVisibility(
            open,
            modifier = Modifier.align(align),
            enter = if (portrait) slideInVertically { it } + fadeIn() else slideInHorizontally { if (lefty) -it else it } + fadeIn(),
            exit = if (portrait) slideOutVertically { it } + fadeOut() else slideOutHorizontally { if (lefty) -it else it } + fadeOut(),
        ) {
            val shape = when {
                portrait -> RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                lefty -> RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
                else -> RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
            }
            val size = if (portrait) Modifier.fillMaxWidth().fillMaxHeight(.72f) else Modifier.fillMaxHeight().width(380.dp)
            Column(
                size
                    .clip(shape)
                    .background(Color(0xF51C1E21))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .padding(WindowInsets.displayCutout.asPaddingValues())
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                SheetHeader(stringResource(R.string.tab_effects), stringResource(R.string.done), onClose)

                Label(stringResource(R.string.instrument))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((tone, name) in ToneNames) Chip(stringResource(name), Settings.tone == tone) { Settings.tone = tone }
                }

                Label(stringResource(R.string.presets))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (p in PRESETS) Chip(stringResource(p.nameRes), false) { Settings.applyPreset(p) }
                }

                Spacer(Modifier.height(10.dp))
                Pedal(stringResource(R.string.fx_drive), PedalDrive, Settings.driveOn, { Settings.driveOn = it }) {
                    Knob(stringResource(R.string.k_gain), Settings.drive) { Settings.drive = it }
                }
                Pedal(stringResource(R.string.fx_wah), PedalWah, Settings.wahOn || Settings.tilt == "wah", { Settings.wahOn = it }) {
                    if (Settings.tilt == "wah") {
                        Text(stringResource(R.string.wah_tilt), fontSize = 13.sp, color = Dim)
                    } else {
                        Knob(stringResource(R.string.k_pedal), Settings.wahPos) { Settings.wahPos = it }
                    }
                }
                Pedal(stringResource(R.string.fx_chorus), PedalChorus, Settings.chorusOn, { Settings.chorusOn = it }) {
                    Knob(stringResource(R.string.k_depth), Settings.chorus) { Settings.chorus = it }
                }
                Pedal(stringResource(R.string.fx_delay), PedalDelay, Settings.delayOn, { Settings.delayOn = it }) {
                    DelayControls()
                }
                Pedal(stringResource(R.string.fx_reverb), PedalReverb, Settings.reverbOn, { Settings.reverbOn = it }) {
                    Knob(stringResource(R.string.k_mix), Settings.reverb) { Settings.reverb = it }
                }
                Spacer(Modifier.height(6.dp))
                Knob(stringResource(R.string.fx_volume), Settings.volume) { Settings.volume = it }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, fontSize = 13.sp, color = Dim, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
}

@Composable
private fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (active) Color.Black else Color.White,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Green else CardHi)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Effektgeraet mit Farbe, Schalter-LED und Reglern. */
@Composable
private fun Pedal(name: String, color: Color, on: Boolean, onToggle: (Boolean) -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .border(1.dp, color.copy(alpha = if (on) .8f else .25f), RoundedCornerShape(14.dp)),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(color.copy(alpha = if (on) .9f else .35f))
                .clickable { onToggle(!on) }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name.uppercase(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (on) Color.Black else Color.White, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (on) Color(0xFFFF3B30) else Color(0xFF3A1010))
                    .border(1.dp, Color.Black.copy(alpha = .4f), CircleShape),
            )
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).alpha(if (on) 1f else .5f), content = content)
    }
}

@Composable
private fun Knob(label: String, value: Float, range: ClosedFloatingPointRange<Float> = 0f..1f, format: (Float) -> String = { "${(it * 100).roundToInt()}" }, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = Color.White, modifier = Modifier.width(88.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Green, inactiveTrackColor = CardHi),
        )
        Text(format(value), fontSize = 13.sp, color = Dim, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun DelayControls() {
    var lastTap by remember { mutableLongStateOf(0L) }
    Knob(stringResource(R.string.k_time), Settings.delayMs.toFloat(), 60f..1200f, { "${it.roundToInt()} ms" }) { Settings.delayMs = it.roundToInt() }
    Knob(stringResource(R.string.k_feedback), Settings.delayFeedback, 0f..0.9f) { Settings.delayFeedback = it }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Knob(stringResource(R.string.k_level), Settings.delay) { Settings.delay = it }
        }
        Spacer(Modifier.width(8.dp))
        // Tempo antippen: Abstand zweier Tipps wird die Echozeit
        Text(
            "TAP",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(PedalDelay)
                .clickable {
                    val now = SystemClock.elapsedRealtime()
                    val gap = now - lastTap
                    if (gap in 60..2000) Settings.delayMs = gap.toInt().coerceIn(60, 1200)
                    lastTap = now
                }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}
