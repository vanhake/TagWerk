# Übergabe — Stand und wie es weitergeht

Letzte Aktualisierung: 2026-09-26, Branch `plan-3-widget-backup`, Commit `9e0ec0f`.

Dieses Dokument soll reichen, um in einer neuen Sitzung ohne Vorwissen
weiterzuarbeiten. Die Wahrheit steht im Code und in `git log`; wo dieses
Dokument davon abweicht, hat der Code recht.

## Wo wir stehen

**Alle drei Pläne sind vollständig umgesetzt.** Plan 1 und 2 sind auf `main`
gemergt. Plan 3 liegt auf `plan-3-widget-backup`: 18 Commits, 317 Tests, alle
grün, Debug **und Release** bauen. Das Abschlussreview über den ganzen Branch
sagt „Merge nach Fixes"; die Fixes sind gemacht (`9e0ec0f`), der Branch ist
mergebereit.

Damit ist der Funktionsumfang des Design-Dokuments erreicht. Die App kann:
Aktivitäten mit vier Zeitplantypen anlegen, bearbeiten und archivieren;
zuverlässig erinnern (ein rollierender Exakt-Alarm, Nachfassen, Nachholen
verpasster Termine, Selbstheilung nach Neustart); aus der Benachrichtigung
abhaken; Punkte sammeln samt Serien-, Pünktlichkeits- und Tagesbonus;
Belohnungen anlegen, archivieren und einlösen, mit Mindestserie oder Startdatum
als Bedingung; Statistik mit Heatmap, Quoten, Serien und Punkteverlauf;
Einstellungen für Tagesbonus und Standard-Kulanzfenster; alles als JSON
exportieren und wieder einlesen; und vom Startbildschirm aus abhaken.

