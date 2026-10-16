package de.vanhake.dayorganizer.data.repository

import de.vanhake.dayorganizer.data.db.BelohnungDao
import de.vanhake.dayorganizer.data.mapper.zuDomaene
import de.vanhake.dayorganizer.data.mapper.zuEntity
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Einloesung
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject

interface BelohnungRepository {
    fun beobachteAktive(): Flow<List<Belohnung>>
    suspend fun alle(): List<Belohnung>
    suspend fun nachId(id: Long): Belohnung?
    suspend fun speichern(belohnung: Belohnung): Long
    suspend fun archivieren(id: Long)
    fun beobachteArchivierte(): Flow<List<Belohnung>>
    /** true, wenn gerade wiederhergestellt; false, wenn unbekannt oder nicht archiviert (idempotent). */
    suspend fun wiederherstellen(id: Long): Boolean
    /** Liefert die von der Datenbank vergebene id -- BelohnungEinloesen gibt sie im Erfolg
     * zurueck, damit ein Rueckgaengig genau diese Zeile stornieren kann. */
    suspend fun einloesungAnlegen(einloesung: Einloesung): Long
    /** true, wenn die Einloesung gerade storniert wurde; false, wenn unbekannt oder schon storniert. */
    suspend fun einloesungStornieren(id: Long, am: LocalDateTime): Boolean
    fun beobachteEinloesungen(): Flow<List<Einloesung>>
    suspend fun alleEinloesungen(): List<Einloesung>
}

class RaumBelohnungRepository @Inject constructor(
    private val dao: BelohnungDao,
) : BelohnungRepository {

    override fun beobachteAktive(): Flow<List<Belohnung>> =
        dao.beobachteAktive().map { liste -> liste.map { it.zuDomaene() } }

    override suspend fun alle(): List<Belohnung> = dao.alle().map { it.zuDomaene() }

    override suspend fun nachId(id: Long): Belohnung? = dao.nachId(id)?.zuDomaene()

    override suspend fun speichern(belohnung: Belohnung): Long =
        if (belohnung.id == 0L) {
            dao.einfuegen(belohnung.zuEntity())
        } else {
            val betroffeneZeilen = dao.aktualisieren(belohnung.zuEntity())
            check(betroffeneZeilen > 0) {
                "Belohnung mit id=${belohnung.id} konnte nicht aktualisiert werden -- " +
                    "kein passender Datensatz vorhanden"
            }
            belohnung.id
        }

    override suspend fun archivieren(id: Long) = dao.archivieren(id)

    override fun beobachteArchivierte(): Flow<List<Belohnung>> =
        dao.beobachteArchivierte().map { liste -> liste.map { it.zuDomaene() } }

    override suspend fun wiederherstellen(id: Long): Boolean = dao.wiederherstellen(id) > 0

    override suspend fun einloesungAnlegen(einloesung: Einloesung): Long =
        dao.einloesungEinfuegen(einloesung.zuEntity())

    override suspend fun einloesungStornieren(id: Long, am: LocalDateTime): Boolean =
        dao.einloesungStornieren(id, am) > 0

    override fun beobachteEinloesungen(): Flow<List<Einloesung>> =
        dao.beobachteEinloesungen().map { liste -> liste.map { it.zuDomaene() } }

    override suspend fun alleEinloesungen(): List<Einloesung> =
        dao.alleEinloesungen().map { it.zuDomaene() }
}
