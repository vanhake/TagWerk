# DayOrganizer — Plan 2: Belohnungen, Statistik und Einstellungen

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Voraussetzung:** Plan 1 (`2026-09-18-dayorganizer-01-fundament.md`) ist vollständig umgesetzt.

**Goal:** Die gesammelten Punkte bekommen ein Ziel — ein Belohnungs-Shop mit Preis und optionaler Bedingung — und der Fortschritt wird sichtbar ausgewertet.

**Architecture:** Die Prüfung, ob eine Belohnung einlösbar ist, wandert als reine Funktion in `:domain` neben die drei bestehenden Rechner. Einlösen ist eine negative Buchung im vorhandenen Journal; ein separater Punktezähler entsteht nicht. Die Statistik liest ausschließlich aus Journal und Fälligkeitshistorie und braucht daher keine eigenen Aggregattabellen.

**Tech Stack:** wie Plan 1.

## Global Constraints

- Alle Constraints aus Plan 1 gelten unverändert weiter, insbesondere: `:domain` ohne Android-Abhängigkeit, `java.time.Clock` statt `now()`, deutsche Texte in `strings.xml`.
- Der Kontostand bleibt die Summe des Buchungsjournals. Eine Einlösung ist eine `EINLOESUNG`-Buchung mit negativem Betrag — nie ein direktes Herabsetzen eines Zählers.
- Eine Einlösung hält Name und Preis als Momentaufnahme fest, damit die Historie lesbar bleibt, wenn die Belohnung später umbenannt oder archiviert wird.
- Datenbankversion steigt in diesem Plan von 2 auf 3. Die Migration wird mit einem Test abgesichert.
- Es gibt kein Gerät und keinen Emulator. Datenbanktests laufen unter Robolectric als JVM-Tests im `test`-Quellverzeichnis.

## File Structure

```
domain/src/main/kotlin/de/vanhake/dayorganizer/domain/
    model/Belohnung.kt            Belohnung, Belohnungsbedingung, Einloesung
    rechner/BelohnungsPruefer.kt  Einloesbarkeit einer Belohnung
    rechner/StatistikRechner.kt   Quoten und Tagesbilanz aus der Historie

app/src/main/kotlin/de/vanhake/dayorganizer/
    data/db/BelohnungEntity.kt, EinloesungEntity.kt, BelohnungDao.kt, EinloesungDao.kt
    data/db/Migrationen.kt        MIGRATION_2_3 ergaenzen
    data/repository/BelohnungRepository.kt
    domainuse/BelohnungEinloesen.kt
    ui/belohnungen/               BelohnungenViewModel, BelohnungenScreen, BelohnungBearbeitenScreen
    ui/statistik/                 StatistikViewModel, StatistikScreen, Heatmap
    ui/einstellungen/             EinstellungenViewModel, EinstellungenScreen
```

---

### Task 1: Belohnungsmodell und Einlösbarkeit

**Files:**
- Create: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/model/Belohnung.kt`
- Create: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/rechner/BelohnungsPruefer.kt`
- Test: `domain/src/test/kotlin/de/vanhake/dayorganizer/domain/rechner/BelohnungsPrueferTest.kt`

