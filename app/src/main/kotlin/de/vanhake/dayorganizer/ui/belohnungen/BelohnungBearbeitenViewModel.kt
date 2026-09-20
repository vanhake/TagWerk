package de.vanhake.dayorganizer.ui.belohnungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BelohnungBearbeitenViewModel @Inject constructor(
    private val belohnungen: BelohnungRepository,
    private val aktivitaeten: AktivitaetRepository,
) : ViewModel() {

    private val _entwurf = MutableStateFlow(BelohnungEntwurf())
    val entwurf: StateFlow<BelohnungEntwurf> = _entwurf.asStateFlow()

    private val _fehler = MutableStateFlow<List<BelohnungFehler>>(emptyList())
    val fehler: StateFlow<List<BelohnungFehler>> = _fehler.asStateFlow()

    private val _auswahl = MutableStateFlow<List<Aktivitaet>>(emptyList())
    val auswahl: StateFlow<List<Aktivitaet>> = _auswahl.asStateFlow()

    fun laden(id: Long) = viewModelScope.launch {
        _auswahl.value = aktivitaeten.alleAktiven()
        if (id == 0L) return@launch
        belohnungen.nachId(id)?.let { _entwurf.value = BelohnungEntwurf.aus(it) }
    }

    fun aendern(block: (BelohnungEntwurf) -> BelohnungEntwurf) {
        _entwurf.value = block(_entwurf.value)
    }

    suspend fun speichern(): Boolean {
        val fehler = _entwurf.value.pruefen()
        _fehler.value = fehler
        if (fehler.isNotEmpty()) return false
        belohnungen.speichern(_entwurf.value.zuBelohnung())
        return true
    }
}
