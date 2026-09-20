package de.vanhake.dayorganizer.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import de.vanhake.dayorganizer.R

object Kanaele {
    const val KANAL_ERINNERUNGEN = "erinnerungen"

    fun anlegen(kontext: Context) {
        val kanal = NotificationChannel(
            KANAL_ERINNERUNGEN,
            kontext.getString(R.string.kanal_erinnerungen_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = kontext.getString(R.string.kanal_erinnerungen_beschreibung)
            enableVibration(true)
        }
        kontext.getSystemService<NotificationManager>()?.createNotificationChannel(kanal)
    }
}
