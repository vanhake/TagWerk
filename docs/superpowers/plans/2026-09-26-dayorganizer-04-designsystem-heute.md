# DayOrganizer Redesign 1: Design-System-Fundament + Heute Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das neue visuelle Redesign (`DayOrganizer App.dc.html`, Prototyp "DayOrganizer · Redesign") auf das Design-System der App und den Heute-Bildschirm anwenden, inklusive der zwei Datenmodell-Erweiterungen, die der Prototyp voraussetzt (Aktivitätensymbol, persistierter Hell/Dunkel-Modus) und eines echten Rückgängig-Mechanismus für Erledigen/Auslassen.

**Architecture:** Neue Design-Tokens (Farben/Formen/Typografie) leben in `ui/theme` und werden über ein `CompositionLocal` bereitgestellt, das `MaterialTheme` ergänzt statt ersetzt. Das Aktivitätensymbol ist ein reiner String-Schlüssel im Domänenmodell (Room-Migration 3→4, Backup-Schema 2), gerendert über einen zustandslosen Schlüssel→ImageVector-Katalog. Rückgängig ist ein echter Anwendungsfall (kein optimistisches UI-Overlay): Erledigen/Auslassen bleibt sofort committet, Rückgängig bucht eine exakte Gegenbuchung (`BuchungsArt.KORREKTUR`) und stellt den vorherigen `Faelligkeit`-Schnappschuss wieder her -- damit bleibt die Punkte-Historie erklärbar, wie im Design-Dokument gefordert.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), Room, Hilt, DataStore, kotlinx.serialization. Neu: eine gebündelte Manrope-Variable-Font-Datei (`res/font/manrope.ttf`, OFL-lizenziert von Google Fonts) statt Google-Play-Services-Downloadable-Fonts -- die App bleibt dadurch vollständig offline (README: "Läuft vollständig lokal · kein Konto, kein Server").

## Global Constraints

- Minimale API-Version bleibt 33, Ziel-API 35 (unverändert, siehe `app/build.gradle.kts`).
- Keine neue Netzwerkberechtigung, kein neuer Netzwerkzugriff zur Laufzeit -- die Manrope-Schrift wird als Ressource gebündelt, nicht heruntergeladen.
- Bestehende deutsche Namenskonvention für Klassen, Funktionen und Ressourcen-Keys wird fortgeführt (z. B. `Aktivitaet`, `Faelligkeit`, `Rueckgaengig`, keine englischen Bezeichner in neuem Code).
- Jede Änderung an `AktivitaetEntity`/`DayOrganizerDatenbank` braucht eine begleitende `Migration` und einen `MigrationTest` (bestehendes Muster in `Migrationen.kt`/`MigrationTest.kt`).
- Jede Änderung an `BackupDatei`/`AktivitaetDto`/`EinstellungenDto` erhöht `BackupUmwandlung.SCHEMA_VERSION` und bekommt eine neue eingefrorene Beispieldatei unter `app/src/test/resources/backup/` (bestehendes Muster, siehe `beispiel-schema-1.json`).
- Punkte-Korrekturen entstehen ausschließlich als neue `Buchung`-Zeilen mit `art = BuchungsArt.KORREKTUR`, nie als Löschung oder Überschreibung bestehender Buchungen (README: "Korrekturen fälschen keine Historie").
- Nach jedem Task: `./gradlew :domain:test :app:testDebugUnitTest` muss grün sein, bevor committet wird.
- `heroStil` und `showWidget` aus dem `.dc.html`-Prototyp sind Editor-Vorschauparameter des Design-Tools (`data-props`), keine App-Funktionen -- dieser Plan implementiert nur den Standardfall (`heroStil = 'Fläche'`, solide Heldenkarte).

---

## Datei-Übersicht

| Datei | Zweck |
|---|---|
| `domain/.../model/Aktivitaet.kt` | `icon`-Feld ergänzen |
| `app/.../ui/aktivitaeten/AktivitaetEntwurf.kt` | `icon`-Feld im Entwurf |
| `app/.../data/db/AktivitaetEntity.kt` | `icon`-Spalte |
| `app/.../data/db/Migrationen.kt` | `MIGRATION_3_4` |
| `app/.../data/db/DayOrganizerDatenbank.kt` | Version 3 → 4 |
| `app/.../di/DatenModul.kt` | neue Migration registrieren |
| `app/.../data/mapper/EntityMapper.kt` | `icon` durchreichen |
| `app/.../backup/BackupModell.kt` | `AktivitaetDto.icon`, `EinstellungenDto.themeModus` |
| `app/.../backup/BackupUmwandlung.kt` | `SCHEMA_VERSION = 2`, Mapping |
| `app/.../data/EinstellungenSpeicher.kt` | `ThemeModus`, Persistenz |
| `app/.../ui/theme/Farben.kt` (neu) | Farb-Tokens hell/dunkel |
| `app/.../ui/theme/Formen.kt` (neu) | Form-Tokens |
| `app/.../ui/theme/Typografie.kt` (neu) | Manrope-Typografie |
| `app/.../ui/theme/Thema.kt` | `DayOrganizerThema` neu verdrahtet |
| `app/.../ui/theme/ThemaViewModel.kt` (neu) | liest persistierten Modus für `MainActivity` |
| `app/.../MainActivity.kt` | Modus an `DayOrganizerThema` übergeben |
| `app/src/main/res/font/manrope.ttf` (neu) | gebündelte Schriftdatei |
| `app/.../ui/theme/AktivitaetSymbol.kt` (neu) | Schlüssel → `ImageVector` |
| `app/.../ui/einstellungen/EinstellungenScreen.kt` | provisorischer Hell/Dunkel-Umschalter |
| `app/.../ui/einstellungen/EinstellungenViewModel.kt` | `themeModusAendern` |
| `app/.../domainuse/FaelligkeitErledigen.kt` | `ErledigungsProtokoll` + `mitProtokoll` |
| `app/.../domainuse/FaelligkeitAuslassen.kt` | `mitProtokoll` |
| `app/.../domainuse/FaelligkeitErledigenRueckgaengig.kt` (neu) | Gegenbuchung |
| `app/.../domainuse/FaelligkeitAuslassenRueckgaengig.kt` (neu) | Status zurücksetzen |
| `app/.../ui/gemeinsam/Hinweistoast.kt` (neu) | Toast-Datenklasse + Composable |
| `app/.../ui/heute/HeuteZustand.kt` | `icon`, `toast`-Feld |
| `app/.../ui/heute/HeuteZusammenstellung.kt` | `icon` übernehmen |
| `app/.../ui/heute/HeuteViewModel.kt` | Toast/Undo-Zustand |
| `app/.../ui/heute/HeuteScreen.kt` | vollständiges Redesign |
| `app/src/main/res/values/strings.xml` | neue Strings |

---

### Task 1: Aktivitätensymbol im Domänenmodell

**Files:**
- Modify: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/model/Aktivitaet.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetEntwurf.kt`
- Test: `domain/src/test/kotlin/de/vanhake/dayorganizer/domain/model/AktivitaetTest.kt` (neu, falls es keine gibt)

**Interfaces:**
- Produces: `Aktivitaet.icon: String` (Default `"self_improvement"`), `AktivitaetEntwurf.icon: String` (Default `"self_improvement"`).

- [ ] **Step 1: Failing test für den Default-Wert**

```kotlin
package de.vanhake.dayorganizer.domain.model

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class AktivitaetTest {
    @Test
    fun `eine neue Aktivitaet bekommt ein Standardsymbol`() {
        val aktivitaet = Aktivitaet(
            name = "Test", farbe = 0, basisPunkte = 1,
            zeitplan = Zeitplan.Wochenziel(1),
            erstelltAm = LocalDate.of(2026, 1, 1),
        )

        assertEquals("self_improvement", aktivitaet.icon)
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :domain:test --tests "de.vanhake.dayorganizer.domain.model.AktivitaetTest"`
Expected: FAIL -- `Aktivitaet` hat keinen Parameter `icon`.

- [ ] **Step 3: Feld ergänzen**

In `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/model/Aktivitaet.kt`, nach `val puenktlichkeitsBonus: Int = 0,` einfügen:

```kotlin
    /** Schluessel aus dem AktivitaetSymbol-Katalog (App-Modul), z. B. "self_improvement". */
    val icon: String = "self_improvement",
```

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `./gradlew :domain:test --tests "de.vanhake.dayorganizer.domain.model.AktivitaetTest"`
Expected: PASS

- [ ] **Step 5: Entwurf ergänzen**

In `AktivitaetEntwurf.kt`: Feld nach `val farbe: Int = 0xFF4CAF50.toInt(),` einfügen:

```kotlin
    val icon: String = "self_improvement",
```

In `zuAktivitaet(heute: LocalDate)`, im `Aktivitaet(...)`-Aufruf nach `farbe = farbe,` ergänzen:

```kotlin
            icon = icon,
```

In der companion-Funktion `aus(aktivitaet: Aktivitaet)`, im `basis`-Aufbau nach `farbe = aktivitaet.farbe,` ergänzen:

```kotlin
                icon = aktivitaet.icon,
```

- [ ] **Step 6: App-Modul kompiliert**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (keine anderen Aufrufer von `Aktivitaet(...)`/`AktivitaetEntwurf(...)` brechen, da beide Felder Default-Werte haben).

- [ ] **Step 7: Commit**

```bash
git add domain/src/main/kotlin/de/vanhake/dayorganizer/domain/model/Aktivitaet.kt \
        domain/src/test/kotlin/de/vanhake/dayorganizer/domain/model/AktivitaetTest.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetEntwurf.kt
git commit -m "feat(aktivitaet): Symbol-Feld fuer das Redesign ergaenzen"
```

---

### Task 2: Room-Migration 3 → 4 für die `icon`-Spalte

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/AktivitaetEntity.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/Migrationen.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/DayOrganizerDatenbank.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/di/DatenModul.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/mapper/EntityMapper.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/data/db/MigrationTest.kt`

**Interfaces:**
- Consumes: `Aktivitaet.icon` (Task 1).
- Produces: `AktivitaetEntity.icon: String`, `MIGRATION_3_4`.

- [ ] **Step 1: Failing Migrationstest**

In `MigrationTest.kt`, nach der bestehenden `migration_1_auf_3_laeuft_durch`-Testmethode ergänzen:

```kotlin
    @Test
    fun `migration_3_auf_4 ergaenzt die Symbol-Spalte mit Standardwert`() {
        helfer.createDatabase(DB_NAME, 3).apply {
            execSQL(
                "INSERT INTO aktivitaet (id, name, notiz, farbe, basisPunkte, kulanzMinuten, " +
                    "puenktlichkeitsBonus, serienSchwellen, nachfassAnzahl, nachfassAbstandMinuten, " +
                    "aktiv, erstelltAm, archiviertAm, zeitplanTyp, zeitplanZeiten, " +
                    "zeitplanWochentage, zeitplanIntervallStunden, zeitplanFensterVon, " +
                    "zeitplanFensterBis, zeitplanZielAnzahl, zeitplanAnstoss) VALUES " +
                    "(1, 'Tabletten', '', 0, 10, 60, 0, '', 2, 15, 1, '2026-01-01', NULL, " +
                    "'FESTE_ZEITEN', '08:00', 'MO,DI,MI,DO,FR,SA,SO', NULL, NULL, NULL, NULL, '')",
            )
            close()
        }

        val db = helfer.runMigrationsAndValidate(
            DB_NAME, 4, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
        )

        db.query("SELECT icon FROM aktivitaet WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("self_improvement", it.getString(0))
        }
        db.close()
    }
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.data.db.MigrationTest"`
Expected: FAIL -- `MIGRATION_3_4` existiert nicht / Spalte `icon` fehlt in Version 3.

- [ ] **Step 3: Entity, Migration, Datenbankversion**

In `AktivitaetEntity.kt`, nach `val puenktlichkeitsBonus: Int,` ergänzen:

```kotlin
    val icon: String = "self_improvement",
```

In `Migrationen.kt` am Ende ergänzen:

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE aktivitaet ADD COLUMN icon TEXT NOT NULL DEFAULT 'self_improvement'",
        )
    }
}
```

In `DayOrganizerDatenbank.kt`: `version = 3` zu `version = 4` ändern.

In `DatenModul.kt`: Import `MIGRATION_3_4` ergänzen und `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)` setzen.

- [ ] **Step 4: Mapper ergänzen**

In `EntityMapper.kt`, `AktivitaetEntity.zuDomaene()`: nach `farbe = farbe,` ergänzen `icon = icon,`.
In `Aktivitaet.zuEntity()`: nach `farbe = farbe,` ergänzen `icon = icon,`.

- [ ] **Step 5: Test ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.data.db.MigrationTest"`
Expected: PASS (alle drei bestehenden Migrationstests bleiben ebenfalls grün).

