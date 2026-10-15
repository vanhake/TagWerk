# DayOrganizer Redesign 3: Belohnungen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Den Belohnungen-Tab und den Belohnungs-Editor auf den Stand des Redesign-Prototyps bringen. Dazu kommen zwei Erweiterungen, die der Prototyp voraussetzt: ein Symbol je Belohnung und ein echtes Rückgängig fürs Einlösen. Außerdem bekommt der Editor das gegen Doppeltipps und Rotation abgesicherte Speichern aus Plan 5.

**Architecture:**
- **Symbol:** Das Belohnungssymbol ist wie beim Aktivitätensymbol (Plan 4) ein reiner String-Schlüssel im Domänenmodell. Es wird über einen zustandslosen Katalog Schlüssel → `ImageVector` gerendert.
- **Rückgängig:** Das Rückgängig fürs Einlösen folgt dem Muster von Plan 4. Die Einlösung bleibt stehen und bekommt nur ein Stornodatum (`Einloesung.storniertAm`). Der Kontostand wird durch eine betragsgleiche Gegenbuchung (`BuchungsArt.KORREKTUR`, mit `belohnungId`) wiederhergestellt; es wird nie gelöscht. So bleibt die Punkte-Historie erklärbar.
- **Datenhaltung:** Room-Migration 4 → 5 und Backup-Schema 3. Backups mit Schema 2 bleiben lesbar, weil die neuen Felder Standardwerte haben.
- **Oberfläche:** Der Belohnungs-Editor übernimmt die Bausteine aus Plan 5. Damit sie nicht kopiert werden, wandern die bisher privaten Formular-Bausteine des Aktivitäten-Editors nach `ui/gemeinsam`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3 aus BOM `2024.12.01`, also material3 1.3.x: `DatePicker`/`DatePickerDialog` sind `@ExperimentalMaterial3Api`), Room 2.6.1, Hilt, kotlinx.serialization, kotlinx.coroutines-test, Robolectric. Keine neuen Abhängigkeiten.

**Design-Quelle:** `docs/design/prototyp/DayOrganizer App.dc.html`. Relevant sind:
- der Markup-Block `<sc-if value="{{ sBel }}">` (Liste)
- die Konstante `rewards` im State
- die Methoden `hindernis()` und `redeem()`
- `renderVals()` → `rewardRows`

Für den Belohnungs-Editor hat der Prototyp **kein** Design. Er wird analog zum Aktivitäten-Editor aus Plan 5 gebaut.

## Global Constraints

- Minimale API-Version bleibt 33, Ziel-API 35. Keine neue Abhängigkeit, keine neue Berechtigung, kein Netzwerkzugriff.
- Die deutsche Namenskonvention gilt weiter für Klassen, Funktionen, Ressourcen-Keys und Testnamen. In Kotlin-Kommentaren werden Umlaute ausgeschrieben (`ae`, `oe`, `ue`). Strings in `strings.xml` und Kotlin-String-Literale behalten echte Umlaute.
- Jede Änderung an Entities/`DayOrganizerDatenbank` braucht eine `Migration` in `Migrationen.kt`, ihre Registrierung in `di/DatenModul.kt`, einen Test in `MigrationTest.kt` und das exportierte Room-Schema (`app/schemas/.../5.json`).
- Jede Änderung an `BackupDatei` und den DTOs erhöht `BackupUmwandlung.SCHEMA_VERSION` und bekommt eine neue eingefrorene Beispieldatei unter `app/src/test/resources/backup/`.
- Punkte-Korrekturen entstehen ausschließlich als neue `Buchung`-Zeilen mit `art = BuchungsArt.KORREKTUR`, nie als Löschung oder Überschreibung (README: „Korrekturen fälschen keine Historie“). Einlösungen werden nie gelöscht, nur storniert.
- Farben ausschließlich über `DayOrganizerTheme.farben` bzw. `DayOrganizerFesteFarben`, Eckenradien über `DayOrganizerFormen`. Einzige Ausnahme ist `Color.White` als Inhaltsfarbe auf der Heldenkarte, wie in `HeuteScreen` (Prototyp `color:#fff`).
- Nutzersichtbare Compose-Texte stehen in `app/src/main/res/values/strings.xml`. Ausnahme wie in Plan 4/5: von ViewModels erzeugte Toast-Texte sind Kotlin-Literale.
- Archivieren bekommt **keinen** Rückgängig-Knopf, wie bei den Aktivitäten in Plan 5. Nur das Einlösen ist rückgängig machbar.
- Bearbeiten-Bildschirme speichern nach dem Muster aus Plan 5:
  - Sperre gegen überlappendes Speichern.
  - Die neue id wird nach dem Insert übernommen.
  - `laden()` ist idempotent.
  - Fehler werden ab dem ersten Speicherversuch bei jeder Änderung neu geprüft.
  - `CancellationException` wird nie verschluckt.
  - Navigation nur im Lifecycle-Zustand `RESUMED`.
- Es gibt keine Compose-UI-Tests. Logik gehört in reine Funktionen und ViewModels mit JVM-Tests. Ob ein Screen baut, belegt `:app:assembleDebug`.
- Nach jedem Task muss `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` grün sein, bevor committet wird.
- Commit-Nachrichten enden mit `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Sichtprüfungen im Emulator: Nur selbst angelegte Testdaten bedienen, nie vorhandene Aktivitäten oder Belohnungen archivieren, einlösen oder ändern. Vor jedem Tipp per `uiautomator dump` prüfen, welcher Knoten getroffen wird.

---

## Datei-Übersicht

| Datei | Zweck |
|---|---|
| `domain/.../model/Belohnung.kt` | `Belohnung.symbol`, `Einloesung.storniertAm` |
| `app/.../data/db/BelohnungEntity.kt`, `EinloesungEntity.kt` | neue Spalten |
| `app/.../data/db/Migrationen.kt`, `DayOrganizerDatenbank.kt`, `di/DatenModul.kt` | `MIGRATION_4_5`, Version 5 |
| `app/.../data/db/BelohnungDao.kt` | Insert liefert id, `einloesungStornieren` |
| `app/.../data/mapper/EntityMapper.kt` | neue Felder durchreichen |
| `app/.../data/repository/BelohnungRepository.kt` | `einloesungAnlegen(): Long`, `einloesungStornieren` |
| `app/.../backup/BackupModell.kt`, `BackupUmwandlung.kt` | Schema 3, Schema 2 bleibt lesbar |
| `app/.../domainuse/BelohnungEinloesen.kt` | Erfolg trägt die echte id |
| `app/.../domainuse/BelohnungEinloesenRueckgaengig.kt` (neu) | Storno + Gegenbuchung |
| `app/.../ui/theme/BelohnungSymbol.kt` (neu) | Katalog |
| `app/.../ui/theme/Farben.kt` | `DayOrganizerFesteFarben.heroStern` |
| `app/.../ui/gemeinsam/SpeicherErgebnis.kt` (neu, verschoben) | von `ui/aktivitaeten` hierher |
| `app/.../ui/gemeinsam/Formularbausteine.kt` (neu) | `Kopfzeile`, `Abschnitt`, `FehlerText`, `NamensFeld`, `SymbolRaster`, `AuswahlKarten` aus dem Aktivitäten-Editor |
| `app/.../ui/gemeinsam/DatumDialog.kt` (neu) | Datumswähler |
| `app/.../ui/belohnungen/*` | ViewModels, Entwurf, Zustand, beide Screens |
| `app/.../ui/aktivitaeten/AktivitaetBearbeitenScreen.kt` | nutzt die verschobenen Bausteine |
| `app/.../ui/Navigation.kt` | Belohnungs-Editor: Ergebnis + RESUMED-Wächter |
| `app/src/main/res/values/strings.xml` | neue/umbenannte Strings |
| Tests | siehe je Task |

`app/...` steht für `app/src/main/kotlin/de/vanhake/dayorganizer`, `domain/...` für `domain/src/main/kotlin/de/vanhake/dayorganizer/domain`, Tests unter `app/src/test/kotlin/de/vanhake/dayorganizer`.

---

### Task 1: Datenschicht — Belohnungssymbol und Stornierung (Room 4 → 5)

**Files:**
- Modify: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/model/Belohnung.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/BelohnungEntity.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/EinloesungEntity.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/BelohnungDao.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/Migrationen.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/DayOrganizerDatenbank.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/di/DatenModul.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/mapper/EntityMapper.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/repository/BelohnungRepository.kt`
- Modify: `app/src/test/kotlin/de/vanhake/dayorganizer/Fakes.kt` (`FakeBelohnungRepository`)
- Create: `app/schemas/de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank/5.json` (von KSP erzeugt)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/data/db/MigrationTest.kt`, `data/db/DaoTest.kt`, `data/mapper/BelohnungMapperTest.kt`, `FakeBelohnungRepositoryTest.kt`

**Interfaces:**
- Produces:
  - `Belohnung.symbol: String` (letzter Konstruktorparameter, Default `"redeem"`)
  - `Einloesung.storniertAm: LocalDateTime?` (letzter Parameter, Default `null`)
  - `BelohnungDao.einloesungEinfuegen(...): Long`
  - `BelohnungDao.einloesungStornieren(id: Long, am: LocalDateTime): Int`
  - `BelohnungRepository.einloesungAnlegen(einloesung: Einloesung): Long`
  - `BelohnungRepository.einloesungStornieren(id: Long, am: LocalDateTime): Boolean`
  - `MIGRATION_4_5`

Die neuen Parameter stehen jeweils **am Ende**, weil bestehende Aufrufe Positionsargumente nutzen (z. B. `Belohnung(1, "Kinoabend", preisPunkte = 200)` in den Tests).

- [ ] **Step 1: Failing Migrationstest**

In `MigrationTest.kt` vor dem `companion object` ergänzen und `MIGRATION_4_5` importieren:

```kotlin
    @Test
    fun `migration_4_auf_5 ergaenzt Belohnungssymbol und Stornodatum`() {
        helfer.createDatabase(DB_NAME, 4).apply {
            execSQL(
                "INSERT INTO belohnung (id, name, beschreibung, preisPunkte, bedingungTyp, " +
                    "bedingungAktivitaetId, bedingungPerioden, bedingungDatum, archiviert) VALUES " +
                    "(1, 'Kinoabend', '', 200, NULL, NULL, NULL, NULL, 0)",
            )
            execSQL(
                "INSERT INTO einloesung (id, belohnungId, name, preisPunkte, zeitpunkt) VALUES " +
                    "(1, 1, 'Kinoabend', 200, '2026-03-02T19:00')",
            )
            close()
        }

        val db = helfer.runMigrationsAndValidate(
            DB_NAME, 5, true, MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
        )

        db.query("SELECT symbol FROM belohnung WHERE id = 1").use {
            it.moveToFirst()
            assertEquals("redeem", it.getString(0))
        }
        db.query("SELECT storniertAm FROM einloesung WHERE id = 1").use {
            it.moveToFirst()
            assertEquals(true, it.isNull(0))
        }
        db.close()
    }
```

- [ ] **Step 2: Failing DAO-, Mapper- und Fake-Tests**

In `DaoTest.kt` ergänzen. Die Hilfsfunktionen `belohnung(...)` und `einloesung(...)` gibt es dort bereits und bleiben unverändert; das neue Feld wird per `copy` gesetzt:

```kotlin
    @Test
    fun belohnungssymbol_uebersteht_den_Rundlauf() = runTest {
        val id = db.belohnungDao().einfuegen(belohnung(name = "Kino").copy(symbol = "movie"))

        assertEquals("movie", db.belohnungDao().nachId(id)?.symbol)
    }

    @Test
    fun einloesungEinfuegen_liefert_die_neue_id() = runTest {
        val belohnungId = db.belohnungDao().einfuegen(belohnung())

        val id = db.belohnungDao().einloesungEinfuegen(
            einloesung(belohnungId = belohnungId, zeitpunkt = LocalDateTime.of(2026, 3, 2, 19, 0)),
        )

        assertEquals(id, db.belohnungDao().alleEinloesungen().single().id)
    }

    @Test
    fun einloesungStornieren_wirkt_genau_einmal() = runTest {
        val belohnungId = db.belohnungDao().einfuegen(belohnung())
        val id = db.belohnungDao().einloesungEinfuegen(
            einloesung(belohnungId = belohnungId, zeitpunkt = LocalDateTime.of(2026, 3, 2, 19, 0)),
        )
        val am = LocalDateTime.of(2026, 3, 2, 19, 1)

        assertEquals(1, db.belohnungDao().einloesungStornieren(id, am))
        assertEquals(0, db.belohnungDao().einloesungStornieren(id, am.plusMinutes(1)))
        assertEquals(0, db.belohnungDao().einloesungStornieren(id + 99, am))
        assertEquals(am, db.belohnungDao().alleEinloesungen().single().storniertAm)
    }
```

In `BelohnungMapperTest.kt` ergänzen und die passenden Imports setzen (`Belohnung`, `Einloesung`, `LocalDateTime`, `zuEntity`, `zuDomaene`, soweit noch nicht vorhanden):

```kotlin
    @Test
    fun `Symbol und Stornodatum ueberstehen den Rundlauf durch die Entity`() {
        val belohnung = Belohnung(id = 4, name = "Kino", preisPunkte = 300, symbol = "movie")
        val einloesung = Einloesung(
            id = 2, belohnungId = 4, name = "Kino", preisPunkte = 300,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 19, 0),
            storniertAm = LocalDateTime.of(2026, 3, 2, 19, 1),
        )

        assertEquals(belohnung, belohnung.zuEntity().zuDomaene())
        assertEquals(einloesung, einloesung.zuEntity().zuDomaene())
    }
```

In `FakeBelohnungRepositoryTest.kt` ergänzen (gleicher Vertrag wie das DAO):

```kotlin
    @Test
    fun `Stornieren wirkt genau einmal und liefert die neue id beim Anlegen`() = runTest {
        val repository = FakeBelohnungRepository()
        val id = repository.einloesungAnlegen(
            Einloesung(belohnungId = 1, name = "Kino", preisPunkte = 300, zeitpunkt = LocalDateTime.of(2026, 3, 2, 19, 0)),
        )
        val am = LocalDateTime.of(2026, 3, 2, 19, 1)

        assertEquals(true, repository.einloesungStornieren(id, am))
        assertEquals(false, repository.einloesungStornieren(id, am.plusMinutes(1)))
        assertEquals(false, repository.einloesungStornieren(id + 99, am))
        assertEquals(am, repository.alleEinloesungen().single().storniertAm)
    }
```

(Nutzt die Datei bereits `runTest`? Falls nicht, Import `kotlinx.coroutines.test.runTest` ergänzen.)

- [ ] **Step 3: Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.data.*' --tests 'de.vanhake.dayorganizer.FakeBelohnungRepositoryTest'`
Expected: FAIL beim Kompilieren (`symbol`, `storniertAm`, `MIGRATION_4_5`, `einloesungStornieren` unbekannt).

- [ ] **Step 4: Domänenmodell**

In `domain/.../model/Belohnung.kt`:

```kotlin
data class Belohnung(
    val id: Long = 0,
    val name: String,
    val beschreibung: String = "",
    val preisPunkte: Int,
    val bedingung: Belohnungsbedingung? = null,
    val archiviert: Boolean = false,
    /** Schluessel aus dem BelohnungSymbol-Katalog (App-Modul), z. B. "movie". */
    val symbol: String = "redeem",
)

/**
 * Name und Preis sind absichtlich kopiert statt verlinkt: die Historie muss
 * lesbar bleiben, auch wenn die Belohnung spaeter umbenannt wird.
 *
 * [storniertAm] ist gesetzt, wenn die Einloesung rueckgaengig gemacht wurde. Die Zeile
 * bleibt stehen (Korrekturen faelschen keine Historie); der Kontostand wird ueber eine
 * KORREKTUR-Buchung wiederhergestellt, siehe BelohnungEinloesenRueckgaengig.
 */
