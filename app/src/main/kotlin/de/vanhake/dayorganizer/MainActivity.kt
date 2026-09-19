package de.vanhake.dayorganizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.ui.heute.HeuteScreen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerThema

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DayOrganizerThema {
                Surface { HeuteScreen() }
            }
        }
    }
}
