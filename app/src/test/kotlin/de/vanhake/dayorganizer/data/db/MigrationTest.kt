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

    private companion object {
        const val DB_NAME = "migrationstest.db"
    }
}
