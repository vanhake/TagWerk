package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import javax.inject.Inject

/** Macht ein FaelligkeitAuslassen rueckgaengig: keine Buchungen zu widerrufen, nur der Status. */
class FaelligkeitAuslassenRueckgaengig @Inject constructor(
    private val faelligkeiten: FaelligkeitRepository,
) {
    suspend operator fun invoke(faelligkeitVorher: Faelligkeit) {
        faelligkeiten.aktualisieren(faelligkeitVorher)
    }
}
