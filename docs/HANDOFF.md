# Übergabe — Stand und wie es weitergeht

Letzte Aktualisierung: 2026-09-20, Commit `6114ebf`.

Dieses Dokument soll reichen, um in einer neuen Sitzung ohne Vorwissen
weiterzuarbeiten. Die Wahrheit steht im Code und in `git log`; wo dieses
Dokument davon abweicht, hat der Code recht.

## Wo wir stehen

**Plan 1 (Fundament und Erinnerungskern) ist vollständig umgesetzt, gemergt und
gepusht.** `main` ist der einzige Branch. 167 Tests, alle grün. GitHub Actions
baut bei jedem Push auf `main` eine installierbare Debug-APK als Artefakt.

Was die App heute kann: Aktivitäten mit vier Zeitplantypen anlegen, bearbeiten
und archivieren; zuverlässig erinnern (ein rollierender Exakt-Alarm, Nachfassen,
Nachholen verpasster Termine, Selbstheilung nach Neustart); aus der
Benachrichtigung abhaken; Punkte sammeln, inklusive Serien-, Pünktlichkeits- und
Tagesbonus; Heute-Screen mit Fortschrittsring und Warnbannern.

Was noch fehlt: Belohnungs-Shop, Statistik, Einstellungen (Plan 2), Widget und
JSON-Backup (Plan 3).

## Umgebung

- Kein Gerät, kein Emulator, kein AVD angelegt. **Alle Tests laufen auf der
  JVM**; Datenbanktests unter Robolectric im `test`-Quellverzeichnis. Es gibt
  bewusst kein `androidTest`-Quellverzeichnis.
- JDK 17 unter `/home/jan-lin/.local/opt/jdk-17.0.13+11`, Android SDK unter
  `/home/jan-lin/.local/opt/android-sdk` (`ANDROID_HOME` ist gesetzt).
- Gradle-Wrapper 8.9 ist eingecheckt, Gradle muss nicht installiert sein.
- `local.properties` ist nicht eingecheckt. Der Build läuft auch ohne sie,
  allein über `ANDROID_HOME` — lokal geprüft.

Vollständiger Durchlauf:

```bash
./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug
```

## Dokumente, in dieser Reihenfolge lesen

| Datei | Wofür |
|---|---|
| `docs/superpowers/specs/2026-09-18-dayorganizer-design.md` | Der Vertrag. Fachliches Modell, alle Regeln, Architektur. Kurz. |
| `docs/superpowers/plans/2026-09-18-dayorganizer-02-belohnungen.md` | Nächster Plan, 8 Tasks. Belohnungen, Statistik, Einstellungen. |
| `docs/superpowers/plans/2026-09-18-dayorganizer-03-widget-backup.md` | Danach, 4 Tasks. Widget und Backup. |
| `docs/superpowers/plans/2026-09-18-dayorganizer-01-fundament.md` | Sehr lang. Nur bei Bedarf abschnittsweise lesen. |
| `.superpowers/sdd/progress.md` | Ledger der Umsetzung: was gefunden und warum so entschieden wurde. Nicht in git. |

## Architektur in fünf Sätzen

`:domain` ist reines Kotlin ohne Android und enthält das Modell plus drei
zustandslose Regelmaschinen: `ZeitplanRechner` (Zeitplan → Fälligkeiten),
`SerienRechner` (Historie → Serienlänge), `PunkteRechner` (Erledigung →
Buchungen). `:app` enthält Room hinter schmalen Repository-Schnittstellen, die
Use Cases, den Alarm- und Benachrichtigungsapparat und die Compose-Oberfläche.
Der Punktestand ist die Summe eines Buchungsjournals, nie ein Zähler. Es ist zu
jedem Zeitpunkt genau ein exakter Alarm gesetzt, für die nächste fällige
Erinnerung überhaupt. Jede Klasse, die die Uhrzeit braucht, bekommt eine
`java.time.Clock` injiziert — daran hängt die gesamte Testbarkeit.

## Konventionen, die durchgehalten werden

- Bezeichner und Kommentare auf Deutsch, in Quelltextkommentaren `ae`/`oe`/`ue`
  statt Umlauten. Testnamen in Backticks als deutsche Sätze.
- Sichtbare Texte ausschließlich in `app/src/main/res/values/strings.xml`.
- Zwei-Konstruktoren-Muster für testbare Use Cases und ViewModels: ein
  primärer, der Funktionen statt Android-Abhängigkeiten nimmt, und **genau ein**
  `@Inject`-Konstruktor, der adaptiert. Dagger duldet nur einen.
- Die Fakes in `app/src/test/kotlin/de/vanhake/dayorganizer/Fakes.kt` müssen die
  Room-Semantik **exakt** nachbilden, Sortierung eingeschlossen. Das war die
  häufigste Fehlerquelle der ganzen Umsetzung: Weicht ein Fake ab, bestehen
  spätere Tests gegen eine Lüge. Bei jeder neuen DAO-Methode den Fake mit der
  SQL vergleichen, nicht mit der Erinnerung daran.
