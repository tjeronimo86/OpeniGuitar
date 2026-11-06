package de.tonyapps.guitar

import android.app.Application
import de.tonyapps.guitar.data.AppContext
import de.tonyapps.guitar.data.Settings

class GuitarApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContext.app = this
        Settings.init(this)
    }
}
