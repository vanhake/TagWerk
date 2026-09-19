package de.vanhake.dayorganizer.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.platform.work.WaechterPlanung
import javax.inject.Inject

/**
 * Nach einem Neustart sind alle gesetzten Alarme weg. Der Waechter plant sie neu.
 */
@AndroidEntryPoint
class BootEmpfaenger : BroadcastReceiver() {

    @Inject lateinit var planung: WaechterPlanung

    override fun onReceive(kontext: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                planung.sofortAusfuehren()
                planung.taeglichPlanen()
            }
        }
    }
}