data class Einloesung(
    val id: Long = 0,
    val belohnungId: Long,
    val name: String,
    val preisPunkte: Int,
    val zeitpunkt: LocalDateTime,
    val storniertAm: LocalDateTime? = null,
)
```

- [ ] **Step 5: Entities, DAO, Migration, Version**

`BelohnungEntity`: als letzten Parameter `val symbol: String = "redeem",` ergänzen. `EinloesungEntity`: als letzten Parameter `val storniertAm: LocalDateTime? = null,` ergänzen. Die `TypeConverters` für `LocalDateTime?` existieren bereits, siehe `FaelligkeitEntity.erledigtAm`.

`BelohnungDao`:

```kotlin
    @Insert suspend fun einloesungEinfuegen(eintrag: EinloesungEntity): Long

    /** 1, wenn die Einloesung gerade storniert wurde; 0, wenn es sie nicht gibt oder sie schon
     * storniert war. Die Bedingung "storniertAm IS NULL" macht das Stornieren idempotent --
     * ein zweiter Rueckgaengig-Tipp darf keine zweite Gegenbuchung ausloesen. */
    @Query("UPDATE einloesung SET storniertAm = :am WHERE id = :id AND storniertAm IS NULL")
    suspend fun einloesungStornieren(id: Long, am: LocalDateTime): Int
```

`Migrationen.kt` am Ende:

```kotlin
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE belohnung ADD COLUMN symbol TEXT NOT NULL DEFAULT 'redeem'")
        db.execSQL("ALTER TABLE einloesung ADD COLUMN storniertAm TEXT")
    }
}
```

`DayOrganizerDatenbank`: `version = 5`. In `DatenModul`: Import `MIGRATION_4_5` und `.addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)`.

- [ ] **Step 6: Mapper und Repository**

`EntityMapper.kt`:
- `Belohnung.zuEntity()` und `BelohnungEntity.zuDomaene()` reichen jeweils `symbol = symbol` durch.
- `Einloesung.zuEntity()` und `EinloesungEntity.zuDomaene()` reichen jeweils `storniertAm = storniertAm` durch.

`BelohnungRepository.kt`, Interface:

```kotlin
    /** Liefert die von der Datenbank vergebene id -- BelohnungEinloesen gibt sie im Erfolg
     * zurueck, damit ein Rueckgaengig genau diese Zeile stornieren kann. */
    suspend fun einloesungAnlegen(einloesung: Einloesung): Long
    /** true, wenn die Einloesung gerade storniert wurde; false, wenn unbekannt oder schon storniert. */
    suspend fun einloesungStornieren(id: Long, am: LocalDateTime): Boolean
```

`RaumBelohnungRepository`:

```kotlin
    override suspend fun einloesungAnlegen(einloesung: Einloesung): Long =
        dao.einloesungEinfuegen(einloesung.zuEntity())

    override suspend fun einloesungStornieren(id: Long, am: LocalDateTime): Boolean =
        dao.einloesungStornieren(id, am) > 0
```

Import `java.time.LocalDateTime` ergänzen.

`FakeBelohnungRepository` in `Fakes.kt`. `einloesungAnlegen` liefert jetzt die vergebene id, `einloesungStornieren` kommt neu dazu:

```kotlin
    override suspend fun einloesungAnlegen(einloesung: Einloesung): Long {
        // (bestehender Kommentar zur id-Vergabe bleibt unveraendert)
        val id = (einloesungen.value.maxOfOrNull { it.id } ?: 0L) + 1
        einloesungen.value = einloesungen.value + einloesung.copy(id = id)
        return id
    }

    /** Spiegelt "UPDATE einloesung SET storniertAm = :am WHERE id = :id AND storniertAm IS NULL". */
    override suspend fun einloesungStornieren(id: Long, am: LocalDateTime): Boolean {
        val treffer = einloesungen.value.any { it.id == id && it.storniertAm == null }
        if (treffer) {
            einloesungen.value = einloesungen.value.map { if (it.id == id) it.copy(storniertAm = am) else it }
        }
        return treffer
    }
```

`BelohnungEinloesen.loese()` ruft `einloesungAnlegen` schon auf und ignoriert den Rückgabewert vorerst. Das ändert erst Task 3.

- [ ] **Step 7: Tests grün, Schema exportieren**

Run: `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL. KSP erzeugt `app/schemas/de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank/5.json`.

- [ ] **Step 8: Commit**

```bash
git add domain/src/main/kotlin/de/vanhake/dayorganizer/domain/model/Belohnung.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/data \
        app/src/main/kotlin/de/vanhake/dayorganizer/di/DatenModul.kt \
        app/schemas/de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank/5.json \
        app/src/test/kotlin/de/vanhake/dayorganizer/Fakes.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/FakeBelohnungRepositoryTest.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/data
git commit -m "feat(db): Belohnungssymbol und Stornierung von Einloesungen (Migration 4-5)

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 2: Backup-Schema 3 (Schema 2 bleibt lesbar)

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/backup/BackupModell.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlung.kt`
- Modify: `app/src/test/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlungTest.kt`
- Create: `app/src/test/resources/backup/beispiel-schema-3.json` (aus dem Code erzeugt, siehe Step 6)

**Interfaces:**
- Consumes: `Belohnung.symbol`, `Einloesung.storniertAm` (Task 1).
- Produces: `BackupUmwandlung.SCHEMA_VERSION == 3`, `BackupUmwandlung.LESBARE_VERSIONEN == setOf(2, 3)`.

**Entscheidung:** Plan 4 hat Schema-1-Backups beim Sprung auf Schema 2 abgelehnt. Diesmal bleiben Schema-2-Dateien lesbar: Die zwei neuen Felder bekommen DTO-Standardwerte (`"redeem"`, `null`), die genau dem Zustand vor diesem Plan entsprechen. Damit Exporte trotzdem jedes Feld ausschreiben, auch wenn es seinem Standardwert entspricht, wird `encodeDefaults = true` gesetzt. Die bestehenden DTOs haben keine Standardwerte, ihre Ausgabe ändert sich dadurch nicht. Schema 1 bleibt abgelehnt.

- [ ] **Step 1: Tests anpassen und ergänzen (failing)**

In `BackupUmwandlungTest.kt`:

1. Fixture `belohnung` um `symbol = "movie"` ergänzen. Fixture `einloesung` bleibt ohne Storno.
2. Im Test `jedes Feld weicht vom Standardwert ab ...`:
   - `archivierteBelohnungMitFruehestensAb` bekommt `symbol = "flight"`.
   - `zweiteEinloesung` bekommt `storniertAm = LocalDateTime.of(2026, 4, 7, 20, 0)`.
   - Beide Fixtures sind in der Datei definiert. Dort die Felder ergänzen.
3. Den Test `eine Schemaversion kleiner als SCHEMA_VERSION wird abgelehnt und nicht nur eine groessere` ersetzen durch:

```kotlin
    @Test
    fun `eine Schemaversion unterhalb der lesbaren Versionen wird abgelehnt`() {
        val fehler = assertFailsWith<UnbekanntesSchema> {
            BackupUmwandlung.ausText(backupText(schemaVersion = "1"))
        }

        assertEquals(1, fehler.gefunden)
    }
```

4. Den Test `ein Schema-1-Backup wird nach dem Sprung auf Schema 2 abgelehnt, nicht stillschweigend falsch gelesen` umbenennen in `ein Schema-1-Backup wird weiterhin abgelehnt, nicht stillschweigend falsch gelesen`. Der Inhalt bleibt.
5. Den Schema-2-Block anpassen:
   - Der Lese-Test erwartet jetzt die Standardwerte der neuen Felder, denn die Schema-2-Datei kennt sie nicht.
   - Der Schreib-Test für Schema 2 entfällt, denn geschrieben wird ab jetzt Schema 3.
   - Die Hilfsfunktion `beispielInhaltSchema2()` entfällt ebenfalls.

```kotlin
    @Test
    fun `die eingefrorene Beispieldatei fuer Schema 2 bleibt lesbar und bekommt die neuen Standardwerte`() {
        val inhalt = BackupUmwandlung.ausText(beispielDateiSchema2)

        assertEquals(listOf(aktivitaet), inhalt.aktivitaeten)
        assertEquals(listOf(faelligkeit), inhalt.faelligkeiten)
        assertEquals(listOf(buchung), inhalt.buchungen)
        assertEquals(listOf(belohnung.copy(symbol = "redeem")), inhalt.belohnungen)
        assertEquals(listOf(einloesung.copy(storniertAm = null)), inhalt.einloesungen)
        assertEquals(
            Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45, themeModus = ThemeModus.DUNKEL),
            inhalt.einstellungen,
        )
    }
```

6. Neuer Schema-3-Block am Dateiende:

```kotlin
    // --- Schema 3: feste Beispieldatei als Vertrag ------------------------------------------
    //
    // Aendert sich diese Datei, werden bestehende Backups von Anwendern unlesbar. Das ist
    // dann keine Testanpassung, sondern eine neue Schemaversion.

    private val stornierteEinloesung = Einloesung(
        id = 3, belohnungId = 3, name = "Kinoabend", preisPunkte = 200,
        zeitpunkt = LocalDateTime.of(2026, 3, 2, 19, 30),
        storniertAm = LocalDateTime.of(2026, 3, 2, 19, 31),
    )

    private fun beispielInhaltSchema3() = BackupUmwandlung.zuDatei(
        erstelltAm = LocalDateTime.of(2026, 3, 2, 21, 0),
        aktivitaeten = listOf(aktivitaet),
        faelligkeiten = listOf(faelligkeit),
        buchungen = listOf(buchung),
        belohnungen = listOf(belohnung),
        einloesungen = listOf(einloesung, stornierteEinloesung),
        einstellungen = Einstellungen(
            tagesbonusPunkte = 25, standardKulanzMinuten = 45, themeModus = ThemeModus.DUNKEL,
        ),
    )

    private val beispielDateiSchema3 = javaClass.classLoader
        ?.getResourceAsStream("backup/beispiel-schema-3.json")!!
        .bufferedReader()
        .readText()

    @Test
    fun `die eingefrorene Beispieldatei fuer Schema 3 wird zu den erwarteten Domaenenobjekten gelesen`() {
        val inhalt = BackupUmwandlung.ausText(beispielDateiSchema3)

        assertEquals(listOf(aktivitaet), inhalt.aktivitaeten)
        assertEquals(listOf(faelligkeit), inhalt.faelligkeiten)
        assertEquals(listOf(buchung), inhalt.buchungen)
        assertEquals(listOf(belohnung), inhalt.belohnungen)
        assertEquals(listOf(einloesung, stornierteEinloesung), inhalt.einloesungen)
        assertEquals(
            Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45, themeModus = ThemeModus.DUNKEL),
            inhalt.einstellungen,
        )
    }

    @Test
    fun `dieselben Domaenenobjekte erzeugen wieder genau die eingefrorene Beispieldatei fuer Schema 3`() {
        val text = BackupUmwandlung.zuText(beispielInhaltSchema3())

        assertEquals(beispielDateiSchema3.trim(), text.trim())
    }

    @Test
    fun `Standardwerte der neuen Felder werden trotzdem ausgeschrieben`() {
        val text = BackupUmwandlung.zuText(
            BackupUmwandlung.zuDatei(
                erstelltAm = LocalDateTime.of(2026, 3, 2, 21, 0),
                aktivitaeten = emptyList(), faelligkeiten = emptyList(), buchungen = emptyList(),
                belohnungen = listOf(Belohnung(id = 1, name = "Eis", preisPunkte = 50)),
                einloesungen = listOf(
                    Einloesung(id = 1, belohnungId = 1, name = "Eis", preisPunkte = 50,
                        zeitpunkt = LocalDateTime.of(2026, 3, 1, 15, 0)),
                ),
                einstellungen = Einstellungen(tagesbonusPunkte = 15, standardKulanzMinuten = 60),
            ),
        )

        assertTrue("\"symbol\": \"redeem\"" in text)
        assertTrue("\"storniertAm\": null" in text)
    }
```

Imports nach Bedarf ergänzen (`kotlin.test.assertTrue`).

- [ ] **Step 2: Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.backup.BackupUmwandlungTest'`
Expected: FAIL. Die Rundlauf-Tests verlieren `symbol` bzw. `storniertAm`, und der Schema-2-Lesetest scheitert noch an der alten Version 2 (bzw. die Ressource `beispiel-schema-3.json` fehlt).

- [ ] **Step 3: DTOs**

In `BackupModell.kt`:

```kotlin
@Serializable
data class BelohnungDto(
    val id: Long,
    val name: String,
    val beschreibung: String,
    val preisPunkte: Int,
    val bedingungTyp: String?,
    val bedingungAktivitaetId: Long?,
    val bedingungPerioden: Int?,
    val bedingungDatum: String?,
    val archiviert: Boolean,
    /** Seit Schema 3. Der Standardwert macht Schema-2-Dateien lesbar. */
    val symbol: String = "redeem",
)

@Serializable
data class EinloesungDto(
    val id: Long,
    val belohnungId: Long,
    val name: String,
    val preisPunkte: Int,
    val zeitpunkt: String,
    /** Seit Schema 3. Der Standardwert macht Schema-2-Dateien lesbar. */
    val storniertAm: String? = null,
)
```

- [ ] **Step 4: Umwandlung**

In `BackupUmwandlung.kt`:

```kotlin
    const val SCHEMA_VERSION = 3

    /** Schema 2 bleibt lesbar: die mit Schema 3 neuen Felder haben DTO-Standardwerte, die
     * genau dem Zustand vor Schema 3 entsprechen. Schema 1 kannte weder Aktivitaetensymbol
     * noch Farbmodus ohne Standardwert und bleibt abgelehnt. */
    val LESBARE_VERSIONEN: Set<Int> = setOf(2, SCHEMA_VERSION)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        // Sonst fehlten Felder mit DTO-Standardwert in der Datei, sobald sie ihm entsprechen --
        // die Datei waere dann je nach Inhalt mal vollstaendig, mal nicht.
        encodeDefaults = true
    }
```

- In `ausText`: `if (gefundeneVersion != null && gefundeneVersion !in LESBARE_VERSIONEN)`.
- In `ausDatei`: `if (datei.schemaVersion !in LESBARE_VERSIONEN) throw UnbekanntesSchema(datei.schemaVersion)`.
- Den KDoc von `ausText` um einen Satz ergänzen: Ältere, lesbare Schemata werden über DTO-Standardwerte aufgefüllt.

Mapping:

```kotlin
    // in Belohnung.zuDto(): nach archiviert = archiviert,
        symbol = symbol,
    // in BelohnungDto.zuDomaene(): nach archiviert = archiviert,
        symbol = symbol,

    private fun Einloesung.zuDto() = EinloesungDto(
        id = id, belohnungId = belohnungId, name = name,
        preisPunkte = preisPunkte, zeitpunkt = zeitpunkt.toString(),
        storniertAm = storniertAm?.toString(),
    )

    private fun EinloesungDto.zuDomaene() = Einloesung(
        id = id, belohnungId = belohnungId, name = name,
        preisPunkte = preisPunkte, zeitpunkt = LocalDateTime.parse(zeitpunkt),
        storniertAm = storniertAm?.let(LocalDateTime::parse),
    )
```

- [ ] **Step 5: Zwischenlauf**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.backup.BackupUmwandlungTest'`
Expected: Alles grün außer den zwei Schema-3-Dateitests, denn die Ressource fehlt noch.

- [ ] **Step 6: Beispieldatei aus dem Code erzeugen**

Wie in Plan 4 wird die Datei nicht von Hand geschrieben:

