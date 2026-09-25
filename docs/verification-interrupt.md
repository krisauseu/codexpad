# Gerätetest: laufenden Codex-Turn stoppen

25. September 2026. Echter Android→HTTPS→VPS→Codex-App-Server-Lauf.

## Teststand

- USB-Tablet YLE_W09, Android 16, native App `dev.codexpad`.
- Debug-APK aus dem lokal geprüften Interrupt-Slice per `adb install -r` aktualisiert;
  App-Daten und gespeicherte Zugangsdaten beibehalten.
- Verbindung der App: `https://pad.feichti.dev`; keine ADB-Reverse-Weiterleitung.
- VPS: `codex-cli 0.156.1`; History des neuen Testthreads bestätigt ebenfalls
  `cliVersion: 0.156.1`. Dienst `codexpad` läuft als Benutzer `codexpad`.
- Der VPS hatte zunächst noch keinen Interrupt-Endpunkt. Vor dem Update wurden alle
  drei bestehenden Threads gelesen: idle, sämtliche Turns completed. Danach wurde
  nur der lokal geprüfte Python-Adapter installiert und der Dienst neu gestartet.
  Sicherung: `/opt/codexpad/server/codexpad_server.py.pre-interrupt-20260925`.
- SHA-256 Server lokal und auf VPS:
  `625719e62e943607a00f207896c91b46d5cf7b85187cfad333236c79e629c9df`.
- SHA-256 installierte APK:
  `f6f63716688f3f70506f7a16d634a8a1f0885c793762cd3daeba810a097fa089`.

## Beobachteter Ablauf

Im Workspace `testprojekt` wurde über die native App ein eigener Thread angelegt:
`01a0d8f5-638c-7ca0-9230-e0a55fca37fe`.

1. Über das Textfeld und „Senden“ einen längeren reinen Textauftrag gestartet,
   ausdrücklich ohne Tools oder Dateiänderungen. Die UI zeigte `active`,
   `inProgress` und den nativen „Stoppen“-Button.
2. „Stoppen“ zweimal unmittelbar nacheinander per ADB-Touch betätigt. Danach zeigte
   die App `interrupted`, Threadstatus `idle` und wieder „Senden“. Kein sichtbarer
   Fehler durch die zweite Betätigung. Die genaue RPC-Anzahl wurde nicht mitgeloggt.
3. App mit Home in den Hintergrund gelegt und wieder geöffnet. Nach erneutem
   Live-/History-Abgleich blieb der Turn `interrupted`.
4. Zusätzlich History direkt über den authentifizierten Adapter auf dem VPS gelesen:
   Turn `01a0d8f5-f8d5-7a63-af82-740dfa2ea5fc` ist `interrupted` und enthält nur
   das User-Item. Keine Tool-/Datei-Items.
5. Im selben Thread über die App eine kurze Folgeanfrage gesendet. Turn
   `01a0d8f7-42bd-7193-9d65-34b06db7b5a3` wurde `completed`; App und History
   zeigen die Antwort `CODEXPAD-RESUME-OK`.
6. Ein zusätzlicher HTTP-Interrupt ausschließlich für die alte, bereits
   unterbrochene Turn-ID wurde mit 409 abgewiesen. Die anschließende History
   enthält unverändert den alten interrupted- und den neuen completed-Turn.

UI-Zustände wurden über die native UI-Hierarchie gelesen. Keine Tokens ausgegeben
oder aus dem Gerät extrahiert. Die temporäre UI-Dump-Datei wurde entfernt. APK und
aktualisierter Server bleiben installiert; die App zeigt den abgeschlossenen
Testthread. Bestehende Benutzerthreads wurden nicht verändert.

## Aussagegrenzen

Dieser Lauf belegt den echten nativen Interrupt und die Fortsetzung samt
History-/Reconnect-Abgleich gegen Codex 0.156.1. Der Übergang „Wird gestoppt …“
war bei der ersten UI-Abfrage bereits durch den Endzustand ersetzt; seine Dauer
und deaktivierte Darstellung wurden auf dem Gerät nicht separat erfasst.

Verlorene HTTP-Antwort, exakt gleichzeitiges Turn-Ende, failed-Endzustand und
Wiederherstellung eines noch unklaren Interrupts wurden nicht künstlich auf dem
VPS herbeigeführt. Dafür bestehen die automatisierten Session-/HTTP-Regressionstests
und der Kotlin/Python-Vertragstest des Slices. Die zwei schnellen Gerätetaps sind
kein Ersatz für den automatisierten Nachweis genau eines Client-POSTs.
