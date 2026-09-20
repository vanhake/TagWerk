package de.vanhake.dayorganizer.data.repository

import de.vanhake.dayorganizer.data.db.ErinnerungDao
import de.vanhake.dayorganizer.data.db.ErinnerungEntity
import de.vanhake.dayorganizer.domain.model.Erinnerung
import java.time.LocalDateTime
import javax.inject.Inject

interface ErinnerungRepository {
    suspend fun anlegenFallsNeu(neue: List<Erinnerung>)
    suspend fun naechsteOffene(): Erinnerung?
    suspend fun faelligeBis(zeitpunkt: LocalDateTime): List<Erinnerung>
    suspend fun alsAusgeloestMarkieren(id: Long)
    suspend fun entfernenFuerFaelligkeit(faelligkeitId: Long)
}

private fun Erinnerung.zuEntity() = ErinnerungEntity(
    id = id,
    faelligkeitId = faelligkeitId,
    schluessel = "$faelligkeitId|$zeitpunkt|$versuch",
    zeitpunkt = zeitpunkt,
    versuch = versuch,
    ausgeloest = ausgeloest,
)

private fun ErinnerungEntity.zuDomaene() = Erinnerung(
    id = id,
    faelligkeitId = faelligkeitId,
    zeitpunkt = zeitpunkt,
    versuch = versuch,
    ausgeloest = ausgeloest,
)

class RaumErinnerungRepository @Inject constructor(
    private val dao: ErinnerungDao,
) : ErinnerungRepository {

    override suspend fun anlegenFallsNeu(neue: List<Erinnerung>) =
        dao.anlegenFallsNeu(neue.map { it.zuEntity() })

    override suspend fun naechsteOffene(): Erinnerung? =
        dao.naechsteOffene()?.zuDomaene()

    override suspend fun faelligeBis(zeitpunkt: LocalDateTime): List<Erinnerung> =
        dao.faelligeBis(zeitpunkt).map { it.zuDomaene() }

    override suspend fun alsAusgeloestMarkieren(id: Long) = dao.alsAusgeloestMarkieren(id)

    override suspend fun entfernenFuerFaelligkeit(faelligkeitId: Long) =
        dao.entfernenFuerFaelligkeit(faelligkeitId)
}