Run: `echo '{}' > app/src/test/resources/backup/beispiel-schema-3.json`
Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.backup.BackupUmwandlungTest.dieselben Domaenenobjekte erzeugen wieder genau die eingefrorene Beispieldatei fuer Schema 3'`
Expected: FAIL mit „Expected <{}>, Actual <{…}>“. Den „Actual“-Text unverändert in `beispiel-schema-3.json` übernehmen.

Prüfe danach mit `grep -c '"schemaVersion": 3' app/src/test/resources/backup/beispiel-schema-3.json`, dass dort `1` herauskommt. Prüfe außerdem, dass die Datei `"symbol": "movie"` und genau ein `"storniertAm": "2026-03-02T19:31"` enthält.

- [ ] **Step 7: Gesamtlauf**

Run: `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL. `BackupDienstTest` bleibt grün; er schreibt und liest über dieselbe Umwandlung.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/backup \
        app/src/test/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlungTest.kt \
        app/src/test/resources/backup/beispiel-schema-3.json
git commit -m "feat(backup): Schema 3 mit Belohnungssymbol und Storno, Schema 2 bleibt lesbar

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 3: Einlösen rückgängig als Gegenbuchung, Vertragstest SQL ↔ Fake

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/BelohnungEinloesen.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/BelohnungEinloesenRueckgaengig.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/BelohnungEinloesenTest.kt` (ergänzen)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/BelohnungEinloesenRueckgaengigTest.kt` (neu)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/data/repository/BuchungRepositoryVertragTest.kt` (neu)

**Interfaces:**
- Consumes: `BelohnungRepository.einloesungAnlegen(): Long`, `einloesungStornieren(id, am): Boolean` (Task 1).
- Produces:
  - `EinloeseErgebnis.Erfolg.einloesung.id` ist die echte, von der Datenbank vergebene id.
  - `class BelohnungEinloesenRueckgaengig @Inject constructor(belohnungen: BelohnungRepository, buchungen: BuchungRepository, transaktionen: Transaktionen, uhr: Clock)` mit `suspend operator fun invoke(einloesung: Einloesung): Boolean`.

**Hintergrund:**
- Eine Gegenbuchung zum Einlösen trägt die `belohnungId`. Die Netto-Tagesbonus-Abfrage (`BuchungDao.nettoTagesbonusPunkte`, Kommentar dort) und `FakeBuchungRepository.tagesbonusGebuchtAm` schließen solche Zeilen über `belohnungId IS NULL` bereits aus.
- Der neue Vertragstest prüft genau das, und die Serienbonus-Dedupe gleich mit, gegen **beide** Implementierungen. Das erledigt die Folgearbeit aus dem Abschlussreview von Plan 4 („ein Test, der die SQL- und Fake-Implementierung der Bonus-Dedupe-Abfragen gegeneinander absichert“).

- [ ] **Step 1: Failing Tests für den Use Case**

In `BelohnungEinloesenTest.kt` einen Test ergänzen. Den bestehenden Aufbau der Datei verwenden, also die dort definierten Fixtures bzw. Hilfsfunktionen für Repository, Guthaben und Use Case:

```kotlin
    @Test
    fun `ein Erfolg traegt die echte id der angelegten Einloesung`() = runTest {
        // Aufbau wie in den bestehenden Erfolgs-Tests dieser Datei: Belohnung id 1, Preis 200,
        // Guthaben 250. Eine schon vorhandene Einloesung sorgt dafuer, dass die neue id nicht
        // zufaellig dem Standardwert 0 + 1 entspricht.
        belohnungen.einloesungen.value = listOf(
            Einloesung(id = 7, belohnungId = 1, name = "Alt", preisPunkte = 10,
                zeitpunkt = LocalDateTime.of(2026, 1, 1, 10, 0)),
        )

        val ergebnis = einloesen(1)

        val erfolg = ergebnis as EinloeseErgebnis.Erfolg
        assertEquals(8, erfolg.einloesung.id)
        assertEquals(belohnungen.einloesungen.value.single { it.id == 8L }, erfolg.einloesung)
    }
```

Die Namen `belohnungen` und `einloesen` sind Platzhalter für die in der Datei tatsächlich vorhandenen Fixture- bzw. Use-Case-Bezeichner. Lies vorher die ersten 80 Zeilen der Datei und passe die zwei Zeilen an, der Testinhalt bleibt.

Neue Datei `BelohnungEinloesenRueckgaengigTest.kt`:

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeBelohnungRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.FakeTransaktionen
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungEinloesenRueckgaengigTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val jetzt = LocalDateTime.of(2026, 3, 2, 19, 0)
    private val uhr = Clock.fixed(jetzt.atZone(zone).toInstant(), zone)

    private val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
    private val buchungen = FakeBuchungRepository(
        listOf(Buchung(art = BuchungsArt.ERLEDIGUNG, betrag = 250, zeitpunkt = jetzt.minusDays(1))),
    )
    private val transaktionen = FakeTransaktionen()
    private val einloesen = BelohnungEinloesen(
        belohnungen, buchungen, FakeFaelligkeitRepository(),
        BelohnungsPruefer(), SerienRechner(), transaktionen, uhr,
    )
    private val rueckgaengig = BelohnungEinloesenRueckgaengig(belohnungen, buchungen, transaktionen, uhr)

    private suspend fun eingeloest() = (einloesen(1) as EinloeseErgebnis.Erfolg).einloesung

    @Test
    fun `Rueckgaengig stellt den Kontostand per Gegenbuchung wieder her`() = runTest {
        val einloesung = eingeloest()

        assertEquals(true, rueckgaengig(einloesung))

        assertEquals(250, buchungen.kontostand())
        val korrektur = buchungen.stand.value.last()
        assertEquals(BuchungsArt.KORREKTUR, korrektur.art)
        assertEquals(200, korrektur.betrag)
        assertEquals(1L, korrektur.belohnungId)
        assertEquals(jetzt, korrektur.zeitpunkt)
    }

    @Test
    fun `die Einloesung bleibt stehen und wird nur storniert`() = runTest {
        val einloesung = eingeloest()

        rueckgaengig(einloesung)

        assertEquals(jetzt, belohnungen.einloesungen.value.single().storniertAm)
    }

    @Test
    fun `ein zweites Rueckgaengig bucht nichts mehr`() = runTest {
        val einloesung = eingeloest()
        rueckgaengig(einloesung)
        val buchungenNachErstem = buchungen.stand.value.size

        assertEquals(false, rueckgaengig(einloesung))

        assertEquals(buchungenNachErstem, buchungen.stand.value.size)
        assertEquals(250, buchungen.kontostand())
    }

    @Test
    fun `Rueckgaengig laeuft in einer Transaktion`() = runTest {
        val einloesung = eingeloest()
        val vorher = transaktionen.aufrufe

        rueckgaengig(einloesung)

        assertEquals(vorher + 1, transaktionen.aufrufe)
    }
}
```

Neue Datei `data/repository/BuchungRepositoryVertragTest.kt`:

```kotlin
package de.vanhake.dayorganizer.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

/**
 * Dieselben Szenarien gegen die SQL-Implementierung (RaumBuchungRepository) und gegen
 * FakeBuchungRepository. Die Fakes tragen die Logik der Bonus-Dedupe-Abfragen nach; ohne
 * diesen Vertrag koennten beide auseinanderlaufen, und die Use-Case-Tests (die nur das Fake
 * sehen) blieben trotzdem gruen. Folgearbeit aus dem Abschlussreview von Plan 4.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class BuchungRepositoryVertragTest {

    private val tag = LocalDate.of(2026, 3, 2)
    private fun um(stunde: Int) = tag.atTime(stunde, 0)

    private suspend fun fuerBeide(block: suspend (name: String, repo: BuchungRepository) -> Unit) {
        block("Fake", FakeBuchungRepository())
        val db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            DayOrganizerDatenbank::class.java,
        ).allowMainThreadQueries().build()
        try {
            block("Raum", RaumBuchungRepository(db.buchungDao()))
        } finally {
            db.close()
        }
    }

    @Test
    fun `ein gebuchter Tagesbonus zaehlt`() = runTest {
        fuerBeide { name, repo ->
            repo.buchen(listOf(Buchung(art = BuchungsArt.TAGESBONUS, betrag = 15, zeitpunkt = um(20))))
            assertEquals(true, repo.tagesbonusGebuchtAm(tag), name)
        }
    }

    @Test
    fun `ein zurueckgenommener Tagesbonus zaehlt nicht mehr`() = runTest {
        fuerBeide { name, repo ->
            repo.buchen(
                listOf(
                    Buchung(art = BuchungsArt.TAGESBONUS, betrag = 15, zeitpunkt = um(20)),
                    Buchung(art = BuchungsArt.KORREKTUR, betrag = -15, zeitpunkt = um(21)),
                ),
            )
            assertEquals(false, repo.tagesbonusGebuchtAm(tag), name)
        }
    }

    @Test
    fun `eine zurueckgenommene Einloesung gilt nicht als Tagesbonus`() = runTest {
        fuerBeide { name, repo ->
            repo.buchen(
                listOf(
                    Buchung(art = BuchungsArt.EINLOESUNG, betrag = -200, zeitpunkt = um(19), belohnungId = 1),
                    Buchung(art = BuchungsArt.KORREKTUR, betrag = 200, zeitpunkt = um(19), belohnungId = 1),
                ),
            )
            assertEquals(false, repo.tagesbonusGebuchtAm(tag), name)
        }
    }

    @Test
    fun `ein Serienbonus zaehlt bis er zurueckgenommen wird`() = runTest {
        fuerBeide { name, repo ->
            val seit = um(0)
            repo.buchen(
                listOf(Buchung(art = BuchungsArt.SERIENBONUS, betrag = 10, zeitpunkt = um(8), aktivitaetId = 1, schwelle = 7)),
            )
            assertEquals(setOf(7), repo.serienbonusSchwellenSeit(1, seit), name)

            repo.buchen(
                listOf(Buchung(art = BuchungsArt.KORREKTUR, betrag = -10, zeitpunkt = um(9), aktivitaetId = 1, schwelle = 7)),
            )
            assertEquals(emptySet(), repo.serienbonusSchwellenSeit(1, seit), name)
        }
    }

    @Test
    fun `ein Serienbonus einer anderen Aktivitaet oder vor dem Stichtag zaehlt nicht`() = runTest {
        fuerBeide { name, repo ->
            repo.buchen(
                listOf(
                    Buchung(art = BuchungsArt.SERIENBONUS, betrag = 10, zeitpunkt = um(8), aktivitaetId = 2, schwelle = 7),
                    Buchung(art = BuchungsArt.SERIENBONUS, betrag = 30, zeitpunkt = um(8).minusDays(5), aktivitaetId = 1, schwelle = 30),
                ),
            )
            assertEquals(emptySet(), repo.serienbonusSchwellenSeit(1, um(0)), name)
        }
    }
}
```

Hinweis zur Prüfung des Konstruktors: `RaumBuchungRepository(dao: BuchungDao)` laut `RaumRepositories.kt`. Weicht etwas ab, gilt der Code.

- [ ] **Step 2: Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.domainuse.BelohnungEinloesen*' --tests 'de.vanhake.dayorganizer.data.repository.BuchungRepositoryVertragTest'`
Expected:
- `BelohnungEinloesenRueckgaengig` ist unbekannt (Kompilierfehler).
- Nach einem probeweisen Auskommentieren wäre `ein Erfolg traegt die echte id` rot (id 0).
- Der Vertragstest sollte bereits grün sein, er sichert bestehendes Verhalten ab. Ist er rot, hast du eine echte Abweichung zwischen SQL und Fake gefunden. Dann STOP und als DONE_WITH_CONCERNS mit der Ausgabe melden, nicht das Fake „passend machen“, ohne die SQL-Seite zu verstehen.

- [ ] **Step 3: Implementierung**

In `BelohnungEinloesen.loese()` das Ende ersetzen:

```kotlin
        val id = belohnungen.einloesungAnlegen(einloesung)
        // Mit der echten id, damit ein Rueckgaengig genau diese Zeile stornieren kann.
        return EinloeseErgebnis.Erfolg(einloesung.copy(id = id))
```

Neue Datei `BelohnungEinloesenRueckgaengig.kt`:

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.Transaktionen
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Einloesung
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Macht genau eine Einloesung rueckgaengig: Die Einloesung wird storniert (nicht geloescht)
 * und der Preis per KORREKTUR-Buchung gutgeschrieben -- dasselbe Prinzip wie
 * FaelligkeitErledigenRueckgaengig (README: "Korrekturen faelschen keine Historie").
 *
 * Die Gegenbuchung traegt die belohnungId. Damit schliesst die Netto-Tagesbonus-Abfrage sie
 * aus (BuchungDao.nettoTagesbonusPunkte, "belohnungId IS NULL"); abgesichert im
 * BuchungRepositoryVertragTest.
 *
 * Gibt false zurueck, wenn die Einloesung unbekannt oder schon storniert ist -- dann wird
 * nichts gebucht. Stornieren und Buchen liegen in einer Transaktion, damit ein Doppeltipp
 * nicht zweimal gutschreibt.
 */
class BelohnungEinloesenRueckgaengig @Inject constructor(
    private val belohnungen: BelohnungRepository,
    private val buchungen: BuchungRepository,
    private val transaktionen: Transaktionen,
    private val uhr: Clock,
) {
    suspend operator fun invoke(einloesung: Einloesung): Boolean = transaktionen.inTransaktion {
        val jetzt = LocalDateTime.now(uhr)
        if (!belohnungen.einloesungStornieren(einloesung.id, jetzt)) return@inTransaktion false
        buchungen.buchen(
            listOf(
                Buchung(
                    art = BuchungsArt.KORREKTUR,
                    betrag = einloesung.preisPunkte,
                    zeitpunkt = jetzt,
                    belohnungId = einloesung.belohnungId,
                    notiz = "Rueckgaengig: ${BuchungsArt.EINLOESUNG}",
                ),
            ),
        )
        true
    }
}
```

- [ ] **Step 4: Tests grün**

Run: `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL. `BelohnungEinloesenRueckgaengigTest` (4), `BuchungRepositoryVertragTest` (5) und der neue `BelohnungEinloesenTest`-Test sind grün.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/BelohnungEinloesen.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/BelohnungEinloesenRueckgaengig.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/BelohnungEinloesenTest.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/BelohnungEinloesenRueckgaengigTest.kt \
        app/src/test/kotlin/de/vanhake/dayorganizer/data/repository/BuchungRepositoryVertragTest.kt
