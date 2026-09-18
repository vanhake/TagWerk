# DayOrganizer — Plan 3: Widget und Backup

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Voraussetzung:** Plan 1 und Plan 2 sind vollständig umgesetzt.

**Goal:** Die App wird alltagstauglich und verlustsicher: heutige Aufgaben direkt vom Startbildschirm abhaken, und alle Daten in eine Datei sichern und zurückholen.

**Architecture:** Das Backup ist eine versionierte JSON-Datei über kotlinx.serialization, geschrieben und gelesen über den System-Dateidialog — die App braucht keine Speicherberechtigung. Das Widget nutzt Glance und schreibt über denselben `FaelligkeitErledigen`-Use-Case wie App und Benachrichtigung; es gibt keinen zweiten Schreibpfad.

**Tech Stack:** wie Plan 1, zusätzlich kotlinx.serialization und Glance.

## Global Constraints

- Alle Constraints aus Plan 1 und 2 gelten weiter.
- Das Backup trägt eine `schemaVersion`. Der Import lehnt eine unbekannte Version rundheraus ab, statt einen Teilimport zu versuchen — eine halb importierte Datenbank wäre schlimmer als gar kein Import.
- Der Import ersetzt den gesamten Bestand, er mischt nicht. Mischen würde bei Punktbuchungen zu Doppelzählungen führen.
- Das Widget schreibt ausschließlich über bestehende Use Cases. Direkter DAO-Zugriff aus dem Widget ist ein Designfehler.
- Nach jedem Schreibvorgang aus Widget, App oder Benachrichtigung wird das Widget aktualisiert und der Alarm neu geplant.

## File Structure

```
app/src/main/kotlin/de/vanhake/dayorganizer/
    backup/BackupModell.kt        serialisierbare Abbilder aller Tabellen
    backup/BackupDienst.kt        exportieren und importieren
    ui/einstellungen/             Export- und Import-Knopf
    widget/HeuteWidget.kt         Glance-Widget
    widget/HeuteWidgetEmpfaenger.kt
    widget/WidgetAktualisierung.kt
app/src/main/res/xml/heute_widget_info.xml
```

---

### Task 1: Backup-Modell und Serialisierung

**Files:**
- Modify: `gradle/libs.versions.toml`, `app/build.gradle.kts`, Wurzel-`build.gradle.kts`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/backup/BackupModell.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlung.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/backup/BackupUmwandlungTest.kt`

**Interfaces:**
- Consumes: alle Domänenmodelle aus Plan 1 und 2
- Produces:
  - `BackupDatei(schemaVersion: Int, erstelltAm: String, aktivitaeten: List<AktivitaetDto>, faelligkeiten: List<FaelligkeitDto>, buchungen: List<BuchungDto>, belohnungen: List<BelohnungDto>, einloesungen: List<EinloesungDto>, einstellungen: EinstellungenDto)`
  - `BackupUmwandlung.zuDatei(...): BackupDatei` und `BackupUmwandlung.ausDatei(datei: BackupDatei): BackupInhalt`
  - `BackupUmwandlung.SCHEMA_VERSION = 1`
  - `class UnbekanntesSchema(val gefunden: Int) : Exception`

Erinnerungstermine werden bewusst **nicht** gesichert: sie sind vollständig aus Aktivitäten und Fälligkeiten ableitbar und werden nach dem Import vom Wächter neu erzeugt. Alles andere wäre doppelte Wahrheit.

- [ ] **Step 1: Abhängigkeiten ergänzen**

In `gradle/libs.versions.toml`:

```toml
kotlinxSerialization = "1.7.3"
glance = "1.1.1"
```

```toml
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "glance" }
```

```toml
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

In der Wurzel-`build.gradle.kts` `alias(libs.plugins.kotlin.serialization) apply false` ergänzen, in `app/build.gradle.kts` das Plugin aktivieren und die drei Bibliotheken als `implementation` eintragen.

- [ ] **Step 2: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.backup

