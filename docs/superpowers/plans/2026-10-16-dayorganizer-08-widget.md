# DayOrganizer Redesign 5: Homescreen-Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das Glance-Widget „Heute“ sieht aus wie im Redesign-Prototyp. Es zeigt eine Kopfzeile mit Fortschritt und Punktestand und darunter Kacheln mit Symbol, Name, Untertitel und Haken. Die Zahl der Zeilen richtet sich nach der Widgethöhe, und die Farben folgen der Hell/Dunkel-Einstellung der App. Danach werden README und HANDOFF auf den Stand von Plan 4–8 gebracht.

**Architecture:**
- **Logik in reinen Funktionen** (`widget/WidgetZustand.kt`, `widget/WidgetMasse.kt`): Zusammenstellung des Zustands (Fortschritt, Leerhinweis, offene Einträge mit Symbol und Wochenziel-Kennung), Untertiteltext und die Zahl der Zeilen, die in eine gegebene Höhe passen.
- **Farben:** Glance kennt kein `DayOrganizerTheme`. Das Widget liest `ThemeModus` aus dem `EinstellungenSpeicher` und wählt `HellFarben` oder `DunkelFarben`. Das sind dieselben Objekte wie in der App, jede Farbe wird in einen `ColorProvider` verpackt. Schaltet der Nutzer in Optionen um, stößt `EinstellungenViewModel` das Widget an.
- **Symbole:** Glance kann keine `ImageVector` zeichnen. Jedes Aktivitätssymbol wird als Vector-Drawable abgelegt (Material Icons Filled, dieselben Glyphen wie `aktivitaetSymbol()`) und zur Laufzeit per `ColorFilter.tint` eingefärbt. Den Haken-Ring zeichnet ein eigenes Drawable, weil Glance keinen Rahmen-Modifier hat.

**Tech Stack:** Kotlin, Glance `1.1.1` (`glance-appwidget`), Hilt (EntryPoint `WidgetZugang`), DataStore, Robolectric `4.14.1` (Ressourcen-Tests), Turbine. Keine neuen Abhängigkeiten.

**Spec:**
- `docs/superpowers/specs/2026-09-18-dayorganizer-design.md` (fachliches Modell, Widget-Abschnitt).
- Design-Quelle `docs/design/prototyp/DayOrganizer App.dc.html`: Markup-Block `showWidget`, dazu in `renderVals()` die Werte `widgetOpen`, `widgetItems`, `hasMore`/`moreText`, `widgetEmpty` und in `mk()` die Werte `sub` und `subColor`. Die Farbrollen stehen in der Kopfzeile (`data-theme`, hell) und in Zeile 19 (`[data-theme="dark"]`).
- Abgestimmte Entscheidungen (16.10.2026):
  1. Hell/Dunkel folgt der **App-Einstellung**, nicht dem System-Nachtmodus.
  2. **Höchstens 4 Einträge**, bei kleinerer Widgethöhe entsprechend weniger, der Rest erscheint als „+ n weitere“.
  3. **Untertitel wie im Prototyp:** „08:00 · überfällig“, „1 von 3 heute“, „2 von 3 diese Woche“, „08:00 · 1 von 2 heute“.
  4. README und HANDOFF werden als letzter Task mitgezogen. Alle übrigen Backlog-Punkte kommen in einen eigenen Aufräumplan.

## Global Constraints

