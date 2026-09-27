package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "aktivitaet")
data class AktivitaetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notiz: String,
    val farbe: Int,
    val basisPunkte: Int,
    val kulanzMinuten: Int,
    val puenktlichkeitsBonus: Int,
    val symbol: String = "self_improvement",
    /** Kodiert als "7:10,30:50". */
    val serienSchwellen: String,
    val nachfassAnzahl: Int,
    val nachfassAbstandMinuten: Int,
    val aktiv: Boolean,
    val erstelltAm: LocalDate,
    val archiviertAm: LocalDate?,
    val zeitplanTyp: String,
    val zeitplanZeiten: String,
    val zeitplanWochentage: String,
    val zeitplanIntervallStunden: Int?,
    val zeitplanFensterVon: String?,
    val zeitplanFensterBis: String?,
    val zeitplanZielAnzahl: Int?,
    val zeitplanAnstoss: String,
)
