package de.vanhake.dayorganizer.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ErinnerungsEmpfaenger : BroadcastReceiver() {

    override fun onReceive(kontext: Context, intent: Intent) {
        // Vollstaendige Verarbeitung folgt in Task 15.
    }

    companion object {
        const val ACTION_ERINNERUNG_FAELLIG = "de.vanhake.dayorganizer.ERINNERUNG_FAELLIG"
    }
}