- Minimale API-Version bleibt 33, Ziel-API 35. Keine neue Abhängigkeit, keine neue Berechtigung. Netzwerk nur einmalig in Task 2 zum Herunterladen der Icon-SVGs (siehe dort), nie zur Laufzeit der App.
- **Keine Änderung an Datenmodell, Room-Schema oder Backup-Schema.** Datenbank Version 5, Backup-Schema 3.
- Deutsche Bezeichner. In Kotlin-Kommentaren werden Umlaute ausgeschrieben (`ae`, `oe`, `ue`); `strings.xml` und Kotlin-String-Literale behalten echte Umlaute.
- Nutzersichtbare Texte stehen in `strings.xml`, auch Trenner wie „ · “ (über Formatstrings).
- **Glance-Grenzen:** kein Farbverlauf, kein `backdrop-filter`, kein Schatten, keine eigene Schrift (Manrope geht in RemoteViews nicht → Systemschrift), `FontWeight` nur `Normal`/`Medium`/`Bold`. Prototyp-Gewicht 800/700 → `Bold`, 600 → `Medium`. Abstände zwischen Kindern über `Spacer`, weil Glance kein `spacedBy` kennt.
- Farben im Widget nur aus `widgetFarben(modus)` (Task 3), keine Literale wie `Color(0xFFF5F5F5)` oder `Color.Black` mehr.
- Prototyp-Werte des Widgets (Block `showWidget`):
  - Karte: Fläche `surface`, Innenabstand 16 dp, Abstand zwischen Kopf, Zeilen und Fußzeile 10 dp. Äußerer Radius: `android.R.dimen.system_app_widget_background_radius` (statt 28 dp; der Launcher schneidet ohnehin auf diesen Radius zu), dazu `appWidgetBackground()`.
  - Kopf links: Kachel 28×28 dp, Radius 9 dp, Fläche `accent`, darin `check_circle` 18 dp in `onAccent`. Daneben 8 dp Abstand, „Heute“ 15 sp Bold `ink`, wieder 8 dp Abstand, „erledigt/gesamt“ 13 sp Medium `ink2`.
  - Kopf rechts: Pille mit Innenabstand 4 oben/unten, 8 links, 10 rechts, Radius 999 (→ halbe Höhe), Fläche `goldSoft`. Darin `star` 16 dp und Punktestand 13 sp Bold, beides `goldInk`, 4 dp Abstand.
  - Zeile: ganze Zeile antippbar, Innenabstand 8 oben/rechts/unten und 10 links, Radius 16 dp, Fläche `surface2`, 10 dp Abstand zwischen den Teilen. Symbol 20 dp, `coral` bei überfällig, sonst `accent`. Name 14 sp Bold `ink`, einzeilig. Untertitel 12 sp Medium, `coral` bei überfällig, sonst `ink2`. Rechts der Haken-Ring 30 dp in `accent`.
  - Fußzeile „+ n weitere“: 12 sp Bold `ink2`, 4 dp seitlicher Einzug.
  - Leer: „Alles erledigt für heute“ 14 sp Bold `green`, Innenabstand 8 oben/unten, 4 seitlich.
- Reihenfolge der Einträge wie bisher (und wie Prototyp `[...ueber, ...jetzt, ...spaeter]`): überfällig zuerst, dann nach Uhrzeit, Ziele ohne Uhrzeit zuletzt. „Überfällig“ heißt wie im Heute-Tab: `geplantFuer` liegt vor jetzt (ohne Kulanz).
- Es gibt keine Compose- oder Glance-UI-Tests. Logik gehört in reine Funktionen und ViewModels mit JVM-Tests, Texte und Drawables werden mit Robolectric geprüft. Ob das Widget baut, zeigt `:app:assembleDebug`, wie es aussieht, zeigt die Sichtprüfung in Task 4.
- Tests, die einen per `flowOn` berechneten Zustand mit Turbine beobachten, geben dem ViewModel ihren Test-Dispatcher (`rechenDispatcher`).
- Nach jedem Task muss `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` grün sein, bevor committet wird. Nach dem Commit mit `git log -1 --oneline` prüfen, dass er wirklich entstanden ist.
- Commit-Nachrichten enden mit `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`.
- Sichtprüfungen im Emulator nur mit eigenen Testdaten (Präfix „Plan8-Test“). Vorhandene Aktivitäten und Belohnungen nie ändern, archivieren, abhaken oder einlösen. Optionen, die der Test verstellt, am Ende auf den Ausgangswert zurücksetzen. Vor jedem Tipp per `uiautomator dump` prüfen, welcher Knoten getroffen wird. Nie Backups speichern oder importieren.

## Review Focus

1. **Große Schrift** (Systemschrift 130–200 %): Die Zeilen werden höher, das Widget zeigt dann weniger Zeilen. Keine Zeile wird mittendrin abgeschnitten, und „+ n weitere“ bleibt sichtbar. → Tests mit `schriftFaktor` 1,5 und 2,0 in Task 1.
2. **Hell/Dunkel umschalten, während das Widget auf dem Homescreen liegt:** Das Widget wechselt ohne App-Neustart mit. Ein Tipp auf den schon gespeicherten Modus oder ein fehlgeschlagener Schreibvorgang löst keinen Anstoß aus. → Tests in Task 3, Sichtprüfung in Task 4.
3. **Tag ganz ohne Fälligkeiten** (neue App, alle Aktivitäten archiviert, Ruhetag): Das Widget zeigt „Heute steht nichts an“ statt „Alles erledigt für heute“, und die Kopfzeile zeigt keinen Zähler „0/0“. → Test in Task 1.
4. **Unbekannter Symbolschlüssel** (älteres Backup, künftige Version): Das Widget zeigt das Standardsymbol `self_improvement` und stürzt nicht ab, wie `aktivitaetSymbol()` in der App. → Test in Task 2.
5. **Wochenziel und Zählziel mit Uhrzeit:** Ein Wochenziel heißt „2 von 3 diese Woche“, nicht „heute“. Ein überfälliger Termin mit `sollAnzahl` 2 heißt „08:00 · überfällig · 1 von 2 heute“. → Tests in Task 1.

