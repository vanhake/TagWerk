package de.vanhake.dayorganizer.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class FaelligkeitStatus { OFFEN, ERLEDIGT, AUSGELASSEN, VERPASST }

/**
 * Konkrete Instanz einer Aktivitaet in einer Periode.
 * Feste Zeiten und Intervall: sollAnzahl = 1, geplantFuer gesetzt.
 * Tages- und Wochenziel: sollAnzahl = X, geplantFuer = null.
 */
data class Faelligkeit(
    val id: Long = 0,
    val aktivitaetId: Long,
    val periodeVon: LocalDate,
    val periodeBis: LocalDate,
    val geplantFuer: LocalDateTime?,
    val sollAnzahl: Int,
    val istAnzahl: Int = 0,
    val status: FaelligkeitStatus = FaelligkeitStatus.OFFEN,
    val erledigtAm: LocalDateTime? = null,
    val warPuenktlich: Boolean = false,
)
