package de.vanhake.dayorganizer.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/** Ein Anstoss ist eine Erinnerungszeit fuer Ziele ohne Soll-Zeitpunkt. */
data class Anstoss(val tag: DayOfWeek, val zeit: LocalTime)

sealed interface Zeitplan {

    /** Feste Uhrzeiten an ausgewaehlten Wochentagen. Je Uhrzeit eine Faelligkeit. */
    data class FesteZeiten(
        val zeiten: List<LocalTime>,
        val wochentage: Set<DayOfWeek>,
    ) : Zeitplan

    /** Alle N Stunden innerhalb eines Tagesfensters. Je Zeitpunkt eine Faelligkeit. */
    data class Intervall(
        val alleNStunden: Int,
        val von: LocalTime,
        val bis: LocalTime,
        val wochentage: Set<DayOfWeek>,
    ) : Zeitplan

    /** X-mal am Tag ohne Soll-Zeitpunkt. Eine Faelligkeit je Tag mit sollAnzahl = anzahl. */
    data class Tagesziel(
        val anzahl: Int,
        val wochentage: Set<DayOfWeek>,
        val anstossZeiten: List<LocalTime> = emptyList(),
    ) : Zeitplan

    /** X-mal je Woche (Montag bis Sonntag). Eine Faelligkeit je Woche. */
    data class Wochenziel(
        val anzahl: Int,
        val anstoss: List<Anstoss> = emptyList(),
    ) : Zeitplan
}
