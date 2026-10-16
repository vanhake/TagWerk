package de.vanhake.dayorganizer.platform.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.vanhake.dayorganizer.domainuse.WaechterAblauf
import de.vanhake.dayorganizer.util.versuchen

@HiltWorker
class WaechterWorker @AssistedInject constructor(
    @Assisted kontext: Context,
    @Assisted parameter: WorkerParameters,
    private val ablauf: WaechterAblauf,
) : CoroutineWorker(kontext, parameter) {

    override suspend fun doWork(): Result = versuchen { ablauf() }
        .fold(
            onSuccess = { Result.success() },
            onFailure = { fehler ->
                // Naechtlicher Job: die periodische Planung ruft ihn heute Nacht ohnehin
                // erneut auf. Ein persistenter Fehler (z.B. DB-Ausnahme oder eine wegen
                // entzogener Exact-Alarm-Berechtigung werfende AlarmPlaner.neuPlanen())
                // wuerde sonst mit exponentiellem Backoff ohne Obergrenze endlos
                // wiederholt werden und nur Akku verbrennen, ohne dass es dem Nutzer
                // sichtbar waere. Nach ein paar Versuchen leise aufgeben ist hier
                // richtiger als lautes Scheitern.
                if (runAttemptCount < MAX_VERSUCHE - 1) {
                    Result.retry()
                } else {
                    Log.w(TAG, "Waechter gibt nach $MAX_VERSUCHE Versuchen auf", fehler)
                    Result.success()
                }
            },
        )

    private companion object {
        const val TAG = "WaechterWorker"
        const val MAX_VERSUCHE = 3
    }
}
