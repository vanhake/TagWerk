package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
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
        val f = faelligkeiten.nachId(faelligkeitId) ?: return
        if (f.status != FaelligkeitStatus.OFFEN) return
        faelligkeiten.aktualisieren(
            f.copy(status = FaelligkeitStatus.AUSGELASSEN, erledigtAm = LocalDateTime.now(uhr)),
        )
    }
}
