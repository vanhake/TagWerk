package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.rechner.ErledigungsKontext
import de.vanhake.dayorganizer.domain.rechner.PunkteRechner
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

enum class ErledigungsErgebnis { GEBUCHT, BEREITS_ERLEDIGT, UNBEKANNT }

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
        speicher: EinstellungenSpeicher,
        uhr: Clock,
    ) : this(
        aktivitaeten, faelligkeiten, buchungen, punkteRechner, serienRechner,
        { speicher.lesen() }, uhr,
    )

    suspend operator fun invoke(faelligkeitId: Long): ErledigungsErgebnis {
        val vorher = faelligkeiten.nachId(faelligkeitId) ?: return ErledigungsErgebnis.UNBEKANNT
        if (vorher.status == FaelligkeitStatus.ERLEDIGT) return ErledigungsErgebnis.BEREITS_ERLEDIGT
        val aktivitaet = aktivitaeten.nachId(vorher.aktivitaetId)
            ?: return ErledigungsErgebnis.UNBEKANNT

        val jetzt = LocalDateTime.now(uhr)
        val heute = LocalDate.now(uhr)
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

        val tagesFaelligkeiten = faelligkeiten.fuerZeitraum(heute, heute)
            .map { if (it.id == nachher.id) nachher else it }

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
        )

        faelligkeiten.aktualisieren(
            nachher.copy(warPuenktlich = punkteRechner.istPuenktlich(kontext)),
        )
        buchungen.buchen(punkteRechner.buchungen(kontext))
        return ErledigungsErgebnis.GEBUCHT
    }
}
