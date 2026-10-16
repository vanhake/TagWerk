package de.vanhake.dayorganizer.widget

import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Reine Zeitlogik des Widgets: wann sich die Darstellung das naechste Mal aendert, ohne
 * dass sich Daten aendern. Ausgelagert, damit sie ohne Glance und AlarmManager testbar ist.
 */
object WidgetTakt {

    /**
     * Naechster Zeitpunkt >= jetzt, an dem sich die Darstellung aendert: das fruehste
     * geplantFuer (>= jetzt) einer heute OFFENEN Faelligkeit, sonst/spaetestens der naechste
     * Tagesbeginn 00:00. Ab diesem Zeitpunkt wird aus "offen" ein "ueberfaellig", ab
     * Mitternacht zeigt das Widget einen anderen Tag.
     */
    fun naechsterUmschaltpunkt(
        faelligkeiten: List<Faelligkeit>,
        jetzt: LocalDateTime,
    ): LocalDateTime {
        val mitternacht = jetzt.toLocalDate().plusDays(1).atStartOfDay()
        val naechsterTermin = faelligkeiten
            .filter { it.status == FaelligkeitStatus.OFFEN }
            .mapNotNull { it.geplantFuer }
            .filter { !it.isBefore(jetzt) }
            .minOrNull()
        // Ein Termin von morgen liegt nach Mitternacht: dann gewinnt die Mitternacht.
        return if (naechsterTermin != null && naechsterTermin.isBefore(mitternacht)) naechsterTermin else mitternacht
    }

    /** periodeVon <= tag <= periodeBis -- dasselbe Praedikat wie FaelligkeitDao.beobachteTag. */
    fun gehoertZuTag(faelligkeit: Faelligkeit, tag: LocalDate): Boolean =
        !faelligkeit.periodeVon.isAfter(tag) && !faelligkeit.periodeBis.isBefore(tag)
}
