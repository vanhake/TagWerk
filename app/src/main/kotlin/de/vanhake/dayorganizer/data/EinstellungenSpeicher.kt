package de.vanhake.dayorganizer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

data class Einstellungen(
    val tagesbonusPunkte: Int = 15,
    val standardKulanzMinuten: Int = 60,
)

private val Context.datenSpeicher by preferencesDataStore(name = "einstellungen")

@Singleton
class EinstellungenSpeicher @Inject constructor(
    @ApplicationContext private val kontext: Context,
) {
    private val tagesbonus = intPreferencesKey("tagesbonus_punkte")
    private val kulanz = intPreferencesKey("standard_kulanz_minuten")

    // Betriebszustand des Waechters, keine Nutzereinstellung -- deshalb ein eigener Key statt
    // eines Feldes auf Einstellungen, das eines Tages von einem JSON-Backup serialisiert wird.
    private val letzterWaechterErfolg = longPreferencesKey("letzter_waechter_erfolg_epoch")

    fun beobachten(): Flow<Einstellungen> = kontext.datenSpeicher.data.map {
        Einstellungen(
            tagesbonusPunkte = it[tagesbonus] ?: 15,
            standardKulanzMinuten = it[kulanz] ?: 60,
        )
    }

    suspend fun lesen(): Einstellungen = beobachten().first()

    suspend fun schreiben(einstellungen: Einstellungen) {
        kontext.datenSpeicher.edit {
            it[tagesbonus] = einstellungen.tagesbonusPunkte
            it[kulanz] = einstellungen.standardKulanzMinuten
        }
    }

    /** Vom Waechter nach einem erfolgreichen Lauf aufgerufen -- Grundlage fuer WAECHTER_HAENGT. */
    suspend fun waechterErfolgMerken(zeitpunkt: LocalDateTime) {
        kontext.datenSpeicher.edit {
            it[letzterWaechterErfolg] = zeitpunkt.toEpochSecond(ZoneOffset.UTC)
        }
    }

    suspend fun letzterWaechterErfolg(): LocalDateTime? =
        kontext.datenSpeicher.data.map { it[letzterWaechterErfolg] }.first()
            ?.let { LocalDateTime.ofEpochSecond(it, 0, ZoneOffset.UTC) }
}
