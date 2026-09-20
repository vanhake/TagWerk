package de.vanhake.dayorganizer.platform.alarm

import android.app.AlarmManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.test.core.app.ApplicationProvider
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.domain.model.Erinnerung
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

/**
 * Sichert das zentrale Versprechen von [AlarmPlaner]: Es ist zu jedem Zeitpunkt
 * genau ein exakter Alarm gesetzt, naemlich fuer die naechste faellige Erinnerung.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AlarmPlanerTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 6, 0).atZone(zone).toInstant(),
        zone,
    )

    private val kontext = ApplicationProvider.getApplicationContext<Context>()
    private val alarmManager = kontext.getSystemService<AlarmManager>()!!
    private val shadowAlarmManager: ShadowAlarmManager = shadowOf(alarmManager)

    @Before
    fun erlaubeExakteAlarme() {
        // Entspricht dem Regelfall (Nutzer hat die Berechtigung erteilt) und
        // prueft damit den im Design versprochenen exakten Pfad.
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private fun erinnerung(id: Long, zeitpunkt: LocalDateTime, ausgeloest: Boolean = false) =
        Erinnerung(id = id, faelligkeitId = id, zeitpunkt = zeitpunkt, ausgeloest = ausgeloest)

    @Test
    fun `neuPlanen setzt genau einen Alarm auf die naechste faellige Erinnerung`() = runTest {
        val fruehere = LocalDateTime.of(2026, 3, 2, 8, 0)
        val spaetere = LocalDateTime.of(2026, 3, 2, 12, 0)
        val repo = FakeErinnerungRepository(listOf(erinnerung(1, spaetere), erinnerung(2, fruehere)))
        val planer = AlarmPlaner(kontext, repo, uhr)

        planer.neuPlanen()

        val alarme = shadowAlarmManager.scheduledAlarms
        assertEquals(1, alarme.size)
        assertEquals(alsEpochMillis(fruehere, ZoneId.systemDefault()), alarme.single().triggerAtMs)
    }

    @Test
    fun `zweimaliges neuPlanen ersetzt den Alarm statt ihn zu verdoppeln`() = runTest {
        val repo = FakeErinnerungRepository(listOf(erinnerung(1, LocalDateTime.of(2026, 3, 2, 8, 0))))
        val planer = AlarmPlaner(kontext, repo, uhr)

        planer.neuPlanen()
        planer.neuPlanen()

        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun `nach dem Ausloesen der fruehesten Erinnerung plant neuPlanen die naechste ein`() = runTest {
        val fruehere = LocalDateTime.of(2026, 3, 2, 8, 0)
        val spaetere = LocalDateTime.of(2026, 3, 2, 12, 0)
        val repo = FakeErinnerungRepository(listOf(erinnerung(1, fruehere), erinnerung(2, spaetere)))
        val planer = AlarmPlaner(kontext, repo, uhr)
        planer.neuPlanen()

        repo.alsAusgeloestMarkieren(1)
        planer.neuPlanen()

        val alarme = shadowAlarmManager.scheduledAlarms
        assertEquals(1, alarme.size)
        assertEquals(alsEpochMillis(spaetere, ZoneId.systemDefault()), alarme.single().triggerAtMs)
    }

    @Test
    fun `neuPlanen ohne faellige Erinnerung storniert einen zuvor gesetzten Alarm`() = runTest {
        val repo = FakeErinnerungRepository(listOf(erinnerung(1, LocalDateTime.of(2026, 3, 2, 8, 0))))
        val planer = AlarmPlaner(kontext, repo, uhr)
        planer.neuPlanen()
        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)

        repo.stand.value = emptyList()
        planer.neuPlanen()

        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun `bereits ausgeloeste Erinnerungen werden nicht eingeplant`() = runTest {
        val repo = FakeErinnerungRepository(
            listOf(erinnerung(1, LocalDateTime.of(2026, 3, 2, 8, 0), ausgeloest = true)),
        )
        val planer = AlarmPlaner(kontext, repo, uhr)

        planer.neuPlanen()

        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun `eine vergangene Erinnerung wird sofort eingeplant`() = runTest {
        val vergangen = LocalDateTime.of(2026, 3, 1, 20, 0)
        val repo = FakeErinnerungRepository(listOf(erinnerung(1, vergangen)))
        val planer = AlarmPlaner(kontext, repo, uhr)

        planer.neuPlanen()

        val alarme = shadowAlarmManager.scheduledAlarms
        assertEquals(1, alarme.size)
        assertEquals(uhr.instant().toEpochMilli(), alarme.single().triggerAtMs)
    }

    @Test
    fun `von einer vergangenen und einer kuenftigen Erinnerung gewinnt die vergangene`() = runTest {
        val vergangen = LocalDateTime.of(2026, 3, 1, 20, 0)
        val kuenftig = LocalDateTime.of(2026, 3, 2, 12, 0)
        val repo = FakeErinnerungRepository(listOf(erinnerung(1, kuenftig), erinnerung(2, vergangen)))
        val planer = AlarmPlaner(kontext, repo, uhr)

        planer.neuPlanen()

        val alarme = shadowAlarmManager.scheduledAlarms
        assertEquals(1, alarme.size)
        assertEquals(uhr.instant().toEpochMilli(), alarme.single().triggerAtMs)
    }

    @Test
    fun `eine bereits ausgeloeste vergangene Erinnerung wird weiterhin ignoriert`() = runTest {
        val vergangen = LocalDateTime.of(2026, 3, 1, 20, 0)
        val repo = FakeErinnerungRepository(listOf(erinnerung(1, vergangen, ausgeloest = true)))
        val planer = AlarmPlaner(kontext, repo, uhr)

        planer.neuPlanen()

        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
    }
}
