package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "einloesung", indices = [Index(value = ["zeitpunkt"])])
data class EinloesungEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val belohnungId: Long,
    val name: String,
    val preisPunkte: Int,
    val zeitpunkt: LocalDateTime,
    val storniertAm: LocalDateTime? = null,
)
