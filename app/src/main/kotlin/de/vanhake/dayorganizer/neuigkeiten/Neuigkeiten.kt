package de.vanhake.dayorganizer.neuigkeiten

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Ein Eintrag aus assets/changelog.json. Die id steigt mit jedem Eintrag und ist unabhaengig
 * von der Versionsnummer (die vergibt die CI); die App merkt sich die hoechste gesehene id.
 */
@Serializable
data class NeuigkeitenEintrag(
    val id: Int,
    /** ISO-Datum, z. B. 2026-11-23. */
    @SerialName("date") val datum: String,
    @SerialName("changes") val aenderungen: List<String>,
)

@Serializable
private data class NeuigkeitenDatei(val entries: List<NeuigkeitenEintrag>)

object Neuigkeiten {
    private val json = Json { ignoreUnknownKeys = true }

    fun parsen(text: String): List<NeuigkeitenEintrag> =
        json.decodeFromString<NeuigkeitenDatei>(text).entries.sortedByDescending { it.id }

    /**
     * Was die Infokarte zeigen soll, neueste zuerst.
     *
     * - zuletztGesehen gesetzt: alle Eintraege danach.
     * - Noch nie gesehen, aber ein Update (die App lief schon vor dieser Funktion): nur der neueste.
     * - Neuinstallation: nichts.
     */
    fun anzuzeigen(
        eintraege: List<NeuigkeitenEintrag>,
        zuletztGesehen: Int?,
        istUpdate: Boolean,
    ): List<NeuigkeitenEintrag> {
        val sortiert = eintraege.sortedByDescending { it.id }
        return when {
            zuletztGesehen != null -> sortiert.filter { it.id > zuletztGesehen }
            istUpdate -> sortiert.take(1)
            else -> emptyList()
        }
    }

    fun neuesteId(eintraege: List<NeuigkeitenEintrag>): Int? = eintraege.maxOfOrNull { it.id }

    /** 2026-11-23 -> 23.11.2026 */
    fun datumFormatieren(isoDatum: String): String {
        val (j, m, t) = DATUM.matchEntire(isoDatum)?.destructured ?: return isoDatum
        return "$t.$m.$j"
    }

    /** Fehler in der Datei, fuer den Test, der changelog.json prueft. Leer = in Ordnung. */
    fun probleme(eintraege: List<NeuigkeitenEintrag>): List<String> = buildList {
        if (eintraege.isEmpty()) add("keine Eintraege")
        eintraege.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { add("id $it doppelt") }
        eintraege.forEach { e ->
            if (e.id < 1) add("id ${e.id} muss groesser als 0 sein")
            if (!DATUM.matches(e.datum)) add("id ${e.id}: Datum '${e.datum}' nicht im Format JJJJ-MM-TT")
            if (e.aenderungen.isEmpty() || e.aenderungen.any { it.isBlank() }) add("id ${e.id}: leere Aenderung")
        }
    }

    private val DATUM = Regex("""(\d{4})-(\d{2})-(\d{2})""")
}
