package de.vanhake.dayorganizer.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dagger.hilt.android.EntryPointAccessors
import de.vanhake.dayorganizer.di.WidgetZugang

class HeuteWidgetEmpfaenger : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeuteWidget()

    // Das letzte Widget wurde entfernt: der Takt-Alarm hat nichts mehr zu zeichnen.
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        EntryPointAccessors.fromApplication(context, WidgetZugang::class.java)
            .widgetWecker()
            .abbestellen()
    }
}