- [ ] **Step 6: Room-Schema exportieren**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL. Dabei erzeugt KSP `app/schemas/de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank/4.json` neu.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/data/db/AktivitaetEntity.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/data/db/Migrationen.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/data/db/DayOrganizerDatenbank.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/di/DatenModul.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/data/mapper/EntityMapper.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/data/db/MigrationTest.kt \
        app/schemas/de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank/4.json
git commit -m "feat(db): Migration 3-4 fuer die Symbol-Spalte der Aktivitaet"
```

---

### Task 3: Backup-Schema 2 (Symbol + Farbmodus)

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/backup/BackupModell.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlung.kt`
- Modify: `app/src/test/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlungTest.kt`
- Create: `app/src/test/resources/backup/beispiel-schema-2.json`

**Interfaces:**
- Consumes: `Aktivitaet.icon` (Task 1), `Einstellungen.themeModus` (Task 4 -- diese Datei zuerst committen, dann Task 4, dann diesen Task; siehe Hinweis in Step 3).
- Produces: `BackupUmwandlung.SCHEMA_VERSION == 2`.

**Hinweis zur Reihenfolge:** Dieser Task braucht `ThemeModus` aus Task 4. Führe Task 4 zuerst aus, dann diesen Task -- die Tabellen-Nummerierung im Plan folgt der Abhängigkeit, nicht zwingend der Ausführungsreihenfolge; wer die Tasks in der oben aufgeführten Zahlenreihenfolge abarbeitet, tauscht schlicht Task 3 und Task 4.

- [ ] **Step 1: Failing Test -- Symbol übersteht den Rundlauf**

In `BackupUmwandlungTest.kt`, das `aktivitaet`-Fixture um ein abweichendes Symbol erweitern:

```kotlin
    private val aktivitaet = Aktivitaet(
        id = 1, name = "Tabletten", notiz = "morgens und abends", farbe = 42, basisPunkte = 10,
        icon = "medication",
        zeitplan = Zeitplan.FesteZeiten(
            listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
        ),
        kulanzMinuten = 45, puenktlichkeitsBonus = 5,
        serienSchwellen = listOf(SerienSchwelle(7, 10)),
        nachfassAnzahl = 2, nachfassAbstandMinuten = 15,
        erstelltAm = LocalDate.of(2026, 1, 1),
    )
```

Und die `datei()`-Hilfsfunktion sowie jede Stelle, die `Einstellungen(...)` für den Rundlauf baut, um `themeModus = ThemeModus.DUNKEL` erweitern, z. B.:

```kotlin
        einstellungen = Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45, themeModus = ThemeModus.DUNKEL),
```

(Diese Änderung betrifft `datei()`, `beispielInhaltSchema1()`/wird in Step 7 zu `beispielInhaltSchema2()`, und den `abweichendeEinstellungen`-Wert weiter unten in der Datei -- dort `themeModus = ThemeModus.HELL` setzen, damit beide Enum-Werte mindestens einmal getestet sind.)

Import ergänzen: `import de.vanhake.dayorganizer.data.ThemeModus`.

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.backup.BackupUmwandlungTest"`
Expected: FAIL -- Compile-Fehler, `Aktivitaet` kennt `icon` zwar schon (Task 1), aber `Einstellungen` kennt `themeModus` noch nicht (Task 4 muss zuerst fertig sein) bzw. `AktivitaetDto`/`EinstellungenDto` haben die Felder noch nicht.

- [ ] **Step 3: DTOs erweitern**

In `BackupModell.kt`, `AktivitaetDto`: nach `val farbe: Int,` ergänzen:

```kotlin
    val icon: String,
```

`EinstellungenDto`: um `themeModus` erweitern:

```kotlin
@Serializable
data class EinstellungenDto(
    val tagesbonusPunkte: Int,
    val standardKulanzMinuten: Int,
    val themeModus: String,
)
```

- [ ] **Step 4: Schemaversion und Mapping**

In `BackupUmwandlung.kt`: `const val SCHEMA_VERSION = 2`.

In `zuDatei(...)`, den `einstellungen`-Block erweitern:

```kotlin
        einstellungen = EinstellungenDto(
            tagesbonusPunkte = einstellungen.tagesbonusPunkte,
            standardKulanzMinuten = einstellungen.standardKulanzMinuten,
            themeModus = einstellungen.themeModus.name,
        ),
```

In `ausDatei(...)`, den `einstellungen`-Block erweitern:

```kotlin
            einstellungen = Einstellungen(
                tagesbonusPunkte = datei.einstellungen.tagesbonusPunkte,
                standardKulanzMinuten = datei.einstellungen.standardKulanzMinuten,
                // Ein unbekannter Modus-Text (kuenftige Version, beschaedigte Datei) faellt auf
                // HELL zurueck, statt den ganzen Import an einem kosmetischen Feld scheitern zu
                // lassen -- anders als bei Belohnungsbedingungen ist das hier kein Vertragsbruch.
                themeModus = runCatching { ThemeModus.valueOf(datei.einstellungen.themeModus) }
                    .getOrDefault(ThemeModus.HELL),
            ),
```

`Aktivitaet.zuDto()`: nach `farbe = farbe,` ergänzen `icon = icon,`.
`AktivitaetDto.zuDomaene()`: nach `farbe = farbe,` ergänzen `icon = icon,`.

Import ergänzen: `import de.vanhake.dayorganizer.data.ThemeModus`.

- [ ] **Step 5: Test ausführen, Erfolg bestätigen (bis auf die Fixture-Tests)**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.backup.BackupUmwandlungTest"`
Expected: Die Rundlauf-Tests sind grün; `die eingefrorene Beispieldatei fuer Schema 1 wird zu den erwarteten Domaenenobjekten gelesen` und `dieselben Domaenenobjekte erzeugen wieder genau die eingefrorene Beispieldatei` schlagen jetzt fehl, weil `SCHEMA_VERSION` 2 ist -- das ist erwartet, siehe nächster Schritt.

- [ ] **Step 6: Alte Fixture als Ablehnungs-Beweis umwidmen**

Die beiden genannten Tests ersetzen durch:

```kotlin
    @Test
    fun `ein Schema-1-Backup wird nach dem Sprung auf Schema 2 abgelehnt, nicht stillschweigend falsch gelesen`() {
        val fehler = assertFailsWith<UnbekanntesSchema> { BackupUmwandlung.ausText(beispielDateiSchema1) }

        assertEquals(1, fehler.gefunden)
    }
```

(`beispielInhaltSchema1()` und deren Aufruf entfallen damit -- falls sie nirgends sonst referenziert werden, die Hilfsfunktion löschen.)

- [ ] **Step 7: Neue eingefrorene Beispieldatei für Schema 2**

Neue Hilfsfunktion und Tests ergänzen (gleiche Fixtures wie oben, jetzt mit Symbol/Modus):

```kotlin
    private fun beispielInhaltSchema2() = BackupUmwandlung.zuDatei(
        erstelltAm = LocalDateTime.of(2026, 3, 2, 21, 0),
        aktivitaeten = listOf(aktivitaet),
        faelligkeiten = listOf(faelligkeit),
        buchungen = listOf(buchung),
        belohnungen = listOf(belohnung),
        einloesungen = listOf(einloesung),
        einstellungen = Einstellungen(
            tagesbonusPunkte = 25, standardKulanzMinuten = 45, themeModus = ThemeModus.DUNKEL,
        ),
    )

    private val beispielDateiSchema2 = javaClass.classLoader
        ?.getResourceAsStream("backup/beispiel-schema-2.json")!!
        .bufferedReader()
        .readText()

    @Test
    fun `die eingefrorene Beispieldatei fuer Schema 2 wird zu den erwarteten Domaenenobjekten gelesen`() {
        val inhalt = BackupUmwandlung.ausText(beispielDateiSchema2)

        assertEquals(listOf(aktivitaet), inhalt.aktivitaeten)
        assertEquals(listOf(faelligkeit), inhalt.faelligkeiten)
        assertEquals(listOf(buchung), inhalt.buchungen)
        assertEquals(listOf(belohnung), inhalt.belohnungen)
        assertEquals(listOf(einloesung), inhalt.einloesungen)
        assertEquals(
            Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45, themeModus = ThemeModus.DUNKEL),
            inhalt.einstellungen,
        )
    }

    @Test
    fun `dieselben Domaenenobjekte erzeugen wieder genau die eingefrorene Beispieldatei fuer Schema 2`() {
        val text = BackupUmwandlung.zuText(beispielInhaltSchema2())

        assertEquals(beispielDateiSchema2.trim(), text.trim())
    }
```

Die Datei selbst existiert noch nicht -- sie wird im nächsten Schritt aus dem Code heraus erzeugt statt von Hand geschrieben, damit sie garantiert exakt zur aktuellen Serialisierung passt.

- [ ] **Step 8: Beispieldatei generieren**

Die Datei wird nicht von Hand geschrieben, sondern aus der tatsächlichen Serialisierung übernommen, damit sie garantiert exakt zum Code passt: lege zunächst eine leere Platzhalterdatei an, lass den Test aus Step 7 bewusst fehlschlagen und kopiere die tatsächliche Ausgabe aus der Fehlermeldung.

Run: `mkdir -p app/src/test/resources/backup && echo '{}' > app/src/test/resources/backup/beispiel-schema-2.json`

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.backup.BackupUmwandlungTest.dieselben Domaenenobjekte erzeugen wieder genau die eingefrorene Beispieldatei fuer Schema 2"`
Expected: FAIL mit einer `assertEquals`-Fehlermeldung der Form "Expected <{}>, Actual <{... vollständiger JSON-Text ...}>". Kopiere den "Actual"-Wert unverändert (inklusive Zeilenumbrüchen) in `app/src/test/resources/backup/beispiel-schema-2.json`, überschreibe damit die Platzhalterdatei.

- [ ] **Step 9: Beide Tests final ausführen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.backup.BackupUmwandlungTest"`
Expected: PASS, alle Tests der Klasse.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/backup/BackupModell.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlung.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlungTest.kt \
        app/src/test/resources/backup/beispiel-schema-2.json
