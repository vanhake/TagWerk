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
