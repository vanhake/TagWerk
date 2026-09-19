package de.vanhake.dayorganizer.data.mapper

import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalTime

/** Flache Darstellung eines Zeitplans, wie sie in der Datenbank liegt. */
data class ZeitplanFelder(
    val typ: String,
    val zeiten: String = "",
    val wochentage: String = "",
    val intervallStunden: Int? = null,
    val fensterVon: String? = null,
    val fensterBis: String? = null,
    val zielAnzahl: Int? = null,
    val anstoss: String = "",
)

/**
 * Wandelt Zeitplaene in lesbare Spaltenwerte um und zurueck.
 * Lesbar deshalb, weil eine falsch gespeicherte Erinnerung sonst nur
 * mit Debugger zu finden waere.
 */
object ZeitplanKodierung {

    private const val FESTE_ZEITEN = "FESTE_ZEITEN"
    private const val INTERVALL = "INTERVALL"
    private const val TAGESZIEL = "TAGESZIEL"
    private const val WOCHENZIEL = "WOCHENZIEL"

    fun kodiere(zeitplan: Zeitplan): ZeitplanFelder = when (zeitplan) {
        is Zeitplan.FesteZeiten -> ZeitplanFelder(
            typ = FESTE_ZEITEN,
            zeiten = zeitplan.zeiten.joinToString(",") { it.toString() },
            wochentage = kodiereTage(zeitplan.wochentage),
        )

        is Zeitplan.Intervall -> ZeitplanFelder(
            typ = INTERVALL,
            wochentage = kodiereTage(zeitplan.wochentage),
            intervallStunden = zeitplan.alleNStunden,
            fensterVon = zeitplan.von.toString(),
            fensterBis = zeitplan.bis.toString(),
        )

        is Zeitplan.Tagesziel -> ZeitplanFelder(
            typ = TAGESZIEL,
            wochentage = kodiereTage(zeitplan.wochentage),
            zielAnzahl = zeitplan.anzahl,
            zeiten = zeitplan.anstossZeiten.joinToString(",") { it.toString() },
        )

        is Zeitplan.Wochenziel -> ZeitplanFelder(
            typ = WOCHENZIEL,
            zielAnzahl = zeitplan.anzahl,
            anstoss = zeitplan.anstoss.joinToString(",") { "${it.tag}@${it.zeit}" },
        )
    }

    fun dekodiere(felder: ZeitplanFelder): Zeitplan = when (felder.typ) {
        FESTE_ZEITEN -> Zeitplan.FesteZeiten(
            zeiten = dekodiereZeiten(felder.zeiten),
            wochentage = dekodiereTage(felder.wochentage),
        )

        INTERVALL -> Zeitplan.Intervall(
            alleNStunden = requireNotNull(felder.intervallStunden) { "Intervall ohne Stundenwert" },
            von = LocalTime.parse(requireNotNull(felder.fensterVon)),
            bis = LocalTime.parse(requireNotNull(felder.fensterBis)),
            wochentage = dekodiereTage(felder.wochentage),
        )

        TAGESZIEL -> Zeitplan.Tagesziel(
            anzahl = requireNotNull(felder.zielAnzahl) { "Tagesziel ohne Anzahl" },
            wochentage = dekodiereTage(felder.wochentage),
            anstossZeiten = dekodiereZeiten(felder.zeiten),
        )

        WOCHENZIEL -> Zeitplan.Wochenziel(
            anzahl = requireNotNull(felder.zielAnzahl) { "Wochenziel ohne Anzahl" },
            anstoss = felder.anstoss.split(",")
                .filter { it.isNotBlank() }
                .map {
                    val (tag, zeit) = it.split("@", limit = 2)
                    Anstoss(DayOfWeek.valueOf(tag), LocalTime.parse(zeit))
                },
        )

        else -> error("Unbekannter Zeitplantyp: ${felder.typ}")
    }

    fun kodiereSchwellen(schwellen: List<SerienSchwelle>): String =
        schwellen.joinToString(",") { "${it.perioden}:${it.bonus}" }

    fun dekodiereSchwellen(text: String): List<SerienSchwelle> =
        text.split(",").filter { it.isNotBlank() }.map {
            val (perioden, bonus) = it.split(":", limit = 2)
            SerienSchwelle(perioden.toInt(), bonus.toInt())
        }

    private fun kodiereTage(tage: Set<DayOfWeek>): String =
        tage.sorted().joinToString(",") { it.name }

    private fun dekodiereTage(text: String): Set<DayOfWeek> =
        text.split(",").filter { it.isNotBlank() }.map { DayOfWeek.valueOf(it) }.toSet()

    private fun dekodiereZeiten(text: String): List<LocalTime> =
        text.split(",").filter { it.isNotBlank() }.map { LocalTime.parse(it) }
}
