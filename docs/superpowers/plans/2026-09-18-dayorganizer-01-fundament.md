# DayOrganizer — Plan 1: Fundament und Erinnerungskern

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eine lauffähige Android-App, die wiederkehrende Aktivitäten verwaltet, zuverlässig daran erinnert und jede Erledigung als Punktbuchung festhält.

**Architecture:** Zwei Gradle-Module. `:domain` ist reines Kotlin ohne Android-Abhängigkeit und enthält den fachlichen Kern in drei zustandslosen Rechnern (`ZeitplanRechner`, `PunkteRechner`, `SerienRechner`), die alle eine `java.time.Clock` von außen bekommen. `:app` enthält Room-Persistenz, den Alarm- und Benachrichtigungsapparat sowie die Compose-Oberfläche. Der Erinnerungsmotor hält zu jedem Zeitpunkt genau einen exakten Alarm für den nächsten anstehenden Erinnerungszeitpunkt; ein nächtlicher WorkManager-Wächter repariert Ausfälle.

**Tech Stack:** Kotlin 2.x, Jetpack Compose (Material 3), Room, Hilt, WorkManager, AlarmManager, JUnit4 + kotlin-test, Turbine.

## Global Constraints

- Package-Root: `de.vanhake.dayorganizer`
- `minSdk = 33`, `targetSdk = 35`, `compileSdk = 35`, Java/Kotlin JVM-Target 17
- Modul `:domain` darf **keine** Android-Abhängigkeit haben. Die Gradle-Modulgrenze erzwingt das; wer dort `android.*` importieren will, hat das Design verletzt.
- Keine zeitabhängige Logik ruft `LocalDate.now()` oder `System.currentTimeMillis()` direkt. Jede Klasse, die die Uhrzeit braucht, bekommt eine `java.time.Clock` injiziert.
- Alle sichtbaren Texte sind Deutsch und liegen in `app/src/main/res/values/strings.xml`. Keine Literale im Compose-Code.
- Keine `INTERNET`-Berechtigung, keine Analytics, keine Crash-Reporter. Die App ist offline.
- Zeitpläne werden als `LocalTime`/`DayOfWeek`/`LocalDate` gespeichert, niemals als absoluter Zeitstempel. Auflösung zu einem Instant passiert ausschließlich im `AlarmPlaner`.
- Commit-Nachrichten im Conventional-Commits-Stil (`feat:`, `test:`, `chore:`, `fix:`).
- Die Versionen in `gradle/libs.versions.toml` sind der Startpunkt. Schlägt die Auflösung fehl, nimm die neueste kompatible Version und halte Kotlin- und KSP-Version paarweise passend.
- Es gibt **kein Gerät und keinen Emulator**. Alle Tests laufen auf der JVM. Datenbanktests, die einen Android-Kontext brauchen, laufen unter Robolectric im `test`-Quellverzeichnis; ein `androidTest`-Quellverzeichnis wird nicht angelegt.
- Der Gradle-Wrapper wird auf **8.9** festgelegt (liegt im lokalen Cache und erfüllt die Mindestanforderung von AGP 8.7.3).
- `local.properties` mit `sdk.dir=/home/jan-lin/.local/opt/android-sdk` wird angelegt und ist über `.gitignore` ausgeschlossen.

## File Structure

```
settings.gradle.kts
gradle/libs.versions.toml
domain/build.gradle.kts
domain/src/main/kotlin/de/vanhake/dayorganizer/domain/
    model/Aktivitaet.kt          Aktivität, SerienSchwelle
    model/Zeitplan.kt            sealed interface Zeitplan + Anstoss
    model/Faelligkeit.kt         Fälligkeit, FaelligkeitStatus
    model/Buchung.kt             Buchung, BuchungsArt
    rechner/ZeitplanRechner.kt   Zeitplan -> Fälligkeiten + Anstoßzeitpunkte
    rechner/SerienRechner.kt     Fälligkeitshistorie -> Serienlängen
    rechner/PunkteRechner.kt     Erledigung -> Buchungen
domain/src/test/kotlin/...       je ein Testfile pro Rechner

app/build.gradle.kts
app/src/main/AndroidManifest.xml
app/src/main/kotlin/de/vanhake/dayorganizer/
    DayOrganizerApp.kt           Application + Hilt
    MainActivity.kt              Single Activity, Navigation
    data/db/                     Entities, Converters, DAOs, Database
    data/mapper/                 Entity <-> Domänenmodell
    data/repository/             AktivitaetRepository, FaelligkeitRepository, BuchungRepository
    data/Einstellungen.kt        DataStore für Tagesbonus u. a.
    domainuse/                   Use Cases (nutzen Repos + Rechner)
    platform/alarm/              AlarmPlaner, ErinnerungsEmpfaenger, BootEmpfaenger
    platform/notification/       Kanaele, ErinnerungsNotifier, AktionsEmpfaenger
    platform/work/               WaechterWorker
    platform/permission/         BerechtigungsStatus
    ui/heute/                    HeuteViewModel, HeuteScreen
    ui/aktivitaeten/             AktivitaetenViewModel, AktivitaetenScreen, AktivitaetBearbeitenScreen
    ui/theme/                    Farben, Typografie
app/src/test/kotlin/...          ViewModel-, Use-Case- und (unter
                                 Robolectric) Room-DAO- und Migrationstests
```

---

### Task 1: Projektgerüst

Zwei Gradle-Module, Hilt, Compose, eine startende App. Ohne dieses Fundament lässt sich keine der folgenden Aufgaben ausführen; deshalb ist die Verifikation hier der grüne Build statt ein fachlicher Test.

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `domain/build.gradle.kts`
- Create: `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/DayOrganizerApp.kt`, `MainActivity.kt`
- Create: `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`
- Create: `.gitignore`

**Interfaces:**
- Consumes: nichts
- Produces: Modul `:domain` (reines JVM-Kotlin) und `:app` (Android, Hilt-fähig, Compose-fähig). Application-Klasse `DayOrganizerApp`, Entry-Activity `MainActivity`.

- [ ] **Step 1: Gradle-Wrapper und Projektdateien anlegen**

`.gitignore`:

```
*.iml
.gradle/
/local.properties
/.idea/
.DS_Store
/build
/captures
.externalNativeBuild
.cxx
local.properties
```

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}
rootProject.name = "DayOrganizer"
include(":app", ":domain")
```

`gradle.properties`:

```
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
org.gradle.caching=true
android.useAndroidX=true
android.nonTransitiveRClass=true
kotlin.code.style=official
```

`gradle/libs.versions.toml`:

```toml
[versions]
agp = "8.7.3"
kotlin = "2.0.21"
ksp = "2.0.21-1.0.28"
hilt = "2.52"
hiltNavigationCompose = "1.2.0"
room = "2.6.1"
composeBom = "2024.12.01"
activityCompose = "1.9.3"
navigationCompose = "2.8.5"
coreKtx = "1.15.0"
lifecycle = "2.8.7"
coroutines = "1.9.0"
work = "2.9.1"
hiltWork = "1.2.0"
datastore = "1.1.1"
turbine = "1.2.0"
junit = "4.13.2"
androidxTestRunner = "1.6.2"
androidxTestExt = "1.2.1"
androidxTestCore = "1.6.1"
robolectric = "4.14.1"

[libraries]
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigationCompose" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-extended = { module = "androidx.compose.material:material-icons-extended" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
room-testing = { module = "androidx.room:room-testing", version.ref = "room" }
hilt-android = { module = "com.google.dagger:hilt-android", version.ref = "hilt" }
hilt-compiler = { module = "com.google.dagger:hilt-android-compiler", version.ref = "hilt" }
hilt-navigation-compose = { module = "androidx.hilt:hilt-navigation-compose", version.ref = "hiltNavigationCompose" }
hilt-work = { module = "androidx.hilt:hilt-work", version.ref = "hiltWork" }
hilt-work-compiler = { module = "androidx.hilt:hilt-compiler", version.ref = "hiltWork" }
androidx-work-runtime-ktx = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }
androidx-datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
turbine = { module = "app.cash.turbine:turbine", version.ref = "turbine" }
junit = { module = "junit:junit", version.ref = "junit" }
androidx-test-runner = { module = "androidx.test:runner", version.ref = "androidxTestRunner" }
androidx-test-ext-junit = { module = "androidx.test.ext:junit", version.ref = "androidxTestExt" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidxTestCore" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
hilt = { id = "com.google.dagger.hilt.android", version.ref = "hilt" }
```

Wurzel-`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}
```

- [ ] **Step 2: Modul `:domain` anlegen**

`domain/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

tasks.withType<Test> { useJUnit() }
```

- [ ] **Step 3: Modul `:app` anlegen**

`app/build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "de.vanhake.dayorganizer"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.vanhake.dayorganizer"
        minSdk = 33
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures { compose = true }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
        getByName("test").java.srcDirs("src/test/kotlin")
        // Die exportierten Room-Schemas liegen als Assets nur im Debug-Build.
        // MigrationTestHelper liest sie ueber den Asset-Manager; das Release-APK
        // bleibt davon frei.
        getByName("debug").assets.srcDirs("$projectDir/schemas")
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Datenbanktests laufen als JVM-Tests unter Robolectric statt auf einem
    // Geraet. Ohne das waeren Migrationen nur dann geprueft, wenn gerade ein
    // Emulator laeuft -- also fast nie.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.room.testing)
}
```

- [ ] **Step 4: Manifest, Application und Activity anlegen**

`app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <application
        android:name=".DayOrganizerApp"
        android:allowBackup="false"
        android:label="@string/app_name"
        android:supportsRtl="false"
        android:theme="@style/Theme.DayOrganizer">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.DayOrganizer">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`app/src/main/kotlin/de/vanhake/dayorganizer/DayOrganizerApp.kt`:

```kotlin
package de.vanhake.dayorganizer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DayOrganizerApp : Application()
```

`app/src/main/kotlin/de/vanhake/dayorganizer/MainActivity.kt`:

```kotlin
package de.vanhake.dayorganizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { Text(getString(R.string.app_name)) }
            }
        }
    }
}
```

`app/src/main/res/values/strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">DayOrganizer</string>
</resources>
```

`app/src/main/res/values/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.DayOrganizer" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

- [ ] **Step 5: Build verifizieren**

Run: `./gradlew :domain:build :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Bei einem Fehler wegen nicht auflösbarer Versionen die betroffene Version in `libs.versions.toml` auf die neueste kompatible heben.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "chore: Gradle-Projektgeruest mit Modulen :app und :domain"
```

---

### Task 2: Domänenmodell und ZeitplanRechner für feste Zeiten

**Files:**
- Create: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/model/Zeitplan.kt`
- Create: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/model/Aktivitaet.kt`
- Create: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/model/Faelligkeit.kt`
- Create: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/model/Buchung.kt`
- Create: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/rechner/ZeitplanRechner.kt`
- Test: `domain/src/test/kotlin/de/vanhake/dayorganizer/domain/rechner/ZeitplanRechnerFesteZeitenTest.kt`
- Test: `domain/src/test/kotlin/de/vanhake/dayorganizer/domain/TestDaten.kt`

**Interfaces:**
- Consumes: Modul `:domain` aus Task 1
- Produces:
  - `Aktivitaet(id: Long, name: String, notiz: String, farbe: Int, basisPunkte: Int, zeitplan: Zeitplan, kulanzMinuten: Int, puenktlichkeitsBonus: Int, serienSchwellen: List<SerienSchwelle>, nachfassAnzahl: Int, nachfassAbstandMinuten: Int, aktiv: Boolean, erstelltAm: LocalDate, archiviertAm: LocalDate?)`
  - `SerienSchwelle(perioden: Int, bonus: Int)`
  - `sealed interface Zeitplan` mit `FesteZeiten`, `Intervall`, `Tagesziel`, `Wochenziel`; `Anstoss(tag: DayOfWeek, zeit: LocalTime)`
  - `Faelligkeit(id: Long, aktivitaetId: Long, periodeVon: LocalDate, periodeBis: LocalDate, geplantFuer: LocalDateTime?, sollAnzahl: Int, istAnzahl: Int, status: FaelligkeitStatus, erledigtAm: LocalDateTime?, warPuenktlich: Boolean)`
  - `enum FaelligkeitStatus { OFFEN, ERLEDIGT, AUSGELASSEN, VERPASST }`
  - `Buchung(id: Long, art: BuchungsArt, betrag: Int, zeitpunkt: LocalDateTime, aktivitaetId: Long?, faelligkeitId: Long?, belohnungId: Long?, schwelle: Int?, notiz: String)` — `schwelle` trägt bei `SERIENBONUS` den ausgelösten Schwellenwert, sonst `null`
  - `enum BuchungsArt { ERLEDIGUNG, PUENKTLICHKEITSBONUS, SERIENBONUS, TAGESBONUS, EINLOESUNG, KORREKTUR }`
  - `ZeitplanRechner.faelligkeitenFuer(aktivitaet: Aktivitaet, von: LocalDate, bis: LocalDate): List<Faelligkeit>` — `von` und `bis` inklusive, Ergebnis nach `periodeVon` und `geplantFuer` aufsteigend sortiert, `id = 0`

- [ ] **Step 1: Modelldateien anlegen**

`model/Zeitplan.kt`:

```kotlin
package de.vanhake.dayorganizer.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/** Ein Anstoss ist eine Erinnerungszeit fuer Ziele ohne Soll-Zeitpunkt. */
data class Anstoss(val tag: DayOfWeek, val zeit: LocalTime)

sealed interface Zeitplan {

    /** Feste Uhrzeiten an ausgewaehlten Wochentagen. Je Uhrzeit eine Faelligkeit. */
    data class FesteZeiten(
        val zeiten: List<LocalTime>,
        val wochentage: Set<DayOfWeek>,
    ) : Zeitplan

    /** Alle N Stunden innerhalb eines Tagesfensters. Je Zeitpunkt eine Faelligkeit. */
    data class Intervall(
        val alleNStunden: Int,
        val von: LocalTime,
        val bis: LocalTime,
        val wochentage: Set<DayOfWeek>,
    ) : Zeitplan

    /** X-mal am Tag ohne Soll-Zeitpunkt. Eine Faelligkeit je Tag mit sollAnzahl = anzahl. */
    data class Tagesziel(
        val anzahl: Int,
        val wochentage: Set<DayOfWeek>,
        val anstossZeiten: List<LocalTime> = emptyList(),
    ) : Zeitplan

    /** X-mal je Woche (Montag bis Sonntag). Eine Faelligkeit je Woche. */
    data class Wochenziel(
        val anzahl: Int,
        val anstoss: List<Anstoss> = emptyList(),
    ) : Zeitplan
}
```

`model/Aktivitaet.kt`:

```kotlin
package de.vanhake.dayorganizer.domain.model

import java.time.LocalDate

/** Bonus, sobald die Serie [perioden] erreicht. */
data class SerienSchwelle(val perioden: Int, val bonus: Int)

data class Aktivitaet(
    val id: Long = 0,
    val name: String,
    val notiz: String = "",
    val farbe: Int,
    val basisPunkte: Int,
    val zeitplan: Zeitplan,
    val kulanzMinuten: Int = 60,
    val puenktlichkeitsBonus: Int = 0,
    val serienSchwellen: List<SerienSchwelle> = emptyList(),
    val nachfassAnzahl: Int = 2,
    val nachfassAbstandMinuten: Int = 15,
    val aktiv: Boolean = true,
    val erstelltAm: LocalDate,
    val archiviertAm: LocalDate? = null,
)
```

`model/Faelligkeit.kt`:

```kotlin
package de.vanhake.dayorganizer.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

enum class FaelligkeitStatus { OFFEN, ERLEDIGT, AUSGELASSEN, VERPASST }

/**
 * Konkrete Instanz einer Aktivitaet in einer Periode.
 * Feste Zeiten und Intervall: sollAnzahl = 1, geplantFuer gesetzt.
 * Tages- und Wochenziel: sollAnzahl = X, geplantFuer = null.
 */
data class Faelligkeit(
    val id: Long = 0,
    val aktivitaetId: Long,
    val periodeVon: LocalDate,
    val periodeBis: LocalDate,
    val geplantFuer: LocalDateTime?,
    val sollAnzahl: Int,
    val istAnzahl: Int = 0,
    val status: FaelligkeitStatus = FaelligkeitStatus.OFFEN,
    val erledigtAm: LocalDateTime? = null,
    val warPuenktlich: Boolean = false,
)
```

`model/Buchung.kt`:

```kotlin
package de.vanhake.dayorganizer.domain.model

import java.time.LocalDateTime

enum class BuchungsArt {
    ERLEDIGUNG,
    PUENKTLICHKEITSBONUS,
    SERIENBONUS,
    TAGESBONUS,
    EINLOESUNG,
    KORREKTUR,
}

/** Eine Zeile im Punktejournal. Der Kontostand ist die Summe aller Betraege. */
data class Buchung(
    val id: Long = 0,
    val art: BuchungsArt,
    val betrag: Int,
    val zeitpunkt: LocalDateTime,
    val aktivitaetId: Long? = null,
    val faelligkeitId: Long? = null,
    val belohnungId: Long? = null,
    /** Bei SERIENBONUS der ausgeloeste Schwellenwert, sonst null. */
    val schwelle: Int? = null,
    val notiz: String = "",
)
```

- [ ] **Step 2: Testhilfen und den ersten fehlschlagenden Test schreiben**

`domain/src/test/kotlin/de/vanhake/dayorganizer/domain/TestDaten.kt`:

```kotlin
package de.vanhake.dayorganizer.domain

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

fun festeUhr(zeitpunkt: LocalDateTime, zone: ZoneId = BERLIN): Clock =
    Clock.fixed(zeitpunkt.atZone(zone).toInstant(), zone)

fun aktivitaet(
    id: Long = 1,
    name: String = "Tabletten",
    basisPunkte: Int = 10,
    zeitplan: Zeitplan,
    kulanzMinuten: Int = 60,
    puenktlichkeitsBonus: Int = 5,
) = Aktivitaet(
    id = id,
    name = name,
    farbe = 0xFF4CAF50.toInt(),
    basisPunkte = basisPunkte,
    zeitplan = zeitplan,
    kulanzMinuten = kulanzMinuten,
    puenktlichkeitsBonus = puenktlichkeitsBonus,
    erstelltAm = LocalDate.of(2026, 1, 1),
)
```

`domain/src/test/kotlin/de/vanhake/dayorganizer/domain/rechner/ZeitplanRechnerFesteZeitenTest.kt`:

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeitplanRechnerFesteZeitenTest {

    private val rechner = ZeitplanRechner()

    @Test
    fun `zwei Uhrzeiten an drei Tagen ergeben sechs Faelligkeiten`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 4),
        )

        assertEquals(6, ergebnis.size)
        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 0), ergebnis.first().geplantFuer)
        assertEquals(LocalDateTime.of(2026, 3, 4, 20, 0), ergebnis.last().geplantFuer)
        assertEquals(1, ergebnis.first().sollAnzahl)
        assertEquals(LocalDate.of(2026, 3, 2), ergebnis.first().periodeVon)
        assertEquals(LocalDate.of(2026, 3, 2), ergebnis.first().periodeBis)
    }

    @Test
    fun `nicht ausgewaehlte Wochentage erzeugen keine Faelligkeit`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(18, 0)),
                wochentage = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ),
        )

        // 2026-03-02 ist ein Montag, der Zeitraum deckt eine volle Woche ab.
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 8),
        )

        assertEquals(3, ergebnis.size)
        assertEquals(
            listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            ergebnis.map { it.periodeVon.dayOfWeek },
        )
    }

    @Test
    fun `Zeitraumgrenzen sind inklusive`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(9, 0)),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 2),
        )

        assertEquals(1, ergebnis.size)
    }
}
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :domain:test --tests "*ZeitplanRechnerFesteZeitenTest*"`
Expected: Kompilierfehler `Unresolved reference: ZeitplanRechner`.

- [ ] **Step 4: ZeitplanRechner minimal implementieren**

`domain/src/main/kotlin/de/vanhake/dayorganizer/domain/rechner/ZeitplanRechner.kt`:

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.LocalDate

/**
 * Bildet einen Zeitplan auf konkrete Faelligkeiten ab.
 * Einziger Ort im Projekt, an dem Zeitplantypen ausgewertet werden.
 */
class ZeitplanRechner {

    /** [von] und [bis] sind inklusive. */
    fun faelligkeitenFuer(
        aktivitaet: Aktivitaet,
        von: LocalDate,
        bis: LocalDate,
    ): List<Faelligkeit> = when (val plan = aktivitaet.zeitplan) {
        is Zeitplan.FesteZeiten -> festeZeiten(aktivitaet, plan, von, bis)
        else -> emptyList()
    }

    private fun festeZeiten(
        aktivitaet: Aktivitaet,
        plan: Zeitplan.FesteZeiten,
        von: LocalDate,
        bis: LocalDate,
    ): List<Faelligkeit> = tageIm(von, bis)
        .filter { it.dayOfWeek in plan.wochentage }
        .flatMap { tag ->
            plan.zeiten.sorted().map { zeit ->
                Faelligkeit(
                    aktivitaetId = aktivitaet.id,
                    periodeVon = tag,
                    periodeBis = tag,
                    geplantFuer = tag.atTime(zeit),
                    sollAnzahl = 1,
                )
            }
        }

    private fun tageIm(von: LocalDate, bis: LocalDate): List<LocalDate> =
        generateSequence(von) { it.plusDays(1) }
            .takeWhile { !it.isAfter(bis) }
            .toList()
}
```

- [ ] **Step 5: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :domain:test --tests "*ZeitplanRechnerFesteZeitenTest*"`
Expected: `BUILD SUCCESSFUL`, 3 Tests bestanden.

- [ ] **Step 6: Commit**

```bash
git add domain/
git commit -m "feat(domain): Modell und ZeitplanRechner fuer feste Zeiten"
```

---

### Task 3: ZeitplanRechner für Intervalle

**Files:**
- Modify: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/rechner/ZeitplanRechner.kt`
- Test: `domain/src/test/kotlin/de/vanhake/dayorganizer/domain/rechner/ZeitplanRechnerIntervallTest.kt`

**Interfaces:**
- Consumes: `ZeitplanRechner.faelligkeitenFuer` und `Zeitplan.Intervall` aus Task 2
- Produces: keine neuen Signaturen, nur ein weiterer behandelter Zeitplantyp

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeitplanRechnerIntervallTest {

    private val rechner = ZeitplanRechner()

    @Test
    fun `alle drei Stunden zwischen acht und zweiundzwanzig Uhr ergibt fuenf Zeitpunkte`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Intervall(
                alleNStunden = 3,
                von = LocalTime.of(8, 0),
                bis = LocalTime.of(22, 0),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 2),
        )

        assertEquals(
            listOf(
                LocalTime.of(8, 0),
                LocalTime.of(11, 0),
                LocalTime.of(14, 0),
                LocalTime.of(17, 0),
                LocalTime.of(20, 0),
            ),
            ergebnis.map { it.geplantFuer!!.toLocalTime() },
        )
    }

    @Test
    fun `das Fensterende ist inklusive wenn es genau getroffen wird`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Intervall(
                alleNStunden = 4,
                von = LocalTime.of(8, 0),
                bis = LocalTime.of(20, 0),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 2),
        )

        assertEquals(4, ergebnis.size)
        assertEquals(LocalTime.of(20, 0), ergebnis.last().geplantFuer!!.toLocalTime())
    }

    @Test
    fun `Intervall respektiert die Wochentagsauswahl`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Intervall(
                alleNStunden = 6,
                von = LocalTime.of(9, 0),
                bis = LocalTime.of(21, 0),
                wochentage = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 8),
        )

        // Samstag 2026-03-07 und Sonntag 2026-03-08, je drei Zeitpunkte (9, 15, 21 Uhr).
        assertEquals(6, ergebnis.size)
        assertEquals(
            setOf(LocalDate.of(2026, 3, 7), LocalDate.of(2026, 3, 8)),
            ergebnis.map { it.periodeVon }.toSet(),
        )
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :domain:test --tests "*ZeitplanRechnerIntervallTest*"`
Expected: FAIL, alle drei Tests erwarten Einträge, bekommen aber eine leere Liste.

- [ ] **Step 3: Intervall implementieren**

In `ZeitplanRechner.kt` den `when`-Zweig ergänzen und die Methode hinzufügen:

```kotlin
        is Zeitplan.Intervall -> intervall(aktivitaet, plan, von, bis)
```

```kotlin
    private fun intervall(
        aktivitaet: Aktivitaet,
        plan: Zeitplan.Intervall,
        von: LocalDate,
        bis: LocalDate,
    ): List<Faelligkeit> {
        require(plan.alleNStunden > 0) { "Intervall muss positiv sein" }
        val fensterMinuten = Duration.between(plan.von, plan.bis).toMinutes()
        if (fensterMinuten < 0) return emptyList()

        // Gerechnet wird ueber einen wachsenden Minutenversatz, nicht ueber
        // LocalTime.plusHours: das rechnet modulo 24, und bei Teilern von 24
        // (8 Stunden von 06:00 bis 22:00, 12 Stunden von 08:00 bis 20:00)
        // landet der Umlauf exakt wieder auf `von` -- die Folge endet nie.
        val schrittMinuten = plan.alleNStunden * 60L
        val zeiten = generateSequence(0L) { it + schrittMinuten }
            .takeWhile { it <= fensterMinuten }
            .map { plan.von.plusMinutes(it) }
            .toList()

        return tageIm(von, bis)
            .filter { it.dayOfWeek in plan.wochentage }
            .flatMap { tag ->
                zeiten.map { zeit ->
                    Faelligkeit(
                        aktivitaetId = aktivitaet.id,
                        periodeVon = tag,
                        periodeBis = tag,
                        geplantFuer = tag.atTime(zeit),
                        sollAnzahl = 1,
                    )
                }
            }
    }
```

Der zusätzliche Import ist `java.time.Duration`.

Der Versatz ist ein streng wachsender `Long` gegen eine feste Schranke — damit
ist die Terminierung strukturell und hängt nicht daran, einen Umlauf zu
erkennen. Ergänze zu den drei Tests aus Step 1 noch fünf weitere, die genau
diese Grenzfälle festnageln: 8 Stunden von 06:00 bis 22:00 (erwartet 06:00,
14:00, 22:00), 12 Stunden von 08:00 bis 20:00 (erwartet 08:00, 20:00), 24
Stunden von 08:00 bis 20:00 (erwartet nur 08:00), ein umgekehrtes Fenster
(erwartet leere Liste) und `alleNStunden = 0` (erwartet
`IllegalArgumentException`). Die ersten drei laufen gegen eine Umlauf-basierte
Implementierung nicht in einen Fehler, sondern in einen `OutOfMemoryError` —
begrenze den RED-Lauf entsprechend.

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :domain:test --tests "*ZeitplanRechnerIntervallTest*"`
Expected: `BUILD SUCCESSFUL`, 8 Tests bestanden.

- [ ] **Step 5: Commit**

```bash
git add domain/
git commit -m "feat(domain): ZeitplanRechner behandelt Intervalle"
```

---

### Task 4: ZeitplanRechner für Tages- und Wochenziele

**Files:**
- Modify: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/rechner/ZeitplanRechner.kt`
- Test: `domain/src/test/kotlin/de/vanhake/dayorganizer/domain/rechner/ZeitplanRechnerZaehlzieleTest.kt`

**Interfaces:**
- Consumes: `ZeitplanRechner.faelligkeitenFuer`, `Zeitplan.Tagesziel`, `Zeitplan.Wochenziel` aus Task 2
- Produces: Wochenziel-Fälligkeiten haben `periodeVon` = Montag und `periodeBis` = Sonntag der ISO-Woche, auch wenn der angefragte Zeitraum mitten in der Woche beginnt

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ZeitplanRechnerZaehlzieleTest {

    private val rechner = ZeitplanRechner()

    @Test
    fun `Tagesziel erzeugt eine Faelligkeit je Tag mit der Sollanzahl`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Tagesziel(anzahl = 3, wochentage = DayOfWeek.entries.toSet()),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 4),
        )

        assertEquals(3, ergebnis.size)
        ergebnis.forEach {
            assertEquals(3, it.sollAnzahl)
            assertEquals(0, it.istAnzahl)
            assertNull(it.geplantFuer)
            assertEquals(it.periodeVon, it.periodeBis)
        }
    }

    @Test
    fun `Wochenziel erzeugt eine Faelligkeit je ISO-Woche von Montag bis Sonntag`() {
        val a = aktivitaet(zeitplan = Zeitplan.Wochenziel(anzahl = 3))

        // Mittwoch 2026-03-04 bis Dienstag 2026-03-10 beruehrt zwei Wochen.
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 4),
            bis = LocalDate.of(2026, 3, 10),
        )

        assertEquals(2, ergebnis.size)
        assertEquals(LocalDate.of(2026, 3, 2), ergebnis[0].periodeVon)
        assertEquals(LocalDate.of(2026, 3, 8), ergebnis[0].periodeBis)
        assertEquals(LocalDate.of(2026, 3, 9), ergebnis[1].periodeVon)
        assertEquals(LocalDate.of(2026, 3, 15), ergebnis[1].periodeBis)
        assertEquals(3, ergebnis[0].sollAnzahl)
    }

    @Test
    fun `Tagesziel respektiert die Wochentagsauswahl`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Tagesziel(
                anzahl = 2,
                wochentage = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
            ),
        )

        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = a,
            von = LocalDate.of(2026, 3, 2),
            bis = LocalDate.of(2026, 3, 8),
        )

        assertEquals(2, ergebnis.size)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :domain:test --tests "*ZeitplanRechnerZaehlzieleTest*"`
