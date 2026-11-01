package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.data.repository.Transaktionen
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.ErledigungsKontext
import de.vanhake.dayorganizer.domain.rechner.PunkteRechner
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * NICHT_OFFEN deckt alle drei geschlossenen Zustaende ab: ERLEDIGT (schon gebucht),
 * AUSGELASSEN (bewusst uebersprungen -- keine Punkte, aber die Serie ueberlebt) und
 * VERPASST (vom naechtlichen Watchdog geschlossen -- die Serie bricht). Keiner davon
 * darf nachtraeglich durch einen zweiten Aufruf gebucht werden. Ebenso NICHT_OFFEN ist eine
 * noch offene Faelligkeit, deren Periode schon abgelaufen ist: sie wird dabei als VERPASST
 * geschlossen, ohne Punkte. Einzige Ausnahme ist die
 * verpasste Einmalaufgabe: sie laesst sich einmal nachtraeglich abhaken, bucht dabei aber
 * nichts (siehe nachtraeglichErledigen).
 */
enum class ErledigungsErgebnis { GEBUCHT, NICHT_OFFEN, UNBEKANNT }

/**
 * Reichhaltigeres Ergebnis fuer Aufrufer, die eine Erledigung spaeter rueckgaengig
 * machen koennen muessen (siehe FaelligkeitErledigenRueckgaengig) -- ErledigungsErgebnis
 * bleibt fuer die bestehenden Aufrufer (Benachrichtigung, Widget) unveraendert, die den
 * Rueckgabewert ohnehin verwerfen.
 */
sealed interface ErledigungsProtokoll {
    data class Gebucht(val faelligkeitVorher: Faelligkeit, val buchungen: List<Buchung>) : ErledigungsProtokoll
    data object NichtOffen : ErledigungsProtokoll

    /** Die Periode war schon vorbei: als VERPASST geschlossen, nichts gebucht. */
    data class Abgelaufen(val faelligkeitVorher: Faelligkeit) : ErledigungsProtokoll
    data object Unbekannt : ErledigungsProtokoll
}

/**
 * Der primaere Konstruktor nimmt die Einstellungen als Funktion entgegen, damit
 * der Test ohne DataStore auskommt. Dagger benutzt den sekundaeren Konstruktor;
 * genau einer darf @Inject tragen.
 */
