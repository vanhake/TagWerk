package de.vanhake.dayorganizer.ui.belohnungen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.domainuse.BelohnungEinloesen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BelohnungenViewModel @Inject constructor(
    private val belohnungen: BelohnungRepository,
    private val buchungen: BuchungRepository,
    private val einloesenUseCase: BelohnungEinloesen,
) : ViewModel() {

    // Sperre gegen einen Doppeltipp: die Transaktion im Use Case pruefte zwar Kontostand
    // und Bedingung, aber zwei fuer sich gueltige Einloesungen (z. B. 450 Punkte, Preis 200)
    // sind beide erlaubt -- ohne diese Sperre bucht ein Doppeltipp zweimal. Anders als bei
    // FaelligkeitErledigen gibt es keine serverseitige Idempotenz zu pruefen: jede Einloesung
    // darf eine neue sein, also muss die Sperre hier, auf Seiten des Aufrufs, sitzen.
    private val _laufendeEinloesungen = MutableStateFlow<Set<Long>>(emptySet())

    val zustand: StateFlow<BelohnungenZustand> = combine(
        belohnungen.beobachteAktive(),
        buchungen.beobachteKontostand(),
        belohnungen.beobachteEinloesungen(),
        _laufendeEinloesungen,
    ) { liste, kontostand, einloesungen, laufende ->
        BelohnungenZustand(
            kontostand = kontostand,
            kacheln = liste.map { belohnung ->
                BelohnungKachel(
                    id = belohnung.id,
                    name = belohnung.name,
                    beschreibung = belohnung.beschreibung,
                    preisPunkte = belohnung.preisPunkte,
                    einloesbarkeit = einloesenUseCase.einloesbarkeit(belohnung, kontostand),
                    wirdEingeloest = belohnung.id in laufende,
                )
            },
            einloesungen = einloesungen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BelohnungenZustand())

    // Property und Methode heissen bewusst unterschiedlich, siehe HeuteViewModel.
    //
    // Die Sperre wird synchron gesetzt, bevor ueberhaupt etwas gestartet wird -- ein
    // zweiter, synchron folgender Aufruf (der klassische Doppeltipp) sieht die Sperre
    // also garantiert, unabhaengig davon, ob die Koroutine schon zum Zug kam. Sie faellt
    // im finally auch dann wieder, wenn der Aufruf mit einer Ausnahme endet, sonst bliebe
    // die Kachel dauerhaft gesperrt.
    fun einloesen(belohnungId: Long) {
        if (belohnungId in _laufendeEinloesungen.value) return
        _laufendeEinloesungen.value = _laufendeEinloesungen.value + belohnungId
        viewModelScope.launch {
            try {
                // Abgelehnt/Unbekannt fuer eine Nutzermeldung auszuwerten ist hier bewusst
                // nicht Teil dieses Aufrufs. Eine Datenbankausnahme darf aber nicht bis zum
                // viewModelScope durchschlagen und die App mitreissen.
                runCatching { einloesenUseCase(belohnungId) }
                    .onFailure { ausnahme -> Log.w(TAG, "Einloesen fehlgeschlagen", ausnahme) }
            } finally {
                _laufendeEinloesungen.value = _laufendeEinloesungen.value - belohnungId
            }
        }
    }

    fun archivieren(belohnungId: Long) = viewModelScope.launch { belohnungen.archivieren(belohnungId) }

    private companion object {
        const val TAG = "BelohnungenViewModel"
    }
}