Expected: FAIL, drei leere Ergebnislisten.

- [ ] **Step 3: Tages- und Wochenziel implementieren**

In `ZeitplanRechner.kt` die beiden `when`-Zweige ergänzen:

```kotlin
        is Zeitplan.Tagesziel -> tagesziel(aktivitaet, plan, von, bis)
        is Zeitplan.Wochenziel -> wochenziel(aktivitaet, plan, von, bis)
```

Danach das `else -> emptyList()` entfernen, damit der Compiler bei einem künftigen fünften Zeitplantyp einen Fehler meldet statt ihn stillschweigend zu ignorieren. Zusätzlich die Methoden hinzufügen:

```kotlin
    private fun tagesziel(
        aktivitaet: Aktivitaet,
        plan: Zeitplan.Tagesziel,
        von: LocalDate,
        bis: LocalDate,
    ): List<Faelligkeit> = tageIm(von, bis)
        .filter { it.dayOfWeek in plan.wochentage }
        .map { tag ->
            Faelligkeit(
                aktivitaetId = aktivitaet.id,
                periodeVon = tag,
                periodeBis = tag,
                geplantFuer = null,
                sollAnzahl = plan.anzahl,
            )
        }

    private fun wochenziel(
        aktivitaet: Aktivitaet,
        plan: Zeitplan.Wochenziel,
        von: LocalDate,
        bis: LocalDate,
    ): List<Faelligkeit> {
        val ersterMontag = von.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return generateSequence(ersterMontag) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(bis) }
            .map { montag ->
                Faelligkeit(
                    aktivitaetId = aktivitaet.id,
                    periodeVon = montag,
                    periodeBis = montag.plusDays(6),
                    geplantFuer = null,
                    sollAnzahl = plan.anzahl,
                )
            }
            .toList()
    }
```

Der zusätzliche Import ist `java.time.DayOfWeek`.

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :domain:test`
Expected: `BUILD SUCCESSFUL`, alle bisherigen ZeitplanRechner-Tests bestanden.

- [ ] **Step 5: Commit**

```bash
git add domain/
git commit -m "feat(domain): ZeitplanRechner behandelt Tages- und Wochenziele"
```

---

### Task 5: Anstoßzeitpunkte

Zähl­ziele haben keinen Soll-Zeitpunkt, sollen aber erinnern können. Diese Aufgabe liefert die Brücke zum Alarmmotor: aus einer Fälligkeit werden die Zeitpunkte, zu denen eine Benachrichtigung fällig ist.

**Files:**
- Modify: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/rechner/ZeitplanRechner.kt`
- Test: `domain/src/test/kotlin/de/vanhake/dayorganizer/domain/rechner/ZeitplanRechnerAnstossTest.kt`

**Interfaces:**
- Consumes: `Faelligkeit`, `Aktivitaet`, `Zeitplan` aus Task 2
- Produces: `ZeitplanRechner.anstossZeitpunkteFuer(aktivitaet: Aktivitaet, faelligkeit: Faelligkeit): List<LocalDateTime>` — aufsteigend sortiert; bei festen Zeiten und Intervall genau der eine Soll-Zeitpunkt, bei Zählzielen die konfigurierten Anstöße, leer wenn keine konfiguriert sind

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeitplanRechnerAnstossTest {

    private val rechner = ZeitplanRechner()

    @Test
    fun `feste Zeiten stossen genau zum Sollzeitpunkt an`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(8, 0)),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )
        val f = Faelligkeit(
            aktivitaetId = a.id,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0),
            sollAnzahl = 1,
        )

        assertEquals(
            listOf(LocalDateTime.of(2026, 3, 2, 8, 0)),
            rechner.anstossZeitpunkteFuer(a, f),
        )
    }

    @Test
    fun `Tagesziel stoesst zu den konfigurierten Uhrzeiten des Tages an`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Tagesziel(
                anzahl = 3,
                wochentage = DayOfWeek.entries.toSet(),
                anstossZeiten = listOf(LocalTime.of(19, 0), LocalTime.of(12, 0)),
            ),
        )
        val f = Faelligkeit(
            aktivitaetId = a.id,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = null,
            sollAnzahl = 3,
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 3, 2, 12, 0),
                LocalDateTime.of(2026, 3, 2, 19, 0),
            ),
            rechner.anstossZeitpunkteFuer(a, f),
        )
    }

    @Test
    fun `Wochenziel stoesst am konfigurierten Wochentag innerhalb der Periode an`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Wochenziel(
                anzahl = 3,
                anstoss = listOf(
                    Anstoss(DayOfWeek.THURSDAY, LocalTime.of(18, 0)),
                    Anstoss(DayOfWeek.TUESDAY, LocalTime.of(18, 0)),
                ),
            ),
        )
        val f = Faelligkeit(
            aktivitaetId = a.id,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 8),
            geplantFuer = null,
            sollAnzahl = 3,
        )

        assertEquals(
            listOf(
                LocalDateTime.of(2026, 3, 3, 18, 0),
                LocalDateTime.of(2026, 3, 5, 18, 0),
            ),
            rechner.anstossZeitpunkteFuer(a, f),
        )
    }

    @Test
    fun `ohne konfigurierte Anstoesse gibt es keine Erinnerung`() {
        val a = aktivitaet(
            zeitplan = Zeitplan.Tagesziel(anzahl = 2, wochentage = DayOfWeek.entries.toSet()),
        )
        val f = Faelligkeit(
            aktivitaetId = a.id,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = null,
            sollAnzahl = 2,
        )

        assertEquals(emptyList(), rechner.anstossZeitpunkteFuer(a, f))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :domain:test --tests "*ZeitplanRechnerAnstossTest*"`
Expected: Kompilierfehler `Unresolved reference: anstossZeitpunkteFuer`.

- [ ] **Step 3: Methode implementieren**

```kotlin
    /** Zeitpunkte, zu denen fuer diese Faelligkeit erinnert werden soll. */
    fun anstossZeitpunkteFuer(
        aktivitaet: Aktivitaet,
        faelligkeit: Faelligkeit,
    ): List<java.time.LocalDateTime> = when (val plan = aktivitaet.zeitplan) {
        is Zeitplan.FesteZeiten, is Zeitplan.Intervall ->
            listOfNotNull(faelligkeit.geplantFuer)

        is Zeitplan.Tagesziel ->
            plan.anstossZeiten.sorted().map { faelligkeit.periodeVon.atTime(it) }

        is Zeitplan.Wochenziel ->
            plan.anstoss
                .map { faelligkeit.periodeVon.with(
                    java.time.temporal.TemporalAdjusters.nextOrSame(it.tag),
                ).atTime(it.zeit) }
                .filter { !it.toLocalDate().isAfter(faelligkeit.periodeBis) }
                .sorted()
    }
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :domain:test`
Expected: `BUILD SUCCESSFUL`, alle Tests bestanden.

- [ ] **Step 5: Zeitumstellung und Schaltjahr absichern**

Diese Fälle sind der Grund, warum Zeitpläne als `LocalTime` gespeichert werden.
Ohne diese Tests fällt ein Fehler erst im März oder Oktober auf — und dann bei
einer Medikamentenerinnerung.

Was diese Tests leisten und was nicht, ehrlich benannt: `faelligkeitenFuer`
rechnet ausschließlich mit `LocalDate` und `LocalTime` und berührt keine
Zeitzone. Die beiden Umstellungstage liefern deshalb dasselbe Ergebnis wie
jeder andere Tag — und genau das ist die Zusage, die sie bewachen. Fügt jemand
später eine `ZoneId`-Auflösung in `festeZeiten` ein, schlagen sie fehl. Die
eigentliche Auflösung von Ortszeit zu Instant passiert im `AlarmPlaner` in
Task 14 und wird dort geprüft, nicht hier.

`domain/src/test/kotlin/de/vanhake/dayorganizer/domain/rechner/ZeitplanRechnerKalenderTest.kt`:

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeitplanRechnerKalenderTest {

    private val rechner = ZeitplanRechner()
    private val berlin = ZoneId.of("Europe/Berlin")

    private fun taeglichUm(vararg stunden: Int) = aktivitaet(
        zeitplan = Zeitplan.FesteZeiten(
            zeiten = stunden.map { LocalTime.of(it, 0) },
            wochentage = DayOfWeek.entries.toSet(),
        ),
    )

    @Test
    fun `die Berechnung bleibt am Sommerzeitwechsel zeitzonenfrei`() {
        // In Europa wird am letzten Sonntag im Maerz 2026 (29.03.) umgestellt;
        // 02:30 gibt es an diesem Tag nicht.
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = taeglichUm(2, 8),
            von = LocalDate.of(2026, 3, 29),
            bis = LocalDate.of(2026, 3, 29),
        )

        assertEquals(2, ergebnis.size)
        assertEquals(
            listOf(LocalDateTime.of(2026, 3, 29, 2, 0), LocalDateTime.of(2026, 3, 29, 8, 0)),
            ergebnis.map { it.geplantFuer },
        )
    }

    @Test
    fun `die Berechnung bleibt am Winterzeitwechsel zeitzonenfrei`() {
        // Am 25.10.2026 gibt es 02:30 zweimal.
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = taeglichUm(2),
            von = LocalDate.of(2026, 10, 25),
            bis = LocalDate.of(2026, 10, 25),
        )

        assertEquals(1, ergebnis.size)
    }

    @Test
    fun `der 29 Februar eines Schaltjahres erzeugt eine Faelligkeit`() {
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = taeglichUm(8),
            von = LocalDate.of(2028, 2, 28),
            bis = LocalDate.of(2028, 3, 1),
        )

        assertEquals(3, ergebnis.size)
        assertEquals(LocalDate.of(2028, 2, 29), ergebnis[1].periodeVon)
    }

    @Test
    fun `ein Wochenziel ueber den Jahreswechsel bleibt eine Periode je Woche`() {
        val ergebnis = rechner.faelligkeitenFuer(
            aktivitaet = aktivitaet(zeitplan = Zeitplan.Wochenziel(anzahl = 3)),
            von = LocalDate.of(2026, 12, 28),
            bis = LocalDate.of(2027, 1, 10),
        )

        assertEquals(2, ergebnis.size)
        assertEquals(LocalDate.of(2026, 12, 28), ergebnis[0].periodeVon)
        assertEquals(LocalDate.of(2027, 1, 4), ergebnis[1].periodeVon)
    }
}
```

Run: `./gradlew :domain:test --tests "*ZeitplanRechnerKalenderTest*"`
Expected: `BUILD SUCCESSFUL`, 4 Tests bestanden. Schlägt einer fehl, liegt der
Fehler im `ZeitplanRechner` und nicht im Test — die erwarteten Werte sind die
fachlich richtigen.

- [ ] **Step 6: Commit**

```bash
git add domain/
git commit -m "feat(domain): Anstosszeitpunkte fuer Zaehlziele, Kalendergrenzfaelle abgesichert"
```

---

### Task 6: SerienRechner

**Files:**
- Create: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/rechner/SerienRechner.kt`
- Test: `domain/src/test/kotlin/de/vanhake/dayorganizer/domain/rechner/SerienRechnerTest.kt`

**Interfaces:**
- Consumes: `Faelligkeit`, `FaelligkeitStatus` aus Task 2
- Produces:
  - `SerienStand(laenge: Int, startPeriode: LocalDate?)`
  - `SerienRechner.aktuelleSerieMitStart(faelligkeiten: List<Faelligkeit>, bisPeriodeVon: LocalDate): SerienStand`
  - `SerienRechner.aktuelleSerie(faelligkeiten: List<Faelligkeit>, bisPeriodeVon: LocalDate): Int`
  - `SerienRechner.laengsteSerie(faelligkeiten: List<Faelligkeit>): Int`

`startPeriode` ist die früheste Periode der laufenden Serie und `null`, wenn die Serie leer ist. Task 12 braucht sie, um zu erkennen, welche Serienschwellen in dieser Serie bereits Bonus erzeugt haben.

Regeln, gruppiert nach `periodeVon`: eine Periode mit mindestens einer `VERPASST`-Fälligkeit bricht die Serie. Eine Periode mit mindestens einer `ERLEDIGT` und ohne `VERPASST` zählt. Eine Periode ganz ohne `ERLEDIGT` und ohne `VERPASST` — also nur ausgelassen oder noch offen — wird übersprungen: sie bricht nicht und zählt nicht.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class SerienRechnerTest {

    private val rechner = SerienRechner()
    private val start = LocalDate.of(2026, 3, 2)

    private fun tag(versatz: Long, status: FaelligkeitStatus) = Faelligkeit(
        id = versatz + 1,
        aktivitaetId = 1,
        periodeVon = start.plusDays(versatz),
        periodeBis = start.plusDays(versatz),
        geplantFuer = start.plusDays(versatz).atTime(8, 0),
        sollAnzahl = 1,
        status = status,
    )

    @Test
    fun `vier erledigte Tage in Folge ergeben Serie vier`() {
        val f = (0L..3L).map { tag(it, FaelligkeitStatus.ERLEDIGT) }

        assertEquals(4, rechner.aktuelleSerie(f, start.plusDays(3)))
    }

    @Test
    fun `eine verpasste Periode bricht die Serie`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.VERPASST),
            tag(2, FaelligkeitStatus.ERLEDIGT),
            tag(3, FaelligkeitStatus.ERLEDIGT),
        )

        assertEquals(2, rechner.aktuelleSerie(f, start.plusDays(3)))
    }

    @Test
    fun `eine ausgelassene Periode bricht die Serie nicht zaehlt aber auch nicht`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.AUSGELASSEN),
            tag(2, FaelligkeitStatus.ERLEDIGT),
        )

        assertEquals(2, rechner.aktuelleSerie(f, start.plusDays(2)))
    }

    @Test
    fun `eine noch offene heutige Periode senkt die Serie nicht`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.OFFEN),
        )

        assertEquals(2, rechner.aktuelleSerie(f, start.plusDays(2)))
    }

    @Test
    fun `eine Periode mit mehreren Faelligkeiten bricht sobald eine verpasst ist`() {
        val morgens = tag(1, FaelligkeitStatus.ERLEDIGT).copy(id = 100)
        val abends = tag(1, FaelligkeitStatus.VERPASST).copy(id = 101)
        val f = listOf(tag(0, FaelligkeitStatus.ERLEDIGT), morgens, abends, tag(2, FaelligkeitStatus.ERLEDIGT))

        assertEquals(1, rechner.aktuelleSerie(f, start.plusDays(2)))
    }

    @Test
    fun `laengste Serie findet den laengsten Block der Historie`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.ERLEDIGT),
            tag(3, FaelligkeitStatus.VERPASST),
            tag(4, FaelligkeitStatus.ERLEDIGT),
        )

        assertEquals(3, rechner.laengsteSerie(f))
    }

    @Test
    fun `ohne Historie ist die Serie null`() {
        assertEquals(0, rechner.aktuelleSerie(emptyList(), start))
        assertEquals(0, rechner.laengsteSerie(emptyList()))
    }

    @Test
    fun `der Serienstand nennt die erste Periode der laufenden Serie`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.VERPASST),
            tag(2, FaelligkeitStatus.ERLEDIGT),
            tag(3, FaelligkeitStatus.AUSGELASSEN),
            tag(4, FaelligkeitStatus.ERLEDIGT),
        )

        val stand = rechner.aktuelleSerieMitStart(f, start.plusDays(4))

        assertEquals(2, stand.laenge)
        assertEquals(start.plusDays(2), stand.startPeriode)
    }

    @Test
    fun `ohne laufende Serie gibt es keine Startperiode`() {
        val f = listOf(tag(0, FaelligkeitStatus.VERPASST))

        assertEquals(SerienStand(0, null), rechner.aktuelleSerieMitStart(f, start))
    }

    // Die folgenden drei nageln fest, was die Serienlogik sonst nur zufaellig
    // richtig macht: die Stichtagsgrenze und die Brueckenwirkung neutraler
    // Perioden. Task 12 rechnet auf beidem Bonuszahlungen aus.

    @Test
    fun `Perioden nach dem Stichtag zaehlen nicht mit`() {
        val f = (0L..2L).map { tag(it, FaelligkeitStatus.ERLEDIGT) }

        assertEquals(2, rechner.aktuelleSerie(f, start.plusDays(1)))
    }

    @Test
    fun `eine verpasste Periode nach dem Stichtag bricht die Serie nicht`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.VERPASST),
        )

        val stand = rechner.aktuelleSerieMitStart(f, start.plusDays(1))

        assertEquals(2, stand.laenge)
        assertEquals(start, stand.startPeriode)
    }

    @Test
    fun `eine ausgelassene Periode in der Mitte teilt die laengste Serie nicht`() {
        val f = listOf(
            tag(0, FaelligkeitStatus.ERLEDIGT),
            tag(1, FaelligkeitStatus.ERLEDIGT),
            tag(2, FaelligkeitStatus.AUSGELASSEN),
            tag(3, FaelligkeitStatus.ERLEDIGT),
            tag(4, FaelligkeitStatus.ERLEDIGT),
        )

        assertEquals(4, rechner.laengsteSerie(f))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :domain:test --tests "*SerienRechnerTest*"`
Expected: Kompilierfehler `Unresolved reference: SerienRechner`.

- [ ] **Step 3: Implementieren**

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDate

/**
 * Serien werden in Perioden gezaehlt: Tage bei taeglichen Zeitplaenen,
 * Wochen bei Wochenzielen. Die Gruppierung ueber periodeVon macht beides gleich.
 */
data class SerienStand(val laenge: Int, val startPeriode: LocalDate?)

class SerienRechner {

    private enum class PeriodenErgebnis { ZAEHLT, NEUTRAL, BRICHT }

    fun aktuelleSerie(faelligkeiten: List<Faelligkeit>, bisPeriodeVon: LocalDate): Int =
        aktuelleSerieMitStart(faelligkeiten, bisPeriodeVon).laenge

    fun aktuelleSerieMitStart(
        faelligkeiten: List<Faelligkeit>,
        bisPeriodeVon: LocalDate,
    ): SerienStand {
        var laenge = 0
        var start: LocalDate? = null
        perioden(faelligkeiten)
            .filterKeys { !it.isAfter(bisPeriodeVon) }
            .toSortedMap(reverseOrder())
            .forEach { (periode, gruppe) ->
                when (bewerte(gruppe)) {
                    PeriodenErgebnis.BRICHT -> return SerienStand(laenge, start)
                    PeriodenErgebnis.ZAEHLT -> {
                        laenge++
                        start = periode
                    }
                    PeriodenErgebnis.NEUTRAL -> Unit
                }
            }
        return SerienStand(laenge, start)
    }

    fun laengsteSerie(faelligkeiten: List<Faelligkeit>): Int {
        var laufend = 0
        var beste = 0
        perioden(faelligkeiten).toSortedMap().forEach { (_, gruppe) ->
            when (bewerte(gruppe)) {
                PeriodenErgebnis.BRICHT -> laufend = 0
                PeriodenErgebnis.ZAEHLT -> {
                    laufend++
                    if (laufend > beste) beste = laufend
                }
                PeriodenErgebnis.NEUTRAL -> Unit
            }
        }
        return beste
    }

    private fun perioden(faelligkeiten: List<Faelligkeit>): Map<LocalDate, List<Faelligkeit>> =
        faelligkeiten.groupBy { it.periodeVon }

    private fun bewerte(gruppe: List<Faelligkeit>): PeriodenErgebnis = when {
        gruppe.any { it.status == FaelligkeitStatus.VERPASST } -> PeriodenErgebnis.BRICHT
        gruppe.any { it.status == FaelligkeitStatus.ERLEDIGT } -> PeriodenErgebnis.ZAEHLT
        else -> PeriodenErgebnis.NEUTRAL
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :domain:test --tests "*SerienRechnerTest*"`
Expected: `BUILD SUCCESSFUL`, 12 Tests bestanden.

- [ ] **Step 5: Commit**

```bash
git add domain/
git commit -m "feat(domain): SerienRechner fuer aktuelle und laengste Serie"
```

---

### Task 7: PunkteRechner

**Files:**
- Create: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/rechner/PunkteRechner.kt`
- Test: `domain/src/test/kotlin/de/vanhake/dayorganizer/domain/rechner/PunkteRechnerTest.kt`

**Interfaces:**
- Consumes: `Aktivitaet`, `Faelligkeit`, `Buchung`, `BuchungsArt` aus Task 2
- Produces:
  - `ErledigungsKontext(aktivitaet: Aktivitaet, faelligkeitVorher: Faelligkeit, zeitpunkt: LocalDateTime, serieNachher: Int, gebuchteSchwellen: Set<Int>, tagesFaelligkeitenNachher: List<Faelligkeit>, tagesbonusPunkte: Int, tagesbonusBereitsGebucht: Boolean)`
  - `PunkteRechner.buchungen(kontext: ErledigungsKontext): List<Buchung>` — Reihenfolge: Erledigung, Pünktlichkeitsbonus, Serienboni aufsteigend nach Schwelle, Tagesbonus. Alle Buchungen haben `id = 0`.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.aktivitaet
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class PunkteRechnerTest {

    private val rechner = PunkteRechner()
    private val tag = LocalDate.of(2026, 3, 2)

    private val tabletten = aktivitaet(
        basisPunkte = 10,
        kulanzMinuten = 60,
        puenktlichkeitsBonus = 5,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
    )

    private val faelligkeit = Faelligkeit(
        id = 42,
        aktivitaetId = tabletten.id,
        periodeVon = tag,
        periodeBis = tag,
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0),
        sollAnzahl = 1,
    )

    private fun kontext(
        zeitpunkt: LocalDateTime,
        serieNachher: Int = 1,
        gebuchteSchwellen: Set<Int> = emptySet(),
        aktivitaet: de.vanhake.dayorganizer.domain.model.Aktivitaet = tabletten,
        tagesFaelligkeitenNachher: List<Faelligkeit> =
            listOf(faelligkeit.copy(status = FaelligkeitStatus.ERLEDIGT, istAnzahl = 1)),
        tagesbonusPunkte: Int = 0,
        tagesbonusBereitsGebucht: Boolean = false,
    ) = ErledigungsKontext(
        aktivitaet = aktivitaet,
        faelligkeitVorher = faelligkeit,
        zeitpunkt = zeitpunkt,
        serieNachher = serieNachher,
        gebuchteSchwellen = gebuchteSchwellen,
        tagesFaelligkeitenNachher = tagesFaelligkeitenNachher,
        tagesbonusPunkte = tagesbonusPunkte,
        tagesbonusBereitsGebucht = tagesbonusBereitsGebucht,
    )

    @Test
    fun `innerhalb des Kulanzfensters gibt es Basis und Puenktlichkeitsbonus`() {
        val b = rechner.buchungen(kontext(LocalDateTime.of(2026, 3, 2, 8, 30)))

        assertEquals(listOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), b.map { it.art })
        assertEquals(listOf(10, 5), b.map { it.betrag })
        assertEquals(42, b.first().faelligkeitId)
    }

    @Test
    fun `vor dem Sollzeitpunkt zaehlt das Kulanzfenster ebenfalls`() {
        val b = rechner.buchungen(kontext(LocalDateTime.of(2026, 3, 2, 7, 15)))

        assertEquals(15, b.sumOf { it.betrag })
    }

    @Test
    fun `ausserhalb des Kulanzfensters gibt es nur Basispunkte`() {
        val b = rechner.buchungen(kontext(LocalDateTime.of(2026, 3, 2, 12, 0)))

        assertEquals(listOf(BuchungsArt.ERLEDIGUNG), b.map { it.art })
        assertEquals(10, b.single().betrag)
    }

    @Test
    fun `Zaehlziele ohne Sollzeitpunkt bekommen keinen Puenktlichkeitsbonus`() {
        val sport = aktivitaet(
            basisPunkte = 20,
            puenktlichkeitsBonus = 5,
            zeitplan = Zeitplan.Wochenziel(anzahl = 3),
        )
        val wochenziel = faelligkeit.copy(geplantFuer = null, sollAnzahl = 3)
        val k = ErledigungsKontext(
            aktivitaet = sport,
            faelligkeitVorher = wochenziel,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 18, 0),
            serieNachher = 1,
            gebuchteSchwellen = emptySet(),
            tagesFaelligkeitenNachher = listOf(wochenziel.copy(istAnzahl = 1)),
            tagesbonusPunkte = 0,
            tagesbonusBereitsGebucht = false,
        )

        val b = rechner.buchungen(k)

        assertEquals(listOf(BuchungsArt.ERLEDIGUNG), b.map { it.art })
        assertEquals(20, b.single().betrag)
    }

    @Test
    fun `eine erreichte Serienschwelle wird einmal gebucht`() {
        val mitSchwellen = tabletten.copy(
            serienSchwellen = listOf(SerienSchwelle(7, 10), SerienSchwelle(30, 50)),
        )

        val b = rechner.buchungen(
            kontext(LocalDateTime.of(2026, 3, 2, 8, 5), serieNachher = 7, aktivitaet = mitSchwellen),
        )

        assertEquals(
            listOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS, BuchungsArt.SERIENBONUS),
            b.map { it.art },
        )
        assertEquals(10, b.last().betrag)
        assertEquals(7, b.last().schwelle)
    }

    @Test
    fun `eine bereits gebuchte Schwelle wird nicht erneut gebucht`() {
        val mitSchwellen = tabletten.copy(serienSchwellen = listOf(SerienSchwelle(7, 10)))

        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 8, 5),
                serieNachher = 9,
                gebuchteSchwellen = setOf(7),
                aktivitaet = mitSchwellen,
            ),
        )

        assertEquals(listOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), b.map { it.art })
    }

    @Test
    fun `der Tagesbonus kommt wenn alle heutigen Faelligkeiten erledigt sind`() {
        val zweite = faelligkeit.copy(id = 43, status = FaelligkeitStatus.ERLEDIGT, istAnzahl = 1)
        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 8, 5),
                tagesFaelligkeitenNachher = listOf(
                    faelligkeit.copy(status = FaelligkeitStatus.ERLEDIGT, istAnzahl = 1),
                    zweite,
                ),
                tagesbonusPunkte = 15,
            ),
        )

        assertEquals(BuchungsArt.TAGESBONUS, b.last().art)
        assertEquals(15, b.last().betrag)
    }

    @Test
    fun `eine ausgelassene Faelligkeit verhindert den Tagesbonus`() {
        val ausgelassen = faelligkeit.copy(id = 43, status = FaelligkeitStatus.AUSGELASSEN)
        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 8, 5),
                tagesFaelligkeitenNachher = listOf(
                    faelligkeit.copy(status = FaelligkeitStatus.ERLEDIGT, istAnzahl = 1),
                    ausgelassen,
                ),
                tagesbonusPunkte = 15,
            ),
        )

        assertEquals(setOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), b.map { it.art }.toSet())
    }

    @Test
    fun `ein bereits gebuchter Tagesbonus wird nicht verdoppelt`() {
        val b = rechner.buchungen(
            kontext(
                LocalDateTime.of(2026, 3, 2, 8, 5),
                tagesbonusPunkte = 15,
                tagesbonusBereitsGebucht = true,
            ),
        )

        assertEquals(setOf(BuchungsArt.ERLEDIGUNG, BuchungsArt.PUENKTLICHKEITSBONUS), b.map { it.art }.toSet())
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :domain:test --tests "*PunkteRechnerTest*"`
Expected: Kompilierfehler `Unresolved reference: PunkteRechner`.

Vier weitere Tests gehören dazu, weil sie Regeln festnageln, die die neun oben
nur zufällig richtig treffen:

- `mehrere auf einmal erreichte Schwellen werden aufsteigend gebucht` —
  `serienSchwellen = listOf(SerienSchwelle(30, 50), SerienSchwelle(7, 10))`,
  also **absteigend** deklariert, mit `puenktlichkeitsBonus = 0` und
  `serieNachher = 30`. Erwartet: Arten `[ERLEDIGUNG, SERIENBONUS, SERIENBONUS]`,
  `schwelle`-Werte `[7, 30]`, Summe 70. Ohne `sortedBy` schlägt der Test fehl.
- `genau am Rand des Kulanzfensters gibt es den Bonus noch` — Soll 08:00,
  Kulanz 60, Erledigung 09:00. Erwartet: Summe 15.
- `eine Minute nach dem Kulanzfenster gibt es ihn nicht mehr` — dasselbe um
  09:01. Erwartet: Summe 10.
- `ein Tag ohne Faelligkeiten bekommt keinen Tagesbonus` —
  `tagesFaelligkeitenNachher = emptyList()` bei `tagesbonusPunkte = 15`.
  Erwartet: kein `TAGESBONUS`. Ohne die `isNotEmpty()`-Prüfung zahlt Kotlins
  `all {}` auf der leeren Liste den Bonus aus.

- [ ] **Step 3: Implementieren**

```kotlin
package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * @param faelligkeitVorher Zustand der Faelligkeit vor dieser Erledigung.
 * @param serieNachher Serienlaenge, nachdem diese Erledigung verbucht ist.
 * @param gebuchteSchwellen Schwellenwerte, die in der laufenden Serie schon
 *   Bonus erzeugt haben. Bricht die Serie, leert der Aufrufer diese Menge.
 * @param tagesFaelligkeitenNachher alle Faelligkeiten des Tages im Zustand
 *   nach dieser Erledigung.
 */
data class ErledigungsKontext(
    val aktivitaet: Aktivitaet,
    val faelligkeitVorher: Faelligkeit,
    val zeitpunkt: LocalDateTime,
    val serieNachher: Int,
    val gebuchteSchwellen: Set<Int>,
    val tagesFaelligkeitenNachher: List<Faelligkeit>,
    val tagesbonusPunkte: Int,
    val tagesbonusBereitsGebucht: Boolean,
)

/** Einziger Ort im Projekt, an dem Punkte entstehen. */
class PunkteRechner {

    fun buchungen(kontext: ErledigungsKontext): List<Buchung> = buildList {
        val a = kontext.aktivitaet

        add(buchung(kontext, BuchungsArt.ERLEDIGUNG, a.basisPunkte))

        if (a.puenktlichkeitsBonus > 0 && istPuenktlich(kontext)) {
            add(buchung(kontext, BuchungsArt.PUENKTLICHKEITSBONUS, a.puenktlichkeitsBonus))
        }

        a.serienSchwellen
            .filter { it.perioden <= kontext.serieNachher && it.perioden !in kontext.gebuchteSchwellen }
            .sortedBy { it.perioden }
            .forEach { add(buchung(kontext, BuchungsArt.SERIENBONUS, it.bonus, schwelle = it.perioden)) }

        if (verdientTagesbonus(kontext)) {
            add(
                Buchung(
                    art = BuchungsArt.TAGESBONUS,
                    betrag = kontext.tagesbonusPunkte,
                    zeitpunkt = kontext.zeitpunkt,
                ),
            )
        }
    }

    private fun istPuenktlich(kontext: ErledigungsKontext): Boolean {
        val soll = kontext.faelligkeitVorher.geplantFuer ?: return false
        val abweichung = abs(Duration.between(soll, kontext.zeitpunkt).toMinutes())
        return abweichung <= kontext.aktivitaet.kulanzMinuten
    }

    private fun verdientTagesbonus(kontext: ErledigungsKontext): Boolean =
        kontext.tagesbonusPunkte > 0 &&
            !kontext.tagesbonusBereitsGebucht &&
            kontext.tagesFaelligkeitenNachher.isNotEmpty() &&
            kontext.tagesFaelligkeitenNachher.all { it.status == FaelligkeitStatus.ERLEDIGT }

    private fun buchung(
        kontext: ErledigungsKontext,
        art: BuchungsArt,
        betrag: Int,
        schwelle: Int? = null,
    ) = Buchung(
        art = art,
        betrag = betrag,
        zeitpunkt = kontext.zeitpunkt,
        aktivitaetId = kontext.aktivitaet.id,
        faelligkeitId = kontext.faelligkeitVorher.id,
        schwelle = schwelle,
    )
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :domain:test --tests "*PunkteRechnerTest*"`
Expected: `BUILD SUCCESSFUL`, 13 Tests bestanden. Danach `./gradlew :domain:test`
für das ganze Modul.

- [ ] **Step 5: Commit**

```bash
git add domain/
git commit -m "feat(domain): PunkteRechner erzeugt Buchungen fuer eine Erledigung"
```

---

### Task 8: Textkodierung der Zeitpläne

Room speichert den Zeitplan nicht als undurchsichtiges Blob, sondern in lesbaren Spalten. Diese Aufgabe liefert die Umwandlung in beide Richtungen und ist reine JVM-Logik, also ohne Emulator testbar.

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/mapper/ZeitplanKodierung.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/data/mapper/ZeitplanKodierungTest.kt`

**Interfaces:**
- Consumes: `Zeitplan`, `Anstoss`, `SerienSchwelle` aus Task 2
- Produces:
  - `ZeitplanFelder(typ: String, zeiten: String, wochentage: String, intervallStunden: Int?, fensterVon: String?, fensterBis: String?, zielAnzahl: Int?, anstoss: String)`
  - `ZeitplanKodierung.kodiere(zeitplan: Zeitplan): ZeitplanFelder`
  - `ZeitplanKodierung.dekodiere(felder: ZeitplanFelder): Zeitplan`
  - `ZeitplanKodierung.kodiereSchwellen(schwellen: List<SerienSchwelle>): String`
  - `ZeitplanKodierung.dekodiereSchwellen(text: String): List<SerienSchwelle>`

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class ZeitplanKodierungTest {

    private fun rundlauf(plan: Zeitplan) {
        assertEquals(plan, ZeitplanKodierung.dekodiere(ZeitplanKodierung.kodiere(plan)))
    }

    @Test
    fun `feste Zeiten ueberstehen den Rundlauf`() {
        rundlauf(
            Zeitplan.FesteZeiten(
                zeiten = listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)),
                wochentage = setOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY),
            ),
        )
    }

    @Test
    fun `Intervall uebersteht den Rundlauf`() {
        rundlauf(
            Zeitplan.Intervall(
                alleNStunden = 3,
                von = LocalTime.of(8, 0),
                bis = LocalTime.of(22, 0),
                wochentage = DayOfWeek.entries.toSet(),
            ),
        )
    }

    @Test
    fun `Tagesziel mit Anstoessen uebersteht den Rundlauf`() {
        rundlauf(
            Zeitplan.Tagesziel(
                anzahl = 3,
                wochentage = setOf(DayOfWeek.TUESDAY),
                anstossZeiten = listOf(LocalTime.of(12, 0), LocalTime.of(19, 0)),
            ),
        )
    }

    @Test
    fun `Wochenziel mit Anstoessen uebersteht den Rundlauf`() {
        rundlauf(
            Zeitplan.Wochenziel(
                anzahl = 3,
                anstoss = listOf(
                    Anstoss(DayOfWeek.TUESDAY, LocalTime.of(18, 0)),
                    Anstoss(DayOfWeek.THURSDAY, LocalTime.of(18, 0)),
                ),
            ),
        )
    }

    @Test
    fun `leere Anstossliste bleibt leer`() {
        rundlauf(Zeitplan.Wochenziel(anzahl = 2))
        rundlauf(Zeitplan.Tagesziel(anzahl = 2, wochentage = setOf(DayOfWeek.FRIDAY)))
    }

    @Test
    fun `Serienschwellen ueberstehen den Rundlauf`() {
        val schwellen = listOf(SerienSchwelle(7, 10), SerienSchwelle(30, 50))

        assertEquals(
            schwellen,
            ZeitplanKodierung.dekodiereSchwellen(ZeitplanKodierung.kodiereSchwellen(schwellen)),
        )
        assertEquals(emptyList(), ZeitplanKodierung.dekodiereSchwellen(""))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*ZeitplanKodierungTest*"`
Expected: Kompilierfehler `Unresolved reference: ZeitplanKodierung`.

- [ ] **Step 3: Implementieren**

```kotlin
package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalTime

/** Flache Darstellung eines Zeitplans, wie sie in der Datenbank liegt. */
data class ZeitplanFelder(
    val typ: String,
    val zeiten: String = "",
    val wochentage: String = "",
    val intervallStunden: Int? = null,
    val fensterVon: String? = null,
    val fensterBis: String? = null,
    val zielAnzahl: Int? = null,
    val anstoss: String = "",
)

/**
 * Wandelt Zeitplaene in lesbare Spaltenwerte um und zurueck.
 * Lesbar deshalb, weil eine falsch gespeicherte Erinnerung sonst nur
 * mit Debugger zu finden waere.
 */
object ZeitplanKodierung {

    private const val FESTE_ZEITEN = "FESTE_ZEITEN"
    private const val INTERVALL = "INTERVALL"
    private const val TAGESZIEL = "TAGESZIEL"
    private const val WOCHENZIEL = "WOCHENZIEL"

    fun kodiere(zeitplan: Zeitplan): ZeitplanFelder = when (zeitplan) {
        is Zeitplan.FesteZeiten -> ZeitplanFelder(
            typ = FESTE_ZEITEN,
            zeiten = zeitplan.zeiten.joinToString(",") { it.toString() },
            wochentage = kodiereTage(zeitplan.wochentage),
        )

        is Zeitplan.Intervall -> ZeitplanFelder(
            typ = INTERVALL,
            wochentage = kodiereTage(zeitplan.wochentage),
            intervallStunden = zeitplan.alleNStunden,
            fensterVon = zeitplan.von.toString(),
            fensterBis = zeitplan.bis.toString(),
        )

        is Zeitplan.Tagesziel -> ZeitplanFelder(
            typ = TAGESZIEL,
            wochentage = kodiereTage(zeitplan.wochentage),
            zielAnzahl = zeitplan.anzahl,
            zeiten = zeitplan.anstossZeiten.joinToString(",") { it.toString() },
        )

        is Zeitplan.Wochenziel -> ZeitplanFelder(
            typ = WOCHENZIEL,
            zielAnzahl = zeitplan.anzahl,
            anstoss = zeitplan.anstoss.joinToString(",") { "${it.tag}@${it.zeit}" },
        )
    }

    fun dekodiere(felder: ZeitplanFelder): Zeitplan = when (felder.typ) {
        FESTE_ZEITEN -> Zeitplan.FesteZeiten(
            zeiten = dekodiereZeiten(felder.zeiten),
            wochentage = dekodiereTage(felder.wochentage),
        )

        INTERVALL -> Zeitplan.Intervall(
            alleNStunden = requireNotNull(felder.intervallStunden) { "Intervall ohne Stundenwert" },
            von = LocalTime.parse(requireNotNull(felder.fensterVon)),
            bis = LocalTime.parse(requireNotNull(felder.fensterBis)),
            wochentage = dekodiereTage(felder.wochentage),
        )

        TAGESZIEL -> Zeitplan.Tagesziel(
            anzahl = requireNotNull(felder.zielAnzahl) { "Tagesziel ohne Anzahl" },
            wochentage = dekodiereTage(felder.wochentage),
            anstossZeiten = dekodiereZeiten(felder.zeiten),
        )

        WOCHENZIEL -> Zeitplan.Wochenziel(
            anzahl = requireNotNull(felder.zielAnzahl) { "Wochenziel ohne Anzahl" },
            anstoss = felder.anstoss.split(",")
                .filter { it.isNotBlank() }
                .map {
                    val (tag, zeit) = it.split("@", limit = 2)
                    Anstoss(DayOfWeek.valueOf(tag), LocalTime.parse(zeit))
                },
        )

        else -> error("Unbekannter Zeitplantyp: ${felder.typ}")
    }

    fun kodiereSchwellen(schwellen: List<SerienSchwelle>): String =
        schwellen.joinToString(",") { "${it.perioden}:${it.bonus}" }

    fun dekodiereSchwellen(text: String): List<SerienSchwelle> =
        text.split(",").filter { it.isNotBlank() }.map {
            val (perioden, bonus) = it.split(":", limit = 2)
            SerienSchwelle(perioden.toInt(), bonus.toInt())
        }

    private fun kodiereTage(tage: Set<DayOfWeek>): String =
        tage.sorted().joinToString(",") { it.name }

    private fun dekodiereTage(text: String): Set<DayOfWeek> =
        text.split(",").filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it) }.toSet()

    private fun dekodiereZeiten(text: String): List<LocalTime> =
        text.split(",").filter { it.isNotBlank() }.map { LocalTime.parse(it) }
}
```

- [ ] **Step 4: Fehlerfälle diagnostizierbar machen und festnageln**

Die sechs Rundlauftests oben decken nur den guten Fall ab. Der Grund für die
lesbare Kodierung ist aber der schlechte Fall — und der muss sagen, was kaputt
ist. Rohes `val (tag, zeit) = it.split("@")` wirft eine
`IndexOutOfBoundsException` ohne Feldnamen und ohne den Wert; damit ist man
genau da, wo man ohne lesbare Kodierung auch wäre.

Ergänze deshalb kleine Parse-Hilfen, die Wert **und** Feldnamen in die Meldung
schreiben, etwa `Ungueltiger Wochentag "MONTAG" in "wochentage"`, und zwar für
den Wochentag (`DayOfWeek.valueOf`), die Uhrzeit (`LocalTime.parse`), die Zahl
(`toInt`) sowie für beide Trennzeichen-Splits (`@` und `:`). Nichts davon darf
einen Fehler schlucken und einen Vorgabewert zurückgeben.

Dazu zwölf weitere Tests: unbekannter `typ`; `INTERVALL` ohne
`intervallStunden`; `TAGESZIEL` ohne `zielAnzahl`; Anstoss ohne `@`;
Serienschwelle ohne `:`; Serienschwelle mit unlesbarer Zahl; ungültiger
Wochentag in `wochentage`; ungültiger Wochentag im Anstoss; ungültige Uhrzeit
in `zeiten`; ungültige Uhrzeit in `fensterVon`; dazu zwei positive Fälle,
`FesteZeiten` mit leerer Wochentagsmenge und eine `LocalTime` mit Sekunden.

Bei den Wochentagstests reicht es **nicht**, auf den Wert zu prüfen: die
JDK-Meldung `No enum constant java.time.DayOfWeek.MONTAG` enthält ihn schon und
ist derselbe Ausnahmetyp. Prüfe auf den Feldnamen — sonst besteht der Test auch
ohne den Wrapper, den er bewachen soll.

- [ ] **Step 5: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*ZeitplanKodierungTest*"`
Expected: `BUILD SUCCESSFUL`, 18 Tests bestanden.

- [ ] **Step 6: Commit**

```bash
git add app/
git commit -m "feat(data): lesbare Textkodierung der Zeitplaene"
```

---

### Task 9: Room-Schicht

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/Converters.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/AktivitaetEntity.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/FaelligkeitEntity.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/BuchungEntity.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/AktivitaetDao.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/FaelligkeitDao.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/BuchungDao.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/DayOrganizerDatenbank.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/data/db/DaoTest.kt`

**Interfaces:**
- Consumes: `ZeitplanFelder` aus Task 8
- Produces:
  - `DayOrganizerDatenbank` (Room, `version = 1`, `exportSchema = true`) mit `aktivitaetDao()`, `faelligkeitDao()`, `buchungDao()`
  - `FaelligkeitEntity.schluessel: String` im Format `"<aktivitaetId>|<periodeVon>|<geplantFuer oder ZIEL>"` mit eindeutigem Index. Er macht das Materialisieren idempotent: ein zweiter Lauf für denselben Zeitraum legt nichts doppelt an.
  - `FaelligkeitDao.anlegenFallsNeu(eintraege: List<FaelligkeitEntity>)` mit `OnConflictStrategy.IGNORE`

- [ ] **Step 1: Fehlschlagenden Test schreiben**

`app/src/test/kotlin/de/vanhake/dayorganizer/data/db/DaoTest.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class DaoTest {

    private lateinit var db: DayOrganizerDatenbank

    @Before
    fun aufbauen() {
        val kontext = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(kontext, DayOrganizerDatenbank::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun abbauen() = db.close()

    private fun aktivitaet() = AktivitaetEntity(
        name = "Tabletten",
        notiz = "",
        farbe = 1,
        basisPunkte = 10,
        kulanzMinuten = 60,
        puenktlichkeitsBonus = 5,
        serienSchwellen = "7:10",
        nachfassAnzahl = 2,
        nachfassAbstandMinuten = 15,
        aktiv = true,
        erstelltAm = LocalDate.of(2026, 1, 1),
        archiviertAm = null,
        zeitplanTyp = "FESTE_ZEITEN",
        zeitplanZeiten = "08:00",
        zeitplanWochentage = "MONDAY",
        zeitplanIntervallStunden = null,
        zeitplanFensterVon = null,
        zeitplanFensterBis = null,
        zeitplanZielAnzahl = null,
        zeitplanAnstoss = "",
    )

    @Test
    fun aktivitaet_ueberlebt_den_Rundlauf() = runTest {
        val id = db.aktivitaetDao().einfuegen(aktivitaet())

        val gelesen = db.aktivitaetDao().nachId(id)

        assertEquals("Tabletten", gelesen?.name)
        assertEquals(LocalDate.of(2026, 1, 1), gelesen?.erstelltAm)
    }

    @Test
    fun doppeltes_Materialisieren_legt_nichts_doppelt_an() = runTest {
        val id = db.aktivitaetDao().einfuegen(aktivitaet())
        val f = FaelligkeitEntity(
            aktivitaetId = id,
            schluessel = "$id|2026-03-02|2026-03-02T08:00",
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0),
            sollAnzahl = 1,
            istAnzahl = 0,
            status = "OFFEN",
            erledigtAm = null,
            warPuenktlich = false,
        )

        db.faelligkeitDao().anlegenFallsNeu(listOf(f))
        db.faelligkeitDao().anlegenFallsNeu(listOf(f))

        assertEquals(
            1,
            db.faelligkeitDao().fuerZeitraum(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 3)).size,
        )
    }

    @Test
    fun kontostand_ist_die_Summe_der_Buchungen() = runTest {
        val id = db.aktivitaetDao().einfuegen(aktivitaet())
        db.buchungDao().einfuegen(
            listOf(
                BuchungEntity(
                    art = "ERLEDIGUNG", betrag = 10,
                    zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 5),
                    aktivitaetId = id, faelligkeitId = null, belohnungId = null,
                    schwelle = null, notiz = "",
                ),
                BuchungEntity(
                    art = "EINLOESUNG", betrag = -4,
                    zeitpunkt = LocalDateTime.of(2026, 3, 2, 9, 0),
                    aktivitaetId = null, faelligkeitId = null, belohnungId = null,
                    schwelle = null, notiz = "",
                ),
            ),
        )

        assertEquals(6, db.buchungDao().kontostandJetzt())
    }

    @Test
    fun offene_Faelligkeiten_bis_Zeitpunkt_werden_gefunden() = runTest {
        val id = db.aktivitaetDao().einfuegen(aktivitaet())
        fun eintrag(stunde: Int, status: String) = FaelligkeitEntity(
            aktivitaetId = id,
            schluessel = "$id|2026-03-02|2026-03-02T$stunde:00",
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = LocalDateTime.of(2026, 3, 2, stunde, 0),
            sollAnzahl = 1, istAnzahl = 0, status = status,
            erledigtAm = null, warPuenktlich = false,
        )
        db.faelligkeitDao().anlegenFallsNeu(
            listOf(eintrag(8, "OFFEN"), eintrag(12, "ERLEDIGT"), eintrag(20, "OFFEN")),
        )

        val offen = db.faelligkeitDao().offeneBis(LocalDateTime.of(2026, 3, 2, 13, 0))

        assertEquals(1, offen.size)
        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 0), offen.single().geplantFuer)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*DaoTest*"`
