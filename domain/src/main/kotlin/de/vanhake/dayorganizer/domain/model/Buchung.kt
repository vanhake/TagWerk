package de.vanhake.dayorganizer.domain.model

import java.time.LocalDateTime

enum class BuchungsArt {
    ERLEDIGUNG,
    PUENKTLICHKEITSBONUS,
    SERIENBONUS,
    TAGESBONUS,
    EINLOESUNG,
    KORREKTUR,
}

/** Eine Zeile im Punktejournal. Der Kontostand ist die Summe aller Betraege. */
data class Buchung(
    val id: Long = 0,
    val art: BuchungsArt,
    val betrag: Int,
    val zeitpunkt: LocalDateTime,
    val aktivitaetId: Long? = null,
    val faelligkeitId: Long? = null,
    val belohnungId: Long? = null,
    /** Bei SERIENBONUS der ausgeloeste Schwellenwert, sonst null. */
    val schwelle: Int? = null,
    val notiz: String = "",
)