import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupUmwandlungTest {

    private val aktivitaet = Aktivitaet(
        id = 1, name = "Tabletten", notiz = "morgens und abends", farbe = 42, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(
            listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
            setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
        ),
        kulanzMinuten = 45, puenktlichkeitsBonus = 5,
        serienSchwellen = listOf(SerienSchwelle(7, 10)),
        nachfassAnzahl = 2, nachfassAbstandMinuten = 15,
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private val faelligkeit = Faelligkeit(
        id = 10, aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 2), periodeBis = LocalDate.of(2026, 3, 2),
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0), sollAnzahl = 1, istAnzahl = 1,
        status = FaelligkeitStatus.ERLEDIGT,
        erledigtAm = LocalDateTime.of(2026, 3, 2, 8, 12), warPuenktlich = true,
    )

    private val buchung = Buchung(
        id = 5, art = BuchungsArt.SERIENBONUS, betrag = 10,
        zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 12),
        aktivitaetId = 1, faelligkeitId = 10, schwelle = 7,
    )

    private val belohnung = Belohnung(
        id = 3, name = "Kinoabend", beschreibung = "mit Popcorn", preisPunkte = 200,
        bedingung = Belohnungsbedingung.MindestSerie(1, 14),
    )

    private val einloesung = Einloesung(
        id = 2, belohnungId = 3, name = "Kinoabend", preisPunkte = 200,
        zeitpunkt = LocalDateTime.of(2026, 3, 2, 19, 0),
    )

    private fun datei() = BackupUmwandlung.zuDatei(
        erstelltAm = LocalDateTime.of(2026, 3, 2, 21, 0),
        aktivitaeten = listOf(aktivitaet),
        faelligkeiten = listOf(faelligkeit),
        buchungen = listOf(buchung),
        belohnungen = listOf(belohnung),
        einloesungen = listOf(einloesung),
        einstellungen = Einstellungen(tagesbonusPunkte = 25, standardKulanzMinuten = 45),
    )

    @Test
    fun `alle Daten ueberstehen den Rundlauf durch JSON`() {
        val json = Json.encodeToString(BackupDatei.serializer(), datei())
        val zurueck = BackupUmwandlung.ausDatei(Json.decodeFromString(BackupDatei.serializer(), json))

        assertEquals(listOf(aktivitaet), zurueck.aktivitaeten)
        assertEquals(listOf(faelligkeit), zurueck.faelligkeiten)
        assertEquals(listOf(buchung), zurueck.buchungen)
        assertEquals(listOf(belohnung), zurueck.belohnungen)
        assertEquals(listOf(einloesung), zurueck.einloesungen)
        assertEquals(25, zurueck.einstellungen.tagesbonusPunkte)
    }

    @Test
    fun `die Schemaversion wird mitgeschrieben`() {
        assertEquals(BackupUmwandlung.SCHEMA_VERSION, datei().schemaVersion)
    }

    @Test
    fun `eine unbekannte Schemaversion wird abgelehnt`() {
        val fremd = datei().copy(schemaVersion = 99)

        val fehler = assertFailsWith<UnbekanntesSchema> { BackupUmwandlung.ausDatei(fremd) }

        assertEquals(99, fehler.gefunden)
    }

    @Test
    fun `alle vier Zeitplantypen ueberstehen den Rundlauf`() {
        val plaene = listOf(
            Zeitplan.FesteZeiten(listOf(LocalTime.of(7, 30)), setOf(DayOfWeek.FRIDAY)),
            Zeitplan.Intervall(3, LocalTime.of(8, 0), LocalTime.of(22, 0), setOf(DayOfWeek.MONDAY)),
            Zeitplan.Tagesziel(3, setOf(DayOfWeek.SUNDAY), listOf(LocalTime.of(12, 0))),
            Zeitplan.Wochenziel(
                3,
                listOf(de.vanhake.dayorganizer.domain.model.Anstoss(DayOfWeek.TUESDAY, LocalTime.of(18, 0))),
            ),
        )

        plaene.forEach { plan ->
            val eine = aktivitaet.copy(zeitplan = plan)
            val datei = BackupUmwandlung.zuDatei(
                erstelltAm = LocalDateTime.of(2026, 3, 2, 21, 0),
                aktivitaeten = listOf(eine),
                faelligkeiten = emptyList(), buchungen = emptyList(),
                belohnungen = emptyList(), einloesungen = emptyList(),
                einstellungen = Einstellungen(),
            )
            val json = Json.encodeToString(BackupDatei.serializer(), datei)
            val zurueck = BackupUmwandlung.ausDatei(Json.decodeFromString(BackupDatei.serializer(), json))

            assertEquals(plan, zurueck.aktivitaeten.single().zeitplan)
        }
    }
}
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BackupUmwandlungTest*"`
Expected: Kompilierfehler `Unresolved reference: BackupDatei`.

- [ ] **Step 4: Implementieren**

`backup/BackupModell.kt`:

```kotlin
package de.vanhake.dayorganizer.backup

import kotlinx.serialization.Serializable

/**
 * Alle Felder sind Text oder Zahl. Datums- und Zeitwerte werden als
 * ISO-8601-Text gefuehrt, damit die Datei ohne die App lesbar bleibt.
 */
@Serializable
data class BackupDatei(
    val schemaVersion: Int,
    val erstelltAm: String,
    val aktivitaeten: List<AktivitaetDto>,
    val faelligkeiten: List<FaelligkeitDto>,
    val buchungen: List<BuchungDto>,
    val belohnungen: List<BelohnungDto>,
    val einloesungen: List<EinloesungDto>,
    val einstellungen: EinstellungenDto,
)

@Serializable
data class AktivitaetDto(
    val id: Long,
    val name: String,
    val notiz: String,
    val farbe: Int,
    val basisPunkte: Int,
    val kulanzMinuten: Int,
    val puenktlichkeitsBonus: Int,
    /** "7:10,30:50" */
    val serienSchwellen: String,
    val nachfassAnzahl: Int,
    val nachfassAbstandMinuten: Int,
    val aktiv: Boolean,
    val erstelltAm: String,
    val archiviertAm: String?,
    val zeitplanTyp: String,
    val zeitplanZeiten: String,
    val zeitplanWochentage: String,
    val zeitplanIntervallStunden: Int?,
    val zeitplanFensterVon: String?,
    val zeitplanFensterBis: String?,
    val zeitplanZielAnzahl: Int?,
    val zeitplanAnstoss: String,
)

@Serializable
data class FaelligkeitDto(
    val id: Long,
    val aktivitaetId: Long,
    val periodeVon: String,
    val periodeBis: String,
    val geplantFuer: String?,
    val sollAnzahl: Int,
    val istAnzahl: Int,
    val status: String,
    val erledigtAm: String?,
    val warPuenktlich: Boolean,
)

@Serializable
data class BuchungDto(
    val id: Long,
    val art: String,
    val betrag: Int,
    val zeitpunkt: String,
    val aktivitaetId: Long?,
    val faelligkeitId: Long?,
    val belohnungId: Long?,
    val schwelle: Int?,
    val notiz: String,
)

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
)

@Serializable
data class EinloesungDto(
    val id: Long,
    val belohnungId: Long,
    val name: String,
    val preisPunkte: Int,
    val zeitpunkt: String,
)

@Serializable
data class EinstellungenDto(
    val tagesbonusPunkte: Int,
    val standardKulanzMinuten: Int,
)
```

`backup/BackupUmwandlung.kt`:

