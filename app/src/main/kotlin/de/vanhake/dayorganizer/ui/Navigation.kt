package de.vanhake.dayorganizer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
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

private data class Tab(val ziel: String, val icon: ImageVector, val titelId: Int)

private val TABS = listOf(
    Tab(Ziele.HEUTE, Icons.Default.CheckCircle, R.string.tab_heute),
    Tab(Ziele.AKTIVITAETEN, Icons.Default.List, R.string.tab_aktivitaeten),
    Tab(Ziele.BELOHNUNGEN, Icons.Default.Star, R.string.tab_belohnungen),
    Tab(Ziele.STATISTIK, Icons.Default.Insights, R.string.tab_statistik),
    Tab(Ziele.EINSTELLUNGEN, Icons.Default.Settings, R.string.tab_einstellungen),
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
                NavigationBar {
                    TABS.forEach { tab ->
                        NavigationBarItem(
                            selected = aktuell == tab.ziel,
                            onClick = {
                                navController.navigate(tab.ziel) {
                                    popUpTo(Ziele.HEUTE)
                                    launchSingleTop = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            // Einzeilig mit Auslassungszeichen: bei grosser Schrift passen fuenf
                            // Beschriftungen nicht nebeneinander, und ein Umbruch mitten im Wort
                            // ("Statisti/k") ist schlechter als ein gekuerztes "Statis...".
                            label = {
                                Text(
                                    stringResource(tab.titelId),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
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
