package de.vanhake.dayorganizer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import java.io.IOException
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
}
