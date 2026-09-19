package de.vanhake.dayorganizer.ui.heute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassen
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigen
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import de.vanhake.dayorganizer.platform.permission.BerechtigungsHinweise
import de.vanhake.dayorganizer.platform.permission.BerechtigungsPruefer
import de.vanhake.dayorganizer.platform.permission.Hinweis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class HeuteViewModel(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val erledigenUseCase: FaelligkeitErledigen,
    private val auslassenUseCase: FaelligkeitAuslassen,
    private val alarmNeuPlanen: suspend () -> Unit,
    private val hinweiseLesen: () -> List<Hinweis>,
    private val uhr: Clock,
) : ViewModel() {

    @Inject
    constructor(
        aktivitaeten: AktivitaetRepository,
        faelligkeiten: FaelligkeitRepository,
        buchungen: BuchungRepository,
        erledigenUseCase: FaelligkeitErledigen,
        auslassenUseCase: FaelligkeitAuslassen,
        alarmPlaner: AlarmPlaner,
        pruefer: BerechtigungsPruefer,
        uhr: Clock,
    ) : this(
        aktivitaeten, faelligkeiten, buchungen, erledigenUseCase, auslassenUseCase,
        { alarmPlaner.neuPlanen() }, { BerechtigungsHinweise.fuer(pruefer.stand()) }, uhr,
    )

    private val hinweise = MutableStateFlow<List<Hinweis>>(emptyList())

    val zustand: StateFlow<HeuteZustand> = combine(
        faelligkeiten.beobachteTag(LocalDate.now(uhr)),
        aktivitaeten.beobachteAktive(),
        buchungen.beobachteKontostand(),
        hinweise,
    ) { faelligkeiten, aktivitaeten, kontostand, hinweise ->
        HeuteZusammenstellung.aus(faelligkeiten, aktivitaeten, kontostand, LocalDateTime.now(uhr))
            .copy(hinweise = hinweise)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeuteZustand())

    /**
     * Wird bei jedem Sichtbarwerden des Screens aufgerufen, nicht nur einmal:
     * der Nutzer kommt aus den Systemeinstellungen zurueck, und dann muss der
     * Banner verschwinden.
     */
    fun berechtigungenPruefen() {
        hinweise.value = hinweiseLesen()
    }

    // Property und Methode heissen bewusst unterschiedlich: gleiche Namen
    // kompilieren zwar, lesen sich aber wie ein Selbstaufruf.
    fun erledigen(faelligkeitId: Long) = viewModelScope.launch {
        erledigenUseCase(faelligkeitId)
        alarmNeuPlanen()
    }

    fun auslassen(faelligkeitId: Long) = viewModelScope.launch {
        auslassenUseCase(faelligkeitId)
        alarmNeuPlanen()
    }
}
