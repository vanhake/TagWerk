package de.vanhake.dayorganizer.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetUntertitelTest {

    private val res = ApplicationProvider.getApplicationContext<Context>().resources

    private fun eintrag(
        zeitText: String?,
        ueberfaellig: Boolean = false,
        ist: Int = 0,
        soll: Int = 1,
        wochenziel: Boolean = false,
    ) = WidgetEintrag(
        faelligkeitId = 1, name = "Tabletten", symbol = "self_improvement",
        zeitText = zeitText, ueberfaellig = ueberfaellig,
        istAnzahl = ist, sollAnzahl = soll, wochenziel = wochenziel,
    )

    @Test
    fun `Uhrzeit allein`() {
        assertEquals("08:00", widgetUntertitel(eintrag("08:00"), res))
    }

    @Test
    fun `ueberfaelliger Termin`() {
        assertEquals("08:00 · überfällig", widgetUntertitel(eintrag("08:00", ueberfaellig = true), res))
    }

    @Test
    fun `Termin mit Zaehlziel`() {
        assertEquals("08:00 · 1 von 2 heute", widgetUntertitel(eintrag("08:00", ist = 1, soll = 2), res))
    }

    @Test
    fun `ueberfaelliger Termin mit Zaehlziel`() {
        assertEquals(
            "08:00 · überfällig · 1 von 2 heute",
            widgetUntertitel(eintrag("08:00", ueberfaellig = true, ist = 1, soll = 2), res),
        )
    }

    @Test
    fun `Tagesziel ohne Uhrzeit`() {
        assertEquals("1 von 3 heute", widgetUntertitel(eintrag(null, ist = 1, soll = 3), res))
    }

    @Test
    fun `Wochenziel ohne Uhrzeit`() {
        assertEquals(
            "2 von 3 diese Woche",
            widgetUntertitel(eintrag(null, ist = 2, soll = 3, wochenziel = true), res),
        )
    }

    @Test
    fun `eine Einmalaufgabe nennt die Tage bis zur Deadline`() {
        val aufgabe = eintrag(null, wochenziel = true)

        assertEquals("noch 3 Tage", widgetUntertitel(aufgabe.copy(deadlineInTagen = 3), res))
        assertEquals("morgen fällig", widgetUntertitel(aufgabe.copy(deadlineInTagen = 1), res))
        assertEquals("heute fällig", widgetUntertitel(aufgabe.copy(deadlineInTagen = 0), res))
    }
}
