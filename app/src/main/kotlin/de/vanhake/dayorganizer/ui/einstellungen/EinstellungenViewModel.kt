package de.vanhake.dayorganizer.ui.einstellungen

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
    private val hinweiseLesen: () -> List<Hinweis>,
) : ViewModel() {

    @Inject
    constructor(speicher: EinstellungenSpeicher, pruefer: BerechtigungsPruefer) : this(
        lesen = { speicher.lesen() },
        schreiben = { speicher.schreiben(it) },
        hinweiseLesen = { BerechtigungsHinweise.fuer(pruefer.stand()) },
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
     * Ungueltige Eingaben werden verworfen, nicht gespeichert. Ein leeres Feld
     * darf den Tagesbonus nicht auf null setzen.
     */
    fun speichern(): Job = viewModelScope.launch {
        val bisher = lesen()
        schreiben(
            Einstellungen(
                tagesbonusPunkte = _zustand.value.tagesbonusText.toIntOrNull()
                    ?.takeIf { it >= 0 } ?: bisher.tagesbonusPunkte,
                standardKulanzMinuten = _zustand.value.standardKulanzText.toIntOrNull()
                    ?.takeIf { it >= 0 } ?: bisher.standardKulanzMinuten,
            ),
        )
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
}
