package de.vanhake.dayorganizer.backup

import java.io.ByteArrayInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupLesenTest {

    @Test
    fun `liest Text bis zur Grenze vollstaendig`() {
        val text = "{\"schemaVersion\":5,\"name\":\"Zähne putzen\"}"
        val bytes = text.toByteArray(Charsets.UTF_8)
        assertEquals(text, ByteArrayInputStream(bytes).backupTextLesen(grenze = bytes.size))
    }

    @Test
    fun `bricht bei zu grosser Datei ab statt alles zu laden`() {
        val strom = ByteArrayInputStream(ByteArray(20_000) { 'a'.code.toByte() })
        assertFailsWith<BackupZuGross> { strom.backupTextLesen(grenze = 10_000) }
    }
}
