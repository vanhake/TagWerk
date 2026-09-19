package de.vanhake.dayorganizer.domain

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Zeitplan
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")

fun festeUhr(zeitpunkt: LocalDateTime, zone: ZoneId = BERLIN): Clock =
    Clock.fixed(zeitpunkt.atZone(zone).toInstant(), zone)

fun aktivitaet(
    id: Long = 1,
    name: String = "Tabletten",
    basisPunkte: Int = 10,
    zeitplan: Zeitplan,
    kulanzMinuten: Int = 60,
    puenktlichkeitsBonus: Int = 5,
) = Aktivitaet(
    id = id,
    name = name,
    farbe = 0xFF4CAF50.toInt(),
    basisPunkte = basisPunkte,
    zeitplan = zeitplan,
    kulanzMinuten = kulanzMinuten,
    puenktlichkeitsBonus = puenktlichkeitsBonus,
    erstelltAm = LocalDate.of(2026, 1, 1),
)