Expected: Kompilierfehler `Unresolved reference: DayOrganizerDatenbank`.

- [ ] **Step 3: Converters und Entities anlegen**

`data/db/Converters.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.LocalDateTime

class Converters {
    @TypeConverter fun datumAusText(text: String?): LocalDate? = text?.let(LocalDate::parse)
    @TypeConverter fun datumZuText(datum: LocalDate?): String? = datum?.toString()
    @TypeConverter fun zeitpunktAusText(text: String?): LocalDateTime? = text?.let(LocalDateTime::parse)
    @TypeConverter fun zeitpunktZuText(zeitpunkt: LocalDateTime?): String? = zeitpunkt?.toString()
}
```

`data/db/AktivitaetEntity.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(tableName = "aktivitaet")
data class AktivitaetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val notiz: String,
    val farbe: Int,
    val basisPunkte: Int,
    val kulanzMinuten: Int,
    val puenktlichkeitsBonus: Int,
    /** Kodiert als "7:10,30:50". */
    val serienSchwellen: String,
    val nachfassAnzahl: Int,
    val nachfassAbstandMinuten: Int,
    val aktiv: Boolean,
    val erstelltAm: LocalDate,
    val archiviertAm: LocalDate?,
    val zeitplanTyp: String,
    val zeitplanZeiten: String,
    val zeitplanWochentage: String,
    val zeitplanIntervallStunden: Int?,
    val zeitplanFensterVon: String?,
    val zeitplanFensterBis: String?,
    val zeitplanZielAnzahl: Int?,
    val zeitplanAnstoss: String,
)
```

`data/db/FaelligkeitEntity.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "faelligkeit",
    foreignKeys = [
        ForeignKey(
            entity = AktivitaetEntity::class,
            parentColumns = ["id"],
            childColumns = ["aktivitaetId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["schluessel"], unique = true),
        Index(value = ["aktivitaetId", "periodeVon"]),
        Index(value = ["status", "geplantFuer"]),
    ],
)
data class FaelligkeitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val aktivitaetId: Long,
    /** "<aktivitaetId>|<periodeVon>|<geplantFuer oder ZIEL>", macht Materialisieren idempotent. */
    val schluessel: String,
    val periodeVon: LocalDate,
    val periodeBis: LocalDate,
    val geplantFuer: LocalDateTime?,
    val sollAnzahl: Int,
    val istAnzahl: Int,
    val status: String,
    val erledigtAm: LocalDateTime?,
    val warPuenktlich: Boolean,
)
```

`data/db/BuchungEntity.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "buchung",
    indices = [Index(value = ["zeitpunkt"]), Index(value = ["aktivitaetId", "art"])],
)
data class BuchungEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val art: String,
    val betrag: Int,
    val zeitpunkt: LocalDateTime,
    val aktivitaetId: Long?,
    val faelligkeitId: Long?,
    val belohnungId: Long?,
    val schwelle: Int?,
    val notiz: String,
)
```

Auf einen Fremdschlüssel von `buchung` auf `aktivitaet` wird bewusst verzichtet: das Journal muss auch dann lesbar bleiben, wenn eine Aktivität irgendwann doch entfernt wird.

- [ ] **Step 4: DAOs und Datenbank anlegen**

`data/db/AktivitaetDao.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AktivitaetDao {

    @Insert
    suspend fun einfuegen(eintrag: AktivitaetEntity): Long

    @Update
    suspend fun aktualisieren(eintrag: AktivitaetEntity)

    @Query("SELECT * FROM aktivitaet WHERE id = :id")
    suspend fun nachId(id: Long): AktivitaetEntity?

    @Query("SELECT * FROM aktivitaet WHERE aktiv = 1 AND archiviertAm IS NULL ORDER BY name")
    suspend fun alleAktiven(): List<AktivitaetEntity>

    @Query("SELECT * FROM aktivitaet WHERE aktiv = 1 AND archiviertAm IS NULL ORDER BY name")
    fun beobachteAktive(): Flow<List<AktivitaetEntity>>

    @Query("UPDATE aktivitaet SET archiviertAm = :am, aktiv = 0 WHERE id = :id")
    suspend fun archivieren(id: Long, am: java.time.LocalDate)
}
```

`data/db/FaelligkeitDao.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

@Dao
interface FaelligkeitDao {

    /** IGNORE zusammen mit dem eindeutigen Schluessel macht Materialisieren idempotent. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun anlegenFallsNeu(eintraege: List<FaelligkeitEntity>)

    @Update
    suspend fun aktualisieren(eintrag: FaelligkeitEntity)

    @Query("SELECT * FROM faelligkeit WHERE id = :id")
    suspend fun nachId(id: Long): FaelligkeitEntity?

    // Dieselbe Sortierkonvention wie beobachteTag: terminierte Eintraege nach
    // Uhrzeit, Zaehlziele ohne Sollzeitpunkt dahinter. Ohne "geplantFuer IS NULL"
    // sortiert SQLite NULL nach vorn -- also genau andersherum.
    @Query(
        "SELECT * FROM faelligkeit WHERE periodeBis >= :von AND periodeVon <= :bis " +
            "ORDER BY periodeVon, geplantFuer IS NULL, geplantFuer",
    )
    suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<FaelligkeitEntity>

    @Query(
        "SELECT * FROM faelligkeit WHERE periodeBis >= :tag AND periodeVon <= :tag " +
            "ORDER BY geplantFuer IS NULL, geplantFuer",
    )
    fun beobachteTag(tag: LocalDate): Flow<List<FaelligkeitEntity>>

    @Query(
        "SELECT * FROM faelligkeit WHERE aktivitaetId = :aktivitaetId AND periodeVon = :periodeVon",
    )
    suspend fun fuerPeriode(aktivitaetId: Long, periodeVon: LocalDate): List<FaelligkeitEntity>

    @Query("SELECT * FROM faelligkeit WHERE aktivitaetId = :aktivitaetId ORDER BY periodeVon")
    suspend fun historie(aktivitaetId: Long): List<FaelligkeitEntity>

    /** Offene Faelligkeiten mit Sollzeitpunkt, deren Zeitpunkt vor [zeitpunkt] liegt. */
    @Query(
        "SELECT * FROM faelligkeit WHERE status = 'OFFEN' AND geplantFuer IS NOT NULL " +
            "AND geplantFuer < :zeitpunkt ORDER BY geplantFuer",
    )
    suspend fun offeneBis(zeitpunkt: LocalDateTime): List<FaelligkeitEntity>

    /** Offene Faelligkeiten, deren Periode vor [tag] endet — Kandidaten fuer VERPASST. */
    @Query("SELECT * FROM faelligkeit WHERE status = 'OFFEN' AND periodeBis < :tag")
    suspend fun offeneVorTag(tag: LocalDate): List<FaelligkeitEntity>

    @Query("SELECT * FROM faelligkeit WHERE status = 'OFFEN' AND periodeBis >= :abTag")
    suspend fun offeneAbTag(abTag: LocalDate): List<FaelligkeitEntity>
}
```

`data/db/BuchungDao.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface BuchungDao {

    @Insert
    suspend fun einfuegen(eintraege: List<BuchungEntity>)

    @Query("SELECT COALESCE(SUM(betrag), 0) FROM buchung")
    fun beobachteKontostand(): Flow<Int>

    @Query("SELECT COALESCE(SUM(betrag), 0) FROM buchung")
    suspend fun kontostandJetzt(): Int

    @Query(
        "SELECT schwelle FROM buchung WHERE art = 'SERIENBONUS' AND aktivitaetId = :aktivitaetId " +
            "AND zeitpunkt >= :seit AND schwelle IS NOT NULL",
    )
    suspend fun serienbonusSchwellenSeit(aktivitaetId: Long, seit: LocalDateTime): List<Int>

    @Query(
        "SELECT COUNT(*) FROM buchung WHERE art = 'TAGESBONUS' " +
            "AND zeitpunkt >= :tagesBeginn AND zeitpunkt < :tagesEnde",
    )
    suspend fun anzahlTagesboni(tagesBeginn: LocalDateTime, tagesEnde: LocalDateTime): Int

    @Query("SELECT * FROM buchung ORDER BY zeitpunkt DESC LIMIT :anzahl")
    suspend fun letzte(anzahl: Int): List<BuchungEntity>
}
```

`data/db/DayOrganizerDatenbank.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [AktivitaetEntity::class, FaelligkeitEntity::class, BuchungEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DayOrganizerDatenbank : RoomDatabase() {
    abstract fun aktivitaetDao(): AktivitaetDao
    abstract fun faelligkeitDao(): FaelligkeitDao
    abstract fun buchungDao(): BuchungDao
}
```

- [ ] **Step 5: Test laufen lassen und grünen Lauf bestätigen**

Zwei Tests gehören noch dazu, weil sie Zusagen festnageln, die sonst nur in
der SQL stehen:

- `Zaehlziele_stehen_hinter_den_terminierten_Eintraegen_des_Tages` — drei
  Fälligkeiten eines Tages in der Reihenfolge 20:00, Zählziel, 08:00 anlegen
  und prüfen, dass `fuerZeitraum` sie als 08:00, 20:00, Zählziel liefert. Gegen
  ein schlichtes `ORDER BY periodeVon, geplantFuer` schlägt der Test fehl.
- `leeres_Journal_hat_Kontostand_null` — `kontostandJetzt()` auf einer frischen
  Datenbank ist 0, nicht null.

Run: `./gradlew :app:testDebugUnitTest --tests "*DaoTest*"`
Expected: `BUILD SUCCESSFUL`, 6 Tests bestanden. Die Datei `app/schemas/de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank/1.json` ist entstanden und wird mit eingecheckt — der Migrationstest in Task 14 liest sie.

- [ ] **Step 6: Commit**

```bash
git add app/
git commit -m "feat(data): Room-Schema mit Aktivitaet, Faelligkeit und Buchungsjournal"
```

---

### Task 10: Repositories und Einstellungen

