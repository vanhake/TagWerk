# DayOrganizer Redesign 2: Aktivitäten-Liste + Aktivität bearbeiten Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Den Aktivitäten-Tab und den Bearbeiten-Bildschirm auf den Stand des Redesign-Prototyps bringen. Dazu gehören Listenzeilen mit Symbolkachel, Plantext, Punkte- und Serien-Plakette, ein erweiterter FAB und Toasts. Der Bearbeiten-Bildschirm bekommt ein Symbolraster, Stepper, Zeitplan-Typkarten, Wochentag-Kacheln, Uhrzeit-Chips mit Zeitwähler, ein editierbares Intervall-Zeitfenster und eine Plan-Zusammenfassung.

**Architecture:** Der Plantext („Täglich · 08:00, 20:00“) ist eine reine Funktion `PlanText.aus(Zeitplan)`. Liste und Bearbeiten-Bildschirm nutzen sie gemeinsam. `AktivitaetEntwurf` verliert seine Text-Zahlenfelder und das Komma-Uhrzeitfeld zugunsten typisierter Felder plus reiner Bearbeitungsoperationen (`punkteAendern`, `zeitHinzufuegen` …). Die Clamp-Regeln der Stepper sind damit ohne Compose testbar. Die Listenzeilen entstehen in einer reinen `AktivitaetenZusammenstellung` (Muster `HeuteZusammenstellung`), die auch die aktuelle Serie über den vorhandenen `SerienRechner` bestimmt. Der Speichern-Toast erreicht die Liste über den `savedStateHandle` des vorherigen Back-Stack-Eintrags (Standard-Navigation-Compose-Ergebnismuster). Wiederverwendbare Bausteine (`Stepper`, `StepperZeile`, `UhrzeitDialog`) liegen in `ui/gemeinsam`, weil Plan 6 sie für Bonus/Kulanz wieder braucht.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 aus BOM `2024.12.01`, also material3 1.3.x: `TimePicker`/`rememberTimePickerState` sind `@ExperimentalMaterial3Api`, eine fertige `TimePickerDialog`-Composable gibt es in dieser Version noch nicht, darum ein eigener `AlertDialog`-Rahmen), Hilt, Navigation Compose 2.8.5, kotlinx.coroutines-test. Es kommen keine neuen Abhängigkeiten hinzu.

**Design-Quelle:** `docs/design/prototyp/DayOrganizer App.dc.html`. Relevante Stellen: Markup-Block `<sc-if value="{{ sAkt }}">` (Liste), `<sc-if value="{{ sEdit }}">` (Bearbeiten), FAB `showFab`, Konstanten `ICONS`/`TYPES`/`DAYS`, Funktionen `tageText`/`planText`, `renderVals()` → `actRows` und `draftVals`.

## Global Constraints

- Minimale API-Version bleibt 33, Ziel-API 35 (unverändert, siehe `app/build.gradle.kts`).
- Keine neue Abhängigkeit, keine neue Berechtigung, kein Netzwerkzugriff.
- Die deutsche Namenskonvention gilt weiter für Klassen, Funktionen, Ressourcen-Keys und Testnamen (z. B. `AktivitaetZeile`, `zeitHinzufuegen`, keine englischen Bezeichner in neuem Code). Umlaute stehen nur in Strings und Kommentaren in Klartext-Dokumenten. In Kotlin-Kommentaren werden sie ausgeschrieben (`ae`, `oe`, `ue`), wie im bestehenden Code.
- **Kein Datenmodell-, Room- oder Backup-Schema-Änderung** in diesem Plan. `Aktivitaet`, `AktivitaetEntity` und `BackupDatei` bleiben unangetastet (Symbol und Farbmodus kamen schon mit Plan 4).
- Farben ausschließlich über `DayOrganizerTheme.farben`, Eckenradien über `DayOrganizerFormen` (Plan 4). Keine hart codierten Farbliterale in Screens.
- Nutzersichtbare Texte in Compose stehen in `app/src/main/res/values/strings.xml`. Ausnahme wie in Plan 4 (`HeuteToastTexte`): von ViewModels oder reinen Funktionen erzeugte Texte (Toasts, Plantext) sind Kotlin-Literale, damit Tests ohne Android-Ressourcen auskommen.
- Archivieren in der Liste bekommt **keinen** Rückgängig-Knopf (bewusste Entscheidung bei der Planabstimmung). Der Toast „<Name> archiviert“ erscheint ohne Aktion.
- Nicht im Bildschirm angebotene Felder des Entwurfs (Notiz, Farbe, Kulanz, Pünktlichkeitsbonus, Serienschwellen, Nachfassen, Wochenziel-Anstöße) werden beim Speichern unverändert durchgereicht. Das Rundlauf-Verhalten von `AktivitaetEntwurf.aus(...).zuAktivitaet(...)` muss erhalten bleiben.
- Es gibt **keine Compose-UI-Tests** (Projektentscheidung, siehe `docs/HANDOFF.md`). Logik gehört in reine Funktionen und ViewModels mit JVM-Tests. Ob ein Screen baut, belegt `:app:assembleDebug`.
- Nach jedem Task muss `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` grün sein, bevor committet wird.
- Commit-Nachrichten enden mit `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

---

## Datei-Übersicht

| Datei | Zweck |
|---|---|
| `app/.../ui/aktivitaeten/PlanText.kt` (neu) | `Zeitplan` → deutscher Kurztext, Wochentagkürzel, `HH:mm`-Format |
| `app/.../ui/aktivitaeten/AktivitaetEntwurf.kt` | typisierte Felder, Bearbeitungsoperationen, `zeitplan()`, `SpeicherErgebnis` |
| `app/.../ui/aktivitaeten/AktivitaetBearbeitenViewModel.kt` | `speichern(): SpeicherErgebnis?`, Fehler live neu bewerten |
| `app/.../ui/aktivitaeten/AktivitaetBearbeitenScreen.kt` | vollständiges Redesign |
| `app/.../ui/aktivitaeten/AktivitaetenZusammenstellung.kt` (neu) | `AktivitaetZeile` + reine Zeilenbildung inkl. Serie |
| `app/.../ui/aktivitaeten/AktivitaetenViewModel.kt` | `zeilen`, Toast, `gespeichertMelden` |
| `app/.../ui/aktivitaeten/AktivitaetenScreen.kt` | vollständiges Redesign |
| `app/.../ui/gemeinsam/Stepper.kt` (neu) | `Stepper`, `StepperZeile`, `StepperZeilenInhalt` |
| `app/.../ui/gemeinsam/UhrzeitDialog.kt` (neu) | Zeitwähler im `AlertDialog` |
| `app/.../ui/theme/Formen.kt` | Radien `feld` (18), `listenKarte` (22), `chip` (12) |
| `app/.../ui/Navigation.kt` | Speichern-Ergebnis über `savedStateHandle`, Zurück-Pfeil |
| `app/src/main/res/values/strings.xml` | neue/angepasste Strings, Plurals |
| `app/src/test/.../ui/aktivitaeten/PlanTextTest.kt` (neu) | |
| `app/src/test/.../ui/aktivitaeten/AktivitaetEntwurfTest.kt` | auf typisierte Felder umgestellt + Operationen |
| `app/src/test/.../ui/aktivitaeten/AktivitaetBearbeitenViewModelTest.kt` | Speichern-Ergebnis, Live-Fehler |
| `app/src/test/.../ui/aktivitaeten/AktivitaetenZusammenstellungTest.kt` (neu) | |
| `app/src/test/.../ui/aktivitaeten/AktivitaetenViewModelTest.kt` (neu) | Toasts |

`app/...` steht für `app/src/main/kotlin/de/vanhake/dayorganizer`, `app/src/test/...` für `app/src/test/kotlin/de/vanhake/dayorganizer`.

---

### Task 1: Plantext als reine Funktion

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/PlanText.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/PlanTextTest.kt`

**Interfaces:**
- Consumes: `de.vanhake.dayorganizer.domain.model.Zeitplan` (sealed: `FesteZeiten(zeiten, wochentage)`, `Intervall(alleNStunden, von, bis, wochentage)`, `Tagesziel(anzahl, wochentage, anstossZeiten)`, `Wochenziel(anzahl, anstoss)`).
- Produces: `object PlanText { val WOCHENTAG_KUERZEL: List<String>; fun kuerzel(tag: DayOfWeek): String; fun uhrzeit(zeit: LocalTime): String; fun tage(wochentage: Set<DayOfWeek>): String; fun aus(zeitplan: Zeitplan): String }`

Der Text bildet `tageText`/`planText` des Prototyps 1:1 nach:

| Zeitplan | Text |
|---|---|
| alle 7 Tage | `Täglich` |
| genau Mo–Fr | `Mo–Fr` (Halbgeviertstrich U+2013) |
| keine Tage | `Keine Tage` |
| sonst | Kürzel in Wochenreihenfolge, `, `-getrennt |
| FesteZeiten | `<tage> · <HH:mm, HH:mm>` bzw. `<tage> · keine Uhrzeit` |
| Intervall | `<tage> · alle <n> Std. von <HH:mm> bis <HH:mm>` |
| Tagesziel | `<n>× täglich`, plus ` · <tage>` wenn nicht alle Tage, plus ` · Erinnerung <HH:mm, …>` wenn Anstoßzeiten da sind |
| Wochenziel | `<n>× pro Woche` |

Trenner ist ` · ` (Mittelpunkt U+00B7), das Malzeichen `×` ist U+00D7.

