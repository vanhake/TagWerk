package de.vanhake.dayorganizer

import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.ErinnerungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.data.repository.Transaktionen
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.model.Erinnerung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

class FakeAktivitaetRepository(start: List<Aktivitaet> = emptyList()) : AktivitaetRepository {
    val stand = MutableStateFlow(start)

    private fun naechsteId(): Long = (stand.value.maxOfOrNull { it.id } ?: 0L) + 1

    override fun beobachteAktive(): Flow<List<Aktivitaet>> =
        stand.map { liste ->
            liste.filter { it.aktiv && it.archiviertAm == null }.sortedBy { it.name }
        }

    override suspend fun alleAktiven(): List<Aktivitaet> =
        stand.value.filter { it.aktiv && it.archiviertAm == null }.sortedBy { it.name }

    override suspend fun nachId(id: Long): Aktivitaet? = stand.value.find { it.id == id }

    override suspend fun speichern(aktivitaet: Aktivitaet): Long {
        if (aktivitaet.id == 0L) {
            val id = naechsteId()
            stand.value = stand.value + aktivitaet.copy(id = id)
            return id
        }
        // Spiegelt Room's @Update: ohne passenden Datensatz gibt es keinen stillen Insert.
        check(stand.value.any { it.id == aktivitaet.id }) {
            "Aktivitaet mit id=${aktivitaet.id} konnte nicht aktualisiert werden -- " +
                "kein passender Datensatz vorhanden"
        }
        stand.value = stand.value.map { if (it.id == aktivitaet.id) aktivitaet else it }
        return aktivitaet.id
    }

    override suspend fun archivieren(id: Long, am: LocalDate) {
        stand.value = stand.value.map {
            if (it.id == id) it.copy(aktiv = false, archiviertAm = am) else it
        }
    }
}

class FakeFaelligkeitRepository(start: List<Faelligkeit> = emptyList()) : FaelligkeitRepository {
    val stand = MutableStateFlow(start)

    private fun naechsteId(): Long = (stand.value.maxOfOrNull { it.id } ?: 0L) + 1

    private fun schluessel(f: Faelligkeit) = "${f.aktivitaetId}|${f.periodeVon}|${f.geplantFuer ?: "ZIEL"}"

    /** Spiegelt "ORDER BY periodeVon, geplantFuer IS NULL, geplantFuer": Ziele (geplantFuer
     * == null) sortieren innerhalb derselben Periode nach den terminierten Eintraegen ein. */
    private val zeitraumOrdnung: Comparator<Faelligkeit> =
        compareBy<Faelligkeit> { it.periodeVon }
            .thenBy { it.geplantFuer == null }
            .thenBy { it.geplantFuer }

    /** Spiegelt "ORDER BY geplantFuer IS NULL, geplantFuer". */
    private val tagOrdnung: Comparator<Faelligkeit> =
        compareBy<Faelligkeit> { it.geplantFuer == null }
            .thenBy { it.geplantFuer }

    override suspend fun anlegenFallsNeu(neue: List<Faelligkeit>) {
        val vorhanden = stand.value.map(::schluessel).toMutableSet()
        val ergaenzt = stand.value.toMutableList()
        var naechsteId = naechsteId()
        neue.forEach { f ->
            if (vorhanden.add(schluessel(f))) ergaenzt += f.copy(id = naechsteId++)
        }
        stand.value = ergaenzt
    }

    override suspend fun nachId(id: Long): Faelligkeit? = stand.value.find { it.id == id }

    override suspend fun fuerPeriode(aktivitaetId: Long, periodeVon: LocalDate): List<Faelligkeit> =
        stand.value.filter { it.aktivitaetId == aktivitaetId && it.periodeVon == periodeVon }

    override suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<Faelligkeit> =
        stand.value
            .filter { !it.periodeBis.isBefore(von) && !it.periodeVon.isAfter(bis) }
            .sortedWith(zeitraumOrdnung)

    override suspend fun historie(aktivitaetId: Long): List<Faelligkeit> =
        stand.value.filter { it.aktivitaetId == aktivitaetId }.sortedBy { it.periodeVon }

    override suspend fun offeneBis(zeitpunkt: LocalDateTime): List<Faelligkeit> =
        stand.value
            .filter {
                it.status == FaelligkeitStatus.OFFEN &&
                    it.geplantFuer != null && it.geplantFuer!!.isBefore(zeitpunkt)
            }
            .sortedBy { it.geplantFuer }

    override suspend fun offeneVorTag(tag: LocalDate): List<Faelligkeit> =
        stand.value.filter { it.status == FaelligkeitStatus.OFFEN && it.periodeBis.isBefore(tag) }

    override suspend fun offeneAbTag(abTag: LocalDate): List<Faelligkeit> =
        stand.value.filter { it.status == FaelligkeitStatus.OFFEN && !it.periodeBis.isBefore(abTag) }

    override suspend fun aktualisieren(faelligkeit: Faelligkeit) {
        stand.value = stand.value.map { if (it.id == faelligkeit.id) faelligkeit else it }
    }

    override fun beobachteTag(tag: LocalDate): Flow<List<Faelligkeit>> =
        stand.map { liste ->
            liste
                .filter { !it.periodeBis.isBefore(tag) && !it.periodeVon.isAfter(tag) }
                .sortedWith(tagOrdnung)
        }

    /** Spiegelt exakt das Praedikat aus FaelligkeitDao.entfernenKuenftigeOffene. */
    override suspend fun entfernenKuenftigeOffene(aktivitaetId: Long, abTag: LocalDate) {
        stand.value = stand.value.filterNot {
            it.aktivitaetId == aktivitaetId &&
                it.status == FaelligkeitStatus.OFFEN &&
                it.istAnzahl == 0 &&
                !it.periodeVon.isBefore(abTag)
        }
    }
}

