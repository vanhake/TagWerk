package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.ErinnerungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import de.vanhake.dayorganizer.widget.WidgetAktualisierung
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Raeumt vor einer Neumaterialisierung auf: wird eine Aktivitaet archiviert oder ihr
 * Zeitplan geaendert, muessen ihre noch nicht begonnenen kuenftigen Faelligkeiten samt
 * Erinnerungen verschwinden, sonst laufen alte und neue Termine parallel weiter (siehe
 * Finding 1 und 2 der Branch-Review: verwaiste Erinnerungen und faelschlich verpasste
 * Faelligkeiten). Ein bereits teilweise erfuelltes Zaehlziel (istAnzahl > 0) und alles
 * Vergangene bleiben unangetastet -- der Fortschritt gehoert dem Nutzer.
 *
 * Die Faelligkeiten-Fake und die Erinnerungen-Fake sind in Tests unabhaengige Objekte
 * ohne echte Fremdschluesselbeziehung; darum bekommt dieser Use Case beide Repositories
 * und loescht die betroffenen Erinnerungen selbst, statt eine Fremdschluessel-Kaskade
 * zwischen zwei Fakes nachzubauen. In der echten Datenbank erledigt das ON DELETE CASCADE
 * ohnehin mit, der Aufruf hier ist dort redundant, aber harmlos.
 */
class AktivitaetNeuPlanen(
    private val faelligkeiten: FaelligkeitRepository,
    private val erinnerungen: ErinnerungRepository,
    private val aktivitaeten: AktivitaetRepository,
    private val materialisieren: FaelligkeitenMaterialisieren,
    private val uhr: Clock,
    private val alarmNeuPlanen: suspend () -> Unit,
    private val widgetAnstossen: suspend () -> Unit,
) {

    @Inject
    constructor(
        faelligkeiten: FaelligkeitRepository,
        erinnerungen: ErinnerungRepository,
        aktivitaeten: AktivitaetRepository,
        materialisieren: FaelligkeitenMaterialisieren,
        uhr: Clock,
        alarmPlaner: AlarmPlaner,
        widgetAktualisierung: WidgetAktualisierung,
    ) : this(
        faelligkeiten,
        erinnerungen,
        aktivitaeten,
        materialisieren,
        uhr,
        { alarmPlaner.neuPlanen() },
        { widgetAktualisierung.anstossen() },
    )

    suspend operator fun invoke(aktivitaetId: Long) {
        val heute = LocalDate.now(uhr)

        // Die offene Faelligkeit einer Einmalaufgabe hat schon begonnen und bleibt darum
        // stehen; ihre Erinnerungen haengen aber an Deadline und Uhrzeit und muessen neu
        // entstehen. Bereits Ausgeloestes geht dabei verloren, das Materialisieren legt nur
        // kuenftige Termine wieder an.
        val einmalig = aktivitaeten.nachId(aktivitaetId)?.zeitplan is Zeitplan.Einmalig

        faelligkeiten.historie(aktivitaetId)
            .filter {
                it.status == FaelligkeitStatus.OFFEN &&
                    (einmalig || (it.istAnzahl == 0 && !it.periodeVon.isBefore(heute)))
            }
            .forEach { erinnerungen.entfernenFuerFaelligkeit(it.id) }

        faelligkeiten.entfernenKuenftigeOffene(aktivitaetId, heute)

        materialisieren()
        alarmNeuPlanen()
        widgetAnstossen()
    }
}
