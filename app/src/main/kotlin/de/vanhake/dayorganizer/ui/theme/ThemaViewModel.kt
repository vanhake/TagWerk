package de.vanhake.dayorganizer.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.data.ThemeModus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemaViewModel @Inject constructor(
    speicher: EinstellungenSpeicher,
) : ViewModel() {
    val themeModus: StateFlow<ThemeModus> = speicher.beobachten()
        .map { it.themeModus }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeModus.HELL)
}
