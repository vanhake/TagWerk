package de.vanhake.dayorganizer.platform.alarm

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmPlanerZeitzoneTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `eine ausgefallene Ortszeit wird auf den naechsten gueltigen Zeitpunkt gelegt`() {
        val millis = alsEpochMillis(LocalDateTime.of(2026, 3, 29, 2, 30), berlin)

        assertEquals(
            LocalDateTime.of(2026, 3, 29, 3, 30),
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), berlin),
        )
    }

    @Test
    fun `eine doppelte Ortszeit nimmt den ersten Durchgang`() {
        val millis = alsEpochMillis(LocalDateTime.of(2026, 10, 25, 2, 30), berlin)

        // Der erste Durchgang liegt noch in der Sommerzeit, also UTC+2.
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), Instant.ofEpochMilli(millis))
    }

    @Test
    fun `eine gewoehnliche Ortszeit bleibt unveraendert`() {
        val millis = alsEpochMillis(LocalDateTime.of(2026, 3, 2, 8, 0), berlin)

        assertEquals(Instant.parse("2026-03-02T07:00:00Z"), Instant.ofEpochMilli(millis))
    }
}
