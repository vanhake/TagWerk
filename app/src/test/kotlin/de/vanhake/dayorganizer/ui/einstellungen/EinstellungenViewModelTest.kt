package de.vanhake.dayorganizer.ui.einstellungen

import app.cash.turbine.test
import de.vanhake.dayorganizer.backup.Backup
import de.vanhake.dayorganizer.backup.ImportErgebnis
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.platform.permission.Hinweis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.InputStream
import java.io.OutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EinstellungenViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private class FakeSpeicher(start: Einstellungen = Einstellungen()) {
        val stand = MutableStateFlow(start)
    }

    private class FakeBackup(
        private val importErgebnis: ImportErgebnis = ImportErgebnis.Erfolg(0),
        private val exportSchlaegtFehl: Boolean = false,
    ) : Backup {
        var exportiert = false
        var importiert = false

        override suspend fun exportieren(ziel: OutputStream) {
            if (exportSchlaegtFehl) error("Export fehlgeschlagen")
            exportiert = true
        }

        override suspend fun importieren(quelle: InputStream): ImportErgebnis {
            importiert = true
            return importErgebnis
        }
    }

    private fun viewModel(
        speicher: FakeSpeicher,
        hinweiseLesen: suspend () -> List<Hinweis> = { emptyList() },
        backupDienst: Backup = FakeBackup(),
        nachImport: () -> Unit = {},
    ) = EinstellungenViewModel(
        lesen = { speicher.stand.value },
        schreiben = { speicher.stand.value = it },
        hinweiseLesen = hinweiseLesen,
        backupDienst = backupDienst,
        nachImport = nachImport,
    )

    @Test
    fun `die gespeicherten Werte erscheinen im Zustand`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            assertEquals("25", zustand.tagesbonusText)
            assertEquals("45", zustand.standardKulanzText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `die Hinweise aus hinweiseLesen landen im Zustand`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher, hinweiseLesen = { listOf(Hinweis.WAECHTER_HAENGT) })

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            assertEquals(listOf(Hinweis.WAECHTER_HAENGT), zustand.hinweise)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Speichern uebernimmt gueltige Werte bei beiden Feldern`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("30")
        vm.kulanzAendern("90")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 30, standardKulanzMinuten = 90), speicher.stand.value)
    }

    @Test
    fun `Null ist ein gueltiger Wert bei beiden Feldern`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("0")
        vm.kulanzAendern("0")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 0, standardKulanzMinuten = 0), speicher.stand.value)
    }

    @Test
    fun `ein negativer Tagesbonus wird verworfen, die gueltige Kulanz aber uebernommen`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("-5")
        vm.kulanzAendern("90")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 90), speicher.stand.value)
    }

    @Test
    fun `eine negative Kulanz wird verworfen, der gueltige Tagesbonus aber uebernommen`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("30")
        vm.kulanzAendern("-5")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 30, standardKulanzMinuten = 45), speicher.stand.value)
    }

    @Test
    fun `ein unlesbarer Tagesbonus wird verworfen, die gueltige Kulanz aber uebernommen`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("viel")
        vm.kulanzAendern("90")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 90), speicher.stand.value)
    }

    @Test
    fun `eine unlesbare Kulanz wird verworfen, der gueltige Tagesbonus aber uebernommen`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("30")
        vm.kulanzAendern("viel")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 30, standardKulanzMinuten = 45), speicher.stand.value)
    }

    @Test
    fun `ein leeres Feld laesst den bisherigen Wert bei beiden Feldern unveraendert`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("")
        vm.kulanzAendern("")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45), speicher.stand.value)
    }

    @Test
    fun `ein erfolgreicher Export meldet EXPORT_FERTIG`() = runTest(dispatcher) {
        val backup = FakeBackup()
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.exportieren(java.io.ByteArrayOutputStream()).join()

        assertTrue(backup.exportiert)
        assertEquals(Meldung.EXPORT_FERTIG, vm.meldung.value)
    }

    @Test
    fun `ein gescheiterter Export meldet EXPORT_FEHLGESCHLAGEN statt die App zu beenden`() = runTest(dispatcher) {
        val backup = FakeBackup(exportSchlaegtFehl = true)
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.exportieren(java.io.ByteArrayOutputStream()).join()

        assertEquals(Meldung.EXPORT_FEHLGESCHLAGEN, vm.meldung.value)
    }

    @Test
    fun `ein erfolgreicher Import meldet IMPORT_FERTIG und stoesst die Waechterplanung an`() = runTest(dispatcher) {
        val backup = FakeBackup(importErgebnis = ImportErgebnis.Erfolg(3))
        var nachImportAufgerufen = false
        val vm = viewModel(FakeSpeicher(), backupDienst = backup, nachImport = { nachImportAufgerufen = true })

        vm.importieren(java.io.ByteArrayInputStream(ByteArray(0))).join()

        assertTrue(backup.importiert)
        assertEquals(Meldung.IMPORT_FERTIG, vm.meldung.value)
        assertTrue(nachImportAufgerufen)
    }

    @Test
    fun `ein falsches Schema meldet IMPORT_FALSCHES_SCHEMA`() = runTest(dispatcher) {
        val backup = FakeBackup(importErgebnis = ImportErgebnis.FalschesSchema(99))
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.importieren(java.io.ByteArrayInputStream(ByteArray(0))).join()

        assertEquals(Meldung.IMPORT_FALSCHES_SCHEMA, vm.meldung.value)
    }

    @Test
    fun `ein unlesbares Backup meldet IMPORT_UNLESBAR`() = runTest(dispatcher) {
        val backup = FakeBackup(importErgebnis = ImportErgebnis.Unlesbar("kaputt"))
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.importieren(java.io.ByteArrayInputStream(ByteArray(0))).join()

        assertEquals(Meldung.IMPORT_UNLESBAR, vm.meldung.value)
    }

    @Test
    fun `meldungGezeigt setzt die Meldung wieder auf null`() = runTest(dispatcher) {
        val vm = viewModel(FakeSpeicher(), backupDienst = FakeBackup())

        vm.exportieren(java.io.ByteArrayOutputStream()).join()
        assertEquals(Meldung.EXPORT_FERTIG, vm.meldung.value)

        vm.meldungGezeigt()

        assertNull(vm.meldung.value)
    }
}
