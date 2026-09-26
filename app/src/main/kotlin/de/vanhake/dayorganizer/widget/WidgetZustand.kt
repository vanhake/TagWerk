package de.vanhake.dayorganizer.widget

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class WidgetEintrag(
    val faelligkeitId: Long,
    val name: String,
    val zeitText: String?,
    val ueberfaellig: Boolean,
    /** Wie HeuteZusammenstellung/HeuteScreen: bei Zaehlzielen (sollAnzahl > 1) zeigt das
     * Widget den Stand "istAnzahl/sollAnzahl" (Review-Fund G2) -- ein Tipp aendert sonst
     * nichts sichtbar, und ein zweiter Tipp aus Unsicherheit zaehlt doppelt. */
    val istAnzahl: Int,
    val sollAnzahl: Int,
)

data class WidgetZustand(
    val kontostand: Int = 0,
    val eintraege: List<WidgetEintrag> = emptyList(),
    /** Wie viele offene Eintraege nicht mehr auf das Widget passen. */
    val weitere: Int = 0,
)

/** Reine Umformung von Rohdaten in den Widget-Zustand, analog zu HeuteZusammenstellung. */
object WidgetZusammenstellung {

    private val UHRZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun aus(
        faelligkeiten: List<Faelligkeit>,
        aktivitaeten: List<Aktivitaet>,
        kontostand: Int,
        jetzt: LocalDateTime,
        hoechstens: Int,
    ): WidgetZustand {
        val nachId = aktivitaeten.associateBy { it.id }

        // Nur Aktivitaeten aus [aktivitaeten] (z.B. alleAktiven()) werden beruecksichtigt --
        // archivierte Aktivitaeten fallen damit automatisch heraus, ohne dass hier extra
        // gefiltert werden muss.
        //
        // Sortierung: ueberfaellig zuerst, danach nach geplantFuer aufsteigend. Eintraege
        // ohne Sollzeitpunkt (Tages-/Wochenziele, geplantFuer == null) koennen per Definition
        // nie ueberfaellig sein und werden mit LocalDateTime.MAX einsortiert, landen also
        // immer hinter allen Eintraegen mit Sollzeitpunkt. sortedWith ist ein stabiler Sort,
        // Ties zwischen mehreren Ziel-Eintraegen (gleiches MAX) behalten deshalb die Reihenfolge
        // der Eingabeliste -- und die kommt deterministisch aus der DB-Abfrage (ORDER BY
        // periodeVon, geplantFuer IS NULL, geplantFuer in FaelligkeitDao.fuerZeitraum).
        val offen = faelligkeiten
            .filter { it.status == FaelligkeitStatus.OFFEN && nachId.containsKey(it.aktivitaetId) }
            .sortedWith(
                compareByDescending<Faelligkeit> {
                    it.geplantFuer != null && it.geplantFuer!!.isBefore(jetzt)
                }.thenBy { it.geplantFuer ?: LocalDateTime.MAX },
            )

        return WidgetZustand(
            kontostand = kontostand,
            eintraege = offen.take(hoechstens).map { f ->
                WidgetEintrag(
                    faelligkeitId = f.id,
                    name = nachId.getValue(f.aktivitaetId).name,
                    zeitText = f.geplantFuer?.format(UHRZEIT),
                    ueberfaellig = f.geplantFuer != null && f.geplantFuer!!.isBefore(jetzt),
                    istAnzahl = f.istAnzahl,
                    sollAnzahl = f.sollAnzahl,
                )
            },
            weitere = (offen.size - hoechstens).coerceAtLeast(0),
        )
    }
}
