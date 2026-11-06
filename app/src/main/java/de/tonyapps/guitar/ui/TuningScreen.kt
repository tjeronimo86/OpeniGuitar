package de.tonyapps.guitar.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.tonyapps.guitar.R
import de.tonyapps.guitar.data.Settings
import de.tonyapps.guitar.data.TUNINGS
import de.tonyapps.guitar.data.label

/** Auswahl der Stimmung; gilt fuer Griffbrett und Stimmgeraet. */
@Composable
fun TuningScreen(onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    Box(
        Modifier.fillMaxSize().background(Bg).pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 560.dp).fillMaxSize().padding(safe).padding(horizontal = 16.dp, vertical = 10.dp)) {
            SheetHeader(stringResource(R.string.tuning), stringResource(R.string.done), onClose)
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                item {
                    TuningRow(stringResource(R.string.chromatic), stringResource(R.string.chromatic_hint), Settings.chromatic) {
                        Settings.chromatic = true
                        onClose()
                    }
                }
                items(TUNINGS, key = { it.id }) { t ->
                    TuningRow(stringResource(t.nameRes), t.label(), !Settings.chromatic && Settings.tuningId == t.id) {
                        Settings.tuningId = t.id
                        Settings.chromatic = false
                        onClose()
                    }
                }
            }
        }
    }
}

@Composable
private fun TuningRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, color = Color.White, fontWeight = FontWeight.Medium)
            Text(sub, fontSize = 14.sp, color = Dim)
        }
        if (selected) {
            Canvas(Modifier.size(22.dp)) {
                val p = Path().apply {
                    moveTo(size.width * .15f, size.height * .52f)
                    lineTo(size.width * .42f, size.height * .78f)
                    lineTo(size.width * .88f, size.height * .24f)
                }
                drawPath(p, Green, style = Stroke(size.minDimension * .12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}
