package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungsPrueferTest {

    private val pruefer = BelohnungsPruefer()
    private val heute = LocalDate.of(2026, 3, 2)
    private val kinoabend = Belohnung(id = 1, name = "Kinoabend", preisPunkte = 200)

    @Test
    fun `mit genug Punkten und ohne Bedingung ist einloesbar`() {
        val ergebnis = pruefer.pruefe(kinoabend, kontostand = 250, serieDerBedingung = 0, heute = heute)

        assertEquals(Einloesbarkeit(moeglich = true, fehlendePunkte = 0, hindernis = null), ergebnis)
    }

    @Test
    fun `fehlende Punkte werden beziffert`() {
        val ergebnis = pruefer.pruefe(kinoabend, kontostand = 160, serieDerBedingung = 0, heute = heute)

        assertEquals(
            Einloesbarkeit(moeglich = false, fehlendePunkte = 40, hindernis = Hindernis.ZU_WENIG_PUNKTE),
            ergebnis,
        )
    }

    @Test
    fun `eine zu kurze Serie verhindert das Einloesen trotz genug Punkten`() {
        val mitSerie = kinoabend.copy(
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
        )

        val ergebnis = pruefer.pruefe(mitSerie, kontostand = 500, serieDerBedingung = 9, heute = heute)

        assertEquals(
            Einloesbarkeit(moeglich = false, fehlendePunkte = 0, hindernis = Hindernis.SERIE_ZU_KURZ),
            ergebnis,
        )
    }

    @Test
    fun `eine erfuellte Serienbedingung laesst das Einloesen zu`() {
        val mitSerie = kinoabend.copy(
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
        )

        val ergebnis = pruefer.pruefe(mitSerie, kontostand = 500, serieDerBedingung = 14, heute = heute)

        assertEquals(true, ergebnis.moeglich)
    }

    @Test
    fun `vor dem Startdatum ist nicht einloesbar`() {
        val abApril = kinoabend.copy(
            bedingung = Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)),
        )

        val ergebnis = pruefer.pruefe(abApril, kontostand = 500, serieDerBedingung = 0, heute = heute)

        assertEquals(Hindernis.ZU_FRUEH, ergebnis.hindernis)
    }

    @Test
    fun `am Startdatum selbst ist einloesbar`() {
        val abHeute = kinoabend.copy(bedingung = Belohnungsbedingung.FruehestensAb(heute))

        assertEquals(true, pruefer.pruefe(abHeute, 500, 0, heute).moeglich)
    }

    @Test
    fun `fehlende Punkte wiegen schwerer als eine unerfuellte Bedingung`() {
        val mitSerie = kinoabend.copy(
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
        )

        val ergebnis = pruefer.pruefe(mitSerie, kontostand = 10, serieDerBedingung = 1, heute = heute)

        assertEquals(Hindernis.ZU_WENIG_PUNKTE, ergebnis.hindernis)
        assertEquals(190, ergebnis.fehlendePunkte)
    }

    @Test
    fun `eine archivierte Belohnung ist nie einloesbar`() {
        val ergebnis = pruefer.pruefe(kinoabend.copy(archiviert = true), 500, 0, heute)

        assertEquals(false, ergebnis.moeglich)
    }
}