**Interfaces:**
- Consumes: nichts aus Plan 1 außer dem Modulaufbau
- Produces:
  - `Belohnung(id: Long, name: String, beschreibung: String, preisPunkte: Int, bedingung: Belohnungsbedingung?, archiviert: Boolean)`
  - `sealed interface Belohnungsbedingung` mit `MindestSerie(aktivitaetId: Long, perioden: Int)` und `FruehestensAb(datum: LocalDate)`
  - `Einloesung(id: Long, belohnungId: Long, name: String, preisPunkte: Int, zeitpunkt: LocalDateTime)`
  - `enum Hindernis { ZU_WENIG_PUNKTE, SERIE_ZU_KURZ, ZU_FRUEH }`
  - `Einloesbarkeit(moeglich: Boolean, fehlendePunkte: Int, hindernis: Hindernis?)`
  - `BelohnungsPruefer.pruefe(belohnung: Belohnung, kontostand: Int, serieDerBedingung: Int, heute: LocalDate): Einloesbarkeit` — `serieDerBedingung` ist die aktuelle Serie der in der Bedingung genannten Aktivität; bei anderer oder fehlender Bedingung wird der Wert ignoriert

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungsPrueferTest {

    private val pruefer = BelohnungsPruefer()
    private val heute = LocalDate.of(2026, 3, 2)
    private val kinoabend = Belohnung(id = 1, name = "Kinoabend", preisPunkte = 200)

    @Test
    fun `mit genug Punkten und ohne Bedingung ist einloesbar`() {
        val ergebnis = pruefer.pruefe(kinoabend, kontostand = 250, serieDerBedingung = 0, heute = heute)

        assertEquals(Einloesbarkeit(moeglich = true, fehlendePunkte = 0, hindernis = null), ergebnis)
    }

    @Test
    fun `fehlende Punkte werden beziffert`() {
        val ergebnis = pruefer.pruefe(kinoabend, kontostand = 160, serieDerBedingung = 0, heute = heute)

        assertEquals(
            Einloesbarkeit(moeglich = false, fehlendePunkte = 40, hindernis = Hindernis.ZU_WENIG_PUNKTE),
            ergebnis,
        )
    }

    @Test
    fun `eine zu kurze Serie verhindert das Einloesen trotz genug Punkten`() {
        val mitSerie = kinoabend.copy(
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
        )

        val ergebnis = pruefer.pruefe(mitSerie, kontostand = 500, serieDerBedingung = 9, heute = heute)

        assertEquals(
            Einloesbarkeit(moeglich = false, fehlendePunkte = 0, hindernis = Hindernis.SERIE_ZU_KURZ),
            ergebnis,
        )
    }

    @Test
    fun `eine erfuellte Serienbedingung laesst das Einloesen zu`() {
        val mitSerie = kinoabend.copy(
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
        )

        val ergebnis = pruefer.pruefe(mitSerie, kontostand = 500, serieDerBedingung = 14, heute = heute)

        assertEquals(true, ergebnis.moeglich)
    }

    @Test
    fun `vor dem Startdatum ist nicht einloesbar`() {
        val abApril = kinoabend.copy(
            bedingung = Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)),
        )

        val ergebnis = pruefer.pruefe(abApril, kontostand = 500, serieDerBedingung = 0, heute = heute)

        assertEquals(Hindernis.ZU_FRUEH, ergebnis.hindernis)
    }

    @Test
    fun `am Startdatum selbst ist einloesbar`() {
        val abHeute = kinoabend.copy(bedingung = Belohnungsbedingung.FruehestensAb(heute))

        assertEquals(true, pruefer.pruefe(abHeute, 500, 0, heute).moeglich)
    }

    @Test
    fun `fehlende Punkte wiegen schwerer als eine unerfuellte Bedingung`() {
        val mitSerie = kinoabend.copy(
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
        )

        val ergebnis = pruefer.pruefe(mitSerie, kontostand = 10, serieDerBedingung = 1, heute = heute)

        assertEquals(Hindernis.ZU_WENIG_PUNKTE, ergebnis.hindernis)
        assertEquals(190, ergebnis.fehlendePunkte)
    }

    @Test
    fun `eine archivierte Belohnung ist nie einloesbar`() {
        val ergebnis = pruefer.pruefe(kinoabend.copy(archiviert = true), 500, 0, heute)

        assertEquals(false, ergebnis.moeglich)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :domain:test --tests "*BelohnungsPrueferTest*"`
Expected: Kompilierfehler `Unresolved reference: BelohnungsPruefer`.

- [ ] **Step 3: Implementieren**

`domain/model/Belohnung.kt`:

```kotlin
package de.vanhake.dayorganizer.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

sealed interface Belohnungsbedingung {
    /** Die genannte Aktivitaet muss eine Serie von mindestens [perioden] erreicht haben. */
    data class MindestSerie(val aktivitaetId: Long, val perioden: Int) : Belohnungsbedingung

    data class FruehestensAb(val datum: LocalDate) : Belohnungsbedingung
}

data class Belohnung(
    val id: Long = 0,
    val name: String,
    val beschreibung: String = "",
    val preisPunkte: Int,
    val bedingung: Belohnungsbedingung? = null,
    val archiviert: Boolean = false,
)

/**
 * Name und Preis sind absichtlich kopiert statt verlinkt: die Historie muss
 * lesbar bleiben, auch wenn die Belohnung spaeter umbenannt wird.
 */
data class Einloesung(
    val id: Long = 0,
    val belohnungId: Long,
    val name: String,
    val preisPunkte: Int,
    val zeitpunkt: LocalDateTime,
)
```

`domain/rechner/BelohnungsPruefer.kt`:

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate

enum class Hindernis { ZU_WENIG_PUNKTE, SERIE_ZU_KURZ, ZU_FRUEH }

data class Einloesbarkeit(
    val moeglich: Boolean,
    val fehlendePunkte: Int,
    val hindernis: Hindernis?,
)

class BelohnungsPruefer {

    /**
     * @param serieDerBedingung aktuelle Serie der in der Bedingung genannten
     *   Aktivitaet. Ohne Serienbedingung ist der Wert bedeutungslos.
     */
    fun pruefe(
        belohnung: Belohnung,
        kontostand: Int,
        serieDerBedingung: Int,
        heute: LocalDate,
    ): Einloesbarkeit {
        val fehlend = (belohnung.preisPunkte - kontostand).coerceAtLeast(0)

        if (belohnung.archiviert) {
            return Einloesbarkeit(false, fehlend, hindernis = null)
        }
        if (fehlend > 0) {
            return Einloesbarkeit(false, fehlend, Hindernis.ZU_WENIG_PUNKTE)
        }

        return when (val bedingung = belohnung.bedingung) {
            null -> Einloesbarkeit(true, 0, null)

            is Belohnungsbedingung.MindestSerie ->
                if (serieDerBedingung >= bedingung.perioden) {
                    Einloesbarkeit(true, 0, null)
                } else {
                    Einloesbarkeit(false, 0, Hindernis.SERIE_ZU_KURZ)
                }

            is Belohnungsbedingung.FruehestensAb ->
                if (!heute.isBefore(bedingung.datum)) {
                    Einloesbarkeit(true, 0, null)
                } else {
                    Einloesbarkeit(false, 0, Hindernis.ZU_FRUEH)
                }
        }
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :domain:test --tests "*BelohnungsPrueferTest*"`
Expected: `BUILD SUCCESSFUL`, 8 Tests bestanden.

- [ ] **Step 5: Commit**

```bash
git add domain/
git commit -m "feat(domain): Belohnungsmodell und Pruefung der Einloesbarkeit"
```

---

### Task 2: Persistenz für Belohnungen und Einlösungen

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/BelohnungEntity.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/EinloesungEntity.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/BelohnungDao.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/DayOrganizerDatenbank.kt` — Version 3
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/Migrationen.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/di/DatenModul.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/repository/BelohnungRepository.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/data/mapper/BelohnungMapperTest.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/data/db/MigrationTest.kt` (erweitern)

**Interfaces:**
- Consumes: `Belohnung`, `Belohnungsbedingung`, `Einloesung` aus Task 1
- Produces:
  - `BelohnungRepository.beobachteAktive(): Flow<List<Belohnung>>`, `.alle(): List<Belohnung>`, `.nachId(id): Belohnung?`, `.speichern(b): Long`, `.archivieren(id)`
  - `BelohnungRepository.einloesungAnlegen(e: Einloesung)`, `.beobachteEinloesungen(): Flow<List<Einloesung>>`
  - Bedingung wird flach gespeichert: `bedingungTyp` (`"MINDESTSERIE"`, `"FRUEHESTENS_AB"` oder `null`), `bedingungAktivitaetId`, `bedingungPerioden`, `bedingungDatum`
  - `fun Belohnung.zuEntity(): BelohnungEntity`, `fun BelohnungEntity.zuDomaene(): Belohnung`

- [ ] **Step 1: Fehlschlagenden Mapper-Test schreiben**

```kotlin
package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungMapperTest {

    private fun rundlauf(belohnung: Belohnung) {
        assertEquals(belohnung, belohnung.zuEntity().zuDomaene())
    }

    @Test
    fun `Belohnung ohne Bedingung uebersteht den Rundlauf`() {
        rundlauf(Belohnung(id = 1, name = "Kinoabend", beschreibung = "mit Popcorn", preisPunkte = 200))
    }

    @Test
    fun `Serienbedingung uebersteht den Rundlauf`() {
        rundlauf(
            Belohnung(
                id = 2, name = "Neues Buch", preisPunkte = 300,
                bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
            ),
        )
    }

    @Test
    fun `Datumsbedingung uebersteht den Rundlauf`() {
        rundlauf(
            Belohnung(
                id = 3, name = "Wochenendausflug", preisPunkte = 800,
                bedingung = Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)),
            ),
        )
    }

    @Test
    fun `archivierte Belohnung uebersteht den Rundlauf`() {
        rundlauf(Belohnung(id = 4, name = "Alt", preisPunkte = 50, archiviert = true))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BelohnungMapperTest*"`
Expected: Kompilierfehler `Unresolved reference: zuEntity`.

- [ ] **Step 3: Entities, Mapper und DAO anlegen**

`data/db/BelohnungEntity.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "belohnung")
data class BelohnungEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val beschreibung: String,
    val preisPunkte: Int,
    /** "MINDESTSERIE", "FRUEHESTENS_AB" oder null. */
    val bedingungTyp: String?,
    val bedingungAktivitaetId: Long?,
    val bedingungPerioden: Int?,
    val bedingungDatum: LocalDate?,
    val archiviert: Boolean,
)
```

`data/db/EinloesungEntity.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "einloesung", indices = [Index(value = ["zeitpunkt"])])
data class EinloesungEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val belohnungId: Long,
    val name: String,
    val preisPunkte: Int,
    val zeitpunkt: LocalDateTime,
)
```

In `data/mapper/EntityMapper.kt` ergänzen:

```kotlin
fun Belohnung.zuEntity(): BelohnungEntity = BelohnungEntity(
    id = id,
    name = name,
    beschreibung = beschreibung,
    preisPunkte = preisPunkte,
    bedingungTyp = when (bedingung) {
        null -> null
        is Belohnungsbedingung.MindestSerie -> "MINDESTSERIE"
        is Belohnungsbedingung.FruehestensAb -> "FRUEHESTENS_AB"
    },
    bedingungAktivitaetId = (bedingung as? Belohnungsbedingung.MindestSerie)?.aktivitaetId,
    bedingungPerioden = (bedingung as? Belohnungsbedingung.MindestSerie)?.perioden,
    bedingungDatum = (bedingung as? Belohnungsbedingung.FruehestensAb)?.datum,
    archiviert = archiviert,
)

fun BelohnungEntity.zuDomaene(): Belohnung = Belohnung(
    id = id,
    name = name,
    beschreibung = beschreibung,
    preisPunkte = preisPunkte,
    bedingung = when (bedingungTyp) {
        "MINDESTSERIE" -> Belohnungsbedingung.MindestSerie(
            aktivitaetId = requireNotNull(bedingungAktivitaetId),
            perioden = requireNotNull(bedingungPerioden),
        )
        "FRUEHESTENS_AB" -> Belohnungsbedingung.FruehestensAb(requireNotNull(bedingungDatum))
        null -> null
        else -> error("Unbekannte Belohnungsbedingung: $bedingungTyp")
    },
    archiviert = archiviert,
)

fun Einloesung.zuEntity(): EinloesungEntity = EinloesungEntity(
    id = id, belohnungId = belohnungId, name = name,
    preisPunkte = preisPunkte, zeitpunkt = zeitpunkt,
)

fun EinloesungEntity.zuDomaene(): Einloesung = Einloesung(
    id = id, belohnungId = belohnungId, name = name,
    preisPunkte = preisPunkte, zeitpunkt = zeitpunkt,
)
```

Neue Importe dort: `de.vanhake.dayorganizer.data.db.BelohnungEntity`, `EinloesungEntity`, `de.vanhake.dayorganizer.domain.model.Belohnung`, `Belohnungsbedingung`, `Einloesung`.

`data/db/BelohnungDao.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BelohnungDao {

    @Insert suspend fun einfuegen(eintrag: BelohnungEntity): Long
    @Update suspend fun aktualisieren(eintrag: BelohnungEntity)

    @Query("SELECT * FROM belohnung WHERE id = :id")
    suspend fun nachId(id: Long): BelohnungEntity?

    @Query("SELECT * FROM belohnung ORDER BY archiviert, preisPunkte")
    suspend fun alle(): List<BelohnungEntity>

    @Query("SELECT * FROM belohnung WHERE archiviert = 0 ORDER BY preisPunkte")
    fun beobachteAktive(): Flow<List<BelohnungEntity>>

    @Query("UPDATE belohnung SET archiviert = 1 WHERE id = :id")
    suspend fun archivieren(id: Long)

    @Insert suspend fun einloesungEinfuegen(eintrag: EinloesungEntity)

    @Query("SELECT * FROM einloesung ORDER BY zeitpunkt DESC")
    fun beobachteEinloesungen(): Flow<List<EinloesungEntity>>

    @Query("SELECT * FROM einloesung ORDER BY zeitpunkt DESC")
    suspend fun alleEinloesungen(): List<EinloesungEntity>
}
```

`data/repository/BelohnungRepository.kt`:

```kotlin
package de.vanhake.dayorganizer.data.repository

import de.vanhake.dayorganizer.data.db.BelohnungDao
import de.vanhake.dayorganizer.data.mapper.zuDomaene
import de.vanhake.dayorganizer.data.mapper.zuEntity
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Einloesung
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface BelohnungRepository {
    fun beobachteAktive(): Flow<List<Belohnung>>
    suspend fun alle(): List<Belohnung>
    suspend fun nachId(id: Long): Belohnung?
    suspend fun speichern(belohnung: Belohnung): Long
    suspend fun archivieren(id: Long)
    suspend fun einloesungAnlegen(einloesung: Einloesung)
    fun beobachteEinloesungen(): Flow<List<Einloesung>>
    suspend fun alleEinloesungen(): List<Einloesung>
}

class RaumBelohnungRepository @Inject constructor(
    private val dao: BelohnungDao,
) : BelohnungRepository {

    override fun beobachteAktive(): Flow<List<Belohnung>> =
        dao.beobachteAktive().map { liste -> liste.map { it.zuDomaene() } }

    override suspend fun alle(): List<Belohnung> = dao.alle().map { it.zuDomaene() }

    override suspend fun nachId(id: Long): Belohnung? = dao.nachId(id)?.zuDomaene()

    override suspend fun speichern(belohnung: Belohnung): Long =
        if (belohnung.id == 0L) {
            dao.einfuegen(belohnung.zuEntity())
        } else {
            dao.aktualisieren(belohnung.zuEntity())
            belohnung.id
        }

    override suspend fun archivieren(id: Long) = dao.archivieren(id)

    override suspend fun einloesungAnlegen(einloesung: Einloesung) =
        dao.einloesungEinfuegen(einloesung.zuEntity())

    override fun beobachteEinloesungen(): Flow<List<Einloesung>> =
        dao.beobachteEinloesungen().map { liste -> liste.map { it.zuDomaene() } }

    override suspend fun alleEinloesungen(): List<Einloesung> =
        dao.alleEinloesungen().map { it.zuDomaene() }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BelohnungMapperTest*"`
Expected: `BUILD SUCCESSFUL`, 4 Tests bestanden.

- [ ] **Step 5: Datenbank auf Version 3 heben**

In `Migrationen.kt` ergänzen:

```kotlin
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `belohnung` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `name` TEXT NOT NULL,
                `beschreibung` TEXT NOT NULL,
                `preisPunkte` INTEGER NOT NULL,
                `bedingungTyp` TEXT,
                `bedingungAktivitaetId` INTEGER,
                `bedingungPerioden` INTEGER,
                `bedingungDatum` TEXT,
                `archiviert` INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `einloesung` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `belohnungId` INTEGER NOT NULL,
                `name` TEXT NOT NULL,
                `preisPunkte` INTEGER NOT NULL,
                `zeitpunkt` TEXT NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_einloesung_zeitpunkt` ON `einloesung` (`zeitpunkt`)")
    }
}
```

In `DayOrganizerDatenbank.kt` die Entities ergänzen, `version = 3` setzen und `abstract fun belohnungDao(): BelohnungDao` hinzufügen. In `DatenModul.kt` `.addMigrations(MIGRATION_1_2, MIGRATION_2_3)` setzen und `@Provides fun belohnungDao(db: DayOrganizerDatenbank): BelohnungDao = db.belohnungDao()` ergänzen. In `RepositoryModul` binden:

```kotlin
    @Binds abstract fun belohnungen(impl: RaumBelohnungRepository): BelohnungRepository
```

In `DatenModul` zusätzlich die beiden neuen Rechner bereitstellen — ohne sie
scheitert die Injektion in `BelohnungEinloesen`, `BelohnungenViewModel` und
`StatistikViewModel`:

```kotlin
    @Provides @Singleton fun belohnungsPruefer(): BelohnungsPruefer = BelohnungsPruefer()

    @Provides @Singleton
    fun statistikRechner(serienRechner: SerienRechner): StatistikRechner =
        StatistikRechner(serienRechner)
