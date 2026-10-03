# Prüfnachweis: Modell, Reasoning und Kontext

Historischer Nachweis. Die damalige absolute Kontext-Restschätzung wurde am
3. Oktober 2026 durch die [Codex-Statusline-Berechnung](research/codex-statusline.md)
ersetzt; dieser Bericht belegt nicht die neue Prozent-/Limit-/Zeitanzeige.

25. September 2026. Implementierung auf Basis des Capability-Audits für 0.156.1.
Die bereits vorhandenen, uncommitteten Interrupt-Änderungen wurden erhalten.
Keine ADR-Änderungen, kein Deployment und keine Modellaufrufe gegen den VPS.

## Ablauf und Vertrag

1. Android lädt beim Öffnen des Threads `GET /models`. Der Python-Adapter sammelt
   alle sichtbaren `model/list`-Seiten, erkennt Cursor-Schleifen und projiziert die
   benötigten Felder. `id` ist die Katalogidentität, `model` der RPC-Selektor.
2. Native Dialoge zeigen Modellnamen/Beschreibungen und ausschließlich zugehörige
   Efforts. Beim Wechsel bleibt ein unterstützter Effort erhalten, sonst gilt der
   unterstützte Default oder der erste gültige Wert. Ein leerer Effortkatalog
   erzeugt keinen Effort-Override. Katalogdefault ersetzt nie unbekannte Threaddaten.
3. Die Auswahl bleibt lokal im ViewModel und ausdrücklich „für nächste Nachricht“.
   Ohne Auswahl bleibt der bisherige Turn-POST unverändert. Mit Auswahl sendet er
   Modell und gegebenenfalls Effort ausschließlich an `turn/start`.
4. Der Adapter prüft Typ, Nichtleere, Länge, erlaubte Requestfelder, Modellselektor
   gegen den aktuellen vollständigen Katalog und Effort gegen genau dieses Modell.
   Effort ohne Modell wird gegen das gelesene Thread-Modell geprüft. Unbekannte
   Werte werden vor Resume/Turnstart abgewiesen; fehlende Overrides benötigen
   keinen Katalog. Die vorhandene Workspace-Prüfung bleibt vorgeschaltet.
5. Die HTTP-Antwort bestätigt den Turnstart, nicht die gewünschte Konfiguration.
   Die verbrauchte lokale Auswahl wird nach HTTP-Erfolg entfernt. Die Anzeige
   „Konfiguriert“ kommt immer aus dem Thread-/History-Abgleich, auch wenn sie vom
   Wunsch abweicht oder null ist. Bei Lesefehlern bleibt der letzte gelesene Zustand
   mit Fehleranzeige stehen. Mutierende Requests werden nicht automatisch wiederholt.
6. `model/rerouted` wird getrennt pro Turn gehalten und dargestellt. Es verändert
   keine Thread-Konfiguration. Diese Telemetrie ist nicht dauerhaft gespeichert.
7. Usage zeigt `last.totalTokens`, ein ausschließlich positives bekanntes
   `modelContextWindow` und `max(0, Fenster - verwendet)` als Restschätzung.
   Fehlende Werte heißen unbekannt. Pause, Disconnect und Overflow machen bekannte
   Usage veraltet; History/Snapshot heilen das nicht. Erst ein Usage-Event oder
   Resume-Replay macht sie wieder aktuell. Nach Prozessverlust ist sie unbekannt.

## Lokale Prüfungen

- `python3 -B -m unittest discover -s server -p 'test_*.py' -q`: 12 Tests bestanden.
- `android/build-local.sh`: Debug-APK, Unit-Tests und Lint erfolgreich.
- Zusätzlicher vollständiger Unit-Testlauf mit echter Python-HTTP-Gegenstelle,
  flüchtigem Testtoken und `--rerun-tasks`: 26 Tests, keine Fehler, keine Skips.
