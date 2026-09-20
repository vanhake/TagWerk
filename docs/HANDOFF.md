# Übergabe — Stand und wie es weitergeht

Letzte Aktualisierung: 2026-09-20, Branch `plan-2-belohnungen`, Commit `582c47c`.

Dieses Dokument soll reichen, um in einer neuen Sitzung ohne Vorwissen
weiterzuarbeiten. Die Wahrheit steht im Code und in `git log`; wo dieses
Dokument davon abweicht, hat der Code recht.

## Wo wir stehen

**Plan 1 und Plan 2 sind vollständig umgesetzt.** Plan 1 ist auf `main`
gemergt. Plan 2 liegt auf `plan-2-belohnungen`: 21 Commits, 248 Tests, alle
grün, APK baut. Das Abschlussreview über den ganzen Branch sagt „Merge nach
Fixes"; die Fixes sind gemacht (`4b1c1a4`, `582c47c`), der Branch ist
mergebereit.

Was die App heute kann: Aktivitäten mit vier Zeitplantypen anlegen, bearbeiten
und archivieren; zuverlässig erinnern (ein rollierender Exakt-Alarm, Nachfassen,
Nachholen verpasster Termine, Selbstheilung nach Neustart); aus der
Benachrichtigung abhaken; Punkte sammeln samt Serien-, Pünktlichkeits- und
Tagesbonus; Belohnungen anlegen und einlösen, mit Mindestserie oder Startdatum
als Bedingung; Statistik mit Heatmap, Quoten, Serien und Punkteverlauf;
Einstellungen für Tagesbonus und Standard-Kulanzfenster.

Was noch fehlt: Widget und JSON-Backup (Plan 3), dazu die unter
„Offene Punkte" gesammelten Lücken.

## Umgebung

- Kein Gerät, kein Emulator, kein AVD angelegt. **Alle Tests laufen auf der
  JVM**; Datenbanktests unter Robolectric im `test`-Quellverzeichnis. Es gibt
  bewusst kein `androidTest`-Quellverzeichnis. Compose-Oberflächen werden
  übersetzt, aber **nie ausgeführt** — `assembleDebug` ist der einzige
  Nachweis, dass ein Screen überhaupt baut.
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
3. **Dem Reviewer sagen, worauf er achten soll.** Generische Reviews finden
   wenig. Die scharfen Funde kamen, wenn der Prompt eine konkrete Sorge nannte
   („bildet der Fake die Sortierung nach?", „zählt ein Wochenziel an allen
   sieben Tagen mit?", „sichert der Transaktionstest wirklich etwas zu?").

Zwei Fallen: Subagenten räumen gelegentlich das Arbeitsverzeichnis auf —
**Controller-Änderungen sofort committen**, bevor ein Subagent startet. Und
Task-Berichte heißen `task-N-report.md`; in Plan 2 überschrieb das anfangs die
Berichte von Plan 1. Die Artefakte liegen jetzt getrennt unter
`.superpowers/sdd/plan-1/` und `.superpowers/sdd/plan-2/`.

## Offene Punkte

Das Abschlussreview hat diese Liste triagiert. Nichts davon blockiert den Merge.

### Vor oder mit Plan 3 angehen

