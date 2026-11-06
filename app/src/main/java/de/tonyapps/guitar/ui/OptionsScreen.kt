package de.tonyapps.guitar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tonyapps.guitar.R
import de.tonyapps.guitar.data.Settings
import de.tonyapps.guitar.data.label

/** Optionen; liegt ueber Gitarre und Stimmgeraet und passt sich der Ausrichtung an. */
@Composable
fun OptionsScreen(onClose: () -> Unit, onNews: () -> Unit, onTunings: () -> Unit) {
    BackHandler(onBack = onClose)
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(safe)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            SheetHeader(stringResource(R.string.tab_options), stringResource(R.string.done), onClose)

            SectionTitle(stringResource(R.string.sec_guitar))
            Group {
                NavRow(stringResource(R.string.tuning), stringResource(Settings.tuning.nameRes) + " · " + Settings.tuning.label(), onTunings)
                Line()
                StepRow(stringResource(R.string.frets_visible), Settings.frets.toString(), { Settings.frets = (Settings.frets - 1).coerceAtLeast(3) }) {
                    Settings.frets = (Settings.frets + 1).coerceAtMost(10)
                }
                Line()
                SliderRow(stringResource(R.string.opt_margin), Settings.margin, .02f..0.16f) { Settings.margin = it }
                Line()
                SwitchRow(stringResource(R.string.opt_portrait), Settings.portrait, stringResource(R.string.opt_portrait_hint)) { Settings.portrait = it }
                Line()
                SwitchRow(stringResource(R.string.opt_lefty), Settings.lefty) { Settings.lefty = it }
                Line()
                SwitchRow(stringResource(R.string.opt_notes), Settings.showNotes) { Settings.showNotes = it }
            }

            SectionTitle(stringResource(R.string.sec_play))
            Group {
                SwitchRow(stringResource(R.string.opt_tap), Settings.tapPlays, stringResource(R.string.opt_tap_hint)) { Settings.tapPlays = it }
                Line()
                SwitchRow(stringResource(R.string.opt_bending), Settings.bending) { Settings.bending = it }
                Line()
                SwitchRow(stringResource(R.string.opt_open_hammer), Settings.openHammer, stringResource(R.string.opt_open_hammer_hint)) { Settings.openHammer = it }
                Line()
                SwitchRow(stringResource(R.string.opt_pull_off), Settings.pullOffOpen, stringResource(R.string.opt_pull_off_hint)) { Settings.pullOffOpen = it }
                Line()
                SwitchRow(stringResource(R.string.opt_haptics), Settings.haptics) { Settings.haptics = it }
            }

            SectionTitle(stringResource(R.string.sec_tilt))
            Group {
                Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TiltChoice("off", R.string.tilt_off)
                    TiltChoice("up", R.string.tilt_up)
                    TiltChoice("down", R.string.tilt_down)
                    TiltChoice("wah", R.string.tilt_wah)
                }
                Line()
                SliderRow(stringResource(R.string.tilt_sens), Settings.tiltSens, 0f..1f) { Settings.tiltSens = it }
            }

            SectionTitle(stringResource(R.string.sec_tuner))
            Group {
                StepRow(stringResource(R.string.a4), "${Settings.a4} Hz", { Settings.a4 = (Settings.a4 - 1).coerceAtLeast(415) }) {
                    Settings.a4 = (Settings.a4 + 1).coerceAtMost(466)
                }
                Line()
                SwitchRow(stringResource(R.string.chromatic), Settings.chromatic, stringResource(R.string.chromatic_hint)) { Settings.chromatic = it }
            }

            SectionTitle(stringResource(R.string.sec_app))
            Group {
                NavRow(stringResource(R.string.whats_new), null, onNews)
                Line()
                Text(
                    stringResource(R.string.restore),
                    fontSize = 16.sp,
                    color = Red,
                    modifier = Modifier.fillMaxWidth().clickable { Settings.restoreDefaults() }.padding(horizontal = 14.dp, vertical = 13.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            VersionRow()
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.about),
                fontSize = 12.sp,
                color = Dim,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RowScope.TiltChoice(id: String, label: Int) {
    val active = Settings.tilt == id
    Text(
        stringResource(label),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = if (active) Color.Black else Color.White,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        maxLines = 1,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) Green else CardHi)
            .clickable { Settings.tilt = id }
            .padding(vertical = 9.dp),
    )
}

@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 16.sp, color = Color.White, modifier = Modifier.width(150.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Green, inactiveTrackColor = CardHi),
        )
    }
}

@Composable
private fun Line() {
    HorizontalDivider(Modifier.padding(start = 14.dp), thickness = .5.dp, color = Color.White.copy(alpha = .1f))
}

@Composable
private fun NavRow(title: String, value: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
        if (value != null) Text(value, fontSize = 14.sp, color = Dim, maxLines = 1)
        Spacer(Modifier.width(6.dp))
        ChevronGlyph(Dim, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, hint: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, color = Color.White)
            if (hint != null) Text(hint, fontSize = 12.sp, color = Dim, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(10.dp))
        AppSwitch(checked, onChange = onChange)
    }
}

@Composable
private fun StepRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, fontSize = 16.sp, color = Color.White, modifier = Modifier.weight(1f))
        StepButton("−", onMinus)
        Text(value, fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Medium, modifier = Modifier.widthIn(min = 64.dp).padding(horizontal = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        StepButton("+", onPlus)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(CardHi).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 20.sp, color = Color.White)
    }
}
