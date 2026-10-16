package de.vanhake.dayorganizer.ui.einstellungen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.backup.Backup
import de.vanhake.dayorganizer.backup.BackupDienst
import de.vanhake.dayorganizer.backup.BackupUmwandlung
import de.vanhake.dayorganizer.backup.ImportErgebnis
import de.vanhake.dayorganizer.backup.UnbekanntesSchema
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.data.ThemeModus
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import de.vanhake.dayorganizer.platform.permission.BerechtigungsHinweise
import de.vanhake.dayorganizer.platform.permission.BerechtigungsPruefer
import de.vanhake.dayorganizer.platform.permission.Hinweis
import de.vanhake.dayorganizer.platform.permission.waechterHaengt
import de.vanhake.dayorganizer.platform.work.WaechterPlanung
import de.vanhake.dayorganizer.util.versuchen
import de.vanhake.dayorganizer.widget.WidgetAktualisierung
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class EinstellungenZustand(
    val tagesbonusPunkte: Int = 15,
    val standardKulanzMinuten: Int = 60,
    val themeModus: ThemeModus = ThemeModus.HELL,
    val hinweise: List<Hinweis> = emptyList(),
    /** Erst nach dem ersten Laden zeigt der Zustand gespeicherte Werte statt Defaults. */
    val geladen: Boolean = false,
)

/**
 * Ergebnis von Export oder Import, vom Screen einmalig als Toast gezeigt. Der Screen
 * startet beim Eintreffen einen Timer (3 800 ms) und ruft danach [EinstellungenViewModel.meldungGezeigt].
 */
enum class Meldung {
    EXPORT_FERTIG,
    EXPORT_FEHLGESCHLAGEN,
    IMPORT_FERTIG,
    IMPORT_EINSTELLUNGEN_FEHLGESCHLAGEN,
    IMPORT_FALSCHES_SCHEMA,
    IMPORT_UNLESBAR,
}

/**
 * Vorschau eines gelesenen, gueltigen Backups (Review-Fund W2): zeigt der Nutzer vor dem
 * Import, von wann die Datei ist und wie viele Aktivitaeten sie enthaelt, bevor der
 * aktuelle Bestand unwiderruflich ersetzt wird.
 */
data class ImportVorschau(
    val erstelltAm: LocalDateTime,
    val anzahlAktivitaeten: Int,
)

