package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate

enum class BedingungTyp { KEINE, MINDESTSERIE, FRUEHESTENS_AB }

enum class BelohnungFehler {
    NAME_LEER,
    PREIS_NICHT_POSITIV,
    SERIE_OHNE_AKTIVITAET,
    SERIE_NICHT_POSITIV,
}

data class BelohnungEntwurf(
    val id: Long = 0,
    val name: String = "",
    val beschreibung: String = "",
    val preisText: String = "100",
    val bedingungTyp: BedingungTyp = BedingungTyp.KEINE,
    val bedingungAktivitaetId: Long? = null,
    val bedingungPeriodenText: String = "7",
    val bedingungDatum: LocalDate? = null,
) {

    private val preis: Int get() = preisText.toIntOrNull() ?: 0
    private val perioden: Int get() = bedingungPeriodenText.toIntOrNull() ?: 0

    fun pruefen(): List<BelohnungFehler> = buildList {
        if (name.isBlank()) add(BelohnungFehler.NAME_LEER)
        if (preis <= 0) add(BelohnungFehler.PREIS_NICHT_POSITIV)
        if (bedingungTyp == BedingungTyp.MINDESTSERIE) {
            if (bedingungAktivitaetId == null) add(BelohnungFehler.SERIE_OHNE_AKTIVITAET)
            if (perioden <= 0) add(BelohnungFehler.SERIE_NICHT_POSITIV)
        }
    }

    /** Nur aufrufen, wenn [pruefen] leer ist. */
    fun zuBelohnung(): Belohnung = Belohnung(
        id = id,
        name = name.trim(),
        beschreibung = beschreibung.trim(),
        preisPunkte = preis,
        bedingung = when (bedingungTyp) {
            BedingungTyp.KEINE -> null
            BedingungTyp.MINDESTSERIE -> Belohnungsbedingung.MindestSerie(
                aktivitaetId = requireNotNull(bedingungAktivitaetId),
                perioden = perioden,
            )
            BedingungTyp.FRUEHESTENS_AB -> Belohnungsbedingung.FruehestensAb(
                requireNotNull(bedingungDatum) { "Datumsbedingung ohne Datum" },
            )
        },
    )

    companion object {
        fun aus(belohnung: Belohnung): BelohnungEntwurf {
            val basis = BelohnungEntwurf(
                id = belohnung.id,
                name = belohnung.name,
                beschreibung = belohnung.beschreibung,
                preisText = belohnung.preisPunkte.toString(),
            )
            return when (val b = belohnung.bedingung) {
                null -> basis
                is Belohnungsbedingung.MindestSerie -> basis.copy(
                    bedingungTyp = BedingungTyp.MINDESTSERIE,
                    bedingungAktivitaetId = b.aktivitaetId,
                    bedingungPeriodenText = b.perioden.toString(),
                )
                is Belohnungsbedingung.FruehestensAb -> basis.copy(
                    bedingungTyp = BedingungTyp.FRUEHESTENS_AB,
                    bedingungDatum = b.datum,
                )
            }
        }
    }
}
