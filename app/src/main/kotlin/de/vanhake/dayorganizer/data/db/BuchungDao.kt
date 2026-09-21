package de.vanhake.dayorganizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface BuchungDao {

    @Insert
    suspend fun einfuegen(eintraege: List<BuchungEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun alleEinfuegen(eintraege: List<BuchungEntity>)

    @Query("DELETE FROM buchung")
    suspend fun alleLoeschen()

    @Query("SELECT COALESCE(SUM(betrag), 0) FROM buchung")
    fun beobachteKontostand(): Flow<Int>

    @Query("SELECT COALESCE(SUM(betrag), 0) FROM buchung")
    suspend fun kontostandJetzt(): Int

    @Query(
        "SELECT schwelle FROM buchung WHERE art = 'SERIENBONUS' AND aktivitaetId = :aktivitaetId " +
            "AND zeitpunkt >= :seit AND schwelle IS NOT NULL",
    )
    suspend fun serienbonusSchwellenSeit(aktivitaetId: Long, seit: LocalDateTime): List<Int>

    @Query(
        "SELECT COUNT(*) FROM buchung WHERE art = 'TAGESBONUS' " +
            "AND zeitpunkt >= :tagesBeginn AND zeitpunkt < :tagesEnde",
    )
    suspend fun anzahlTagesboni(tagesBeginn: LocalDateTime, tagesEnde: LocalDateTime): Int

    @Query("SELECT * FROM buchung ORDER BY zeitpunkt DESC LIMIT :anzahl")
    suspend fun letzte(anzahl: Int): List<BuchungEntity>

    @Query("SELECT * FROM buchung WHERE zeitpunkt >= :von AND zeitpunkt < :bis ORDER BY zeitpunkt")
    suspend fun fuerZeitraum(von: LocalDateTime, bis: LocalDateTime): List<BuchungEntity>

    @Query("SELECT * FROM buchung ORDER BY zeitpunkt")
    suspend fun alle(): List<BuchungEntity>
}
