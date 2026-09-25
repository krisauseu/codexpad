> Archivhinweis vom 18.09.2026: Historischer Bericht; Befunde unverändert. Nur dieser Bericht war in den kopierten Verzeichnissen vorhanden. Die unten genannten Rohdaten, Schemas und der Testclient von Spike B wurden nicht mitgeliefert und sind nicht archiviert.

# CodexPad – command/exec PTY Persistence Mini-Spike

Testzeitpunkt: 2026-09-18, Europe/Berlin

## Environment

- OS: Ubuntu 24.04.5 LTS
- Kernel: `Linux 6.8.0-139-generic #139-Ubuntu SMP PREEMPT_DYNAMIC Sat Aug 1 03:52:05 UTC 2026 x86_64`
- Codex: `codex-cli 0.154.0`
- Aufgerufene Binary: `/root/.local/bin/codex`
- Aufgelöstes Binary: `/root/.codex/packages/standalone/releases/0.154.0-x86_64-unknown-linux-musl/bin/codex`
- App Server: `codex app-server --listen unix:///root/codexpad-pty-spike/workspace/app-server.sock`; ausschließlich lokaler Unix-Socket, kein TCP-Port
- Sandbox: **PASS**. Ein harmloses `command/exec` mit `sandboxPolicy: {"type":"workspaceWrite"}` endete mit Exitcode 0 und meldete `APP_SERVER_SANDBOX_OK`. Das systemweite `bubblewrap` fehlt; Codex 0.154.0 meldete den dokumentierten Fallback auf sein gebündeltes Bubblewrap. Der Sandbox-Start selbst war erfolgreich.
- App-Server-Persistenz: Derselbe App-Server-Prozess (Host-PID `1624489`) lief vor, zwischen und nach beiden absichtlichen Client-Disconnects weiter.
- Relevante Kommandos in 0.154.0: `codex app-server`, `codex app-server daemon`, `codex app-server proxy`, `codex app-server generate-json-schema`. Der aktuelle direkte Sandbox-Unterbefehl heißt `codex sandbox` und verlangt hier ein konfiguriertes Permission Profile; der maßgebliche Sandbox-Test erfolgte deshalb direkt über die zu untersuchende strukturierte `command/exec`-API.

## Test Matrix

| Test | Prozess gestartet | PID nach Disconnect | Handle nach Reconnect | Reattach | Ergebnis |
| ---- | ----------------- | ------------------- | --------------------- | -------- | -------- |
| `command/exec` ohne PTY | Ja; Namespace-PID `2`, Host-PID `1626101`; Handle vor Disconnect verwendbar | Nein, nach 1,1 s nicht mehr vorhanden | Nein; `command/exec/write` → `-32600 no active command/exec` | Nein | Prozess wird beim Client-Disconnect beendet |
| `command/exec` mit PTY | Ja; Namespace-PID `2`, Host-PID `1626173`; Handle vor Disconnect verwendbar | Nein, nach 1,1 s nicht mehr vorhanden | Nein; `command/exec/resize` → `-32600 no active command/exec` | Nein | PTY-Prozess wird beim Client-Disconnect beendet |

Die PID-Prüfung verwendete nicht nur die in der Sandbox sichtbare Namespace-PID `2`, sondern ordnete sie über `/proc/<pid>/status` (`NSpid`) und einen testfallspezifischen Marker der tatsächlichen Host-PID zu. Gegen PID-Wiederverwendung wurde zusätzlich der Startzeit-Tick aus `/proc/<pid>/stat` geprüft. Nach dem Disconnect gab es auch keinen weiteren Prozess mit dem jeweiligen Marker.

## Protocol Evidence

Transport: strukturierte JSON-RPC-Nachrichten über WebSocket auf dem lokalen Unix-Socket. Der Clienttransport wurde abrupt geschlossen; der App Server wurde nicht beendet. Danach wurde eine neue WebSocket-Verbindung geöffnet und vollständig neu initialisiert. Es wurde keine Ersatz-Shell gestartet.

### Ohne PTY

