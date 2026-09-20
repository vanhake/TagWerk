package de.vanhake.dayorganizer.ui.belohnungen

import app.cash.turbine.test
import de.vanhake.dayorganizer.FakeBelohnungRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.Hindernis
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domainuse.BelohnungEinloesen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungenViewModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 19, 0).atZone(zone).toInstant(),
        zone,
    )
    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private fun viewModel(
        belohnungen: FakeBelohnungRepository,
        buchungen: FakeBuchungRepository,
    ): BelohnungenViewModel {
        val faelligkeiten = FakeFaelligkeitRepository()
        return BelohnungenViewModel(
            belohnungen = belohnungen,
            buchungen = buchungen,
            einloesenUseCase = BelohnungEinloesen(
                belohnungen, buchungen, faelligkeiten,
                BelohnungsPruefer(), SerienRechner(), FakeTransaktionen(), uhr,
            ),
        )
    }

    private fun guthaben(punkte: Int) = FakeBuchungRepository(
        listOf(
            Buchung(
                art = BuchungsArt.ERLEDIGUNG, betrag = punkte,
                zeitpunkt = LocalDateTime.of(2026, 3, 1, 8, 0),
            ),
        ),
    )

    @Test
    fun `Kacheln zeigen fehlende Punkte`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200))),
            guthaben(160),
        )

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            assertEquals(160, zustand.kontostand)
            assertEquals(40, zustand.kacheln.single().einloesbarkeit.fehlendePunkte)
            assertEquals(
                Hindernis.ZU_WENIG_PUNKTE,
                zustand.kacheln.single().einloesbarkeit.hindernis,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Einloesen senkt den Kontostand und erscheint in der Historie`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(250)
        val vm = viewModel(belohnungen, buchungen)

        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(50, buchungen.kontostand())
        assertEquals(listOf("Kinoabend"), belohnungen.einloesungen.value.map { it.name })
    }

    // Guthaben reicht fuer zwei Einloesungen (450 bei Preis 200) -- ein Test mit knappem
    // Guthaben bliebe gruen, auch wenn einloesen() den Use Case zweimal aufriefe, weil der
    // zweite Aufruf dann ohnehin am Kontostand scheitern wuerde. Erst mit ausreichend
    // Guthaben wird dieser Test zum Waechter fuer die Doppeltipp-Sperre.
    //
    // Geprueft wird das Ergebnis am Repository, nicht an einer Ausgabe des Zustandsflusses:
    // einloesen() laeuft im viewModelScope auf [dispatcher] und steht nach
    // advanceUntilIdle() fest, waehrend der Zustandsfluss seit flowOn(Dispatchers.Default)
    // von einem echten Thread kommt. Eine Zusicherung auf die Ausgaben bestuende dort
    // zeitabhaengig -- per Mutationstest belegt: sie fing die entfernte Sperre nicht mehr.
    @Test
    fun `ein Doppeltipp loest nur einmal ein`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(450)
        val vm = viewModel(belohnungen, buchungen)

        vm.einloesen(1)
        vm.einloesen(1)
        dispatcher.scheduler.advanceUntilIdle()

        // 450 minus einmal 200. Ohne die Sperre waeren es zwei Abbuchungen und 50.
        assertEquals(250, buchungen.kontostand())
        assertEquals(listOf("Kinoabend"), belohnungen.einloesungen.value.map { it.name })
    }
}
