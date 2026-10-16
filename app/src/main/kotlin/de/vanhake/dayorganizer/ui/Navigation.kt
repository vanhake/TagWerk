package de.vanhake.dayorganizer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Redeem
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetBearbeitenScreen
import de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetenScreen
import de.vanhake.dayorganizer.ui.belohnungen.BelohnungBearbeitenScreen
import de.vanhake.dayorganizer.ui.belohnungen.BelohnungenScreen
import de.vanhake.dayorganizer.ui.einstellungen.EinstellungenScreen
import de.vanhake.dayorganizer.ui.gemeinsam.Navigationsleiste
import de.vanhake.dayorganizer.ui.gemeinsam.NavigationsEintrag
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import de.vanhake.dayorganizer.ui.heute.HeuteScreen
import de.vanhake.dayorganizer.ui.statistik.StatistikScreen

object Ziele {
    const val HEUTE = "heute"
    const val AKTIVITAETEN = "aktivitaeten"
    const val BELOHNUNGEN = "belohnungen"
    const val STATISTIK = "statistik"
    const val EINSTELLUNGEN = "einstellungen"
    const val AKTIVITAET_BEARBEITEN = "aktivitaet/{id}"
    const val BELOHNUNG_BEARBEITEN = "belohnung/{id}"

    /** savedStateHandle-Schluessel: Bearbeiten -> Liste, Wert = SpeicherErgebnis.name. */
    const val ERGEBNIS_AKTIVITAET_GESPEICHERT = "aktivitaet_gespeichert"

    /** savedStateHandle-Schluessel: Belohnungs-Editor -> Liste, Wert = SpeicherErgebnis.name. */
    const val ERGEBNIS_BELOHNUNG_GESPEICHERT = "belohnung_gespeichert"

    fun aktivitaetBearbeiten(id: Long) = "aktivitaet/$id"
    fun belohnungBearbeiten(id: Long) = "belohnung/$id"
}

private val TABS = listOf(
    NavigationsEintrag(
        Ziele.HEUTE, R.string.tab_heute, Icons.Filled.CheckCircle, Icons.Outlined.CheckCircle,
    ),
    NavigationsEintrag(
        Ziele.AKTIVITAETEN, R.string.tab_aktivitaeten,
        Icons.AutoMirrored.Filled.FormatListBulleted, Icons.AutoMirrored.Outlined.FormatListBulleted,
    ),
    NavigationsEintrag(
        Ziele.BELOHNUNGEN, R.string.tab_belohnungen, Icons.Filled.Redeem, Icons.Outlined.Redeem,
    ),
    NavigationsEintrag(
        Ziele.STATISTIK, R.string.tab_statistik, Icons.Filled.Insights, Icons.Outlined.Insights,
    ),
    NavigationsEintrag(
        Ziele.EINSTELLUNGEN, R.string.tab_einstellungen, Icons.Filled.Tune, Icons.Outlined.Tune,
    ),
)

@Composable
fun DayOrganizerNavigation() {
    val navController = rememberNavController()
    val eintrag by navController.currentBackStackEntryAsState()
    val aktuell = eintrag?.destination?.route

    Scaffold(
        bottomBar = {
            // Nur auf den Tab-Zielen selbst zeigen: auf aktivitaet/{id} und belohnung/{id}
            // waere ohnehin kein Tab markiert, und ein Tipp wuerfe ueber das popUpTo unten
            // den halb ausgefuellten Bearbeiten-Bildschirm ohne Rueckfrage vom Stapel.
            if (TABS.any { it.ziel == aktuell }) {
                Navigationsleiste(TABS, aktuell) { ziel ->
                    navController.navigate(ziel) {
                        popUpTo(Ziele.HEUTE)
                        launchSingleTop = true
                    }
                }
            }
        },
    ) { innen ->
        NavHost(
            navController = navController,
            startDestination = Ziele.HEUTE,
            modifier = Modifier.padding(innen),
        ) {
            composable(Ziele.HEUTE) { HeuteScreen() }

            composable(Ziele.AKTIVITAETEN) { eintrag ->
                val gespeichert by eintrag.savedStateHandle
                    .getStateFlow<String?>(Ziele.ERGEBNIS_AKTIVITAET_GESPEICHERT, null)
                    .collectAsStateWithLifecycle()
                AktivitaetenScreen(
                    bearbeiten = { id -> navController.navigate(Ziele.aktivitaetBearbeiten(id)) },
                    gespeichert = gespeichert?.let(SpeicherErgebnis::valueOf),
                    gespeichertGezeigt = {
                        eintrag.savedStateHandle[Ziele.ERGEBNIS_AKTIVITAET_GESPEICHERT] = null
                    },
                )
            }

            composable(Ziele.BELOHNUNGEN) { eintrag ->
                val gespeichert by eintrag.savedStateHandle
                    .getStateFlow<String?>(Ziele.ERGEBNIS_BELOHNUNG_GESPEICHERT, null)
                    .collectAsStateWithLifecycle()
                BelohnungenScreen(
                    bearbeiten = { id -> navController.navigate(Ziele.belohnungBearbeiten(id)) },
                    gespeichert = gespeichert?.let(SpeicherErgebnis::valueOf),
                    gespeichertGezeigt = {
                        eintrag.savedStateHandle[Ziele.ERGEBNIS_BELOHNUNG_GESPEICHERT] = null
                    },
                )
            }

            composable(Ziele.STATISTIK) { StatistikScreen() }
            composable(Ziele.EINSTELLUNGEN) { EinstellungenScreen() }

            composable(Ziele.AKTIVITAET_BEARBEITEN) { eintrag ->
                AktivitaetBearbeitenScreen(
                    id = eintrag.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    // Nur im RESUMED-Zustand navigieren: ein zweiter Aufruf waehrend der
                    // Ausblend-Animation riefe sonst popBackStack() erneut auf und
                    // wuerfe auch die Liste vom Stapel.
                    fertig = { ergebnis ->
                        if (eintrag.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            navController.previousBackStackEntry?.savedStateHandle
                                ?.set(Ziele.ERGEBNIS_AKTIVITAET_GESPEICHERT, ergebnis.name)
                            navController.popBackStack()
                        }
                    },
                    zurueck = {
                        if (eintrag.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            navController.popBackStack()
                        }
                    },
                )
            }

            composable(Ziele.BELOHNUNG_BEARBEITEN) { eintrag ->
                BelohnungBearbeitenScreen(
                    id = eintrag.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    // Nur im RESUMED-Zustand navigieren: ein zweiter Aufruf waehrend der
                    // Ausblend-Animation riefe sonst popBackStack() erneut auf und
                    // wuerfe auch die Liste vom Stapel.
                    fertig = { ergebnis ->
                        if (eintrag.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            navController.previousBackStackEntry?.savedStateHandle
                                ?.set(Ziele.ERGEBNIS_BELOHNUNG_GESPEICHERT, ergebnis.name)
                            navController.popBackStack()
                        }
                    },
                    zurueck = {
                        if (eintrag.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            navController.popBackStack()
                        }
                    },
                )
            }
        }
    }
}
