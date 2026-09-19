package de.vanhake.dayorganizer.platform.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.vanhake.dayorganizer.domainuse.WaechterAblauf

@HiltWorker
class WaechterWorker @AssistedInject constructor(
    @Assisted kontext: Context,
    @Assisted parameter: WorkerParameters,
    private val ablauf: WaechterAblauf,
) : CoroutineWorker(kontext, parameter) {

    override suspend fun doWork(): Result = runCatching { ablauf() }
        .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
}
