package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "buchung",
    indices = [Index(value = ["zeitpunkt"]), Index(value = ["aktivitaetId", "art"])],
)
data class BuchungEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val art: String,
    val betrag: Int,
    val zeitpunkt: LocalDateTime,
    val aktivitaetId: Long?,
    val faelligkeitId: Long?,
    val belohnungId: Long?,
    val schwelle: Int?,
    val notiz: String,
)
