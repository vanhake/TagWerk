# DayOrganizer Plan 9: Aufräumen nach dem Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Backlog aus `docs/HANDOFF.md` („Offene Punkte“ und „Neu aus Plan 8“) wird abgearbeitet. Archivieren bekommt Rückfrage, Rückgängig und einen Archivbereich. Toasts stapeln sich, statt ein offenes Rückgängig zu verdrängen. Editoren, Import und Widget stürzen bei Fehlern nicht mehr ab und schreiben keine falschen Werte. Das Widget zeichnet zur geplanten Uhrzeit und um Mitternacht neu. Die Statistik zeigt archivierte Aktivitäten und ist mit TalkBack lesbar.

**Architecture:**
- **`ToastStapel`** (`ui/gemeinsam`) ist eine kleine Klasse mit eigenem `CoroutineScope`. Sie ersetzt in Heute, Aktivitäten und Belohnungen das Trio `_toast`, `toastJob` und `ausstehendesRueckgaengig`. Rückgängig-Einträge stapeln sich, Info-Einträge warten.
- **Archiv:** Je Repository kommen `beobachteArchivierte()` und `wiederherstellen(id)` dazu, nur neue Abfragen. Datenbank bleibt Version 5. Rückgängig nach dem Archivieren ist dasselbe `wiederherstellen`.
- **Fehler:** Überall `versuchen {}` aus `util`. `Repository.speichern` wirft bei fehlendem Datensatz `DatensatzFehlt` statt `IllegalStateException`. Die Editoren zeigen das als Toast und bleiben offen.
- **Widget-Takt:** Eine reine Funktion `WidgetTakt.naechsterUmschaltpunkt` liefert den nächsten Zeitpunkt, an dem sich die Darstellung ändert (nächste geplante Uhrzeit eines offenen Eintrags oder Mitternacht). `WidgetWecker` stellt dafür einen ungenauen `RTC`-Alarm, ohne neue Berechtigung. Der Widget-Tipp läuft über eine testbare Klasse `WidgetAbhaken`. Sie lehnt Einträge eines anderen Tages ab.

**Tech Stack:** Kotlin, Compose M3, Room (Version 5, unverändert), Hilt, DataStore, Glance `1.1.1`, Robolectric `4.14.1`, Turbine, kotlinx-coroutines-test. Keine neuen Abhängigkeiten.