git commit -m "feat(backup): Schema 2 mit Aktivitaetensymbol und Farbmodus"
```

---

### Task 4: Persistierter Farbmodus (`ThemeModus`)

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/EinstellungenSpeicher.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/data/EinstellungenSpeicherTest.kt` (falls vorhanden, sonst neu anlegen -- prüfe zuerst mit `find app/src/test -iname "*Einstellungen*"`)

**Interfaces:**
- Produces: `enum class ThemeModus { HELL, DUNKEL }`, `Einstellungen.themeModus: ThemeModus = ThemeModus.HELL`.

- [ ] **Step 1: Prüfen, ob es bereits einen Test für `EinstellungenSpeicher` gibt**

Run: `find "app/src/test" -iname "*EinstellungenSpeicher*"`
Expected: Ergebnis bestimmt, ob Step 2 eine neue Datei anlegt oder eine bestehende erweitert. Für diesen Plan wird von "keine vorhandene Datei" ausgegangen; existiert doch eine, die folgenden Tests dort ergänzen statt einer neuen Datei.

- [ ] **Step 2: Failing Test**

```kotlin
package de.vanhake.dayorganizer.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class EinstellungenSpeicherTest {

    private fun neuerSpeicher(): EinstellungenSpeicher {
        val kontext = ApplicationProvider.getApplicationContext<Context>()
        val datei = File(kontext.filesDir, "test-einstellungen-${System.nanoTime()}.preferences_pb")
        val store = PreferenceDataStoreFactory.create(
            corruptionHandler = EINSTELLUNGEN_BEI_KORRUPTION,
        ) { datei }
        return EinstellungenSpeicher(store)
    }

    @Test
    fun `ohne gespeicherten Modus gilt hell als Standard`() = runTest {
        val speicher = neuerSpeicher()

        assertEquals(ThemeModus.HELL, speicher.lesen().themeModus)
    }

    @Test
    fun `der Farbmodus wird gespeichert und wieder gelesen`() = runTest {
        val speicher = neuerSpeicher()

        speicher.schreiben(Einstellungen(themeModus = ThemeModus.DUNKEL))

        assertEquals(ThemeModus.DUNKEL, speicher.lesen().themeModus)
    }
}
```

- [ ] **Step 3: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.data.EinstellungenSpeicherTest"`
Expected: FAIL -- Compile-Fehler, `ThemeModus` und `Einstellungen.themeModus` existieren nicht.

- [ ] **Step 4: Implementieren**

In `EinstellungenSpeicher.kt`, `Einstellungen` erweitern und `ThemeModus` ergänzen:

```kotlin
enum class ThemeModus { HELL, DUNKEL }

data class Einstellungen(
    val tagesbonusPunkte: Int = 15,
    val standardKulanzMinuten: Int = 60,
    val themeModus: ThemeModus = ThemeModus.HELL,
)
```

Import ergänzen: `import androidx.datastore.preferences.core.stringPreferencesKey`.

In `EinstellungenSpeicher`, neuen Key ergänzen:

```kotlin
    private val themeModus = stringPreferencesKey("theme_modus")
```

`beobachten()` erweitern:

```kotlin
    fun beobachten(): Flow<Einstellungen> = gesicherterDatenstrom.map {
        Einstellungen(
            tagesbonusPunkte = it[tagesbonus] ?: 15,
            standardKulanzMinuten = it[kulanz] ?: 60,
            themeModus = it[themeModus]?.let { name -> runCatching { ThemeModus.valueOf(name) }.getOrNull() }
                ?: ThemeModus.HELL,
        )
    }
```

`schreiben(...)` erweitern:

```kotlin
    suspend fun schreiben(einstellungen: Einstellungen) {
        datenStore.edit {
            it[tagesbonus] = einstellungen.tagesbonusPunkte
            it[kulanz] = einstellungen.standardKulanzMinuten
            it[themeModus] = einstellungen.themeModus.name
        }
    }
```

- [ ] **Step 5: Test ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.data.EinstellungenSpeicherTest"`
Expected: PASS

- [ ] **Step 6: Gesamten Testlauf prüfen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL (Task 3 kann jetzt, falls noch nicht geschehen, seine `themeModus`-Referenzen kompilieren).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/data/EinstellungenSpeicher.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/data/EinstellungenSpeicherTest.kt
git commit -m "feat(einstellungen): persistierten Hell/Dunkel-Modus ergaenzen"
```

---

### Task 5: Design-System-Fundament (Farben, Formen, Typografie, Manrope)

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Farben.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Formen.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Typografie.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/ThemaViewModel.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Thema.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/MainActivity.kt`
- Create (binär): `app/src/main/res/font/manrope.ttf`
- Create: `docs/licenses/manrope-OFL.txt`

**Interfaces:**
- Consumes: `ThemeModus` (Task 4).
- Produces: `DayOrganizerTheme.farben` (Composable-Property), `DayOrganizerFormen`, `HellFarben`/`DunkelFarben`, `DayOrganizerThema(themeModus, inhalt)`.

Dieser Task hat keine sinnvolle "roten Test" TDD-Form (reine Farbwert-/Ressourcen-Definitionen ohne verzweigte Logik) -- stattdessen: Implementieren, dann mit einem Kompilier- und einem einfachen Wertetest absichern.

- [ ] **Step 1: Manrope-Schriftdatei laden**

Run:
```bash
mkdir -p app/src/main/res/font docs/licenses
curl -sL -o app/src/main/res/font/manrope.ttf \
  "https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/Manrope%5Bwght%5D.ttf"
curl -sL -o docs/licenses/manrope-OFL.txt \
  "https://raw.githubusercontent.com/google/fonts/main/ofl/manrope/OFL.txt"
sha256sum app/src/main/res/font/manrope.ttf
```
Expected: `sha256sum` druckt `3ae11c49db0455a3cc33e37d380f20fdb8c7f8b41dc07625c177e3d87a9d6ae6  app/src/main/res/font/manrope.ttf` (Größe 164700 Bytes). Weicht der Hash ab, ist entweder der Download beschädigt oder Google hat die Datei aktualisiert -- in letzterem Fall den Hash hier im Plan-Kommentar aktualisieren, nicht stillschweigend akzeptieren.

Android-Ressourcendateinamen dürfen keine eckigen Klammern enthalten, deshalb heißt die lokale Datei `manrope.ttf`, nicht `Manrope[wght].ttf`.

- [ ] **Step 2: Farb-Tokens**

Erzeuge `Farben.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantische Farbrollen aus dem Redesign-Prototyp (DayOrganizer App.dc.html).
 * Die oklch(...)-Werte des Prototyps sind vorab in sRGB umgerechnet (Standard-
 * CSS-Color-4-Formel OKLCH -> OKLab -> linear sRGB -> sRGB), damit hier keine
 * Farbraum-Mathematik zur Laufzeit noetig ist.
 */
data class DayOrganizerFarben(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val ink2: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val hero: Color,
    val accentSoft: Color,
    val onAccentSoft: Color,
    val coral: Color,
    val coralSoft: Color,
    val green: Color,
    val greenSoft: Color,
    val goldSoft: Color,
    val goldInk: Color,
    val inverse: Color,
    val onInverse: Color,
)

/** Feste Literale des Prototyps, die nicht vom Farbschema abhaengen (kein var() im CSS). */
object DayOrganizerFesteFarben {
    val heroUeberfaelligSolid = Color(0xFFD33C33)
    val toastRueckgaengigInk = Color(0xFFAFABFF)
    val heroChipflaeche = Color(0x2EFFFFFF)
    val heroGeisterlinie = Color(0x73FFFFFF)
}

val HellFarben = DayOrganizerFarben(
    bg = Color(0xFFF6F5F9), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFEFEDF4),
    ink = Color(0xFF17151F), ink2 = Color(0xFF5F5B6E), line = Color(0x1417151F),
    accent = Color(0xFF6445DF), onAccent = Color(0xFFFFFFFF), hero = Color(0xFF5F3ED8),
    accentSoft = Color(0xFFE8E8FF), onAccentSoft = Color(0xFF452AA2),
    coral = Color(0xFFD33C33), coralSoft = Color(0xFFFFE7E3),
    green = Color(0xFF0E9254), greenSoft = Color(0xFFDAF7E3),
    goldSoft = Color(0xFFFFF0D0), goldInk = Color(0xFF8A5600),
    inverse = Color(0xFF1D1B25), onInverse = Color(0xFFFFFFFF),
)

val DunkelFarben = DayOrganizerFarben(
    bg = Color(0xFF100F15), surface = Color(0xFF1B1923), surface2 = Color(0xFF26232F),
    ink = Color(0xFFF3F1F8), ink2 = Color(0xFFA6A2B5), line = Color(0x17FFFFFF),
    accent = Color(0xFF968BFF), onAccent = Color(0xFF190F41), hero = Color(0xFF5437C0),
    accentSoft = Color(0xFF2D2957), onAccentSoft = Color(0xFFD2D2FF),
    coral = Color(0xFFF9786A), coralSoft = Color(0xFF4E201C),
    green = Color(0xFF55C483), greenSoft = Color(0xFF133621),
    goldSoft = Color(0xFF3E2D10), goldInk = Color(0xFFF5CB70),
    inverse = Color(0xFFF3F1F8), onInverse = Color(0xFF17151F),
)

val LocalDayOrganizerFarben = staticCompositionLocalOf { HellFarben }
```

- [ ] **Step 3: Formen**

Erzeuge `Formen.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Eckenradien aus dem Redesign-Prototyp, px 1:1 als dp uebernommen. */
object DayOrganizerFormen {
    val heldenKarte = RoundedCornerShape(28.dp)
    val karte = RoundedCornerShape(24.dp)
    val zeile = RoundedCornerShape(20.dp)
    val kachel = RoundedCornerShape(14.dp)
    val knopf = RoundedCornerShape(16.dp)
    val pille = RoundedCornerShape(50)
}
```

- [ ] **Step 4: Typografie**

Erzeuge `Typografie.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import de.vanhake.dayorganizer.R

/**
 * Eine einzelne Variable-Font-Datei (Achse "wght"), fuenfmal mit unterschiedlichen
 * FontVariation.Settings eingebunden -- Compose waehlt darueber das jeweilige Gewicht,
 * ganz ohne fuenf separate Dateien.
 */
val Manrope = FontFamily(
    Font(R.font.manrope, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.manrope, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.manrope, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.manrope, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.manrope, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

/**
 * Das Redesign setzt durchgehend deutlich schwerere Schnitte ein als die
 * Material-3-Standardtypografie (z. B. Abschnittstitel mit font-weight:800
 * statt der M3-Vorgabe 500) -- deshalb eine eigene Gewichtszuordnung je Rolle,
 * nicht nur ein Fontfamilientausch.
 */
fun manropeTypografie(): Typography {
    val basis = Typography()
    fun androidx.compose.ui.text.TextStyle.mitManrope(gewicht: FontWeight) =
        copy(fontFamily = Manrope, fontWeight = gewicht)

    return basis.copy(
        displayLarge = basis.displayLarge.mitManrope(FontWeight.ExtraBold),
        displayMedium = basis.displayMedium.mitManrope(FontWeight.ExtraBold),
        displaySmall = basis.displaySmall.mitManrope(FontWeight.ExtraBold),
        headlineLarge = basis.headlineLarge.mitManrope(FontWeight.ExtraBold),
        headlineMedium = basis.headlineMedium.mitManrope(FontWeight.Bold),
        headlineSmall = basis.headlineSmall.mitManrope(FontWeight.Bold),
        titleLarge = basis.titleLarge.mitManrope(FontWeight.Bold),
        titleMedium = basis.titleMedium.mitManrope(FontWeight.Bold),
        titleSmall = basis.titleSmall.mitManrope(FontWeight.ExtraBold),
        bodyLarge = basis.bodyLarge.mitManrope(FontWeight.SemiBold),
        bodyMedium = basis.bodyMedium.mitManrope(FontWeight.SemiBold),
        bodySmall = basis.bodySmall.mitManrope(FontWeight.Medium),
        labelLarge = basis.labelLarge.mitManrope(FontWeight.Bold),
        labelMedium = basis.labelMedium.mitManrope(FontWeight.Bold),
        labelSmall = basis.labelSmall.mitManrope(FontWeight.Bold),
    )
}
```

