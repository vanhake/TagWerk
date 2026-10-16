package de.vanhake.dayorganizer.widget

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.theme.AKTIVITAET_SYMBOLE
import de.vanhake.dayorganizer.ui.theme.AKTIVITAET_SYMBOL_STANDARD
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Sichert, dass jedes Aktivitaetssymbol im Widget ein eigenes, ladbares Drawable hat. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetSymboleTest {

    @Test
    fun `jedes Aktivitaetssymbol hat ein eigenes Drawable`() {
        assertEquals(12, AKTIVITAET_SYMBOLE.map(::widgetSymbol).toSet().size)
    }

    @Test
    fun `unbekannter Schluessel faellt auf das Standardsymbol zurueck`() {
        assertEquals(widgetSymbol(AKTIVITAET_SYMBOL_STANDARD), widgetSymbol("gibt_es_nicht"))
        assertEquals(R.drawable.widget_symbol_self_improvement, widgetSymbol("gibt_es_nicht"))
    }

    @Test
    fun `alle Widget-Drawables lassen sich laden`() {
        val kontext = ApplicationProvider.getApplicationContext<Context>()
        val ids = AKTIVITAET_SYMBOLE.map(::widgetSymbol) +
            listOf(R.drawable.widget_check_circle, R.drawable.widget_star, R.drawable.widget_haken)
        ids.forEach { id -> assertNotNull(ContextCompat.getDrawable(kontext, id), "Drawable $id") }
    }
}
