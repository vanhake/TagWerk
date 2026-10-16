# Übergabe — Stand und wie es weitergeht

Letzte Aktualisierung: 2026-10-16. Plan 9 umgesetzt (Branch
`worktree-redesign-plan9-aufraeumen`, PR folgt), aufbauend auf Plan 8.

Dieses Dokument soll reichen, um in einer neuen Sitzung ohne Vorwissen
weiterzuarbeiten. Die Wahrheit steht im Code und in `git log`; wo dieses
Dokument davon abweicht, hat der Code recht.

## Wo wir stehen

**Die Pläne 1–9 sind umgesetzt.** Plan 1–3 lieferten Funktionsumfang und
Backup; die Pläne 4–8 haben die ganze App an den Redesign-Prototyp
(`docs/design/prototyp/`) angeglichen:

| Plan | Inhalt |
|---|---|
| 4 | Design-System (Farben, Formen, Manrope), Heute mit Heldenkarte, Aktivitätensymbol, Hell/Dunkel-Modus, Rückgängig für Erledigen/Auslassen |
| 5 | Aktivitäten-Liste und Editor (Symbolraster, Stepper, Typkarten, Plantext) |
| 6 | Belohnungen und Editor, Belohnungssymbol, Einlösen rückgängig (Migration 4→5, Backup-Schema 3) |
| 7 | Statistik (Heatmap-Stufen, Flächendiagramm), Optionen mit sofort speichernden Steppern, Navigationsleiste, Import-Validierung |
| 8 | Homescreen-Widget im Prototyp-Stil (Kopf mit Fortschritt und Punkten, Symbolkacheln, Zeilenzahl nach Höhe, Farben nach App-Einstellung) |
| 9 | Aufräumen: gestapelte Rückgängig-Toasts, Archivieren mit Rückfrage und Archivbereich zum Wiederherstellen, einheitliche Fehlerbehandlung (`versuchen {}`), Import-Sperre für Stepper, Widget-Wecker, Katalogvorschau und Fehlerlayout, Statistik mit archivierten Aktivitäten und TalkBack-Texten |

Plan 4–7 sind auf `main` gemergt; Plan 8 liegt auf dem Branch
`worktree-redesign-plan8-widget`, Plan 9 darauf aufbauend auf
`worktree-redesign-plan9-aufraeumen` (Review gelaufen, PR folgt).
Datenbank Version 5, Backup-Schema 3 (Schema 2 bleibt lesbar).

Was offen ist, steht unter „Offene Punkte“: ein Restbestand kleiner Punkte,
keine fehlende Funktion.

## Umgebung

- **Alle Tests laufen auf der JVM**; Datenbanktests unter Robolectric im
  `test`-Quellverzeichnis. Es gibt bewusst kein `androidTest`-Quellverzeichnis,
  und es gibt keine Compose-UI-Tests: Oberflächen werden übersetzt, aber im Test
  nie ausgeführt — `assembleDebug` ist der einzige Nachweis, dass ein Screen baut.
- **Ein Emulator ist vorhanden und einsatzbereit.** Emulator 36.5.10 und das
  System-Image `android-34/google_apis/x86_64` sind installiert, `/dev/kvm` ist
  per ACL freigegeben. `ANDROID_AVD_HOME` zeigt nach
  `~/.var/app/com.visualstudio.code/config/.android/avd`; dort liegt das AVD
  `LernApp_Pixel` (Pixel 5, Android 14), das auch DayOrganizer trägt:

  ```bash
  ANDROID_AVD_HOME=~/.var/app/com.visualstudio.code/config/.android/avd \
    $ANDROID_HOME/emulator/emulator -avd LernApp_Pixel -no-snapshot-save -no-boot-anim &
  ```

  Zum Steuern taugt `adb exec-out screencap -p` zusammen mit
  `adb shell uiautomator dump`: Knoten über ihren Text suchen und auf die Mitte
  ihrer `bounds` tippen. **Blind nach Bildschirmkoordinaten zu tippen geht schief** —
  die Tab-Leiste fehlt auf den Bearbeiten-Screens, und getippte Stellen landen
  dann auf der Tastatur.
