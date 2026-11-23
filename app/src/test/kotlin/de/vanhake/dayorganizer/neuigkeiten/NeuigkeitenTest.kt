package de.vanhake.dayorganizer.neuigkeiten

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NeuigkeitenTest {
    private fun eintrag(id: Int) = NeuigkeitenEintrag(id, "2026-11-0$id", listOf("Aenderung $id"))
    private val eintraege = listOf(eintrag(1), eintrag(3), eintrag(2))

    @Test
    fun `Neuinstallation zeigt nichts`() {
        assertTrue(Neuigkeiten.anzuzeigen(eintraege, zuletztGesehen = null, istUpdate = false).isEmpty())
    }

    @Test
    fun `erstes Update mit dieser Funktion zeigt nur den neuesten Eintrag`() {
        assertEquals(listOf(3), Neuigkeiten.anzuzeigen(eintraege, zuletztGesehen = null, istUpdate = true).map { it.id })
    }

    @Test
    fun `zeigt alle Einträge seit dem zuletzt gesehenen, neueste zuerst`() {
        assertEquals(listOf(3, 2), Neuigkeiten.anzuzeigen(eintraege, zuletztGesehen = 1, istUpdate = true).map { it.id })
    }

    @Test
    fun `Update ohne neue Einträge zeigt nichts`() {
        assertTrue(Neuigkeiten.anzuzeigen(eintraege, zuletztGesehen = 3, istUpdate = true).isEmpty())
    }

    @Test
    fun `parsen sortiert absteigend und ignoriert unbekannte Felder`() {
        val gelesen = Neuigkeiten.parsen(
            """{"entries":[{"id":1,"date":"2026-01-01","changes":["a"]},{"id":2,"date":"2026-02-01","changes":["b"],"x":1}]}""",
        )
        assertEquals(listOf(2, 1), gelesen.map { it.id })
        assertEquals(listOf("b"), gelesen.first().aenderungen)
    }

    @Test
    fun `probleme findet doppelte ids, falsches Datum und leere Änderungen`() {
        val kaputt = listOf(
            NeuigkeitenEintrag(1, "2026-11-23", listOf("a")),
            NeuigkeitenEintrag(1, "23.11.2026", emptyList()),
        )
        assertEquals(3, Neuigkeiten.probleme(kaputt).size)
    }

    @Test
    fun `datumFormatieren macht aus ISO das deutsche Datum`() {
        assertEquals("23.11.2026", Neuigkeiten.datumFormatieren("2026-11-23"))
    }

    @Test
    fun `changelog json der App ist gültig`() {
        val datei = File("src/main/assets/changelog.json")
        check(datei.exists()) { "changelog.json nicht gefunden: ${datei.absolutePath}" }
        assertEquals(emptyList<String>(), Neuigkeiten.probleme(Neuigkeiten.parsen(datei.readText())))
    }
}
