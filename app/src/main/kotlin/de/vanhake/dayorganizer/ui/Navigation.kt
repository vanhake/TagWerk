package de.vanhake.dayorganizer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetBearbeitenScreen
import de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetenScreen
import de.vanhake.dayorganizer.ui.heute.HeuteScreen

object Ziele {
    const val HEUTE = "heute"
    const val AKTIVITAETEN = "aktivitaeten"
    const val AKTIVITAET_BEARBEITEN = "aktivitaet/{id}"
    fun bearbeiten(id: Long) = "aktivitaet/$id"
}

@Composable
fun DayOrganizerNavigation() {
    val navController = rememberNavController()
    val eintrag by navController.currentBackStackEntryAsState()
    val aktuell = eintrag?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = aktuell == Ziele.HEUTE,
                    onClick = { navController.navigate(Ziele.HEUTE) },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_heute)) },
                )
                NavigationBarItem(
                    selected = aktuell == Ziele.AKTIVITAETEN,
                    onClick = { navController.navigate(Ziele.AKTIVITAETEN) },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_aktivitaeten)) },
                )
            }
        },
    ) { innen ->
        NavHost(
            navController = navController,
            startDestination = Ziele.HEUTE,
            modifier = Modifier.padding(innen),
        ) {
            composable(Ziele.HEUTE) { HeuteScreen() }
            composable(Ziele.AKTIVITAETEN) {
                AktivitaetenScreen(
                    bearbeiten = { id -> navController.navigate(Ziele.bearbeiten(id)) },
                )
            }
            composable(Ziele.AKTIVITAET_BEARBEITEN) { eintrag ->
                AktivitaetBearbeitenScreen(
                    id = eintrag.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    fertig = { navController.popBackStack() },
                )
            }
        }
    }
}
