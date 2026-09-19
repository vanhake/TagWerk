package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
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
import kotlin.test.assertTrue

class WaechterAblaufTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 3, 0).atZone(zone).toInstant(),
        zone,
    )

    private val tabletten = Aktivitaet(
        id = 1,
        name = "Tabletten",
        farbe = 1,
        basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private val gestern = Faelligkeit(
        id = 9,
        aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 1),
        periodeBis = LocalDate.of(2026, 3, 1),
        geplantFuer = LocalDateTime.of(2026, 3, 1, 8, 0),
        sollAnzahl = 1,
    )

    @Test
    fun `der Waechter schliesst Verpasstes und fuellt den Vorausblick auf`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(gestern))
        val aktivitaeten = FakeAktivitaetRepository(listOf(tabletten))
        val erinnerungen = FakeErinnerungRepository()
        var alarmNeuGeplant = false

        val ablauf = WaechterAblauf(
            verpassteAbschliessen = VerpassteAbschliessen(faelligkeiten, uhr),
            materialisieren = FaelligkeitenMaterialisieren(
                aktivitaeten, faelligkeiten, erinnerungen, ZeitplanRechner(), uhr,
            ),
            alarmNeuPlanen = { alarmNeuGeplant = true },
        )

        ablauf()

        assertEquals(
            FaelligkeitStatus.VERPASST,
            faelligkeiten.stand.value.first { it.id == 9L }.status,
        )
        // FaelligkeitenMaterialisieren() laeuft mit vorausTage=7, das erzeugt fuer den
        // Zeitraum [heute, heute+7] (inklusive) 8 neue Faelligkeiten fuer "Tabletten"
        // (siehe FaelligkeitenMaterialisierenTest: vorausTage=6 -> 7 Tage). Zusammen mit
        // der bereits vorhandenen "gestern"-Faelligkeit (jetzt VERPASST) ergibt das 9.
        assertEquals(9, faelligkeiten.stand.value.size)
        assertTrue(alarmNeuGeplant)
    }

    @Test
    fun `eine heute frueh angelegte Faelligkeit wird nicht sofort verpasst`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository()
        val aktivitaeten = FakeAktivitaetRepository(listOf(tabletten))
        val ablauf = WaechterAblauf(
            verpassteAbschliessen = VerpassteAbschliessen(faelligkeiten, uhr),
            materialisieren = FaelligkeitenMaterialisieren(
                aktivitaeten, faelligkeiten, FakeErinnerungRepository(), ZeitplanRechner(), uhr,
            ),
            alarmNeuPlanen = {},
        )

        ablauf()

        assertTrue(faelligkeiten.stand.value.all { it.status == FaelligkeitStatus.OFFEN })
    }
}
