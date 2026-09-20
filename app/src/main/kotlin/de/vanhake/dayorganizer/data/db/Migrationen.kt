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
