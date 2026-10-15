package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "belohnung")
data class BelohnungEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val beschreibung: String,
    val preisPunkte: Int,
    /** "MINDESTSERIE", "FRUEHESTENS_AB" oder null. */
    val bedingungTyp: String?,
    val bedingungAktivitaetId: Long?,
    val bedingungPerioden: Int?,
    val bedingungDatum: LocalDate?,
    val archiviert: Boolean,
    val symbol: String = "redeem",
)
