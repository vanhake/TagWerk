package de.vanhake.dayorganizer.domainuse

import de.vanhake.dayorganizer.data.EinstellungenSpeicher
import de.vanhake.dayorganizer.platform.alarm.AlarmPlaner
import de.vanhake.dayorganizer.widget.WidgetAktualisierung
import java.time.Clock
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Die Selbstheilung der App: Faelligkeiten schliessen, Vorausblick auffuellen,
 * Alarm neu planen. Die Reihenfolge ist bewusst, aber derzeit nicht zwingend:
 * VerpassteAbschliessen arbeitet auf Tagen, nicht Zeitpunkten, daher beruehrt es
 * nicht, was gerade fuer heute angelegt wurde. Schliessen zuerst bleibt sicherer,
 * falls die Pruefung irgendwann zeitgenau wird.
 *
 * [erfolgMerken] laeuft ganz am Ende, nach dem Neuplanen: nur ein vollstaendiger Lauf
 * zaehlt als Erfolg. Ohne diesen Zeitstempel gaebe es keine Moeglichkeit, einen dauerhaft
 * scheiternden Waechter (WaechterWorker.doWork gibt nach drei Versuchen still auf) dem
 * Nutzer ueberhaupt anzuzeigen -- WAECHTER_HAENGT in BerechtigungsHinweise baut darauf auf.
 */
class WaechterAblauf(
    private val verpassteAbschliessen: VerpassteAbschliessen,
    private val materialisieren: FaelligkeitenMaterialisieren,
    private val alarmNeuPlanen: suspend () -> Unit,
    private val erfolgMerken: suspend () -> Unit = {},
    private val widgetAnstossen: suspend () -> Unit = {},
) {

    @Inject
    constructor(
        verpassteAbschliessen: VerpassteAbschliessen,
        materialisieren: FaelligkeitenMaterialisieren,
        alarmPlaner: AlarmPlaner,
        einstellungen: EinstellungenSpeicher,
        uhr: Clock,
        widgetAktualisierung: WidgetAktualisierung,
    ) : this(
        verpassteAbschliessen,
        materialisieren,
        { alarmPlaner.neuPlanen() },
        { einstellungen.waechterErfolgMerken(LocalDateTime.now(uhr)) },
        { widgetAktualisierung.anstossen() },
    )

    suspend operator fun invoke() {
        verpassteAbschliessen()
        materialisieren()
        alarmNeuPlanen()
        erfolgMerken()
        widgetAnstossen()
    }
}
