package de.vanhake.dayorganizer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.vanhake.dayorganizer.platform.notification.Kanaele

@HiltAndroidApp
class DayOrganizerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Kanaele.anlegen(this)
    }
}