```kotlin
package de.vanhake.dayorganizer.backup

import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.mapper.ZeitplanFelder
import de.vanhake.dayorganizer.data.mapper.ZeitplanKodierung
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDate
import java.time.LocalDateTime

class UnbekanntesSchema(val gefunden: Int) :
    Exception("Backup-Schema $gefunden wird von dieser Version nicht gelesen")

data class BackupInhalt(
    val aktivitaeten: List<Aktivitaet>,
    val faelligkeiten: List<Faelligkeit>,
    val buchungen: List<Buchung>,
    val belohnungen: List<Belohnung>,
    val einloesungen: List<Einloesung>,
    val einstellungen: Einstellungen,
)

object BackupUmwandlung {

    const val SCHEMA_VERSION = 1

    fun zuDatei(
        erstelltAm: LocalDateTime,
        aktivitaeten: List<Aktivitaet>,
        faelligkeiten: List<Faelligkeit>,
        buchungen: List<Buchung>,
        belohnungen: List<Belohnung>,
        einloesungen: List<Einloesung>,
        einstellungen: Einstellungen,
    ) = BackupDatei(
        schemaVersion = SCHEMA_VERSION,
        erstelltAm = erstelltAm.toString(),
        aktivitaeten = aktivitaeten.map { it.zuDto() },
        faelligkeiten = faelligkeiten.map { it.zuDto() },
        buchungen = buchungen.map { it.zuDto() },
        belohnungen = belohnungen.map { it.zuDto() },
        einloesungen = einloesungen.map { it.zuDto() },
        einstellungen = EinstellungenDto(
            tagesbonusPunkte = einstellungen.tagesbonusPunkte,
            standardKulanzMinuten = einstellungen.standardKulanzMinuten,
        ),
    )

    fun ausDatei(datei: BackupDatei): BackupInhalt {
        if (datei.schemaVersion != SCHEMA_VERSION) throw UnbekanntesSchema(datei.schemaVersion)

        return BackupInhalt(
            aktivitaeten = datei.aktivitaeten.map { it.zuDomaene() },
            faelligkeiten = datei.faelligkeiten.map { it.zuDomaene() },
            buchungen = datei.buchungen.map { it.zuDomaene() },
            belohnungen = datei.belohnungen.map { it.zuDomaene() },
            einloesungen = datei.einloesungen.map { it.zuDomaene() },
            einstellungen = Einstellungen(
                tagesbonusPunkte = datei.einstellungen.tagesbonusPunkte,
                standardKulanzMinuten = datei.einstellungen.standardKulanzMinuten,
            ),
        )
    }

    private fun Aktivitaet.zuDto(): AktivitaetDto {
        val felder = ZeitplanKodierung.kodiere(zeitplan)
        return AktivitaetDto(
            id = id, name = name, notiz = notiz, farbe = farbe, basisPunkte = basisPunkte,
            kulanzMinuten = kulanzMinuten, puenktlichkeitsBonus = puenktlichkeitsBonus,
            serienSchwellen = ZeitplanKodierung.kodiereSchwellen(serienSchwellen),
            nachfassAnzahl = nachfassAnzahl, nachfassAbstandMinuten = nachfassAbstandMinuten,
            aktiv = aktiv, erstelltAm = erstelltAm.toString(),
            archiviertAm = archiviertAm?.toString(),
            zeitplanTyp = felder.typ, zeitplanZeiten = felder.zeiten,
            zeitplanWochentage = felder.wochentage,
            zeitplanIntervallStunden = felder.intervallStunden,
            zeitplanFensterVon = felder.fensterVon, zeitplanFensterBis = felder.fensterBis,
            zeitplanZielAnzahl = felder.zielAnzahl, zeitplanAnstoss = felder.anstoss,
        )
    }

    private fun AktivitaetDto.zuDomaene() = Aktivitaet(
        id = id, name = name, notiz = notiz, farbe = farbe, basisPunkte = basisPunkte,
        zeitplan = ZeitplanKodierung.dekodiere(
            ZeitplanFelder(
                typ = zeitplanTyp, zeiten = zeitplanZeiten, wochentage = zeitplanWochentage,
                intervallStunden = zeitplanIntervallStunden,
                fensterVon = zeitplanFensterVon, fensterBis = zeitplanFensterBis,
                zielAnzahl = zeitplanZielAnzahl, anstoss = zeitplanAnstoss,
            ),
        ),
        kulanzMinuten = kulanzMinuten, puenktlichkeitsBonus = puenktlichkeitsBonus,
        serienSchwellen = ZeitplanKodierung.dekodiereSchwellen(serienSchwellen),
        nachfassAnzahl = nachfassAnzahl, nachfassAbstandMinuten = nachfassAbstandMinuten,
        aktiv = aktiv, erstelltAm = LocalDate.parse(erstelltAm),
        archiviertAm = archiviertAm?.let(LocalDate::parse),
    )

    private fun Faelligkeit.zuDto() = FaelligkeitDto(
        id = id, aktivitaetId = aktivitaetId,
        periodeVon = periodeVon.toString(), periodeBis = periodeBis.toString(),
        geplantFuer = geplantFuer?.toString(), sollAnzahl = sollAnzahl, istAnzahl = istAnzahl,
        status = status.name, erledigtAm = erledigtAm?.toString(), warPuenktlich = warPuenktlich,
    )

    private fun FaelligkeitDto.zuDomaene() = Faelligkeit(
        id = id, aktivitaetId = aktivitaetId,
        periodeVon = LocalDate.parse(periodeVon), periodeBis = LocalDate.parse(periodeBis),
        geplantFuer = geplantFuer?.let(LocalDateTime::parse),
        sollAnzahl = sollAnzahl, istAnzahl = istAnzahl,
        status = FaelligkeitStatus.valueOf(status),
        erledigtAm = erledigtAm?.let(LocalDateTime::parse), warPuenktlich = warPuenktlich,
    )

    private fun Buchung.zuDto() = BuchungDto(
        id = id, art = art.name, betrag = betrag, zeitpunkt = zeitpunkt.toString(),
        aktivitaetId = aktivitaetId, faelligkeitId = faelligkeitId, belohnungId = belohnungId,
        schwelle = schwelle, notiz = notiz,
    )

    private fun BuchungDto.zuDomaene() = Buchung(
        id = id, art = BuchungsArt.valueOf(art), betrag = betrag,
        zeitpunkt = LocalDateTime.parse(zeitpunkt),
        aktivitaetId = aktivitaetId, faelligkeitId = faelligkeitId, belohnungId = belohnungId,
        schwelle = schwelle, notiz = notiz,
    )

    private fun Belohnung.zuDto() = BelohnungDto(
        id = id, name = name, beschreibung = beschreibung, preisPunkte = preisPunkte,
        bedingungTyp = when (bedingung) {
            null -> null
            is Belohnungsbedingung.MindestSerie -> "MINDESTSERIE"
            is Belohnungsbedingung.FruehestensAb -> "FRUEHESTENS_AB"
        },
        bedingungAktivitaetId = (bedingung as? Belohnungsbedingung.MindestSerie)?.aktivitaetId,
        bedingungPerioden = (bedingung as? Belohnungsbedingung.MindestSerie)?.perioden,
        bedingungDatum = (bedingung as? Belohnungsbedingung.FruehestensAb)?.datum?.toString(),
        archiviert = archiviert,
    )

    private fun BelohnungDto.zuDomaene() = Belohnung(
        id = id, name = name, beschreibung = beschreibung, preisPunkte = preisPunkte,
        bedingung = when (bedingungTyp) {
            "MINDESTSERIE" -> Belohnungsbedingung.MindestSerie(
                requireNotNull(bedingungAktivitaetId), requireNotNull(bedingungPerioden),
            )
            "FRUEHESTENS_AB" -> Belohnungsbedingung.FruehestensAb(
                LocalDate.parse(requireNotNull(bedingungDatum)),
            )
            null -> null
            else -> error("Unbekannte Belohnungsbedingung: $bedingungTyp")
        },
        archiviert = archiviert,
    )

    private fun Einloesung.zuDto() = EinloesungDto(
        id = id, belohnungId = belohnungId, name = name,
        preisPunkte = preisPunkte, zeitpunkt = zeitpunkt.toString(),
    )

    private fun EinloesungDto.zuDomaene() = Einloesung(
        id = id, belohnungId = belohnungId, name = name,
        preisPunkte = preisPunkte, zeitpunkt = LocalDateTime.parse(zeitpunkt),
    )
}
```

