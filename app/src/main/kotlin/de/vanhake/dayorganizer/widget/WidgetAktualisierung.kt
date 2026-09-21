package de.vanhake.dayorganizer.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetAktualisierung @Inject constructor(
    @ApplicationContext private val kontext: Context,
) {
    suspend fun anstossen() {
        runCatching { HeuteWidget().updateAll(kontext) }
        // Ist kein Widget platziert, ist das kein Fehler, sondern der Normalfall.
    }
}
