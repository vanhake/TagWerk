package de.vanhake.dayorganizer.ui.belohnungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Speichern nach dem in Plan 5 fuer AktivitaetBearbeitenViewModel abgesicherten Muster. */
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

    private var geladeneId: Long? = null

    // Ab dem ersten Speicherversuch prueft aendern() bei jeder Aenderung neu.
    private var speicherVersucht = false

    // Sperre gegen ueberlappendes Speichern (Doppeltipp); synchron gesetzt, in finally frei.
    private var speichertGerade = false

    fun laden(id: Long) = viewModelScope.launch {
        // Nach einer Rotation startet der Screen laden() erneut; ohne diese Pruefung
        // ueberschriebe der DB-Stand den noch ungespeicherten Entwurf.
        if (geladeneId == id) return@launch
        geladeneId = id
        _auswahl.value = aktivitaeten.alleAktiven()
        if (id == 0L) return@launch
        belohnungen.nachId(id)?.let { _entwurf.value = BelohnungEntwurf.aus(it) }
    }

    fun aendern(block: (BelohnungEntwurf) -> BelohnungEntwurf) {
        _entwurf.value = block(_entwurf.value)
        if (speicherVersucht) _fehler.value = _entwurf.value.pruefen()
    }

    /** Gibt null zurueck, wenn der Entwurf Fehler hat oder schon ein Speichern laeuft. */
    suspend fun speichern(): SpeicherErgebnis? {
        if (speichertGerade) return null
        speichertGerade = true
        try {
            speicherVersucht = true
            val fehler = _entwurf.value.pruefen()
            _fehler.value = fehler
            if (fehler.isNotEmpty()) return null

            val ergebnis = if (_entwurf.value.id == 0L) SpeicherErgebnis.ANGELEGT else SpeicherErgebnis.GEAENDERT
            val id = belohnungen.speichern(_entwurf.value.zuBelohnung())
            // Die neue id uebernehmen: waehrend der Ausblend-Animation ist der Screen noch
            // klickbar; ein spaeter Tipp wird so zum harmlosen Update statt zu einem Insert.
            _entwurf.value = _entwurf.value.copy(id = id)
            return ergebnis
        } finally {
            speichertGerade = false
        }
    }
}
