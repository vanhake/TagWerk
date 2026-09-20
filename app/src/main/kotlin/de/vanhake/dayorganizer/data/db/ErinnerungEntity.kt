package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "erinnerung",
    foreignKeys = [
        ForeignKey(
            entity = FaelligkeitEntity::class,
            parentColumns = ["id"],
            childColumns = ["faelligkeitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["schluessel"], unique = true),
        Index(value = ["ausgeloest", "zeitpunkt"]),
        Index(value = ["faelligkeitId"]),
    ],
)
data class ErinnerungEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val faelligkeitId: Long,
    /** "<faelligkeitId>|<zeitpunkt>|<versuch>". */
    val schluessel: String,
    val zeitpunkt: LocalDateTime,
    val versuch: Int,
    val ausgeloest: Boolean,
)
