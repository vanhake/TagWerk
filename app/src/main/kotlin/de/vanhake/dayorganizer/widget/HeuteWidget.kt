package de.vanhake.dayorganizer.widget

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
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
 * Zeigt die heute offenen Aufgaben und den Punktestand. Jeder Eintrag ist per
 * clickable (Task 4) mit WidgetErledigenAktion verknuepft: Antippen erledigt die
 * Faelligkeit ueber denselben Use Case wie App und Benachrichtigung.
 */
class HeuteWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zugang = EntryPointAccessors.fromApplication(context, WidgetZugang::class.java)
        val uhr = zugang.uhr()
        val heute = LocalDate.now(uhr)

        // Nur der Startwert fuer collectAsState (Review-Fund K1): Glance ruft
        // provideGlance einmal pro Sitzung auf (~45s), waehrend eine Sitzung laeuft
        // liest provideContent unten reaktiv weiter. Ohne diesen einmaligen Vorablesevorgang
        // wuerde das erste Bild leer bzw. mit "0 Punkte" aufblitzen, bis der erste
        // Flow-Wert eintrifft.
        val faelligkeitenAnfangswert = zugang.faelligkeiten().fuerZeitraum(heute, heute)
        val aktivitaetenAnfangswert = zugang.aktivitaeten().alleAktiven()
        val kontostandAnfangswert = zugang.buchungen().kontostand()

        provideContent {
            val res = LocalContext.current

            // Reaktiv statt einmalig eingefangen (Review-Fund K1): ein updateAll
            // waehrend einer laufenden Glance-Sitzung loest nur eine Recomposition
            // aus, keinen erneuten Aufruf von provideGlance -- ohne collectAsState
            // wuerde das Widget bis zur naechsten Sitzung den alten Zustand zeigen.
            // beobachteTag hat dasselbe Praedikat wie fuerZeitraum(heute, heute)
            // (siehe FaelligkeitDao). Die Flows laufen und werden mit der Sitzung
            // abgebrochen -- das ist gewollt.
            val faelligkeiten by zugang.faelligkeiten().beobachteTag(heute)
                .collectAsState(initial = faelligkeitenAnfangswert)
            val aktivitaeten by zugang.aktivitaeten().beobachteAktive()
                .collectAsState(initial = aktivitaetenAnfangswert)
            val kontostand by zugang.buchungen().beobachteKontostand()
                .collectAsState(initial = kontostandAnfangswert)

            val zustand = WidgetZusammenstellung.aus(
                faelligkeiten = faelligkeiten,
                aktivitaeten = aktivitaeten,
                kontostand = kontostand,
                jetzt = LocalDateTime.now(uhr),
                hoechstens = 5,
            )

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
                            .padding(vertical = 4.dp)
                            .clickable(
                                actionRunCallback<WidgetErledigenAktion>(
                                    actionParametersOf(
                                        WidgetErledigenAktion.FAELLIGKEIT_ID to eintrag.faelligkeitId,
                                    ),
                                ),
                            ),
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