Die Use Cases sollen ohne Emulator testbar bleiben. Deshalb bekommen sie schmale Schnittstellen statt der DAOs, und die Room-Implementierungen liegen dahinter.

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/repository/Repositories.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/repository/RaumRepositories.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/mapper/EntityMapper.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/EinstellungenSpeicher.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/di/DatenModul.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/data/mapper/EntityMapperTest.kt`

**Interfaces:**
- Consumes: DAOs und Entities aus Task 9, `ZeitplanKodierung` aus Task 8, Domänenmodell aus Task 2
- Produces:
  - `fun AktivitaetEntity.zuDomaene(): Aktivitaet`, `fun Aktivitaet.zuEntity(): AktivitaetEntity`
  - `fun FaelligkeitEntity.zuDomaene(): Faelligkeit`, `fun Faelligkeit.zuEntity(): FaelligkeitEntity`, `fun Faelligkeit.schluessel(): String`
  - `fun BuchungEntity.zuDomaene(): Buchung`, `fun Buchung.zuEntity(): BuchungEntity`
  - Schnittstellen `AktivitaetRepository`, `FaelligkeitRepository`, `BuchungRepository` (Signaturen unten)
  - `Einstellungen(tagesbonusPunkte: Int, standardKulanzMinuten: Int)` und `EinstellungenSpeicher.beobachten(): Flow<Einstellungen>`, `EinstellungenSpeicher.lesen(): Einstellungen`, `EinstellungenSpeicher.schreiben(einstellungen: Einstellungen)`

- [ ] **Step 1: Fehlschlagenden Mapper-Test schreiben**

```kotlin
package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityMapperTest {

    private val aktivitaet = Aktivitaet(
        id = 7,
        name = "Sport",
        notiz = "Laufen oder Rad",
        farbe = 123,
        basisPunkte = 20,
        zeitplan = Zeitplan.FesteZeiten(
            zeiten = listOf(LocalTime.of(18, 0)),
            wochentage = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        ),
        kulanzMinuten = 45,
        puenktlichkeitsBonus = 5,
        serienSchwellen = listOf(SerienSchwelle(7, 10)),
        nachfassAnzahl = 1,
        nachfassAbstandMinuten = 20,
        aktiv = true,
        erstelltAm = LocalDate.of(2026, 2, 1),
        archiviertAm = null,
    )

    @Test
    fun `Aktivitaet uebersteht den Rundlauf`() {
        assertEquals(aktivitaet, aktivitaet.zuEntity().zuDomaene())
    }

    @Test
    fun `Faelligkeit uebersteht den Rundlauf`() {
        val f = Faelligkeit(
            id = 3,
            aktivitaetId = 7,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = LocalDateTime.of(2026, 3, 2, 18, 0),
            sollAnzahl = 1,
            istAnzahl = 1,
            status = FaelligkeitStatus.ERLEDIGT,
            erledigtAm = LocalDateTime.of(2026, 3, 2, 18, 12),
            warPuenktlich = true,
        )

        assertEquals(f, f.zuEntity().zuDomaene())
    }

    @Test
    fun `der Schluessel unterscheidet Sollzeitpunkt und Zaehlziel`() {
        val termin = Faelligkeit(
            aktivitaetId = 7,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 2),
            geplantFuer = LocalDateTime.of(2026, 3, 2, 18, 0),
            sollAnzahl = 1,
        )
        val ziel = termin.copy(geplantFuer = null, sollAnzahl = 3)

        assertEquals("7|2026-03-02|2026-03-02T18:00", termin.schluessel())
        assertEquals("7|2026-03-02|ZIEL", ziel.schluessel())
    }

    @Test
    fun `Buchung uebersteht den Rundlauf`() {
        val b = Buchung(
            id = 9,
            art = BuchungsArt.SERIENBONUS,
            betrag = 10,
            zeitpunkt = LocalDateTime.of(2026, 3, 2, 18, 12),
            aktivitaetId = 7,
            faelligkeitId = 3,
            belohnungId = null,
            schwelle = 7,
            notiz = "",
        )

        assertEquals(b, b.zuEntity().zuDomaene())
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*EntityMapperTest*"`
Expected: Kompilierfehler `Unresolved reference: zuEntity`.

- [ ] **Step 3: Mapper implementieren**

`data/mapper/EntityMapper.kt`:

```kotlin
package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.data.db.AktivitaetEntity
import de.vanhake.dayorganizer.data.db.BuchungEntity
import de.vanhake.dayorganizer.data.db.FaelligkeitEntity
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus

fun AktivitaetEntity.zuDomaene(): Aktivitaet = Aktivitaet(
    id = id,
    name = name,
    notiz = notiz,
    farbe = farbe,
    basisPunkte = basisPunkte,
    zeitplan = ZeitplanKodierung.dekodiere(
        ZeitplanFelder(
            typ = zeitplanTyp,
            zeiten = zeitplanZeiten,
            wochentage = zeitplanWochentage,
            intervallStunden = zeitplanIntervallStunden,
            fensterVon = zeitplanFensterVon,
            fensterBis = zeitplanFensterBis,
            zielAnzahl = zeitplanZielAnzahl,
            anstoss = zeitplanAnstoss,
        ),
    ),
    kulanzMinuten = kulanzMinuten,
    puenktlichkeitsBonus = puenktlichkeitsBonus,
    serienSchwellen = ZeitplanKodierung.dekodiereSchwellen(serienSchwellen),
    nachfassAnzahl = nachfassAnzahl,
    nachfassAbstandMinuten = nachfassAbstandMinuten,
    aktiv = aktiv,
    erstelltAm = erstelltAm,
    archiviertAm = archiviertAm,
)

fun Aktivitaet.zuEntity(): AktivitaetEntity {
    val felder = ZeitplanKodierung.kodiere(zeitplan)
    return AktivitaetEntity(
        id = id,
        name = name,
        notiz = notiz,
        farbe = farbe,
        basisPunkte = basisPunkte,
        kulanzMinuten = kulanzMinuten,
        puenktlichkeitsBonus = puenktlichkeitsBonus,
        serienSchwellen = ZeitplanKodierung.kodiereSchwellen(serienSchwellen),
        nachfassAnzahl = nachfassAnzahl,
        nachfassAbstandMinuten = nachfassAbstandMinuten,
        aktiv = aktiv,
        erstelltAm = erstelltAm,
        archiviertAm = archiviertAm,
        zeitplanTyp = felder.typ,
        zeitplanZeiten = felder.zeiten,
        zeitplanWochentage = felder.wochentage,
        zeitplanIntervallStunden = felder.intervallStunden,
        zeitplanFensterVon = felder.fensterVon,
        zeitplanFensterBis = felder.fensterBis,
        zeitplanZielAnzahl = felder.zielAnzahl,
        zeitplanAnstoss = felder.anstoss,
    )
}

fun Faelligkeit.schluessel(): String =
    "$aktivitaetId|$periodeVon|${geplantFuer?.toString() ?: "ZIEL"}"

fun FaelligkeitEntity.zuDomaene(): Faelligkeit = Faelligkeit(
    id = id,
    aktivitaetId = aktivitaetId,
    periodeVon = periodeVon,
    periodeBis = periodeBis,
    geplantFuer = geplantFuer,
    sollAnzahl = sollAnzahl,
    istAnzahl = istAnzahl,
    status = FaelligkeitStatus.valueOf(status),
    erledigtAm = erledigtAm,
    warPuenktlich = warPuenktlich,
)

fun Faelligkeit.zuEntity(): FaelligkeitEntity = FaelligkeitEntity(
    id = id,
    aktivitaetId = aktivitaetId,
    schluessel = schluessel(),
    periodeVon = periodeVon,
    periodeBis = periodeBis,
    geplantFuer = geplantFuer,
    sollAnzahl = sollAnzahl,
    istAnzahl = istAnzahl,
    status = status.name,
    erledigtAm = erledigtAm,
    warPuenktlich = warPuenktlich,
)

fun BuchungEntity.zuDomaene(): Buchung = Buchung(
    id = id,
    art = BuchungsArt.valueOf(art),
    betrag = betrag,
    zeitpunkt = zeitpunkt,
    aktivitaetId = aktivitaetId,
    faelligkeitId = faelligkeitId,
    belohnungId = belohnungId,
    schwelle = schwelle,
    notiz = notiz,
)

fun Buchung.zuEntity(): BuchungEntity = BuchungEntity(
    id = id,
    art = art.name,
    betrag = betrag,
    zeitpunkt = zeitpunkt,
    aktivitaetId = aktivitaetId,
    faelligkeitId = faelligkeitId,
    belohnungId = belohnungId,
    schwelle = schwelle,
    notiz = notiz,
)
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*EntityMapperTest*"`
Expected: `BUILD SUCCESSFUL`, 4 Tests bestanden.

- [ ] **Step 5: Repository-Schnittstellen und Room-Implementierungen anlegen**

`data/repository/Repositories.kt`:

```kotlin
package de.vanhake.dayorganizer.data.repository

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime

interface AktivitaetRepository {
    fun beobachteAktive(): Flow<List<Aktivitaet>>
    suspend fun alleAktiven(): List<Aktivitaet>
    suspend fun nachId(id: Long): Aktivitaet?
    suspend fun speichern(aktivitaet: Aktivitaet): Long
    suspend fun archivieren(id: Long, am: LocalDate)
}

interface FaelligkeitRepository {
    suspend fun anlegenFallsNeu(neue: List<Faelligkeit>)
    suspend fun nachId(id: Long): Faelligkeit?
    suspend fun fuerPeriode(aktivitaetId: Long, periodeVon: LocalDate): List<Faelligkeit>
    suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<Faelligkeit>
    suspend fun historie(aktivitaetId: Long): List<Faelligkeit>
    suspend fun offeneBis(zeitpunkt: LocalDateTime): List<Faelligkeit>
    suspend fun offeneVorTag(tag: LocalDate): List<Faelligkeit>
    suspend fun offeneAbTag(abTag: LocalDate): List<Faelligkeit>
    suspend fun aktualisieren(faelligkeit: Faelligkeit)
    fun beobachteTag(tag: LocalDate): Flow<List<Faelligkeit>>
}

interface BuchungRepository {
    suspend fun buchen(buchungen: List<Buchung>)
    fun beobachteKontostand(): Flow<Int>
    suspend fun kontostand(): Int
    suspend fun serienbonusSchwellenSeit(aktivitaetId: Long, seit: LocalDateTime): Set<Int>
    suspend fun tagesbonusGebuchtAm(tag: LocalDate): Boolean
}
```

`data/repository/RaumRepositories.kt`:

```kotlin
package de.vanhake.dayorganizer.data.repository

import de.vanhake.dayorganizer.data.db.AktivitaetDao
import de.vanhake.dayorganizer.data.db.BuchungDao
import de.vanhake.dayorganizer.data.db.FaelligkeitDao
import de.vanhake.dayorganizer.data.mapper.zuDomaene
import de.vanhake.dayorganizer.data.mapper.zuEntity
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

class RaumAktivitaetRepository @Inject constructor(
    private val dao: AktivitaetDao,
) : AktivitaetRepository {

    override fun beobachteAktive(): Flow<List<Aktivitaet>> =
        dao.beobachteAktive().map { liste -> liste.map { it.zuDomaene() } }

    override suspend fun alleAktiven(): List<Aktivitaet> =
        dao.alleAktiven().map { it.zuDomaene() }

    override suspend fun nachId(id: Long): Aktivitaet? = dao.nachId(id)?.zuDomaene()

    override suspend fun speichern(aktivitaet: Aktivitaet): Long =
        if (aktivitaet.id == 0L) {
            dao.einfuegen(aktivitaet.zuEntity())
        } else {
            dao.aktualisieren(aktivitaet.zuEntity())
            aktivitaet.id
        }

    override suspend fun archivieren(id: Long, am: LocalDate) = dao.archivieren(id, am)
}

class RaumFaelligkeitRepository @Inject constructor(
    private val dao: FaelligkeitDao,
) : FaelligkeitRepository {

    override suspend fun anlegenFallsNeu(neue: List<Faelligkeit>) =
        dao.anlegenFallsNeu(neue.map { it.zuEntity() })

    override suspend fun nachId(id: Long): Faelligkeit? = dao.nachId(id)?.zuDomaene()

    override suspend fun fuerPeriode(aktivitaetId: Long, periodeVon: LocalDate): List<Faelligkeit> =
        dao.fuerPeriode(aktivitaetId, periodeVon).map { it.zuDomaene() }

    override suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<Faelligkeit> =
        dao.fuerZeitraum(von, bis).map { it.zuDomaene() }

    override suspend fun historie(aktivitaetId: Long): List<Faelligkeit> =
        dao.historie(aktivitaetId).map { it.zuDomaene() }

    override suspend fun offeneBis(zeitpunkt: LocalDateTime): List<Faelligkeit> =
        dao.offeneBis(zeitpunkt).map { it.zuDomaene() }

    override suspend fun offeneVorTag(tag: LocalDate): List<Faelligkeit> =
        dao.offeneVorTag(tag).map { it.zuDomaene() }

    override suspend fun offeneAbTag(abTag: LocalDate): List<Faelligkeit> =
        dao.offeneAbTag(abTag).map { it.zuDomaene() }

    override suspend fun aktualisieren(faelligkeit: Faelligkeit) =
        dao.aktualisieren(faelligkeit.zuEntity())

    override fun beobachteTag(tag: LocalDate): Flow<List<Faelligkeit>> =
        dao.beobachteTag(tag).map { liste -> liste.map { it.zuDomaene() } }
}

class RaumBuchungRepository @Inject constructor(
    private val dao: BuchungDao,
) : BuchungRepository {

    override suspend fun buchen(buchungen: List<Buchung>) =
        dao.einfuegen(buchungen.map { it.zuEntity() })

    override fun beobachteKontostand(): Flow<Int> = dao.beobachteKontostand()

    override suspend fun kontostand(): Int = dao.kontostandJetzt()

    override suspend fun serienbonusSchwellenSeit(aktivitaetId: Long, seit: LocalDateTime): Set<Int> =
        dao.serienbonusSchwellenSeit(aktivitaetId, seit).toSet()

    override suspend fun tagesbonusGebuchtAm(tag: LocalDate): Boolean =
        dao.anzahlTagesboni(tag.atStartOfDay(), tag.plusDays(1).atStartOfDay()) > 0
}
```

- [ ] **Step 6: Einstellungen und Hilt-Modul anlegen**

`data/EinstellungenSpeicher.kt`:

```kotlin
package de.vanhake.dayorganizer.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class Einstellungen(
    val tagesbonusPunkte: Int = 15,
    val standardKulanzMinuten: Int = 60,
)

private val Context.datenSpeicher by preferencesDataStore(name = "einstellungen")

@Singleton
class EinstellungenSpeicher @Inject constructor(
    @ApplicationContext private val kontext: Context,
) {
    private val tagesbonus = intPreferencesKey("tagesbonus_punkte")
    private val kulanz = intPreferencesKey("standard_kulanz_minuten")

    fun beobachten(): Flow<Einstellungen> = kontext.datenSpeicher.data.map {
        Einstellungen(
            tagesbonusPunkte = it[tagesbonus] ?: 15,
            standardKulanzMinuten = it[kulanz] ?: 60,
        )
    }

    suspend fun lesen(): Einstellungen = beobachten().first()

    suspend fun schreiben(einstellungen: Einstellungen) {
        kontext.datenSpeicher.edit {
            it[tagesbonus] = einstellungen.tagesbonusPunkte
            it[kulanz] = einstellungen.standardKulanzMinuten
        }
    }
}
```

`di/DatenModul.kt`:

```kotlin
package de.vanhake.dayorganizer.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.vanhake.dayorganizer.data.db.AktivitaetDao
import de.vanhake.dayorganizer.data.db.BuchungDao
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import de.vanhake.dayorganizer.data.db.FaelligkeitDao
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.data.repository.RaumAktivitaetRepository
import de.vanhake.dayorganizer.data.repository.RaumBuchungRepository
import de.vanhake.dayorganizer.data.repository.RaumFaelligkeitRepository
import de.vanhake.dayorganizer.domain.rechner.PunkteRechner
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatenModul {

    @Provides
    @Singleton
    fun datenbank(@ApplicationContext kontext: Context): DayOrganizerDatenbank =
        Room.databaseBuilder(kontext, DayOrganizerDatenbank::class.java, "dayorganizer.db").build()

    @Provides fun aktivitaetDao(db: DayOrganizerDatenbank): AktivitaetDao = db.aktivitaetDao()
    @Provides fun faelligkeitDao(db: DayOrganizerDatenbank): FaelligkeitDao = db.faelligkeitDao()
    @Provides fun buchungDao(db: DayOrganizerDatenbank): BuchungDao = db.buchungDao()

    @Provides @Singleton fun uhr(): Clock = Clock.systemDefaultZone()
    @Provides @Singleton fun zeitplanRechner(): ZeitplanRechner = ZeitplanRechner()
    @Provides @Singleton fun punkteRechner(): PunkteRechner = PunkteRechner()
    @Provides @Singleton fun serienRechner(): SerienRechner = SerienRechner()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModul {
    @Binds abstract fun aktivitaeten(impl: RaumAktivitaetRepository): AktivitaetRepository
    @Binds abstract fun faelligkeiten(impl: RaumFaelligkeitRepository): FaelligkeitRepository
    @Binds abstract fun buchungen(impl: RaumBuchungRepository): BuchungRepository
}
```

- [ ] **Step 7: Build und alle Tests laufen lassen**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :domain:test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/
git commit -m "feat(data): Repositories, Mapper, Einstellungen und Hilt-Verdrahtung"
```

---

### Task 11: Fälligkeiten materialisieren

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitenMaterialisieren.kt`
- Create: `app/src/test/kotlin/de/vanhake/dayorganizer/Fakes.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitenMaterialisierenTest.kt`

**Interfaces:**
- Consumes: `AktivitaetRepository`, `FaelligkeitRepository` aus Task 10, `ZeitplanRechner` aus Task 2
- Produces:
  - `FaelligkeitenMaterialisieren.invoke(vorausTage: Long = 7)` — legt für alle aktiven Aktivitäten die Fälligkeiten von heute bis `heute + vorausTage` an, doppelte werden durch den eindeutigen Schlüssel geschluckt
  - Testdoubles `FakeAktivitaetRepository`, `FakeFaelligkeitRepository`, `FakeBuchungRepository` für alle weiteren Use-Case-Tests

- [ ] **Step 1: Testdoubles anlegen**

`app/src/test/kotlin/de/vanhake/dayorganizer/Fakes.kt`:

```kotlin
package de.vanhake.dayorganizer

import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime

class FakeAktivitaetRepository(start: List<Aktivitaet> = emptyList()) : AktivitaetRepository {
    private val stand = MutableStateFlow(start)
    private var naechsteId = (start.maxOfOrNull { it.id } ?: 0L) + 1

    override fun beobachteAktive(): Flow<List<Aktivitaet>> =
        stand.map { liste -> liste.filter { it.aktiv && it.archiviertAm == null } }

    override suspend fun alleAktiven(): List<Aktivitaet> =
        stand.value.filter { it.aktiv && it.archiviertAm == null }

    override suspend fun nachId(id: Long): Aktivitaet? = stand.value.find { it.id == id }

    override suspend fun speichern(aktivitaet: Aktivitaet): Long {
        val id = if (aktivitaet.id == 0L) naechsteId++ else aktivitaet.id
        stand.value = stand.value.filterNot { it.id == id } + aktivitaet.copy(id = id)
        return id
    }

    override suspend fun archivieren(id: Long, am: LocalDate) {
        stand.value = stand.value.map {
            if (it.id == id) it.copy(aktiv = false, archiviertAm = am) else it
        }
    }
}

class FakeFaelligkeitRepository(start: List<Faelligkeit> = emptyList()) : FaelligkeitRepository {
    val stand = MutableStateFlow(start)
    private var naechsteId = (start.maxOfOrNull { it.id } ?: 0L) + 1

    private fun schluessel(f: Faelligkeit) = "${f.aktivitaetId}|${f.periodeVon}|${f.geplantFuer ?: "ZIEL"}"

    override suspend fun anlegenFallsNeu(neue: List<Faelligkeit>) {
        val vorhanden = stand.value.map(::schluessel).toMutableSet()
        val ergaenzt = stand.value.toMutableList()
        neue.forEach { f ->
            if (vorhanden.add(schluessel(f))) ergaenzt += f.copy(id = naechsteId++)
        }
        stand.value = ergaenzt
    }

    override suspend fun nachId(id: Long): Faelligkeit? = stand.value.find { it.id == id }

    override suspend fun fuerPeriode(aktivitaetId: Long, periodeVon: LocalDate): List<Faelligkeit> =
        stand.value.filter { it.aktivitaetId == aktivitaetId && it.periodeVon == periodeVon }

    override suspend fun fuerZeitraum(von: LocalDate, bis: LocalDate): List<Faelligkeit> =
        stand.value.filter { !it.periodeBis.isBefore(von) && !it.periodeVon.isAfter(bis) }

    override suspend fun historie(aktivitaetId: Long): List<Faelligkeit> =
        stand.value.filter { it.aktivitaetId == aktivitaetId }.sortedBy { it.periodeVon }

    override suspend fun offeneBis(zeitpunkt: LocalDateTime): List<Faelligkeit> =
        stand.value.filter {
            it.status == FaelligkeitStatus.OFFEN &&
                it.geplantFuer != null && it.geplantFuer!!.isBefore(zeitpunkt)
        }

    override suspend fun offeneVorTag(tag: LocalDate): List<Faelligkeit> =
        stand.value.filter { it.status == FaelligkeitStatus.OFFEN && it.periodeBis.isBefore(tag) }

    override suspend fun offeneAbTag(abTag: LocalDate): List<Faelligkeit> =
        stand.value.filter { it.status == FaelligkeitStatus.OFFEN && !it.periodeBis.isBefore(abTag) }

    override suspend fun aktualisieren(faelligkeit: Faelligkeit) {
        stand.value = stand.value.map { if (it.id == faelligkeit.id) faelligkeit else it }
    }

    override fun beobachteTag(tag: LocalDate): Flow<List<Faelligkeit>> =
        stand.map { liste ->
            liste.filter { !it.periodeBis.isBefore(tag) && !it.periodeVon.isAfter(tag) }
        }
}

class FakeBuchungRepository(start: List<Buchung> = emptyList()) : BuchungRepository {
    val stand = MutableStateFlow(start)

    override suspend fun buchen(buchungen: List<Buchung>) {
        stand.value = stand.value + buchungen
    }

    override fun beobachteKontostand(): Flow<Int> = stand.map { liste -> liste.sumOf { it.betrag } }

    override suspend fun kontostand(): Int = stand.value.sumOf { it.betrag }

    override suspend fun serienbonusSchwellenSeit(aktivitaetId: Long, seit: LocalDateTime): Set<Int> =
        stand.value
            .filter {
                it.art == BuchungsArt.SERIENBONUS &&
                    it.aktivitaetId == aktivitaetId &&
                    !it.zeitpunkt.isBefore(seit)
            }
            .mapNotNull { it.schwelle }
            .toSet()

    override suspend fun tagesbonusGebuchtAm(tag: LocalDate): Boolean =
        stand.value.any {
            it.art == BuchungsArt.TAGESBONUS && it.zeitpunkt.toLocalDate() == tag
        }
}
```

- [ ] **Step 2: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class FaelligkeitenMaterialisierenTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 6, 0).atZone(zone).toInstant(),
        zone,
    )

    private val tabletten = Aktivitaet(
        id = 1,
        name = "Tabletten",
        farbe = 1,
        basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(
            zeiten = listOf(java.time.LocalTime.of(8, 0)),
            wochentage = DayOfWeek.entries.toSet(),
        ),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `legt Faelligkeiten fuer den Vorausblick an`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            faelligkeiten = faelligkeiten,
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 6)

        assertEquals(7, faelligkeiten.stand.value.size)
        assertEquals(LocalDate.of(2026, 3, 2), faelligkeiten.stand.value.first().periodeVon)
        assertEquals(LocalDate.of(2026, 3, 8), faelligkeiten.stand.value.last().periodeVon)
    }

    @Test
    fun `ein zweiter Lauf legt nichts doppelt an`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            faelligkeiten = faelligkeiten,
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 6)
        use(vorausTage = 6)

        assertEquals(7, faelligkeiten.stand.value.size)
    }

    @Test
    fun `archivierte Aktivitaeten erzeugen keine Faelligkeiten`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository()
        val aktivitaeten = FakeAktivitaetRepository(
            listOf(tabletten.copy(aktiv = false, archiviertAm = LocalDate.of(2026, 2, 1))),
        )
        val use = FaelligkeitenMaterialisieren(aktivitaeten, faelligkeiten, ZeitplanRechner(), uhr)

        use(vorausTage = 6)

        assertEquals(0, faelligkeiten.stand.value.size)
    }
}
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*FaelligkeitenMaterialisierenTest*"`
Expected: Kompilierfehler `Unresolved reference: FaelligkeitenMaterialisieren`.

- [ ] **Step 4: Implementieren**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Legt die Faelligkeiten des Vorausblicks an. Mehrfachaufrufe sind unschaedlich:
 * der eindeutige Schluessel je Faelligkeit schluckt Wiederholungen.
 */
class FaelligkeitenMaterialisieren @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val zeitplanRechner: ZeitplanRechner,
    private val uhr: Clock,
) {
    suspend operator fun invoke(vorausTage: Long = 7) {
        val heute = LocalDate.now(uhr)
        val neue = aktivitaeten.alleAktiven().flatMap { aktivitaet ->
            zeitplanRechner.faelligkeitenFuer(aktivitaet, heute, heute.plusDays(vorausTage))
        }
        faelligkeiten.anlegenFallsNeu(neue)
    }
}
```

- [ ] **Step 5: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*FaelligkeitenMaterialisierenTest*"`
Expected: `BUILD SUCCESSFUL`, 3 Tests bestanden.

- [ ] **Step 6: Commit**

```bash
git add app/
git commit -m "feat(domainuse): Faelligkeiten fuer den Vorausblick materialisieren"
```

---

### Task 12: Erledigen, Auslassen und Verpassen

Der wichtigste Use Case der App. Er ist idempotent, damit Widget, Benachrichtigung und Oberfläche nicht doppelt buchen können.

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/repository/Transaktionen.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitErledigen.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/di/DatenModul.kt` — `Transaktionen` binden
- Modify: `app/src/test/kotlin/de/vanhake/dayorganizer/Fakes.kt` — `FakeTransaktionen`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitAuslassen.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/VerpassteAbschliessen.kt`
- Modify: `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/rechner/PunkteRechner.kt` — `istPuenktlich` von `private` auf öffentlich heben
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitErledigenTest.kt`

**Interfaces:**
- Consumes: alle Repositories aus Task 10, `PunkteRechner`, `SerienRechner`, `ErledigungsKontext` aus Tasks 6 und 7, `EinstellungenSpeicher` aus Task 10
- Produces:
  - `interface Transaktionen { suspend fun <T> inTransaktion(block: suspend () -> T): T }` mit `RaumTransaktionen` dahinter
  - `enum ErledigungsErgebnis { GEBUCHT, NICHT_OFFEN, UNBEKANNT }`
  - `FaelligkeitErledigen.invoke(faelligkeitId: Long): ErledigungsErgebnis`
  - `FaelligkeitAuslassen.invoke(faelligkeitId: Long)`
  - `VerpassteAbschliessen.invoke()` — setzt alle offenen Fälligkeiten, deren Periode vor heute endet, auf `VERPASST`

- [ ] **Step 1: Transaktionsgrenze anlegen**

Das Design verspricht: *"Das Abhaken läuft in einer Transaktion und ist
idempotent: eine Fälligkeit kann nur einmal gebucht werden, auch wenn Widget,
Benachrichtigung und App gleichzeitig zugreifen."* Ohne Transaktion ist die
Statusprüfung der einzige Schutz — und zwei gleichzeitige Aufrufer lesen beide
`OFFEN`, bevor einer schreibt. Beide buchen.

Der Use Case arbeitet bewusst gegen schmale Schnittstellen statt gegen Room,
damit er ohne Emulator testbar bleibt. Die Transaktionsgrenze bekommt deshalb
ebenfalls eine Schnittstelle.

`data/repository/Transaktionen.kt`:

```kotlin
package de.vanhake.dayorganizer.data.repository

import androidx.room.withTransaction
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import javax.inject.Inject

/**
 * Klammert mehrere Schreibvorgaenge zu einer Einheit. Ohne sie waere die
 * Statuspruefung im Erledigen-Use-Case die einzige Absicherung gegen
 * Doppelbuchungen -- und die greift nicht, wenn zwei Aufrufer gleichzeitig
 * lesen, bevor einer schreibt.
 */
interface Transaktionen {
    suspend fun <T> inTransaktion(block: suspend () -> T): T
}

class RaumTransaktionen @Inject constructor(
    private val db: DayOrganizerDatenbank,
) : Transaktionen {
    override suspend fun <T> inTransaktion(block: suspend () -> T): T =
        db.withTransaction(block)
}
```

In `RepositoryModul` binden:

```kotlin
    @Binds abstract fun transaktionen(impl: RaumTransaktionen): Transaktionen
```

In `Fakes.kt` ergänzen:

```kotlin
/** Fuehrt den Block direkt aus. Die Fakes sind ohnehin single-threaded. */
class FakeTransaktionen : Transaktionen {
    override suspend fun <T> inTransaktion(block: suspend () -> T): T = block()
}
```

- [ ] **Step 2: `istPuenktlich` öffentlich machen**

In `PunkteRechner.kt` die Signatur ändern, damit der Use Case dieselbe Regel benutzt statt sie zu wiederholen:

```kotlin
    fun istPuenktlich(kontext: ErledigungsKontext): Boolean {
        val soll = kontext.faelligkeitVorher.geplantFuer ?: return false
        val abweichung = abs(Duration.between(soll, kontext.zeitpunkt).toMinutes())
        return abweichung <= kontext.aktivitaet.kulanzMinuten
    }
