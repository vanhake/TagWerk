package de.vanhake.dayorganizer.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.platform.alarm.alsEpochMillis
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Haelt einen ungenauen Alarm auf den naechsten Umschaltpunkt des Widgets (Uhrzeit eines
 * offenen Eintrags, sonst Mitternacht). Ohne ihn zeichnet sich das Widget nur bei
 * Datenaenderungen neu, ein Eintrag wird also nie von selbst "ueberfaellig".
 */
@Singleton
class WidgetWecker @Inject constructor(
    @ApplicationContext private val kontext: Context,
    private val faelligkeiten: FaelligkeitRepository,
    private val uhr: Clock,
) {

    /** Stellt den Alarm auf naechsterUmschaltpunkt + NACHLAUF_MS; ersetzt einen frueheren. */
    suspend fun planen() {
        val manager = kontext.getSystemService<AlarmManager>() ?: return
        // Dieselbe Abfrage, die das Widget rendert.
        val heute = faelligkeiten.beobachteTag(LocalDate.now(uhr)).first()
        val punkt = WidgetTakt.naechsterUmschaltpunkt(heute, LocalDateTime.now(uhr))
        // Der Nachlauf stellt sicher, dass beim Zeichnen die Uhrzeit sicher ueberschritten ist.
        // RTC statt RTC_WAKEUP: bei ausgeschaltetem Bildschirm muss nichts neu gezeichnet werden.
        manager.setWindow(
            AlarmManager.RTC,
            alsEpochMillis(punkt, ZoneId.systemDefault()) + NACHLAUF_MS,
            FENSTER_MS,
            alarmIntent(),
        )
    }

    fun abbestellen() {
        val manager = kontext.getSystemService<AlarmManager>() ?: return
        manager.cancel(alarmIntent())
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        kontext,
        ALARM_ANFRAGE,
        Intent(kontext, WidgetWeckerEmpfaenger::class.java).apply {
            action = ACTION_WIDGET_TAKT
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_WIDGET_TAKT = "de.vanhake.dayorganizer.WIDGET_TAKT"
        const val NACHLAUF_MS = 1_000L
        const val FENSTER_MS = 60_000L
        private const val ALARM_ANFRAGE = 2001
    }
}