- [ ] **Step 5: `Thema.kt` neu verdrahten**

Ersetze den gesamten Inhalt von `Thema.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import de.vanhake.dayorganizer.data.ThemeModus

@Composable
fun DayOrganizerThema(themeModus: ThemeModus, inhalt: @Composable () -> Unit) {
    val farben = if (themeModus == ThemeModus.DUNKEL) DunkelFarben else HellFarben
    val materialFarben = if (themeModus == ThemeModus.DUNKEL) {
        darkColorScheme(
            primary = farben.accent, onPrimary = farben.onAccent,
            background = farben.bg, onBackground = farben.ink,
            surface = farben.surface, onSurface = farben.ink,
            surfaceVariant = farben.surface2, error = farben.coral,
        )
    } else {
        lightColorScheme(
            primary = farben.accent, onPrimary = farben.onAccent,
            background = farben.bg, onBackground = farben.ink,
            surface = farben.surface, onSurface = farben.ink,
            surfaceVariant = farben.surface2, error = farben.coral,
        )
    }

    CompositionLocalProvider(LocalDayOrganizerFarben provides farben) {
        MaterialTheme(colorScheme = materialFarben, typography = manropeTypografie(), content = inhalt)
    }
}

/** Zugriff auf die Redesign-Farbrollen, analog zu MaterialTheme.colorScheme. */
object DayOrganizerTheme {
    val farben: DayOrganizerFarben
        @Composable get() = LocalDayOrganizerFarben.current
}
```

- [ ] **Step 6: `ThemaViewModel` für den Compose-Root**

Erzeuge `ThemaViewModel.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.data.ThemeModus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ThemaViewModel @Inject constructor(
    speicher: EinstellungenSpeicher,
) : ViewModel() {
    val themeModus: StateFlow<ThemeModus> = speicher.beobachten()
        .map { it.themeModus }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeModus.HELL)
}
```

- [ ] **Step 7: `MainActivity` verdrahten**

```kotlin
package de.vanhake.dayorganizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.ui.DayOrganizerNavigation
import de.vanhake.dayorganizer.ui.theme.DayOrganizerThema
import de.vanhake.dayorganizer.ui.theme.ThemaViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themaViewModel: ThemaViewModel = hiltViewModel()
            val themeModus by themaViewModel.themeModus.collectAsStateWithLifecycle()
            DayOrganizerThema(themeModus) {
                Surface { DayOrganizerNavigation() }
            }
        }
    }
}
```

- [ ] **Step 8: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Schlägt es mit "unresolved reference: font" fehl, prüfe, dass die Datei exakt unter `app/src/main/res/font/manrope.ttf` liegt (Ressourcenname `R.font.manrope` wird aus dem Dateinamen ohne Endung abgeleitet).

- [ ] **Step 9: App auf einem Emulator/Gerät ansehen**

Run: `./gradlew :app:installDebug`
Erwartet: App startet, Heute-Bildschirm zeigt jetzt Manrope-Schrift und den violetten Akzent auf den bestehenden, noch unveränderten M3-Komponenten (Cards, Buttons) -- das vollständige visuelle Redesign des Heute-Bildschirms folgt erst in Task 11.

- [ ] **Step 10: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Farben.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Formen.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Typografie.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/ThemaViewModel.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Thema.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/MainActivity.kt \
        app/src/main/res/font/manrope.ttf \
        docs/licenses/manrope-OFL.txt
git commit -m "feat(theme): Design-System-Fundament (Farben, Formen, Manrope-Typografie)"
```

---

### Task 6: Provisorischer Hell/Dunkel-Umschalter

Ohne diesen Task wäre der neue, persistierte Farbmodus aus Task 4/5 von der Oberfläche aus nicht erreichbar -- ein Rückschritt gegenüber heute, wo Dunkelmodus dem Systemschalter folgt. Der endgültige, gestaltete Umschalter (segmentierter Button wie im Prototyp) kommt erst mit dem Optionen-Redesign in einem späteren Plan; hier nur ein einfacher, funktionierender Zwischenstand.

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModel.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModelTest.kt`

**Interfaces:**
- Consumes: `Einstellungen.themeModus`, `EinstellungenSpeicher.schreiben` (Task 4).
- Produces: `EinstellungenZustand.themeModus`, `EinstellungenViewModel.themeModusAendern(ThemeModus)`.

- [ ] **Step 1: Bestehenden Test-Aufbau ansehen**

Run: `find app/src/test -iname "*EinstellungenViewModel*"`
Expected: Zeigt, ob bereits eine Testdatei existiert, deren Fixtures/Fakes wiederverwendet werden (z. B. Fake-`Backup`, Fake-`speicher`/`lesen`/`schreiben`-Lambdas wie im Haupt-Konstruktor von `EinstellungenViewModel`). Die folgenden Schritte gehen von den Konstruktorparametern aus, wie sie oben in `EinstellungenViewModel.kt` gelesen wurden (`lesen`, `schreiben`, ...).

- [ ] **Step 2: Failing Test**

Neuer Test (Fixtures an die tatsächlich vorgefundene Testdatei anpassen, falls eine existiert):

```kotlin
    @Test
    fun `themeModusAendern schreibt sofort, ohne auf Speichern zu warten`() = runTest {
        var geschrieben: Einstellungen? = null
        val viewModel = EinstellungenViewModel(
            lesen = { Einstellungen() },
            schreiben = { geschrieben = it },
            hinweiseLesen = { emptyList() },
            backupDienst = FakeBackup(),
            nachImport = {},
            ioDispatcher = dispatcher,
            heute = { LocalDate.of(2026, 3, 2) },
        )

        viewModel.themeModusAendern(ThemeModus.DUNKEL)
        advanceUntilIdle()

        assertEquals(ThemeModus.DUNKEL, geschrieben?.themeModus)
        assertEquals(ThemeModus.DUNKEL, viewModel.zustand.value.themeModus)
    }
```

(`FakeBackup`, `dispatcher`, benötigte Imports an den Stil der vorgefundenen Testdatei angleichen; falls keine existiert, eine neue Datei nach dem Muster von `HeuteViewModelTest.kt` mit `StandardTestDispatcher` anlegen und ein minimales `FakeBackup : Backup` mit leeren Methoden ergänzen.)

- [ ] **Step 3: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.ui.einstellungen.EinstellungenViewModelTest"`
Expected: FAIL -- `themeModusAendern` und `EinstellungenZustand.themeModus` existieren nicht.

- [ ] **Step 4: Implementieren**

In `EinstellungenViewModel.kt`, `EinstellungenZustand` erweitern:

```kotlin
data class EinstellungenZustand(
    val tagesbonusText: String = "",
    val standardKulanzText: String = "",
    val themeModus: ThemeModus = ThemeModus.HELL,
    val hinweise: List<Hinweis> = emptyList(),
)
```

Import ergänzen: `import de.vanhake.dayorganizer.data.ThemeModus`.

`neuLaden()` erweitern:

```kotlin
    private suspend fun neuLaden() {
        val aktuell = lesen()
        _zustand.value = EinstellungenZustand(
            tagesbonusText = aktuell.tagesbonusPunkte.toString(),
            standardKulanzText = aktuell.standardKulanzMinuten.toString(),
            themeModus = aktuell.themeModus,
            hinweise = hinweiseLesen(),
        )
    }
```

Neue Funktion ergänzen (schreibt sofort, unabhängig vom "Speichern"-Knopf für die Zahlenfelder -- der Modus ist ein Umschalter, kein Formularfeld):

```kotlin
    /** Wirkt sofort, anders als tagesbonusAendern/kulanzAendern: ein Farbmodus ist ein
     * Umschalter, kein Formularfeld, das erst ueber "Speichern" uebernommen wird. */
    fun themeModusAendern(neu: ThemeModus) = viewModelScope.launch {
        val bisher = lesen()
        versuchen {
            schreiben(bisher.copy(themeModus = neu))
        }.onFailure { ausnahme ->
            Log.w(TAG, "Speichern des Farbmodus fehlgeschlagen", ausnahme)
        }
        neuLaden()
    }
```

- [ ] **Step 5: Test ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.ui.einstellungen.EinstellungenViewModelTest"`
Expected: PASS

- [ ] **Step 6: Zwei Strings ergänzen**

In `strings.xml`, nach `<string name="feld_standard_kulanz">...` einfügen:

```xml
    <string name="theme_hell">Hell</string>
    <string name="theme_dunkel">Dunkel</string>
```

- [ ] **Step 7: Screen ergänzen**

In `EinstellungenScreen.kt`: Imports ergänzen (`androidx.compose.material3.FilterChip`, `androidx.compose.foundation.layout.Row`, `de.vanhake.dayorganizer.data.ThemeModus`). Direkt vor dem `OutlinedTextField` für `zustand.tagesbonusText` einfügen:

```kotlin
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = zustand.themeModus == ThemeModus.HELL,
                    onClick = { viewModel.themeModusAendern(ThemeModus.HELL) },
                    label = { Text(stringResource(R.string.theme_hell)) },
                )
                FilterChip(
                    selected = zustand.themeModus == ThemeModus.DUNKEL,
                    onClick = { viewModel.themeModusAendern(ThemeModus.DUNKEL) },
                    label = { Text(stringResource(R.string.theme_dunkel)) },
                )
            }
```

- [ ] **Step 8: Kompilieren und manuell prüfen**

Run: `./gradlew :app:installDebug`
Erwartet: In Optionen lässt sich zwischen Hell/Dunkel umschalten, die Änderung wirkt sofort auf die ganze App (Task 5) und übersteht einen Neustart der App.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModel.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenScreen.kt \
        app/src/main/res/values/strings.xml \
        app/src/test/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModelTest.kt
git commit -m "feat(optionen): provisorischen Hell/Dunkel-Umschalter ergaenzen"
```

---

### Task 7: Aktivitätensymbol-Katalog

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/AktivitaetSymbol.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/theme/AktivitaetSymbolTest.kt`

**Interfaces:**
- Produces: `AKTIVITAET_SYMBOLE: List<String>`, `aktivitaetSymbol(schluessel: String): ImageVector`.

- [ ] **Step 1: Failing Test**

```kotlin
package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.WaterDrop
import kotlin.test.Test
import kotlin.test.assertEquals

class AktivitaetSymbolTest {

    @Test
    fun `ein bekannter Schluessel liefert sein Symbol`() {
        assertEquals(Icons.Filled.WaterDrop, aktivitaetSymbol("water_drop"))
    }

    @Test
    fun `ein unbekannter Schluessel faellt auf das Standardsymbol zurueck`() {
        assertEquals(Icons.Filled.SelfImprovement, aktivitaetSymbol("nicht_vorhanden"))
    }

    @Test
    fun `jeder Katalogeintrag liefert ein Symbol`() {
        AKTIVITAET_SYMBOLE.forEach { schluessel ->
            assertEquals(aktivitaetSymbol(schluessel), aktivitaetSymbol(schluessel))
        }
        assertEquals(12, AKTIVITAET_SYMBOLE.size)
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.ui.theme.AktivitaetSymbolTest"`
Expected: FAIL -- Datei/Funktionen existieren nicht.

