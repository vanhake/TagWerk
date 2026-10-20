plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Versionsnummer je Build: in der CI ist es die fortlaufende Run-Nummer von
// GitHub Actions, lokal die Zahl der Commits. Beides steigt nur, darum laesst
// sich jede neuere APK ueber die vorige installieren. Lokale Builds tragen den
// Zusatz "-lokal", damit sie sich nicht mit einem CI-Build verwechseln lassen.
val ciBuildNummer = providers.environmentVariable("GITHUB_RUN_NUMBER").orNull?.toIntOrNull()
val buildNummer = ciBuildNummer ?: runCatching {
    providers.exec { commandLine("git", "rev-list", "--count", "HEAD") }
        .standardOutput.asText.get().trim().toInt()
}.getOrDefault(1)

// Ein Release-Build traegt die Version seines Tags, z. B. "v1.2.0" -> "1.2.0".
// Der Release-Workflow reicht sie herein, weil er nicht vom Tag selbst
// ausgeloest wird. Alle anderen Builds behalten das Schema 0.1.<Build-Nummer>.
val releaseVersion = providers.environmentVariable("TAGWERK_RELEASE_VERSION").orNull
    ?.removePrefix("v")

// Der Release-Schluessel kommt nur ueber Umgebungsvariablen herein, in der CI
// aus den GitHub-Secrets, und liegt nie im Repo. Fehlen sie, bleibt die
// Release-APK wie bisher unsigniert.
val releaseKeystore = providers.environmentVariable("TAGWERK_KEYSTORE_PATH").orNull
fun signierwert(name: String) = providers.environmentVariable(name).orNull
    ?: error("$name fehlt, obwohl TAGWERK_KEYSTORE_PATH gesetzt ist")

android {
    namespace = "de.vanhake.dayorganizer"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.vanhake.dayorganizer"
        minSdk = 33
        targetSdk = 35
        versionCode = buildNummer
        versionName = releaseVersion
            ?: ("0.1.$buildNummer" + if (ciBuildNummer == null) "-lokal" else "")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = signierwert("TAGWERK_KEYSTORE_PASSWORD")
                keyAlias = signierwert("TAGWERK_KEY_ALIAS")
                keyPassword = signierwert("TAGWERK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        compose = true
        buildConfig = true
    }

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

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.glance.appwidget)

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
