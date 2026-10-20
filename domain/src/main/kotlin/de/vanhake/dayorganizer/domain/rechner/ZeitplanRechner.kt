package de.vanhake.dayorganizer.domain.rechner

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

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
        is Zeitplan.Tagesziel -> tagesziel(aktivitaet, plan, von, bis)
        is Zeitplan.Wochenziel -> wochenziel(aktivitaet, plan, von, bis)
        is Zeitplan.Einmalig -> einmalig(aktivitaet, plan, von, bis)
    }

    /** Zeitpunkte, zu denen fuer diese Faelligkeit erinnert werden soll. */
    fun anstossZeitpunkteFuer(
        aktivitaet: Aktivitaet,
        faelligkeit: Faelligkeit,
    ): List<LocalDateTime> = when (val plan = aktivitaet.zeitplan) {
        is Zeitplan.FesteZeiten, is Zeitplan.Intervall ->
            listOfNotNull(faelligkeit.geplantFuer)

        is Zeitplan.Tagesziel ->
            plan.anstossZeiten.sorted().map { faelligkeit.periodeVon.atTime(it) }

        is Zeitplan.Wochenziel ->
            plan.anstoss
                .map { faelligkeit.periodeVon.with(
                    TemporalAdjusters.nextOrSame(it.tag),
                ).atTime(it.zeit) }
                .filter { !it.toLocalDate().isAfter(faelligkeit.periodeBis) }
                .sorted()

        is Zeitplan.Einmalig -> {
            val zeit = plan.erinnerungsZeit
            if (zeit == null) {
                emptyList()
            } else {
                einmaligErinnerungstage(faelligkeit.periodeVon, faelligkeit.periodeBis)
                    .map { it.atTime(zeit) }
            }
        }
    }

    /**
     * Erinnerungstage einer Einmalaufgabe von [start] bis [deadline], beide inklusive:
     * jede Woche am Wochentag der Deadline (also auch am Deadline-Tag selbst), in den
     * letzten vier Wochen zusaetzlich drei Tage nach diesem Wochentag. Rueckwaerts von der
     * Deadline gerechnet, damit der Rhythmus an ihr haengt und nicht am Anlegetag.
     */
    fun einmaligErinnerungstage(start: LocalDate, deadline: LocalDate): List<LocalDate> {
        val woechentlich = generateSequence(deadline) { it.minusWeeks(1) }
            .takeWhile { !it.isBefore(start) }
        val zusaetzlich = (0L until LETZTE_WOCHEN)
            .map { deadline.minusDays(ZWEITER_TAG_VOR_WOCHENTAG).minusWeeks(it) }
            .filter { !it.isBefore(start) }
        return (woechentlich + zusaetzlich).distinct().sorted().toList()
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

    private fun tagesziel(
        aktivitaet: Aktivitaet,
        plan: Zeitplan.Tagesziel,
        von: LocalDate,
        bis: LocalDate,
    ): List<Faelligkeit> = tageIm(von, bis)
        .filter { it.dayOfWeek in plan.wochentage }
        .map { tag ->
            Faelligkeit(
                aktivitaetId = aktivitaet.id,
                periodeVon = tag,
                periodeBis = tag,
                geplantFuer = null,
                sollAnzahl = plan.anzahl,
            )
        }

    private fun wochenziel(
        aktivitaet: Aktivitaet,
        plan: Zeitplan.Wochenziel,
        von: LocalDate,
        bis: LocalDate,
    ): List<Faelligkeit> {
        val ersterMontag = von.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return generateSequence(ersterMontag) { it.plusWeeks(1) }
            .takeWhile { !it.isAfter(bis) }
            .map { montag ->
                Faelligkeit(
                    aktivitaetId = aktivitaet.id,
                    periodeVon = montag,
                    periodeBis = montag.plusDays(6),
                    geplantFuer = null,
                    sollAnzahl = plan.anzahl,
                )
            }
            .toList()
    }

    /** Die eine Faelligkeit, sofern ihre Periode den Zeitraum [von]..[bis] beruehrt. Ihr
     * Beginn ist der Anlegetag: damit bleibt der Duplikat-Schluessel ueber alle Laeufe gleich,
     * und die Aufgabe steht ab dem ersten Tag in der Liste. */
    private fun einmalig(
        aktivitaet: Aktivitaet,
        plan: Zeitplan.Einmalig,
        von: LocalDate,
        bis: LocalDate,
    ): List<Faelligkeit> {
        val start = aktivitaet.erstelltAm
        if (plan.deadline.isBefore(start) || plan.deadline.isBefore(von) || start.isAfter(bis)) {
            return emptyList()
        }
        return listOf(
            Faelligkeit(
                aktivitaetId = aktivitaet.id,
                periodeVon = start,
                periodeBis = plan.deadline,
                geplantFuer = null,
                sollAnzahl = 1,
            ),
        )
    }

    private fun tageIm(von: LocalDate, bis: LocalDate): List<LocalDate> =
        generateSequence(von) { it.plusDays(1) }
            .takeWhile { !it.isAfter(bis) }
            .toList()

    private companion object {
        /** In den letzten vier Wochen vor der Deadline wird zweimal pro Woche erinnert. */
        const val LETZTE_WOCHEN = 4L

        /** Der zweite Wochentermin liegt drei Tage nach dem Wochentag der Deadline,
         * also vier Tage vor ihm (Freitag-Deadline: Freitag und Montag). */
        const val ZWEITER_TAG_VOR_WOCHENTAG = 4L
    }
}
