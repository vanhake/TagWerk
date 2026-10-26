package de.vanhake.dayorganizer.ui.heute

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import de.vanhake.dayorganizer.domainuse.FaelligkeitWiederOeffnen
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
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
        aufgaben: List<Faelligkeit> = emptyList(),
    ): HeuteZustand {
        val nachId = aktivitaeten.associateBy { it.id }
        // Einmalaufgaben reichen ueber Wochen und gehoeren nicht zum Tagesfortschritt; sie
        // stehen in einem eigenen Bereich (aufgabenEintraege) statt in den Tageslisten.
        val einmalig = aktivitaeten.filter { it.zeitplan is Zeitplan.Einmalig }.map { it.id }.toSet()
        val eintraege = faelligkeiten.filter { it.aktivitaetId !in einmalig }.mapNotNull { f ->
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
                wiederOeffenbar = FaelligkeitWiederOeffnen.istWiederOeffenbar(f),
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
                .map { it.second } + heuteErledigteAufgaben(aufgaben, nachId, einmalig, jetzt),
            aufgaben = aufgabenEintraege(aufgaben, nachId, einmalig, jetzt),
            naechsteBelohnung = NaechsteBelohnungWahl.aus(belohnungen, kontostand),
        )
    }

    /**
     * Offen heisst hier: noch nicht erledigt. Eine verstrichene Deadline schliesst der
     * Waechter als VERPASST; solange sie nicht nachtraeglich abgehakt ist (erledigtAm ==
     * null), bleibt sie als ueberfaellig stehen, damit sie nicht in Vergessenheit geraet.
     */
    private fun aufgabenEintraege(
        aufgaben: List<Faelligkeit>,
        nachId: Map<Long, Aktivitaet>,
        einmalig: Set<Long>,
        jetzt: LocalDateTime,
    ): List<HeuteEintrag> {
        val heute = jetzt.toLocalDate()
        return aufgaben
            .filter { it.aktivitaetId in einmalig }
            .filter {
                it.status == FaelligkeitStatus.OFFEN ||
                    (it.status == FaelligkeitStatus.VERPASST && it.erledigtAm == null)
            }
            .sortedBy { it.periodeBis }
            .map { f ->
                val aktivitaet = nachId.getValue(f.aktivitaetId)
                HeuteEintrag(
                    faelligkeitId = f.id,
                    aktivitaetName = aktivitaet.name,
                    farbe = aktivitaet.farbe,
                    symbol = aktivitaet.symbol,
                    zeitText = null,
                    sollAnzahl = f.sollAnzahl,
                    istAnzahl = f.istAnzahl,
                    status = f.status,
                    ueberfaellig = f.periodeBis.isBefore(heute),
                    deadlineInTagen = ChronoUnit.DAYS.between(heute, f.periodeBis),
                )
            }
    }

    /**
     * Eine abgehakte Einmalaufgabe verschwaende sonst sofort aus "Aufgaben" und liesse sich
     * nach einem Fehltipp nicht mehr finden. Am Tag des Abhakens steht sie deshalb unter
     * "Erledigt" und laesst sich dort wieder oeffnen; zum Tagesfortschritt zaehlt sie nicht.
     */
    private fun heuteErledigteAufgaben(
        aufgaben: List<Faelligkeit>,
        nachId: Map<Long, Aktivitaet>,
        einmalig: Set<Long>,
        jetzt: LocalDateTime,
    ): List<HeuteEintrag> {
        val heute = jetzt.toLocalDate()
        return aufgaben
            .filter { it.aktivitaetId in einmalig && it.erledigtAm?.toLocalDate() == heute }
            .filter { it.status == FaelligkeitStatus.ERLEDIGT || it.status == FaelligkeitStatus.VERPASST }
            .sortedBy { it.erledigtAm }
            .map { f ->
                val aktivitaet = nachId.getValue(f.aktivitaetId)
                HeuteEintrag(
                    faelligkeitId = f.id,
                    aktivitaetName = aktivitaet.name,
                    farbe = aktivitaet.farbe,
                    symbol = aktivitaet.symbol,
                    zeitText = null,
                    sollAnzahl = f.sollAnzahl,
                    istAnzahl = f.istAnzahl,
                    status = f.status,
                    ueberfaellig = false,
                    wiederOeffenbar = FaelligkeitWiederOeffnen.istWiederOeffenbar(f),
                )
            }
    }
}