git commit -m "feat(belohnungen): Einloesen rueckgaengig als Gegenbuchung, Vertragstest SQL gegen Fake

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 4: Belohnungen-ViewModel — Toast mit Rückgängig, Symbol, Historie ohne Stornierte

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/BelohnungSymbol.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/SpeicherErgebnis.kt` (verschoben)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetEntwurf.kt` (enum entfernen), sowie alle Importstellen von `SpeicherErgebnis`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungenZustand.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungenViewModel.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/theme/BelohnungSymbolTest.kt` (neu)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungenZustandTest.kt` (neu)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungenViewModelTest.kt`

**Interfaces:**
- Consumes: `BelohnungEinloesenRueckgaengig` (Task 3), `Belohnung.symbol`, `Einloesung.storniertAm` (Task 1), `Hinweistoast` (Plan 4).
- Produces:
  - `BELOHNUNG_SYMBOLE: List<String>` (12 Schlüssel), `BELOHNUNG_SYMBOL_STANDARD = "redeem"`, `belohnungSymbol(schluessel: String): ImageVector`
  - `enum class SpeicherErgebnis { ANGELEGT, GEAENDERT }` jetzt in `de.vanhake.dayorganizer.ui.gemeinsam`
  - `BelohnungKachel.symbol: String`
  - `fun List<Einloesung>.sichtbare(): List<Einloesung>`
  - `BelohnungenViewModel`: zusätzlicher Konstruktorparameter `einloesenRueckgaengig: BelohnungEinloesenRueckgaengig` (an vierter Stelle, vor `widgetAnstossen`), `toast: StateFlow<Hinweistoast?>`, `rueckgaengig(): Job`, `gespeichertMelden(ergebnis: SpeicherErgebnis)`, `archivieren(belohnungId: Long): Job` zeigt jetzt einen Toast.
- Toast-Texte (Kotlin-Literale):
  - `"<Name> eingelöst · −<Preis> Punkte"`, mit Rückgängig. Das Minus ist U+2212, der Punkt U+00B7, wie im Prototyp `redeem()`.
  - `"<Name> archiviert"`
  - `"Belohnung angelegt"`
  - `"Änderungen gespeichert"`
  - Anzeigedauer 3 800 ms.

**Symbolkatalog:** Die ersten Schlüssel stammen aus dem Prototyp (`rewards`: `auto_stories`, `movie`, `ramen_dining`, `spa`). Der Rest ergänzt auf 12 Einträge, damit das Raster im Editor wie bei den Aktivitäten zwei volle Reihen à 6 hat:

| Schlüssel | Icon |
|---|---|
| `redeem` (Standard) | `Icons.Filled.Redeem` |
| `auto_stories` | `Icons.Filled.AutoStories` |
| `movie` | `Icons.Filled.Movie` |
| `ramen_dining` | `Icons.Filled.RamenDining` |
| `spa` | `Icons.Filled.Spa` |
| `local_cafe` | `Icons.Filled.LocalCafe` |
| `icecream` | `Icons.Filled.Icecream` |
| `sports_esports` | `Icons.Filled.SportsEsports` |
| `shopping_bag` | `Icons.Filled.ShoppingBag` |
| `flight` | `Icons.Filled.Flight` |
| `music_note` | `Icons.Filled.MusicNote` |
| `celebration` | `Icons.Filled.Celebration` |

Kompiliert eines dieser Icons nicht, nimm ein naheliegendes, optisch unterscheidbares aus `material-icons-extended`. Der Test „jeder Katalogeintrag liefert ein eigenes Symbol“ verlangt 12 verschiedene. Vermerke den Ersatz im Commit.

- [ ] **Step 1: `SpeicherErgebnis` verschieben (reiner Umzug)**

Neue Datei `ui/gemeinsam/SpeicherErgebnis.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.gemeinsam

/** Was ein Bearbeiten-Bildschirm beim Speichern getan hat -- die Liste zeigt danach den
 * passenden Toast. Gemeinsam fuer Aktivitaeten und Belohnungen. */
enum class SpeicherErgebnis { ANGELEGT, GEAENDERT }
```

Das enum samt KDoc aus `ui/aktivitaeten/AktivitaetEntwurf.kt` entfernen. Alle Verwender auf `de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis` umstellen: `grep -rln "SpeicherErgebnis" app/src` zeigt sie, u. a. `Navigation.kt`, `AktivitaetBearbeitenViewModel.kt`, `AktivitaetBearbeitenScreen.kt`, `AktivitaetenScreen.kt`, `AktivitaetenViewModel.kt` und die zugehörigen Tests.

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL (reiner Umzug, keine Verhaltensänderung).

- [ ] **Step 2: Failing Tests**

`ui/theme/BelohnungSymbolTest.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Redeem
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungSymbolTest {

    @Test
    fun `ein bekannter Schluessel liefert sein Symbol`() {
        assertEquals(Icons.Filled.Movie, belohnungSymbol("movie"))
    }

    @Test
    fun `ein unbekannter Schluessel faellt auf das Standardsymbol zurueck`() {
        assertEquals(Icons.Filled.Redeem, belohnungSymbol("nicht_vorhanden"))
    }

    @Test
    fun `jeder Katalogeintrag liefert ein eigenes Symbol`() {
        assertEquals(12, BELOHNUNG_SYMBOLE.size)
        assertEquals(12, BELOHNUNG_SYMBOLE.map(::belohnungSymbol).toSet().size)
    }

    @Test
    fun `der Standard steht im Katalog und entspricht dem Modell-Standard`() {
        assertEquals("redeem", BELOHNUNG_SYMBOL_STANDARD)
        assertEquals(BELOHNUNG_SYMBOL_STANDARD, BELOHNUNG_SYMBOLE.first())
    }
}
```

`ui/belohnungen/BelohnungenZustandTest.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Einloesung
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungenZustandTest {

    private fun einloesung(id: Long, storniertAm: LocalDateTime? = null) = Einloesung(
        id = id, belohnungId = 1, name = "Kino $id", preisPunkte = 100,
        zeitpunkt = LocalDateTime.of(2026, 3, id.toInt(), 19, 0), storniertAm = storniertAm,
    )

    @Test
    fun `stornierte Einloesungen erscheinen nicht in der sichtbaren Historie`() {
        val liste = listOf(einloesung(3), einloesung(2, storniertAm = LocalDateTime.of(2026, 3, 2, 19, 1)), einloesung(1))

        assertEquals(listOf(3L, 1L), liste.sichtbare().map { it.id })
    }
}
```

In `BelohnungenViewModelTest.kt`:
- Der Helfer `viewModel(...)` übergibt zusätzlich `einloesenRueckgaengig = BelohnungEinloesenRueckgaengig(belohnungen, buchungen, transaktionen, uhr)`. Dafür eine gemeinsame `FakeTransaktionen()`-Instanz im Helfer anlegen und an beide Use Cases geben.
- Dazu Imports: `BelohnungEinloesenRueckgaengig`, `Hinweistoast`, `SpeicherErgebnis` (aus `ui.gemeinsam`).

Dann ergänzen:

```kotlin
    @Test
    fun `ein erfolgreiches Einloesen zeigt einen Toast mit Rueckgaengig`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val vm = viewModel(belohnungen, guthaben(250))

        vm.einloesen(1)
        dispatcher.scheduler.runCurrent()

        assertEquals(Hinweistoast("Kinoabend eingelöst · −200 Punkte", kannRueckgaengig = true), vm.toast.value)
    }

    @Test
    fun `Rueckgaengig nach dem Einloesen stellt Kontostand her und storniert`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(250)
        var widgetAngestossen = 0
        val vm = viewModel(belohnungen, buchungen, widgetAnstossen = { widgetAngestossen++ })
        vm.einloesen(1)
        dispatcher.scheduler.runCurrent()

        vm.rueckgaengig()
        dispatcher.scheduler.runCurrent()

        assertEquals(250, buchungen.kontostand())
        assertEquals(true, belohnungen.einloesungen.value.single().storniertAm != null)
        assertNull(vm.toast.value)
        // einmal nach dem Einloesen, einmal nach dem Rueckgaengig
        assertEquals(2, widgetAngestossen)
    }

    @Test
    fun `nach Ablauf des Toasts gibt es kein Rueckgaengig mehr`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(250)
        val vm = viewModel(belohnungen, buchungen)
        vm.einloesen(1)
        dispatcher.scheduler.runCurrent()
        dispatcher.scheduler.advanceTimeBy(3_801)
        dispatcher.scheduler.runCurrent()

        vm.rueckgaengig()
        dispatcher.scheduler.runCurrent()

        assertNull(vm.toast.value)
        assertEquals(50, buchungen.kontostand())
    }

    @Test
    fun `Archivieren zeigt einen Toast ohne Rueckgaengig`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val vm = viewModel(belohnungen, guthaben(0))

        vm.archivieren(1)
        dispatcher.scheduler.runCurrent()

        assertEquals(Hinweistoast("Kinoabend archiviert", kannRueckgaengig = false), vm.toast.value)
    }

    @Test
    fun `gespeichert melden zeigt den passenden Toast`() = runTest(dispatcher) {
        val vm = viewModel(FakeBelohnungRepository(), guthaben(0))

        vm.gespeichertMelden(SpeicherErgebnis.ANGELEGT)
        dispatcher.scheduler.runCurrent()
        assertEquals("Belohnung angelegt", vm.toast.value?.text)

        vm.gespeichertMelden(SpeicherErgebnis.GEAENDERT)
        dispatcher.scheduler.runCurrent()
        assertEquals("Änderungen gespeichert", vm.toast.value?.text)
    }
```

Der bestehende Test `Ein erfolgreiches Einloesen zeigt keine Meldung` bleibt unverändert. `meldung` ist weiterhin nur für Fehlschläge da, der Erfolg läuft über `toast`.

- [ ] **Step 3: Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.ui.belohnungen.*' --tests 'de.vanhake.dayorganizer.ui.theme.BelohnungSymbolTest'`
Expected: FAIL beim Kompilieren (`belohnungSymbol`, `sichtbare`, `toast`, Konstruktor).

- [ ] **Step 4: Katalog**

`ui/theme/BelohnungSymbol.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Icecream
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RamenDining
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Schluessel der Belohnungssymbole. Die ersten Eintraege nach "redeem" stammen aus dem
 * Redesign-Prototyp (Konstante rewards), der Rest ergaenzt auf zwei volle Reihen im Raster.
 * Der Standard muss Belohnung.symbol im Domaenenmodell entsprechen.
 */
val BELOHNUNG_SYMBOLE: List<String> = listOf(
    "redeem", "auto_stories", "movie", "ramen_dining", "spa", "local_cafe",
    "icecream", "sports_esports", "shopping_bag", "flight", "music_note", "celebration",
)

const val BELOHNUNG_SYMBOL_STANDARD = "redeem"

/** Unbekannte Schluessel (aelteres Backup, kuenftige Version) fallen auf das Standardsymbol
 * zurueck -- rein kosmetisch, wie bei aktivitaetSymbol. */
fun belohnungSymbol(schluessel: String): ImageVector = when (schluessel) {
    "auto_stories" -> Icons.Filled.AutoStories
    "movie" -> Icons.Filled.Movie
    "ramen_dining" -> Icons.Filled.RamenDining
    "spa" -> Icons.Filled.Spa
    "local_cafe" -> Icons.Filled.LocalCafe
    "icecream" -> Icons.Filled.Icecream
    "sports_esports" -> Icons.Filled.SportsEsports
    "shopping_bag" -> Icons.Filled.ShoppingBag
    "flight" -> Icons.Filled.Flight
    "music_note" -> Icons.Filled.MusicNote
    "celebration" -> Icons.Filled.Celebration
    else -> Icons.Filled.Redeem
}
```

- [ ] **Step 5: Zustand**

In `BelohnungenZustand.kt`:
- `BelohnungKachel` bekommt nach `beschreibung` das Feld `val symbol: String,`.
- Am Dateiende kommt hinzu:

```kotlin
/** Stornierte Einloesungen bleiben fuer Backup und Nachvollziehbarkeit in der Datenbank,
 * gehoeren aber nicht in die sichtbare Historie. */
fun List<Einloesung>.sichtbare(): List<Einloesung> = filter { it.storniertAm == null }
```

Den KDoc von `EinloeseMeldung` anpassen: „Ein Erfolg braucht keine eigene Meldung -- er erscheint als Toast mit Rückgängig (BelohnungenViewModel.toast).“

- [ ] **Step 6: ViewModel**

`BelohnungenViewModel.kt` anpassen. Bestehende Kommentare bleiben, wo der Code bleibt.

```kotlin
@HiltViewModel
class BelohnungenViewModel(
    private val belohnungen: BelohnungRepository,
    private val buchungen: BuchungRepository,
    private val einloesenUseCase: BelohnungEinloesen,
    private val einloesenRueckgaengig: BelohnungEinloesenRueckgaengig,
    private val widgetAnstossen: suspend () -> Unit,
) : ViewModel() {

    @Inject
    constructor(
        belohnungen: BelohnungRepository,
        buchungen: BuchungRepository,
        einloesenUseCase: BelohnungEinloesen,
        einloesenRueckgaengig: BelohnungEinloesenRueckgaengig,
        widgetAktualisierung: WidgetAktualisierung,
    ) : this(
        belohnungen,
        buchungen,
        einloesenUseCase,
        einloesenRueckgaengig,
        { widgetAktualisierung.anstossen() },
    )

    // ... _laufendeEinloesungen, _meldung, _meldungFehlendePunkte unveraendert ...

    // Eigener Flow wie in HeuteViewModel: synchron gesetzt, im Test deterministisch.
    private val _toast = MutableStateFlow<Hinweistoast?>(null)
    val toast: StateFlow<Hinweistoast?> = _toast.asStateFlow()
    private var ausstehendesRueckgaengig: (suspend () -> Unit)? = null
    private var toastJob: Job? = null

    val zustand: StateFlow<BelohnungenZustand> = combine(
        belohnungen.beobachteAktive(),
        buchungen.beobachteKontostand(),
        belohnungen.beobachteEinloesungen(),
        _laufendeEinloesungen,
    ) { liste, kontostand, einloesungen, laufende ->
        BelohnungenZustand(
            kontostand = kontostand,
            kacheln = liste.map { belohnung ->
                BelohnungKachel(
                    id = belohnung.id,
                    name = belohnung.name,
                    beschreibung = belohnung.beschreibung,
                    symbol = belohnung.symbol,
                    preisPunkte = belohnung.preisPunkte,
                    einloesbarkeit = einloesenUseCase.einloesbarkeit(belohnung, kontostand),
                    wirdEingeloest = belohnung.id in laufende,
                )
            },
            einloesungen = einloesungen.sichtbare(),
        )
    }
        // (bestehender flowOn-Kommentar)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BelohnungenZustand())

    // einloesen(...) unveraendert.

    /** Setzt [_meldung] fuer alles ausser einem Erfolg; ein Erfolg wird als Toast mit
     * Rueckgaengig gezeigt. */
    private fun meldungFuer(ergebnis: EinloeseErgebnis) {
        when (ergebnis) {
            is EinloeseErgebnis.Erfolg -> zeigeToast(
                BelohnungenToastTexte.eingeloest(ergebnis.einloesung.name, ergebnis.einloesung.preisPunkte),
                rueckgaengigAktion = { einloesenRueckgaengig(ergebnis.einloesung) },
            )
            // ... Abgelehnt / Unbekannt unveraendert ...
        }
    }

    // meldungGezeigt() unveraendert.

    fun rueckgaengig() = viewModelScope.launch {
        val aktion = ausstehendesRueckgaengig ?: return@launch
        toastJob?.cancel()
        ausstehendesRueckgaengig = null
        _toast.value = null
        // Wie beim Einloesen darf eine Datenbankausnahme nicht die App mitreissen;
        // ein Abbruch ist dagegen kein Fehler und wird weitergereicht.
        try {
            aktion()
        } catch (abbruch: CancellationException) {
            throw abbruch
        } catch (ausnahme: Exception) {
            Log.w(TAG, "Rueckgaengig fehlgeschlagen", ausnahme)
            _meldung.value = EinloeseMeldung.FEHLER
            return@launch
        }
        widgetAnstossen()
    }

    // Kein Rueckgaengig (Entscheidung in Plan 6, wie bei den Aktivitaeten in Plan 5).
    fun archivieren(belohnungId: Long) = viewModelScope.launch {
        val name = belohnungen.nachId(belohnungId)?.name
        belohnungen.archivieren(belohnungId)
        name?.let { zeigeToast(BelohnungenToastTexte.archiviert(it)) }
    }

    /** Vom Screen aufgerufen, wenn der Editor mit einem Ergebnis zurueckgekehrt ist. */
    fun gespeichertMelden(ergebnis: SpeicherErgebnis) {
        zeigeToast(
            when (ergebnis) {
                SpeicherErgebnis.ANGELEGT -> BelohnungenToastTexte.ANGELEGT
                SpeicherErgebnis.GEAENDERT -> BelohnungenToastTexte.GEAENDERT
            },
        )
    }

    private fun zeigeToast(text: String, rueckgaengigAktion: (suspend () -> Unit)? = null) {
        toastJob?.cancel()
        ausstehendesRueckgaengig = rueckgaengigAktion
        _toast.value = Hinweistoast(text, kannRueckgaengig = rueckgaengigAktion != null)
        toastJob = viewModelScope.launch {
            delay(TOAST_DAUER_MS)
            _toast.value = null
            ausstehendesRueckgaengig = null
        }
    }

    private companion object {
        const val TAG = "BelohnungenViewModel"
        /** Wie HeuteViewModel. */
        const val TOAST_DAUER_MS = 3_800L
    }
}

/** Reine Textbausteine, ohne Android-Ressourcen testbar -- Muster HeuteToastTexte. */
private object BelohnungenToastTexte {
    const val ANGELEGT = "Belohnung angelegt"
    const val GEAENDERT = "Änderungen gespeichert"
    fun archiviert(name: String) = "$name archiviert"
    fun eingeloest(name: String, preis: Int) = "$name eingelöst · −$preis Punkte"
}
```

