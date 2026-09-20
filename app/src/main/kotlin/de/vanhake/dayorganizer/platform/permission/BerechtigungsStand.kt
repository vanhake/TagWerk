package de.vanhake.dayorganizer.platform.permission

data class BerechtigungsStand(
    val benachrichtigungenErlaubt: Boolean,
    val exakteAlarmeErlaubt: Boolean,
    val akkuOptimierungAktiv: Boolean,
    // BerechtigungsPruefer liest nur aus Context und kann DataStore nicht synchron lesen,
    // deshalb kommt dieses Feld nicht aus BerechtigungsPruefer.stand() selbst, sondern wird
    // von aussen (HeuteViewModel) per copy() gesetzt. Default false haelt bestehende
    // BerechtigungsPruefer-Aufrufer unveraendert.
    val waechterHaengt: Boolean = false,
)

enum class Hinweis {
    /** Ohne diese Berechtigung sieht der Nutzer gar nichts. */
    BENACHRICHTIGUNGEN_FEHLEN,

    /** Erinnerungen kommen dann verspaetet. */
    EXAKTE_ALARME_FEHLEN,

    /** Hersteller-Akkuoptimierung beendet Hintergrundarbeit. */
    AKKUOPTIMIERUNG_AKTIV,

    /** Der vagste der vier Hinweise: die Selbstheilung koennte tot sein, ohne dass
     * irgendetwas anderes das verraet. */
    WAECHTER_HAENGT,
}

object BerechtigungsHinweise {
    fun fuer(stand: BerechtigungsStand): List<Hinweis> = buildList {
        if (!stand.benachrichtigungenErlaubt) add(Hinweis.BENACHRICHTIGUNGEN_FEHLEN)
        if (!stand.exakteAlarmeErlaubt) add(Hinweis.EXAKTE_ALARME_FEHLEN)
        if (stand.akkuOptimierungAktiv) add(Hinweis.AKKUOPTIMIERUNG_AKTIV)
        if (stand.waechterHaengt) add(Hinweis.WAECHTER_HAENGT)
    }
}
