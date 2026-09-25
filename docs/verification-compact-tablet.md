# Compact-Slice: reales USB-Tablet

25. September 2026, ca. 17:36–17:44 Uhr Europe/Berlin.

## Stand und Installation

- Ausschließlich lokal in `/Users/kf/codexpad` gearbeitet. Kein SSH, kein
  Deployment, keine Änderung an VPS, Diensten, Konfiguration oder Serverdateien.
  Die Tablet-App verwendete die bereits gespeicherte Verbindung zu
  `https://pad.feichti.dev`. Die beauftragten Compact-/Turn-/Interrupt-Aufrufe
  erfolgten ausschließlich über die App.
- Ausgangsstand sauber, HEAD
  `8fb3393384db0a6d0acd7e3f8e440962945d827a`; kein Produktionscode geändert.
- ADB: `AWDVBB6428000843`, Status `device`, Modell `YLE_W09`, Produkt
  `YLE-W09EEA`, Android **16**.
- `android/build-local.sh`: **BUILD SUCCESSFUL** für `assembleDebug`,
  `testDebugUnitTest`, `lintDebug`. Inkrementeller Build; 34 Tests erfasst,
  33 bestanden, ein opt-in Python-Vertragstest ohne Gegenstelle übersprungen,
  keine Fehler. Lint: 0 Fehler, 7 bestehende Hinweise.
- Die Socket-/USB-Sandbox blockierte den ersten ADB-Aufruf. Freigegebene lokale
  Ausführung außerhalb der Sandbox funktionierte; kein Android-App-Fehler.
- Installation mit `adb -s AWDVBB6428000843 install -r
  android/app/build/outputs/apk/debug/app-debug.apk`: **Success**.
  Keine Deinstallation, kein Löschen von App-Daten, keine Zugangsdatenextraktion.
- Paket `dev.codexpad`, Version `0.1.0`, versionCode `1`, Debug, targetSdk 37.
  Gerätezeit der Aktualisierung: `2026-09-25 17:37:55`; Erstinstallation weiterhin
  `2026-09-25 13:05:02`.
- SHA-256 der lokalen APK und der installierten `base.apk` identisch:
  `e8447d4e89fd7ef115874fbade664b64f2152efa54eda22511233599f27b89f3`.

Bedienung durch ADB-Touches und Beobachtung nativer UI-Automator-Hierarchien.
`FLAG_SECURE` blieb unangetastet. Lokale Rohbelege und Bedienhilfen liegen
ignoriert unter `android/.local/compact-tablet/`; die temporäre XML-Datei auf
dem Tablet wurde entfernt. Kein separater HTTP-/SSE-Mitschnitt, keine direkten
Serverabfragen und keine Einsicht in serverseitige Logs in diesem Lauf.

## Erhaltene Daten und Prüfthread

Nach dem Update öffnete sich die Workspace-Liste ohne erneute Anmeldung.
`testprojekt` und die bisherigen Threads waren vorhanden, einschließlich der
separaten VPS-Prüfthreads. Für diesen Lauf wurde der bestehende Tablet-Prüfthread
`01a0d911-8fde-7af2-85f2-5b623c73dfff` verwendet.

Vor dem Update und nach erneutem Öffnen zeigte er `Live verbunden`, `idle`,
`gpt-6-sol · Reasoning low` und dieselbe Usage von **26838** Tokens.
Die Auswahl des geöffneten Screens wurde beim APK-Update nicht beibehalten;
Verbindung und Gesprächsdaten blieben erhalten.

## Praktische Ergebnisse

