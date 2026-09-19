package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import javax.inject.Inject

/**
 * Die Selbstheilung der App: Faelligkeiten schliessen, Vorausblick auffuellen,
 * Alarm neu planen. Die Reihenfolge ist bewusst, aber derzeit nicht zwingend:
 * VerpassteAbschliessen arbeitet auf Tagen, nicht Zeitpunkten, daher beruehrt es
 * nicht, was gerade fuer heute angelegt wurde. Schliessen zuerst bleibt sicherer,
 * falls die Pruefung irgendwann zeitgenau wird.
 */
class WaechterAblauf(
    private val verpassteAbschliessen: VerpassteAbschliessen,
    private val materialisieren: FaelligkeitenMaterialisieren,
    private val alarmNeuPlanen: suspend () -> Unit,
) {

    @Inject
    constructor(
        verpassteAbschliessen: VerpassteAbschliessen,
        materialisieren: FaelligkeitenMaterialisieren,
        alarmPlaner: AlarmPlaner,
    ) : this(verpassteAbschliessen, materialisieren, { alarmPlaner.neuPlanen() })

    suspend operator fun invoke() {
        verpassteAbschliessen()
        materialisieren()
        alarmNeuPlanen()
    }
}
