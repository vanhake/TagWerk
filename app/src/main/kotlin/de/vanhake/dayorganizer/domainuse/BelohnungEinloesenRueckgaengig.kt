package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.Transaktionen
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Einloesung
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Macht genau eine Einloesung rueckgaengig: Die Einloesung wird storniert (nicht geloescht)
 * und der Preis per KORREKTUR-Buchung gutgeschrieben -- dasselbe Prinzip wie
 * FaelligkeitErledigenRueckgaengig (README: "Korrekturen faelschen keine Historie").
 *
 * Die Gegenbuchung traegt die belohnungId. Damit schliesst die Netto-Tagesbonus-Abfrage sie
 * aus (BuchungDao.nettoTagesbonusPunkte, "belohnungId IS NULL"); abgesichert im
 * BuchungRepositoryVertragTest.
 *
 * Gibt false zurueck, wenn die Einloesung unbekannt oder schon storniert ist -- dann wird
 * nichts gebucht. Stornieren und Buchen liegen in einer Transaktion, damit ein Doppeltipp
 * nicht zweimal gutschreibt.
 */
class BelohnungEinloesenRueckgaengig @Inject constructor(
    private val belohnungen: BelohnungRepository,
    private val buchungen: BuchungRepository,
    private val transaktionen: Transaktionen,
    private val uhr: Clock,
) {
    suspend operator fun invoke(einloesung: Einloesung): Boolean = transaktionen.inTransaktion {
        val jetzt = LocalDateTime.now(uhr)
        if (!belohnungen.einloesungStornieren(einloesung.id, jetzt)) return@inTransaktion false
        buchungen.buchen(
            listOf(
                Buchung(
                    art = BuchungsArt.KORREKTUR,
                    betrag = einloesung.preisPunkte,
                    zeitpunkt = jetzt,
                    belohnungId = einloesung.belohnungId,
                    notiz = "Rueckgaengig: ${BuchungsArt.EINLOESUNG} #${einloesung.id}",
                ),
            ),
        )
        true
    }
}
