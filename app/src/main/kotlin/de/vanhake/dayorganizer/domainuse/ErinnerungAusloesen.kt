package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.ErinnerungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Erinnerung
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/** Verarbeitet alle bis jetzt faelligen Erinnerungstermine. */
class ErinnerungAusloesen @Inject constructor(
    private val erinnerungen: ErinnerungRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val aktivitaeten: AktivitaetRepository,
    private val anzeige: Erinnerungsanzeige,
    private val uhr: Clock,
) {
    suspend operator fun invoke() {
        val jetzt = LocalDateTime.now(uhr)

        erinnerungen.faelligeBis(jetzt).forEach { erinnerung ->
            erinnerungen.alsAusgeloestMarkieren(erinnerung.id)

            val faelligkeit = faelligkeiten.nachId(erinnerung.faelligkeitId) ?: return@forEach
            if (faelligkeit.status != FaelligkeitStatus.OFFEN) return@forEach
            val aktivitaet = aktivitaeten.nachId(faelligkeit.aktivitaetId) ?: return@forEach

            // Gehoert zum Aufraeumen bei Archivieren/Umplanen (siehe AktivitaetNeuPlanen):
            // das Aufraeumen kann durch einen Absturz uebersprungen worden sein, darum
            // sichert dieser Check zusaetzlich ab, dass eine verwaiste Erinnerung nie
            // eine Benachrichtigung fuer eine archivierte oder inaktive Aktivitaet zeigt.
            if (!aktivitaet.aktiv || aktivitaet.archiviertAm != null) return@forEach

            anzeige.zeige(faelligkeit, aktivitaet)

            // Eine Einmalaufgabe erinnert ueber Wochen von selbst wieder; ein Nachfassen im
            // Minutentakt waere dort aufdringlich.
            val nachfassen = aktivitaet.zeitplan !is Zeitplan.Einmalig
            if (nachfassen && erinnerung.versuch < aktivitaet.nachfassAnzahl) {
                erinnerungen.anlegenFallsNeu(
                    listOf(
                        Erinnerung(
                            faelligkeitId = faelligkeit.id,
                            zeitpunkt = jetzt.plusMinutes(aktivitaet.nachfassAbstandMinuten.toLong()),
                            versuch = erinnerung.versuch + 1,
                        ),
                    ),
                )
            }
        }
    }
}

/** Verschiebt die Erinnerung einer Faelligkeit um den Nachfassabstand. */
class ErinnerungSpaeter @Inject constructor(
    private val erinnerungen: ErinnerungRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val aktivitaeten: AktivitaetRepository,
    private val uhr: Clock,
) {
    suspend operator fun invoke(faelligkeitId: Long) {
        val faelligkeit = faelligkeiten.nachId(faelligkeitId) ?: return
        if (faelligkeit.status != FaelligkeitStatus.OFFEN) return
        val aktivitaet = aktivitaeten.nachId(faelligkeit.aktivitaetId) ?: return

        erinnerungen.entfernenFuerFaelligkeit(faelligkeitId)
        erinnerungen.anlegenFallsNeu(
            listOf(
                Erinnerung(
                    faelligkeitId = faelligkeitId,
                    zeitpunkt = LocalDateTime.now(uhr)
                        .plusMinutes(aktivitaet.nachfassAbstandMinuten.toLong()),
                    versuch = 0,
                ),
            ),
        )
    }
}
