package de.tonyapps.guitar.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import de.tonyapps.guitar.data.Changelog
import de.tonyapps.guitar.data.Settings
import de.tonyapps.guitar.update.Updater

@Composable
fun GuitarApp() {
    val context = LocalContext.current
    MaterialTheme(colorScheme = darkColorScheme(primary = Green, background = Bg, surface = Bg, onSurface = Color.White)) {
        var options by remember { mutableStateOf(false) }
        var tunings by remember { mutableStateOf(false) }
        var news by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            if (Changelog.isNew(context)) news = true
            Updater.autoCheck(context)
        }
        CompositionLocalProvider(LocalContentColor provides Color.White) {
        Box(Modifier.fillMaxSize()) {
            if (Settings.screen == "tuner") {
                TunerScreen(
                    onGuitar = { Settings.screen = "guitar" },
                    onOptions = { options = true },
                    onTunings = { tunings = true },
                )
            } else {
                GuitarScreen(
                    onTuner = { Settings.screen = "tuner" },
                    onOptions = { options = true },
                )
            }
            if (options) OptionsScreen(onClose = { options = false }, onNews = { news = true }, onTunings = { tunings = true })
            if (tunings) TuningScreen { tunings = false }
            if (news) WhatsNewScreen { news = false }
            UpdateSheet()
        }
        }
    }
}
