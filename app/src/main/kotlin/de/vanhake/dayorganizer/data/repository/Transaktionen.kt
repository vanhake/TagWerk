package de.vanhake.dayorganizer.data.repository

import androidx.room.withTransaction
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import javax.inject.Inject

/**
 * Klammert mehrere Schreibvorgaenge zu einer Einheit. Ohne sie waere die
 * Statuspruefung im Erledigen-Use-Case die einzige Absicherung gegen
 * Doppelbuchungen -- und die greift nicht, wenn zwei Aufrufer gleichzeitig
 * lesen, bevor einer schreibt.
 */
interface Transaktionen {
    suspend fun <T> inTransaktion(block: suspend () -> T): T
}

class RaumTransaktionen @Inject constructor(
    private val db: DayOrganizerDatenbank,
) : Transaktionen {
    override suspend fun <T> inTransaktion(block: suspend () -> T): T =
        db.withTransaction(block)
}