```

- [ ] **Step 3: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.PunkteRechner
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class FaelligkeitErledigenTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private fun uhrUm(stunde: Int, minute: Int) = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, stunde, minute).atZone(zone).toInstant(),
        zone,
    )

    private val tabletten = Aktivitaet(
        id = 1,
        name = "Tabletten",
        farbe = 1,
        basisPunkte = 10,
        kulanzMinuten = 60,
        puenktlichkeitsBonus = 5,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private val morgens = Faelligkeit(
        id = 10,
        aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 2),
        periodeBis = LocalDate.of(2026, 3, 2),
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0),
        sollAnzahl = 1,
    )

    private fun useCase(
        uhr: Clock,
        aktivitaeten: FakeAktivitaetRepository,
        faelligkeiten: FakeFaelligkeitRepository,
        buchungen: FakeBuchungRepository,
        tagesbonus: Int = 0,
    ) = FaelligkeitErledigen(
        aktivitaeten = aktivitaeten,
        faelligkeiten = faelligkeiten,
        buchungen = buchungen,
        punkteRechner = PunkteRechner(),
        serienRechner = SerienRechner(),
        einstellungenLesen = { Einstellungen(tagesbonusPunkte = tagesbonus) },
        uhr = uhr,
    )

    @Test
    fun `puenktliches Erledigen bucht Basis und Bonus und schliesst die Faelligkeit`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b)

        assertEquals(ErledigungsErgebnis.GEBUCHT, use(10))

        assertEquals(15, b.stand.value.sumOf { it.betrag })
        assertEquals(FaelligkeitStatus.ERLEDIGT, f.stand.value.single().status)
        assertEquals(true, f.stand.value.single().warPuenktlich)
    }

    @Test
    fun `ein zweiter Aufruf bucht nicht erneut`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), f, b)

        use(10)
        assertEquals(ErledigungsErgebnis.NICHT_OFFEN, use(10))

        assertEquals(15, b.stand.value.sumOf { it.betrag })
    }

    @Test
    fun `ein Zaehlziel bleibt offen bis die Sollanzahl erreicht ist`() = runTest {
        val sport = tabletten.copy(
            id = 2,
            name = "Sport",
            basisPunkte = 20,
            puenktlichkeitsBonus = 0,
            zeitplan = Zeitplan.Wochenziel(anzahl = 3),
        )
        val ziel = Faelligkeit(
            id = 20,
            aktivitaetId = 2,
            periodeVon = LocalDate.of(2026, 3, 2),
            periodeBis = LocalDate.of(2026, 3, 8),
            geplantFuer = null,
            sollAnzahl = 3,
        )
        val f = FakeFaelligkeitRepository(listOf(ziel))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(18, 0), FakeAktivitaetRepository(listOf(sport)), f, b)

        use(20)
        use(20)

        assertEquals(2, f.stand.value.single().istAnzahl)
        assertEquals(FaelligkeitStatus.OFFEN, f.stand.value.single().status)
        assertEquals(40, b.stand.value.sumOf { it.betrag })

        use(20)

        assertEquals(FaelligkeitStatus.ERLEDIGT, f.stand.value.single().status)
        assertEquals(60, b.stand.value.sumOf { it.betrag })
    }

    @Test
    fun `der Serienbonus wird bei zwei Erledigungen am selben Tag nur einmal gebucht`() = runTest {
        val zweimal = tabletten.copy(
            puenktlichkeitsBonus = 0,
            serienSchwellen = listOf(de.vanhake.dayorganizer.domain.model.SerienSchwelle(1, 100)),
            zeitplan = Zeitplan.FesteZeiten(
                listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)),
                DayOfWeek.entries.toSet(),
            ),
        )
        val abends = morgens.copy(id = 11, geplantFuer = LocalDateTime.of(2026, 3, 2, 20, 0))
        val f = FakeFaelligkeitRepository(listOf(morgens, abends))
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(20, 30), FakeAktivitaetRepository(listOf(zweimal)), f, b)

        use(10)
        use(11)

        assertEquals(1, b.stand.value.count { it.art == BuchungsArt.SERIENBONUS })
        assertEquals(120, b.stand.value.sumOf { it.betrag })
    }

    @Test
    fun `der Tagesbonus kommt erst mit der letzten Faelligkeit des Tages`() = runTest {
        val abends = morgens.copy(id = 11, geplantFuer = LocalDateTime.of(2026, 3, 2, 20, 0))
        val f = FakeFaelligkeitRepository(listOf(morgens, abends))
        val b = FakeBuchungRepository()
        val use = useCase(
            uhrUm(20, 30), FakeAktivitaetRepository(listOf(tabletten)), f, b, tagesbonus = 25,
        )

        use(10)
        assertEquals(0, b.stand.value.count { it.art == BuchungsArt.TAGESBONUS })

        use(11)
        assertEquals(1, b.stand.value.count { it.art == BuchungsArt.TAGESBONUS })
    }

    @Test
    fun `eine unbekannte Faelligkeit wird gemeldet statt zu buchen`() = runTest {
        val b = FakeBuchungRepository()
        val use = useCase(uhrUm(8, 10), FakeAktivitaetRepository(listOf(tabletten)), FakeFaelligkeitRepository(), b)

        assertEquals(ErledigungsErgebnis.UNBEKANNT, use(999))
        assertEquals(0, b.stand.value.size)
    }

    @Test
    fun `Auslassen bucht nichts und markiert die Faelligkeit`() = runTest {
        val f = FakeFaelligkeitRepository(listOf(morgens))
        val b = FakeBuchungRepository()
        val use = FaelligkeitAuslassen(f, uhrUm(9, 0))

        use(10)

        assertEquals(FaelligkeitStatus.AUSGELASSEN, f.stand.value.single().status)
        assertEquals(0, b.stand.value.size)
    }

    @Test
    fun `VerpassteAbschliessen markiert nur abgelaufene offene Faelligkeiten`() = runTest {
        val gestern = morgens.copy(
            id = 9,
            periodeVon = LocalDate.of(2026, 3, 1),
            periodeBis = LocalDate.of(2026, 3, 1),
            geplantFuer = LocalDateTime.of(2026, 3, 1, 8, 0),
        )
        val f = FakeFaelligkeitRepository(listOf(gestern, morgens))
        val use = VerpassteAbschliessen(f, uhrUm(3, 0))

        use()

        assertEquals(FaelligkeitStatus.VERPASST, f.stand.value.first { it.id == 9L }.status)
        assertEquals(FaelligkeitStatus.OFFEN, f.stand.value.first { it.id == 10L }.status)
    }
}
```

- [ ] **Step 4: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*FaelligkeitErledigenTest*"`
Expected: Kompilierfehler `Unresolved reference: FaelligkeitErledigen`.

- [ ] **Step 5: Implementieren**

`domainuse/FaelligkeitErledigen.kt`:

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.rechner.ErledigungsKontext
import de.vanhake.dayorganizer.domain.rechner.PunkteRechner
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * NICHT_OFFEN deckt alles ab, was nicht mehr angefasst werden darf: bereits
 * erledigt, bewusst ausgelassen, oder vom Waechter als verpasst geschlossen.
 * Eine Sperre nur auf ERLEDIGT wuerde ein ausgelassenes Mittagessen beim
 * naechsten Widget-Tipp nachtraeglich bezahlen.
 */
enum class ErledigungsErgebnis { GEBUCHT, NICHT_OFFEN, UNBEKANNT }

/**
 * Der primaere Konstruktor nimmt die Einstellungen als Funktion entgegen, damit
 * der Test ohne DataStore auskommt. Dagger benutzt den sekundaeren Konstruktor;
 * genau einer darf @Inject tragen.
 */
class FaelligkeitErledigen(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val punkteRechner: PunkteRechner,
    private val serienRechner: SerienRechner,
    private val transaktionen: Transaktionen,
    private val einstellungenLesen: suspend () -> Einstellungen,
    private val uhr: Clock,
) {

    @Inject
    constructor(
        aktivitaeten: AktivitaetRepository,
        faelligkeiten: FaelligkeitRepository,
        buchungen: BuchungRepository,
        punkteRechner: PunkteRechner,
        serienRechner: SerienRechner,
        transaktionen: Transaktionen,
        speicher: EinstellungenSpeicher,
        uhr: Clock,
    ) : this(
        aktivitaeten, faelligkeiten, buchungen, punkteRechner, serienRechner,
        transaktionen, { speicher.lesen() }, uhr,
    )

    // Lesen, Pruefen, Schreiben und Buchen liegen zusammen in der Transaktion.
    // Nur so trennt die Statuspruefung zwei gleichzeitige Aufrufer.
    suspend operator fun invoke(faelligkeitId: Long): ErledigungsErgebnis =
        transaktionen.inTransaktion { erledige(faelligkeitId) }

    private suspend fun erledige(faelligkeitId: Long): ErledigungsErgebnis {
        val vorher = faelligkeiten.nachId(faelligkeitId) ?: return ErledigungsErgebnis.UNBEKANNT
        if (vorher.status != FaelligkeitStatus.OFFEN) return ErledigungsErgebnis.NICHT_OFFEN
        val aktivitaet = aktivitaeten.nachId(vorher.aktivitaetId)
            ?: return ErledigungsErgebnis.UNBEKANNT

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

        faelligkeiten.aktualisieren(
            nachher.copy(warPuenktlich = punkteRechner.istPuenktlich(kontext)),
        )
        buchungen.buchen(punkteRechner.buchungen(kontext))
        return ErledigungsErgebnis.GEBUCHT
    }
}
```

`domainuse/FaelligkeitAuslassen.kt`:

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
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
        val f = faelligkeiten.nachId(faelligkeitId) ?: return
        if (f.status != FaelligkeitStatus.OFFEN) return
        faelligkeiten.aktualisieren(
            f.copy(status = FaelligkeitStatus.AUSGELASSEN, erledigtAm = LocalDateTime.now(uhr)),
        )
    }
}
```

`domainuse/VerpassteAbschliessen.kt`:

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Schliesst alle offenen Faelligkeiten, deren Periode vor heute endet. */
class VerpassteAbschliessen @Inject constructor(
    private val faelligkeiten: FaelligkeitRepository,
    private val uhr: Clock,
) {
    suspend operator fun invoke() {
        val heute = LocalDate.now(uhr)
        faelligkeiten.offeneVorTag(heute).forEach {
            faelligkeiten.aktualisieren(it.copy(status = FaelligkeitStatus.VERPASST))
        }
    }
}
```

- [ ] **Step 6: Test laufen lassen und grünen Lauf bestätigen**

Drei Tests kommen zu den acht oben dazu, weil sie genau die Zusage prüfen, an
der der Plan zuerst vorbeigeschrieben hat:

- `eine ausgelassene Faelligkeit wird nicht nachtraeglich bezahlt` — auslassen,
  dann erledigen. Erwartet: `NICHT_OFFEN`, Status bleibt `AUSGELASSEN`, keine
  Buchung.
- `eine verpasste Faelligkeit wird nicht nachtraeglich bezahlt` — Status
  `VERPASST`, dann erledigen. Erwartet: `NICHT_OFFEN`, Status bleibt, keine
  Buchung.
- `alles laeuft in einer Transaktion` — ein `FakeTransaktionen`, das mitzählt,
  belegt, dass `invoke` seinen Rumpf genau einmal in `inTransaktion` ausführt.

Run: `./gradlew :app:testDebugUnitTest --tests "*FaelligkeitErledigenTest*"`
Expected: `BUILD SUCCESSFUL`, 11 Tests bestanden.

- [ ] **Step 7: Commit**

```bash
git add app/ domain/
git commit -m "feat(domainuse): Erledigen, Auslassen und Verpassen mit Punktebuchung"
```

---

### Task 13: Benachrichtigungen

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/notification/Benachrichtigungsaktionen.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/notification/Kanaele.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/notification/Erinnerungsanzeige.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/notification/ErinnerungsNotifier.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/DayOrganizerApp.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/platform/notification/BenachrichtigungsaktionenTest.kt`

**Interfaces:**
- Consumes: nichts aus vorherigen Tasks
- Produces:
  - `sealed interface Benachrichtigungsaktion` mit `Erledigt(faelligkeitId)`, `Spaeter(faelligkeitId)`, `Ausgelassen(faelligkeitId)`
  - `Benachrichtigungsaktionen.ACTION_ERLEDIGT`, `ACTION_SPAETER`, `ACTION_AUSGELASSEN`, `EXTRA_FAELLIGKEIT_ID`
  - `Benachrichtigungsaktionen.aus(action: String?, faelligkeitId: Long): Benachrichtigungsaktion?` — `null` bei unbekannter Aktion oder `faelligkeitId <= 0`
  - `Kanaele.KANAL_ERINNERUNGEN`, `Kanaele.anlegen(kontext: Context)`
  - `interface Erinnerungsanzeige { fun zeige(faelligkeit: Faelligkeit, aktivitaet: Aktivitaet); fun verstecke(faelligkeitId: Long) }` — die Textgestaltung liegt in der Android-Implementierung, damit der Use Case in Task 15 ohne Ressourcen prüfbar bleibt
  - `ErinnerungsNotifier : Erinnerungsanzeige`, in `RepositoryModul` gebunden

Die Zuordnung von Intent zu Aktion ist als reine Funktion herausgezogen, damit sie ohne Emulator geprüft werden kann. Der BroadcastReceiver aus Task 15 reicht nur `intent.action` und das Extra hinein.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.platform.notification

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BenachrichtigungsaktionenTest {

    @Test
    fun `bekannte Aktionen werden zugeordnet`() {
        assertEquals(
            Benachrichtigungsaktion.Erledigt(7),
            Benachrichtigungsaktionen.aus(Benachrichtigungsaktionen.ACTION_ERLEDIGT, 7),
        )
        assertEquals(
            Benachrichtigungsaktion.Spaeter(7),
            Benachrichtigungsaktionen.aus(Benachrichtigungsaktionen.ACTION_SPAETER, 7),
        )
        assertEquals(
            Benachrichtigungsaktion.Ausgelassen(7),
            Benachrichtigungsaktionen.aus(Benachrichtigungsaktionen.ACTION_AUSGELASSEN, 7),
        )
    }

    @Test
    fun `unbekannte Aktion ergibt nichts`() {
        assertNull(Benachrichtigungsaktionen.aus("de.example.UNSINN", 7))
        assertNull(Benachrichtigungsaktionen.aus(null, 7))
    }

    @Test
    fun `fehlende Faelligkeit ergibt nichts`() {
        assertNull(Benachrichtigungsaktionen.aus(Benachrichtigungsaktionen.ACTION_ERLEDIGT, 0))
        assertNull(Benachrichtigungsaktionen.aus(Benachrichtigungsaktionen.ACTION_ERLEDIGT, -1))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BenachrichtigungsaktionenTest*"`
Expected: Kompilierfehler `Unresolved reference: Benachrichtigungsaktionen`.

- [ ] **Step 3: Aktionen implementieren**

```kotlin
package de.vanhake.dayorganizer.platform.notification

sealed interface Benachrichtigungsaktion {
    val faelligkeitId: Long

    data class Erledigt(override val faelligkeitId: Long) : Benachrichtigungsaktion
    data class Spaeter(override val faelligkeitId: Long) : Benachrichtigungsaktion
    data class Ausgelassen(override val faelligkeitId: Long) : Benachrichtigungsaktion
}

object Benachrichtigungsaktionen {
    const val ACTION_ERLEDIGT = "de.vanhake.dayorganizer.ERLEDIGT"
    const val ACTION_SPAETER = "de.vanhake.dayorganizer.SPAETER"
    const val ACTION_AUSGELASSEN = "de.vanhake.dayorganizer.AUSGELASSEN"
    const val EXTRA_FAELLIGKEIT_ID = "faelligkeitId"

    fun aus(action: String?, faelligkeitId: Long): Benachrichtigungsaktion? {
        if (faelligkeitId <= 0) return null
        return when (action) {
            ACTION_ERLEDIGT -> Benachrichtigungsaktion.Erledigt(faelligkeitId)
            ACTION_SPAETER -> Benachrichtigungsaktion.Spaeter(faelligkeitId)
            ACTION_AUSGELASSEN -> Benachrichtigungsaktion.Ausgelassen(faelligkeitId)
            else -> null
        }
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BenachrichtigungsaktionenTest*"`
Expected: `BUILD SUCCESSFUL`, 3 Tests bestanden.

- [ ] **Step 5: Kanal und Notifier anlegen**

`platform/notification/Kanaele.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService
import de.vanhake.dayorganizer.R

object Kanaele {
    const val KANAL_ERINNERUNGEN = "erinnerungen"

    fun anlegen(kontext: Context) {
        val kanal = NotificationChannel(
            KANAL_ERINNERUNGEN,
            kontext.getString(R.string.kanal_erinnerungen_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = kontext.getString(R.string.kanal_erinnerungen_beschreibung)
            enableVibration(true)
        }
        kontext.getSystemService<NotificationManager>()?.createNotificationChannel(kanal)
    }
}
```

`platform/notification/Erinnerungsanzeige.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.notification

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit

/**
 * Trennt das Anzeigen einer Erinnerung von der Entscheidung, wann erinnert
 * wird. Die Textgestaltung gehoert in die Android-Implementierung, damit der
 * Use Case in Task 15 ohne Ressourcen und ohne Emulator pruefbar bleibt.
 */
interface Erinnerungsanzeige {
    fun zeige(faelligkeit: Faelligkeit, aktivitaet: Aktivitaet)
    fun verstecke(faelligkeitId: Long)
}
```

`platform/notification/ErinnerungsNotifier.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.vanhake.dayorganizer.MainActivity
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ErinnerungsNotifier @Inject constructor(
    @ApplicationContext private val kontext: Context,
) : Erinnerungsanzeige {

    override fun zeige(faelligkeit: Faelligkeit, aktivitaet: Aktivitaet) {
        val faelligkeitId = faelligkeit.id
        val text = if (faelligkeit.sollAnzahl > 1) {
            kontext.getString(
                R.string.erinnerung_noch_offen,
                faelligkeit.sollAnzahl - faelligkeit.istAnzahl,
                faelligkeit.sollAnzahl,
            )
        } else {
            kontext.getString(R.string.erinnerung_jetzt_faellig)
        }

        val benachrichtigung = NotificationCompat.Builder(kontext, Kanaele.KANAL_ERINNERUNGEN)
            .setSmallIcon(R.drawable.ic_erinnerung)
            .setContentTitle(aktivitaet.name)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(appOeffnen())
            .addAction(
                0,
                kontext.getString(R.string.aktion_erledigt),
                aktion(Benachrichtigungsaktionen.ACTION_ERLEDIGT, faelligkeitId),
            )
            .addAction(
                0,
                kontext.getString(R.string.aktion_spaeter),
                aktion(Benachrichtigungsaktionen.ACTION_SPAETER, faelligkeitId),
            )
            .addAction(
                0,
                kontext.getString(R.string.aktion_ausgelassen),
                aktion(Benachrichtigungsaktionen.ACTION_AUSGELASSEN, faelligkeitId),
            )
            .build()

        runCatching {
            NotificationManagerCompat.from(kontext).notify(faelligkeitId.toInt(), benachrichtigung)
        }
        // Fehlt POST_NOTIFICATIONS, wirft notify eine SecurityException. Der Banner
        // auf dem Heute-Screen aus Task 17 weist den Nutzer darauf hin; hier darf
        // die Erinnerungskette deswegen nicht abbrechen.
    }

    override fun verstecke(faelligkeitId: Long) {
        NotificationManagerCompat.from(kontext).cancel(faelligkeitId.toInt())
    }

    private fun aktion(action: String, faelligkeitId: Long): PendingIntent {
        val intent = Intent(action).apply {
            setPackage(kontext.packageName)
            putExtra(Benachrichtigungsaktionen.EXTRA_FAELLIGKEIT_ID, faelligkeitId)
        }
        return PendingIntent.getBroadcast(
            kontext,
            (action.hashCode() * 31 + faelligkeitId.toInt()),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun appOeffnen(): PendingIntent = PendingIntent.getActivity(
        kontext,
        0,
        Intent(kontext, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}
```

- [ ] **Step 6: Icon, Texte und Kanalanlage ergänzen**

`app/src/main/res/drawable/ic_erinnerung.xml` — der Tint verweist auf das
Framework-Attribut, nicht auf `?attr/...`: das Projekt benutzt kein AppCompat,
und AAPT bricht sonst mit `resource attr/colorControlNormal not found` ab.

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24"
    android:tint="?android:attr/colorControlNormal">
    <path android:fillColor="@android:color/white"
        android:pathData="M12,22c1.1,0 2,-0.9 2,-2h-4C10,21.1 10.9,22 12,22zM18,16v-5c0,-3.07 -1.64,-5.64 -4.5,-6.32L13.5,4c0,-0.83 -0.67,-1.5 -1.5,-1.5s-1.5,0.67 -1.5,1.5v0.68C7.63,5.36 6,7.92 6,11v5l-2,2v1h16v-1L18,16z" />
</vector>
```

In `strings.xml` ergänzen:

```xml
    <string name="kanal_erinnerungen_name">Erinnerungen</string>
    <string name="kanal_erinnerungen_beschreibung">Erinnerungen an deine täglichen Aktivitäten</string>
    <string name="aktion_erledigt">Erledigt</string>
    <string name="aktion_spaeter">Später</string>
    <string name="aktion_ausgelassen">Ausgelassen</string>
    <string name="erinnerung_jetzt_faellig">Jetzt fällig</string>
    <string name="erinnerung_noch_offen">Noch %1$d von %2$d offen</string>
```

In `RepositoryModul` in `di/DatenModul.kt` binden:

```kotlin
    @Binds abstract fun erinnerungsanzeige(impl: ErinnerungsNotifier): Erinnerungsanzeige
```

`DayOrganizerApp.kt` erweitern:

```kotlin
package de.vanhake.dayorganizer

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import de.vanhake.dayorganizer.platform.notification.Kanaele

@HiltAndroidApp
class DayOrganizerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Kanaele.anlegen(this)
    }
}
```

- [ ] **Step 7: Build und Tests laufen lassen**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/
git commit -m "feat(platform): Benachrichtigungskanal, Notifier und Aktionszuordnung"
```

---

### Task 14: Erinnerungstermine und Alarmplanung

Hier entsteht der Kern des Erinnerungsmotors. Erinnerungstermine werden als eigene Tabelle materialisiert; damit ist „der nächste Alarm" eine simple Minimum-Abfrage, und Nachfassen oder „Später" sind nur weitere Zeilen.

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/ErinnerungEntity.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/ErinnerungDao.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/Migrationen.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/data/db/DayOrganizerDatenbank.kt` — Version 2
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/di/DatenModul.kt` — Migration registrieren, `ErinnerungDao` bereitstellen
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/data/repository/ErinnerungRepository.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/FaelligkeitenMaterialisieren.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/alarm/AlarmPlaner.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/ErinnerungenMaterialisierenTest.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/data/db/MigrationTest.kt`

**Interfaces:**
- Consumes: `ZeitplanRechner.anstossZeitpunkteFuer` aus Task 5, `FaelligkeitenMaterialisieren` aus Task 11
- Produces:
  - `Erinnerung(id: Long, faelligkeitId: Long, zeitpunkt: LocalDateTime, versuch: Int, ausgeloest: Boolean)`
  - `ErinnerungRepository.anlegenFallsNeu(neue: List<Erinnerung>)`, `.naechsteAb(zeitpunkt: LocalDateTime): Erinnerung?`, `.faelligeBis(zeitpunkt: LocalDateTime): List<Erinnerung>`, `.alsAusgeloestMarkieren(id: Long)`, `.entfernenFuerFaelligkeit(faelligkeitId: Long)`
  - `FaelligkeitenMaterialisieren` legt jetzt zusätzlich die Erinnerungstermine an
  - `AlarmPlaner.neuPlanen()` — setzt genau einen exakten Alarm auf `naechsteAb(jetzt)`; gibt es keinen Termin, wird ein eventuell gesetzter Alarm gelöscht
  - `ErinnerungEntity.schluessel` mit eindeutigem Index, Format `"<faelligkeitId>|<zeitpunkt>|<versuch>"`

- [ ] **Step 1: Fehlschlagenden Test für die Materialisierung schreiben**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ErinnerungenMaterialisierenTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 6, 0).atZone(zone).toInstant(),
        zone,
    )

    private fun aktivitaet(id: Long, zeitplan: Zeitplan) = Aktivitaet(
        id = id,
        name = "Test $id",
        farbe = 1,
        basisPunkte = 10,
        zeitplan = zeitplan,
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    @Test
    fun `feste Zeiten erzeugen je Faelligkeit genau einen Erinnerungstermin`() = runTest {
        val erinnerungen = FakeErinnerungRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(
                listOf(
                    aktivitaet(
                        1,
                        Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
                    ),
                ),
            ),
            faelligkeiten = FakeFaelligkeitRepository(),
            erinnerungen = erinnerungen,
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 2)

        assertEquals(3, erinnerungen.stand.value.size)
        assertEquals(
            LocalDateTime.of(2026, 3, 2, 8, 0),
            erinnerungen.stand.value.first().zeitpunkt,
        )
        assertEquals(0, erinnerungen.stand.value.first().versuch)
    }

    @Test
    fun `ein Wochenziel ohne Anstoss erzeugt keinen Erinnerungstermin`() = runTest {
        val erinnerungen = FakeErinnerungRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(listOf(aktivitaet(1, Zeitplan.Wochenziel(3)))),
            faelligkeiten = FakeFaelligkeitRepository(),
            erinnerungen = erinnerungen,
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 6)

        assertEquals(0, erinnerungen.stand.value.size)
    }

    @Test
    fun `ein Wochenziel mit Anstoss erinnert an den gewaehlten Tagen`() = runTest {
        val erinnerungen = FakeErinnerungRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(
                listOf(
                    aktivitaet(
                        1,
                        Zeitplan.Wochenziel(
                            anzahl = 3,
                            anstoss = listOf(Anstoss(DayOfWeek.TUESDAY, LocalTime.of(18, 0))),
                        ),
                    ),
                ),
            ),
            faelligkeiten = FakeFaelligkeitRepository(),
            erinnerungen = erinnerungen,
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 6)

        assertEquals(
            listOf(LocalDateTime.of(2026, 3, 3, 18, 0)),
            erinnerungen.stand.value.map { it.zeitpunkt },
        )
    }

    @Test
    fun `ein zweiter Lauf legt keine doppelten Erinnerungen an`() = runTest {
        val erinnerungen = FakeErinnerungRepository()
        val use = FaelligkeitenMaterialisieren(
            aktivitaeten = FakeAktivitaetRepository(
                listOf(
                    aktivitaet(
                        1,
                        Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
                    ),
                ),
            ),
            faelligkeiten = FakeFaelligkeitRepository(),
            erinnerungen = erinnerungen,
            zeitplanRechner = ZeitplanRechner(),
            uhr = uhr,
        )

        use(vorausTage = 2)
        use(vorausTage = 2)

        assertEquals(3, erinnerungen.stand.value.size)
    }
}
```

Dazu kommt in `Fakes.kt`:

```kotlin
class FakeErinnerungRepository(start: List<Erinnerung> = emptyList()) : ErinnerungRepository {
    val stand = MutableStateFlow(start)
    private var naechsteId = (start.maxOfOrNull { it.id } ?: 0L) + 1

    private fun schluessel(e: Erinnerung) = "${e.faelligkeitId}|${e.zeitpunkt}|${e.versuch}"

    override suspend fun anlegenFallsNeu(neue: List<Erinnerung>) {
        val vorhanden = stand.value.map(::schluessel).toMutableSet()
        val ergaenzt = stand.value.toMutableList()
        neue.forEach { e -> if (vorhanden.add(schluessel(e))) ergaenzt += e.copy(id = naechsteId++) }
        stand.value = ergaenzt.sortedBy { it.zeitpunkt }
    }

    override suspend fun naechsteAb(zeitpunkt: LocalDateTime): Erinnerung? =
        stand.value.filter { !it.ausgeloest && !it.zeitpunkt.isBefore(zeitpunkt) }
            .minByOrNull { it.zeitpunkt }

    override suspend fun faelligeBis(zeitpunkt: LocalDateTime): List<Erinnerung> =
        stand.value.filter { !it.ausgeloest && !it.zeitpunkt.isAfter(zeitpunkt) }
            .sortedBy { it.zeitpunkt }

    override suspend fun alsAusgeloestMarkieren(id: Long) {
        stand.value = stand.value.map { if (it.id == id) it.copy(ausgeloest = true) else it }
    }

    // Wie die DAO-Abfrage: bereits ausgeloeste Erinnerungen bleiben stehen,
    // damit die Historie erhalten bleibt.
    override suspend fun entfernenFuerFaelligkeit(faelligkeitId: Long) {
        stand.value = stand.value.filterNot { it.faelligkeitId == faelligkeitId && !it.ausgeloest }
    }
}
```

Die dafür nötigen Importe in `Fakes.kt` sind `de.vanhake.dayorganizer.data.repository.ErinnerungRepository` und `de.vanhake.dayorganizer.domain.model.Erinnerung`.

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*ErinnerungenMaterialisierenTest*"`
Expected: Kompilierfehler `Unresolved reference: FakeErinnerungRepository`.

- [ ] **Step 3: Modell, Entity, DAO und Repository anlegen**

In `domain/src/main/kotlin/de/vanhake/dayorganizer/domain/model/Erinnerung.kt`:

```kotlin
package de.vanhake.dayorganizer.domain.model

import java.time.LocalDateTime

/**
 * Ein geplanter Erinnerungszeitpunkt. Erstversuche entstehen beim
 * Materialisieren, Nachfassen und "Spaeter" legen weitere Zeilen an.
 */
data class Erinnerung(
    val id: Long = 0,
    val faelligkeitId: Long,
    val zeitpunkt: LocalDateTime,
    val versuch: Int = 0,
    val ausgeloest: Boolean = false,
)
```

`data/db/ErinnerungEntity.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(
    tableName = "erinnerung",
    foreignKeys = [
        ForeignKey(
            entity = FaelligkeitEntity::class,
            parentColumns = ["id"],
            childColumns = ["faelligkeitId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["schluessel"], unique = true),
        Index(value = ["ausgeloest", "zeitpunkt"]),
        Index(value = ["faelligkeitId"]),
    ],
)
data class ErinnerungEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val faelligkeitId: Long,
    /** "<faelligkeitId>|<zeitpunkt>|<versuch>". */
    val schluessel: String,
    val zeitpunkt: LocalDateTime,
    val versuch: Int,
    val ausgeloest: Boolean,
)
```

