package de.vanhake.dayorganizer.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.di.WidgetZugang
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Reine Anzeige (Task 3): zeigt die heute offenen Aufgaben und den Punktestand.
 * Das Abhaken aus dem Widget -- inklusive der clickable-Aktion samt
 * WidgetErledigenAktion -- kommt erst mit Task 4 dazu.
 */
class HeuteWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zugang = EntryPointAccessors.fromApplication(context, WidgetZugang::class.java)
        val uhr = zugang.uhr()
        val heute = LocalDate.now(uhr)

        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = zugang.faelligkeiten().fuerZeitraum(heute, heute),
            aktivitaeten = zugang.aktivitaeten().alleAktiven(),
            kontostand = zugang.buchungen().kontostand(),
            jetzt = LocalDateTime.now(uhr),
            hoechstens = 5,
        )

        provideContent {
            val res = LocalContext.current

            // fillMaxSize statt fillMaxWidth: sonst schwimmt die Karte mittig in der
            // Widget-Flaeche, mit leerem Rand darueber und darunter -- so auf dem
            // Emulator zu sehen.
            Column(GlanceModifier.fillMaxSize().padding(12.dp).background(Color(0xFFF5F5F5))) {
                Text(
                    res.getString(R.string.punktestand, zustand.kontostand),
                    style = TextStyle(color = ColorProvider(Color.Black)),
                )

                zustand.eintraege.forEach { eintrag ->
                    Row(
                        GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            text = listOfNotNull(eintrag.zeitText, eintrag.name).joinToString("  "),
                            style = TextStyle(
                                color = ColorProvider(
                                    if (eintrag.ueberfaellig) Color(0xFFB00020) else Color.Black,
                                ),
                            ),
                        )
                    }
                }

                if (zustand.weitere > 0) {
                    Text(
                        res.getString(R.string.widget_weitere, zustand.weitere),
                        style = TextStyle(color = ColorProvider(Color.DarkGray)),
                    )
                }
            }
        }
    }
}