- [ ] **Step 5: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BackupUmwandlungTest*"`
Expected: `BUILD SUCCESSFUL`, 4 Tests bestanden.

- [ ] **Step 6: Commit**

```bash
git add app/ gradle/ build.gradle.kts
git commit -m "feat(backup): versioniertes JSON-Modell fuer den Datenexport"
```

---

### Task 2: Export und Import

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/backup/BackupDienst.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/AktivitaetDao.kt`, `FaelligkeitDao.kt`, `BuchungDao.kt`, `BelohnungDao.kt` — Leeren und Sammelinsert
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/repository/Repositories.kt` und die Room-Implementierungen
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModel.kt` und `EinstellungenScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/androidTest/kotlin/de/vanhake/dayorganizer/backup/BackupDienstTest.kt`

**Interfaces:**
- Consumes: `BackupUmwandlung` aus Task 1, alle Repositories
- Produces:
  - `BackupDienst.exportieren(ziel: OutputStream)` — schreibt formatiertes JSON
  - `sealed interface ImportErgebnis` mit `Erfolg(anzahlAktivitaeten: Int)`, `FalschesSchema(gefunden: Int)`, `Unlesbar(grund: String)`
  - `BackupDienst.importieren(quelle: InputStream): ImportErgebnis` — ersetzt den gesamten Bestand in einer Transaktion und stößt danach Wächter und Alarmplanung an
  - `BackupSpeicher.alleLeeren()` in der Datenbank-Fassade

- [ ] **Step 1: DAOs und Repositories um Sammeloperationen erweitern**

`AktivitaetDao` ergänzen:

```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun alleEinfuegen(eintraege: List<AktivitaetEntity>)

    @Query("DELETE FROM aktivitaet")
    suspend fun alleLoeschen()

    @Query("SELECT * FROM aktivitaet ORDER BY id")
    suspend fun alle(): List<AktivitaetEntity>
```

`FaelligkeitDao` ergänzen:

```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun alleEinfuegen(eintraege: List<FaelligkeitEntity>)

    @Query("DELETE FROM faelligkeit")
    suspend fun alleLoeschen()

    @Query("SELECT * FROM faelligkeit ORDER BY id")
    suspend fun alle(): List<FaelligkeitEntity>
```

`ErinnerungDao` ergänzen:

```kotlin
    @Query("DELETE FROM erinnerung")
    suspend fun alleLoeschen()
```

`BuchungDao` ergänzen — `alle()` existiert bereits seit Plan 2 Task 7 und darf
nicht ein zweites Mal deklariert werden:

```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun alleEinfuegen(eintraege: List<BuchungEntity>)

    @Query("DELETE FROM buchung")
    suspend fun alleLoeschen()
```

`BelohnungDao` ergänzen:

```kotlin
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun alleEinfuegen(eintraege: List<BelohnungEntity>)

    @Query("DELETE FROM belohnung")
    suspend fun alleLoeschen()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun alleEinloesungenEinfuegen(eintraege: List<EinloesungEntity>)

    @Query("DELETE FROM einloesung")
    suspend fun alleEinloesungenLoeschen()
