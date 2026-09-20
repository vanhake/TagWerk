package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Belohnung
import de.vanhake.dayorganizer.domain.model.Belohnungsbedingung
import java.time.LocalDate

enum class Hindernis { ZU_WENIG_PUNKTE, SERIE_ZU_KURZ, ZU_FRUEH }

data class Einloesbarkeit(
    val moeglich: Boolean,
    val fehlendePunkte: Int,
    val hindernis: Hindernis?,
)

class BelohnungsPruefer {

    /**
     * @param serieDerBedingung aktuelle Serie der in der Bedingung genannten
     *   Aktivitaet. Ohne Serienbedingung ist der Wert bedeutungslos.
     */
    fun pruefe(
        belohnung: Belohnung,
        kontostand: Int,
        serieDerBedingung: Int,
        heute: LocalDate,
    ): Einloesbarkeit {
        val fehlend = (belohnung.preisPunkte - kontostand).coerceAtLeast(0)

        if (belohnung.archiviert) {
            return Einloesbarkeit(false, fehlend, hindernis = null)
        }
        if (fehlend > 0) {
            return Einloesbarkeit(false, fehlend, Hindernis.ZU_WENIG_PUNKTE)
        }

        return when (val bedingung = belohnung.bedingung) {
            null -> Einloesbarkeit(true, 0, null)

            is Belohnungsbedingung.MindestSerie ->
                if (serieDerBedingung >= bedingung.perioden) {
                    Einloesbarkeit(true, 0, null)
                } else {
                    Einloesbarkeit(false, 0, Hindernis.SERIE_ZU_KURZ)
                }

            is Belohnungsbedingung.FruehestensAb ->
                if (!heute.isBefore(bedingung.datum)) {
                    Einloesbarkeit(true, 0, null)
                } else {
                    Einloesbarkeit(false, 0, Hindernis.ZU_FRUEH)
                }
        }
    }
}
