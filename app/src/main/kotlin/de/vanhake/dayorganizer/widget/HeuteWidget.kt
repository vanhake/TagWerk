package de.vanhake.dayorganizer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.android.EntryPointAccessors
import de.vanhake.dayorganizer.MainActivity
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.di.WidgetZugang
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFarben
import de.vanhake.dayorganizer.util.versuchen
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Zeigt die heute offenen Aufgaben und den Punktestand im Stil des Prototyps
 * (Block showWidget). Jeder Eintrag ist per clickable mit WidgetErledigenAktion
 * verknuepft: Antippen erledigt die Faelligkeit ueber denselben Use Case wie App
 * und Benachrichtigung.
 */
class HeuteWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_fehler) {

    // Exact statt Single: nur so kennt LocalSize die tatsaechliche Hoehe, und
    // WidgetMasse kann daraus die Zeilenzahl berechnen.
    override val sizeMode = SizeMode.Exact

    /**
     * Statt des Standard-Fehlerbilds ein tippbares: Glance zeigt sonst nur den Text aus
     * errorUiLayout. Der Tipp oeffnet die App, damit der Nutzer nicht vor einer toten
     * Flaeche steht. Ein Fehler beim Aufbau darf nicht erneut werfen.
     */
    override fun onCompositionError(
        context: Context,
        glanceId: GlanceId,
        appWidgetId: Int,
        throwable: Throwable,
    ) {
        Log.w("HeuteWidget", "Widget-Komposition fehlgeschlagen", throwable)
        versuchen {
            val ansicht = RemoteViews(context.packageName, R.layout.widget_fehler)
            val oeffnen = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            ansicht.setOnClickPendingIntent(R.id.widget_fehler_wurzel, oeffnen)
            AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, ansicht)
        }.onFailure { Log.w("HeuteWidget", "Fehlerbild konnte nicht gesetzt werden", it) }
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zugang = EntryPointAccessors.fromApplication(context, WidgetZugang::class.java)
        val uhr = zugang.uhr()
        val heute = LocalDate.now(uhr)

        // Nur der Startwert fuer collectAsState (Review-Fund K1): Glance ruft
        // provideGlance einmal pro Sitzung auf (~45s), waehrend eine Sitzung laeuft
        // liest provideContent unten reaktiv weiter. Ohne diesen einmaligen Vorablesevorgang
        // wuerde das erste Bild leer bzw. mit "0 Punkte" aufblitzen, bis der erste
        // Flow-Wert eintrifft. Fuer den Farbmodus gilt dasselbe: sonst blitzt ein
        // dunkles Widget beim Sitzungsstart kurz hell auf.
        // Startwert aus derselben Abfrage, die danach beobachtet wird. fuerZeitraum
        // waehlt dieselben Zeilen, sortiert aber anders; bei mehreren Zielen ohne
        // Uhrzeit sprang die Reihenfolge sonst zwischen erstem Bild und erster
        // Aktualisierung um, obwohl sich nichts geaendert hatte.
        val faelligkeitenAnfangswert = zugang.faelligkeiten().beobachteTag(heute).first()
        val aktivitaetenAnfangswert = zugang.aktivitaeten().alleAktiven()
        val kontostandAnfangswert = zugang.buchungen().kontostand()
        val modusAnfangswert = zugang.einstellungen().lesen().themeModus

        // Takt-Alarm fuer "ueberfaellig" und Mitternacht; ein Fehler darf das Zeichnen nicht stoppen.
        versuchen { zugang.widgetWecker().planen() }

