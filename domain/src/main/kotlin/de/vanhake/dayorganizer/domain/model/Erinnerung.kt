package de.vanhake.dayorganizer.domain.model

import java.time.LocalDateTime

/**
 * Ein geplanter Erinnerungszeitpunkt. Erstversuche entstehen beim
 * Materialisieren, Nachfassen und "Spaeter" legen weitere Zeilen an.
 */
data class Erinnerung(
    val id: Long = 0,
    val faelligkeitId: Long,
    val zeitpunkt: LocalDateTime,
    val versuch: Int = 0,
    val ausgeloest: Boolean = false,
)
