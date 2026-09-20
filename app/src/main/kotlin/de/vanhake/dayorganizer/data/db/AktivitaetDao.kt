package de.vanhake.dayorganizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AktivitaetDao {

    @Insert
    suspend fun einfuegen(eintrag: AktivitaetEntity): Long

    /** Rueckgabe ist die Anzahl betroffener Zeilen -- 0, wenn keine Aktivitaet mit dieser id
     * existiert. RaumAktivitaetRepository.speichern wertet das aus, statt einen fehlgeschlagenen
     * Update-Aufruf stillschweigend als Erfolg zu melden. */
    @Update
    suspend fun aktualisieren(eintrag: AktivitaetEntity): Int

    @Query("SELECT * FROM aktivitaet WHERE id = :id")
    suspend fun nachId(id: Long): AktivitaetEntity?

    @Query("SELECT * FROM aktivitaet WHERE aktiv = 1 AND archiviertAm IS NULL ORDER BY name")
    suspend fun alleAktiven(): List<AktivitaetEntity>

    @Query("SELECT * FROM aktivitaet WHERE aktiv = 1 AND archiviertAm IS NULL ORDER BY name")
    fun beobachteAktive(): Flow<List<AktivitaetEntity>>

    @Query("UPDATE aktivitaet SET archiviertAm = :am, aktiv = 0 WHERE id = :id")
    suspend fun archivieren(id: Long, am: java.time.LocalDate)
}
