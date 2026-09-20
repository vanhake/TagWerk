package de.vanhake.dayorganizer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AktivitaetEntity::class,
        FaelligkeitEntity::class,
        BuchungEntity::class,
        ErinnerungEntity::class,
        BelohnungEntity::class,
        EinloesungEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class DayOrganizerDatenbank : RoomDatabase() {
    abstract fun aktivitaetDao(): AktivitaetDao
    abstract fun faelligkeitDao(): FaelligkeitDao
    abstract fun buchungDao(): BuchungDao
    abstract fun erinnerungDao(): ErinnerungDao
    abstract fun belohnungDao(): BelohnungDao
}