- JDK 17 unter `/home/jan-lin/.local/opt/jdk-17.0.13+11`, Android SDK unter
  `/home/jan-lin/.local/opt/android-sdk` (`ANDROID_HOME` ist gesetzt).
- Gradle-Wrapper 8.9 ist eingecheckt, Gradle muss nicht installiert sein.

Vollständiger Durchlauf:

```bash
./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug
```

## Dokumente, in dieser Reihenfolge lesen

| Datei | Wofür |
|---|---|
| `docs/superpowers/specs/2026-09-18-dayorganizer-design.md` | Der Vertrag. Fachliches Modell, alle Regeln, Architektur. Kurz. |
| `docs/superpowers/plans/2026-09-26-dayorganizer-04-designsystem-heute.md` bis `2026-10-16-dayorganizer-09-aufraeumen.md` | Pläne 04–09: das Redesign und der Aufräumplan. Plan 9 ist der jüngste; jeder Plan nennt oben seine Design-Quelle und Global Constraints. |
| `docs/design/prototyp/` | Die visuelle Vorlage (`DayOrganizer App.dc.html`) mit eigener README. |
| `docs/superpowers/plans/2026-09-18-dayorganizer-0{1,2,3}-*.md` | Pläne 1–3 (Fundament, Belohnungen, Widget und Backup), umgesetzt. |
| `.superpowers/sdd/plan-N/` | Ledger, Aufträge und Berichte je Plan. Nicht eingecheckt. |

## Architektur in sieben Sätzen

`:domain` ist reines Kotlin ohne Android und enthält das Modell plus vier
zustandslose Regelmaschinen: `ZeitplanRechner` (Zeitplan → Fälligkeiten),
`SerienRechner` (Historie → Serienlänge), `PunkteRechner` (Erledigung →
Buchungen) und `StatistikRechner` (Historie → Quoten, Tagesbilanzen,
Punkteverlauf), dazu `BelohnungsPruefer` (Belohnung + Kontostand → einlösbar?).
`:app` enthält Room hinter schmalen Repository-Schnittstellen, die Use Cases,
den Alarm- und Benachrichtigungsapparat und die Compose-Oberfläche.
Der Punktestand ist die Summe eines Buchungsjournals, nie ein Zähler. Es ist zu
jedem Zeitpunkt genau ein exakter Alarm gesetzt, für die nächste fällige
Erinnerung überhaupt. Das Widget (Glance) rechnet seinen Zustand in reinen
Funktionen (`WidgetZustand`, `WidgetMasse`) und liest die Farben über
`widgetFarben(modus)` aus denselben Objekten wie die App. `ToastStapel` hält den
Toast-Zustand der Screens: Rückgängig-Einträge stapeln sich und laufen je nach
3 800 ms ab, Info-Einträge warten, bis kein Rückgängig mehr offen ist.
`WidgetWecker` stellt einen ungenauen `RTC`-Alarm auf den nächsten Umschaltpunkt
des Widgets (Uhrzeit eines offenen Eintrags, sonst Mitternacht), damit
„überfällig“ und der Tageswechsel ohne Datenänderung erscheinen. Jede Klasse, die die
Uhrzeit braucht, bekommt eine `java.time.Clock` injiziert — daran hängt die
gesamte Testbarkeit.

## Konventionen, die durchgehalten werden

- Bezeichner und Kommentare auf Deutsch, in Quelltextkommentaren `ae`/`oe`/`ue`
  statt Umlauten. Testnamen in Backticks als deutsche Sätze — **Ausnahme**
  `DaoTest` und `MigrationTest`, die `unterstrich_namen` benutzen. Folge dem
  Stil der Datei, die du anfasst.
- Sichtbare Texte ausschließlich in `app/src/main/res/values/strings.xml`.
- Zwei-Konstruktoren-Muster für testbare Use Cases und ViewModels: ein
  primärer, der Funktionen statt Android-Abhängigkeiten nimmt, und **genau ein**
  `@Inject`-Konstruktor, der adaptiert. Dagger duldet nur einen.
