package de.vanhake.dayorganizer.ui.aktivitaeten

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domainuse.AktivitaetNeuPlanen
import de.vanhake.dayorganizer.ui.gemeinsam.ArchivEintrag
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import de.vanhake.dayorganizer.ui.gemeinsam.ToastStapel
import de.vanhake.dayorganizer.util.versuchen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AktivitaetenViewModel(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val neuPlanen: suspend (Long) -> Unit,
    private val serienRechner: SerienRechner,
    private val uhr: Clock,
) : ViewModel() {

    // Zwei-Konstruktoren-Muster wie HeuteViewModel: Tests geben einen Spion statt des
    // Use Cases hinein.
    @Inject
    constructor(
        aktivitaeten: AktivitaetRepository,
        faelligkeiten: FaelligkeitRepository,
        neuPlanen: AktivitaetNeuPlanen,
        serienRechner: SerienRechner,
        uhr: Clock,
    ) : this(aktivitaeten, faelligkeiten, { id -> neuPlanen(id) }, serienRechner, uhr)

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

    // Eigener Flow statt Teil der zeilen-Kette -- gleiches Muster wie ToastStapel in HeuteViewModel.
    private val stapel = ToastStapel(viewModelScope)
    val toast: StateFlow<Hinweistoast?> = stapel.sichtbar

    // Ohne AktivitaetNeuPlanen liefen die schon materialisierten kuenftigen Faelligkeiten
    // und ihre Erinnerungen der archivierten Aktivitaet einfach weiter (Finding 1 der
    // Branch-Review): Erinnerungen feuerten fuer etwas, das nirgends mehr sichtbar ist,
    // und die Faelligkeiten wurden am naechsten Mitternachtslauf faelschlich als
    // VERPASST geschlossen. FaelligkeitenMaterialisieren liest nur aktive, nicht
    // archivierte Aktivitaeten (AktivitaetDao.alleAktiven: "aktiv = 1 AND archiviertAm
    // IS NULL"), darum legt der Aufruf hier fuer die gerade archivierte Aktivitaet
    // nichts Neues an -- er raeumt nur auf.
    //
    // Der Toast bietet Rueckgaengig an (Plan 9): es holt die Aktivitaet samt Planung zurueck.
    fun archivieren(id: Long) = viewModelScope.launch {
        val name = aktivitaeten.nachId(id)?.name
        aktivitaeten.archivieren(id, LocalDate.now(uhr))
        // Archiviert ist die Aktivitaet in jedem Fall; eine Ausnahme der Nachbereitung darf
        // weder den Toast verhindern noch im viewModelScope die App abstuerzen lassen.
        nachbereiten(id, "Nachbereitung nach dem Archivieren fehlgeschlagen")
        name?.let {
            stapel.zeigen(AktivitaetenToastTexte.archiviert(it), rueckgaengig = { wiederherstellenIntern(id) })
        }
    }

    /** Archivierte Aktivitaeten fuer den einklappbaren Bereich unter der Liste. */
    val archiviert: StateFlow<List<ArchivEintrag>> = aktivitaeten.beobachteArchivierte()
        .map { liste -> liste.map { ArchivEintrag(it.id, it.name, it.symbol) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun wiederherstellen(id: Long) = viewModelScope.launch {
        val name = aktivitaeten.nachId(id)?.name
        // Ein zweiter Tipp findet nichts Archiviertes mehr: dann weder Toast noch Neuplanen.
        if (wiederherstellenIntern(id)) {
            name?.let { stapel.zeigen(AktivitaetenToastTexte.wiederhergestellt(it)) }
        }
    }

    fun rueckgaengig() = viewModelScope.launch {
        stapel.rueckgaengigNehmen()?.invoke()
    }

    // Wie nach dem Archivieren muss die Planung folgen: sonst haette die wiederhergestellte
    // Aktivitaet keine kuenftigen Faelligkeiten und keine Erinnerungen.
    private suspend fun wiederherstellenIntern(id: Long): Boolean {
        val wiederhergestellt = aktivitaeten.wiederherstellen(id)
        if (wiederhergestellt) nachbereiten(id, "Nachbereitung nach dem Wiederherstellen fehlgeschlagen")
        return wiederhergestellt
    }

    private suspend fun nachbereiten(id: Long, fehlertext: String) {
        versuchen { neuPlanen(id) }.onFailure { Log.w(TAG, fehlertext, it) }
    }

    /** Vom Screen aufgerufen, wenn der Bearbeiten-Bildschirm mit einem Ergebnis
     * zurueckgekehrt ist (Navigation reicht es ueber den savedStateHandle durch). */
    fun gespeichertMelden(ergebnis: SpeicherErgebnis) {
        stapel.zeigen(
            when (ergebnis) {
                SpeicherErgebnis.ANGELEGT -> AktivitaetenToastTexte.ANGELEGT
                SpeicherErgebnis.GEAENDERT -> AktivitaetenToastTexte.GEAENDERT
            },
        )
    }

    private companion object {
        const val TAG = "Aktivitaeten"
    }
}

/** Reine Textbausteine, ohne Android-Ressourcen testbar -- Muster HeuteToastTexte. */
private object AktivitaetenToastTexte {
    const val ANGELEGT = "Aktivität angelegt"
    const val GEAENDERT = "Änderungen gespeichert"
    fun archiviert(name: String) = "$name archiviert"
    fun wiederhergestellt(name: String) = "$name wiederhergestellt"
}