---

## Datei-Übersicht

| Datei | Zweck |
|---|---|
| `app/.../widget/WidgetZustand.kt` | Zustand, Zusammenstellung, Untertitel |
| `app/.../widget/WidgetMasse.kt` (neu) | passende Zeilenzahl für eine Höhe |
| `app/.../widget/WidgetSymbole.kt` (neu) | Symbolschlüssel → Drawable |
| `app/.../widget/WidgetFarben.kt` (neu) | ThemeModus → Farbsatz |
| `app/.../widget/HeuteWidget.kt` | Layout im Prototyp-Stil, `SizeMode.Exact`, Farbmodus |
| `app/.../di/WidgetZugang.kt` | zusätzlich `einstellungen()` |
| `app/.../ui/einstellungen/EinstellungenViewModel.kt` | stößt Widget bei Farbmoduswechsel an |
| `app/src/main/res/drawable/widget_*.xml` (neu) | 12 Aktivitätssymbole, `check_circle`, `star`, Haken-Ring |
| `app/src/main/res/xml/heute_widget_info.xml` | Zielgröße |
| `app/src/main/res/values/strings.xml` | Widget-Texte |
| `README.md`, `docs/HANDOFF.md` | Stand Plan 4–8 |

`app/...` = `app/src/main/kotlin/de/vanhake/dayorganizer`, Tests unter `app/src/test/kotlin/de/vanhake/dayorganizer`.

---

### Task 1: Widget-Logik — Zustand, Untertitel, Zeilenzahl

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/WidgetZustand.kt`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/WidgetMasse.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/HeuteWidget.kt` (nur so weit, dass es mit dem neuen Zustand baut; das Layout folgt in Task 4)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/widget/WidgetZustandTest.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/widget/WidgetUntertitelTest.kt` (neu, Robolectric)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/widget/WidgetMasseTest.kt` (neu)

**Interfaces:**
- Produces:
  ```kotlin
  data class WidgetEintrag(
      val faelligkeitId: Long,
      val name: String,
      val symbol: String,          // Aktivitaet.symbol, Schluessel wie AKTIVITAET_SYMBOLE
      val zeitText: String?,       // "HH:mm" oder null
      val ueberfaellig: Boolean,
      val istAnzahl: Int,
      val sollAnzahl: Int,
      val wochenziel: Boolean,     // periodeBis > periodeVon
  )
  enum class WidgetLeer { ALLES_ERLEDIGT, NICHTS_GEPLANT }
  data class WidgetZustand(
      val kontostand: Int = 0,
      val erledigt: Int = 0,       // Status ERLEDIGT, nur aktive Aktivitaeten
      val gesamt: Int = 0,         // alle Faelligkeiten des Tages aktiver Aktivitaeten
      val offen: List<WidgetEintrag> = emptyList(),  // ALLE offenen, sortiert
      val leer: WidgetLeer? = null, // null, solange etwas offen ist
  )
  object WidgetZusammenstellung {
      fun aus(faelligkeiten: List<Faelligkeit>, aktivitaeten: List<Aktivitaet>, kontostand: Int, jetzt: LocalDateTime): WidgetZustand
  }
  fun widgetUntertitel(eintrag: WidgetEintrag, res: Resources): String
  object WidgetMasse {
      fun passendeZeilen(hoeheDp: Float, offen: Int, schriftFaktor: Float): Int
  }
  ```
- Entfällt: Parameter `hoechstens` und Feld `weitere`. Die Zahl der sichtbaren Zeilen bestimmt künftig `passendeZeilen`, „weitere“ ist `offen.size - zeilen`.

**Regeln:**
- `erledigt`/`gesamt` werden gezählt wie `HeuteZusammenstellung.erledigtHeute`/`gesamtHeute`: Fälligkeiten, deren Aktivität in `aktivitaeten` steht, unabhängig vom Status, ausgelassene zählen also zu `gesamt`.
- `leer`: `NICHTS_GEPLANT`, wenn `gesamt == 0`, `ALLES_ERLEDIGT`, wenn `gesamt > 0` und nichts offen ist, sonst `null`.
- `widgetUntertitel` setzt sich aus Teilen zusammen. Diese Copy stammt aus `mk()` im Prototyp:
  - Zähltext `z`: `widget_ziel_woche` bei Wochenziel, sonst `widget_ziel_heute`, jeweils mit `(istAnzahl, sollAnzahl)`.
  - Ohne Uhrzeit: `z`.
  - Mit Uhrzeit: `t` = `zeitText` bzw. `widget_ueberfaellig(zeitText)`, wenn überfällig. Ist `sollAnzahl > 1`, folgt `widget_untertitel_verbindung(t, z)`, sonst bleibt es bei `t`.
