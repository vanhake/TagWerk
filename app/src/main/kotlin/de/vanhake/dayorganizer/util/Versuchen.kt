package de.vanhake.dayorganizer.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Wie runCatching, laesst aber einen Abbruch durch.
 *
 * runCatching faengt auch CancellationException. In einem viewModelScope heisst
 * das: verlaesst der Nutzer den Bildschirm, waehrend etwas laeuft, wird der Abbruch
 * zu einem gewoehnlichen Fehlschlag umgedeutet -- mit falscher Fehlermeldung, und
 * die Koroutine laeuft weiter, obwohl sie abgebrochen werden sollte. So stand es in
 * BelohnungenViewModel.einloesen und im Export und Import der Einstellungen.
 *
 * Urspruenglich in de.vanhake.dayorganizer.ui, hierher verschoben (Paket util statt ui),
 * weil BackupDienst -- ausserhalb von ui -- dasselbe Muster braucht (Review-Fund W1):
 * auch dort duerfen die catch-Bloecke einen Abbruch nicht als gewoehnlichen Fehlschlag
 * umdeuten.
 */
inline fun <T> versuchen(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (abbruch: CancellationException) {
        throw abbruch
    } catch (fehler: Throwable) {
        Result.failure(fehler)
    }
