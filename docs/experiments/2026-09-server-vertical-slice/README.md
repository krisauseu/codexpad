# Lokaler Server Vertical Slice (24. September 2026)

## Ergebnis und Umgebung

Auf diesem Host lief `codex-cli 0.156.1` unter Linux 6.8.0-139-generic x86_64 mit Python 3.12.3. `codex app-server` ist ein Unterkommando desselben Binaries; der Handshake lieferte `userAgent: codexpad_local/0.156.1`, `platformFamily: unix`, `platformOs: linux`. Die lokale Binary exportierte das App-Server-Schema; die hier genutzten Methoden und Parameter wurden zusätzlich im Lauf geprüft. Die älteren 0.154.0-/0.155.0-Befunde bleiben historische Evidenz.

Protokollreferenz: [offizielle OpenAI-Dokumentation zum Codex App Server](https://learn.chatgpt.com/docs/app-server). Die tatsächliche installierte Binary und ihr Schema waren für diesen Test maßgeblich.

Ein lokaler HTTP-Server aus der Python-Standardbibliothek startet `codex app-server --stdio` als Kindprozess. CodexPad besitzt einen kleinen festen Satz fachlicher Routen; HTTP-Clients können keine beliebigen App-Server-RPCs benennen. Der Prozess lauscht ausschließlich auf `127.0.0.1`. Test-Workspace war `/tmp/codexpad-vertical-slice/workspace-a`, über die ID `workspace-a` erreichbar. Das Root ist über `CODEXPAD_WORKSPACE_ROOT` konfigurierbar und umfasst nur direkte Unterverzeichnisse.

```text
lokaler Testclient -> HTTP/SSE 127.0.0.1 -> CodexPad-Server
                                         -> privates stdio -> Codex App Server -> Workspace
```

## Eigene API und Upstream-Operationen

| CodexPad-Route | Verhalten | App-Server-RPC |
| --- | --- | --- |
| `GET /health` | Prozess- und Handshake-Information | `initialize` beim Start |
| `GET /workspaces` | direkte Verzeichnisse des konfigurierten Roots, stabile IDs | keiner |
| `GET /workspaces/:id/threads` | alle Seiten für exakt dieses `cwd` | `thread/list` |
| `POST /workspaces/:id/threads` mit `{}` | dauerhaften Thread anlegen | `thread/start` |
| `GET /threads/:id` | aktuellen Threadstatus lesen | `thread/read` |
| `POST /threads/:id/turns` mit `{"message":"..."}` | Text-Turn starten | `thread/resume`, `turn/start` |
| `GET /threads/:id/history` | vollständige Legacy-Turns und Items lesen | `thread/read` mit `includeTurns:true` |
| `GET /threads/:id/events` | Snapshot, dann Live-Ereignisse als SSE | `thread/resume`, `thread/read`, Notifications |

Alle Thread-Routen vergleichen das vom App Server gelieferte `cwd` mit der konfigurierten Workspace-Liste. Neue Threads verwenden `approvalPolicy:"never"`; Modell, Provider und sonstige Codex-Konfiguration stammen aus der vorhandenen lokalen Codex-Installation. Der Server sendet `initialize` und `initialized`; `initialize` enthält kein `experimentalApi`-Opt-in. `historyMode` wird nicht gesetzt, weil Legacy der dokumentierte Default ist und das Feld selbst experimentell ist. Der Client erhält Thread-/Turn-Objekte als strukturierte JSON-Daten. Es wird keine CLI-Ausgabe geparst.

## Streaming und Reconnect

SSE ist hier einfacher als ein eigener WebSocket-Server: es reicht ein einseitiger Eventkanal; Turnstart und Historienabruf laufen separat über HTTP. Der Server abonniert vor dem Snapshot den betreffenden Thread, ruft `thread/resume` und `thread/read` auf und sendet zuerst ein SSE-Event `snapshot` mit dem aktuellen Thread samt Turns. Danach folgen App-Server-Notifications als `event`, jeweils mit `method` und `params`. Heartbeats halten die Verbindung offen. Die Backendverbindung und der Modell-Turn bleiben beim Schließen der Client-SSE-Verbindung bestehen.

SSE ist kein persistentes Eventjournal. Bei Reconnect muss der Client den neuen Snapshot und bei Bedarf `GET /threads/:id/history` lesen. Abgeschlossene Turn-Items aus der History ersetzen lokal zusammengesetzte, möglicherweise lückenhafte Textdeltas. Während eines laufenden Turns kann Zwischeninhalt fehlen; ältere queued Events nach dem Snapshot dürfen einen bereits abgeschlossenen Snapshot nicht überschreiben. Thread-/Turn-IDs sind die Zuordnung, rekonstruierte Legacy-Item-IDs können von Live-IDs abweichen. Ein unbekannter Ausgang von `POST /turns` darf nicht blind erneut gesendet werden.

## Nachweis

`server/smoke.py` prüft die folgenden Schritte gegen den laufenden lokalen Server:

1. Health, Workspace-Liste und Thread-Liste lesen.
2. Thread erzeugen und laden.
3. SSE-Stream öffnen, Snapshot empfangen, echten Modell-Turn starten, Textdelta empfangen.
4. Erste SSE-Verbindung schließen; neue Verbindung zu derselben Thread-ID öffnen und Snapshot empfangen.
5. History wiederholt lesen, bis der Turn abgeschlossen ist; Status und vollständigen Agent-Text samt Marker `CODEXPAD-RECONNECT-OK` prüfen.
6. Thread erneut laden und in der Workspace-Liste wiederfinden.

Der Test war mit `codex-cli 0.156.1` erfolgreich: Turnstatus `completed`, zahlreiche `item/agentMessage/delta`-Events, vollständige Zahlenfolge 1–80 plus Marker in der History. Der erste Stream wurde vor dem Abschluss geschlossen. Kein öffentlicher Listener wurde eingerichtet.

## Bekannte Grenzen

- Nur lokaler persönlicher Betrieb: keine Authentifizierung, Multi-User-Trennung, Transportverschlüsselung oder harte Workspace-Isolation. `cwd`-Prüfung ist eine fachliche Zuordnung, keine Host-Sicherheitsgrenze.
- Kein Approval-UI/Antwortpfad. Unverlangte App-Server-Requests werden mit Fehler beantwortet; neue Threads nutzen `approvalPolicy:"never"`. Der nächste fachliche Ausbau muss offene Approval-Requests sichtbar machen, korrekt einem Thread zuordnen und ihre Antwort-/Reconnect-Rennen behandeln.
- SSE-Events werden nicht dauerhaft gespeichert. Ein langsamer Client kann Deltas verlieren; History ist maßgeblich. Keine generische Reconciliation-Engine oder garantierter partieller Live-Text.
- Der App Server ist an die CodexPad-Prozesslebensdauer gekoppelt. Client-Disconnect wurde geprüft; Serverprozessausfall während eines laufenden Turns und anschließendes automatisches Recovery nicht.
- Keine Idempotenz für `thread/start`/`turn/start` bei verlorener HTTP-Antwort, keine Produktionslimits für parallele Clients oder große Historien. `thread/read` mit voller Legacy-History ist für sehr lange Threads nicht paginiert.
- Kein Terminal, Dateimanager, Git-GUI, Android-Client oder Remote-Transport.

**Nächster Android-Schritt:** Eine minimale Kotlin/Compose-Ansicht für Workspace-Liste, Thread-Liste und Thread-Detail an genau diese API binden. Im Thread-Detail zuerst Snapshot/History laden, dann SSE-Deltas anzeigen und bei Reconnect erneut Snapshot/History laden. Der Verbindungsweg zu `127.0.0.1` bleibt für diese lokale Phase ein Testaufbau; öffentliches Deployment ist nicht Teil dieses Spikes.
