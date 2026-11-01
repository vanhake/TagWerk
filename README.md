# Tagwerk

Eine Android-App für wiederkehrende Alltagsaktivitäten, an die man erinnert
werden möchte — Tabletten morgens und abends, Medizin zum Mittagessen, dreimal
die Woche Sport. Jede Erledigung bringt Punkte, und die Punkte gibt man für
Belohnungen aus, die man sich selbst gesetzt hat.

Die App läuft vollständig lokal: kein Server, kein Konto, keine
Netzwerkberechtigung. Sie ist für den privaten Gebrauch gedacht und wird als
APK verteilt, nicht über den Play Store.

Bis Oktober 2026 hieß die App „DayOrganizer“. Intern trägt sie diesen Namen
weiter: Die Paketkennung `de.vanhake.dayorganizer`, die Kotlin-Pakete und der
Datenbankname bleiben unverändert, damit eine installierte App ihre Daten
behält und alte Backups importierbar bleiben.

## Stand

Die App ist funktional vollständig und im Design des Redesign-Prototyps
umgesetzt: Aktivitäten anlegen, zuverlässig erinnert werden, aus der
Benachrichtigung oder vom Startbildschirm abhaken, Punkte sammeln und für
Belohnungen einlösen, Statistik ansehen, Optionen und JSON-Backup. Die Pläne
1–8 sind umgesetzt (siehe [Dokumentation](#dokumentation)). Offen ist ein
Backlog kleiner Aufräumpunkte, der in `docs/HANDOFF.md` steht.

## Die Oberfläche

Die visuelle Vorlage ist der Prototyp unter
[`docs/design/prototyp/`](docs/design/prototyp/) (`DayOrganizer App.dc.html`).
Farben, Formen und Schrift (Manrope, als Ressource gebündelt) leben in
`ui/theme`; jeder Bildschirm gibt es in Hell und Dunkel.

**Heute.** Heldenkarte mit Tagesfortschritt und Punktestand, darunter die
fälligen Aktivitäten als Kacheln mit Symbol. Erledigen und Auslassen zeigen
einen Toast mit *Rückgängig*; das Rückgängig bucht eine exakte Gegenbuchung
und stellt die Fälligkeit wieder her, die Historie bleibt erklärbar. Offene
Einmalaufgaben stehen im Bereich „Aufgaben“ mit den Tagen bis zur Deadline.
Ist der Toast schon weg, lässt sich ein versehentlich erledigter oder
ausgelassener Eintrag im Bereich „Erledigt“ über *Wieder öffnen* zurückholen;
die Punkte dieses Abhakens werden gegengebucht. Eine Einmalaufgabe steht dafür
am Tag des Abhakens ebenfalls unter „Erledigt“. Punkte gibt es nur innerhalb
der eigenen Periode (Tag, Woche bzw. bis zur Deadline): wird ein abgelaufener
Eintrag noch abgehakt – etwa über die Benachrichtigung von gestern, bevor der
nächtliche Wächter ihn geschlossen hat –, gilt er als verpasst und bucht nichts.

**Einmalaufgaben.** Eine Aufgabe wie „Steuererklärung“ hat eine Deadline und
bringt nur Punkte, wenn sie bis dahin erledigt ist; danach lässt sie sich noch
ohne Punkte abhaken. Optional erinnert sie wöchentlich am Wochentag der
Deadline, in den letzten vier Wochen zweimal pro Woche. Sie zählt nicht zum
Tagesfortschritt und erscheint im Widget erst eine Woche vor der Deadline.

**Aktivitäten.** Liste mit Symbolkachel, Plantext („Täglich · 08:00, 20:00“),
Punkten und Serie, Einmalaufgaben in einem eigenen Abschnitt. Der Editor hat ein
Symbolraster, Stepper, Typkarten für die fünf Zeitplanarten, Wochentag-Kacheln,
Uhrzeit-Chips mit Zeitwähler, ein editierbares Zeitfenster für Intervalle, für
Einmalaufgaben Deadline und Erinnerung, und eine Zusammenfassung des Plans.

**Belohnungen.** Heldenkarte mit dem Punktestand, Belohnungen mit Symbol und
Bedingung (Mindestserie oder Startdatum), Verlauf der Einlösungen. Eine
Einlösung lässt sich per Toast rückgängig machen: sie bleibt als stornierte
Zeile stehen, eine Gegenbuchung stellt den Kontostand her.

**Statistik.** Heatmap mit Stufen je Tag, Gesamtquote, Punkteverlauf als
Flächendiagramm und eine Zeile je Aktivität mit Quote und Serie.

**Optionen.** Tagesbonus und Kulanzfenster per Stepper, die sofort speichern
(kein Speichern-Knopf). Umschalter Hell/Dunkel, Export und Import des Backups.

**Widget.** Das Homescreen-Widget „Heute“ zeigt eine Kopfzeile mit Fortschritt
und Punktestand und darunter höchstens vier Kacheln mit Symbol, Name, Untertitel
(„08:00 · überfällig“, „2 von 3 diese Woche“) und Haken zum Abhaken. Die
Zeilenzahl richtet sich nach der Widgethöhe, der Rest erscheint als „+ n
weitere“. Die Farben folgen der Hell/Dunkel-Einstellung der App.

## Was die App kann

**Vier Arten von Zeitplan.** Feste Uhrzeiten an ausgewählten Wochentagen
(Tabletten Mo–So um 8:00 und 20:00), Intervalle innerhalb eines Zeitfensters
(alle drei Stunden zwischen 8:00 und 22:00), Tagesziele ohne feste Uhrzeit
(dreimal täglich) und Wochenziele (dreimal die Woche Sport).

**Erinnerungen, die ankommen.** Es ist immer genau ein exakter Alarm gesetzt,
für die nächste fällige Erinnerung überhaupt; wenn er feuert, plant die App
sofort den nächsten. Die Benachrichtigung trägt die Aktionen *Erledigt*,
*Später* und *Ausgelassen* und wird direkt aus der Statusleiste verarbeitet.
Bleibt sie unbeantwortet, wird nachgefasst.

**Punkte als Buchungsjournal.** Der Punktestand ist die Summe aller Buchungen,
nie ein gespeicherter Zähler. Jede Erledigung, jeder Bonus und jede Einlösung
ist eine eigene Zeile mit Betrag, Zeitpunkt und Grund — damit lässt sich jede
Zahl erklären, und Korrekturen fälschen keine Historie.

**Serien und Boni.** Eine Serie zählt in Tagen, bei Wochenzielen in Wochen. Ein
bewusst ausgelassener Tag bricht sie nicht, verlängert sie aber auch nicht.
Konfigurierbare Schwellen geben Bonuspunkte, dazu ein Tagesbonus, wenn alles
erledigt ist.

**Selbstheilung.** Ein nächtlicher Hintergrundjob schließt abgelaufene
Fälligkeiten, füllt den Vorausblick auf und setzt den Alarm neu — auch nach
einem Neustart oder wenn Android einen Alarm verschluckt hat. Eine Erinnerung,
deren Zeitpunkt vergangen ist, wird nachgeholt statt übersprungen: verspätet
ist bei einer Medikamentenerinnerung deutlich besser als gar nicht.

**Sagt Bescheid, wenn etwas blockiert.** Fehlen Benachrichtigungsrechte, sind
exakte Alarme nicht erlaubt oder greift die Akkuoptimierung des Herstellers,
erscheint auf *Heute* ein Banner mit direktem Sprung in die passende
Systemeinstellung. Auch ein dauerhaft hängender Wächter meldet sich dort. Die
App scheitert an dieser Stelle nie stillschweigend.

## Aufbau

Zwei Gradle-Module:

```
domain/   Reines Kotlin, keine Android-Abhängigkeit. Das Modell und drei
          zustandslose Regelmaschinen:
            ZeitplanRechner   Zeitplan  -> konkrete Fälligkeiten
            SerienRechner     Historie  -> Serienlänge
            PunkteRechner     Erledigung -> Punktbuchungen

app/      Android. Room-Persistenz hinter schmalen Repository-Schnittstellen,
          Use Cases, Alarm- und Benachrichtigungsapparat, Compose-Oberfläche.
```

Die Trennung ist keine Zierde: die Regeln sind der Teil, der stimmen muss, und
im `domain`-Modul lassen sie sich ohne Emulator und ohne Android-Laufzeit
prüfen. Jede Klasse, die die Uhrzeit braucht, bekommt eine `java.time.Clock`
injiziert — dadurch sind Mitternachtsübergänge, Kulanzfenster und die
Zeitumstellung in Millisekunden testbar statt gar nicht.

Zeitpläne werden als `LocalTime`, `DayOfWeek` und `LocalDate` gespeichert, nie
als absoluter Zeitstempel. Die Auflösung zu einem Instant passiert an genau
einer Stelle, im `AlarmPlaner`. Sonst verschöbe die Sommerzeitumstellung die
Tabletteneinnahme um eine Stunde.

**Stack:** Kotlin, Jetpack Compose (Material 3), Glance (Widget), Room, Hilt,
DataStore, WorkManager, AlarmManager. Mindestens Android 13 (API 33), Ziel API 35.

**Daten.** Die Room-Datenbank steht auf Version 5 (Migrationen 1→2 bis 4→5,
Schemata unter `app/schemas`). Das JSON-Backup trägt eine eigene Version, aktuell
Schema 3; Backups mit Schema 2 bleiben lesbar, weil die neuen Felder
Standardwerte haben. Ein Import lehnt fachlich ungültige Werte ab (Preis,
Serienperioden oder Punkte von 0).

## Bauen

Vorausgesetzt sind ein JDK 17 und ein Android SDK. Der Gradle-Wrapper ist
eingecheckt, Gradle selbst muss also nicht installiert sein.

```bash
./gradlew :app:assembleDebug
```

Die APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

Auf ein angeschlossenes Gerät installieren:

```bash
./gradlew :app:installDebug
```

Findet Gradle das SDK nicht, lege eine `local.properties` im Projektwurzel an
(die Datei ist absichtlich nicht eingecheckt):

```properties
sdk.dir=/pfad/zum/android-sdk
```

### Fertige APK aus GitHub

Bei jedem Push auf `main` baut GitHub Actions eine installierbare Debug-APK und
hängt sie als Artefakt an den Lauf. Zu finden unter *Actions* → letzter Lauf →
*Artifacts*. Damit muss niemand, der die App nur benutzen will, eine
Entwicklungsumgebung aufsetzen.

### Release veröffentlichen

Ein Tag der Form `v<Version>` baut eine signierte Release-APK und legt damit
ein GitHub-Release an (Workflow `release.yml`):

```bash
git tag v1.0.0
git push origin v1.0.0
```

Die App trägt dann die Version aus dem Tag, hier `1.0.0`. Release-Notizen
stellt GitHub aus den PRs seit dem letzten Tag zusammen.

Den Schlüssel legst du einmalig selbst an und hinterlegst ihn als Secrets
unter *Settings* → *Secrets and variables* → *Actions*:

```bash
keytool -genkeypair -v -keystore tagwerk-release.jks -alias tagwerk \
  -keyalg RSA -keysize 4096 -validity 10000
base64 -w0 tagwerk-release.jks   # Ausgabe ist der Wert für KEYSTORE_BASE64
```

| Secret | Inhalt |
|---|---|
| `KEYSTORE_BASE64` | Keystore als Base64 |
| `KEYSTORE_PASSWORD` | Passwort des Keystores |
| `KEY_ALIAS` | Alias des Schlüssels, z. B. `tagwerk` |
| `KEY_PASSWORD` | Passwort des Schlüssels |

Keystore und Passwort sicher aufbewahren: Ohne sie lassen sich keine Updates
mehr ausliefern, Android lehnt eine APK mit anderem Schlüssel ab. Aus demselben
Grund lässt sich eine Debug-APK nicht durch eine Release-APK ersetzen; beim
Umstieg einmal ein Backup exportieren, deinstallieren, neu installieren und das
Backup wieder einspielen.

## Tests

```bash
./gradlew :domain:test :app:testDebugUnitTest
```

466 Tests, alle auf der JVM. Datenbanktests laufen unter Robolectric, es wird
also weder ein Gerät noch ein Emulator gebraucht. Der Schwerpunkt liegt auf den
drei Regelmaschinen, jeweils mit fester Uhr: Wochentagsauswahl,
Intervallgrenzen, Zähl- und Wochenziele, Kulanzfenster, Serienabbruch durch
Verpassen gegenüber Überleben durch Auslassen, Zeitumstellung, Schaltjahr.
Dazu Room-Tests inklusive Migration, Use-Case- und ViewModel-Tests, reine
Darstellungsfunktionen (Plantext, Heatmap-Stufen, Widget-Zustand) und
Ressourcentests unter Robolectric. Compose- und Glance-Oberflächen haben keine
UI-Tests; ob sie bauen, zeigt `:app:assembleDebug`, wie sie aussehen, die
Sichtprüfung im Emulator.

## Dokumentation

| Datei | Inhalt |
|---|---|
| [Design](docs/superpowers/specs/2026-09-18-dayorganizer-design.md) | Fachliches Modell, Regeln, Architektur, Fehlerbehandlung |
| [Plan 1](docs/superpowers/plans/2026-09-18-dayorganizer-01-fundament.md) | Fundament und Erinnerungskern — umgesetzt |
| [Plan 2](docs/superpowers/plans/2026-09-18-dayorganizer-02-belohnungen.md) | Belohnungen, Statistik, Einstellungen — umgesetzt |
| [Plan 3](docs/superpowers/plans/2026-09-18-dayorganizer-03-widget-backup.md) | Widget und Backup — umgesetzt |
| [Plan 4](docs/superpowers/plans/2026-09-26-dayorganizer-04-designsystem-heute.md) | Redesign: Design-System und Heute — umgesetzt |
| [Plan 5](docs/superpowers/plans/2026-10-15-dayorganizer-05-aktivitaeten.md) | Redesign: Aktivitäten-Liste und Editor — umgesetzt |
| [Plan 6](docs/superpowers/plans/2026-10-15-dayorganizer-06-belohnungen.md) | Redesign: Belohnungen, Symbol, Einlösen rückgängig — umgesetzt |
| [Plan 7](docs/superpowers/plans/2026-10-16-dayorganizer-07-statistik-optionen-navigation.md) | Redesign: Statistik, Optionen, Navigationsleiste — umgesetzt |
| [Plan 8](docs/superpowers/plans/2026-10-16-dayorganizer-08-widget.md) | Redesign: Homescreen-Widget, README/HANDOFF — umgesetzt |
| [Design-Prototyp](docs/design/prototyp/) | Visuelle Vorlage für das Redesign (Pläne 4–8) |
| [Übergabe](docs/HANDOFF.md) | Stand, Umgebung, Arbeitsweise, Backlog |

Das Design-Dokument ist der Vertrag: es beschreibt, was die App tun soll und
warum sie es so tut. Wo Umsetzung und Design auseinandergingen, wurde das
Design nachgezogen, nicht der Code verbogen.

## Bewusst nicht enthalten

Notiz zur einzelnen Erledigung, Mengen- und Dauerziele mit Teilerfüllung,
Pause- und Urlaubsmodus, Cooldown auf Belohnungen, Cloud-Synchronisation,
Vollbild-Alarm, Minuspunkte bei Verpassen, getrennte Punktetöpfe je Bereich,
mehrsprachige Oberfläche. Alles nachrüstbar, ohne das Datenmodell umzuwerfen.
