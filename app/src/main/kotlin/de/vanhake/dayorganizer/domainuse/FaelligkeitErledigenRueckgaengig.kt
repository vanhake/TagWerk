package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.data.repository.Transaktionen
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Macht genau eine erfolgreiche FaelligkeitErledigen-Buchung rueckgaengig, anhand des
 * ErledigungsProtokoll.Gebucht, das dieser eine Aufruf zurueckgegeben hat. Stellt die
 * Faelligkeit auf ihren exakten Vorzustand zurueck und bucht fuer jede damals gebuchte
 * Zeile eine betragsgleiche Gegenbuchung (BuchungsArt.KORREKTUR) -- nie eine Loeschung,
 * damit die Punkte-Historie erklaerbar bleibt (README: "Korrekturen faelschen keine
 * Historie").
 */
class FaelligkeitErledigenRueckgaengig @Inject constructor(
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val transaktionen: Transaktionen,
    private val uhr: Clock,
) {
    suspend operator fun invoke(protokoll: ErledigungsProtokoll.Gebucht) = transaktionen.inTransaktion {
        faelligkeiten.aktualisieren(protokoll.faelligkeitVorher)
        val jetzt = LocalDateTime.now(uhr)
        buchungen.buchen(
            protokoll.buchungen.map { original ->
                original.copy(
                    id = 0,
                    art = BuchungsArt.KORREKTUR,
                    betrag = -original.betrag,
                    zeitpunkt = jetzt,
                    notiz = "Rueckgaengig: ${original.art}",
                )
            },
        )
    }
}