- `passendeZeilen` liefert das größte `k` aus `0..min(4, offen)`, für das gilt:
  `2*16 + kopf + k*(10 + zeile) + (wenn offen > k: 10 + 16*f, sonst 0) <= hoeheDp`.
  Dabei ist `kopf = max(28, 20*f)` und `zeile = 16 + max(30, 36*f)`, mit `f = schriftFaktor`. Die 36 sind eine Namenszeile mit 14 sp (≈ 20 dp) plus eine Untertitelzeile mit 12 sp (≈ 16 dp). Die 16 sind der vertikale Innenabstand der Zeile. Die Konstanten stehen als benannte `private const val` in `WidgetMasse`.

Neue Strings:

```xml
<string name="widget_titel">Heute</string>
<string name="widget_ueberfaellig">%1$s · überfällig</string>
<string name="widget_ziel_heute">%1$d von %2$d heute</string>
<string name="widget_ziel_woche">%1$d von %2$d diese Woche</string>
<string name="widget_untertitel_verbindung">%1$s · %2$s</string>
<string name="widget_alles_erledigt">Alles erledigt für heute</string>
<string name="widget_nichts_geplant">Heute steht nichts an</string>
<string name="widget_abhaken">%1$s abhaken</string>
```

`widget_ziel_fortschritt` (`%1$d/%2$d`) bleibt und dient künftig dem Kopfzähler. `widget_weitere` (`+ %1$d weitere`) bleibt.

- [ ] **Step 1: `WidgetZustandTest` anpassen und ergänzen.** Die vorhandenen Tests stellen auf `offen` um. `was nicht passt wird gezaehlt statt verschwiegen` entfällt, das deckt jetzt `WidgetMasseTest` ab. Neue Tests:
  - `zaehlt erledigt und gesamt wie der Heute-Tab`: 1× erledigt, 1× ausgelassen, 2× offen → `erledigt == 1`, `gesamt == 4`, `offen.size == 2`, `leer == null`.
  - `ohne Faelligkeiten heisst es nichts geplant`: leere Liste → `leer == WidgetLeer.NICHTS_GEPLANT`, `gesamt == 0`.
  - `alles abgehakt heisst alles erledigt`: zwei erledigte → `leer == WidgetLeer.ALLES_ERLEDIGT`.
  - `Faelligkeiten archivierter Aktivitaeten zaehlen nicht`: Fälligkeit mit unbekannter `aktivitaetId` → `gesamt == 0`.
  - `ein Eintrag traegt Symbol und Wochenziel-Kennung`: Aktivität mit `symbol = "water_drop"` und einer Fälligkeit `periodeVon = Montag`, `periodeBis = Sonntag` → `symbol == "water_drop"`, `wochenziel == true`. Bei einer Tagesfälligkeit gilt `wochenziel == false`.
- [ ] **Step 2: `WidgetUntertitelTest` (Robolectric, `@RunWith(RobolectricTestRunner::class)`, Ressourcen über `ApplicationProvider.getApplicationContext<Context>().resources`).** Jeder Test baut einen `WidgetEintrag` und vergleicht den Text exakt:

  | Eintrag | Erwartet |
  |---|---|
  | `zeitText = "08:00"`, nicht überfällig, soll 1 | `"08:00"` |
  | `zeitText = "08:00"`, überfällig, soll 1 | `"08:00 · überfällig"` |
  | `zeitText = "08:00"`, nicht überfällig, ist 1, soll 2 | `"08:00 · 1 von 2 heute"` |
  | `zeitText = "08:00"`, überfällig, ist 1, soll 2 | `"08:00 · überfällig · 1 von 2 heute"` |
  | `zeitText = null`, ist 1, soll 3, kein Wochenziel | `"1 von 3 heute"` |
  | `zeitText = null`, ist 2, soll 3, Wochenziel | `"2 von 3 diese Woche"` |

- [ ] **Step 3: `WidgetMasseTest`.** Je ein Test pro Zeile, Werte exakt:

  | `hoeheDp` | `offen` | `f` | Ergebnis | Grund |
  |---|---|---|---|---|
  | 334 | 6 | 1,0 | 4 | 32+28+4·62+26 = 334 |
  | 333 | 6 | 1,0 | 3 | eine Zeile weniger |
  | 308 | 4 | 1,0 | 4 | keine Fußzeile nötig |
  | 110 | 3 | 1,0 | 0 | nur Kopf und „+ 3 weitere“ |
  | 148 | 3 | 1,0 | 1 | 32+28+62+26 |
  | 400 | 2 | 1,0 | 2 | nie mehr als offen |
  | 1000 | 9 | 1,0 | 4 | höchstens 4 |
  | 500 | 0 | 1,0 | 0 | nichts offen |
  | 334 | 6 | 1,5 | 2 | kopf 30, zeile 70, Fuß 34 |
  | 334 | 6 | 2,0 | 2 | kopf 40, zeile 88, Fuß 42 → 32+40+2·98+42 = 310 |