Imports ergänzen: `Hinweistoast`, `SpeicherErgebnis` (aus `ui.gemeinsam`), `BelohnungEinloesenRueckgaengig`, `kotlinx.coroutines.Job`, `kotlinx.coroutines.delay`, `kotlin.coroutines.cancellation.CancellationException`.

Den bestehenden `BelohnungenScreen` (alter Stand, wird in Task 5 ersetzt) nicht anfassen. Er kompiliert weiter, weil `BelohnungKachel` nur ein Feld dazubekommt.

- [ ] **Step 7: Tests und Build**

Run: `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL, die neuen Tests sind grün, die bestehenden `BelohnungenViewModelTest`-Tests ebenso.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui \
        app/src/test/kotlin/de/vanhake/dayorganizer/ui
git commit -m "feat(belohnungen): Toast mit Rueckgaengig beim Einloesen, Symbolkatalog, Historie ohne Stornierte

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 5: Belohnungen-Liste im neuen Design

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungenScreen.kt` (vollständig ersetzen)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Farben.kt` (`heroStern`)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt` (Belohnungen-Eintrag: Ergebnis aus dem `savedStateHandle`)
- Modify: `app/src/main/res/values/strings.xml`

**Interfaces:**
- Consumes: `BelohnungenViewModel.zustand/.toast/.meldung/.meldungFehlendePunkte/.einloesen/.archivieren/.rueckgaengig/.meldungGezeigt/.gespeichertMelden` (Task 4), `belohnungSymbol` (Task 4), `SpeicherErgebnis` (Task 4), `HinweistoastLeiste` (Plan 4).
- Produces:
  - `BelohnungenScreen(bearbeiten: (Long) -> Unit, gespeichert: SpeicherErgebnis?, gespeichertGezeigt: () -> Unit, viewModel = hiltViewModel())`
  - `Ziele.ERGEBNIS_BELOHNUNG_GESPEICHERT = "belohnung_gespeichert"`
  - `DayOrganizerFesteFarben.heroStern = Color(0xFFFEC84B)` (Prototyp `oklch(0.86 0.15 85)`, mit derselben OKLCH→sRGB-Formel wie die übrigen Farben umgerechnet)

**Aufbau (Prototyp `sBel`):**
- Überschrift „Belohnungen“ (32 sp, ExtraBold).
- Heldenkarte auf `hero` mit „Dein Punktestand“, Kontostand (48 sp, ExtraBold) und Stern rechts unten in `heroStern`.
- Je Belohnung eine Karte (Radius 24 = `DayOrganizerFormen.karte`, Fläche `surface`):
  - Symbolkachel 48 dp auf `goldSoft`/`goldInk`, Name, Beschreibung, Preis „N Pkt“ rechts.
  - Fortschrittsbalken 8 dp: Füllung `green`, wenn einlösbar, sonst `accent`; Spur `surface2`; Anteil = Kontostand/Preis, höchstens 1.
  - Hinweiszeile mit Symbol:
    - `hourglass_top` → „Noch N Punkte nötig“
    - `local_fire_department` → Serie zu kurz
    - `lock_clock` → zu früh
    - `check_circle` → „Bereit zum Einlösen“ in `green`
    - Sonst ist die Zeile `ink2`.
  - Archiv-Knopf und Knopf „Einlösen“: `accent`/`onAccent`, wenn möglich, sonst deaktiviert in `surface2`/`ink2`.
- Ein Tipp auf die Karte öffnet den Editor. Den Knopf „Bearbeiten“ gibt es nicht mehr; der Prototyp hat keinen.
- Historie „EINGELÖST“ (13 sp, ExtraBold, Großbuchstaben, `ink2`): Zeilen mit Name, Datum `dd.MM.yyyy` und rechts „−N“, getrennt durch 1-dp-Linien in `line`.
- Erweiterter FAB „Neue Belohnung“ wie bei den Aktivitäten, ausgeblendet, solange ein Toast oder eine Meldung steht.
- Toast unten: Erfolg und Archivieren über `viewModel.toast` (mit Rückgängig), Fehlschläge über `meldung`. Beides nutzt dieselbe `HinweistoastLeiste`. Eine Meldung verschwindet nach 3 800 ms (`meldungGezeigt()`). Steht beides, hat der Toast Vorrang.
- Leerer Zustand: Hinweistext unter der Heldenkarte.

- [ ] **Step 1: Farbe und Strings**

In `Farben.kt`, `object DayOrganizerFesteFarben`:

```kotlin
    /** Stern auf der Punktestand-Heldenkarte (Prototyp oklch(0.86 0.15 85)). */
    val heroStern = Color(0xFFFEC84B)
```

`strings.xml` ergänzen:

```xml
    <string name="tab_belohnungen_titel">Belohnungen</string>
    <string name="dein_punktestand">Dein Punktestand</string>
    <string name="bereit_zum_einloesen">Bereit zum Einlösen</string>
    <string name="einloesung_abzug">−%1$d</string>
    <string name="belohnungen_leer">Noch keine Belohnungen. Lege mit „Neue Belohnung“ die erste an.</string>
```

`einloesung_zeile` (Format „%1$s — %2$d Punkte“) ist danach unbenutzt und wird entfernt. Vorher mit `grep -rn einloesung_zeile app/src` prüfen, dass nur `BelohnungenScreen.kt` sie nutzt. `bearbeiten` bleibt, falls `grep` weitere Nutzer zeigt, sonst wird es ebenfalls entfernt.

- [ ] **Step 2: `BelohnungenScreen.kt` vollständig ersetzen**

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.rechner.Hindernis
import de.vanhake.dayorganizer.ui.gemeinsam.Hinweistoast
import de.vanhake.dayorganizer.ui.gemeinsam.HinweistoastLeiste
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFesteFarben
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import de.vanhake.dayorganizer.ui.theme.belohnungSymbol
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter

private fun EinloeseMeldung.textId(): Int = when (this) {
    EinloeseMeldung.ZU_WENIG_PUNKTE -> R.string.noch_punkte_noetig
    EinloeseMeldung.SERIE_ZU_KURZ -> R.string.serie_zu_kurz
    EinloeseMeldung.ZU_FRUEH -> R.string.noch_zu_frueh
    EinloeseMeldung.NICHT_MEHR_VORHANDEN -> R.string.einloese_nicht_mehr_vorhanden
    EinloeseMeldung.FEHLER -> R.string.einloese_fehlgeschlagen
}

private val DATUM_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

/** Wie lange eine Fehlschlag-Meldung steht -- gleich lang wie die Toasts der ViewModels. */
private const val MELDUNG_DAUER_MS = 3_800L

@Composable
fun BelohnungenScreen(
    bearbeiten: (Long) -> Unit,
    gespeichert: SpeicherErgebnis?,
    gespeichertGezeigt: () -> Unit,
    viewModel: BelohnungenViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val meldung by viewModel.meldung.collectAsStateWithLifecycle()
    val kontext = LocalContext.current
    val farben = DayOrganizerTheme.farben

    // Ergebnis aus dem Editor einmalig melden und im savedStateHandle loeschen -- Muster
    // AktivitaetenScreen.
    LaunchedEffect(gespeichert) {
        gespeichert?.let {
            viewModel.gespeichertMelden(it)
            gespeichertGezeigt()
        }
    }

    // Fehlschlag-Meldungen stehen so lange wie ein Toast und werden dann zurueckgesetzt --
    // sonst erschiene dieselbe Meldung nach jeder Rotation erneut.
    LaunchedEffect(meldung) {
        if (meldung == null) return@LaunchedEffect
        delay(MELDUNG_DAUER_MS)
        viewModel.meldungGezeigt()
    }

    val meldungsToast = meldung?.let { aktuelle ->
        val text = if (aktuelle == EinloeseMeldung.ZU_WENIG_PUNKTE) {
            kontext.getString(aktuelle.textId(), viewModel.meldungFehlendePunkte.value)
        } else {
            kontext.getString(aktuelle.textId())
        }
        Hinweistoast(text, kannRueckgaengig = false)
    }
    // Der Toast hat Vorrang: er gehoert zur letzten erfolgreichen Aktion.
    val sichtbarerToast = toast ?: meldungsToast

    Box(Modifier.fillMaxSize().background(farben.bg)) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.tab_belohnungen_titel),
                    modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 4.dp),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.02).em,
                    color = farben.ink,
                )
            }

            item { PunktestandKarte(zustand.kontostand) }

            if (zustand.kacheln.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.belohnungen_leer),
                        modifier = Modifier.padding(horizontal = 4.dp),
                        fontSize = 15.sp,
                        color = farben.ink2,
                    )
                }
            }

            // "kachel-"/"einloesung-" statt der nackten id: beide ids sind eigene, je bei 1
            // beginnende Autoincrement-Folgen und teilen sich sonst den Key-Namensraum der
            // LazyColumn -- Compose stuerzt bei gleicher id ab.
            items(zustand.kacheln, key = { "kachel-${it.id}" }) { kachel ->
                BelohnungKarte(
                    kachel = kachel,
                    kontostand = zustand.kontostand,
                    bearbeiten = { bearbeiten(kachel.id) },
                    einloesen = { viewModel.einloesen(kachel.id) },
                    archivieren = { viewModel.archivieren(kachel.id) },
                )
            }

            if (zustand.einloesungen.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.einloese_historie).uppercase(),
                        modifier = Modifier.padding(start = 4.dp, top = 14.dp, bottom = 0.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.06.em,
                        color = farben.ink2,
                    )
                }
                items(zustand.einloesungen, key = { "einloesung-${it.id}" }) { einloesung ->
                    HistorienZeile(einloesung)
                }
            }
        }

        if (sichtbarerToast == null) {
            ExtendedFloatingActionButton(
                onClick = { bearbeiten(0L) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.neue_belohnung), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) },
                shape = DayOrganizerFormen.feld,
                containerColor = farben.accent,
                contentColor = farben.onAccent,
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            )
        }

        sichtbarerToast?.let { aktueller ->
            HinweistoastLeiste(
                toast = aktueller,
                rueckgaengig = { viewModel.rueckgaengig() },
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            )
        }
    }
}

@Composable
private fun PunktestandKarte(kontostand: Int) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = DayOrganizerFormen.heldenKarte,
        color = DayOrganizerTheme.farben.hero,
        contentColor = Color.White,
    ) {
        Row(
            modifier = Modifier.padding(22.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.dein_punktestand),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    kontostand.toString(),
                    fontSize = 48.sp,
                    lineHeight = 48.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.03).em,
                )
            }
            Icon(
                Icons.Filled.Star,
                contentDescription = null,
                tint = DayOrganizerFesteFarben.heroStern,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun BelohnungKarte(
    kachel: BelohnungKachel,
    kontostand: Int,
    bearbeiten: () -> Unit,
    einloesen: () -> Unit,
    archivieren: () -> Unit,
) {
    val farben = DayOrganizerTheme.farben
    val moeglich = kachel.einloesbarkeit.moeglich
    val anteil = (kontostand.toFloat() / kachel.preisPunkte).coerceIn(0f, 1f)

    Surface(
        onClick = bearbeiten,
        shape = DayOrganizerFormen.karte,
        color = farben.surface,
        contentColor = farben.ink,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
                Surface(
                    shape = DayOrganizerFormen.knopf,
                    color = farben.goldSoft,
                    contentColor = farben.goldInk,
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(belohnungSymbol(kachel.symbol), contentDescription = null, modifier = Modifier.size(26.dp))
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(kachel.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (kachel.beschreibung.isNotBlank()) {
                        Text(kachel.beschreibung, fontSize = 13.sp, lineHeight = 17.sp, color = farben.ink2)
                    }
                }
                Text(
                    stringResource(R.string.punkte_kurz, kachel.preisPunkte),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                )
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(farben.surface2),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(anteil)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (moeglich) farben.green else farben.accent),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val (symbol, text) = hinweis(kachel)
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val hinweisFarbe = if (moeglich) farben.green else farben.ink2
                    Icon(symbol, contentDescription = null, tint = hinweisFarbe, modifier = Modifier.size(18.dp))
                    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = hinweisFarbe)
                }
                IconButton(onClick = archivieren) {
                    Icon(Icons.Outlined.Archive, contentDescription = stringResource(R.string.archivieren), tint = farben.ink2)
                }
                Button(
                    onClick = einloesen,
                    enabled = moeglich && !kachel.wirdEingeloest,
                    shape = DayOrganizerFormen.kachel,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = farben.accent,
                        contentColor = farben.onAccent,
                        disabledContainerColor = farben.surface2,
                        disabledContentColor = farben.ink2,
                    ),
                ) {
                    Text(stringResource(R.string.einloesen), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun hinweis(kachel: BelohnungKachel): Pair<ImageVector, String> =
    when (kachel.einloesbarkeit.hindernis) {
        Hindernis.ZU_WENIG_PUNKTE -> Icons.Filled.HourglassTop to
            stringResource(R.string.noch_punkte_noetig, kachel.einloesbarkeit.fehlendePunkte)
        Hindernis.SERIE_ZU_KURZ -> Icons.Filled.LocalFireDepartment to stringResource(R.string.serie_zu_kurz)
        Hindernis.ZU_FRUEH -> Icons.Filled.LockClock to stringResource(R.string.noch_zu_frueh)
        null -> Icons.Filled.CheckCircle to stringResource(R.string.bereit_zum_einloesen)
    }

@Composable
private fun HistorienZeile(einloesung: Einloesung) {
    val farben = DayOrganizerTheme.farben
    Column(Modifier.padding(horizontal = 4.dp)) {
        HorizontalDivider(color = farben.line)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(einloesung.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = farben.ink)
                Text(einloesung.zeitpunkt.format(DATUM_FORMAT), fontSize = 12.sp, color = farben.ink2)
            }
            Text(
                stringResource(R.string.einloesung_abzug, einloesung.preisPunkte),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = farben.ink2,
            )
        }
    }
}
```

Die Überschrift nutzt den eigenen String `tab_belohnungen_titel` = „Belohnungen“ aus Step 1, denn der Tab-String `tab_belohnungen` lautet „Belohnung“ (gekürzt für die Leiste).

- [ ] **Step 3: Navigation**

In `Navigation.kt`:
- In `object Ziele` ergänzen:

```kotlin
    /** savedStateHandle-Schluessel: Belohnungs-Editor -> Liste, Wert = SpeicherErgebnis.name. */
    const val ERGEBNIS_BELOHNUNG_GESPEICHERT = "belohnung_gespeichert"