class FaelligkeitErledigen(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val punkteRechner: PunkteRechner,
    private val serienRechner: SerienRechner,
    private val transaktionen: Transaktionen,
    private val einstellungenLesen: suspend () -> Einstellungen,
    private val uhr: Clock,
) {

    @Inject
    constructor(
        aktivitaeten: AktivitaetRepository,
        faelligkeiten: FaelligkeitRepository,
        buchungen: BuchungRepository,
        punkteRechner: PunkteRechner,
        serienRechner: SerienRechner,
        transaktionen: Transaktionen,
        speicher: EinstellungenSpeicher,
        uhr: Clock,
    ) : this(
        aktivitaeten, faelligkeiten, buchungen, punkteRechner, serienRechner,
        transaktionen, { speicher.lesenOderWerfen() }, uhr,
    )

    // Lesen, Pruefen, Schreiben und Buchen liegen zusammen in der Transaktion.
    // Nur so trennt die Statuspruefung zwei gleichzeitige Aufrufer.
    suspend operator fun invoke(faelligkeitId: Long): ErledigungsErgebnis =
        when (val protokoll = mitProtokoll(faelligkeitId)) {
            is ErledigungsProtokoll.Gebucht -> ErledigungsErgebnis.GEBUCHT
            ErledigungsProtokoll.NichtOffen, is ErledigungsProtokoll.Abgelaufen -> ErledigungsErgebnis.NICHT_OFFEN
            ErledigungsProtokoll.Unbekannt -> ErledigungsErgebnis.UNBEKANNT
        }

    suspend fun mitProtokoll(faelligkeitId: Long): ErledigungsProtokoll =
        transaktionen.inTransaktion { erledige(faelligkeitId) }

    private suspend fun erledige(faelligkeitId: Long): ErledigungsProtokoll {
        val vorher = faelligkeiten.nachId(faelligkeitId) ?: return ErledigungsProtokoll.Unbekannt
        val offen = vorher.status == FaelligkeitStatus.OFFEN
        // VERPASST kommt nur bei einer Einmalaufgabe weiter, dafuer braucht es die Aktivitaet.
        if (!offen && vorher.status != FaelligkeitStatus.VERPASST) return ErledigungsProtokoll.NichtOffen
        val aktivitaet = aktivitaeten.nachId(vorher.aktivitaetId)
            ?: return if (offen) ErledigungsProtokoll.Unbekannt else ErledigungsProtokoll.NichtOffen

        val jetzt = LocalDateTime.now(uhr)
        val heute = LocalDate.now(uhr)

        // Abgelaufen ist eine Faelligkeit, sobald ihre Periode (bei Einmalaufgaben die
        // Deadline) vor heute endet. Punkte gibt es dann in keinem Fall mehr -- auch nicht,
        // wenn der Waechter sie noch nicht als verpasst geschlossen hat (er laeuft nur
        // naechtlich und kann sich verspaeten; Benachrichtigung, Widget oder ein ueber
        // Mitternacht offener Heute-Screen zeigen sie bis dahin weiter als offen an).
        val plan = aktivitaet.zeitplan
        val fristEnde = if (plan is Zeitplan.Einmalig) minOf(plan.deadline, vorher.periodeBis) else vorher.periodeBis
        if (fristEnde.isBefore(heute)) {
            // Eine Einmalaufgabe laesst sich einmal nachtraeglich abhaken, bucht aber nichts.
            if (plan is Zeitplan.Einmalig) return nachtraeglichErledigen(vorher, jetzt)
            return abgelaufenSchliessen(vorher)
        }
        if (vorher.status != FaelligkeitStatus.OFFEN) return ErledigungsProtokoll.NichtOffen
        val istNeu = vorher.istAnzahl + 1
        val erfuellt = istNeu >= vorher.sollAnzahl

        val nachher = vorher.copy(
            istAnzahl = istNeu,
            status = if (erfuellt) FaelligkeitStatus.ERLEDIGT else FaelligkeitStatus.OFFEN,
            erledigtAm = jetzt,
        )

        val historie = faelligkeiten.historie(aktivitaet.id)
            .map { if (it.id == nachher.id) nachher else it }
        val serie = serienRechner.aktuelleSerieMitStart(historie, nachher.periodeVon)
        val gebuchteSchwellen = serie.startPeriode
            ?.let { buchungen.serienbonusSchwellenSeit(aktivitaet.id, it.atStartOfDay()) }
            ?: emptySet()

        // fuerZeitraum(heute, heute) liefert auch Wochenziele zurueck, deren Periode diesen
        // Tag ueberlappt (periodeBis >= heute && periodeVon <= heute) -- das gilt fuer jeden
        // Tag ihrer Woche. Ein Wochenziel gehoert aber der Woche, nicht dem Dienstag: ohne
        // diesen Filter wuerde ein noch offenes Wochenziel den Tagesbonus sechs von sieben
        // Tagen blockieren. Nur echte Tagesitems (periodeVon == periodeBis) zaehlen daher.
        val tagesFaelligkeiten = faelligkeiten.fuerZeitraum(heute, heute)
            .map { if (it.id == nachher.id) nachher else it }
            .filter { it.periodeVon == it.periodeBis }

        val einstellungen = einstellungenLesen()
        val kontext = ErledigungsKontext(
            aktivitaet = aktivitaet,
            faelligkeitVorher = vorher,
            zeitpunkt = jetzt,
            serieNachher = serie.laenge,
            gebuchteSchwellen = gebuchteSchwellen,
            tagesFaelligkeitenNachher = tagesFaelligkeiten,
            tagesbonusPunkte = einstellungen.tagesbonusPunkte,
            tagesbonusBereitsGebucht = buchungen.tagesbonusGebuchtAm(heute),
            verspaetungsRegel = einstellungen.verspaetung,
        )

        val gebuchteZeilen = punkteRechner.buchungen(kontext)

        faelligkeiten.aktualisieren(
            nachher.copy(warPuenktlich = punkteRechner.istPuenktlich(kontext)),
        )
        buchungen.buchen(gebuchteZeilen)
        return ErledigungsProtokoll.Gebucht(faelligkeitVorher = vorher, buchungen = gebuchteZeilen)
    }

    /**
     * Eine wiederkehrende Faelligkeit, deren Periode schon vorbei ist: sie wird sofort so
     * geschlossen, wie es der Waechter beim naechsten Lauf ohnehin taete (VERPASST, die Serie
     * bricht), und es wird nichts gebucht.
     */
    private suspend fun abgelaufenSchliessen(vorher: Faelligkeit): ErledigungsProtokoll {
        if (vorher.status != FaelligkeitStatus.OFFEN) return ErledigungsProtokoll.NichtOffen
        faelligkeiten.aktualisieren(vorher.copy(status = FaelligkeitStatus.VERPASST))
        return ErledigungsProtokoll.Abgelaufen(vorher)
    }

    /**
     * Haekt eine Einmalaufgabe nach ihrer Deadline ab. Sie bleibt VERPASST -- Quote und
     * Statistik sollen die versaeumte Deadline weiter zeigen --, traegt aber Erledigungszeit
     * und Ist-Anzahl, damit die Oberflaeche "zu spaet erledigt" anzeigen kann. Gebucht wird
     * nichts; das leere Protokoll laesst sich trotzdem ruecknehmen.
     */
    private suspend fun nachtraeglichErledigen(
        vorher: Faelligkeit,
        jetzt: LocalDateTime,
    ): ErledigungsProtokoll {
        val offenOderVerpasst = vorher.status == FaelligkeitStatus.OFFEN ||
            vorher.status == FaelligkeitStatus.VERPASST
        if (!offenOderVerpasst || vorher.erledigtAm != null) return ErledigungsProtokoll.NichtOffen

        faelligkeiten.aktualisieren(
            vorher.copy(
                istAnzahl = vorher.sollAnzahl,
                status = FaelligkeitStatus.VERPASST,
                erledigtAm = jetzt,
            ),
        )
        return ErledigungsProtokoll.Gebucht(faelligkeitVorher = vorher, buchungen = emptyList())
    }
}
