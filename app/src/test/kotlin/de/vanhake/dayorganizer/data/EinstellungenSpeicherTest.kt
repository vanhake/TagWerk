package de.vanhake.dayorganizer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Ein DataStore, dessen [data]-Strom beim Sammeln sofort den uebergebenen Fehler wirft --
 * simuliert, wie DataStore.data bei einer beschaedigten Preferences-Datei eine IOException
 * wirft (oder, fuer den Gegentest, wie ein Programmierfehler jede andere Exception werfen
 * koennte). updateData() wird in diesen Tests nicht aufgerufen.
 */
private class WerfenderDatenSpeicher(private val fehler: Throwable) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw fehler }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        throw UnsupportedOperationException("in diesem Test nicht benoetigt")
}

class EinstellungenSpeicherTest {

    @Test
    fun `eine beschaedigte Datei liefert die Vorgabewerte statt die App abstuerzen zu lassen`() = runTest {
        val speicher = EinstellungenSpeicher(WerfenderDatenSpeicher(IOException("Preferences-Datei kaputt")))

        assertEquals(Einstellungen(tagesbonusPunkte = 15, standardKulanzMinuten = 60), speicher.lesen())
        assertNull(speicher.letzterWaechterErfolg())
    }

    @Test
    fun `ein Programmierfehler wird weitergereicht statt verschluckt zu werden`() = runTest {
        val speicher = EinstellungenSpeicher(WerfenderDatenSpeicher(IllegalStateException("Programmierfehler")))

        assertFailsWith<IllegalStateException> { speicher.lesen() }
        assertFailsWith<IllegalStateException> { speicher.letzterWaechterErfolg() }
    }

    @Test
    fun `lesenOderWerfen reicht einen Lesefehler weiter`() = runTest {
        val speicher = EinstellungenSpeicher(WerfenderDatenSpeicher(IOException("Preferences-Datei kaputt")))

        assertFailsWith<IOException> { speicher.lesenOderWerfen() }
    }

    // Gegen einen echten DataStore mit einer echten kaputten Datei, nicht gegen einen
    // Fake: es geht darum, ob die Datei danach repariert ist. Auf dem Emulator blieb
    // sie ohne Korruptionsbehandlung nach "Speichern" unveraendert kaputt -- das
    // Schreiben scheiterte still, weil edit() intern zuerst liest.
    @Test
    fun `nach einer beschaedigten Datei laesst sich wieder speichern`() = runTest {
        val verzeichnis = Files.createTempDirectory("einstellungen").toFile()
        val datei = File(verzeichnis, "einstellungen.preferences_pb")
        datei.writeBytes(byteArrayOf(-1, -1, -1, 'k'.code.toByte(), 'a'.code.toByte(), -1))
        val datenStore = PreferenceDataStoreFactory.create(
            corruptionHandler = EINSTELLUNGEN_BEI_KORRUPTION,
            scope = backgroundScope,
            produceFile = { datei },
        )
        val speicher = EinstellungenSpeicher(datenStore)

        speicher.schreiben(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        speicher.waechterErfolgMerken(LocalDateTime.of(2026, 3, 2, 3, 0))

        assertEquals(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45), speicher.lesen())
        assertEquals(LocalDateTime.of(2026, 3, 2, 3, 0), speicher.letzterWaechterErfolg())
        verzeichnis.deleteRecursively()
    }

    private fun neuerSpeicher(): EinstellungenSpeicher {
        val verzeichnis = Files.createTempDirectory("einstellungen-theme").toFile()
        val datei = File(verzeichnis, "einstellungen.preferences_pb")
        val datenStore = PreferenceDataStoreFactory.create(
            corruptionHandler = EINSTELLUNGEN_BEI_KORRUPTION,
            produceFile = { datei },
        )
        return EinstellungenSpeicher(datenStore)
    }

    @Test
    fun `ohne gespeicherten Modus gilt hell als Standard`() = runTest {
        val speicher = neuerSpeicher()

        assertEquals(ThemeModus.HELL, speicher.lesen().themeModus)
    }

    @Test
    fun `der Farbmodus wird gespeichert und wieder gelesen`() = runTest {
        val speicher = neuerSpeicher()

        speicher.schreiben(Einstellungen(themeModus = ThemeModus.DUNKEL))

        assertEquals(ThemeModus.DUNKEL, speicher.lesen().themeModus)
    }
}
