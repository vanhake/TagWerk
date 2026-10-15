package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Redeem
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungSymbolTest {

    @Test
    fun `ein bekannter Schluessel liefert sein Symbol`() {
        assertEquals(Icons.Filled.Movie, belohnungSymbol("movie"))
    }

    @Test
    fun `ein unbekannter Schluessel faellt auf das Standardsymbol zurueck`() {
        assertEquals(Icons.Filled.Redeem, belohnungSymbol("nicht_vorhanden"))
    }

    @Test
    fun `jeder Katalogeintrag liefert ein eigenes Symbol`() {
        assertEquals(12, BELOHNUNG_SYMBOLE.size)
        assertEquals(12, BELOHNUNG_SYMBOLE.map(::belohnungSymbol).toSet().size)
    }

    @Test
    fun `der Standard steht im Katalog und entspricht dem Modell-Standard`() {
        assertEquals("redeem", BELOHNUNG_SYMBOL_STANDARD)
        assertEquals(BELOHNUNG_SYMBOL_STANDARD, BELOHNUNG_SYMBOLE.first())
    }
}
