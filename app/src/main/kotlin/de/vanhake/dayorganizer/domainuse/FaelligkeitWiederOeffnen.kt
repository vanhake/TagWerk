package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.repository.BuchungRepository
import de.vanhake.dayorganizer.data.repository.FaelligkeitRepository
import de.vanhake.dayorganizer.data.repository.Transaktionen
import de.vanhake.dayorganizer.domain.model.Buchung
import de.vanhake.dayorganizer.domain.model.BuchungsArt
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Oeffnet eine versehentlich abgeschlossene Faelligkeit wieder -- auch dann, wenn der
 * Rueckgaengig-Toast laengst verschwunden ist und es kein ErledigungsProtokoll mehr gibt.
 *
 * - ERLEDIGT: der letzte Abhak-Schritt wird zurueckgenommen (istAnzahl - 1, Status OFFEN).
 *   Dessen Buchungen tragen exakt den Zeitpunkt erledigtAm (PunkteRechner bucht mit
 *   kontext.zeitpunkt = erledigtAm); fuer jede bucht dieser Use Case eine betragsgleiche
 *   KORREKTUR -- dieselbe Form wie FaelligkeitErledigenRueckgaengig, damit Serienbonus-
 *   und Tagesbonus-Abfragen netto weiter stimmen. Fruehere Schritte eines Zaehlziels
 *   behalten ihre Punkte.
 * - AUSGELASSEN: nur der Status, wie FaelligkeitAuslassenRueckgaengig.
 * - VERPASST mit erledigtAm: eine nachtraeglich abgehakte Einmalaufgabe; gebucht wurde
 *   nichts, sie steht danach wieder als ueberfaellig unter "Aufgaben".
 *
 * Eine vom Waechter geschlossene VERPASST-Faelligkeit (erledigtAm == null) war kein
 * Versehen des Nutzers und bleibt zu.
 */
class FaelligkeitWiederOeffnen @Inject constructor(
    private val faelligkeiten: FaelligkeitRepository,
    private val buchungen: BuchungRepository,
    private val transaktionen: Transaktionen,
    private val uhr: Clock,
) {
    /** Die Faelligkeit vor dem Wiederoeffnen, oder null, wenn sie unbekannt oder nicht wieder zu oeffnen ist. */
    suspend operator fun invoke(faelligkeitId: Long): Faelligkeit? = transaktionen.inTransaktion {
        val vorher = faelligkeiten.nachId(faelligkeitId) ?: return@inTransaktion null
        if (!istWiederOeffenbar(vorher)) return@inTransaktion null
        when (vorher.status) {
            FaelligkeitStatus.ERLEDIGT -> {
                faelligkeiten.aktualisieren(
                    vorher.copy(
                        istAnzahl = (vorher.istAnzahl - 1).coerceAtLeast(0),
                        status = FaelligkeitStatus.OFFEN,
                        erledigtAm = null,
                        warPuenktlich = false,
                    ),
                )
                buchungen.buchen(gegenbuchungen(vorher))
            }
            FaelligkeitStatus.AUSGELASSEN ->
                faelligkeiten.aktualisieren(vorher.copy(status = FaelligkeitStatus.OFFEN, erledigtAm = null))
            FaelligkeitStatus.VERPASST ->
                faelligkeiten.aktualisieren(vorher.copy(istAnzahl = 0, erledigtAm = null))
            FaelligkeitStatus.OFFEN -> Unit
        }
        vorher
    }

    private suspend fun gegenbuchungen(vorher: Faelligkeit): List<Buchung> {
        val erledigtAm = vorher.erledigtAm ?: return emptyList()
        val tag = erledigtAm.toLocalDate()
        val jetzt = LocalDateTime.now(uhr)
        return buchungen.fuerZeitraum(tag, tag)
            .filter { it.zeitpunkt == erledigtAm && it.art != BuchungsArt.KORREKTUR }
            .filter {
                it.faelligkeitId == vorher.id ||
                    // Der Tagesbonus traegt keine faelligkeitId, wurde aber im selben
                    // Abhak-Schritt (gleicher Zeitpunkt) gebucht.
                    (it.art == BuchungsArt.TAGESBONUS && it.faelligkeitId == null)
            }
            .map { original ->
                // tagesbonusGebuchtAm rechnet netto je Kalendertag: eine Tagesbonus-Korrektur
                // muss deshalb am Tag des Bonus liegen. An einem spaeteren Tag gebucht, liesse
                // sie den alten Bonus stehen und gaebe dafuer heute einen zweiten frei.
                val zeitpunkt = if (original.art == BuchungsArt.TAGESBONUS && jetzt.toLocalDate() != tag) {
                    original.zeitpunkt
                } else {
                    jetzt
                }
                original.copy(
                    id = 0,
                    art = BuchungsArt.KORREKTUR,
                    betrag = -original.betrag,
                    zeitpunkt = zeitpunkt,
                    notiz = "Wieder geoeffnet: ${original.art}",
                )
            }
    }

    companion object {
        /** Gemeinsame Regel fuer Use Case und Oberflaeche (Knopf "Wieder öffnen"). */
        fun istWiederOeffenbar(f: Faelligkeit): Boolean = when (f.status) {
            FaelligkeitStatus.ERLEDIGT, FaelligkeitStatus.AUSGELASSEN -> true
            FaelligkeitStatus.VERPASST -> f.erledigtAm != null
            FaelligkeitStatus.OFFEN -> false
        }
    }
}
