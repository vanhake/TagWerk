package de.vanhake.dayorganizer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.vanhake.dayorganizer.platform.notification.Kanaele
import de.vanhake.dayorganizer.platform.work.WaechterPlanung
import javax.inject.Inject

@HiltAndroidApp
class DayOrganizerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var waechterPlanung: WaechterPlanung

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Kanaele.anlegen(this)
        waechterPlanung.taeglichPlanen()
        waechterPlanung.sofortAusfuehren()
    }
}
