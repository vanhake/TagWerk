package de.vanhake.dayorganizer.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

sealed interface Belohnungsbedingung {
    /** Die genannte Aktivitaet muss eine Serie von mindestens [perioden] erreicht haben. */
    data class MindestSerie(val aktivitaetId: Long, val perioden: Int) : Belohnungsbedingung

    data class FruehestensAb(val datum: LocalDate) : Belohnungsbedingung
}

data class Belohnung(
    val id: Long = 0,
    val name: String,
    val beschreibung: String = "",
    val preisPunkte: Int,
    val bedingung: Belohnungsbedingung? = null,
    val archiviert: Boolean = false,
    /** Schluessel aus dem BelohnungSymbol-Katalog (App-Modul), z. B. "movie". */
    val symbol: String = "redeem",
)

/**
 * Name und Preis sind absichtlich kopiert statt verlinkt: die Historie muss
 * lesbar bleiben, auch wenn die Belohnung spaeter umbenannt wird.
 *
 * [storniertAm] ist gesetzt, wenn die Einloesung rueckgaengig gemacht wurde. Die Zeile
 * bleibt stehen (Korrekturen faelschen keine Historie); der Kontostand wird ueber eine
 * KORREKTUR-Buchung wiederhergestellt, siehe BelohnungEinloesenRueckgaengig.
 */
data class Einloesung(
    val id: Long = 0,
    val belohnungId: Long,
    val name: String,
    val preisPunkte: Int,
    val zeitpunkt: LocalDateTime,
    val storniertAm: LocalDateTime? = null,
)