```

Die Löschreihenfolge ist wegen der Fremdschlüssel bindend: erst `erinnerung`,
dann `faelligkeit`, dann `aktivitaet`. `buchung`, `belohnung` und `einloesung`
haben keine Fremdschlüssel und sind in der Reihenfolge frei.

Der `BackupDienst` greift bewusst direkt auf die DAOs zu statt auf die
Repositories: er arbeitet auf ganzen Tabellen, und die Repository-Schnittstellen
um Massenoperationen aufzublähen, die nur das Backup braucht, wäre der
schlechtere Tausch.

- [ ] **Step 2: Fehlschlagenden Test schreiben**

`app/src/androidTest/kotlin/de/vanhake/dayorganizer/backup/BackupDienstTest.kt`:

```kotlin
package de.vanhake.dayorganizer.backup

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import de.vanhake.dayorganizer.data.db.AktivitaetEntity
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupDienstTest {

    private lateinit var db: DayOrganizerDatenbank
    private lateinit var dienst: BackupDienst

    @Before
    fun aufbauen() {
        val kontext = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(kontext, DayOrganizerDatenbank::class.java)
            .allowMainThreadQueries()
            .build()
        dienst = BackupDienst.fuerTest(db)
    }

    @After
    fun abbauen() = db.close()

    private fun aktivitaet(name: String) = AktivitaetEntity(
        name = name, notiz = "", farbe = 1, basisPunkte = 10, kulanzMinuten = 60,
        puenktlichkeitsBonus = 5, serienSchwellen = "7:10", nachfassAnzahl = 2,
        nachfassAbstandMinuten = 15, aktiv = true, erstelltAm = LocalDate.of(2026, 1, 1),
        archiviertAm = null, zeitplanTyp = "FESTE_ZEITEN", zeitplanZeiten = "08:00",
        zeitplanWochentage = "MONDAY", zeitplanIntervallStunden = null,
        zeitplanFensterVon = null, zeitplanFensterBis = null, zeitplanZielAnzahl = null,
        zeitplanAnstoss = "",
    )

    @Test
    fun export_und_import_stellen_den_Bestand_wieder_her() = runTest {
        db.aktivitaetDao().einfuegen(aktivitaet("Tabletten"))
        db.aktivitaetDao().einfuegen(aktivitaet("Sport"))

        val puffer = ByteArrayOutputStream()
        dienst.exportieren(puffer)

        db.aktivitaetDao().alleLoeschen()
        assertEquals(0, db.aktivitaetDao().alle().size)

        val ergebnis = dienst.importieren(ByteArrayInputStream(puffer.toByteArray()))

        assertTrue(ergebnis is ImportErgebnis.Erfolg)
        assertEquals(
            listOf("Sport", "Tabletten"),
            db.aktivitaetDao().alle().map { it.name }.sorted(),
        )
    }

    @Test
    fun ein_Import_ersetzt_statt_zu_mischen() = runTest {
        db.aktivitaetDao().einfuegen(aktivitaet("Tabletten"))
        val puffer = ByteArrayOutputStream()
        dienst.exportieren(puffer)

        db.aktivitaetDao().einfuegen(aktivitaet("Spaeter dazugekommen"))
        dienst.importieren(ByteArrayInputStream(puffer.toByteArray()))

        assertEquals(listOf("Tabletten"), db.aktivitaetDao().alle().map { it.name })
    }

    @Test
    fun eine_fremde_Schemaversion_wird_abgelehnt() = runTest {
        val fremd = """{"schemaVersion":99,"erstelltAm":"2026-03-02T21:00",""" +
            """"aktivitaeten":[],"faelligkeiten":[],"buchungen":[],"belohnungen":[],""" +
            """"einloesungen":[],"einstellungen":{"tagesbonusPunkte":15,"standardKulanzMinuten":60}}"""

        val ergebnis = dienst.importieren(ByteArrayInputStream(fremd.toByteArray()))

        assertEquals(ImportErgebnis.FalschesSchema(99), ergebnis)
    }

    @Test
    fun beschaedigtes_JSON_laesst_den_Bestand_unberuehrt() = runTest {
        db.aktivitaetDao().einfuegen(aktivitaet("Tabletten"))

        val ergebnis = dienst.importieren(ByteArrayInputStream("{kein json".toByteArray()))

        assertTrue(ergebnis is ImportErgebnis.Unlesbar)
        assertEquals(1, db.aktivitaetDao().alle().size)
    }
}
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*BackupDienstTest*"`
Expected: Kompilierfehler `Unresolved reference: BackupDienst`.

- [ ] **Step 4: Dienst implementieren**

```kotlin
package de.vanhake.dayorganizer.backup

import androidx.room.withTransaction
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import de.vanhake.dayorganizer.data.mapper.zuDomaene
import de.vanhake.dayorganizer.data.mapper.zuEntity
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

sealed interface ImportErgebnis {
    data class Erfolg(val anzahlAktivitaeten: Int) : ImportErgebnis
    data class FalschesSchema(val gefunden: Int) : ImportErgebnis
    data class Unlesbar(val grund: String) : ImportErgebnis
}

class BackupDienst @Inject constructor(
    private val db: DayOrganizerDatenbank,
    private val einstellungenLesen: suspend () -> Einstellungen,
    private val einstellungenSchreiben: suspend (Einstellungen) -> Unit,
    private val uhr: Clock,
) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = false }

    suspend fun exportieren(ziel: OutputStream) {
        val datei = BackupUmwandlung.zuDatei(
            erstelltAm = LocalDateTime.now(uhr),
            aktivitaeten = db.aktivitaetDao().alle().map { it.zuDomaene() },
            faelligkeiten = db.faelligkeitDao().alle().map { it.zuDomaene() },
            buchungen = db.buchungDao().alle().map { it.zuDomaene() },
            belohnungen = db.belohnungDao().alle().map { it.zuDomaene() },
            einloesungen = db.belohnungDao().alleEinloesungen().map { it.zuDomaene() },
            einstellungen = einstellungenLesen(),
        )
        ziel.bufferedWriter().use { it.write(json.encodeToString(BackupDatei.serializer(), datei)) }
    }

    /**
     * Ersetzt den gesamten Bestand. Mischen waere gefaehrlich: gleiche
     * Buchungs-Ids in beiden Bestaenden wuerden Punkte doppelt zaehlen.
     */
    suspend fun importieren(quelle: InputStream): ImportErgebnis {
        val inhalt = try {
            val text = quelle.bufferedReader().use { it.readText() }
            BackupUmwandlung.ausDatei(json.decodeFromString(BackupDatei.serializer(), text))
        } catch (fehler: UnbekanntesSchema) {
            return ImportErgebnis.FalschesSchema(fehler.gefunden)
        } catch (fehler: Exception) {
            return ImportErgebnis.Unlesbar(fehler.message ?: fehler::class.simpleName.orEmpty())
        }

        db.withTransaction {
            // Reihenfolge wegen der Fremdschluessel: Kinder vor Eltern loeschen.
            db.erinnerungDao().alleLoeschen()
            db.faelligkeitDao().alleLoeschen()
            db.aktivitaetDao().alleLoeschen()
            db.buchungDao().alleLoeschen()
            db.belohnungDao().alleEinloesungenLoeschen()
            db.belohnungDao().alleLoeschen()

            db.aktivitaetDao().alleEinfuegen(inhalt.aktivitaeten.map { it.zuEntity() })
            db.faelligkeitDao().alleEinfuegen(inhalt.faelligkeiten.map { it.zuEntity() })
            db.buchungDao().alleEinfuegen(inhalt.buchungen.map { it.zuEntity() })
            db.belohnungDao().alleEinfuegen(inhalt.belohnungen.map { it.zuEntity() })
            db.belohnungDao().alleEinloesungenEinfuegen(inhalt.einloesungen.map { it.zuEntity() })
        }

        einstellungenSchreiben(inhalt.einstellungen)
        return ImportErgebnis.Erfolg(inhalt.aktivitaeten.size)
    }

    companion object {
        /** Konstruktion ohne DataStore, fuer den Instrumented-Test. */
        fun fuerTest(db: DayOrganizerDatenbank): BackupDienst {
            var gehalten = Einstellungen()
            return BackupDienst(
                db = db,
                einstellungenLesen = { gehalten },
                einstellungenSchreiben = { gehalten = it },
                uhr = Clock.systemDefaultZone(),
            )
        }
    }
}
```

Für die Hilt-Bereitstellung in `DatenModul.kt`:

```kotlin
    @Provides
    @Singleton
    fun backupDienst(
        db: DayOrganizerDatenbank,
        speicher: EinstellungenSpeicher,
        uhr: Clock,
    ): BackupDienst = BackupDienst(
        db = db,
        einstellungenLesen = { speicher.lesen() },
        einstellungenSchreiben = { speicher.schreiben(it) },
        uhr = uhr,
    )
```

- [ ] **Step 5: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:connectedDebugAndroidTest --tests "*BackupDienstTest*"`
Expected: `BUILD SUCCESSFUL`, 4 Tests bestanden.

- [ ] **Step 6: Oberfläche anbinden**

Der Konstruktor von `EinstellungenViewModel` bekommt zwei weitere
Abhängigkeiten. Der primäre Konstruktor nimmt sie als Werte, der `@Inject`-
Konstruktor reicht sie durch:

```kotlin
@HiltViewModel
class EinstellungenViewModel(
    private val lesen: suspend () -> Einstellungen,
    private val schreiben: suspend (Einstellungen) -> Unit,
    private val hinweiseLesen: () -> List<Hinweis>,
    private val backupDienst: Backup,
    private val nachImport: () -> Unit,
) : ViewModel() {

    @Inject
    constructor(
        speicher: EinstellungenSpeicher,
        pruefer: BerechtigungsPruefer,
        backupDienst: BackupDienst,
        waechterPlanung: WaechterPlanung,
    ) : this(
        lesen = { speicher.lesen() },
        schreiben = { speicher.schreiben(it) },
        hinweiseLesen = { BerechtigungsHinweise.fuer(pruefer.stand()) },
        backupDienst = backupDienst,
        nachImport = { waechterPlanung.sofortAusfuehren() },
    )
```

Damit die drei Unit-Tests aus Plan 2 Task 8 ohne Datenbank weiterlaufen,
bekommt `BackupDienst` eine schmale Schnittstelle, die das ViewModel benutzt.
In `backup/BackupDienst.kt` ergänzen:

```kotlin
interface Backup {
    suspend fun exportieren(ziel: OutputStream)
    suspend fun importieren(quelle: InputStream): ImportErgebnis
}
```

`class BackupDienst ... : Backup` implementiert sie, die beiden Methoden
bekommen `override`. Das ViewModel hält `private val backupDienst: Backup`.
Der Testdouble in `EinstellungenViewModelTest`:

```kotlin
    private class FakeBackup : Backup {
        var exportiert = false
        var importiert = false
        override suspend fun exportieren(ziel: java.io.OutputStream) { exportiert = true }
        override suspend fun importieren(quelle: java.io.InputStream): ImportErgebnis {
            importiert = true
            return ImportErgebnis.Erfolg(0)
        }
    }
```

`WaechterPlanung` wird in den Tests nicht gebraucht; das ViewModel hält dafür
ebenfalls eine Funktion `private val nachImport: () -> Unit`, die im
`@Inject`-Konstruktor auf `{ waechterPlanung.sofortAusfuehren() }` zeigt und im
Test auf `{}`.

Danach ergänzen:

```kotlin
    fun exportieren(ziel: java.io.OutputStream) = viewModelScope.launch {
        backupDienst.exportieren(ziel)
        _meldung.value = Meldung.EXPORT_FERTIG
    }

    fun importieren(quelle: java.io.InputStream) = viewModelScope.launch {
        _meldung.value = when (val ergebnis = backupDienst.importieren(quelle)) {
            is ImportErgebnis.Erfolg -> Meldung.IMPORT_FERTIG
            is ImportErgebnis.FalschesSchema -> Meldung.IMPORT_FALSCHES_SCHEMA
            is ImportErgebnis.Unlesbar -> Meldung.IMPORT_UNLESBAR
        }
        nachImport()
    }
```

mit `enum class Meldung { EXPORT_FERTIG, IMPORT_FERTIG, IMPORT_FALSCHES_SCHEMA, IMPORT_UNLESBAR }` und einem `MutableStateFlow<Meldung?>`, das der Screen als Snackbar zeigt und danach auf `null` zurücksetzt.

In `EinstellungenScreen` zwei Knöpfe mit den Systemdialogen:

```kotlin
    val exportZiel = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        uri?.let {
            kontext.contentResolver.openOutputStream(it)?.use { strom ->
                viewModel.exportieren(strom)
            }
        }
    }

    val importQuelle = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            kontext.contentResolver.openInputStream(it)?.use { strom ->
                viewModel.importieren(strom)
            }
        }
    }

    Button(onClick = { exportZiel.launch("dayorganizer-backup.json") }) {
        Text(stringResource(R.string.backup_exportieren))
    }
    Button(onClick = { importQuelle.launch(arrayOf("application/json")) }) {
        Text(stringResource(R.string.backup_importieren))
    }
```

In `strings.xml` ergänzen:

```xml
    <string name="backup_exportieren">Backup speichern</string>
    <string name="backup_importieren">Backup einlesen</string>
    <string name="backup_export_fertig">Backup gespeichert</string>
    <string name="backup_import_fertig">Backup eingelesen</string>
    <string name="backup_falsches_schema">Diese Backup-Datei stammt aus einer anderen App-Version</string>
    <string name="backup_unlesbar">Die Datei konnte nicht gelesen werden</string>
```

- [ ] **Step 7: Auf dem Gerät durchspielen**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`.

Manuell: Backup speichern, App-Daten löschen, App öffnen, Backup einlesen — Aktivitäten, Punktestand und Belohnungen sind zurück.

- [ ] **Step 8: Commit**

```bash
git add app/
git commit -m "feat(backup): Export und Import ueber den System-Dateidialog"
```

---

### Task 3: Widget-Anzeige

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/WidgetZustand.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/HeuteWidget.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/HeuteWidgetEmpfaenger.kt`
- Create: `app/src/main/res/xml/heute_widget_info.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/build.gradle.kts` — Glance-Abhängigkeiten
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/widget/WidgetZustandTest.kt`

