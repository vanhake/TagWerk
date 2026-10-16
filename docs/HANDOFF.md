# Übergabe — Stand und wie es weitergeht

Letzte Aktualisierung: 2026-10-16, Branch `worktree-redesign-plan8-widget`
(Plan 8, nach dem Merge von Plan 7).

Dieses Dokument soll reichen, um in einer neuen Sitzung ohne Vorwissen
weiterzuarbeiten. Die Wahrheit steht im Code und in `git log`; wo dieses
Dokument davon abweicht, hat der Code recht.

## Wo wir stehen

**Die Pläne 1–8 sind umgesetzt.** Plan 1–3 lieferten Funktionsumfang und
Backup; die Pläne 4–8 haben die ganze App an den Redesign-Prototyp
(`docs/design/prototyp/`) angeglichen:

| Plan | Inhalt |
|---|---|
| 4 | Design-System (Farben, Formen, Manrope), Heute mit Heldenkarte, Aktivitätensymbol, Hell/Dunkel-Modus, Rückgängig für Erledigen/Auslassen |
| 5 | Aktivitäten-Liste und Editor (Symbolraster, Stepper, Typkarten, Plantext) |
| 6 | Belohnungen und Editor, Belohnungssymbol, Einlösen rückgängig (Migration 4→5, Backup-Schema 3) |
| 7 | Statistik (Heatmap-Stufen, Flächendiagramm), Optionen mit sofort speichernden Steppern, Navigationsleiste, Import-Validierung |
| 8 | Homescreen-Widget im Prototyp-Stil (Kopf mit Fortschritt und Punkten, Symbolkacheln, Zeilenzahl nach Höhe, Farben nach App-Einstellung) |

Plan 4–7 sind auf `main` gemergt; Plan 8 liegt auf dem Branch
`worktree-redesign-plan8-widget` und ist nach Review und Merge abgeschlossen.
Datenbank Version 5, Backup-Schema 3 (Schema 2 bleibt lesbar).

Was nach dem Redesign offen ist, steht unter „Offene Punkte“. Es ist ein
Backlog kleiner Aufräumpunkte für einen eigenen Plan 9, keine fehlende Funktion.

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
| `docs/superpowers/plans/2026-09-26-dayorganizer-04-designsystem-heute.md` bis `2026-10-16-dayorganizer-08-widget.md` | Pläne 04–08, das Redesign. Plan 8 ist der jüngste; jeder Plan nennt oben seine Design-Quelle und Global Constraints. |
| `docs/design/prototyp/` | Die visuelle Vorlage (`DayOrganizer App.dc.html`) mit eigener README. |
| `docs/superpowers/plans/2026-09-18-dayorganizer-0{1,2,3}-*.md` | Pläne 1–3 (Fundament, Belohnungen, Widget und Backup), umgesetzt. |
| `.superpowers/sdd/plan-N/` | Ledger, Aufträge und Berichte je Plan. Nicht eingecheckt. |

## Architektur in fünf Sätzen

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
`widgetFarben(modus)` aus denselben Objekten wie die App. Jede Klasse, die die Uhrzeit braucht, bekommt eine
`java.time.Clock` injiziert — daran hängt die gesamte Testbarkeit.

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

Beide Pläne liefen subagent-getrieben (`superpowers:subagent-driven-development`):
pro Task ein frischer Implementierer, danach ein Reviewer mit Spec- und
Qualitätsurteil, Fixes bis sauber, Fortschritt im Ledger, am Ende ein Review
über den gesamten Branch.

**Der Befund aus Plan 1 hat sich in Plan 2 bestätigt und verschärft: fast alle
gefundenen Fehler steckten im Plan, nicht in der Umsetzung.** In Plan 2 unter
anderem: doppelte `LazyColumn`-Keys, die beim bloßen Öffnen des Belohnungs-Tabs
abstürzen; ein Datumsfeld, in das sich nichts eintippen ließ und das beim
Speichern die App mitriss; ein Doppeltipp, der zweimal abbuchte; ein
Punkteverlauf, der die Netto-Änderung statt des Kontostands zeigte; ein
Wochenziel, das in der Heatmap sieben erledigte Tage meldete.

Drei Techniken tragen das und sollten beibehalten werden:

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

Backlog nach Plan 8, gedacht für einen eigenen Aufräumplan (Plan 9). Nichts
davon blockiert einen Merge.

- **Toasts verdrängen sich.** Ein neuer Toast ersetzt ein noch offenes
  Rückgängig (Heute, Belohnungen).
- **Stepper und Import.** Ein Stepper-Tipp in Optionen während eines laufenden
  Imports kann importierte Werte überschreiben.
- **Archivieren ohne Rückfrage und Wiederherstellung.** Im Emulator sind dadurch
  vier echte Aktivitäten archiviert (Magnesium, Rücken, Tabletten, Vitamin).
  Außerdem steht aus Plan 6 eine Test-Einlösung über −10 Punkte im Emulator.
- **Fehlerbehandlung der Editoren.** Das Speichern beider Editoren sollte
  einheitlich mit Fehlern umgehen; `Repository.speichern` wirft bei fehlendem
  Datensatz.
- **TalkBack.** Die Heatmap hat keine Semantik. Das „–“ bei Aktivitäten ohne
  Quote hat keinen Vorlesetext.
- **Dateidialog.** Nach dem Abbrechen des System-Dateidialogs stand die App im
  Emulator wieder auf „Heute“. Eine Ursache im Code ist nicht gefunden.

### Neu aus Plan 8

- **„Überfällig“ im Widget kommt verspätet.** Es erscheint erst bei der nächsten
  Neuzeichnung (Datenänderung, Farbmoduswechsel oder spätestens nach 30
  Minuten), nicht genau zur geplanten Uhrzeit.
- **Sehr große Systemschrift.** Bei 200 % wachsen Punktepille und Kopfzeile des
  Widgets etwas über die Annahmen in `WidgetMasse` hinaus (ca. 2 dp).
- **Testbuchung im Emulator.** Aus der Plan-8-Sichtprüfung steht dort eine
  Testbuchung über +1 Punkt.
- **Feste Uhrzeit, die schon vorbei ist.** Wird eine Aktivität mit fester
  Uhrzeit angelegt, die heute bereits vorbei ist, entsteht für heute keine
  Fälligkeit. Beobachtet, nicht geprüft, ob das gewollt ist.

## Datenbank

Version 5. Migrationen 1→2 (Tabelle `erinnerung`), 2→3 (`belohnung` und
`einloesung`), 3→4 (Symbol der Aktivität) und 4→5 (Symbol der Belohnung,
Stornierung von Einlösungen) sind vorhanden und werden im Test gegen die
exportierten Schemata validiert. `app/schemas/.../1.json` bis `5.json` sind
eingecheckt und müssen es bleiben — der Migrationstest liest sie. Es ist
**kein** `fallbackToDestructiveMigration` gesetzt, und das muss so bleiben: die
Daten liegen ausschließlich lokal. Plan 7 und 8 haben weder Datenbank noch
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
1. Plan 8 reviewen und mergen (Branch worktree-redesign-plan8-widget).
2. Auf einem echten Handy durchspielen.  ./gradlew :app:installDebug
   Was der Emulator nicht zeigen kann: ob die Erinnerung auf einem konkreten
   Gerät um 8:00 ankommt und der Hersteller sie nicht wegoptimiert, wie das
   Widget auf dem Launcher des Geräts aussieht und ob der Dateidialog bei
   Google Drive die .json-Datei anbietet (ungeprüft).
3. Plan 9 schreiben: den Backlog unter „Offene Punkte“ abarbeiten.
```
