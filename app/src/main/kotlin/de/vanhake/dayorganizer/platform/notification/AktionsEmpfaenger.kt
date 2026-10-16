package de.vanhake.dayorganizer.platform.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.domainuse.ErinnerungSpaeter
import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassen
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigen
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import de.vanhake.dayorganizer.util.versuchen
import de.vanhake.dayorganizer.widget.WidgetAktualisierung
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AktionsEmpfaenger : BroadcastReceiver() {

    @Inject lateinit var erledigen: FaelligkeitErledigen
    @Inject lateinit var auslassen: FaelligkeitAuslassen
    @Inject lateinit var spaeter: ErinnerungSpaeter
    @Inject lateinit var notifier: ErinnerungsNotifier
    @Inject lateinit var alarmPlaner: AlarmPlaner
    @Inject lateinit var widgetAktualisierung: WidgetAktualisierung

    override fun onReceive(kontext: Context, intent: Intent) {
        val aktion = Benachrichtigungsaktionen.aus(
            intent.action,
            intent.getLongExtra(Benachrichtigungsaktionen.EXTRA_FAELLIGKEIT_ID, 0),
        ) ?: return

        val fertig = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val geklappt = versuchen {
                    when (aktion) {
                        is Benachrichtigungsaktion.Erledigt -> erledigen(aktion.faelligkeitId)
                        is Benachrichtigungsaktion.Ausgelassen -> auslassen(aktion.faelligkeitId)
                        is Benachrichtigungsaktion.Spaeter -> spaeter(aktion.faelligkeitId)
                    }
                }.onFailure { Log.w(TAG, "Benachrichtigungsaktion fehlgeschlagen", it) }.isSuccess
                // Bei einem Fehlschlag bleibt die Benachrichtigung stehen: der Nutzer sieht,
                // dass nichts passiert ist, und kann es erneut versuchen. Alarm und Widget
                // werden trotzdem angestossen, damit der Rest aktuell bleibt.
                if (geklappt) notifier.verstecke(aktion.faelligkeitId)
                // neuPlanen() liest die Datenbank: derselbe E/A-Fehler, an dem die Aktion
                // gescheitert ist, trifft sehr wahrscheinlich auch hier -- ungefangen risse
                // er den Prozess aus einem Benachrichtigungstipp heraus mit.
                versuchen { alarmPlaner.neuPlanen() }
                    .onFailure { Log.w(TAG, "Neuplanen der Alarme fehlgeschlagen", it) }
                widgetAktualisierung.anstossen()
            } finally {
                fertig.finish()
            }
        }
    }

    private companion object {
        const val TAG = "AktionsEmpfaenger"
    }
}
