package de.vanhake.dayorganizer.platform.permission

data class BerechtigungsStand(
    val benachrichtigungenErlaubt: Boolean,
    val exakteAlarmeErlaubt: Boolean,
    val akkuOptimierungAktiv: Boolean,
)

enum class Hinweis {
    /** Ohne diese Berechtigung sieht der Nutzer gar nichts. */
    BENACHRICHTIGUNGEN_FEHLEN,

    /** Erinnerungen kommen dann verspaetet. */
    EXAKTE_ALARME_FEHLEN,

    /** Hersteller-Akkuoptimierung beendet Hintergrundarbeit. */
    AKKUOPTIMIERUNG_AKTIV,
}

object BerechtigungsHinweise {
    fun fuer(stand: BerechtigungsStand): List<Hinweis> = buildList {
        if (!stand.benachrichtigungenErlaubt) add(Hinweis.BENACHRICHTIGUNGEN_FEHLEN)
        if (!stand.exakteAlarmeErlaubt) add(Hinweis.EXAKTE_ALARME_FEHLEN)
        if (stand.akkuOptimierungAktiv) add(Hinweis.AKKUOPTIMIERUNG_AKTIV)
    }
}
