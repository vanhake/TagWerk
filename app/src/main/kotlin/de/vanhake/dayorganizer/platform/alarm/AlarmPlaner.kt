package de.vanhake.dayorganizer.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import de.vanhake.dayorganizer.data.repository.ErinnerungRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Haelt genau einen exakten Alarm auf den naechsten anstehenden Erinnerungstermin.
 * Jede Aenderung an Aktivitaeten, Faelligkeiten oder Erinnerungen ruft [neuPlanen].
 */
@Singleton
class AlarmPlaner @Inject constructor(
    @ApplicationContext private val kontext: Context,
    private val erinnerungen: ErinnerungRepository,
    private val uhr: Clock,
) {

    suspend fun neuPlanen() {
        val manager = kontext.getSystemService<AlarmManager>() ?: return
        val naechste = erinnerungen.naechsteAb(LocalDateTime.now(uhr))

        if (naechste == null) {
            manager.cancel(alarmIntent())
            return
        }

        val zeitpunktMillis = alsEpochMillis(naechste.zeitpunkt, ZoneId.systemDefault())

        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                zeitpunktMillis,
                alarmIntent(),
            )
        } else {
            // Ohne die Berechtigung bleibt nur der ungenaue Alarm. Der Banner auf
            // dem Heute-Screen weist darauf hin, dass Erinnerungen verspaetet kommen.
            Log.w("AlarmPlaner", "Exakte Alarme nicht erlaubt, weiche auf ungenauen Alarm aus")
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, zeitpunktMillis, alarmIntent())
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        kontext,
        ALARM_ANFRAGE,
        Intent(kontext, ErinnerungsEmpfaenger::class.java).apply {
            action = ErinnerungsEmpfaenger.ACTION_ERINNERUNG_FAELLIG
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val ALARM_ANFRAGE = 1001
    }
}

/**
 * Ortszeit zu Instant. Faellt die Ortszeit durch die Sommerzeitumstellung aus,
 * nimmt die Zone den naechsten gueltigen Zeitpunkt; gibt es sie doppelt, den
 * ersten. Beides ist besser als ein Alarm, der gar nicht feuert.
 */
internal fun alsEpochMillis(zeitpunkt: LocalDateTime, zone: ZoneId): Long =
    zeitpunkt.atZone(zone).toInstant().toEpochMilli()
