package de.vanhake.dayorganizer.ui.einstellungen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.platform.permission.BerechtigungsHinweise
import de.vanhake.dayorganizer.platform.permission.BerechtigungsPruefer
import de.vanhake.dayorganizer.platform.permission.Hinweis
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

data class EinstellungenZustand(
    val tagesbonusText: String = "",
    val standardKulanzText: String = "",
    val hinweise: List<Hinweis> = emptyList(),
)

@HiltViewModel
class EinstellungenViewModel(
    private val lesen: suspend () -> Einstellungen,
    private val schreiben: suspend (Einstellungen) -> Unit,
    private val hinweiseLesen: suspend () -> List<Hinweis>,
) : ViewModel() {

    @Inject
    constructor(speicher: EinstellungenSpeicher, pruefer: BerechtigungsPruefer, uhr: Clock) : this(
        lesen = { speicher.lesen() },
        schreiben = { speicher.schreiben(it) },
        hinweiseLesen = {
            // BerechtigungsPruefer liest nur aus Context und kann DataStore nicht synchron
            // lesen, deshalb wird die Waechter-Frische hier -- suspend -- dazugemischt.
            // Gleiches Muster wie HeuteViewModel.
            val letzterErfolg = speicher.letzterWaechterErfolg()
            val haengt = letzterErfolg == null ||
                Duration.between(letzterErfolg, LocalDateTime.now(uhr)) > WAECHTER_FRISCHE_GRENZE
            BerechtigungsHinweise.fuer(pruefer.stand().copy(waechterHaengt = haengt))
        },
    )

    private val _zustand = MutableStateFlow(EinstellungenZustand())
    val zustand: StateFlow<EinstellungenZustand> = _zustand.asStateFlow()

    init {
        viewModelScope.launch { neuLaden() }
    }

    fun tagesbonusAendern(text: String) {
        _zustand.value = _zustand.value.copy(tagesbonusText = text)
    }

    fun kulanzAendern(text: String) {
        _zustand.value = _zustand.value.copy(standardKulanzText = text)
    }

    /**
     * Wird bei jedem Sichtbarwerden des Screens aufgerufen, nicht nur einmal:
     * der Nutzer kommt aus den Systemeinstellungen zurueck, und dann muss der
     * Banner verschwinden. Gleiches Muster wie HeuteViewModel.beimSichtbarwerden.
     */
    fun beimSichtbarwerden() {
        viewModelScope.launch { _zustand.value = _zustand.value.copy(hinweise = hinweiseLesen()) }
    }

    /**
     * Ungueltige Eingaben werden verworfen, nicht gespeichert. Ein leeres Feld
     * darf den Tagesbonus nicht auf null setzen.
     */
    fun speichern(): Job = viewModelScope.launch {
        val bisher = lesen()
        val neu = Einstellungen(
            tagesbonusPunkte = _zustand.value.tagesbonusText.toIntOrNull()
                ?.takeIf { it >= 0 } ?: bisher.tagesbonusPunkte,
            standardKulanzMinuten = _zustand.value.standardKulanzText.toIntOrNull()
                ?.takeIf { it >= 0 } ?: bisher.standardKulanzMinuten,
        )
        // DataStore.edit wirft bei Platten- oder Korruptionsfehlern eine IOException --
        // unbehandelt reisst sie die App mit. Gleiches Muster wie
        // AktivitaetBearbeitenViewModel.speichern(): protokollieren statt abstuerzen.
        runCatching {
            schreiben(neu)
        }.onFailure { ausnahme ->
            Log.w(TAG, "Speichern der Einstellungen fehlgeschlagen", ausnahme)
        }
        neuLaden()
    }

    private suspend fun neuLaden() {
        val aktuell = lesen()
        _zustand.value = EinstellungenZustand(
            tagesbonusText = aktuell.tagesbonusPunkte.toString(),
            standardKulanzText = aktuell.standardKulanzMinuten.toString(),
            hinweise = hinweiseLesen(),
        )
    }

    private companion object {
        const val TAG = "EinstellungenVM"
        val WAECHTER_FRISCHE_GRENZE: Duration = Duration.ofHours(36)
    }
}
