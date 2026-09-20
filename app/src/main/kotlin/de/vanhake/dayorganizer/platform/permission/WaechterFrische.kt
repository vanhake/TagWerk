package de.vanhake.dayorganizer.platform.permission

import java.time.Duration
import java.time.LocalDateTime

/**
 * Wie lange der Waechter ausbleiben darf, bevor die Selbstheilung als kaputt gilt.
 * Er laeuft taeglich; 36 Stunden lassen einen ausgefallenen Lauf zu, ohne dass
 * sofort ein Banner erscheint.
 */
val WAECHTER_FRISCHE_GRENZE: Duration = Duration.ofHours(36)

/**
 * Entscheidet, ob die Selbstheilung haengt. Reine Funktion, damit die Regel an
 * genau einer Stelle steht -- vorher stand sie samt Grenzwert doppelt in
 * HeuteViewModel und EinstellungenViewModel.
 *
 * Der Knackpunkt ist [letzterErfolg] == null. Das heisst "der Waechter hat noch nie
 * erfolgreich gelaufen", und das ist zweierlei: bei einer frischen Installation ist
 * es der Normalzustand, spaeter waere es ein echter Befund. Frueher galt null
 * pauschal als haengend -- der Banner "Selbsheilung reagiert nicht" stand damit
 * schon beim allerersten Start, bevor der Waechter ueberhaupt laufen konnte. Auf
 * dem Emulator war das der erste Eindruck der App.
 *
 * Deshalb wird ohne Erfolg ab [installiertAm] gemessen: hat der Waechter binnen
 * derselben Frist nach der Installation nichts zustande gebracht, ist das sehr wohl
 * ein Befund -- nur eben keiner, der in der ersten Minute schon feststeht.
 */
fun waechterHaengt(
    letzterErfolg: LocalDateTime?,
    installiertAm: LocalDateTime,
    jetzt: LocalDateTime,
): Boolean = Duration.between(letzterErfolg ?: installiertAm, jetzt) > WAECHTER_FRISCHE_GRENZE