```

`StatistikRechner` entsteht erst in Task 6; bis dahin bleibt diese zweite
`@Provides`-Methode auskommentiert.

- [ ] **Step 6: Migrationstest erweitern und laufen lassen**

In `MigrationTest.kt` ergänzen:

```kotlin
    @Test
    fun migration_2_auf_3_legt_Belohnung_und_Einloesung_an() {
        helfer.createDatabase(DB_NAME, 2).close()

        val db = helfer.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_1_2, MIGRATION_2_3)

        db.query("SELECT COUNT(*) FROM belohnung").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM einloesung").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    @Test
    fun migration_1_auf_3_laeuft_durch() {
        helfer.createDatabase(DB_NAME, 1).close()

        helfer.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_1_2, MIGRATION_2_3).close()
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests "*MigrationTest*"`
Expected: `BUILD SUCCESSFUL`, 3 Tests bestanden.

- [ ] **Step 7: Commit**

```bash
git add app/
git commit -m "feat(data): Persistenz fuer Belohnungen und Einloesungen"
```

---

### Task 3: Belohnung einlösen

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/BelohnungEinloesen.kt`
- Modify: `app/src/test/kotlin/de/vanhake/dayorganizer/Fakes.kt` — `FakeBelohnungRepository`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/BelohnungEinloesenTest.kt`

**Interfaces:**
- Consumes: `BelohnungsPruefer` aus Task 1, `BelohnungRepository` aus Task 2, `BuchungRepository`, `FaelligkeitRepository`, `SerienRechner` aus Plan 1
- Produces:
  - `sealed interface EinloeseErgebnis` mit `Erfolg(einloesung: Einloesung)` und `Abgelehnt(einloesbarkeit: Einloesbarkeit)` sowie `Unbekannt`
  - `BelohnungEinloesen.invoke(belohnungId: Long): EinloeseErgebnis` — prüft, bucht negativ und legt die Einlösung an; niemals in umgekehrter Reihenfolge, damit ein Abbruch nicht eine Einlösung ohne Buchung hinterlässt
  - `BelohnungEinloesen.einloesbarkeit(belohnung: Belohnung): Einloesbarkeit` für die Anzeige

- [ ] **Step 1: Fake ergänzen und fehlschlagenden Test schreiben**

In `Fakes.kt` ergänzen:

```kotlin
class FakeBelohnungRepository(
    start: List<Belohnung> = emptyList(),
) : BelohnungRepository {
    val stand = MutableStateFlow(start)
    val einloesungen = MutableStateFlow<List<Einloesung>>(emptyList())
    private var naechsteId = (start.maxOfOrNull { it.id } ?: 0L) + 1

    override fun beobachteAktive(): Flow<List<Belohnung>> =
        stand.map { liste -> liste.filterNot { it.archiviert } }

    override suspend fun alle(): List<Belohnung> = stand.value

    override suspend fun nachId(id: Long): Belohnung? = stand.value.find { it.id == id }

    override suspend fun speichern(belohnung: Belohnung): Long {
        val id = if (belohnung.id == 0L) naechsteId++ else belohnung.id
        stand.value = stand.value.filterNot { it.id == id } + belohnung.copy(id = id)
        return id
    }

    override suspend fun archivieren(id: Long) {
        stand.value = stand.value.map { if (it.id == id) it.copy(archiviert = true) else it }
    }

    override suspend fun einloesungAnlegen(einloesung: Einloesung) {
        einloesungen.value = einloesungen.value + einloesung.copy(id = einloesungen.value.size + 1L)
    }

    override fun beobachteEinloesungen(): Flow<List<Einloesung>> = einloesungen

    override suspend fun alleEinloesungen(): List<Einloesung> = einloesungen.value
}
```

Importe dort: `de.vanhake.dayorganizer.data.repository.BelohnungRepository`, `de.vanhake.dayorganizer.domain.model.Belohnung`, `de.vanhake.dayorganizer.domain.model.Einloesung`.

Der Test:

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeBelohnungRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.Hindernis
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BelohnungEinloesenTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 19, 0).atZone(zone).toInstant(),
        zone,
    )
    private val kinoabend = Belohnung(id = 1, name = "Kinoabend", preisPunkte = 200)

    private fun guthaben(punkte: Int) = FakeBuchungRepository(
        listOf(
            Buchung(
                art = BuchungsArt.ERLEDIGUNG, betrag = punkte,
                zeitpunkt = LocalDateTime.of(2026, 3, 1, 8, 0),
            ),
        ),
    )

    private fun useCase(
        belohnungen: FakeBelohnungRepository,
        buchungen: FakeBuchungRepository,
        faelligkeiten: FakeFaelligkeitRepository = FakeFaelligkeitRepository(),
    ) = BelohnungEinloesen(
        belohnungen = belohnungen,
        buchungen = buchungen,
        faelligkeiten = faelligkeiten,
        pruefer = BelohnungsPruefer(),
        serienRechner = SerienRechner(),
        uhr = uhr,
    )

    @Test
    fun `mit genug Punkten wird abgebucht und die Einloesung angelegt`() = runTest {
        val belohnungen = FakeBelohnungRepository(listOf(kinoabend))
        val buchungen = guthaben(250)

        val ergebnis = useCase(belohnungen, buchungen)(1)

        assertTrue(ergebnis is EinloeseErgebnis.Erfolg)
        assertEquals(50, buchungen.kontostand())
        assertEquals(1, belohnungen.einloesungen.value.size)
        assertEquals("Kinoabend", belohnungen.einloesungen.value.single().name)
        assertEquals(
            -200,
            buchungen.stand.value.single { it.art == BuchungsArt.EINLOESUNG }.betrag,
        )
    }

    @Test
    fun `ohne genug Punkte passiert nichts`() = runTest {
        val belohnungen = FakeBelohnungRepository(listOf(kinoabend))
        val buchungen = guthaben(100)

        val ergebnis = useCase(belohnungen, buchungen)(1)

        assertTrue(ergebnis is EinloeseErgebnis.Abgelehnt)
        assertEquals(Hindernis.ZU_WENIG_PUNKTE, ergebnis.einloesbarkeit.hindernis)
        assertEquals(100, buchungen.kontostand())
        assertEquals(0, belohnungen.einloesungen.value.size)
    }

    @Test
    fun `eine unerfuellte Serienbedingung blockiert trotz Guthaben`() = runTest {
        val mitSerie = kinoabend.copy(
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 14),
        )
        val belohnungen = FakeBelohnungRepository(listOf(mitSerie))
        val buchungen = guthaben(500)

        val ergebnis = useCase(belohnungen, buchungen)(1)

        assertTrue(ergebnis is EinloeseErgebnis.Abgelehnt)
        assertEquals(Hindernis.SERIE_ZU_KURZ, ergebnis.einloesbarkeit.hindernis)
        assertEquals(500, buchungen.kontostand())
    }

    @Test
    fun `eine erfuellte Serienbedingung laesst einloesen`() = runTest {
        val mitSerie = kinoabend.copy(
            bedingung = Belohnungsbedingung.MindestSerie(aktivitaetId = 7, perioden = 2),
        )
        val historie = (0L..2L).map { versatz ->
            Faelligkeit(
                id = versatz + 1, aktivitaetId = 7,
                periodeVon = LocalDate.of(2026, 2, 28).plusDays(versatz),
                periodeBis = LocalDate.of(2026, 2, 28).plusDays(versatz),
                geplantFuer = null, sollAnzahl = 1, istAnzahl = 1,
                status = FaelligkeitStatus.ERLEDIGT,
            )
        }
        val belohnungen = FakeBelohnungRepository(listOf(mitSerie))
        val buchungen = guthaben(500)

        val ergebnis = useCase(belohnungen, buchungen, FakeFaelligkeitRepository(historie))(1)

        assertTrue(ergebnis is EinloeseErgebnis.Erfolg)
        assertEquals(300, buchungen.kontostand())
    }

    @Test
    fun `eine unbekannte Belohnung wird gemeldet`() = runTest {
        val ergebnis = useCase(FakeBelohnungRepository(), guthaben(500))(999)

        assertEquals(EinloeseErgebnis.Unbekannt, ergebnis)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BelohnungEinloesenTest*"`
Expected: Kompilierfehler `Unresolved reference: BelohnungEinloesen`.

- [ ] **Step 3: Implementieren**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.Einloesbarkeit
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

sealed interface EinloeseErgebnis {
    data class Erfolg(val einloesung: Einloesung) : EinloeseErgebnis
    data class Abgelehnt(val einloesbarkeit: Einloesbarkeit) : EinloeseErgebnis
    data object Unbekannt : EinloeseErgebnis
}

