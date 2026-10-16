package de.vanhake.dayorganizer.ui.statistik

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.StatistikRechner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class StatistikViewModel(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val rechner: StatistikRechner,
    private val uhr: Clock,
    // Injizierbar, damit Tests die Berechnung auf ihrem Test-Dispatcher laufen lassen: auf
    // einem echten Default-Thread kam der Zustand auf einem ausgelasteten CI-Runner nicht
    // innerhalb von Turbines 3-s-Timeout an (flaky StatistikViewModelTest).
    private val rechenDispatcher: CoroutineDispatcher,
) : ViewModel() {

    @Inject
    constructor(
        aktivitaeten: AktivitaetRepository,
        faelligkeiten: FaelligkeitRepository,
        buchungen: BuchungRepository,
        rechner: StatistikRechner,
        uhr: Clock,
    ) : this(aktivitaeten, faelligkeiten, buchungen, rechner, uhr, Dispatchers.Default)

    private val zeitraum = MutableStateFlow(30)

    val zustand: StateFlow<StatistikZustand> = zeitraum
        .map { tage -> zusammenstellen(tage) }
        // Die Berechnung skaliert mit einem Jahr mal allen Faelligkeiten (tagesBilanzen
        // ist O(Tage * Faelligkeiten)) -- ohne Dispatcher-Wechsel liefe das auf dem
        // Hauptthread, weil Korutinen nach jedem suspend-Aufruf in ihren Ausgangskontext
        // zurueckkehren (hier viewModelScope == Dispatchers.Main.immediate).
        .flowOn(rechenDispatcher)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatistikZustand())

    /** Erlaubt sind 30, 90 und 365 Tage. */
    fun zeitraumWaehlen(tage: Int) {
        zeitraum.value = tage
    }

    private suspend fun zusammenstellen(tage: Int): StatistikZustand {
        val heute = LocalDate.now(uhr)
        val von = heute.minusDays(tage - 1L)

        val imZeitraum = faelligkeiten.fuerZeitraum(von, heute)
        val liste = aktivitaeten.alleAktiven()

        val bilanzen = rechner.tagesBilanzen(imZeitraum, von, heute)

        return StatistikZustand(
            zeitraumTage = tage,
            bilanzen = bilanzen,
            gesamtQuote = gesamtQuoteProzent(bilanzen),
            zeilen = liste.map { aktivitaet ->
                StatistikZeile(
                    aktivitaetId = aktivitaet.id,
                    name = aktivitaet.name,
                    symbol = aktivitaet.symbol,
                    serieInWochen = aktivitaet.zeitplan is Zeitplan.Wochenziel,
                    statistik = rechner.fuerAktivitaet(
                        aktivitaetId = aktivitaet.id,
                        faelligkeiten = faelligkeiten.historie(aktivitaet.id),
                        heute = heute,
                    ),
                )
            },
            // punkteVerlauf bildet den Anfangsbestand aus allen Buchungen VOR "von" (siehe
            // StatistikRechnerTest."der Punkteverlauf beginnt beim Stand vor dem Zeitraum").
            // fuerZeitraum(von, heute) enthaelt per Konstruktion nichts vor "von" -- damit
            // waere der Anfangsbestand immer 0 und herauskaeme die Netto-Aenderung seit "von"
            // statt des tatsaechlichen Kontostands. Deshalb hier bewusst alle().
            punkteVerlauf = rechner.punkteVerlauf(buchungen.alle(), von, heute),
        )
    }
}
