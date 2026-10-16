package de.vanhake.dayorganizer.ui.heute

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domainuse.ErledigungsProtokoll
import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassen
import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassenRueckgaengig
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigen
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigenRueckgaengig
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import de.vanhake.dayorganizer.platform.permission.BerechtigungsHinweise
import de.vanhake.dayorganizer.platform.permission.BerechtigungsPruefer
import de.vanhake.dayorganizer.platform.permission.Hinweis
import de.vanhake.dayorganizer.platform.permission.waechterHaengt
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import de.vanhake.dayorganizer.ui.gemeinsam.ToastStapel
import de.vanhake.dayorganizer.util.versuchen
import de.vanhake.dayorganizer.widget.WidgetAktualisierung
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class HeuteViewModel(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val belohnungen: BelohnungRepository,
    private val erledigenUseCase: FaelligkeitErledigen,
    private val auslassenUseCase: FaelligkeitAuslassen,
    private val erledigenRueckgaengig: FaelligkeitErledigenRueckgaengig,
    private val auslassenRueckgaengig: FaelligkeitAuslassenRueckgaengig,
    private val anzeige: Erinnerungsanzeige,
    private val alarmNeuPlanen: suspend () -> Unit,
    private val hinweiseLesen: suspend () -> List<Hinweis>,
    private val uhr: Clock,
    private val widgetAnstossen: suspend () -> Unit,
) : ViewModel() {

    @Inject
    constructor(
        aktivitaeten: AktivitaetRepository,
        faelligkeiten: FaelligkeitRepository,
        buchungen: BuchungRepository,
        belohnungen: BelohnungRepository,
        erledigenUseCase: FaelligkeitErledigen,
        auslassenUseCase: FaelligkeitAuslassen,
        erledigenRueckgaengig: FaelligkeitErledigenRueckgaengig,
        auslassenRueckgaengig: FaelligkeitAuslassenRueckgaengig,
        anzeige: Erinnerungsanzeige,
        alarmPlaner: AlarmPlaner,
        pruefer: BerechtigungsPruefer,
        einstellungen: EinstellungenSpeicher,
        uhr: Clock,
        widgetAktualisierung: WidgetAktualisierung,
    ) : this(
        aktivitaeten, faelligkeiten, buchungen, belohnungen, erledigenUseCase, auslassenUseCase,
        erledigenRueckgaengig, auslassenRueckgaengig, anzeige,
        { alarmPlaner.neuPlanen() },
        {
            // BerechtigungsPruefer liest nur aus Context und kann DataStore nicht synchron
            // lesen, deshalb wird die Waechter-Frische hier -- suspend -- dazugemischt.
            val haengt = waechterHaengt(
                letzterErfolg = einstellungen.letzterWaechterErfolg(),
                installiertAm = pruefer.installiertAm(),
                jetzt = LocalDateTime.now(uhr),
            )
            BerechtigungsHinweise.fuer(pruefer.stand().copy(waechterHaengt = haengt))
        },
        uhr,
        { widgetAktualisierung.anstossen() },
    )

    private val hinweise = MutableStateFlow<List<Hinweis>>(emptyList())
    private val tag = MutableStateFlow(LocalDate.now(uhr))

    // Die Toasts liegen bewusst nicht in der zustand-Kette unten: combine(...) hat nur
    // typisierte Ueberladungen bis fuenf Flows (die Kette nutzt bereits alle fuenf), und ein
    // Toast soll unabhaengig von DB-Updates kommen und gehen. ToastStapel haelt die offenen
    // Rueckgaengig-Eintraege und den Info-Eintrag und gibt nur den sichtbaren als Flow heraus.
    private val stapel = ToastStapel(viewModelScope)
    val toast: StateFlow<Hinweistoast?> = stapel.sichtbar

    @OptIn(ExperimentalCoroutinesApi::class)
    val zustand: StateFlow<HeuteZustand> = combine(
        tag.flatMapLatest { faelligkeiten.beobachteTag(it) },
        aktivitaeten.beobachteAktive(),
        belohnungen.beobachteAktive(),
        buchungen.beobachteKontostand(),
        hinweise,
    ) { faelligkeiten, aktivitaeten, belohnungen, kontostand, hinweise ->
        HeuteZusammenstellung.aus(faelligkeiten, aktivitaeten, belohnungen, kontostand, LocalDateTime.now(uhr))
            .copy(hinweise = hinweise)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeuteZustand())

    /**
     * Wird bei jedem Sichtbarwerden des Screens aufgerufen, nicht nur einmal:
     * der Nutzer kommt aus den Systemeinstellungen zurueck, und dann muss der
     * Banner verschwinden. Ausserdem kann seit dem letzten Sichtbarwerden
     * Mitternacht vergangen sein, also wird auch der beobachtete Tag neu gelesen.
     */
    fun beimSichtbarwerden() {
        tag.value = LocalDate.now(uhr)
        viewModelScope.launch { hinweise.value = hinweiseLesen() }
    }

    // Property und Methode heissen bewusst unterschiedlich: gleiche Namen
    // kompilieren zwar, lesen sich aber wie ein Selbstaufruf.
    // Ohne anzeige.verstecke() bliebe die Systembenachrichtigung stehen, obwohl der
    // Nutzer die Faelligkeit gerade hier im Screen erledigt/ausgelassen hat -- AktionsEmpfaenger
    // (der Weg ueber die Benachrichtigungsaktionen) versteckt sie schon, dieser Pfad muss
    // dasselbe tun (Finding 3 der Branch-Review).
    fun erledigen(faelligkeitId: Long) = viewModelScope.launch {
        // Scheitert das Buchen (z.B. Einstellungen unlesbar), hat sich nichts geaendert:
        // Benachrichtigung, Alarm und Widget bleiben deshalb unberuehrt.
        val protokoll = versuchen { erledigenUseCase.mitProtokoll(faelligkeitId) }
            .onFailure { Log.w(TAG, "Abhaken fehlgeschlagen", it) }
            .getOrNull()
        if (protokoll == null) {
            stapel.zeigen(HeuteToastTexte.ERLEDIGEN_FEHLGESCHLAGEN)
            return@launch
        }
        // anzeige.verstecke() muss bei JEDEM Ausgang laufen, nicht nur bei Gebucht: NichtOffen
        // deckt ERLEDIGT/AUSGELASSEN/VERPASST ab (siehe FaelligkeitErledigen), und in jedem
        // dieser Faelle kann noch eine Systembenachrichtigung fuer diese Faelligkeit stehen
        // (veraltete Liste, Doppel-Tap, oder der Watchdog war schneller). alarmNeuPlanen(),
        // widgetAnstossen() und der Toast bleiben dagegen auf den echten Erfolgsfall
        // beschraenkt, weil sich an Alarm/Widget/Punktestand sonst nichts geaendert hat.
        anzeige.verstecke(faelligkeitId)
        when (protokoll) {
            is ErledigungsProtokoll.Gebucht -> {
                folgeschritt { alarmNeuPlanen() }
                widgetAnstossen()
                val gesamt = protokoll.buchungen.sumOf { it.betrag }
                val text = if (protokoll.buchungen.any { it.art == BuchungsArt.TAGESBONUS }) {
                    HeuteToastTexte.tagesbonusToast(gesamt)
                } else {
                    HeuteToastTexte.erledigtToast(gesamt, nameVon(protokoll.faelligkeitVorher.aktivitaetId))
                }
                stapel.zeigen(text) { erledigenRueckgaengig(protokoll) }
            }
            ErledigungsProtokoll.NichtOffen, ErledigungsProtokoll.Unbekannt -> Unit
        }
    }

    fun auslassen(faelligkeitId: Long) = viewModelScope.launch {
        val ergebnis = versuchen { auslassenUseCase.mitProtokoll(faelligkeitId) }
            .onFailure { Log.w(TAG, "Auslassen fehlgeschlagen", it) }
        if (ergebnis.isFailure) {
            stapel.zeigen(HeuteToastTexte.AUSLASSEN_FEHLGESCHLAGEN)
            return@launch
        }
        val vorher = ergebnis.getOrNull()
        // Siehe Kommentar in erledigen(): verstecke() muss auch laufen, wenn mitProtokoll()
        // null liefert (Faelligkeit unbekannt oder schon nicht mehr OFFEN).
        anzeige.verstecke(faelligkeitId)
        vorher ?: return@launch
        folgeschritt { alarmNeuPlanen() }
        widgetAnstossen()
        stapel.zeigen(HeuteToastTexte.ausgelassenToast(nameVon(vorher.aktivitaetId))) {
            auslassenRueckgaengig(vorher)
        }
    }

    fun rueckgaengig() = viewModelScope.launch {
        val aktion = stapel.rueckgaengigNehmen() ?: return@launch
        val geklappt = versuchen { aktion() }
            .onFailure { Log.w(TAG, "Rueckgaengig fehlgeschlagen", it) }
            .isSuccess
        if (!geklappt) {
            stapel.zeigen(HeuteToastTexte.RUECKGAENGIG_FEHLGESCHLAGEN)
            return@launch
        }
        folgeschritt { alarmNeuPlanen() }
        widgetAnstossen()
    }

    // Die Folgeschritte laufen erst nach erfolgreicher Buchung bzw. erfolgreichem
    // Rueckgaengig: ein Fehlschlag hier wird nur protokolliert und zeigt keinen
    // "fehlgeschlagen"-Toast, denn die eigentliche Aktion ist ja erfolgt. Ungefangen
    // risse er dagegen viewModelScope und damit den Toast mit Rueckgaengig mit.
    private suspend fun folgeschritt(schritt: suspend () -> Unit) {
        versuchen { schritt() }.onFailure { Log.w(TAG, "Folgeschritt fehlgeschlagen", it) }
    }

    /** Leerer Name, wenn die Aktivitaet fehlt oder nicht lesbar ist -- wie bisher bei orEmpty(). */
    private suspend fun nameVon(aktivitaetId: Long): String =
        versuchen { aktivitaeten.nachId(aktivitaetId)?.name }
            .onFailure { Log.w(TAG, "Name der Aktivitaet nicht lesbar", it) }
            .getOrNull()
            .orEmpty()

    private companion object {
        const val TAG = "HeuteViewModel"
    }
}

/** Reine Textbausteine fuer die Heute-Toasts -- ausserhalb der Klasse, damit Tests sie
 * nicht extra mocken muessen (keine Android-Ressourcen, kein Context noetig). */
private object HeuteToastTexte {
    fun erledigtToast(punkte: Int, name: String) = "+$punkte Punkte · $name"
    fun tagesbonusToast(punkte: Int) = "+$punkte Punkte · inkl. Tagesbonus"
    fun ausgelassenToast(name: String) = "$name ausgelassen"
    const val ERLEDIGEN_FEHLGESCHLAGEN = "Abhaken fehlgeschlagen"
    const val AUSLASSEN_FEHLGESCHLAGEN = "Auslassen fehlgeschlagen"
    const val RUECKGAENGIG_FEHLGESCHLAGEN = "Rückgängig fehlgeschlagen"
}
