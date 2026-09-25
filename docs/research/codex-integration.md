# Codex als Agent für einen nativen Client

Stand: 18. September 2026. Konsolidierte Research; keine Implementierung. Vier begrenzte Architekturentscheidungen stehen in den [ADRs](../decisions/README.md).

## Evidenz und Gültigkeit

Untersucht wurde ein frischer, flacher Checkout des offiziellen Repositorys `openai/codex`, Branch `main`, Commit [`7498521d288b9b3b96ffba4eedf089d8d6e06a84`](https://github.com/openai/codex/commit/7498521d288b9b3b96ffba4eedf089d8d6e06a84), Commit-Zeit `2026-09-18T00:59:10Z`. Das ist ein Quellcode-Snapshot, keine Behauptung über die zuletzt veröffentlichte stabile CLI. Der Checkout lag ausschließlich im temporären Rechercheverzeichnis; kein Fork wurde erstellt. In dieser ursprünglichen Quellrecherche wurden weder Codex gebaut noch Integrationstests ausgeführt. Danach folgten zwei Linux-Spikes mit dem unveränderten Release 0.154.0; deren Befunde stehen unten getrennt vom Quellcode-Snapshot.

**Fakt** bezeichnet nachgelesene Dokumentation oder Code. **Experiment** bezeichnet beobachtetes Verhalten der Spikes, keine allgemeine Versionsgarantie. **Architekturfolgerung** bzw. **Hypothese** bezeichnet eine Schlussfolgerung für CodexPad; akzeptierte Entscheidungen verlinken einen ADR. **Offen** bezeichnet fehlende praktische oder vertragliche Absicherung. Quellen wurden am obigen Datum gelesen. Die Dokumentation unter `developers.openai.com/codex/app-server` leitet inzwischen auf `learn.chatgpt.com/docs/app-server` weiter. Online-Dokumentation und `main` können einer installierten Version voraus sein. Ein konkreter Unterschied: Die Online-Dokumentation bezeichnet `thread/turns/list` als experimentell; in `common.rs` des geprüften Commits trägt die Methodendeklaration kein entsprechendes `#[experimental]`-Gate. Darum die tatsächlich verwendete Binary samt Schema prüfen und nicht aus einer Webseite pauschal den Reifegrad aller Felder ableiten. [D1, S2]

## Ergebnis

**Fakt:** Es gibt einen ausdrücklich für reichhaltige eigene Clients vorgesehenen App-Server mit bidirektionalem, strukturiertem Protokoll. Ein eigener Android-Client benötigt dafür keinen Codex-Fork. Die öffentliche Dokumentation nennt Authentifizierung, Gesprächshistorie, Approvals und Agent-Events als Anwendungsfälle. JSONL über stdio sowie WebSocket-Transporte sind beschrieben. Der App-Server-Befehl und der WebSocket-Betrieb sind in der aktuellen Dokumentation ausdrücklich experimentell und nicht für Produktionslasten unterstützt. Davon zu unterscheiden ist die dort als stabil bezeichnete Teilmenge der RPCs ohne Experimental-Opt-in. [D1, D2]

**Entscheidung:** Der App-Server ist die primäre Agent-Schnittstelle für den derzeitigen Umfang, siehe [ADR 0001](../decisions/0001-app-server-interface.md). Kotlin kann die JSON-Nachrichten selbst verarbeiten; Rust-Core oder Node.js müssen deshalb nicht in die Android-App eingebettet werden. Die Wahl des Remote-Transports bleibt offen.

## Experimenteller Stand 0.154.0

[Spike A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md) nutzte Ubuntu 24.04.4, WebSocket über lokalen Unix-Socket und `experimentalApi:true`. Ein unabhängiger Python-Client steuerte das unveränderte Binary ohne CLI-Textparsing. Threads, Modell-Turns, Datei-Approvals mit Accept/Decline und Recovery funktionierten. Die Shell-Sandbox scheiterte auf diesem Host beim Bubblewrap-Netzwerksetup; Änderungen einer bestehenden Datei und Agent-Hintergrundterminals sind dadurch nicht erfolgreich nachgewiesen. Neue Dateien über genehmigte Dateiänderungen funktionierten.

[Spike B](../experiments/2026-09-command-exec-persistence/pty-persistence.md) prüfte auf einem separaten Ubuntu-24.04.5-VPS mit erfolgreicher regulärer Sandbox `command/exec` ohne und mit PTY. Beide Prozesse starteten und waren vor Disconnect steuerbar. Nach abruptem Clientverlust starben die Hostprozesse, obwohl derselbe App Server weiterlief. Die Host-PID-Prüfung berücksichtigte Namespace-PIDs und Prozessstartzeit. Die neue Verbindung erhielt für das alte `processId` einen Fehler; kein Reattach. Für B liegt nur der Bericht vor, keine mitgelieferten Rohdaten.

Diese Experimente belegen keine Android-, TCP-/WSS-, Last- oder allgemeine Versionskompatibilität. Der erste Spike verwendete experimentelle APIs und externen Token-Login im Speicher; eine Minimalmenge ohne Opt-in und der Tablet-Login sind weiterhin offen.

## Komponenten und ihre Grenzen

| Komponente | Verifizierte Rolle | Bedeutung für CodexPad |
| --- | --- | --- |
| `codex-rs/cli`, `tui` | Kommando-Einstieg und Terminaloberfläche | CLI auf Linux starten; TUI nicht als Android-Oberfläche übernehmen. |
| `core` | Agent-Geschäftslogik für Rust-Oberflächen, mit Betriebssystem- und Sandbox-Annahmen | Upstream unverändert betreiben, keine JNI-Portierung als Voraussetzung. |
| `app-server` | Client-RPC, Notifications, Server-Requests, Zugriff auf Agent-Funktionen | Hauptkandidat für den Codex-Adapter. |
| `app-server-protocol` | Typisierte v2-Nachrichten, Schema-Export und Experimental-Markierungen | Drahtvertrag anhand der tatsächlich eingesetzten Version prüfen. |
| `app-server-client`, `app-server-transport` | Rust-Client-/Transportbausteine | Referenz für Verhalten, kein fertiges Kotlin-SDK. |
| `exec`, TypeScript-SDK | Nichtinteraktive Ausführung; SDK startet CLI und verarbeitet JSONL | Gut für Jobs und Tests; weniger passend für eine vollständige interaktive Approval-Oberfläche. |
| `rollout`, `state`, `thread-store` | Persistenz und Wiederherstellung von Threads | Über APIs verwenden, Speicherformate nicht zur eigenen Datenbank-API erklären. |
| `app-server-daemon` | Experimentelle Lifecycle-Verwaltung für Remote-Clients, auch Start über SSH | Möglicherweise weniger Eigenentwicklung auf Linux nötig. |
| `exec-server`, `code-mode-host` | Weitere Ausführungs-/Host-Komponenten im Snapshot | Kein Nachweis eines stabilen, agentneutralen Workspace-Vertrags. Separate spätere Prüfung. |

Belege: [S1–S5, S9]. Aus der Open-Source-Verfügbarkeit dieser Komponenten folgt nicht, dass die komplette Desktop-Oberfläche als wiederverwendbare Android-Basis vorliegt.

## Strukturierter Ablauf

**Fakt:** Der App-Server verwendet JSON-RPC-artige Requests mit `method`, `params`, `id`, Antworten mit `result` oder `error` und Notifications ohne `id`. Das Feld `jsonrpc: "2.0"` wird auf dem Draht ausgelassen. stdio nutzt zeilengetrenntes JSON, WebSocket einen JSON-RPC-Inhalt pro Textframe. Der Client initialisiert die Verbindung und bestätigt mit `initialized`. Schema-Export ist über `codex app-server generate-json-schema` bzw. `generate-ts` vorgesehen. [D1, S2]

Die fachliche Abfolge lautet:

1. Verbindung und Fähigkeiten initialisieren.
2. Auth-Status lesen; bei Bedarf den dokumentierten Login beginnen.
3. Mit `thread/start` einen Thread im gewünschten Arbeitsverzeichnis beginnen oder `thread/resume` mit gespeicherter Thread-ID aufrufen.
4. Mit `turn/start` eine Eingabe senden.
5. Thread-, Turn- und Item-Ereignisse darstellen; Server-Requests gesondert beantworten.
6. Bei Bedarf `turn/interrupt`; Historie über `thread/read` und `thread/list` wieder einlesen.

**Fakt aus dem Snapshot:** `thread/fork` verzweigt die Historie. Thread, Turn und Item sind unterschiedliche Identitäten. Der Client muss sie getrennt speichern. `thread/read` dient dem Lesen, `thread/resume` dem Laden/Fortsetzen. JSONL-Rollouts und SQLite-Zustand sind an der Persistenz beteiligt. Standardmäßig liegen Sessions unter dem Codex-Home; die SDK-Dokumentation nennt `~/.codex/sessions`. Ephemere Threads und konfigurierbares `CODEX_HOME` schließen eine universelle Annahme über Dateipfade aus. [S2, S4, S5]

**Hypothese:** Auf Android nur eigene Anzeigezustände, Host-/Workspace-Zuordnung und Thread-IDs cachen. Die Agent-Historie bleibt vom Linux-Codex verwaltet. Direkter Zugriff auf Rollout-Dateien wäre fragil, unter anderem wegen Komprimierung, Indizes und zukünftiger Speicheränderungen.

## Tool-Aufrufe, Diffs und Approvals

**Fakt aus dem Protokoll:** Items umfassen unter anderem Nutzer-/Agent-Nachrichten, Plan, Reasoning, `commandExecution`, `fileChange`, `mcpToolCall` und weitere Tool-Arten. Start-, Delta- und Abschluss-Notifications erlauben inkrementelle Darstellung. `item/completed` liefert den abgeschlossenen Zustand. Befehlsausführung trägt unter anderem Kommando, Arbeitsverzeichnis, Status und Ausgabedaten; Dateiänderungen enthalten Pfade, Änderungstyp und Diff. Turn-Diffs werden separat aktualisiert. [S2, S3]

Ein Agent-Diff ist nicht automatisch der aktuelle vollständige Git-Diff. Manuelle Änderungen, andere Prozesse, Index, unversionierte Dateien und Änderungen während des Reviews müssen separat erhoben werden.

**Fakt:** Approval-Anfragen sind vom Server initiierte Requests, keine Textpassagen. Dazu gehören `item/commandExecution/requestApproval` und `item/fileChange/requestApproval`. Befehlsentscheidungen unterscheiden unter anderem einmalige Zustimmung, Zustimmung für die Session, Regeländerung, Ablehnung und Abbruch. Netzwerk-Approvals können Host-/Protokoll-Kontext statt eines aussagekräftigen Shell-Befehls tragen. Weitere Interaktionen sind Permission-Anfragen, MCP-Elicitations und Benutzerrückfragen. [S2, S3]

**Hypothese:** Eine native Approval-Ansicht braucht Thread/Host/Workspace, konkreten Vorgang, Umfang und Lebensdauer der Freigabe. Unbekannte Entscheidungen dürfen nicht stillschweigend in Zustimmung übersetzt werden. Ein neu verbundener Client muss ausstehende Requests mit ihren IDs rekonstruieren und doppelte Antworten verhindern. Für Agent-Tools bleiben die Core-Policies serverseitig maßgeblich; direkte Host-RPCs besitzen eine andere Grenze.

## Workspace-Funktionen sind bereits vorhanden

**Fakt aus `v2/fs.rs`:** `fs/readFile`, `writeFile`, `createDirectory`, `getMetadata`, `readDirectory`, `remove`, `copy`, `watch` und `unwatch` existieren. Pfade sind absolute Host-Pfade; Dateiinhalt wird Base64-kodiert übertragen. Die geprüften Read-/Write-Parameter enthalten weder Offset-/Chunk-Felder noch eine erwartete Dateiversion. `readDirectory` gibt die direkten Einträge zurück, ohne Pagination-Feld in diesem Schema. Watch-IDs sind verbindungsgebunden. [S6]

**Experimentelle Ergänzung:** `fs/writeFile` schrieb außerhalb des Thread-Workspaces innerhalb des isolierten Spikes trotz geladenem Read-only-Thread ohne Approval. `process/spawn` startete ohne Agent-Sandbox und ohne Account-Login auf einer neuen lokalen Instanz. Damit ist die Trennung zwischen Modell-Accountauth, Agentberechtigungen und Client-/Hostrechten praktisch belegt. [Spike A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md), [ADR 0004](../decisions/0004-client-host-trust-boundary.md). Remoteauth und Isolation wurden nicht getestet.

**Zusätzlicher Sicherheitsbefund:** Der geprüfte `fs_processor.rs` wählt die lokale Ausführungsumgebung des Servers und übergibt bei Lese-/Schreiboperationen `sandbox: None`. Direkte Filesystem-RPCs erhalten damit in diesem Pfad keine Agent-Turn-Sandbox. Betriebssystemrechte und etwaige äußere Isolation gelten weiterhin. Ein authentifizierter UI-Client ist deshalb eine andere Vertrauensrolle als ein Agent mit eingeschränkten Tools; Workspace-Grenzen dürfen nicht allein durch die Android-Dateiansicht entstehen. [S13]

**Experiment:** Kleine Dateien, Verzeichnis-/Metadatenabruf, Schreiben und `fs/changed` nach Watching funktionierten. Git-Status und Unified Diff kamen über das ausdrücklich unsandboxierte `process/spawn`, ohne zusätzlichen Workspace-Daemon. Das ist kein Nachweis von Git über `command/exec`. Bei einem genehmigten Add war der Item-Diff der Dateiinhalt; `turn/diff/updated` wurde nicht beobachtet. [Spike A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md)

**Folgerung:** Für diesen geprüften Umfang ist kein eigener Dateidienst erforderlich. Große Dateien, Transfer-Resume, paginierte riesige Verzeichnisse, atomarer Konfliktschutz und ein Workspace-begrenztes Berechtigungsmodell sind damit nicht nachgewiesen. Ein `cwd` ist keine Sicherheitsgrenze. Remote-Pfade sind keine Android-Pfade.

**Fakt aus `v2/command_exec.rs`:** `command/exec` kann unabhängig von einem Agent-Turn eine Argumentliste unter der Server-Sandbox ausführen. PTY, stdin, Streaming, Resize und Terminate sind vorhanden. Die finale Antwort kommt erst nach Prozessende. Gestreamte Ausgabe wird nicht nochmals in der finalen Antwort dupliziert. Handles sind verbindungsgebunden. `process/*` ist dagegen ausdrücklich experimentell und startet Prozesse außerhalb der Codex-Sandbox. [S7]

**Architekturrelevanter Fakt:** Der Test `command_exec_process_ids_are_connection_scoped_and_disconnect_terminates_process` prüft ausdrücklich, dass ein anderer Client den Handle nicht steuern kann und der Prozess nach Disconnect endet. Der Watch-Manager entfernt Watches beim Verbindungsende. Dieser ursprüngliche Befund stammt aus dem Code. [S8] Die command/exec-Lebensdauer wurde inzwischen unabhängig in [Spike B](../experiments/2026-09-command-exec-persistence/pty-persistence.md) bestätigt; die Watch-Lebensdauer bleibt hier eine dokumentierte Codeeigenschaft.

## Reconnect ist mehr als Session Resume

**Fakt:** Upstream-Tests prüfen das Wiederanzeigen ausstehender Command- und File-Approvals bei `thread/resume`. Das belegt bestimmte Replay-Pfade im untersuchten Prozesszustand, keine unbegrenzte persistente Wiederholung aller Notifications über Serverneustarts. [S10]

**Experiment:** Der laufende Modell-Turn überlebte den Clientverlust mit gleicher Turn-ID; die Ausgabe sprang von Zeile 1 auf 8. Nach Abschluss waren alle 400 Zeilen über Historienabfragen abrufbar. Ein vollständig offline abgeschlossener Turn erschien als `completed` in der Historie, ohne nachgeliefertes Endevent. [Spike A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md)

Ein offenes Datei-Approval wurde nach `thread/resume` mit identischer Request-ID, Parametern und Zeitstempel erneut zugestellt und erfolgreich beantwortet. `thread/read` allein lieferte den offenen Vorschlag nicht vollständig. Bei Serververlust nach SIGTERM und anschließendem SIGKILL nach zehn Sekunden wurde der Turn dagegen als `interrupted` rekonstruiert; offenes Item und Approval fehlten. Abgeschlossene Historie und Gesprächskontext überlebten den Neustart. Das belegt keinen vollständig graceful abgeschlossenen Shutdown und keine universelle Approval-Persistenz.

**Architekturfolgerung:** Reconciliation ist erforderlich, siehe [ADR 0002](../decisions/0002-state-reconciliation.md).

```text
Live Events
     │
     ▼
State Reconciliation ──► UI
     ▲
     │
Persistierter Server-State
```

Live-Events liefern unmittelbare Darstellung. Nach Reconnect lädt der Client den autoritativen Serverzustand und die Historie erneut. Snapshot und währenddessen eintreffende Events müssen zusammengeführt werden. Vollständige abgeschlossene Inhalte ersetzen lückenhafte lokale Deltaverkettungen. Ein laufender partieller Text kann vorübergehend unvollständig bleiben. Legacy-Historie rekonstruierte andere Item-IDs als der Livestream; im geprüften paginierten Modus blieben abgeschlossene User-/Agent-IDs stabil. Eine allgemeine ID-/Mehrseiten-Garantie folgt daraus nicht.

| Ressource | Clientverlust bei lebendem Server | Serververlust / Neustart |
| --- | --- | --- |
| Thread / Conversation | Wiederfinden und Resume beobachtet | Abgeschlossene Historie und Kontext wiederhergestellt |
| Turn | Laufender Modell-Turn arbeitete weiter | Getesteter wartender Approval-Turn `interrupted`; andere aktive Aktionen nicht pauschal geprüft |
| Live Event Stream | Verpasste Deltas und Endevents nicht vollständig replayed | Kein dauerhaftes Eventjournal voraussetzen |
| Approval | Offenes Datei-Approval nach Resume erneut bedienbar | Offenes Approval im getesteten Verlustfall nicht wiederhergestellt |
| App-Server-Prozess | Blieb in den Disconnect-Tests aktiv | Neue Instanz mit persistierter Historie, kein fortgesetzter In-Memory-Zustand |
| `command/exec` | Aufruf/Handle an Ursprungsverbindung gebunden | Neustart-Reattach in B nicht separat getestet |
| PTY | Getestete command/exec-PTY endet mit Prozess | Keine unabhängige persistente Terminalsession belegt |
| OS-Prozess | Getestete command/exec-Prozesse in B und process/spawn-Prozesse in A beendet | process/spawn-PTY in A beendet; keine Aussage über beliebige Hostprozesse |

Belege: [A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md), [B](../experiments/2026-09-command-exec-persistence/pty-persistence.md). Agent-Hintergrundterminals bilden eine weiterhin ungeprüfte Kategorie. „Resume funktioniert“ ist ohne Ressource und Ausfallart keine ausreichende Aussage.

**Offen:** Wiederholte Disconnects, Snapshot-/Event-Rennen, verlorenes `turn/start`-Acknowledgement, Retry-/Idempotenzsemantik und Abbruch genau während einer Approvalantwort. Auch ein anderer bzw. längerer geordneter Shutdown ist nicht geprüft.

**Hypothese:** Nach Reconnect neu initialisieren, Thread-Zustand lesen/resumieren, offene Requests abgleichen, Watches neu registrieren und Dateiansichten/Git aktualisieren. Nicht bestätigte mutierende Requests nicht blind wiederholen. JSON-RPC-IDs sind keine automatisch wirksamen dauerhaften Idempotenzschlüssel. Ein dauerhaftes Entwicklerterminal benötigt eine Persistenzlösung außerhalb von `command/exec`, siehe [ADR 0003](../decisions/0003-terminal-lifecycle.md) und [Spike B](../experiments/2026-09-command-exec-persistence/pty-persistence.md). `tmux`, eigener PTY-Service oder andere serverseitige Sessionmodelle sind Untersuchungsoptionen, keine ausgewählte Technologie.

## Authentifizierung und Secrets

**Fakt:** Account-RPCs unterstützen Lesen, Login, Abbruch und Logout. Codex-verwalteter ChatGPT-Login, Device-Code und API-Key sind dokumentiert; externe ChatGPT-Tokens sind experimentell und für Hosts gedacht, die den Auth-Lebenszyklus bereits selbst besitzen. Die Login-Implementierung kennt dateibasierte Speicherung in `auth.json`, Keyring-Backends und automatische Auswahl/Fallback. [D1, S11]

**Hypothese:** Codex-Credentials zunächst auf dem Linux-Host belassen. Android verwaltet die Host-Verbindung; ein späterer Login-Dialog darf den dokumentierten Device-Code-/Browser-Ablauf vermitteln, ohne eine eigene ChatGPT-OAuth-Integration zu erfinden. Ein localhost-Callback auf Linux ist vom Tablet-Browser aus nicht automatisch erreichbar. Headless-Login und Kontorichtlinien müssen konkret geprüft werden.

Host-Zugang, App-Server-Transport-Authentifizierung und OpenAI-/Agent-Authentifizierung sind drei verschiedene Ebenen. Ein erfolgreicher Codex-Login schützt keinen offen erreichbaren Workspace-Endpunkt.

**Fakt aus der aktuellen Dokumentation:** Ein nicht auf Loopback beschränkter WebSocket-Listener erlaubt im beschriebenen Rollout standardmäßig unauthentifizierte Verbindungen. Auth-Flags und Bearer-Handshake sind vorgesehen. Für den ersten Versuch deshalb Loopback plus SSH-Tunnel erwägen; bei direktem Remote-WebSocket TLS und explizite Authentifizierung vorsehen. Diese Transportsicherheit ersetzt keine Autorisierung pro Workspace. [D1]

## Upstream-Daemon und weitere Remote-Komponenten

**Fakt:** `app-server-daemon` dokumentiert maschinenlesbare JSON-Antworten für Start, Stop, Version, Bootstrap und Update. Er ist ausdrücklich experimentell. Er unterstützt Linux und weitere Desktop-Systeme; die Dokumentation beschreibt SSH-gestützte Remote-Clients. Clients teilen die beim Daemonstart geerbte Umgebung, ohne Isolation pro Client. Updates können einen Neustart und Unterbrechungen auslösen. [S9]

**Hypothese:** Diesen Lifecycle untersuchen, bevor CodexPad selbst Installations-, Supervisor- und Update-Logik baut. Für reproduzierbare Exploration Version und Update-Verhalten festhalten. Die Existenz von Upstream-Remote-Control ist kein Nachweis, dass dessen gehostete Relays einem unabhängigen Client als öffentlich unterstützter Dienst zur Verfügung stehen.

## Alternativen und Stabilität

| Integrationsweg | Einordnung |
| --- | --- |
| App-Server v2 ohne Experimental-Opt-in | Dokumentierter Client-Vertrag und bevorzugte Exploration. „Stable surface“ bedeutet keine zugesicherte jahrelange Versionskompatibilität. |
| App-Server mit Experimental-Opt-in | Nur gezielt für belegten Bedarf. Methoden und Felder können sich ändern. WebSocket-Reifegrad zusätzlich beachten. |
| `codex exec --json` / TypeScript-SDK | Strukturierte Ereignisse ohne CLI-Textparsing. Passend für Batch-Jobs; das SDK benötigt Node.js und ist kein nativer Android-Client. |
| Rust-Core direkt einbetten | Technisch Quellcode verfügbar, aber stärkere Kopplung an interne APIs, Betriebssystem und Sandbox. Kein bevorzugter Start. |
| Rollout-Dateien oder menschenlesbare TUI-Ausgabe parsen | Persistenzdetails bzw. Präsentationsformat sind kein belastbarer UI-Vertrag. Nur begründeter letzter Ausweg. |
| Alter `codex mcp-server` | Die aktuelle offizielle Dokumentation meldet seine Entfernung und verweist auf App-Server. Nicht mit Codex als Client externer MCP-Tools verwechseln. [D2] |

**Hypothese:** Einen kleinen Codex-Adapter mit expliziter Versions-/Capability-Matrix entwickeln, sobald die Exploration beauftragt ist. Unbekannte Events tolerant anzeigen oder protokollieren, sicherheitsrelevante Requests dagegen geschlossen behandeln. Schema der eingesetzten Binary archivieren und bei Upgrades vergleichen. Provider-spezifische Fähigkeiten nicht in ein künstliches universelles Minimalprotokoll pressen.

## Lizenz und Projektgrenzen

**Verifizierter Lizenztext:** Das Repository steht unter Apache-2.0 und enthält eine `NOTICE`, unter anderem für abgeleiteten MIT-lizenzierten Ratatui-Code. Bei Weiterverteilung sind insbesondere Lizenzbeilage, einschlägige Hinweise und Kennzeichnung eigener Änderungen relevant. Apache-2.0 erteilt keine allgemeine Markenlizenz. [S12, L1]

**Folgerung für die Planung:** Ein separat geschriebener Protokollclient erfordert keinen Fork. Die eigene Projektlizenz ist noch zu wählen. Werden Codex-Binaries oder Quellteile mitverteilt, müssen deren Lizenz-/Notice-Pflichten und weitere Abhängigkeiten geprüft werden. Eine Protokollanbindung allein beantwortet die Lizenzfrage jeder zusätzlich eingebetteten Android-Komponente nicht. Open-Source-Code ist außerdem keine Zusage kostenloser Modellnutzung oder Nutzungsrechte an gehosteten Diensten. „CodexPad“ bleibt bis zur Namens-/Markenprüfung Arbeitstitel.

Upstream sollten Agent-Core, Auth, Sandbox, Modellkommunikation, Rollout-Persistenz und Codex-Policies bleiben. CodexPad sollte zunächst native UX, Verbindungszustand, Darstellung und Adapter verantworten. Das minimiert die Wartung nach längeren Projektpausen.

## Quellen

Alle S-Links sind auf den untersuchten Commit fixiert.

- D1: [Offizielle App-Server-Dokumentation](https://learn.chatgpt.com/docs/app-server), insbesondere Protocol, Filesystem, Approvals und Auth endpoints.
- D2: [Entfernung des Codex-MCP-Servers](https://learn.chatgpt.com/docs/mcp-server).
- S1: [Rust-Komponenten](https://github.com/openai/codex/tree/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs), [Core-README](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/core/README.md).
- S2: [Methoden und Experimental-Gates](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server-protocol/src/protocol/common.rs), [v2-Typen](https://github.com/openai/codex/tree/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server-protocol/src/protocol/v2).
- S3: [Items und Approval-Entscheidungen](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server-protocol/src/protocol/v2/item.rs).
- S4: [TypeScript-SDK](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/sdk/typescript/README.md).
- S5: [Rollout-Persistenz](https://github.com/openai/codex/tree/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/rollout/src), [App-Server-Ergänzungen](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server/README.md).
- S6: [Filesystem-Schema](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server-protocol/src/protocol/v2/fs.rs).
- S7: [Command-/PTY-Schema](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server-protocol/src/protocol/v2/command_exec.rs), [ungesandboxte Prozesse](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server-protocol/src/protocol/v2/process.rs).
- S8: [Command-Disconnect-Test](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server/tests/suite/v2/command_exec.rs#L1198), [Watch-Lebensdauer](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server/src/fs_watch.rs).
- S9: [Upstream-Daemon](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server-daemon/README.md).
- S10: [Resume-/Approval-Tests](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server/tests/suite/v2/thread_resume.rs#L5392).
- S11: [Credential-Speicherung](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/login/src/auth/storage.rs), [Account-Schema](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server-protocol/src/protocol/v2/account.rs).
- S12: [LICENSE](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/LICENSE), [NOTICE](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/NOTICE).
- L1: [Apache License 2.0, insbesondere Abschnitte 4 und 6](https://www.apache.org/licenses/LICENSE-2.0).
- S13: [Filesystem-Request-Processor und Sandbox-Parameter](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server/src/request_processors/fs_processor.rs).
