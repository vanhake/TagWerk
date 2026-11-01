package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.VerspaetungsRegel
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PunkteRechnerTest {

    private val rechner = PunkteRechner()
    private val tag = LocalDate.of(2026, 3, 2)

    private val tabletten = aktivitaet(
        basisPunkte = 10,
        kulanzMinuten = 60,
        puenktlichkeitsBonus = 5,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
    )

    private val faelligkeit = Faelligkeit(
        id = 42,
        aktivitaetId = tabletten.id,
        periodeVon = tag,
        periodeBis = tag,
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0),
        sollAnzahl = 1,
    )

    private fun kontext(
        zeitpunkt: LocalDateTime,
        serieNachher: Int = 1,
        gebuchteSchwellen: Set<Int> = emptySet(),
        aktivitaet: de.vanhake.dayorganizer.domain.model.Aktivitaet = tabletten,
        tagesFaelligkeitenNachher: List<Faelligkeit> =
            listOf(faelligkeit.copy(status = FaelligkeitStatus.ERLEDIGT, istAnzahl = 1)),
        tagesbonusPunkte: Int = 0,
        tagesbonusBereitsGebucht: Boolean = false,
        verspaetungsRegel: VerspaetungsRegel = VerspaetungsRegel.VOLLE_PUNKTE,
    ) = ErledigungsKontext(
        aktivitaet = aktivitaet,
        faelligkeitVorher = faelligkeit,
        zeitpunkt = zeitpunkt,
        serieNachher = serieNachher,
        gebuchteSchwellen = gebuchteSchwellen,
        tagesFaelligkeitenNachher = tagesFaelligkeitenNachher,
        tagesbonusPunkte = tagesbonusPunkte,
        tagesbonusBereitsGebucht = tagesbonusBereitsGebucht,
        verspaetungsRegel = verspaetungsRegel,
    )

    @Test
    fun `innerhalb des Kulanzfensters gibt es Basis und Puenktlichkeitsbonus`() {
        val b = rechner.buchungen(kontext(LocalDateTime.of(2026, 3, 2, 8, 30)))

        assertEquals(listOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), b.map { it.art })
        assertEquals(listOf(10, 5), b.map { it.betrag })
        assertEquals(42, b.first().faelligkeitId)
    }

    @Test
    fun `vor dem Sollzeitpunkt zaehlt das Kulanzfenster ebenfalls`() {
        val b = rechner.buchungen(kontext(LocalDateTime.of(2026, 3, 2, 7, 15)))

        assertEquals(15, b.sumOf { it.betrag })
    }

    @Test
    fun `ausserhalb des Kulanzfensters gibt es nur Basispunkte`() {
        val b = rechner.buchungen(kontext(LocalDateTime.of(2026, 3, 2, 12, 0)))

        assertEquals(listOf(BuchungsArt.ERLEDIGUNG), b.map { it.art })
        assertEquals(10, b.single().betrag)
    }

    @Test
    fun `Zaehlziele ohne Sollzeitpunkt bekommen keinen Puenctlichkeitsbonus`() {
        val sport = aktivitaet(
            basisPunkte = 20,
            puenktlichkeitsBonus = 5,
            zeitplan = Zeitplan.Wochenziel(anzahl = 3),
        )
        val wochenziel = faelligkeit.copy(geplantFuer = null, sollAnzahl = 3)
        val k = ErledigungsKontext(
            aktivitaet = sport,
            faelligkeitVorher = wochenziel,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 18, 0),
            serieNachher = 1,
            gebuchteSchwellen = emptySet(),
            tagesFaelligkeitenNachher = listOf(wochenziel.copy(istAnzahl = 1)),
            tagesbonusPunkte = 0,
            tagesbonusBereitsGebucht = false,
        )

        val b = rechner.buchungen(k)

        assertEquals(listOf(BuchungsArt.ERLEDIGUNG), b.map { it.art })
        assertEquals(20, b.single().betrag)
    }

    @Test
    fun `eine erreichte Serienschwelle wird einmal gebucht`() {
        val mitSchwellen = tabletten.copy(
            serienSchwellen = listOf(SerienSchwelle(7, 10), SerienSchwelle(30, 50)),
        )

        val b = rechner.buchungen(
            kontext(LocalDateTime.of(2026, 3, 2, 8, 5), serieNachher = 7, aktivitaet = mitSchwellen),
        )

        assertEquals(
            listOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS, BuchungsArt.SERIENBONUS),
            b.map { it.art },
        )
        assertEquals(10, b.last().betrag)
        assertEquals(7, b.last().schwelle)
    }

    @Test
    fun `eine bereits gebuchte Schwelle wird nicht erneut gebucht`() {
        val mitSchwellen = tabletten.copy(serienSchwellen = listOf(SerienSchwelle(7, 10)))

        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 8, 5),
                serieNachher = 9,
                gebuchteSchwellen = setOf(7),
                aktivitaet = mitSchwellen,
            ),
        )

        assertEquals(listOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), b.map { it.art })
    }

    @Test
    fun `der Tagesbonus kommt wenn alle heutigen Faelligkeiten erledigt sind`() {
        val zweite = faelligkeit.copy(id = 43, status = FaelligkeitStatus.ERLEDIGT, istAnzahl = 1)
        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 8, 5),
                tagesFaelligkeitenNachher = listOf(
                    faelligkeit.copy(status = FaelligkeitStatus.ERLEDIGT, istAnzahl = 1),
                    zweite,
                ),
                tagesbonusPunkte = 15,
            ),
        )

        assertEquals(BuchungsArt.TAGESBONUS, b.last().art)
        assertEquals(15, b.last().betrag)
    }

    @Test
    fun `eine ausgelassene Faelligkeit verhindert den Tagesbonus`() {
        val ausgelassen = faelligkeit.copy(id = 43, status = FaelligkeitStatus.AUSGELASSEN)
        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 8, 5),
                tagesFaelligkeitenNachher = listOf(
                    faelligkeit.copy(status = FaelligkeitStatus.ERLEDIGT, istAnzahl = 1),
                    ausgelassen,
                ),
                tagesbonusPunkte = 15,
            ),
        )

        assertEquals(setOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), b.map { it.art }.toSet())
    }

    @Test
    fun `ein bereits gebuchter Tagesbonus wird nicht verdoppelt`() {
        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 8, 5),
                tagesbonusPunkte = 15,
                tagesbonusBereitsGebucht = true,
            ),
        )

        assertEquals(setOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), b.map { it.art }.toSet())
    }

    @Test
    fun `mehrere auf einmal erreichte Schwellen werden aufsteigend gebucht`() {
        val mitSchwellen = tabletten.copy(
            puenktlichkeitsBonus = 0,
            serienSchwellen = listOf(SerienSchwelle(30, 50), SerienSchwelle(7, 10)),
        )

        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 8, 5),
                serieNachher = 30,
                gebuchteSchwellen = emptySet(),
                aktivitaet = mitSchwellen,
            ),
        )

        assertEquals(
            listOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.SERIENBONUS, BuchungsArt.SERIENBONUS),
            b.map { it.art },
        )
        assertEquals(listOf(7, 30), b.filter { it.art == BuchungsArt.SERIENBONUS }.map { it.schwelle })
        assertEquals(70, b.sumOf { it.betrag })
    }

    @Test
    fun `genau am Rand des Kulanzfensters gibt es den Bonus noch`() {
        val b = rechner.buchungen(kontext(LocalDateTime.of(2026, 3, 2, 9, 0)))

        assertEquals(listOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), b.map { it.art })
        assertEquals(15, b.sumOf { it.betrag })
    }

    @Test
    fun `eine Minute nach dem Kulanzfenster gibt es ihn nicht mehr`() {
        val b = rechner.buchungen(kontext(LocalDateTime.of(2026, 3, 2, 9, 1)))

        assertEquals(listOf(BuchungsArt.ERLEDIGUNG), b.map { it.art })
        assertEquals(10, b.single().betrag)
    }

    @Test
    fun `ein Tag ohne Faelligkeiten bekommt keinen Tagesbonus`() {
        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 8, 5),
                tagesFaelligkeitenNachher = emptyList(),
                tagesbonusPunkte = 15,
            ),
        )

        assertEquals(false, b.any { it.art == BuchungsArt.TAGESBONUS })
        assertEquals(setOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), b.map { it.art }.toSet())
    }

    // --- Verspaetung: Erledigung nach dem Kulanzfenster ------------------------------------

    @Test
    fun `nach dem Kulanzfenster bucht KEINE_PUNKTE nichts, auch keine Boni`() {
        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 9, 1),
                serieNachher = 3,
                aktivitaet = tabletten.copy(serienSchwellen = listOf(SerienSchwelle(3, 30))),
                tagesbonusPunkte = 15,
                verspaetungsRegel = VerspaetungsRegel.KEINE_PUNKTE,
            ),
        )

        assertEquals(emptyList(), b)
    }

    @Test
    fun `genau am Ende des Kulanzfensters gibt es noch alle Punkte`() {
        val b = rechner.buchungen(
            kontext(LocalDateTime.of(2026, 3, 2, 9, 0), verspaetungsRegel = VerspaetungsRegel.KEINE_PUNKTE),
        )

        assertEquals(15, b.sumOf { it.betrag })
    }

    @Test
    fun `zu frueh ist nicht verspaetet`() {
        val b = rechner.buchungen(
            kontext(LocalDateTime.of(2026, 3, 2, 6, 0), verspaetungsRegel = VerspaetungsRegel.KEINE_PUNKTE),
        )

        assertEquals(listOf(BuchungsArt.ERLEDIGUNG), b.map { it.art })
        assertEquals(10, b.single().betrag)
    }

    @Test
    fun `nach dem Kulanzfenster bucht HALBE_PUNKTE die halbe Basis und die Boni`() {
        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 12, 0),
                serieNachher = 3,
                aktivitaet = tabletten.copy(basisPunkte = 15, serienSchwellen = listOf(SerienSchwelle(3, 30))),
                tagesbonusPunkte = 20,
                verspaetungsRegel = VerspaetungsRegel.HALBE_PUNKTE,
            ),
        )

        assertEquals(
            listOf(BuchungsArt.ERLEDIGUNG to 7, BuchungsArt.SERIENBONUS to 30, BuchungsArt.TAGESBONUS to 20),
            b.map { it.art to it.betrag },
        )
    }

    @Test
    fun `Zaehlziele sind nie verspaetet`() {
        val sport = aktivitaet(basisPunkte = 20, zeitplan = Zeitplan.Wochenziel(anzahl = 3))
        val k = kontext(LocalDateTime.of(2026, 3, 2, 23, 0), aktivitaet = sport, verspaetungsRegel = VerspaetungsRegel.KEINE_PUNKTE)
            .copy(faelligkeitVorher = faelligkeit.copy(geplantFuer = null, sollAnzahl = 3))

        assertEquals(20, rechner.buchungen(k).single().betrag)
    }
}
