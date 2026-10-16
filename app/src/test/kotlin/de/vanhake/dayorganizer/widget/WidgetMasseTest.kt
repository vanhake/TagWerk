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

    // kopf 30, zeile 70, Fuss 34
    @Test
    fun `Schriftfaktor 1,5 zeigt weniger Zeilen`() {
        assertEquals(2, WidgetMasse.passendeZeilen(334f, 6, 1.5f))
    }

    // kopf 40, zeile 88, Fuss 42 -> 32 + 40 + 2 * 98 + 42 = 310
    @Test
    fun `Schriftfaktor 2,0 zeigt weniger Zeilen`() {
        assertEquals(2, WidgetMasse.passendeZeilen(334f, 6, 2.0f))
    }
}
