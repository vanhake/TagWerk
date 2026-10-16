package de.vanhake.dayorganizer.ui.gemeinsam

/** Was ein Bearbeiten-Bildschirm beim Speichern getan hat -- die Liste zeigt danach den
 * passenden Toast. Gemeinsam fuer Aktivitaeten und Belohnungen. */
enum class SpeicherErgebnis { ANGELEGT, GEAENDERT }

/** Warum ein Speichern im Bearbeiten-Bildschirm scheiterte; der Screen zeigt dazu einen Hinweis. */
enum class SpeicherFehler { NICHT_MEHR_VORHANDEN, FEHLGESCHLAGEN }