- [ ] **Step 4: Tests laufen lassen** `./gradlew :app:testDebugUnitTest --tests "de.vanhake.dayorganizer.widget.*"` → FAIL (Kompilierfehler: `offen`, `leer`, `widgetUntertitel`, `WidgetMasse` fehlen).
- [ ] **Step 5: Umsetzen** in `WidgetZustand.kt` und `WidgetMasse.kt`, dazu die Strings. `HeuteWidget.kt` nur minimal nachziehen: `hoechstens` entfällt, vorerst `offen.take(4)` und `offen.size - 4` als „weitere“, Untertitel über `widgetUntertitel`. Der Kommentar in `WidgetEintrag` zum Zählziel-Stand (Review-Fund G2) wandert zu `widgetUntertitel`.
- [ ] **Step 6: Gesamtlauf** `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 7: Commit** `feat(widget): Zustand mit Fortschritt, Leerhinweis und Untertitel; Zeilenzahl nach Hoehe`, danach `git log -1 --oneline`.

---

### Task 2: Widget-Symbole als Vector-Drawables

**Files:**
- Create: `app/src/main/res/drawable/widget_symbol_<schluessel>.xml` für alle 12 Schlüssel aus `AKTIVITAET_SYMBOLE`
- Create: `app/src/main/res/drawable/widget_check_circle.xml`, `widget_star.xml`, `widget_haken.xml`
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/WidgetSymbole.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/widget/WidgetSymboleTest.kt` (neu, Robolectric)

**Interfaces:**
- Consumes: `AKTIVITAET_SYMBOLE`, `AKTIVITAET_SYMBOL_STANDARD` aus `ui/theme/AktivitaetSymbol.kt`.
- Produces: `@DrawableRes fun widgetSymbol(schluessel: String): Int` sowie die Drawables `R.drawable.widget_check_circle`, `R.drawable.widget_star` und `R.drawable.widget_haken`.

**Quelle der Pfade:** Material Icons, Stil „Filled“ (`materialicons`), Apache 2.0. Das sind dieselben Glyphen wie `Icons.Filled.*` in der App. Die SVGs liegen unter
`https://raw.githubusercontent.com/google/material-design-icons/master/src/<kategorie>/<name>/materialicons/24px.svg`.

Die Zuordnung folgt `aktivitaetSymbol()`, damit App und Widget dasselbe Bild zeigen (`pill` → Icon `medication`, `medication` → Icon `local_pharmacy`):

| Drawable | Icon | Kategorie |
|---|---|---|
| `widget_symbol_pill` | medication | device |
| `widget_symbol_medication` | local_pharmacy | maps |
| `widget_symbol_water_drop` | water_drop | social |
| `widget_symbol_fitness_center` | fitness_center | places |
| `widget_symbol_menu_book` | menu_book | maps |
| `widget_symbol_directions_walk` | directions_walk | maps |
| `widget_symbol_self_improvement` | self_improvement | social |
| `widget_symbol_bedtime` | bedtime | image |
| `widget_symbol_restaurant` | restaurant | maps |
| `widget_symbol_spa` | spa | places |
| `widget_symbol_music_note` | music_note | image |
| `widget_symbol_cleaning_services` | cleaning_services | maps |
| `widget_check_circle` | check_circle | action |
| `widget_star` | star | toggle |

Format jedes Drawables: `<vector android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">`. Jeder `<path d>` des SVG wird zu einem `<path android:fillColor="#FFFFFFFF" android:pathData="…"/>`. Pfade mit `fill="none"` (die Begrenzungsbox) entfallen. `menu_book` und `directions_walk` bekommen `android:autoMirrored="true"`, weil die App dort `Icons.AutoMirrored` nutzt. Die Farbe kommt zur Laufzeit per Tint.

`widget_haken.xml`: Vector 30×30 dp, Viewport 30×30. Ein Kreis mit Mittelpunkt (15, 15) und Radius 14, nur Kontur mit `strokeWidth` 2 und `strokeColor` `#FFFFFFFF`, ohne Füllung. Darin der Pfad von `check` (Kategorie `navigation`), auf 18 dp skaliert und zentriert, per `<group android:translateX="6" android:translateY="6" android:scaleX="0.75" android:scaleY="0.75">`. Füllung `#FFFFFFFF`.

