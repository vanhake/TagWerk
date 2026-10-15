package de.vanhake.dayorganizer.ui.belohnungen

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import de.vanhake.dayorganizer.ui.theme.BELOHNUNG_SYMBOL_STANDARD
import java.time.LocalDate

enum class BedingungTyp { KEINE, MINDESTSERIE, FRUEHESTENS_AB }

enum class BelohnungFehler {
    NAME_LEER,
    PREIS_NICHT_POSITIV,
    SERIE_OHNE_AKTIVITAET,
    SERIE_NICHT_POSITIV,
    DATUM_FEHLT,
}

/**
 * Bearbeitbarer Zwischenstand. Seit dem Redesign (Plan 6) kommen Preis und Perioden aus
 * Steppern und das Datum aus einem Datumswaehler -- darum sind diese Felder typisiert,
 * wie bei AktivitaetEntwurf seit Plan 5. Die Bedingungsfelder bleiben beim Wechsel des
 * Bedingungstyps erhalten; gespeichert wird nur der gewaehlte Typ.
 */
data class BelohnungEntwurf(
    val id: Long = 0,
    val name: String = "",
    val beschreibung: String = "",
    val symbol: String = BELOHNUNG_SYMBOL_STANDARD,
    val preisPunkte: Int = 100,
    val bedingungTyp: BedingungTyp = BedingungTyp.KEINE,
    val bedingungAktivitaetId: Long? = null,
    val bedingungPerioden: Int = 7,
    val bedingungDatum: LocalDate? = null,
    val archiviert: Boolean = false,
) {

    /** Zehnerschritte; ein geladener Altpreis unter 10 bleibt stehen, bis der Stepper
     * bedient wird. */
    fun preisAendern(schritt: Int): BelohnungEntwurf =
        copy(preisPunkte = (preisPunkte + schritt * PREIS_SCHRITT).coerceAtLeast(PREIS_MIN))

    fun periodenAendern(schritt: Int): BelohnungEntwurf =
        copy(bedingungPerioden = (bedingungPerioden + schritt).coerceAtLeast(PERIODEN_MIN))

    fun pruefen(): List<BelohnungFehler> = buildList {
        if (name.isBlank()) add(BelohnungFehler.NAME_LEER)
        if (preisPunkte <= 0) add(BelohnungFehler.PREIS_NICHT_POSITIV)
        if (bedingungTyp == BedingungTyp.MINDESTSERIE) {
            if (bedingungAktivitaetId == null) add(BelohnungFehler.SERIE_OHNE_AKTIVITAET)
            if (bedingungPerioden <= 0) add(BelohnungFehler.SERIE_NICHT_POSITIV)
        }
        if (bedingungTyp == BedingungTyp.FRUEHESTENS_AB && bedingungDatum == null) {
            add(BelohnungFehler.DATUM_FEHLT)
        }
    }

    /** Nur aufrufen, wenn [pruefen] leer ist. */
    fun zuBelohnung(): Belohnung = Belohnung(
        id = id,
        name = name.trim(),
        beschreibung = beschreibung.trim(),
        preisPunkte = preisPunkte,
        bedingung = when (bedingungTyp) {
            BedingungTyp.KEINE -> null
            BedingungTyp.MINDESTSERIE -> Belohnungsbedingung.MindestSerie(
                aktivitaetId = requireNotNull(bedingungAktivitaetId),
                perioden = bedingungPerioden,
            )
            BedingungTyp.FRUEHESTENS_AB -> Belohnungsbedingung.FruehestensAb(requireNotNull(bedingungDatum))
        },
        archiviert = archiviert,
        symbol = symbol,
    )

    companion object {
        const val PREIS_SCHRITT = 10
        const val PREIS_MIN = 10
        const val PERIODEN_MIN = 1

        fun aus(belohnung: Belohnung): BelohnungEntwurf {
            val basis = BelohnungEntwurf(
                id = belohnung.id,
                name = belohnung.name,
                beschreibung = belohnung.beschreibung,
                symbol = belohnung.symbol,
                preisPunkte = belohnung.preisPunkte,
                archiviert = belohnung.archiviert,
            )
            return when (val b = belohnung.bedingung) {
                null -> basis
                is Belohnungsbedingung.MindestSerie -> basis.copy(
                    bedingungTyp = BedingungTyp.MINDESTSERIE,
                    bedingungAktivitaetId = b.aktivitaetId,
                    bedingungPerioden = b.perioden,
                )
                is Belohnungsbedingung.FruehestensAb -> basis.copy(
                    bedingungTyp = BedingungTyp.FRUEHESTENS_AB,
                    bedingungDatum = b.datum,
                )
            }
        }
    }
}
