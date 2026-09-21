package de.vanhake.dayorganizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface FaelligkeitDao {

    /** IGNORE zusammen mit dem eindeutigen Schluessel macht Materialisieren idempotent. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun anlegenFallsNeu(eintraege: List<FaelligkeitEntity>)

    // ABORT (Standard) statt REPLACE: ein doppelter Schluessel in der Backup-Datei
    // soll die Transaktion zurueckrollen lassen, nicht still verschwinden.
    @Insert
    suspend fun alleEinfuegen(eintraege: List<FaelligkeitEntity>)

    @Query("DELETE FROM faelligkeit")
    suspend fun alleLoeschen()

    @Query("SELECT * FROM faelligkeit ORDER BY id")
    suspend fun alle(): List<FaelligkeitEntity>

    @Update
    suspend fun aktualisieren(eintrag: FaelligkeitEntity)

    @Query("SELECT * FROM faelligkeit WHERE id = :id")
    suspend fun nachId(id: Long): FaelligkeitEntity?

    @Query(
        "SELECT * FROM faelligkeit WHERE periodeBis >= :von AND periodeVon <= :bis " +
            "ORDER BY periodeVon, geplantFuer IS NULL, geplantFuer",
    )
    suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<FaelligkeitEntity>

    @Query(
        "SELECT * FROM faelligkeit WHERE periodeBis >= :tag AND periodeVon <= :tag " +
            "ORDER BY geplantFuer IS NULL, geplantFuer",
    )
    fun beobachteTag(tag: LocalDate): Flow<List<FaelligkeitEntity>>

    @Query(
        "SELECT * FROM faelligkeit WHERE aktivitaetId = :aktivitaetId AND periodeVon = :periodeVon",
    )
    suspend fun fuerPeriode(aktivitaetId: Long, periodeVon: LocalDate): List<FaelligkeitEntity>

    @Query("SELECT * FROM faelligkeit WHERE aktivitaetId = :aktivitaetId ORDER BY periodeVon")
    suspend fun historie(aktivitaetId: Long): List<FaelligkeitEntity>

    /** Offene Faelligkeiten mit Sollzeitpunkt, deren Zeitpunkt vor [zeitpunkt] liegt. */
    @Query(
        "SELECT * FROM faelligkeit WHERE status = 'OFFEN' AND geplantFuer IS NOT NULL " +
            "AND geplantFuer < :zeitpunkt ORDER BY geplantFuer",
    )
    suspend fun offeneBis(zeitpunkt: LocalDateTime): List<FaelligkeitEntity>

    /** Offene Faelligkeiten, deren Periode vor [tag] endet — Kandidaten fuer VERPASST. */
    @Query("SELECT * FROM faelligkeit WHERE status = 'OFFEN' AND periodeBis < :tag")
    suspend fun offeneVorTag(tag: LocalDate): List<FaelligkeitEntity>

    @Query("SELECT * FROM faelligkeit WHERE status = 'OFFEN' AND periodeBis >= :abTag")
    suspend fun offeneAbTag(abTag: LocalDate): List<FaelligkeitEntity>

    /**
     * Raeumt kuenftige, noch nicht begonnene Faelligkeiten einer Aktivitaet weg -- fuer
     * Archivieren und Zeitplanaenderung. istAnzahl = 0 schuetzt ein teilweise erfuelltes
     * Zaehlziel (z.B. 2 von 5 Trainingseinheiten): der Fortschritt gehoert dem Nutzer.
     * Die Erinnerungen haengen per ON DELETE CASCADE daran und verschwinden mit.
     */
    @Query(
        "DELETE FROM faelligkeit WHERE aktivitaetId = :aktivitaetId AND status = 'OFFEN' " +
            "AND istAnzahl = 0 AND periodeVon >= :abTag",
    )
    suspend fun entfernenKuenftigeOffene(aktivitaetId: Long, abTag: LocalDate)
}