- Verbindung 1: `initialize` Request `id: 1` → erfolgreich; danach `initialized`.
- `command/exec` Request `id: 10`, `tty: false`, `streamStdin: true`, `streamStdoutStderr: true`, `processId: "codexpad-pipe-6e437e63a75e"`, `sandboxPolicy.type: "workspaceWrite"`.
- `command/exec/outputDelta` für dieselbe `processId` lieferte `PID=2 MARKER=CODEXPAD_PIPE_9d96ef0196d6`.
- Vor Disconnect: `command/exec/write`, `id: 11`, dieselbe `processId` → `result: {}`. Das Handle war aktiv.
- Nur Verbindung 1 geschlossen; App Server blieb aktiv. Host-PID `1626101` war nach 1,1 s nicht mehr vorhanden.
- Verbindung 2: `initialize` Request `id: 20` → erfolgreich; danach `initialized`.
- Reconnect-Probe: `command/exec/write`, `id: 21`, alte `processId` → Fehler `-32600`, `no active command/exec for process id ...`.

### Mit PTY

- Verbindung 1: `initialize` Request `id: 1` → erfolgreich; danach `initialized`.
- `command/exec` Request `id: 10`, `tty: true`, Größe `80x24`, `processId: "codexpad-pty-59cdad455753"`, `sandboxPolicy.type: "workspaceWrite"`. PTY impliziert die Streaming-Funktionen; sie wurden zusätzlich explizit gesetzt.
- `command/exec/outputDelta` für dieselbe `processId` lieferte `PID=2 MARKER=CODEXPAD_PTY_ee86608ac6f7` (PTY-Zeilenende normalisiert).
- Vor Disconnect: `command/exec/resize`, `id: 11`, dieselbe `processId` → `result: {}`. Handle und PTY waren aktiv steuerbar.
- Nur Verbindung 1 geschlossen; App Server blieb aktiv. Host-PID `1626173` war nach 1,1 s nicht mehr vorhanden.
- Verbindung 2: `initialize` Request `id: 20` → erfolgreich; danach `initialized`.
- Reconnect-Probe: `command/exec/resize`, `id: 21`, alte `processId` → Fehler `-32600`, `no active command/exec for process id ...`.

Die von 0.154.0 erzeugte experimentelle Protokoll-Schema-Datei bestätigt die beobachtete Semantik ausdrücklich: `command/exec`-Output und die clientseitig vergebene `processId` sind verbindungsgebunden; beim Schließen der Ursprungsverbindung beendet der Server den Prozess. Die offizielle App-Server-Dokumentation beschreibt `processId` als Handle für `command/exec/write`, `command/exec/resize` und `command/exec/terminate`: <https://developers.openai.com/codex/app-server>.

Rohdaten:

- `logs/pipe.jsonl`: Requests, Responses, Events und Transportereignisse des Pipe-Tests
- `logs/pty.jsonl`: Requests, Responses, Events und Transportereignisse des PTY-Tests
- `results/pipe.json`, `results/pty.json`: PID-/Handle-Auswertung
- `logs/schema/`: von Codex 0.154.0 generiertes Protokollschema
- `logs/app-server.log`: App-Server-Hinweis zum gebündelten Bubblewrap

## Conclusion

1. **Nein.** Ein erfolgreich sandboxgestartetes `command/exec` ohne PTY überlebt den Client-Disconnect nicht; die verifizierte Host-PID war nach 1,1 Sekunden beendet.
2. **Nein.** Ein erfolgreich sandboxgestartetes `command/exec` mit PTY überlebt den Client-Disconnect ebenfalls nicht; die verifizierte Host-PID war nach 1,1 Sekunden beendet.
3. Der Bedingungsfall trat nicht ein: In beiden Tests lebte der OS-Prozess nicht weiter. Unabhängig davon konnte der neue Client das alte Handle nicht erreichen.
4. Die `processId` ist **verbindungsgebunden**, nicht reconnectfähig. Sie funktionierte vor dem Disconnect und wurde auf der neu initialisierten Verbindung als nicht aktiv abgewiesen.
5. **Ja.** Falls CodexPad Terminals über Client-/Transportabbrüche hinweg erhalten soll, braucht es voraussichtlich eine zusätzliche Persistenzlösung außerhalb von `command/exec` (einschließlich eigener Lebenszyklus-, Reattach- und Sicherheitslogik). `process/spawn` wurde nicht als Ersatz getestet, weil `command/exec` bereits eindeutig war und `process/spawn` laut Protokoll außerhalb der Codex-Sandbox läuft.
