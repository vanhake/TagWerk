package de.vanhake.dayorganizer.ui.einstellungen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.backup.Backup
import de.vanhake.dayorganizer.backup.BackupDienst
import de.vanhake.dayorganizer.backup.ImportErgebnis
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import de.vanhake.dayorganizer.platform.permission.BerechtigungsHinweise
import de.vanhake.dayorganizer.platform.permission.BerechtigungsPruefer
import de.vanhake.dayorganizer.platform.permission.Hinweis
import de.vanhake.dayorganizer.platform.permission.waechterHaengt
import de.vanhake.dayorganizer.platform.work.WaechterPlanung
import de.vanhake.dayorganizer.widget.WidgetAktualisierung
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject

data class EinstellungenZustand(
    val tagesbonusText: String = "",
    val standardKulanzText: String = "",
    val hinweise: List<Hinweis> = emptyList(),
)

/** Ergebnis von Export oder Import, vom Screen einmalig als Snackbar gezeigt. */
enum class Meldung {
    EXPORT_FERTIG,
    EXPORT_FEHLGESCHLAGEN,
    IMPORT_FERTIG,
    IMPORT_EINSTELLUNGEN_FEHLGESCHLAGEN,
    IMPORT_FALSCHES_SCHEMA,
    IMPORT_UNLESBAR,
}

@HiltViewModel
class EinstellungenViewModel(
    private val lesen: suspend () -> Einstellungen,
    private val schreiben: suspend (Einstellungen) -> Unit,
    private val hinweiseLesen: suspend () -> List<Hinweis>,
    private val backupDienst: Backup,
    private val nachImport: suspend () -> Unit,
    private val ioDispatcher: CoroutineDispatcher,
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
    )

    private val _zustand = MutableStateFlow(EinstellungenZustand())
    val zustand: StateFlow<EinstellungenZustand> = _zustand.asStateFlow()

    private val _meldung = MutableStateFlow<Meldung?>(null)
    val meldung: StateFlow<Meldung?> = _meldung.asStateFlow()

    init {
        viewModelScope.launch { neuLaden() }
    }

    /** Vom Screen aufgerufen, nachdem die Snackbar gezeigt wurde -- sonst erschiene
     * dieselbe Meldung bei jeder Rekomposition erneut. */
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
        _meldung.value = runCatching {
            val strom = oeffnen() ?: error("Ausgabestrom konnte nicht geoeffnet werden")
            strom.use { backupDienst.exportieren(it) }
        }.fold(
            onSuccess = { Meldung.EXPORT_FERTIG },
            onFailure = { Meldung.EXPORT_FEHLGESCHLAGEN },
        )
    }

    /** Siehe [exportieren] -- dasselbe Muster fuer den Eingabestrom. */
    fun importieren(oeffnen: () -> InputStream?) = viewModelScope.launch(ioDispatcher) {
        // Der Dienst liefert jeden bekannten Fehlschlag als ImportErgebnis. Das
        // runCatching faengt, was trotzdem durchkommt -- ein Strom, der sich nicht
        // oeffnen laesst, oder ein Lesefehler des Dateidialogs --, damit ein Import
        // niemals die App beendet.
        val ergebnis = runCatching {
            val strom = oeffnen() ?: error("Eingabestrom konnte nicht geoeffnet werden")
            strom.use { backupDienst.importieren(it) }
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

    fun tagesbonusAendern(text: String) {
        _zustand.value = _zustand.value.copy(tagesbonusText = text)
    }

    fun kulanzAendern(text: String) {
        _zustand.value = _zustand.value.copy(standardKulanzText = text)
    }

    /**
     * Wird bei jedem Sichtbarwerden des Screens aufgerufen, nicht nur einmal:
     * der Nutzer kommt aus den Systemeinstellungen zurueck, und dann muss der
     * Banner verschwinden. Gleiches Muster wie HeuteViewModel.beimSichtbarwerden.
     */
    fun beimSichtbarwerden() {
        viewModelScope.launch { _zustand.value = _zustand.value.copy(hinweise = hinweiseLesen()) }
    }

    /**
     * Ungueltige Eingaben werden verworfen, nicht gespeichert. Ein leeres Feld
     * darf den Tagesbonus nicht auf null setzen.
     */
    fun speichern(): Job = viewModelScope.launch {
        val bisher = lesen()
        val neu = Einstellungen(
            tagesbonusPunkte = _zustand.value.tagesbonusText.toIntOrNull()
                ?.takeIf { it >= 0 } ?: bisher.tagesbonusPunkte,
            standardKulanzMinuten = _zustand.value.standardKulanzText.toIntOrNull()
                ?.takeIf { it >= 0 } ?: bisher.standardKulanzMinuten,
        )
        // DataStore.edit wirft bei Platten- oder Korruptionsfehlern eine IOException --
        // unbehandelt reisst sie die App mit. Gleiches Muster wie
        // AktivitaetBearbeitenViewModel.speichern(): protokollieren statt abstuerzen.
        runCatching {
            schreiben(neu)
        }.onFailure { ausnahme ->
            Log.w(TAG, "Speichern der Einstellungen fehlgeschlagen", ausnahme)
        }
        neuLaden()
    }

    private suspend fun neuLaden() {
        val aktuell = lesen()
        _zustand.value = EinstellungenZustand(
            tagesbonusText = aktuell.tagesbonusPunkte.toString(),
            standardKulanzText = aktuell.standardKulanzMinuten.toString(),
            hinweise = hinweiseLesen(),
        )
    }

    private companion object {
        const val TAG = "EinstellungenVM"
    }
}
