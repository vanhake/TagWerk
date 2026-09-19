package de.vanhake.dayorganizer.ui.aktivitaeten

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.domainuse.FaelligkeitenMaterialisieren
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AktivitaetBearbeitenViewModel @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val materialisieren: FaelligkeitenMaterialisieren,
    private val alarmPlaner: AlarmPlaner,
    private val uhr: Clock,
) : ViewModel() {

    private val _entwurf = MutableStateFlow(AktivitaetEntwurf())
    val entwurf: StateFlow<AktivitaetEntwurf> = _entwurf.asStateFlow()

    private val _fehler = MutableStateFlow<List<EntwurfFehler>>(emptyList())
    val fehler: StateFlow<List<EntwurfFehler>> = _fehler.asStateFlow()

    fun laden(id: Long) = viewModelScope.launch {
        if (id == 0L) return@launch
        aktivitaeten.nachId(id)?.let { _entwurf.value = AktivitaetEntwurf.aus(it) }
    }

    fun aendern(block: (AktivitaetEntwurf) -> AktivitaetEntwurf) {
        _entwurf.value = block(_entwurf.value)
    }

    /** Speichert und plant neu. Gibt false zurueck, wenn der Entwurf Fehler hat. */
    suspend fun speichern(): Boolean {
        val fehler = _entwurf.value.pruefen()
        _fehler.value = fehler
        if (fehler.isNotEmpty()) return false

        aktivitaeten.speichern(_entwurf.value.zuAktivitaet(LocalDate.now(uhr)))
        // Ohne diese beiden Zeilen wirkt eine neue Aktivitaet erst nach dem
        // naechtlichen Waechterlauf. Sie sind reine Optimierung: Wenn sie
        // fehlschlagen, holt der naechtliche Waechterlauf das nach, darum
        // duerfen sie das Ergebnis nicht mit runterreissen.
        runCatching {
            materialisieren()
            alarmPlaner.neuPlanen()
        }.onFailure { ausnahme ->
            Log.w(TAG, "Nachbereitung nach dem Speichern fehlgeschlagen", ausnahme)
        }
        return true
    }

    private companion object {
        const val TAG = "AktivitaetBearbeiten"
    }
}
