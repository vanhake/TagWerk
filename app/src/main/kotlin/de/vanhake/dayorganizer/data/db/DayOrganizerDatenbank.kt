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