```

- Den `BELOHNUNGEN`-Eintrag ersetzen:

```kotlin
            composable(Ziele.BELOHNUNGEN) { eintrag ->
                val gespeichert by eintrag.savedStateHandle
                    .getStateFlow<String?>(Ziele.ERGEBNIS_BELOHNUNG_GESPEICHERT, null)
                    .collectAsStateWithLifecycle()
                BelohnungenScreen(
                    bearbeiten = { id -> navController.navigate(Ziele.belohnungBearbeiten(id)) },
                    gespeichert = gespeichert?.let(SpeicherErgebnis::valueOf),
                    gespeichertGezeigt = {
                        eintrag.savedStateHandle[Ziele.ERGEBNIS_BELOHNUNG_GESPEICHERT] = null
                    },
                )
            }
```

Der `BELOHNUNG_BEARBEITEN`-Eintrag bleibt bis Task 6 unverändert, denn der Editor liefert noch kein Ergebnis.

- [ ] **Step 4: Tests und Build**

Run: `./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL. `grep -rn "einloesung_zeile" app/src` liefert keine Treffer.

- [ ] **Step 5: Sichtprüfung im Emulator (manuell)**

Emulator wie in `docs/HANDOFF.md` starten und `./gradlew :app:installDebug` ausführen. **Nur eine eigene Testbelohnung anlegen und bedienen** (Name z. B. „Plan6-Test“, Preis klein genug für das vorhandene Guthaben), noch über den alten Editor. Dann hell und dunkel prüfen:
1. Überschrift, Heldenkarte mit Kontostand und Stern, Karte der Testbelohnung mit Symbol (Standard: Geschenk), Preis, Balken und Hinweis.
2. Einlösen der Testbelohnung: Toast „Plan6-Test eingelöst · −N Punkte“ mit „Rückgängig“, Kontostand sinkt, die Historie zeigt die Zeile.
3. „Rückgängig“: Kontostand wieder wie vorher, die Historienzeile verschwindet.
4. Einlösen ohne genug Punkte ist nicht möglich (Knopf deaktiviert, Hinweis „Noch N Punkte nötig“).
5. Archivieren der Testbelohnung: Toast „Plan6-Test archiviert“, die Karte verschwindet.

Bildschirmfotos ins Scratchpad legen und im Bericht nennen. Ohne Emulator wird dieser Step mit Begründung übersprungen.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungenScreen.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Farben.kt \
        app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt \
        app/src/main/res/values/strings.xml
git commit -m "feat(belohnungen): Liste im neuen Design mit Punktestand-Heldenkarte und Toasts

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

### Task 6: Belohnung bearbeiten — gemeinsame Formularbausteine, typisierter Entwurf, abgesichertes Speichern

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/Formularbausteine.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/DatumDialog.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/UhrzeitDialog.kt` (String-Keys)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetBearbeitenScreen.kt` (Bausteine aus `gemeinsam` nutzen)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungEntwurf.kt` (vollständig ersetzen)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungBearbeitenViewModel.kt` (vollständig ersetzen)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungBearbeitenScreen.kt` (vollständig ersetzen)
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt` (`BELOHNUNG_BEARBEITEN`)
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungEntwurfTest.kt` (vollständig ersetzen)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungBearbeitenViewModelTest.kt` (neu)

**Interfaces:**
- Consumes: `BELOHNUNG_SYMBOLE`, `belohnungSymbol`, `SpeicherErgebnis` (Task 4); `Ziele.ERGEBNIS_BELOHNUNG_GESPEICHERT` (Task 5); `Stepper`, `StepperZeile`, `UhrzeitDialog` (Plan 5); `aktivitaetSymbol` (Plan 4).
- Produces:
  - `ui/gemeinsam/Formularbausteine.kt`:
    - `Kopfzeile(titel: String, zurueck: () -> Unit)`
    - `Abschnitt(titelId: Int, inhalt: @Composable ColumnScope.() -> Unit)`
    - `FehlerText(sichtbar: Boolean, textId: Int)`
    - `NamensFeld(wert: String, platzhalterId: Int, fehlerhaft: Boolean, aendern: (String) -> Unit, einzeilig: Boolean = true)`
    - `SymbolRaster(schluessel: List<String>, gewaehlt: String, symbolFuer: (String) -> ImageVector, waehlen: (String) -> Unit)`
    - `data class AuswahlKarte<T>(val wert: T, val titelId: Int, val hinweisId: Int)`
    - `AuswahlKarten(karten: List<AuswahlKarte<T>>, gewaehlt: T, waehlen: (T) -> Unit)`
  - `ui/gemeinsam/DatumDialog.kt`: `DatumDialog(start: LocalDate, bestaetigen: (LocalDate) -> Unit, abbrechen: () -> Unit)`
  - `BelohnungEntwurf` mit `symbol: String`, `preisPunkte: Int`, `bedingungPerioden: Int`, `bedingungDatum: LocalDate?`; die Text-Felder `preisText`, `bedingungPeriodenText` und `bedingungDatumText` entfallen. Dazu `preisAendern(schritt: Int)`, `periodenAendern(schritt: Int)` sowie die Konstanten `PREIS_SCHRITT = 10`, `PREIS_MIN = 10`, `PERIODEN_MIN = 1`.
  - `BelohnungBearbeitenViewModel.speichern(): SpeicherErgebnis?`
  - `BelohnungBearbeitenScreen(id: Long, fertig: (SpeicherErgebnis) -> Unit, zurueck: () -> Unit, viewModel = hiltViewModel())`

**Verhaltensentscheidungen:**
- **Aufbau wie beim Aktivitäten-Editor:**
  - Kopfzeile, Symbolraster, Name, Beschreibung (mehrzeilig) und Preis-Stepper (Schritt 10, Minimum 10).
  - Bedingung als drei Auswahlkarten: Keine, Mindestserie und Frühestens ab.
  - Bei Mindestserie: Aktivitätsauswahl als Chips mit Aktivitätssymbol, dazu ein Perioden-Stepper (Minimum 1).
  - Bei Frühestens ab: Datumsknopf mit Datumswähler.
  - Am Ende der Knopf „Speichern“.
- **Altdaten:** Ein geladener Preis unter 10 bleibt stehen. Der erste Minus-Schritt landet auf 10 (`coerceAtLeast`), ein Plus-Schritt addiert 10.
- **Bestehende Bedingungsfelder:** Sie bleiben beim Wechsel des Bedingungstyps im Entwurf erhalten, aber nur der gewählte Typ wird gespeichert. So ist das Hin- und Herwechseln verlustfrei, wie bei den Uhrzeiten in Plan 5.
- **Speichern** nach dem Plan-5-Muster:
  - Sperre `speichertGerade`, die neue id wird übernommen.
  - `geladeneId` macht `laden()` idempotent.
  - `speicherVersucht` sorgt für die Live-Neuprüfung.
  - Navigation nur in `RESUMED`.
- **Kein Wiederherstellen archivierter Belohnungen:** Ein archivierter Entwurf bleibt archiviert, wie bisher (Rundlauf-Test).

- [ ] **Step 1: Formularbausteine aus dem Aktivitäten-Editor herauslösen (reiner Umzug)**

Die privaten Funktionen `Kopfzeile`, `Abschnitt`, `FehlerText`, `NameFeld`, `SymbolRaster` und `TypKarten`/`TypKarte`/`TYP_KARTEN` werden aus `ui/aktivitaeten/AktivitaetBearbeitenScreen.kt` **entfernt**. An ihre Stelle tritt die öffentliche, verallgemeinerte Fassung in der neuen Datei `ui/gemeinsam/Formularbausteine.kt`. Styling und Kommentare bleiben wie in Plan 5:
- `NameFeld` heißt jetzt `NamensFeld` und bekommt den Platzhalter als Parameter. Mit `einzeilig = false` dient es als Beschreibungsfeld.
- `SymbolRaster` bekommt Katalog und Abbildung als Parameter.
- Aus `TypKarten` wird das generische `AuswahlKarten`. Bei ungerader Kartenzahl hält ein `Spacer` die letzte Karte auf halber Breite.

```kotlin
package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme

/** Zurueck-Pfeil und Titel der Bearbeiten-Bildschirme. */
@Composable
fun Kopfzeile(titel: String, zurueck: () -> Unit) {
    Row(
        modifier = Modifier.offset(x = (-8).dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        IconButton(onClick = zurueck) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.zurueck),
                tint = DayOrganizerTheme.farben.ink,
            )
        }
        Text(titel, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = DayOrganizerTheme.farben.ink)
    }
}

/** Kleiner Abschnittstitel (Prototyp: 13px, 700, ink2) ueber seinem Inhalt. */
@Composable
fun Abschnitt(titelId: Int, inhalt: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(titelId),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = DayOrganizerTheme.farben.ink2,
        )
        inhalt()
    }
}

@Composable
fun FehlerText(sichtbar: Boolean, textId: Int) {
    if (sichtbar) {
        Text(
            stringResource(textId),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = DayOrganizerTheme.farben.coral,
        )
    }
}

/** Texteingabe im Prototyp-Stil. Mit [einzeilig] = false fuer laengere Texte (Beschreibung). */
@Composable
fun NamensFeld(
    wert: String,
    platzhalterId: Int,
    fehlerhaft: Boolean,
    aendern: (String) -> Unit,
    einzeilig: Boolean = true,
) {
    val farben = DayOrganizerTheme.farben
    OutlinedTextField(
        value = wert,
        onValueChange = aendern,
        placeholder = { Text(stringResource(platzhalterId)) },
        singleLine = einzeilig,
        minLines = if (einzeilig) 1 else 2,
        isError = fehlerhaft,
        shape = DayOrganizerFormen.knopf,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = farben.surface,
            unfocusedContainerColor = farben.surface,
            errorContainerColor = farben.surface,
            focusedBorderColor = farben.accent,
            unfocusedBorderColor = farben.line,
            errorBorderColor = farben.coral,
            focusedTextColor = farben.ink,
            unfocusedTextColor = farben.ink,
            errorTextColor = farben.ink,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Raster mit sechs Symbolen je Reihe; [symbolFuer] bildet einen Katalogschluessel ab. */
@Composable
fun SymbolRaster(
    schluessel: List<String>,
    gewaehlt: String,
    symbolFuer: (String) -> ImageVector,
    waehlen: (String) -> Unit,
) {
    val farben = DayOrganizerTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        schluessel.chunked(6).forEach { reihe ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reihe.forEach { eintrag ->
                    val aktiv = eintrag == gewaehlt
                    Surface(
                        selected = aktiv,
                        onClick = { waehlen(eintrag) },
                        modifier = Modifier.weight(1f).aspectRatio(1f),
                        shape = DayOrganizerFormen.knopf,
                        color = if (aktiv) farben.accent else farben.surface,
                        contentColor = if (aktiv) farben.onAccent else farben.ink2,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            // Der Schluessel ist zugleich die Bedienhilfe-Beschreibung: es gibt
                            // keine deutschen Namen fuer die Symbole, und ein leeres
                            // contentDescription machte das Raster fuer TalkBack stumm.
                            Icon(symbolFuer(eintrag), contentDescription = eintrag, modifier = Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}

data class AuswahlKarte<T>(val wert: T, val titelId: Int, val hinweisId: Int)

/** Zwei Karten je Reihe (Prototyp: Zeitplan-Typkarten). */
@Composable
fun <T> AuswahlKarten(karten: List<AuswahlKarte<T>>, gewaehlt: T, waehlen: (T) -> Unit) {
    val farben = DayOrganizerTheme.farben
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        karten.chunked(2).forEach { reihe ->
            // IntrinsicSize.Min + fillMaxHeight: beide Karten einer Reihe gleich hoch, auch
            // wenn ein Hinweistext bei grosser Schrift umbricht.
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                reihe.forEach { karte ->
                    val aktiv = karte.wert == gewaehlt
                    Surface(
                        selected = aktiv,
                        onClick = { waehlen(karte.wert) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        shape = DayOrganizerFormen.knopf,
                        color = if (aktiv) farben.accentSoft else farben.surface,
                        border = BorderStroke(2.dp, if (aktiv) farben.accent else Color.Transparent),
                    ) {
                        Column(
                            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                stringResource(karte.titelId),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (aktiv) farben.onAccentSoft else farben.ink,
                            )
                            Text(stringResource(karte.hinweisId), fontSize = 12.sp, color = farben.ink2)
                        }
                    }
                }
                // Eine allein stehende letzte Karte behaelt halbe Breite statt die Reihe zu fuellen.
                if (reihe.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
```

`AktivitaetBearbeitenScreen.kt` nutzt danach die gemeinsamen Funktionen:
- `NamensFeld(entwurf.name, R.string.name_platzhalter, …)`
- `SymbolRaster(AKTIVITAET_SYMBOLE, entwurf.symbol, ::aktivitaetSymbol) { … }`
- `AuswahlKarten(TYP_KARTEN, entwurf.typ) { … }` mit `TYP_KARTEN: List<AuswahlKarte<ZeitplanTyp>>`

Nicht mehr benötigte Imports dort entfernen.

String-Keys der Dialogknöpfe verallgemeinern:
- In `strings.xml` werden `uhrzeit_uebernehmen` → `dialog_uebernehmen` und `uhrzeit_abbrechen` → `dialog_abbrechen` umbenannt; die Texte bleiben.
- `UhrzeitDialog.kt` wird entsprechend angepasst.

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL. Das Verhalten des Aktivitäten-Editors bleibt unverändert, `AktivitaetBearbeitenViewModelTest` und `AktivitaetEntwurfTest` sind unberührt grün.

Commit dieses Zwischenstands:

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui app/src/main/res/values/strings.xml
git commit -m "refactor(ui): Formularbausteine des Aktivitaeten-Editors nach ui/gemeinsam

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

- [ ] **Step 2: Entwurf-Tests auf das neue Modell (failing)**

