package de.vanhake.dayorganizer.domain.model

import java.time.LocalDate

/** Bonus, sobald die Serie [perioden] erreicht. */
data class SerienSchwelle(val perioden: Int, val bonus: Int)

data class Aktivitaet(
    val id: Long = 0,
    val name: String,
    val notiz: String = "",
    val farbe: Int,
    val basisPunkte: Int,
    val zeitplan: Zeitplan,
    val kulanzMinuten: Int = 60,
    val puenktlichkeitsBonus: Int = 0,
    val serienSchwellen: List<SerienSchwelle> = emptyList(),
    val nachfassAnzahl: Int = 2,
    val nachfassAbstandMinuten: Int = 15,
    val aktiv: Boolean = true,
    val erstelltAm: LocalDate,
    val archiviertAm: LocalDate? = null,
)
