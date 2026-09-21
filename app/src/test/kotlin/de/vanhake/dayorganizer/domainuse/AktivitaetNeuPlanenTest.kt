package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Erinnerung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Deckt Finding 1 und 2 der Branch-Review ab: Archivieren und Zeitplanaenderung duerfen
 * kuenftige, noch nicht begonnene Faelligkeiten und ihre Erinnerungen nicht liegen lassen,
 * duerfen aber weder Vergangenheit noch angefangenen Fortschritt anfassen.
 */
class AktivitaetNeuPlanenTest {

    private val zone = ZoneId.of("Europe/Berlin")

    // "Heute" fuer alle Tests: 2026-03-02.
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 6, 0).atZone(zone).toInstant(),
        zone,
    )

    // wochentage bewusst leer: FaelligkeitenMaterialisieren legt dann in diesen Tests nichts
    // Neues an. So bleibt eindeutig unterscheidbar, ob eine Zeile durch das Aufraeumen
    // verschwunden ist, statt zufaellig durch eine frisch materialisierte Zeile am selben
    // Slot mit derselben (in der Fake wiederverwendeten) Id ersetzt zu werden.
    private val tabletten = Aktivitaet(
        id = 1,
        name = "Tabletten",
        farbe = 1,
        basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), emptySet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    /** Kuenftig, offen, unangetastet -- der Kandidat, der verschwinden muss. */
    private val kuenftigUnberuehrt = Faelligkeit(
        id = 1, aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 3), periodeBis = LocalDate.of(2026, 3, 3),
        geplantFuer = LocalDateTime.of(2026, 3, 3, 8, 0),
        sollAnzahl = 1, istAnzahl = 0, status = FaelligkeitStatus.OFFEN,
    )

    /** Kuenftiges Wochenziel, teilweise erfuellt -- der Fortschritt gehoert dem Nutzer. */
    private val teilweiseErfuellt = Faelligkeit(
        id = 2, aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 2), periodeBis = LocalDate.of(2026, 3, 8),
        geplantFuer = null,
        sollAnzahl = 5, istAnzahl = 2, status = FaelligkeitStatus.OFFEN,
    )

    /** Vergangen, offen -- Historie wird nicht umgeschrieben. */
    private val vergangen = Faelligkeit(
        id = 3, aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 1), periodeBis = LocalDate.of(2026, 3, 1),
        geplantFuer = LocalDateTime.of(2026, 3, 1, 8, 0),
        sollAnzahl = 1, istAnzahl = 0, status = FaelligkeitStatus.OFFEN,
    )

    /** Kuenftig, aber schon erledigt. */
    private val kuenftigErledigt = Faelligkeit(
        id = 4, aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 5), periodeBis = LocalDate.of(2026, 3, 5),
        geplantFuer = LocalDateTime.of(2026, 3, 5, 8, 0),
        sollAnzahl = 1, istAnzahl = 1, status = FaelligkeitStatus.ERLEDIGT,
    )

    /** Kuenftig, aber bewusst ausgelassen. */
    private val kuenftigAusgelassen = Faelligkeit(
        id = 5, aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 6), periodeBis = LocalDate.of(2026, 3, 6),
        geplantFuer = LocalDateTime.of(2026, 3, 6, 8, 0),
        sollAnzahl = 1, istAnzahl = 0, status = FaelligkeitStatus.AUSGELASSEN,
    )

    private fun neuPlanen(
        faelligkeiten: FakeFaelligkeitRepository,
        erinnerungen: FakeErinnerungRepository,
        aktivitaeten: FakeAktivitaetRepository = FakeAktivitaetRepository(listOf(tabletten)),
        alarmNeuPlanen: suspend () -> Unit = {},
        widgetAnstossen: suspend () -> Unit = {},
    ) = AktivitaetNeuPlanen(
        faelligkeiten = faelligkeiten,
        erinnerungen = erinnerungen,
        materialisieren = FaelligkeitenMaterialisieren(
            aktivitaeten, faelligkeiten, erinnerungen, ZeitplanRechner(), uhr,
        ),
        uhr = uhr,
        alarmNeuPlanen = alarmNeuPlanen,
        widgetAnstossen = widgetAnstossen,
    )

    @Test
    fun `kuenftige offene unberuehrte Faelligkeiten und ihre Erinnerungen verschwinden`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(kuenftigUnberuehrt))
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 100, faelligkeitId = 1, zeitpunkt = LocalDateTime.of(2026, 3, 3, 8, 0))),
        )
        val use = neuPlanen(faelligkeiten, erinnerungen)

        use(1)

        assertTrue(faelligkeiten.stand.value.none { it.id == 1L })
        assertTrue(erinnerungen.stand.value.none { it.faelligkeitId == 1L })
    }

    @Test
    fun `ein teilweise erfuelltes Zaehlziel bleibt stehen`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(teilweiseErfuellt))
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 200, faelligkeitId = 2, zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 0))),
        )
        val use = neuPlanen(faelligkeiten, erinnerungen)

        use(1)

        val nachher = faelligkeiten.stand.value.find { it.id == 2L }
        assertTrue(nachher != null, "Das teilweise erfuellte Ziel muss erhalten bleiben")
        assertEquals(2, nachher?.istAnzahl)
        assertTrue(erinnerungen.stand.value.any { it.faelligkeitId == 2L })
    }

    @Test
    fun `eine vergangene Faelligkeit bleibt unangetastet`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(vergangen))
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 300, faelligkeitId = 3, zeitpunkt = LocalDateTime.of(2026, 3, 1, 8, 0))),
        )
        val use = neuPlanen(faelligkeiten, erinnerungen)

        use(1)

        assertTrue(faelligkeiten.stand.value.any { it.id == 3L })
        assertTrue(erinnerungen.stand.value.any { it.faelligkeitId == 3L })
    }

    @Test
    fun `eine bereits erledigte kuenftige Faelligkeit bleibt stehen`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(kuenftigErledigt))
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 400, faelligkeitId = 4, zeitpunkt = LocalDateTime.of(2026, 3, 5, 8, 0))),
        )
        val use = neuPlanen(faelligkeiten, erinnerungen)

        use(1)

        assertTrue(faelligkeiten.stand.value.any { it.id == 4L })
        assertTrue(erinnerungen.stand.value.any { it.faelligkeitId == 4L })
    }

    @Test
    fun `eine bereits ausgelassene kuenftige Faelligkeit bleibt stehen`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(kuenftigAusgelassen))
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 500, faelligkeitId = 5, zeitpunkt = LocalDateTime.of(2026, 3, 6, 8, 0))),
        )
        val use = neuPlanen(faelligkeiten, erinnerungen)

        use(1)

        assertTrue(faelligkeiten.stand.value.any { it.id == 5L })
        assertTrue(erinnerungen.stand.value.any { it.faelligkeitId == 5L })
    }

    @Test
    fun `alle Faelle zusammen -- nur das kuenftige unberuehrte verschwindet`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(
            listOf(kuenftigUnberuehrt, teilweiseErfuellt, vergangen, kuenftigErledigt, kuenftigAusgelassen),
        )
        val erinnerungen = FakeErinnerungRepository(
            listOf(
                Erinnerung(id = 101, faelligkeitId = 1, zeitpunkt = LocalDateTime.of(2026, 3, 3, 8, 0)),
                Erinnerung(id = 102, faelligkeitId = 2, zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 0)),
                Erinnerung(id = 103, faelligkeitId = 3, zeitpunkt = LocalDateTime.of(2026, 3, 1, 8, 0)),
                Erinnerung(id = 104, faelligkeitId = 4, zeitpunkt = LocalDateTime.of(2026, 3, 5, 8, 0)),
                Erinnerung(id = 105, faelligkeitId = 5, zeitpunkt = LocalDateTime.of(2026, 3, 6, 8, 0)),
            ),
        )
        val use = neuPlanen(faelligkeiten, erinnerungen)

        use(1)

        assertFalse(faelligkeiten.stand.value.any { it.id == 1L }, "Kuenftig+unberuehrt muss weg sein")
        assertTrue(faelligkeiten.stand.value.any { it.id == 2L }, "Teilweise erfuelltes Ziel bleibt")
        assertTrue(faelligkeiten.stand.value.any { it.id == 3L }, "Vergangenheit bleibt")
        assertTrue(faelligkeiten.stand.value.any { it.id == 4L }, "Erledigtes bleibt")
        assertTrue(faelligkeiten.stand.value.any { it.id == 5L }, "Ausgelassenes bleibt")

        assertFalse(erinnerungen.stand.value.any { it.faelligkeitId == 1L })
        assertTrue(erinnerungen.stand.value.any { it.faelligkeitId == 2L })
        assertTrue(erinnerungen.stand.value.any { it.faelligkeitId == 3L })
        assertTrue(erinnerungen.stand.value.any { it.faelligkeitId == 4L })
        assertTrue(erinnerungen.stand.value.any { it.faelligkeitId == 5L })
    }

    @Test
    fun `der Alarm wird neu geplant`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(kuenftigUnberuehrt))
        val erinnerungen = FakeErinnerungRepository()
        var alarmNeuGeplant = false
        val use = neuPlanen(faelligkeiten, erinnerungen, alarmNeuPlanen = { alarmNeuGeplant = true })

        use(1)

        assertTrue(alarmNeuGeplant)
    }

    @Test
    fun `das Widget wird angestossen, damit archivierte Aufgaben nicht weiter erscheinen`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(kuenftigUnberuehrt))
        val erinnerungen = FakeErinnerungRepository()
        var widgetAngestossen = 0
        val use = neuPlanen(faelligkeiten, erinnerungen, widgetAnstossen = { widgetAngestossen++ })

        use(1)

        assertEquals(1, widgetAngestossen)
    }
}
