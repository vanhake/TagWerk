package de.vanhake.dayorganizer

import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.ErinnerungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.data.repository.Transaktionen
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
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
        val id = if (aktivitaet.id == 0L) naechsteId() else aktivitaet.id
        stand.value = stand.value.filterNot { it.id == id } + aktivitaet.copy(id = id)
        return id
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

    override suspend fun naechsteAb(zeitpunkt: LocalDateTime): Erinnerung? =
        stand.value.filter { !it.ausgeloest && !it.zeitpunkt.isBefore(zeitpunkt) }
            .minByOrNull { it.zeitpunkt }

    override suspend fun faelligeBis(zeitpunkt: LocalDateTime): List<Erinnerung> =
        stand.value.filter { !it.ausgeloest && !it.zeitpunkt.isAfter(zeitpunkt) }
            .sortedBy { it.zeitpunkt }

    override suspend fun alsAusgeloestMarkieren(id: Long) {
        stand.value = stand.value.map { if (it.id == id) it.copy(ausgeloest = true) else it }
    }

    override suspend fun entfernenFuerFaelligkeit(faelligkeitId: Long) {
        stand.value = stand.value.filterNot { it.faelligkeitId == faelligkeitId }
    }
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
