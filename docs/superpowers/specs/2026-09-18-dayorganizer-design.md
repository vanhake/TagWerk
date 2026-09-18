# DayOrganizer — Design-Dokument

**Datum:** 2026-09-18
**Status:** Entwurf abgestimmt, bereit für den Implementierungsplan
**Repository:** https://github.com/vanhake/DayOrganizer.git

## 1. Zweck

DayOrganizer erinnert an wiederkehrende Alltagsaktivitäten und belohnt deren
Erledigung mit Punkten, die gegen selbst festgelegte Belohnungen eingelöst
werden. Die App richtet sich an Fälle, in denen die Erinnerung allein nicht
reicht und ein spürbarer Anreiz den Ausschlag gibt: Tabletten morgens und
abends, Medizin zum Mittagessen, dreimal pro Woche Sport.

Erfolgskriterium: Eine Erinnerung kommt verlässlich zur richtigen Zeit an, das
Abhaken dauert eine Sekunde, und der Punktestand macht den Fortschritt so
sichtbar, dass er trägt.

## 2. Rahmenbedingungen

| Entscheidung | Festlegung |
|---|---|
| Nutzerkreis | Der Autor sowie Familie und Freunde, Verteilung als APK |
| Datenhaltung | Ausschließlich lokal auf dem Gerät, kein Server, keine Konten |
| Plattform | Android nativ, Kotlin und Jetpack Compose |
| Mindestversion | Android 13, API 33 |
| Sprache | Deutsch, Texte in `strings.xml` ausgelagert |

Rein lokale Datenhaltung ist die tragende Entscheidung: kein Backend, keine
Registrierung, keine Datenschutzerklärung, keine Synchronisationskonflikte.
Der Preis ist, dass Backup und Export zur Pflichtfunktion werden, weil sonst
ein Gerätewechsel alle Daten vernichtet.

## 3. Fachliches Modell

### 3.1 Aktivität

Eine Aktivität ist etwas, das wiederkehrend getan werden soll.

| Feld | Bedeutung |
|---|---|
| `name`, `notiz`, `farbe`, `icon` | Darstellung |
| `basisPunkte` | Gutschrift je Erledigung |
| `zeitplan` | einer der vier Typen aus 3.2 |
| `kulanzMinuten` | Fenster um den Soll-Zeitpunkt für den Pünktlichkeitsbonus |
| `puenktlichkeitsBonus` | Zusatzpunkte bei Erledigung im Fenster |
| `serienSchwellen` | Liste aus (Tage, Bonuspunkte), z. B. 7 → +10, 30 → +50 |
| `nachfassAnzahl`, `nachfassAbstandMinuten` | Verhalten bei ignorierter Erinnerung |
| `aktiv`, `erstelltAm`, `archiviertAm` | Lebenszyklus |

Aktivitäten werden archiviert, nie gelöscht. Andernfalls verschwände mit der
Aktivität auch die Historie, die den Punktestand erklärt.

### 3.2 Zeitplantypen

1. **Feste Zeiten** — eine Menge von Uhrzeiten und eine Menge von Wochentagen.
   *Tabletten, Mo–So, 8:00 und 20:00.*
2. **Intervall** — alle N Stunden innerhalb eines aktiven Zeitfensters.
   *Alle 3 Stunden zwischen 8:00 und 22:00.*
3. **Tagesziel** — X-mal am Tag ohne feste Uhrzeit, an ausgewählten Wochentagen.
   *Dreimal täglich Rückenübung.*
4. **Wochenziel** — X-mal innerhalb einer Woche, Woche beginnt Montag.
   *Dreimal pro Woche Sport.*

