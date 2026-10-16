package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.vanhake.dayorganizer.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Datumswaehler. Der Material-DatePicker rechnet in UTC-Millisekunden um Mitternacht --
 * Umrechnung darum bewusst ueber ZoneOffset.UTC, sonst verschoebe eine Zeitzone westlich
 * von UTC das Datum um einen Tag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatumDialog(start: LocalDate, bestaetigen: (LocalDate) -> Unit, abbrechen: () -> Unit) {
    val zustand = rememberDatePickerState(
        initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = abbrechen,
        confirmButton = {
            TextButton(
                onClick = {
                    zustand.selectedDateMillis?.let { millis ->
                        bestaetigen(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = zustand.selectedDateMillis != null,
            ) { Text(stringResource(R.string.dialog_uebernehmen)) }
        },
        dismissButton = {
            TextButton(onClick = abbrechen) { Text(stringResource(R.string.dialog_abbrechen)) }
        },
    ) {
        DatePicker(state = zustand)
    }
}