`data/db/ErinnerungDao.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.time.LocalDateTime

@Dao
interface ErinnerungDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun anlegenFallsNeu(eintraege: List<ErinnerungEntity>)

    @Query(
        "SELECT * FROM erinnerung WHERE ausgeloest = 0 AND zeitpunkt >= :zeitpunkt " +
            "ORDER BY zeitpunkt LIMIT 1",
    )
    suspend fun naechsteAb(zeitpunkt: LocalDateTime): ErinnerungEntity?

    @Query(
        "SELECT * FROM erinnerung WHERE ausgeloest = 0 AND zeitpunkt <= :zeitpunkt ORDER BY zeitpunkt",
    )
    suspend fun faelligeBis(zeitpunkt: LocalDateTime): List<ErinnerungEntity>

    @Query("UPDATE erinnerung SET ausgeloest = 1 WHERE id = :id")
    suspend fun alsAusgeloestMarkieren(id: Long)

    @Query("DELETE FROM erinnerung WHERE faelligkeitId = :faelligkeitId AND ausgeloest = 0")
    suspend fun entfernenFuerFaelligkeit(faelligkeitId: Long)
}
```

`data/repository/ErinnerungRepository.kt`:

```kotlin
package de.vanhake.dayorganizer.data.repository

import de.vanhake.dayorganizer.data.db.ErinnerungDao
import de.vanhake.dayorganizer.data.db.ErinnerungEntity
import de.vanhake.dayorganizer.domain.model.Erinnerung
import java.time.LocalDateTime
import javax.inject.Inject

interface ErinnerungRepository {
    suspend fun anlegenFallsNeu(neue: List<Erinnerung>)
    suspend fun naechsteAb(zeitpunkt: LocalDateTime): Erinnerung?
    suspend fun faelligeBis(zeitpunkt: LocalDateTime): List<Erinnerung>
    suspend fun alsAusgeloestMarkieren(id: Long)
    suspend fun entfernenFuerFaelligkeit(faelligkeitId: Long)
}

private fun Erinnerung.zuEntity() = ErinnerungEntity(
    id = id,
    faelligkeitId = faelligkeitId,
    schluessel = "$faelligkeitId|$zeitpunkt|$versuch",
    zeitpunkt = zeitpunkt,
    versuch = versuch,
    ausgeloest = ausgeloest,
)

private fun ErinnerungEntity.zuDomaene() = Erinnerung(
    id = id,
    faelligkeitId = faelligkeitId,
    zeitpunkt = zeitpunkt,
    versuch = versuch,
    ausgeloest = ausgeloest,
)

class RaumErinnerungRepository @Inject constructor(
    private val dao: ErinnerungDao,
) : ErinnerungRepository {

    override suspend fun anlegenFallsNeu(neue: List<Erinnerung>) =
        dao.anlegenFallsNeu(neue.map { it.zuEntity() })

    override suspend fun naechsteAb(zeitpunkt: LocalDateTime): Erinnerung? =
        dao.naechsteAb(zeitpunkt)?.zuDomaene()

    override suspend fun faelligeBis(zeitpunkt: LocalDateTime): List<Erinnerung> =
        dao.faelligeBis(zeitpunkt).map { it.zuDomaene() }

    override suspend fun alsAusgeloestMarkieren(id: Long) = dao.alsAusgeloestMarkieren(id)

    override suspend fun entfernenFuerFaelligkeit(faelligkeitId: Long) =
        dao.entfernenFuerFaelligkeit(faelligkeitId)
}
```

- [ ] **Step 4: Datenbank auf Version 2 heben**

`data/db/Migrationen.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `erinnerung` (
                `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                `faelligkeitId` INTEGER NOT NULL,
                `schluessel` TEXT NOT NULL,
                `zeitpunkt` TEXT NOT NULL,
                `versuch` INTEGER NOT NULL,
                `ausgeloest` INTEGER NOT NULL,
                FOREIGN KEY(`faelligkeitId`) REFERENCES `faelligkeit`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_erinnerung_schluessel` ON `erinnerung` (`schluessel`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_erinnerung_ausgeloest_zeitpunkt` ON `erinnerung` (`ausgeloest`, `zeitpunkt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_erinnerung_faelligkeitId` ON `erinnerung` (`faelligkeitId`)")
    }
}
```

In `DayOrganizerDatenbank.kt` die Annotation ändern und den DAO ergänzen:

```kotlin
@Database(
    entities = [
        AktivitaetEntity::class,
        FaelligkeitEntity::class,
        BuchungEntity::class,
        ErinnerungEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
```

```kotlin
    abstract fun erinnerungDao(): ErinnerungDao
```

In `di/DatenModul.kt` die Migration registrieren und den DAO bereitstellen:

```kotlin
    @Provides
    @Singleton
    fun datenbank(@ApplicationContext kontext: Context): DayOrganizerDatenbank =
        Room.databaseBuilder(kontext, DayOrganizerDatenbank::class.java, "dayorganizer.db")
            .addMigrations(de.vanhake.dayorganizer.data.db.MIGRATION_1_2)
            .build()

    @Provides fun erinnerungDao(db: DayOrganizerDatenbank): ErinnerungDao = db.erinnerungDao()
```

In `RepositoryModul` ergänzen:

```kotlin
    @Binds abstract fun erinnerungen(impl: RaumErinnerungRepository): ErinnerungRepository
```

- [ ] **Step 5: Materialisierung um Erinnerungstermine erweitern**

`domainuse/FaelligkeitenMaterialisieren.kt` vollständig ersetzen:

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.ErinnerungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Erinnerung
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * Legt Faelligkeiten und ihre Erstversuch-Erinnerungen fuer den Vorausblick an.
 * Mehrfachaufrufe sind unschaedlich: eindeutige Schluessel schlucken Wiederholungen.
 */
class FaelligkeitenMaterialisieren @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val erinnerungen: ErinnerungRepository,
    private val zeitplanRechner: ZeitplanRechner,
    private val uhr: Clock,
) {
    suspend operator fun invoke(vorausTage: Long = 7) {
        val heute = LocalDate.now(uhr)
        val bis = heute.plusDays(vorausTage)

        aktivitaeten.alleAktiven().forEach { aktivitaet ->
            faelligkeiten.anlegenFallsNeu(
                zeitplanRechner.faelligkeitenFuer(aktivitaet, heute, bis),
            )
        }

        // Erst nach dem Anlegen lesen: die Faelligkeiten brauchen ihre vergebenen Ids,
        // damit die Erinnerungen darauf zeigen koennen.
        val neueErinnerungen = faelligkeiten.fuerZeitraum(heute, bis).flatMap { f ->
            val aktivitaet = aktivitaeten.nachId(f.aktivitaetId) ?: return@flatMap emptyList()
            zeitplanRechner.anstossZeitpunkteFuer(aktivitaet, f)
                .map { Erinnerung(faelligkeitId = f.id, zeitpunkt = it, versuch = 0) }
        }
        erinnerungen.anlegenFallsNeu(neueErinnerungen)
    }
}
```

- [ ] **Step 6: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*ErinnerungenMaterialisierenTest*" --tests "*FaelligkeitenMaterialisierenTest*"`
Expected: `BUILD SUCCESSFUL`. Der bestehende `FaelligkeitenMaterialisierenTest` muss um das neue Konstruktorargument `erinnerungen = FakeErinnerungRepository()` ergänzt werden.

- [ ] **Step 7: AlarmPlaner anlegen**

`platform/alarm/AlarmPlaner.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import de.vanhake.dayorganizer.data.repository.ErinnerungRepository
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Haelt genau einen exakten Alarm auf den naechsten anstehenden Erinnerungstermin.
 * Jede Aenderung an Aktivitaeten, Faelligkeiten oder Erinnerungen ruft [neuPlanen].
 */
@Singleton
class AlarmPlaner @Inject constructor(
    @ApplicationContext private val kontext: Context,
    private val erinnerungen: ErinnerungRepository,
    private val uhr: Clock,
) {

    suspend fun neuPlanen() {
        val manager = kontext.getSystemService<AlarmManager>() ?: return
        val naechste = erinnerungen.naechsteAb(LocalDateTime.now(uhr))

        if (naechste == null) {
            manager.cancel(alarmIntent())
            return
        }

        val zeitpunktMillis = naechste.zeitpunkt
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        if (manager.canScheduleExactAlarms()) {
            manager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                zeitpunktMillis,
                alarmIntent(),
            )
        } else {
            // Ohne die Berechtigung bleibt nur der ungenaue Alarm. Der Banner auf
            // dem Heute-Screen weist darauf hin, dass Erinnerungen verspaetet kommen.
            Log.w("AlarmPlaner", "Exakte Alarme nicht erlaubt, weiche auf ungenauen Alarm aus")
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, zeitpunktMillis, alarmIntent())
        }
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        kontext,
        ALARM_ANFRAGE,
        Intent(kontext, ErinnerungsEmpfaenger::class.java).apply {
            action = ErinnerungsEmpfaenger.ACTION_ERINNERUNG_FAELLIG
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private companion object {
        const val ALARM_ANFRAGE = 1001
    }
}
```

`ErinnerungsEmpfaenger` entsteht in Task 15. Damit dieser Task für sich kompiliert, wird dort zunächst nur das Gerüst angelegt:

`platform/alarm/ErinnerungsEmpfaenger.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ErinnerungsEmpfaenger : BroadcastReceiver() {

    override fun onReceive(kontext: Context, intent: Intent) {
        // Vollstaendige Verarbeitung folgt in Task 15.
    }

    companion object {
        const val ACTION_ERINNERUNG_FAELLIG = "de.vanhake.dayorganizer.ERINNERUNG_FAELLIG"
    }
}
```

Im Manifest innerhalb von `<application>` registrieren:

```xml
        <receiver
            android:name=".platform.alarm.ErinnerungsEmpfaenger"
            android:exported="false">
            <intent-filter>
                <action android:name="de.vanhake.dayorganizer.ERINNERUNG_FAELLIG" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 8: Migrationstest schreiben und laufen lassen**

`app/src/test/kotlin/de/vanhake/dayorganizer/data/db/MigrationTest.kt`:

```kotlin
package de.vanhake.dayorganizer.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationTest {

    @get:Rule
    val helfer = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        DayOrganizerDatenbank::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migration_1_auf_2_legt_die_Erinnerungstabelle_an() {
        helfer.createDatabase(DB_NAME, 1).close()

        val db = helfer.runMigrationsAndValidate(DB_NAME, 2, true, MIGRATION_1_2)

        db.query("SELECT COUNT(*) FROM erinnerung").use {
            it.moveToFirst()
            assertEquals(0, it.getInt(0))
        }
        db.close()
    }

    private companion object {
        const val DB_NAME = "migrationstest.db"
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "*MigrationTest*"`
Expected: `BUILD SUCCESSFUL`, 1 Test bestanden.

Findet `MigrationTestHelper` die exportierten Schemas unter Robolectric nicht,
liegt es am Asset-Pfad. Fällt der Versuch aus, ersetze den Helfer durch einen
direkten Test: eine Version-1-Datenbank mit explizitem `CREATE TABLE` anlegen,
`MIGRATION_1_2.migrate(db)` aufrufen und das Vorhandensein der Tabelle prüfen.
Das prüft dieselbe Zusage und hängt an keinem Asset.

- [ ] **Step 9: Auflösung über die Zeitumstellung absichern**

Der `AlarmPlaner` ist die einzige Stelle, an der Ortszeit zu einem Instant
wird. Genau hier schlägt die Zeitumstellung zu: am 29.03.2026 existiert 02:30
in Europa/Berlin nicht, am 25.10.2026 gibt es sie zweimal. Diese Zusage gehört
geprüft, und zwar hier statt im `ZeitplanRechner`, der gar keine Zeitzone kennt.

Ziehe die Umrechnung dafür in eine eigene, testbare Funktion in
`platform/alarm/AlarmPlaner.kt` auf Dateiebene:

```kotlin
/**
 * Ortszeit zu Instant. Faellt die Ortszeit durch die Sommerzeitumstellung aus,
 * nimmt die Zone den naechsten gueltigen Zeitpunkt; gibt es sie doppelt, den
 * ersten. Beides ist besser als ein Alarm, der gar nicht feuert.
 */
internal fun alsEpochMillis(zeitpunkt: LocalDateTime, zone: ZoneId): Long =
    zeitpunkt.atZone(zone).toInstant().toEpochMilli()
```

und rufe sie aus `neuPlanen()` auf, statt dort direkt umzurechnen. Dazu der Test
`app/src/test/kotlin/de/vanhake/dayorganizer/platform/alarm/AlarmPlanerZeitzoneTest.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.alarm

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class AlarmPlanerZeitzoneTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `eine ausgefallene Ortszeit wird auf den naechsten gueltigen Zeitpunkt gelegt`() {
        val millis = alsEpochMillis(LocalDateTime.of(2026, 3, 29, 2, 30), berlin)

        assertEquals(
            LocalDateTime.of(2026, 3, 29, 3, 30),
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), berlin),
        )
    }

    @Test
    fun `eine doppelte Ortszeit nimmt den ersten Durchgang`() {
        val millis = alsEpochMillis(LocalDateTime.of(2026, 10, 25, 2, 30), berlin)

        // Der erste Durchgang liegt noch in der Sommerzeit, also UTC+2.
        assertEquals(Instant.parse("2026-10-25T00:30:00Z"), Instant.ofEpochMilli(millis))
    }

    @Test
    fun `eine gewoehnliche Ortszeit bleibt unveraendert`() {
        val millis = alsEpochMillis(LocalDateTime.of(2026, 3, 2, 8, 0), berlin)

        assertEquals(Instant.parse("2026-03-02T07:00:00Z"), Instant.ofEpochMilli(millis))
    }
}
```

Run: `./gradlew :app:testDebugUnitTest --tests "*AlarmPlanerZeitzoneTest*"`
Expected: `BUILD SUCCESSFUL`, 3 Tests bestanden.

- [ ] **Step 10: Den rollierenden Alarm selbst festnageln**

`alsEpochMillis` ist geprüft, `neuPlanen` bisher nicht — und genau dort steht
die zentrale Zusage des Designs. Bricht später jemand die `PendingIntent`-
Gleichheit zwischen Setz- und Abbruchpfad, feuert ein verwaister Alarm für
immer weiter, ohne dass ein Test das bemerkt.

Schreibe `app/src/test/kotlin/de/vanhake/dayorganizer/platform/alarm/AlarmPlanerTest.kt`
als Robolectric-Test (`@Config(sdk = [33])`), mit
`ApplicationProvider.getApplicationContext<Context>()`, einem
`FakeErinnerungRepository` und fester Uhr. `org.robolectric.Shadows.shadowOf`
liefert einen `ShadowAlarmManager`, über den sich die gesetzten Alarme lesen
lassen. Abgedeckt gehören:

- genau ein Alarm, und zwar auf die **frühere** von zwei Erinnerungen
- zweimal `neuPlanen()` ergibt weiterhin genau einen Alarm, nicht zwei
- nach `alsAusgeloestMarkieren` der frühesten zeigt der Alarm auf die nächste
- ohne fällige Erinnerung wird der Alarm abgebrochen — erst einen setzen, dann
  leeren und neu planen, damit der Test das Abbrechen belegt und nicht nur die
  Abwesenheit
- bereits ausgelöste Erinnerungen werden übersprungen

Dazu in `ErinnerungenMaterialisierenTest` noch
`ein Wochenziel mit Anstoss erzeugt beim zweiten Lauf keine doppelte Erinnerung` —
Zählziele laufen über `anstossZeitpunkteFuer` und haben `geplantFuer == null`,
also genau den Fall, für den es den `ZIEL`-Platzhalter gibt.

Run: `./gradlew :app:testDebugUnitTest --tests "*AlarmPlanerTest*"`
Expected: `BUILD SUCCESSFUL`, 5 Tests bestanden.

- [ ] **Step 11: Commit**

```bash
git add app/ domain/
git commit -m "feat(platform): Erinnerungstermine als Tabelle und rollierender Exakt-Alarm"
```

---

### Task 15: Erinnerungen auslösen und Aktionen verarbeiten

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/ErinnerungAusloesen.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/alarm/ErinnerungsEmpfaenger.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/notification/AktionsEmpfaenger.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/ErinnerungAusloesenTest.kt`

**Interfaces:**
- Consumes: `Erinnerungsanzeige` aus Task 13, `ErinnerungRepository` und `AlarmPlaner` aus Task 14, `FaelligkeitErledigen`, `FaelligkeitAuslassen` aus Task 12
- Produces:
  - `ErinnerungAusloesen.invoke()` — verarbeitet alle bis jetzt fälligen Erinnerungen, zeigt Benachrichtigungen, plant Nachfassen und setzt den nächsten Alarm
  - `ErinnerungSpaeter.invoke(faelligkeitId: Long)` — verschiebt um `nachfassAbstandMinuten`

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Erinnerung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class ErinnerungAusloesenTest {

    private val zone = ZoneId.of("Europe/Berlin")

    private class FakeAnzeige : Erinnerungsanzeige {
        val gezeigt = mutableListOf<Long>()
        val versteckt = mutableListOf<Long>()
        override fun zeige(faelligkeit: Faelligkeit, aktivitaet: Aktivitaet) {
            gezeigt += faelligkeit.id
        }
        override fun verstecke(faelligkeitId: Long) {
            versteckt += faelligkeitId
        }
    }

    private val tabletten = Aktivitaet(
        id = 1,
        name = "Tabletten",
        farbe = 1,
        basisPunkte = 10,
        nachfassAnzahl = 2,
        nachfassAbstandMinuten = 15,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private val morgens = Faelligkeit(
        id = 10,
        aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 2),
        periodeBis = LocalDate.of(2026, 3, 2),
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0),
        sollAnzahl = 1,
    )

    private fun uhrUm(stunde: Int, minute: Int) = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, stunde, minute).atZone(zone).toInstant(),
        zone,
    )

    @Test
    fun `eine faellige Erinnerung wird gezeigt und das Nachfassen geplant`() = runTest {
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 1, faelligkeitId = 10, zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 0))),
        )
        val anzeige = FakeAnzeige()
        val use = ErinnerungAusloesen(
            erinnerungen = erinnerungen,
            faelligkeiten = FakeFaelligkeitRepository(listOf(morgens)),
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            anzeige = anzeige,
            uhr = uhrUm(8, 0),
        )

        use()

        assertEquals(listOf(10L), anzeige.gezeigt)
        assertEquals(true, erinnerungen.stand.value.first { it.id == 1L }.ausgeloest)
        val nachfass = erinnerungen.stand.value.single { it.versuch == 1 }
        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 15), nachfass.zeitpunkt)
    }

    @Test
    fun `nach dem letzten Nachfassversuch wird nicht weiter geplant`() = runTest {
        val erinnerungen = FakeErinnerungRepository(
            listOf(
                Erinnerung(
                    id = 1, faelligkeitId = 10,
                    zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 30), versuch = 2,
                ),
            ),
        )
        val use = ErinnerungAusloesen(
            erinnerungen = erinnerungen,
            faelligkeiten = FakeFaelligkeitRepository(listOf(morgens)),
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            anzeige = FakeAnzeige(),
            uhr = uhrUm(8, 30),
        )

        use()

        assertEquals(1, erinnerungen.stand.value.size)
    }

    @Test
    fun `eine bereits erledigte Faelligkeit erzeugt keine Benachrichtigung`() = runTest {
        val erledigt = morgens.copy(status = FaelligkeitStatus.ERLEDIGT, istAnzahl = 1)
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 1, faelligkeitId = 10, zeitpunkt = LocalDateTime.of(2026, 3, 2, 8, 0))),
        )
        val anzeige = FakeAnzeige()
        val use = ErinnerungAusloesen(
            erinnerungen = erinnerungen,
            faelligkeiten = FakeFaelligkeitRepository(listOf(erledigt)),
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            anzeige = anzeige,
            uhr = uhrUm(8, 0),
        )

        use()

        assertEquals(emptyList(), anzeige.gezeigt)
        assertEquals(true, erinnerungen.stand.value.single().ausgeloest)
    }

    @Test
    fun `noch nicht faellige Erinnerungen bleiben unberuehrt`() = runTest {
        val erinnerungen = FakeErinnerungRepository(
            listOf(Erinnerung(id = 1, faelligkeitId = 10, zeitpunkt = LocalDateTime.of(2026, 3, 2, 20, 0))),
        )
        val anzeige = FakeAnzeige()
        val use = ErinnerungAusloesen(
            erinnerungen = erinnerungen,
            faelligkeiten = FakeFaelligkeitRepository(listOf(morgens)),
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            anzeige = anzeige,
            uhr = uhrUm(8, 0),
        )

        use()

        assertEquals(emptyList(), anzeige.gezeigt)
        assertEquals(false, erinnerungen.stand.value.single().ausgeloest)
    }

    @Test
    fun `Spaeter verschiebt um den Nachfassabstand`() = runTest {
        val erinnerungen = FakeErinnerungRepository()
        val use = ErinnerungSpaeter(
            erinnerungen = erinnerungen,
            faelligkeiten = FakeFaelligkeitRepository(listOf(morgens)),
            aktivitaeten = FakeAktivitaetRepository(listOf(tabletten)),
            uhr = uhrUm(8, 5),
        )

        use(10)

        assertEquals(LocalDateTime.of(2026, 3, 2, 8, 20), erinnerungen.stand.value.single().zeitpunkt)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*ErinnerungAusloesenTest*"`
Expected: Kompilierfehler `Unresolved reference: ErinnerungAusloesen`.

- [ ] **Step 3: Use Cases implementieren**

`domainuse/ErinnerungAusloesen.kt`:

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.ErinnerungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Erinnerung
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/** Verarbeitet alle bis jetzt faelligen Erinnerungstermine. */
class ErinnerungAusloesen @Inject constructor(
    private val erinnerungen: ErinnerungRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val aktivitaeten: AktivitaetRepository,
    private val anzeige: Erinnerungsanzeige,
    private val uhr: Clock,
) {
    suspend operator fun invoke() {
        val jetzt = LocalDateTime.now(uhr)

        erinnerungen.faelligeBis(jetzt).forEach { erinnerung ->
            erinnerungen.alsAusgeloestMarkieren(erinnerung.id)

            val faelligkeit = faelligkeiten.nachId(erinnerung.faelligkeitId) ?: return@forEach
            if (faelligkeit.status != FaelligkeitStatus.OFFEN) return@forEach
            val aktivitaet = aktivitaeten.nachId(faelligkeit.aktivitaetId) ?: return@forEach

            anzeige.zeige(faelligkeit, aktivitaet)

            if (erinnerung.versuch < aktivitaet.nachfassAnzahl) {
                erinnerungen.anlegenFallsNeu(
                    listOf(
                        Erinnerung(
                            faelligkeitId = faelligkeit.id,
                            zeitpunkt = jetzt.plusMinutes(aktivitaet.nachfassAbstandMinuten.toLong()),
                            versuch = erinnerung.versuch + 1,
                        ),
                    ),
                )
            }
        }
    }
}

/** Verschiebt die Erinnerung einer Faelligkeit um den Nachfassabstand. */
class ErinnerungSpaeter @Inject constructor(
    private val erinnerungen: ErinnerungRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val aktivitaeten: AktivitaetRepository,
    private val uhr: Clock,
) {
    suspend operator fun invoke(faelligkeitId: Long) {
        val faelligkeit = faelligkeiten.nachId(faelligkeitId) ?: return
        if (faelligkeit.status != FaelligkeitStatus.OFFEN) return
        val aktivitaet = aktivitaeten.nachId(faelligkeit.aktivitaetId) ?: return

        erinnerungen.entfernenFuerFaelligkeit(faelligkeitId)
        erinnerungen.anlegenFallsNeu(
            listOf(
                Erinnerung(
                    faelligkeitId = faelligkeitId,
                    zeitpunkt = LocalDateTime.now(uhr)
                        .plusMinutes(aktivitaet.nachfassAbstandMinuten.toLong()),
                    versuch = 0,
                ),
            ),
        )
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*ErinnerungAusloesenTest*"`
Expected: `BUILD SUCCESSFUL`, 5 Tests bestanden.

- [ ] **Step 5: Empfänger verdrahten**

`platform/alarm/ErinnerungsEmpfaenger.kt` vollständig ersetzen:

```kotlin
package de.vanhake.dayorganizer.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.domainuse.ErinnerungAusloesen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ErinnerungsEmpfaenger : BroadcastReceiver() {

    @Inject lateinit var ausloesen: ErinnerungAusloesen
    @Inject lateinit var alarmPlaner: AlarmPlaner

    override fun onReceive(kontext: Context, intent: Intent) {
        if (intent.action != ACTION_ERINNERUNG_FAELLIG) return
        val fertig = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                ausloesen()
                alarmPlaner.neuPlanen()
            } finally {
                fertig.finish()
            }
        }
    }

    companion object {
        const val ACTION_ERINNERUNG_FAELLIG = "de.vanhake.dayorganizer.ERINNERUNG_FAELLIG"
    }
}
```

`platform/notification/AktionsEmpfaenger.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.domainuse.ErinnerungSpaeter
import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassen
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigen
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AktionsEmpfaenger : BroadcastReceiver() {

    @Inject lateinit var erledigen: FaelligkeitErledigen
    @Inject lateinit var auslassen: FaelligkeitAuslassen
    @Inject lateinit var spaeter: ErinnerungSpaeter
    @Inject lateinit var notifier: ErinnerungsNotifier
    @Inject lateinit var alarmPlaner: AlarmPlaner

    override fun onReceive(kontext: Context, intent: Intent) {
        val aktion = Benachrichtigungsaktionen.aus(
            intent.action,
            intent.getLongExtra(Benachrichtigungsaktionen.EXTRA_FAELLIGKEIT_ID, 0),
        ) ?: return

        val fertig = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                when (aktion) {
                    is Benachrichtigungsaktion.Erledigt -> erledigen(aktion.faelligkeitId)
                    is Benachrichtigungsaktion.Ausgelassen -> auslassen(aktion.faelligkeitId)
                    is Benachrichtigungsaktion.Spaeter -> spaeter(aktion.faelligkeitId)
                }
                notifier.verstecke(aktion.faelligkeitId)
                alarmPlaner.neuPlanen()
            } finally {
                fertig.finish()
            }
        }
    }
}
```

Im Manifest innerhalb von `<application>` ergänzen:

```xml
        <receiver
            android:name=".platform.notification.AktionsEmpfaenger"
            android:exported="false">
            <intent-filter>
                <action android:name="de.vanhake.dayorganizer.ERLEDIGT" />
                <action android:name="de.vanhake.dayorganizer.SPAETER" />
                <action android:name="de.vanhake.dayorganizer.AUSGELASSEN" />
            </intent-filter>
        </receiver>
```

- [ ] **Step 6: Build und alle Tests laufen lassen**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :domain:test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/
git commit -m "feat(platform): Erinnerungen ausloesen, nachfassen und aus der Leiste abhaken"
```

---

### Task 16: Wächter und Neustart

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/domainuse/WaechterAblauf.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/work/WaechterWorker.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/work/WaechterPlanung.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/alarm/BootEmpfaenger.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/DayOrganizerApp.kt` — WorkManager-Konfiguration für Hilt
- Modify: `app/src/main/AndroidManifest.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/domainuse/WaechterAblaufTest.kt`

**Interfaces:**
- Consumes: `VerpassteAbschliessen` aus Task 12, `FaelligkeitenMaterialisieren` aus Task 14
- Produces:
  - `WaechterAblauf.invoke()` — führt in dieser Reihenfolge aus: verpasste Fälligkeiten schließen, Vorausblick materialisieren, nächsten Alarm setzen
  - `WaechterPlanung.taeglichPlanen()` — meldet den periodischen Job an; `WaechterPlanung.sofortAusfuehren()` für Boot und App-Update
  - `BootEmpfaenger` reagiert auf `BOOT_COMPLETED` und `MY_PACKAGE_REPLACED`

Die Reihenfolge — erst schließen, dann materialisieren, dann neu planen — ist
absichtlich so gewählt, trägt aber heute nichts: `VerpassteAbschliessen`
vergleicht ganze Tage (`periodeBis < heute`), und materialisiert werden nur
Fälligkeiten ab heute. Die beiden Mengen überschneiden sich nie. Schließen
zuerst bleibt trotzdem die sichere Reihenfolge, falls die Verpasst-Prüfung
jemals auf Uhrzeiten umgestellt wird. Schreibe keinen Test, der behauptet, die
Reihenfolge zu beweisen — er kann nicht fehlschlagen.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import kotlinx.coroutines.test.runTest
import java.time.Clock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaechterAblaufTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 3, 0).atZone(zone).toInstant(),
        zone,
    )

    private val tabletten = Aktivitaet(
        id = 1,
        name = "Tabletten",
        farbe = 1,
        basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private val gestern = Faelligkeit(
        id = 9,
        aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 1),
        periodeBis = LocalDate.of(2026, 3, 1),
        geplantFuer = LocalDateTime.of(2026, 3, 1, 8, 0),
        sollAnzahl = 1,
    )

    @Test
    fun `der Waechter schliesst Verpasstes und fuellt den Vorausblick auf`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(gestern))
        val aktivitaeten = FakeAktivitaetRepository(listOf(tabletten))
        val erinnerungen = FakeErinnerungRepository()
        var alarmNeuGeplant = false

        val ablauf = WaechterAblauf(
            verpassteAbschliessen = VerpassteAbschliessen(faelligkeiten, uhr),
            materialisieren = FaelligkeitenMaterialisieren(
                aktivitaeten, faelligkeiten, erinnerungen, ZeitplanRechner(), uhr,
            ),
            alarmNeuPlanen = { alarmNeuGeplant = true },
        )

        ablauf()

        assertEquals(
            FaelligkeitStatus.VERPASST,
            faelligkeiten.stand.value.first { it.id == 9L }.status,
        )
        assertEquals(9, faelligkeiten.stand.value.size)
        assertTrue(alarmNeuGeplant)
    }

    @Test
    fun `eine heute faellige Aufgabe bleibt bis Mitternacht offen`() = runTest {
        val faelligkeiten = FakeFaelligkeitRepository()
        val aktivitaeten = FakeAktivitaetRepository(listOf(tabletten))
        val ablauf = WaechterAblauf(
            verpassteAbschliessen = VerpassteAbschliessen(faelligkeiten, uhr),
            materialisieren = FaelligkeitenMaterialisieren(
                aktivitaeten, faelligkeiten, FakeErinnerungRepository(), ZeitplanRechner(), uhr,
            ),
            alarmNeuPlanen = {},
        )

        ablauf()

        assertTrue(faelligkeiten.stand.value.all { it.status == FaelligkeitStatus.OFFEN })
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*WaechterAblaufTest*"`
Expected: Kompilierfehler `Unresolved reference: WaechterAblauf`.

- [ ] **Step 3: Ablauf implementieren**

```kotlin
package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import javax.inject.Inject

/**
 * Die Selbstheilung der App. Reihenfolge ist bindend: erst abgelaufene
 * Faelligkeiten schliessen, dann den Vorausblick auffuellen, dann den
 * naechsten Alarm setzen.
 */
class WaechterAblauf(
    private val verpassteAbschliessen: VerpassteAbschliessen,
    private val materialisieren: FaelligkeitenMaterialisieren,
    private val alarmNeuPlanen: suspend () -> Unit,
) {

    @Inject
    constructor(
        verpassteAbschliessen: VerpassteAbschliessen,
        materialisieren: FaelligkeitenMaterialisieren,
        alarmPlaner: AlarmPlaner,
    ) : this(verpassteAbschliessen, materialisieren, { alarmPlaner.neuPlanen() })

    suspend operator fun invoke() {
        verpassteAbschliessen()
        materialisieren()
        alarmNeuPlanen()
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Zwei Dinge zu den Erwartungswerten. Der erste Test erwartet **9** Fälligkeiten,
nicht 8: ein Vorausblick von sieben Tagen deckt acht Tage einschließlich heute
ab, dazu kommt die vorbestehende von gestern.

Der zweite Test nagelt die Kulanzregel fest statt der Reihenfolge: eine Aufgabe,
deren Sollzeitpunkt heute schon vorbei ist, bleibt bis Mitternacht offen. Setze
die Uhr dafür auf 12:00 und die Aktivität auf 08:00 und lege die Fälligkeit
vorher an — gegen eine auf Uhrzeiten umgestellte Verpasst-Prüfung schlägt er
dann fehl.

Run: `./gradlew :app:testDebugUnitTest --tests "*WaechterAblaufTest*"`
Expected: `BUILD SUCCESSFUL`, 2 Tests bestanden.

- [ ] **Step 5: Worker, Planung und Boot-Empfänger anlegen**

`platform/work/WaechterWorker.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import de.vanhake.dayorganizer.domainuse.WaechterAblauf

@HiltWorker
class WaechterWorker @AssistedInject constructor(
    @Assisted kontext: Context,
    @Assisted parameter: WorkerParameters,
    private val ablauf: WaechterAblauf,
) : CoroutineWorker(kontext, parameter) {

    override suspend fun doWork(): Result = runCatching { ablauf() }
        .fold(
            onSuccess = { Result.success() },
            onFailure = { fehler ->
                // Nach drei Versuchen aufgeben statt endlos zu wiederholen:
                // der naechtliche Lauf holt es ohnehin nach, und ein dauerhaft
                // scheiterndes Retry kostet nur Akku.
                if (runAttemptCount < MAX_VERSUCHE - 1) {
                    Result.retry()
                } else {
                    Log.w(TAG, "Waechter gibt nach $MAX_VERSUCHE Versuchen auf", fehler)
                    Result.success()
                }
            },
        )

    private companion object {
        const val MAX_VERSUCHE = 3
        const val TAG = "WaechterWorker"
    }
}
```

`platform/work/WaechterPlanung.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WaechterPlanung @Inject constructor(
    @ApplicationContext private val kontext: Context,
    private val uhr: Clock,
) {

    /** Taeglich um drei Uhr nachts, wenn das Geraet es zulaesst. */
    fun taeglichPlanen() {
        val jetzt = LocalDateTime.now(uhr)
        val naechsterLauf = jetzt.toLocalDate().atTime(LAUFZEIT)
            .let { if (it.isAfter(jetzt)) it else it.plusDays(1) }

        val anfrage = PeriodicWorkRequestBuilder<WaechterWorker>(Duration.ofDays(1))
            .setInitialDelay(Duration.between(jetzt, naechsterLauf))
            .build()

        WorkManager.getInstance(kontext).enqueueUniquePeriodicWork(
            NAME_TAEGLICH,
            ExistingPeriodicWorkPolicy.UPDATE,
            anfrage,
        )
    }

    /** Nach Neustart, App-Update und beim ersten App-Start. */
    fun sofortAusfuehren() {
        WorkManager.getInstance(kontext).enqueueUniqueWork(
            NAME_SOFORT,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WaechterWorker>().build(),
        )
    }

    private companion object {
        val LAUFZEIT: LocalTime = LocalTime.of(3, 0)
        const val NAME_TAEGLICH = "waechter_taeglich"
        const val NAME_SOFORT = "waechter_sofort"
    }
}
```

`platform/alarm/BootEmpfaenger.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.platform.work.WaechterPlanung
import javax.inject.Inject

/**
 * Nach einem Neustart sind alle gesetzten Alarme weg. Der Waechter plant sie neu.
 */
@AndroidEntryPoint
class BootEmpfaenger : BroadcastReceiver() {

    @Inject lateinit var planung: WaechterPlanung

    override fun onReceive(kontext: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                planung.sofortAusfuehren()
                planung.taeglichPlanen()
            }
        }
    }
}
```

- [ ] **Step 6: WorkManager für Hilt konfigurieren und Empfänger registrieren**

`DayOrganizerApp.kt` ersetzen:

```kotlin
package de.vanhake.dayorganizer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import de.vanhake.dayorganizer.platform.notification.Kanaele
import de.vanhake.dayorganizer.platform.work.WaechterPlanung
import javax.inject.Inject

