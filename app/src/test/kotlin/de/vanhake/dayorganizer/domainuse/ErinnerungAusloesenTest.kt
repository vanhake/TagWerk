package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Erinnerung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ErinnerungAusloesenTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private class FakeAnzeige : Erinnerungsanzeige {
        val gezeigt = mutableListOf<Long>()
        val versteckt = mutableListOf<Long>()
        override fun zeige(faelligkeit: Faelligkeit, aktivitaet: Aktivitaet) {
            gezeigt += faelligkeit.id
        }
        override fun verstecke(faelligkeitId: Long) {
            versteckt += faelligkeitId
        }
    }

    private val tabletten = Aktivitaet(
        id = 1,
        name = "Tabletten",
        farbe = 1,
        basisPunkte = 10,
        nachfassAnzahl = 2,
        nachfassAbstandMinuten = 15,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private val morgens = Faelligkeit(
        id = 10,
        aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 2),
        periodeBis = LocalDate.of(2026, 3, 2),
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0),
        sollAnzahl = 1,
    )

    private fun uhrUm(stunde: Int, minute: Int) = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, stunde, minute).atZone(zone).toInstant(),
        zone,
    )

    @Test
    fun `eine faellige Erinnerung wird gezeigt und das Nachfassen geplant`() = runTest {
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 1, faelligkeitId = 10, zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 0))),
        )
        val anzeige = FakeAnzeige()
        val use = ErinnerungAusloesen(
            erinnerungen = erinnerungen,
            faelligkeiten = FakeFaelligkeitRepository(listOf(morgens)),
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            anzeige = anzeige,
            uhr = uhrUm(8, 0),
        )

        use()

        assertEquals(listOf(10L), anzeige.gezeigt)
        assertEquals(true, erinnerungen.stand.value.first { it.id == 1L }.ausgeloest)
        val nachfass = erinnerungen.stand.value.single { it.versuch == 1 }
        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 15), nachfass.zeitpunkt)
    }

    @Test
    fun `nach dem letzten Nachfassversuch wird nicht weiter geplant`() = runTest {
        val erinnerungen = FakeErinnerungRepository(
            listOf(
                Erinnerung(
                    id = 1, faelligkeitId = 10,
                    zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 30), versuch = 2,
                ),
            ),
        )
        val use = ErinnerungAusloesen(
            erinnerungen = erinnerungen,
            faelligkeiten = FakeFaelligkeitRepository(listOf(morgens)),
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            anzeige = FakeAnzeige(),
            uhr = uhrUm(8, 30),
        )

        use()

        assertEquals(1, erinnerungen.stand.value.size)
    }

    @Test
    fun `eine bereits erledigte Faelligkeit erzeugt keine Benachrichtigung`() = runTest {
        val erledigt = morgens.copy(status = FaelligkeitStatus.ERLEDIGT, istAnzahl = 1)
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 1, faelligkeitId = 10, zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 0))),
        )
        val anzeige = FakeAnzeige()
        val use = ErinnerungAusloesen(
            erinnerungen = erinnerungen,
            faelligkeiten = FakeFaelligkeitRepository(listOf(erledigt)),
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            anzeige = anzeige,
            uhr = uhrUm(8, 0),
        )

        use()

        assertEquals(emptyList(), anzeige.gezeigt)
        assertEquals(true, erinnerungen.stand.value.single().ausgeloest)
    }

    @Test
    fun `noch nicht faellige Erinnerungen bleiben unberuehrt`() = runTest {
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 1, faelligkeitId = 10, zeitpunkt = LocalDateTime.of(2026, 3, 2, 20, 0))),
        )
        val anzeige = FakeAnzeige()
        val use = ErinnerungAusloesen(
            erinnerungen = erinnerungen,
            faelligkeiten = FakeFaelligkeitRepository(listOf(morgens)),
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            anzeige = anzeige,
            uhr = uhrUm(8, 0),
        )

        use()

        assertEquals(emptyList(), anzeige.gezeigt)
        assertEquals(false, erinnerungen.stand.value.single().ausgeloest)
    }

    @Test
    fun `Spaeter verschiebt um den Nachfassabstand`() = runTest {
        val erinnerungen = FakeErinnerungRepository()
        val use = ErinnerungSpaeter(
            erinnerungen = erinnerungen,
            faelligkeiten = FakeFaelligkeitRepository(listOf(morgens)),
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            uhr = uhrUm(8, 5),
        )

        use(10)

        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 20), erinnerungen.stand.value.single().zeitpunkt)
    }
}