@HiltViewModel
class EinstellungenViewModel(
    private val lesen: suspend () -> Einstellungen,
    private val schreiben: suspend (Einstellungen) -> Unit,
    private val hinweiseLesen: suspend () -> List<Hinweis>,
    private val backupDienst: Backup,
    private val nachImport: suspend () -> Unit,
    private val ioDispatcher: CoroutineDispatcher,
    private val heute: () -> LocalDate,
) : ViewModel() {

    @Inject
    constructor(
        speicher: EinstellungenSpeicher,
        pruefer: BerechtigungsPruefer,
        uhr: Clock,
        backupDienst: BackupDienst,
        waechterPlanung: WaechterPlanung,
        erinnerungsanzeige: Erinnerungsanzeige,
        widgetAktualisierung: WidgetAktualisierung,
    ) : this(
        lesen = { speicher.lesen() },
        schreiben = { speicher.schreiben(it) },
        hinweiseLesen = {
            // BerechtigungsPruefer liest nur aus Context und kann DataStore nicht synchron
            // lesen, deshalb wird die Waechter-Frische hier -- suspend -- dazugemischt.
            // Gleiches Muster wie HeuteViewModel.
            val haengt = waechterHaengt(
                letzterErfolg = speicher.letzterWaechterErfolg(),
                installiertAm = pruefer.installiertAm(),
                jetzt = LocalDateTime.now(uhr),
            )
            BerechtigungsHinweise.fuer(pruefer.stand().copy(waechterHaengt = haengt))
        },
        backupDienst = backupDienst,
        nachImport = {
            waechterPlanung.sofortAusfuehren()
            // Alte Benachrichtigungen tragen faelligkeitIds, die nach dem Import zu
            // ganz anderen Faelligkeiten gehoeren koennten (Review-Fund W3).
            erinnerungsanzeige.alleVerstecken()
            // Ein Widget-Eintrag kann nach dem Import auf eine ganz andere Faelligkeit
            // zeigen (die Datei bringt ihre eigenen ids mit) -- ohne diesen Anstoss
            // zeichnet erst der naechste Waechter-Lauf neu (Review-Fund W1).
            widgetAktualisierung.anstossen()
        },
        ioDispatcher = Dispatchers.IO,
        // Kein now(): dasselbe injizierte Clock, das auch fuer die Hinweise benutzt wird
        // (Review-Fund W2 -- der vorgeschlagene Export-Dateiname traegt das heutige Datum).
        heute = { LocalDate.now(uhr) },
    )

    private val _zustand = MutableStateFlow(EinstellungenZustand())
    val zustand: StateFlow<EinstellungenZustand> = _zustand.asStateFlow()

    // Serialisiert die Schreibvorgaenge der Stepper und des Farbmodus (siehe uebernehmen()).
    private val schreibSperre = Mutex()

    private val _meldung = MutableStateFlow<Meldung?>(null)
    val meldung: StateFlow<Meldung?> = _meldung.asStateFlow()

    private val _importVorschau = MutableStateFlow<ImportVorschau?>(null)
    val importVorschau: StateFlow<ImportVorschau?> = _importVorschau.asStateFlow()

    /**
     * Der in [importVorschauAnfordern] gelesene Dateitext, bis zur Bestaetigung oder zum
     * Abbruch (Review-Fund W2). Kein StateFlow: nur [importBestaetigen] liest ihn, der
     * Screen braucht ihn nicht -- die Datei muss dafuer nicht ein zweites Mal geoeffnet
     * werden.
     */
    private var gelesenerImportText: String? = null

    /** Heutiges Datum fuer den vorgeschlagenen Export-Dateinamen (Review-Fund W2). */
    val exportDatum: String get() = heute().toString()

    init {
        viewModelScope.launch { neuLaden() }
    }

    /** Vom Screen aufgerufen, wenn der Toast-Timer (MELDUNG_DAUER_MS) abgelaufen ist --
     * sonst erschiene dieselbe Meldung bei jeder Rekomposition erneut. */
    fun meldungGezeigt() {
        _meldung.value = null
    }

    /**
     * [oeffnen] liefert den Ausgabestrom statt ihn entgegenzunehmen: Oeffnen, Schreiben
     * und Schliessen muessen in derselben Koroutine passieren. Der Screen reicht den
     * Strom vorher schon fertig geoeffnet und per `use` wieder geschlossen herein --
     * `use` schliesst dabei sofort, bevor die hier nur *gestartete* Koroutine ueberhaupt
     * zu schreiben beginnt, und jeder Export landet als leere Datei (Review-Fund K1,
     * per Test bewiesen). Das Oeffnen auf Dispatchers.IO nimmt zugleich Datei-I/O und
     * JSON vom Hauptthread (Review-Fund W4).
     */
    fun exportieren(oeffnen: () -> OutputStream?) = viewModelScope.launch(ioDispatcher) {
        // Ein gescheiterter Export -- volle Platte, entzogene Dateiberechtigung, ein
        // Strom, der sich gar nicht erst oeffnen laesst -- darf weder die App beenden
        // noch still als Erfolg durchgehen. Ein Backup, das man fuer geschrieben haelt
        // und das es nicht gibt, ist das Schlimmste, was diese Funktion anrichten kann.
        _meldung.value = versuchen {
            val strom = oeffnen() ?: error("Ausgabestrom konnte nicht geoeffnet werden")
            strom.use { backupDienst.exportieren(it) }
        }.fold(
            onSuccess = { Meldung.EXPORT_FERTIG },
            onFailure = { Meldung.EXPORT_FEHLGESCHLAGEN },
        )
    }

    /**
     * Erster Schritt des zweistufigen Imports (Review-Fund W2): liest die gewaehlte Datei
     * und prueft sie -- ueber [BackupUmwandlung.ausText], dieselbe Schemapruefung wie
     * [BackupDienst.importieren], keine zweite Leselogik. Ist die Datei gueltig, zeigt
     * [importVorschau] von wann sie ist und wie viele Aktivitaeten sie enthaelt; ist sie
     * ungueltig, erscheint direkt die bestehende Fehlermeldung, ohne Rueckfrage. Der
     * gelesene Text bleibt bis [importBestaetigen] oder [importVorschauAbbrechen] hier
     * gehalten, damit die Datei nicht zweimal geoeffnet werden muss.
     */
    fun importVorschauAnfordern(oeffnen: () -> InputStream?) = viewModelScope.launch(ioDispatcher) {
        val ergebnis = versuchen {
            val strom = oeffnen() ?: error("Eingabestrom konnte nicht geoeffnet werden")
            val text = strom.use { it.bufferedReader().readText() }
            text to BackupUmwandlung.ausText(text)
        }
        ergebnis.fold(
            onSuccess = { (text, inhalt) ->
                gelesenerImportText = text
                _importVorschau.value = ImportVorschau(
                    erstelltAm = inhalt.erstelltAm,
                    anzahlAktivitaeten = inhalt.aktivitaeten.size,
                )
            },
            onFailure = { fehler ->
                _meldung.value = if (fehler is UnbekanntesSchema) {
                    Meldung.IMPORT_FALSCHES_SCHEMA
                } else {
                    Meldung.IMPORT_UNLESBAR
                }
            },
        )
    }

    /** "Abbrechen" im Vorschau-Dialog (Review-Fund W2): laesst den Bestand unveraendert. */
    fun importVorschauAbbrechen() {
        gelesenerImportText = null
        _importVorschau.value = null
    }

    /**
     * Zweiter Schritt (Review-Fund W2): importiert mit dem in [importVorschauAnfordern]
     * gelesenen Text, ohne die Datei erneut zu oeffnen.
     *
     * Alles ab hier laeuft unter NonCancellable (Review-Fund W1): verlaesst der Nutzer den
     * Einstellungen-Tab, waehrend der Import laeuft, nimmt popUpTo den Eintrag vom
     * Navigations-Stapel und viewModelScope wird abgebrochen. Trifft dieser Abbruch NACH
     * dem Datenbank-Commit in backupDienst.importieren() ein, muessen neuLaden() und
     * nachImport() trotzdem zu Ende laufen -- sonst bleibt die Tabelle erinnerung leer und
     * kein Waechter wird angestossen, bis zum naechsten planmaessigen Lauf.
     */
    fun importBestaetigen() = viewModelScope.launch(ioDispatcher) {
        val text = gelesenerImportText ?: return@launch
        gelesenerImportText = null
        _importVorschau.value = null

        withContext(NonCancellable) {
            val ergebnis = versuchen {
                backupDienst.importieren(ByteArrayInputStream(text.toByteArray()))
            }
            _meldung.value = ergebnis.fold(
                onSuccess = { erfolg ->
                    when (erfolg) {
                        is ImportErgebnis.Erfolg -> if (erfolg.einstellungenUebernommen) {
                            Meldung.IMPORT_FERTIG
                        } else {
                            Meldung.IMPORT_EINSTELLUNGEN_FEHLGESCHLAGEN
                        }
                        is ImportErgebnis.FalschesSchema -> Meldung.IMPORT_FALSCHES_SCHEMA
                        is ImportErgebnis.Unlesbar -> Meldung.IMPORT_UNLESBAR
                    }
                },
                onFailure = { Meldung.IMPORT_UNLESBAR },
            )
            // Nur ein Import, der den Bestand tatsaechlich ersetzt hat, darf den Zustand
            // neu laden und Waechter/Benachrichtigungen anstossen -- ein abgelehnter Import
            // (falsches Schema, unlesbar) hat nichts veraendert (Review-Fund W3/W6).
            if (ergebnis.getOrNull() is ImportErgebnis.Erfolg) {
                neuLaden()
                nachImport()
            }
        }
    }

    /** [schritt] ist +1 oder -1 und wird mit [STEPPER_SCHRITT] multipliziert. */
    fun tagesbonusAendern(schritt: Int): Job {
        val z = _zustand.value
        if (!z.geladen) return ignoriert()
        _zustand.value = z.copy(
            tagesbonusPunkte = (z.tagesbonusPunkte + schritt * STEPPER_SCHRITT).coerceAtLeast(STEPPER_MIN),
        )
        return uebernehmen()
    }

    fun kulanzAendern(schritt: Int): Job {
        val z = _zustand.value
        if (!z.geladen) return ignoriert()
        _zustand.value = z.copy(
            standardKulanzMinuten = (z.standardKulanzMinuten + schritt * STEPPER_SCHRITT)
                .coerceAtLeast(STEPPER_MIN),
        )
        return uebernehmen()
    }

    /** Wirkt wie die Stepper sofort: ein Farbmodus ist ein Umschalter, kein Formularfeld. */
    fun themeModusAendern(neu: ThemeModus): Job {
        if (!_zustand.value.geladen) return ignoriert()
        _zustand.value = _zustand.value.copy(themeModus = neu)
        return uebernehmen()
    }

    /**
     * Wird bei jedem Sichtbarwerden des Screens aufgerufen, nicht nur einmal:
     * der Nutzer kommt aus den Systemeinstellungen zurueck, und dann muss der
     * Banner verschwinden. Gleiches Muster wie HeuteViewModel.beimSichtbarwerden.
     */
    fun beimSichtbarwerden() {
        viewModelScope.launch { _zustand.value = _zustand.value.copy(hinweise = hinweiseLesen()) }
    }

    /** Vor dem Erstladen zeigt der Zustand nur Defaults; ein Tipp wuerde sie speichern und
     * die echten Werte still ueberschreiben. Deshalb zaehlt er nicht (kein Schreibvorgang). */
    private fun ignoriert(): Job = Job().apply { complete() }

    /**
     * Schreibt den *aktuellen* Zustand, nicht den zum Tipp-Zeitpunkt: Die Anzeige wurde
     * schon synchron geaendert, und unter der Sperre liest jeder Lauf den neuesten Stand.
     * So traegt jeder Schreibvorgang alle bisherigen Tipps, und ein spaeterer Lauf
     * ueberholt nie einen frueheren -- fuenf schnelle Tipps ergeben +25, nicht +5.
     * Kein neuLaden() nach Erfolg, sonst spraenge die Anzeige bei schnellen Tipps zurueck.
     */
    private fun uebernehmen(): Job = viewModelScope.launch {
        // NonCancellable (wie bei importBestaetigen, Review-Fund W1): ein Tab-Wechsel direkt
        // nach dem Tipp bricht viewModelScope ab; der Tipp ist aber schon angezeigt und darf
        // nicht verloren gehen.
        val fehlgeschlagen = withContext(NonCancellable) {
            schreibSperre.withLock {
                val z = _zustand.value
                // DataStore.edit wirft bei Platten- oder Korruptionsfehlern eine IOException --
                // unbehandelt reisst sie die App mit. Gleiches Muster wie
                // AktivitaetBearbeitenViewModel.speichern(): protokollieren statt abstuerzen.
                versuchen {
                    schreiben(
                        lesen().copy(
                            tagesbonusPunkte = z.tagesbonusPunkte,
                            standardKulanzMinuten = z.standardKulanzMinuten,
                            themeModus = z.themeModus,
                        ),
                    )
                }.onFailure { ausnahme ->
                    Log.w(TAG, "Speichern der Einstellungen fehlgeschlagen", ausnahme)
                }.isFailure
            }
        }
        // Nur im Fehlerfall: die Anzeige zeigt wieder den tatsaechlich gespeicherten Stand.
        if (fehlgeschlagen) neuLaden()
    }

    private suspend fun neuLaden() {
        val aktuell = lesen()
        _zustand.value = EinstellungenZustand(
            tagesbonusPunkte = aktuell.tagesbonusPunkte,
            standardKulanzMinuten = aktuell.standardKulanzMinuten,
            themeModus = aktuell.themeModus,
            hinweise = hinweiseLesen(),
            geladen = true,
        )
    }

    companion object {
        const val STEPPER_SCHRITT = 5
        const val STEPPER_MIN = 0
        private const val TAG = "EinstellungenVM"
    }
}