@HiltAndroidApp
class DayOrganizerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var waechterPlanung: WaechterPlanung

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        Kanaele.anlegen(this)
        waechterPlanung.taeglichPlanen()
        waechterPlanung.sofortAusfuehren()
    }
}
```

Im Manifest den Standard-Initializer von WorkManager abschalten und den Boot-Empfänger registrieren:

```xml
        <provider
            android:name="androidx.startup.InitializationProvider"
            android:authorities="${applicationId}.androidx-startup"
            android:exported="false"
            tools:node="merge">
            <meta-data
                android:name="androidx.work.WorkManagerInitializer"
                android:value="androidx.startup"
                tools:node="remove" />
        </provider>

        <receiver
            android:name=".platform.alarm.BootEmpfaenger"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
            </intent-filter>
        </receiver>
```

Im `<manifest>`-Tag muss `xmlns:tools="http://schemas.android.com/tools"` ergänzt werden.

- [ ] **Step 7: Build und alle Tests laufen lassen**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :domain:test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Auf dem Gerät prüfen**

APK installieren, eine Aktivität mit Sollzeitpunkt in zwei Minuten anlegen (über die Datenbank oder nach Task 19 über die Oberfläche), Gerät neu starten und warten. Die Benachrichtigung muss kommen.

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`, App erscheint auf dem Gerät.

- [ ] **Step 9: Commit**

```bash
git add app/
git commit -m "feat(platform): naechtlicher Waechter und Neuplanung nach Neustart"
```

---

### Task 17: Berechtigungen und Warnbanner

Ohne diese Aufgabe scheitert die App still: keine Benachrichtigung, kein Hinweis, kein erkennbarer Grund.

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/permission/BerechtigungsStand.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/platform/permission/BerechtigungsPruefer.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/gemeinsam/WarnBanner.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/platform/permission/BerechtigungsHinweiseTest.kt`

**Interfaces:**
- Consumes: nichts aus vorherigen Tasks
- Produces:
  - `BerechtigungsStand(benachrichtigungenErlaubt: Boolean, exakteAlarmeErlaubt: Boolean, akkuOptimierungAktiv: Boolean)`
  - `enum Hinweis { BENACHRICHTIGUNGEN_FEHLEN, EXAKTE_ALARME_FEHLEN, AKKUOPTIMIERUNG_AKTIV }`
  - `BerechtigungsHinweise.fuer(stand: BerechtigungsStand): List<Hinweis>` — Reihenfolge nach Dringlichkeit, leer wenn alles passt
  - `BerechtigungsPruefer.stand(): BerechtigungsStand`
  - `WarnBanner(hinweis: Hinweis, beheben: () -> Unit)` als Composable
  - `Hinweis.einstellungsIntent(kontext: Context): Intent` — führt direkt in die passende Systemeinstellung

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.platform.permission

import kotlin.test.Test
import kotlin.test.assertEquals

class BerechtigungsHinweiseTest {

    private val allesGut = BerechtigungsStand(
        benachrichtigungenErlaubt = true,
        exakteAlarmeErlaubt = true,
        akkuOptimierungAktiv = false,
    )

    @Test
    fun `bei vollstaendigen Berechtigungen gibt es keinen Hinweis`() {
        assertEquals(emptyList(), BerechtigungsHinweise.fuer(allesGut))
    }

    @Test
    fun `fehlende Benachrichtigungen stehen an erster Stelle`() {
        val stand = allesGut.copy(benachrichtigungenErlaubt = false, exakteAlarmeErlaubt = false)

        assertEquals(
            listOf(Hinweis.BENACHRICHTIGUNGEN_FEHLEN, Hinweis.EXAKTE_ALARME_FEHLEN),
            BerechtigungsHinweise.fuer(stand),
        )
    }

    @Test
    fun `aktive Akkuoptimierung wird gemeldet`() {
        assertEquals(
            listOf(Hinweis.AKKUOPTIMIERUNG_AKTIV),
            BerechtigungsHinweise.fuer(allesGut.copy(akkuOptimierungAktiv = true)),
        )
    }

    @Test
    fun `alle drei Probleme werden nach Dringlichkeit sortiert gemeldet`() {
        val stand = BerechtigungsStand(false, false, true)

        assertEquals(
            listOf(
                Hinweis.BENACHRICHTIGUNGEN_FEHLEN,
                Hinweis.EXAKTE_ALARME_FEHLEN,
                Hinweis.AKKUOPTIMIERUNG_AKTIV,
            ),
            BerechtigungsHinweise.fuer(stand),
        )
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BerechtigungsHinweiseTest*"`
Expected: Kompilierfehler `Unresolved reference: BerechtigungsHinweise`.

- [ ] **Step 3: Implementieren**

`platform/permission/BerechtigungsStand.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.permission

data class BerechtigungsStand(
    val benachrichtigungenErlaubt: Boolean,
    val exakteAlarmeErlaubt: Boolean,
    val akkuOptimierungAktiv: Boolean,
)

enum class Hinweis {
    /** Ohne diese Berechtigung sieht der Nutzer gar nichts. */
    BENACHRICHTIGUNGEN_FEHLEN,

    /** Erinnerungen kommen dann verspaetet. */
    EXAKTE_ALARME_FEHLEN,

    /** Hersteller-Akkuoptimierung beendet Hintergrundarbeit. */
    AKKUOPTIMIERUNG_AKTIV,
}

object BerechtigungsHinweise {
    fun fuer(stand: BerechtigungsStand): List<Hinweis> = buildList {
        if (!stand.benachrichtigungenErlaubt) add(Hinweis.BENACHRICHTIGUNGEN_FEHLEN)
        if (!stand.exakteAlarmeErlaubt) add(Hinweis.EXAKTE_ALARME_FEHLEN)
        if (stand.akkuOptimierungAktiv) add(Hinweis.AKKUOPTIMIERUNG_AKTIV)
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*BerechtigungsHinweiseTest*"`
Expected: `BUILD SUCCESSFUL`, 4 Tests bestanden.

- [ ] **Step 5: Prüfer und Banner anlegen**

`platform/permission/BerechtigungsPruefer.kt`:

```kotlin
package de.vanhake.dayorganizer.platform.permission

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BerechtigungsPruefer @Inject constructor(
    @ApplicationContext private val kontext: Context,
) {
    fun stand(): BerechtigungsStand = BerechtigungsStand(
        benachrichtigungenErlaubt = ContextCompat.checkSelfPermission(
            kontext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED,
        exakteAlarmeErlaubt = kontext.getSystemService<AlarmManager>()
            ?.canScheduleExactAlarms() ?: false,
        // Regel fuer alle drei: laesst sich eine Pruefung nicht durchfuehren,
        // wird gewarnt statt geschwiegen. Eine ausbleibende Erinnerung wiegt
        // schwerer als ein Banner, das der Nutzer wegtippt.
        akkuOptimierungAktiv = kontext.getSystemService<PowerManager>()
            ?.isIgnoringBatteryOptimizations(kontext.packageName)?.not() ?: true,
    )
}

fun Hinweis.einstellungsIntent(kontext: Context): Intent = when (this) {
    Hinweis.BENACHRICHTIGUNGEN_FEHLEN -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, kontext.packageName)

    Hinweis.EXAKTE_ALARME_FEHLEN -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        .setData(Uri.fromParts("package", kontext.packageName, null))

    Hinweis.AKKUOPTIMIERUNG_AKTIV -> Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
}
```

`ui/gemeinsam/WarnBanner.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.gemeinsam

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.platform.permission.Hinweis

@Composable
fun WarnBanner(hinweis: Hinweis, beheben: () -> Unit, modifier: Modifier = Modifier) {
    val (titel, text) = when (hinweis) {
        Hinweis.BENACHRICHTIGUNGEN_FEHLEN ->
            R.string.warnung_benachrichtigungen_titel to R.string.warnung_benachrichtigungen_text
        Hinweis.EXAKTE_ALARME_FEHLEN ->
            R.string.warnung_exakte_alarme_titel to R.string.warnung_exakte_alarme_text
        Hinweis.AKKUOPTIMIERUNG_AKTIV ->
            R.string.warnung_akku_titel to R.string.warnung_akku_text
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(titel), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
            Row {
                TextButton(onClick = beheben) { Text(stringResource(R.string.warnung_beheben)) }
            }
        }
    }
}
```

In `strings.xml` ergänzen:

```xml
    <string name="warnung_benachrichtigungen_titel">Benachrichtigungen sind aus</string>
    <string name="warnung_benachrichtigungen_text">Ohne Benachrichtigungen erinnert dich die App an nichts.</string>
    <string name="warnung_exakte_alarme_titel">Exakte Alarme sind nicht erlaubt</string>
    <string name="warnung_exakte_alarme_text">Erinnerungen können dadurch deutlich verspätet kommen.</string>
    <string name="warnung_akku_titel">Akkuoptimierung ist aktiv</string>
    <string name="warnung_akku_text">Das System kann Erinnerungen unterdrücken. Nimm DayOrganizer von der Optimierung aus.</string>
    <string name="warnung_beheben">Einstellung öffnen</string>
```

- [ ] **Step 6: Build und Tests laufen lassen**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/
git commit -m "feat(ui): Berechtigungspruefung und Warnbanner"
```

---

### Task 18: Heute-Screen

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteZustand.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteZusammenstellung.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteViewModel.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteScreen.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/theme/Thema.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteZusammenstellungTest.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/heute/HeuteViewModelTest.kt`

**Interfaces:**
- Consumes: `FaelligkeitRepository.beobachteTag`, `AktivitaetRepository.beobachteAktive`, `BuchungRepository.beobachteKontostand` aus Task 10, `FaelligkeitErledigen` und `FaelligkeitAuslassen` aus Task 12, `BerechtigungsPruefer`, `BerechtigungsHinweise` und `WarnBanner` aus Task 17
- Produces:
  - `HeuteEintrag(faelligkeitId: Long, aktivitaetName: String, farbe: Int, zeitText: String?, sollAnzahl: Int, istAnzahl: Int, status: FaelligkeitStatus, ueberfaellig: Boolean)`
  - `HeuteZustand(kontostand: Int, erledigtHeute: Int, gesamtHeute: Int, ueberfaellig: List<HeuteEintrag>, anstehend: List<HeuteEintrag>, spaeter: List<HeuteEintrag>, abgeschlossen: List<HeuteEintrag>)`
  - `HeuteZusammenstellung.aus(faelligkeiten, aktivitaeten, kontostand, jetzt): HeuteZustand` — reine Funktion
  - `HeuteViewModel.zustand: StateFlow<HeuteZustand>`, `.erledigen(id)`, `.auslassen(id)`, `.beimSichtbarwerden()`

Der beobachtete Tag ist ein `MutableStateFlow<LocalDate>`, aus dem per
`flatMapLatest` die Fälligkeiten kommen — nicht ein einmal gelesenes Datum.
Ein Prozess, der über Mitternacht überlebt, zeigte sonst weiter den gestrigen
Tag, und ein Screen namens *Heute* darf das nicht. `beimSichtbarwerden()` liest
Tag **und** Berechtigungen neu; zwei getrennte Methoden würden dazu einladen,
nur eine davon aufzurufen.

Die Einteilung: *überfällig* sind offene Fälligkeiten mit Sollzeitpunkt in der Vergangenheit. *Anstehend* sind offene mit Sollzeitpunkt innerhalb der nächsten zwei Stunden sowie alle Zählziele ohne Sollzeitpunkt. *Später* ist der Rest der offenen. *Abgeschlossen* sind erledigte, ausgelassene und verpasste.

- [ ] **Step 1: Fehlschlagenden Test für die Zusammenstellung schreiben**

```kotlin
package de.vanhake.dayorganizer.ui.heute

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

class HeuteZusammenstellungTest {

    private val tag = LocalDate.of(2026, 3, 2)
    private val jetzt = LocalDateTime.of(2026, 3, 2, 12, 0)

    private val tabletten = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 11, basisPunkte = 10,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )
    private val sport = Aktivitaet(
        id = 2, name = "Sport", farbe = 22, basisPunkte = 20,
        zeitplan = Zeitplan.Wochenziel(3),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )

    private fun termin(id: Long, stunde: Int, status: FaelligkeitStatus) = Faelligkeit(
        id = id, aktivitaetId = 1, periodeVon = tag, periodeBis = tag,
        geplantFuer = tag.atTime(stunde, 0), sollAnzahl = 1, status = status,
    )

    @Test
    fun `Eintraege werden nach Dringlichkeit einsortiert`() {
        val ziel = Faelligkeit(
            id = 30, aktivitaetId = 2, periodeVon = tag, periodeBis = tag.plusDays(6),
            geplantFuer = null, sollAnzahl = 3, istAnzahl = 1,
        )

        val zustand = HeuteZusammenstellung.aus(
            faelligkeiten = listOf(
                termin(10, 8, FaelligkeitStatus.OFFEN),
                termin(11, 13, FaelligkeitStatus.OFFEN),
                termin(12, 20, FaelligkeitStatus.OFFEN),
                termin(13, 7, FaelligkeitStatus.ERLEDIGT),
                ziel,
            ),
            aktivitaeten = listOf(tabletten, sport),
            kontostand = 120,
            jetzt = jetzt,
        )

        assertEquals(listOf(10L), zustand.ueberfaellig.map { it.faelligkeitId })
        assertEquals(listOf(11L, 30L), zustand.anstehend.map { it.faelligkeitId })
        assertEquals(listOf(12L), zustand.spaeter.map { it.faelligkeitId })
        assertEquals(listOf(13L), zustand.abgeschlossen.map { it.faelligkeitId })
        assertEquals(120, zustand.kontostand)
    }

    @Test
    fun `der Tagesfortschritt zaehlt nur erledigte`() {
        val zustand = HeuteZusammenstellung.aus(
            faelligkeiten = listOf(
                termin(10, 8, FaelligkeitStatus.ERLEDIGT),
                termin(11, 13, FaelligkeitStatus.AUSGELASSEN),
                termin(12, 20, FaelligkeitStatus.OFFEN),
            ),
            aktivitaeten = listOf(tabletten),
            kontostand = 0,
            jetzt = jetzt,
        )

        assertEquals(1, zustand.erledigtHeute)
        assertEquals(3, zustand.gesamtHeute)
    }

    @Test
    fun `Zaehlziele zeigen Uhrzeit nicht sondern ihren Fortschritt`() {
        val ziel = Faelligkeit(
            id = 30, aktivitaetId = 2, periodeVon = tag, periodeBis = tag.plusDays(6),
            geplantFuer = null, sollAnzahl = 3, istAnzahl = 2,
        )

        val zustand = HeuteZusammenstellung.aus(listOf(ziel), listOf(sport), 0, jetzt)

        val eintrag = zustand.anstehend.single()
        assertEquals(null, eintrag.zeitText)
        assertEquals(2, eintrag.istAnzahl)
        assertEquals(3, eintrag.sollAnzahl)
        assertEquals("Sport", eintrag.aktivitaetName)
    }

    @Test
    fun `Faelligkeiten ohne passende Aktivitaet werden uebersprungen`() {
        val zustand = HeuteZusammenstellung.aus(
            faelligkeiten = listOf(termin(10, 8, FaelligkeitStatus.OFFEN)),
            aktivitaeten = emptyList(),
            kontostand = 0,
            jetzt = jetzt,
        )

        assertEquals(0, zustand.gesamtHeute)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*HeuteZusammenstellungTest*"`
Expected: Kompilierfehler `Unresolved reference: HeuteZusammenstellung`.

- [ ] **Step 3: Zustand und Zusammenstellung implementieren**

`ui/heute/HeuteZustand.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.heute

import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.platform.permission.Hinweis

data class HeuteEintrag(
    val faelligkeitId: Long,
    val aktivitaetName: String,
    val farbe: Int,
    /** "08:00" bei Terminen, null bei Zaehlzielen. */
    val zeitText: String?,
    val sollAnzahl: Int,
    val istAnzahl: Int,
    val status: FaelligkeitStatus,
    val ueberfaellig: Boolean,
)

data class HeuteZustand(
    val kontostand: Int = 0,
    val erledigtHeute: Int = 0,
    val gesamtHeute: Int = 0,
    val ueberfaellig: List<HeuteEintrag> = emptyList(),
    val anstehend: List<HeuteEintrag> = emptyList(),
    val spaeter: List<HeuteEintrag> = emptyList(),
    val abgeschlossen: List<HeuteEintrag> = emptyList(),
    val hinweise: List<Hinweis> = emptyList(),
)
```

`ui/heute/HeuteZusammenstellung.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.heute

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Reine Umformung von Rohdaten in den Bildschirmzustand. */
object HeuteZusammenstellung {

    private val UHRZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private const val ANSTEHEND_STUNDEN = 2L

    fun aus(
        faelligkeiten: List<Faelligkeit>,
        aktivitaeten: List<Aktivitaet>,
        kontostand: Int,
        jetzt: LocalDateTime,
    ): HeuteZustand {
        val nachId = aktivitaeten.associateBy { it.id }
        val eintraege = faelligkeiten.mapNotNull { f ->
            val aktivitaet = nachId[f.aktivitaetId] ?: return@mapNotNull null
            f to HeuteEintrag(
                faelligkeitId = f.id,
                aktivitaetName = aktivitaet.name,
                farbe = aktivitaet.farbe,
                zeitText = f.geplantFuer?.format(UHRZEIT),
                sollAnzahl = f.sollAnzahl,
                istAnzahl = f.istAnzahl,
                status = f.status,
                ueberfaellig = f.status == FaelligkeitStatus.OFFEN &&
                    f.geplantFuer != null && f.geplantFuer!!.isBefore(jetzt),
            )
        }

        val offen = eintraege.filter { it.first.status == FaelligkeitStatus.OFFEN }
        val grenze = jetzt.plusHours(ANSTEHEND_STUNDEN)

        return HeuteZustand(
            kontostand = kontostand,
            erledigtHeute = eintraege.count { it.first.status == FaelligkeitStatus.ERLEDIGT },
            gesamtHeute = eintraege.size,
            ueberfaellig = offen.filter { it.second.ueberfaellig }.map { it.second },
            anstehend = offen
                .filter { (f, e) ->
                    !e.ueberfaellig && (f.geplantFuer == null || f.geplantFuer!!.isBefore(grenze))
                }
                .map { it.second },
            spaeter = offen
                .filter { (f, e) ->
                    !e.ueberfaellig && f.geplantFuer != null && !f.geplantFuer!!.isBefore(grenze)
                }
                .map { it.second },
            abgeschlossen = eintraege
                .filter { it.first.status != FaelligkeitStatus.OFFEN }
                .map { it.second },
        )
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*HeuteZusammenstellungTest*"`
Expected: `BUILD SUCCESSFUL`, 4 Tests bestanden.

- [ ] **Step 5: Fehlschlagenden ViewModel-Test schreiben**

