package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Kurztext eines Zeitplans, 1:1 nach tageText()/planText() des Redesign-Prototyps
 * (docs/design/prototyp/DayOrganizer App.dc.html). Kotlin-Literale statt String-
 * Ressourcen, damit Liste, Bearbeiten-Zusammenfassung und Tests denselben Text ohne
 * Context bekommen -- gleiches Muster wie HeuteToastTexte.
 */
object PlanText {

    /** Index = DayOfWeek.ordinal (Montag = 0). */
    val WOCHENTAG_KUERZEL: List<String> = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

    private val WERKTAGE: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )

    private val UHRZEIT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun kuerzel(tag: DayOfWeek): String = WOCHENTAG_KUERZEL[tag.ordinal]

    fun uhrzeit(zeit: LocalTime): String = zeit.format(UHRZEIT_FORMAT)

    fun tage(wochentage: Set<DayOfWeek>): String = when {
        wochentage.size == DayOfWeek.entries.size -> "Täglich"
        wochentage == WERKTAGE -> "Mo–Fr"
        wochentage.isEmpty() -> "Keine Tage"
        else -> wochentage.sorted().joinToString(", ") { kuerzel(it) }
    }

    fun aus(zeitplan: Zeitplan): String = when (zeitplan) {
        is Zeitplan.FesteZeiten -> {
            val zeiten = if (zeitplan.zeiten.isEmpty()) "keine Uhrzeit" else uhrzeiten(zeitplan.zeiten)
            "${tage(zeitplan.wochentage)} · $zeiten"
        }
        is Zeitplan.Intervall ->
            "${tage(zeitplan.wochentage)} · alle ${zeitplan.alleNStunden} Std. " +
                "von ${uhrzeit(zeitplan.von)} bis ${uhrzeit(zeitplan.bis)}"
        is Zeitplan.Tagesziel -> buildString {
            append("${zeitplan.anzahl}× täglich")
            if (zeitplan.wochentage.size != DayOfWeek.entries.size) append(" · ${tage(zeitplan.wochentage)}")
            if (zeitplan.anstossZeiten.isNotEmpty()) append(" · Erinnerung ${uhrzeiten(zeitplan.anstossZeiten)}")
        }
        is Zeitplan.Wochenziel -> "${zeitplan.anzahl}× pro Woche"
    }

    private fun uhrzeiten(zeiten: List<LocalTime>): String = zeiten.joinToString(", ") { uhrzeit(it) }
}
