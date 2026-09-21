package de.vanhake.dayorganizer.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class HeuteWidgetEmpfaenger : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeuteWidget()
}
