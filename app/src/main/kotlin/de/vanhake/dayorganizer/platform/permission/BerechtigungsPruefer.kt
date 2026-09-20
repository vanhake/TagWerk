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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BerechtigungsPruefer @Inject constructor(
    @ApplicationContext private val kontext: Context,
) {
    fun stand(): BerechtigungsStand = BerechtigungsStand(
        // Wenn ein Service nicht verfuegbar ist: lieber warnen als schweigen.
        // Eine fehlende Erinnerung ist schlimmer als ein weggetipptes Banner.
        benachrichtigungenErlaubt = ContextCompat.checkSelfPermission(
            kontext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED,
        exakteAlarmeErlaubt = kontext.getSystemService<AlarmManager>()
            ?.canScheduleExactAlarms() ?: false,
        akkuOptimierungAktiv = kontext.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(kontext.packageName)?.not() ?: true,
    )

    /**
     * Wann diese Installation entstanden ist. Dient als Messpunkt fuer die
     * Waechter-Frische, solange der Waechter noch nie erfolgreich gelaufen ist --
     * sonst haette eine frische Installation sofort einen haengenden Waechter.
     */
    fun installiertAm(): LocalDateTime = Instant
        .ofEpochMilli(
            kontext.packageManager.getPackageInfo(kontext.packageName, 0).firstInstallTime,
        )
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
}

fun Hinweis.einstellungsIntent(kontext: Context): Intent = when (this) {
    Hinweis.BENACHRICHTIGUNGEN_FEHLEN -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, kontext.packageName)

    Hinweis.EXAKTE_ALARME_FEHLEN -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.fromParts("package", kontext.packageName, null))

    Hinweis.AKKUOPTIMIERUNG_AKTIV -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    // Ein haengender Waechter ist meistens Hersteller-Akkuoptimierung, die den
    // Hintergrundjob abwuergt -- dieselbe Einstellung ehrlich anzubieten ist die
    // naheliegendste Massnahme, auch wenn WAECHTER_HAENGT selbst vager ist.
    Hinweis.WAECHTER_HAENGT -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
