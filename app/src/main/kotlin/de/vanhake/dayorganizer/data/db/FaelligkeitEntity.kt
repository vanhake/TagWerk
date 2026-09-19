package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "faelligkeit",
    foreignKeys = [
        ForeignKey(
            entity = AktivitaetEntity::class,
            parentColumns = ["id"],
            childColumns = ["aktivitaetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["schluessel"], unique = true),
        Index(value = ["aktivitaetId", "periodeVon"]),
        Index(value = ["status", "geplantFuer"]),
    ],
)
data class FaelligkeitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val aktivitaetId: Long,
    /** "<aktivitaetId>|<periodeVon>|<geplantFuer oder ZIEL>", macht Materialisieren idempotent. */
    val schluessel: String,
    val periodeVon: LocalDate,
    val periodeBis: LocalDate,
    val geplantFuer: LocalDateTime?,
    val sollAnzahl: Int,
    val istAnzahl: Int,
    val status: String,
    val erledigtAm: LocalDateTime?,
    val warPuenktlich: Boolean,
)
