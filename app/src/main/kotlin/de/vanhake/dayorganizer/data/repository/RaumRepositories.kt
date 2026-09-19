package de.vanhake.dayorganizer.data.repository

import de.vanhake.dayorganizer.data.db.AktivitaetDao
import de.vanhake.dayorganizer.data.db.BuchungDao
import de.vanhake.dayorganizer.data.db.FaelligkeitDao
import de.vanhake.dayorganizer.data.mapper.zuDomaene
import de.vanhake.dayorganizer.data.mapper.zuEntity
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class RaumAktivitaetRepository @Inject constructor(
    private val dao: AktivitaetDao,
) : AktivitaetRepository {

    override fun beobachteAktive(): Flow<List<Aktivitaet>> =
        dao.beobachteAktive().map { liste -> liste.map { it.zuDomaene() } }

    override suspend fun alleAktiven(): List<Aktivitaet> =
        dao.alleAktiven().map { it.zuDomaene() }

    override suspend fun nachId(id: Long): Aktivitaet? = dao.nachId(id)?.zuDomaene()

    override suspend fun speichern(aktivitaet: Aktivitaet): Long =
        if (aktivitaet.id == 0L) {
            dao.einfuegen(aktivitaet.zuEntity())
        } else {
            dao.aktualisieren(aktivitaet.zuEntity())
            aktivitaet.id
        }

    override suspend fun archivieren(id: Long, am: LocalDate) = dao.archivieren(id, am)
}

class RaumFaelligkeitRepository @Inject constructor(
    private val dao: FaelligkeitDao,
) : FaelligkeitRepository {

    override suspend fun anlegenFallsNeu(neue: List<Faelligkeit>) =
        dao.anlegenFallsNeu(neue.map { it.zuEntity() })

    override suspend fun nachId(id: Long): Faelligkeit? = dao.nachId(id)?.zuDomaene()

    override suspend fun fuerPeriode(aktivitaetId: Long, periodeVon: LocalDate): List<Faelligkeit> =
        dao.fuerPeriode(aktivitaetId, periodeVon).map { it.zuDomaene() }

    override suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<Faelligkeit> =
        dao.fuerZeitraum(von, bis).map { it.zuDomaene() }

    override suspend fun historie(aktivitaetId: Long): List<Faelligkeit> =
        dao.historie(aktivitaetId).map { it.zuDomaene() }

    override suspend fun offeneBis(zeitpunkt: LocalDateTime): List<Faelligkeit> =
        dao.offeneBis(zeitpunkt).map { it.zuDomaene() }

    override suspend fun offeneVorTag(tag: LocalDate): List<Faelligkeit> =
        dao.offeneVorTag(tag).map { it.zuDomaene() }

    override suspend fun offeneAbTag(abTag: LocalDate): List<Faelligkeit> =
        dao.offeneAbTag(abTag).map { it.zuDomaene() }

    override suspend fun aktualisieren(faelligkeit: Faelligkeit) =
        dao.aktualisieren(faelligkeit.zuEntity())

    override fun beobachteTag(tag: LocalDate): Flow<List<Faelligkeit>> =
        dao.beobachteTag(tag).map { liste -> liste.map { it.zuDomaene() } }
}

class RaumBuchungRepository @Inject constructor(
    private val dao: BuchungDao,
) : BuchungRepository {

    override suspend fun buchen(buchungen: List<Buchung>) =
        dao.einfuegen(buchungen.map { it.zuEntity() })

    override fun beobachteKontostand(): Flow<Int> = dao.beobachteKontostand()

    override suspend fun kontostand(): Int = dao.kontostandJetzt()

    override suspend fun serienbonusSchwellenSeit(aktivitaetId: Long, seit: LocalDateTime): Set<Int> =
        dao.serienbonusSchwellenSeit(aktivitaetId, seit).toSet()

    override suspend fun tagesbonusGebuchtAm(tag: LocalDate): Boolean =
        dao.anzahlTagesboni(tag.atStartOfDay(), tag.plusDays(1).atStartOfDay()) > 0
}
