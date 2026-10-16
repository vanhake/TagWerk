package de.vanhake.dayorganizer.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.EntryPointAccessors
import de.vanhake.dayorganizer.di.WidgetZugang

/**
 * Schreibt ueber denselben Use Case wie App und Benachrichtigung. Ein zweiter
 * Schreibpfad wuerde die Idempotenz des Abhakens unterlaufen. Der Ablauf selbst
 * (Tagespruefung, Fehlerschutz, Nachbereitung) liegt in [WidgetAbhaken].
 */
class WidgetErledigenAktion : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val faelligkeitId = parameters[FAELLIGKEIT_ID] ?: return
        val zugang = EntryPointAccessors.fromApplication(context, WidgetZugang::class.java)

        WidgetAbhaken(
            faelligkeitLesen = { zugang.faelligkeiten().nachId(it) },
            erledigen = { zugang.erledigen().mitProtokoll(it) },
            nachbereiten = { id ->
                // Wie in HeuteViewModel.erledigen: ohne verstecke() bliebe die Erinnerung in
                // der Statusleiste stehen, obwohl die Aufgabe erledigt ist. Genau diese Luecke
                // hat das Plan-1-Review fuer die App schon einmal geschlossen (Fix 9).
                zugang.anzeige().verstecke(id)
                zugang.alarmPlaner().neuPlanen()
            },
            widgetAnstossen = { zugang.widgetAktualisierung().anstossen() },
            uhr = zugang.uhr(),
        )(faelligkeitId)
    }

    companion object {
        val FAELLIGKEIT_ID = ActionParameters.Key<Long>("faelligkeitId")
    }
}
