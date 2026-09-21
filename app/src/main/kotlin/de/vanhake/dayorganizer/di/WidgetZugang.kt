package de.vanhake.dayorganizer.di

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.vanhake.dayorganizer.data.repository.AktivitaetRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.domainuse.FaelligkeitErledigen
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import de.vanhake.dayorganizer.platform.notification.Erinnerungsanzeige
import java.time.Clock

/**
 * Glance kann keine Felder per Hilt injizieren (kein Android-Kontext mit eigenem
 * Injection-Punkt) -- deshalb holt sich das Widget seine Abhaengigkeiten ueber diesen
 * Zugangspunkt direkt aus dem SingletonComponent. erledigen(), alarmPlaner() und
 * anzeige() werden von WidgetErledigenAktion (Task 4) fuer das Abhaken aus dem Widget
 * gebraucht.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetZugang {
    fun aktivitaeten(): AktivitaetRepository
    fun faelligkeiten(): FaelligkeitRepository
    fun buchungen(): BuchungRepository
    fun erledigen(): FaelligkeitErledigen
    fun alarmPlaner(): AlarmPlaner
    fun anzeige(): Erinnerungsanzeige
    fun uhr(): Clock
}