- Conventional Commits, `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

## Vorgehen, das sich bewährt hat

Die Umsetzung lief subagent-getrieben (`superpowers:subagent-driven-development`):
pro Task ein frischer Implementierer, danach ein Reviewer mit Spec- und
Qualitätsurteil, Fixes bis sauber, Fortschritt im Ledger. Am Ende ein Review
über den gesamten Branch.

Das hat sich als der eigentliche Wert herausgestellt: **Fast alle gefundenen
Fehler steckten im Plan, nicht in der Umsetzung.** Unter anderem eine
Endlosschleife bei Intervallen, die Teiler von 24 sind; eine Serienschwelle, die
sich wiederholt kassieren ließ; eine Idempotenz-Sperre, die nur auf „erledigt"
prüfte und Ausgelassenes nachträglich bezahlte; eine fehlende Transaktion trotz
Zusage im Design; ein Warnbanner, den niemand verdrahtet hatte.

Zwei Techniken haben sich besonders gelohnt und sollten beibehalten werden:

1. **Mutationsnachweis.** Nach jedem Fix den Produktionscode absichtlich kaputt
   machen, prüfen, dass der neue Test umfällt, und wiederherstellen. Mehrere
   Tests, die „bestanden", haben sich so als wirkungslos entpuppt.
2. **Dem Reviewer sagen, worauf er achten soll.** Generische Reviews finden
   wenig. Die scharfen Funde kamen, wenn der Prompt eine konkrete Sorge nannte
   („kann diese Sequenz endlos laufen?", „bildet der Fake die Sortierung nach?").

Eine Falle: Subagenten räumen gelegentlich das Arbeitsverzeichnis auf. Eigene,
noch nicht committete Änderungen am Plan gingen dadurch einmal verloren.
**Controller-Änderungen sofort committen**, bevor ein Subagent startet.

## Vor Plan 2 zu klären

Zwei Entscheidungen sind offen und blockieren sauberes Arbeiten an der
Statistik:

1. **Ist der Tagesbonus einer Aktivität zurechenbar?** `TAGESBONUS`-Buchungen
   tragen heute bewusst weder `aktivitaetId` noch `faelligkeitId` — sie gehören
   dem Tag. Plan 2 will „Punkte je Aktivität" zeigen; dabei fiele der Tagesbonus
   stillschweigend heraus und die Summen stimmten nicht mit dem Kontostand
   überein. Entweder zurechnen oder im Design festhalten, dass er es nicht ist.
2. **`SerienRechner` liest die gesamte Historie.** `FaelligkeitErledigen` ruft
   ihn mit `historie(aktivitaetId)` auf — jede Erledigung, die es je gab. Nach
   einem Jahr sind das Tausende Zeilen bei jedem Abhaken. Plan 2 ruft ihn
   zusätzlich pro Belohnung bei jedem Rendern des Shops. Vorher eine begrenzte
   Abfrage `historieSeit(aktivitaetId, ab)` einziehen; die aktuelle Serie muss
   nie weiter zurückschauen als bis zum ersten Bruch.

## Bekannte Kleinigkeiten, bewusst offen

- Ausgelöste `erinnerung`-Zeilen werden nie aufgeräumt (1–2 KB/Tag). Kandidat
  für den nächtlichen Wächter.
- `AUSGELASSEN` setzt `erledigtAm`. Plan-2-Abfragen müssen auf `status`
  filtern, nicht auf `erledigtAm IS NOT NULL`, sonst zählen Auslassungen als
  Erledigungen.
- `Navigation.kt` hat kein `popUpTo`/`launchSingleTop`; jeder Tab-Tipp legt
  einen Eintrag auf den Zurück-Stapel. Plan 2 ersetzt die Datei ohnehin
  vollständig, die Endfassung dort hat es.
- `HeuteEintrag.farbe` wird befüllt, aber nirgends angezeigt.
- `DaoTest` benutzt `unterstrich_namen`, alle anderen Tests deutsche Sätze in
  Backticks.
- `app/build.gradle.kts` deklariert einen `testInstrumentationRunner`, obwohl es
  kein `androidTest`-Quellverzeichnis gibt.
- `actions/checkout@v4`, `upload-artifact@v4` und `setup-gradle@v4` laufen auf
  einer von GitHub als veraltet markierten Node-Version. Sie funktionieren.

## Datenbank

Version 2. Migration 1→2 (Tabelle `erinnerung`) ist vorhanden und wird im Test
gegen das exportierte Schema validiert. `app/schemas/.../1.json` und `2.json`
sind eingecheckt und müssen es bleiben — der Migrationstest liest sie. Plan 2
hebt auf Version 3 (Belohnungen und Einlösungen) und bringt `MIGRATION_2_3`
bereits mit.

## So geht es weiter

```
1. Einmal auf einem echten Gerät durchspielen.  ./gradlew :app:installDebug
   Alles ist über JVM-Tests abgesichert, aber ob die Benachrichtigung auf einem
   konkreten Handy um 8:00 ankommt und der Hersteller sie nicht wegoptimiert,
   zeigt sich erst dort.
2. Die beiden Fragen oben entscheiden.
3. Plan 2 umsetzen, wieder subagent-getrieben.
4. Plan 3.
```
