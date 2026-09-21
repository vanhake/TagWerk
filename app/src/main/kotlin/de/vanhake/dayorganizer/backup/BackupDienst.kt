package de.vanhake.dayorganizer.backup

import androidx.room.withTransaction
import de.vanhake.dayorganizer.data.Einstellungen
import de.vanhake.dayorganizer.data.db.DayOrganizerDatenbank
import de.vanhake.dayorganizer.data.mapper.zuDomaene
import de.vanhake.dayorganizer.data.mapper.zuEntity
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.LocalDateTime

sealed interface ImportErgebnis {
    data class Erfolg(val anzahlAktivitaeten: Int) : ImportErgebnis
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
        val datei = BackupUmwandlung.zuDatei(
            erstelltAm = LocalDateTime.now(uhr),
            aktivitaeten = db.aktivitaetDao().alle().map { it.zuDomaene() },
            faelligkeiten = db.faelligkeitDao().alle().map { it.zuDomaene() },
            buchungen = db.buchungDao().alle().map { it.zuDomaene() },
            belohnungen = db.belohnungDao().alle().map { it.zuDomaene() },
            einloesungen = db.belohnungDao().alleEinloesungen().map { it.zuDomaene() },
            einstellungen = einstellungenLesen(),
        )
        ziel.bufferedWriter().use { it.write(BackupUmwandlung.zuText(datei)) }
    }

    /**
     * Ersetzt den gesamten Bestand. Mischen waere gefaehrlich: gleiche
     * Buchungs-Ids in beiden Bestaenden wuerden Punkte doppelt zaehlen.
     */
    override suspend fun importieren(quelle: InputStream): ImportErgebnis {
        val inhalt = try {
            BackupUmwandlung.ausText(quelle.bufferedReader().use { it.readText() })
        } catch (fehler: UnbekanntesSchema) {
            return ImportErgebnis.FalschesSchema(fehler.gefunden)
        } catch (fehler: Exception) {
            return ImportErgebnis.Unlesbar(fehler.message ?: fehler::class.simpleName.orEmpty())
        }

        // Eine Datei kann sich lesen lassen und trotzdem nicht in die Datenbank passen:
        // eine Faelligkeit, die auf eine fehlende Aktivitaet verweist, oder ein doppelter
        // schluessel. Das faellt erst beim Einfuegen auf -- innerhalb der Transaktion,
        // die dann zurueckrollt und den alten Bestand stehen laesst. Ohne diesen Fang
        // flog die Ausnahme ungefangen bis ins ViewModel und beendete die App.
        try {
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
        } catch (fehler: Exception) {
            return ImportErgebnis.Unlesbar(fehler.message ?: fehler::class.simpleName.orEmpty())
        }

        einstellungenSchreiben(inhalt.einstellungen)
        return ImportErgebnis.Erfolg(inhalt.aktivitaeten.size)
    }

    companion object {
        /** Konstruktion ohne DataStore, fuer den Instrumented-Test. */
        fun fuerTest(db: DayOrganizerDatenbank): BackupDienst {
            var gehalten = Einstellungen()
            return BackupDienst(
                db = db,
                einstellungenLesen = { gehalten },
                einstellungenSchreiben = { gehalten = it },
                uhr = Clock.systemDefaultZone(),
            )
        }
    }
}
