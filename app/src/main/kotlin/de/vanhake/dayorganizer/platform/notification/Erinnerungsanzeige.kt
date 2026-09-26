package de.vanhake.dayorganizer.platform.notification

import de.vanhake.dayorganizer.domain.model.Aktivitaet
import de.vanhake.dayorganizer.domain.model.Faelligkeit

/**
 * Trennt das Anzeigen einer Erinnerung von der Entscheidung, wann erinnert
 * wird. Die Textgestaltung gehoert in die Android-Implementierung, damit der
 * Use Case in Task 15 ohne Ressourcen und ohne Emulator pruefbar bleibt.
 */
interface Erinnerungsanzeige {
    fun zeige(faelligkeit: Faelligkeit, aktivitaet: Aktivitaet)
    fun verstecke(faelligkeitId: Long)

    /**
     * Nach einem Backup-Import koennte dieselbe faelligkeitId jetzt zu einer
     * ganz anderen Faelligkeit gehoeren -- eine alte Benachrichtigung mit
     * "Erledigt" haekte dann das Falsche ab. Deshalb verschwinden nach einem
     * erfolgreichen Import alle Benachrichtigungen der App auf einmal.
     */
    fun alleVerstecken()
}
