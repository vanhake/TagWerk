package de.vanhake.dayorganizer.platform.permission

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.ActivityNotFoundException
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

// BatteryLife: Lint warnt vor dem Ausnahme-Dialog wegen der Play-Richtlinie; die App
// wird nur als APK verteilt (siehe Manifest).
@SuppressLint("BatteryLife")
fun Hinweis.einstellungsIntent(kontext: Context): Intent = when (this) {
    Hinweis.BENACHRICHTIGUNGEN_FEHLEN -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, kontext.packageName)

    Hinweis.EXAKTE_ALARME_FEHLEN -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.fromParts("package", kontext.packageName, null))

    // Direkt der System-Dialog statt der Einstellungsseite: Seit Android 14 zeigt die
    // App-Akkuseite nur noch den Schalter "Hintergrundnutzung zulassen", und der steht
    // auch bei "Optimiert" auf an -- isIgnoringBatteryOptimizations wird aber erst bei
    // "Uneingeschraenkt" wahr. Viele Hersteller (z.B. Motorola) verstecken diese Wahl,
    // der Banner liesse sich dort also nie wegbekommen. Der Dialog setzt genau den
    // Ausnahmezustand, den stand() prueft.
    Hinweis.AKKUOPTIMIERUNG_AKTIV -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
        .setData(Uri.fromParts("package", kontext.packageName, null))

    // Ein haengender Waechter ist meistens Hersteller-Akkuoptimierung, die den
    // Hintergrundjob abwuergt -- dieselbe Einstellung ehrlich anzubieten ist die
    // naheliegendste Massnahme, auch wenn WAECHTER_HAENGT selbst vager ist.
    Hinweis.WAECHTER_HAENGT -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}

/**
 * Oeffnet die passende Systemeinstellung. Faellt auf die allgemeine Liste der
 * Akkuoptimierung zurueck, falls ein Hersteller den Ausnahme-Dialog entfernt hat.
 */
fun Hinweis.einstellungOeffnen(kontext: Context) {
    try {
        ContextCompat.startActivity(kontext, einstellungsIntent(kontext), null)
    } catch (fehlt: ActivityNotFoundException) {
        if (this != Hinweis.AKKUOPTIMIERUNG_AKTIV) throw fehlt
        ContextCompat.startActivity(
            kontext,
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            null,
        )
    }
}
