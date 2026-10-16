package de.vanhake.dayorganizer.widget

import android.content.Context
import android.view.LayoutInflater
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.theme.HellFarben
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetRessourcenTest {

    private val kontext = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `die Vorschaufarben stimmen mit HellFarben ueberein`() {
        val erwartet = mapOf(
            R.color.widget_vorschau_surface to HellFarben.surface,
            R.color.widget_vorschau_surface2 to HellFarben.surface2,
            R.color.widget_vorschau_ink to HellFarben.ink,
            R.color.widget_vorschau_ink2 to HellFarben.ink2,
            R.color.widget_vorschau_accent to HellFarben.accent,
            R.color.widget_vorschau_on_accent to HellFarben.onAccent,
            R.color.widget_vorschau_coral to HellFarben.coral,
            R.color.widget_vorschau_gold_soft to HellFarben.goldSoft,
            R.color.widget_vorschau_gold_ink to HellFarben.goldInk,
        )
        erwartet.forEach { (id, farbe) ->
            assertEquals(farbe.toArgb(), kontext.getColor(id), kontext.resources.getResourceEntryName(id))
        }
    }

    @Test
    fun `Vorschau- und Fehlerlayout lassen sich aufblasen`() {
        val inflater = LayoutInflater.from(kontext)
        assertNotNull(inflater.inflate(R.layout.widget_vorschau, null))
        assertNotNull(inflater.inflate(R.layout.widget_fehler, null))
    }
}