- [ ] **Step 1: Failing tests schreiben**

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.DayOfWeek.FRIDAY
import java.time.DayOfWeek.MONDAY
import java.time.DayOfWeek.SATURDAY
import java.time.DayOfWeek.SUNDAY
import java.time.DayOfWeek.THURSDAY
import java.time.DayOfWeek.TUESDAY
import java.time.DayOfWeek.WEDNESDAY
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanTextTest {

    private val alle = DayOfWeek.entries.toSet()
    private val werktage = setOf(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY)

    @Test
    fun `feste Zeiten an allen Tagen`() {
        val plan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), alle)

        assertEquals("Täglich · 08:00, 20:00", PlanText.aus(plan))
    }

    @Test
    fun `feste Zeiten an Werktagen`() {
        val plan = Zeitplan.FesteZeiten(listOf(LocalTime.of(12, 30)), werktage)

        assertEquals("Mo–Fr · 12:30", PlanText.aus(plan))
    }

    @Test
    fun `einzelne Tage erscheinen in Wochenreihenfolge, egal wie das Set sortiert ist`() {
        val plan = Zeitplan.FesteZeiten(emptyList(), linkedSetOf(SATURDAY, MONDAY, WEDNESDAY))

        assertEquals("Mo, Mi, Sa · keine Uhrzeit", PlanText.aus(plan))
    }

    @Test
    fun `ein Intervall nennt Abstand und Zeitfenster`() {
        val plan = Zeitplan.Intervall(3, LocalTime.of(8, 0), LocalTime.of(22, 0), alle)

        assertEquals("Täglich · alle 3 Std. von 08:00 bis 22:00", PlanText.aus(plan))
    }

    @Test
    fun `ein Intervall ohne Tage sagt das`() {
        val plan = Zeitplan.Intervall(2, LocalTime.of(9, 0), LocalTime.of(17, 0), emptySet())

        assertEquals("Keine Tage · alle 2 Std. von 09:00 bis 17:00", PlanText.aus(plan))
    }

    @Test
    fun `ein taegliches Tagesziel ohne Erinnerung ist nur die Anzahl`() {
        assertEquals("2× täglich", PlanText.aus(Zeitplan.Tagesziel(2, alle)))
    }

    @Test
    fun `ein Tagesziel an einzelnen Tagen mit Erinnerung nennt beides`() {
        val plan = Zeitplan.Tagesziel(1, setOf(SATURDAY, SUNDAY), listOf(LocalTime.of(19, 0)))

        assertEquals("1× täglich · Sa, So · Erinnerung 19:00", PlanText.aus(plan))
    }

    @Test
    fun `ein Wochenziel ist nur die Anzahl pro Woche`() {
        assertEquals("3× pro Woche", PlanText.aus(Zeitplan.Wochenziel(3)))
    }

    @Test
    fun `Sekunden werden in der Uhrzeit nicht angezeigt`() {
        assertEquals("08:00", PlanText.uhrzeit(LocalTime.of(8, 0, 30)))
    }

    @Test
    fun `die Kuerzel folgen der Wochenreihenfolge ab Montag`() {
        assertEquals(listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"), DayOfWeek.entries.map(PlanText::kuerzel))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.ui.aktivitaeten.PlanTextTest'`
Expected: FAIL, Kompilierfehler `Unresolved reference: PlanText`.

- [ ] **Step 3: Implementierung**

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Kurztext eines Zeitplans, 1:1 nach tageText()/planText() des Redesign-Prototyps
 * (docs/design/prototyp/DayOrganizer App.dc.html). Kotlin-Literale statt String-
 * Ressourcen, damit Liste, Bearbeiten-Zusammenfassung und Tests denselben Text ohne
 * Context bekommen -- gleiches Muster wie HeuteToastTexte.
 */
object PlanText {

    /** Index = DayOfWeek.ordinal (Montag = 0). */
    val WOCHENTAG_KUERZEL: List<String> = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    private val WERKTAGE: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    private val UHRZEIT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun kuerzel(tag: DayOfWeek): String = WOCHENTAG_KUERZEL[tag.ordinal]

    fun uhrzeit(zeit: LocalTime): String = zeit.format(UHRZEIT_FORMAT)

    fun tage(wochentage: Set<DayOfWeek>): String = when {
        wochentage.size == DayOfWeek.entries.size -> "Täglich"
        wochentage == WERKTAGE -> "Mo–Fr"
        wochentage.isEmpty() -> "Keine Tage"
        else -> wochentage.sorted().joinToString(", ") { kuerzel(it) }
    }

    fun aus(zeitplan: Zeitplan): String = when (zeitplan) {
        is Zeitplan.FesteZeiten -> {
            val zeiten = if (zeitplan.zeiten.isEmpty()) "keine Uhrzeit" else uhrzeiten(zeitplan.zeiten)
            "${tage(zeitplan.wochentage)} · $zeiten"
        }
        is Zeitplan.Intervall ->
            "${tage(zeitplan.wochentage)} · alle ${zeitplan.alleNStunden} Std. " +
                "von ${uhrzeit(zeitplan.von)} bis ${uhrzeit(zeitplan.bis)}"
        is Zeitplan.Tagesziel -> buildString {
            append("${zeitplan.anzahl}× täglich")
            if (zeitplan.wochentage.size != DayOfWeek.entries.size) append(" · ${tage(zeitplan.wochentage)}")
            if (zeitplan.anstossZeiten.isNotEmpty()) append(" · Erinnerung ${uhrzeiten(zeitplan.anstossZeiten)}")
        }
        is Zeitplan.Wochenziel -> "${zeitplan.anzahl}× pro Woche"
    }

    private fun uhrzeiten(zeiten: List<LocalTime>): String = zeiten.joinToString(", ") { uhrzeit(it) }
}
```

- [ ] **Step 4: Test laufen lassen und Erfolg sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.ui.aktivitaeten.PlanTextTest'`
Expected: PASS (10 Tests).

- [ ] **Step 5: Gesamtlauf**

Run: `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/PlanText.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/PlanTextTest.kt
git commit -m "feat(aktivitaeten): Plantext als reine Funktion nach Prototyp

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Bearbeiten — typisierter Entwurf, ViewModel und Bildschirm im neuen Design

Dieser Task ist bewusst nicht in „Entwurf“ und „Screen“ getrennt: Sobald die Textfelder des Entwurfs wegfallen, kompiliert der alte Bildschirm nicht mehr, und ein Zwischenstand bräuchte Wegwerf-Code. Die Logik (Steps 1–8) ist vollständig getestet, der Bildschirm (Steps 9–13) wird über `assembleDebug` belegt.

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetEntwurf.kt` (vollständig ersetzen)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetBearbeitenViewModel.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetBearbeitenScreen.kt` (vollständig ersetzen)
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/Stepper.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/UhrzeitDialog.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Formen.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt` (nur der `AKTIVITAET_BEARBEITEN`-Eintrag)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetEntwurfTest.kt` (vollständig ersetzen)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetBearbeitenViewModelTest.kt` (Tests ergänzen)

**Interfaces:**
- Consumes: `PlanText.aus(Zeitplan)`, `PlanText.kuerzel(DayOfWeek)`, `PlanText.uhrzeit(LocalTime)` (Task 1); `AKTIVITAET_SYMBOLE`, `aktivitaetSymbol(String)` (Plan 4, `ui/theme/AktivitaetSymbol.kt`); `DayOrganizerTheme.farben`, `DayOrganizerFormen` (Plan 4).
- Produces:
  - `enum class SpeicherErgebnis { ANGELEGT, GEAENDERT }` (in `AktivitaetEntwurf.kt`)
  - `AktivitaetEntwurf` mit `basisPunkte: Int`, `zeiten: List<LocalTime>`, `intervallStunden: Int`, `zielAnzahl: Int` (die Felder `basisPunkteText`, `zeitenText`, `intervallStundenText`, `zielAnzahlText` entfallen) sowie `punkteAendern(schritt: Int)`, `intervallAendern(schritt: Int)`, `zielAendern(schritt: Int)`, `wochentagUmschalten(tag: DayOfWeek)`, `zeitHinzufuegen(zeit: LocalTime)`, `zeitEntfernen(zeit: LocalTime)`, `zeitplan(): Zeitplan`; Konstanten `PUNKTE_MIN = 1`, `INTERVALL_MIN = 1`, `INTERVALL_MAX = 12`, `ZIEL_MIN = 1`
  - `EntwurfFehler` ohne `ZEIT_UNLESBAR` (mit Zeitwähler nicht mehr erreichbar)
  - `AktivitaetBearbeitenViewModel.speichern(): SpeicherErgebnis?` (null = Entwurf hat Fehler)
  - `AktivitaetBearbeitenScreen(id: Long, fertig: (SpeicherErgebnis) -> Unit, zurueck: () -> Unit, viewModel = hiltViewModel())`
  - `ui/gemeinsam`: `Stepper(wert: Int, verringern: () -> Unit, erhoehen: () -> Unit, modifier: Modifier = Modifier, verringernMoeglich: Boolean = true, erhoehenMoeglich: Boolean = true)`, `StepperZeile(titel: String, modifier: Modifier = Modifier, untertitel: String? = null, stepper: @Composable () -> Unit)`, `StepperZeilenInhalt(...)` (gleiche Parameter, ohne Fläche), `UhrzeitDialog(start: LocalTime, bestaetigen: (LocalTime) -> Unit, abbrechen: () -> Unit)`
  - `DayOrganizerFormen.feld` (18 dp), `.listenKarte` (22 dp), `.chip` (12 dp)

**Verhaltensentscheidungen (aus dem Prototyp abgeleitet):**
- Ein neuer Entwurf hat `zeiten = [08:00]` (Prototyp `openEdit(0)`), damit „Feste Zeiten“ sofort speicherbar ist. Alle anderen Vorgaben bleiben wie bisher (10 Punkte, Intervall 3, Ziel 3).
- Stepper: Punkte ≥ 1, Intervall 1–12 Stunden, Ziel ≥ 1 (Prototyp `ptsDec`/`ivDec`/`ivInc`/`zielDec`). Ein geladener Altwert über 12 Stunden bleibt stehen, bis der Nutzer den Stepper bedient. Der erste Minus-Schritt landet dann auf 12.
- `zeitHinzufuegen` sortiert und entfernt Doppelte. Dieselbe Uhrzeit zweimal ergäbe sonst zwei identische Fälligkeiten.
- Fehler bleiben bis zum ersten Speicherversuch stumm. Danach wird bei jeder Änderung neu geprüft, damit ein Fehlerhinweis verschwindet, sobald er behoben ist (Prototyp: `setName` löscht `err`).
- Die Uhrzeit-Chips erscheinen bei „Feste Zeiten“ und „Mal pro Tag“ (dort als Erinnerungszeiten, `Tagesziel.anstossZeiten`), wie `showZeiten` im Prototyp.
- Der Prototyp zeichnet den „Uhrzeit hinzufügen“-Chip gestrichelt. Compose hat dafür keinen Standard-Border, darum hier ein durchgezogener 1,5-dp-Rahmen in `line`. Das ist eine bewusste Vereinfachung.

- [ ] **Step 1: Entwurf-Tests auf das neue Modell umstellen (failing)**

`app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetEntwurfTest.kt` vollständig ersetzen:

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AktivitaetEntwurfTest {

    private val acht = LocalTime.of(8, 0)
    private val zwoelf = LocalTime.of(12, 0)

    private val gueltig = AktivitaetEntwurf(
        name = "Tabletten",
        basisPunkte = 10,
        typ = ZeitplanTyp.FESTE_ZEITEN,
        zeiten = listOf(acht),
        wochentage = DayOfWeek.entries.toSet(),
    )

    @Test
    fun `ein vollstaendiger Entwurf hat keine Fehler`() {
        assertEquals(emptyList(), gueltig.pruefen())
    }

    @Test
    fun `ein neuer Entwurf ist bis auf den Namen sofort gueltig`() {
        assertEquals(listOf(EntwurfFehler.NAME_LEER), AktivitaetEntwurf().pruefen())
    }

    @Test
    fun `leerer Name und nicht positive Punkte werden gemeldet`() {
        val fehler = gueltig.copy(name = "  ", basisPunkte = 0).pruefen()

        assertTrue(EntwurfFehler.NAME_LEER in fehler)
        assertTrue(EntwurfFehler.PUNKTE_NICHT_POSITIV in fehler)
    }

    @Test
    fun `feste Zeiten brauchen mindestens eine Uhrzeit und einen Wochentag`() {
        val fehler = gueltig.copy(zeiten = emptyList(), wochentage = emptySet()).pruefen()

        assertTrue(EntwurfFehler.KEINE_ZEIT in fehler)
        assertTrue(EntwurfFehler.KEIN_WOCHENTAG in fehler)
    }

    @Test
    fun `ein Wochenziel braucht keine Wochentage aber eine positive Anzahl`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.WOCHENZIEL,
            zeiten = emptyList(),
            wochentage = emptySet(),
            zielAnzahl = 0,
        )

        assertEquals(listOf(EntwurfFehler.ZIEL_NICHT_POSITIV), entwurf.pruefen())
    }

    @Test
    fun `ein Intervall braucht ein positives Fenster`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.INTERVALL,
            zeiten = emptyList(),
            intervallStunden = 0,
            fensterVon = LocalTime.of(20, 0),
            fensterBis = LocalTime.of(8, 0),
        )

        assertTrue(EntwurfFehler.INTERVALL_UNGUELTIG in entwurf.pruefen())
    }

    @Test
    fun `ein Tagesziel braucht eine positive Anzahl und einen Wochentag, aber keine Uhrzeit`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.TAGESZIEL,
            zeiten = emptyList(),
            wochentage = emptySet(),
            zielAnzahl = 0,
        )

        assertEquals(
            listOf(EntwurfFehler.ZIEL_NICHT_POSITIV, EntwurfFehler.KEIN_WOCHENTAG),
            entwurf.pruefen(),
        )
    }

    @Test
    fun `Punkte bleiben beim Verringern mindestens 1`() {
        val entwurf = gueltig.copy(basisPunkte = 1)

        assertEquals(1, entwurf.punkteAendern(-1).basisPunkte)
        assertEquals(2, entwurf.punkteAendern(+1).basisPunkte)
    }

    @Test
    fun `das Intervall bleibt zwischen 1 und 12 Stunden`() {
        assertEquals(1, gueltig.copy(intervallStunden = 1).intervallAendern(-1).intervallStunden)
        assertEquals(12, gueltig.copy(intervallStunden = 12).intervallAendern(+1).intervallStunden)
        assertEquals(4, gueltig.copy(intervallStunden = 3).intervallAendern(+1).intervallStunden)
    }

    @Test
    fun `ein geladener Altwert ueber 12 Stunden faellt beim ersten Schritt auf 12`() {
        assertEquals(12, gueltig.copy(intervallStunden = 16).intervallAendern(-1).intervallStunden)
    }

    @Test
    fun `das Ziel bleibt beim Verringern mindestens 1`() {
        assertEquals(1, gueltig.copy(zielAnzahl = 1).zielAendern(-1).zielAnzahl)
        assertEquals(4, gueltig.copy(zielAnzahl = 3).zielAendern(+1).zielAnzahl)
    }

    @Test
    fun `ein Wochentag wird umgeschaltet`() {
        val ohneMontag = gueltig.wochentagUmschalten(DayOfWeek.MONDAY)

        assertTrue(DayOfWeek.MONDAY !in ohneMontag.wochentage)
        assertTrue(DayOfWeek.MONDAY in ohneMontag.wochentagUmschalten(DayOfWeek.MONDAY).wochentage)
    }

    @Test
    fun `eine hinzugefuegte Uhrzeit wird einsortiert und nicht doppelt aufgenommen`() {
        val entwurf = gueltig.copy(zeiten = listOf(zwoelf))
            .zeitHinzufuegen(acht)
            .zeitHinzufuegen(zwoelf)

        assertEquals(listOf(acht, zwoelf), entwurf.zeiten)
    }

    @Test
    fun `eine Uhrzeit wird entfernt`() {
        val entwurf = gueltig.copy(zeiten = listOf(acht, zwoelf)).zeitEntfernen(acht)

        assertEquals(listOf(zwoelf), entwurf.zeiten)
    }

    @Test
    fun `der Zeitplan nimmt die Uhrzeiten sortiert auf`() {
        val entwurf = gueltig.copy(zeiten = listOf(zwoelf, acht))

        assertEquals(
            Zeitplan.FesteZeiten(listOf(acht, zwoelf), DayOfWeek.entries.toSet()),
            entwurf.zeitplan(),
        )
    }

    @Test
    fun `beim Tagesziel werden die Uhrzeiten zu Erinnerungszeiten`() {
        val entwurf = gueltig.copy(typ = ZeitplanTyp.TAGESZIEL, zielAnzahl = 2, zeiten = listOf(acht))

        assertEquals(
            Zeitplan.Tagesziel(2, DayOfWeek.entries.toSet(), listOf(acht)),
            entwurf.zeitplan(),
        )
    }

    @Test
    fun `das Intervall uebernimmt das Zeitfenster`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.INTERVALL,
            intervallStunden = 2,
            fensterVon = LocalTime.of(9, 0),
            fensterBis = LocalTime.of(17, 0),
        )

        assertEquals(
            Zeitplan.Intervall(2, LocalTime.of(9, 0), LocalTime.of(17, 0), DayOfWeek.entries.toSet()),
            entwurf.zeitplan(),
        )
    }

    @Test
    fun `der Rundlauf durch aus() erhaelt die Uhrzeiten`() {
        val aktivitaet = Aktivitaet(
            id = 7,
            name = "Tabletten",
            farbe = 0xFF4CAF50.toInt(),
            basisPunkte = 10,
            zeitplan = Zeitplan.FesteZeiten(
                listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)),
                DayOfWeek.entries.toSet(),
            ),
            erstelltAm = LocalDate.of(2025, 12, 1),
        )

        val entwurf = AktivitaetEntwurf.aus(aktivitaet)
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)), entwurf.zeiten)

        val rundlauf = entwurf.zuAktivitaet(LocalDate.of(2026, 3, 2))
        assertEquals(aktivitaet.zeitplan, rundlauf.zeitplan)
    }

    @Test
    fun `ein Tagesziel ohne Erinnerung laedt ohne Uhrzeiten`() {
        val aktivitaet = Aktivitaet(
            id = 7,
            name = "Lesen",
            farbe = 0xFF4CAF50.toInt(),
            basisPunkte = 10,
            zeitplan = Zeitplan.Tagesziel(1, DayOfWeek.entries.toSet()),
            erstelltAm = LocalDate.of(2025, 12, 1),
        )

        assertEquals(emptyList(), AktivitaetEntwurf.aus(aktivitaet).zeiten)
    }

    @Test
    fun `der Entwurf wird in den richtigen Zeitplan umgesetzt`() {
        val aktivitaet = gueltig.zuAktivitaet(LocalDate.of(2026, 3, 2))

        assertEquals("Tabletten", aktivitaet.name)
        assertEquals(10, aktivitaet.basisPunkte)
        assertEquals(
            Zeitplan.FesteZeiten(listOf(acht), DayOfWeek.entries.toSet()),
            aktivitaet.zeitplan,
        )
    }

    @Test
    fun `ein Wochenziel wird in den Wochenziel-Zeitplan umgesetzt`() {
        val entwurf = gueltig.copy(typ = ZeitplanTyp.WOCHENZIEL, zielAnzahl = 3)

        assertEquals(Zeitplan.Wochenziel(3), entwurf.zuAktivitaet(LocalDate.of(2026, 3, 2)).zeitplan)
    }

    @Test
    fun `ein archivierter Entwurf bleibt beim Rundlauf archiviert`() {
        val archiviertAm = LocalDate.of(2026, 1, 15)
        val aktivitaet = Aktivitaet(
            id = 7,
            name = "Tabletten",
            farbe = 0xFF4CAF50.toInt(),
            basisPunkte = 10,
            zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
            aktiv = false,
            erstelltAm = LocalDate.of(2025, 12, 1),
            archiviertAm = archiviertAm,
        )

        val rundlauf = AktivitaetEntwurf.aus(aktivitaet).zuAktivitaet(LocalDate.of(2026, 3, 2))

        assertEquals(false, rundlauf.aktiv)
        assertEquals(archiviertAm, rundlauf.archiviertAm)
    }

    @Test
    fun `ein Entwurf behaelt das echte Erstellungsdatum beim erneuten Speichern`() {
        val erstelltAm = LocalDate.of(2025, 12, 1)
        val aktivitaet = Aktivitaet(
            id = 7,
            name = "Tabletten",
            farbe = 0xFF4CAF50.toInt(),
            basisPunkte = 10,
            zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
            erstelltAm = erstelltAm,
        )

        // Umbenennen simuliert das erneute Speichern eines bestehenden Entwurfs an einem
        // spaeteren Tag -- das Anlegedatum darf dabei nicht auf "heute" zurueckspringen.
        val rundlauf = AktivitaetEntwurf.aus(aktivitaet).copy(name = "Tabletten neu")
            .zuAktivitaet(LocalDate.of(2026, 3, 2))

        assertEquals(erstelltAm, rundlauf.erstelltAm)
    }

    @Test
    fun `ein brandneuer Entwurf ohne eigenes Erstellungsdatum bekommt heute`() {
        val heute = LocalDate.of(2026, 3, 2)

        val aktivitaet = gueltig.zuAktivitaet(heute)

        assertEquals(heute, aktivitaet.erstelltAm)
    }

    @Test
    fun `eine aktive Aktivitaet bleibt beim Rundlauf gleich`() {
        val aktivitaet = Aktivitaet(
            id = 7,
            name = "Tabletten",
            notiz = "vor dem Essen",
            farbe = 0xFF4CAF50.toInt(),
            symbol = "pill",
            basisPunkte = 10,
            zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
            kulanzMinuten = 45,
            puenktlichkeitsBonus = 5,
            nachfassAnzahl = 1,
            nachfassAbstandMinuten = 20,
            aktiv = true,
            erstelltAm = LocalDate.of(2025, 12, 1),
            archiviertAm = null,
        )

        val rundlauf = AktivitaetEntwurf.aus(aktivitaet).zuAktivitaet(aktivitaet.erstelltAm)

        assertEquals(aktivitaet, rundlauf)
    }

    @Test
    fun `ein geleertes Nachfass-Feld faellt auf den Modell-Standard zurueck statt Nachfassen abzuschalten`() {
        val entwurf = gueltig.copy(nachfassAnzahlText = "")

        val aktivitaet = entwurf.zuAktivitaet(LocalDate.of(2026, 3, 2))

        // 2 ist Aktivitaet.nachfassAnzahl's eigener Standard -- ein geleertes Feld darf
        // Nachfassen nicht implizit auf 0 (aus) setzen.
        assertEquals(2, aktivitaet.nachfassAnzahl)
    }
}
```

Hinweis: Der Rundlauf-Test setzt jetzt bewusst `symbol = "pill"` (Konstruktorparameter seit Plan 4), damit er auch das Symbol abdeckt.

- [ ] **Step 2: Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetEntwurfTest'`
Expected: FAIL beim Kompilieren (`No parameter with name 'basisPunkte'`, `Unresolved reference: punkteAendern` usw.).

