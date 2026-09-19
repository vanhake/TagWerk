package de.vanhake.dayorganizer.platform.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.domainuse.ErinnerungSpaeter
import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassen
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigen
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
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

    override fun onReceive(kontext: Context, intent: Intent) {
        val aktion = Benachrichtigungsaktionen.aus(
            intent.action,
            intent.getLongExtra(Benachrichtigungsaktionen.EXTRA_FAELLIGKEIT_ID, 0),
        ) ?: return

        val fertig = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (aktion) {
                    is Benachrichtigungsaktion.Erledigt -> erledigen(aktion.faelligkeitId)
                    is Benachrichtigungsaktion.Ausgelassen -> auslassen(aktion.faelligkeitId)
                    is Benachrichtigungsaktion.Spaeter -> spaeter(aktion.faelligkeitId)
                }
                notifier.verstecke(aktion.faelligkeitId)
                alarmPlaner.neuPlanen()
            } finally {
                fertig.finish()
            }
        }
    }
}
