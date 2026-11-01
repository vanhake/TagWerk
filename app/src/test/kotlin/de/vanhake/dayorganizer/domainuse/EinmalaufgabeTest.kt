package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Erinnerung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.PunkteRechner
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Zusammenspiel der Use Cases fuer eine Einmalaufgabe (Issue #14). */
class EinmalaufgabeTest {

    private val zone = ZoneId.of("Europe/Berlin")

    // Montag, 2026-03-02.
    private fun uhrAm(tag: LocalDate, stunde: Int = 6) = Clock.fixed(
        tag.atTime(stunde, 0).atZone(zone).toInstant(),
        zone,
    )

    private val heute = LocalDate.of(2026, 3, 2)

    // Ein Freitag.
    private val deadline = LocalDate.of(2026, 3, 13)

    private val steuer = Aktivitaet(
        id = 1,
        name = "Steuererklaerung",
        farbe = 1,
        basisPunkte = 20,
        zeitplan = Zeitplan.Einmalig(deadline, LocalTime.of(9, 0)),
        erstelltAm = heute,
    )

    private val offen = Faelligkeit(
        id = 10,
        aktivitaetId = 1,
        periodeVon = heute,
        periodeBis = deadline,
        geplantFuer = null,
        sollAnzahl = 1,
    )

    private fun materialisieren(
        aktivitaeten: FakeAktivitaetRepository,
        faelligkeiten: FakeFaelligkeitRepository,
        erinnerungen: FakeErinnerungRepository,
        uhr: Clock,
    ) = FaelligkeitenMaterialisieren(aktivitaeten, faelligkeiten, erinnerungen, ZeitplanRechner(), uhr)

    private fun erledigen(
        faelligkeiten: FakeFaelligkeitRepository,
        buchungen: FakeBuchungRepository,
        uhr: Clock,
    ) = FaelligkeitErledigen(
        aktivitaeten = FakeAktivitaetRepository(listOf(steuer)),
        faelligkeiten = faelligkeiten,
        buchungen = buchungen,
        punkteRechner = PunkteRechner(),
        serienRechner = SerienRechner(),
        transaktionen = FakeTransaktionen(),
        einstellungenLesen = { Einstellungen(tagesbonusPunkte = 10) },
        uhr = uhr,
    )

    @Test
    fun `Materialisieren legt eine Faelligkeit und ihre Erinnerungen an`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository()
        val erinnerungen = FakeErinnerungRepository()

        materialisieren(FakeAktivitaetRepository(listOf(steuer)), faelligkeiten, erinnerungen, uhrAm(heute))()

        val f = faelligkeiten.stand.value.single()
        assertEquals(heute, f.periodeVon)
        assertEquals(deadline, f.periodeBis)
        assertEquals(
            listOf(2, 6, 9, 13).map { LocalDateTime.of(2026, 3, it, 9, 0) },
            erinnerungen.stand.value.map { it.zeitpunkt },
        )
    }

    @Test
    fun `ein schon vergangener Erinnerungstermin wird nicht angelegt`() = runTest {
        val erinnerungen = FakeErinnerungRepository()

        materialisieren(
            FakeAktivitaetRepository(listOf(steuer)),
            FakeFaelligkeitRepository(),
            erinnerungen,
            uhrAm(heute, stunde = 10),
        )()

        assertEquals(
            listOf(6, 9, 13).map { LocalDateTime.of(2026, 3, it, 9, 0) },
            erinnerungen.stand.value.map { it.zeitpunkt },
        )
    }

    @Test
    fun `wiederholtes Materialisieren an spaeteren Tagen legt nichts doppelt an`() = runTest {
        val aktivitaeten = FakeAktivitaetRepository(listOf(steuer))
        val faelligkeiten = FakeFaelligkeitRepository()
        val erinnerungen = FakeErinnerungRepository()

        materialisieren(aktivitaeten, faelligkeiten, erinnerungen, uhrAm(heute))()
        materialisieren(aktivitaeten, faelligkeiten, erinnerungen, uhrAm(heute.plusDays(3)))()

        assertEquals(1, faelligkeiten.stand.value.size)
        assertEquals(4, erinnerungen.stand.value.size)
    }

    @Test
    fun `verschobene Deadline passt Faelligkeit und Erinnerungen an`() = runTest {
        val aktivitaeten = FakeAktivitaetRepository(listOf(steuer))
        val faelligkeiten = FakeFaelligkeitRepository()
        val erinnerungen = FakeErinnerungRepository()
        val uhr = uhrAm(heute)
        val material = materialisieren(aktivitaeten, faelligkeiten, erinnerungen, uhr)
        material()

        val neueDeadline = LocalDate.of(2026, 3, 20)
        aktivitaeten.speichern(steuer.copy(zeitplan = Zeitplan.Einmalig(neueDeadline, LocalTime.of(9, 0))))
        AktivitaetNeuPlanen(
            faelligkeiten, erinnerungen, aktivitaeten, material, uhr,
            alarmNeuPlanen = {},
            widgetAnstossen = {},
        )(1)

        val f = faelligkeiten.stand.value.single()
        assertEquals(heute, f.periodeVon)
        assertEquals(neueDeadline, f.periodeBis)
        assertEquals(
            listOf(2, 6, 9, 13, 16, 20).map { LocalDateTime.of(2026, 3, it, 9, 0) },
            erinnerungen.stand.value.map { it.zeitpunkt },
        )
    }

    @Test
    fun `geaenderte Erinnerungszeit ersetzt die alten Termine`() = runTest {
        val aktivitaeten = FakeAktivitaetRepository(listOf(steuer))
        val faelligkeiten = FakeFaelligkeitRepository()
        val erinnerungen = FakeErinnerungRepository()
        val uhr = uhrAm(heute)
        val material = materialisieren(aktivitaeten, faelligkeiten, erinnerungen, uhr)
        material()

        aktivitaeten.speichern(steuer.copy(zeitplan = Zeitplan.Einmalig(deadline, LocalTime.of(18, 0))))
        AktivitaetNeuPlanen(
            faelligkeiten, erinnerungen, aktivitaeten, material, uhr,
            alarmNeuPlanen = {},
            widgetAnstossen = {},
        )(1)

        assertEquals(
            listOf(2, 6, 9, 13).map { LocalDateTime.of(2026, 3, it, 18, 0) },
            erinnerungen.stand.value.map { it.zeitpunkt },
        )
    }

    @Test
    fun `vor der Deadline gibt es die Punkte ohne Tagesbonus-Blockade`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(offen))
        val buchungen = FakeBuchungRepository()

        assertEquals(ErledigungsErgebnis.GEBUCHT, erledigen(faelligkeiten, buchungen, uhrAm(deadline, 20))(10))

        // Nur die Basispunkte: die Einmalaufgabe ist kein Tagesitem und loest keinen
        // Tagesbonus aus.
        assertEquals(20, buchungen.stand.value.sumOf { it.betrag })
        assertEquals(FaelligkeitStatus.ERLEDIGT, faelligkeiten.stand.value.single().status)
    }

    @Test
    fun `eine verpasste Einmalaufgabe laesst sich ohne Punkte nachtraeglich abhaken`() = runTest {
        val verpasst = offen.copy(status = FaelligkeitStatus.VERPASST)
        val faelligkeiten = FakeFaelligkeitRepository(listOf(verpasst))
        val buchungen = FakeBuchungRepository()
        val use = erledigen(faelligkeiten, buchungen, uhrAm(deadline.plusDays(2), 12))

        val protokoll = use.mitProtokoll(10)

        assertEquals(ErledigungsProtokoll.Gebucht(verpasst, emptyList()), protokoll)
        val nachher = faelligkeiten.stand.value.single()
        assertEquals(FaelligkeitStatus.VERPASST, nachher.status)
        assertEquals(1, nachher.istAnzahl)
        assertEquals(LocalDateTime.of(2026, 3, 15, 12, 0), nachher.erledigtAm)
        assertEquals(emptyList(), buchungen.stand.value)
        assertEquals(ErledigungsErgebnis.NICHT_OFFEN, use(10))
    }

    @Test
    fun `nach der Deadline gibt es auch vor dem Waechterlauf keine Punkte`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(offen))
        val buchungen = FakeBuchungRepository()

        erledigen(faelligkeiten, buchungen, uhrAm(deadline.plusDays(1), 7))(10)

        assertEquals(emptyList(), buchungen.stand.value)
        assertEquals(FaelligkeitStatus.VERPASST, faelligkeiten.stand.value.single().status)
    }

    @Test
    fun `das nachtraegliche Abhaken laesst sich ruecknehmen`() = runTest {
        val verpasst = offen.copy(status = FaelligkeitStatus.VERPASST)
        val faelligkeiten = FakeFaelligkeitRepository(listOf(verpasst))
        val buchungen = FakeBuchungRepository()
        val uhr = uhrAm(deadline.plusDays(2), 12)
        val protokoll = erledigen(faelligkeiten, buchungen, uhr).mitProtokoll(10)

        FaelligkeitErledigenRueckgaengig(faelligkeiten, buchungen, FakeTransaktionen(), uhr)(
            protokoll as ErledigungsProtokoll.Gebucht,
        )

        assertEquals(verpasst, faelligkeiten.stand.value.single())
        assertNull(faelligkeiten.stand.value.single().erledigtAm)
        assertEquals(emptyList(), buchungen.stand.value)
    }

    @Test
    fun `eine Erinnerung zur Einmalaufgabe fasst nicht nach`() = runTest {
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 1, faelligkeitId = 10, zeitpunkt = LocalDateTime.of(2026, 3, 2, 9, 0))),
        )
        val gezeigt = mutableListOf<Long>()
        val anzeige = object : Erinnerungsanzeige {
            override fun zeige(faelligkeit: Faelligkeit, aktivitaet: Aktivitaet) {
                gezeigt += faelligkeit.id
            }
            override fun verstecke(faelligkeitId: Long) {}
            override fun alleVerstecken() {}
        }

        ErinnerungAusloesen(
            erinnerungen,
            FakeFaelligkeitRepository(listOf(offen)),
            FakeAktivitaetRepository(listOf(steuer)),
            anzeige,
            uhrAm(heute, 9),
        )()

        assertEquals(listOf(10L), gezeigt)
        assertEquals(1, erinnerungen.stand.value.size)
    }

    @Test
    fun `eine vorverlegte Deadline gilt auch vor dem Abgleich der Faelligkeit`() = runTest {
        // Die Faelligkeit traegt noch die alte, spaetere Deadline; die Aktivitaet ist schon
        // auf eine fruehere umgestellt, die inzwischen verstrichen ist.
        val frueher = steuer.copy(zeitplan = Zeitplan.Einmalig(LocalDate.of(2026, 3, 6)))
        val faelligkeiten = FakeFaelligkeitRepository(listOf(offen))
        val buchungen = FakeBuchungRepository()
        val use = FaelligkeitErledigen(
            aktivitaeten = FakeAktivitaetRepository(listOf(frueher)),
            faelligkeiten = faelligkeiten,
            buchungen = buchungen,
            punkteRechner = PunkteRechner(),
            serienRechner = SerienRechner(),
            transaktionen = FakeTransaktionen(),
            einstellungenLesen = { Einstellungen() },
            uhr = uhrAm(LocalDate.of(2026, 3, 9), 12),
        )

        use(10)

        assertEquals(emptyList(), buchungen.stand.value)
        assertEquals(FaelligkeitStatus.VERPASST, faelligkeiten.stand.value.single().status)
    }
}
