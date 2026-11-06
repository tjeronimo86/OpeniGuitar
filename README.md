# OpeniGuitar

Gitarre zum Spielen auf dem Handy, nach dem Vorbild der alten iPhone-App aus 2008/2009 – mit eingebautem Stimmgerät.
Privates Hobbyprojekt, nicht kommerziell. Kotlin und Jetpack Compose, ab Android 8.

[Update-Verlauf](changelog/CHANGELOG.md) · [Downloads](../../releases/latest)

## Funktionen

- Griffbrett im Querformat, mehrere Finger gleichzeitig
- Anschlagen auf dem Korpus, Streichen über die Saiten, Hammer-on, Pull-off, Slide, Ziehen
- Hals verschiebbar bis zum 22. Bund
- Klang in Echtzeit berechnet (Karplus-Strong-Saitenmodell): Akustik, Nylon, Clean, Rock
- Effekte: Verzerrung, Chorus, Echo, Hall
- Stimmgerät über das Mikrofon (YIN-Tonhöhenerkennung), automatisch oder je Saite, chromatisch
- Neun Stimmungen, Kammerton einstellbar, Linkshänder-Modus
- Was ist neu und Update-Suche mit Download der neuen Version

## Bauen

Jeder Push auf `main` baut per GitHub Actions eine signierte APK und veröffentlicht sie als Release.
Der Update-Verlauf in `changelog/` ist gleichzeitig der Text in der App und im Release.