`BelohnungEntwurfTest.kt` vollständig ersetzen:

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BelohnungEntwurfTest {

    private val gueltig = BelohnungEntwurf(name = "Kinoabend", preisPunkte = 200)

    @Test
    fun `ein vollstaendiger Entwurf hat keine Fehler`() {
        assertEquals(emptyList(), gueltig.pruefen())
    }

    @Test
    fun `ein neuer Entwurf ist bis auf den Namen gueltig und hat das Standardsymbol`() {
        val neu = BelohnungEntwurf()

        assertEquals(listOf(BelohnungFehler.NAME_LEER), neu.pruefen())
        assertEquals("redeem", neu.symbol)
        assertEquals(100, neu.preisPunkte)
    }

    @Test
    fun `leerer Name und nicht positiver Preis werden gemeldet`() {
        val fehler = gueltig.copy(name = " ", preisPunkte = 0).pruefen()

        assertTrue(BelohnungFehler.NAME_LEER in fehler)
        assertTrue(BelohnungFehler.PREIS_NICHT_POSITIV in fehler)
    }

    @Test
    fun `eine Serienbedingung ohne Aktivitaet wird gemeldet`() {
        val fehler = gueltig.copy(bedingungTyp = BedingungTyp.MINDESTSERIE, bedingungAktivitaetId = null).pruefen()

        assertTrue(BelohnungFehler.SERIE_OHNE_AKTIVITAET in fehler)
    }

    @Test
    fun `eine Serienbedingung mit Periode null wird gemeldet`() {
        val fehler = gueltig.copy(
            bedingungTyp = BedingungTyp.MINDESTSERIE,
            bedingungAktivitaetId = 7,
            bedingungPerioden = 0,
        ).pruefen()

        assertTrue(BelohnungFehler.SERIE_NICHT_POSITIV in fehler)
    }

    @Test
    fun `eine Datumsbedingung ohne Datum wird gemeldet, mit Datum nicht`() {
        val ohne = gueltig.copy(bedingungTyp = BedingungTyp.FRUEHESTENS_AB, bedingungDatum = null)
        assertEquals(listOf(BelohnungFehler.DATUM_FEHLT), ohne.pruefen())

        val mit = ohne.copy(bedingungDatum = LocalDate.of(2026, 4, 1))
        assertEquals(emptyList(), mit.pruefen())
    }

    @Test
    fun `der Preis aendert sich in Zehnerschritten und bleibt mindestens 10`() {
        assertEquals(210, gueltig.preisAendern(+1).preisPunkte)
        assertEquals(190, gueltig.preisAendern(-1).preisPunkte)
        assertEquals(10, gueltig.copy(preisPunkte = 10).preisAendern(-1).preisPunkte)
    }

    @Test
    fun `ein geladener Altpreis unter 10 faellt beim Verringern auf 10 und waechst beim Erhoehen um 10`() {
        assertEquals(10, gueltig.copy(preisPunkte = 5).preisAendern(-1).preisPunkte)
        assertEquals(15, gueltig.copy(preisPunkte = 5).preisAendern(+1).preisPunkte)
    }

    @Test
    fun `die Perioden bleiben mindestens 1`() {
        assertEquals(1, gueltig.copy(bedingungPerioden = 1).periodenAendern(-1).bedingungPerioden)
        assertEquals(8, gueltig.copy(bedingungPerioden = 7).periodenAendern(+1).bedingungPerioden)
    }

    @Test
    fun `nur die gewaehlte Bedingung wird gespeichert, die anderen Felder bleiben im Entwurf`() {
        val entwurf = gueltig.copy(
            bedingungTyp = BedingungTyp.KEINE,
            bedingungAktivitaetId = 7,
            bedingungPerioden = 14,
            bedingungDatum = LocalDate.of(2026, 4, 1),
        )

        assertEquals(null, entwurf.zuBelohnung().bedingung)
        assertEquals(
            Belohnungsbedingung.MindestSerie(7, 14),
            entwurf.copy(bedingungTyp = BedingungTyp.MINDESTSERIE).zuBelohnung().bedingung,
        )
        assertEquals(
            Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)),
            entwurf.copy(bedingungTyp = BedingungTyp.FRUEHESTENS_AB).zuBelohnung().bedingung,
        )
    }

    @Test
    fun `der Rundlauf ueber aus und zuBelohnung liefert die urspruengliche Belohnung`() {
        val ohneBedingung = Belohnung(
            id = 5, name = "Kinoabend", beschreibung = "Popcorn", preisPunkte = 200, symbol = "movie",
        )
        assertEquals(ohneBedingung, BelohnungEntwurf.aus(ohneBedingung).zuBelohnung())

        val mitSerie = ohneBedingung.copy(bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14))
        assertEquals(mitSerie, BelohnungEntwurf.aus(mitSerie).zuBelohnung())

        val mitDatum = ohneBedingung.copy(bedingung = Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)))
        assertEquals(mitDatum, BelohnungEntwurf.aus(mitDatum).zuBelohnung())
    }

    @Test
    fun `eine archivierte Belohnung bleibt nach dem Rundlauf archiviert`() {
        val archiviert = Belohnung(id = 5, name = "Kinoabend", beschreibung = "Popcorn", preisPunkte = 200, archiviert = true)

        val ergebnis = BelohnungEntwurf.aus(archiviert).zuBelohnung()

        assertTrue(ergebnis.archiviert)
        assertEquals(archiviert, ergebnis)
    }
}
```

Neue Datei `BelohnungBearbeitenViewModelTest.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeBelohnungRepository
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BelohnungBearbeitenViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private val kino = Belohnung(id = 1, name = "Kinoabend", preisPunkte = 200, symbol = "movie")

    /** Verzoegert speichern(), damit sich zwei Aufrufe ueberlappen koennen -- ohne Suspension
     * liefe der erste komplett durch, bevor der zweite startet. */
    private class LangsamesRepository(private val delegate: FakeBelohnungRepository) :
        de.vanhake.dayorganizer.data.repository.BelohnungRepository by delegate {
        override suspend fun speichern(belohnung: Belohnung): Long {
            delay(100)
            return delegate.speichern(belohnung)
        }
    }

    private fun viewModel(belohnungen: de.vanhake.dayorganizer.data.repository.BelohnungRepository = FakeBelohnungRepository()) =
        BelohnungBearbeitenViewModel(belohnungen, FakeAktivitaetRepository())

    @Test
    fun `eine neue gueltige Belohnung wird angelegt`() = runTest(dispatcher) {
        val repo = FakeBelohnungRepository()
        val vm = viewModel(repo)
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Eis", symbol = "icecream") }

        assertEquals(SpeicherErgebnis.ANGELEGT, vm.speichern())
        assertEquals(listOf("Eis" to "icecream"), repo.stand.value.map { it.name to it.symbol })
    }

    @Test
    fun `eine bestehende Belohnung meldet geaendert und behaelt ihr Symbol`() = runTest(dispatcher) {
        val repo = FakeBelohnungRepository(listOf(kino))
        val vm = viewModel(repo)
        vm.laden(1L).join()
        vm.aendern { it.preisAendern(+1) }

        assertEquals(SpeicherErgebnis.GEAENDERT, vm.speichern())
        assertEquals(kino.copy(preisPunkte = 210), repo.stand.value.single())
    }

    @Test
    fun `ein fehlerhafter Entwurf wird nicht gespeichert`() = runTest(dispatcher) {
        val repo = FakeBelohnungRepository()
        val vm = viewModel(repo)
        vm.laden(0L).join()

        assertNull(vm.speichern())
        assertEquals(listOf(BelohnungFehler.NAME_LEER), vm.fehler.value)
        assertEquals(emptyList(), repo.stand.value)
    }

    @Test
    fun `Fehler bleiben bis zum ersten Speicherversuch stumm und werden danach live geprueft`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.laden(0L).join()
        vm.aendern { it.copy(preisPunkte = 0) }
        assertEquals(emptyList(), vm.fehler.value)

        vm.speichern()
        vm.aendern { it.copy(name = "Eis", preisPunkte = 50) }
        assertEquals(emptyList(), vm.fehler.value)

        vm.aendern { it.copy(name = "") }
        assertEquals(listOf(BelohnungFehler.NAME_LEER), vm.fehler.value)
    }

    @Test
    fun `ein zweites laden ueberschreibt ungespeicherte Aenderungen nicht`() = runTest(dispatcher) {
        val vm = viewModel(FakeBelohnungRepository(listOf(kino)))
        vm.laden(1L).join()
        vm.aendern { it.copy(name = "Neu") }

        vm.laden(1L).join()

        assertEquals("Neu", vm.entwurf.value.name)
    }

    @Test
    fun `zwei ueberlappende Speichervorgaenge legen nur eine Belohnung an`() = runTest(dispatcher) {
        val repo = FakeBelohnungRepository()
        val vm = viewModel(LangsamesRepository(repo))
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Eis") }

        val ergebnisse = listOf(async { vm.speichern() }, async { vm.speichern() }).awaitAll()

        assertEquals(1, repo.stand.value.size)
        assertEquals(setOf(SpeicherErgebnis.ANGELEGT, null), ergebnisse.toSet())
    }

    @Test
    fun `ein spaeter zweiter Speichern-Tipp aktualisiert statt neu anzulegen`() = runTest(dispatcher) {
        val repo = FakeBelohnungRepository()
        val vm = viewModel(repo)
        vm.laden(0L).join()
        vm.aendern { it.copy(name = "Eis") }

        assertEquals(SpeicherErgebnis.ANGELEGT, vm.speichern())
        assertEquals(SpeicherErgebnis.GEAENDERT, vm.speichern())
        assertEquals(1, repo.stand.value.size)
    }
}
```

- [ ] **Step 3: Fehlschlag sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.ui.belohnungen.*'`
Expected: FAIL beim Kompilieren (`preisPunkte`, `symbol`, `preisAendern`, `speichern(): SpeicherErgebnis?` …).

- [ ] **Step 4: `BelohnungEntwurf.kt` vollständig ersetzen**

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.ui.theme.BELOHNUNG_SYMBOL_STANDARD
import java.time.LocalDate

enum class BedingungTyp { KEINE, MINDESTSERIE, FRUEHESTENS_AB }

enum class BelohnungFehler {
    NAME_LEER,
    PREIS_NICHT_POSITIV,
    SERIE_OHNE_AKTIVITAET,
    SERIE_NICHT_POSITIV,
    DATUM_FEHLT,
}

/**
 * Bearbeitbarer Zwischenstand. Seit dem Redesign (Plan 6) kommen Preis und Perioden aus
 * Steppern und das Datum aus einem Datumswaehler -- darum sind diese Felder typisiert,
 * wie bei AktivitaetEntwurf seit Plan 5. Die Bedingungsfelder bleiben beim Wechsel des
 * Bedingungstyps erhalten; gespeichert wird nur der gewaehlte Typ.
 */
data class BelohnungEntwurf(
    val id: Long = 0,
    val name: String = "",
    val beschreibung: String = "",
    val symbol: String = BELOHNUNG_SYMBOL_STANDARD,
    val preisPunkte: Int = 100,
    val bedingungTyp: BedingungTyp = BedingungTyp.KEINE,
    val bedingungAktivitaetId: Long? = null,
    val bedingungPerioden: Int = 7,
    val bedingungDatum: LocalDate? = null,
    val archiviert: Boolean = false,
) {

    /** Zehnerschritte; ein geladener Altpreis unter 10 bleibt stehen, bis der Stepper
     * bedient wird. */
    fun preisAendern(schritt: Int): BelohnungEntwurf =
        copy(preisPunkte = (preisPunkte + schritt * PREIS_SCHRITT).coerceAtLeast(PREIS_MIN))

    fun periodenAendern(schritt: Int): BelohnungEntwurf =
        copy(bedingungPerioden = (bedingungPerioden + schritt).coerceAtLeast(PERIODEN_MIN))

    fun pruefen(): List<BelohnungFehler> = buildList {
        if (name.isBlank()) add(BelohnungFehler.NAME_LEER)
        if (preisPunkte <= 0) add(BelohnungFehler.PREIS_NICHT_POSITIV)
        if (bedingungTyp == BedingungTyp.MINDESTSERIE) {
            if (bedingungAktivitaetId == null) add(BelohnungFehler.SERIE_OHNE_AKTIVITAET)
            if (bedingungPerioden <= 0) add(BelohnungFehler.SERIE_NICHT_POSITIV)
        }
        if (bedingungTyp == BedingungTyp.FRUEHESTENS_AB && bedingungDatum == null) {
            add(BelohnungFehler.DATUM_FEHLT)
        }
    }

    /** Nur aufrufen, wenn [pruefen] leer ist. */
    fun zuBelohnung(): Belohnung = Belohnung(
        id = id,
        name = name.trim(),
        beschreibung = beschreibung.trim(),
        preisPunkte = preisPunkte,
        bedingung = when (bedingungTyp) {
            BedingungTyp.KEINE -> null
            BedingungTyp.MINDESTSERIE -> Belohnungsbedingung.MindestSerie(
                aktivitaetId = requireNotNull(bedingungAktivitaetId),
                perioden = bedingungPerioden,
            )
            BedingungTyp.FRUEHESTENS_AB -> Belohnungsbedingung.FruehestensAb(requireNotNull(bedingungDatum))
        },
        archiviert = archiviert,
        symbol = symbol,
    )

    companion object {
        const val PREIS_SCHRITT = 10
        const val PREIS_MIN = 10
        const val PERIODEN_MIN = 1

        fun aus(belohnung: Belohnung): BelohnungEntwurf {
            val basis = BelohnungEntwurf(
                id = belohnung.id,
                name = belohnung.name,
                beschreibung = belohnung.beschreibung,
                symbol = belohnung.symbol,
                preisPunkte = belohnung.preisPunkte,
                archiviert = belohnung.archiviert,
            )
            return when (val b = belohnung.bedingung) {
                null -> basis
                is Belohnungsbedingung.MindestSerie -> basis.copy(
                    bedingungTyp = BedingungTyp.MINDESTSERIE,
                    bedingungAktivitaetId = b.aktivitaetId,
                    bedingungPerioden = b.perioden,
                )
                is Belohnungsbedingung.FruehestensAb -> basis.copy(
                    bedingungTyp = BedingungTyp.FRUEHESTENS_AB,
                    bedingungDatum = b.datum,
                )
            }
        }
    }
}
```

- [ ] **Step 5: `BelohnungBearbeitenViewModel.kt` vollständig ersetzen**

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Speichern nach dem in Plan 5 fuer AktivitaetBearbeitenViewModel abgesicherten Muster. */
@HiltViewModel
class BelohnungBearbeitenViewModel @Inject constructor(
    private val belohnungen: BelohnungRepository,
    private val aktivitaeten: AktivitaetRepository,
) : ViewModel() {

    private val _entwurf = MutableStateFlow(BelohnungEntwurf())
    val entwurf: StateFlow<BelohnungEntwurf> = _entwurf.asStateFlow()

    private val _fehler = MutableStateFlow<List<BelohnungFehler>>(emptyList())
    val fehler: StateFlow<List<BelohnungFehler>> = _fehler.asStateFlow()

    private val _auswahl = MutableStateFlow<List<Aktivitaet>>(emptyList())
    val auswahl: StateFlow<List<Aktivitaet>> = _auswahl.asStateFlow()

    private var geladeneId: Long? = null

    // Ab dem ersten Speicherversuch prueft aendern() bei jeder Aenderung neu.
    private var speicherVersucht = false

    // Sperre gegen ueberlappendes Speichern (Doppeltipp); synchron gesetzt, in finally frei.
    private var speichertGerade = false

    fun laden(id: Long) = viewModelScope.launch {
        // Nach einer Rotation startet der Screen laden() erneut; ohne diese Pruefung
        // ueberschriebe der DB-Stand den noch ungespeicherten Entwurf.
        if (geladeneId == id) return@launch
        geladeneId = id
        _auswahl.value = aktivitaeten.alleAktiven()
        if (id == 0L) return@launch
        belohnungen.nachId(id)?.let { _entwurf.value = BelohnungEntwurf.aus(it) }
    }

    fun aendern(block: (BelohnungEntwurf) -> BelohnungEntwurf) {
        _entwurf.value = block(_entwurf.value)
        if (speicherVersucht) _fehler.value = _entwurf.value.pruefen()
    }

    /** Gibt null zurueck, wenn der Entwurf Fehler hat oder schon ein Speichern laeuft. */
    suspend fun speichern(): SpeicherErgebnis? {
        if (speichertGerade) return null
        speichertGerade = true
        try {
            speicherVersucht = true
            val fehler = _entwurf.value.pruefen()
            _fehler.value = fehler
            if (fehler.isNotEmpty()) return null

            val ergebnis = if (_entwurf.value.id == 0L) SpeicherErgebnis.ANGELEGT else SpeicherErgebnis.GEAENDERT
            val id = belohnungen.speichern(_entwurf.value.zuBelohnung())
            // Die neue id uebernehmen: waehrend der Ausblend-Animation ist der Screen noch
            // klickbar; ein spaeter Tipp wird so zum harmlosen Update statt zu einem Insert.
            _entwurf.value = _entwurf.value.copy(id = id)
            return ergebnis
        } finally {
            speichertGerade = false
        }
    }
}
```

`laden()` liest `alleAktiven()` jetzt nur beim ersten Laden. Die Aktivitätsauswahl ändert sich während des Bearbeitens nicht.

- [ ] **Step 6: Tests grün sehen**

Run: `./gradlew :app:testDebugUnitTest --tests 'de.vanhake.dayorganizer.ui.belohnungen.*'`
Expected: Kompilierfehler nur noch im alten `BelohnungBearbeitenScreen.kt`. Weiter mit Step 7, danach sind die Tests grün.

- [ ] **Step 7: `DatumDialog.kt` und Strings**

