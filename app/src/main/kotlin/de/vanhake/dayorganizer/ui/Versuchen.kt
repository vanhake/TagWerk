package de.vanhake.dayorganizer.ui

import kotlin.coroutines.cancellation.CancellationException

/**
 * Wie runCatching, laesst aber einen Abbruch durch.
 *
 * runCatching faengt auch CancellationException. In einem viewModelScope heisst
 * das: verlaesst der Nutzer den Bildschirm, waehrend etwas laeuft, wird der Abbruch
 * zu einem gewoehnlichen Fehlschlag umgedeutet -- mit falscher Fehlermeldung, und
 * die Koroutine laeuft weiter, obwohl sie abgebrochen werden sollte. So stand es in
 * BelohnungenViewModel.einloesen und im Export und Import der Einstellungen.
 */
inline fun <T> versuchen(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (abbruch: CancellationException) {
        throw abbruch
    } catch (fehler: Throwable) {
        Result.failure(fehler)
    }
