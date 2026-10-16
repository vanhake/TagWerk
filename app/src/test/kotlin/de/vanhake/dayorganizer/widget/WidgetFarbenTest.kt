package de.vanhake.dayorganizer.widget

import de.vanhake.dayorganizer.data.ThemeModus
import de.vanhake.dayorganizer.ui.theme.DunkelFarben
import de.vanhake.dayorganizer.ui.theme.HellFarben
import kotlin.test.Test
import kotlin.test.assertSame

class WidgetFarbenTest {

    @Test
    fun `Dunkel liefert den dunklen Farbsatz der App`() {
        assertSame(DunkelFarben, widgetFarben(ThemeModus.DUNKEL))
    }

    @Test
    fun `Hell liefert den hellen Farbsatz der App`() {
        assertSame(HellFarben, widgetFarben(ThemeModus.HELL))
    }
}
