package de.vanhake.dayorganizer.backup

import androidx.room.withTransaction
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import de.vanhake.dayorganizer.data.mapper.zuDomaene
import de.vanhake.dayorganizer.data.mapper.zuEntity
import de.vanhake.dayorganizer.util.versuchen
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDateTime

sealed interface ImportErgebnis {
    /**
     * [einstellungenUebernommen] ist false, wenn der Datenbestand erfolgreich ersetzt
     * wurde, das Schreiben der Einstellungen danach aber gescheitert ist -- der
     * Import darf das nicht als vollstaendigen Erfolg verschweigen (Review-Fund W1).
     */
    data class Erfolg(val anzahlAktivitaeten: Int, val einstellungenUebernommen: Boolean = true) : ImportErgebnis
    data class FalschesSchema(val gefunden: Int) : ImportErgebnis
    data class Unlesbar(val grund: String) : ImportErgebnis
}

/**
 * Schmale Schnittstelle, die das ViewModel benutzt, damit dessen bestehende
 * Unit-Tests ohne Datenbank weiterlaufen -- ein Testdouble implementiert nur
 * diese zwei Methoden statt des ganzen [BackupDienst].
 */
interface Backup {
    suspend fun exportieren(ziel: OutputStream)
    suspend fun importieren(quelle: InputStream): ImportErgebnis
}

// Bewusst ohne @Inject: der Konstruktor nimmt Funktionen, die Hilt nicht aufloesen
// kann, und DatenModul stellt den Dienst per @Provides bereit. Beides zugleich waere
// fuer Dagger eine doppelte Bindung.
class BackupDienst(
    private val db: DayOrganizerDatenbank,
    private val einstellungenLesen: suspend () -> Einstellungen,
    private val einstellungenSchreiben: suspend (Einstellungen) -> Unit,
    private val uhr: Clock,
) : Backup {

    // Lesen und Schreiben des Textes liegen in BackupUmwandlung (zuText/ausText), nicht
    // hier: dort ist festgelegt, dass die Schemaversion VOR dem strengen Einlesen
    // geprueft wird. Eine eigene Json-Instanz an dieser Stelle waere eine zweite
    // Wahrheit darueber, wie ein Backup aussieht.
    override suspend fun exportieren(ziel: OutputStream) {
        // Die Einstellungen liegen im DataStore, nicht in der Room-Datenbank, und
        // gehoeren deshalb nicht in dieselbe Transaktion -- nur die fuenf Tabellen
        // muessen als in sich stimmiger Schnappschuss gelesen werden.
        val einstellungen = einstellungenLesen()
        val datei = db.withTransaction {
            BackupUmwandlung.zuDatei(
                erstelltAm = LocalDateTime.now(uhr),
                aktivitaeten = db.aktivitaetDao().alle().map { it.zuDomaene() },
                faelligkeiten = db.faelligkeitDao().alle().map { it.zuDomaene() },
                buchungen = db.buchungDao().alle().map { it.zuDomaene() },
                belohnungen = db.belohnungDao().alle().map { it.zuDomaene() },
                einloesungen = db.belohnungDao().alleEinloesungen().map { it.zuDomaene() },
                einstellungen = einstellungen,
            )
        }
        ziel.bufferedWriter().use { it.write(BackupUmwandlung.zuText(datei)) }
    }

    /**
     * Ersetzt den gesamten Bestand. Mischen waere gefaehrlich: gleiche
     * Buchungs-Ids in beiden Bestaenden wuerden Punkte doppelt zaehlen.
     */
    override suspend fun importieren(quelle: InputStream): ImportErgebnis {
        // versuchen statt eines eigenen catch (fehler: Exception): ein Abbruch waehrend
        // des Lesens/Parsens muss weiterfliegen statt als "Unlesbar" misszudeuten
        // (Review-Fund W1) -- sonst liesse sich ein Import nie sauber verlassen.
        val inhalt = versuchen {
            BackupUmwandlung.ausText(quelle.use { it.backupTextLesen() })
        }.getOrElse { fehler ->
            return if (fehler is UnbekanntesSchema) {
                ImportErgebnis.FalschesSchema(fehler.gefunden)
            } else {
                ImportErgebnis.Unlesbar(fehler.message ?: fehler::class.simpleName.orEmpty())
            }
        }

        // Eine Datei kann sich lesen lassen und trotzdem nicht in die Datenbank passen:
        // eine Faelligkeit, die auf eine fehlende Aktivitaet verweist, oder ein doppelter
        // schluessel. Das faellt erst beim Einfuegen auf -- innerhalb der Transaktion,
        // die dann zurueckrollt und den alten Bestand stehen laesst. Ohne diesen Fang
        // flog die Ausnahme ungefangen bis ins ViewModel und beendete die App. versuchen
        // statt catch (fehler: Exception): ein Abbruch hier darf nicht als "Unlesbar"
        // gemeldet werden (Review-Fund W1) -- die Transaktion ist ohnehin zurueckgerollt,
        // an der Datenbank hat sich also noch nichts geaendert.
        versuchen {
            db.withTransaction {
                // Reihenfolge wegen der Fremdschluessel: Kinder vor Eltern loeschen.
                db.erinnerungDao().alleLoeschen()
                db.faelligkeitDao().alleLoeschen()
                db.aktivitaetDao().alleLoeschen()
                db.buchungDao().alleLoeschen()
                db.belohnungDao().alleEinloesungenLoeschen()
                db.belohnungDao().alleLoeschen()

                db.aktivitaetDao().alleEinfuegen(inhalt.aktivitaeten.map { it.zuEntity() })
                db.faelligkeitDao().alleEinfuegen(inhalt.faelligkeiten.map { it.zuEntity() })
                db.buchungDao().alleEinfuegen(inhalt.buchungen.map { it.zuEntity() })
                db.belohnungDao().alleEinfuegen(inhalt.belohnungen.map { it.zuEntity() })
                db.belohnungDao().alleEinloesungenEinfuegen(inhalt.einloesungen.map { it.zuEntity() })
            }
        }.onFailure { fehler ->
            return ImportErgebnis.Unlesbar(fehler.message ?: fehler::class.simpleName.orEmpty())
        }

        // Der Datenbestand ist an dieser Stelle bereits vollstaendig ersetzt und
        // committet. Scheitert nur noch das Schreiben der Einstellungen -- etwa eine
        // volle Platte im DataStore --, darf der Dienst das nicht ungefangen werfen
        // (Vorgabe: nie eine Ausnahme) und der Aufrufer darf nicht "Datei unlesbar"
        // lesen, obwohl die Daten laengst uebernommen sind (Review-Fund W1). Ein Abbruch
        // ist davon ausgenommen: versuchen laesst ihn durch, statt ihn hier zu einem
        // stillen einstellungenUebernommen = false zu verwaschen -- der Aufrufer (Review-
        // Fund W1: EinstellungenViewModel.importBestaetigen) faengt den Commit-Fall
        // ohnehin per NonCancellable ab, bevor diese Zeile ueberhaupt erreicht wird.
        val einstellungenUebernommen = versuchen {
            einstellungenSchreiben(inhalt.einstellungen)
        }.isSuccess
        return ImportErgebnis.Erfolg(inhalt.aktivitaeten.size, einstellungenUebernommen)
    }
}
