package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.ErinnerungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Erinnerung
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Legt Faelligkeiten und ihre Erstversuch-Erinnerungen fuer den Vorausblick an.
 * Mehrfachaufrufe sind unschaedlich: eindeutige Schluessel schlucken Wiederholungen.
 */
class FaelligkeitenMaterialisieren @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val erinnerungen: ErinnerungRepository,
    private val zeitplanRechner: ZeitplanRechner,
    private val uhr: Clock,
) {
    suspend operator fun invoke(vorausTage: Long = 7) {
        val heute = LocalDate.now(uhr)
        val bis = heute.plusDays(vorausTage)

        aktivitaeten.alleAktiven().forEach { aktivitaet ->
            faelligkeiten.anlegenFallsNeu(
                zeitplanRechner.faelligkeitenFuer(aktivitaet, heute, bis),
            )
        }

        // Erst nach dem Anlegen lesen: die Faelligkeiten brauchen ihre vergebenen Ids,
        // damit die Erinnerungen darauf zeigen koennen.
        val neueErinnerungen = faelligkeiten.fuerZeitraum(heute, bis).flatMap { f ->
            val aktivitaet = aktivitaeten.nachId(f.aktivitaetId) ?: return@flatMap emptyList()
            zeitplanRechner.anstossZeitpunkteFuer(aktivitaet, f)
                .map { Erinnerung(faelligkeitId = f.id, zeitpunkt = it, versuch = 0) }
        }
        erinnerungen.anlegenFallsNeu(neueErinnerungen)
    }
}