Lizenzhinweis: Ein XML-Kommentar in jeder Datei: `<!-- Material Icons (Apache License 2.0), Glyphe <name> -->`.

- [ ] **Step 1: `WidgetSymboleTest`.**
  - `jedes Aktivitaetssymbol hat ein eigenes Drawable`: `AKTIVITAET_SYMBOLE.map(::widgetSymbol).toSet().size == 12`.
  - `unbekannter Schluessel faellt auf das Standardsymbol zurueck`: `widgetSymbol("gibt_es_nicht") == widgetSymbol(AKTIVITAET_SYMBOL_STANDARD) == R.drawable.widget_symbol_self_improvement`.
  - `alle Widget-Drawables lassen sich laden` (Robolectric): Für jedes Symbol und für `widget_check_circle`, `widget_star`, `widget_haken` gilt `AppCompatResources.getDrawable(kontext, id)` bzw. `ContextCompat.getDrawable(kontext, id)` ist nicht `null`.
- [ ] **Step 2: Test laufen lassen** → FAIL (`widgetSymbol` fehlt).
- [ ] **Step 3: SVGs herunterladen** (`curl` in ein Scratch-Verzeichnis außerhalb des Repos) und die Drawables wie beschrieben anlegen. Die SVG-Dateien selbst kommen nicht ins Repo.
- [ ] **Step 4: `widgetSymbol` umsetzen**, ein `when` analog zu `aktivitaetSymbol()` mit `else` auf `widget_symbol_self_improvement`.
- [ ] **Step 5: Gesamtlauf** → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `feat(widget): Aktivitaetssymbole, Stern und Haken-Ring als Vector-Drawables`, danach `git log -1 --oneline`.

---

### Task 3: Farbmodus im Widget

