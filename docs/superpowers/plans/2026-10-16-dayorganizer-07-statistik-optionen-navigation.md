# DayOrganizer Redesign 4: Statistik, Optionen und Navigationsleiste Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Statistik- und Optionen-Tab sowie die untere Navigationsleiste auf den Stand des Redesign-Prototyps bringen. Tagesbonus und Kulanzfenster werden per Stepper sofort gespeichert. Der Backup-Import lehnt fachlich ungültige Werte ab.

**Architecture:**
- **Statistik:** Die Darstellungslogik liegt in reinen Funktionen in `ui/statistik/StatistikDarstellung.kt`: Heatmap-Stufe je Tag, Gesamtquote, Text der Serienzeile. `StatistikZeile` bekommt Symbol und Periodeneinheit. Die Composables `Heatmap` und `Punkteverlaufsdiagramm` bleiben bestehen und werden nur umgefärbt bzw. erweitert.
- **Optionen:** Das ViewModel hält Tagesbonus und Kulanz als `Int`. Jede Änderung (Stepper, Farbmodus) setzt den Zustand synchron und schreibt danach den **gesamten** Zustand unter einer `Mutex`. Schnelle Mehrfachtipps verlieren so keine Schritte. Der Knopf „Speichern“ entfällt.
- **Navigation:** Die Leiste ist eine eigene Composable statt der Material-`NavigationBar`. Die Navigationslogik (Ziele, `popUpTo`, nur auf Tab-Zielen sichtbar) bleibt unverändert.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3, BOM `2024.12.01`), Hilt, DataStore, kotlinx.serialization, kotlinx.coroutines (inkl. `kotlinx.coroutines.sync.Mutex`), Turbine. Keine neuen Abhängigkeiten.

**Spec:**
- `docs/superpowers/specs/2026-09-18-dayorganizer-design.md` (fachliches Modell).
- Design-Quelle `docs/design/prototyp/DayOrganizer App.dc.html`. Relevant sind die Markup-Blöcke `sStat`, `sOpt`, `dialog` und `showNav`, außerdem in `renderVals()` die Werte `ranges`, `heat`, `LEVELS`, `legend`, `heatQuote`, `chartLine`/`chartArea`/`chartMax`/`chartFrom`, `statRows`, `bonusDec`/`bonusInc`, `kulanzDec`/`kulanzInc`, `themeSeg` und `tabs`.
- Entscheidungen aus der Abstimmung zu Plan 6: Stepper sofort speichernd; Import-Validierung gehört zu diesem Plan.

## Global Constraints