- [ ] **Step 3: `AktivitaetEntwurf.kt` vollständig ersetzen**

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.ui.theme.AKTIVITAET_SYMBOL_STANDARD
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class ZeitplanTyp { FESTE_ZEITEN, INTERVALL, TAGESZIEL, WOCHENZIEL }

enum class EntwurfFehler {
    NAME_LEER,
    PUNKTE_NICHT_POSITIV,
    KEINE_ZEIT,
    KEIN_WOCHENTAG,
    ZIEL_NICHT_POSITIV,
    INTERVALL_UNGUELTIG,
}

/** Was [AktivitaetBearbeitenViewModel.speichern] getan hat -- die Liste zeigt danach den
 * passenden Toast ("Aktivitaet angelegt" / "Aenderungen gespeichert"). */
enum class SpeicherErgebnis { ANGELEGT, GEAENDERT }

/**
 * Bearbeitbarer Zwischenstand.
 *
 * Seit dem Redesign (Plan 5) kommen Punkte, Intervall und Zielanzahl aus Steppern und
 * Uhrzeiten aus einem Zeitwaehler -- halb getippte Zahlen oder unlesbare Uhrzeiten gibt
 * es nicht mehr, darum sind diese Felder typisiert. Die Felder, die der Bildschirm nicht
 * anbietet (Kulanz, Nachfassen, ...), bleiben Text und werden unveraendert durchgereicht.
 *
 * Die Bearbeitungsoperationen (punkteAendern, zeitHinzufuegen, ...) tragen die Regeln der
 * Bedienelemente, damit sie ohne Compose testbar sind.
 */
