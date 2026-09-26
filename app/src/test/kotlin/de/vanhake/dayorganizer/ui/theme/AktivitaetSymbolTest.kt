package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
import kotlin.test.Test
import kotlin.test.assertEquals

class AktivitaetSymbolTest {

    @Test
    fun `ein bekannter Schluessel liefert sein Symbol`() {
        assertEquals(Icons.Filled.WaterDrop, aktivitaetSymbol("water_drop"))
    }

    @Test
    fun `ein unbekannter Schluessel faellt auf das Standardsymbol zurueck`() {
        assertEquals(Icons.Filled.SelfImprovement, aktivitaetSymbol("nicht_vorhanden"))
    }

    @Test
    fun `jeder Katalogeintrag liefert ein Symbol`() {
        AKTIVITAET_SYMBOLE.forEach { schluessel ->
            assertEquals(aktivitaetSymbol(schluessel), aktivitaetSymbol(schluessel))
        }
        assertEquals(12, AKTIVITAET_SYMBOLE.size)
    }
}
