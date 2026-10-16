package de.vanhake.dayorganizer.widget

import de.vanhake.dayorganizer.domain.model.Faelligkeit
import de.vanhake.dayorganizer.domainuse.ErledigungsProtokoll
import de.vanhake.dayorganizer.util.versuchen
import java.time.Clock
import java.time.LocalDate

enum class WidgetAbhakErgebnis { GEBUCHT, ANDERER_TAG, NICHT_OFFEN, UNBEKANNT, FEHLER }

/**
 * Ablauf eines Tipps auf einen Widget-Eintrag. Funktionen statt Abhaengigkeiten, damit
 * der Ablauf ohne Hilt und Glance testbar ist.
 *
 * Ein Eintrag von gestern kann auf einem nicht neu gezeichneten Widget noch stehen
 * (Mitternacht ohne Neuzeichnen); ihn zu buchen waere fuer den Nutzer ueberraschend.
 * Statt dessen wird nur neu gezeichnet.
 */
class WidgetAbhaken(
    private val faelligkeitLesen: suspend (Long) -> Faelligkeit?,
    private val erledigen: suspend (Long) -> ErledigungsProtokoll,
    private val nachbereiten: suspend (Long) -> Unit,
    private val widgetAnstossen: suspend () -> Unit,
    private val uhr: Clock,
) {

    suspend operator fun invoke(faelligkeitId: Long): WidgetAbhakErgebnis {
        val ergebnis = ausfuehren(faelligkeitId)
        // Bei jedem Ergebnis: auch ein veralteter oder fehlgeschlagener Tipp soll das Bild
        // auf den wahren Stand bringen.
        widgetAnstossen()
        return ergebnis
    }

    private suspend fun ausfuehren(faelligkeitId: Long): WidgetAbhakErgebnis {
        val faelligkeit = faelligkeitLesen(faelligkeitId) ?: return WidgetAbhakErgebnis.UNBEKANNT
        if (!WidgetTakt.gehoertZuTag(faelligkeit, LocalDate.now(uhr))) return WidgetAbhakErgebnis.ANDERER_TAG

        val protokoll = versuchen { erledigen(faelligkeitId) }.getOrElse { return WidgetAbhakErgebnis.FEHLER }
        val ergebnis = when (protokoll) {
            is ErledigungsProtokoll.Gebucht -> WidgetAbhakErgebnis.GEBUCHT
            ErledigungsProtokoll.NichtOffen, ErledigungsProtokoll.Unbekannt -> WidgetAbhakErgebnis.NICHT_OFFEN
        }
        // Wie die App: bei jedem Ausgang die Erinnerung verstecken und neu planen.
        versuchen { nachbereiten(faelligkeitId) }
        return ergebnis
    }
}
