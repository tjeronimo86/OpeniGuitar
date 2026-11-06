package de.tonyapps.guitar

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import de.tonyapps.guitar.audio.GuitarEngine
import de.tonyapps.guitar.ui.GuitarApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Beim Spielen und Stimmen soll der Bildschirm nicht ausgehen
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { GuitarApp() }
    }

    override fun onResume() {
        super.onResume()
        GuitarEngine.start()
    }

    override fun onPause() {
        GuitarEngine.stop()
        super.onPause()
    }
}
