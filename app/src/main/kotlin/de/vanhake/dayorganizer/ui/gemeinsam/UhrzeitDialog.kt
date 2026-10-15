package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.vanhake.dayorganizer.R
import java.time.LocalTime

/**
 * Zeitwaehler im AlertDialog. material3 1.3 (BOM 2024.12.01) hat noch keine fertige
 * TimePickerDialog-Composable, darum dieser schmale Rahmen. Immer 24-Stunden-Format --
 * die App ist durchgehend deutsch, und der Plantext zeigt HH:mm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UhrzeitDialog(start: LocalTime, bestaetigen: (LocalTime) -> Unit, abbrechen: () -> Unit) {
    val zustand = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = abbrechen,
        confirmButton = {
            TextButton(onClick = { bestaetigen(LocalTime.of(zustand.hour, zustand.minute)) }) {
                Text(stringResource(R.string.dialog_uebernehmen))
            }
        },
        dismissButton = {
            TextButton(onClick = abbrechen) { Text(stringResource(R.string.dialog_abbrechen)) }
        },
        text = { TimePicker(state = zustand) },
    )
}
