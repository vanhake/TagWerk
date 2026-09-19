package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import javax.inject.Inject

/**
 * Die Selbstheilung der App. Reihenfolge ist bindend: erst abgelaufene
 * Faelligkeiten schliessen, dann den Vorausblick auffuellen, dann den
 * naechsten Alarm setzen.
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
