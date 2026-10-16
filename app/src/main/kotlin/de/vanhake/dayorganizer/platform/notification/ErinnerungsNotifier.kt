package de.vanhake.dayorganizer.platform.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.vanhake.dayorganizer.MainActivity
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.util.versuchen
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErinnerungsNotifier @Inject constructor(
    @ApplicationContext private val kontext: Context,
) : Erinnerungsanzeige {

    override fun zeige(faelligkeit: Faelligkeit, aktivitaet: Aktivitaet) {
        val faelligkeitId = faelligkeit.id
        val text = if (faelligkeit.sollAnzahl > 1) {
            kontext.getString(
                R.string.erinnerung_noch_offen,
                faelligkeit.sollAnzahl - faelligkeit.istAnzahl,
                faelligkeit.sollAnzahl,
            )
        } else {
            kontext.getString(R.string.erinnerung_jetzt_faellig)
        }

        val benachrichtigung = NotificationCompat.Builder(kontext, Kanaele.KANAL_ERINNERUNGEN)
            .setSmallIcon(R.drawable.ic_erinnerung)
            .setContentTitle(aktivitaet.name)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(appOeffnen())
            .addAction(
                0,
                kontext.getString(R.string.aktion_erledigt),
                aktion(Benachrichtigungsaktionen.ACTION_ERLEDIGT, faelligkeitId),
            )
            .addAction(
                0,
                kontext.getString(R.string.aktion_spaeter),
                aktion(Benachrichtigungsaktionen.ACTION_SPAETER, faelligkeitId),
            )
            .addAction(
                0,
                kontext.getString(R.string.aktion_ausgelassen),
                aktion(Benachrichtigungsaktionen.ACTION_AUSGELASSEN, faelligkeitId),
            )
            .build()

        versuchen {
            NotificationManagerCompat.from(kontext).notify(faelligkeitId.toInt(), benachrichtigung)
        }
        // Fehlt POST_NOTIFICATIONS, wirft notify eine SecurityException. Der Banner
        // auf dem Heute-Screen aus Task 17 weist den Nutzer darauf hin; hier darf
        // die Erinnerungskette deswegen nicht abbrechen.
    }

    override fun verstecke(faelligkeitId: Long) {
        NotificationManagerCompat.from(kontext).cancel(faelligkeitId.toInt())
    }

    override fun alleVerstecken() {
        NotificationManagerCompat.from(kontext).cancelAll()
    }

    private fun aktion(action: String, faelligkeitId: Long): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(kontext.packageName)
            putExtra(Benachrichtigungsaktionen.EXTRA_FAELLIGKEIT_ID, faelligkeitId)
        }
        return PendingIntent.getBroadcast(
            kontext,
            (action.hashCode() * 31 + faelligkeitId.toInt()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun appOeffnen(): PendingIntent = PendingIntent.getActivity(
        kontext,
        0,
        Intent(kontext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