class FakeBuchungRepository(start: List<Buchung> = emptyList()) : BuchungRepository {
    val stand = MutableStateFlow(start)

    override suspend fun buchen(buchungen: List<Buchung>) {
        stand.value = stand.value + buchungen
    }

    override fun beobachteKontostand(): Flow<Int> = stand.map { liste -> liste.sumOf { it.betrag } }

    override suspend fun kontostand(): Int = stand.value.sumOf { it.betrag }

    override suspend fun serienbonusSchwellenSeit(aktivitaetId: Long, seit: LocalDateTime): Set<Int> =
        stand.value
            .filter {
                it.art == BuchungsArt.SERIENBONUS &&
                    it.aktivitaetId == aktivitaetId &&
                    !it.zeitpunkt.isBefore(seit)
            }
            .mapNotNull { it.schwelle }
            .toSet()

    override suspend fun tagesbonusGebuchtAm(tag: LocalDate): Boolean =
        stand.value.any {
            it.art == BuchungsArt.TAGESBONUS && it.zeitpunkt.toLocalDate() == tag
        }
}

class FakeErinnerungRepository(start: List<Erinnerung> = emptyList()) : ErinnerungRepository {
    val stand = MutableStateFlow(start)
    private var naechsteId = (start.maxOfOrNull { it.id } ?: 0L) + 1

    private fun schluessel(e: Erinnerung) = "${e.faelligkeitId}|${e.zeitpunkt}|${e.versuch}"

    override suspend fun anlegenFallsNeu(neue: List<Erinnerung>) {
        val vorhanden = stand.value.map(::schluessel).toMutableSet()
        val ergaenzt = stand.value.toMutableList()
        neue.forEach { e -> if (vorhanden.add(schluessel(e))) ergaenzt += e.copy(id = naechsteId++) }
        stand.value = ergaenzt.sortedBy { it.zeitpunkt }
    }

    override suspend fun naechsteOffene(): Erinnerung? =
        stand.value.filter { !it.ausgeloest }.minByOrNull { it.zeitpunkt }

    override suspend fun faelligeBis(zeitpunkt: LocalDateTime): List<Erinnerung> =
        stand.value.filter { !it.ausgeloest && !it.zeitpunkt.isAfter(zeitpunkt) }
            .sortedBy { it.zeitpunkt }

    override suspend fun alsAusgeloestMarkieren(id: Long) {
        stand.value = stand.value.map { if (it.id == id) it.copy(ausgeloest = true) else it }
    }

    override suspend fun entfernenFuerFaelligkeit(faelligkeitId: Long) {
        stand.value = stand.value.filterNot { it.faelligkeitId == faelligkeitId && !it.ausgeloest }
    }
}

class FakeBelohnungRepository(
    start: List<Belohnung> = emptyList(),
) : BelohnungRepository {
    val stand = MutableStateFlow(start)
    val einloesungen = MutableStateFlow<List<Einloesung>>(emptyList())
    private var naechsteId = (start.maxOfOrNull { it.id } ?: 0L) + 1

    /** Spiegelt "SELECT * FROM belohnung WHERE archiviert = 0 ORDER BY preisPunkte". */
    override fun beobachteAktive(): Flow<List<Belohnung>> =
        stand.map { liste -> liste.filterNot { it.archiviert }.sortedBy { it.preisPunkte } }

    /** Spiegelt "SELECT * FROM belohnung ORDER BY archiviert, preisPunkte" -- aktive vor
     * archivierten, jeweils nach Preis. */
    override suspend fun alle(): List<Belohnung> =
        stand.value.sortedWith(compareBy({ it.archiviert }, { it.preisPunkte }))

    override suspend fun nachId(id: Long): Belohnung? = stand.value.find { it.id == id }

    override suspend fun speichern(belohnung: Belohnung): Long {
        if (belohnung.id == 0L) {
            val id = naechsteId++
            stand.value = stand.value + belohnung.copy(id = id)
            return id
        }
        // Spiegelt Room's @Update: ohne passenden Datensatz gibt es keinen stillen Insert.
        check(stand.value.any { it.id == belohnung.id }) {
            "Belohnung mit id=${belohnung.id} konnte nicht aktualisiert werden -- " +
                "kein passender Datensatz vorhanden"
        }
        stand.value = stand.value.map { if (it.id == belohnung.id) belohnung else it }
        return belohnung.id
    }

    override suspend fun archivieren(id: Long) {
        stand.value = stand.value.map { if (it.id == id) it.copy(archiviert = true) else it }
    }

    override suspend fun einloesungAnlegen(einloesung: Einloesung) {
        einloesungen.value = einloesungen.value + einloesung.copy(id = einloesungen.value.size + 1L)
    }

    /** Spiegelt "ORDER BY zeitpunkt DESC, id DESC". */
    private val einloesungOrdnung: Comparator<Einloesung> =
        compareByDescending<Einloesung> { it.zeitpunkt }.thenByDescending { it.id }

    override fun beobachteEinloesungen(): Flow<List<Einloesung>> =
        einloesungen.map { liste -> liste.sortedWith(einloesungOrdnung) }

    override suspend fun alleEinloesungen(): List<Einloesung> =
        einloesungen.value.sortedWith(einloesungOrdnung)
}

/** Fuehrt den Block direkt aus. Die Fakes sind ohnehin single-threaded. */
class FakeTransaktionen : Transaktionen {
    var aufrufe = 0
        private set

    override suspend fun <T> inTransaktion(block: suspend () -> T): T {
        aufrufe++
        return block()
    }
}
