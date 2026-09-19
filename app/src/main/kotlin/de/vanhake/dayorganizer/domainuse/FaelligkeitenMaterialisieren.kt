package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Legt die Faelligkeiten des Vorausblicks an. Mehrfachaufrufe sind unschaedlich:
 * der eindeutige Schluessel je Faelligkeit schluckt Wiederholungen.
 */
class FaelligkeitenMaterialisieren @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val zeitplanRechner: ZeitplanRechner,
    private val uhr: Clock,
) {
    suspend operator fun invoke(vorausTage: Long = 7) {
        val heute = LocalDate.now(uhr)
        val neue = aktivitaeten.alleAktiven().flatMap { aktivitaet ->
            zeitplanRechner.faelligkeitenFuer(aktivitaet, heute, heute.plusDays(vorausTage))
        }
        faelligkeiten.anlegenFallsNeu(neue)
    }
}
