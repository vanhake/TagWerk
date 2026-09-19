package de.vanhake.dayorganizer.ui.aktivitaeten

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Anstoss
import de.vanhake.dayorganizer.domain.model.SerienSchwelle
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

enum class ZeitplanTyp { FESTE_ZEITEN, INTERVALL, TAGESZIEL, WOCHENZIEL }

enum class EntwurfFehler {
    NAME_LEER,
    PUNKTE_NICHT_POSITIV,
    KEINE_ZEIT,
    KEIN_WOCHENTAG,
    ZIEL_NICHT_POSITIV,
    INTERVALL_UNGUELTIG,
}

/**
 * Bearbeitbarer Zwischenstand. Zahlen bleiben Text, damit ein halb getipptes
 * Feld die Eingabe nicht zurueckspringen laesst.
 */
data class AktivitaetEntwurf(
    val id: Long = 0,
    val name: String = "",
    val notiz: String = "",
    val farbe: Int = 0xFF4CAF50.toInt(),
    val basisPunkteText: String = "10",
    val typ: ZeitplanTyp = ZeitplanTyp.FESTE_ZEITEN,
    val zeiten: List<LocalTime> = emptyList(),
    val wochentage: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val intervallStundenText: String = "3",
    val fensterVon: LocalTime = LocalTime.of(8, 0),
    val fensterBis: LocalTime = LocalTime.of(22, 0),
    val zielAnzahlText: String = "3",
    val anstoss: List<Anstoss> = emptyList(),
    val kulanzMinutenText: String = "60",
    val puenktlichkeitsBonusText: String = "5",
    val serienSchwellen: List<SerienSchwelle> = listOf(SerienSchwelle(7, 10)),
    val nachfassAnzahlText: String = "2",
    val nachfassAbstandText: String = "15",
) {

    private val basisPunkte: Int get() = basisPunkteText.toIntOrNull() ?: 0
    private val intervallStunden: Int get() = intervallStundenText.toIntOrNull() ?: 0
    private val zielAnzahl: Int get() = zielAnzahlText.toIntOrNull() ?: 0

    fun pruefen(): List<EntwurfFehler> = buildList {
        if (name.isBlank()) add(EntwurfFehler.NAME_LEER)
        if (basisPunkte <= 0) add(EntwurfFehler.PUNKTE_NICHT_POSITIV)

        when (typ) {
            ZeitplanTyp.FESTE_ZEITEN -> {
                if (zeiten.isEmpty()) add(EntwurfFehler.KEINE_ZEIT)
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.INTERVALL -> {
                if (intervallStunden <= 0 || !fensterVon.isBefore(fensterBis)) {
                    add(EntwurfFehler.INTERVALL_UNGUELTIG)
                }
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.TAGESZIEL -> {
                if (zielAnzahl <= 0) add(EntwurfFehler.ZIEL_NICHT_POSITIV)
                if (wochentage.isEmpty()) add(EntwurfFehler.KEIN_WOCHENTAG)
            }

            ZeitplanTyp.WOCHENZIEL -> {
                if (zielAnzahl <= 0) add(EntwurfFehler.ZIEL_NICHT_POSITIV)
            }
        }
    }

    /** Nur aufrufen, wenn [pruefen] leer ist. */
    fun zuAktivitaet(erstelltAm: LocalDate): Aktivitaet = Aktivitaet(
        id = id,
        name = name.trim(),
        notiz = notiz.trim(),
        farbe = farbe,
        basisPunkte = basisPunkte,
        zeitplan = when (typ) {
            ZeitplanTyp.FESTE_ZEITEN -> Zeitplan.FesteZeiten(zeiten.sorted(), wochentage)
            ZeitplanTyp.INTERVALL -> Zeitplan.Intervall(
                intervallStunden, fensterVon, fensterBis, wochentage,
            )
            ZeitplanTyp.TAGESZIEL -> Zeitplan.Tagesziel(zielAnzahl, wochentage, zeiten.sorted())
            ZeitplanTyp.WOCHENZIEL -> Zeitplan.Wochenziel(zielAnzahl, anstoss)
        },
        kulanzMinuten = kulanzMinutenText.toIntOrNull() ?: 60,
        puenktlichkeitsBonus = puenktlichkeitsBonusText.toIntOrNull() ?: 0,
        serienSchwellen = serienSchwellen,
        nachfassAnzahl = nachfassAnzahlText.toIntOrNull() ?: 0,
        nachfassAbstandMinuten = nachfassAbstandText.toIntOrNull() ?: 15,
        erstelltAm = erstelltAm,
    )

    companion object {
        fun aus(aktivitaet: Aktivitaet): AktivitaetEntwurf {
            val basis = AktivitaetEntwurf(
                id = aktivitaet.id,
                name = aktivitaet.name,
                notiz = aktivitaet.notiz,
                farbe = aktivitaet.farbe,
                basisPunkteText = aktivitaet.basisPunkte.toString(),
                kulanzMinutenText = aktivitaet.kulanzMinuten.toString(),
                puenktlichkeitsBonusText = aktivitaet.puenktlichkeitsBonus.toString(),
                serienSchwellen = aktivitaet.serienSchwellen,
                nachfassAnzahlText = aktivitaet.nachfassAnzahl.toString(),
                nachfassAbstandText = aktivitaet.nachfassAbstandMinuten.toString(),
            )
            return when (val plan = aktivitaet.zeitplan) {
                is Zeitplan.FesteZeiten -> basis.copy(
                    typ = ZeitplanTyp.FESTE_ZEITEN,
                    zeiten = plan.zeiten,
                    wochentage = plan.wochentage,
                )
                is Zeitplan.Intervall -> basis.copy(
                    typ = ZeitplanTyp.INTERVALL,
                    intervallStundenText = plan.alleNStunden.toString(),
                    fensterVon = plan.von,
                    fensterBis = plan.bis,
                    wochentage = plan.wochentage,
                )
                is Zeitplan.Tagesziel -> basis.copy(
                    typ = ZeitplanTyp.TAGESZIEL,
                    zielAnzahlText = plan.anzahl.toString(),
                    wochentage = plan.wochentage,
                    zeiten = plan.anstossZeiten,
                )
                is Zeitplan.Wochenziel -> basis.copy(
                    typ = ZeitplanTyp.WOCHENZIEL,
                    zielAnzahlText = plan.anzahl.toString(),
                    anstoss = plan.anstoss,
                )
            }
        }
    }
}
