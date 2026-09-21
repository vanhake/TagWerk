package de.vanhake.dayorganizer.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.EntryPointAccessors
import de.vanhake.dayorganizer.di.WidgetZugang

/**
 * Schreibt ueber denselben Use Case wie App und Benachrichtigung. Ein zweiter
 * Schreibpfad wuerde die Idempotenz des Abhakens unterlaufen.
 */
class WidgetErledigenAktion : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val faelligkeitId = parameters[FAELLIGKEIT_ID] ?: return
        val zugang = EntryPointAccessors.fromApplication(context, WidgetZugang::class.java)

        zugang.erledigen().invoke(faelligkeitId)
        // Wie in HeuteViewModel.erledigen: ohne verstecke() bliebe die Erinnerung in
        // der Statusleiste stehen, obwohl die Aufgabe erledigt ist. Genau diese Luecke
        // hat das Plan-1-Review fuer die App schon einmal geschlossen (Fix 9).
        zugang.anzeige().verstecke(faelligkeitId)
        zugang.alarmPlaner().neuPlanen()
        HeuteWidget().updateAll(context)
    }

    companion object {
        val FAELLIGKEIT_ID = ActionParameters.Key<Long>("faelligkeitId")
    }
}
