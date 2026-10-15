package de.vanhake.dayorganizer.ui.aktivitaeten

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.domainuse.AktivitaetNeuPlanen
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AktivitaetBearbeitenViewModel(
    private val aktivitaeten: AktivitaetRepository,
    private val neuPlanen: AktivitaetNeuPlanen,
    private val standardKulanzLesen: suspend () -> Int,
    private val uhr: Clock,
) : ViewModel() {

    @Inject
    constructor(
        aktivitaeten: AktivitaetRepository,
        neuPlanen: AktivitaetNeuPlanen,
        einstellungen: EinstellungenSpeicher,
        uhr: Clock,
    ) : this(
        aktivitaeten,
        neuPlanen,
        { einstellungen.lesen().standardKulanzMinuten },
        uhr,
    )

    private val _entwurf = MutableStateFlow(AktivitaetEntwurf())
    val entwurf: StateFlow<AktivitaetEntwurf> = _entwurf.asStateFlow()

    private val _fehler = MutableStateFlow<List<EntwurfFehler>>(emptyList())
    val fehler: StateFlow<List<EntwurfFehler>> = _fehler.asStateFlow()

    private var geladeneId: Long? = null

    // Wird in speichern() gesetzt; ab dann prueft aendern() bei jeder Aenderung neu.
    private var speicherVersucht = false

    // Sperre gegen Doppeltipp: Ohne sie legte ein zweites speichern() bei id 0 ein Duplikat
    // an. Wird synchron gesetzt (vor der ersten Suspension) und in finally zurueckgesetzt,
    // Muster BelohnungenViewModel._laufendeEinloesungen.
    private var speichertGerade = false

    /**
     * Bei einer neuen Aktivitaet (id == 0) kommt das Kulanzfenster als Vorgabe aus den
     * Einstellungen statt aus dem hart codierten Standardwert von AktivitaetEntwurf.
     * Eine bestehende Aktivitaet wird stattdessen geladen und behaelt ihren eigenen,
     * bereits gespeicherten Wert unangetastet.
     */
    fun laden(id: Long) = viewModelScope.launch {
        // Nach einer Rotation startet der Screen laden() erneut; ohne diese Pruefung
        // ueberschriebe der DB-Stand den noch ungespeicherten Entwurf.
        if (geladeneId == id) return@launch
        geladeneId = id
        if (id == 0L) {
            _entwurf.value = _entwurf.value.copy(kulanzMinutenText = standardKulanzLesen().toString())
            return@launch
        }
        aktivitaeten.nachId(id)?.let { _entwurf.value = AktivitaetEntwurf.aus(it) }
    }

    /** Fehler bleiben bis zum ersten Speicherversuch stumm. Danach wird jede Aenderung neu
     * geprueft, damit ein behobener Fehler sofort verschwindet (Prototyp: setName loescht
     * err) und ein neu entstandener sofort erscheint. */
    fun aendern(block: (AktivitaetEntwurf) -> AktivitaetEntwurf) {
        _entwurf.value = block(_entwurf.value)
        if (speicherVersucht) _fehler.value = _entwurf.value.pruefen()
    }

    /** Speichert und plant neu. Gibt null zurueck, wenn der Entwurf Fehler hat oder schon
     * ein Speichern laeuft. */
    suspend fun speichern(): SpeicherErgebnis? {
        if (speichertGerade) return null
        speichertGerade = true
        try {
            speicherVersucht = true
            val fehler = _entwurf.value.pruefen()
            _fehler.value = fehler
            if (fehler.isNotEmpty()) return null

            val ergebnis = if (_entwurf.value.id == 0L) SpeicherErgebnis.ANGELEGT else SpeicherErgebnis.GEAENDERT
            val id = aktivitaeten.speichern(_entwurf.value.zuAktivitaet(LocalDate.now(uhr)))
            // Die neue id uebernehmen: Waehrend der Ausblend-Animation ist der Screen noch
            // klickbar; ein spaeter Tipp wird so zum harmlosen Update statt zu einem
            // zweiten Insert (die Sperre ist dann schon wieder frei).
            _entwurf.value = _entwurf.value.copy(id = id)
            // Ohne diese Nachbereitung wirkt eine neue oder geaenderte Aktivitaet erst nach
            // dem naechtlichen Waechterlauf. Sie ist reine Optimierung: Wenn sie fehlschlaegt,
            // holt der naechtliche Waechterlauf das nach, darum darf sie das Ergebnis nicht
            // mit runterreissen. AktivitaetNeuPlanen raeumt dabei auch noch nicht begonnene
            // kuenftige Faelligkeiten samt Erinnerungen der alten Planung weg, bevor es neu
            // materialisiert -- sonst laeuft der alte Zeitplan neben dem neuen weiter.
            // CancellationException wird bewusst weitergereicht: sie ist kein Fehlschlag,
            // sondern der Abbruch des Aufrufers.
            try {
                neuPlanen(id)
            } catch (abbruch: CancellationException) {
                throw abbruch
            } catch (ausnahme: Exception) {
                Log.w(TAG, "Nachbereitung nach dem Speichern fehlgeschlagen", ausnahme)
            }
            return ergebnis
        } finally {
            speichertGerade = false
        }
    }

    private companion object {
        const val TAG = "AktivitaetBearbeiten"
    }
}
