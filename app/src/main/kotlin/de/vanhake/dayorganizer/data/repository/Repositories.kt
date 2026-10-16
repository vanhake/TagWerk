package de.vanhake.dayorganizer.data.repository

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

/** Ein Update traf keine Zeile: der Datensatz ist weg (z. B. durch ein eingespieltes Backup).
 * Eigener Typ, damit die Editoren das von einem echten Datenbankfehler unterscheiden. */
class DatensatzFehlt(val id: Long, art: String) : IllegalStateException("$art mit id=$id existiert nicht")

interface AktivitaetRepository {
    fun beobachteAktive(): Flow<List<Aktivitaet>>
    suspend fun alleAktiven(): List<Aktivitaet>
    suspend fun nachId(id: Long): Aktivitaet?
    suspend fun speichern(aktivitaet: Aktivitaet): Long
    suspend fun archivieren(id: Long, am: LocalDate)
    fun beobachteArchivierte(): Flow<List<Aktivitaet>>
    /** Alle Aktivitaeten, auch archivierte und pausierte, nach id. */
    suspend fun alle(): List<Aktivitaet>
    /** true, wenn gerade wiederhergestellt; false, wenn unbekannt oder nicht archiviert (idempotent). */
    suspend fun wiederherstellen(id: Long): Boolean
}

interface FaelligkeitRepository {
    suspend fun anlegenFallsNeu(neue: List<Faelligkeit>)
    suspend fun nachId(id: Long): Faelligkeit?
    suspend fun fuerPeriode(aktivitaetId: Long, periodeVon: LocalDate): List<Faelligkeit>
    suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<Faelligkeit>
    suspend fun historie(aktivitaetId: Long): List<Faelligkeit>
    suspend fun offeneBis(zeitpunkt: LocalDateTime): List<Faelligkeit>
    suspend fun offeneVorTag(tag: LocalDate): List<Faelligkeit>
    suspend fun offeneAbTag(abTag: LocalDate): List<Faelligkeit>
    suspend fun aktualisieren(faelligkeit: Faelligkeit)
    fun beobachteTag(tag: LocalDate): Flow<List<Faelligkeit>>
    suspend fun entfernenKuenftigeOffene(aktivitaetId: Long, abTag: LocalDate)
}

interface BuchungRepository {
    suspend fun buchen(buchungen: List<Buchung>)
    fun beobachteKontostand(): Flow<Int>
    suspend fun kontostand(): Int
    suspend fun serienbonusSchwellenSeit(aktivitaetId: Long, seit: LocalDateTime): Set<Int>
    suspend fun tagesbonusGebuchtAm(tag: LocalDate): Boolean
    suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<Buchung>
    suspend fun alle(): List<Buchung>
}
