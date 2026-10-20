package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.ErinnerungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Erinnerung
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Legt Faelligkeiten und ihre Erstversuch-Erinnerungen fuer den Vorausblick an.
 * Mehrfachaufrufe sind unschaedlich: eindeutige Schluessel schlucken Wiederholungen.
 */
class FaelligkeitenMaterialisieren @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val erinnerungen: ErinnerungRepository,
    private val zeitplanRechner: ZeitplanRechner,
    private val uhr: Clock,
) {
    suspend operator fun invoke(vorausTage: Long = 7) {
        val heute = LocalDate.now(uhr)
        val jetzt = LocalDateTime.now(uhr)
        val bis = heute.plusDays(vorausTage)

        aktivitaeten.alleAktiven().forEach { aktivitaet ->
            deadlineAbgleichen(aktivitaet)
            val faelligkeitenNeu = zeitplanRechner.faelligkeitenFuer(aktivitaet, heute, bis)
                // Eine neue Aktivitaet beginnt zum Anlegezeitpunkt, nicht um Mitternacht
                // (Design): ein frisch berechnetes Item, dessen Zielzeitpunkt schon in der
                // Vergangenheit liegt, wird verworfen statt angelegt. Fuer eine BESTEHENDE
                // Aktivitaet ist das unschaedlich -- ihre vergangenen Items wurden bereits in
                // einem frueheren Lauf angelegt, und anlegenFallsNeu ignoriert sie ueber den
                // Duplikat-Schluessel ohnehin. Der Filter unterdrueckt also ausschliesslich
                // Items, die erst jetzt entstehen und schon bei der Geburt ueberfaellig
                // waeren. Ziele ohne Zielzeitpunkt (geplantFuer == null, Zaehlziele) sind
                // nicht betroffen. Konsequenz: lief die App mehrere Tage nicht, werden die
                // uebersprungenen vergangenen Slots einfach nie angelegt, statt angelegt und
                // sofort als verpasst markiert zu werden -- das ist die gewollte, mildere
                // Wirkung.
                .filter { f -> f.geplantFuer?.isBefore(jetzt) != true }
            faelligkeiten.anlegenFallsNeu(faelligkeitenNeu)
        }

        // Erst nach dem Anlegen lesen: die Faelligkeiten brauchen ihre vergebenen Ids,
        // damit die Erinnerungen darauf zeigen koennen.
        val neueErinnerungen = faelligkeiten.fuerZeitraum(heute, bis).flatMap { f ->
            val aktivitaet = aktivitaeten.nachId(f.aktivitaetId) ?: return@flatMap emptyList()
            zeitplanRechner.anstossZeitpunkteFuer(aktivitaet, f)
                // Eine Einmalaufgabe reicht ueber Wochen; ihre Erinnerungen werden darum auch
                // nach dem Anlegen oder einer verschobenen Deadline neu berechnet. Ein Termin,
                // der dabei schon vorbei ist, wuerde sofort ausloesen -- er entfaellt.
                .filter { aktivitaet.zeitplan !is Zeitplan.Einmalig || !it.isBefore(jetzt) }
                .map { Erinnerung(faelligkeitId = f.id, zeitpunkt = it, versuch = 0) }
        }
        erinnerungen.anlegenFallsNeu(neueErinnerungen)
    }

    /**
     * Die Faelligkeit einer Einmalaufgabe beginnt am Anlegetag und traegt darum denselben
     * Duplikat-Schluessel, auch wenn die Deadline spaeter verschoben wird -- anlegenFallsNeu
     * wuerde die neue Deadline also verschlucken. Stattdessen wird die offene Faelligkeit
     * hier an die aktuelle Deadline angepasst; ihre Erinnerungen fallen weg und entstehen
     * im selben Lauf passend zur neuen Deadline neu.
     */
    private suspend fun deadlineAbgleichen(aktivitaet: Aktivitaet) {
        val plan = aktivitaet.zeitplan as? Zeitplan.Einmalig ?: return
        faelligkeiten.historie(aktivitaet.id)
            .filter { it.status == FaelligkeitStatus.OFFEN && it.periodeBis != plan.deadline }
            .forEach { offen ->
                erinnerungen.entfernenFuerFaelligkeit(offen.id)
                // Eine Deadline vor heute schliesst der Waechter danach als verpasst.
                faelligkeiten.aktualisieren(offen.copy(periodeBis = plan.deadline))
            }
    }
}