- Die Fakes in `app/src/test/kotlin/de/vanhake/dayorganizer/Fakes.kt` müssen die
  Room-Semantik **exakt** nachbilden, Sortierung eingeschlossen. Bei jeder neuen
  DAO-Methode den Fake mit der SQL vergleichen, nicht mit der Erinnerung daran.
  In Plan 2 wich der im Plan vorgegebene Fake an fünf Stellen von der echten
  Abfrage ab und wurde nur deshalb gefangen, weil der Implementierer den Auftrag
  hatte, genau darauf zu achten.
- Ein `@Update`, dessen Ergebnis niemand prüft, meldet Misserfolg als Erfolg.
  `RaumAktivitaetRepository` und `RaumBelohnungRepository` werten deshalb die
  Zeilenzahl mit `check(...)` aus. Neue Repositories genauso.
- Conventional Commits, `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.

## Vorgehen, das sich bewährt hat

Alle Pläne liefen subagent-getrieben (`superpowers:subagent-driven-development`):
pro Task ein frischer Implementierer, danach ein Reviewer mit Spec- und
Qualitätsurteil, Fixes bis sauber, Fortschritt im Ledger, am Ende ein Review
über den gesamten Branch.

Die Funde aus Plan 1 und 2, die diese Arbeitsweise begründet haben (die Pläne
4–8 stehen im Block darunter):

**Der Befund aus Plan 1 hat sich in Plan 2 bestätigt und verschärft: fast alle
gefundenen Fehler steckten im Plan, nicht in der Umsetzung.** In Plan 2 unter
anderem: doppelte `LazyColumn`-Keys, die beim bloßen Öffnen des Belohnungs-Tabs
abstürzen; ein Datumsfeld, in das sich nichts eintippen ließ und das beim
Speichern die App mitriss; ein Doppeltipp, der zweimal abbuchte; ein
Punkteverlauf, der die Netto-Änderung statt des Kontostands zeigte; ein
Wochenziel, das in der Heatmap sieben erledigte Tage meldete.

Vier Techniken tragen das und sollten beibehalten werden:

1. **Mutationsnachweis.** Nach jedem Fix den Produktionscode absichtlich kaputt
   machen, prüfen, dass der neue Test umfällt, und wiederherstellen. In Plan 2
   hat das zweimal einen scheinbar guten Test als wirkungslos entlarvt — einmal
   einen Grenzfall-Test, der mit nur einem Datensatz zwei Implementierungen gar
   nicht unterscheiden konnte, und einmal einen Tiebreaker-Test, der
   grundsätzlich nicht falsifizierbar ist. Beide Befunde stehen so im Code
   dokumentiert, statt einen Nachweis vorzutäuschen.
2. **Wenn ein Fix bestehende Tests bricht, ist die Anpassung selbst
   reviewpflichtig.** Genau das ging in Plan 2 einmal schief: ein `flowOn`
   brach zwei Tests, die Anpassung machte sie wieder grün — und entschärfte
   dabei den Wächter für die Doppeltipp-Sperre. „Test wieder grün" ist kein
   Nachweis, dass er noch bewacht, was er bewacht hat. Erneut mutieren.
3. **Einmal auf dem Emulator durchspielen, sobald ein Screen fertig ist.** Der
   erste Lauf hat drei Darstellungsfehler bestätigt, die das Abschlussreview
   allein durch Lesen vorhergesagt hatte — und einen vierten gefunden, den
   niemand vorhergesehen hatte. Umgekehrt hat er nichts Falsches gemeldet: was
   die JVM-Tests zusicherten, hielt auch auf dem Gerät.
4. **Dem Reviewer sagen, worauf er achten soll.** Generische Reviews finden
   wenig. Die scharfen Funde kamen, wenn der Prompt eine konkrete Sorge nannte
   („bildet der Fake die Sortierung nach?", „zählt ein Wochenziel an allen
   sieben Tagen mit?", „sichert der Transaktionstest wirklich etwas zu?").

Aus den Plänen 4–8 kommt dazu:

- **Aufträge als Dateien.** Implementer- und Reviewer-Anweisungen, Task-Briefs
  und Berichte liegen als Dateien unter `.superpowers/sdd/plan-N/`; der Prompt
  verweist nur darauf. So bleibt jeder Auftrag nachlesbar.
- **Commit prüfen.** Nach jedem Commit `git log -1 --oneline` ansehen. Ein
  verweigerter Befehl hat einmal einen Commit gemeldet, den es nicht gab.
- **`rechenDispatcher`.** ViewModels, die einen Zustand per `flowOn`
  berechnen, nehmen einen injizierbaren `rechenDispatcher`; Turbine-Tests geben
  ihm den Test-Dispatcher, sonst ist der Zustand nicht deterministisch.
- **Emulator-Regeln.** Nur eigene Testdaten mit Präfix (z. B. „Plan8-Test“),
  vorhandene Aktivitäten und Belohnungen nie ändern, archivieren, abhaken oder
  einlösen. Vor jedem Tipp per `uiautomator dump` prüfen, welcher Knoten
  getroffen wird. Verstellte Optionen (Hell/Dunkel, Stepper) auf den
  Ausgangswert zurücksetzen. Nie ein Backup speichern oder importieren.

Zwei Fallen: Subagenten räumen gelegentlich das Arbeitsverzeichnis auf —
**Controller-Änderungen sofort committen**, bevor ein Subagent startet. Und
Task-Berichte heißen `task-N-report.md`; in Plan 2 überschrieb das anfangs die
Berichte von Plan 1. Die Artefakte liegen jetzt getrennt unter
`.superpowers/sdd/plan-1/` und `.superpowers/sdd/plan-2/`.

## Offene Punkte

Restbestand nach Plan 9. Nichts davon blockiert einen Merge.

- **Dateidialog.** Nach dem Abbrechen des System-Dateidialogs stand die App im
  Emulator wieder auf „Heute“. Eine Ursache im Code ist nicht gefunden.
- **Feste Uhrzeit, die schon vorbei ist.** Wird eine Aktivität mit fester
  Uhrzeit angelegt, die heute bereits vorbei ist, entsteht für heute keine
  Fälligkeit. Beobachtet, nicht geprüft, ob das gewollt ist.
- **Testhygiene.** `org.junit.Test` ist mit `kotlin.test` gemischt; für Abbruch
  und `NonCancellable` beim Widget-Anstoß gibt es keinen Test; der Drawable-Test
  prüft nur die Ladbarkeit; `self_improvement` ist nur über `else` abgedeckt.
- **Doppelte Stilblöcke im Widget.** Fußzeile und „Heute steht nichts an“
  wiederholen denselben `TextStyle`.
- **Testbuchungen im Emulator.** Dort stehen aus Plan 6 eine Test-Einlösung über
  −10 Punkte und aus Plan 8 eine Testbuchung über +1 Punkt. Aus der
  Plan-9-Sichtprüfung kommen +30 Punkte auf inzwischen archivierten
  „Plan9-Test“-Aktivitäten dazu.

### Neu aus Plan 9

- **Heatmap-Zusammenfassung fehlt im Bedienungshilfen-Baum.** Im Emulator
  erscheinen nur die Wochentexte; das Canvas-Blatt mit der Zusammenfassung wird
  vollständig vom Wochen-Overlay verdeckt, und Compose lässt verdeckte Knoten weg.
  TalkBack kann die Zusammenfassung so nicht vorlesen.
- **Widget-Wecker auf Android 14.** Das System dehnt das Alarmfenster
  (`min_window` 10 Minuten); „überfällig“ kam im Emulator gut 2 Minuten nach der
  Uhrzeit, um Mitternacht sind bis zu 10 Minuten möglich.
- **Doppeltipp auf Rückgängig** nimmt bei gestapelten Einträgen zwei verschiedene
  Aktionen zurück.
- **Fehlermeldung in Belohnungen** wartet bis zu 3,8 s hinter einem offenen
  Rückgängig-Toast.
- **Leerer Archivbereich** erzeugt in Aktivitäten und Belohnungen einen
  Abstand am Listenende.
- **Editor-Fehlertoast.** Derselbe Speicherfehler zweimal hintereinander startet
  die Anzeigedauer nicht neu.
- **Ausnahmen nach erfolgreicher Buchung** (Alarm, Widget, Benachrichtigung) in
  Heute und im Aktionsempfänger werden nicht gefangen.
- **Stepper und Import.** Ein fehlgeschlagener Stepper-Schreibvorgang lädt
  außerhalb der Sperre neu und kann die Import-Anzeige mitten im Import
  freigeben; ein zweiter Import während eines laufenden lässt sie flackern.
- **Widget-Abhaken.** Das Lesen der Fälligkeit in `WidgetAbhaken` ist
  ungeschützt; ein Datenbankfehler entkommt dem `ActionCallback`.
- **Widget über Mitternacht.** Eine offene Glance-Sitzung zeigt nach Mitternacht
  bis zur nächsten Neuzeichnung die Liste von gestern (ein Tipp bucht nicht mehr);
  `WidgetWecker` mischt die Zeitzone der Uhr mit `systemDefault`.
- **Widget-Vorschau.** Kartenradius 28 dp statt des Systemradius, ohne
  Medium-Schriftgewicht.
- **Statistik.** Die Tagesklassifikation steht doppelt (`Heatmap.kt` und
  `StatistikDarstellung.kt`), Wochenspalten werden bei 365 Tagen sehr schmal
  (ca. 6 dp), ein Kommentar in `Heatmap.kt` nennt noch den Container als Träger
  der Zusammenfassung.
- **Testlücken.** Unter anderem: kein Test sichert `setWindow` (`FENSTER_MS`),
  Fehlerpfade von Wiederherstellen/Rückgängig/Auslassen und der Abbruch im
  Editor sind ungetestet, Statistik-Fixtures sind schmal.
- **Kleinkram.** Stepper-Handler nutzen `value = copy` statt `update {}`;
  `versuchen` um das Nachbereiten im Widget loggt nicht; ein Testname in
  `BelohnungenViewModelTest` widerspricht seiner Zusicherung.

## Datenbank

Version 5. Migrationen 1→2 (Tabelle `erinnerung`), 2→3 (`belohnung` und
`einloesung`), 3→4 (Symbol der Aktivität) und 4→5 (Symbol der Belohnung,
Stornierung von Einlösungen) sind vorhanden und werden im Test gegen die
exportierten Schemata validiert. `app/schemas/.../1.json` bis `5.json` sind
eingecheckt und müssen es bleiben — der Migrationstest liest sie. Es ist
**kein** `fallbackToDestructiveMigration` gesetzt, und das muss so bleiben: die
Daten liegen ausschließlich lokal. Plan 7 bis 9 haben weder Datenbank noch
Backup-Schema geändert.

**Eine Falle bei der nächsten Versionshebung:** Der erste Lauf, in dem Room eine
neue Schema-Datei erzeugt, scheitert mit `FileNotFoundException: Missing file:
.../N.json` in `mergeDebugAssets`, weil `app/schemas` außerhalb des
Build-Verzeichnisses liegt. Ein zweiter, identischer Lauf geht durch.

**Das Backup trägt eine eigene Version.** `BackupUmwandlung.SCHEMA_VERSION` steht
auf 3 (Schema 2 brachte Aktivitätensymbol und Farbmodus, Schema 3 das
Belohnungssymbol und das Storno). Schema 2 bleibt lesbar, weil die neuen Felder
Standardwerte haben. `app/src/test/resources/backup/` enthält die eingefrorenen
Beispieldateien als Vertrag: Wer ein DTO-Feld, eine Enum-Konstante oder die
Kodierung eines Zeitplans ändert, macht bestehende Backups unlesbar — dann
braucht es die nächste Version und einen Lesepfad für die alten, keine
angepasste Testdatei. Der Import lehnt Preis, Serienperioden oder Punkte von 0
ab.

## So geht es weiter

```
1. PRs für Plan 8 und Plan 9 öffnen und mergen (Branches
   worktree-redesign-plan8-widget, dann worktree-redesign-plan9-aufraeumen).
2. Auf einem echten Handy durchspielen.  ./gradlew :app:installDebug
   Was der Emulator nicht zeigen kann: ob die Erinnerung auf einem konkreten
   Gerät um 8:00 ankommt und der Hersteller sie nicht wegoptimiert, wie das
   Widget auf dem Launcher des Geräts aussieht und ob der Dateidialog bei
   Google Drive die .json-Datei anbietet (ungeprüft).
3. Den Restbestand unter „Offene Punkte“ sichten, zuerst die Heatmap-Zusammenfassung.
```
