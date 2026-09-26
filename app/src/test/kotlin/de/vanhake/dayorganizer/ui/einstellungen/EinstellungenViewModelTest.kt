package de.vanhake.dayorganizer.ui.einstellungen

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import de.vanhake.dayorganizer.backup.Backup
import de.vanhake.dayorganizer.backup.BackupUmwandlung
import de.vanhake.dayorganizer.backup.ImportErgebnis
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.ThemeModus
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.platform.permission.Hinweis
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        private val beimImportieren: suspend () -> Unit = {},
    ) : Backup {
        var exportiert = false
        var importiert = false

        override suspend fun exportieren(ziel: OutputStream) {
            if (exportSchlaegtFehl) error("Export fehlgeschlagen")
            exportiert = true
        }

        override suspend fun importieren(quelle: InputStream): ImportErgebnis {
            importiert = true
            beimImportieren()
            return importErgebnis
        }
    }

    private fun aktivitaet(id: Long, name: String) = Aktivitaet(
        id = id, name = name, farbe = 1, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), setOf(DayOfWeek.MONDAY)),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    /** Baut echten Backup-Text ueber BackupUmwandlung -- keine zweite, handgeschriebene
     * Leselogik fuer die Tests der Importvorschau (Review-Fund W2). */
    private fun backupText(
        erstelltAm: LocalDateTime = LocalDateTime.of(2026, 3, 2, 21, 0),
        aktivitaeten: List<Aktivitaet> = emptyList(),
    ) = BackupUmwandlung.zuText(
        BackupUmwandlung.zuDatei(
            erstelltAm = erstelltAm,
            aktivitaeten = aktivitaeten,
            faelligkeiten = emptyList(), buchungen = emptyList(),
            belohnungen = emptyList(), einloesungen = emptyList(),
            einstellungen = Einstellungen(),
        ),
    )

    private fun fremdesSchemaText() = """
        {"schemaVersion":99,"erstelltAm":"2026-03-02T21:00","aktivitaeten":[],
        "faelligkeiten":[],"buchungen":[],"belohnungen":[],"einloesungen":[],
        "einstellungen":{"tagesbonusPunkte":15,"standardKulanzMinuten":60}}
    """.trimIndent()

    private fun viewModel(
        speicher: FakeSpeicher,
        hinweiseLesen: suspend () -> List<Hinweis> = { emptyList() },
        backupDienst: Backup = FakeBackup(),
        nachImport: suspend () -> Unit = {},
        // Ohne den Test-Dispatcher wuerde exportieren/importieren -- seit sie auf
        // Dispatchers.IO laufen (Review-Fund K1) -- ausserhalb der virtuellen Testzeit
        // ablaufen, und advanceUntilIdle()/join() koennten nicht mehr deterministisch
        // auf sie warten. Deshalb reicht die Testfabrik denselben StandardTestDispatcher
        // herein, den runTest(dispatcher) auch fuer Dispatchers.Main benutzt.
        ioDispatcher: CoroutineDispatcher = dispatcher,
        heute: () -> LocalDate = { LocalDate.of(2026, 3, 2) },
    ) = EinstellungenViewModel(
        lesen = { speicher.stand.value },
        schreiben = { speicher.stand.value = it },
        hinweiseLesen = hinweiseLesen,
        backupDienst = backupDienst,
        nachImport = nachImport,
        ioDispatcher = ioDispatcher,
        heute = heute,
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

    // Ein Farbmodus ist ein Umschalter, kein Formularfeld: die Aenderung muss sofort
    // geschrieben werden, ohne auf den "Speichern"-Knopf zu warten (der nur fuer die
    // Zahlenfelder gilt).
    @Test
    fun `themeModusAendern schreibt sofort, ohne auf Speichern zu warten`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher)

        vm.themeModusAendern(ThemeModus.DUNKEL)
        advanceUntilIdle()

        assertEquals(ThemeModus.DUNKEL, speicher.stand.value.themeModus)
        assertEquals(ThemeModus.DUNKEL, vm.zustand.value.themeModus)
    }

    // Review-Fund (Kritisch): speichern() baute die neue Einstellungen bisher per
    // Einstellungen(...) mit nur den beiden Zahlenfeldern statt per bisher.copy(...) --
    // dadurch fiel themeModus stets auf den Klassendefault HELL zurueck, auch wenn zuvor
    // per themeModusAendern() DUNKEL geschrieben wurde. Dieser Test haette den alten Bug
    // gefangen: mit Einstellungen(...) waere neu.themeModus == HELL gewesen, unabhaengig
    // vom zuvor gesetzten DUNKEL, und die Assertion unten waere fehlgeschlagen.
    @Test
    fun `speichern setzt den zuvor per themeModusAendern gesetzten Farbmodus nicht zurueck`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher)

        vm.themeModusAendern(ThemeModus.DUNKEL)
        advanceUntilIdle()
        vm.speichern().join()

        assertEquals(ThemeModus.DUNKEL, speicher.stand.value.themeModus)
        assertEquals(ThemeModus.DUNKEL, vm.zustand.value.themeModus)
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

        vm.exportieren { ByteArrayOutputStream() }.join()

        assertTrue(backup.exportiert)
        assertEquals(Meldung.EXPORT_FERTIG, vm.meldung.value)
    }

    @Test
    fun `ein gescheiterter Export meldet EXPORT_FEHLGESCHLAGEN statt die App zu beenden`() = runTest(dispatcher) {
        val backup = FakeBackup(exportSchlaegtFehl = true)
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.exportieren { ByteArrayOutputStream() }.join()

        assertEquals(Meldung.EXPORT_FEHLGESCHLAGEN, vm.meldung.value)
    }

    // Review-Fund K1, per Test bewiesen: Oeffnen, Schreiben und Schliessen des Stroms
    // muessen in derselben Koroutine passieren. Das alte Muster (Screen oeffnet und
    // schliesst den Strom selbst, bevor die nur *gestartete* Koroutine im ViewModel
    // ueberhaupt zu schreiben beginnt) haette hier "geschlossen" VOR "geschrieben"
    // aufgezeichnet -- oder gar keinen Inhalt im Strom hinterlassen.
    //
    // Mutationsnachweis: baut man das alte Muster testweise nach --
    //   val strom = oeffnen(); backupDienst.exportieren(strom); strom.close() ausserhalb
    //   von viewModelScope.launch, also VOR dem Start der Koroutine --
    // faellt dieser Test, weil "geschlossen" dann vor "geschrieben" steht bzw. der
    // Puffer beim Auslesen leer ist.
    @Test
    fun `der Ausgabestrom wird erst nach dem Schreiben geschlossen`() = runTest(dispatcher) {
        val ereignisse = mutableListOf<String>()
        val backup = object : Backup {
            override suspend fun exportieren(ziel: OutputStream) {
                ziel.write("Inhalt".toByteArray())
                ereignisse += "geschrieben"
            }
            override suspend fun importieren(quelle: InputStream): ImportErgebnis =
                error("in diesem Test nicht benutzt")
        }
        val strom = object : ByteArrayOutputStream() {
            override fun close() {
                ereignisse += "geschlossen"
                super.close()
            }
        }
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.exportieren { strom }.join()

        assertEquals(listOf("geschrieben", "geschlossen"), ereignisse)
        assertEquals("Inhalt", strom.toString())
    }

    // Review-Fund W5: Ein Wurf oder ein fehlender Strom beim Oeffnen darf weder
    // abstuerzen noch stumm nichts tun, sondern muss als EXPORT_FEHLGESCHLAGEN enden.
    @Test
    fun `ein gescheitertes Oeffnen des Ausgabestroms meldet EXPORT_FEHLGESCHLAGEN`() = runTest(dispatcher) {
        val vm = viewModel(FakeSpeicher())

        vm.exportieren { throw SecurityException("kein Zugriff") }.join()
        assertEquals(Meldung.EXPORT_FEHLGESCHLAGEN, vm.meldung.value)

        vm.meldungGezeigt()
        vm.exportieren { null }.join()
        assertEquals(Meldung.EXPORT_FEHLGESCHLAGEN, vm.meldung.value)
    }

    // Review-Fund W5, jetzt fuer den ersten Schritt des zweistufigen Imports (Review-Fund
    // W2): ein gescheitertes Oeffnen des Eingabestroms zeigt keine Rueckfrage, sondern
    // direkt IMPORT_UNLESBAR.
    @Test
    fun `ein gescheitertes Oeffnen des Eingabestroms meldet IMPORT_UNLESBAR ohne Rueckfrage`() = runTest(dispatcher) {
        val vm = viewModel(FakeSpeicher())

        vm.importVorschauAnfordern { throw FileNotFoundException() }.join()
        assertEquals(Meldung.IMPORT_UNLESBAR, vm.meldung.value)
        assertNull(vm.importVorschau.value)

        vm.meldungGezeigt()
        vm.importVorschauAnfordern { null }.join()
        assertEquals(Meldung.IMPORT_UNLESBAR, vm.meldung.value)
        assertNull(vm.importVorschau.value)
    }

    // Review-Fund W2: eine gueltige Datei zeigt zuerst eine Vorschau -- von wann sie ist
    // und wie viele Aktivitaeten sie enthaelt -- statt sofort zu importieren.
    @Test
    fun `die Vorschau einer gueltigen Datei zeigt Datum und Anzahl`() = runTest(dispatcher) {
        val erstelltAm = LocalDateTime.of(2026, 4, 1, 10, 30)
        val text = backupText(erstelltAm, aktivitaeten = listOf(aktivitaet(1, "Sport"), aktivitaet(2, "Lesen")))
        val backup = FakeBackup()
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()

        assertEquals(ImportVorschau(erstelltAm, 2), vm.importVorschau.value)
        assertNull(vm.meldung.value)
        assertFalse(backup.importiert)
    }

    // Review-Fund W2: eine ungueltige Datei (falsches Schema) zeigt keine Rueckfrage,
    // sondern direkt die bestehende Fehlermeldung.
    @Test
    fun `eine fremde Schemaversion zeigt keine Rueckfrage, sondern die Fehlermeldung`() = runTest(dispatcher) {
        val backup = FakeBackup()
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.importVorschauAnfordern { ByteArrayInputStream(fremdesSchemaText().toByteArray()) }.join()

        assertEquals(Meldung.IMPORT_FALSCHES_SCHEMA, vm.meldung.value)
        assertNull(vm.importVorschau.value)
        assertFalse(backup.importiert)
    }

    // Review-Fund W2: eine unlesbare Datei (kaputtes JSON) zeigt ebenfalls keine
    // Rueckfrage, sondern direkt die Fehlermeldung.
    @Test
    fun `eine unlesbare Datei zeigt keine Rueckfrage, sondern die Fehlermeldung`() = runTest(dispatcher) {
        val backup = FakeBackup()
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.importVorschauAnfordern { ByteArrayInputStream("{kein json".toByteArray()) }.join()

        assertEquals(Meldung.IMPORT_UNLESBAR, vm.meldung.value)
        assertNull(vm.importVorschau.value)
        assertFalse(backup.importiert)
    }

    // Review-Fund W2: "Abbrechen" im Vorschau-Dialog laesst den Bestand unveraendert --
    // backupDienst.importieren() wird gar nicht erst aufgerufen.
    @Test
    fun `Abbrechen importiert nicht und laesst den Bestand unveraendert`() = runTest(dispatcher) {
        val text = backupText()
        val backup = FakeBackup()
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()
        assertTrue(vm.importVorschau.value != null)

        vm.importVorschauAbbrechen()
        assertNull(vm.importVorschau.value)

        // Ein spaeteres importBestaetigen() darf jetzt nichts mehr tun -- der gehaltene
        // Text ist mit dem Abbrechen verworfen worden.
        vm.importBestaetigen().join()
        assertFalse(backup.importiert)
        assertNull(vm.meldung.value)
    }

    // Review-Fund W2: der zweite Schritt importiert mit dem in importVorschauAnfordern
    // gelesenen Text -- die Datei wird dafuer kein zweites Mal geoeffnet.
    @Test
    fun `Bestaetigen importiert und stoesst die Waechterplanung an`() = runTest(dispatcher) {
        val text = backupText(aktivitaeten = listOf(aktivitaet(1, "Sport")))
        val backup = FakeBackup(importErgebnis = ImportErgebnis.Erfolg(1))
        var nachImportAufgerufen = false
        val vm = viewModel(FakeSpeicher(), backupDienst = backup, nachImport = { nachImportAufgerufen = true })

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()
        vm.importBestaetigen().join()

        assertTrue(backup.importiert)
        assertEquals(Meldung.IMPORT_FERTIG, vm.meldung.value)
        assertTrue(nachImportAufgerufen)
        assertNull(vm.importVorschau.value)
    }

    // Review-Fund W1: Scheitert nur das Schreiben der Einstellungen -- der Datenbestand
    // ist laengst ersetzt --, muss das ViewModel das ehrlich als eigene Meldung zeigen,
    // nicht als vollen Erfolg.
    @Test
    fun `Daten uebernommen aber Einstellungen fehlgeschlagen meldet das eigene Ergebnis`() = runTest(dispatcher) {
        val text = backupText()
        val backup = FakeBackup(importErgebnis = ImportErgebnis.Erfolg(3, einstellungenUebernommen = false))
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()
        vm.importBestaetigen().join()

        assertEquals(Meldung.IMPORT_EINSTELLUNGEN_FEHLGESCHLAGEN, vm.meldung.value)
    }

    // Defensive Behandlung im zweiten Schritt: backupDienst.importieren() kann trotz
    // gueltiger Vorschau noch FalschesSchema/Unlesbar liefern (z. B. eine Race zwischen
    // Vorschau und Bestaetigen) -- das ViewModel muss auch das korrekt abbilden.
    @Test
    fun `ein falsches Schema beim Bestaetigen meldet IMPORT_FALSCHES_SCHEMA`() = runTest(dispatcher) {
        val text = backupText()
        val backup = FakeBackup(importErgebnis = ImportErgebnis.FalschesSchema(99))
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()
        vm.importBestaetigen().join()

        assertEquals(Meldung.IMPORT_FALSCHES_SCHEMA, vm.meldung.value)
    }

    @Test
    fun `ein unlesbares Backup beim Bestaetigen meldet IMPORT_UNLESBAR`() = runTest(dispatcher) {
        val text = backupText()
        val backup = FakeBackup(importErgebnis = ImportErgebnis.Unlesbar("kaputt"))
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()
        vm.importBestaetigen().join()

        assertEquals(Meldung.IMPORT_UNLESBAR, vm.meldung.value)
    }

    // Review-Fund W3: Ein abgelehnter Import (falsches Schema oder unlesbar) hat am
    // Bestand nichts veraendert -- die alten Benachrichtigungen zeigen weiterhin auf
    // die richtigen Faelligkeiten, also darf nachImport nicht aufgerufen werden.
    @Test
    fun `ein Import mit falschem Schema ruft nachImport nicht auf`() = runTest(dispatcher) {
        val text = backupText()
        val backup = FakeBackup(importErgebnis = ImportErgebnis.FalschesSchema(99))
        var nachImportAufgerufen = false
        val vm = viewModel(FakeSpeicher(), backupDienst = backup, nachImport = { nachImportAufgerufen = true })

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()
        vm.importBestaetigen().join()

        assertFalse(nachImportAufgerufen)
    }

    @Test
    fun `ein unlesbares Backup ruft nachImport nicht auf`() = runTest(dispatcher) {
        val text = backupText()
        val backup = FakeBackup(importErgebnis = ImportErgebnis.Unlesbar("kaputt"))
        var nachImportAufgerufen = false
        val vm = viewModel(FakeSpeicher(), backupDienst = backup, nachImport = { nachImportAufgerufen = true })

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()
        vm.importBestaetigen().join()

        assertFalse(nachImportAufgerufen)
    }

    // Review-Fund W6: Nach einem erfolgreichen Import muss der Zustand die importierten
    // Einstellungen zeigen -- sonst wuerde ein anschliessendes "Speichern" sie mit den
    // alten, noch angezeigten Werten ueberschreiben.
    @Test
    fun `nach einem erfolgreichen Import zeigt der Zustand die importierten Einstellungen`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val text = backupText()
        val backup = FakeBackup(
            importErgebnis = ImportErgebnis.Erfolg(1),
            beimImportieren = {
                speicher.stand.value = Einstellungen(tagesbonusPunkte = 99, standardKulanzMinuten = 5)
            },
        )
        val vm = viewModel(speicher, backupDienst = backup)
        vm.zustand.test { skipItems(1); cancelAndIgnoreRemainingEvents() }

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()
        vm.importBestaetigen().join()

        assertEquals("99", vm.zustand.value.tagesbonusText)
        assertEquals("5", vm.zustand.value.standardKulanzText)
    }

    // Review-Fund W1: Ein Widget-Eintrag kann nach dem Import auf eine ganz andere
    // Faelligkeit zeigen (die Datei bringt ihre eigenen ids mit) -- deshalb muss ein
    // erfolgreicher Import das Widget anstossen, genau wie es das fuer Benachrichtigungen
    // schon per alleVerstecken() tut. nachImport traegt diesen Anstoss im Produktivcode
    // (EinstellungenViewModel.@Inject-Konstruktor); hier wird gezaehlt statt nur ein
    // Bool gesetzt, damit ein versehentlicher doppelter Aufruf ebenfalls auffiele.
    @Test
    fun `ein erfolgreicher Import stoesst das Widget genau einmal an`() = runTest(dispatcher) {
        val text = backupText()
        val backup = FakeBackup(importErgebnis = ImportErgebnis.Erfolg(3))
        var widgetAnstossZaehler = 0
        val vm = viewModel(FakeSpeicher(), backupDienst = backup, nachImport = { widgetAnstossZaehler++ })

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()
        vm.importBestaetigen().join()

        assertEquals(1, widgetAnstossZaehler)
    }

    // Review-Fund W1: Ein abgelehnter Import (falsches Schema oder unlesbar) hat am
    // Bestand nichts veraendert -- ein Widget-Anstoss waere hier fehl am Platz.
    @Test
    fun `ein abgelehnter Import stoesst das Widget nicht an`() = runTest(dispatcher) {
        val text = backupText()
        val backup = FakeBackup(importErgebnis = ImportErgebnis.FalschesSchema(99))
        var widgetAnstossZaehler = 0
        val vm = viewModel(FakeSpeicher(), backupDienst = backup, nachImport = { widgetAnstossZaehler++ })

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()
        vm.importBestaetigen().join()

        assertEquals(0, widgetAnstossZaehler)
    }

    // Review-Fund W1, per Test bewiesen: Verlaesst der Nutzer den Einstellungen-Tab, waehrend
    // der Import laeuft, nimmt popUpTo den Eintrag vom Navigations-Stapel und viewModelScope
    // wird abgebrochen. Trifft dieser Abbruch NACH dem erfolgreichen backupDienst.importieren()
    // ein -- der Datenbestand ist dann schon ersetzt und committet --, muessen neuLaden() und
    // nachImport() trotzdem zu Ende laufen: sonst bleibt die Tabelle erinnerung leer und kein
    // Waechter wird angestossen.
    //
    // Der Abbruch wird hier ueber vm.viewModelScope.cancel() ausgeloest, wirklich waehrend
    // neuLaden() laeuft (in hinweiseLesen, direkt nach dem simulierten Commit per
    // beimImportieren), gefolgt von einem echten Suspend-Punkt (delay), an dem eine
    // kooperative Kotlin-Koroutine den Abbruch ueberhaupt bemerken kann.
    //
    // Mutationsnachweis: entfernt man withContext(NonCancellable) in
    // EinstellungenViewModel.importBestaetigen(), wirft delay() nach dem cancel() eine
    // CancellationException, neuLaden() bricht ab, und nachImportAufgerufen bleibt false --
    // dieser Test faellt dann.
    @Test
    fun `ein Abbruch nach dem erfolgreichen Import laesst neuLaden und nachImport trotzdem zu Ende laufen`() =
        runTest(dispatcher) {
            lateinit var vm: EinstellungenViewModel
            var nachImportAufgerufen = false
            var importAbgeschlossen = false
            val text = backupText()
            val backup = FakeBackup(
                importErgebnis = ImportErgebnis.Erfolg(3),
                beimImportieren = { importAbgeschlossen = true },
            )

            vm = viewModel(
                FakeSpeicher(),
                hinweiseLesen = {
                    if (importAbgeschlossen) {
                        // Simuliert den Tab-Wechsel unmittelbar nach dem Commit.
                        vm.viewModelScope.cancel()
                        delay(1)
                    }
                    emptyList()
                },
                backupDienst = backup,
                nachImport = { nachImportAufgerufen = true },
            )

            vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()
            vm.importBestaetigen().join()

            assertTrue(nachImportAufgerufen)
        }

    @Test
    fun `meldungGezeigt setzt die Meldung wieder auf null`() = runTest(dispatcher) {
        val vm = viewModel(FakeSpeicher(), backupDienst = FakeBackup())

        vm.exportieren { ByteArrayOutputStream() }.join()
        assertEquals(Meldung.EXPORT_FERTIG, vm.meldung.value)

        vm.meldungGezeigt()

        assertNull(vm.meldung.value)
    }

    // Review-Fund W2: der vorgeschlagene Export-Dateiname traegt das heutige Datum aus
    // der injizierten Clock -- hier ueber [heute] simuliert, kein now() im Produktivcode.
    @Test
    fun `der vorgeschlagene Dateiname traegt das Datum der Testuhr`() = runTest(dispatcher) {
        val vm = viewModel(FakeSpeicher(), heute = { LocalDate.of(2026, 4, 17) })

        assertEquals("2026-04-17", vm.exportDatum)
    }
}
