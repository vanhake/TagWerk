package de.vanhake.dayorganizer.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.util.versuchen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nicht exportiert: nur der eigene Alarm von [WidgetWecker] loest ihn aus. */
@AndroidEntryPoint
class WidgetWeckerEmpfaenger : BroadcastReceiver() {

    @Inject lateinit var widgetAktualisierung: WidgetAktualisierung
    @Inject lateinit var wecker: WidgetWecker

    override fun onReceive(kontext: Context, intent: Intent) {
        if (intent.action != WidgetWecker.ACTION_WIDGET_TAKT) return
        val fertig = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Getrennt, damit ein Fehler beim Zeichnen den naechsten Alarm nicht verhindert.
                versuchen { widgetAktualisierung.anstossen() }
                versuchen { wecker.planen() }
            } finally {
                fertig.finish()
            }
        }
    }
}
