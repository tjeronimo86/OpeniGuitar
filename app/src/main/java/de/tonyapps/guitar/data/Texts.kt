package de.tonyapps.guitar.data

import android.content.Context

/** Texte fuer Stellen ohne Compose-Kontext; folgt der Sprache des Geraets. */
object AppContext {
    lateinit var app: Context
}

fun tr(id: Int, vararg args: Any): String = AppContext.app.getString(id, *args)