**Spec:**
- `docs/superpowers/specs/2026-09-18-dayorganizer-design.md` (§3.1 Archivieren erhält die Historie, Widget-Abschnitt).
- `docs/HANDOFF.md`, Abschnitt „Offene Punkte“ samt „Neu aus Plan 8“ (Stand nach Merge von PR #5, `bf0af24`).
- Abgestimmte Entscheidungen (16.10.2026):
  1. **Archivieren:** Rückfrage-Dialog **und** Rückgängig im Toast **und** ein einklappbarer Bereich „Archiviert (n)“ mit „Wiederherstellen“, in Aktivitäten und Belohnungen.
  2. **Toasts stapeln:** Rückgängig nimmt die jüngste Aktion zurück. Danach erscheint der vorige Rückgängig-Toast wieder, solange seine eigene Zeit noch läuft. Info-Toasts verdrängen kein offenes Rückgängig, sie warten.
  3. **Import:** Der Import läuft unter derselben Schreibsperre wie die Stepper. Stepper und Farbmodus sind währenddessen deaktiviert, danach wird neu geladen.
  4. **Editor-Fehler:** Der Editor bleibt offen und der Entwurf bleibt erhalten. Ein Toast zeigt „Speichern fehlgeschlagen“ oder „gibt es nicht mehr“.
  5. **Widget:** eigener ungenauer Wecker für den nächsten Umschaltpunkt. Ein Tipp auf einen Eintrag von einem anderen Tag bucht nicht, er zeichnet nur neu.
  6. **Widget-Extras:** `previewLayout`, eigenes Fehlerlayout, Leerhinweis und 200-%-Schrift im Höhenbudget.
  7. **TalkBack Statistik:** Die Heatmap bekommt eine Zusammenfassung und je Woche eine vorlesbare Spalte. „–“ wird als „noch keine Quote“ vorgelesen.
  8. **Robustheit:** Ein kaputter Belohnungsdatensatz wird übersprungen statt zu werfen. Der Buchungspfad liest die Einstellungen streng und wirft. Alle Fangstellen nutzen einheitlich `versuchen {}`.
  9. **Statistik:** Archivierte Aktivitäten mit Fälligkeiten im Zeitraum erscheinen bei Quoten und Serien, gekennzeichnet und nach den aktiven.
  10. **Emulator:** Die Sichtprüfung darf Magnesium, Rücken, Tabletten und Vitamin über den neuen Archivbereich wiederherstellen. Das ist ausdrücklich freigegeben. Alles andere Vorhandene bleibt tabu.
- **Nicht in Plan 9** (bleibt im HANDOFF-Backlog): Dateidialog springt auf „Heute“, feste Uhrzeit schon vorbei, Testhygiene (`org.junit.Test`/`kotlin.test`, Drawable-Test, `self_improvement`), doppelte `TextStyle`-Blöcke im Widget, die beiden Testbuchungen im Emulator.

## Global Constraints

- Minimale API-Version 33, Ziel-API 35. Keine neue Abhängigkeit, keine neue Berechtigung (der Widget-Wecker nutzt `AlarmManager.setWindow` mit `RTC`, das braucht keine).
- **Keine Änderung an Room-Schema oder Backup-Schema.** Datenbank Version 5, Backup-Schema 3. Nur neue `@Query`-Methoden.
- Deutsche Bezeichner. In Kotlin-Kommentaren werden Umlaute ausgeschrieben (`ae`, `oe`, `ue`). `strings.xml` und Kotlin-String-Literale behalten echte Umlaute.
- Sichtbare Texte stehen in `strings.xml`. **Ausnahme wie bisher:** Toast-Texte der ViewModels stehen als Konstanten in den privaten `…ToastTexte`-Objekten, damit JVM-Tests sie ohne Context prüfen können.
- Fangstellen nur noch über `versuchen {}` (`de.vanhake.dayorganizer.util`). Kein `runCatching` und kein `catch (Exception)` mehr in `app/src/main`. Einzige Ausnahmen sind `ZeitplanKodierung` (fängt gezielt Parse-Ausnahmen) und `BackupUmwandlung`/`EinstellungenSpeicher` beim `ThemeModus.valueOf` (reines Parsen, keine Koroutine).
- Fakes in `Fakes.kt` bilden die SQL **exakt** nach, Sortierung eingeschlossen. Bei jeder neuen DAO-Methode den Fake mit der SQL vergleichen.
- Testnamen in Backticks als deutsche Sätze. Ausnahme: `DaoTest` und `MigrationTest` nutzen `unterstrich_namen`.
- Zwei-Konstruktoren-Muster: primärer Konstruktor mit Funktionen, genau ein `@Inject`-Konstruktor.
- Tests mit Turbine auf einen per `flowOn` berechneten Zustand geben dem ViewModel den Test-Dispatcher (`rechenDispatcher`).
- Toastdauer bleibt **3 800 ms** (`ToastStapel.TOAST_DAUER_MS`).
- Es gibt keine Compose- oder Glance-UI-Tests. Logik gehört in reine Funktionen und ViewModels mit JVM-Tests, Ressourcen werden mit Robolectric geprüft. `:app:assembleDebug` zeigt, dass die Oberfläche baut.
- Nach jedem Task muss `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` grün sein, bevor committet wird. Nach dem Commit mit `git log -1 --oneline` prüfen, dass er entstanden ist.
- **Bricht ein Fix bestehende Tests, ist die Anpassung reviewpflichtig.** Danach erneut mutieren, um zu zeigen, dass der Test noch bewacht, was er bewacht hat.
- Commit-Nachrichten: Conventional Commits, Ende `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Emulator nur mit eigenen Testdaten (Präfix „Plan9-Test“). Ausnahme ist Entscheidung 10. Vor jedem Tipp per `uiautomator dump` den Knoten prüfen. Verstellte Optionen am Ende zurücksetzen. Nie ein Backup speichern oder importieren.

## Review Focus

1. **Schnell nacheinander abhaken:** Drei Einträge in zwei Sekunden auf Heute abhaken. Dann dreimal Rückgängig. Jeder Tipp nimmt genau die jüngste noch offene Aktion zurück, keiner bucht doppelt, und ein abgelaufener Toast taucht nicht wieder auf. → Tests in Task 1 (Stapel) und Task 1 Step 6 (HeuteViewModel).
2. **Archivieren und sofort Rückgängig:** Die Aktivität kommt mit ihren Erinnerungen zurück: `neuPlanen(id)` läuft nach dem Wiederherstellen. Ein Doppeltipp auf „Wiederherstellen“ wirft nicht. → Tests in Task 2.
3. **Nach Mitternacht aufs Widget tippen, bevor es neu gezeichnet hat:** Es wird nichts gebucht, das Widget zeichnet neu. Ein Wochenziel derselben Woche wird dagegen weiter gebucht. → Tests in Task 6.
4. **Import, während ein Stepper-Schreibvorgang hängt:** Die importierten Einstellungen gewinnen. Ein Tipp während des Imports wird ignoriert. → Tests in Task 5.
5. **Kaputter Datensatz oder E/A-Fehler beim Abhaken:** Heute bleibt bedienbar. Die Fälligkeit bleibt OFFEN, ohne Buchung, und ein Toast meldet den Fehlschlag. Ein kaputter Belohnungsdatensatz fehlt nur im Shop. → Tests in Task 4.

---

## Datei-Übersicht

| Datei | Zweck |
|---|---|
| `app/.../ui/gemeinsam/ToastStapel.kt` (neu) | Stapel aus Rückgängig- und Info-Toasts |
| `app/.../ui/gemeinsam/ArchivBausteine.kt` (neu) | `ArchivierenDialog`, `ArchivBereich`, `ArchivEintrag` |
| `app/.../ui/heute/HeuteViewModel.kt` | Stapel, Fehlertoasts beim Abhaken |
| `app/.../ui/aktivitaeten/AktivitaetenViewModel.kt`, `…Screen.kt` | Stapel, Rückgängig, Archivbereich, Dialog |
| `app/.../ui/belohnungen/BelohnungenViewModel.kt`, `…Screen.kt` | Stapel, Rückgängig, Archivbereich, Dialog |
| `app/.../ui/aktivitaeten/AktivitaetBearbeitenViewModel.kt`, `…Screen.kt` | Speicherfehler als Toast |
| `app/.../ui/belohnungen/BelohnungBearbeitenViewModel.kt`, `…Screen.kt` | Speicherfehler als Toast |
| `app/.../ui/gemeinsam/SpeicherErgebnis.kt` | zusätzlich `SpeicherFehler` |
| `app/.../data/repository/Repositories.kt`, `RaumRepositories.kt`, `BelohnungRepository.kt` | `DatensatzFehlt`, `wiederherstellen`, `beobachteArchivierte`, `alle()` |
| `app/.../data/db/AktivitaetDao.kt`, `BelohnungDao.kt` | neue Abfragen |
| `app/.../data/mapper/EntityMapper.kt` | `BelohnungEntity.zuDomaeneOderNull()` |
| `app/.../data/EinstellungenSpeicher.kt` | `lesenOderWerfen()` |
| `app/.../di/DatenModul.kt` | Buchungspfad liest streng |
| `app/.../ui/einstellungen/EinstellungenViewModel.kt`, `…Screen.kt` | Import unter Sperre, `importLaeuft` |
| `app/.../widget/WidgetTakt.kt` (neu) | nächster Umschaltpunkt, `gehoertZuTag` |
| `app/.../widget/WidgetWecker.kt` (neu) | ungenauer Alarm auf den Umschaltpunkt, Empfänger |
| `app/.../widget/WidgetAbhaken.kt` (neu) | Tipp-Logik mit Tag-Schutz |
| `app/.../widget/WidgetErledigenAktion.kt`, `HeuteWidget.kt`, `HeuteWidgetEmpfaenger.kt`, `WidgetAktualisierung.kt`, `WidgetMasse.kt` | Anbindung, Fehlerlayout, Höhenbudget |
| `app/.../di/WidgetZugang.kt` | `widgetWecker()`, `widgetAktualisierung()` |
| `app/.../platform/notification/AktionsEmpfaenger.kt`, `ErinnerungsNotifier.kt`, `platform/work/WaechterWorker.kt` | `versuchen {}` |
| `app/src/main/res/layout/widget_vorschau.xml`, `widget_fehler.xml` (neu) | Katalogvorschau, Fehlerlayout |
| `app/src/main/res/values/colors.xml` (neu) | Vorschaufarben (aus `HellFarben`) |
| `app/src/main/res/xml/heute_widget_info.xml` | `previewLayout` |
| `app/src/main/AndroidManifest.xml` | `WidgetWeckerEmpfaenger` |
| `app/.../ui/statistik/StatistikViewModel.kt`, `StatistikZustand.kt`, `StatistikDarstellung.kt`, `StatistikScreen.kt`, `Heatmap.kt` | archivierte Aktivitäten, Vorlesetexte |
| `app/src/main/res/values/strings.xml` | neue Texte |
| `docs/HANDOFF.md` | Stand nach Plan 9 |

`app/...` = `app/src/main/kotlin/de/vanhake/dayorganizer`, Tests unter `app/src/test/kotlin/de/vanhake/dayorganizer`.

---

### Task 1: ToastStapel und Umstellung von Heute und Belohnungen

**Files:**
- Create: `app/.../ui/gemeinsam/ToastStapel.kt`
- Modify: `app/.../ui/heute/HeuteViewModel.kt`, `app/.../ui/belohnungen/BelohnungenViewModel.kt`, `app/.../ui/belohnungen/BelohnungenScreen.kt:104-121`, `app/.../ui/aktivitaeten/AktivitaetenViewModel.kt`
- Test: `app/src/test/.../ui/gemeinsam/ToastStapelTest.kt` (neu), `HeuteViewModelTest.kt`, `BelohnungenViewModelTest.kt`, `AktivitaetenViewModelTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  class ToastStapel(private val scope: CoroutineScope, private val dauerMs: Long = TOAST_DAUER_MS) {
      val sichtbar: StateFlow<Hinweistoast?>
      /** Mit [rueckgaengig] ein Rueckgaengig-Eintrag (stapelt sich), ohne ein Info-Eintrag. */
      fun zeigen(text: String, rueckgaengig: (suspend () -> Unit)? = null)
      /** Nimmt den obersten noch laufenden Rueckgaengig-Eintrag vom Stapel und liefert seine
       * Aktion; null, wenn keiner offen ist. Fuehrt die Aktion NICHT aus. */
      fun rueckgaengigNehmen(): (suspend () -> Unit)?
      companion object { const val TOAST_DAUER_MS = 3_800L }
  }
  ```
  Die ViewModels behalten ihre öffentliche API: `val toast: StateFlow<Hinweistoast?>` (jetzt `= stapel.sichtbar`) und `fun rueckgaengig()`.

**Regeln des Stapels** (sind durch die Tests unten festgelegt):
- Jeder Rückgängig-Eintrag läuft `dauerMs` ab seinem `zeigen` ab. Das gilt auch, während er von einem jüngeren verdeckt ist.
- Sichtbar ist der jüngste noch laufende Rückgängig-Eintrag. Gibt es keinen, ist der Info-Eintrag sichtbar, sonst `null`.
- Ein Info-Eintrag, der kommt, während ein Rückgängig offen ist, wartet. Seine `dauerMs` beginnt erst, wenn er sichtbar wird. Ein späterer Info-Eintrag ersetzt einen wartenden oder sichtbaren Info-Eintrag und startet dessen Zeit neu.

- [ ] **Step 1: Tests für den Stapel schreiben** (`runTest`, Stapel mit `backgroundScope`, Zeit mit `advanceTimeBy`/`runCurrent`)

```kotlin
@Test fun `zwei Rueckgaengig-Toasts stapeln sich und Rueckgaengig nimmt den juengsten zuerst`() // A(undo a) bei 0, B(undo b) bei 1000: sichtbar.text == "B"; rueckgaengigNehmen() === b; sichtbar.text == "A"; rueckgaengigNehmen() === a; sichtbar == null
@Test fun `ein abgelaufener Eintrag kommt nach Rueckgaengig nicht wieder`() // A bei 0, B bei 3000, Zeit 4000: rueckgaengigNehmen() === b; sichtbar == null; rueckgaengigNehmen() == null
@Test fun `ein verdeckter Eintrag laeuft trotzdem ab`() // A bei 0, B bei 1000, Zeit 3900: sichtbar.text == "B"; Zeit 4900: sichtbar == null
@Test fun `ein Info-Toast wartet bis kein Rueckgaengig mehr offen ist`() // A(undo) bei 0, Info I bei 1000: sichtbar.text == "A"; Zeit 3800: sichtbar.text == "I", kannRueckgaengig == false; Zeit 7599: "I"; Zeit 7600: null
@Test fun `ohne offenes Rueckgaengig ersetzt ein Info-Toast den vorigen sofort`() // I1 bei 0, I2 bei 1000: "I2"; Zeit 4799: "I2"; 4800: null
@Test fun `Rueckgaengig waehrend ein Info-Toast wartet zeigt sofort den Info-Toast`() // A(undo) bei 0, I bei 500, rueckgaengigNehmen() bei 1000: sichtbar.text == "I"
```

- [ ] **Step 2: Tests laufen lassen, sie schlagen fehl**

Run: `./gradlew :app:testDebugUnitTest --tests '*ToastStapelTest'`
Expected: FAIL (Klasse fehlt).

- [ ] **Step 3: `ToastStapel` implementieren.** Je Rückgängig-Eintrag ein Ablauf-Job im `scope`, dazu ein Job für den sichtbaren Info-Eintrag. Nach jeder Änderung `sichtbar` neu berechnen.

- [ ] **Step 4: Stapeltests grün**

Run: `./gradlew :app:testDebugUnitTest --tests '*ToastStapelTest'`
Expected: PASS.

- [ ] **Step 5: ViewModels umstellen.** `HeuteViewModel`, `BelohnungenViewModel` und `AktivitaetenViewModel` halten `private val stapel = ToastStapel(viewModelScope)`. Ihr `toast` ist `stapel.sichtbar`. `rueckgaengig()` ruft `stapel.rueckgaengigNehmen()` und führt die Aktion aus, Rest wie bisher. `toastJob`, `ausstehendesRueckgaengig` und die lokalen `TOAST_DAUER_MS` entfallen. In `BelohnungenScreen` startet die Wartezeit einer `EinloeseMeldung` erst, wenn kein Toast mehr sichtbar ist: `LaunchedEffect(meldung, toast == null)`. Der Screen gibt `toast` weiter Vorrang. So verdrängt die Meldung kein offenes Rückgängig und geht auch nicht verloren.

- [ ] **Step 6: ViewModel-Tests ergänzen**

```kotlin
// HeuteViewModelTest
@Test fun `zwei schnell abgehakte Eintraege lassen sich beide rueckgaengig machen`() // erledigen(1), erledigen(2), rueckgaengig(), rueckgaengig(): beide Faelligkeiten wieder OFFEN, Kontostand wie vorher
// BelohnungenViewModelTest
@Test fun `ein Speichern-Toast verdraengt das Rueckgaengig einer Einloesung nicht`() // einloesen(...) -> Erfolg, gespeichertMelden(GEAENDERT): toast.value!!.kannRueckgaengig == true; rueckgaengig(): Einloesung storniert
```

Bestehende Tests, die das alte Verdrängen zusichern, werden angepasst (reviewpflichtig, siehe Global Constraints).

- [ ] **Step 7: Volllauf und Commit**

Run: `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` → grün.

```bash
git add -A app/src
git commit -m "feat(toast): Toasts stapeln statt ein offenes Rueckgaengig zu verdraengen"
git log -1 --oneline
```

---

### Task 2: Archivieren mit Rückfrage, Rückgängig und Archivbereich

**Files:**
- Modify: `app/.../data/db/AktivitaetDao.kt`, `BelohnungDao.kt`, `app/.../data/repository/Repositories.kt`, `RaumRepositories.kt`, `BelohnungRepository.kt`
- Modify: `app/.../ui/aktivitaeten/AktivitaetenViewModel.kt`, `AktivitaetenScreen.kt`, `app/.../ui/belohnungen/BelohnungenViewModel.kt`, `BelohnungenScreen.kt`, `app/src/main/res/values/strings.xml`
- Create: `app/.../ui/gemeinsam/ArchivBausteine.kt`
- Test: `DaoTest.kt`, `Fakes.kt`, `FakeAktivitaetRepositoryTest.kt`, `FakeBelohnungRepositoryTest.kt`, `AktivitaetenViewModelTest.kt`, `BelohnungenViewModelTest.kt`

**Interfaces:**
- Consumes: `ToastStapel` (Task 1).
- Produces:
  ```kotlin
  // AktivitaetDao
  @Query("SELECT * FROM aktivitaet WHERE archiviertAm IS NOT NULL ORDER BY name")
  fun beobachteArchivierte(): Flow<List<AktivitaetEntity>>
  @Query("UPDATE aktivitaet SET archiviertAm = NULL, aktiv = 1 WHERE id = :id AND archiviertAm IS NOT NULL")
  suspend fun wiederherstellen(id: Long): Int
  // BelohnungDao
  @Query("SELECT * FROM belohnung WHERE archiviert = 1 ORDER BY name")
  fun beobachteArchivierte(): Flow<List<BelohnungEntity>>
  @Query("UPDATE belohnung SET archiviert = 0 WHERE id = :id AND archiviert = 1")
  suspend fun wiederherstellen(id: Long): Int
  // AktivitaetRepository / BelohnungRepository
  fun beobachteArchivierte(): Flow<List<Aktivitaet>>      // bzw. List<Belohnung>
  /** true, wenn gerade wiederhergestellt; false, wenn unbekannt oder nicht archiviert (idempotent). */
  suspend fun wiederherstellen(id: Long): Boolean
  // ui/gemeinsam/ArchivBausteine.kt
  data class ArchivEintrag(val id: Long, val name: String, val symbol: String)
  @Composable fun ArchivierenDialog(name: String, erklaerung: String, bestaetigen: () -> Unit, abbrechen: () -> Unit)
  @Composable fun ArchivBereich(eintraege: List<ArchivEintrag>, symbol: @Composable (String) -> ImageVector, wiederherstellen: (Long) -> Unit)
  // AktivitaetenViewModel / BelohnungenViewModel
  val archiviert: StateFlow<List<ArchivEintrag>>
  fun wiederherstellen(id: Long): Job
  ```
- `archivieren(id)` zeigt jetzt `zeigen("$name archiviert", rueckgaengig = { wiederherstellenIntern(id) })`. `wiederherstellen(id)` zeigt den Info-Toast `"$name wiederhergestellt"`. Bei Aktivitäten läuft nach jedem erfolgreichen Wiederherstellen `neuPlanen(id)` in `versuchen {}`, genau wie nach dem Archivieren.

**Werte:**
- Dialog: Titel `archivieren_frage` = „%1$s archivieren?“. Text `archivieren_erklaerung_aktivitaet` = „Sie verschwindet aus Heute und dem Widget, Erinnerungen hören auf. Die Historie bleibt erhalten, unter „Archiviert“ kannst du sie wiederherstellen.“ Text `archivieren_erklaerung_belohnung` = „Sie verschwindet aus dem Shop. Einlösungen bleiben in der Historie, unter „Archiviert“ kannst du sie wiederherstellen.“ Knöpfe `archivieren` (vorhanden) und `dialog_abbrechen` (vorhanden).
- Bereich: Kopf `archiv_titel` = „Archiviert (%1$d)“ mit Pfeil auf/zu, standardmäßig zu. Der Zustand liegt in `rememberSaveable`. Der Bereich erscheint nur bei mindestens einem Eintrag und steht als letztes `item` der `LazyColumn`, bei Belohnungen vor der Einlösehistorie. Je Zeile Symbol 20 dp in `ink2`, Name 14 sp `ink2` und ein `TextButton` `wiederherstellen` = „Wiederherstellen“. Die `LazyColumn`-Keys sind `"archiv-${id}"`, damit sie nicht mit `kachel-`/`einloesung-`/Aktivitäts-ids kollidieren.
- Toast-Texte in den `…ToastTexte`-Objekten: `archiviert(name) = "$name archiviert"` (vorhanden), `wiederhergestellt(name) = "$name wiederhergestellt"`.

- [ ] **Step 1: DAO-Tests schreiben** (`DaoTest`, Unterstrich-Namen)

```kotlin
@Test fun archivierte_aktivitaeten_nach_name_und_wiederherstellen_setzt_aktiv_zurueck() // archivieren(B), archivieren(A): beobachteArchivierte().first() == [A, B]; wiederherstellen(A.id) == 1; nachId(A).archiviertAm == null && aktiv; zweites wiederherstellen(A.id) == 0
@Test fun wiederherstellen_einer_nicht_archivierten_aktivitaet_aendert_nichts() // == 0
@Test fun archivierte_belohnungen_nach_name_und_wiederherstellen() // analog, archiviert = 0 danach
```

- [ ] **Step 2: Tests schlagen fehl** — Run: `./gradlew :app:testDebugUnitTest --tests '*DaoTest'` → FAIL.

- [ ] **Step 3: DAO-Methoden, Repository-Methoden und Fakes umsetzen.** Die Fakes bilden `ORDER BY name` und die Bedingung im `WHERE` nach. `FakeAktivitaetRepositoryTest` und `FakeBelohnungRepositoryTest` bekommen je einen Test: Sortierung und Rückgabe `false` beim zweiten Aufruf.

- [ ] **Step 4: DAO- und Fake-Tests grün** — Run: `./gradlew :app:testDebugUnitTest --tests '*DaoTest' --tests '*Fake*RepositoryTest'` → PASS.

- [ ] **Step 5: ViewModel-Tests schreiben**

```kotlin
// AktivitaetenViewModelTest
@Test fun `Archivieren zeigt Rueckgaengig und Rueckgaengig holt die Aktivitaet samt Planung zurueck`() // archivieren(1); toast.kannRueckgaengig; rueckgaengig(): repo.nachId(1).archiviertAm == null; neuPlanen-Spion hat id 1 zweimal gesehen (Archivieren + Wiederherstellen)
@Test fun `Wiederherstellen aus dem Archivbereich plant neu und meldet es`() // archiviert.value enthaelt 1; wiederherstellen(1): archiviert.value leer, toast.text == "<Name> wiederhergestellt", neuPlanen(1)
@Test fun `doppeltes Wiederherstellen wirft nicht und plant nur einmal`() // wiederherstellen(1) zweimal: neuPlanen genau einmal fuer die Wiederherstellung
// BelohnungenViewModelTest
@Test fun `Archivieren einer Belohnung laesst sich rueckgaengig machen`()
@Test fun `Wiederherstellen einer Belohnung zeigt sie wieder im Shop`() // zustand.kacheln enthaelt sie wieder
```

- [ ] **Step 6: ViewModels umsetzen, bis die Tests grün sind** — Run: `./gradlew :app:testDebugUnitTest --tests '*AktivitaetenViewModelTest' --tests '*BelohnungenViewModelTest'` → PASS.

- [ ] **Step 7: Oberfläche.** Der Archivier-Knopf öffnet `ArchivierenDialog`. Erst „Archivieren“ ruft `viewModel.archivieren(id)`. Der Dialogzustand (`id` oder `null`) liegt im Screen in `rememberSaveable`. Dazu den `ArchivBereich` einbauen und die Strings anlegen.

- [ ] **Step 8: Volllauf und Commit**

```bash
git add -A app/src
git commit -m "feat(archiv): Rueckfrage, Rueckgaengig und Archivbereich fuer Aktivitaeten und Belohnungen"
git log -1 --oneline
```

---

### Task 3: Editor-Fehler und einheitliches `versuchen {}`

**Files:**
- Modify: `app/.../data/repository/Repositories.kt`, `RaumRepositories.kt`, `BelohnungRepository.kt`, `Fakes.kt`
- Modify: `app/.../ui/gemeinsam/SpeicherErgebnis.kt`, `AktivitaetBearbeitenViewModel.kt`, `AktivitaetBearbeitenScreen.kt`, `BelohnungBearbeitenViewModel.kt`, `BelohnungBearbeitenScreen.kt`
- Modify: `app/.../ui/aktivitaeten/AktivitaetenViewModel.kt`, `app/.../ui/belohnungen/BelohnungenViewModel.kt`, `app/.../widget/WidgetAktualisierung.kt`, `app/.../platform/notification/ErinnerungsNotifier.kt`, `app/.../platform/work/WaechterWorker.kt`
- Test: `AktivitaetBearbeitenViewModelTest.kt`, `BelohnungBearbeitenViewModelTest.kt`, `FakeAktivitaetRepositoryTest.kt`, `FakeBelohnungRepositoryTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  // Repositories.kt
  class DatensatzFehlt(val id: Long, art: String) : IllegalStateException("$art mit id=$id existiert nicht")
  // SpeicherErgebnis.kt
  enum class SpeicherFehler { NICHT_MEHR_VORHANDEN, FEHLGESCHLAGEN }
  // Beide Bearbeiten-ViewModels
  val speicherFehler: StateFlow<SpeicherFehler?>
  fun speicherFehlerGezeigt()
  ```
- `speichern()` behält die Signatur `suspend fun speichern(): SpeicherErgebnis?`. Bei einer Ausnahme liefert es `null` und setzt `speicherFehler`. `DatensatzFehlt` wird zu `NICHT_MEHR_VORHANDEN`, alles andere zu `FEHLGESCHLAGEN`. Der Entwurf bleibt unverändert, und die Sperre `speichertGerade` fällt wieder.
- `RaumAktivitaetRepository.speichern` und `RaumBelohnungRepository.speichern` werfen `DatensatzFehlt` statt `check(...)` zu nutzen. `wiederherstellen` (Task 2) bleibt bei `false` für unbekannte und nicht archivierte ids.

**Werte:** `speichern_fehlgeschlagen` = „Speichern fehlgeschlagen. Bitte noch einmal versuchen.“, `speichern_nicht_mehr_vorhanden` = „Diesen Eintrag gibt es nicht mehr – vielleicht wurde inzwischen ein Backup eingespielt.“ Der Editor-Screen zeigt das als `HinweistoastLeiste` unten, 3 800 ms, danach `speicherFehlerGezeigt()`. Das Muster ist dasselbe wie die `meldung` in `EinstellungenScreen`.

- [ ] **Step 1: Tests schreiben**

```kotlin
// AktivitaetBearbeitenViewModelTest
@Test fun `fehlt der Datensatz beim Speichern bleibt der Editor mit Entwurf offen`() // geladene Aktivitaet id 5 aus dem Fake entfernen; speichern() == null; speicherFehler.value == NICHT_MEHR_VORHANDEN; entwurf.value unveraendert
@Test fun `eine Datenbankausnahme beim Speichern wird als Fehlschlag gemeldet`() // Repository-Spion wirft IOException: speichern() == null; speicherFehler.value == FEHLGESCHLAGEN
@Test fun `nach einem Fehlschlag laesst sich erneut speichern`() // erster Aufruf wirft, zweiter nicht: zweites speichern() == GEAENDERT
// BelohnungBearbeitenViewModelTest: dieselben drei Faelle
// Fake*RepositoryTest
@Test fun `speichern einer unbekannten id wirft DatensatzFehlt`()
```

- [ ] **Step 2: Tests schlagen fehl** — Run: `./gradlew :app:testDebugUnitTest --tests '*BearbeitenViewModelTest' --tests '*Fake*RepositoryTest'` → FAIL.

- [ ] **Step 3: Umsetzen.** `DatensatzFehlt` einführen und in den Fakes ebenfalls werfen. Die Editoren fangen mit `versuchen {}` und zeigen den Toast im Screen.

- [ ] **Step 4: Fangstellen vereinheitlichen.** In `AktivitaetenViewModel.archivieren`, `AktivitaetBearbeitenViewModel.speichern` (Nachbereitung), `BelohnungenViewModel.rueckgaengig`, `WidgetAktualisierung.anstossen`, `ErinnerungsNotifier` (`notify`) und `WaechterWorker.doWork` steht jetzt `versuchen {}` statt `runCatching` oder `try/catch (Exception)`. Kommentare und Logs bleiben, wo sie etwas erklären.

Check: `grep -rnE "runCatching|catch \((ausnahme|e|fehler): Exception" app/src/main/kotlin` zeigt nur noch die Ausnahmen aus den Global Constraints.

- [ ] **Step 5: Volllauf und Commit**

```bash
git add -A app/src
git commit -m "fix(editor): Speicherfehler als Hinweis statt Absturz, versuchen{} einheitlich"
git log -1 --oneline
```

---

### Task 4: Robustheit im Lesepfad — Belohnungsdatensatz und Einstellungen

**Files:**
- Modify: `app/.../data/mapper/EntityMapper.kt:145-160`, `app/.../data/repository/BelohnungRepository.kt`, `app/.../data/EinstellungenSpeicher.kt`, `app/.../di/DatenModul.kt:82`
- Modify: `app/.../ui/heute/HeuteViewModel.kt`, `app/.../platform/notification/AktionsEmpfaenger.kt`
- Test: `BelohnungMapperTest.kt`, `app/src/test/.../data/repository/BelohnungRepositoryTest.kt` (neu, Robolectric, In-Memory-DB wie `BuchungRepositoryVertragTest`), `EinstellungenSpeicherTest.kt`, `FaelligkeitErledigenTest.kt`, `HeuteViewModelTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  /** null bei unbekanntem bedingungTyp oder fehlendem Pflichtfeld der Bedingung. */
  fun BelohnungEntity.zuDomaeneOderNull(): Belohnung?
  // EinstellungenSpeicher
  /** Wie lesen(), faengt aber keine IOException: fuer den Buchungspfad, der lieber scheitert
   * als mit Vorgabewerten bucht. */
  suspend fun lesenOderWerfen(): Einstellungen
  ```
- `RaumBelohnungRepository` nutzt in `beobachteAktive`, `beobachteArchivierte`, `alle` und `nachId` die Funktion `zuDomaeneOderNull` mit `mapNotNull`. Für jeden übersprungenen Datensatz schreibt es `Log.w` mit der id. `BelohnungEntity.zuDomaene()` entfällt, wenn danach kein Aufrufer mehr übrig ist. Sonst ruft es `zuDomaeneOderNull() ?: error(...)` auf.
- `DatenModul` übergibt `FaelligkeitErledigen` jetzt `{ speicher.lesenOderWerfen() }`.
- `HeuteViewModel.erledigen`, `auslassen` und `rueckgaengig` fangen mit `versuchen {}`. Bei einem Fehlschlag gibt es `Log.w` und einen Info-Toast. Benachrichtigung, Alarm und Widget bleiben unberührt, weil sich nichts geändert hat. Texte in `HeuteToastTexte`: `ERLEDIGEN_FEHLGESCHLAGEN = "Abhaken fehlgeschlagen"`, `AUSLASSEN_FEHLGESCHLAGEN = "Auslassen fehlgeschlagen"`, `RUECKGAENGIG_FEHLGESCHLAGEN = "Rückgängig fehlgeschlagen"`.
- `AktionsEmpfaenger` fängt den Use-Case-Aufruf mit `versuchen {}` und `Log.w`. Bei einem Fehlschlag bleibt die Benachrichtigung stehen, `verstecke` wird dann nicht aufgerufen. `neuPlanen` und `anstossen` laufen trotzdem.

- [ ] **Step 1: Tests schreiben**

```kotlin
// BelohnungMapperTest
@Test fun `eine unbekannte Bedingung ergibt null statt einer Ausnahme`() // bedingungTyp = "ZUKUNFT" -> null
@Test fun `eine Mindestserie ohne Aktivitaet ergibt null`() // bedingungTyp = "MINDESTSERIE", bedingungAktivitaetId = null -> null
// BelohnungRepositoryTest (Robolectric)
@Test fun `ein kaputter Datensatz fehlt im Shop, die anderen bleiben`() // zwei gesunde + einer mit "ZUKUNFT": beobachteAktive().first().map { it.name } == [gesunde nach preisPunkte]
// EinstellungenSpeicherTest
@Test fun `lesenOderWerfen reicht einen Lesefehler weiter`() // WerfenderDatenSpeicher(IOException) -> assertFailsWith<IOException>
// FaelligkeitErledigenTest
@Test fun `scheitert das Lesen der Einstellungen wird weder gebucht noch abgehakt`() // einstellungenLesen wirft IOException: assertFailsWith; Faelligkeit OFFEN, istAnzahl 0, keine Buchung
// HeuteViewModelTest
@Test fun `ein Fehler beim Abhaken meldet sich als Toast und laesst die Benachrichtigung stehen`() // Use Case wirft: toast.text == "Abhaken fehlgeschlagen", kannRueckgaengig == false; anzeige.versteckt leer; alarm/widget nicht angestossen
```

- [ ] **Step 2: Tests schlagen fehl** — Run: `./gradlew :app:testDebugUnitTest --tests '*BelohnungMapperTest' --tests '*BelohnungRepositoryTest' --tests '*EinstellungenSpeicherTest' --tests '*FaelligkeitErledigenTest' --tests '*HeuteViewModelTest'` → FAIL.

- [ ] **Step 3: Umsetzen.**

- [ ] **Step 4: Tests grün, dann Mutationsnachweis.** `mapNotNull` probehalber durch `map { it.zuDomaeneOderNull()!! }` ersetzen. Der Repository-Test muss dann fehlschlagen. Danach wiederherstellen.

- [ ] **Step 5: Volllauf und Commit**

```bash
git add -A app/src
git commit -m "fix(robustheit): kaputte Belohnung ueberspringen, Buchungspfad liest Einstellungen streng"
git log -1 --oneline
```

---

### Task 5: Import und Stepper unter einer Sperre

**Files:**
- Modify: `app/.../ui/einstellungen/EinstellungenViewModel.kt`, `EinstellungenScreen.kt`
- Test: `EinstellungenViewModelTest.kt`

**Interfaces:**
- Produces: `EinstellungenZustand.importLaeuft: Boolean = false`.
- `importBestaetigen()` setzt `importLaeuft = true` **synchron**, vor dem `launch`, und nur wenn ein Text vorliegt. Import und `neuLaden()` laufen unter `schreibSperre.withLock` innerhalb des vorhandenen `NonCancellable`. `neuLaden()` setzt `importLaeuft` wieder auf `false`. Scheitert der Import, gilt das ebenso (`finally`). `nachImport()` läuft wie bisher nach dem Lösen der Sperre.
- `tagesbonusAendern`, `kulanzAendern` und `themeModusAendern` liefern `ignoriert()`, solange `importLaeuft`.
- `uebernehmen()` liest `lesen()` jetzt innerhalb von `versuchen {}` (Backlog „Neu aus Plan 8“).
- Screen: Die Stepper-Knöpfe sind gesperrt (`verringernMoeglich`/`erhoehenMoeglich` `&& !importLaeuft`), der `Segmentschalter` hat `aktiv = geladen && !importLaeuft`.

- [ ] **Step 1: Tests schreiben** (Backup-Fake, dessen `importieren` an einem `CompletableDeferred` hängt und dabei `schreiben(...)` mit Bonus 40 aufruft. Dazu ein `schreiben`-Fake, der optional an einem Tor hängt.)

```kotlin
@Test fun `ein Stepper-Tipp waehrend des Imports wird ignoriert`() // importBestaetigen(); runCurrent(); zustand.importLaeuft; tagesbonusAendern(+1) -> keine Aenderung; Import fertig: gespeichert.tagesbonusPunkte == 40, zustand.tagesbonusPunkte == 40, !importLaeuft
@Test fun `ein haengender Stepper-Schreibvorgang wird vom Import nicht ueberholt und nicht ueberschrieben`() // schreiben-Tor zu; tagesbonusAendern(+1) (15->20); importBestaetigen(); Tor auf: Reihenfolge der Schreibvorgaenge [20, 40]; Endstand 40
@Test fun `ein fehlgeschlagener Import gibt die Stepper wieder frei`() // importieren wirft: !importLaeuft
@Test fun `ein Lesefehler in uebernehmen laedt neu statt abzustuerzen`() // lesen wirft beim zweiten Aufruf IOException: kein Absturz, zustand zeigt gespeicherten Stand
```

- [ ] **Step 2: Tests schlagen fehl**, dann umsetzen. **Step 3:** grün. **Step 4:** Mutationsnachweis: `withLock` im Import entfernen, dann muss der zweite Test fallen. Danach wiederherstellen.

- [ ] **Step 5: Volllauf und Commit**

```bash
git add -A app/src
git commit -m "fix(einstellungen): Import und Stepper unter einer Schreibsperre, Stepper waehrend Import gesperrt"
git log -1 --oneline
```

---

### Task 6: Widget-Takt — überfällig pünktlich, Mitternacht, Tipp-Schutz

**Files:**
- Create: `app/.../widget/WidgetTakt.kt`, `app/.../widget/WidgetWecker.kt`, `app/.../widget/WidgetAbhaken.kt`
- Modify: `app/.../widget/WidgetErledigenAktion.kt`, `HeuteWidget.kt`, `HeuteWidgetEmpfaenger.kt`, `app/.../di/WidgetZugang.kt`, `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/.../widget/WidgetTaktTest.kt`, `WidgetWeckerTest.kt` (Robolectric, `ShadowAlarmManager` wie `AlarmPlanerTest`), `WidgetAbhakenTest.kt` (alle neu)

**Interfaces:**
- Produces:
  ```kotlin
  object WidgetTakt {
      /** Naechster Zeitpunkt >= jetzt, an dem sich die Darstellung aendert: das frueheste
       * geplantFuer (>= jetzt) einer heute OFFENEN Faelligkeit, sonst/spaetestens der naechste
       * Tagesbeginn 00:00. */
      fun naechsterUmschaltpunkt(faelligkeiten: List<Faelligkeit>, jetzt: LocalDateTime): LocalDateTime
      /** periodeVon <= tag <= periodeBis -- dasselbe Praedikat wie FaelligkeitDao.beobachteTag. */
      fun gehoertZuTag(faelligkeit: Faelligkeit, tag: LocalDate): Boolean
  }

  @Singleton
  class WidgetWecker @Inject constructor(
      @ApplicationContext kontext: Context,
      private val faelligkeiten: FaelligkeitRepository,
      private val uhr: Clock,
  ) {
      /** Stellt den Alarm auf naechsterUmschaltpunkt + NACHLAUF_MS; ersetzt einen frueheren. */
      suspend fun planen()
      fun abbestellen()
      companion object {
          const val ACTION_WIDGET_TAKT = "de.vanhake.dayorganizer.WIDGET_TAKT"
          const val NACHLAUF_MS = 1_000L
          const val FENSTER_MS = 60_000L
      }
  }

  /** Nicht exportiert, @AndroidEntryPoint; goAsync, dann widgetAktualisierung.anstossen()
   * und wecker.planen(), beides je in versuchen{}. */
  class WidgetWeckerEmpfaenger : BroadcastReceiver

  enum class WidgetAbhakErgebnis { GEBUCHT, ANDERER_TAG, NICHT_OFFEN, UNBEKANNT, FEHLER }

  class WidgetAbhaken(
      private val faelligkeitLesen: suspend (Long) -> Faelligkeit?,
      private val erledigen: suspend (Long) -> ErledigungsProtokoll,
      private val nachbereiten: suspend (Long) -> Unit,   // anzeige.verstecke(id) + alarmPlaner.neuPlanen()
      private val widgetAnstossen: suspend () -> Unit,    // WidgetAktualisierung.anstossen()
      private val uhr: Clock,
  ) { suspend operator fun invoke(faelligkeitId: Long): WidgetAbhakErgebnis }

  // WidgetZugang zusaetzlich
  fun widgetWecker(): WidgetWecker
  fun widgetAktualisierung(): WidgetAktualisierung
  ```
- Ablauf von `WidgetAbhaken`: Fälligkeit lesen. Ist sie `null`, Ergebnis `UNBEKANNT`. Gehört sie nicht zu `LocalDate.now(uhr)`, Ergebnis `ANDERER_TAG` ohne Buchung. Sonst `erledigen` in `versuchen {}`: `Gebucht` ergibt `GEBUCHT`, `NichtOffen`/`Unbekannt` ergeben `NICHT_OFFEN`, eine Ausnahme ergibt `FEHLER`. `nachbereiten(id)` läuft bei `GEBUCHT` und `NICHT_OFFEN`, so wie die App bei jedem Ausgang versteckt. **`widgetAnstossen()` läuft bei jedem Ergebnis.**
- `WidgetErledigenAktion.onAction` baut `WidgetAbhaken` aus dem `WidgetZugang` (`erledigen().mitProtokoll`) und ruft es auf. Kein direktes `updateAll` mehr.
- `HeuteWidget.provideGlance` ruft nach den Anfangswerten `versuchen { zugang.widgetWecker().planen() }`. `HeuteWidgetEmpfaenger.onDisabled` ruft `abbestellen()`. Der Alarm nutzt `AlarmManager.setWindow(AlarmManager.RTC, …)`: RTC weckt das Gerät nicht, ein Widget muss bei ausgeschaltetem Bildschirm nicht neu zeichnen. `PendingIntent` mit eigener Anfrage-Nummer `2001` und `FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE`. Die Umrechnung macht `alsEpochMillis` aus `AlarmPlaner.kt`.

- [ ] **Step 1: Tests schreiben**

```kotlin
// WidgetTaktTest (jetzt = 2026-10-16T07:30)
@Test fun `der naechste Umschaltpunkt ist die frueheste kuenftige Uhrzeit eines offenen Eintrags`() // offen 08:00 und 09:00 -> 08:00
@Test fun `erledigte und vergangene Eintraege zaehlen nicht`() // erledigt 07:45, offen 07:00 (schon ueberfaellig), offen 10:00 -> 10:00
@Test fun `ein Eintrag genau jetzt zaehlt noch`() // offen 07:30 -> 07:30
@Test fun `ohne kuenftige Uhrzeit ist Mitternacht der Umschaltpunkt`() // nur Wochenziel ohne Uhrzeit -> 2026-10-17T00:00
@Test fun `Mitternacht gewinnt gegen einen Termin von morgen`() // offen morgen 06:00 (periode morgen) -> 2026-10-17T00:00
@Test fun `ein Wochenziel gehoert zu jedem Tag seiner Woche`() // periode 12.-18.10.: gehoertZuTag(16.10.) true, gehoertZuTag(19.10.) false
// WidgetWeckerTest (Robolectric)
@Test fun `planen stellt einen RTC-Alarm eine Sekunde nach dem Umschaltpunkt`() // naechsterGeplanterAlarm.type == RTC, triggerAtTime == millis(08:00) + 1000
@Test fun `abbestellen entfernt den Alarm`()
// WidgetAbhakenTest (Uhr 2026-10-17T00:10, Faelligkeit periode 16.10.)
@Test fun `ein Eintrag von gestern wird nicht gebucht, das Widget zeichnet trotzdem neu`() // == ANDERER_TAG; erledigen nie aufgerufen; widgetAnstossen einmal
@Test fun `ein heutiger Eintrag wird gebucht und nachbereitet`() // == GEBUCHT; nachbereiten(id); widgetAnstossen
@Test fun `ein Fehler beim Buchen reisst die Aktion nicht mit`() // erledigen wirft: == FEHLER; widgetAnstossen einmal
@Test fun `ein Wochenziel dieser Woche wird nach Mitternacht weiter gebucht`() // periode 12.-18.10. -> GEBUCHT
```

- [ ] **Step 2: Tests schlagen fehl** — Run: `./gradlew :app:testDebugUnitTest --tests '*WidgetTaktTest' --tests '*WidgetWeckerTest' --tests '*WidgetAbhakenTest'` → FAIL.

- [ ] **Step 3: Umsetzen,** inklusive Manifest-Eintrag (`<receiver android:name=".widget.WidgetWeckerEmpfaenger" android:exported="false">` mit Intent-Filter auf `ACTION_WIDGET_TAKT`).

- [ ] **Step 4: Tests grün, dann Mutationsnachweis:** In `WidgetAbhaken` die Tagesprüfung entfernen, dann muss der Gestern-Test fallen. Wiederherstellen.

- [ ] **Step 5: Volllauf und Commit**

```bash
git add -A app/src
git commit -m "fix(widget): Neuzeichnen zur geplanten Uhrzeit und um Mitternacht, Tipp auf gestrige Eintraege bucht nicht"
git log -1 --oneline
```

---

### Task 7: Widget-Vorschau, Fehlerlayout und Höhenbudget

**Files:**
- Create: `app/src/main/res/layout/widget_vorschau.xml`, `app/src/main/res/layout/widget_fehler.xml`
- Modify: `app/src/main/res/values/colors.xml` (neu), `app/src/main/res/xml/heute_widget_info.xml`, `app/.../widget/HeuteWidget.kt`, `app/.../widget/WidgetMasse.kt`, `strings.xml`
- Test: `WidgetMasseTest.kt`, `app/src/test/.../widget/WidgetRessourcenTest.kt` (neu, Robolectric)

**Interfaces:**
- Produces: `fun WidgetMasse.leerhinweisPasst(hoeheDp: Float, schriftFaktor: Float): Boolean`. Wahr, wenn `2*16 + kopf + 10 + 8 + 20*schriftFaktor + 8 <= hoeheDp`. `kopf` ist dieselbe Rechnung wie in `passendeZeilen`. `HeuteWidget` zeigt „Alles erledigt für heute“ bzw. „Heute steht nichts an“ nur, wenn das passt.
- `KOPF_SCHRIFT_DP` wird von `20f` auf `21f` angehoben. Bei 200 % wuchsen Pille und Kopfzeile auf dem Emulator ca. 2 dp über die Annahme hinaus.
- `HeuteWidget : GlanceAppWidget(errorUiLayout = R.layout.widget_fehler)`. In Glance 1.1.1 zusätzlich `onCompositionError` überschreiben, falls die API dort vorhanden ist. Dann `RemoteViews(R.layout.widget_fehler)` bauen, auf der Wurzel per `setOnClickPendingIntent` die `MainActivity` öffnen und über `AppWidgetManager.updateAppWidget(appWidgetId, …)` setzen. Fehlt die API, bleibt es beim `errorUiLayout` ohne Klick. Das wird im Bericht vermerkt.
- `heute_widget_info.xml`: `android:previewLayout="@layout/widget_vorschau"`.

**Werte:**
- `widget_fehler.xml`: `FrameLayout` mit Hintergrund `@color/widget_vorschau_surface` und 16 dp Innenabstand. Darin ein `TextView` mit `widget_fehler` = „Widget konnte nicht geladen werden. Tippen, um die App zu öffnen.“, 14 sp, Farbe `@color/widget_vorschau_ink`.
- `widget_vorschau.xml`: statischer Nachbau des Widgets im Hellmodus. Kopf mit „Heute“ und „2/5“, Pille „120“. Zwei Zeilen: „Wasser trinken“ / „08:00 · überfällig“ (Symbol und Untertitel in `coral`) und „Spaziergang“ / „1 von 3 heute“. Fußzeile „+ 2 weitere“. Maße wie Plan 8 (Global Constraints dort). Drawables sind die vorhandenen `widget_*`, die Texte stehen als `widget_vorschau_*` in `strings.xml`.
- `colors.xml`: `widget_vorschau_surface`, `_surface2`, `_ink`, `_ink2`, `_accent`, `_on_accent`, `_coral`, `_gold_soft`, `_gold_ink`, Werte aus `HellFarben`.

- [ ] **Step 1: Tests schreiben**

```kotlin
// WidgetMasseTest
@Test fun `der Leerhinweis passt bei normaler Schrift in die Mindesthoehe`() // leerhinweisPasst(110f, 1f) == true
@Test fun `bei doppelter Schrift passt der Leerhinweis nicht in die Mindesthoehe`() // leerhinweisPasst(110f, 2f) == false; leerhinweisPasst(150f, 2f) == true
// WidgetRessourcenTest (Robolectric)
@Test fun `die Vorschaufarben stimmen mit HellFarben ueberein`() // je Farbe: getColor(R.color.widget_vorschau_x) == HellFarben.x.toArgb()
@Test fun `Vorschau- und Fehlerlayout lassen sich aufblasen`() // LayoutInflater.inflate(R.layout.widget_vorschau / widget_fehler) != null
```

Bestehende `WidgetMasseTest`-Grenzwerte, die sich durch `21f` verschieben, werden mit neu gerechneten Werten angepasst. Das ist reviewpflichtig.

- [ ] **Step 2: fehlschlagen lassen, umsetzen, grün.** Run: `./gradlew :app:testDebugUnitTest --tests '*WidgetMasseTest' --tests '*WidgetRessourcenTest'`.

- [ ] **Step 3: Volllauf und Commit**

```bash
git add -A app/src
git commit -m "feat(widget): Katalogvorschau, eigenes Fehlerlayout, Leerhinweis im Hoehenbudget"
git log -1 --oneline
```

---

### Task 8: Statistik — archivierte Aktivitäten und TalkBack

**Files:**
- Modify: `app/.../data/repository/Repositories.kt`, `RaumRepositories.kt`, `Fakes.kt` (`AktivitaetRepository.alle()`)
- Modify: `app/.../ui/statistik/StatistikViewModel.kt`, `StatistikZustand.kt`, `StatistikDarstellung.kt`, `StatistikScreen.kt`, `Heatmap.kt`, `strings.xml`
- Test: `StatistikViewModelTest.kt`, `StatistikDarstellungTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  // AktivitaetRepository
  suspend fun alle(): List<Aktivitaet>   // dao.alle(): ORDER BY id
  // StatistikZeile
  val archiviert: Boolean = false
  // StatistikDarstellung.kt
  data class HeatmapZusammenfassung(val tage: Int, val vollstaendig: Int, val teilweise: Int, val nichtsErledigt: Int, val frei: Int)
  fun heatmapZusammenfassung(bilanzen: List<TagesBilanz>): HeatmapZusammenfassung
  /** Bilanzen je Kalenderwoche (Montag bis Sonntag), in der Reihenfolge der Heatmap-Spalten. */
  fun heatmapWochen(bilanzen: List<TagesBilanz>): List<List<TagesBilanz>>
  ```
- Klassen der Zusammenfassung: `frei` heißt `gesamt == 0`, `vollstaendig` heißt `erledigt == gesamt > 0`, `nichtsErledigt` heißt `erledigt == 0 < gesamt`, alles andere ist `teilweise`.
- `StatistikViewModel` nimmt aktive Aktivitäten (wie bisher `alleAktiven()`, nach Name). Danach kommen archivierte (`archiviertAm != null`) aus `alle()`, deren id unter den `aktivitaetId`s von `imZeitraum` vorkommt, nach Name sortiert und mit `archiviert = true`.
- Screen: Archivierte Zeilen bekommen in der Metazeile den Zusatz `statistik_archiviert` = „archiviert“, verbunden über den vorhandenen Trenner-Formatstring. Das Symbol wird in `ink2` statt `onAccentSoft` gezeichnet.
- TalkBack: Die `Heatmap` wird zu einem Container mit `semantics { contentDescription = zusammenfassung }`. Darüber liegt eine `Row` aus `wochen` unsichtbaren Boxen in Spaltenbreite. Jede Box trägt `semantics { contentDescription = wochenText }`. Das `Canvas` selbst ist `clearAndSetSemantics {}`. Die Quote-„–“ bekommt `contentDescription = aktivitaet_quote_leer_vorlesen`.

**Werte (strings.xml):**
- `heatmap_vorlesen` = „%1$d Tage: %2$d vollständig, %3$d teilweise, %4$d nichts erledigt, %5$d ohne Aufgaben“
- `heatmap_woche_vorlesen` = „Woche ab %1$s: %2$s“ (Datum als „6. Oktober“, Formatter `d. MMMM`, `Locale.GERMAN`). Die Tage werden mit „, “ verbunden.
- `heatmap_tag_vollstaendig` = „%1$s alles erledigt“, `heatmap_tag_teilweise` = „%1$s %2$d von %3$d“, `heatmap_tag_frei` = „%1$s frei“. `%1$s` ist der Wochentag ausgeschrieben (`EEEE`, `Locale.GERMAN`). „nichts erledigt“ fällt unter `teilweise` mit „0 von n“.
- `aktivitaet_quote_leer_vorlesen` = „noch keine Quote“

- [ ] **Step 1: Tests schreiben**

```kotlin
// StatistikViewModelTest
@Test fun `eine archivierte Aktivitaet mit Faelligkeiten im Zeitraum steht nach den aktiven`() // aktiv "B", archiviert "A" mit Faelligkeit vor 5 Tagen: zeilen.map { it.name to it.archiviert } == [("B", false), ("A", true)]
@Test fun `eine archivierte Aktivitaet ohne Faelligkeit im Zeitraum fehlt`() // Faelligkeit vor 40 Tagen, Zeitraum 30 -> nicht enthalten; Zeitraum 90 -> enthalten
// StatistikDarstellungTest
@Test fun `die Zusammenfassung zaehlt jeden Tag genau einmal`() // Bilanzen (0/0, 2/2, 1/3, 0/2): HeatmapZusammenfassung(4, 1, 1, 1, 1)
@Test fun `die Wochen beginnen am Montag wie die Heatmap-Spalten`() // 10 Tage ab Mittwoch 2026-10-07: Groessen [5, 5]; erste Woche beginnt mit 07.10.
```

- [ ] **Step 2: fehlschlagen lassen, umsetzen, grün.** Run: `./gradlew :app:testDebugUnitTest --tests '*Statistik*'`.

- [ ] **Step 3: Volllauf und Commit**

```bash
git add -A app/src
git commit -m "feat(statistik): archivierte Aktivitaeten bei Quoten, Heatmap und Quote fuer TalkBack"
git log -1 --oneline
```

---

### Task 9: Sichtprüfung im Emulator und HANDOFF

**Files:**
- Modify: `docs/HANDOFF.md`

Emulator starten (siehe HANDOFF „Umgebung“), `./gradlew :app:installDebug`. Testdaten heißen „Plan9-Test …“. Vor jedem Tipp `uiautomator dump`.

- [ ] **Step 1: Toasts.** Zwei „Plan9-Test“-Aktivitäten fällig machen, beide schnell abhaken, zweimal Rückgängig. Prüfen: beide wieder offen, Punktestand wie vorher.
- [ ] **Step 2: Archiv.** Eine „Plan9-Test“-Aktivität archivieren: Dialog erscheint. Dann Rückgängig: Sie ist wieder da. Erneut archivieren, über „Archiviert (n)“ wiederherstellen. Dasselbe mit einer „Plan9-Test“-Belohnung. **Dann (Entscheidung 10) Magnesium, Rücken, Tabletten und Vitamin über den Archivbereich wiederherstellen**, sonst nichts Vorhandenes anfassen.
- [ ] **Step 3: Editorfehler** lässt sich im Emulator nicht ohne Import auslösen. Er gilt durch die Tests in Task 3 als geprüft, das steht so im Bericht.
- [ ] **Step 4: Optionen.** Stepper bedienen und auf den Ausgangswert zurücksetzen. Import wird **nicht** ausgeführt (Emulator-Regel), er gilt durch die Tests in Task 5 als geprüft.
- [ ] **Step 5: Widget.** Den Widget-Katalog öffnen: Die Vorschau zeigt das Prototyp-Layout. Eine „Plan9-Test“-Aktivität mit fester Uhrzeit in 3 Minuten anlegen und das Widget beobachten: „überfällig“ erscheint spätestens ca. 1 Minute nach der Uhrzeit. `adb shell dumpsys alarm | grep -A3 WIDGET_TAKT` zeigt den gestellten Alarm. Systemschrift 200 %, minimale Widgethöhe: kein abgeschnittener Text. Danach die Schrift zurück auf 100 %.
- [ ] **Step 6: Statistik.** TalkBack einschalten und die Heatmap antippen: Die Zusammenfassung wird gelesen, Wischen geht Woche für Woche. Die wiederhergestellten vier und eine archivierte „Plan9-Test“-Aktivität erscheinen korrekt. TalkBack wieder aus.
- [ ] **Step 7: Testdaten aufräumen.** Alle „Plan9-Test“-Einträge archivieren.
- [ ] **Step 8: HANDOFF aktualisieren.** In „Wo wir stehen“ Plan 9 nachtragen. Unter „Offene Punkte“ die erledigten Punkte streichen. Stehen bleiben: Dateidialog, feste Uhrzeit vorbei, Testhygiene, doppelte Stilblöcke, Testbuchungen −10/+1 im Emulator. Neue Funde aus dem Abschlussreview kommen dazu. In „Architektur“ einen Satz zu `ToastStapel` und `WidgetWecker` ergänzen.

```bash
git add docs/HANDOFF.md
git commit -m "docs: HANDOFF auf Stand Plan 9"
git log -1 --oneline
```