| Punkt | Warum |
|---|---|
| Belohnungen lassen sich nirgends archivieren | `BelohnungenViewModel.archivieren()` ist toter Code, DAO und Repository sind fertig, nur der Knopf fehlt. Design §5 nennt „Belohnungsverwaltung". Eine falsch angelegte Belohnung bleibt für immer im Shop **und** beeinflusst die Zielzeile auf Heute. |
| Ein korrupter Datensatz reißt den Startbildschirm mit | `error("Unbekannte Belohnungsbedingung")` im Mapper läuft innerhalb von `beobachteAktive().map`. Seit der Heute-Screen denselben Flow sammelt, nimmt ein einziger kaputter Eintrag nicht mehr nur den Shop mit, sondern den Start. |
| Eine gescheiterte Einlösung bleibt stumm | `Abgelehnt` und `Unbekannt` werden verworfen, der Zustand hat kein Fehlerfeld. Bei einer Aktion, die Punkte kostet, sollte der Nutzer erfahren, dass nichts passiert ist. |
| Der Abbruchfall ist nirgends verifiziert | `FakeTransaktionen` kann kein Rollback abbilden, `RaumTransaktionen` hat keinen Test. Die Atomarität des Geldpfads ruht allein auf Rooms Zusage. Unter Robolectric mit echter Room-Datenbank testbar — dafür braucht es kein Gerät. |
| Archivierte Aktivitäten in der Statistik | Ihre Fälligkeiten färben weiter die Heatmap, aber Quote und Serie sind unsichtbar (`alleAktiven()` filtert sie). Design §3.1 begründet das Archivieren gerade damit, dass die Historie erhalten bleibt. |
| Ungeschützte DataStore-Lesezugriffe | `HeuteViewModel.beimSichtbarwerden`, `EinstellungenViewModel.neuLaden` und `AktivitaetBearbeitenViewModel.laden(0)` lesen ohne `runCatching`. Eine `IOException` bei Korruption beendet die App — auf Heute eine Absturzschleife. Die Schreibseite ist überall geschützt, die Leseseite nie. `BelohnungenViewModel.einloesen` macht es richtig. |

### Lücken gegenüber dem Design, die kein Plan abdeckt

- **Standardwert fürs Nachfassen.** Design §5 verlangt Standardwerte für
  Kulanzfenster *und Nachfassen*. Die Kulanz ist in Plan 2 verdrahtet worden,
  das Nachfassen nicht — `nachfassAnzahl` und `nachfassAbstandMinuten` stehen
  fest in `AktivitaetEntwurf`.
- **Detailansicht einer Aktivität** mit Serie, Quote und Historie (Design §5).
  Steht in keinem der drei Pläne.
- **Balken** unter der Zeile „noch 40 Punkte bis Kinoabend" auf Heute
  (Design §5). Der Text ist da, der Balken fehlt.

### Kleinigkeiten, bewusst offen

- Doppelt gepflegt: die Regel „was gehört diesem Tag" steht in
  `FaelligkeitErledigen` und `StatistikRechner`. Plan 3 (Widget) braucht sie ein
  drittes Mal — ein `Faelligkeit.gehoertZumTag(tag)` in `:domain` schließt das.
- Der Heute-Ring zählt Wochenziele mit, Tagesbonus und Heatmap nicht. Zwei von
  drei stimmen überein, der Ring ist der Ausreißer.
- Die Zeitraum-Chips der Statistik regieren Heatmap und Punkteverlauf, aber
  nicht Quote und Serien — für `laengsteSerie` ist das zwingend, für die Quote
  eine stille Abweichung. Eine Beschriftung genügt.
- `BuchungRepository.fuerZeitraum` hat keinen Produktivaufrufer mehr, seit der
  Punkteverlauf auf `alle()` umgestellt wurde. Samt DAO-Test und Fake.
- `bedingung_datum_fehlt` ist eine verwaiste Zeichenkette: bei ungültigem Datum
  sieht der Nutzer nur einen roten Rand, die vorbereitete Erklärung nie.
- Navigation ohne `saveState`/`restoreState`: jeder Tab-Wechsel verwirft das
  ViewModel des verlassenen Tabs samt Scrollposition.
- Beide Canvas-Flächen (Heatmap, Punkteverlauf) sind für TalkBack unsichtbar.
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
hebt auf Version 4 und wird dem begegnen.

## So geht es weiter

```
1. plan-2-belohnungen mergen. Das Abschlussreview ist durch, die Fixes sind drin.
2. Einmal auf einem echten Gerät durchspielen.  ./gradlew :app:installDebug
   Alles ist über JVM-Tests abgesichert, aber die gesamte Oberfläche wurde nie
   ausgeführt — Heatmap, Punkteverlauf und die beiden Formulare hat noch nie
   jemand gesehen. Und ob die Benachrichtigung auf einem konkreten Handy um 8:00
   ankommt und der Hersteller sie nicht wegoptimiert, zeigt sich erst dort.
3. Aus „Vor oder mit Plan 3 angehen" entscheiden, was vorgezogen wird.
4. Plan 3 umsetzen, wieder subagent-getrieben.
```
