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
import de.vanhake.dayorganizer.data.db.BelohnungDao
import de.vanhake.dayorganizer.data.db.BuchungDao
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import de.vanhake.dayorganizer.data.db.ErinnerungDao
import de.vanhake.dayorganizer.data.db.FaelligkeitDao
import de.vanhake.dayorganizer.data.db.MIGRATION_1_2
import de.vanhake.dayorganizer.data.db.MIGRATION_2_3
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.ErinnerungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.data.repository.RaumAktivitaetRepository
import de.vanhake.dayorganizer.data.repository.RaumBelohnungRepository
import de.vanhake.dayorganizer.data.repository.RaumBuchungRepository
import de.vanhake.dayorganizer.data.repository.RaumErinnerungRepository
import de.vanhake.dayorganizer.data.repository.RaumFaelligkeitRepository
import de.vanhake.dayorganizer.data.repository.RaumTransaktionen
import de.vanhake.dayorganizer.data.repository.Transaktionen
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.PunkteRechner
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import de.vanhake.dayorganizer.domain.rechner.StatistikRechner
import de.vanhake.dayorganizer.domain.rechner.ZeitplanRechner
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import de.vanhake.dayorganizer.platform.notification.ErinnerungsNotifier
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatenModul {

    @Provides
    @Singleton
    fun datenbank(@ApplicationContext kontext: Context): DayOrganizerDatenbank =
        Room.databaseBuilder(kontext, DayOrganizerDatenbank::class.java, "dayorganizer.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides fun aktivitaetDao(db: DayOrganizerDatenbank): AktivitaetDao = db.aktivitaetDao()
    @Provides fun faelligkeitDao(db: DayOrganizerDatenbank): FaelligkeitDao = db.faelligkeitDao()
    @Provides fun buchungDao(db: DayOrganizerDatenbank): BuchungDao = db.buchungDao()
    @Provides fun erinnerungDao(db: DayOrganizerDatenbank): ErinnerungDao = db.erinnerungDao()
    @Provides fun belohnungDao(db: DayOrganizerDatenbank): BelohnungDao = db.belohnungDao()

    @Provides @Singleton fun uhr(): Clock = Clock.systemDefaultZone()
    @Provides @Singleton fun zeitplanRechner(): ZeitplanRechner = ZeitplanRechner()
    @Provides @Singleton fun punkteRechner(): PunkteRechner = PunkteRechner()
    @Provides @Singleton fun serienRechner(): SerienRechner = SerienRechner()
    @Provides @Singleton fun belohnungsPruefer(): BelohnungsPruefer = BelohnungsPruefer()

    @Provides @Singleton
    fun statistikRechner(serienRechner: SerienRechner): StatistikRechner =
        StatistikRechner(serienRechner)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModul {
    @Binds abstract fun aktivitaeten(impl: RaumAktivitaetRepository): AktivitaetRepository
    @Binds abstract fun faelligkeiten(impl: RaumFaelligkeitRepository): FaelligkeitRepository
    @Binds abstract fun buchungen(impl: RaumBuchungRepository): BuchungRepository
    @Binds abstract fun transaktionen(impl: RaumTransaktionen): Transaktionen
    @Binds abstract fun erinnerungsanzeige(impl: ErinnerungsNotifier): Erinnerungsanzeige
    @Binds abstract fun erinnerungen(impl: RaumErinnerungRepository): ErinnerungRepository
    @Binds abstract fun belohnungen(impl: RaumBelohnungRepository): BelohnungRepository
}
