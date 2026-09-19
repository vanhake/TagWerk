package de.vanhake.dayorganizer.platform.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaechterPlanung @Inject constructor(
    @ApplicationContext private val kontext: Context,
    private val uhr: Clock,
) {

    /** Taeglich um drei Uhr nachts, wenn das Geraet es zulaesst. */
    fun taeglichPlanen() {
        val jetzt = LocalDateTime.now(uhr)
        val naechsterLauf = jetzt.toLocalDate().atTime(LAUFZEIT)
            .let { if (it.isAfter(jetzt)) it else it.plusDays(1) }

        val anfrage = PeriodicWorkRequestBuilder<WaechterWorker>(Duration.ofDays(1))
            .setInitialDelay(Duration.between(jetzt, naechsterLauf))
            .build()

        WorkManager.getInstance(kontext).enqueueUniquePeriodicWork(
            NAME_TAEGLICH,
            ExistingPeriodicWorkPolicy.UPDATE,
            anfrage,
        )
    }

    /** Nach Neustart, App-Update und beim ersten App-Start. */
    fun sofortAusfuehren() {
        WorkManager.getInstance(kontext).enqueueUniqueWork(
            NAME_SOFORT,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WaechterWorker>().build(),
        )
    }

    private companion object {
        val LAUFZEIT: LocalTime = LocalTime.of(3, 0)
        const val NAME_TAEGLICH = "waechter_taeglich"
        const val NAME_SOFORT = "waechter_sofort"
    }
}
