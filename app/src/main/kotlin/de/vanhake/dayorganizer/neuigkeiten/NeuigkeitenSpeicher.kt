package de.vanhake.dayorganizer.neuigkeiten

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * Entscheidet beim Start, ob die Infokarte "Neu in Version ..." erscheint, und merkt sich die
 * zuletzt gesehenen Neuigkeiten. Eigene SharedPreferences statt der Einstellungen-DataStore:
 * das ist App-Zustand, keine Nutzereinstellung, und gehoert nicht ins JSON-Backup.
 */
object NeuigkeitenSpeicher {
    private const val DATEI = "neuigkeiten"
    private const val ZULETZT_GESEHEN = "zuletzt_gesehen_id"

    data class Offen(val versionName: String, val eintraege: List<NeuigkeitenEintrag>, val neuesteId: Int)

    /** Liest Asset und Speicher; nicht auf dem Main-Thread aufrufen. */
    fun offen(kontext: Context): Offen? {
        val eintraege = runCatching {
            kontext.assets.open("changelog.json").bufferedReader().use { Neuigkeiten.parsen(it.readText()) }
        }.onFailure { Log.w("Neuigkeiten", "changelog.json nicht lesbar", it) }.getOrNull() ?: return null
        val neuesteId = Neuigkeiten.neuesteId(eintraege) ?: return null
        val prefs = kontext.getSharedPreferences(DATEI, Context.MODE_PRIVATE)
        val zuletzt = if (prefs.contains(ZULETZT_GESEHEN)) prefs.getInt(ZULETZT_GESEHEN, 0) else null
        // minSdk 33: die Variante mit PackageInfoFlags gibt es immer.
        val info = kontext.packageManager.getPackageInfo(kontext.packageName, PackageManager.PackageInfoFlags.of(0))
        val istUpdate = info.lastUpdateTime > info.firstInstallTime
        val anzeigen = Neuigkeiten.anzuzeigen(eintraege, zuletzt, istUpdate)
        if (anzeigen.isEmpty()) {
            // Neuinstallation: nichts zeigen, aber den Stand merken, damit das naechste Update zaehlt.
            if (zuletzt == null) gesehen(kontext, neuesteId)
            return null
        }
        return Offen(info.versionName ?: "", anzeigen, neuesteId)
    }

    fun gesehen(kontext: Context, id: Int) {
        kontext.getSharedPreferences(DATEI, Context.MODE_PRIVATE).edit().putInt(ZULETZT_GESEHEN, id).apply()
    }
}
