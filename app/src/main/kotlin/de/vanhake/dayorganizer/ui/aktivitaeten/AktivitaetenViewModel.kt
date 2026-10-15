package de.vanhake.dayorganizer.ui.aktivitaeten

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domainuse.AktivitaetNeuPlanen
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AktivitaetenViewModel @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val neuPlanen: AktivitaetNeuPlanen,
    private val serienRechner: SerienRechner,
    private val uhr: Clock,
) : ViewModel() {

    // beobachteTag(heute) liefert nur den Ausloeser: wer auf "Heute" abhakt, aendert eine
    // heutige Faelligkeit, und die Serie in der Liste soll mitziehen. Die Historie selbst
    // kommt je Aktivitaet aus historie(). mapLatest verwirft eine noch laufende Berechnung,
    // wenn schon die naechste Emission da ist. flowOn(Default) aus demselben Grund wie im
    // StatistikViewModel: SerienRechner laeuft ueber die volle Historie.
    @OptIn(ExperimentalCoroutinesApi::class)
    val zeilen: StateFlow<List<AktivitaetZeile>> = combine(
        aktivitaeten.beobachteAktive(),
        faelligkeiten.beobachteTag(LocalDate.now(uhr)),
    ) { liste, _ -> liste }
        .mapLatest { liste ->
            val historien = liste.associate { it.id to faelligkeiten.historie(it.id) }
            AktivitaetenZusammenstellung.zeilen(liste, historien, LocalDate.now(uhr), serienRechner)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Eigener Flow statt Teil der zeilen-Kette -- gleiches Muster wie HeuteViewModel._toast.
    private val _toast = MutableStateFlow<Hinweistoast?>(null)
    val toast: StateFlow<Hinweistoast?> = _toast.asStateFlow()
    private var toastJob: Job? = null

    // Ohne AktivitaetNeuPlanen liefen die schon materialisierten kuenftigen Faelligkeiten
    // und ihre Erinnerungen der archivierten Aktivitaet einfach weiter (Finding 1 der
    // Branch-Review): Erinnerungen feuerten fuer etwas, das nirgends mehr sichtbar ist,
    // und die Faelligkeiten wurden am naechsten Mitternachtslauf faelschlich als
    // VERPASST geschlossen. FaelligkeitenMaterialisieren liest nur aktive, nicht
    // archivierte Aktivitaeten (AktivitaetDao.alleAktiven: "aktiv = 1 AND archiviertAm
    // IS NULL"), darum legt der Aufruf hier fuer die gerade archivierte Aktivitaet
    // nichts Neues an -- er raeumt nur auf.
    //
    // Kein Rueckgaengig (Entscheidung in Plan 5): der Toast bestaetigt nur.
    fun archivieren(id: Long) = viewModelScope.launch {
        val name = aktivitaeten.nachId(id)?.name
        aktivitaeten.archivieren(id, LocalDate.now(uhr))
        // Archiviert ist die Aktivitaet in jedem Fall; eine Ausnahme der Nachbereitung darf
        // weder den Toast verhindern noch im viewModelScope die App abstuerzen lassen.
        try {
            neuPlanen(id)
        } catch (abbruch: CancellationException) {
            throw abbruch
        } catch (ausnahme: Exception) {
            Log.w(TAG, "Nachbereitung nach dem Archivieren fehlgeschlagen", ausnahme)
        }
        name?.let { zeigeToast(AktivitaetenToastTexte.archiviert(it)) }
    }

    /** Vom Screen aufgerufen, wenn der Bearbeiten-Bildschirm mit einem Ergebnis
     * zurueckgekehrt ist (Navigation reicht es ueber den savedStateHandle durch). */
    fun gespeichertMelden(ergebnis: SpeicherErgebnis) {
        zeigeToast(
            when (ergebnis) {
                SpeicherErgebnis.ANGELEGT -> AktivitaetenToastTexte.ANGELEGT
                SpeicherErgebnis.GEAENDERT -> AktivitaetenToastTexte.GEAENDERT
            },
        )
    }

    private fun zeigeToast(text: String) {
        toastJob?.cancel()
        _toast.value = Hinweistoast(text, kannRueckgaengig = false)
        toastJob = viewModelScope.launch {
            delay(TOAST_DAUER_MS)
            _toast.value = null
        }
    }

    private companion object {
        const val TAG = "Aktivitaeten"

        /** Wie HeuteViewModel. */
        const val TOAST_DAUER_MS = 3_800L
    }
}

/** Reine Textbausteine, ohne Android-Ressourcen testbar -- Muster HeuteToastTexte. */
private object AktivitaetenToastTexte {
    const val ANGELEGT = "Aktivität angelegt"
    const val GEAENDERT = "Änderungen gespeichert"
    fun archiviert(name: String) = "$name archiviert"
}
