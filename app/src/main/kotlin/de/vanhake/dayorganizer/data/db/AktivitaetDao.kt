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

    // ABORT (Standard) statt REPLACE: ein doppelter Schluessel in der Backup-Datei
    // soll die Transaktion zurueckrollen lassen, nicht still verschwinden.
    @Insert
    suspend fun alleEinfuegen(eintraege: List<AktivitaetEntity>)

    @Query("DELETE FROM aktivitaet")
    suspend fun alleLoeschen()

    @Query("SELECT * FROM aktivitaet ORDER BY id")
    suspend fun alle(): List<AktivitaetEntity>

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

    @Query("SELECT * FROM aktivitaet WHERE archiviertAm IS NOT NULL ORDER BY name")
    fun beobachteArchivierte(): Flow<List<AktivitaetEntity>>

    /** 1, wenn die Aktivitaet gerade wiederhergestellt wurde; 0, wenn es sie nicht gibt oder
     * sie nicht archiviert war. Die Bedingung macht das Wiederherstellen idempotent. */
    @Query("UPDATE aktivitaet SET archiviertAm = NULL, aktiv = 1 WHERE id = :id AND archiviertAm IS NOT NULL")
    suspend fun wiederherstellen(id: Long): Int
}
