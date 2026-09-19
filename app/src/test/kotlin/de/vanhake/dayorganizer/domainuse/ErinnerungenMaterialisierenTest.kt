package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ErinnerungenMaterialisierenTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 6, 0).atZone(zone).toInstant(),
        zone,
    )

    private fun aktivitaet(id: Long, zeitplan: Zeitplan) = Aktivitaet(
        id = id,
        name = "Test $id",
        farbe = 1,
        basisPunkte = 10,
        zeitplan = zeitplan,
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `feste Zeiten erzeugen je Faelligkeit genau einen Erinnerungstermin`() = runTest {
        val erinnerungen = FakeErinnerungRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(
                listOf(
                    aktivitaet(
                        1,
                        Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
                    ),
                ),
            ),
            faelligkeiten = FakeFaelligkeitRepository(),
            erinnerungen = erinnerungen,
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 2)

        assertEquals(3, erinnerungen.stand.value.size)
        assertEquals(
            LocalDateTime.of(2026, 3, 2, 8, 0),
            erinnerungen.stand.value.first().zeitpunkt,
        )
        assertEquals(0, erinnerungen.stand.value.first().versuch)
    }

    @Test
    fun `ein Wochenziel ohne Anstoss erzeugt keinen Erinnerungstermin`() = runTest {
        val erinnerungen = FakeErinnerungRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(listOf(aktivitaet(1, Zeitplan.Wochenziel(3)))),
            faelligkeiten = FakeFaelligkeitRepository(),
            erinnerungen = erinnerungen,
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 6)

        assertEquals(0, erinnerungen.stand.value.size)
    }

    @Test
    fun `ein Wochenziel mit Anstoss erinnert an den gewaehlten Tagen`() = runTest {
        val erinnerungen = FakeErinnerungRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(
                listOf(
                    aktivitaet(
                        1,
                        Zeitplan.Wochenziel(
                            anzahl = 3,
                            anstoss = listOf(Anstoss(DayOfWeek.TUESDAY, LocalTime.of(18, 0))),
                        ),
                    ),
                ),
            ),
            faelligkeiten = FakeFaelligkeitRepository(),
            erinnerungen = erinnerungen,
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 6)

        assertEquals(
            listOf(LocalDateTime.of(2026, 3, 3, 18, 0)),
            erinnerungen.stand.value.map { it.zeitpunkt },
        )
    }

    @Test
    fun `ein zweiter Lauf legt keine doppelten Erinnerungen an`() = runTest {
        val erinnerungen = FakeErinnerungRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(
                listOf(
                    aktivitaet(
                        1,
                        Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
                    ),
                ),
            ),
            faelligkeiten = FakeFaelligkeitRepository(),
            erinnerungen = erinnerungen,
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 2)
        use(vorausTage = 2)

        assertEquals(3, erinnerungen.stand.value.size)
    }
}