- [ ] **Step 3: Implementieren**

```kotlin
package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Schluessel exakt wie im Redesign-Prototyp (DayOrganizer App.dc.html, Konstante ICONS).
 * "pill" und "medication" haben im Material-Icons-Extended-Katalog kein eigenes
 * Tabletten-Symbol -- Medication/LocalPharmacy sind die naechstliegenden, optisch
 * unterscheidbaren Ersatzsymbole.
 */
val AKTIVITAET_SYMBOLE: List<String> = listOf(
    "pill", "medication", "water_drop", "fitness_center", "menu_book", "directions_walk",
    "self_improvement", "bedtime", "restaurant", "spa", "music_note", "cleaning_services",
)

const val AKTIVITAET_SYMBOL_STANDARD = "self_improvement"

/**
 * Ein unbekannter Schluessel (aelteres Backup, kuenftige Version) faellt auf das
 * Standardsymbol zurueck statt abzustuerzen -- anders als bei den Belohnungsbedingungen
 * ist das Symbol rein kosmetisch, kein Vertragsbruch.
 */
fun aktivitaetSymbol(schluessel: String): ImageVector = when (schluessel) {
    "pill" -> Icons.Filled.Medication
    "medication" -> Icons.Filled.LocalPharmacy
    "water_drop" -> Icons.Filled.WaterDrop
    "fitness_center" -> Icons.Filled.FitnessCenter
    "menu_book" -> Icons.Filled.MenuBook
    "directions_walk" -> Icons.Filled.DirectionsWalk
    "bedtime" -> Icons.Filled.Bedtime
    "restaurant" -> Icons.Filled.Restaurant
    "spa" -> Icons.Filled.Spa
    "music_note" -> Icons.Filled.MusicNote
    "cleaning_services" -> Icons.Filled.CleaningServices
    else -> Icons.Filled.SelfImprovement
}
```

- [ ] **Step 4: Test ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.ui.theme.AktivitaetSymbolTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/AktivitaetSymbol.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/ui/theme/AktivitaetSymbolTest.kt
git commit -m "feat(theme): Aktivitaetensymbol-Katalog ergaenzen"
```

---

### Task 8: Rückgängig-fähige Protokolle in Erledigen/Auslassen

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitErledigen.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitAuslassen.kt`
- Modify: `app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitErledigenTest.kt`

**Interfaces:**
- Produces: `sealed interface ErledigungsProtokoll { data class Gebucht(val faelligkeitVorher: Faelligkeit, val buchungen: List<Buchung>); data object NichtOffen; data object Unbekannt }`, `FaelligkeitErledigen.mitProtokoll(Long): ErledigungsProtokoll`, `FaelligkeitAuslassen.mitProtokoll(Long): Faelligkeit?`.
- Constraint: Das bestehende `invoke(Long): ErledigungsErgebnis` und `FaelligkeitAuslassen.invoke(Long): Unit` behalten ihr heutiges Verhalten exakt bei -- `AktionsEmpfaenger` und `WidgetErledigenAktion` rufen beide nur `invoke(...)` auf und werten den Rückgabewert nicht aus, bleiben also unverändert.

- [ ] **Step 1: Failing Test -- `mitProtokoll` liefert die Buchungen**

In `FaelligkeitErledigenTest.kt` ergänzen:

```kotlin
    @Test
    fun `mitProtokoll liefert die vorherige Faelligkeit und alle gebuchten Zeilen`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b)

        val protokoll = use.mitProtokoll(10) as ErledigungsProtokoll.Gebucht

        assertEquals(morgens, protokoll.faelligkeitVorher)
        assertEquals(15, protokoll.buchungen.sumOf { it.betrag })
        assertEquals(setOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), protokoll.buchungen.map { it.art }.toSet())
    }

    @Test
    fun `mitProtokoll meldet NichtOffen ohne zu buchen`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b)
        use(10)

        val protokoll = use.mitProtokoll(10)

        assertEquals(ErledigungsProtokoll.NichtOffen, protokoll)
    }

    @Test
    fun `invoke verhaelt sich nach der Protokoll-Erweiterung unveraendert`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b)

        assertEquals(ErledigungsErgebnis.GEBUCHT, use(10))
        assertEquals(ErledigungsErgebnis.NICHT_OFFEN, use(10))
    }
```

Und für `FaelligkeitAuslassen` (gleiche Datei, direkt nach den bestehenden Auslassen-Tests):

```kotlin
    @Test
    fun `Auslassen mitProtokoll liefert die vorherige Faelligkeit`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val use = FaelligkeitAuslassen(f, uhrUm(9, 0))

        val vorher = use.mitProtokoll(10)

        assertEquals(morgens, vorher)
        assertEquals(FaelligkeitStatus.AUSGELASSEN, f.stand.value.single().status)
    }

    @Test
    fun `Auslassen mitProtokoll liefert null wenn nicht offen`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val use = FaelligkeitAuslassen(f, uhrUm(9, 0))
        use(10)

        assertEquals(null, use.mitProtokoll(10))
    }
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.domainuse.FaelligkeitErledigenTest"`
Expected: FAIL -- `mitProtokoll` existiert auf keiner der beiden Klassen, `ErledigungsProtokoll` existiert nicht.

- [ ] **Step 3: `FaelligkeitErledigen` erweitern**

`ErledigungsErgebnis` bleibt unverändert. Direkt darunter ergänzen:

```kotlin
/**
 * Reichhaltigeres Ergebnis fuer Aufrufer, die eine Erledigung spaeter rueckgaengig
 * machen koennen muessen (siehe FaelligkeitErledigenRueckgaengig) -- ErledigungsErgebnis
 * bleibt fuer die bestehenden Aufrufer (Benachrichtigung, Widget) unveraendert, die den
 * Rueckgabewert ohnehin verwerfen.
 */
sealed interface ErledigungsProtokoll {
    data class Gebucht(val faelligkeitVorher: Faelligkeit, val buchungen: List<Buchung>) : ErledigungsProtokoll
    data object NichtOffen : ErledigungsProtokoll
    data object Unbekannt : ErledigungsProtokoll
}
```

Import ergänzen: `import de.vanhake.dayorganizer.domain.model.Buchung`.

`invoke` und `erledige` umbauen:

```kotlin
    suspend operator fun invoke(faelligkeitId: Long): ErledigungsErgebnis =
        when (val protokoll = mitProtokoll(faelligkeitId)) {
            is ErledigungsProtokoll.Gebucht -> ErledigungsErgebnis.GEBUCHT
            ErledigungsProtokoll.NichtOffen -> ErledigungsErgebnis.NICHT_OFFEN
            ErledigungsProtokoll.Unbekannt -> ErledigungsErgebnis.UNBEKANNT
        }

    suspend fun mitProtokoll(faelligkeitId: Long): ErledigungsProtokoll =
        transaktionen.inTransaktion { erledige(faelligkeitId) }

    private suspend fun erledige(faelligkeitId: Long): ErledigungsProtokoll {
        val vorher = faelligkeiten.nachId(faelligkeitId) ?: return ErledigungsProtokoll.Unbekannt
        if (vorher.status != FaelligkeitStatus.OFFEN) return ErledigungsProtokoll.NichtOffen
        val aktivitaet = aktivitaeten.nachId(vorher.aktivitaetId)
            ?: return ErledigungsProtokoll.Unbekannt

        val jetzt = LocalDateTime.now(uhr)
        val heute = LocalDate.now(uhr)
        val istNeu = vorher.istAnzahl + 1
        val erfuellt = istNeu >= vorher.sollAnzahl

        val nachher = vorher.copy(
            istAnzahl = istNeu,
            status = if (erfuellt) FaelligkeitStatus.ERLEDIGT else FaelligkeitStatus.OFFEN,
            erledigtAm = jetzt,
        )

        val historie = faelligkeiten.historie(aktivitaet.id)
            .map { if (it.id == nachher.id) nachher else it }
        val serie = serienRechner.aktuelleSerieMitStart(historie, nachher.periodeVon)
        val gebuchteSchwellen = serie.startPeriode
            ?.let { buchungen.serienbonusSchwellenSeit(aktivitaet.id, it.atStartOfDay()) }
            ?: emptySet()

        val tagesFaelligkeiten = faelligkeiten.fuerZeitraum(heute, heute)
            .map { if (it.id == nachher.id) nachher else it }
            .filter { it.periodeVon == it.periodeBis }

        val einstellungen = einstellungenLesen()
        val kontext = ErledigungsKontext(
            aktivitaet = aktivitaet,
            faelligkeitVorher = vorher,
            zeitpunkt = jetzt,
            serieNachher = serie.laenge,
            gebuchteSchwellen = gebuchteSchwellen,
            tagesFaelligkeitenNachher = tagesFaelligkeiten,
            tagesbonusPunkte = einstellungen.tagesbonusPunkte,
            tagesbonusBereitsGebucht = buchungen.tagesbonusGebuchtAm(heute),
        )

        val gebuchteZeilen = punkteRechner.buchungen(kontext)

        faelligkeiten.aktualisieren(
            nachher.copy(warPuenktlich = punkteRechner.istPuenktlich(kontext)),
        )
        buchungen.buchen(gebuchteZeilen)
        return ErledigungsProtokoll.Gebucht(faelligkeitVorher = vorher, buchungen = gebuchteZeilen)
    }
```

(Der Methodenkörper ist inhaltlich identisch zum bisherigen `erledige(...)` -- einzige Änderungen: Rückgabetyp `ErledigungsProtokoll` statt `ErledigungsErgebnis`, und `gebuchteZeilen` wird vor dem Buchen in eine Variable gelegt, damit sie im Rückgabewert landet.)

- [ ] **Step 4: `FaelligkeitAuslassen` erweitern**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/** Bewusstes Auslassen: keine Punkte, aber die Serie ueberlebt. */
class FaelligkeitAuslassen @Inject constructor(
    private val faelligkeiten: FaelligkeitRepository,
    private val uhr: Clock,
) {
    suspend operator fun invoke(faelligkeitId: Long) {
        mitProtokoll(faelligkeitId)
    }

    /** Liefert die Faelligkeit, wie sie vor dieser Aktion war -- Grundlage fuer
     * FaelligkeitAuslassenRueckgaengig. Null, wenn nichts geaendert wurde. */
    suspend fun mitProtokoll(faelligkeitId: Long): Faelligkeit? {
        val f = faelligkeiten.nachId(faelligkeitId) ?: return null
        if (f.status != FaelligkeitStatus.OFFEN) return null
        faelligkeiten.aktualisieren(
            f.copy(status = FaelligkeitStatus.AUSGELASSEN, erledigtAm = LocalDateTime.now(uhr)),
        )
        return f
    }
}
```

- [ ] **Step 5: Tests ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.domainuse.FaelligkeitErledigenTest"`
Expected: PASS, alle Tests der Klasse (bestehende und neue).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitErledigen.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitAuslassen.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitErledigenTest.kt
git commit -m "feat(faelligkeit): Buchungsprotokoll fuer Erledigen/Auslassen"
```

---

### Task 9: Rückgängig-Anwendungsfälle

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitErledigenRueckgaengig.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitAuslassenRueckgaengig.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitRueckgaengigTest.kt`

