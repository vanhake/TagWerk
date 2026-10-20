package de.vanhake.dayorganizer.widget

import android.content.res.Resources
import de.vanhake.dayorganizer.R
import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

data class WidgetEintrag(
    val faelligkeitId: Long,
    val name: String,
    /** Aktivitaet.symbol, Schluessel wie AKTIVITAET_SYMBOLE. */
    val symbol: String,
    val zeitText: String?,
    val ueberfaellig: Boolean,
    val istAnzahl: Int,
    val sollAnzahl: Int,
    /** periodeBis > periodeVon: Wochenziel statt Tagesziel. */
    val wochenziel: Boolean,
    /** Nur bei Einmalaufgaben: Tage bis zur Deadline. */
    val deadlineInTagen: Long? = null,
)

/** Warum das Widget keine offenen Eintraege zeigt. */
enum class WidgetLeer { ALLES_ERLEDIGT, NICHTS_GEPLANT }

data class WidgetZustand(
    val kontostand: Int = 0,
    /** Status ERLEDIGT, nur aktive Aktivitaeten. */
    val erledigt: Int = 0,
    /** Alle Faelligkeiten des Tages aktiver Aktivitaeten, unabhaengig vom Status. */
    val gesamt: Int = 0,
    /** Alle offenen Eintraege, sortiert; wie viele passen, bestimmt WidgetMasse. */
    val offen: List<WidgetEintrag> = emptyList(),
    /** null, solange etwas offen ist. */
    val leer: WidgetLeer? = null,
)

/** Reine Umformung von Rohdaten in den Widget-Zustand, analog zu HeuteZusammenstellung. */
object WidgetZusammenstellung {

    private val UHRZEIT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    /** Eine Einmalaufgabe erscheint erst, wenn ihre Deadline so nah ist -- vorher wuerde sie
     * die wenigen Zeilen des Widgets wochenlang belegen. */
    const val AUFGABE_VORLAUF_TAGE = 7L

    fun aus(
        faelligkeiten: List<Faelligkeit>,
        aktivitaeten: List<Aktivitaet>,
        kontostand: Int,
        jetzt: LocalDateTime,
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
        //
        // Einmalaufgaben zaehlen wie auf "Heute" nicht zum Tagesfortschritt; offen erscheinen
        // sie hinter den Tageseintraegen, sobald die Deadline hoechstens AUFGABE_VORLAUF_TAGE
        // entfernt ist.
        val heute = jetzt.toLocalDate()
        val einmalig = aktivitaeten.filter { it.zeitplan is Zeitplan.Einmalig }.map { it.id }.toSet()
        val bekannt = faelligkeiten.filter { nachId.containsKey(it.aktivitaetId) }
        val dieserTag = bekannt.filter { it.aktivitaetId !in einmalig }
        val aufgaben = bekannt
            .filter {
                it.aktivitaetId in einmalig &&
                    it.status == FaelligkeitStatus.OFFEN &&
                    ChronoUnit.DAYS.between(heute, it.periodeBis) <= AUFGABE_VORLAUF_TAGE
            }
            .sortedBy { it.periodeBis }
        val offen = dieserTag
            .filter { it.status == FaelligkeitStatus.OFFEN }
            .sortedWith(
                compareByDescending<Faelligkeit> {
                    it.geplantFuer != null && it.geplantFuer!!.isBefore(jetzt)
                }.thenBy { it.geplantFuer ?: LocalDateTime.MAX },
            ) + aufgaben

        return WidgetZustand(
            kontostand = kontostand,
            erledigt = dieserTag.count { it.status == FaelligkeitStatus.ERLEDIGT },
            gesamt = dieserTag.size,
            offen = offen.map { f ->
                val aktivitaet = nachId.getValue(f.aktivitaetId)
                WidgetEintrag(
                    faelligkeitId = f.id,
                    name = aktivitaet.name,
                    symbol = aktivitaet.symbol,
                    zeitText = f.geplantFuer?.format(UHRZEIT),
                    ueberfaellig = f.geplantFuer != null && f.geplantFuer!!.isBefore(jetzt),
                    istAnzahl = f.istAnzahl,
                    sollAnzahl = f.sollAnzahl,
                    wochenziel = f.periodeBis.isAfter(f.periodeVon),
                    deadlineInTagen = if (f.aktivitaetId in einmalig) {
                        ChronoUnit.DAYS.between(heute, f.periodeBis)
                    } else {
                        null
                    },
                )
            },
            leer = when {
                offen.isNotEmpty() -> null
                dieserTag.isEmpty() -> WidgetLeer.NICHTS_GEPLANT
                else -> WidgetLeer.ALLES_ERLEDIGT
            },
        )
    }
}

/**
 * Untertitel einer Zeile, Copy wie mk() im Prototyp. Zaehlziele (sollAnzahl > 1) zeigen
 * ihren Stand wie der Heute-Bildschirm (Review-Fund G2) -- ein Tipp aendert sonst nichts
 * sichtbar, und ein zweiter Tipp aus Unsicherheit zaehlt doppelt.
 */
fun widgetUntertitel(eintrag: WidgetEintrag, res: Resources): String {
    eintrag.deadlineInTagen?.let { tage ->
        return when (tage) {
            0L -> res.getString(R.string.deadline_heute)
            1L -> res.getString(R.string.deadline_morgen)
            else -> res.getQuantityString(R.plurals.deadline_noch, tage.toInt(), tage.toInt())
        }
    }
    val zaehltext = res.getString(
        if (eintrag.wochenziel) R.string.widget_ziel_woche else R.string.widget_ziel_heute,
        eintrag.istAnzahl,
        eintrag.sollAnzahl,
    )
    val zeitText = eintrag.zeitText ?: return zaehltext
    val zeit = if (eintrag.ueberfaellig) {
        res.getString(R.string.widget_ueberfaellig, zeitText)
    } else {
        zeitText
    }
    return if (eintrag.sollAnzahl > 1) {
        res.getString(R.string.widget_untertitel_verbindung, zeit, zaehltext)
    } else {
        zeit
    }
}
