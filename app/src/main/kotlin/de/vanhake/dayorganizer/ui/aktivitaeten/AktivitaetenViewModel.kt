package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domainuse.AktivitaetNeuPlanen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AktivitaetenViewModel @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val neuPlanen: AktivitaetNeuPlanen,
    private val uhr: Clock,
) : ViewModel() {

    val liste: StateFlow<List<Aktivitaet>> = aktivitaeten.beobachteAktive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Ohne AktivitaetNeuPlanen liefen die schon materialisierten kuenftigen Faelligkeiten
    // und ihre Erinnerungen der archivierten Aktivitaet einfach weiter (Finding 1 der
    // Branch-Review): Erinnerungen feuerten fuer etwas, das nirgends mehr sichtbar ist,
    // und die Faelligkeiten wurden am naechsten Mitternachtslauf faelschlich als
    // VERPASST geschlossen. FaelligkeitenMaterialisieren liest nur aktive, nicht
    // archivierte Aktivitaeten (AktivitaetDao.alleAktiven: "aktiv = 1 AND archiviertAm
    // IS NULL"), darum legt der Aufruf hier fuer die gerade archivierte Aktivitaet
    // nichts Neues an -- er raeumt nur auf.
    fun archivieren(id: Long) = viewModelScope.launch {
        aktivitaeten.archivieren(id, LocalDate.now(uhr))
        neuPlanen(id)
    }
}