**Interfaces:**
- Consumes: `ErledigungsProtokoll.Gebucht` (Task 8).
- Produces: `FaelligkeitErledigenRueckgaengig(protokoll)`, `FaelligkeitAuslassenRueckgaengig(faelligkeitVorher)`.

- [ ] **Step 1: Failing Test**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class FaelligkeitRueckgaengigTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private fun uhrUm(stunde: Int, minute: Int) = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, stunde, minute).atZone(zone).toInstant(), zone,
    )

    private val vorher = Faelligkeit(
        id = 10, aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 2), periodeBis = LocalDate.of(2026, 3, 2),
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0), sollAnzahl = 1,
    )
    private val nachher = vorher.copy(
        istAnzahl = 1, status = FaelligkeitStatus.ERLEDIGT,
        erledigtAm = LocalDateTime.of(2026, 3, 2, 8, 10), warPuenktlich = true,
    )
    private val gebuchteZeilen = listOf(
        Buchung(
            id = 100, art = BuchungsArt.ERLEDIGUNG, betrag = 10,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 10), aktivitaetId = 1, faelligkeitId = 10,
        ),
        Buchung(
            id = 101, art = BuchungsArt.PUENKTLICHKEITSBONUS, betrag = 5,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 10), aktivitaetId = 1, faelligkeitId = 10,
        ),
    )

    @Test
    fun `Erledigen-Rueckgaengig stellt die Faelligkeit wieder her und bucht Gegenbuchungen`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(nachher))
        val b = FakeBuchungRepository()
        val rueckgaengig = FaelligkeitErledigenRueckgaengig(f, b, FakeTransaktionen(), uhrUm(8, 12))

        rueckgaengig(ErledigungsProtokoll.Gebucht(faelligkeitVorher = vorher, buchungen = gebuchteZeilen))

        assertEquals(vorher, f.stand.value.single())
        // FakeBuchungRepository startet leer -- hier stehen ausschliesslich die zwei neuen
        // Korrekturbuchungen, die Original-Buchungen aus gebuchteZeilen waren nie in b enthalten
        // (sie kamen im echten Ablauf aus einem frueheren FaelligkeitErledigen-Aufruf).
        assertEquals(2, b.stand.value.size)
        assertEquals(0, b.stand.value.sumOf { it.betrag })
        assertEquals(2, b.stand.value.count { it.art == BuchungsArt.KORREKTUR })
        assertEquals(setOf(-10, -5), b.stand.value.map { it.betrag }.toSet())
    }

    @Test
    fun `Auslassen-Rueckgaengig stellt die Faelligkeit wieder her`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(vorher.copy(status = FaelligkeitStatus.AUSGELASSEN)))
        val rueckgaengig = FaelligkeitAuslassenRueckgaengig(f)

        rueckgaengig(vorher)

        assertEquals(vorher, f.stand.value.single())
    }
}
```

- [ ] **Step 2: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.domainuse.FaelligkeitRueckgaengigTest"`
Expected: FAIL -- beide Klassen existieren nicht.