- `git diff --check`: erfolgreich.
- Gradle und HTTP-Testserver benötigen lokale Sockets; die Sandbox blockierte
  den ersten Start. Die freigegebenen Läufe außerhalb der Sandbox bestanden.
- Beim letzten Python-Lauf erschien trotz grünem Ergebnis einmal ein BrokenPipe
  beim Schreiben einer Parser-Fehlerantwort an einen bereits geschlossenen
  Testclient; keine fehlgeschlagene Assertion und keine ausgegebenen Zugangsdaten.

| Fall | Nachweis |
| --- | --- |
| Katalog laden, mehrere Seiten, Cursor-Schleife, Feldprojektion, Auth | Python-HTTP-Test |
| Modell mit mehreren/custom Efforts, kompatibler/inkompatibler Wechsel, ungültiger Default | ModelSelectionTest |
| Kein Override, nur Modell, Modell + Effort, nur Effort | Python-HTTP-Test; Android-HTTP-Test für erste drei Varianten |
| Ungültiges Modell, inkompatibler Effort, null/Typ/Leerwert, fremdes Feld | Python-HTTP-Test; keine Mutation |
| Tatsächlicher Modell-/Effort-Readback | Android-HTTP-Test und Kotlin→Python-Vertragstest |
| Response-Verlust mit Overrides, keine Wiederholung | Android-MockWebServer-Test |
| Reconnect mit Usage-Replay, veraltet ohne Replay, unbekannt ohne Usage | ThreadSessionTest |
| Fehlendes/null/nichtpositives Fenster, Überbelegung, last statt total | ModelSelectionTest |
| Reroute bleibt turnbezogen, Konfiguration unverändert | ThreadSessionTest |
| Echter HTTP/SSE-Resume-Pfad mit Usage-Replay | Kotlin→Python-Vertragstest, produktiver Handler mit Backendfixture |

## Geänderte Dateien dieses Slices

- `server/codexpad_server.py`, `server/test_auth.py`, `server/README.md`
- `android/app/src/main/java/dev/codexpad/model/Models.kt`
- `android/app/src/main/java/dev/codexpad/model/ModelSelection.kt` (neu)
- `android/app/src/main/java/dev/codexpad/network/CodexPadApi.kt`
- `android/app/src/main/java/dev/codexpad/data/ThreadSession.kt`
- `android/app/src/main/java/dev/codexpad/ui/PadViewModel.kt`
- `android/app/src/main/java/dev/codexpad/ui/CodexPadApp.kt`
- `android/app/src/test/java/dev/codexpad/ApiTest.kt`
- `android/app/src/test/java/dev/codexpad/ModelSelectionTest.kt` (neu)
- `android/app/src/test/java/dev/codexpad/ThreadSessionTest.kt`
- `android/app/src/test/java/dev/codexpad/PythonContractTest.kt`
- `android/tools/contract_server.py`, `android/README.md`, diese Datei

## Offene Grenzen

Kein neuer VPS- oder physischer Gerätelauf in diesem Slice. Der frühere
Interrupt-Gerätenachweis belegt nicht die neuen Picker oder Modellwechsel.
Providerverfügbarkeit, echte Modellanwendung und echtes 0.156.1-Usage-Replay sind
noch gegen den VPS zu prüfen; die obigen Laufzeitfälle nutzen deterministische
Gegenstellen. Kein Deployment und keine Installation auf dem Tablet vorgenommen.
Dialoglayout, große Schrift, TalkBack und Hardwarebedienung benötigen Gerätetest.

Der Katalog ist eine Momentaufnahme; der Server validiert Overrides erneut und
Codex kann sie weiterhin ablehnen. Keine Exactly-once-Zusage. Keine dauerhafte
Speicherung lokaler Auswahl oder Reroute-Telemetrie über Prozessverlust. Keine
zusätzliche Usage-Route, Settings-RPCs, Accountlimits, Compact- oder Config-Proxys.
