package de.vanhake.dayorganizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BelohnungDao {

    @Insert suspend fun einfuegen(eintrag: BelohnungEntity): Long

    /** Rueckgabe ist die Anzahl betroffener Zeilen -- 0, wenn keine Belohnung mit dieser id
     * existiert. RaumBelohnungRepository.speichern wertet das aus, statt einen fehlgeschlagenen
     * Update-Aufruf stillschweigend als Erfolg zu melden. */
    @Update suspend fun aktualisieren(eintrag: BelohnungEntity): Int

    @Query("SELECT * FROM belohnung WHERE id = :id")
    suspend fun nachId(id: Long): BelohnungEntity?

    @Query("SELECT * FROM belohnung ORDER BY archiviert, preisPunkte")
    suspend fun alle(): List<BelohnungEntity>

    @Query("SELECT * FROM belohnung WHERE archiviert = 0 ORDER BY preisPunkte")
    fun beobachteAktive(): Flow<List<BelohnungEntity>>

    @Query("UPDATE belohnung SET archiviert = 1 WHERE id = :id")
    suspend fun archivieren(id: Long)

    @Insert suspend fun einloesungEinfuegen(eintrag: EinloesungEntity)

    @Query("SELECT * FROM einloesung ORDER BY zeitpunkt DESC, id DESC")
    fun beobachteEinloesungen(): Flow<List<EinloesungEntity>>

    @Query("SELECT * FROM einloesung ORDER BY zeitpunkt DESC, id DESC")
    suspend fun alleEinloesungen(): List<EinloesungEntity>
}