**Files:**
- Create: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/WidgetFarben.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/di/WidgetZugang.kt`
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModel.kt`
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/widget/WidgetFarbenTest.kt` (neu)
- Test: `app/src/test/kotlin/de/vanhake/dayorganizer/ui/einstellungen/EinstellungenViewModelTest.kt`

**Interfaces:**
- Consumes: `HellFarben`, `DunkelFarben`, `DayOrganizerFarben` (`ui/theme/Farben.kt`), `ThemeModus`, `EinstellungenSpeicher.lesen()`/`beobachten()`.
- Produces:
  - `fun widgetFarben(modus: ThemeModus): DayOrganizerFarben`: `DUNKEL` → `DunkelFarben`, sonst `HellFarben`. Ein zweiter Farbsatz entsteht nicht. Der KDoc nennt den Grund: Glance hat keinen Zugriff auf `LocalDayOrganizerFarben`, die Werte sind aber dieselben.
  - `WidgetZugang.einstellungen(): EinstellungenSpeicher`.
  - Neuer Konstruktorparameter von `EinstellungenViewModel` direkt nach `nachImport`: `private val nachFarbmodus: suspend () -> Unit`. Der `@Inject`-Konstruktor übergibt `{ widgetAktualisierung.anstossen() }`.

**Verhalten in `uebernehmen()`:** Unter der Sperre wird vor dem Schreiben `lesen()` einmal aufgerufen. Der Wert wird schon für `copy` gebraucht. Mit ihm wird verglichen, ob sich `themeModus` ändert. Nur wenn der Schreibvorgang gelingt **und** sich der Modus geändert hat, wird nach dem Lösen der Sperre, aber noch innerhalb von `withContext(NonCancellable)`, `nachFarbmodus()` aufgerufen. Ein Tab-Wechsel direkt nach dem Tipp darf den Anstoß nicht verschlucken.

- [ ] **Step 1: `WidgetFarbenTest`:** `widgetFarben(ThemeModus.DUNKEL) === DunkelFarben`, `widgetFarben(ThemeModus.HELL) === HellFarben`.
- [ ] **Step 2: Tests in `EinstellungenViewModelTest`.** Die Hilfsfunktion `viewModel(...)` bekommt `nachFarbmodus: suspend () -> Unit = {}`. Neue Tests zählen die Aufrufe mit `var anstoesse = 0`:
  - `ein Farbmoduswechsel stoesst das Widget an`: gespeichert HELL, `themeModusAendern(DUNKEL).join()` → `anstoesse == 1`.
  - `ein Tipp auf den gespeicherten Farbmodus stoesst nicht an`: gespeichert HELL, `themeModusAendern(HELL).join()` → `0`.
  - `ein Stepper-Tipp stoesst das Widget nicht an`: `tagesbonusAendern(+5).join()` → `0`.
  - `ein fehlgeschlagener Farbmodus-Schreibvorgang stoesst nicht an`: `viewModel(..., schreibFehler = true)` (vorhandener Schalter, wirft `IOException`) → `0`.
- [ ] **Step 3: Tests laufen lassen** → FAIL.
- [ ] **Step 4: Umsetzen** (`WidgetFarben.kt`, `WidgetZugang`, ViewModel). `HeuteWidget` nutzt die Farben erst in Task 4.
- [ ] **Step 5: Gesamtlauf** → BUILD SUCCESSFUL.
- [ ] **Step 6: Commit** `feat(widget): Farbsatz nach App-Einstellung; Farbmoduswechsel stoesst Widget an`, danach `git log -1 --oneline`.

---

### Task 4: Widget-Layout im Prototyp-Stil und Sichtprüfung

**Files:**
- Modify: `app/src/main/kotlin/de/vanhake/dayorganizer/widget/HeuteWidget.kt`
- Modify: `app/src/main/res/xml/heute_widget_info.xml`

**Interfaces:**
- Consumes: `WidgetZusammenstellung.aus`, `widgetUntertitel`, `WidgetMasse.passendeZeilen` (Task 1), `widgetSymbol`, `R.drawable.widget_check_circle`/`widget_star`/`widget_haken` (Task 2), `widgetFarben`, `WidgetZugang.einstellungen()` (Task 3).

**Aufbau:**
- `override val sizeMode = SizeMode.Exact`. Die Höhe kommt aus `LocalSize.current.height.value`, der Schriftfaktor aus `LocalContext.current.resources.configuration.fontScale`.
- Farbmodus wie die übrigen Werte: Startwert in `provideGlance` über `zugang.einstellungen().lesen().themeModus`, danach in `provideContent` über `beobachten().map { it.themeModus }.distinctUntilChanged().collectAsState(initial = …)`. Der vorhandene Kommentar zu Review-Fund K1 erklärt das Muster und gilt hier mit.
- Farben: `val farben = widgetFarben(modus)`, jede Verwendung als `ColorProvider(farben.x)`.
- Aufbau von oben nach unten: Kopfzeile, für `offen.take(zeilen)` je eine Zeile, dann entweder „+ n weitere“ (`zeilen < offen.size`), „Alles erledigt für heute“ (`leer == ALLES_ERLEDIGT`, Farbe `green`) oder „Heute steht nichts an“ (`leer == NICHTS_GEPLANT`, Stil wie „+ n weitere“, aber 14 sp). Alle Werte stehen in den Global Constraints.
- Der Kopfzähler `widget_ziel_fortschritt(erledigt, gesamt)` entfällt bei `gesamt == 0`.
- Zeile: `clickable(actionRunCallback<WidgetErledigenAktion>(…))` wie bisher. Die Bedienhilfe-Beschriftung `widget_abhaken(name)` kommt über `GlanceModifier.semantics { contentDescription = … }`. Symbol und Haken bekommen `contentDescription = null`, der Stern `punktestand(kontostand)`.
- Bilder: `Image(ImageProvider(id), contentDescription, GlanceModifier.size(…), colorFilter = ColorFilter.tint(ColorProvider(…)))`.
- Name einzeilig mit `maxLines = 1`. Der Untertitel bleibt ebenfalls einzeilig.
- `heute_widget_info.xml`: `minWidth`/`minHeight` bleiben (180/110 dp). Neu sind `android:targetCellWidth="4"`, `android:targetCellHeight="3"`, `android:minResizeWidth="180dp"`, `android:minResizeHeight="110dp"` und `android:description="@string/widget_beschreibung"` mit dem neuen String `<string name="widget_beschreibung">Offene Aufgaben von heute abhaken</string>`.

- [ ] **Step 1: `HeuteWidget` umbauen.** Die alten Literale (`Color(0xFFF5F5F5)`, `Color.Black`, `Color(0xFFB00020)`, `Color.DarkGray`) entfallen vollständig.
- [ ] **Step 2: Widget-Info anpassen.**
- [ ] **Step 3: Gesamtlauf** `./gradlew clean :domain:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL.
- [ ] **Step 4: Sichtprüfung im Emulator** (AVD `LernApp_Pixel`, Setup in `docs/HANDOFF.md` unter „Umgebung“, Regeln aus den Global Constraints):
  1. APK installieren. Liegt noch kein DayOrganizer-Widget auf dem Homescreen, eines platzieren (Homescreen lange drücken → Widgets → DayOrganizer). Gelingt das Platzieren per `uiautomator`/`input swipe` nicht, abbrechen und im Bericht melden, statt herumzuprobieren.
  2. Zwei Testaktivitäten anlegen: „Plan8-Test Wasser“ (Symbol `water_drop`, Mal pro Tag, Ziel 3, Punkte 1) und „Plan8-Test Termin“ (Symbol `fitness_center`, feste Uhrzeit eine Stunde **vor** jetzt, Punkte 1, damit sie überfällig ist).
  3. Widget hell prüfen: Kopf „Heute x/y“ mit Sternpille, Kacheln mit Symbol, Name, Untertitel („… · überfällig“ in Koralle, „0 von 3 heute“) und Haken-Ring. Das Widget per Ziehen am Rand kleiner und größer machen: Die Zeilenzahl passt sich an, „+ n weitere“ erscheint.
  4. **Genau einmal** auf „Plan8-Test Wasser“ tippen, aber nur, wenn danach noch mindestens ein echter Eintrag offen ist (sonst würde der Tagesbonus gebucht). Der Untertitel wechselt auf „1 von 3 heute“. Die Buchung über 1 Punkt im Bericht nennen.
  5. In Optionen auf „Dunkel“ umschalten → Das Widget wird dunkel, ohne dass die App neu startet. Danach den Ausgangswert wiederherstellen und prüfen, dass das Widget mitwechselt.
  6. Aufräumen: Beide Plan8-Test-Aktivitäten archivieren (nur diese, Ziel vor jedem Tipp per Dump prüfen).

  Bildschirmfotos (hell, dunkel, klein) ins Scratchpad legen und im Bericht nennen.