Tages- und Wochenziele haben keinen Soll-Zeitpunkt, sollen aber trotzdem
erinnern können. Dafür tragen sie eine optionale Liste von **Anstoßzeiten**,
zu denen erinnert wird, solange das Ziel der laufenden Periode noch offen ist
(*Wochenziel Sport, Anstoß Di und Do 18:00: „noch 2-mal diese Woche"*). Ohne
Anstoßzeiten erscheint das Ziel nur in der App und im Widget.

### 3.3 Fälligkeit

Alle vier Zeitplantypen werden auf ein gemeinsames Objekt abgebildet. Eine
Fälligkeit ist die konkrete Instanz einer Aktivität in einem Zeitraum.

| Feld | Bedeutung |
|---|---|
| `aktivitaetId` | Zugehörigkeit |
| `periodeVon`, `periodeBis` | Tag bzw. Woche, in der sie gilt |
| `geplantFuer` | Soll-Zeitpunkt; `null` bei Tages- und Wochenzielen, die stattdessen über Anstoßzeiten erinnern |
| `sollAnzahl` | 1 bei festen Zeiten und Intervall, sonst X |
| `istAnzahl` | wie oft bereits erledigt |
| `status` | `OFFEN`, `ERLEDIGT`, `AUSGELASSEN`, `VERPASST` |
| `erledigtAm`, `warPuenktlich` | Auswertung |

Diese Vereinheitlichung hält Abhak-Logik, UI-Liste, Widget und Statistik frei
von Fallunterscheidungen über den Zeitplantyp.

### 3.4 Punktekonto als Buchungsjournal

Der Punktestand wird nicht als Zähler gespeichert, sondern als Summe aller
Buchungen berechnet. Jede Buchung hat Betrag, Zeitpunkt, Art und einen Verweis
auf ihren Auslöser.

Buchungsarten: `ERLEDIGUNG`, `PUENKTLICHKEITSBONUS`, `SERIENBONUS`,
`TAGESBONUS`, `EINLOESUNG` (negativ), `KORREKTUR`.

Das Journal liefert die Statistik nahezu kostenlos, macht jede Zahl erklärbar
und erlaubt Korrekturen, ohne Historie zu fälschen. Der aktuelle Stand wird als
laufende Summe gelesen; bei Bedarf lässt sich später ein Zwischenstand je Monat
einziehen, ohne dass sich die Semantik ändert.

### 3.5 Regeln

- Erledigung innerhalb des Kulanzfensters: Basispunkte plus Pünktlichkeitsbonus.
- Erledigung später am selben Tag: nur Basispunkte.
- Nach Mitternacht setzt der Wächter offene Fälligkeiten auf `VERPASST`.
  Die Serie bricht.
- `AUSGELASSEN` wird bewusst vom Verpassen getrennt: keine Punkte, aber die
  Serie überlebt. Ein bewusster Verzicht soll nicht wie Versagen wirken.
- Serien zählen in Perioden: in Tagen, bei Wochenzielen in Wochen. Eine
  Periode hält die Serie, wenn keine ihrer Fälligkeiten `VERPASST` ist. Eine
  ausgelassene Fälligkeit bricht die Serie also nicht, verlängert sie aber auch
  nicht — die Serie wird fortgeschrieben, nicht hochgezählt.
- Erreicht eine Serie eine konfigurierte Schwelle, wird der Bonus einmalig
  gebucht. Bricht die Serie und wächst erneut über dieselbe Schwelle, wird
  erneut gebucht.
- Der global konfigurierte Tagesbonus wird gebucht, wenn jede Fälligkeit des
  Tages den Status `ERLEDIGT` hat. Eine ausgelassene Fälligkeit verhindert den
  Tagesbonus, anders als bei der Serie.
- Pünktlichkeit gilt nur für Zeitplantypen mit konkretem Zeitpunkt.
  Tages- und Wochenziele kennen keinen Pünktlichkeitsbonus.
- Bei Tages- und Wochenzielen werden Basispunkte je Teilerledigung gebucht.

### 3.6 Belohnung und Einlösung

Eine Belohnung hat Name, Beschreibung, Icon und einen Preis in Punkten.
Optional kommt eine Zusatzbedingung hinzu:

- **Mindestserie** — eine benannte Aktivität muss eine Serie von mindestens
  N Perioden erreicht haben; Perioden sind Tage, bei Wochenzielen Wochen.
- **Frühestens ab** — ein Datum, vor dem nicht eingelöst werden kann.

Einlösen prüft Kontostand und Bedingung, bucht den Preis negativ und legt einen
Einlöse-Eintrag an, der Name und Preis als Momentaufnahme festhält. So bleibt
die Historie lesbar, auch wenn die Belohnung später umbenannt oder archiviert
wird. Belohnungen sind wiederholt einlösbar, solange der Kontostand reicht.

## 4. Architektur

### 4.1 Schichten

```
ui        Compose, Material 3, Single-Activity, Navigation,
          ein ViewModel je Screen, Zustand als StateFlow
domain    reines Kotlin, keine Android-Abhängigkeiten:
          ZeitplanRechner, PunkteRechner, SerienRechner, Use Cases
data      Room, DAOs, Repositories, Backup-Serialisierung
platform  AlarmPlaner, ErinnerungsEmpfaenger, AktionsEmpfaenger,
          BootEmpfaenger, WaechterWorker, Glance-Widget
```

Abhängigkeiten zeigen ausschließlich nach innen: `ui` und `platform` kennen
`domain`, `domain` kennt niemanden. Dependency Injection über Hilt, Gradle mit
Version Catalog.

### 4.2 Die drei Rechner

Der fachliche Kern liegt in drei reinen Funktionen ohne Android-Bezug.

- **`ZeitplanRechner`** — bildet aus Aktivität und Zeitraum die Menge der
  Fälligkeiten. Einziger Ort, an dem Zeitplantypen ausgewertet werden.
- **`PunkteRechner`** — bildet aus einer Erledigung und ihrem Kontext die Menge
  der zu schreibenden Buchungen. Einziger Ort, an dem Punkte entstehen.
- **`SerienRechner`** — bildet aus der Fälligkeitshistorie einer Aktivität die
  aktuelle und die längste Serie.

Alle drei bekommen eine `java.time.Clock` injiziert statt `now()` zu rufen.
Damit sind Mitternachtsübergänge, Kulanzfenster und Zeitumstellung in
Millisekunden testbar statt gar nicht.

### 4.3 Zeit und Zeitzonen

Zeitpläne werden als `LocalTime` und `DayOfWeek` gespeichert, Perioden als
`LocalDate`. Die Auflösung zu einem konkreten Zeitpunkt geschieht erst beim
Planen des Alarms, in der aktuellen Gerätezeitzone. Würde man absolute
Zeitpunkte speichern, verschöbe die Zeitumstellung die Tabletteneinnahme um
eine Stunde. Fällt eine Uhrzeit durch die Umstellung aus oder doppelt sich,
wird der nächstgelegene gültige Zeitpunkt genommen und der Termin einmal
gefeuert.

### 4.4 Erinnerungsmotor

Es ist zu jedem Zeitpunkt genau **ein** exakter Alarm gesetzt, nämlich für die
nächste fällige Erinnerung überhaupt.

1. `AlarmPlaner` ermittelt den frühesten anstehenden Erinnerungszeitpunkt und
   setzt `setExactAndAllowWhileIdle`. In diese eine Warteschlange laufen alle
   Quellen gleichberechtigt ein: Soll-Zeitpunkte fester und Intervall-Termine,
   Anstoßzeiten offener Tages- und Wochenziele, geplante Nachfass-Erinnerungen
   und per „Später" verschobene Termine.
2. `ErinnerungsEmpfaenger` zeigt die Benachrichtigung und plant unmittelbar den
   nächsten Alarm.
3. Jede Änderung an einer Aktivität oder Fälligkeit stößt eine Neuplanung an,
   die den einen Alarm ersetzt.

Damit entstehen keine verwaisten Alarme, die beim Bearbeiten oder Löschen einer
Aktivität aufzuräumen wären.

**Wächter.** Ein `WorkManager`-Job läuft täglich nachts sowie nach Neustart und
App-Update und tut drei Dinge: abgelaufene offene Fälligkeiten auf `VERPASST`
setzen, Fälligkeiten für die nächsten sieben Tage materialisieren, den nächsten
Alarm neu setzen. Der Wächter ist die Selbstheilung des Systems für den Fall,
dass Android einen Alarm verschluckt hat.

### 4.5 Benachrichtigungen

Jede Erinnerung trägt die Aktionen **Erledigt**, **Später** und **Ausgelassen**
und wird direkt aus der Statusleiste heraus verarbeitet, ohne die App zu öffnen.
"Später" plant eine Wiedervorlage im konfigurierten Abstand. Bleibt die
Benachrichtigung unbeantwortet, wird bis zu `nachfassAnzahl`-mal nachgefasst.
Eigener Notification Channel je Wichtigkeitsstufe, damit der Nutzer im System
differenzieren kann.

### 4.6 Berechtigungen

Unter API 33+ ist das der fragilste Teil und bekommt einen eigenen
Onboarding-Ablauf:

- `POST_NOTIFICATIONS` zur Laufzeit anfragen.
- `SCHEDULE_EXACT_ALARM` erklären und zum Systemdialog führen.
- Auf herstellerseitige Akkuoptimierung hinweisen. Geräte von Xiaomi, Samsung
  und Huawei beenden Hintergrundprozesse aggressiv; ohne Ausnahme bleiben
  Alarme aus.

Fehlt eine Berechtigung, erscheint auf *Heute* ein sichtbarer Warnbanner mit
direktem Sprung in die passende Einstellung. Die App scheitert an dieser Stelle
niemals still.

### 4.7 Widget

Ein Glance-Widget zeigt die heute offenen Fälligkeiten und den Punktestand.
Tippen hakt ab und schreibt über denselben Pfad wie App und Benachrichtigung.

### 4.8 Backup

Export und Import als JSON über den System-Dateidialog
(`ACTION_CREATE_DOCUMENT` und `ACTION_OPEN_DOCUMENT`). Die Datei trägt eine
Schema-Version; der Import lehnt unbekannte Versionen ab, statt die Datenbank
in einen inkonsistenten Zustand zu bringen. Enthalten sind Aktivitäten,
Fälligkeiten, Buchungen, Belohnungen, Einlösungen und Einstellungen.

## 5. Oberfläche

**Heute** — Fortschrittsring über die heutigen Fälligkeiten, Punktestand, Zeile
"noch 40 Punkte bis Kinoabend" mit Balken, darunter die Liste gruppiert in
überfällig, jetzt anstehend und später. Abhaken mit einem Tippen, bestätigt
durch kurze Animation, Punktgewinn-Einblendung und Vibration.

**Aktivitäten** — Liste aller aktiven Aktivitäten, Anlegen und Bearbeiten,
Archivieren. Die Detailansicht zeigt Serie, Erfüllungsquote und Historie.

**Belohnungen** — Kacheln mit Preis und Fortschrittsbalken; der Einlösen-Button
ist aktiv, sobald Kontostand und Bedingung passen, sonst steht dort, was noch
fehlt. Darunter die Einlöse-Historie.

**Statistik** — Kalender-Heatmap der erledigten Tage, Erfüllungsquote je
Aktivität, aktuelle und längste Serie, Punkteverlauf über die Zeit.

**Einstellungen** — Tagesbonus, Standardwerte für Kulanzfenster und Nachfassen,
Backup und Export, Berechtigungsstatus, Aktivitäten- und Belohnungsverwaltung.

## 6. Fehlerbehandlung

- **Doppelte Erledigung.** Das Abhaken läuft in einer Transaktion und ist
  idempotent: eine Fälligkeit kann nur einmal gebucht werden, auch wenn Widget,
  Benachrichtigung und App gleichzeitig zugreifen.
- **Exakter Alarm verweigert.** Fallback auf einen ungenauen Alarm, sichtbarer
  Hinweis, dass Erinnerungen verspätet kommen können.
- **Verschluckter Alarm.** Der nächtliche Wächter erkennt und repariert.
- **Beschädigtes Backup.** Schema-Version und Strukturprüfung vor dem Import,
  Abbruch mit klarer Meldung statt Teilimport.
- **Zeitumstellung.** Siehe 4.3; abgedeckt durch Tests.

## 7. Teststrategie

Schwerpunkt auf den drei Rechnern, jeweils mit fester Uhr:

- `ZeitplanRechner`: Wochentagsauswahl, Intervallgrenzen, Tages- und
  Wochenziele, Wochenwechsel am Montag, Zeitumstellung, Schaltjahr.
- `PunkteRechner`: Kulanzfenster innen und außen, Teilerledigung bei Zählzielen,
  Serienschwelle wird genau einmal gebucht, Tagesbonus.
- `SerienRechner`: Abbruch durch `VERPASST`, Überleben durch `AUSGELASSEN`,
  längste Serie über Lücken hinweg.

Ergänzend: Room-Tests gegen eine In-Memory-Datenbank inklusive Migrationen,
ViewModel-Tests auf den Zustandsflüssen, und wenige Instrumented-Tests für
Benachrichtigungsaktionen, Neuplanung nach Boot und Widget-Interaktion.

## 8. Bewusst nicht in Version 1

Notiz zur einzelnen Erledigung · Mengen- und Dauerziele mit Teilerfüllung ·
Pause- und Urlaubsmodus · Cooldown auf Belohnungen · Cloud-Synchronisation ·
Vollbild-Alarm für kritische Aktivitäten · Minuspunkte bei Verpassen ·
getrennte Punktetöpfe je Bereich · mehrsprachige Oberfläche.

Alle diese Punkte lassen sich nachrüsten, ohne das Datenmodell umzuwerfen:
Fälligkeit und Buchungsjournal tragen die nötigen Freiheitsgrade bereits.
