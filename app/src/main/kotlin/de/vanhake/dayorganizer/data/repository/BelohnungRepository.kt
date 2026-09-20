package de.vanhake.dayorganizer.data.repository

import de.vanhake.dayorganizer.data.db.BelohnungDao
import de.vanhake.dayorganizer.data.mapper.zuDomaene
import de.vanhake.dayorganizer.data.mapper.zuEntity
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Einloesung
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface BelohnungRepository {
    fun beobachteAktive(): Flow<List<Belohnung>>
    suspend fun alle(): List<Belohnung>
    suspend fun nachId(id: Long): Belohnung?
    suspend fun speichern(belohnung: Belohnung): Long
    suspend fun archivieren(id: Long)
    suspend fun einloesungAnlegen(einloesung: Einloesung)
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

    override suspend fun einloesungAnlegen(einloesung: Einloesung) =
        dao.einloesungEinfuegen(einloesung.zuEntity())

    override fun beobachteEinloesungen(): Flow<List<Einloesung>> =
        dao.beobachteEinloesungen().map { liste -> liste.map { it.zuDomaene() } }

    override suspend fun alleEinloesungen(): List<Einloesung> =
        dao.alleEinloesungen().map { it.zuDomaene() }
}
