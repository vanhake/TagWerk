package de.vanhake.dayorganizer.platform.permission

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BerechtigungsPruefer @Inject constructor(
    @ApplicationContext private val kontext: Context,
) {
    fun stand(): BerechtigungsStand = BerechtigungsStand(
        // Wenn ein Service nicht verfuegbar ist: lieber warnen als schweigen.
        // Eine fehlende Erinnerung ist schlimmer als ein abgelentes Banner.
        benachrichtigungenErlaubt = ContextCompat.checkSelfPermission(
            kontext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED,
        exakteAlarmeErlaubt = kontext.getSystemService<AlarmManager>()
            ?.canScheduleExactAlarms() ?: false,
        akkuOptimierungAktiv = kontext.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(kontext.packageName)?.not() ?: true,
    )
}

fun Hinweis.einstellungsIntent(kontext: Context): Intent = when (this) {
    Hinweis.BENACHRICHTIGUNGEN_FEHLEN -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, kontext.packageName)

    Hinweis.EXAKTE_ALARME_FEHLEN -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.fromParts("package", kontext.packageName, null))

    Hinweis.AKKUOPTIMIERUNG_AKTIV -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
