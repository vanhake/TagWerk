package de.vanhake.dayorganizer.widget

import android.app.AlarmManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.platform.alarm.alsEpochMillis
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WidgetWeckerTest {

    private val zone = ZoneId.systemDefault()
    private val uhr = Clock.fixed(LocalDateTime.of(2026, 10, 16, 7, 30).atZone(zone).toInstant(), zone)
    private val kontext = ApplicationProvider.getApplicationContext<Context>()
    private val shadow = shadowOf(kontext.getSystemService<AlarmManager>()!!)

    private val acht = LocalDateTime.of(2026, 10, 16, 8, 0)
    private val heute = LocalDate.of(2026, 10, 16)

    private fun wecker() = WidgetWecker(
        kontext,
        FakeFaelligkeitRepository(
            listOf(
                Faelligkeit(
                    id = 1, aktivitaetId = 1, periodeVon = heute, periodeBis = heute,
                    geplantFuer = acht, sollAnzahl = 1,
                ),
            ),
        ),
        uhr,
    )

    @Test
    fun `planen stellt einen RTC-Alarm eine Sekunde nach dem Umschaltpunkt`() = runTest {
        wecker().planen()

        val alarm = assertNotNull(shadow.nextScheduledAlarm)
        assertEquals(AlarmManager.RTC, alarm.type)
        assertEquals(alsEpochMillis(acht, zone) + WidgetWecker.NACHLAUF_MS, alarm.triggerAtMs)
    }

    @Test
    fun `abbestellen entfernt den Alarm`() = runTest {
        val wecker = wecker()
        wecker.planen()

        wecker.abbestellen()

        assertNull(shadow.nextScheduledAlarm)
    }
}