**Interfaces:**
- Consumes: `FaelligkeitRepository`, `AktivitaetRepository`, `BuchungRepository` aus Plan 1
- Produces:
  - `WidgetEintrag(faelligkeitId: Long, name: String, zeitText: String?, ueberfaellig: Boolean)`
  - `WidgetZustand(kontostand: Int, eintraege: List<WidgetEintrag>, weitere: Int)`
  - `WidgetZusammenstellung.aus(faelligkeiten, aktivitaeten, kontostand, jetzt, hoechstens: Int): WidgetZustand` — überfällige zuerst, dann nach Sollzeitpunkt; was nicht mehr passt, wird als `weitere` gezählt

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.widget

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetZustandTest {

    private val tag = LocalDate.of(2026, 3, 2)
    private val jetzt = LocalDateTime.of(2026, 3, 2, 12, 0)

    private val tabletten = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 1, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private fun termin(id: Long, stunde: Int, status: FaelligkeitStatus = FaelligkeitStatus.OFFEN) =
        Faelligkeit(
            id = id, aktivitaetId = 1, periodeVon = tag, periodeBis = tag,
            geplantFuer = tag.atTime(stunde, 0), sollAnzahl = 1, status = status,
        )

    @Test
    fun `ueberfaellige stehen oben`() {
        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = listOf(termin(11, 18), termin(10, 8)),
            aktivitaeten = listOf(tabletten),
            kontostand = 120,
            jetzt = jetzt,
            hoechstens = 5,
        )

        assertEquals(listOf(10L, 11L), zustand.eintraege.map { it.faelligkeitId })
        assertEquals(true, zustand.eintraege.first().ueberfaellig)
        assertEquals(120, zustand.kontostand)
    }

    @Test
    fun `erledigte erscheinen nicht`() {
        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = listOf(
                termin(10, 8, FaelligkeitStatus.ERLEDIGT),
                termin(11, 18),
            ),
            aktivitaeten = listOf(tabletten),
            kontostand = 0,
            jetzt = jetzt,
            hoechstens = 5,
        )

        assertEquals(listOf(11L), zustand.eintraege.map { it.faelligkeitId })
    }

    @Test
    fun `was nicht passt wird gezaehlt statt verschwiegen`() {
        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = (1L..6L).map { termin(it, 8 + it.toInt()) },
            aktivitaeten = listOf(tabletten),
            kontostand = 0,
            jetzt = jetzt,
            hoechstens = 4,
        )

        assertEquals(4, zustand.eintraege.size)
        assertEquals(2, zustand.weitere)
    }

    @Test
    fun `Zaehlziele erscheinen ohne Uhrzeit`() {
        val ziel = Faelligkeit(
            id = 30, aktivitaetId = 1, periodeVon = tag, periodeBis = tag.plusDays(6),
            geplantFuer = null, sollAnzahl = 3, istAnzahl = 1,
        )

        val zustand = WidgetZusammenstellung.aus(listOf(ziel), listOf(tabletten), 0, jetzt, 5)

        assertEquals(null, zustand.eintraege.single().zeitText)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*WidgetZustandTest*"`
Expected: Kompilierfehler `Unresolved reference: WidgetZusammenstellung`.

- [ ] **Step 3: Implementieren**

```kotlin
package de.vanhake.dayorganizer.widget

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class WidgetEintrag(
    val faelligkeitId: Long,
    val name: String,
    val zeitText: String?,
    val ueberfaellig: Boolean,
)

data class WidgetZustand(
    val kontostand: Int = 0,
    val eintraege: List<WidgetEintrag> = emptyList(),
    /** Wie viele offene Eintraege nicht mehr auf das Widget passen. */
    val weitere: Int = 0,
)

object WidgetZusammenstellung {

    private val UHRZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun aus(
        faelligkeiten: List<Faelligkeit>,
        aktivitaeten: List<Aktivitaet>,
        kontostand: Int,
        jetzt: LocalDateTime,
        hoechstens: Int,
    ): WidgetZustand {
        val nachId = aktivitaeten.associateBy { it.id }

        val offen = faelligkeiten
            .filter { it.status == FaelligkeitStatus.OFFEN && nachId.containsKey(it.aktivitaetId) }
            .sortedWith(
                compareByDescending<Faelligkeit> {
                    it.geplantFuer != null && it.geplantFuer!!.isBefore(jetzt)
                }.thenBy { it.geplantFuer ?: LocalDateTime.MAX },
            )

        return WidgetZustand(
            kontostand = kontostand,
            eintraege = offen.take(hoechstens).map { f ->
                WidgetEintrag(
                    faelligkeitId = f.id,
                    name = nachId.getValue(f.aktivitaetId).name,
                    zeitText = f.geplantFuer?.format(UHRZEIT),
                    ueberfaellig = f.geplantFuer != null && f.geplantFuer!!.isBefore(jetzt),
                )
            },
            weitere = (offen.size - hoechstens).coerceAtLeast(0),
        )
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*WidgetZustandTest*"`
Expected: `BUILD SUCCESSFUL`, 4 Tests bestanden.

- [ ] **Step 5: Glance-Widget anlegen**

`widget/HeuteWidget.kt`:

```kotlin
package de.vanhake.dayorganizer.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import de.vanhake.dayorganizer.di.WidgetZugang
import java.time.LocalDate
import java.time.LocalDateTime

class HeuteWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val zugang = EntryPointAccessors.fromApplication(context, WidgetZugang::class.java)
        val uhr = zugang.uhr()
        val heute = LocalDate.now(uhr)

        val zustand = WidgetZusammenstellung.aus(
            faelligkeiten = zugang.faelligkeiten().fuerZeitraum(heute, heute),
            aktivitaeten = zugang.aktivitaeten().alleAktiven(),
            kontostand = zugang.buchungen().kontostand(),
            jetzt = LocalDateTime.now(uhr),
            hoechstens = 5,
        )

        provideContent {
            Column(GlanceModifier.fillMaxWidth().padding(12.dp).background(Color(0xFFF5F5F5))) {
                Text("${zustand.kontostand} Punkte", style = TextStyle(color = ColorProvider(Color.Black)))

                zustand.eintraege.forEach { eintrag ->
                    Row(
                        GlanceModifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(
                                actionRunCallback<WidgetErledigenAktion>(
                                    actionParametersOf(
                                        WidgetErledigenAktion.FAELLIGKEIT_ID to eintrag.faelligkeitId,
                                    ),
                                ),
                            ),
                    ) {
                        Text(
                            text = listOfNotNull(eintrag.zeitText, eintrag.name).joinToString("  "),
                            style = TextStyle(
                                color = ColorProvider(
                                    if (eintrag.ueberfaellig) Color(0xFFB00020) else Color.Black,
                                ),
                            ),
                        )
                    }
                }

                if (zustand.weitere > 0) {
                    Text(
                        "+ ${zustand.weitere} weitere",
                        style = TextStyle(color = ColorProvider(Color.DarkGray)),
                    )
                }
            }
        }
    }
}
```

`widget/HeuteWidgetEmpfaenger.kt`:

```kotlin
package de.vanhake.dayorganizer.widget

import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

class HeuteWidgetEmpfaenger : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HeuteWidget()
}
```

`di/WidgetZugang.kt` — Glance kann keine Felder injizieren, deshalb ein Hilt-Zugangspunkt:

```kotlin
package de.vanhake.dayorganizer.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigen
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import java.time.Clock

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetZugang {
    fun aktivitaeten(): AktivitaetRepository
    fun faelligkeiten(): FaelligkeitRepository
    fun buchungen(): BuchungRepository
    fun erledigen(): FaelligkeitErledigen
    fun alarmPlaner(): AlarmPlaner
    fun uhr(): Clock
}
```

`res/xml/heute_widget_info.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="110dp"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen" />
```

Im Manifest innerhalb von `<application>`:

```xml
        <receiver
            android:name=".widget.HeuteWidgetEmpfaenger"
            android:exported="false">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/heute_widget_info" />
        </receiver>
```

- [ ] **Step 6: Build laufen lassen**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. `WidgetErledigenAktion` fehlt noch und entsteht in Task 4; bis dahin die `clickable`-Zeile auskommentieren, damit dieser Schritt für sich kompiliert.

- [ ] **Step 7: Commit**

```bash
git add app/
git commit -m "feat(widget): Glance-Widget zeigt heutige Aufgaben und Punktestand"
```

---

### Task 4: Widget-Interaktion und Aktualisierung

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/WidgetErledigenAktion.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/WidgetAktualisierung.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/HeuteWidget.kt` — `clickable` aktivieren
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/notification/AktionsEmpfaenger.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteViewModel.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/WaechterAblauf.kt`

**Interfaces:**
- Consumes: `FaelligkeitErledigen` aus Plan 1 Task 12, `WidgetZugang` aus Task 3
- Produces:
  - `WidgetErledigenAktion : ActionCallback` mit `FAELLIGKEIT_ID: ActionParameters.Key<Long>`
  - `WidgetAktualisierung.anstossen()` — erneuert alle Instanzen des Widgets

Nach jeder Zustandsänderung — aus App, Benachrichtigung, Widget oder Wächter — wird das Widget angestoßen. Sonst zeigt der Startbildschirm veraltete Aufgaben, und der Nutzer hakt etwas zweimal ab.

- [ ] **Step 1: Aktion und Aktualisierung implementieren**

`widget/WidgetErledigenAktion.kt`:

```kotlin
package de.vanhake.dayorganizer.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.EntryPointAccessors
import de.vanhake.dayorganizer.di.WidgetZugang

/**
 * Schreibt ueber denselben Use Case wie App und Benachrichtigung. Ein zweiter
 * Schreibpfad wuerde die Idempotenz des Abhakens unterlaufen.
 */
class WidgetErledigenAktion : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val faelligkeitId = parameters[FAELLIGKEIT_ID] ?: return
        val zugang = EntryPointAccessors.fromApplication(context, WidgetZugang::class.java)

        zugang.erledigen().invoke(faelligkeitId)
        zugang.alarmPlaner().neuPlanen()
        HeuteWidget().updateAll(context)
    }

    companion object {
        val FAELLIGKEIT_ID = ActionParameters.Key<Long>("faelligkeitId")
    }
}
```

`widget/WidgetAktualisierung.kt`:

```kotlin
package de.vanhake.dayorganizer.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetAktualisierung @Inject constructor(
    @ApplicationContext private val kontext: Context,
) {
    suspend fun anstossen() {
        runCatching { HeuteWidget().updateAll(kontext) }
        // Ist kein Widget platziert, ist das kein Fehler, sondern der Normalfall.
    }
}
```

In `HeuteWidget.kt` die in Task 3 auskommentierte `clickable`-Zeile wieder aktivieren; der zusätzliche Import ist `androidx.glance.action.clickable`.

- [ ] **Step 2: Alle Schreibpfade anbinden**

In `AktionsEmpfaenger` nach `alarmPlaner.neuPlanen()` ergänzen:

```kotlin
                widgetAktualisierung.anstossen()
```

mit `@Inject lateinit var widgetAktualisierung: WidgetAktualisierung`.

In `HeuteViewModel` den Parameter `private val widgetAnstossen: suspend () -> Unit` ergänzen (im Hilt-Konstruktor `{ widgetAktualisierung.anstossen() }`) und in `erledigen` sowie `auslassen` nach `alarmNeuPlanen()` aufrufen. Der bestehende `HeuteViewModelTest` bekommt `widgetAnstossen = {}`.

In `WaechterAblauf` analog einen Parameter `private val widgetAnstossen: suspend () -> Unit` ergänzen und am Ende von `invoke()` aufrufen; im Test `widgetAnstossen = {}`.

- [ ] **Step 3: Build und alle Tests laufen lassen**

Run: `./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Auf dem Gerät durchspielen**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`.

Manuell prüfen: Widget auf den Startbildschirm legen, eine heutige Aufgabe antippen — sie verschwindet aus dem Widget, der Punktestand steigt, und in der App steht sie unter *Erledigt*. Danach in der App eine weitere Aufgabe abhaken — das Widget aktualisiert sich ebenfalls.

- [ ] **Step 5: Commit**

```bash
git add app/
git commit -m "feat(widget): Abhaken vom Startbildschirm und Aktualisierung aller Schreibpfade"
```

---

## Abschluss

Damit ist der Funktionsumfang aus dem Design-Dokument vollständig. Abschließender Durchlauf:

```bash
./gradlew clean :domain:test :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleRelease
```

Nicht umgesetzt und bewusst zurückgestellt: Notiz zur einzelnen Erledigung, Mengen- und Dauerziele, Pause- und Urlaubsmodus, Cooldown auf Belohnungen, Cloud-Synchronisation, Vollbild-Alarm, Minuspunkte, getrennte Punktetöpfe, mehrsprachige Oberfläche.
