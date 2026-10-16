package de.vanhake.dayorganizer.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import de.vanhake.dayorganizer.util.versuchen
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetAktualisierung @Inject constructor(
    @ApplicationContext private val kontext: Context,
) {
    suspend fun anstossen() {
        // Ist kein Widget platziert, wirft updateAll nicht: es iteriert nur ueber eine
        // leere Liste von Glance-Ids. versuchen faengt hier echte Fehlschlaege ab, damit ein
        // misslungenes Widget-Update nie den aufrufenden Schreibpfad mitreisst; einen Abbruch
        // reicht es durch.
        versuchen {
            HeuteWidget().updateAll(kontext)
        }.onFailure { fehler ->
            Log.w(TAG, "Widget-Aktualisierung fehlgeschlagen", fehler)
        }
    }

    private companion object {
        const val TAG = "WidgetAktualisierung"
    }
}
