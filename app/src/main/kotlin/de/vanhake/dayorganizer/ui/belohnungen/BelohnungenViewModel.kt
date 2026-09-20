package de.vanhake.dayorganizer.ui.belohnungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.domainuse.BelohnungEinloesen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BelohnungenViewModel @Inject constructor(
    private val belohnungen: BelohnungRepository,
    private val buchungen: BuchungRepository,
    private val einloesenUseCase: BelohnungEinloesen,
) : ViewModel() {

    val zustand: StateFlow<BelohnungenZustand> = combine(
        belohnungen.beobachteAktive(),
        buchungen.beobachteKontostand(),
        belohnungen.beobachteEinloesungen(),
    ) { liste, kontostand, einloesungen ->
        Triple(liste, kontostand, einloesungen)
    }.map { (liste, kontostand, einloesungen) ->
        BelohnungenZustand(
            kontostand = kontostand,
            kacheln = liste.map { belohnung ->
                BelohnungKachel(
                    id = belohnung.id,
                    name = belohnung.name,
                    beschreibung = belohnung.beschreibung,
                    preisPunkte = belohnung.preisPunkte,
                    einloesbarkeit = einloesenUseCase.einloesbarkeit(belohnung, kontostand),
                )
            },
            einloesungen = einloesungen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BelohnungenZustand())

    // Property und Methode heissen bewusst unterschiedlich, siehe HeuteViewModel.
    fun einloesen(belohnungId: Long) = viewModelScope.launch { einloesenUseCase(belohnungId) }

    fun archivieren(belohnungId: Long) = viewModelScope.launch { belohnungen.archivieren(belohnungId) }

}