- [ ] **Step 3: `FaelligkeitErledigenRueckgaengig` implementieren**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.data.repository.Transaktionen
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Macht genau eine erfolgreiche FaelligkeitErledigen-Buchung rueckgaengig, anhand des
 * ErledigungsProtokoll.Gebucht, das dieser eine Aufruf zurueckgegeben hat. Stellt die
 * Faelligkeit auf ihren exakten Vorzustand zurueck und bucht fuer jede damals gebuchte
 * Zeile eine betragsgleiche Gegenbuchung (BuchungsArt.KORREKTUR) -- nie eine Loeschung,
 * damit die Punkte-Historie erklaerbar bleibt (README: "Korrekturen faelschen keine
 * Historie").
 */
class FaelligkeitErledigenRueckgaengig @Inject constructor(
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val transaktionen: Transaktionen,
    private val uhr: Clock,
) {
    suspend operator fun invoke(protokoll: ErledigungsProtokoll.Gebucht) = transaktionen.inTransaktion {
        faelligkeiten.aktualisieren(protokoll.faelligkeitVorher)
        val jetzt = LocalDateTime.now(uhr)
        buchungen.buchen(
            protokoll.buchungen.map { original ->
                original.copy(
                    id = 0,
                    art = BuchungsArt.KORREKTUR,
                    betrag = -original.betrag,
                    zeitpunkt = jetzt,
                    notiz = "Rueckgaengig: ${original.art}",
                )
            },
        )
    }
}
```

- [ ] **Step 4: `FaelligkeitAuslassenRueckgaengig` implementieren**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import javax.inject.Inject

/** Macht ein FaelligkeitAuslassen rueckgaengig: keine Buchungen zu widerrufen, nur der Status. */
class FaelligkeitAuslassenRueckgaengig @Inject constructor(
    private val faelligkeiten: FaelligkeitRepository,
) {
    suspend operator fun invoke(faelligkeitVorher: Faelligkeit) {
        faelligkeiten.aktualisieren(faelligkeitVorher)
    }
}
```

- [ ] **Step 5: Test ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.domainuse.FaelligkeitRueckgaengigTest"`
Expected: PASS

- [ ] **Step 6: Gesamten Testlauf des Moduls prüfen**

Run: `./gradlew :domain:test :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle bestehenden und neuen Tests grün.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitErledigenRueckgaengig.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitAuslassenRueckgaengig.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitRueckgaengigTest.kt
git commit -m "feat(faelligkeit): echte Rueckgaengig-Anwendungsfaelle fuer Erledigen/Auslassen"
```

---

### Task 10: Hinweistoast und HeuteViewModel-Verdrahtung

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/Hinweistoast.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteZustand.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteZusammenstellung.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteViewModel.kt`
- Modify: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteViewModelTest.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `FaelligkeitErledigen.mitProtokoll`, `FaelligkeitErledigenRueckgaengig`, `FaelligkeitAuslassen.mitProtokoll`, `FaelligkeitAuslassenRueckgaengig` (Task 8/9), `aktivitaetSymbol` (Task 7).
- Produces: `HeuteEintrag.icon: String`, `HeuteViewModel.toast: StateFlow<Hinweistoast?>`, `HeuteViewModel.rueckgaengig()`.
- Design-Entscheidung: `toast` ist ein **eigener** `StateFlow` auf dem ViewModel, nicht Teil von `HeuteZustand`/der `combine(...)`-Kette, die `zustand` speist -- exakt das bestehende Muster von `EinstellungenViewModel._meldung`/`meldung` neben `_zustand`/`zustand` in derselben Codebasis. Zwei Gründe: `combine(...)` in kotlinx.coroutines hat nur typisierte Überladungen bis fünf Flows (die bestehende `zustand`-Kette nutzt bereits alle fünf), und ein sechster, unabhängig getakteter Flow in derselben `combine` würde den DB-getriebenen Zustandswechsel und das Setzen des Toasts als zwei separate Emissionen auseinanderreißen, statt sie synchron im selben `zustand`-Update zu liefern.

- [ ] **Step 1: `Hinweistoast`-Datenklasse und Composable**

```kotlin
package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFesteFarben
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme

data class Hinweistoast(val text: String, val kannRueckgaengig: Boolean)

@Composable
fun HinweistoastLeiste(toast: Hinweistoast, rueckgaengig: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DayOrganizerTheme.farben.inverse,
        contentColor = DayOrganizerTheme.farben.onInverse,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(toast.text, modifier = Modifier.weight(1f))
            if (toast.kannRueckgaengig) {
                TextButton(onClick = rueckgaengig) {
                    Text(
                        stringResource(R.string.toast_rueckgaengig),
                        color = DayOrganizerFesteFarben.toastRueckgaengigInk,
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Strings ergänzen**

In `strings.xml` ergänzen:

```xml
    <string name="toast_rueckgaengig">Rückgängig</string>
```

- [ ] **Step 3: Failing Test -- Toast nach Erledigen**

In `HeuteViewModelTest.kt` ergänzen (Konstruktor-Helper `viewModel(...)` um die zwei neuen Rückgängig-Use-Cases erweitern -- siehe Step 5 unten für die endgültige Signatur):

```kotlin
    @Test
    fun `Erledigen zeigt einen Toast mit Rueckgaengig-Option`() = runTest(dispatcher) {
        val vm = viewModel(FakeFaelligkeitRepository(listOf(morgens)), FakeBuchungRepository())

        vm.erledigen(10)
        advanceUntilIdle()

        assertEquals(true, vm.toast.value?.kannRueckgaengig)
    }

    @Test
    fun `Rueckgaengig macht die Erledigung und ihre Buchungen wieder rueckgaengig`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        val buchungen = FakeBuchungRepository()
        val vm = viewModel(faelligkeiten, buchungen)

        vm.erledigen(10)
        advanceUntilIdle()
        vm.rueckgaengig()
        advanceUntilIdle()

        assertEquals(null, vm.toast.value)
        assertEquals(0, buchungen.stand.value.sumOf { it.betrag })
        assertEquals(FaelligkeitStatus.OFFEN, faelligkeiten.stand.value.single().status)
    }
```

Import ergänzen: `import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus`.

- [ ] **Step 4: Test ausführen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.ui.heute.HeuteViewModelTest"`
Expected: FAIL -- `HeuteViewModel.toast`/`rueckgaengig()` existieren nicht, der `viewModel(...)`-Helper hat noch nicht die passenden Parameter.

- [ ] **Step 5: `HeuteEintrag` um das Symbol erweitern**

In `HeuteZustand.kt`, nur `HeuteEintrag` ändern -- `HeuteZustand` selbst bleibt unverändert (der Toast ist ein eigener Flow, siehe Interfaces oben):

```kotlin
data class HeuteEintrag(
    val faelligkeitId: Long,
    val aktivitaetName: String,
    val farbe: Int,
    val icon: String,
    val zeitText: String?,
    val sollAnzahl: Int,
    val istAnzahl: Int,
    val status: FaelligkeitStatus,
    val ueberfaellig: Boolean,
)
```

- [ ] **Step 6: `HeuteZusammenstellung` erweitern**

In `HeuteZusammenstellung.aus(...)`, im `HeuteEintrag(...)`-Aufbau nach `farbe = aktivitaet.farbe,` ergänzen:

```kotlin
                icon = aktivitaet.icon,
```

- [ ] **Step 7: `HeuteViewModel` umbauen**

Konstruktor erweitern (neue Parameter für die beiden Rückgängig-Use-Cases; Reihenfolge der bestehenden Parameter unverändert lassen):

```kotlin
@HiltViewModel
class HeuteViewModel(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val belohnungen: BelohnungRepository,
    private val erledigenUseCase: FaelligkeitErledigen,
    private val auslassenUseCase: FaelligkeitAuslassen,
    private val erledigenRueckgaengig: FaelligkeitErledigenRueckgaengig,
    private val auslassenRueckgaengig: FaelligkeitAuslassenRueckgaengig,
    private val anzeige: Erinnerungsanzeige,
    private val alarmNeuPlanen: suspend () -> Unit,
    private val hinweiseLesen: suspend () -> List<Hinweis>,
    private val uhr: Clock,
    private val widgetAnstossen: suspend () -> Unit,
) : ViewModel() {

    @Inject
    constructor(
        aktivitaeten: AktivitaetRepository,
        faelligkeiten: FaelligkeitRepository,
        buchungen: BuchungRepository,
        belohnungen: BelohnungRepository,
        erledigenUseCase: FaelligkeitErledigen,
        auslassenUseCase: FaelligkeitAuslassen,
        erledigenRueckgaengig: FaelligkeitErledigenRueckgaengig,
        auslassenRueckgaengig: FaelligkeitAuslassenRueckgaengig,
        anzeige: Erinnerungsanzeige,
        alarmPlaner: AlarmPlaner,
        pruefer: BerechtigungsPruefer,
        einstellungen: EinstellungenSpeicher,
        uhr: Clock,
        widgetAktualisierung: WidgetAktualisierung,
    ) : this(
        aktivitaeten, faelligkeiten, buchungen, belohnungen, erledigenUseCase, auslassenUseCase,
        erledigenRueckgaengig, auslassenRueckgaengig, anzeige,
        { alarmPlaner.neuPlanen() },
        {
            val haengt = waechterHaengt(
                letzterErfolg = einstellungen.letzterWaechterErfolg(),
                installiertAm = pruefer.installiertAm(),
                jetzt = LocalDateTime.now(uhr),
            )
            BerechtigungsHinweise.fuer(pruefer.stand().copy(waechterHaengt = haengt))
        },
        uhr,
        { widgetAktualisierung.anstossen() },
    )

    private val hinweise = MutableStateFlow<List<Hinweis>>(emptyList())
    private val tag = MutableStateFlow(LocalDate.now(uhr))

    // Eigener Flow statt Teil der zustand-Kette unten -- exakt das Muster von
    // EinstellungenViewModel._meldung/meldung. combine(...) hat nur typisierte
    // Ueberladungen bis fuenf Flows (die zustand-Kette nutzt bereits alle fuenf), und ein
    // gemeinsamer sechster Flow wuerde den DB-getriebenen Update und das Setzen des
    // Toasts ausserdem in zwei getrennte Emissionen auseinanderreissen.
    private val _toast = MutableStateFlow<Hinweistoast?>(null)
    val toast: StateFlow<Hinweistoast?> = _toast.asStateFlow()

    private var ausstehendesRueckgaengig: (suspend () -> Unit)? = null
    private var toastJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val zustand: StateFlow<HeuteZustand> = combine(
        tag.flatMapLatest { faelligkeiten.beobachteTag(it) },
        aktivitaeten.beobachteAktive(),
        belohnungen.beobachteAktive(),
        buchungen.beobachteKontostand(),
        hinweise,
    ) { faelligkeiten, aktivitaeten, belohnungen, kontostand, hinweise ->
        HeuteZusammenstellung.aus(faelligkeiten, aktivitaeten, belohnungen, kontostand, LocalDateTime.now(uhr))
            .copy(hinweise = hinweise)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeuteZustand())

    fun beimSichtbarwerden() {
        tag.value = LocalDate.now(uhr)
        viewModelScope.launch { hinweise.value = hinweiseLesen() }
    }

    fun erledigen(faelligkeitId: Long) = viewModelScope.launch {
        when (val protokoll = erledigenUseCase.mitProtokoll(faelligkeitId)) {
            is ErledigungsProtokoll.Gebucht -> {
                anzeige.verstecke(faelligkeitId)
                alarmNeuPlanen()
                widgetAnstossen()
                val gesamt = protokoll.buchungen.sumOf { it.betrag }
                val text = if (protokoll.buchungen.any { it.art == BuchungsArt.TAGESBONUS }) {
                    HeuteToastTexte.tagesbonusToast(gesamt)
                } else {
                    val name = aktivitaeten.nachId(protokoll.faelligkeitVorher.aktivitaetId)?.name.orEmpty()
                    HeuteToastTexte.erledigtToast(gesamt, name)
                }
                zeigeToast(text, kannRueckgaengig = true) { erledigenRueckgaengig(protokoll) }
            }
            ErledigungsProtokoll.NichtOffen, ErledigungsProtokoll.Unbekannt -> Unit
        }
    }

    fun auslassen(faelligkeitId: Long) = viewModelScope.launch {
        val vorher = auslassenUseCase.mitProtokoll(faelligkeitId) ?: return@launch
        anzeige.verstecke(faelligkeitId)
        alarmNeuPlanen()
        widgetAnstossen()
        val name = aktivitaeten.nachId(vorher.aktivitaetId)?.name.orEmpty()
        zeigeToast(HeuteToastTexte.ausgelassenToast(name), kannRueckgaengig = true) {
            auslassenRueckgaengig(vorher)
        }
    }

    fun rueckgaengig() = viewModelScope.launch {
        val aktion = ausstehendesRueckgaengig ?: return@launch
        toastJob?.cancel()
        ausstehendesRueckgaengig = null
        _toast.value = null
        aktion()
        alarmNeuPlanen()
        widgetAnstossen()
    }

    private fun zeigeToast(text: String, kannRueckgaengig: Boolean, rueckgaengigAktion: suspend () -> Unit) {
        toastJob?.cancel()
        ausstehendesRueckgaengig = rueckgaengigAktion
        _toast.value = Hinweistoast(text, kannRueckgaengig)
        toastJob = viewModelScope.launch {
            delay(3_800)
            _toast.value = null
            ausstehendesRueckgaengig = null
        }
    }
}

/** Reine Textbausteine fuer die Heute-Toasts -- ausserhalb der Klasse, damit Tests sie
 * nicht extra mocken muessen (keine Android-Ressourcen, kein Context noetig). */
private object HeuteToastTexte {
    fun erledigtToast(punkte: Int, name: String) = "+$punkte Punkte · $name"
    fun tagesbonusToast(punkte: Int) = "+$punkte Punkte · inkl. Tagesbonus"
    fun ausgelassenToast(name: String) = "$name ausgelassen"
}
```

`HeuteToastTexte` ist bewusst eine eigenständige Top-Level-`object`-Deklaration außerhalb der Klasse (nicht Teil von `HeuteViewModel`) und baut die Texte als reine Strings, nicht über `stringResource(...)`: `HeuteViewModel` hat als reines ViewModel keinen `Context`, und ein testbarer Text ohne Android-Ressourcen ist hier wichtiger als Übersetzbarkeit (die App hat ohnehin nur eine Sprache, siehe README "Bewusst nicht enthalten: mehrsprachige Oberfläche").

Imports in `HeuteViewModel.kt` ergänzen: `import de.vanhake.dayorganizer.domainuse.ErledigungsProtokoll`, `import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigenRueckgaengig`, `import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassenRueckgaengig`, `import de.vanhake.dayorganizer.domain.model.BuchungsArt`, `import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast`, `import kotlinx.coroutines.Job`, `import kotlinx.coroutines.delay`.

- [ ] **Step 8: Test-Helper in `HeuteViewModelTest.kt` anpassen**

Den `viewModel(...)`-Helper um die zwei neuen Konstruktorparameter erweitern:

```kotlin
    private fun viewModel(
        faelligkeiten: FakeFaelligkeitRepository,
        buchungen: FakeBuchungRepository,
        uhr: Clock = this.uhr,
        anzeige: Erinnerungsanzeige = FakeAnzeige(),
        belohnungen: FakeBelohnungRepository = FakeBelohnungRepository(),
        widgetAnstossen: suspend () -> Unit = {},
    ): HeuteViewModel {
        val aktivitaeten = FakeAktivitaetRepository(listOf(tabletten))
        val transaktionen = FakeTransaktionen()
        val erledigenUseCase = FaelligkeitErledigen(
            aktivitaeten, faelligkeiten, buchungen, PunkteRechner(), SerienRechner(),
            transaktionen, { Einstellungen(tagesbonusPunkte = 0) }, uhr,
        )
        return HeuteViewModel(
            aktivitaeten = aktivitaeten,
            faelligkeiten = faelligkeiten,
            buchungen = buchungen,
            belohnungen = belohnungen,
            erledigenUseCase = erledigenUseCase,
            auslassenUseCase = FaelligkeitAuslassen(faelligkeiten, uhr),
            erledigenRueckgaengig = FaelligkeitErledigenRueckgaengig(faelligkeiten, buchungen, transaktionen, uhr),
            auslassenRueckgaengig = FaelligkeitAuslassenRueckgaengig(faelligkeiten),
            anzeige = anzeige,
            alarmNeuPlanen = {},
            hinweiseLesen = { emptyList() },
            uhr = uhr,
            widgetAnstossen = widgetAnstossen,
        )
    }
```

Import ergänzen: `import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigenRueckgaengig`, `import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassenRueckgaengig`, `import de.vanhake.dayorganizer.FakeTransaktionen`.

Da `erledigen()`/`auslassen()` jetzt über `delay(3_800)` einen Hintergrund-Job starten: `toast` ist ein eigener `StateFlow` (siehe Interfaces oben), deshalb genügt für die beiden Tests in Step 3 ein einfaches `advanceUntilIdle()` nach dem Aufruf und ein direkter Blick auf `vm.toast.value` -- kein `turbine`-Abonnement auf `zustand` nötig, um den Toast zu prüfen.

- [ ] **Step 9 (optional, empfohlen): Test für automatisches Verschwinden**

```kotlin
    @Test
    fun `der Toast verschwindet von selbst, wenn niemand Rueckgaengig antippt`() = runTest(dispatcher) {
        val vm = viewModel(FakeFaelligkeitRepository(listOf(morgens)), FakeBuchungRepository())

        vm.erledigen(10)
        advanceUntilIdle()
        assertEquals(true, vm.toast.value != null)

        dispatcher.scheduler.advanceTimeBy(3_900)
        dispatcher.scheduler.runCurrent()

        assertEquals(null, vm.toast.value)
    }
```

- [ ] **Step 10: Tests ausführen, Erfolg bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.ui.heute.HeuteViewModelTest"`
Expected: PASS, alle Tests der Klasse.

- [ ] **Step 11: Gesamten Testlauf prüfen**

Run: `./gradlew :domain:test :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/Hinweistoast.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteZustand.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteZusammenstellung.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteViewModel.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteViewModelTest.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(heute): Toast mit echtem Rueckgaengig fuer Erledigen/Auslassen"
```

---

### Task 11: Heute-Bildschirm im neuen Design

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteScreen.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/WarnBanner.kt`

**Interfaces:**
- Consumes: `DayOrganizerTheme.farben`, `DayOrganizerFormen`, `aktivitaetSymbol` (Task 5/7), `HeuteZustand.toast`, `HeuteViewModel.rueckgaengig` (Task 10).

Dieser Task ist überwiegend visuell und hat keine sinnvolle rot/grün-TDD-Form für Layout-Details -- die zugrunde liegenden Daten (`HeuteZustand`, Gruppierung in ueberfaellig/anstehend/spaeter/abgeschlossen) sind durch `HeuteZusammenstellungTest.kt` bereits abgesichert. Absicherung hier: das Modul muss weiter kompilieren und `HeuteViewModelTest` grün bleiben; manuelle Prüfung auf dem Gerät gegen den Prototyp.

- [ ] **Step 1: Warnbanner an die neuen Farbrollen anpassen**

In `WarnBanner.kt`, `CardDefaults.cardColors(...)` ersetzen durch die Gold-Rollen des Prototyps (der Banner im `.dc.html` nutzt `goldSoft`/`goldInk`, nicht die Fehlerfarbe):

```kotlin
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = DayOrganizerFormen.zeile,
        colors = CardDefaults.cardColors(
            containerColor = DayOrganizerTheme.farben.goldSoft,
            contentColor = DayOrganizerTheme.farben.ink,
        ),
    ) {
```

Imports ergänzen: `import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen`, `import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme`.

- [ ] **Step 2: `HeuteScreen.kt` vollständig ersetzen**

```kotlin
package de.vanhake.dayorganizer.ui.heute

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.platform.permission.einstellungsIntent
import de.vanhake.dayorganizer.ui.gemeinsam.HinweistoastLeiste
import de.vanhake.dayorganizer.ui.gemeinsam.WarnBanner
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import de.vanhake.dayorganizer.ui.theme.aktivitaetSymbol

@Composable
fun HeuteScreen(viewModel: HeuteViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val kontext = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.beimSichtbarwerden()
        onPauseOrDispose { }
    }

    val heldenEintrag = zustand.ueberfaellig.firstOrNull()
        ?: zustand.anstehend.firstOrNull()
        ?: zustand.spaeter.firstOrNull()

    Box(Modifier.fillMaxSize().background(DayOrganizerTheme.farben.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(zustand.hinweise, key = { it.name }) { hinweis ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    WarnBanner(
                        hinweis = hinweis,
                        beheben = {
                            ContextCompat.startActivity(kontext, hinweis.einstellungsIntent(kontext), null)
                        },
                    )
                }
            }

            item { Kopf(zustand) }

            if (heldenEintrag != null) {
                item {
                    HeldenKarte(
                        eintrag = heldenEintrag,
                        erledigen = { viewModel.erledigen(heldenEintrag.faelligkeitId) },
                        auslassen = { viewModel.auslassen(heldenEintrag.faelligkeitId) },
                    )
                }
            } else if (zustand.gesamtHeute > 0) {
                item { AllesErledigtKarte() }
            }

            item { FortschrittsKarte(zustand) }

            abschnitt(
                R.string.abschnitt_ueberfaellig,
                zustand.ueberfaellig.filter { it.faelligkeitId != heldenEintrag?.faelligkeitId },
                viewModel,
            )
            abschnitt(
                R.string.abschnitt_anstehend,
                zustand.anstehend.filter { it.faelligkeitId != heldenEintrag?.faelligkeitId },
                viewModel,
            )
            abschnitt(
                R.string.abschnitt_spaeter,
                zustand.spaeter.filter { it.faelligkeitId != heldenEintrag?.faelligkeitId },
                viewModel,
            )
            abschnitt(R.string.abschnitt_abgeschlossen, zustand.abgeschlossen, viewModel)
        }

        toast?.let { aktuellerToast ->
            HinweistoastLeiste(
                toast = aktuellerToast,
                rueckgaengig = viewModel::rueckgaengig,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }
}

private fun LazyListScope.abschnitt(
    titelId: Int,
    eintraege: List<HeuteEintrag>,
    viewModel: HeuteViewModel,
) {
    if (eintraege.isEmpty()) return
    item {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(titelId),
                style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                color = DayOrganizerTheme.farben.ink2,
            )
            Text(
                eintraege.size.toString(),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = DayOrganizerTheme.farben.ink2,
            )
        }
    }
    items(eintraege, key = { it.faelligkeitId }) { eintrag ->
        Box(Modifier.padding(horizontal = 16.dp)) {
            EintragZeile(
                eintrag = eintrag,
                erledigen = { viewModel.erledigen(eintrag.faelligkeitId) },
                auslassen = { viewModel.auslassen(eintrag.faelligkeitId) },
            )
        }
    }
}

@Composable
private fun Kopf(zustand: HeuteZustand) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            stringResource(R.string.tagesfortschritt, zustand.erledigtHeute, zustand.gesamtHeute),
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = DayOrganizerTheme.farben.goldInk)
            Text(
                stringResource(R.string.punktestand, zustand.kontostand),
                color = DayOrganizerTheme.farben.goldInk,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

@Composable
private fun HeldenKarte(eintrag: HeuteEintrag, erledigen: () -> Unit, auslassen: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = DayOrganizerFormen.heldenKarte,
        color = if (eintrag.ueberfaellig) {
            de.vanhake.dayorganizer.ui.theme.DayOrganizerFesteFarben.heroUeberfaelligSolid
        } else {
            DayOrganizerTheme.farben.hero
        },
        contentColor = androidx.compose.ui.graphics.Color.White,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = DayOrganizerFormen.kachel,
                    color = de.vanhake.dayorganizer.ui.theme.DayOrganizerFesteFarben.heroChipflaeche,
                ) {
                    Icon(
                        aktivitaetSymbol(eintrag.icon),
                        contentDescription = null,
                        modifier = Modifier.padding(18.dp).size(24.dp),
                        tint = androidx.compose.ui.graphics.Color.White,
                    )
                }
                Column {
                    Text(eintrag.aktivitaetName, fontWeight = FontWeight.ExtraBold)
                    Text(eintrag.zeitText ?: stringResource(R.string.ziel_fortschritt, eintrag.istAnzahl, eintrag.sollAnzahl))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = erledigen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text(stringResource(R.string.aktion_erledigt))
                }
                OutlinedIconButton(onClick = auslassen) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.aktion_ausgelassen))
                }
            }
        }
    }
}

