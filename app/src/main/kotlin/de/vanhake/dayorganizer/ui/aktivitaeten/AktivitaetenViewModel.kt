package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
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
    private val uhr: Clock,
) : ViewModel() {

    val liste: StateFlow<List<Aktivitaet>> = aktivitaeten.beobachteAktive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun archivieren(id: Long) = viewModelScope.launch {
        aktivitaeten.archivieren(id, LocalDate.now(uhr))
    }
}
