# DayOrganizer

Eine Android-App für wiederkehrende Alltagsaktivitäten, an die man erinnert
werden möchte — Tabletten morgens und abends, Medizin zum Mittagessen, dreimal
die Woche Sport. Jede Erledigung bringt Punkte, und die Punkte gibt man für
Belohnungen aus, die man sich selbst gesetzt hat.

Die App läuft vollständig lokal: kein Server, kein Konto, keine
Netzwerkberechtigung. Sie ist für den privaten Gebrauch gedacht und wird als
APK verteilt, nicht über den Play Store.

## Stand

Das Fundament steht: Aktivitäten anlegen, zuverlässig erinnert werden, aus der
Benachrichtigung abhaken, Punkte sammeln. Der Belohnungs-Shop, die Statistik,
die Einstellungen, ein Homescreen-Widget und das JSON-Backup sind geplant und
als eigene Pläne beschrieben (siehe [Dokumentation](#dokumentation)).

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

**Stack:** Kotlin, Jetpack Compose (Material 3), Room, Hilt, WorkManager,
AlarmManager. Mindestens Android 13 (API 33), Ziel API 35.

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

Die Release-Variante wird bewusst nicht gebaut: sie wäre unsigniert und ließe
sich nicht installieren. Für eine signierte Release-APK bräuchte es einen
eigenen Schlüssel in den GitHub-Secrets.

## Tests

```bash
./gradlew :domain:test :app:testDebugUnitTest
```

167 Tests, alle auf der JVM. Datenbanktests laufen unter Robolectric, es wird
also weder ein Gerät noch ein Emulator gebraucht. Der Schwerpunkt liegt auf den
drei Regelmaschinen, jeweils mit fester Uhr: Wochentagsauswahl,
Intervallgrenzen, Zähl- und Wochenziele, Kulanzfenster, Serienabbruch durch
Verpassen gegenüber Überleben durch Auslassen, Zeitumstellung, Schaltjahr.
Dazu Room-Tests inklusive Migration, Use-Case- und ViewModel-Tests.

## Dokumentation

| Datei | Inhalt |
|---|---|
| [Design](docs/superpowers/specs/2026-09-18-dayorganizer-design.md) | Fachliches Modell, Regeln, Architektur, Fehlerbehandlung |
| [Plan 1](docs/superpowers/plans/2026-09-18-dayorganizer-01-fundament.md) | Fundament und Erinnerungskern — umgesetzt |
| [Plan 2](docs/superpowers/plans/2026-09-18-dayorganizer-02-belohnungen.md) | Belohnungen, Statistik, Einstellungen — offen |
| [Plan 3](docs/superpowers/plans/2026-09-18-dayorganizer-03-widget-backup.md) | Widget und Backup — offen |

Das Design-Dokument ist der Vertrag: es beschreibt, was die App tun soll und
warum sie es so tut. Wo Umsetzung und Design auseinandergingen, wurde das
Design nachgezogen, nicht der Code verbogen.

## Bewusst nicht enthalten

Notiz zur einzelnen Erledigung, Mengen- und Dauerziele mit Teilerfüllung,
Pause- und Urlaubsmodus, Cooldown auf Belohnungen, Cloud-Synchronisation,
Vollbild-Alarm, Minuspunkte bei Verpassen, getrennte Punktetöpfe je Bereich,
mehrsprachige Oberfläche. Alles nachrüstbar, ohne das Datenmodell umzuwerfen.
