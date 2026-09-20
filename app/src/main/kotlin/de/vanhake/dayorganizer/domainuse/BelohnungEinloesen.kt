package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.BelohnungRepository
import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.data.repository.Transaktionen
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Einloesung
import de.vanhake.dayorganizer.domain.rechner.BelohnungsPruefer
import de.vanhake.dayorganizer.domain.rechner.Einloesbarkeit
import de.vanhake.dayorganizer.domain.rechner.SerienRechner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

sealed interface EinloeseErgebnis {
    data class Erfolg(val einloesung: Einloesung) : EinloeseErgebnis
    data class Abgelehnt(val einloesbarkeit: Einloesbarkeit) : EinloeseErgebnis
    data object Unbekannt : EinloeseErgebnis
}

class BelohnungEinloesen @Inject constructor(
    private val belohnungen: BelohnungRepository,
    private val buchungen: BuchungRepository,
    private val faelligkeiten: FaelligkeitRepository,
    private val pruefer: BelohnungsPruefer,
    private val serienRechner: SerienRechner,
    private val transaktionen: Transaktionen,
    private val uhr: Clock,
) {

    /**
     * Der Kontostand kommt von aussen, statt hier gelesen zu werden. Zwei Aufrufer
     * brauchen ihn naemlich aus verschiedenen Quellen: der Shop-Bildschirm hat ihn
     * bereits aus seinem Flow und wuerde sonst je Kachel ein zweites Mal lesen --
     * mit dem Risiko, andere fehlende Punkte anzuzeigen als der Kontostand darueber
     * hergibt. loese() dagegen liest ihn innerhalb der Transaktion, wo er
     * verbindlich ist. So bleibt die Serienberechnung trotzdem an einer Stelle.
     */
    suspend fun einloesbarkeit(belohnung: Belohnung, kontostand: Int): Einloesbarkeit =
        pruefer.pruefe(
            belohnung = belohnung,
            kontostand = kontostand,
            serieDerBedingung = serieFuer(belohnung),
            heute = LocalDate.now(uhr),
        )

    // Lesen, Pruefen, Buchen und Anlegen liegen zusammen in der Transaktion. Die
    // Reihenfolge allein reicht nicht: stuende die Pruefung davor, laesen zwei
    // gleichzeitige Aufrufer denselben Kontostand, bestuenden beide und ueberzoegen
    // das Konto -- ein Doppeltipp auf Einloesen genuegt. FaelligkeitErledigen loest
    // dieselbe Stelle genauso.
    suspend operator fun invoke(belohnungId: Long): EinloeseErgebnis =
        transaktionen.inTransaktion { loese(belohnungId) }

    private suspend fun loese(belohnungId: Long): EinloeseErgebnis {
        val belohnung = belohnungen.nachId(belohnungId) ?: return EinloeseErgebnis.Unbekannt

        // Der Lesezugriff steht hier drin, nicht davor -- er ist Teil der Pruefung,
        // die zwei gleichzeitige Aufrufer trennen muss.
        val pruefung = einloesbarkeit(belohnung, buchungen.kontostand())
        if (!pruefung.moeglich) return EinloeseErgebnis.Abgelehnt(pruefung)

        val jetzt = LocalDateTime.now(uhr)

        buchungen.buchen(
            listOf(
                Buchung(
                    art = BuchungsArt.EINLOESUNG,
                    betrag = -belohnung.preisPunkte,
                    zeitpunkt = jetzt,
                    belohnungId = belohnung.id,
                    notiz = belohnung.name,
                ),
            ),
        )

        val einloesung = Einloesung(
            belohnungId = belohnung.id,
            name = belohnung.name,
            preisPunkte = belohnung.preisPunkte,
            zeitpunkt = jetzt,
        )
        belohnungen.einloesungAnlegen(einloesung)
        return EinloeseErgebnis.Erfolg(einloesung)
    }

    private suspend fun serieFuer(belohnung: Belohnung): Int {
        val bedingung = belohnung.bedingung as? Belohnungsbedingung.MindestSerie ?: return 0
        val historie = faelligkeiten.historie(bedingung.aktivitaetId)
        return serienRechner.aktuelleSerie(historie, LocalDate.now(uhr))
    }
}
