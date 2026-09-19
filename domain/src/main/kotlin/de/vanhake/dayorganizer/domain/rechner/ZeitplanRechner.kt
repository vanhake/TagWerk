package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.Duration
import java.time.LocalDate

/**
 * Bildet einen Zeitplan auf konkrete Faelligkeiten ab.
 * Einziger Ort im Projekt, an dem Zeitplantypen ausgewertet werden.
 */
class ZeitplanRechner {

    /** [von] und [bis] sind inklusive. */
    fun faelligkeitenFuer(
        aktivitaet: Aktivitaet,
        von: LocalDate,
        bis: LocalDate,
    ): List<Faelligkeit> = when (val plan = aktivitaet.zeitplan) {
        is Zeitplan.FesteZeiten -> festeZeiten(aktivitaet, plan, von, bis)
        is Zeitplan.Intervall -> intervall(aktivitaet, plan, von, bis)
        else -> emptyList()
    }

    private fun festeZeiten(
        aktivitaet: Aktivitaet,
        plan: Zeitplan.FesteZeiten,
        von: LocalDate,
        bis: LocalDate,
    ): List<Faelligkeit> = tageIm(von, bis)
        .filter { it.dayOfWeek in plan.wochentage }
        .flatMap { tag ->
            plan.zeiten.sorted().map { zeit ->
                Faelligkeit(
                    aktivitaetId = aktivitaet.id,
                    periodeVon = tag,
                    periodeBis = tag,
                    geplantFuer = tag.atTime(zeit),
                    sollAnzahl = 1,
                )
            }
        }

    private fun intervall(
        aktivitaet: Aktivitaet,
        plan: Zeitplan.Intervall,
        von: LocalDate,
        bis: LocalDate,
    ): List<Faelligkeit> {
        require(plan.alleNStunden > 0) { "Intervall muss positiv sein" }
        val fensterMinuten = Duration.between(plan.von, plan.bis).toMinutes()
        if (fensterMinuten < 0) return emptyList()
        val schrittMinuten = plan.alleNStunden.toLong() * 60
        return tageIm(von, bis)
            .filter { it.dayOfWeek in plan.wochentage }
            .flatMap { tag ->
                // Offset-Arithmetik statt LocalTime.plusHours: plusHours rechnet modulo 24
                // und wuerde bei Teilern von 24 (z.B. 8, 12, 24 Stunden) nie terminieren.
                generateSequence(0L) { it + schrittMinuten }
                    .takeWhile { it <= fensterMinuten }
                    .map { offset ->
                        Faelligkeit(
                            aktivitaetId = aktivitaet.id,
                            periodeVon = tag,
                            periodeBis = tag,
                            geplantFuer = tag.atTime(plan.von.plusMinutes(offset)),
                            sollAnzahl = 1,
                        )
                    }
                    .toList()
            }
    }

    private fun tageIm(von: LocalDate, bis: LocalDate): List<LocalDate> =
        generateSequence(von) { it.plusDays(1) }
            .takeWhile { !it.isAfter(bis) }
            .toList()
}