- [ ] **Step 5: Commit** `feat(widget): Homescreen-Widget im Prototyp-Stil`, danach `git log -1 --oneline`.

---

### Task 5: README und HANDOFF auf Stand Plan 4–8

**Files:**
- Modify: `README.md`
- Modify: `docs/HANDOFF.md`

Reine Doku, keine Tests. Inhalt aus den Plänen 04–08 und `git log`, nicht aus dem Gedächtnis:

- **README:** Funktionsumfang je Tab (Heute mit Heldenkarte und Rückgängig, Aktivitäten mit Symbolen und Editor, Belohnungen mit Bedingungen und Verlauf, Statistik mit Heatmap-Stufen und Punkteverlauf, Optionen mit sofort speichernden Steppern und Hell/Dunkel), Widget, Datenbank Version 5, Backup-Schema 3 (Schema 2 bleibt lesbar), Design-Quelle `docs/design/prototyp/`.
- **HANDOFF:** „Wo wir stehen“ auf Plan 8. Unter „Dokumente“ die Pläne 04–08. Bewährte Arbeitsweise ergänzen: Implementer- und Reviewer-Anweisungen als Dateien unter `.superpowers/sdd/plan-N/`, `git log -1 --oneline` nach jedem Commit, `rechenDispatcher` für ViewModels mit `flowOn`, Emulator-Regeln (Präfix-Testdaten, `uiautomator dump`, Optionen zurücksetzen, keine Backups). Den Abschnitt „Offene Punkte“ durch den aktuellen Backlog ersetzen:
  - Ein neuer Toast verdrängt ein noch offenes Rückgängig (Heute, Belohnungen).
  - Ein Stepper-Tipp in Optionen während eines laufenden Imports kann importierte Werte überschreiben.
  - Archivieren hat keine Rückfrage und keine Wiederherstellung. Im Emulator sind dadurch vier echte Aktivitäten archiviert (Magnesium, Rücken, Tabletten, Vitamin), und aus Plan 6 steht eine Test-Einlösung über −10 Punkte.
  - Die Fehlerbehandlung beim Speichern beider Editoren vereinheitlichen (`Repository.speichern` wirft bei fehlendem Datensatz).
  - Die Heatmap hat keine TalkBack-Semantik. Das „–“ bei Aktivitäten ohne Quote hat keinen Vorlesetext.
  - Nach dem Abbrechen des System-Dateidialogs stand die App im Emulator wieder auf „Heute“. Eine Ursache im Code ist nicht gefunden.
  - Neu aus Plan 8, falls sich bei der Sichtprüfung etwas ergibt (sonst weglassen).
- Abschnitt „Datenbank“ auf Version 5 prüfen.

- [ ] **Step 1: README aktualisieren.**
- [ ] **Step 2: HANDOFF aktualisieren.**
- [ ] **Step 3: Gesamtlauf** `./gradlew :domain:test :app:testDebugUnitTest :app:assembleDebug` → BUILD SUCCESSFUL. Er läuft auch bei reiner Doku, damit der Branch-Kopf grün ist.
- [ ] **Step 4: Commit** `docs: README und HANDOFF auf Stand Plan 4-8`, danach `git log -1 --oneline`.

---

## Abschluss dieses Plans

Nach Task 5 entspricht die ganze App einschließlich Widget dem Prototyp. Offen bleibt der Backlog aus Task 5 für einen eigenen Aufräumplan (Plan 9).
