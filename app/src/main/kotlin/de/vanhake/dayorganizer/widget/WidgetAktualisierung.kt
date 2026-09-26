package de.vanhake.dayorganizer.widget

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetAktualisierung @Inject constructor(
    @ApplicationContext private val kontext: Context,
) {
    suspend fun anstossen() {
        // Ist kein Widget platziert, wirft updateAll nicht: es iteriert nur ueber eine
        // leere Liste von Glance-Ids. runCatching faengt hier echte Fehlschlaege ab,
        // damit ein misslungenes Widget-Update nie den aufrufenden Schreibpfad mitreisst.
        runCatching {
            HeuteWidget().updateAll(kontext)
        }.onFailure { fehler ->
            if (fehler is CancellationException) throw fehler
            Log.w(TAG, "Widget-Aktualisierung fehlgeschlagen", fehler)
        }
    }

    private companion object {
        const val TAG = "WidgetAktualisierung"
    }
}
