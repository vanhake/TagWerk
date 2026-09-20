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
            zeiten = dekodiereZeiten(felder.zeiten, "zeiten"),
            wochentage = dekodiereTage(felder.wochentage, "wochentage"),
        )

        INTERVALL -> Zeitplan.Intervall(
            alleNStunden = requireNotNull(felder.intervallStunden) { "Intervall ohne Stundenwert" },
            von = parseZeit(requireNotNull(felder.fensterVon) { "Intervall ohne Fenster-Start" }, "Intervall.fensterVon"),
            bis = parseZeit(requireNotNull(felder.fensterBis) { "Intervall ohne Fenster-Ende" }, "Intervall.fensterBis"),
            wochentage = dekodiereTage(felder.wochentage, "wochentage"),
        )

        TAGESZIEL -> Zeitplan.Tagesziel(
            anzahl = requireNotNull(felder.zielAnzahl) { "Tagesziel ohne Anzahl" },
            wochentage = dekodiereTage(felder.wochentage, "wochentage"),
            anstossZeiten = dekodiereZeiten(felder.zeiten, "zeiten"),
        )

        WOCHENZIEL -> Zeitplan.Wochenziel(
            anzahl = requireNotNull(felder.zielAnzahl) { "Wochenziel ohne Anzahl" },
            anstoss = felder.anstoss.split(",")
                .filter { it.isNotBlank() }
                .map { dekodiereAnstoss(it) },
        )

        else -> error("Unbekannter Zeitplantyp: ${felder.typ}")
    }

    fun kodiereSchwellen(schwellen: List<SerienSchwelle>): String =
        schwellen.joinToString(",") { "${it.perioden}:${it.bonus}" }

    fun dekodiereSchwellen(text: String): List<SerienSchwelle> =
        text.split(",").filter { it.isNotBlank() }.map { eintrag ->
            val teile = eintrag.split(":", limit = 2)
            require(teile.size == 2) { "Ungueltiger Serienschwellen-Eintrag: \"$eintrag\"" }
            SerienSchwelle(
                perioden = parseZahl(teile[0], eintrag),
                bonus = parseZahl(teile[1], eintrag),
            )
        }

    private fun dekodiereAnstoss(eintrag: String): Anstoss {
        val teile = eintrag.split("@", limit = 2)
        require(teile.size == 2) { "Ungueltiger Anstoss-Eintrag: \"$eintrag\"" }
        return Anstoss(
            tag = parseTag(teile[0], "Anstoss"),
            zeit = parseZeit(teile[1], "Anstoss"),
        )
    }

    private fun kodiereTage(tage: Set<DayOfWeek>): String =
        tage.sorted().joinToString(",") { it.name }

    private fun dekodiereTage(text: String, fieldName: String): Set<DayOfWeek> =
        text.split(",").filter { it.isNotBlank() }.map { parseTag(it, fieldName) }.toSet()

    private fun dekodiereZeiten(text: String, fieldName: String): List<LocalTime> =
        text.split(",").filter { it.isNotBlank() }.map { parseZeit(it, fieldName) }

    /** Wandelt [text] in einen [DayOfWeek], mit [quelle] (Eintrag oder Feldname) in der Fehlermeldung. */
    private fun parseTag(text: String, quelle: String): DayOfWeek =
        try {
            DayOfWeek.valueOf(text)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Ungueltiger Wochentag \"$text\" in \"$quelle\"", e)
        }

    /** Wandelt [text] in eine [LocalTime], mit [quelle] (Eintrag oder Feldname) in der Fehlermeldung. */
    private fun parseZeit(text: String, quelle: String): LocalTime =
        try {
            LocalTime.parse(text)
        } catch (e: java.time.format.DateTimeParseException) {
            throw IllegalArgumentException("Ungueltige Uhrzeit \"$text\" in \"$quelle\"", e)
        }

    /** Wandelt [text] in eine Zahl, mit [quelle] (der ganze Eintrag) in der Fehlermeldung. */
    private fun parseZahl(text: String, quelle: String): Int =
        text.toIntOrNull() ?: throw IllegalArgumentException("Ungueltige Zahl \"$text\" in \"$quelle\"")
}
