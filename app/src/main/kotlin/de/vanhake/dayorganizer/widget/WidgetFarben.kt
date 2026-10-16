package de.vanhake.dayorganizer.widget

import de.vanhake.dayorganizer.data.ThemeModus
import de.vanhake.dayorganizer.ui.theme.DayOrganizerFarben
import de.vanhake.dayorganizer.ui.theme.DunkelFarben
import de.vanhake.dayorganizer.ui.theme.HellFarben

/**
 * Farbsatz des Widgets nach der App-Einstellung. Glance hat keinen Zugriff auf
 * LocalDayOrganizerFarben (kein Compose-Theme in RemoteViews); die Werte sind aber
 * dieselben wie in der App, deshalb entsteht kein zweiter Farbsatz.
 */
fun widgetFarben(modus: ThemeModus): DayOrganizerFarben =
    if (modus == ThemeModus.DUNKEL) DunkelFarben else HellFarben
