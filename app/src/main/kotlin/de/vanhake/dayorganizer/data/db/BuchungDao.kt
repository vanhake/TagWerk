package de.vanhake.dayorganizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface BuchungDao {

    @Insert
    suspend fun einfuegen(eintraege: List<BuchungEntity>)

    // ABORT (Standard) statt REPLACE: eine doppelte Buchungs-id in der Backup-Datei
    // soll die Transaktion zurueckrollen lassen, nicht still verschwinden.
    @Insert
    suspend fun alleEinfuegen(eintraege: List<BuchungEntity>)

    @Query("DELETE FROM buchung")
    suspend fun alleLoeschen()

    @Query("SELECT COALESCE(SUM(betrag), 0) FROM buchung")
    fun beobachteKontostand(): Flow<Int>

    @Query("SELECT COALESCE(SUM(betrag), 0) FROM buchung")
    suspend fun kontostandJetzt(): Int

    // Netto je Schwelle statt reiner Zeilenliste: eine KORREKTUR-Zeile traegt dieselbe
    // aktivitaetId/schwelle wie der Serienbonus, den sie zuruecknimmt (per copy() in
    // FaelligkeitErledigenRueckgaengig). GROUP BY/HAVING liefert nur Schwellen, deren
    // Buchungen in Summe noch positiv sind -- eine vollstaendig zurueckgenommene faellt heraus.
    @Query(
        "SELECT schwelle FROM buchung WHERE (art = 'SERIENBONUS' OR art = 'KORREKTUR') " +
            "AND aktivitaetId = :aktivitaetId AND schwelle IS NOT NULL AND zeitpunkt >= :seit " +
            "GROUP BY schwelle HAVING SUM(betrag) > 0",
    )
    suspend fun serienbonusSchwellenSeit(aktivitaetId: Long, seit: LocalDateTime): List<Int>

    // Netto statt Anzahl: eine per FaelligkeitErledigenRueckgaengig gebuchte KORREKTUR-Zeile
    // (negierter Betrag, dieselbe aktivitaetId=null/faelligkeitId=null-Form wie der Tagesbonus
    // selbst) muss den Tagesbonus wieder freigeben, sonst bucht ein erneutes Abschliessen am
    // selben Tag ihn nie wieder. AND belohnungId IS NULL grenzt zusaetzlich EINLOESUNG-Zeilen
    // aus (aktivitaetId/faelligkeitId ebenfalls null, nur belohnungId gesetzt) -- ein echter
    // Tagesbonus setzt nie eine belohnungId. Ohne diese Bedingung wuerde eine kuenftige
    // KORREKTUR, die eine EINLOESUNG zuruecknimmt (positiver Betrag, belohnungId der
    // Einloesung), dieselbe null/null-Form annehmen und hier faelschlich mitgezaehlt.
    @Query(
        "SELECT COALESCE(SUM(betrag), 0) FROM buchung WHERE (art = 'TAGESBONUS' OR art = 'KORREKTUR') " +
            "AND aktivitaetId IS NULL AND faelligkeitId IS NULL AND belohnungId IS NULL " +
            "AND zeitpunkt >= :tagesBeginn AND zeitpunkt < :tagesEnde",
    )
    suspend fun nettoTagesbonusPunkte(tagesBeginn: LocalDateTime, tagesEnde: LocalDateTime): Int

    @Query("SELECT * FROM buchung ORDER BY zeitpunkt DESC LIMIT :anzahl")
    suspend fun letzte(anzahl: Int): List<BuchungEntity>

    @Query("SELECT * FROM buchung WHERE zeitpunkt >= :von AND zeitpunkt < :bis ORDER BY zeitpunkt")
    suspend fun fuerZeitraum(von: LocalDateTime, bis: LocalDateTime): List<BuchungEntity>

    @Query("SELECT * FROM buchung ORDER BY zeitpunkt")
    suspend fun alle(): List<BuchungEntity>
}
