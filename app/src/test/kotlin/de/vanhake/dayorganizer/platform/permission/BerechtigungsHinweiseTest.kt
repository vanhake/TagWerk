package de.vanhake.dayorganizer.platform.permission

import kotlin.test.Test
import kotlin.test.assertEquals

class BerechtigungsHinweiseTest {

    private val allesGut = BerechtigungsStand(
        benachrichtigungenErlaubt = true,
        exakteAlarmeErlaubt = true,
        akkuOptimierungAktiv = false,
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
    fun `alle drei Probleme werden nach Dringlichkeit sortiert gemeldet`() {
        val stand = BerechtigungsStand(false, false, true)

        assertEquals(
            listOf(
                Hinweis.BENACHRICHTIGUNGEN_FEHLEN,
                Hinweis.EXAKTE_ALARME_FEHLEN,
                Hinweis.AKKUOPTIMIERUNG_AKTIV,
            ),
            BerechtigungsHinweise.fuer(stand),
        )
    }
}
