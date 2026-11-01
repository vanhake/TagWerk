package de.vanhake.dayorganizer.domain.model

/**
 * Was eine Erledigung bringt, die nach dem Kulanzfenster einer terminierten Faelligkeit
 * (feste Uhrzeit, Intervall) kommt. Zaehlziele und Einmalaufgaben haben keinen
 * Soll-Zeitpunkt und sind nie verspaetet.
 */
enum class VerspaetungsRegel {
    /** Basispunkte, Serien- und Tagesbonus wie gewohnt; nur der Puenktlichkeitsbonus entfaellt. */
    VOLLE_PUNKTE,

    /** Halbe Basispunkte (abgerundet); Serien- und Tagesbonus wie gewohnt. */
    HALBE_PUNKTE,

    /**
     * Gar keine Buchung. Die Faelligkeit gilt trotzdem als erledigt (Serie und
     * Tagesfortschritt laufen weiter); ein dabei erreichter Serienbonus wird erst mit der
     * naechsten rechtzeitigen Erledigung gebucht.
     */
    KEINE_PUNKTE,
}
