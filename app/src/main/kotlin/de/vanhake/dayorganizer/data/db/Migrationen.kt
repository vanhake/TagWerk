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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE aktivitaet ADD COLUMN symbol TEXT NOT NULL DEFAULT 'self_improvement'",
        )
    }
}
