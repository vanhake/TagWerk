package de.vanhake.dayorganizer.ui.gemeinsam

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToastStapelTest {

    // Virtuelle Zeit seit Teststart; die Hilfsfunktion springt auf einen absoluten Zeitpunkt.
    private fun kotlinx.coroutines.test.TestScope.bis(zeitMs: Long) {
        advanceTimeBy(zeitMs - testScheduler.currentTime)
        runCurrent()
    }

    @Test
    fun `zwei Rueckgaengig-Toasts stapeln sich und Rueckgaengig nimmt den juengsten zuerst`() = runTest {
        val stapel = ToastStapel(backgroundScope)
        val a: suspend () -> Unit = {}
        val b: suspend () -> Unit = {}
        stapel.zeigen("A", a)
        bis(1_000)
        stapel.zeigen("B", b)
        assertEquals("B", stapel.sichtbar.value?.text)

        assertSame(b, stapel.rueckgaengigNehmen())
        assertEquals("A", stapel.sichtbar.value?.text)
        assertSame(a, stapel.rueckgaengigNehmen())
        assertNull(stapel.sichtbar.value)
    }

    @Test
    fun `ein abgelaufener Eintrag kommt nach Rueckgaengig nicht wieder`() = runTest {
        val stapel = ToastStapel(backgroundScope)
        val a: suspend () -> Unit = {}
        val b: suspend () -> Unit = {}
        stapel.zeigen("A", a)
        bis(3_000)
        stapel.zeigen("B", b)
        bis(4_000)

        assertSame(b, stapel.rueckgaengigNehmen())
        assertNull(stapel.sichtbar.value)
        assertNull(stapel.rueckgaengigNehmen())
    }

    @Test
    fun `ein verdeckter Eintrag laeuft trotzdem ab`() = runTest {
        val stapel = ToastStapel(backgroundScope)
        stapel.zeigen("A") { }
        bis(1_000)
        stapel.zeigen("B") { }
        bis(3_900)
        assertEquals("B", stapel.sichtbar.value?.text)
        bis(4_900)
        assertNull(stapel.sichtbar.value)
    }

    @Test
    fun `ein Info-Toast wartet bis kein Rueckgaengig mehr offen ist`() = runTest {
        val stapel = ToastStapel(backgroundScope)
        stapel.zeigen("A") { }
        bis(1_000)
        stapel.zeigen("I")
        assertEquals("A", stapel.sichtbar.value?.text)
        bis(3_800)
        assertEquals("I", stapel.sichtbar.value?.text)
        assertFalse(stapel.sichtbar.value!!.kannRueckgaengig)
        bis(7_599)
        assertEquals("I", stapel.sichtbar.value?.text)
        bis(7_600)
        assertNull(stapel.sichtbar.value)
    }

    @Test
    fun `ohne offenes Rueckgaengig ersetzt ein Info-Toast den vorigen sofort`() = runTest {
        val stapel = ToastStapel(backgroundScope)
        stapel.zeigen("I1")
        bis(1_000)
        stapel.zeigen("I2")
        assertEquals("I2", stapel.sichtbar.value?.text)
        bis(4_799)
        assertEquals("I2", stapel.sichtbar.value?.text)
        bis(4_800)
        assertNull(stapel.sichtbar.value)
    }

    @Test
    fun `Rueckgaengig waehrend ein Info-Toast wartet zeigt sofort den Info-Toast`() = runTest {
        val stapel = ToastStapel(backgroundScope)
        stapel.zeigen("A") { }
        bis(500)
        stapel.zeigen("I")
        bis(1_000)
        stapel.rueckgaengigNehmen()
        assertEquals("I", stapel.sichtbar.value?.text)
    }
}