data class AktivitaetEntwurf(
    val id: Long = 0,
    val name: String = "",
    val notiz: String = "",
    val farbe: Int = 0xFF4CAF50.toInt(),
    val symbol: String = AKTIVITAET_SYMBOL_STANDARD,
    val basisPunkte: Int = 10,
    val typ: ZeitplanTyp = ZeitplanTyp.FESTE_ZEITEN,
    // Prototyp openEdit(0): eine neue Aktivitaet startet mit 08:00, damit "Feste Zeiten"
    // ohne weiteren Schritt speicherbar ist.
    val zeiten: List<LocalTime> = listOf(LocalTime.of(8, 0)),
    val wochentage: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val intervallStunden: Int = 3,
    val fensterVon: LocalTime = LocalTime.of(8, 0),
    val fensterBis: LocalTime = LocalTime.of(22, 0),
    val zielAnzahl: Int = 3,
    val anstoss: List<Anstoss> = emptyList(),
    val kulanzMinutenText: String = "60",
    val puenktlichkeitsBonusText: String = "5",
    val serienSchwellen: List<SerienSchwelle> = listOf(SerienSchwelle(7, 10)),
    val nachfassAnzahlText: String = "2",
    val nachfassAbstandText: String = "15",
    val aktiv: Boolean = true,
    val archiviertAm: LocalDate? = null,
    val erstelltAm: LocalDate? = null,
) {

    fun punkteAendern(schritt: Int): AktivitaetEntwurf =
        copy(basisPunkte = (basisPunkte + schritt).coerceAtLeast(PUNKTE_MIN))

    /** coerceIn statt nur einer Untergrenze: der Prototyp begrenzt auf 12 Stunden. Ein
     * geladener Altwert darueber bleibt stehen, bis der Stepper bedient wird. */
    fun intervallAendern(schritt: Int): AktivitaetEntwurf =
        copy(intervallStunden = (intervallStunden + schritt).coerceIn(INTERVALL_MIN, INTERVALL_MAX))

    fun zielAendern(schritt: Int): AktivitaetEntwurf =
        copy(zielAnzahl = (zielAnzahl + schritt).coerceAtLeast(ZIEL_MIN))

    fun wochentagUmschalten(tag: DayOfWeek): AktivitaetEntwurf =
        copy(wochentage = if (tag in wochentage) wochentage - tag else wochentage + tag)

    /** Sortiert und ohne Doppelte -- dieselbe Uhrzeit zweimal ergaebe zwei identische
     * Faelligkeiten. */
    fun zeitHinzufuegen(zeit: LocalTime): AktivitaetEntwurf =
        copy(zeiten = (zeiten + zeit).distinct().sorted())

    fun zeitEntfernen(zeit: LocalTime): AktivitaetEntwurf = copy(zeiten = zeiten - zeit)

    fun pruefen(): List<EntwurfFehler> = buildList {
        if (name.isBlank()) add(EntwurfFehler.NAME_LEER)
        if (basisPunkte <= 0) add(EntwurfFehler.PUNKTE_NICHT_POSITIV)

        when (typ) {
            ZeitplanTyp.FESTE_ZEITEN -> {
                if (zeiten.isEmpty()) add(EntwurfFehler.KEINE_ZEIT)
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.INTERVALL -> {
                if (intervallStunden <= 0 || !fensterVon.isBefore(fensterBis)) {
                    add(EntwurfFehler.INTERVALL_UNGUELTIG)
                }
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.TAGESZIEL -> {
                if (zielAnzahl <= 0) add(EntwurfFehler.ZIEL_NICHT_POSITIV)
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.WOCHENZIEL -> {
                if (zielAnzahl <= 0) add(EntwurfFehler.ZIEL_NICHT_POSITIV)
            }
        }
    }

    /** Auch fuer einen noch fehlerhaften Entwurf aufrufbar -- die Plan-Zusammenfassung im
     * Bildschirm zeigt den Zwischenstand live an. */
    fun zeitplan(): Zeitplan {
        val sortiert = zeiten.sorted()
        return when (typ) {
            ZeitplanTyp.FESTE_ZEITEN -> Zeitplan.FesteZeiten(sortiert, wochentage)
            ZeitplanTyp.INTERVALL -> Zeitplan.Intervall(intervallStunden, fensterVon, fensterBis, wochentage)
            ZeitplanTyp.TAGESZIEL -> Zeitplan.Tagesziel(zielAnzahl, wochentage, sortiert)
            ZeitplanTyp.WOCHENZIEL -> Zeitplan.Wochenziel(zielAnzahl, anstoss)
        }
    }

    /** Nur aufrufen, wenn [pruefen] leer ist. [heute] gilt nur, wenn der Entwurf noch kein
     * eigenes [erstelltAm] traegt -- ein neu angelegter Entwurf bekommt so das heutige Datum,
     * eine bestehende Aktivitaet behaelt ihr echtes Anlegedatum ueber jedes Speichern hinweg. */
    fun zuAktivitaet(heute: LocalDate): Aktivitaet = Aktivitaet(
        id = id,
        name = name.trim(),
        notiz = notiz.trim(),
        farbe = farbe,
        symbol = symbol,
        basisPunkte = basisPunkte,
        zeitplan = zeitplan(),
        kulanzMinuten = kulanzMinutenText.toIntOrNull() ?: 60,
        puenktlichkeitsBonus = puenktlichkeitsBonusText.toIntOrNull() ?: 0,
        serienSchwellen = serienSchwellen,
        nachfassAnzahl = nachfassAnzahlText.toIntOrNull() ?: 2,
        nachfassAbstandMinuten = nachfassAbstandText.toIntOrNull() ?: 15,
        erstelltAm = erstelltAm ?: heute,
        aktiv = aktiv,
        archiviertAm = archiviertAm,
    )

    companion object {
        const val PUNKTE_MIN = 1
        const val INTERVALL_MIN = 1
        const val INTERVALL_MAX = 12
        const val ZIEL_MIN = 1

        fun aus(aktivitaet: Aktivitaet): AktivitaetEntwurf {
            val basis = AktivitaetEntwurf(
                id = aktivitaet.id,
                name = aktivitaet.name,
                notiz = aktivitaet.notiz,
                farbe = aktivitaet.farbe,
                symbol = aktivitaet.symbol,
                basisPunkte = aktivitaet.basisPunkte,
                kulanzMinutenText = aktivitaet.kulanzMinuten.toString(),
                puenktlichkeitsBonusText = aktivitaet.puenktlichkeitsBonus.toString(),
                serienSchwellen = aktivitaet.serienSchwellen,
                nachfassAnzahlText = aktivitaet.nachfassAnzahl.toString(),
                nachfassAbstandText = aktivitaet.nachfassAbstandMinuten.toString(),
                aktiv = aktivitaet.aktiv,
                archiviertAm = aktivitaet.archiviertAm,
                erstelltAm = aktivitaet.erstelltAm,
            )
            return when (val plan = aktivitaet.zeitplan) {
                is Zeitplan.FesteZeiten -> basis.copy(
                    typ = ZeitplanTyp.FESTE_ZEITEN,
                    zeiten = plan.zeiten,
                    wochentage = plan.wochentage,
                )
                is Zeitplan.Intervall -> basis.copy(
                    typ = ZeitplanTyp.INTERVALL,
                    intervallStunden = plan.alleNStunden,
                    fensterVon = plan.von,
                    fensterBis = plan.bis,
                    wochentage = plan.wochentage,
                )
                is Zeitplan.Tagesziel -> basis.copy(
                    typ = ZeitplanTyp.TAGESZIEL,
                    zielAnzahl = plan.anzahl,
                    wochentage = plan.wochentage,
                    zeiten = plan.anstossZeiten,
                )
                is Zeitplan.Wochenziel -> basis.copy(
                    typ = ZeitplanTyp.WOCHENZIEL,
                    zielAnzahl = plan.anzahl,
                    anstoss = plan.anstoss,
                )
            }
        }
    }
}
```

Hinweis: `AKTIVITAET_SYMBOL_STANDARD` existiert seit Plan 4 in `ui/theme/AktivitaetSymbol.kt` und hat den Wert `"self_improvement"`. Der Default bleibt damit inhaltlich gleich.

- [ ] **Step 4: ViewModel-Tests ergänzen (failing)**

In `AktivitaetBearbeitenViewModelTest.kt` folgende Imports ergänzen: `kotlin.test.assertNull`, `kotlin.test.assertTrue`. Unter den bestehenden Tests diese Tests anfügen:

```kotlin
    private val bestehendeAktivitaet = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 1, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `ein neuer gueltiger Entwurf wird angelegt`() = runTest(dispatcher) {
        val repo = FakeAktivitaetRepository()
        val vm = viewModel(repo)
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Lesen") }

        assertEquals(SpeicherErgebnis.ANGELEGT, vm.speichern())
        assertEquals("Lesen", repo.stand.value.single().name)
    }

    @Test
    fun `ein bestehender Entwurf meldet geaendert`() = runTest(dispatcher) {
        val repo = FakeAktivitaetRepository(listOf(bestehendeAktivitaet))
        val vm = viewModel(repo)
        vm.laden(1L).join()
        vm.aendern { it.punkteAendern(+1) }

        assertEquals(SpeicherErgebnis.GEAENDERT, vm.speichern())
        assertEquals(11, repo.stand.value.single().basisPunkte)
    }

    @Test
    fun `ein fehlerhafter Entwurf wird nicht gespeichert`() = runTest(dispatcher) {
        val repo = FakeAktivitaetRepository()
        val vm = viewModel(repo)
        vm.laden(0L).join()

        assertNull(vm.speichern())
        assertEquals(listOf(EntwurfFehler.NAME_LEER), vm.fehler.value)
        assertTrue(repo.stand.value.isEmpty())
    }

    @Test
    fun `vor dem ersten Speicherversuch bleiben Fehler stumm`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.laden(0L).join()

        vm.aendern { it.copy(wochentage = emptySet()) }

        assertEquals(emptyList(), vm.fehler.value)
    }

    @Test
    fun `nach einem gescheiterten Speichern verschwindet ein behobener Fehler sofort`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.laden(0L).join()
        vm.speichern()

        vm.aendern { it.copy(name = "Lesen") }

        assertEquals(emptyList(), vm.fehler.value)
    }
```

- [ ] **Step 5: Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.ui.aktivitaeten.*'`
Expected: FAIL. Der ViewModel-Test kompiliert nicht (`speichern()` liefert `Boolean`, Vergleich mit `SpeicherErgebnis`), außerdem kompiliert `AktivitaetBearbeitenScreen.kt` nicht mehr.

- [ ] **Step 6: ViewModel anpassen**

In `AktivitaetBearbeitenViewModel.kt` `aendern` und `speichern` ersetzen:

```kotlin
    /** Fehler bleiben bis zum ersten Speicherversuch stumm. Danach wird jede Aenderung neu
     * geprueft, damit ein behobener Fehler sofort verschwindet (Prototyp: setName loescht
     * err). */
    fun aendern(block: (AktivitaetEntwurf) -> AktivitaetEntwurf) {
        _entwurf.value = block(_entwurf.value)
        if (_fehler.value.isNotEmpty()) _fehler.value = _entwurf.value.pruefen()
    }

    /** Speichert und plant neu. Gibt null zurueck, wenn der Entwurf Fehler hat. */
    suspend fun speichern(): SpeicherErgebnis? {
        val fehler = _entwurf.value.pruefen()
        _fehler.value = fehler
        if (fehler.isNotEmpty()) return null

        val ergebnis = if (_entwurf.value.id == 0L) SpeicherErgebnis.ANGELEGT else SpeicherErgebnis.GEAENDERT
        val id = aktivitaeten.speichern(_entwurf.value.zuAktivitaet(LocalDate.now(uhr)))
        // (bestehender Kommentar zur Nachbereitung unveraendert uebernehmen)
        runCatching {
            neuPlanen(id)
        }.onFailure { ausnahme ->
            Log.w(TAG, "Nachbereitung nach dem Speichern fehlgeschlagen", ausnahme)
        }
        return ergebnis
    }
```

Den langen Kommentarblock vor `runCatching` aus dem bisherigen Code wörtlich behalten. Der Platzhalter-Kommentar oben steht nur für ihn und wird nicht als Text übernommen.

- [ ] **Step 7: Formen ergänzen**

In `app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Formen.kt` innerhalb von `object DayOrganizerFormen` ergänzen:

```kotlin
    /** Stepper-Zeilen, Intervall-Karte, Speichern-Knopf (Prototyp border-radius:18px). */
    val feld = RoundedCornerShape(18.dp)
    /** Zeilen der Aktivitaeten-Liste (Prototyp border-radius:22px). */
    val listenKarte = RoundedCornerShape(22.dp)
    /** Uhrzeit-Chips (Prototyp border-radius:12px). */
    val chip = RoundedCornerShape(12.dp)
```

- [ ] **Step 8: Strings ergänzen und anpassen**

In `app/src/main/res/values/strings.xml`:
- `feld_uhrzeiten` ändern auf `Uhrzeiten` (der alte Text erklärte das Komma-Format, das es nicht mehr gibt).
- Neue Einträge im Block nach `speichern` einfügen:

```xml
    <string name="aktivitaet_bearbeiten_titel">Aktivität bearbeiten</string>
    <string name="feld_symbol">Symbol</string>
    <string name="name_platzhalter">z. B. Tabletten</string>
    <string name="typ_feste_zeiten_hinweis">z. B. 8:00 und 20:00</string>
    <string name="typ_intervall_hinweis">alle paar Stunden</string>
    <string name="typ_tagesziel_hinweis">ohne feste Uhrzeit</string>
    <string name="typ_wochenziel_hinweis">z. B. 3× Sport</string>
    <string name="feld_zeitfenster">Zeitfenster</string>
    <string name="zeitfenster_bis">bis</string>
    <string name="ziel_einheit_tag">mal pro Tag</string>
    <string name="ziel_einheit_woche">mal pro Woche</string>
    <string name="uhrzeit_hinzufuegen">Uhrzeit</string>
    <string name="uhrzeit_entfernen">%1$s entfernen</string>
    <string name="uhrzeit_uebernehmen">Übernehmen</string>
    <string name="uhrzeit_abbrechen">Abbrechen</string>
    <string name="stepper_weniger">Weniger</string>
    <string name="stepper_mehr">Mehr</string>
    <string name="zurueck">Zurück</string>
    <string name="fehler_name_leer">Bitte einen Namen eingeben</string>
    <string name="fehler_kein_wochentag">Mindestens einen Wochentag wählen</string>
    <string name="fehler_keine_zeit">Mindestens eine Uhrzeit hinzufügen</string>
    <string name="fehler_intervall">Das Zeitfenster muss vor seinem Ende beginnen</string>
```

Vorher mit `grep -n 'name="zurueck"\|name="feld_symbol"' app/src/main/res/values/strings.xml` prüfen, dass keiner der Schlüssel schon existiert. Bei Planerstellung existierte keiner.

- [ ] **Step 9: `ui/gemeinsam/Stepper.kt` anlegen**

```kotlin
package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme

/** Plus/Minus-Pille aus dem Prototyp (Punkte, Intervall, Ziel; in Plan 6 Bonus/Kulanz).
 * Die Grenzen kennt der Aufrufer -- hier wird nur der jeweilige Knopf deaktiviert. */
@Composable
fun Stepper(
    wert: Int,
    verringern: () -> Unit,
    erhoehen: () -> Unit,
    modifier: Modifier = Modifier,
    verringernMoeglich: Boolean = true,
    erhoehenMoeglich: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(DayOrganizerFormen.pille)
            .background(DayOrganizerTheme.farben.surface2)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StepperKnopf(Icons.Filled.Remove, stringResource(R.string.stepper_weniger), verringernMoeglich, verringern)
        Text(
            wert.toString(),
            modifier = Modifier.widthIn(min = 36.dp),
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DayOrganizerTheme.farben.ink,
        )
        StepperKnopf(Icons.Filled.Add, stringResource(R.string.stepper_mehr), erhoehenMoeglich, erhoehen)
    }
}

@Composable
private fun StepperKnopf(symbol: ImageVector, beschreibung: String, aktiv: Boolean, aktion: () -> Unit) {
    val farben = DayOrganizerTheme.farben
    FilledIconButton(
        onClick = aktion,
        enabled = aktiv,
        modifier = Modifier.size(40.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = farben.surface,
            contentColor = farben.ink,
            disabledContainerColor = farben.surface,
            disabledContentColor = farben.ink2.copy(alpha = 0.4f),
        ),
    ) {
        Icon(symbol, contentDescription = beschreibung, modifier = Modifier.size(20.dp))
    }
}

/** Titel (+ optionaler Untertitel) links, Stepper rechts, ohne eigene Flaeche -- fuer
 * Karten, die mehrere Zeilen buendeln (Intervall-Karte). */
@Composable
fun StepperZeilenInhalt(
    titel: String,
    modifier: Modifier = Modifier,
    untertitel: String? = null,
    stepper: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titel, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            untertitel?.let { Text(it, fontSize = 12.sp, color = DayOrganizerTheme.farben.ink2) }
        }
        stepper()
    }
}

/** [StepperZeilenInhalt] auf eigener Flaeche (Prototyp: border-radius 18px, var(--surface)). */
@Composable
fun StepperZeile(
    titel: String,
    modifier: Modifier = Modifier,
    untertitel: String? = null,
    stepper: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = DayOrganizerFormen.feld,
        color = DayOrganizerTheme.farben.surface,
        contentColor = DayOrganizerTheme.farben.ink,
    ) {
        StepperZeilenInhalt(titel = titel, untertitel = untertitel, stepper = stepper)
    }
}
```

- [ ] **Step 10: `ui/gemeinsam/UhrzeitDialog.kt` anlegen**

```kotlin
package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.vanhake.dayorganizer.R
import java.time.LocalTime

/**
 * Zeitwaehler im AlertDialog. material3 1.3 (BOM 2024.12.01) hat noch keine fertige
 * TimePickerDialog-Composable, darum dieser schmale Rahmen. Immer 24-Stunden-Format --
 * die App ist durchgehend deutsch, und der Plantext zeigt HH:mm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UhrzeitDialog(start: LocalTime, bestaetigen: (LocalTime) -> Unit, abbrechen: () -> Unit) {
    val zustand = rememberTimePickerState(initialHour = start.hour, initialMinute = start.minute, is24Hour = true)
    AlertDialog(
        onDismissRequest = abbrechen,
        confirmButton = {
            TextButton(onClick = { bestaetigen(LocalTime.of(zustand.hour, zustand.minute)) }) {
                Text(stringResource(R.string.uhrzeit_uebernehmen))
            }
        },
        dismissButton = {
            TextButton(onClick = abbrechen) { Text(stringResource(R.string.uhrzeit_abbrechen)) }
        },
        text = { TimePicker(state = zustand) },
    )
}
```

- [ ] **Step 11: `AktivitaetBearbeitenScreen.kt` vollständig ersetzen**

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.gemeinsam.Stepper
import de.vanhake.dayorganizer.ui.gemeinsam.StepperZeile
import de.vanhake.dayorganizer.ui.gemeinsam.StepperZeilenInhalt
import de.vanhake.dayorganizer.ui.gemeinsam.UhrzeitDialog
import de.vanhake.dayorganizer.ui.theme.AKTIVITAET_SYMBOLE
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import de.vanhake.dayorganizer.ui.theme.aktivitaetSymbol
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

/** Wofuer der offene Zeitwaehler gerade waehlt. */
private enum class UhrzeitZiel { NEU, VON, BIS }

@Composable
fun AktivitaetBearbeitenScreen(
    id: Long,
    fertig: (SpeicherErgebnis) -> Unit,
    zurueck: () -> Unit,
    viewModel: AktivitaetBearbeitenViewModel = hiltViewModel(),
) {
    val entwurf by viewModel.entwurf.collectAsStateWithLifecycle()
    val fehler by viewModel.fehler.collectAsStateWithLifecycle()
    val bereich = rememberCoroutineScope()
    val farben = DayOrganizerTheme.farben
    var uhrzeitZiel by remember { mutableStateOf<UhrzeitZiel?>(null) }

    LaunchedEffect(id) { viewModel.laden(id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(farben.bg)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Kopfzeile(
            titel = stringResource(if (id == 0L) R.string.neue_aktivitaet else R.string.aktivitaet_bearbeiten_titel),
            zurueck = zurueck,
        )

        Abschnitt(R.string.feld_symbol) {
            SymbolRaster(entwurf.symbol) { neu -> viewModel.aendern { it.copy(symbol = neu) } }
        }

        Abschnitt(R.string.feld_name) {
            NameFeld(
                name = entwurf.name,
                fehlerhaft = EntwurfFehler.NAME_LEER in fehler,
                aendern = { neu -> viewModel.aendern { it.copy(name = neu) } },
            )
            FehlerText(EntwurfFehler.NAME_LEER in fehler, R.string.fehler_name_leer)
        }

        StepperZeile(titel = stringResource(R.string.feld_punkte)) {
            Stepper(
                wert = entwurf.basisPunkte,
                verringern = { viewModel.aendern { it.punkteAendern(-1) } },
                erhoehen = { viewModel.aendern { it.punkteAendern(+1) } },
                verringernMoeglich = entwurf.basisPunkte > AktivitaetEntwurf.PUNKTE_MIN,
            )
        }

        Abschnitt(R.string.feld_zeitplan) {
            TypKarten(entwurf.typ) { typ -> viewModel.aendern { it.copy(typ = typ) } }
        }

        if (entwurf.typ != ZeitplanTyp.WOCHENZIEL) {
            Abschnitt(R.string.feld_wochentage) {
                WochentagKacheln(entwurf.wochentage) { tag -> viewModel.aendern { it.wochentagUmschalten(tag) } }
                FehlerText(EntwurfFehler.KEIN_WOCHENTAG in fehler, R.string.fehler_kein_wochentag)
            }
        }

        if (entwurf.typ == ZeitplanTyp.FESTE_ZEITEN || entwurf.typ == ZeitplanTyp.TAGESZIEL) {
            Abschnitt(R.string.feld_uhrzeiten) {
                UhrzeitChips(
                    zeiten = entwurf.zeiten,
                    entfernen = { zeit -> viewModel.aendern { it.zeitEntfernen(zeit) } },
                    hinzufuegen = { uhrzeitZiel = UhrzeitZiel.NEU },
                )
                FehlerText(EntwurfFehler.KEINE_ZEIT in fehler, R.string.fehler_keine_zeit)
            }
        }

        if (entwurf.typ == ZeitplanTyp.INTERVALL) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                IntervallKarte(
                    entwurf = entwurf,
                    intervallAendern = { schritt -> viewModel.aendern { it.intervallAendern(schritt) } },
                    vonWaehlen = { uhrzeitZiel = UhrzeitZiel.VON },
                    bisWaehlen = { uhrzeitZiel = UhrzeitZiel.BIS },
                )
                FehlerText(EntwurfFehler.INTERVALL_UNGUELTIG in fehler, R.string.fehler_intervall)
            }
        }

        if (entwurf.typ == ZeitplanTyp.TAGESZIEL || entwurf.typ == ZeitplanTyp.WOCHENZIEL) {
            StepperZeile(
                titel = stringResource(R.string.feld_zielanzahl),
                untertitel = stringResource(
                    if (entwurf.typ == ZeitplanTyp.WOCHENZIEL) R.string.ziel_einheit_woche else R.string.ziel_einheit_tag,
                ),
            ) {
                Stepper(
                    wert = entwurf.zielAnzahl,
                    verringern = { viewModel.aendern { it.zielAendern(-1) } },
                    erhoehen = { viewModel.aendern { it.zielAendern(+1) } },
                    verringernMoeglich = entwurf.zielAnzahl > AktivitaetEntwurf.ZIEL_MIN,
                )
            }
        }

        PlanZusammenfassung(PlanText.aus(entwurf.zeitplan()))

        Button(
            onClick = { bereich.launch { viewModel.speichern()?.let(fertig) } },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = DayOrganizerFormen.feld,
            colors = ButtonDefaults.buttonColors(containerColor = farben.accent, contentColor = farben.onAccent),
        ) {
            Text(stringResource(R.string.speichern), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }

    uhrzeitZiel?.let { ziel ->
        UhrzeitDialog(
            start = when (ziel) {
                UhrzeitZiel.NEU -> entwurf.zeiten.lastOrNull() ?: LocalTime.of(8, 0)
                UhrzeitZiel.VON -> entwurf.fensterVon
                UhrzeitZiel.BIS -> entwurf.fensterBis
            },
            bestaetigen = { zeit ->
                viewModel.aendern {
                    when (ziel) {
                        UhrzeitZiel.NEU -> it.zeitHinzufuegen(zeit)
                        UhrzeitZiel.VON -> it.copy(fensterVon = zeit)
                        UhrzeitZiel.BIS -> it.copy(fensterBis = zeit)
                    }
                }
                uhrzeitZiel = null
            },
            abbrechen = { uhrzeitZiel = null },
        )
    }
}

@Composable
private fun Kopfzeile(titel: String, zurueck: () -> Unit) {
    Row(
        modifier = Modifier.offset(x = (-8).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = zurueck) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.zurueck),
                tint = DayOrganizerTheme.farben.ink,
            )
        }
        Text(titel, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = DayOrganizerTheme.farben.ink)
    }
}