```kotlin
package de.vanhake.dayorganizer.ui.heute

import app.cash.turbine.test
import de.vanhake.dayorganizer.FakeAktivitaetRepository
import de.vanhake.dayorganizer.FakeBuchungRepository
import de.vanhake.dayorganizer.FakeErinnerungRepository
import de.vanhake.dayorganizer.FakeFaelligkeitRepository
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domain.rechner.PunkteRechner
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassen
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigen
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

class HeuteViewModelTest {

    private val zone = ZoneId.of("Europe/Berlin")
    private val uhr = Clock.fixed(
        LocalDateTime.of(2026, 3, 2, 12, 0).atZone(zone).toInstant(),
        zone,
    )
    private val dispatcher = StandardTestDispatcher()

    @Before fun vorher() = Dispatchers.setMain(dispatcher)
    @After fun nachher() = Dispatchers.resetMain()

    private val tabletten = Aktivitaet(
        id = 1, name = "Tabletten", farbe = 1, basisPunkte = 10, puenktlichkeitsBonus = 0,
        zeitplan = Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
        erstelltAm = LocalDate.of(2026, 1, 1),
    )
    private val morgens = Faelligkeit(
        id = 10, aktivitaetId = 1,
        periodeVon = LocalDate.of(2026, 3, 2), periodeBis = LocalDate.of(2026, 3, 2),
        geplantFuer = LocalDateTime.of(2026, 3, 2, 8, 0), sollAnzahl = 1,
    )

    private fun viewModel(
        faelligkeiten: FakeFaelligkeitRepository,
        buchungen: FakeBuchungRepository,
    ): HeuteViewModel {
        val aktivitaeten = FakeAktivitaetRepository(listOf(tabletten))
        return HeuteViewModel(
            aktivitaeten = aktivitaeten,
            faelligkeiten = faelligkeiten,
            buchungen = buchungen,
            erledigenUseCase = FaelligkeitErledigen(
                aktivitaeten, faelligkeiten, buchungen, PunkteRechner(), SerienRechner(),
                { Einstellungen(tagesbonusPunkte = 0) }, uhr,
            ),
            auslassenUseCase = FaelligkeitAuslassen(faelligkeiten, uhr),
            alarmNeuPlanen = {},
            hinweiseLesen = { emptyList() },
            uhr = uhr,
        )
    }

    @Test
    fun `der Zustand zeigt die heutigen Faelligkeiten`() = runTest(dispatcher) {
        val vm = viewModel(FakeFaelligkeitRepository(listOf(morgens)), FakeBuchungRepository())

        vm.zustand.test {
            assertEquals(HeuteZustand(), awaitItem())
            val gefuellt = awaitItem()
            assertEquals(1, gefuellt.gesamtHeute)
            assertEquals(listOf(10L), gefuellt.ueberfaellig.map { it.faelligkeitId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Erledigen aktualisiert Fortschritt und Kontostand`() = runTest(dispatcher) {
        val faelligkeiten = FakeFaelligkeitRepository(listOf(morgens))
        val buchungen = FakeBuchungRepository()
        val vm = viewModel(faelligkeiten, buchungen)

        vm.zustand.test {
            skipItems(2)
            vm.erledigen(10)
            val danach = awaitItem()
            assertEquals(1, danach.erledigtHeute)
            assertEquals(10, danach.kontostand)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

- [ ] **Step 6: ViewModel implementieren**

`ui/heute/HeuteViewModel.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.heute

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domainuse.FaelligkeitAuslassen
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigen
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

@HiltViewModel
class HeuteViewModel(
    private val aktivitaeten: AktivitaetRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val erledigenUseCase: FaelligkeitErledigen,
    private val auslassenUseCase: FaelligkeitAuslassen,
    private val alarmNeuPlanen: suspend () -> Unit,
    private val hinweiseLesen: () -> List<Hinweis>,
    private val uhr: Clock,
) : ViewModel() {

    @Inject
    constructor(
        aktivitaeten: AktivitaetRepository,
        faelligkeiten: FaelligkeitRepository,
        buchungen: BuchungRepository,
        erledigenUseCase: FaelligkeitErledigen,
        auslassenUseCase: FaelligkeitAuslassen,
        alarmPlaner: AlarmPlaner,
        pruefer: BerechtigungsPruefer,
        uhr: Clock,
    ) : this(
        aktivitaeten, faelligkeiten, buchungen, erledigenUseCase, auslassenUseCase,
        { alarmPlaner.neuPlanen() }, { BerechtigungsHinweise.fuer(pruefer.stand()) }, uhr,
    )

    private val hinweise = MutableStateFlow<List<Hinweis>>(emptyList())
    private val tag = MutableStateFlow(LocalDate.now(uhr))

    @OptIn(ExperimentalCoroutinesApi::class)
    val zustand: StateFlow<HeuteZustand> = combine(
        tag.flatMapLatest { faelligkeiten.beobachteTag(it) },
        aktivitaeten.beobachteAktive(),
        buchungen.beobachteKontostand(),
        hinweise,
    ) { faelligkeiten, aktivitaeten, kontostand, hinweise ->
        HeuteZusammenstellung.aus(faelligkeiten, aktivitaeten, kontostand, LocalDateTime.now(uhr))
            .copy(hinweise = hinweise)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HeuteZustand())

    /**
     * Bei jedem Sichtbarwerden des Screens, nicht nur einmal. Zwei Gruende:
     * der Nutzer kommt aus den Systemeinstellungen zurueck und der Banner muss
     * verschwinden, und ein Prozess kann ueber Mitternacht ueberleben, dann
     * zeigt "Heute" sonst weiter gestern.
     */
    fun beimSichtbarwerden() {
        tag.value = LocalDate.now(uhr)
        hinweise.value = hinweiseLesen()
    }

    // Property und Methode heissen bewusst unterschiedlich: gleiche Namen
    // kompilieren zwar, lesen sich aber wie ein Selbstaufruf.
    fun erledigen(faelligkeitId: Long) = viewModelScope.launch {
        erledigenUseCase(faelligkeitId)
        alarmNeuPlanen()
    }

    fun auslassen(faelligkeitId: Long) = viewModelScope.launch {
        auslassenUseCase(faelligkeitId)
        alarmNeuPlanen()
    }
}
```

- [ ] **Step 7: Test laufen lassen und grünen Lauf bestätigen**

Ein dritter Test gehört dazu: `nach Mitternacht zeigt der Screen den neuen Tag`.
Er braucht eine bewegliche Uhr — eine kleine `Clock`-Ableitung im Testfile mit
einem veränderlichen `instant` genügt. Zwei Fälligkeiten anlegen, eine für den
2026-03-02 und eine für den 2026-03-03, die Uhr auf den 02. um 23:59 stellen,
den Zustand einsammeln, dann die Uhr auf den 03. um 00:01 stellen,
`beimSichtbarwerden()` aufrufen und prüfen, dass jetzt die andere Fälligkeit
erscheint.

Run: `./gradlew :app:testDebugUnitTest --tests "*HeuteViewModelTest*"`
Expected: `BUILD SUCCESSFUL`, 3 Tests bestanden.

- [ ] **Step 8: Oberfläche anlegen**

`ui/theme/Thema.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun DayOrganizerThema(dunkel: Boolean = isSystemInDarkTheme(), inhalt: @Composable () -> Unit) {
    val kontext = LocalContext.current
    val farben = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dunkel -> dynamicDarkColorScheme(kontext)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(kontext)
        dunkel -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = farben, content = inhalt)
}
```

`ui/heute/HeuteScreen.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.heute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.platform.permission.Hinweis
import de.vanhake.dayorganizer.platform.permission.einstellungsIntent
import de.vanhake.dayorganizer.ui.gemeinsam.WarnBanner

@Composable
fun HeuteScreen(viewModel: HeuteViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsStateWithLifecycle()
    val kontext = LocalContext.current

    // Bei jedem Sichtbarwerden neu pruefen: der Nutzer kommt aus den
    // Systemeinstellungen zurueck, und dann muss der Banner verschwinden.
    LifecycleResumeEffect(Unit) {
        viewModel.beimSichtbarwerden()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(zustand.hinweise, key = { it.name }) { hinweis ->
            WarnBanner(
                hinweis = hinweis,
                beheben = {
                    ContextCompat.startActivity(kontext, hinweis.einstellungsIntent(kontext), null)
                },
            )
        }

        item { Kopf(zustand) }

        abschnitt(R.string.abschnitt_ueberfaellig, zustand.ueberfaellig, viewModel)
        abschnitt(R.string.abschnitt_anstehend, zustand.anstehend, viewModel)
        abschnitt(R.string.abschnitt_spaeter, zustand.spaeter, viewModel)
        abschnitt(R.string.abschnitt_abgeschlossen, zustand.abgeschlossen, viewModel)
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.abschnitt(
    titelId: Int,
    eintraege: List<HeuteEintrag>,
    viewModel: HeuteViewModel,
) {
    if (eintraege.isEmpty()) return
    item {
        Text(stringResource(titelId), style = MaterialTheme.typography.titleSmall)
    }
    items(eintraege, key = { it.faelligkeitId }) { eintrag ->
        EintragZeile(
            eintrag = eintrag,
            erledigen = { viewModel.erledigen(eintrag.faelligkeitId) },
            auslassen = { viewModel.auslassen(eintrag.faelligkeitId) },
        )
    }
    item { HorizontalDivider() }
}

@Composable
private fun Kopf(zustand: HeuteZustand) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.punktestand, zustand.kontostand),
                style = MaterialTheme.typography.headlineSmall,
            )
            // Ring statt Balken: das Design sagt einen Fortschrittsring zu,
            // und der Anteil wird geklemmt, weil Material3 0..1 erwartet.
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    progress = {
                        val anteil = if (zustand.gesamtHeute == 0) 0f
                        else zustand.erledigtHeute.toFloat() / zustand.gesamtHeute
                        anteil.coerceIn(0f, 1f)
                    },
                    modifier = Modifier.padding(end = 16.dp),
                )
                Text(
                    stringResource(
                        R.string.tagesfortschritt,
                        zustand.erledigtHeute,
                        zustand.gesamtHeute,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

@Composable
private fun EintragZeile(eintrag: HeuteEintrag, erledigen: () -> Unit, auslassen: () -> Unit) {
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(eintrag.aktivitaetName, style = MaterialTheme.typography.titleMedium)
                val untertitel = eintrag.zeitText
                    ?: stringResource(R.string.ziel_fortschritt, eintrag.istAnzahl, eintrag.sollAnzahl)
                Text(untertitel, style = MaterialTheme.typography.bodySmall)
            }
            if (eintrag.status == de.vanhake.dayorganizer.domain.model.FaelligkeitStatus.OFFEN) {
                TextButton(onClick = auslassen) { Text(stringResource(R.string.aktion_ausgelassen)) }
                TextButton(onClick = erledigen) { Text(stringResource(R.string.aktion_erledigt)) }
            }
        }
    }
}
```

`MainActivity.kt` ersetzen:

```kotlin
package de.vanhake.dayorganizer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import de.vanhake.dayorganizer.ui.heute.HeuteScreen
import de.vanhake.dayorganizer.ui.theme.DayOrganizerThema

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            DayOrganizerThema {
                Surface { HeuteScreen() }
            }
        }
    }
}
```

In `strings.xml` ergänzen:

```xml
    <string name="punktestand">%1$d Punkte</string>
    <string name="tagesfortschritt">Heute %1$d von %2$d erledigt</string>
    <string name="ziel_fortschritt">%1$d von %2$d</string>
    <string name="abschnitt_ueberfaellig">Überfällig</string>
    <string name="abschnitt_anstehend">Jetzt dran</string>
    <string name="abschnitt_spaeter">Später heute</string>
    <string name="abschnitt_abgeschlossen">Erledigt</string>
```

- [ ] **Step 9: Build und alle Tests laufen lassen**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :domain:test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 10: Commit**

```bash
git add app/
git commit -m "feat(ui): Heute-Screen mit Tagesfortschritt und Abhaken"
```

---

### Task 19: Aktivitäten anlegen und bearbeiten

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetEntwurf.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetBearbeitenViewModel.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenViewModel.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetenScreen.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetBearbeitenScreen.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/Navigation.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/aktivitaeten/AktivitaetEntwurfTest.kt`

**Interfaces:**
- Consumes: `AktivitaetRepository` aus Task 10, `FaelligkeitenMaterialisieren` und `AlarmPlaner` aus Task 14
- Produces:
  - `AktivitaetEntwurf` — bearbeitbarer Zwischenstand mit Textfeldern statt fertigen Typen
  - `enum EntwurfFehler { NAME_LEER, PUNKTE_NICHT_POSITIV, KEINE_ZEIT, KEIN_WOCHENTAG, ZIEL_NICHT_POSITIV, INTERVALL_UNGUELTIG }`
  - `AktivitaetEntwurf.pruefen(): List<EntwurfFehler>`
  - `AktivitaetEntwurf.zuAktivitaet(erstelltAm: LocalDate): Aktivitaet` — nur aufrufen, wenn `pruefen()` leer ist
  - `AktivitaetenViewModel.aktivitaeten: StateFlow<List<Aktivitaet>>`, `.archivieren(id)`
  - `AktivitaetBearbeitenViewModel.entwurf: StateFlow<AktivitaetEntwurf>`, `.laden(id)`, `.speichern(): Boolean`

Nach jedem Speichern werden Fälligkeiten neu materialisiert und der Alarm neu gesetzt — sonst wirkt eine neue Aktivität erst am nächsten Morgen.

- [ ] **Step 1: Fehlschlagenden Test schreiben**

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AktivitaetEntwurfTest {

    private val gueltig = AktivitaetEntwurf(
        name = "Tabletten",
        basisPunkteText = "10",
        typ = ZeitplanTyp.FESTE_ZEITEN,
        zeiten = listOf(LocalTime.of(8, 0)),
        wochentage = DayOfWeek.entries.toSet(),
    )

    @Test
    fun `ein vollstaendiger Entwurf hat keine Fehler`() {
        assertEquals(emptyList(), gueltig.pruefen())
    }

    @Test
    fun `leerer Name und nicht positive Punkte werden gemeldet`() {
        val fehler = gueltig.copy(name = "  ", basisPunkteText = "0").pruefen()

        assertTrue(EntwurfFehler.NAME_LEER in fehler)
        assertTrue(EntwurfFehler.PUNKTE_NICHT_POSITIV in fehler)
    }

    @Test
    fun `feste Zeiten brauchen mindestens eine Uhrzeit und einen Wochentag`() {
        val fehler = gueltig.copy(zeiten = emptyList(), wochentage = emptySet()).pruefen()

        assertTrue(EntwurfFehler.KEINE_ZEIT in fehler)
        assertTrue(EntwurfFehler.KEIN_WOCHENTAG in fehler)
    }

    @Test
    fun `ein Wochenziel braucht keine Wochentage aber eine positive Anzahl`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.WOCHENZIEL,
            zeiten = emptyList(),
            wochentage = emptySet(),
            zielAnzahlText = "0",
        )

        assertEquals(listOf(EntwurfFehler.ZIEL_NICHT_POSITIV), entwurf.pruefen())
    }

    @Test
    fun `ein Intervall braucht ein positives Fenster`() {
        val entwurf = gueltig.copy(
            typ = ZeitplanTyp.INTERVALL,
            zeiten = emptyList(),
            intervallStundenText = "0",
            fensterVon = LocalTime.of(20, 0),
            fensterBis = LocalTime.of(8, 0),
        )

        assertTrue(EntwurfFehler.INTERVALL_UNGUELTIG in entwurf.pruefen())
    }

    @Test
    fun `der Entwurf wird in den richtigen Zeitplan umgesetzt`() {
        val aktivitaet = gueltig.zuAktivitaet(LocalDate.of(2026, 3, 2))

        assertEquals("Tabletten", aktivitaet.name)
        assertEquals(10, aktivitaet.basisPunkte)
        assertEquals(
            Zeitplan.FesteZeiten(listOf(LocalTime.of(8, 0)), DayOfWeek.entries.toSet()),
            aktivitaet.zeitplan,
        )
    }

    @Test
    fun `ein Wochenziel wird in den Wochenziel-Zeitplan umgesetzt`() {
        val entwurf = gueltig.copy(typ = ZeitplanTyp.WOCHENZIEL, zielAnzahlText = "3")

        assertEquals(Zeitplan.Wochenziel(3), entwurf.zuAktivitaet(LocalDate.of(2026, 3, 2)).zeitplan)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*AktivitaetEntwurfTest*"`
Expected: Kompilierfehler `Unresolved reference: AktivitaetEntwurf`.

- [ ] **Step 3: Entwurf implementieren**

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class ZeitplanTyp { FESTE_ZEITEN, INTERVALL, TAGESZIEL, WOCHENZIEL }

enum class EntwurfFehler {
    NAME_LEER,
    PUNKTE_NICHT_POSITIV,
    KEINE_ZEIT,
    KEIN_WOCHENTAG,
    ZIEL_NICHT_POSITIV,
    INTERVALL_UNGUELTIG,
}

/**
 * Bearbeitbarer Zwischenstand. Zahlen bleiben Text, damit ein halb getipptes
 * Feld die Eingabe nicht zurueckspringen laesst.
 */
data class AktivitaetEntwurf(
    val id: Long = 0,
    val name: String = "",
    val notiz: String = "",
    val farbe: Int = 0xFF4CAF50.toInt(),
    val basisPunkteText: String = "10",
    val typ: ZeitplanTyp = ZeitplanTyp.FESTE_ZEITEN,
    val zeiten: List<LocalTime> = emptyList(),
    val wochentage: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val intervallStundenText: String = "3",
    val fensterVon: LocalTime = LocalTime.of(8, 0),
    val fensterBis: LocalTime = LocalTime.of(22, 0),
    val zielAnzahlText: String = "3",
    val anstoss: List<Anstoss> = emptyList(),
    val kulanzMinutenText: String = "60",
    val puenktlichkeitsBonusText: String = "5",
    val serienSchwellen: List<SerienSchwelle> = listOf(SerienSchwelle(7, 10)),
    val nachfassAnzahlText: String = "2",
    val nachfassAbstandText: String = "15",
) {

    private val basisPunkte: Int get() = basisPunkteText.toIntOrNull() ?: 0
    private val intervallStunden: Int get() = intervallStundenText.toIntOrNull() ?: 0
    private val zielAnzahl: Int get() = zielAnzahlText.toIntOrNull() ?: 0

    fun pruefen(): List<EntwurfFehler> = buildList {
        if (name.isBlank()) add(EntwurfFehler.NAME_LEER)
        if (basisPunkte <= 0) add(EntwurfFehler.PUNKTE_NICHT_POSITIV)

        when (typ) {
            ZeitplanTyp.FESTE_ZEITEN -> {
                if (zeiten.isEmpty()) add(EntwurfFehler.KEINE_ZEIT)
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.INTERVALL -> {
                if (intervallStunden <= 0 || !fensterVon.isBefore(fensterBis)) {
                    add(EntwurfFehler.INTERVALL_UNGUELTIG)
                }
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.TAGESZIEL -> {
                if (zielAnzahl <= 0) add(EntwurfFehler.ZIEL_NICHT_POSITIV)
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.WOCHENZIEL -> {
                if (zielAnzahl <= 0) add(EntwurfFehler.ZIEL_NICHT_POSITIV)
            }
        }
    }

    /** Nur aufrufen, wenn [pruefen] leer ist. */
    fun zuAktivitaet(erstelltAm: LocalDate): Aktivitaet = Aktivitaet(
        id = id,
        name = name.trim(),
        notiz = notiz.trim(),
        farbe = farbe,
        basisPunkte = basisPunkte,
        zeitplan = when (typ) {
            ZeitplanTyp.FESTE_ZEITEN -> Zeitplan.FesteZeiten(zeiten.sorted(), wochentage)
            ZeitplanTyp.INTERVALL -> Zeitplan.Intervall(
                intervallStunden, fensterVon, fensterBis, wochentage,
            )
            ZeitplanTyp.TAGESZIEL -> Zeitplan.Tagesziel(zielAnzahl, wochentage, zeiten.sorted())
            ZeitplanTyp.WOCHENZIEL -> Zeitplan.Wochenziel(zielAnzahl, anstoss)
        },
        kulanzMinuten = kulanzMinutenText.toIntOrNull() ?: 60,
        puenktlichkeitsBonus = puenktlichkeitsBonusText.toIntOrNull() ?: 0,
        serienSchwellen = serienSchwellen,
        nachfassAnzahl = nachfassAnzahlText.toIntOrNull() ?: 0,
        nachfassAbstandMinuten = nachfassAbstandText.toIntOrNull() ?: 15,
        erstelltAm = erstelltAm,
    )

    companion object {
        fun aus(aktivitaet: Aktivitaet): AktivitaetEntwurf {
            val basis = AktivitaetEntwurf(
                id = aktivitaet.id,
                name = aktivitaet.name,
                notiz = aktivitaet.notiz,
                farbe = aktivitaet.farbe,
                basisPunkteText = aktivitaet.basisPunkte.toString(),
                kulanzMinutenText = aktivitaet.kulanzMinuten.toString(),
                puenktlichkeitsBonusText = aktivitaet.puenktlichkeitsBonus.toString(),
                serienSchwellen = aktivitaet.serienSchwellen,
                nachfassAnzahlText = aktivitaet.nachfassAnzahl.toString(),
                nachfassAbstandText = aktivitaet.nachfassAbstandMinuten.toString(),
            )
            return when (val plan = aktivitaet.zeitplan) {
                is Zeitplan.FesteZeiten -> basis.copy(
                    typ = ZeitplanTyp.FESTE_ZEITEN,
                    zeiten = plan.zeiten,
                    wochentage = plan.wochentage,
                )
                is Zeitplan.Intervall -> basis.copy(
                    typ = ZeitplanTyp.INTERVALL,
                    intervallStundenText = plan.alleNStunden.toString(),
                    fensterVon = plan.von,
                    fensterBis = plan.bis,
                    wochentage = plan.wochentage,
                )
                is Zeitplan.Tagesziel -> basis.copy(
                    typ = ZeitplanTyp.TAGESZIEL,
                    zielAnzahlText = plan.anzahl.toString(),
                    wochentage = plan.wochentage,
                    zeiten = plan.anstossZeiten,
                )
                is Zeitplan.Wochenziel -> basis.copy(
                    typ = ZeitplanTyp.WOCHENZIEL,
                    zielAnzahlText = plan.anzahl.toString(),
                    anstoss = plan.anstoss,
                )
            }
        }
    }
}
```

- [ ] **Step 4: Test laufen lassen und grünen Lauf bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*AktivitaetEntwurfTest*"`
Expected: `BUILD SUCCESSFUL`, 7 Tests bestanden.

- [ ] **Step 5: ViewModels anlegen**

`ui/aktivitaeten/AktivitaetenViewModel.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AktivitaetenViewModel @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val uhr: Clock,
) : ViewModel() {

    val liste: StateFlow<List<Aktivitaet>> = aktivitaeten.beobachteAktive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun archivieren(id: Long) = viewModelScope.launch {
        aktivitaeten.archivieren(id, LocalDate.now(uhr))
    }
}
```

`ui/aktivitaeten/AktivitaetBearbeitenViewModel.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.domainuse.FaelligkeitenMaterialisieren
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AktivitaetBearbeitenViewModel @Inject constructor(
    private val aktivitaeten: AktivitaetRepository,
    private val materialisieren: FaelligkeitenMaterialisieren,
    private val alarmPlaner: AlarmPlaner,
    private val uhr: Clock,
) : ViewModel() {

    private val _entwurf = MutableStateFlow(AktivitaetEntwurf())
    val entwurf: StateFlow<AktivitaetEntwurf> = _entwurf.asStateFlow()

    private val _fehler = MutableStateFlow<List<EntwurfFehler>>(emptyList())
    val fehler: StateFlow<List<EntwurfFehler>> = _fehler.asStateFlow()

    fun laden(id: Long) = viewModelScope.launch {
        if (id == 0L) return@launch
        aktivitaeten.nachId(id)?.let { _entwurf.value = AktivitaetEntwurf.aus(it) }
    }

    fun aendern(block: (AktivitaetEntwurf) -> AktivitaetEntwurf) {
        _entwurf.value = block(_entwurf.value)
    }

    /** Speichert und plant neu. Gibt false zurueck, wenn der Entwurf Fehler hat. */
    suspend fun speichern(): Boolean {
        val fehler = _entwurf.value.pruefen()
        _fehler.value = fehler
        if (fehler.isNotEmpty()) return false

        aktivitaeten.speichern(_entwurf.value.zuAktivitaet(LocalDate.now(uhr)))
        // Ohne diese beiden Zeilen wirkt eine neue Aktivitaet erst nach dem
        // naechtlichen Waechterlauf.
        materialisieren()
        alarmPlaner.neuPlanen()
        return true
    }
}
```

- [ ] **Step 6: Screens und Navigation anlegen**

`ui/Navigation.kt`:

```kotlin
package de.vanhake.dayorganizer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetBearbeitenScreen
import de.vanhake.dayorganizer.ui.aktivitaeten.AktivitaetenScreen
import de.vanhake.dayorganizer.ui.heute.HeuteScreen

object Ziele {
    const val HEUTE = "heute"
    const val AKTIVITAETEN = "aktivitaeten"
    const val AKTIVITAET_BEARBEITEN = "aktivitaet/{id}"
    fun bearbeiten(id: Long) = "aktivitaet/$id"
}

@Composable
fun DayOrganizerNavigation() {
    val navController = rememberNavController()
    val eintrag by navController.currentBackStackEntryAsState()
    val aktuell = eintrag?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = aktuell == Ziele.HEUTE,
                    onClick = { navController.navigate(Ziele.HEUTE) },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_heute)) },
                )
                NavigationBarItem(
                    selected = aktuell == Ziele.AKTIVITAETEN,
                    onClick = { navController.navigate(Ziele.AKTIVITAETEN) },
                    icon = { Icon(Icons.Default.List, contentDescription = null) },
                    label = { Text(stringResource(R.string.tab_aktivitaeten)) },
                )
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
                    bearbeiten = { id -> navController.navigate(Ziele.bearbeiten(id)) },
                )
            }
            composable(Ziele.AKTIVITAET_BEARBEITEN) { eintrag ->
                AktivitaetBearbeitenScreen(
                    id = eintrag.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                    fertig = { navController.popBackStack() },
                )
            }
        }
    }
}
```

`ui/aktivitaeten/AktivitaetenScreen.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.vanhake.dayorganizer.R

@Composable
fun AktivitaetenScreen(
    bearbeiten: (Long) -> Unit,
    viewModel: AktivitaetenViewModel = hiltViewModel(),
) {
    val liste by viewModel.liste.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { bearbeiten(0L) }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.neue_aktivitaet))
            }
        },
    ) { innen ->
        LazyColumn(
            modifier = Modifier.padding(innen).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(liste, key = { it.id }) { aktivitaet ->
                Card(Modifier.fillMaxWidth().clickable { bearbeiten(aktivitaet.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(aktivitaet.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.punkte_je_erledigung, aktivitaet.basisPunkte),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { viewModel.archivieren(aktivitaet.id) }) {
                            Text(stringResource(R.string.archivieren))
                        }
                    }
                }
            }
        }
    }
}
```

`ui/aktivitaeten/AktivitaetBearbeitenScreen.kt`:

```kotlin
package de.vanhake.dayorganizer.ui.aktivitaeten

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
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun AktivitaetBearbeitenScreen(
    id: Long,
    fertig: () -> Unit,
    viewModel: AktivitaetBearbeitenViewModel = hiltViewModel(),
) {
    val entwurf by viewModel.entwurf.collectAsStateWithLifecycle()
    val fehler by viewModel.fehler.collectAsStateWithLifecycle()
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
            isError = EntwurfFehler.NAME_LEER in fehler,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = entwurf.basisPunkteText,
            onValueChange = { neu -> viewModel.aendern { it.copy(basisPunkteText = neu) } },
            label = { Text(stringResource(R.string.feld_punkte)) },
            isError = EntwurfFehler.PUNKTE_NICHT_POSITIV in fehler,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.feld_zeitplan), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ZeitplanTyp.entries.forEach { typ ->
                FilterChip(
                    selected = entwurf.typ == typ,
                    onClick = { viewModel.aendern { it.copy(typ = typ) } },
                    label = { Text(beschriftung(typ)) },
                )
            }
        }

        if (entwurf.typ != ZeitplanTyp.WOCHENZIEL) {
            Text(stringResource(R.string.feld_wochentage), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DayOfWeek.entries.forEach { tag ->
                    FilterChip(
                        selected = tag in entwurf.wochentage,
                        onClick = {
                            viewModel.aendern {
                                it.copy(
                                    wochentage = if (tag in it.wochentage) {
                                        it.wochentage - tag
                                    } else {
                                        it.wochentage + tag
                                    },
                                )
                            }
                        },
                        label = { Text(tag.getDisplayName(TextStyle.SHORT, Locale.GERMAN)) },
                    )
                }
            }
        }

        when (entwurf.typ) {
            ZeitplanTyp.FESTE_ZEITEN, ZeitplanTyp.TAGESZIEL -> UhrzeitenFeld(entwurf, viewModel)
            ZeitplanTyp.INTERVALL -> OutlinedTextField(
                value = entwurf.intervallStundenText,
                onValueChange = { neu -> viewModel.aendern { it.copy(intervallStundenText = neu) } },
                label = { Text(stringResource(R.string.feld_intervall)) },
                isError = EntwurfFehler.INTERVALL_UNGUELTIG in fehler,
                modifier = Modifier.fillMaxWidth(),
            )
            ZeitplanTyp.WOCHENZIEL -> Unit
        }

        if (entwurf.typ == ZeitplanTyp.TAGESZIEL || entwurf.typ == ZeitplanTyp.WOCHENZIEL) {
            OutlinedTextField(
                value = entwurf.zielAnzahlText,
                onValueChange = { neu -> viewModel.aendern { it.copy(zielAnzahlText = neu) } },
                label = { Text(stringResource(R.string.feld_zielanzahl)) },
                isError = EntwurfFehler.ZIEL_NICHT_POSITIV in fehler,
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

@Composable
private fun UhrzeitenFeld(entwurf: AktivitaetEntwurf, viewModel: AktivitaetBearbeitenViewModel) {
    // Eingabe als kommagetrennte Liste im Format HH:mm. Ein Zeitwaehler-Dialog
    // waere schoener, kostet aber Aufwand, den Version 1 nicht braucht.
    OutlinedTextField(
        value = entwurf.zeiten.joinToString(", ") { it.toString() },
        onValueChange = { neu ->
            val zeiten = neu.split(",").mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
            viewModel.aendern { it.copy(zeiten = zeiten) }
        },
        label = { Text(stringResource(R.string.feld_uhrzeiten)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun beschriftung(typ: ZeitplanTyp): String = stringResource(
    when (typ) {
        ZeitplanTyp.FESTE_ZEITEN -> R.string.typ_feste_zeiten
        ZeitplanTyp.INTERVALL -> R.string.typ_intervall
        ZeitplanTyp.TAGESZIEL -> R.string.typ_tagesziel
        ZeitplanTyp.WOCHENZIEL -> R.string.typ_wochenziel
    },
)
```

`MainActivity.kt` auf die Navigation umstellen:

```kotlin
        setContent {
            DayOrganizerThema {
                Surface { DayOrganizerNavigation() }
            }
        }
```

In `strings.xml` ergänzen:

```xml
    <string name="tab_heute">Heute</string>
    <string name="tab_aktivitaeten">Aktivitäten</string>
    <string name="neue_aktivitaet">Neue Aktivität</string>
    <string name="punkte_je_erledigung">%1$d Punkte je Erledigung</string>
    <string name="archivieren">Archivieren</string>
    <string name="feld_name">Name</string>
    <string name="feld_punkte">Punkte je Erledigung</string>
    <string name="feld_zeitplan">Zeitplan</string>
    <string name="feld_wochentage">Wochentage</string>
    <string name="feld_uhrzeiten">Uhrzeiten, kommagetrennt (z. B. 08:00, 20:00)</string>
    <string name="feld_intervall">Alle wie viele Stunden</string>
    <string name="feld_zielanzahl">Wie oft</string>
    <string name="typ_feste_zeiten">Feste Zeiten</string>
    <string name="typ_intervall">Intervall</string>
    <string name="typ_tagesziel">Mal pro Tag</string>
    <string name="typ_wochenziel">Mal pro Woche</string>
    <string name="speichern">Speichern</string>
</resources>
```

- [ ] **Step 7: Build und alle Tests laufen lassen**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest :domain:test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 8: Auf dem Gerät durchspielen**

Run: `./gradlew :app:installDebug`
Expected: `BUILD SUCCESSFUL`.

Manuell prüfen: Aktivität mit Sollzeitpunkt in zwei Minuten anlegen, App schließen, auf die Benachrichtigung warten, dort auf *Erledigt* tippen, App öffnen — der Punktestand ist gestiegen und der Eintrag steht unter *Erledigt*.

- [ ] **Step 9: Commit**

```bash
git add app/
git commit -m "feat(ui): Aktivitaeten anlegen, bearbeiten und archivieren"
```

---

## Abschluss von Plan 1

Nach Task 19 steht eine benutzbare App: Aktivitäten anlegen, zuverlässig erinnert werden, aus der Benachrichtigung abhaken, Punkte sammeln. Was noch fehlt, liegt in Plan 2 (Belohnungen, Statistik, Einstellungen) und Plan 3 (Widget, Backup).

Vor dem Übergang einmal vollständig prüfen:

```bash
./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug
```
