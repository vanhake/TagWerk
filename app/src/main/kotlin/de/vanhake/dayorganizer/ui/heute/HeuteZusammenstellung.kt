package de.vanhake.dayorganizer.ui.heute

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Reine Umformung von Rohdaten in den Bildschirmzustand. */
object HeuteZusammenstellung {

    private val UHRZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private const val ANSTEHEND_STUNDEN = 2L

    fun aus(
        faelligkeiten: List<Faelligkeit>,
        aktivitaeten: List<Aktivitaet>,
        belohnungen: List<Belohnung>,
        kontostand: Int,
        jetzt: LocalDateTime,
    ): HeuteZustand {
        val nachId = aktivitaeten.associateBy { it.id }
        val eintraege = faelligkeiten.mapNotNull { f ->
            val aktivitaet = nachId[f.aktivitaetId] ?: return@mapNotNull null
            f to HeuteEintrag(
                faelligkeitId = f.id,
                aktivitaetName = aktivitaet.name,
                farbe = aktivitaet.farbe,
                symbol = aktivitaet.symbol,
                zeitText = f.geplantFuer?.format(UHRZEIT),
                sollAnzahl = f.sollAnzahl,
                istAnzahl = f.istAnzahl,
                status = f.status,
                ueberfaellig = f.status == FaelligkeitStatus.OFFEN &&
                    f.geplantFuer != null && f.geplantFuer!!.isBefore(jetzt),
            )
        }

        val offen = eintraege.filter { it.first.status == FaelligkeitStatus.OFFEN }
        val grenze = jetzt.plusHours(ANSTEHEND_STUNDEN)

        return HeuteZustand(
            kontostand = kontostand,
            erledigtHeute = eintraege.count { it.first.status == FaelligkeitStatus.ERLEDIGT },
            gesamtHeute = eintraege.size,
            ueberfaellig = offen.filter { it.second.ueberfaellig }.map { it.second },
            anstehend = offen
                .filter { (f, e) ->
                    !e.ueberfaellig && (f.geplantFuer == null || f.geplantFuer!!.isBefore(grenze))
                }
                .map { it.second },
            spaeter = offen
                .filter { (f, e) ->
                    !e.ueberfaellig && f.geplantFuer != null && !f.geplantFuer!!.isBefore(grenze)
                }
                .map { it.second },
            abgeschlossen = eintraege
                .filter { it.first.status != FaelligkeitStatus.OFFEN }
                .map { it.second },
            naechsteBelohnung = NaechsteBelohnungWahl.aus(belohnungen, kontostand),
        )
    }
}