| Prüfung | Beobachtung |
| --- | --- |
| Aktion an vorgesehener Stelle | Oben im Thread: **Kontext** → Erklärung → **Kontext komprimieren**. Im ruhenden verbundenen Thread aktiviert. |
| Compact auslösen | Um 17:39:10 sichtbar: **Kontext wird komprimiert …**, Thread `active`, Senden gesperrt. |
| Wiederholungssperre | Um 17:39:13 war der anklickbare übergeordnete Menüeintrag ausdrücklich `enabled=false`. Das Textkind allein meldet weiterhin `enabled=true`; maßgeblich ist der Aktionsknoten. Einschränkung des späteren Test-Tipps siehe unten. |
| Usage während Compact | Zunächst bisherige **26838** mit **Kontext · veraltet**. Später **5700** ebenfalls veraltet bei laufendem Compact. Kein erfundener Nullwert oder lokale Ersparnis. |
| Bestätigtes Ende | Um 17:39:47 **Kontext komprimiert**, `idle`, terminaler History-Eintrag `completed`, strukturierter Marker **Kontextkomprimierung**. Usage **5607**, ohne Veraltet-Markierung. |
| Normaler Folge-Turn | Nachricht `Reply with exactly TABLET-COMPACT-FOLLOWUP-OK. Do not use tools or Change files.` genau einmal gesendet; Antwort **TABLET-COMPACT-FOLLOWUP-OK**, History `completed`, anschließend `idle`. Usage **19221**. |
| Compact mit Hintergrundwechsel | Erneut bewusst gestartet. Um 17:40:51 laufender Zustand und **19221 · veraltet** erfasst; unmittelbar danach Home. Rückkehr um 17:41:07: **Live verbunden**, **Kontext komprimiert**, `idle`, **5603** aktuelle Tokens. Ein abgeschlossener Compact-Eintrag war in der neu abgeglichenen History vorhanden. |
| Kein beobachteter automatischer Neustart | Nach Rückkehr blieb der Thread idle; beim Durchscrollen nur die unten beschriebenen drei Compact-Einträge. Auch ein weiterer Home-/Rückkehr-Zyklus nach Interrupt startete keinen neuen Vorgang. Kein Paketmitschnitt als exakter POST-Zähler vorhanden. |
| History erhalten | Nach Reconnect durch die gesamte ursprüngliche Unterhaltung bis zum ersten Turn gescrollt. Alle vier ursprünglichen Turns und ihre Texte weiterhin sichtbar; Details unten. |
| Interrupt | Luna/medium-Turn mit Auftrag für 500 nummerierte Sätze ohne Tools/Dateiänderungen gestartet. `active`, laufender Text und **Stoppen** beobachtet. Einmal Stoppen betätigt; danach History **Turn 01a0d93c · interrupted**, `idle`, normaler Composer. |
| Modell-/Effort-Regression | Modellpicker zeigt sieben Modelle. Luna gewählt; Effortpicker `low, medium, high, xhigh, max`, kein `ultra`. `medium` ausgewählt. Vor dem Senden lokal vorgemerkt, konfiguriert weiterhin Sol/low; nach Senden **gpt-6-luna · Reasoning medium**, Vormerkung verbraucht. |

### Zeitliche Unschärfe beim Sperrtest

Zwischen dem Dump des deaktivierten Menüeintrags und dem Test-Tipp vergingen
etwa 16 Sekunden. Die History enthält anschließend **zwei** aufeinanderfolgende
Compact-Turns vor dem normalen Folge-Turn, beide mit dem sichtbaren ID-Präfix
`01a0d938` und Status `completed`. Das spricht dafür, dass der spätere Tipp
bereits die nach Abschluss wieder aktivierte Aktion traf. Ein Durchbruch der
Sperre während des laufenden Vorgangs ist damit **nicht** belegt.

Der Hintergrundtest betraf dadurch tatsächlich den **dritten** Compact dieses
Laufs (sichtbares Präfix `01a0d939`), nicht den zweiten. Die drei Einträge sind
durch drei manuelle Aktions-Tipps erklärbar; es gibt keine beobachtete zusätzliche
Kompaktierung nach Reconnect. Die UI-IDs sind auf acht Zeichen gekürzt, daher
werden hier keine vollständigen Turn-IDs oder exakten HTTP-Anzahlen behauptet.
Der einzelne terminale Zwischenstand des allerersten Compact wurde nicht
isoliert erfasst; **5700** ist ein beobachteter veralteter Zwischenwert und kein
gesondert belegter aktueller Endwert dieses ersten Vorgangs.

Die native Deaktivierung ist praktisch belegt. Atomare Doppelauslösung und
Transport-Retry-Verhalten werden ergänzend durch die bestandenen vorhandenen
Compact-/HTTP-Tests abgesichert; dieser zeitlich verzögerte Tipp ersetzt keinen
framegenauen Doppeltipp-Nachweis.

## Usage-Verlauf

