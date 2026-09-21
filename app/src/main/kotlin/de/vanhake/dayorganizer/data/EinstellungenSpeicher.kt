package de.vanhake.dayorganizer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

data class Einstellungen(
    val tagesbonusPunkte: Int = 15,
    val standardKulanzMinuten: Int = 60,
)

/**
 * Ersetzt eine beschaedigte Einstellungsdatei beim ersten Zugriff durch eine leere.
 *
 * Das Abfangen der IOException beim Lesen (gesicherterDatenstrom unten) allein
 * reicht nicht: es verhindert den Absturz, repariert aber die Datei nicht. edit()
 * liest intern zuerst und scheitert dann an derselben Stelle -- auf dem Emulator
 * blieb die Datei nach "Speichern" unveraendert kaputt. Der Nutzer koennte seine
 * Einstellungen nie wieder aendern, ohne es zu merken, und der Waechter koennte
 * seinen Erfolg nie mehr festhalten: nach 36 Stunden stuende dauerhaft der Banner
 * "Selbstheilung reagiert nicht", obwohl er einwandfrei laeuft. Mit diesem
 * Handler kostet eine kaputte Datei einmalig die Einstellungen, danach
 * funktionieren Lesen und Schreiben wieder.
 */
internal val EINSTELLUNGEN_BEI_KORRUPTION =
    ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }

// Property-Delegat fuer preferencesDataStore darf pro Datei nur einmal existieren, sonst wirft
// DataStore zur Laufzeit ("There are multiple DataStores active for the same file"). Deshalb
// bleibt dieser Delegat die einzige Stelle, die ihn fuer "einstellungen" erzeugt -- der
// @Inject-Konstruktor unten greift lediglich darauf zu.
private val Context.datenSpeicher by preferencesDataStore(
    name = "einstellungen",
    corruptionHandler = EINSTELLUNGEN_BEI_KORRUPTION,
)

@Singleton
class EinstellungenSpeicher(
    private val datenStore: DataStore<Preferences>,
) {
    @Inject constructor(@ApplicationContext kontext: Context) : this(kontext.datenSpeicher)

    private val tagesbonus = intPreferencesKey("tagesbonus_punkte")
    private val kulanz = intPreferencesKey("standard_kulanz_minuten")

    // Betriebszustand des Waechters, keine Nutzereinstellung -- deshalb ein eigener Key statt
    // eines Feldes auf Einstellungen, das eines Tages von einem JSON-Backup serialisiert wird.
    private val letzterWaechterErfolg = longPreferencesKey("letzter_waechter_erfolg_epoch")

    // Alle Lesezugriffe laufen ueber diesen einen Datenstrom -- das dokumentierte DataStore-
    // Muster faengt eine IOException (beschaedigte Datei) ab und liefert stattdessen leere
    // Einstellungen, das entspricht den Vorgabewerten unten bzw. letzterWaechterErfolg() == null.
    // Andere Ausnahmen sind Programmierfehler und werden weitergeworfen, nicht verschluckt.
    private val gesicherterDatenstrom: Flow<Preferences> = datenStore.data.catch {
        if (it is IOException) emit(emptyPreferences()) else throw it
    }

    fun beobachten(): Flow<Einstellungen> = gesicherterDatenstrom.map {
        Einstellungen(
            tagesbonusPunkte = it[tagesbonus] ?: 15,
            standardKulanzMinuten = it[kulanz] ?: 60,
        )
    }

    suspend fun lesen(): Einstellungen = beobachten().first()

    suspend fun schreiben(einstellungen: Einstellungen) {
        datenStore.edit {
            it[tagesbonus] = einstellungen.tagesbonusPunkte
            it[kulanz] = einstellungen.standardKulanzMinuten
        }
    }

    /** Vom Waechter nach einem erfolgreichen Lauf aufgerufen -- Grundlage fuer WAECHTER_HAENGT. */
    suspend fun waechterErfolgMerken(zeitpunkt: LocalDateTime) {
        datenStore.edit {
            it[letzterWaechterErfolg] = zeitpunkt.toEpochSecond(ZoneOffset.UTC)
        }
    }

    suspend fun letzterWaechterErfolg(): LocalDateTime? =
        gesicherterDatenstrom.map { it[letzterWaechterErfolg] }.first()
            ?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }
}
