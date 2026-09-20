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
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import de.vanhake.dayorganizer.platform.permission.BerechtigungsHinweise
import de.vanhake.dayorganizer.platform.permission.BerechtigungsPruefer
import de.vanhake.dayorganizer.platform.permission.Hinweis
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
    private val anzeige: Erinnerungsanzeige,
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
        anzeige: Erinnerungsanzeige,
        alarmPlaner: AlarmPlaner,
        pruefer: BerechtigungsPruefer,
        uhr: Clock,
    ) : this(
        aktivitaeten, faelligkeiten, buchungen, erledigenUseCase, auslassenUseCase, anzeige,
        { alarmPlaner.neuPlanen() }, { BerechtigungsHinweise.fuer(pruefer.stand()) }, uhr,
    )

    private val hinweise = MutableStateFlow<List<Hinweis>>(emptyList())
    private val tag = MutableStateFlow(LocalDate.now(uhr))

    @OptIn(ExperimentalCoroutinesApi::class)
    val zustand: StateFlow<HeuteZustand> = combine(
        tag.flatMapLatest { faelligkeiten.beobachteTag(it) },
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
     * Banner verschwinden. Ausserdem kann seit dem letzten Sichtbarwerden
     * Mitternacht vergangen sein, also wird auch der beobachtete Tag neu gelesen.
     */
    fun beimSichtbarwerden() {
        tag.value = LocalDate.now(uhr)
        hinweise.value = hinweiseLesen()
    }

    // Property und Methode heissen bewusst unterschiedlich: gleiche Namen
    // kompilieren zwar, lesen sich aber wie ein Selbstaufruf.
    // Ohne anzeige.verstecke() bliebe die Systembenachrichtigung stehen, obwohl der
    // Nutzer die Faelligkeit gerade hier im Screen erledigt/ausgelassen hat -- AktionsEmpfaenger
    // (der Weg ueber die Benachrichtigungsaktionen) versteckt sie schon, dieser Pfad muss
    // dasselbe tun (Finding 3 der Branch-Review).
    fun erledigen(faelligkeitId: Long) = viewModelScope.launch {
        erledigenUseCase(faelligkeitId)
        anzeige.verstecke(faelligkeitId)
        alarmNeuPlanen()
    }

    fun auslassen(faelligkeitId: Long) = viewModelScope.launch {
        auslassenUseCase(faelligkeitId)
        anzeige.verstecke(faelligkeitId)
        alarmNeuPlanen()
    }
}
