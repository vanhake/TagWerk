package de.vanhake.dayorganizer.widget

import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetMasseTest {

    // 32 + 28 + 4 * 62 + 26 = 334
    @Test
    fun `334 dp bei 6 offenen zeigen 4 Zeilen`() {
        assertEquals(4, WidgetMasse.passendeZeilen(334f, 6, 1.0f))
    }

    @Test
    fun `333 dp bei 6 offenen zeigen eine Zeile weniger`() {
        assertEquals(3, WidgetMasse.passendeZeilen(333f, 6, 1.0f))
    }

    @Test
    fun `308 dp bei 4 offenen brauchen keine Fusszeile`() {
        assertEquals(4, WidgetMasse.passendeZeilen(308f, 4, 1.0f))
    }

    @Test
    fun `110 dp bei 3 offenen zeigen nur Kopf und Fusszeile`() {
        assertEquals(0, WidgetMasse.passendeZeilen(110f, 3, 1.0f))
    }

    // 32 + 28 + 62 + 26 = 148
    @Test
    fun `148 dp bei 3 offenen zeigen eine Zeile`() {
        assertEquals(1, WidgetMasse.passendeZeilen(148f, 3, 1.0f))
    }

    @Test
    fun `nie mehr Zeilen als offen`() {
        assertEquals(2, WidgetMasse.passendeZeilen(400f, 2, 1.0f))
    }

    @Test
    fun `hoechstens 4 Zeilen`() {
        assertEquals(4, WidgetMasse.passendeZeilen(1000f, 9, 1.0f))
    }

    @Test
    fun `nichts offen ergibt keine Zeilen`() {
        assertEquals(0, WidgetMasse.passendeZeilen(500f, 0, 1.0f))
    }

    // kopf 31,5, zeile 70, Fuss 34: k = 2 -> 257,5, k = 3 -> 337,5 > 334
    @Test
    fun `Schriftfaktor 1,5 zeigt weniger Zeilen`() {
        assertEquals(2, WidgetMasse.passendeZeilen(334f, 6, 1.5f))
    }

    // kopf 42, zeile 88, Fuss 42 -> 32 + 42 + 2 * 98 + 42 = 312 (k = 3: 410)
    @Test
    fun `Schriftfaktor 2,0 zeigt weniger Zeilen`() {
        assertEquals(2, WidgetMasse.passendeZeilen(334f, 6, 2.0f))
    }

    // Kopf 42 + Innenabstand 32 = 74 dp, dazu Fusszeile 42: 116 > 110, auch k = 0 passt nicht.
    @Test
    fun `passt nicht einmal der Kopf mit Fusszeile ergibt 0 statt Ausnahme`() {
        assertEquals(0, WidgetMasse.passendeZeilen(110f, 3, 2.0f))
    }

    @Test
    fun `Hoehe unter dem Kopf ergibt 0 Zeilen`() {
        assertEquals(0, WidgetMasse.passendeZeilen(50f, 3, 1.0f))
    }

    // 32 + 28 + 10 + 8 + 20 + 8 = 106
    @Test
    fun `der Leerhinweis passt bei normaler Schrift in die Mindesthoehe`() {
        assertEquals(true, WidgetMasse.leerhinweisPasst(110f, 1.0f))
    }

    // Kopf 42: 32 + 42 + 10 + 8 + 40 + 8 = 140
    @Test
    fun `bei doppelter Schrift passt der Leerhinweis nicht in die Mindesthoehe`() {
        assertEquals(false, WidgetMasse.leerhinweisPasst(110f, 2.0f))
        assertEquals(true, WidgetMasse.leerhinweisPasst(150f, 2.0f))
    }

    // Bewacht KOPF_SCHRIFT_DP = 21f: 140 > 139; mit 20f waeren es 138 <= 139.
    @Test
    fun `der Leerhinweis nutzt die Kopfhoehe von 21 dp je Schriftfaktor`() {
        assertEquals(false, WidgetMasse.leerhinweisPasst(139f, 2.0f))
    }

    // Mit 21f: 337,5 > 336; mit 20f waeren es 336 <= 336 (drei Zeilen).
    @Test
    fun `Schriftfaktor 1,5 zeigt bei 336 dp nur zwei Zeilen`() {
        assertEquals(2, WidgetMasse.passendeZeilen(336f, 6, 1.5f))
    }
}
