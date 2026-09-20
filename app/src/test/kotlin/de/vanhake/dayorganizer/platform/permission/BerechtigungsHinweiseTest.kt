package de.vanhake.dayorganizer.platform.permission

import kotlin.test.Test
import kotlin.test.assertEquals

class BerechtigungsHinweiseTest {

    private val allesGut = BerechtigungsStand(
        benachrichtigungenErlaubt = true,
        exakteAlarmeErlaubt = true,
        akkuOptimierungAktiv = false,
        waechterHaengt = false,
    )

    @Test
    fun `bei vollstaendigen Berechtigungen gibt es keinen Hinweis`() {
        assertEquals(emptyList(), BerechtigungsHinweise.fuer(allesGut))
    }

    @Test
    fun `fehlende Benachrichtigungen stehen an erster Stelle`() {
        val stand = allesGut.copy(benachrichtigungenErlaubt = false, exakteAlarmeErlaubt = false)

        assertEquals(
            listOf(Hinweis.BENACHRICHTIGUNGEN_FEHLEN, Hinweis.EXAKTE_ALARME_FEHLEN),
            BerechtigungsHinweise.fuer(stand),
        )
    }

    @Test
    fun `aktive Akkuoptimierung wird gemeldet`() {
        assertEquals(
            listOf(Hinweis.AKKUOPTIMIERUNG_AKTIV),
            BerechtigungsHinweise.fuer(allesGut.copy(akkuOptimierungAktiv = true)),
        )
    }

    @Test
    fun `ein haengender Waechter wird gemeldet`() {
        assertEquals(
            listOf(Hinweis.WAECHTER_HAENGT),
            BerechtigungsHinweise.fuer(allesGut.copy(waechterHaengt = true)),
        )
    }

    @Test
    fun `alle vier Probleme werden nach Dringlichkeit sortiert gemeldet, der haengende Waechter zuletzt`() {
        val stand = BerechtigungsStand(
            benachrichtigungenErlaubt = false,
            exakteAlarmeErlaubt = false,
            akkuOptimierungAktiv = true,
            waechterHaengt = true,
        )

        assertEquals(
            listOf(
                Hinweis.BENACHRICHTIGUNGEN_FEHLEN,
                Hinweis.EXAKTE_ALARME_FEHLEN,
                Hinweis.AKKUOPTIMIERUNG_AKTIV,
                Hinweis.WAECHTER_HAENGT,
            ),
            BerechtigungsHinweise.fuer(stand),
        )
    }
}
