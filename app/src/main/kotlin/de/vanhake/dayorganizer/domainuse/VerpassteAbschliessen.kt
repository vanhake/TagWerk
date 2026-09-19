package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Schliesst alle offenen Faelligkeiten, deren Periode vor heute endet. */
class VerpassteAbschliessen @Inject constructor(
    private val faelligkeiten: FaelligkeitRepository,
    private val uhr: Clock,
) {
    suspend operator fun invoke() {
        val heute = LocalDate.now(uhr)
        faelligkeiten.offeneVorTag(heute).forEach {
            faelligkeiten.aktualisieren(it.copy(status = FaelligkeitStatus.VERPASST))
        }
    }
}
