package de.vanhake.dayorganizer.ui.heute

import de.vanhake.dayorganizer.domain.model.FaelligkeitStatus
import de.vanhake.dayorganizer.platform.permission.Hinweis

data class HeuteEintrag(
    val faelligkeitId: Long,
    val aktivitaetName: String,
    val farbe: Int,
    val symbol: String,
    /** "08:00" bei Terminen, null bei Zaehlzielen. */
    val zeitText: String?,
    val sollAnzahl: Int,
    val istAnzahl: Int,
    val status: FaelligkeitStatus,
    val ueberfaellig: Boolean,
)

data class NaechsteBelohnung(
    val name: String,
    val preisPunkte: Int,
    val fehlendePunkte: Int,
)

data class HeuteZustand(
    val kontostand: Int = 0,
    val erledigtHeute: Int = 0,
    val gesamtHeute: Int = 0,
    val ueberfaellig: List<HeuteEintrag> = emptyList(),
    val anstehend: List<HeuteEintrag> = emptyList(),
    val spaeter: List<HeuteEintrag> = emptyList(),
    val abgeschlossen: List<HeuteEintrag> = emptyList(),
    val hinweise: List<Hinweis> = emptyList(),
    val naechsteBelohnung: NaechsteBelohnung? = null,
)
