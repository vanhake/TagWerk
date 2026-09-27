package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/** Bewusstes Auslassen: keine Punkte, aber die Serie ueberlebt. */
class FaelligkeitAuslassen @Inject constructor(
    private val faelligkeiten: FaelligkeitRepository,
    private val uhr: Clock,
) {
    suspend operator fun invoke(faelligkeitId: Long) {
        mitProtokoll(faelligkeitId)
    }

    /** Liefert die Faelligkeit, wie sie vor dieser Aktion war -- Grundlage fuer
     * FaelligkeitAuslassenRueckgaengig. Null, wenn nichts geaendert wurde. */
    suspend fun mitProtokoll(faelligkeitId: Long): Faelligkeit? {
        val f = faelligkeiten.nachId(faelligkeitId) ?: return null
        if (f.status != FaelligkeitStatus.OFFEN) return null
        faelligkeiten.aktualisieren(
            f.copy(status = FaelligkeitStatus.AUSGELASSEN, erledigtAm = LocalDateTime.now(uhr)),
        )
        return f
    }
}