- Minimale API-Version bleibt 33, Ziel-API 35. Keine neue Abhängigkeit, keine neue Berechtigung, kein Netzwerkzugriff.
- **Keine Änderung an Datenmodell, Room-Schema oder Backup-Schema.** Schema bleibt 3, Datenbank Version 5. Die Import-Validierung prüft nur Werte, sie ändert das Format nicht.
- Deutsche Bezeichner. In Kotlin-Kommentaren werden Umlaute ausgeschrieben (`ae`, `oe`, `ue`); `strings.xml` und Kotlin-String-Literale behalten echte Umlaute.
- Farben nur über `DayOrganizerTheme.farben` bzw. `DayOrganizerFesteFarben`, Radien über `DayOrganizerFormen`. Material-`colorScheme` wird in den umgestellten Composables nicht mehr direkt benutzt.
- Nutzersichtbare Compose-Texte stehen in `strings.xml`.
- Stepper-Werte im Prototyp: Tagesbonus und Kulanz in **5er-Schritten**, Minimum **0**, kein Maximum (`bonusDec: Math.max(0, bonus - 5)`).
- Zeiträume der Statistik bleiben **30, 90 und 365 Tage**, Standard 30.
- Navigation: Die fünf Tabs heißen in dieser Reihenfolge „Heute“, „Aktivitäten“, „Belohnung“, „Statistik“, „Optionen“, das sind die vorhandenen Strings `tab_*`. Ihre Symbole sind `check_circle`, `format_list_bulleted`, `redeem`, `insights` und `tune`, gefüllt im aktiven, ungefüllt im inaktiven Tab.
- Es gibt keine Compose-UI-Tests. Logik gehört in reine Funktionen und ViewModels mit JVM-Tests. Ob ein Screen baut, belegt `:app:assembleDebug`.
- Tests, die einen per `flowOn` berechneten Zustand mit Turbine beobachten, geben dem ViewModel ihren Test-Dispatcher (`rechenDispatcher`, seit dem CI-Fix in PR #3). Sie warten nie auf einen echten Hintergrund-Thread.
- Nach jedem Task muss `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` grün sein, bevor committet wird.
- Commit-Nachrichten enden mit `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Sichtprüfungen im Emulator nur mit eigenen Testdaten (Präfix „Plan7-Test“). Vorhandene Aktivitäten und Belohnungen nie ändern, archivieren oder einlösen. Optionen, die der Test verstellt, am Ende auf den Ausgangswert zurücksetzen. Vor jedem Tipp per `uiautomator dump` prüfen, welcher Knoten getroffen wird.

## Review Focus

1. **Schnelle Mehrfachtipps auf einen Stepper** (fünfmal „+“ in einer Sekunde): Erwartet wird der Endwert Ausgangswert + 25, und genau dieser landet im Speicher. Kein Schritt darf durch überlappendes Lesen und Schreiben verloren gehen. → Test in Task 2.
2. **„−“ bei 0:** Der Wert bleibt 0 und wird nie negativ, weder im Zustand noch im Speicher. → Test in Task 2.
3. **Hand-editiertes Backup mit Preis 0, Serienbedingung über 0 Perioden oder einer Aktivität mit 0 Punkten:** Der Import wird schon in der Vorschau als unlesbar abgelehnt. Der Bestand bleibt unverändert, statt einer Belohnung, die immer „Bereit“ zeigt. → Tests in Task 1.
4. **Zeitraum ohne jede Fälligkeit** (neue App, 30 Tage leer): Die Statistik zeigt statt „NaN %“ oder „0 % erledigt“ einen klaren Leerhinweis. Ein Tag mit Fälligkeiten, aber null Erledigten, ist in der Heatmap sichtbar anders gefärbt als ein Tag ganz ohne Fälligkeiten. → Tests in Task 4.
5. **Wochenziel in der Statistik:** Die Serie wird in Wochen gezählt und so beschriftet („Serie 3 Wochen · längste 6 Wochen“), nicht in Tagen. → Test in Task 4.

---

## Datei-Übersicht

| Datei | Zweck |
|---|---|
| `app/.../backup/BackupUmwandlung.kt` | Wertprüfung beim Einlesen |
| `app/.../ui/einstellungen/EinstellungenViewModel.kt` | Int-Zustand, sofort speichernde Stepper, Mutex |
| `app/.../ui/einstellungen/EinstellungenScreen.kt` | vollständiges Redesign inkl. Import-Dialog und Toasts |
| `app/.../ui/gemeinsam/Segmentschalter.kt` (neu) | Hell/Dunkel-Segmentschalter |
| `app/.../ui/statistik/StatistikDarstellung.kt` (neu) | reine Darstellungsfunktionen |
| `app/.../ui/statistik/StatistikZustand.kt`, `StatistikViewModel.kt` | Symbol, Periodeneinheit, Gesamtquote |
| `app/.../ui/statistik/Heatmap.kt`, `Punkteverlaufsdiagramm.kt` | Prototyp-Farben, Stufen, Fläche |
| `app/.../ui/statistik/StatistikScreen.kt` | vollständiges Redesign |
| `app/.../ui/gemeinsam/Navigationsleiste.kt` (neu) | Leiste im Prototyp-Stil |
| `app/.../ui/Navigation.kt` | nutzt die neue Leiste |
| `app/src/main/res/values/strings.xml` | neue und entfallene Strings |

`app/...` = `app/src/main/kotlin/de/vanhake/dayorganizer`, Tests unter `app/src/test/kotlin/de/vanhake/dayorganizer`.

---

### Task 1: Backup-Import prüft fachliche Werte

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlung.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlungTest.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModelTest.kt`

**Interfaces:**
- Produces: `BackupUmwandlung.ausDatei(datei)` (und damit `ausText`) wirft `IllegalArgumentException`, wenn
  - eine Belohnung `preisPunkte <= 0` hat,
  - eine `MINDESTSERIE`-Bedingung `bedingungPerioden <= 0` hat,
  - eine Aktivität `basisPunkte <= 0` hat.

  Die Meldung nennt Art und id, z. B. `"Belohnung 3: preisPunkte muss positiv sein"`. `BackupDienst.importieren` bildet das bereits auf `ImportErgebnis.Unlesbar` ab, die Vorschau (`importVorschauAnfordern`) auf `Meldung.IMPORT_UNLESBAR`.

Entscheidung: Mehr Regeln braucht es nicht. Genau diese drei Werte erzeugen die Editoren nie (Stepper-Minima, `pruefen()`), und alle drei hätten sichtbare Folgen: Eine Belohnung wäre immer einlösbar, eine Serienbedingung immer erfüllt, eine Erledigung brächte keine Punkte. Weitere Felder (Kulanz, Nachfassen) haben Standardwert-Fallbacks.

- [ ] **Step 1: Failing Tests in `BackupUmwandlungTest`**

Drei Tests, je einer pro Regel. Jeder baut über `BackupUmwandlung.zuDatei(...)` mit den vorhandenen Fixtures eine Datei, bei der genau ein Wert ungültig ist (`belohnung.copy(preisPunkte = 0)`, `belohnung.copy(bedingung = MindestSerie(1, 0))`, `aktivitaet.copy(basisPunkte = 0)`), und erwartet:

```kotlin
val fehler = assertFailsWith<IllegalArgumentException> { BackupUmwandlung.ausDatei(datei) }
assertTrue("preisPunkte" in fehler.message.orEmpty())   // bzw. "bedingungPerioden" / "basisPunkte"
```

Testnamen:
- `eine Belohnung mit Preis 0 wird beim Einlesen abgelehnt`
- `eine Serienbedingung ueber 0 Perioden wird beim Einlesen abgelehnt`
- `eine Aktivitaet mit 0 Punkten wird beim Einlesen abgelehnt`

Zusätzlich muss ein Grenztest grün bleiben: `ein Preis von 1 und eine Serie ueber 1 Periode sind gueltig`, mit erfolgreichem Rundlauf.

- [ ] **Step 2: Failing Test in `EinstellungenViewModelTest`**

`eine fachlich ungueltige Datei zeigt keine Rueckfrage, sondern IMPORT_UNLESBAR`: Er nutzt den vorhandenen Helfer `backupText(...)`, erweitert um einen optionalen Parameter `belohnungen: List<Belohnung> = emptyList()`, mit `Belohnung(1, "Eis", preisPunkte = 0)` und `FakeBackup` wie die Nachbartests. Nach `importVorschauAnfordern { text.byteInputStream() }` und `advanceUntilIdle()` gilt `importVorschau.value == null` und `meldung.value == Meldung.IMPORT_UNLESBAR`.

- [ ] **Step 3: Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.backup.BackupUmwandlungTest" --tests "de.vanhake.dayorganizer.ui.einstellungen.EinstellungenViewModelTest"`
Expected: Die vier neuen Tests sind rot, weil keine Ausnahme kommt bzw. eine Vorschau erscheint.

- [ ] **Step 4: Prüfung in `ausDatei` ergänzen**

`require(...)` in den Mappern `BelohnungDto.zuDomaene()` (Preis, Perioden bei `MINDESTSERIE`) und `AktivitaetDto.zuDomaene()` (Basispunkte), neben den bestehenden `requireNotNull`-Prüfungen. Dazu ein KDoc-Satz an `ausDatei`, warum genau diese drei Werte geprüft werden.

- [ ] **Step 5: Tests grün, Gesamtlauf, Commit**

Run: `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL. Die eingefrorenen Beispieldateien für Schema 2 und 3 bleiben lesbar, denn ihre Werte sind positiv.

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlung.kt app/src/test/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlungTest.kt app/src/test/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModelTest.kt
git commit -m "feat(backup): Import lehnt Preis, Serienperioden oder Punkte von 0 ab"
```

---

### Task 2: Optionen-ViewModel — Stepper speichern sofort und verlieren keine Schritte

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModel.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenScreen.kt` (nur so weit, dass es kompiliert; Task 3 ersetzt den Screen)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModelTest.kt`

**Interfaces:**
- Produces:
  - `EinstellungenZustand(tagesbonusPunkte: Int = 15, standardKulanzMinuten: Int = 60, themeModus: ThemeModus = HELL, hinweise: List<Hinweis> = emptyList())`. Die Felder `tagesbonusText` und `standardKulanzText` entfallen.
  - `EinstellungenViewModel.tagesbonusAendern(schritt: Int): Job` und `kulanzAendern(schritt: Int): Job`. `schritt` ist +1 oder −1 und wird mit `STEPPER_SCHRITT = 5` multipliziert.
  - Konstanten im `companion object`: `const val STEPPER_SCHRITT = 5`, `const val STEPPER_MIN = 0`.
  - `themeModusAendern(neu: ThemeModus): Job` (Signatur unverändert).
  - `speichern()` **entfällt**.

**Ablauf (verbindlich, weil Test 1 der Review-Focus-Liste daran hängt):**
1. Der neue Wert wird **synchron** aus `_zustand.value` berechnet (`coerceAtLeast(STEPPER_MIN)`) und sofort in `_zustand` gesetzt.
2. Danach startet eine Koroutine, die unter einer privaten `Mutex` (`schreibSperre.withLock { … }`) `schreiben(lesen().copy(tagesbonusPunkte = z.tagesbonusPunkte, standardKulanzMinuten = z.standardKulanzMinuten, themeModus = z.themeModus))` mit `z = _zustand.value` **zum Schreibzeitpunkt** ausführt.
3. Das sichert zwei Dinge: Jeder Schreibvorgang trägt den neuesten Zustand, und ein späterer Lauf überholt nie einen früheren.
4. Danach wird **nicht** `neuLaden()` aufgerufen, sonst spränge die Anzeige bei schnellen Tipps zurück.
5. Nur im Fehlerfall (`versuchen { … }.onFailure`) wird protokolliert und `neuLaden()` aufgerufen, damit die Anzeige wieder den gespeicherten Stand zeigt.
6. `themeModusAendern` läuft über denselben Weg.

- [ ] **Step 1: Bestehende Tests umstellen und neue schreiben (failing)**

Diese Tests prüfen die Textfeld-Semantik und entfallen:
- `Speichern uebernimmt gueltige Werte bei beiden Feldern`
- `Null ist ein gueltiger Wert bei beiden Feldern`
- `ein negativer Tagesbonus wird verworfen …`
- `eine negative Kulanz wird verworfen …`
- `ein unlesbarer Tagesbonus …`
- `eine unlesbare Kulanz …`
- `ein leeres Feld laesst …`
- `speichern setzt den zuvor per themeModusAendern gesetzten Farbmodus nicht zurueck`

`die gespeicherten Werte erscheinen im Zustand` prüft jetzt `tagesbonusPunkte`/`standardKulanzMinuten` als `Int`.

Neue Tests. Jeder wartet zuerst, bis der Zustand geladen ist (`vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }` wie bisher), und nutzt den vorhandenen `FakeSpeicher` (Start `Einstellungen()` = Bonus 15, Kulanz 60).

Der Testhelfer `viewModel(...)` bekommt zwei optionale Parameter:
- `schreibVerzoegerung: Long = 0`: Das `schreiben`-Lambda wartet per `delay(schreibVerzoegerung)` vor dem Setzen von `speicher.stand.value`.
- `schreibFehler: Boolean = false`: Das Lambda wirft eine `IOException`.


```kotlin
// `ein Tipp auf Plus erhoeht den Tagesbonus um 5 und speichert sofort`
vm.tagesbonusAendern(+1).join()
assertEquals(20, vm.zustand.value.tagesbonusPunkte)
assertEquals(20, speicher.stand.value.tagesbonusPunkte)

// `fuenf schnelle Tipps verlieren keinen Schritt`   (Review Focus 1)
// Ein FakeSpeicher, dessen schreiben() per delay(50) verzoegert, damit sich die Laeufe ueberlappen.
repeat(5) { vm.tagesbonusAendern(+1) }
advanceUntilIdle()
assertEquals(40, vm.zustand.value.tagesbonusPunkte)
assertEquals(40, speicher.stand.value.tagesbonusPunkte)

// `Minus bei 0 bleibt bei 0`   (Review Focus 2)  — Start Einstellungen(tagesbonusPunkte = 0, standardKulanzMinuten = 0)
vm.tagesbonusAendern(-1).join(); vm.kulanzAendern(-1).join()
assertEquals(0, speicher.stand.value.tagesbonusPunkte)
assertEquals(0, speicher.stand.value.standardKulanzMinuten)

// `Kulanz und Tagesbonus ueberschreiben sich nicht gegenseitig`
vm.tagesbonusAendern(+1); vm.kulanzAendern(-1); advanceUntilIdle()
assertEquals(Einstellungen(tagesbonusPunkte = 20, standardKulanzMinuten = 55), speicher.stand.value)

// `ein Stepper-Tipp setzt den Farbmodus nicht zurueck`
vm.themeModusAendern(ThemeModus.DUNKEL); vm.tagesbonusAendern(+1); advanceUntilIdle()
assertEquals(ThemeModus.DUNKEL, speicher.stand.value.themeModus)

// `ein gescheitertes Schreiben zeigt wieder den gespeicherten Stand`
// schreiben wirft IOException → nach advanceUntilIdle: zustand.tagesbonusPunkte == 15
```

Der Test zu `themeModusAendern schreibt sofort, ohne auf Speichern zu warten` bleibt.

- [ ] **Step 2: Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.ui.einstellungen.EinstellungenViewModelTest"`
Expected: Kompilierfehler (`tagesbonusPunkte`, `tagesbonusAendern(Int)`).

- [ ] **Step 3: ViewModel umbauen**

Nach dem Ablauf oben. Den bestehenden Kommentar zum DataStore-Fehler (IOException) beim neuen Schreibweg übernehmen. Eine gemeinsame private Funktion `uebernehmen(): Job` kapselt die Schritte 2 bis 5.

Den alten `EinstellungenScreen` so anpassen, dass er kompiliert: Die zwei Textfelder werden durch `StepperZeile` + `Stepper` aus `ui/gemeinsam` ersetzt, der Knopf „Speichern“ entfällt. Das ist Zwischenstand bis Task 3.

- [ ] **Step 4: Tests grün, Gesamtlauf, Commit**

Run: `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.

```bash
git commit -m "feat(optionen): Tagesbonus und Kulanz per Stepper sofort speichern"
```

---

### Task 3: Optionen-Bildschirm im neuen Design

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenScreen.kt` (vollständig ersetzen)
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/Segmentschalter.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `EinstellungenViewModel` (Task 2), `zustand`, `meldung`, `meldungGezeigt()`, `importVorschau`, `importVorschauAnfordern`, `importVorschauAbbrechen`, `importBestaetigen`, `exportieren`, `exportDatum`, `beimSichtbarwerden()`. Außerdem `StepperZeilenInhalt`, `Stepper`, `HinweistoastLeiste`, `WarnBanner` aus `ui/gemeinsam`.
- Produces:
  - `data class Segment<T>(val wert: T, val titelId: Int, val symbol: ImageVector)`
  - `@Composable fun <T> Segmentschalter(segmente: List<Segment<T>>, gewaehlt: T, waehlen: (T) -> Unit, modifier: Modifier = Modifier)`

**Aufbau (Prototyp `sOpt`, Werte in px = dp):**
- Überschrift „Optionen“ (32 sp, ExtraBold, `tab_einstellungen`).
- Darunter die Warnbanner wie bisher (`WarnBanner`, Behebung über `einstellungsIntent`).
- Abschnittstitel „PUNKTE“, „DARSTELLUNG“ und „BACKUP“: 13 sp, ExtraBold, Großbuchstaben, `letterSpacing 0.06.em`, `ink2`, Innenabstand oben 8.
- **Punkte-Karte** (Radius 22 = `DayOrganizerFormen.listenKarte`, `surface`):
  - Zwei `StepperZeilenInhalt`: „Tagesbonus“ / „wenn alles erledigt ist“ und „Kulanzfenster“ / „Standard in Minuten“.
  - Dazwischen `HorizontalDivider` in `line` mit Innenabstand 16.
  - Die Stepper bekommen `verringernMoeglich = wert > STEPPER_MIN`.
- **Darstellung:** `Segmentschalter`:
  - Hintergrund `surface2`, Radius 18 (`feld`), Innenabstand 4, Abstand 4, zwei gleich breite Segmente, Höhe 44, Radius 14 (`kachel`).
  - Aktives Segment `surface`/`ink`, inaktives transparent/`ink2`.
  - Symbol 18 dp vor dem Text, 14 sp Bold.
  - Segmente „Hell“ mit `Icons.Filled.LightMode` und „Dunkel“ mit `Icons.Filled.DarkMode`.
  - Auswahl über `selectable(role = Role.Tab)`.
- **Backup-Karte** (`listenKarte`, `surface`), zwei Zeilen:
  - Jede Zeile hat eine Symbolkachel 40 dp (Radius 13 ≈ `kachel`, `accentSoft`/`onAccentSoft`), Titel 15 sp Bold, Untertitel 12 sp `ink2` und rechts `Icons.AutoMirrored.Filled.KeyboardArrowRight` in `ink2`. Getrennt durch einen Divider.
  - Zeile 1: `Icons.Filled.Download`, „Backup speichern“, Untertitel = vorgeschlagener Dateiname (`backup_dateiname` mit `exportDatum`). Startet wie bisher `CreateDocument`.
  - Zeile 2: `Icons.Filled.Upload`, „Backup einlesen“, „ersetzt den aktuellen Bestand“. Startet wie bisher `OpenDocument` mit denselben drei MIME-Typen.
- **Fußzeile:** „Läuft vollständig lokal · kein Konto, kein Server“, zentriert, 12 sp, `ink2`, Innenabstand oben 10.
- **Import-Dialog** (Prototyp `dialog`): eigener `Dialog` mit `surface`, Radius 28 (`heldenKarte`), Innenabstand 24 und Abstand 14:
  - Symbolkachel 48 dp `coralSoft`/`coral` mit `Icons.Filled.UploadFile`.
  - Titel „Backup importieren?“ (22 sp, ExtraBold).
  - Text `backup_import_vorschau_text` (14 sp, `ink2`).
  - Rechts die Knöpfe „Abbrechen“ (Text, `ink`) und „Importieren“ (`coral` mit `Color.White`, Radius 14).
- **Meldungen** (Export und Import): Statt Snackbar wie in Plan 6 die `HinweistoastLeiste` unten, ohne Rückgängig. Sie verschwindet nach 3 800 ms (`LaunchedEffect(meldung) { delay; meldungGezeigt() }`).

Strings:
- Neu: `optionen_abschnitt_punkte` „Punkte“, `optionen_abschnitt_darstellung` „Darstellung“, `optionen_abschnitt_backup` „Backup“ (Großschreibung per `uppercase()` im Code), `tagesbonus_titel` „Tagesbonus“, `tagesbonus_hinweis` „wenn alles erledigt ist“, `kulanz_titel` „Kulanzfenster“, `kulanz_hinweis` „Standard in Minuten“, `backup_einlesen_hinweis` „ersetzt den aktuellen Bestand“, `optionen_fusszeile` „Läuft vollständig lokal · kein Konto, kein Server“.
- Entfallen: `feld_tagesbonus`, `feld_standard_kulanz`. `speichern` wird von den Editoren weiter genutzt und bleibt.
- Mit `grep` sicherstellen, dass entfernte Keys keine Nutzer mehr haben.

- [ ] **Step 1: `Segmentschalter.kt` anlegen** (Signatur oben). Baut mit `assembleDebug`.
- [ ] **Step 2: Strings ergänzen bzw. entfernen**
- [ ] **Step 3: `EinstellungenScreen.kt` nach dem Aufbau oben ersetzen.** Unverändert übernommen werden der `LifecycleResumeEffect` für `beimSichtbarwerden()` mit seinem Kommentar, die beiden `rememberLauncherForActivityResult`-Blöcke mit ihren Kommentaren (Review-Funde K1 und G5) und die `exportDatum`-Nutzung (Review-Fund W2).
- [ ] **Step 4: Gesamtlauf** `./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL. `grep -rn "feld_tagesbonus\|feld_standard_kulanz\|SnackbarHost" app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen` liefert keine Treffer.
- [ ] **Step 5: Commit** `feat(optionen): Optionen im neuen Design mit Segmentschalter, Backup-Karten und Import-Dialog`

---

### Task 4: Statistik-Logik — Heatmap-Stufen, Gesamtquote, Serienzeile mit Einheit

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/statistik/StatistikDarstellung.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/statistik/StatistikZustand.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/statistik/StatistikViewModel.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/statistik/StatistikDarstellungTest.kt` (neu)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/statistik/StatistikViewModelTest.kt`

**Interfaces:**
- Consumes: `TagesBilanz(tag, erledigt, gesamt)`, `AktivitaetStatistik` (`:domain`), `Zeitplan.Wochenziel`.
- Produces in `StatistikDarstellung.kt`:
  - `fun heatStufe(bilanz: TagesBilanz): Int` mit Werten 0..4:
    - 0 = keine Fälligkeit (`gesamt == 0`)
    - 1 = Anteil ≤ 1/3, **auch 0**, damit „belegt, nichts geschafft“ sichtbar anders ist als „nichts geplant“; das ist dieselbe Absicht wie die bisherige `MINDESTANTEIL_BELEGTER_TAG`
    - 2 = Anteil ≤ 2/3
    - 3 = Anteil < 1
    - 4 = alles erledigt
  - `const val HEAT_STUFEN = 5`
  - `fun gesamtQuoteProzent(bilanzen: List<TagesBilanz>): Int?` = `round(100 * Σerledigt / Σgesamt)`; `null`, wenn `Σgesamt == 0`.
  - `fun serieText(aktuell: Int, laengste: Int, inWochen: Boolean): String`. Ergebnis `"Serie 9 Tage · längste 21 Tage"` bzw. `"Serie 3 Wochen · längste 6 Wochen"`, Singular bei 1: „1 Tag“, „1 Woche“. Kotlin-Literal wie `PlanText`, aus demselben Grund (testbar ohne Context).
  - `fun metaText(erledigt: Int, verpasst: Int): String` = `"$erledigt geschafft · $verpasst verpasst"`.
- `StatistikZustand.kt`: `AktivitaetZeile` heißt künftig `StatistikZeile`, um die Namensgleichheit mit `ui.aktivitaeten.AktivitaetZeile` aufzulösen. Sie bekommt `symbol: String` und `serieInWochen: Boolean`. `StatistikZustand` bekommt `gesamtQuote: Int?` (Default `null`).
- `StatistikViewModel` füllt die neuen Felder (`symbol = aktivitaet.symbol`, `serieInWochen = aktivitaet.zeitplan is Zeitplan.Wochenziel`, `gesamtQuote = gesamtQuoteProzent(bilanzen)`).

- [ ] **Step 1: Failing Tests `StatistikDarstellungTest`**
  - `heatStufe`:
    - `TagesBilanz(t, 0, 0)` → 0; `(t, 0, 3)` → 1 (Review Focus 4); `(t, 1, 3)` → 1; `(t, 2, 3)` → 2; `(t, 3, 4)` → 3; `(t, 3, 3)` → 4.
    - Für jede Bilanz gilt `heatStufe in 0 until HEAT_STUFEN`.
  - `gesamtQuoteProzent`: `[(t,1,2),(t,2,2)]` → 75; leere Liste → `null`; nur `gesamt == 0` → `null` (Review Focus 4); `[(t,1,3)]` → 33.
  - `serieText(9, 21, false)` → `"Serie 9 Tage · längste 21 Tage"`; `serieText(1, 1, false)` → `"Serie 1 Tag · längste 1 Tag"`; `serieText(3, 6, true)` → `"Serie 3 Wochen · längste 6 Wochen"` (Review Focus 5).
  - `metaText(56, 2)` → `"56 geschafft · 2 verpasst"`.
- [ ] **Step 2: Failing Test im ViewModel-Test** `der Zustand traegt Symbol, Wochen-Einheit und Gesamtquote`: `sport` (Wochenziel) mit `symbol = "fitness_center"` → `zeile.serieInWochen == true`, `zeile.symbol == "fitness_center"`. Fälligkeiten wie im ersten Test der Datei → `zustand.gesamtQuote == 67`. Dazu ein Test ohne Fälligkeiten → `gesamtQuote == null`. Der Test gibt `rechenDispatcher = dispatcher` wie die übrigen.
- [ ] **Step 3: Fehlschlag sehen** `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.ui.statistik.*"` → Kompilierfehler.
- [ ] **Step 4: Implementieren** nach den Signaturen oben. Die Umbenennung `AktivitaetZeile` → `StatistikZeile` gilt nur im Paket `ui.statistik`; der alte `StatistikScreen` wird nur soweit angepasst, dass er kompiliert.
- [ ] **Step 5: Gesamtlauf, Commit** `feat(statistik): Heatmap-Stufen, Gesamtquote und Serienzeile mit Einheit`

---

### Task 5: Statistik-Bildschirm im neuen Design

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/statistik/StatistikScreen.kt` (vollständig ersetzen)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/statistik/Heatmap.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/statistik/Punkteverlaufsdiagramm.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `StatistikZustand` mit `zeitraumTage`, `bilanzen`, `zeilen: List<StatistikZeile>`, `punkteVerlauf`, `gesamtQuote` (Task 4); `heatStufe`, `HEAT_STUFEN`, `serieText`, `metaText` (Task 4); `aktivitaetSymbol`.
- Produces: `fun heatFarbe(stufe: Int, farben: DayOrganizerFarben): Color` in `Heatmap.kt`, öffentlich, weil die Legende sie mitbenutzt.
  - Stufen 0..4 = `surface2`, Mischungen von `accent` in `surface2` zu 28 %, 52 % und 76 %, und `accent`, wie im Prototyp `LEVELS`.
  - Die Mischung ist linear in sRGB, wie das bisherige `mische()`. Die Prototyp-Mischung in oklch ist dafür eine bewusste Vereinfachung.

**Aufbau (Prototyp `sStat`):**
- Überschrift „Statistik“ (32 sp, ExtraBold).
- **Zeitraum-Pillen** 30/90/365:
  - Höhe 40, Radius `pille`, Innenabstand 16, 14 sp Bold.
  - Aktiv: `accentSoft`/`onAccentSoft` ohne Rahmen. Inaktiv: transparent/`ink2` mit 1,5-dp-Rahmen in `line`.
  - `selectable`.
- **Heatmap-Karte** (`karte`, `surface`, Innenabstand 16, Abstand 14):
  - Kopf „Erledigt je Tag“ (16 sp, Bold) und rechts die Quote, 13 sp Bold in `accent`: `"N % erledigt"` bzw. `"keine Fälligkeiten"`, wenn `gesamtQuote == null`.
  - Raster wie bisher (Wochenspalten), Kacheln aber als abgerundete Rechtecke in `heatFarbe(heatStufe(b))`.
  - Eckradius nach Zeitraum: 7 dp bei 30, 4 dp bei 90, 2 dp bei 365 Tagen. Abstand: 6 dp bei 30, 3 dp bei 90, 2 dp bei 365 Tagen.
  - Die bisherige Kantenlogik (gedeckelt, mittig) bleibt, der Deckel wird für 30 Tage auf 34 dp angehoben (Prototyp `repeat(weeks, 34px)`).
  - Legende rechtsbündig: „weniger“, fünf Felder 12×12 (Radius 3) und „mehr“, 11 sp SemiBold `ink2`.
- **Punkteverlauf-Karte**:
  - Kopf „Punkteverlauf“ und rechts `"N max."` (13 sp Bold `ink2`, N = Höchstwert des Verlaufs).
  - Das Diagramm ist 130 dp hoch: eine Fläche unter der Linie in `accentSoft` und die Linie 2,5 dp in `accent`, rund verbunden.
  - Fußzeile „vor N Tagen“ links, „heute“ rechts (12 sp SemiBold `ink2`).
  - Die bisherigen Sonderfälle bleiben: ein einzelner Punkt und eine flache Linie.
- **Aktivitätszeilen** (Radius 22 = `listenKarte`, `surface`, Innenabstand 14/16, Abstand 10):
  - Kopfzeile: Symbolkachel 40 dp (`accentSoft`/`onAccentSoft`, Symbol 22 dp), Name (15 sp Bold), `metaText` (12 sp `ink2`) und rechts die Quote `"N%"` (22 sp ExtraBold).
  - Balken 6 dp: `surface2`-Spur, Füllung `accent` mit Anteil = Quote.
  - Fußzeile: `Icons.Filled.LocalFireDepartment` 16 dp in `coral`, dazu `serieText(...)` (12 sp SemiBold `ink2`).
- **Leerer Zustand:** Ohne aktive Aktivitäten erscheint unter den Karten der Hinweis „Noch keine Aktivitäten“ (`statistik_leer`).

Strings:
- Neu: `statistik_quote` „%1$d %% erledigt“, `statistik_keine_faelligkeiten` „keine Fälligkeiten“, `heatmap_weniger` „weniger“, `heatmap_mehr` „mehr“, `punkteverlauf_max` „%1$d max.“, `punkteverlauf_von` „vor %1$d Tagen“, `punkteverlauf_heute` „heute“, `aktivitaet_quote` „%1$d%%“, `statistik_leer` „Noch keine Aktivitäten“, `tab_statistik_titel` „Statistik“ (falls `tab_statistik` künftig gekürzt wird, bleibt die Überschrift stabil).
- Entfallen: `quote_zeile`, `serie_zeile`. Mit `grep` prüfen.

- [ ] **Step 1: `heatFarbe` und das neue Zeichnen in `Heatmap.kt`.** Die Signatur `Heatmap(bilanzen: List<TagesBilanz>, zeitraumTage: Int, modifier: Modifier = Modifier)` bekommt `zeitraumTage` für Radius und Abstand dazu. `MINDESTANTEIL_BELEGTER_TAG` und `mische()` fallen weg, wenn `heatStufe` sie ersetzt; der KDoc-Grund wandert an `heatStufe` in Task 4.
- [ ] **Step 2: `Punkteverlaufsdiagramm`** mit Fläche (`Path` von der Linie bis zur Grundlinie, geschlossen) und Theme-Farben. Signatur unverändert, Höhe 130 dp.
- [ ] **Step 3: Strings**
- [ ] **Step 4: `StatistikScreen.kt` nach dem Aufbau ersetzen**, auf `farben.bg` wie die anderen Tabs, `LazyColumn` mit Innenabstand 14/16/32.
- [ ] **Step 5: Gesamtlauf** `./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL. `grep -rn "quote_zeile\|serie_zeile\|MaterialTheme.colorScheme" app/src/main/kotlin/de/vanhake/dayorganizer/ui/statistik` liefert keine Treffer.
- [ ] **Step 6: Commit** `feat(statistik): Statistik im neuen Design mit Stufen-Heatmap, Flaechendiagramm und Aktivitaetszeilen`

---

### Task 6: Navigationsleiste im Prototyp-Stil und Sichtprüfung des ganzen Plans

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/Navigationsleiste.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt`

**Interfaces:**
- Produces:
  - `data class NavigationsEintrag(val ziel: String, val titelId: Int, val symbolAktiv: ImageVector, val symbolInaktiv: ImageVector)`
  - `@Composable fun Navigationsleiste(eintraege: List<NavigationsEintrag>, aktuell: String?, waehlen: (String) -> Unit)`

**Aufbau (Prototyp `showNav`):**
- Fläche `surface`, oben eine 1-dp-Linie in `line`, Innenabstand 10 oben, 4 seitlich, 6 unten, plus `navigationBarsPadding()`. Fünf gleich breite Spalten.
- Je Eintrag eine Pille 60×32 dp (Radius 16) mit dem Symbol 24 dp, darunter der Titel (12 sp, einzeilig, Auslassungszeichen).
- Aktiv: Pille `accentSoft`, Symbol und Text `onAccentSoft`, Text ExtraBold. Inaktiv: Pille transparent, `ink2`, Text SemiBold.
- `selectable(selected, role = Role.Tab)` je Eintrag; der Titel ist die Bedienhilfe-Beschriftung.

Symbole (gefüllt / ungefüllt):

| Tab | aktiv | inaktiv |
|---|---|---|
| Heute | `Icons.Filled.CheckCircle` | `Icons.Outlined.CheckCircle` |
| Aktivitäten | `Icons.AutoMirrored.Filled.FormatListBulleted` | `Icons.AutoMirrored.Outlined.FormatListBulleted` |
| Belohnung | `Icons.Filled.Redeem` | `Icons.Outlined.Redeem` |
| Statistik | `Icons.Filled.Insights` | `Icons.Outlined.Insights` |
| Optionen | `Icons.Filled.Tune` | `Icons.Outlined.Tune` |

`Navigation.kt`:
- `TABS` wird zur Liste von `NavigationsEintrag` (private `Tab`-Klasse entfällt).
- Statt `NavigationBar { … }` steht `Navigationsleiste(TABS, aktuell) { ziel -> navController.navigate(ziel) { popUpTo(Ziele.HEUTE); launchSingleTop = true } }`.
- Die Bedingung „nur auf Tab-Zielen zeigen“ und ihr Kommentar bleiben unverändert.

- [ ] **Step 1: `Navigationsleiste.kt` anlegen.** Fehlt ein Outlined-Symbol im Extended-Katalog, nimm das Filled-Symbol mit `ink2` und vermerke es im Commit.
- [ ] **Step 2: `Navigation.kt` umstellen.** Nicht mehr benötigte Imports (`NavigationBar`, `NavigationBarItem`, `Icons.Default.*`) entfernen.
- [ ] **Step 3: Gesamtlauf** `./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 4: Sichtprüfung im Emulator, hell und dunkel** (Regeln aus den Global Constraints):
  1. Navigationsleiste auf allen fünf Tabs: aktive Pille, gefüllte bzw. ungefüllte Symbole, keine Leiste auf den Bearbeiten-Bildschirmen.
  2. Statistik: Pillen 30/90/365 schalten um, die Heatmap passt in die Breite (365 Tage ohne Überlauf), Legende, Verlauf mit Fläche, Zeilen mit Quote, Balken und Serie.
  3. Optionen: Tagesbonus fünfmal schnell „+“ tippen, Tab wechseln, zurückkehren → der Wert ist um 25 höher. Danach fünfmal „−“ zurück auf den Ausgangswert. Bei Kulanz einmal „+“ und einmal „−“.
  4. Hell/Dunkel per Segmentschalter wechseln, danach wieder auf den Ausgangswert stellen.
  5. „Backup speichern“ öffnet den Dateidialog des Systems → **abbrechen**, ohne zu speichern. „Backup einlesen“ öffnet die Dateiauswahl → **abbrechen**. Es wird kein Backup importiert.

  Bildschirmfotos ins Scratchpad legen und im Bericht nennen.
- [ ] **Step 5: Commit** `feat(navigation): Navigationsleiste im Prototyp-Stil`

---

## Abschluss dieses Plans

Nach Task 6 sind alle fünf Tabs und die Navigationsleiste auf dem Stand des Prototyps. Offen bleiben:

- **Plan 8:** Homescreen-Widget im neuen Design (Glance-Einschränkungen: kein CSS-Farbverlauf, kein `backdrop-filter`).
- **Backlog aus Plan 6:**
  - Ein neuer Toast verdrängt ein offenes Rückgängig (Heute und Belohnungen).
  - Archivieren hat keine Rückfrage und keine Wiederherstellung.
  - Die Fehlerbehandlung beim Speichern beider Editoren sollte vereinheitlicht werden.
- **Redaktionell:** `README.md` und `docs/HANDOFF.md` auf den Stand von Plan 4–7 bringen.