```kotlin
package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import de.vanhake.dayorganizer.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Datumswaehler. Der Material-DatePicker rechnet in UTC-Millisekunden um Mitternacht --
 * Umrechnung darum bewusst ueber ZoneOffset.UTC, sonst verschoebe eine Zeitzone westlich
 * von UTC das Datum um einen Tag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatumDialog(start: LocalDate, bestaetigen: (LocalDate) -> Unit, abbrechen: () -> Unit) {
    val zustand = rememberDatePickerState(
        initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = abbrechen,
        confirmButton = {
            TextButton(
                onClick = {
                    zustand.selectedDateMillis?.let { millis ->
                        bestaetigen(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                },
                enabled = zustand.selectedDateMillis != null,
            ) { Text(stringResource(R.string.dialog_uebernehmen)) }
        },
        dismissButton = {
            TextButton(onClick = abbrechen) { Text(stringResource(R.string.dialog_abbrechen)) }
        },
    ) {
        DatePicker(state = zustand)
    }
}
```

`strings.xml`:
- `feld_bedingung_datum` (enthält den Hinweis „im Format JJJJ-MM-TT“) wird ersetzt durch `feld_datum` = „Ab welchem Datum“.
- `bedingung_datum_fehlt` = „Bitte ein Datum wählen“.
- `feld_preis` (ist „Preis in Punkten“) bleibt.
- Neu:

```xml
    <string name="belohnung_bearbeiten_titel">Belohnung bearbeiten</string>
    <string name="belohnung_name_platzhalter">z. B. Kinoabend</string>
    <string name="beschreibung_platzhalter">optional, z. B. mit Popcorn</string>
    <string name="bedingung_keine_hinweis">jederzeit einlösbar</string>
    <string name="bedingung_serie_hinweis">Serie einer Aktivität</string>
    <string name="bedingung_datum_hinweis">ab einem Datum</string>
    <string name="datum_waehlen">Datum wählen</string>
    <string name="fehler_preis">Mindestens 1 Punkt</string>
    <string name="fehler_serie_aktivitaet">Bitte eine Aktivität wählen</string>
    <string name="fehler_serie_perioden">Mindestens 1 Periode</string>
    <string name="keine_aktivitaeten_fuer_serie">Lege zuerst eine Aktivität an, um eine Serie zu verlangen.</string>
```

`grep -rn "feld_bedingung_datum\|bedingung_datum_fehlt" app/src` zeigt alle Nutzer. Nach Step 8 darf nur noch `bedingung_datum_fehlt` im neuen Screen vorkommen.

- [ ] **Step 8: `BelohnungBearbeitenScreen.kt` vollständig ersetzen**

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.ui.gemeinsam.Abschnitt
import de.vanhake.dayorganizer.ui.gemeinsam.AuswahlKarte
import de.vanhake.dayorganizer.ui.gemeinsam.AuswahlKarten
import de.vanhake.dayorganizer.ui.gemeinsam.DatumDialog
import de.vanhake.dayorganizer.ui.gemeinsam.FehlerText
import de.vanhake.dayorganizer.ui.gemeinsam.Kopfzeile
import de.vanhake.dayorganizer.ui.gemeinsam.NamensFeld
import de.vanhake.dayorganizer.ui.gemeinsam.SpeicherErgebnis
import de.vanhake.dayorganizer.ui.gemeinsam.Stepper
import de.vanhake.dayorganizer.ui.gemeinsam.StepperZeile
import de.vanhake.dayorganizer.ui.gemeinsam.SymbolRaster
import de.vanhake.dayorganizer.ui.theme.BELOHNUNG_SYMBOLE
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFormen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerTheme
import de.vanhake.dayorganizer.ui.theme.aktivitaetSymbol
import de.vanhake.dayorganizer.ui.theme.belohnungSymbol
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATUM_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

private val BEDINGUNG_KARTEN = listOf(
    AuswahlKarte(BedingungTyp.KEINE, R.string.bedingung_keine, R.string.bedingung_keine_hinweis),
    AuswahlKarte(BedingungTyp.MINDESTSERIE, R.string.bedingung_serie, R.string.bedingung_serie_hinweis),
    AuswahlKarte(BedingungTyp.FRUEHESTENS_AB, R.string.bedingung_datum, R.string.bedingung_datum_hinweis),
)

@Composable
fun BelohnungBearbeitenScreen(
    id: Long,
    fertig: (SpeicherErgebnis) -> Unit,
    zurueck: () -> Unit,
    viewModel: BelohnungBearbeitenViewModel = hiltViewModel(),
) {
    val entwurf by viewModel.entwurf.collectAsStateWithLifecycle()
    val fehler by viewModel.fehler.collectAsStateWithLifecycle()
    val auswahl by viewModel.auswahl.collectAsStateWithLifecycle()
    val bereich = rememberCoroutineScope()
    val farben = DayOrganizerTheme.farben
    var datumOffen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(id) { viewModel.laden(id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(farben.bg)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Kopfzeile(
            titel = stringResource(if (id == 0L) R.string.neue_belohnung else R.string.belohnung_bearbeiten_titel),
            zurueck = zurueck,
        )

        Abschnitt(R.string.feld_symbol) {
            SymbolRaster(BELOHNUNG_SYMBOLE, entwurf.symbol, ::belohnungSymbol) { neu ->
                viewModel.aendern { it.copy(symbol = neu) }
            }
        }

        Abschnitt(R.string.feld_name) {
            NamensFeld(
                wert = entwurf.name,
                platzhalterId = R.string.belohnung_name_platzhalter,
                fehlerhaft = BelohnungFehler.NAME_LEER in fehler,
                aendern = { neu -> viewModel.aendern { it.copy(name = neu) } },
            )
            FehlerText(BelohnungFehler.NAME_LEER in fehler, R.string.fehler_name_leer)
        }

        Abschnitt(R.string.feld_beschreibung) {
            NamensFeld(
                wert = entwurf.beschreibung,
                platzhalterId = R.string.beschreibung_platzhalter,
                fehlerhaft = false,
                aendern = { neu -> viewModel.aendern { it.copy(beschreibung = neu) } },
                einzeilig = false,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StepperZeile(titel = stringResource(R.string.feld_preis)) {
                Stepper(
                    wert = entwurf.preisPunkte,
                    verringern = { viewModel.aendern { it.preisAendern(-1) } },
                    erhoehen = { viewModel.aendern { it.preisAendern(+1) } },
                    verringernMoeglich = entwurf.preisPunkte > BelohnungEntwurf.PREIS_MIN,
                )
            }
            FehlerText(BelohnungFehler.PREIS_NICHT_POSITIV in fehler, R.string.fehler_preis)
        }

        Abschnitt(R.string.feld_bedingung) {
            AuswahlKarten(BEDINGUNG_KARTEN, entwurf.bedingungTyp) { typ ->
                viewModel.aendern { it.copy(bedingungTyp = typ) }
            }
        }

        when (entwurf.bedingungTyp) {
            BedingungTyp.KEINE -> Unit

            BedingungTyp.MINDESTSERIE -> {
                Abschnitt(R.string.feld_bedingung_aktivitaet) {
                    if (auswahl.isEmpty()) {
                        Text(stringResource(R.string.keine_aktivitaeten_fuer_serie), fontSize = 14.sp, color = farben.ink2)
                    } else {
                        AktivitaetsChips(auswahl, entwurf.bedingungAktivitaetId) { gewaehlt ->
                            viewModel.aendern { it.copy(bedingungAktivitaetId = gewaehlt) }
                        }
                    }
                    FehlerText(BelohnungFehler.SERIE_OHNE_AKTIVITAET in fehler, R.string.fehler_serie_aktivitaet)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepperZeile(titel = stringResource(R.string.feld_bedingung_perioden)) {
                        Stepper(
                            wert = entwurf.bedingungPerioden,
                            verringern = { viewModel.aendern { it.periodenAendern(-1) } },
                            erhoehen = { viewModel.aendern { it.periodenAendern(+1) } },
                            verringernMoeglich = entwurf.bedingungPerioden > BelohnungEntwurf.PERIODEN_MIN,
                        )
                    }
                    FehlerText(BelohnungFehler.SERIE_NICHT_POSITIV in fehler, R.string.fehler_serie_perioden)
                }
            }

            BedingungTyp.FRUEHESTENS_AB -> Abschnitt(R.string.feld_datum) {
                Surface(
                    onClick = { datumOffen = true },
                    shape = DayOrganizerFormen.feld,
                    color = farben.surface,
                    contentColor = farben.ink,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Event, contentDescription = null, tint = farben.ink2)
                        Text(
                            entwurf.bedingungDatum?.format(DATUM_FORMAT) ?: stringResource(R.string.datum_waehlen),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                FehlerText(BelohnungFehler.DATUM_FEHLT in fehler, R.string.bedingung_datum_fehlt)
            }
        }

        Button(
            onClick = { bereich.launch { viewModel.speichern()?.let(fertig) } },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = DayOrganizerFormen.feld,
            colors = ButtonDefaults.buttonColors(containerColor = farben.accent, contentColor = farben.onAccent),
        ) {
            Text(stringResource(R.string.speichern), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
        }
    }

    if (datumOffen) {
        DatumDialog(
            start = entwurf.bedingungDatum ?: LocalDate.now(),
            bestaetigen = { datum ->
                viewModel.aendern { it.copy(bedingungDatum = datum) }
                datumOffen = false
            },
            abbrechen = { datumOffen = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AktivitaetsChips(aktivitaeten: List<Aktivitaet>, gewaehlt: Long?, waehlen: (Long) -> Unit) {
    val farben = DayOrganizerTheme.farben
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        aktivitaeten.forEach { aktivitaet ->
            val aktiv = aktivitaet.id == gewaehlt
            Surface(
                selected = aktiv,
                onClick = { waehlen(aktivitaet.id) },
                shape = DayOrganizerFormen.chip,
                color = if (aktiv) farben.accentSoft else farben.surface,
                contentColor = if (aktiv) farben.onAccentSoft else farben.ink,
                border = BorderStroke(2.dp, if (aktiv) farben.accent else Color.Transparent),
            ) {
                Row(
                    modifier = Modifier.height(40.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(aktivitaetSymbol(aktivitaet.symbol), contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(aktivitaet.name, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
```

Hinweise:
- `LocalDate.now()` im Dialog ist nur der Startpunkt der Auswahl, kein fachlicher Wert. Eine injizierte Uhr ist hier unnötig.
- `Color.Transparent` am Rahmen folgt dem Muster der `AuswahlKarten` aus Plan 5. Der Rahmen reserviert so seinen Platz, damit der Chip beim Auswählen nicht springt.

- [ ] **Step 9: Navigation**

In `Navigation.kt` den `BELOHNUNG_BEARBEITEN`-Eintrag ersetzen, nach dem Muster des Aktivitäten-Editors:

```kotlin
            composable(Ziele.BELOHNUNG_BEARBEITEN) { eintrag ->
                BelohnungBearbeitenScreen(
                    id = eintrag.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    // Nur im RESUMED-Zustand navigieren: ein zweiter Aufruf waehrend der
                    // Ausblend-Animation riefe sonst popBackStack() erneut auf und
                    // wuerfe auch die Liste vom Stapel.
                    fertig = { ergebnis ->
                        if (eintrag.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            navController.previousBackStackEntry?.savedStateHandle
                                ?.set(Ziele.ERGEBNIS_BELOHNUNG_GESPEICHERT, ergebnis.name)
                            navController.popBackStack()
                        }
                    },
                    zurueck = {
                        if (eintrag.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                            navController.popBackStack()
                        }
                    },
                )
            }
```

- [ ] **Step 10: Tests und Build**

Run: `./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL. `BelohnungEntwurfTest` (12) und `BelohnungBearbeitenViewModelTest` (7) sind grün, alle übrigen Tests ebenfalls.

Run: `grep -rn "preisText\|bedingungPeriodenText\|bedingungDatumText\|feld_bedingung_datum\|uhrzeit_uebernehmen\|uhrzeit_abbrechen" app/src`
Expected: keine Treffer.

- [ ] **Step 11: Sichtprüfung im Emulator (manuell)**

Nur mit eigenen Testdaten, siehe Global Constraints:
1. „Neue Belohnung“ öffnen: Symbolraster (2×6), Name, Beschreibung, Preis-Stepper (100, ±10) und drei Bedingungskarten (die dritte in halber Breite).
2. Name leer lassen und Speichern tippen: Hinweis erscheint. Namen tippen: der Hinweis verschwindet sofort.
3. „Mindestserie“: Aktivitäts-Chips mit Symbol und Perioden-Stepper. „Frühestens ab“: Datumsknopf öffnet den Datumswähler, das gewählte Datum steht als `dd.MM.yyyy` da.
4. Speichern: zurück in der Liste, Toast „Belohnung angelegt“, die Karte zeigt das gewählte Symbol.
5. Karte antippen → „Belohnung bearbeiten“ mit geladenen Werten. Gerät mit offener Änderung drehen: die Änderung bleibt.
6. Schneller Doppeltipp auf Speichern bei einer neuen Belohnung: genau eine neue Karte, man landet in der Liste.
7. Hell- und Dunkelmodus.
8. Den Aktivitäten-Editor kurz öffnen (nur ansehen, nicht speichern): er sieht unverändert aus wie nach Plan 5.

Am Ende die eigenen Testbelohnungen archivieren, damit der Emulator-Bestand sauber bleibt.

- [ ] **Step 12: Commit**

```bash
git add app/src/main/kotlin/de/vanhake/dayorganizer/ui \
        app/src/main/res/values/strings.xml \
        app/src/test/kotlin/de/vanhake/dayorganizer/ui/belohnungen
git commit -m "feat(belohnungen): Editor im neuen Design mit Symbol, Steppern, Datumswaehler und abgesichertem Speichern

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

---

## Abschluss dieses Plans

Nach Task 6 ist der Belohnungen-Tab auf dem Stand des Prototyps, mit diesen Ergänzungen und Abweichungen:
- Rückgängig beim Einlösen als Storno plus Gegenbuchung.
- Kein Rückgängig beim Archivieren.
- Ein Editor, den der Prototyp nicht zeigt, analog zum Aktivitäten-Editor.
- Ein FAB „Neue Belohnung“, den der Prototyp nicht hat.
- Ein leerer Zustand.

Datenbank (Version 5) und Backup (Schema 3) sind erweitert. Backups mit Schema 2 bleiben lesbar.

Offen für die Folgepläne:

- **Plan 7: Statistik + Optionen + Navigationsleiste.**
  - Optionen: Tagesbonus und Kulanzfenster als Stepper in 5er-Schritten, die **sofort speichern**. Der Knopf „Speichern“ entfällt (Entscheidung bei der Abstimmung zu Plan 6).
  - Der segmentierte Hell/Dunkel-Schalter ersetzt den provisorischen aus Plan 4. Dazu kommen Backup-Karten und ein Import-Dialog im Prototyp-Stil (`dialog`-Block).
  - Statistik: Zeitraum-Pillen, Heatmap- und Verlaufskarten und Aktivitätszeilen mit Quote, Balken und Serie (`sStat`).
  - Untere Navigationsleiste im Prototyp-Stil: Pillen-Indikator 60×32 und gefüllte Symbole im aktiven Tab (`check_circle`, `format_list_bulleted`, `redeem`, `insights`, `tune`). Der Tab „Optionen“ heißt im Prototyp so, in der App bisher „Einstellungen“ bzw. hat das Zahnrad-Symbol.
- **Plan 8:** Homescreen-Widget im neuen Design (Glance-Einschränkungen: kein CSS-Farbverlauf, kein `backdrop-filter`).
- **Redaktionell:** `README.md` (Stand, Dokumentationstabelle) und `docs/HANDOFF.md` auf den Stand von Plan 4–6 bringen. Die HANDOFF steht noch auf Plan 3.
