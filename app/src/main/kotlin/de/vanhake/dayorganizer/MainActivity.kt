package de.vanhake.dayorganizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.ui.DayOrganizerNavigation
import de.vanhake.dayorganizer.ui.theme.DayOrganizerThema
import de.vanhake.dayorganizer.ui.theme.ThemaViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themaViewModel: ThemaViewModel = hiltViewModel()
            val themeModus by themaViewModel.themeModus.collectAsStateWithLifecycle()
            DayOrganizerThema(themeModus) {
                Surface { DayOrganizerNavigation() }
            }
        }
    }
}
