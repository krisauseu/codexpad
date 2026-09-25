# Android-Client-Nachweis · 25. September 2026

> Historischer Nachweis des ersten Client-Slices. Aktueller HTTPS-/Auth-Nachweis:
> [verification-https.md](../docs/verification-https.md). Die damaligen tokenlosen
> Startbefehle gelten nur für den damaligen Stand.

## Ergebnis

Debug-APK auf diesem Apple-Silicon-Mac gebaut und auf dem angeschlossenen Tablet YLE_W09 mit Android 16 installiert. Getestet gegen einen ausschließlich lokalen Server mit synthetischem Codex-Backend. Kein VPS-Zugriff, kein echter Modellturn, keine Aussage über den noch ausstehenden Remote-Transport.

## Automatisierte Prüfungen

```sh
python3 -B android/tools/contract_server.py
# separates Terminal:
CODEXPAD_CONTRACT_URL=http://127.0.0.1:18765 android/build-local.sh :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

- Debug-Build erfolgreich, keine Compilefehler.
- 11 JVM-Tests bestanden, 0 Fehler, 0 übersprungen im Lauf mit Testgegenstelle.
- Android Lint: keine Fehler. Sechs Hinweise betreffen neuere verfügbare Gradle-/Bibliotheksversionen; die getesteten Versionen bleiben bewusst gepinnt.
- Syntaxprüfung der beiden Python-Dateien erfolgreich.

| Testgruppe | Nachweis |
| --- | --- |
| Timeline (5) | Legacy-Parsing, unbekannte Itemtypen, SSE-Kommentare/mehrzeilige Daten, vollständige History ersetzt lückenhafte Deltas trotz anderer IDs, queued Events und verspätetes POST-Ack überschreiben keinen abgeschlossenen Turn |
| HTTP (3) | alle acht Endpunkte und Request-Bodies, lesbare Fehler und kodierte IDs, genau ein POST bei Verlust der Antwort |
| ThreadSession (2) | Snapshot → History → SSE auch nach Disconnect, offline abgeschlossener Turn ohne Endevent, Polling heilt fehlendes Endevent, keine Turn-POSTs beim Reconnect |
| Python-Vertrag (1) | echte CodexPad-Routen: Workspace mit Leerzeichen/Umlaut, Threadliste/-anlage, Snapshot, History, Turnstart, SSE-Delta, Streamabbruch, zweiter Snapshot, vollständige Endantwort und weiterer Turn |

Vor der kleinen Serverkorrektur scheiterte der Python-Vertragstest reproduzierbar mit `404 Unknown workspace` für `demo%20space-%C3%A4`; nach segmentweiser URL-Dekodierung besteht er. Der Backend-Simulator ist nur ein Testhilfsmittel; die produktiven HTTP-Routen stammen aus dem vorhandenen Server.

## Tablet-Prüfung

USB-Verbindung; Debug-APK `dev.codexpad`, Version 0.1.0; Darstellung mit 3000 × 1920 Pixeln im Querformat. `adb reverse tcp:8765 tcp:18765` verband die unveränderte Entwicklungs-URL mit der lokalen Fixture.

Geprüft:

- Workspace aus dem Serverkatalog ausgewählt, Threadliste geöffnet.
- Neuen Thread über die App angelegt; Detail mit initialem History-/SSE-Abgleich geöffnet.
- Testnachricht gesendet, User-/Agentdarstellung und Turnstatus sichtbar.
- App während des Turns in den Hintergrund gelegt; nach Rückkehr vollständige Antwort `CODEXPAD-CLIENT-OK` genau einmal aus der History angezeigt.
- Bestehenden Thread fortgesetzt und nach APK-Update wieder aus der Liste geöffnet.
- Nachscrollen bei neuen Antworten nach visueller Prüfung korrigiert und anschließend am Tablet bestätigt: der laufende Agenttext bleibt sichtbar. Nachrichteneinträge werden einzeln lazy gerendert. Manuelles Zurückscrollen und die dabei erscheinende Aktion „Zu den neuesten Nachrichten“ ebenfalls am Tablet geprüft.

Die APK bleibt installiert. Lokaler Testserver beendet, USB-Testweiterleitung und temporäre UI-Dump-Datei vom Tablet entfernt. Die App wurde zur Workspace-Auswahl zurückgeführt und in den Hintergrund gelegt. Keine globale Systemeinstellung des Tablets wurde geändert; USB-Debugging war bereits aktiv. Screenshots und lokale Prüfberichte liegen ausschließlich in ignorierten Build-/`.local`-Verzeichnissen.

## Grenzen und nächster Test

Keine Prüfung gegen den VPS oder ein echtes Modell. Keine umfassende Rotation-/Prozessverlust-/TalkBack-/Split-Screen-Matrix. Kein Lasttest langer Histories. Die eingebauten Begrenzungen des bestehenden Servers (u. a. keine Approvals und kein Abbruch-Endpunkt) bleiben bestehen.

Der damals geplante Tunneltest wurde durch den beauftragten direkten HTTPS-Slice abgelöst. Der aktuelle nächste Schritt ist der [lesende VPS-Preflight](../docs/deployment.md#exakt-nächster-deployment-schritt-lesender-vps-preflight). Auch für die weiterhin mögliche lokale ADB-Debug-Verbindung ist nun ein Token erforderlich.
