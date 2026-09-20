package de.vanhake.dayorganizer.ui.statistik

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.rechner.StatistikRechner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class StatistikViewModel @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val rechner: StatistikRechner,
    private val uhr: Clock,
) : ViewModel() {

    private val zeitraum = MutableStateFlow(30)

    val zustand: StateFlow<StatistikZustand> = zeitraum
        .map { tage -> zusammenstellen(tage) }
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

        return StatistikZustand(
            zeitraumTage = tage,
            bilanzen = rechner.tagesBilanzen(imZeitraum, von, heute),
            zeilen = liste.map { aktivitaet ->
                AktivitaetZeile(
                    aktivitaetId = aktivitaet.id,
                    name = aktivitaet.name,
                    statistik = rechner.fuerAktivitaet(
                        aktivitaetId = aktivitaet.id,
                        faelligkeiten = faelligkeiten.historie(aktivitaet.id),
                        heute = heute,
                    ),
                )
            },
            punkteVerlauf = rechner.punkteVerlauf(buchungen.fuerZeitraum(von, heute), von, heute),
        )
    }
}
