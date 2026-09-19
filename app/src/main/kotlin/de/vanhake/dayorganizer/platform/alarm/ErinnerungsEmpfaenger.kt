package de.vanhake.dayorganizer.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.domainuse.ErinnerungAusloesen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ErinnerungsEmpfaenger : BroadcastReceiver() {

    @Inject lateinit var ausloesen: ErinnerungAusloesen
    @Inject lateinit var alarmPlaner: AlarmPlaner

    override fun onReceive(kontext: Context, intent: Intent) {
        if (intent.action != ACTION_ERINNERUNG_FAELLIG) return
        val fertig = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ausloesen()
                alarmPlaner.neuPlanen()
            } finally {
                fertig.finish()
            }
        }
    }

    companion object {
        const val ACTION_ERINNERUNG_FAELLIG = "de.vanhake.dayorganizer.ERINNERUNG_FAELLIG"
    }
}
