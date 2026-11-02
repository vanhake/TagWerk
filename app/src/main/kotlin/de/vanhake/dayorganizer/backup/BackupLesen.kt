package de.vanhake.dayorganizer.backup

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Obergrenze fuer eine Backup-Datei. Echte Backups sind wenige hundert KB gross;
 * ohne Grenze liesse eine versehentlich gewaehlte riesige Datei (Video, Archiv)
 * die App beim Einlesen mit OutOfMemoryError abstuerzen.
 */
const val MAX_BACKUP_BYTES = 10 * 1024 * 1024

class BackupZuGross(val grenze: Int) :
    IOException("Backup-Datei ist groesser als ${grenze / (1024 * 1024)} MB")

/** Liest den Strom als UTF-8-Text, aber hoechstens [grenze] Bytes; sonst [BackupZuGross]. */
fun InputStream.backupTextLesen(grenze: Int = MAX_BACKUP_BYTES): String {
    val puffer = ByteArrayOutputStream()
    val block = ByteArray(8 * 1024)
    while (true) {
        val gelesen = read(block)
        if (gelesen < 0) break
        if (puffer.size() + gelesen > grenze) throw BackupZuGross(grenze)
        puffer.write(block, 0, gelesen)
    }
    return String(puffer.toByteArray(), Charsets.UTF_8)
}