Was fehlt, steht unter „Offene Punkte" — nichts davon ist eine ganze Funktion,
die das Design verspricht, bis auf drei kleine Lücken, die dort benannt sind.

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
| `docs/superpowers/plans/2026-09-18-dayorganizer-03-widget-backup.md` | Nächster Plan, 4 Tasks. Widget und Backup. |
| `docs/superpowers/plans/2026-09-18-dayorganizer-02-belohnungen.md` | Gerade umgesetzt. Task 4 trägt einen Nachtrag über drei Fehler im Beispielcode. |
| `.superpowers/sdd/progress.md` | Ledger beider Pläne: was gefunden und warum so entschieden wurde. Nicht in git. |

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
Erinnerung überhaupt. Jede Klasse, die die Uhrzeit braucht, bekommt eine
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
- Conventional Commits, `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

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

Zwei Fallen: Subagenten räumen gelegentlich das Arbeitsverzeichnis auf —
**Controller-Änderungen sofort committen**, bevor ein Subagent startet. Und
Task-Berichte heißen `task-N-report.md`; in Plan 2 überschrieb das anfangs die
Berichte von Plan 1. Die Artefakte liegen jetzt getrennt unter
`.superpowers/sdd/plan-1/` und `.superpowers/sdd/plan-2/`.

## Offene Punkte

Das Abschlussreview über Plan 3 hat diese Liste triagiert. Nichts davon
blockiert den Merge. Die Liste aus Plan 2 ist abgearbeitet: Belohnungen lassen
sich archivieren, eine gescheiterte Einlösung meldet sich, die DataStore-Zugriffe
sind abgesichert, und der Rollback ist erstmals nachgewiesen.

### Als Erstes angehen

| Punkt | Warum |
|---|---|
| Drei Stile der Fehlerbehandlung nebeneinander | `versuchen{}` (in `util`), `runCatching` mit Weiterwerfen in `WidgetAktualisierung`, und bis zuletzt `catch (Exception)` im `BackupDienst`. Aus genau dieser Uneinheitlichkeit ist der schwerste Fund des Abschlussreviews entstanden. Überall `versuchen{}` benutzen. |
| Die Folge „verstecken, neu planen, Widget anstoßen" steht viermal von Hand da | In `HeuteViewModel` (zweimal), `AktionsEmpfaenger` und `WidgetErledigenAktion`. Dieselbe Lücke ist zweimal aufgetreten — Fix 9 in Plan 1 und die Plan-3-Korrektur. Ein gemeinsamer Nach-Schreib-Schritt schließt das. |
| Ein korrupter Datensatz reißt den Startbildschirm mit | `error("Unbekannte Belohnungsbedingung")` im Mapper läuft innerhalb von `beobachteAktive().map`. Seit der Heute-Screen denselben Flow sammelt, nimmt ein einziger kaputter Eintrag nicht mehr nur den Shop mit, sondern den Start. Seit dem Backup ist er auch von außen erzeugbar. |
| `EinstellungenSpeicher` liefert bei E/A-Fehlern still Vorgabewerte | Seit dem `ReplaceFileCorruptionHandler` trifft der `IOException`-Fang nur noch echte Lesefehler. Dann bucht `FaelligkeitErledigen` still den Tagesbonus 15 statt des eingestellten Werts ins Journal — falsche Zahlen statt eines lauten Fehlers. Vorschlag: nur die Beobachtung für die Oberfläche absichern, im Buchungspfad weiterwerfen. |
| Archivierte Aktivitäten in der Statistik | Ihre Fälligkeiten färben weiter die Heatmap, aber Quote und Serie sind unsichtbar (`alleAktiven()` filtert sie). Design §3.1 begründet das Archivieren gerade damit, dass die Historie erhalten bleibt. |

### Lücken gegenüber dem Design, die kein Plan abdeckt

- **`icon` gibt es im Modell nicht.** Design §3.1 und §3.7 nennen ein Icon für
  Aktivitäten und Belohnungen. Es fehlt seit Plan 1 durchgehend — und eine
  spätere Ergänzung braucht **Backup-Schemaversion 2**, weil die Datei das Feld
  dann führen muss.
- **Standardwert fürs Nachfassen.** Design §5 verlangt Standardwerte für
  Kulanzfenster *und Nachfassen*. Die Kulanz ist verdrahtet, das Nachfassen
  nicht — `nachfassAnzahl` und `nachfassAbstandMinuten` stehen fest in
  `AktivitaetEntwurf`.
- **Detailansicht einer Aktivität** mit Serie, Quote und Historie (Design §5).
  Steht in keinem der drei Pläne.
- **Balken** unter der Zeile „noch 40 Punkte bis Kinoabend" auf Heute
  (Design §5). Der Text ist da, der Balken fehlt.

### Kleinigkeiten, bewusst offen

- Doppelt gepflegt: die Regel „was gehört diesem Tag" steht in
  `FaelligkeitErledigen` und `StatistikRechner`; `WidgetZusammenstellung` und
  `HeuteZusammenstellung` wählen dieselben Einträge zweimal aus. Heute
  antworten sie gleich — aber es sind zwei Wahrheiten.
- Direkt nach einem Import feuern die Erinnerungen für alles heute Offene
  erneut, weil die „ausgelöst"-Zeilen nicht gesichert werden. Beim
  Gerätewechsel ist das sogar erwünscht; es sollte nur dokumentiert sein.
- Zwischen Mitternacht und dem nächsten 30-Minuten-Update zeigt das Widget die
  Fälligkeiten von gestern; ein Tipp bucht dann die gestrige. Die
  Benachrichtigungen verhalten sich genauso, der Wächter läuft erst um 03:00.
- `VerpassteAbschliessen` schreibt ganze Zeilen per `@Update` ohne Transaktion.
  Läuft der Wächter genau während eines Imports, könnte er alte Zeilen über
  neue ids schreiben. Fenster: Millisekunden.
- `WidgetErledigenAktion` ruft `updateAll()` direkt statt über
  `WidgetAktualisierung` und hat keine Fehlerbehandlung.
- Der Widget-Katalog zeigt nur das App-Symbol (kein `previewLayout`), und
  Glance hat kein eigenes `errorUiLayout`.
- `beobachteTag` hat keinen `id`-Tiebreaker; zwei Zeilen gleicher Periode ohne
  Sollzeitpunkt haben keine zugesicherte Reihenfolge.
- Der Heute-Ring zählt Wochenziele mit, Tagesbonus und Heatmap nicht.
- Die Zeitraum-Chips der Statistik regieren Heatmap und Punkteverlauf, aber
  nicht Quote und Serien — für `laengsteSerie` ist das zwingend, für die Quote
  eine stille Abweichung. Eine Beschriftung genügt.
- `BuchungRepository.fuerZeitraum` hat keinen Produktivaufrufer mehr.
- `bedingung_datum_fehlt` ist eine verwaiste Zeichenkette: bei ungültigem Datum
  sieht der Nutzer nur einen roten Rand, die vorbereitete Erklärung nie.
- Navigation ohne `saveState`/`restoreState`: jeder Tab-Wechsel verwirft das
  ViewModel des verlassenen Tabs samt Scrollposition.
- Heatmap, Punkteverlauf und das Widget sind für TalkBack unsichtbar.
- Kein Limit auf `beobachteEinloesungen()`; die Historie wächst unbegrenzt.
- Tagesbonus ohne Obergrenze; der Kontostand wird als `Int` summiert.
- Ausgelöste `erinnerung`-Zeilen werden nie aufgeräumt (1–2 KB/Tag).
- `AUSGELASSEN` setzt `erledigtAm`. Abfragen müssen auf `status` filtern, nicht
  auf `erledigtAm IS NOT NULL`.
- `app/build.gradle.kts` deklariert einen `testInstrumentationRunner`, obwohl es
  kein `androidTest`-Quellverzeichnis gibt.

## Datenbank

Version 3. Migrationen 1→2 (Tabelle `erinnerung`) und 2→3 (`belohnung` und
`einloesung`) sind vorhanden und werden im Test gegen die exportierten Schemata
validiert. `app/schemas/.../1.json`, `2.json` und `3.json` sind eingecheckt und
müssen es bleiben — der Migrationstest liest sie. Es ist **kein**
`fallbackToDestructiveMigration` gesetzt, und das muss so bleiben: die Daten
liegen ausschließlich lokal.

**Eine Falle bei der nächsten Versionshebung:** Der erste Lauf, in dem Room eine
neue Schema-Datei erzeugt, scheitert mit `FileNotFoundException: Missing file:
.../N.json` in `mergeDebugAssets`, weil `app/schemas` außerhalb des
Build-Verzeichnisses liegt. Ein zweiter, identischer Lauf geht durch. Plan 3
ergänzte nur Abfragen und hat die Version nicht angehoben.

**Das Backup trägt eine eigene Version.** `BackupUmwandlung.SCHEMA_VERSION` steht
auf 1, und `app/src/test/resources/backup/beispiel-schema-1.json` ist der
eingefrorene Vertrag dafür: Wer ein DTO-Feld, eine Enum-Konstante oder die
Kodierung eines Zeitplans ändert, macht alle bestehenden Backups unlesbar — dann
braucht es Version 2 und einen Lesepfad für Version 1, keine angepasste
Testdatei.

## So geht es weiter

```
1. plan-3-widget-backup mergen. Das Abschlussreview ist durch, die Fixes sind
   drin, Debug und Release bauen.
2. Auf einem echten Handy durchspielen.  ./gradlew :app:installDebug
   Im Emulator ist alles einmal durchgespielt, inklusive Backup, Widget und
   einer absichtlich beschädigten Einstellungsdatei. Was der Emulator nicht
   zeigen kann: ob die Erinnerung auf einem konkreten Gerät um 8:00 ankommt und
   der Hersteller sie nicht wegoptimiert, und ob der Dateidialog bei Google
   Drive die .json-Datei anbietet (der MIME-Filter ist dafür erweitert, aber
   ungeprüft).
3. Aus „Als Erstes angehen" entscheiden, was zuerst drankommt. Ein vierter Plan
   ist nicht geschrieben; die Punkte sind klein genug, um sie einzeln
   anzugehen.
```