@Composable
private fun Abschnitt(titelId: Int, inhalt: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(titelId),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = DayOrganizerTheme.farben.ink2,
        )
        inhalt()
    }
}

@Composable
private fun FehlerText(sichtbar: Boolean, textId: Int) {
    if (sichtbar) {
        Text(
            stringResource(textId),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DayOrganizerTheme.farben.coral,
        )
    }
}

@Composable
private fun SymbolRaster(gewaehlt: String, waehlen: (String) -> Unit) {
    val farben = DayOrganizerTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AKTIVITAET_SYMBOLE.chunked(6).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reihe.forEach { schluessel ->
                    val aktiv = schluessel == gewaehlt
                    Surface(
                        selected = aktiv,
                        onClick = { waehlen(schluessel) },
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        shape = DayOrganizerFormen.knopf,
                        color = if (aktiv) farben.accent else farben.surface,
                        contentColor = if (aktiv) farben.onAccent else farben.ink2,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Der Schluessel ist zugleich die Bedienhilfe-Beschreibung: es gibt
                            // keine deutschen Namen fuer die Symbole, und ein leeres
                            // contentDescription machte das Raster fuer TalkBack stumm.
                            Icon(aktivitaetSymbol(schluessel), contentDescription = schluessel, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NameFeld(name: String, fehlerhaft: Boolean, aendern: (String) -> Unit) {
    val farben = DayOrganizerTheme.farben
    OutlinedTextField(
        value = name,
        onValueChange = aendern,
        placeholder = { Text(stringResource(R.string.name_platzhalter)) },
        singleLine = true,
        isError = fehlerhaft,
        shape = DayOrganizerFormen.knopf,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = farben.surface,
            unfocusedContainerColor = farben.surface,
            errorContainerColor = farben.surface,
            focusedBorderColor = farben.accent,
            unfocusedBorderColor = farben.line,
            errorBorderColor = farben.coral,
            focusedTextColor = farben.ink,
            unfocusedTextColor = farben.ink,
            errorTextColor = farben.ink,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

private data class TypKarte(val typ: ZeitplanTyp, val titelId: Int, val hinweisId: Int)

private val TYP_KARTEN = listOf(
    TypKarte(ZeitplanTyp.FESTE_ZEITEN, R.string.typ_feste_zeiten, R.string.typ_feste_zeiten_hinweis),
    TypKarte(ZeitplanTyp.INTERVALL, R.string.typ_intervall, R.string.typ_intervall_hinweis),
    TypKarte(ZeitplanTyp.TAGESZIEL, R.string.typ_tagesziel, R.string.typ_tagesziel_hinweis),
    TypKarte(ZeitplanTyp.WOCHENZIEL, R.string.typ_wochenziel, R.string.typ_wochenziel_hinweis),
)

@Composable
private fun TypKarten(gewaehlt: ZeitplanTyp, waehlen: (ZeitplanTyp) -> Unit) {
    val farben = DayOrganizerTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TYP_KARTEN.chunked(2).forEach { reihe ->
            // IntrinsicSize.Min + fillMaxHeight: beide Karten einer Reihe gleich hoch, auch
            // wenn ein Hinweistext bei grosser Schrift umbricht.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reihe.forEach { karte ->
                    val aktiv = karte.typ == gewaehlt
                    Surface(
                        selected = aktiv,
                        onClick = { waehlen(karte.typ) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = DayOrganizerFormen.knopf,
                        color = if (aktiv) farben.accentSoft else farben.surface,
                        border = BorderStroke(2.dp, if (aktiv) farben.accent else Color.Transparent),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                stringResource(karte.titelId),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (aktiv) farben.onAccentSoft else farben.ink,
                            )
                            Text(stringResource(karte.hinweisId), fontSize = 12.sp, color = farben.ink2)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WochentagKacheln(gewaehlt: Set<DayOfWeek>, umschalten: (DayOfWeek) -> Unit) {
    val farben = DayOrganizerTheme.farben
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        DayOfWeek.entries.forEach { tag ->
            val aktiv = tag in gewaehlt
            Surface(
                selected = aktiv,
                onClick = { umschalten(tag) },
                modifier = Modifier.weight(1f).height(44.dp),
                shape = DayOrganizerFormen.kachel,
                color = if (aktiv) farben.accent else farben.surface,
                contentColor = if (aktiv) farben.onAccent else farben.ink2,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(PlanText.kuerzel(tag), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UhrzeitChips(zeiten: List<LocalTime>, entfernen: (LocalTime) -> Unit, hinzufuegen: () -> Unit) {
    val farben = DayOrganizerTheme.farben
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        zeiten.forEach { zeit ->
            val text = PlanText.uhrzeit(zeit)
            Surface(shape = DayOrganizerFormen.chip, color = farben.accentSoft, contentColor = farben.onAccentSoft) {
                Row(
                    modifier = Modifier.height(40.dp).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                    IconButton(onClick = { entfernen(zeit) }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.uhrzeit_entfernen, text),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Surface(
            onClick = hinzufuegen,
            shape = DayOrganizerFormen.chip,
            color = farben.surface,
            contentColor = farben.ink,
            border = BorderStroke(1.5.dp, farben.line),
        ) {
            Row(
                modifier = Modifier.height(40.dp).padding(start = 12.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.uhrzeit_hinzufuegen), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier.size(32.dp).clip(CircleShape).background(farben.accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = farben.onAccent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun IntervallKarte(
    entwurf: AktivitaetEntwurf,
    intervallAendern: (Int) -> Unit,
    vonWaehlen: () -> Unit,
    bisWaehlen: () -> Unit,
) {
    val farben = DayOrganizerTheme.farben
    Surface(shape = DayOrganizerFormen.feld, color = farben.surface, contentColor = farben.ink) {
        Column {
            StepperZeilenInhalt(titel = stringResource(R.string.feld_intervall)) {
                Stepper(
                    wert = entwurf.intervallStunden,
                    verringern = { intervallAendern(-1) },
                    erhoehen = { intervallAendern(+1) },
                    verringernMoeglich = entwurf.intervallStunden > AktivitaetEntwurf.INTERVALL_MIN,
                    erhoehenMoeglich = entwurf.intervallStunden < AktivitaetEntwurf.INTERVALL_MAX,
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = farben.line)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stringResource(R.string.feld_zeitfenster), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZeitKnopf(entwurf.fensterVon, vonWaehlen)
                    Text(stringResource(R.string.zeitfenster_bis), fontSize = 14.sp, color = farben.ink2)
                    ZeitKnopf(entwurf.fensterBis, bisWaehlen)
                }
            }
        }
    }
}

@Composable
private fun ZeitKnopf(zeit: LocalTime, waehlen: () -> Unit) {
    Surface(
        onClick = waehlen,
        shape = DayOrganizerFormen.kachel,
        color = DayOrganizerTheme.farben.surface2,
        contentColor = DayOrganizerTheme.farben.ink,
    ) {
        Text(
            PlanText.uhrzeit(zeit),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun PlanZusammenfassung(text: String) {
    Surface(
        shape = DayOrganizerFormen.knopf,
        color = DayOrganizerTheme.farben.surface2,
        contentColor = DayOrganizerTheme.farben.ink2,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.EventRepeat, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
```

Hinweise für den Implementierer:
- `DayOrganizerFormen.kachel` ist 14 dp (Wochentag-Kacheln im Prototyp: `border-radius:14px`). Für den Zeitknopf im Prototyp (10 px) ist 14 dp eine bewusste Annäherung, eine eigene Form für ein einzelnes Element lohnt nicht.
- `Surface(selected = …, onClick = …)` ist die auswählbare Überladung von material3 und setzt die Semantik `selected` für TalkBack. Sie ist in material3 1.3 nicht experimentell. Meldet der Compiler trotzdem ein Opt-in, ergänze `@OptIn(ExperimentalMaterial3Api::class)` an der betroffenen Funktion.
- Falls `Icons.Filled.EventRepeat` im Extended-Katalog fehlt (Kompilierfehler), nimm `Icons.Filled.Repeat` und vermerke das im Commit.

- [ ] **Step 12: Navigation für die neue Signatur anpassen**

In `app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt` den `AKTIVITAET_BEARBEITEN`-Eintrag ersetzen. Das Ergebnis wird erst in Task 4 an die Liste weitergereicht, hier wird es noch ignoriert:

```kotlin
            composable(Ziele.AKTIVITAET_BEARBEITEN) { eintrag ->
                AktivitaetBearbeitenScreen(
                    id = eintrag.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    fertig = { _ -> navController.popBackStack() },
                    zurueck = { navController.popBackStack() },
                )
            }
```

- [ ] **Step 13: Tests und Build**

Run: `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL. `AktivitaetEntwurfTest` (26 Tests) und `AktivitaetBearbeitenViewModelTest` (7 Tests) grün.

Zusätzlich prüfen, dass nichts mehr auf die alten Felder zeigt:

Run: `grep -rn "zeitenText\|basisPunkteText\|intervallStundenText\|zielAnzahlText\|ZEIT_UNLESBAR" app/src`
Expected: keine Treffer.

- [ ] **Step 14: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetEntwurf.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetBearbeitenViewModel.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetBearbeitenScreen.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/Stepper.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/UhrzeitDialog.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Formen.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetEntwurfTest.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetBearbeitenViewModelTest.kt
git commit -m "feat(aktivitaeten): Bearbeiten-Bildschirm im neuen Design

Typisierter Entwurf mit Bearbeitungsoperationen statt Textfeldern,
Symbolraster, Stepper, Zeitplankarten, Wochentag-Kacheln, Uhrzeit-Chips
mit Zeitwaehler, editierbares Intervall-Zeitfenster, Plan-Zusammenfassung.

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Listenzeilen mit Serie und Toasts im ViewModel

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenZusammenstellung.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenViewModel.kt` (vollständig ersetzen)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenScreen.kt` (nur `liste` → `zeilen`, siehe Step 7)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenZusammenstellungTest.kt` (neu)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenViewModelTest.kt` (neu)

**Interfaces:**
- Consumes: `PlanText.aus(Zeitplan)` (Task 1); `SpeicherErgebnis` (Task 2); `SerienRechner.aktuelleSerie(faelligkeiten: List<Faelligkeit>, bisPeriodeVon: LocalDate): Int` (`:domain`); `FaelligkeitRepository.historie(aktivitaetId: Long): List<Faelligkeit>` und `beobachteTag(tag: LocalDate): Flow<List<Faelligkeit>>`; `Hinweistoast(text: String, kannRueckgaengig: Boolean)` (Plan 4, `ui/gemeinsam/Hinweistoast.kt`).
- Produces:
  - `data class AktivitaetZeile(val id: Long, val name: String, val symbol: String, val planText: String, val basisPunkte: Int, val serie: Int, val serieInWochen: Boolean)`
  - `object AktivitaetenZusammenstellung { fun zeilen(aktivitaeten: List<Aktivitaet>, historien: Map<Long, List<Faelligkeit>>, heute: LocalDate, serienRechner: SerienRechner): List<AktivitaetZeile> }`
  - `AktivitaetenViewModel(aktivitaeten: AktivitaetRepository, faelligkeiten: FaelligkeitRepository, neuPlanen: AktivitaetNeuPlanen, serienRechner: SerienRechner, uhr: Clock)` mit `zeilen: StateFlow<List<AktivitaetZeile>>`, `toast: StateFlow<Hinweistoast?>`, `archivieren(id: Long): Job`, `gespeichertMelden(ergebnis: SpeicherErgebnis)`
  - Toasttexte: `Aktivität angelegt`, `Änderungen gespeichert`, `<Name> archiviert`; Anzeigedauer 3 800 ms (wie Heute)

**Verhaltensentscheidungen:**
- Die Serie wird wie in der Statistik bestimmt: `SerienRechner.aktuelleSerie(historie, heute)`. Bei einem Wochenziel zählt sie Wochen, sonst Tage (`serieInWochen`).
- `zeilen` wird neu berechnet, wenn sich die aktive Liste **oder** die Fälligkeiten von heute ändern. `beobachteTag(heute)` dient dabei nur als Auslöser, denn Abhaken auf „Heute“ ändert eine heutige Fälligkeit, und die Serie in der Liste soll mitziehen. Ein Datumswechsel bei offener Liste wird nicht nachgezogen, denn die Liste lebt nur, solange der Tab sichtbar ist (`WhileSubscribed`).
- Die Berechnung läuft auf `Dispatchers.Default` (volle Historie je Aktivität, wie `StatistikViewModel`). Darum wird `zeilen` im ViewModel-Test nicht geprüft. Die Logik steckt vollständig in der reinen `AktivitaetenZusammenstellung` und wird dort getestet.
- Der Toast ist ein eigener `StateFlow` und nicht Teil der `zeilen`-Kette. Gleiches Muster wie `HeuteViewModel._toast`: synchron gesetzt und im Test deterministisch.

- [ ] **Step 1: Failing test für die Zusammenstellung**

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class AktivitaetenZusammenstellungTest {

    // Montag
    private val heute = LocalDate.of(2026, 3, 2)

    private fun aktivitaet(id: Long, name: String, zeitplan: Zeitplan, symbol: String = "pill") = Aktivitaet(
        id = id, name = name, farbe = 1, symbol = symbol, basisPunkte = 5,
        zeitplan = zeitplan, erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private fun tag(aktivitaetId: Long, tag: LocalDate, status: FaelligkeitStatus) = Faelligkeit(
        aktivitaetId = aktivitaetId, periodeVon = tag, periodeBis = tag,
        geplantFuer = tag.atTime(8, 0), sollAnzahl = 1, status = status,
    )

    private fun woche(aktivitaetId: Long, montag: LocalDate, status: FaelligkeitStatus) = Faelligkeit(
        aktivitaetId = aktivitaetId, periodeVon = montag, periodeBis = montag.plusDays(6),
        geplantFuer = null, sollAnzahl = 3, status = status,
    )

    private val tabletten = aktivitaet(
        1, "Tabletten", Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
    )
    private val sport = aktivitaet(2, "Sport", Zeitplan.Wochenziel(3), symbol = "fitness_center")

    @Test
    fun `eine Zeile traegt Name, Symbol, Plantext und Punkte`() {
        val zeile = AktivitaetenZusammenstellung.zeilen(listOf(tabletten), emptyMap(), heute, SerienRechner()).single()

        assertEquals(
            AktivitaetZeile(
                id = 1, name = "Tabletten", symbol = "pill", planText = "Täglich · 08:00",
                basisPunkte = 5, serie = 0, serieInWochen = false,
            ),
            zeile,
        )
    }

    @Test
    fun `die Serie zaehlt abgeschlossene Tage bis zum letzten Verpassen`() {
        val historie = listOf(
            tag(1, heute.minusDays(3), FaelligkeitStatus.VERPASST),
            tag(1, heute.minusDays(2), FaelligkeitStatus.ERLEDIGT),
            tag(1, heute.minusDays(1), FaelligkeitStatus.ERLEDIGT),
            tag(1, heute, FaelligkeitStatus.OFFEN),
        )

        val zeile = AktivitaetenZusammenstellung.zeilen(
            listOf(tabletten), mapOf(1L to historie), heute, SerienRechner(),
        ).single()

        assertEquals(2, zeile.serie)
    }

    @Test
    fun `ein Wochenziel zaehlt seine Serie in Wochen`() {
        val historie = listOf(
            woche(2, heute.minusWeeks(2), FaelligkeitStatus.ERLEDIGT),
            woche(2, heute.minusWeeks(1), FaelligkeitStatus.ERLEDIGT),
            woche(2, heute, FaelligkeitStatus.OFFEN),
        )

        val zeile = AktivitaetenZusammenstellung.zeilen(
            listOf(sport), mapOf(2L to historie), heute, SerienRechner(),
        ).single()

        assertEquals(2, zeile.serie)
        assertEquals(true, zeile.serieInWochen)
        assertEquals("3× pro Woche", zeile.planText)
    }

    @Test
    fun `die Reihenfolge der Eingabe bleibt erhalten`() {
        val zeilen = AktivitaetenZusammenstellung.zeilen(listOf(sport, tabletten), emptyMap(), heute, SerienRechner())

        assertEquals(listOf("Sport", "Tabletten"), zeilen.map { it.name })
    }
}
```

- [ ] **Step 2: Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetenZusammenstellungTest'`
Expected: FAIL, `Unresolved reference: AktivitaetenZusammenstellung`.

- [ ] **Step 3: Implementierung**

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import java.time.LocalDate

/** Eine Zeile der Aktivitaeten-Liste (Prototyp actRows). */
data class AktivitaetZeile(
    val id: Long,
    val name: String,
    val symbol: String,
    val planText: String,
    val basisPunkte: Int,
    val serie: Int,
    /** Wochenziele zaehlen ihre Serie in Wochen, alle anderen Zeitplaene in Tagen. */
    val serieInWochen: Boolean,
)

/** Rein und zustandslos, damit die Zeilenbildung ohne ViewModel/Dispatcher testbar ist --
 * gleiches Muster wie HeuteZusammenstellung. */
object AktivitaetenZusammenstellung {

    fun zeilen(
        aktivitaeten: List<Aktivitaet>,
        historien: Map<Long, List<Faelligkeit>>,
        heute: LocalDate,
        serienRechner: SerienRechner,
    ): List<AktivitaetZeile> = aktivitaeten.map { aktivitaet ->
        AktivitaetZeile(
            id = aktivitaet.id,
            name = aktivitaet.name,
            symbol = aktivitaet.symbol,
            planText = PlanText.aus(aktivitaet.zeitplan),
            basisPunkte = aktivitaet.basisPunkte,
            // Gleiche Berechnung wie StatistikRechner.fuerAktivitaet: heute als obere
            // Grenze; die laufende Periode zaehlt erst, wenn nichts mehr offen ist.
            serie = serienRechner.aktuelleSerie(historien[aktivitaet.id].orEmpty(), heute),
            serieInWochen = aktivitaet.zeitplan is Zeitplan.Wochenziel,
        )
    }
}
```

- [ ] **Step 4: Test grün sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetenZusammenstellungTest'`
Expected: PASS (4 Tests).

- [ ] **Step 5: Failing test für die ViewModel-Toasts**

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import de.vanhake.dayorganizer.domainuse.AktivitaetNeuPlanen
import de.vanhake.dayorganizer.domainuse.FaelligkeitenMaterialisieren
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AktivitaetenViewModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val heute = LocalDate.of(2026, 3, 2)
    private val uhr = Clock.fixed(LocalDateTime.of(heute, LocalTime.NOON).atZone(zone).toInstant(), zone)
    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private val tabletten = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 1, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private fun viewModel(aktivitaeten: FakeAktivitaetRepository = FakeAktivitaetRepository()): AktivitaetenViewModel {
        val faelligkeiten = FakeFaelligkeitRepository()
        val erinnerungen = FakeErinnerungRepository()
        val materialisieren = FaelligkeitenMaterialisieren(
            aktivitaeten, faelligkeiten, erinnerungen, ZeitplanRechner(), uhr,
        )
        val neuPlanen = AktivitaetNeuPlanen(
            faelligkeiten, erinnerungen, materialisieren, uhr,
            alarmNeuPlanen = {},
            widgetAnstossen = {},
        )
        return AktivitaetenViewModel(aktivitaeten, faelligkeiten, neuPlanen, SerienRechner(), uhr)
    }

    @Test
    fun `nach dem Anlegen erscheint ein Toast ohne Rueckgaengig`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.gespeichertMelden(SpeicherErgebnis.ANGELEGT)
        testScheduler.runCurrent()

        assertEquals(Hinweistoast("Aktivität angelegt", kannRueckgaengig = false), vm.toast.value)
    }

    @Test
    fun `nach dem Aendern erscheint der Geaendert-Toast`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.gespeichertMelden(SpeicherErgebnis.GEAENDERT)
        testScheduler.runCurrent()

        assertEquals("Änderungen gespeichert", vm.toast.value?.text)
    }

    @Test
    fun `der Toast verschwindet nach der Anzeigedauer`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.gespeichertMelden(SpeicherErgebnis.ANGELEGT)
        testScheduler.runCurrent()

        testScheduler.advanceTimeBy(3_801)
        testScheduler.runCurrent()

        assertNull(vm.toast.value)
    }

    @Test
    fun `ein neuer Toast ersetzt den alten samt dessen Ablauf`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.gespeichertMelden(SpeicherErgebnis.ANGELEGT)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(3_000)

        vm.gespeichertMelden(SpeicherErgebnis.GEAENDERT)
        testScheduler.runCurrent()
        // Der erste Toast waere jetzt abgelaufen -- sein Ablauf darf den zweiten nicht loeschen.
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()

        assertEquals("Änderungen gespeichert", vm.toast.value?.text)
    }

    @Test
    fun `Archivieren archiviert und meldet den Namen`() = runTest(dispatcher) {
        val repo = FakeAktivitaetRepository(listOf(tabletten))
        val vm = viewModel(repo)

        vm.archivieren(1L).join()

        assertEquals(heute, repo.stand.value.single().archiviertAm)
        assertEquals(Hinweistoast("Tabletten archiviert", kannRueckgaengig = false), vm.toast.value)
    }
}
```

Hinweis: Die Konstruktor-Reihenfolge von `FaelligkeitenMaterialisieren` und `AktivitaetNeuPlanen` ist aus `AktivitaetBearbeitenViewModelTest` übernommen. Falls sie dort inzwischen anders lautet, gilt die dortige Fassung.

- [ ] **Step 6: Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetenViewModelTest'`
Expected: FAIL beim Kompilieren (Konstruktor mit 5 Parametern unbekannt, `gespeichertMelden`/`toast` unbekannt).

- [ ] **Step 7: ViewModel ersetzen und alten Screen minimal nachziehen**

`AktivitaetenViewModel.kt` vollständig ersetzen:

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domainuse.AktivitaetNeuPlanen
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AktivitaetenViewModel @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val neuPlanen: AktivitaetNeuPlanen,
    private val serienRechner: SerienRechner,
    private val uhr: Clock,
) : ViewModel() {

    // beobachteTag(heute) liefert nur den Ausloeser: wer auf "Heute" abhakt, aendert eine
    // heutige Faelligkeit, und die Serie in der Liste soll mitziehen. Die Historie selbst
    // kommt je Aktivitaet aus historie(). mapLatest verwirft eine noch laufende Berechnung,
    // wenn schon die naechste Emission da ist. flowOn(Default) aus demselben Grund wie im
    // StatistikViewModel: SerienRechner laeuft ueber die volle Historie.
    @OptIn(ExperimentalCoroutinesApi::class)
    val zeilen: StateFlow<List<AktivitaetZeile>> = combine(
        aktivitaeten.beobachteAktive(),
        faelligkeiten.beobachteTag(LocalDate.now(uhr)),
    ) { liste, _ -> liste }
        .mapLatest { liste ->
            val historien = liste.associate { it.id to faelligkeiten.historie(it.id) }
            AktivitaetenZusammenstellung.zeilen(liste, historien, LocalDate.now(uhr), serienRechner)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Eigener Flow statt Teil der zeilen-Kette -- gleiches Muster wie HeuteViewModel._toast.
    private val _toast = MutableStateFlow<Hinweistoast?>(null)
    val toast: StateFlow<Hinweistoast?> = _toast.asStateFlow()
    private var toastJob: Job? = null

    // Ohne AktivitaetNeuPlanen liefen die schon materialisierten kuenftigen Faelligkeiten
    // und ihre Erinnerungen der archivierten Aktivitaet einfach weiter (Finding 1 der
    // Branch-Review): Erinnerungen feuerten fuer etwas, das nirgends mehr sichtbar ist,
    // und die Faelligkeiten wurden am naechsten Mitternachtslauf faelschlich als
    // VERPASST geschlossen. FaelligkeitenMaterialisieren liest nur aktive, nicht
    // archivierte Aktivitaeten (AktivitaetDao.alleAktiven: "aktiv = 1 AND archiviertAm
    // IS NULL"), darum legt der Aufruf hier fuer die gerade archivierte Aktivitaet
    // nichts Neues an -- er raeumt nur auf.
    //
    // Kein Rueckgaengig (Entscheidung in Plan 5): der Toast bestaetigt nur.
    fun archivieren(id: Long) = viewModelScope.launch {
        val name = aktivitaeten.nachId(id)?.name
        aktivitaeten.archivieren(id, LocalDate.now(uhr))
        neuPlanen(id)
        name?.let { zeigeToast(AktivitaetenToastTexte.archiviert(it)) }
    }

    /** Vom Screen aufgerufen, wenn der Bearbeiten-Bildschirm mit einem Ergebnis
     * zurueckgekehrt ist (Navigation reicht es ueber den savedStateHandle durch). */
    fun gespeichertMelden(ergebnis: SpeicherErgebnis) {
        zeigeToast(
            when (ergebnis) {
                SpeicherErgebnis.ANGELEGT -> AktivitaetenToastTexte.ANGELEGT
                SpeicherErgebnis.GEAENDERT -> AktivitaetenToastTexte.GEAENDERT
            },
        )
    }

    private fun zeigeToast(text: String) {
        toastJob?.cancel()
        _toast.value = Hinweistoast(text, kannRueckgaengig = false)
        toastJob = viewModelScope.launch {
            delay(TOAST_DAUER_MS)
            _toast.value = null
        }
    }

    private companion object {
        /** Wie HeuteViewModel. */
        const val TOAST_DAUER_MS = 3_800L
    }
}

/** Reine Textbausteine, ohne Android-Ressourcen testbar -- Muster HeuteToastTexte. */
private object AktivitaetenToastTexte {
    const val ANGELEGT = "Aktivität angelegt"
    const val GEAENDERT = "Änderungen gespeichert"
    fun archiviert(name: String) = "$name archiviert"
}
```

In `AktivitaetenScreen.kt` (alter Stand, wird in Task 4 ersetzt) nur so viel ändern, dass es kompiliert: `val liste by viewModel.liste.collectAsStateWithLifecycle()` wird zu `val liste by viewModel.zeilen.collectAsStateWithLifecycle()`. `AktivitaetZeile` hat dieselben Feldnamen `id`, `name`, `basisPunkte`, die der alte Screen liest, der Rest bleibt unverändert.

Prüfe außerdem, dass `SerienRechner` per Hilt injizierbar ist: `DatenModul` stellt ihn mit `@Provides @Singleton fun serienRechner(): SerienRechner` bereit (bei Planerstellung vorhanden).

- [ ] **Step 8: Tests und Build**

Run: `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL. `AktivitaetenViewModelTest` (5 Tests) und `AktivitaetenZusammenstellungTest` (4 Tests) grün.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenZusammenstellung.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenViewModel.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenScreen.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenZusammenstellungTest.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenViewModelTest.kt
git commit -m "feat(aktivitaeten): Listenzeilen mit Plantext und Serie, Toasts im ViewModel

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Aktivitäten-Liste im neuen Design und Speichern-Toast über die Navigation

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenScreen.kt` (vollständig ersetzen)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `AktivitaetenViewModel.zeilen`, `.toast`, `.archivieren(id)`, `.gespeichertMelden(ergebnis)` (Task 3); `AktivitaetZeile` (Task 3); `SpeicherErgebnis` (Task 2); `HinweistoastLeiste(toast, rueckgaengig, modifier)` (Plan 4); `DayOrganizerFormen.listenKarte`, `.feld` (Task 2).
- Produces: `AktivitaetenScreen(bearbeiten: (Long) -> Unit, gespeichert: SpeicherErgebnis?, gespeichertGezeigt: () -> Unit, viewModel = hiltViewModel())`; `Ziele.ERGEBNIS_AKTIVITAET_GESPEICHERT = "aktivitaet_gespeichert"`.

**Verhaltensentscheidungen:**
- Ergebnisübergabe: Der Bearbeiten-Eintrag schreibt `SpeicherErgebnis.name` in `navController.previousBackStackEntry?.savedStateHandle`, die Liste liest es aus dem `savedStateHandle` **ihres eigenen** `NavBackStackEntry`. Das ist nicht derselbe Handle wie der `SavedStateHandle` eines `hiltViewModel()`. Deshalb läuft der Wert über Navigation → Screen-Parameter → `viewModel.gespeichertMelden(...)`, und der Screen setzt den Schlüssel nach dem Melden auf `null`, damit der Toast bei einer Rekomposition oder nach Rotation nicht erneut erscheint.
- FAB „Neue Aktivität“ wird ausgeblendet, solange ein Toast steht (Prototyp `showFab: … && !st.toast`), damit beide nicht übereinanderliegen.
- Leerer Zustand: Der Prototyp zeigt keinen, die echte App startet aber leer. Dafür gibt es eine Textzeile unter der Überschrift, und der Zähler zeigt „0 aktiv“.
- Archivieren nutzt `Icons.Outlined.Archive` (Prototyp-Symbol `archive`, ungefüllt).

- [ ] **Step 1: Strings ergänzen**

In `app/src/main/res/values/strings.xml`:
- `punkte_je_erledigung` **entfernen** (einziger Nutzer war der alte Listen-Screen; vorher mit `grep -rn punkte_je_erledigung app/src` bestätigen, dass nur `AktivitaetenScreen.kt` sie nutzt).
- Ergänzen:

```xml
    <string name="anzahl_aktiv">%1$d aktiv</string>
    <string name="punkte_kurz">%1$d Pkt</string>
    <string name="aktivitaeten_leer">Noch keine Aktivitäten. Lege mit „Neue Aktivität“ die erste an.</string>
    <plurals name="serie_tage">
        <item quantity="one">%1$d Tag</item>
        <item quantity="other">%1$d Tage</item>
    </plurals>
    <plurals name="serie_wochen">
        <item quantity="one">%1$d Woche</item>
        <item quantity="other">%1$d Wochen</item>
    </plurals>
```

- [ ] **Step 2: `AktivitaetenScreen.kt` vollständig ersetzen**

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.gemeinsam.HinweistoastLeiste
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import de.vanhake.dayorganizer.ui.theme.aktivitaetSymbol

@Composable
fun AktivitaetenScreen(
    bearbeiten: (Long) -> Unit,
    gespeichert: SpeicherErgebnis?,
    gespeichertGezeigt: () -> Unit,
    viewModel: AktivitaetenViewModel = hiltViewModel(),
) {
    val zeilen by viewModel.zeilen.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val farben = DayOrganizerTheme.farben

    // Das Ergebnis kommt einmalig aus dem savedStateHandle des Back-Stack-Eintrags;
    // gespeichertGezeigt() loescht es dort, sonst kaeme der Toast nach jeder Rotation wieder.
    LaunchedEffect(gespeichert) {
        gespeichert?.let {
            viewModel.gespeichertMelden(it)
            gespeichertGezeigt()
        }
    }

    Box(Modifier.fillMaxSize().background(farben.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        stringResource(R.string.tab_aktivitaeten),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.02).em,
                        color = farben.ink,
                    )
                    Text(
                        stringResource(R.string.anzahl_aktiv, zeilen.size),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = farben.ink2,
                    )
                }
            }

            if (zeilen.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.aktivitaeten_leer),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        fontSize = 15.sp,
                        color = farben.ink2,
                    )
                }
            }

            items(zeilen, key = { it.id }) { zeile ->
                AktivitaetZeileKarte(
                    zeile = zeile,
                    bearbeiten = { bearbeiten(zeile.id) },
                    archivieren = { viewModel.archivieren(zeile.id) },
                )
            }
        }

        if (toast == null) {
            ExtendedFloatingActionButton(
                onClick = { bearbeiten(0L) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.neue_aktivitaet), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) },
                shape = DayOrganizerFormen.feld,
                containerColor = farben.accent,
                contentColor = farben.onAccent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }

        toast?.let { aktuellerToast ->
            HinweistoastLeiste(
                toast = aktuellerToast,
                // Listen-Toasts tragen nie Rueckgaengig (kannRueckgaengig = false), der
                // Knopf wird also gar nicht gezeigt.
                rueckgaengig = {},
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

@Composable
private fun AktivitaetZeileKarte(zeile: AktivitaetZeile, bearbeiten: () -> Unit, archivieren: () -> Unit) {
    val farben = DayOrganizerTheme.farben
    Surface(
        onClick = bearbeiten,
        shape = DayOrganizerFormen.listenKarte,
        color = farben.surface,
        contentColor = farben.ink,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = DayOrganizerFormen.knopf,
                color = farben.accentSoft,
                contentColor = farben.onAccentSoft,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(aktivitaetSymbol(zeile.symbol), contentDescription = null, modifier = Modifier.size(26.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    zeile.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(zeile.planText, fontSize = 13.sp, lineHeight = 17.sp, color = farben.ink2)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Plakette(
                        symbol = Icons.Filled.Star,
                        text = stringResource(R.string.punkte_kurz, zeile.basisPunkte),
                        flaeche = farben.goldSoft,
                        farbe = farben.goldInk,
                    )
                    Plakette(
                        symbol = Icons.Filled.LocalFireDepartment,
                        text = if (zeile.serieInWochen) {
                            pluralStringResource(R.plurals.serie_wochen, zeile.serie, zeile.serie)
                        } else {
                            pluralStringResource(R.plurals.serie_tage, zeile.serie, zeile.serie)
                        },
                        flaeche = farben.coralSoft,
                        farbe = farben.coral,
                    )
                }
            }
            IconButton(onClick = archivieren) {
                Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.archivieren), tint = farben.ink2)
            }
        }
    }
}

@Composable
private fun Plakette(symbol: ImageVector, text: String, flaeche: Color, farbe: Color) {
    Row(
        modifier = Modifier
            .clip(DayOrganizerFormen.pille)
            .background(flaeche)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(symbol, contentDescription = null, tint = farbe, modifier = Modifier.size(14.dp))
        Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = farbe)
    }
}
```

- [ ] **Step 3: Navigation verdrahten**

In `app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt`:

1. In `object Ziele` ergänzen:

```kotlin
    /** savedStateHandle-Schluessel: Bearbeiten -> Liste, Wert = SpeicherErgebnis.name. */
    const val ERGEBNIS_AKTIVITAET_GESPEICHERT = "aktivitaet_gespeichert"
```

2. Imports ergänzen: `androidx.lifecycle.compose.collectAsStateWithLifecycle`, `de.vanhake.dayorganizer.ui.aktivitaeten.SpeicherErgebnis`.

3. Den `AKTIVITAETEN`-Eintrag ersetzen:

```kotlin
            composable(Ziele.AKTIVITAETEN) { eintrag ->
                val gespeichert by eintrag.savedStateHandle
                    .getStateFlow<String?>(Ziele.ERGEBNIS_AKTIVITAET_GESPEICHERT, null)
                    .collectAsStateWithLifecycle()
                AktivitaetenScreen(
                    bearbeiten = { id -> navController.navigate(Ziele.aktivitaetBearbeiten(id)) },
                    gespeichert = gespeichert?.let(SpeicherErgebnis::valueOf),
                    gespeichertGezeigt = {
                        eintrag.savedStateHandle[Ziele.ERGEBNIS_AKTIVITAET_GESPEICHERT] = null
                    },
                )
            }
```

4. Den `AKTIVITAET_BEARBEITEN`-Eintrag (Zwischenstand aus Task 2) ersetzen:

```kotlin
            composable(Ziele.AKTIVITAET_BEARBEITEN) { eintrag ->
                AktivitaetBearbeitenScreen(
                    id = eintrag.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    fertig = { ergebnis ->
                        navController.previousBackStackEntry?.savedStateHandle
                            ?.set(Ziele.ERGEBNIS_AKTIVITAET_GESPEICHERT, ergebnis.name)
                        navController.popBackStack()
                    },
                    zurueck = { navController.popBackStack() },
                )
            }
```

`SavedStateHandle.set(key, null)` mit `String?` ist zulässig. `getStateFlow<String?>` liefert danach wieder `null`, und der `LaunchedEffect` im Screen tut nichts.

- [ ] **Step 4: Tests und Build**

Run: `./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

Run: `grep -rn "punkte_je_erledigung\|viewModel.liste" app/src`
Expected: keine Treffer.

- [ ] **Step 5: Sichtprüfung im Emulator (manuell, nicht blockierend für CI)**

Emulator wie in `docs/HANDOFF.md` starten, `./gradlew :app:installDebug`, dann prüfen, hell und dunkel (Umschalter unter Einstellungen):
1. Tab „Aktivitäten“: Überschrift, „N aktiv“, Zeilen mit Symbolkachel, Plantext, „X Pkt“ und Serie, Archiv-Symbol und unten rechts der FAB „Neue Aktivität“.
2. FAB → Bearbeiten: Symbolraster (2 Reihen à 6), Name, Punkte-Stepper, Zeitplankarten 2×2, Wochentage, Uhrzeit-Chip „08:00“ und „Uhrzeit +“.
3. Name leer lassen → Speichern → roter Rahmen und Hinweis. Namen tippen → der Hinweis verschwindet sofort.
4. „Intervall“ wählen → Karte mit Stepper (bei 12 ist Plus deaktiviert), Zeitfenster-Knöpfe öffnen den Zeitwähler, die Zusammenfassung ändert sich live.
5. Speichern → zurück in der Liste, Toast „Aktivität angelegt“, der FAB ist während des Toasts ausgeblendet.
6. Gerät drehen, solange der Toast steht → kein zweiter Toast nach Ablauf.
7. Archiv-Symbol → Toast „<Name> archiviert“, die Zeile verschwindet.

Bedienung per `adb shell uiautomator dump` + Tippen auf die `bounds`-Mitte (siehe HANDOFF, nicht blind nach Koordinaten tippen). Bildschirmfotos mit `adb exec-out screencap -p > …` im Scratchpad ablegen und im Abschlussbericht des Tasks erwähnen. Ist kein Emulator verfügbar (z. B. in einer Cloud-Sitzung), wird dieser Step mit Begründung übersprungen und im Bericht vermerkt.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenScreen.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(aktivitaeten): Liste im neuen Design, Speichern-Toast ueber die Navigation

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Abschluss dieses Plans

Nach Task 4 sind Aktivitäten-Liste und Bearbeiten-Bildschirm auf dem Stand des Prototyps, mit diesen bewussten Abweichungen:
- kein Rückgängig beim Archivieren (Abstimmung zu Plan 5)
- durchgezogener statt gestrichelter Rahmen am „Uhrzeit hinzufügen“-Chip
- Zeitwähler-Dialog statt Inline-`<input type="time">`
- leerer Zustand ergänzt

Offen für die Folgepläne:

- **Plan 6:** Belohnungen + Statistik + Optionen. Der segmentierte Hell/Dunkel-Schalter ersetzt den provisorischen aus Plan 4, dazu Backup-Karten und Bonus-/Kulanz-Stepper (nutzt `Stepper`/`StepperZeile` aus diesem Plan). Die untere Navigationsleiste im Prototyp-Stil (Pillen-Indikator, Symbole `check_circle`/`format_list_bulleted`/`redeem`/`insights`/`tune`) ist bisher in keinem Plan enthalten und sollte dort mit hinein.
- **Plan 7:** Homescreen-Widget im neuen Design (Glance-Einschränkungen beachten: kein CSS-Farbverlauf, kein `backdrop-filter`).
- **Folgearbeit aus dem Plan-4-Review (nicht blockierend):** ein Test, der die SQL- und die Fake-Implementierung der Bonus-Dedupe-Abfragen (`BuchungRepository.serienbonusSchwellenSeit`, `tagesbonusGebuchtAm`) gegeneinander absichert.
- **Redaktionell:** `README.md` (Abschnitt „Stand“, Dokumentationstabelle) um Plan 4 und 5 ergänzen. `docs/HANDOFF.md` steht noch auf dem Stand von Plan 3 und sollte aktualisiert werden.