        provideContent {
            val res = LocalContext.current.resources

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
            // distinctUntilChanged: andere Einstellungen (z.B. Ruhetage) sollen keine
            // Recomposition mit unveraendertem Farbsatz ausloesen.
            val modus by zugang.einstellungen().beobachten()
                .map { it.themeModus }
                .distinctUntilChanged()
                .collectAsState(initial = modusAnfangswert)

            val zustand = WidgetZusammenstellung.aus(
                faelligkeiten = faelligkeiten,
                aktivitaeten = aktivitaeten,
                kontostand = kontostand,
                jetzt = LocalDateTime.now(uhr),
            )
            val farben = widgetFarben(modus)
            val zeilen = WidgetMasse.passendeZeilen(
                hoeheDp = LocalSize.current.height.value,
                offen = zustand.offen.size,
                schriftFaktor = res.configuration.fontScale,
            )

            // fillMaxSize statt fillMaxWidth: sonst schwimmt die Karte mittig in der
            // Widget-Flaeche, mit leerem Rand darueber und darunter -- so auf dem
            // Emulator zu sehen. Der Systemradius statt 28 dp, weil der Launcher
            // ohnehin auf diesen Radius zuschneidet.
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .appWidgetBackground()
                    .cornerRadius(android.R.dimen.system_app_widget_background_radius)
                    .background(ColorProvider(farben.surface))
                    .padding(16.dp),
            ) {
                Kopf(zustand, farben, res)

                zustand.offen.take(zeilen).forEach { eintrag ->
                    Spacer(GlanceModifier.height(10.dp))
                    Zeile(eintrag, farben, res)
                }

                if (zeilen < zustand.offen.size) {
                    Spacer(GlanceModifier.height(10.dp))
                    Text(
                        text = res.getString(R.string.widget_weitere, zustand.offen.size - zeilen),
                        modifier = GlanceModifier.padding(horizontal = 4.dp),
                        style = TextStyle(
                            color = ColorProvider(farben.ink2),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                    )
                }

                // Bei grosser Schrift und kleiner Hoehe passt der Hinweis nicht mehr unter den
                // Kopf; dann lieber weglassen als am Rand abschneiden.
                val leerhinweisPasst = WidgetMasse.leerhinweisPasst(
                    hoeheDp = LocalSize.current.height.value,
                    schriftFaktor = res.configuration.fontScale,
                )
                when (if (leerhinweisPasst) zustand.leer else null) {
                    WidgetLeer.ALLES_ERLEDIGT -> {
                        Spacer(GlanceModifier.height(10.dp))
                        Text(
                            text = res.getString(R.string.widget_alles_erledigt),
                            modifier = GlanceModifier.padding(horizontal = 4.dp, vertical = 8.dp),
                            style = TextStyle(
                                color = ColorProvider(farben.green),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            // Einzeilig halten, damit bei grosser Schrift nichts mittendrin
                            // abgeschnitten wird; das Hoehenbudget prueft leerhinweisPasst.
                            maxLines = 1,
                        )
                    }
                    WidgetLeer.NICHTS_GEPLANT -> {
                        Spacer(GlanceModifier.height(10.dp))
                        Text(
                            text = res.getString(R.string.widget_nichts_geplant),
                            modifier = GlanceModifier.padding(horizontal = 4.dp),
                            style = TextStyle(
                                color = ColorProvider(farben.ink2),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = 1,
                        )
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun Kopf(zustand: WidgetZustand, farben: DayOrganizerFarben, res: Resources) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(28.dp)
                .cornerRadius(9.dp)
                .background(ColorProvider(farben.accent)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_check_circle),
                contentDescription = null,
                modifier = GlanceModifier.size(18.dp),
                colorFilter = ColorFilter.tint(ColorProvider(farben.onAccent)),
            )
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(
            text = res.getString(R.string.widget_titel),
            style = TextStyle(
                color = ColorProvider(farben.ink),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        // Ohne Faelligkeiten waere "0/0" nur Rauschen; der Leerhinweis sagt es schon.
        if (zustand.gesamt > 0) {
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = res.getString(R.string.widget_ziel_fortschritt, zustand.erledigt, zustand.gesamt),
                style = TextStyle(
                    color = ColorProvider(farben.ink2),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        // Radius 12 dp = halbe Hoehe (16 dp Stern + 2 x 4 dp): Glance kennt kein
        // Pillenmass, und ein Radius 999 wird vom Outline-Zuschnitt nicht verlaesslich
        // auf die halbe Hoehe begrenzt.
        // Der Vorlesetext liegt auf der ganzen Pille, sonst liest TalkBack die Punkte
        // doppelt (Stern und Zahl).
        val punkteBeschriftung = res.getString(R.string.punktestand, zustand.kontostand)
        Row(
            modifier = GlanceModifier
                .cornerRadius(12.dp)
                .background(ColorProvider(farben.goldSoft))
                .padding(start = 8.dp, top = 4.dp, end = 10.dp, bottom = 4.dp)
                .semantics { contentDescription = punkteBeschriftung },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_star),
                contentDescription = null,
                modifier = GlanceModifier.size(16.dp),
                colorFilter = ColorFilter.tint(ColorProvider(farben.goldInk)),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = zustand.kontostand.toString(),
                style = TextStyle(
                    color = ColorProvider(farben.goldInk),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Zeile(eintrag: WidgetEintrag, farben: DayOrganizerFarben, res: Resources) {
    val beschriftung = res.getString(R.string.widget_abhaken, eintrag.name)
    val hervorhebung = if (eintrag.ueberfaellig) farben.coral else farben.accent
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(16.dp)
            .background(ColorProvider(farben.surface2))
            .padding(start = 10.dp, top = 8.dp, end = 8.dp, bottom = 8.dp)
            .semantics { contentDescription = beschriftung }
            .clickable(
                actionRunCallback<WidgetErledigenAktion>(
                    actionParametersOf(
                        WidgetErledigenAktion.FAELLIGKEIT_ID to eintrag.faelligkeitId,
                    ),
                ),
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(widgetSymbol(eintrag.symbol)),
            contentDescription = null,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(ColorProvider(hervorhebung)),
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = eintrag.name,
                style = TextStyle(
                    color = ColorProvider(farben.ink),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Text(
                text = widgetUntertitel(eintrag, res),
                style = TextStyle(
                    color = ColorProvider(if (eintrag.ueberfaellig) farben.coral else farben.ink2),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(10.dp))
        Image(
            provider = ImageProvider(R.drawable.widget_haken),
            contentDescription = null,
            modifier = GlanceModifier.size(30.dp),
            colorFilter = ColorFilter.tint(ColorProvider(farben.accent)),
        )
    }
}
