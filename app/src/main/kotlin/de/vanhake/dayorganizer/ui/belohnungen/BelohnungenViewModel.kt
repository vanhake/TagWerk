package de.vanhake.dayorganizer.ui.belohnungen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.domain.rechner.Hindernis
import de.vanhake.dayorganizer.domainuse.BelohnungEinloesen
import de.vanhake.dayorganizer.domainuse.BelohnungEinloesenRueckgaengig
import de.vanhake.dayorganizer.domainuse.EinloeseErgebnis
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import de.vanhake.dayorganizer.util.versuchen
import de.vanhake.dayorganizer.widget.WidgetAktualisierung
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BelohnungenViewModel(
    private val belohnungen: BelohnungRepository,
    private val buchungen: BuchungRepository,
    private val einloesenUseCase: BelohnungEinloesen,
    private val einloesenRueckgaengig: BelohnungEinloesenRueckgaengig,
    private val widgetAnstossen: suspend () -> Unit,
) : ViewModel() {

    @Inject
    constructor(
        belohnungen: BelohnungRepository,
        buchungen: BuchungRepository,
        einloesenUseCase: BelohnungEinloesen,
        einloesenRueckgaengig: BelohnungEinloesenRueckgaengig,
        widgetAktualisierung: WidgetAktualisierung,
    ) : this(
        belohnungen,
        buchungen,
        einloesenUseCase,
        einloesenRueckgaengig,
        { widgetAktualisierung.anstossen() },
    )

    // Sperre gegen einen Doppeltipp: die Transaktion im Use Case pruefte zwar Kontostand
    // und Bedingung, aber zwei fuer sich gueltige Einloesungen (z. B. 450 Punkte, Preis 200)
    // sind beide erlaubt -- ohne diese Sperre bucht ein Doppeltipp zweimal. Anders als bei
    // FaelligkeitErledigen gibt es keine serverseitige Idempotenz zu pruefen: jede Einloesung
    // darf eine neue sein, also muss die Sperre hier, auf Seiten des Aufrufs, sitzen.
    private val _laufendeEinloesungen = MutableStateFlow<Set<Long>>(emptySet())

    // Eigener StateFlow statt Teil der combine-Kette unten: der Zustandsfluss kommt seit
    // Plan 2 ueber flowOn(Dispatchers.Default) von einem echten Thread und ist in Tests
    // nicht deterministisch nach advanceUntilIdle() zu pruefen (siehe Kommentar an
    // einloesen()). Die Meldung wird dagegen synchron im viewModelScope gesetzt, damit ein
    // Test sie direkt nach advanceUntilIdle() zusichern kann.
    private val _meldung = MutableStateFlow<EinloeseMeldung?>(null)
    val meldung: StateFlow<EinloeseMeldung?> = _meldung.asStateFlow()

    // Nur bei EinloeseMeldung.ZU_WENIG_PUNKTE relevant: noch_punkte_noetig braucht die
    // fehlenden Punkte als Platzhalter. Enum-Konstanten koennen keinen Wert pro Aufruf
    // tragen, deshalb ein eigenes, synchron mit _meldung gesetztes StateFlow.
    private val _meldungFehlendePunkte = MutableStateFlow(0)
    val meldungFehlendePunkte: StateFlow<Int> = _meldungFehlendePunkte.asStateFlow()

    // Eigener Flow wie in HeuteViewModel: synchron gesetzt, im Test deterministisch.
    private val _toast = MutableStateFlow<Hinweistoast?>(null)
    val toast: StateFlow<Hinweistoast?> = _toast.asStateFlow()
    private var ausstehendesRueckgaengig: (suspend () -> Unit)? = null
    private var toastJob: Job? = null

    val zustand: StateFlow<BelohnungenZustand> = combine(
        belohnungen.beobachteAktive(),
        buchungen.beobachteKontostand(),
        belohnungen.beobachteEinloesungen(),
        _laufendeEinloesungen,
    ) { liste, kontostand, einloesungen, laufende ->
        BelohnungenZustand(
            kontostand = kontostand,
            kacheln = liste.map { belohnung ->
                BelohnungKachel(
                    id = belohnung.id,
                    name = belohnung.name,
                    beschreibung = belohnung.beschreibung,
                    symbol = belohnung.symbol,
                    preisPunkte = belohnung.preisPunkte,
                    einloesbarkeit = einloesenUseCase.einloesbarkeit(belohnung, kontostand),
                    wirdEingeloest = belohnung.id in laufende,
                )
            },
            einloesungen = einloesungen.sichtbare(),
        )
    }
        // SerienRechner.aktuelleSerie laeuft je Emission und je Kachel mit Serienbedingung
        // ueber die volle Historie -- ohne Dispatcher-Wechsel liefe das auf dem Hauptthread,
        // siehe StatistikViewModel fuer dasselbe Muster.
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BelohnungenZustand())

    // Property und Methode heissen bewusst unterschiedlich, siehe HeuteViewModel.
    //
    // Die Sperre wird synchron gesetzt, bevor ueberhaupt etwas gestartet wird -- ein
    // zweiter, synchron folgender Aufruf (der klassische Doppeltipp) sieht die Sperre
    // also garantiert, unabhaengig davon, ob die Koroutine schon zum Zug kam. Sie faellt
    // im finally auch dann wieder, wenn der Aufruf mit einer Ausnahme endet, sonst bliebe
    // die Kachel dauerhaft gesperrt.
    fun einloesen(belohnungId: Long) {
        if (belohnungId in _laufendeEinloesungen.value) return
        _laufendeEinloesungen.value = _laufendeEinloesungen.value + belohnungId
        viewModelScope.launch {
            try {
                // Eine Datenbankausnahme darf nicht bis zum viewModelScope durchschlagen
                // und die App mitreissen -- der Nutzer bekommt stattdessen eine allgemeine
                // Fehlermeldung. Abgelehnt/Unbekannt sind kein Fehlschlag des Aufrufs
                // selbst, deshalb faengt onFailure sie nicht ab, sondern meldungFuer().
                versuchen { einloesenUseCase(belohnungId) }
                    .onSuccess { ergebnis -> meldungFuer(ergebnis) }
                    .onFailure { ausnahme ->
                        Log.w(TAG, "Einloesen fehlgeschlagen", ausnahme)
                        _meldung.value = EinloeseMeldung.FEHLER
                    }
                widgetAnstossen()
            } finally {
                _laufendeEinloesungen.value = _laufendeEinloesungen.value - belohnungId
            }
        }
    }

    /** Setzt [_meldung] fuer alles ausser einem Erfolg; ein Erfolg wird als Toast mit
     * Rueckgaengig gezeigt. */
    private fun meldungFuer(ergebnis: EinloeseErgebnis) {
        when (ergebnis) {
            is EinloeseErgebnis.Erfolg -> zeigeToast(
                BelohnungenToastTexte.eingeloest(ergebnis.einloesung.name, ergebnis.einloesung.preisPunkte),
                rueckgaengigAktion = { einloesenRueckgaengig(ergebnis.einloesung) },
            )

            is EinloeseErgebnis.Abgelehnt -> when (val hindernis = ergebnis.einloesbarkeit.hindernis) {
                Hindernis.ZU_WENIG_PUNKTE -> {
                    _meldungFehlendePunkte.value = ergebnis.einloesbarkeit.fehlendePunkte
                    _meldung.value = EinloeseMeldung.ZU_WENIG_PUNKTE
                }
                Hindernis.SERIE_ZU_KURZ -> _meldung.value = EinloeseMeldung.SERIE_ZU_KURZ
                Hindernis.ZU_FRUEH -> _meldung.value = EinloeseMeldung.ZU_FRUEH
                // null: die Belohnung ist archiviert -- fuer den Nutzer nicht von einer
                // geloeschten/verschwundenen Belohnung zu unterscheiden.
                null -> _meldung.value = EinloeseMeldung.NICHT_MEHR_VORHANDEN
            }

            EinloeseErgebnis.Unbekannt -> _meldung.value = EinloeseMeldung.NICHT_MEHR_VORHANDEN
        }
    }

    /** Vom Screen aufgerufen, nachdem die Snackbar gezeigt wurde -- sonst erschiene dieselbe
     * Meldung bei jeder Rekomposition erneut. Gleiches Muster wie EinstellungenViewModel. */
    fun meldungGezeigt() {
        _meldung.value = null
    }

    fun rueckgaengig() = viewModelScope.launch {
        val aktion = ausstehendesRueckgaengig ?: return@launch
        toastJob?.cancel()
        ausstehendesRueckgaengig = null
        _toast.value = null
        // Wie beim Einloesen darf eine Datenbankausnahme nicht die App mitreissen;
        // ein Abbruch ist dagegen kein Fehler und wird weitergereicht.
        try {
            aktion()
        } catch (abbruch: CancellationException) {
            throw abbruch
        } catch (ausnahme: Exception) {
            Log.w(TAG, "Rueckgaengig fehlgeschlagen", ausnahme)
            _meldung.value = EinloeseMeldung.FEHLER
            return@launch
        }
        widgetAnstossen()
    }

    // Kein Rueckgaengig (Entscheidung in Plan 6, wie bei den Aktivitaeten in Plan 5).
    fun archivieren(belohnungId: Long) = viewModelScope.launch {
        val name = belohnungen.nachId(belohnungId)?.name
        belohnungen.archivieren(belohnungId)
        name?.let { zeigeToast(BelohnungenToastTexte.archiviert(it)) }
    }

    /** Vom Screen aufgerufen, wenn der Editor mit einem Ergebnis zurueckgekehrt ist. */
    fun gespeichertMelden(ergebnis: SpeicherErgebnis) {
        zeigeToast(
            when (ergebnis) {
                SpeicherErgebnis.ANGELEGT -> BelohnungenToastTexte.ANGELEGT
                SpeicherErgebnis.GEAENDERT -> BelohnungenToastTexte.GEAENDERT
            },
        )
    }

    private fun zeigeToast(text: String, rueckgaengigAktion: (suspend () -> Unit)? = null) {
        toastJob?.cancel()
        ausstehendesRueckgaengig = rueckgaengigAktion
        _toast.value = Hinweistoast(text, kannRueckgaengig = rueckgaengigAktion != null)
        toastJob = viewModelScope.launch {
            delay(TOAST_DAUER_MS)
            _toast.value = null
            ausstehendesRueckgaengig = null
        }
    }

    private companion object {
        const val TAG = "BelohnungenViewModel"

        /** Wie HeuteViewModel. */
        const val TOAST_DAUER_MS = 3_800L
    }
}

/** Reine Textbausteine, ohne Android-Ressourcen testbar -- Muster HeuteToastTexte. */
private object BelohnungenToastTexte {
    const val ANGELEGT = "Belohnung angelegt"
    const val GEAENDERT = "Änderungen gespeichert"
    fun archiviert(name: String) = "$name archiviert"
    fun eingeloest(name: String, preis: Int) = "$name eingelöst · −$preis Punkte"
}