class BelohnungEinloesen @Inject constructor(
    private val belohnungen: BelohnungRepository,
    private val buchungen: BuchungRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val pruefer: BelohnungsPruefer,
    private val serienRechner: SerienRechner,
    private val uhr: Clock,
) {

    suspend fun einloesbarkeit(belohnung: Belohnung): Einloesbarkeit = pruefer.pruefe(
        belohnung = belohnung,
        kontostand = buchungen.kontostand(),
        serieDerBedingung = serieFuer(belohnung),
        heute = LocalDate.now(uhr),
    )

    suspend operator fun invoke(belohnungId: Long): EinloeseErgebnis {
        val belohnung = belohnungen.nachId(belohnungId) ?: return EinloeseErgebnis.Unbekannt

        val pruefung = einloesbarkeit(belohnung)
        if (!pruefung.moeglich) return EinloeseErgebnis.Abgelehnt(pruefung)

        val jetzt = LocalDateTime.now(uhr)

        // Erst buchen, dann die Einloesung anlegen. Bricht etwas ab, fehlt hoechstens
        // der Historieneintrag — nie umgekehrt ein Eintrag ohne Abbuchung.
        buchungen.buchen(
            listOf(
                Buchung(
                    art = BuchungsArt.EINLOESUNG,
                    betrag = -belohnung.preisPunkte,
                    zeitpunkt = jetzt,
                    belohnungId = belohnung.id,
                    notiz = belohnung.name,
                ),
            ),
        )

        val einloesung = Einloesung(
            belohnungId = belohnung.id,
            name = belohnung.name,
            preisPunkte = belohnung.preisPunkte,
            zeitpunkt = jetzt,
        )
        belohnungen.einloesungAnlegen(einloesung)
        return EinloeseErgebnis.Erfolg(einloesung)
    }

    private suspend fun serieFuer(belohnung: Belohnung): Int {
        val bedingung = belohnung.bedingung as? Belohnungsbedingung.MindestSerie ?: return 0
        val historie = faelligkeiten.historie(bedingung.aktivitaetId)
        return serienRechner.aktuelleSerie(historie, LocalDate.now(uhr))
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BelohnungEinloesenTest*"`
Expected: `BUILD SUCCESSFUL`, 5 Tests bestanden.

- [ ] **Step 5: Commit**

```bash
git add app/
git commit -m "feat(domainuse): Belohnung einloesen mit Pruefung und negativer Buchung"
```

---

### Task 4: Belohnungs-Screen

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungenZustand.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungenViewModel.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungenScreen.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungBearbeitenScreen.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungEntwurf.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungEntwurfTest.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/belohnungen/BelohnungenViewModelTest.kt`

**Interfaces:**
- Consumes: `BelohnungRepository` aus Task 2, `BelohnungEinloesen` aus Task 3
- Produces:
  - `BelohnungKachel(id: Long, name: String, beschreibung: String, preisPunkte: Int, einloesbarkeit: Einloesbarkeit)`
  - `BelohnungenZustand(kontostand: Int, kacheln: List<BelohnungKachel>, einloesungen: List<Einloesung>)`
  - `BelohnungenViewModel.zustand: StateFlow<BelohnungenZustand>`, `.einloesen(id: Long)`, `.archivieren(id: Long)`
  - `BelohnungEntwurf` mit `pruefen(): List<BelohnungFehler>` und `zuBelohnung(): Belohnung`
  - `enum BelohnungFehler { NAME_LEER, PREIS_NICHT_POSITIV, SERIE_OHNE_AKTIVITAET, SERIE_NICHT_POSITIV }`

- [ ] **Step 1: Fehlschlagenden Entwurfs-Test schreiben**

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BelohnungEntwurfTest {

    private val gueltig = BelohnungEntwurf(name = "Kinoabend", preisText = "200")

    @Test
    fun `ein vollstaendiger Entwurf hat keine Fehler`() {
        assertEquals(emptyList(), gueltig.pruefen())
    }

    @Test
    fun `leerer Name und nicht positiver Preis werden gemeldet`() {
        val fehler = gueltig.copy(name = " ", preisText = "0").pruefen()

        assertTrue(BelohnungFehler.NAME_LEER in fehler)
        assertTrue(BelohnungFehler.PREIS_NICHT_POSITIV in fehler)
    }

    @Test
    fun `eine Serienbedingung ohne Aktivitaet wird gemeldet`() {
        val fehler = gueltig.copy(
            bedingungTyp = BedingungTyp.MINDESTSERIE,
            bedingungAktivitaetId = null,
            bedingungPeriodenText = "14",
        ).pruefen()

        assertTrue(BelohnungFehler.SERIE_OHNE_AKTIVITAET in fehler)
    }

    @Test
    fun `eine Serienbedingung mit Periode null wird gemeldet`() {
        val fehler = gueltig.copy(
            bedingungTyp = BedingungTyp.MINDESTSERIE,
            bedingungAktivitaetId = 7,
            bedingungPeriodenText = "0",
        ).pruefen()

        assertTrue(BelohnungFehler.SERIE_NICHT_POSITIV in fehler)
    }

    @Test
    fun `der Entwurf wird in die richtige Bedingung umgesetzt`() {
        val mitSerie = gueltig.copy(
            bedingungTyp = BedingungTyp.MINDESTSERIE,
            bedingungAktivitaetId = 7,
            bedingungPeriodenText = "14",
        )

        assertEquals(
            Belohnungsbedingung.MindestSerie(7, 14),
            mitSerie.zuBelohnung().bedingung,
        )

        val mitDatum = gueltig.copy(
            bedingungTyp = BedingungTyp.FRUEHESTENS_AB,
            bedingungDatum = LocalDate.of(2026, 4, 1),
        )

        assertEquals(
            Belohnungsbedingung.FruehestensAb(LocalDate.of(2026, 4, 1)),
            mitDatum.zuBelohnung().bedingung,
        )

        assertEquals(null, gueltig.zuBelohnung().bedingung)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BelohnungEntwurfTest*"`
Expected: Kompilierfehler `Unresolved reference: BelohnungEntwurf`.

- [ ] **Step 3: Entwurf implementieren**

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate

enum class BedingungTyp { KEINE, MINDESTSERIE, FRUEHESTENS_AB }

enum class BelohnungFehler {
    NAME_LEER,
    PREIS_NICHT_POSITIV,
    SERIE_OHNE_AKTIVITAET,
    SERIE_NICHT_POSITIV,
}

data class BelohnungEntwurf(
    val id: Long = 0,
    val name: String = "",
    val beschreibung: String = "",
    val preisText: String = "100",
    val bedingungTyp: BedingungTyp = BedingungTyp.KEINE,
    val bedingungAktivitaetId: Long? = null,
    val bedingungPeriodenText: String = "7",
    val bedingungDatum: LocalDate? = null,
) {

    private val preis: Int get() = preisText.toIntOrNull() ?: 0
    private val perioden: Int get() = bedingungPeriodenText.toIntOrNull() ?: 0

    fun pruefen(): List<BelohnungFehler> = buildList {
        if (name.isBlank()) add(BelohnungFehler.NAME_LEER)
        if (preis <= 0) add(BelohnungFehler.PREIS_NICHT_POSITIV)
        if (bedingungTyp == BedingungTyp.MINDESTSERIE) {
            if (bedingungAktivitaetId == null) add(BelohnungFehler.SERIE_OHNE_AKTIVITAET)
            if (perioden <= 0) add(BelohnungFehler.SERIE_NICHT_POSITIV)
        }
    }

    /** Nur aufrufen, wenn [pruefen] leer ist. */
    fun zuBelohnung(): Belohnung = Belohnung(
        id = id,
        name = name.trim(),
        beschreibung = beschreibung.trim(),
        preisPunkte = preis,
        bedingung = when (bedingungTyp) {
            BedingungTyp.KEINE -> null
            BedingungTyp.MINDESTSERIE -> Belohnungsbedingung.MindestSerie(
                aktivitaetId = requireNotNull(bedingungAktivitaetId),
                perioden = perioden,
            )
            BedingungTyp.FRUEHESTENS_AB -> Belohnungsbedingung.FruehestensAb(
                requireNotNull(bedingungDatum) { "Datumsbedingung ohne Datum" },
            )
        },
    )

    companion object {
        fun aus(belohnung: Belohnung): BelohnungEntwurf {
            val basis = BelohnungEntwurf(
                id = belohnung.id,
                name = belohnung.name,
                beschreibung = belohnung.beschreibung,
                preisText = belohnung.preisPunkte.toString(),
            )
            return when (val b = belohnung.bedingung) {
                null -> basis
                is Belohnungsbedingung.MindestSerie -> basis.copy(
                    bedingungTyp = BedingungTyp.MINDESTSERIE,
                    bedingungAktivitaetId = b.aktivitaetId,
                    bedingungPeriodenText = b.perioden.toString(),
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

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BelohnungEntwurfTest*"`
Expected: `BUILD SUCCESSFUL`, 5 Tests bestanden.

- [ ] **Step 5: Fehlschlagenden ViewModel-Test schreiben**

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import app.cash.turbine.test
import de.vanhake.dayorganizer.FakeBelohnungRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.Hindernis
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domainuse.BelohnungEinloesen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class BelohnungenViewModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 19, 0).atZone(zone).toInstant(),
        zone,
    )
    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private fun viewModel(
        belohnungen: FakeBelohnungRepository,
        buchungen: FakeBuchungRepository,
    ): BelohnungenViewModel {
        val faelligkeiten = FakeFaelligkeitRepository()
        return BelohnungenViewModel(
            belohnungen = belohnungen,
            buchungen = buchungen,
            einloesenUseCase = BelohnungEinloesen(
                belohnungen, buchungen, faelligkeiten,
                BelohnungsPruefer(), SerienRechner(), uhr,
            ),
            pruefer = BelohnungsPruefer(),
            faelligkeiten = faelligkeiten,
            serienRechner = SerienRechner(),
            uhr = uhr,
        )
    }

    private fun guthaben(punkte: Int) = FakeBuchungRepository(
        listOf(
            Buchung(
                art = BuchungsArt.ERLEDIGUNG, betrag = punkte,
                zeitpunkt = LocalDateTime.of(2026, 3, 1, 8, 0),
            ),
        ),
    )

    @Test
    fun `Kacheln zeigen fehlende Punkte`() = runTest(dispatcher) {
        val vm = viewModel(
            FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200))),
            guthaben(160),
        )

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            assertEquals(160, zustand.kontostand)
            assertEquals(40, zustand.kacheln.single().einloesbarkeit.fehlendePunkte)
            assertEquals(
                Hindernis.ZU_WENIG_PUNKTE,
                zustand.kacheln.single().einloesbarkeit.hindernis,
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Einloesen senkt den Kontostand und erscheint in der Historie`() = runTest(dispatcher) {
        val belohnungen = FakeBelohnungRepository(listOf(Belohnung(1, "Kinoabend", preisPunkte = 200)))
        val buchungen = guthaben(250)
        val vm = viewModel(belohnungen, buchungen)

        vm.zustand.test {
            skipItems(2)
            vm.einloesen(1)
            val danach = awaitItem()
            assertEquals(50, danach.kontostand)
            assertEquals(listOf("Kinoabend"), danach.einloesungen.map { it.name })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 6: Zustand und ViewModel implementieren**

`ui/belohnungen/BelohnungenZustand.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.rechner.Einloesbarkeit

data class BelohnungKachel(
    val id: Long,
    val name: String,
    val beschreibung: String,
    val preisPunkte: Int,
    val einloesbarkeit: Einloesbarkeit,
)

data class BelohnungenZustand(
    val kontostand: Int = 0,
    val kacheln: List<BelohnungKachel> = emptyList(),
    val einloesungen: List<Einloesung> = emptyList(),
)
```

`ui/belohnungen/BelohnungenViewModel.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domainuse.BelohnungEinloesen
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class BelohnungenViewModel @Inject constructor(
    private val belohnungen: BelohnungRepository,
    private val buchungen: BuchungRepository,
    private val einloesenUseCase: BelohnungEinloesen,
    private val pruefer: BelohnungsPruefer,
    private val faelligkeiten: FaelligkeitRepository,
    private val serienRechner: SerienRechner,
    private val uhr: Clock,
) : ViewModel() {

    val zustand: StateFlow<BelohnungenZustand> = combine(
        belohnungen.beobachteAktive(),
        buchungen.beobachteKontostand(),
        belohnungen.beobachteEinloesungen(),
    ) { liste, kontostand, einloesungen ->
        Triple(liste, kontostand, einloesungen)
    }.map { (liste, kontostand, einloesungen) ->
        BelohnungenZustand(
            kontostand = kontostand,
            kacheln = liste.map { belohnung ->
                BelohnungKachel(
                    id = belohnung.id,
                    name = belohnung.name,
                    beschreibung = belohnung.beschreibung,
                    preisPunkte = belohnung.preisPunkte,
                    einloesbarkeit = pruefer.pruefe(
                        belohnung = belohnung,
                        kontostand = kontostand,
                        serieDerBedingung = serieFuer(belohnung),
                        heute = LocalDate.now(uhr),
                    ),
                )
            },
            einloesungen = einloesungen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BelohnungenZustand())

    // Property und Methode heissen bewusst unterschiedlich, siehe HeuteViewModel.
    fun einloesen(belohnungId: Long) = viewModelScope.launch { einloesenUseCase(belohnungId) }

    fun archivieren(belohnungId: Long) = viewModelScope.launch { belohnungen.archivieren(belohnungId) }

    private suspend fun serieFuer(belohnung: Belohnung): Int {
        val bedingung = belohnung.bedingung as? Belohnungsbedingung.MindestSerie ?: return 0
        return serienRechner.aktuelleSerie(
            faelligkeiten.historie(bedingung.aktivitaetId),
            LocalDate.now(uhr),
        )
    }
}
```

- [ ] **Step 7: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BelohnungenViewModelTest*"`
Expected: `BUILD SUCCESSFUL`, 2 Tests bestanden.

- [ ] **Step 8: Screens anlegen**

`ui/belohnungen/BelohnungenScreen.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.rechner.Hindernis

@Composable
fun BelohnungenScreen(
    bearbeiten: (Long) -> Unit,
    viewModel: BelohnungenViewModel = hiltViewModel(),
) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { bearbeiten(0L) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.neue_belohnung))
            }
        },
    ) { innen ->
        LazyColumn(
            modifier = Modifier.padding(innen).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.punktestand, zustand.kontostand),
                    style = MaterialTheme.typography.headlineSmall,
                )
            }

            items(zustand.kacheln, key = { it.id }) { kachel ->
                Kachel(
                    kachel = kachel,
                    kontostand = zustand.kontostand,
                    einloesen = { viewModel.einloesen(kachel.id) },
                    bearbeiten = { bearbeiten(kachel.id) },
                )
            }

            if (zustand.einloesungen.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.einloese_historie),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
                items(zustand.einloesungen, key = { it.id }) { einloesung ->
                    Text(
                        stringResource(
                            R.string.einloesung_zeile,
                            einloesung.name,
                            einloesung.preisPunkte,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun Kachel(
    kachel: BelohnungKachel,
    kontostand: Int,
    einloesen: () -> Unit,
    bearbeiten: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(kachel.name, style = MaterialTheme.typography.titleMedium)
            if (kachel.beschreibung.isNotBlank()) {
                Text(kachel.beschreibung, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                stringResource(R.string.preis_punkte, kachel.preisPunkte),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = {
                    (kontostand.toFloat() / kachel.preisPunkte).coerceIn(0f, 1f)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = einloesen, enabled = kachel.einloesbarkeit.moeglich) {
                    Text(stringResource(R.string.einloesen))
                }
                Button(onClick = bearbeiten) { Text(stringResource(R.string.bearbeiten)) }
            }
            val hinweis = when (kachel.einloesbarkeit.hindernis) {
                Hindernis.ZU_WENIG_PUNKTE -> stringResource(
                    R.string.noch_punkte_noetig,
                    kachel.einloesbarkeit.fehlendePunkte,
                )
                Hindernis.SERIE_ZU_KURZ -> stringResource(R.string.serie_zu_kurz)
                Hindernis.ZU_FRUEH -> stringResource(R.string.noch_zu_frueh)
                null -> null
            }
            if (hinweis != null) {
                Text(hinweis, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
```

`ui/belohnungen/BelohnungBearbeitenViewModel.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

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

    fun laden(id: Long) = viewModelScope.launch {
        _auswahl.value = aktivitaeten.alleAktiven()
        if (id == 0L) return@launch
        belohnungen.nachId(id)?.let { _entwurf.value = BelohnungEntwurf.aus(it) }
    }

    fun aendern(block: (BelohnungEntwurf) -> BelohnungEntwurf) {
        _entwurf.value = block(_entwurf.value)
    }

    suspend fun speichern(): Boolean {
        val fehler = _entwurf.value.pruefen()
        _fehler.value = fehler
        if (fehler.isNotEmpty()) return false
        belohnungen.speichern(_entwurf.value.zuBelohnung())
        return true
    }
}
```

`ui/belohnungen/BelohnungBearbeitenScreen.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.belohnungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import kotlinx.coroutines.launch
import java.time.LocalDate

@Composable
fun BelohnungBearbeitenScreen(
    id: Long,
    fertig: () -> Unit,
    viewModel: BelohnungBearbeitenViewModel = hiltViewModel(),
) {
    val entwurf by viewModel.entwurf.collectAsStateWithLifecycle()
    val fehler by viewModel.fehler.collectAsStateWithLifecycle()
    val auswahl by viewModel.auswahl.collectAsStateWithLifecycle()
    val bereich = rememberCoroutineScope()

    LaunchedEffect(id) { viewModel.laden(id) }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = entwurf.name,
            onValueChange = { neu -> viewModel.aendern { it.copy(name = neu) } },
            label = { Text(stringResource(R.string.feld_name)) },
            isError = BelohnungFehler.NAME_LEER in fehler,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = entwurf.beschreibung,
            onValueChange = { neu -> viewModel.aendern { it.copy(beschreibung = neu) } },
            label = { Text(stringResource(R.string.feld_beschreibung)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = entwurf.preisText,
            onValueChange = { neu -> viewModel.aendern { it.copy(preisText = neu) } },
            label = { Text(stringResource(R.string.feld_preis)) },
            isError = BelohnungFehler.PREIS_NICHT_POSITIV in fehler,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.feld_bedingung), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BedingungTyp.entries.forEach { typ ->
                FilterChip(
                    selected = entwurf.bedingungTyp == typ,
                    onClick = { viewModel.aendern { it.copy(bedingungTyp = typ) } },
                    label = {
                        Text(
                            stringResource(
                                when (typ) {
                                    BedingungTyp.KEINE -> R.string.bedingung_keine
                                    BedingungTyp.MINDESTSERIE -> R.string.bedingung_serie
                                    BedingungTyp.FRUEHESTENS_AB -> R.string.bedingung_datum
                                },
                            ),
                        )
                    },
                )
            }
        }

        when (entwurf.bedingungTyp) {
            BedingungTyp.KEINE -> Unit

            BedingungTyp.MINDESTSERIE -> {
                Text(
                    stringResource(R.string.feld_bedingung_aktivitaet),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    auswahl.forEach { aktivitaet ->
                        FilterChip(
                            selected = entwurf.bedingungAktivitaetId == aktivitaet.id,
                            onClick = {
                                viewModel.aendern { it.copy(bedingungAktivitaetId = aktivitaet.id) }
                            },
                            label = { Text(aktivitaet.name) },
                        )
                    }
                }
                OutlinedTextField(
                    value = entwurf.bedingungPeriodenText,
                    onValueChange = { neu ->
                        viewModel.aendern { it.copy(bedingungPeriodenText = neu) }
                    },
                    label = { Text(stringResource(R.string.feld_bedingung_perioden)) },
                    isError = BelohnungFehler.SERIE_NICHT_POSITIV in fehler,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            BedingungTyp.FRUEHESTENS_AB -> OutlinedTextField(
                value = entwurf.bedingungDatum?.toString().orEmpty(),
                onValueChange = { neu ->
                    val datum = runCatching { LocalDate.parse(neu.trim()) }.getOrNull()
                    viewModel.aendern { it.copy(bedingungDatum = datum) }
                },
                label = { Text(stringResource(R.string.feld_bedingung_datum)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Button(
            onClick = { bereich.launch { if (viewModel.speichern()) fertig() } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.speichern))
        }
    }
}
```

In `Navigation.kt` ein drittes Tab ergänzen:

```kotlin
object Ziele {
    const val HEUTE = "heute"
    const val AKTIVITAETEN = "aktivitaeten"
    const val BELOHNUNGEN = "belohnungen"
    const val AKTIVITAET_BEARBEITEN = "aktivitaet/{id}"
    const val BELOHNUNG_BEARBEITEN = "belohnung/{id}"
    fun bearbeiten(id: Long) = "aktivitaet/$id"
    fun belohnungBearbeiten(id: Long) = "belohnung/$id"
}
```

mit passendem `NavigationBarItem` (Icon `Icons.Default.Star`, Label `R.string.tab_belohnungen`) und den beiden `composable`-Einträgen.

In `strings.xml` ergänzen:

```xml
    <string name="tab_belohnungen">Belohnungen</string>
    <string name="neue_belohnung">Neue Belohnung</string>
    <string name="preis_punkte">%1$d Punkte</string>
    <string name="einloesen">Einlösen</string>
    <string name="bearbeiten">Bearbeiten</string>
    <string name="noch_punkte_noetig">Noch %1$d Punkte nötig</string>
    <string name="serie_zu_kurz">Die geforderte Serie ist noch nicht erreicht</string>
    <string name="noch_zu_frueh">Noch nicht freigeschaltet</string>
    <string name="einloese_historie">Eingelöst</string>
    <string name="einloesung_zeile">%1$s — %2$d Punkte</string>
    <string name="feld_beschreibung">Beschreibung</string>
    <string name="feld_preis">Preis in Punkten</string>
    <string name="feld_bedingung">Zusätzliche Bedingung</string>
    <string name="bedingung_keine">Keine</string>
    <string name="bedingung_serie">Mindestserie</string>
    <string name="bedingung_datum">Frühestens ab</string>
    <string name="feld_bedingung_aktivitaet">Bei welcher Aktivität</string>
    <string name="feld_bedingung_perioden">Wie viele Perioden am Stück</string>
    <string name="feld_bedingung_datum">Datum im Format JJJJ-MM-TT</string>
```

- [ ] **Step 9: Build und alle Tests laufen lassen**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :domain:test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add app/
git commit -m "feat(ui): Belohnungs-Shop mit Einloesen und Historie"
```

---

### Task 5: Nächste Belohnung auf dem Heute-Screen

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteZustand.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteZusammenstellung.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteViewModel.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/heute/NaechsteBelohnungTest.kt`

**Interfaces:**
- Consumes: `BelohnungRepository` aus Task 2
- Produces:
  - `NaechsteBelohnung(name: String, preisPunkte: Int, fehlendePunkte: Int)`
  - `HeuteZustand.naechsteBelohnung: NaechsteBelohnung?`
  - `NaechsteBelohnungWahl.aus(belohnungen: List<Belohnung>, kontostand: Int): NaechsteBelohnung?` — wählt die günstigste noch nicht bezahlbare Belohnung; sind alle bezahlbar, die teuerste bezahlbare; ohne Belohnungen `null`

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.ui.heute

import de.vanhake.dayorganizer.domain.model.Belohnung
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NaechsteBelohnungTest {

    private val kino = Belohnung(1, "Kinoabend", preisPunkte = 200)
    private val buch = Belohnung(2, "Neues Buch", preisPunkte = 300)
    private val ausflug = Belohnung(3, "Ausflug", preisPunkte = 800)

    @Test
    fun `die guenstigste noch nicht bezahlbare Belohnung wird gezeigt`() {
        val ergebnis = NaechsteBelohnungWahl.aus(listOf(ausflug, buch, kino), kontostand = 250)

        assertEquals(NaechsteBelohnung("Neues Buch", 300, 50), ergebnis)
    }

    @Test
    fun `sind alle bezahlbar wird die teuerste gezeigt`() {
        val ergebnis = NaechsteBelohnungWahl.aus(listOf(kino, buch), kontostand = 500)

        assertEquals(NaechsteBelohnung("Neues Buch", 300, 0), ergebnis)
    }

    @Test
    fun `ohne Belohnungen gibt es nichts zu zeigen`() {
        assertNull(NaechsteBelohnungWahl.aus(emptyList(), kontostand = 500))
    }

    @Test
    fun `archivierte Belohnungen zaehlen nicht`() {
        val ergebnis = NaechsteBelohnungWahl.aus(
            listOf(kino.copy(archiviert = true), buch),
            kontostand = 0,
        )

        assertEquals("Neues Buch", ergebnis?.name)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*NaechsteBelohnungTest*"`
Expected: Kompilierfehler `Unresolved reference: NaechsteBelohnungWahl`.

- [ ] **Step 3: Implementieren**

In `ui/heute/HeuteZustand.kt` ergänzen:

```kotlin
data class NaechsteBelohnung(
    val name: String,
    val preisPunkte: Int,
    val fehlendePunkte: Int,
)
```

und `HeuteZustand` um `val naechsteBelohnung: NaechsteBelohnung? = null` erweitern.

Neue Datei `ui/heute/NaechsteBelohnungWahl.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.heute

import de.vanhake.dayorganizer.domain.model.Belohnung

/**
 * Waehlt das Ziel, das den Punkten Sinn gibt: die naechste Belohnung, auf die
 * man noch hinsparen muss. Ist alles bezahlbar, die teuerste als Ausblick.
 */
object NaechsteBelohnungWahl {

    fun aus(belohnungen: List<Belohnung>, kontostand: Int): NaechsteBelohnung? {
        val offen = belohnungen.filterNot { it.archiviert }
        if (offen.isEmpty()) return null

        val ziel = offen.filter { it.preisPunkte > kontostand }.minByOrNull { it.preisPunkte }
            ?: offen.maxByOrNull { it.preisPunkte }
            ?: return null

        return NaechsteBelohnung(
            name = ziel.name,
            preisPunkte = ziel.preisPunkte,
            fehlendePunkte = (ziel.preisPunkte - kontostand).coerceAtLeast(0),
        )
    }
}
```

In `HeuteZusammenstellung.aus` einen weiteren Parameter `belohnungen: List<Belohnung>` ergänzen und im Ergebnis `naechsteBelohnung = NaechsteBelohnungWahl.aus(belohnungen, kontostand)` setzen. Der bestehende `HeuteZusammenstellungTest` wird um `belohnungen = emptyList()` ergänzt.

In `HeuteViewModel` den `combine`-Aufruf um `belohnungen.beobachteAktive()` erweitern; `combine` nimmt bis zu fünf Flows, die vier hier passen.

In `HeuteScreen.Kopf` unter dem Fortschrittsbalken ergänzen:

```kotlin
            zustand.naechsteBelohnung?.let { ziel ->
                Text(
                    if (ziel.fehlendePunkte > 0) {
                        stringResource(R.string.noch_bis_belohnung, ziel.fehlendePunkte, ziel.name)
                    } else {
                        stringResource(R.string.belohnung_bereit, ziel.name)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
```

In `strings.xml` ergänzen:

```xml
    <string name="noch_bis_belohnung">Noch %1$d Punkte bis %2$s</string>
    <string name="belohnung_bereit">%1$s ist freigeschaltet</string>
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, alle Tests bestanden, einschließlich der angepassten `HeuteZusammenstellungTest` und `HeuteViewModelTest`.

- [ ] **Step 5: Commit**

```bash
git add app/
git commit -m "feat(ui): naechste Belohnung als Ziel auf dem Heute-Screen"
```

---

### Task 6: StatistikRechner

**Files:**
- Create: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/rechner/StatistikRechner.kt`
- Test: `domain/src/test/kotlin/de/vanhake/dayorganizer/domain/rechner/StatistikRechnerTest.kt`

**Interfaces:**
- Consumes: `Faelligkeit`, `FaelligkeitStatus`, `Buchung` aus Plan 1, `SerienRechner` aus Plan 1 Task 6
- Produces:
  - `AktivitaetStatistik(aktivitaetId: Long, erledigt: Int, ausgelassen: Int, verpasst: Int, quote: Float, aktuelleSerie: Int, laengsteSerie: Int)` — `quote` ist `erledigt / (erledigt + verpasst)`, also ohne die bewusst ausgelassenen, und `0f`, wenn der Nenner null ist
  - `TagesBilanz(tag: LocalDate, erledigt: Int, gesamt: Int)`
  - `StatistikRechner.fuerAktivitaet(aktivitaetId: Long, faelligkeiten: List<Faelligkeit>, heute: LocalDate): AktivitaetStatistik`
  - `StatistikRechner.tagesBilanzen(faelligkeiten: List<Faelligkeit>, von: LocalDate, bis: LocalDate): List<TagesBilanz>` — lückenlos, auch für Tage ohne Fälligkeit
  - `StatistikRechner.punkteVerlauf(buchungen: List<Buchung>, von: LocalDate, bis: LocalDate): List<Pair<LocalDate, Int>>` — kumulierter Kontostand je Tag

Ausgelassene Fälligkeiten aus der Quote herauszurechnen ist eine bewusste Entscheidung: wer bewusst verzichtet, soll seine Statistik nicht verderben.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StatistikRechnerTest {

    private val rechner = StatistikRechner()
    private val start = LocalDate.of(2026, 3, 2)

    private fun tag(versatz: Long, status: FaelligkeitStatus, id: Long = versatz + 1) = Faelligkeit(
        id = id,
        aktivitaetId = 1,
        periodeVon = start.plusDays(versatz),
        periodeBis = start.plusDays(versatz),
        geplantFuer = start.plusDays(versatz).atTime(8, 0),
        sollAnzahl = 1,
        status = status,
    )

    @Test
    fun `die Quote laesst ausgelassene ausser Betracht`() {
        val historie = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.ERLEDIGT),
            tag(3, FaelligkeitStatus.VERPASST),
            tag(4, FaelligkeitStatus.AUSGELASSEN),
        )

        val statistik = rechner.fuerAktivitaet(1, historie, start.plusDays(4))

        assertEquals(3, statistik.erledigt)
        assertEquals(1, statistik.verpasst)
        assertEquals(1, statistik.ausgelassen)
        assertEquals(0.75f, statistik.quote)
    }

    @Test
    fun `ohne erledigte und verpasste ist die Quote null`() {
        val statistik = rechner.fuerAktivitaet(1, listOf(tag(0, FaelligkeitStatus.OFFEN)), start)

        assertEquals(0f, statistik.quote)
    }

    @Test
    fun `Serien werden mitgeliefert`() {
        val historie = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.VERPASST),
            tag(3, FaelligkeitStatus.ERLEDIGT),
        )

        val statistik = rechner.fuerAktivitaet(1, historie, start.plusDays(3))

        assertEquals(1, statistik.aktuelleSerie)
        assertEquals(2, statistik.laengsteSerie)
    }

    @Test
    fun `Tagesbilanzen sind lueckenlos`() {
        val historie = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(0, FaelligkeitStatus.VERPASST, id = 100),
            tag(2, FaelligkeitStatus.ERLEDIGT),
        )

        val bilanzen = rechner.tagesBilanzen(historie, start, start.plusDays(2))

        assertEquals(3, bilanzen.size)
        assertEquals(TagesBilanz(start, erledigt = 1, gesamt = 2), bilanzen[0])
        assertEquals(TagesBilanz(start.plusDays(1), erledigt = 0, gesamt = 0), bilanzen[1])
        assertEquals(TagesBilanz(start.plusDays(2), erledigt = 1, gesamt = 1), bilanzen[2])
    }

    @Test
    fun `der Punkteverlauf ist kumuliert und haelt sich an Tagen ohne Buchung`() {
        val buchungen = listOf(
            Buchung(art = BuchungsArt.ERLEDIGUNG, betrag = 10, zeitpunkt = start.atTime(8, 0)),
            Buchung(art = BuchungsArt.ERLEDIGUNG, betrag = 5, zeitpunkt = start.atTime(20, 0)),
            Buchung(
                art = BuchungsArt.EINLOESUNG, betrag = -12,
                zeitpunkt = start.plusDays(2).atTime(19, 0),
            ),
        )

        val verlauf = rechner.punkteVerlauf(buchungen, start, start.plusDays(2))

        assertEquals(
            listOf(start to 15, start.plusDays(1) to 15, start.plusDays(2) to 3),
            verlauf,
        )
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :domain:test --tests "*StatistikRechnerTest*"`
Expected: Kompilierfehler `Unresolved reference: StatistikRechner`.

- [ ] **Step 3: Implementieren**

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDate

data class AktivitaetStatistik(
    val aktivitaetId: Long,
    val erledigt: Int,
    val ausgelassen: Int,
    val verpasst: Int,
    /** erledigt / (erledigt + verpasst); bewusst Ausgelassenes zaehlt nicht dagegen. */
    val quote: Float,
    val aktuelleSerie: Int,
    val laengsteSerie: Int,
)

data class TagesBilanz(val tag: LocalDate, val erledigt: Int, val gesamt: Int)

class StatistikRechner(private val serienRechner: SerienRechner = SerienRechner()) {

    fun fuerAktivitaet(
        aktivitaetId: Long,
        faelligkeiten: List<Faelligkeit>,
        heute: LocalDate,
    ): AktivitaetStatistik {
        val eigene = faelligkeiten.filter { it.aktivitaetId == aktivitaetId }
        val erledigt = eigene.count { it.status == FaelligkeitStatus.ERLEDIGT }
        val verpasst = eigene.count { it.status == FaelligkeitStatus.VERPASST }
        val nenner = erledigt + verpasst

        return AktivitaetStatistik(
            aktivitaetId = aktivitaetId,
            erledigt = erledigt,
            ausgelassen = eigene.count { it.status == FaelligkeitStatus.AUSGELASSEN },
            verpasst = verpasst,
            quote = if (nenner == 0) 0f else erledigt.toFloat() / nenner,
            aktuelleSerie = serienRechner.aktuelleSerie(eigene, heute),
            laengsteSerie = serienRechner.laengsteSerie(eigene),
        )
    }

    fun tagesBilanzen(
        faelligkeiten: List<Faelligkeit>,
        von: LocalDate,
        bis: LocalDate,
    ): List<TagesBilanz> = generateSequence(von) { it.plusDays(1) }
        .takeWhile { !it.isAfter(bis) }
        .map { tag ->
            val desTages = faelligkeiten.filter {
                !it.periodeVon.isAfter(tag) && !it.periodeBis.isBefore(tag)
            }
            TagesBilanz(
                tag = tag,
                erledigt = desTages.count { it.status == FaelligkeitStatus.ERLEDIGT },
                gesamt = desTages.size,
            )
        }
        .toList()

    fun punkteVerlauf(
        buchungen: List<Buchung>,
        von: LocalDate,
        bis: LocalDate,
    ): List<Pair<LocalDate, Int>> {
        val jeTag = buchungen.groupBy { it.zeitpunkt.toLocalDate() }
            .mapValues { (_, liste) -> liste.sumOf { it.betrag } }

        var stand = buchungen
            .filter { it.zeitpunkt.toLocalDate().isBefore(von) }
            .sumOf { it.betrag }

        return generateSequence(von) { it.plusDays(1) }
            .takeWhile { !it.isAfter(bis) }
            .map { tag ->
                stand += jeTag[tag] ?: 0
                tag to stand
            }
            .toList()
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :domain:test --tests "*StatistikRechnerTest*"`
Expected: `BUILD SUCCESSFUL`, 5 Tests bestanden.

- [ ] **Step 5: Commit**

```bash
git add domain/
git commit -m "feat(domain): StatistikRechner fuer Quoten, Tagesbilanzen und Punkteverlauf"
```

---

### Task 7: Statistik-Screen

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/statistik/StatistikZustand.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/statistik/StatistikViewModel.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/statistik/StatistikScreen.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/statistik/Heatmap.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/BuchungDao.kt` — Buchungen eines Zeitraums
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/repository/Repositories.kt` und `RaumRepositories.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/statistik/StatistikViewModelTest.kt`

**Interfaces:**
- Consumes: `StatistikRechner` aus Task 6, `FaelligkeitRepository`, `BuchungRepository`, `AktivitaetRepository` aus Plan 1
- Produces:
  - `BuchungRepository.fuerZeitraum(von: LocalDate, bis: LocalDate): List<Buchung>` (neu)
  - `AktivitaetZeile(aktivitaetId: Long, name: String, statistik: AktivitaetStatistik)`
  - `StatistikZustand(zeitraumTage: Int, bilanzen: List<TagesBilanz>, zeilen: List<AktivitaetZeile>, punkteVerlauf: List<Pair<LocalDate, Int>>)`
  - `StatistikViewModel.zustand: StateFlow<StatistikZustand>`, `.zeitraumWaehlen(tage: Int)` mit den Stufen 30, 90 und 365

- [ ] **Step 1: DAO und Repository erweitern**

In `BuchungDao.kt`:

```kotlin
    @Query("SELECT * FROM buchung WHERE zeitpunkt >= :von AND zeitpunkt < :bis ORDER BY zeitpunkt")
    suspend fun fuerZeitraum(von: LocalDateTime, bis: LocalDateTime): List<BuchungEntity>

    @Query("SELECT * FROM buchung ORDER BY zeitpunkt")
    suspend fun alle(): List<BuchungEntity>
```

In `Repositories.kt` die Schnittstelle `BuchungRepository` erweitern:

```kotlin
    suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<Buchung>
    suspend fun alle(): List<Buchung>
```

In `RaumBuchungRepository`:

```kotlin
    override suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<Buchung> =
        dao.fuerZeitraum(von.atStartOfDay(), bis.plusDays(1).atStartOfDay()).map { it.zuDomaene() }

    override suspend fun alle(): List<Buchung> = dao.alle().map { it.zuDomaene() }
```

In `FakeBuchungRepository` entsprechend:

```kotlin
    override suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<Buchung> =
        stand.value.filter {
            val tag = it.zeitpunkt.toLocalDate()
            !tag.isBefore(von) && !tag.isAfter(bis)
        }

    override suspend fun alle(): List<Buchung> = stand.value
```

- [ ] **Step 2: Fehlschlagenden ViewModel-Test schreiben**

```kotlin
package de.vanhake.dayorganizer.ui.statistik

import app.cash.turbine.test
import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.StatistikRechner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class StatistikViewModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val heute = LocalDate.of(2026, 3, 10)
    private val uhr = Clock.fixed(heute.atTime(12, 0).atZone(zone).toInstant(), zone)
    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private val tabletten = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 1, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private fun tag(versatz: Long, status: FaelligkeitStatus) = Faelligkeit(
        id = versatz + 1, aktivitaetId = 1,
        periodeVon = heute.minusDays(versatz), periodeBis = heute.minusDays(versatz),
        geplantFuer = heute.minusDays(versatz).atTime(8, 0), sollAnzahl = 1, status = status,
    )

    @Test
    fun `der Zustand fasst Quote und Serie je Aktivitaet zusammen`() = runTest(dispatcher) {
        val vm = StatistikViewModel(
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            faelligkeiten = FakeFaelligkeitRepository(
                listOf(
                    tag(0, FaelligkeitStatus.ERLEDIGT),
                    tag(1, FaelligkeitStatus.ERLEDIGT),
                    tag(2, FaelligkeitStatus.VERPASST),
                ),
            ),
            buchungen = FakeBuchungRepository(
                listOf(
                    Buchung(
                        art = BuchungsArt.ERLEDIGUNG, betrag = 10,
                        zeitpunkt = LocalDateTime.of(2026, 3, 9, 8, 5),
                    ),
                ),
            ),
            rechner = StatistikRechner(),
            uhr = uhr,
        )

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            val zeile = zustand.zeilen.single()
            assertEquals("Tabletten", zeile.name)
            assertEquals(2, zeile.statistik.erledigt)
            assertEquals(1, zeile.statistik.verpasst)
            assertEquals(30, zustand.bilanzen.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ein anderer Zeitraum aendert die Anzahl der Tagesbilanzen`() = runTest(dispatcher) {
        val vm = StatistikViewModel(
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            faelligkeiten = FakeFaelligkeitRepository(),
            buchungen = FakeBuchungRepository(),
            rechner = StatistikRechner(),
            uhr = uhr,
        )

        vm.zustand.test {
            skipItems(2)
            vm.zeitraumWaehlen(90)
            assertEquals(90, awaitItem().bilanzen.size)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*StatistikViewModelTest*"`
Expected: Kompilierfehler `Unresolved reference: StatistikViewModel`.

- [ ] **Step 4: Zustand und ViewModel implementieren**

`ui/statistik/StatistikZustand.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.statistik

import de.vanhake.dayorganizer.domain.rechner.AktivitaetStatistik
import de.vanhake.dayorganizer.domain.rechner.TagesBilanz
import java.time.LocalDate

data class AktivitaetZeile(
    val aktivitaetId: Long,
    val name: String,
    val statistik: AktivitaetStatistik,
)

data class StatistikZustand(
    val zeitraumTage: Int = 30,
    val bilanzen: List<TagesBilanz> = emptyList(),
    val zeilen: List<AktivitaetZeile> = emptyList(),
    val punkteVerlauf: List<Pair<LocalDate, Int>> = emptyList(),
)
```

`ui/statistik/StatistikViewModel.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.statistik

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.rechner.StatistikRechner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class StatistikViewModel @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val rechner: StatistikRechner,
    private val uhr: Clock,
) : ViewModel() {

    private val zeitraum = MutableStateFlow(30)

    val zustand: StateFlow<StatistikZustand> = zeitraum
        .map { tage -> zusammenstellen(tage) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatistikZustand())

    /** Erlaubt sind 30, 90 und 365 Tage. */
    fun zeitraumWaehlen(tage: Int) {
        zeitraum.value = tage
    }

    private suspend fun zusammenstellen(tage: Int): StatistikZustand {
        val heute = LocalDate.now(uhr)
        val von = heute.minusDays(tage - 1L)

        val imZeitraum = faelligkeiten.fuerZeitraum(von, heute)
        val liste = aktivitaeten.alleAktiven()

        return StatistikZustand(
            zeitraumTage = tage,
            bilanzen = rechner.tagesBilanzen(imZeitraum, von, heute),
            zeilen = liste.map { aktivitaet ->
                AktivitaetZeile(
                    aktivitaetId = aktivitaet.id,
                    name = aktivitaet.name,
                    statistik = rechner.fuerAktivitaet(
                        aktivitaetId = aktivitaet.id,
                        faelligkeiten = faelligkeiten.historie(aktivitaet.id),
                        heute = heute,
                    ),
                )
            },
            punkteVerlauf = rechner.punkteVerlauf(buchungen.fuerZeitraum(von, heute), von, heute),
        )
    }
}
```

- [ ] **Step 5: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*StatistikViewModelTest*"`
Expected: `BUILD SUCCESSFUL`, 2 Tests bestanden.

- [ ] **Step 6: Heatmap und Screen anlegen**

`ui/statistik/Heatmap.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.statistik

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.vanhake.dayorganizer.domain.rechner.TagesBilanz
import java.time.DayOfWeek

/**
 * Kalender-Heatmap im Wochenraster: eine Spalte je Woche, sieben Zeilen von
 * Montag bis Sonntag. Die Faerbung ist der Anteil erledigter Faelligkeiten.
 */
@Composable
fun Heatmap(bilanzen: List<TagesBilanz>, modifier: Modifier = Modifier) {
    if (bilanzen.isEmpty()) return
    val basis = MaterialTheme.colorScheme.primary
    val leer = MaterialTheme.colorScheme.surfaceVariant

    val ersterTag = bilanzen.first().tag
    val versatz = ersterTag.dayOfWeek.value - DayOfWeek.MONDAY.value
    val wochen = (bilanzen.size + versatz + 6) / 7

    Canvas(modifier.fillMaxWidth().height((7 * 14).dp)) {
        val kante = minOf(size.width / wochen, size.height / 7)
        val abstand = kante * 0.15f

        bilanzen.forEachIndexed { index, bilanz ->
            val position = index + versatz
            val spalte = position / 7
            val zeile = position % 7
            val anteil = if (bilanz.gesamt == 0) 0f else bilanz.erledigt.toFloat() / bilanz.gesamt

            drawRect(
                color = if (bilanz.gesamt == 0) leer else mische(leer, basis, anteil),
                topLeft = Offset(spalte * kante, zeile * kante),
                size = Size(kante - abstand, kante - abstand),
            )
        }
    }
}

private fun mische(von: Color, nach: Color, anteil: Float): Color = Color(
    red = von.red + (nach.red - von.red) * anteil,
    green = von.green + (nach.green - von.green) * anteil,
    blue = von.blue + (nach.blue - von.blue) * anteil,
    alpha = 1f,
)
```

`ui/statistik/StatistikScreen.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.statistik

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import kotlin.math.roundToInt

@Composable
fun StatistikScreen(viewModel: StatistikViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(30, 90, 365).forEach { tage ->
                    FilterChip(
                        selected = zustand.zeitraumTage == tage,
                        onClick = { viewModel.zeitraumWaehlen(tage) },
                        label = { Text(stringResource(R.string.zeitraum_tage, tage)) },
                    )
                }
            }
        }

        item {
            Card {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.heatmap_titel),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Heatmap(zustand.bilanzen, Modifier.padding(top = 8.dp))
                }
            }
        }

        items(zustand.zeilen, key = { it.aktivitaetId }) { zeile ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(zeile.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            R.string.quote_zeile,
                            (zeile.statistik.quote * 100).roundToInt(),
                            zeile.statistik.erledigt,
                            zeile.statistik.verpasst,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        stringResource(
                            R.string.serie_zeile,
                            zeile.statistik.aktuelleSerie,
                            zeile.statistik.laengsteSerie,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
```

In `strings.xml` ergänzen:

```xml
    <string name="tab_statistik">Statistik</string>
    <string name="zeitraum_tage">%1$d Tage</string>
    <string name="heatmap_titel">Erledigt je Tag</string>
    <string name="quote_zeile">%1$d %% erledigt — %2$d geschafft, %3$d verpasst</string>
    <string name="serie_zeile">Serie: %1$d aktuell, %2$d am längsten</string>
```

In `Navigation.kt` ein viertes Tab `Ziele.STATISTIK = "statistik"` mit `StatistikScreen()` und Icon `Icons.Default.Insights` ergänzen. Die vollständige, endgültige Fassung der Datei steht in Task 8 Step 5.

- [ ] **Step 7: Build und alle Tests laufen lassen**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :domain:test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/
git commit -m "feat(ui): Statistik mit Heatmap, Quoten und Serien"
```

---

### Task 8: Einstellungen-Screen

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModel.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenScreen.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModelTest.kt`

**Interfaces:**
- Consumes: `EinstellungenSpeicher` aus Plan 1 Task 10, `BerechtigungsPruefer` und `Hinweis` aus Plan 1 Task 17
- Produces:
  - `EinstellungenZustand(tagesbonusText: String, standardKulanzText: String, hinweise: List<Hinweis>)`
  - `EinstellungenViewModel.zustand: StateFlow<EinstellungenZustand>`, `.tagesbonusAendern(text)`, `.kulanzAendern(text)`, `.speichern()`
  - Ungültige Eingaben werden beim Speichern auf die bisherigen Werte zurückgesetzt statt die Einstellung zu zerstören

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.ui.einstellungen

import app.cash.turbine.test
import de.vanhake.dayorganizer.data.Einstellungen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import kotlin.test.Test
import kotlin.test.assertEquals

class EinstellungenViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private class FakeSpeicher(start: Einstellungen = Einstellungen()) {
        val stand = MutableStateFlow(start)
    }

    private fun viewModel(speicher: FakeSpeicher) = EinstellungenViewModel(
        lesen = { speicher.stand.value },
        schreiben = { speicher.stand.value = it },
        hinweiseLesen = { emptyList() },
    )

    @Test
    fun `die gespeicherten Werte erscheinen im Zustand`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test {
            skipItems(1)
            val zustand = awaitItem()
            assertEquals("25", zustand.tagesbonusText)
            assertEquals("45", zustand.standardKulanzText)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Speichern uebernimmt gueltige Werte`() = runTest(dispatcher) {
        val speicher = FakeSpeicher()
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("30")
        vm.kulanzAendern("90")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 30, standardKulanzMinuten = 90), speicher.stand.value)
    }

    @Test
    fun `unsinnige Eingaben zerstoeren die Einstellung nicht`() = runTest(dispatcher) {
        val speicher = FakeSpeicher(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45))
        val vm = viewModel(speicher)

        vm.zustand.test { skipItems(2); cancelAndIgnoreRemainingEvents() }
        vm.tagesbonusAendern("viel")
        vm.kulanzAendern("-5")
        vm.speichern().join()

        assertEquals(Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45), speicher.stand.value)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*EinstellungenViewModelTest*"`
Expected: Kompilierfehler `Unresolved reference: EinstellungenViewModel`.

- [ ] **Step 3: ViewModel implementieren**

```kotlin
package de.vanhake.dayorganizer.ui.einstellungen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.platform.permission.BerechtigungsHinweise
import de.vanhake.dayorganizer.platform.permission.BerechtigungsPruefer
import de.vanhake.dayorganizer.platform.permission.Hinweis
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EinstellungenZustand(
    val tagesbonusText: String = "",
    val standardKulanzText: String = "",
    val hinweise: List<Hinweis> = emptyList(),
)

@HiltViewModel
class EinstellungenViewModel(
    private val lesen: suspend () -> Einstellungen,
    private val schreiben: suspend (Einstellungen) -> Unit,
    private val hinweiseLesen: () -> List<Hinweis>,
) : ViewModel() {

    @Inject
    constructor(speicher: EinstellungenSpeicher, pruefer: BerechtigungsPruefer) : this(
        lesen = { speicher.lesen() },
        schreiben = { speicher.schreiben(it) },
        hinweiseLesen = { BerechtigungsHinweise.fuer(pruefer.stand()) },
    )

    private val _zustand = MutableStateFlow(EinstellungenZustand())
    val zustand: StateFlow<EinstellungenZustand> = _zustand.asStateFlow()

    init {
        viewModelScope.launch { neuLaden() }
    }

    fun tagesbonusAendern(text: String) {
        _zustand.value = _zustand.value.copy(tagesbonusText = text)
    }

    fun kulanzAendern(text: String) {
        _zustand.value = _zustand.value.copy(standardKulanzText = text)
    }

    /**
     * Ungueltige Eingaben werden verworfen, nicht gespeichert. Ein leeres Feld
     * darf den Tagesbonus nicht auf null setzen.
     */
    fun speichern(): Job = viewModelScope.launch {
        val bisher = lesen()
        schreiben(
            Einstellungen(
                tagesbonusPunkte = _zustand.value.tagesbonusText.toIntOrNull()
                    ?.takeIf { it >= 0 } ?: bisher.tagesbonusPunkte,
                standardKulanzMinuten = _zustand.value.standardKulanzText.toIntOrNull()
                    ?.takeIf { it >= 0 } ?: bisher.standardKulanzMinuten,
            ),
        )
        neuLaden()
    }

    private suspend fun neuLaden() {
        val aktuell = lesen()
        _zustand.value = EinstellungenZustand(
            tagesbonusText = aktuell.tagesbonusPunkte.toString(),
            standardKulanzText = aktuell.standardKulanzMinuten.toString(),
            hinweise = hinweiseLesen(),
        )
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*EinstellungenViewModelTest*"`
Expected: `BUILD SUCCESSFUL`, 3 Tests bestanden.

- [ ] **Step 5: Screen anlegen**

```kotlin
package de.vanhake.dayorganizer.ui.einstellungen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.platform.permission.einstellungsIntent
import de.vanhake.dayorganizer.ui.gemeinsam.WarnBanner

@Composable
fun EinstellungenScreen(viewModel: EinstellungenViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        zustand.hinweise.forEach { hinweis ->
            WarnBanner(
                hinweis = hinweis,
                beheben = {
                    ContextCompat.startActivity(kontext, hinweis.einstellungsIntent(kontext), null)
                },
            )
        }

        OutlinedTextField(
            value = zustand.tagesbonusText,
            onValueChange = viewModel::tagesbonusAendern,
            label = { Text(stringResource(R.string.feld_tagesbonus)) },
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = zustand.standardKulanzText,
            onValueChange = viewModel::kulanzAendern,
            label = { Text(stringResource(R.string.feld_standard_kulanz)) },
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = { viewModel.speichern() }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.speichern))
        }
    }
}
```

In `strings.xml` ergänzen:

```xml
    <string name="tab_einstellungen">Einstellungen</string>
    <string name="feld_tagesbonus">Tagesbonus, wenn alles erledigt ist</string>
    <string name="feld_standard_kulanz">Standard-Kulanzfenster in Minuten</string>
```

`ui/Navigation.kt` bekommt jetzt seine endgültige Fassung mit allen fünf Zielen:

```kotlin
package de.vanhake.dayorganizer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetBearbeitenScreen
import de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetenScreen
import de.vanhake.dayorganizer.ui.belohnungen.BelohnungBearbeitenScreen
import de.vanhake.dayorganizer.ui.belohnungen.BelohnungenScreen
import de.vanhake.dayorganizer.ui.einstellungen.EinstellungenScreen
import de.vanhake.dayorganizer.ui.heute.HeuteScreen
import de.vanhake.dayorganizer.ui.statistik.StatistikScreen

object Ziele {
    const val HEUTE = "heute"
    const val AKTIVITAETEN = "aktivitaeten"
    const val BELOHNUNGEN = "belohnungen"
    const val STATISTIK = "statistik"
    const val EINSTELLUNGEN = "einstellungen"
    const val AKTIVITAET_BEARBEITEN = "aktivitaet/{id}"
    const val BELOHNUNG_BEARBEITEN = "belohnung/{id}"

    fun aktivitaetBearbeiten(id: Long) = "aktivitaet/$id"
    fun belohnungBearbeiten(id: Long) = "belohnung/$id"
}

private data class Tab(val ziel: String, val icon: ImageVector, val titelId: Int)

private val TABS = listOf(
    Tab(Ziele.HEUTE, Icons.Default.CheckCircle, R.string.tab_heute),
    Tab(Ziele.AKTIVITAETEN, Icons.Default.List, R.string.tab_aktivitaeten),
    Tab(Ziele.BELOHNUNGEN, Icons.Default.Star, R.string.tab_belohnungen),
    Tab(Ziele.STATISTIK, Icons.Default.Insights, R.string.tab_statistik),
    Tab(Ziele.EINSTELLUNGEN, Icons.Default.Settings, R.string.tab_einstellungen),
)

@Composable
fun DayOrganizerNavigation() {
    val navController = rememberNavController()
    val eintrag by navController.currentBackStackEntryAsState()
    val aktuell = eintrag?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                TABS.forEach { tab ->
                    NavigationBarItem(
                        selected = aktuell == tab.ziel,
                        onClick = {
                            navController.navigate(tab.ziel) {
                                popUpTo(Ziele.HEUTE)
                                launchSingleTop = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.titelId)) },
                    )
                }
            }
        },
    ) { innen ->
        NavHost(
            navController = navController,
            startDestination = Ziele.HEUTE,
            modifier = Modifier.padding(innen),
        ) {
            composable(Ziele.HEUTE) { HeuteScreen() }

            composable(Ziele.AKTIVITAETEN) {
                AktivitaetenScreen(
                    bearbeiten = { id -> navController.navigate(Ziele.aktivitaetBearbeiten(id)) },
                )
            }

            composable(Ziele.BELOHNUNGEN) {
                BelohnungenScreen(
                    bearbeiten = { id -> navController.navigate(Ziele.belohnungBearbeiten(id)) },
                )
            }

            composable(Ziele.STATISTIK) { StatistikScreen() }
            composable(Ziele.EINSTELLUNGEN) { EinstellungenScreen() }

            composable(Ziele.AKTIVITAET_BEARBEITEN) { eintrag ->
                AktivitaetBearbeitenScreen(
                    id = eintrag.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    fertig = { navController.popBackStack() },
                )
            }

            composable(Ziele.BELOHNUNG_BEARBEITEN) { eintrag ->
                BelohnungBearbeitenScreen(
                    id = eintrag.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    fertig = { navController.popBackStack() },
                )
            }
        }
    }
}
```

`popUpTo(Ziele.HEUTE)` mit `launchSingleTop` verhindert, dass sich beim Hin- und Herwechseln zwischen den Tabs ein endloser Zurück-Stapel aufbaut.

- [ ] **Step 6: Build und alle Tests laufen lassen**

Run: `./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/
git commit -m "feat(ui): Einstellungen fuer Tagesbonus, Kulanz und Berechtigungen"
```

---

## Abschluss von Plan 2

Die Punkte haben jetzt ein Ziel und der Fortschritt ist sichtbar. Es fehlen noch Widget und Backup — Plan 3.

Vollständiger Durchlauf vor dem Übergang:

```bash
./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug
```
