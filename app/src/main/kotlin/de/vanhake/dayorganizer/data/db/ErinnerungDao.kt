package de.vanhake.dayorganizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDateTime

@Dao
interface ErinnerungDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun anlegenFallsNeu(eintraege: List<ErinnerungEntity>)

    @Query(
        "SELECT * FROM erinnerung WHERE ausgeloest = 0 AND zeitpunkt >= :zeitpunkt " +
            "ORDER BY zeitpunkt LIMIT 1",
    )
    suspend fun naechsteAb(zeitpunkt: LocalDateTime): ErinnerungEntity?

    @Query(
        "SELECT * FROM erinnerung WHERE ausgeloest = 0 AND zeitpunkt <= :zeitpunkt ORDER BY zeitpunkt",
    )
    suspend fun faelligeBis(zeitpunkt: LocalDateTime): List<ErinnerungEntity>

    @Query("UPDATE erinnerung SET ausgeloest = 1 WHERE id = :id")
    suspend fun alsAusgeloestMarkieren(id: Long)

    @Query("DELETE FROM erinnerung WHERE faelligkeitId = :faelligkeitId AND ausgeloest = 0")
    suspend fun entfernenFuerFaelligkeit(faelligkeitId: Long)
}
