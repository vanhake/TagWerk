package de.vanhake.dayorganizer.ui.gemeinsam

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Gemeinsamer Toast-Zustand der Screens mit Hinweistoast. Rueckgaengig-Eintraege stapeln
 * sich, statt sich zu verdraengen: wer zwei Eintraege kurz nacheinander abhakt, kann beide
 * zuruecknehmen. Info-Eintraege (ohne Rueckgaengig) verdraengen dagegen nie ein offenes
 * Rueckgaengig, sie warten.
 *
 * Nicht threadsicher; die ViewModels rufen ihn nur vom Hauptthread (viewModelScope) auf.
 */
class ToastStapel(
    private val scope: CoroutineScope,
    private val dauerMs: Long = TOAST_DAUER_MS,
) {
    private class Offen(val text: String, val aktion: suspend () -> Unit) {
        var ablauf: Job? = null
    }

    private val offene = ArrayList<Offen>()
    private var infoText: String? = null
    private var infoJob: Job? = null

    // Laeuft nach jedem erfolgreichen Nehmen DOPPELTIPP_SPERRE_MS lang: ein schneller
    // Doppeltipp auf Rueckgaengig nimmt sonst zwei verschiedene gestapelte Aktionen zurueck.
    // Ein Job im scope statt einer Uhr, damit in Tests die virtuelle Zeit gilt.
    private var sperre: Job? = null

    private val _sichtbar = MutableStateFlow<Hinweistoast?>(null)
    val sichtbar: StateFlow<Hinweistoast?> = _sichtbar.asStateFlow()

    /** Mit [rueckgaengig] ein Rueckgaengig-Eintrag (stapelt sich), ohne ein Info-Eintrag. */
    fun zeigen(text: String, rueckgaengig: (suspend () -> Unit)? = null) {
        if (rueckgaengig != null) {
            val eintrag = Offen(text, rueckgaengig)
            // Jeder Eintrag laeuft ab seinem zeigen() ab, auch wenn ein juengerer ihn verdeckt.
            eintrag.ablauf = scope.launch {
                delay(dauerMs)
                offene.remove(eintrag)
                aktualisieren()
            }
            offene.add(eintrag)
        } else {
            // Ein neuer Info-Eintrag ersetzt den alten und startet dessen Zeit neu.
            infoText = text
            infoJob?.cancel()
            infoJob = null
        }
        aktualisieren()
    }

    /** Nimmt den obersten noch laufenden Rueckgaengig-Eintrag vom Stapel und liefert seine
     * Aktion; null, wenn keiner offen ist oder das letzte Nehmen weniger als
     * [DOPPELTIPP_SPERRE_MS] her ist. Fuehrt die Aktion NICHT aus. */
    fun rueckgaengigNehmen(): (suspend () -> Unit)? {
        if (sperre?.isActive == true) return null
        val eintrag = offene.removeLastOrNull() ?: return null
        eintrag.ablauf?.cancel()
        sperre = scope.launch { delay(DOPPELTIPP_SPERRE_MS) }
        aktualisieren()
        return eintrag.aktion
    }

    private fun aktualisieren() {
        val oben = offene.lastOrNull()
        val info = infoText
        when {
            oben != null -> {
                // Der Info-Eintrag wartet; seine Zeit beginnt erst, wenn er sichtbar wird.
                infoJob?.cancel()
                infoJob = null
                _sichtbar.value = Hinweistoast(oben.text, kannRueckgaengig = true)
            }
            info != null -> {
                if (infoJob == null) {
                    infoJob = scope.launch {
                        delay(dauerMs)
                        infoText = null
                        infoJob = null
                        aktualisieren()
                    }
                }
                _sichtbar.value = Hinweistoast(info, kannRueckgaengig = false)
            }
            else -> _sichtbar.value = null
        }
    }

    companion object {
        const val TOAST_DAUER_MS = 3_800L
        const val DOPPELTIPP_SPERRE_MS = 400L
    }
}