@Composable
private fun AllesErledigtKarte() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = DayOrganizerFormen.karte,
        color = DayOrganizerTheme.farben.greenSoft,
        contentColor = DayOrganizerTheme.farben.ink,
    ) {
        Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Alles erledigt", fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun FortschrittsKarte(zustand: HeuteZustand) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = DayOrganizerFormen.karte,
        color = DayOrganizerTheme.farben.surface,
        contentColor = DayOrganizerTheme.farben.ink,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = {
                        if (zustand.gesamtHeute == 0) 0f
                        else (zustand.erledigtHeute.toFloat() / zustand.gesamtHeute).coerceIn(0f, 1f)
                    },
                    modifier = Modifier.fillMaxSize(),
                    color = DayOrganizerTheme.farben.accent,
                    trackColor = DayOrganizerTheme.farben.surface2,
                    strokeWidth = 7.dp,
                )
                Text(
                    "${zustand.erledigtHeute}/${zustand.gesamtHeute}",
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(
                    stringResource(R.string.tagesfortschritt, zustand.erledigtHeute, zustand.gesamtHeute),
                    fontWeight = FontWeight.Bold,
                )
                zustand.naechsteBelohnung?.let { ziel ->
                    Text(
                        if (ziel.fehlendePunkte > 0) {
                            stringResource(R.string.noch_bis_belohnung, ziel.fehlendePunkte, ziel.name)
                        } else {
                            stringResource(R.string.belohnung_bereit, ziel.name)
                        },
                        color = DayOrganizerTheme.farben.ink2,
                    )
                }
            }
        }
    }
}

@Composable
private fun EintragZeile(eintrag: HeuteEintrag, erledigen: () -> Unit, auslassen: () -> Unit) {
    val farben = DayOrganizerTheme.farben
    val erledigt = eintrag.status == FaelligkeitStatus.ERLEDIGT
    val ausgelassen = eintrag.status == FaelligkeitStatus.AUSGELASSEN
    val kachelFarbe = when {
        eintrag.ueberfaellig -> farben.coralSoft
        erledigt -> farben.greenSoft
        ausgelassen -> farben.surface2
        else -> farben.accentSoft
    }
    val kachelInk = when {
        eintrag.ueberfaellig -> farben.coral
        erledigt -> farben.green
        ausgelassen -> farben.ink2
        else -> farben.onAccentSoft
    }

    Surface(shape = DayOrganizerFormen.zeile, color = farben.surface, contentColor = farben.ink) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(shape = DayOrganizerFormen.kachel, color = kachelFarbe, contentColor = kachelInk) {
                Icon(
                    aktivitaetSymbol(eintrag.icon),
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp).size(24.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    eintrag.aktivitaetName,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val untertitel = eintrag.zeitText
                    ?: stringResource(R.string.ziel_fortschritt, eintrag.istAnzahl, eintrag.sollAnzahl)
                Text(untertitel, color = if (eintrag.ueberfaellig) farben.coral else farben.ink2)
            }
            if (eintrag.status == FaelligkeitStatus.OFFEN) {
                IconButton(onClick = auslassen) {
                    Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.aktion_ausgelassen), tint = farben.ink2)
                }
                OutlinedIconButton(onClick = erledigen) {
                    Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.aktion_erledigt), tint = farben.accent)
                }
            }
        }
    }
}
```

Achte beim Einfügen darauf, nur tatsächlich verwendete Imports zu übernehmen (z. B. wird `BoxWithConstraints`/`CircleShape` aus der obigen Importliste nirgends gebraucht und kann entfallen) -- `./gradlew`-Warnungen zu ungenutzten Imports sind harmlos, aber unnötig.

- [ ] **Step 3: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Häufigste Fehlerquelle: `CircularProgressIndicator` ohne `trackColor`-Overload -- prüfe die tatsächlich aufgelöste Compose-Material3-BOM-Version (`compose-bom = "2024.12.01"`); falls `trackColor` dort nicht existiert, ersatzweise ohne `trackColor`-Parameter aufrufen und stattdessen einen zweiten, halbtransparenten `CircularProgressIndicator(progress = { 1f }, color = surface2)` als Hintergrundring darunterlegen.

- [ ] **Step 4: Bestehende Tests laufen lassen**

Run: `./gradlew :domain:test :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL -- reine UI-Änderungen, keine der bestehenden ViewModel-/Domänen-Tests darf betroffen sein.

- [ ] **Step 5: Manuell gegen den Prototyp prüfen**

Run: `./gradlew :app:installDebug`
Öffne die App und vergleiche mit `DayOrganizer App.dc.html` (im Browser öffnen oder über die Design-Vorschau): Heldenkarte mit nächster fälliger Aktivität, Fortschrittsring, Abschnitte "Überfällig"/"Jetzt dran"/"Später heute"/"Erledigt" mit Symbol-Kacheln, Toast mit "Rückgängig" nach Erledigen/Auslassen, Rückgängig stellt Punktestand und Status wieder her. Prüfe außerdem: Hell/Dunkel-Umschalter (Task 6) färbt die Heldenkarte und alle Symbol-Kacheln korrekt um.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteScreen.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/WarnBanner.kt
git commit -m "feat(heute): Bildschirm im neuen Design (Heldenkarte, Ring, Symbol-Kacheln, Toast)"
```

---

## Abschluss dieses Plans

Nach Task 11 ist die Heute-Ansicht optisch und funktional auf dem Stand des Redesign-Prototyps (bis auf `heroStil`/`showWidget`, siehe Global Constraints). Aktivitäten, Belohnungen, Statistik, die vollständige Optionen-Seite (segmentierter Hell/Dunkel-Schalter, Backup-Karten im neuen Stil) und das Homescreen-Widget bleiben absichtlich im alten Erscheinungsbild -- sie sind Gegenstand der Folgepläne:

- Plan 5: Aktivitäten-Liste + Aktivität-bearbeiten-Bildschirm (Symbol-Auswahlraster, Wochentag-/Uhrzeit-Chips, Zeitplan-Typ-Karten).
- Plan 6: Belohnungen + Statistik + Optionen (segmentierter Hell/Dunkel-Schalter ersetzt den provisorischen aus Task 6, Backup-Karten, Bonus-/Kulanz-Stepper).
- Plan 7: Homescreen-Widget im neuen Design (Glance-Einschränkungen beachten -- kein CSS-Farbverlauf, kein `backdrop-filter`).

Nach Abschluss dieses Plans: `README.md`-Abschnitt "Stand" und die Dokumentationstabelle um "Plan 4: Design-System-Fundament + Heute -- umgesetzt" ergänzen (nicht Teil dieses Plans, da rein redaktionell).
