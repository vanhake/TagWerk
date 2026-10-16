package de.vanhake.dayorganizer.ui.einstellungen

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import de.vanhake.dayorganizer.backup.Backup
import de.vanhake.dayorganizer.backup.BackupUmwandlung
import de.vanhake.dayorganizer.backup.ImportErgebnis
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.ThemeModus
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.platform.permission.Hinweis
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
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
        belohnungen: List<Belohnung> = emptyList(),
    ) = BackupUmwandlung.zuText(
        BackupUmwandlung.zuDatei(
            erstelltAm = erstelltAm,
            aktivitaeten = aktivitaeten,
            faelligkeiten = emptyList(), buchungen = emptyList(),
            belohnungen = belohnungen, einloesungen = emptyList(),
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
        nachFarbmodus: suspend () -> Unit = {},
        // Ohne den Test-Dispatcher wuerde exportieren/importieren -- seit sie auf
        // Dispatchers.IO laufen (Review-Fund K1) -- ausserhalb der virtuellen Testzeit
        // ablaufen, und advanceUntilIdle()/join() koennten nicht mehr deterministisch
        // auf sie warten. Deshalb reicht die Testfabrik denselben StandardTestDispatcher
        // herein, den runTest(dispatcher) auch fuer Dispatchers.Main benutzt.
        ioDispatcher: CoroutineDispatcher = dispatcher,
        heute: () -> LocalDate = { LocalDate.of(2026, 3, 2) },
        // Verzoegertes Schreiben laesst die Laeufe schneller Tipps in virtueller Zeit
        // ueberlappen -- nur so zeigt sich, ob ein Schritt verloren ginge.
        schreibVerzoegerung: Long = 0,
        // Verzoegerung je Aufruf in Aufrufreihenfolge (ueberschreibt schreibVerzoegerung,
        // solange Eintraege da sind): erlaubt einen langsamen ersten und schnellen zweiten Lauf.
        schreibVerzoegerungen: List<Long> = emptyList(),
        schreibFehler: Boolean = false,
        // Ersetzt das Standard-Lesen, z. B. um beim n-ten Aufruf zu scheitern.
        lesenFake: (suspend () -> Einstellungen)? = null,
        // Ersetzt das Standard-Schreiben, damit Tests Reihenfolge und Tor selbst bestimmen.
        schreibenFake: (suspend (Einstellungen) -> Unit)? = null,
    ): EinstellungenViewModel {
        var aufruf = 0
        return EinstellungenViewModel(
            lesen = lesenFake ?: { speicher.stand.value },
            schreiben = schreibenFake ?: {
                val verzoegerung = schreibVerzoegerungen.getOrNull(aufruf++) ?: schreibVerzoegerung
                if (verzoegerung > 0) delay(verzoegerung)
                if (schreibFehler) throw IOException("Platte voll")
                speicher.stand.value = it
            },
            hinweiseLesen = hinweiseLesen,
            backupDienst = backupDienst,
            nachImport = nachImport,
            nachFarbmodus = nachFarbmodus,
            ioDispatcher = ioDispatcher,
            heute = heute,
        )
    }

    @Test
    fun `die gespeicherten Werte erscheinen im Zustand`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            assertEquals(25, zustand.tagesbonusPunkte)
            assertEquals(45, zustand.standardKulanzMinuten)
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
    // geschrieben werden.
    @Test
    fun `themeModusAendern schreibt sofort, ohne auf Speichern zu warten`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher)
        advanceUntilIdle() // erst das Erstladen abwarten, sonst ueberschreibt es den Tipp

        vm.themeModusAendern(ThemeModus.DUNKEL)
        advanceUntilIdle()

        assertEquals(ThemeModus.DUNKEL, speicher.stand.value.themeModus)
        assertEquals(ThemeModus.DUNKEL, vm.zustand.value.themeModus)
    }

    @Test
    fun `ein Tipp auf Plus erhoeht den Tagesbonus um 5 und speichert sofort`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher)
        advanceUntilIdle() // Zustand geladen (kein Turbine: Startzustand == Defaults, StateFlow meldet nichts Neues)

        vm.tagesbonusAendern(+1).join()

        assertEquals(20, vm.zustand.value.tagesbonusPunkte)
        assertEquals(20, speicher.stand.value.tagesbonusPunkte)
    }

    // Review-Fokus 1: mit verzoegertem Schreiben ueberlappen die Laeufe. Ohne Mutex und
    // ohne "aktuellen Zustand zum Schreibzeitpunkt" wuerden alle fuenf denselben Ausgangswert
    // lesen und das Ergebnis waere 20 statt 40.
    @Test
    fun `fuenf schnelle Tipps verlieren keinen Schritt`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher, schreibVerzoegerung = 50)
        advanceUntilIdle() // Zustand geladen (kein Turbine: Startzustand == Defaults, StateFlow meldet nichts Neues)

        repeat(5) { vm.tagesbonusAendern(+1) }
        advanceUntilIdle()

        assertEquals(40, vm.zustand.value.tagesbonusPunkte)
        assertEquals(40, speicher.stand.value.tagesbonusPunkte)
    }

    // Beweist die Mutex: der erste Lauf ist langsam, der zweite schnell. Ohne Sperre
    // schriebe der zweite 25 bei t=0 und der erste ueberholte ihn bei t=100 mit 20.
    @Test
    fun `ein langsamer frueherer Schreibvorgang ueberholt keinen spaeteren`() = runTest(dispatcher) {
        val speicher = FakeSpeicher() // Start: Tagesbonus 15
        val vm = viewModel(speicher, schreibVerzoegerungen = listOf(100, 0))
        advanceUntilIdle() // Erstladen abwarten

        vm.tagesbonusAendern(+1)
        runCurrent()
        vm.tagesbonusAendern(+1)
        runCurrent()
        advanceUntilIdle()

        assertEquals(25, speicher.stand.value.tagesbonusPunkte)
    }

    // Review-Fokus 2
    @Test
    fun `Minus bei 0 bleibt bei 0`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 0, standardKulanzMinuten = 0))
        val vm = viewModel(speicher)
        advanceUntilIdle() // Zustand geladen (kein Turbine: Startzustand == Defaults, StateFlow meldet nichts Neues)

        vm.tagesbonusAendern(-1).join()
        vm.kulanzAendern(-1).join()

        assertEquals(0, speicher.stand.value.tagesbonusPunkte)
        assertEquals(0, speicher.stand.value.standardKulanzMinuten)
        assertEquals(0, vm.zustand.value.tagesbonusPunkte)
    }

    @Test
    fun `Kulanz und Tagesbonus ueberschreiben sich nicht gegenseitig`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher, schreibVerzoegerung = 50)
        advanceUntilIdle() // Zustand geladen (kein Turbine: Startzustand == Defaults, StateFlow meldet nichts Neues)

        vm.tagesbonusAendern(+1)
        vm.kulanzAendern(-1)
        advanceUntilIdle()

        assertEquals(Einstellungen(tagesbonusPunkte = 20, standardKulanzMinuten = 55), speicher.stand.value)
    }

    @Test
    fun `ein Stepper-Tipp setzt den Farbmodus nicht zurueck`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher, schreibVerzoegerung = 50)
        advanceUntilIdle() // Zustand geladen (kein Turbine: Startzustand == Defaults, StateFlow meldet nichts Neues)

        vm.themeModusAendern(ThemeModus.DUNKEL)
        vm.tagesbonusAendern(+1)
        advanceUntilIdle()

        assertEquals(ThemeModus.DUNKEL, speicher.stand.value.themeModus)
        assertEquals(20, speicher.stand.value.tagesbonusPunkte)
    }

    @Test
    fun `ein gescheitertes Schreiben zeigt wieder den gespeicherten Stand`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher, schreibFehler = true)
        advanceUntilIdle() // Zustand geladen (kein Turbine: Startzustand == Defaults, StateFlow meldet nichts Neues)

        vm.tagesbonusAendern(+1)
        advanceUntilIdle()

        assertEquals(15, vm.zustand.value.tagesbonusPunkte)
        assertEquals(15, speicher.stand.value.tagesbonusPunkte)
    }

    // Vor dem Erstladen zeigt der Zustand nur Defaults: ein Tipp wuerde diese Defaults
    // (plus Schritt) speichern und die echten gespeicherten Werte still ueberschreiben.
    @Test
    fun `Eingaben vor dem Erstladen werden ignoriert und ueberschreiben nichts`() = runTest(dispatcher) {
        val gespeichert = Einstellungen(tagesbonusPunkte = 30, standardKulanzMinuten = 45, themeModus = ThemeModus.DUNKEL)
        val speicher = FakeSpeicher(gespeichert)
        // Das Erstladen suspendiert (wie echtes Hinweis-Lesen), damit der Schreibvorgang
        // des Tipps dazwischen laufen koennte.
        val vm = viewModel(speicher, hinweiseLesen = { delay(100); emptyList() })

        vm.tagesbonusAendern(+1)
        vm.themeModusAendern(ThemeModus.HELL)
        advanceUntilIdle()

        assertEquals(gespeichert, speicher.stand.value)
        val zustand = vm.zustand.value
        assertEquals(30, zustand.tagesbonusPunkte)
        assertEquals(45, zustand.standardKulanzMinuten)
        assertEquals(ThemeModus.DUNKEL, zustand.themeModus)
        assertTrue(zustand.geladen)
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

    // Eine Datei, die lesbar ist, aber fachlich Unmoegliches enthaelt (Preis 0), zeigt keine
    // Rueckfrage -- sonst wuerde der Bestand durch etwas ersetzt, das die App nie erzeugt.
    @Test
    fun `eine fachlich ungueltige Datei zeigt keine Rueckfrage, sondern IMPORT_UNLESBAR`() = runTest(dispatcher) {
        val text = backupText(belohnungen = listOf(Belohnung(1, "Eis", preisPunkte = 0)))
        val backup = FakeBackup()
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)

        vm.importVorschauAnfordern { ByteArrayInputStream(text.toByteArray()) }.join()

        assertNull(vm.importVorschau.value)
        assertEquals(Meldung.IMPORT_UNLESBAR, vm.meldung.value)
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
    // Einstellungen zeigen -- sonst wuerde ein anschliessender Stepper-Tipp sie mit den
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

        assertEquals(99, vm.zustand.value.tagesbonusPunkte)
        assertEquals(5, vm.zustand.value.standardKulanzMinuten)
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

    @Test
    fun `ein Farbmoduswechsel stoesst das Widget an`() = runTest(dispatcher) {
        var anstoesse = 0
        val vm = viewModel(FakeSpeicher(Einstellungen(themeModus = ThemeModus.HELL)), nachFarbmodus = { anstoesse++ })
        advanceUntilIdle()

        vm.themeModusAendern(ThemeModus.DUNKEL).join()

        assertEquals(1, anstoesse)
    }

    @Test
    fun `ein Tipp auf den gespeicherten Farbmodus stoesst nicht an`() = runTest(dispatcher) {
        var anstoesse = 0
        val vm = viewModel(FakeSpeicher(Einstellungen(themeModus = ThemeModus.HELL)), nachFarbmodus = { anstoesse++ })
        advanceUntilIdle()

        vm.themeModusAendern(ThemeModus.HELL).join()

        assertEquals(0, anstoesse)
    }

    @Test
    fun `ein Stepper-Tipp stoesst das Widget nicht an`() = runTest(dispatcher) {
        var anstoesse = 0
        val vm = viewModel(FakeSpeicher(), nachFarbmodus = { anstoesse++ })
        advanceUntilIdle()

        vm.tagesbonusAendern(+5).join()

        assertEquals(0, anstoesse)
    }

    @Test
    fun `ein fehlgeschlagener Farbmodus-Schreibvorgang stoesst nicht an`() = runTest(dispatcher) {
        var anstoesse = 0
        val vm = viewModel(
            FakeSpeicher(Einstellungen(themeModus = ThemeModus.HELL)),
            nachFarbmodus = { anstoesse++ },
            schreibFehler = true,
        )
        advanceUntilIdle()

        vm.themeModusAendern(ThemeModus.DUNKEL).join()

        assertEquals(0, anstoesse)
    }

    // Task 5: Import und Stepper teilen sich eine Schreibsperre. Gemeinsames Geruest:
    // schreiben() haengt beim ersten Aufruf optional an einem Tor und protokolliert die
    // Bonus-Werte in Schreibreihenfolge; der Backup-Fake schreibt dabei 40 wie der echte
    // Import (der die Einstellungen aus der Datei uebernimmt).
    private class ImportAufbau(val speicher: FakeSpeicher = FakeSpeicher()) {
        val tor = CompletableDeferred<Unit>()
        val importTor = CompletableDeferred<Unit>()
        val protokoll = mutableListOf<Int>()
        var schreibAufrufe = 0
        var ersterSchreibvorgangHaengtAmTor = false

        val schreiben: suspend (Einstellungen) -> Unit = {
            if (schreibAufrufe++ == 0 && ersterSchreibvorgangHaengtAmTor) tor.await()
            protokoll += it.tagesbonusPunkte
            speicher.stand.value = it
        }
        val backup = FakeBackup(
            importErgebnis = ImportErgebnis.Erfolg(0),
            beimImportieren = {
                importTor.await()
                schreiben(speicher.stand.value.copy(tagesbonusPunkte = 40))
            },
        )
    }

    @Test
    fun `ein Stepper-Tipp waehrend des Imports wird ignoriert`() = runTest(dispatcher) {
        val a = ImportAufbau()
        val vm = viewModel(a.speicher, backupDienst = a.backup, schreibenFake = a.schreiben)
        advanceUntilIdle()
        vm.importVorschauAnfordern { ByteArrayInputStream(backupText().toByteArray()) }.join()

        vm.importBestaetigen()
        runCurrent()
        assertTrue(vm.zustand.value.importLaeuft)

        vm.tagesbonusAendern(+1).join()
        vm.kulanzAendern(+1).join()
        vm.themeModusAendern(ThemeModus.DUNKEL).join()
        assertEquals(15, vm.zustand.value.tagesbonusPunkte)
        assertEquals(60, vm.zustand.value.standardKulanzMinuten)
        assertEquals(ThemeModus.HELL, vm.zustand.value.themeModus)
        assertTrue(a.protokoll.isEmpty())

        a.importTor.complete(Unit)
        advanceUntilIdle()

        assertEquals(40, a.speicher.stand.value.tagesbonusPunkte)
        assertEquals(40, vm.zustand.value.tagesbonusPunkte)
        assertFalse(vm.zustand.value.importLaeuft)
    }

    @Test
    fun `ein haengender Stepper-Schreibvorgang wird vom Import nicht ueberholt und nicht ueberschrieben`() =
        runTest(dispatcher) {
            val a = ImportAufbau().apply { ersterSchreibvorgangHaengtAmTor = true }
            val vm = viewModel(a.speicher, backupDienst = a.backup, schreibenFake = a.schreiben)
            advanceUntilIdle()
            vm.importVorschauAnfordern { ByteArrayInputStream(backupText().toByteArray()) }.join()
            a.importTor.complete(Unit)

            vm.tagesbonusAendern(+1) // 15 -> 20, haengt im Schreibvorgang am Tor
            runCurrent()
            vm.importBestaetigen()
            runCurrent()
            a.tor.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf(20, 40), a.protokoll)
            assertEquals(40, a.speicher.stand.value.tagesbonusPunkte)
            assertEquals(40, vm.zustand.value.tagesbonusPunkte)
        }

    @Test
    fun `ein fehlgeschlagener Import gibt die Stepper wieder frei`() = runTest(dispatcher) {
        val backup = FakeBackup(beimImportieren = { throw IOException("Datei weg") })
        val vm = viewModel(FakeSpeicher(), backupDienst = backup)
        advanceUntilIdle()
        vm.importVorschauAnfordern { ByteArrayInputStream(backupText().toByteArray()) }.join()

        vm.importBestaetigen().join()

        assertFalse(vm.zustand.value.importLaeuft)
        assertEquals(Meldung.IMPORT_UNLESBAR, vm.meldung.value)
        vm.tagesbonusAendern(+1).join()
        assertEquals(20, vm.zustand.value.tagesbonusPunkte)
    }

    @Test
    fun `ein Lesefehler in uebernehmen laedt neu statt abzustuerzen`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        var leseAufrufe = 0
        val vm = viewModel(
            speicher,
            lesenFake = {
                if (++leseAufrufe == 2) throw IOException("Platte weg")
                speicher.stand.value
            },
        )
        advanceUntilIdle()

        vm.tagesbonusAendern(+1).join()

        // Nichts geschrieben, die Anzeige zeigt wieder den gespeicherten Stand.
        assertEquals(15, speicher.stand.value.tagesbonusPunkte)
        assertEquals(15, vm.zustand.value.tagesbonusPunkte)
    }
}