| Beobachteter Stand | Verwendet | Fenster | Rest (Schätzung) | Kennzeichnung |
| --- | ---: | ---: | ---: | --- |
| Vor Compact | 26838 | 258400 | 231562 | aktuell |
| Erster laufender Compact | 26838 | 258400 | 231562 | veraltet |
| Weiterer laufender Zwischenstand | 5700 | 258400 | 252700 | veraltet |
| Nach den beiden ersten Compact-Turns | 5607 | 258400 | 252793 | aktuell |
| Nach normalem Folge-Turn | 19221 | 258400 | 239179 | aktuell |
| Beim Start des Hintergrund-Compact | 19221 | 258400 | 239179 | veraltet |
| Nach Rückkehr und Abgleich | 5603 | 258400 | 252797 | aktuell |
| Nach Interrupt und erneutem Reconnect | 5603 | 258400 | 252797 | aktuell, letzter verfügbarer Usage-Stand |

Die Zahlen wurden aus der App abgelesen. Der bestehende Client übernimmt sie
ausschließlich aus `thread/tokenUsage/updated` (`last.totalTokens`), nicht aus
History oder HTTP-202. Keine künstliche Verzögerung von Usage oder Replay
erzeugt. Die spezielle Ablehnung eines alten Pre-Compact-Replays ist durch die
bestehenden Unit-Tests, nicht durch einen gezielt provozierten Tablet-Replay
belegt. Beim echten Compact blieb die vorherige Usage sichtbar veraltet;
neue Werte wurden übernommen, und nach terminalem Abgleich aktuell angezeigt.

## Sichtbare History

Nach dem Hintergrund-Reconnect waren weiterhin vorhanden:

- `01a0d913 · completed`: ursprünglicher Default-Auftrag und `TABLET-DEFAULT-OK`.
- `01a0d915 · completed`: ursprünglicher Luna-Auftrag und `TABLET-LUNA-MEDIUM-OK`.
- `01a0d917 · interrupted`: ursprünglicher Interrupt-Auftrag und Reasoning-Marker.
- `01a0d918 · completed`: ursprünglicher Sol-Auftrag und `TABLET-SOL-LOW-RESUME-OK`.

Danach folgen zwei Compact-Einträge, der neue abgeschlossene Folge-Turn,
der Compact mit Hintergrundwechsel und abschließend der unterbrochene
Regressionsturn. Keine ursprüngliche sichtbare Nachricht wurde durch die
Kontextzusammenfassung ersetzt oder entfernt. Dies ist ein Sichtvergleich der
gerenderten vollständigen Ausgangsunterhaltung, kein Bytevergleich der
serverseitigen History-Payloads.

## Grenzen, Fehler und Git

- Kein reproduzierbarer Android-Integrationsfehler festgestellt. Keine
  Codekorrektur, kein zweiter Build notwendig. Der zusätzliche manuelle Compact
  ist die oben dokumentierte Testablauf-Unschärfe.
- Reconnect-Erfolg durch UI und abgeglichene terminale History belegt. Der
  bestehende lokale Code trennt `compact()` von `run()`/`refresh()`; Rückkehr
  liest Snapshot, History und SSE und postet den gespeicherten Vorgang nicht
  erneut. Ohne Transportmitschnitt keine unabhängige Exactly-once-Zusage.
- Hintergrundwechsel mit laufendem Compact geprüft; kein Prozessverlust,
  Force-stop, künstlicher Antwortverlust, WLAN-Ausfall, Overflow oder Rennen mit
  externen Clients. Der kurze unbekannte Rückkehr-Zustand wurde nicht erfasst;
  beim ersten Dump war der Serverabgleich bereits abgeschlossen.
- Interrupt-Endzustand belegt, der kurze Text **Wird gestoppt …** nicht erfasst.
  Modell-/Effort-Test ist eine kurze Auswahl-/Readback-Regression, keine erneute
  Prüfung sämtlicher Modellkombinationen oder Reroutes.
- Gefilterter `AndroidRuntime:E`-Log enthielt keine Crashmeldung. Keine
  umfassende HTTP-/SSE-Telemetrie erhoben.
- App bleibt im Prüfthread geöffnet, `Live verbunden`, `idle`, Compact
  abgeschlossen, zuletzt konfiguriert Luna/medium, kein Nachrichtenentwurf.
- `git diff --check` bestanden. Einzige nicht ignorierte Änderung:
  `?? docs/verification-compact-tablet.md`. Build und lokale XML-Belege ignoriert.
  Nichts gestaged, committet oder gepusht.
