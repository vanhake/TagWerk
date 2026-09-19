package de.vanhake.dayorganizer.platform.notification

sealed interface Benachrichtigungsaktion {
    val faelligkeitId: Long

    data class Erledigt(override val faelligkeitId: Long) : Benachrichtigungsaktion
    data class Spaeter(override val faelligkeitId: Long) : Benachrichtigungsaktion
    data class Ausgelassen(override val faelligkeitId: Long) : Benachrichtigungsaktion
}

object Benachrichtigungsaktionen {
    const val ACTION_ERLEDIGT = "de.vanhake.dayorganizer.ERLEDIGT"
    const val ACTION_SPAETER = "de.vanhake.dayorganizer.SPAETER"
    const val ACTION_AUSGELASSEN = "de.vanhake.dayorganizer.AUSGELASSEN"
    const val EXTRA_FAELLIGKEIT_ID = "faelligkeitId"

    fun aus(action: String?, faelligkeitId: Long): Benachrichtigungsaktion? {
        if (faelligkeitId <= 0) return null
        return when (action) {
            ACTION_ERLEDIGT -> Benachrichtigungsaktion.Erledigt(faelligkeitId)
            ACTION_SPAETER -> Benachrichtigungsaktion.Spaeter(faelligkeitId)
            ACTION_AUSGELASSEN -> Benachrichtigungsaktion.Ausgelassen(faelligkeitId)
            else -> null
        }
    }
}
