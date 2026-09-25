> Archivhinweis vom 18.09.2026: Historischer Bericht; Befunde unverändert. Die spätere command/exec-Prüfung ist in [Spike B](../2026-09-command-exec-persistence/pty-persistence.md) abgeschlossen. Ursprüngliche Rohlogpfade im Text sind historische Referenzen; erhaltene Belege und Grenzen stehen im [Archivindex](README.md).

# CodexPad – Linux Protocol Spike

Stand: 2026-09-18 UTC. Gesamtstatus: **PARTIAL**, Versuch abgeschlossen.

Ein unabhängiger Python-Client konnte den unveränderten App Server strukturiert steuern,
Turns ausführen, Datei-Approvals akzeptieren/ablehnen und Threads nach Clientverlust sowie
Serverneustart wieder aufnehmen. Offene Approvals überleben einen Client-Disconnect,
aber im getesteten Serververlust nicht. Laufende Modell-Turns überleben den Clientverlust;
die getesteten Hostprozesse nicht. Die VM verhindert sandboxgeschützte Befehle bereits
beim Sandboxstart. Deshalb ist dieser Spike kein vollständiger Nachweis für normale
agentische Shell-Arbeit oder `command/exec`-Persistenz.

## Environment

| Merkmal | Getesteter Stand |
|---|---|
| OS | Ubuntu 24.04.4 LTS, x86_64 |
| Kernel | `6.8.0-139-generic`, Build `#139-Ubuntu SMP PREEMPT_DYNAMIC Sat Aug 1 03:52:05 UTC 2026` |
| Codex / App Server | `codex-cli 0.154.0`; App Server ist Unterkommando desselben Binaries |
| Aufrufpfad | `/home/kf/.local/bin/codex` |
| Installation | Standalone-Release; Symlink über `.codex/packages/standalone/current/bin/codex` |
| Aufgelöstes Binary | `/home/kf/.codex/packages/standalone/releases/0.154.0-x86_64-unknown-linux-musl/bin/codex` |
| Binary SHA-256 | `3188814c35471432d4123203e0eb38e5bddc60226e3d7ddf0e59e649ea140022` |
| Client | Python 3, ausschließlich Standardbibliothek; genaue Python-Version im Environment-Log |
| Transport | WebSocket mit HTTP Upgrade über `/home/kf/codexpad-spike/app-server.sock`; kein TCP-Listener |
| Protokoll | bidirektionale JSON-RPC-Nachrichten ohne `jsonrpc`-Feld; generierte v2-Typen plus vorhandene Legacy-Methoden |
| Schema | direkt aus diesem Binary, `generate-json-schema --experimental` und `generate-ts --experimental` |
| Modell | `gpt-6-astra`, Reasoning `low` |
| Auth | vorhandener ChatGPT-Login nur lesend; `chatgptAuthTokens` im Speicher, Credential-Store `ephemeral` |

Die Bestandsprüfung erfolgte vor Clientimplementierung. Lokale `--help`-Ausgaben nennen
`daemon`, `proxy`, `generate-ts`, `generate-json-schema`; Listener sind `stdio://`,
`unix://`, `unix://PATH`, `ws://IP:PORT`, `off`. Die angebotenen WebSocket-Auth-Flags
wurden nicht für einen Remotezugriff getestet. Es wurde kein bestehender Daemon verwendet.

Belege: [archivierte Umgebungsdaten](environment.json), [Schema-Hashes](schema-sha256.json),
[ausgewählte generierte Typen](README.md#schema), [Testkonfiguration](test-config.toml).
Die Hilfe wurde zusätzlich am Ende in das Environment-Log aufgenommen.

Das vorhandene Nutzerprofil gab `model = "gpt-6-astra"` vor. Es wurde nicht kopiert.
Der Server erhielt ein eigenes `CODEX_HOME`, eigene TMP-/XDG-Verzeichnisse unter dem
Spike und folgende explizite Testwerte: `on-request`, `workspace-write`, Netzwerk für
Agent-Tools deaktiviert, keine zusätzlichen `/tmp`-Schreibfreigaben, Analytics aus.
Approval-Threads verwendeten die strengere Policy `read-only`, Reviewer `user`.
`initialize.capabilities.experimentalApi` war `true`. Damit sind Ergebnisse ausdrücklich
auf diese Version und diese aktivierten experimentellen Schnittstellen begrenzt.

Der Server meldete fehlendes systemweites Bubblewrap und verwendete seine mitgelieferte
Version. Ein direkter Read-only-Test lieferte Exitcode 1 mit
`bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted`.
Es wurden weder Pakete installiert noch Kernel-/AppArmor-/Namespace-Regeln geändert.
Die genaue Ursache der Namespace-Beschränkung wurde nicht weiter diagnostiziert.

Der Workspace ist ein neues Git-Repository **ohne Commit und ohne Remote**. `README.md`
und `sample.py` wurden nur im Index vorgemerkt. `sample.py` enthält `greet(name)` und
druckt `Hallo, CodexPad!`. Am Ende blieb diese Datei unverändert; erfolgreiche neue
Testdateien sind `approval-accepted.txt`, `approval-reconnected.txt` und `fs-watch.txt`.
So ist ein leerer `git diff` für `sample.py` ein Fehlernachweis des Änderungsversuchs,
kein angeblicher Änderungsnachweis. Für neue Dateien wurde `git diff --no-index` verwendet.

## Test Matrix

PASS bedeutet, dass die genannte Erwartung beobachtet wurde; FAIL bezeichnet eine
widerlegte Persistenz-Erwartung, nicht notwendigerweise einen Codex-Defekt.
PARTIAL trennt einen gelungenen Protokollteil von fehlender Abdeckung.

| Test | Erwartung | Tatsächliches Verhalten | Status | Beleg unter `logs/` |
|---|---|---|---|---|
| Installation / Schema | exakten lokalen Stand identifizieren | Binary, Hilfe, JSON-/TS-Schema vorhanden | PASS | `environment.jsonl`, `results/schema-sha256.json` |
| Handshake / Thread / Turn | strukturierte Session und Turn-Abschluss | initialize, start, Items, Deltas, completed erfolgreich | PASS | `02-baseline.jsonl` |
| Basistest Datei lesen/erklären | Agent liest `sample.py` | Sandboxfehler; vorgeschlagenen unsandboxierten Fallback abgebrochen; separater Marker-Turn erfolgreich | PARTIAL | `01-basic.jsonl`, `01-observer.jsonl`, `02-baseline.jsonl` |
| Bestehende Datei ändern | Kommentar in `sample.py` | Sandbox-Helfer scheitert, kein Diff; Modell beendet Turn mit Fehlerbeschreibung | FAIL | `02-baseline.jsonl`, `git-diff.jsonl` |
| Neue Datei / Diff | echte Änderung, strukturierte Vorschau und Ergebnis | genehmigtes Add erfolgreich; `fileChange.changes` plus verifizierter Git-Diff | PASS | `03-reconnect.jsonl`, `09-direct-git.jsonl` |
| Approval akzeptieren | Zustimmung führt Änderung aus | `accept`, resolved, completed; exakter Inhalt vorhanden | PASS | `03-reconnect.jsonl` |
| Approval ablehnen | keine Änderung; Turn kann sauber enden | `decline`, Item `declined`, Turn `completed`, Datei fehlt | PASS | `03-reconnect.jsonl`, `09-direct-git.jsonl` |
| Abgeschlossenen Thread reconnecten | Historie und Gesprächskontext zurück | Thread wiedergefunden; Marker korrekt, ohne Marker im Folgeprompt | PASS | `03-reconnect.jsonl` |
| Laufenden Turn reconnecten | gleicher Turn läuft weiter | active / inProgress, Anschluss an weiteren Stream, completed | PASS | `07-active-owner.jsonl`, `07-active-reconnect.jsonl` |
| Lückenloser Event-Replay | verpasste Deltas/Endevents erneut erhalten | Deltas 2–7 fehlen; offline erhaltenes Turn-Ende wird nicht erneut gesendet | FAIL | `07-active-reconnect.jsonl`, `07-offline-completed.jsonl` |
| Abgeschlossene Inhalte rekonstruieren | vollständiger Endtext trotz Eventlücke | 400 Zeilen vollständig; offline abgeschlossener Turn samt Text abrufbar | PASS | `07-offline-completed.jsonl` |
| Disconnect während Approval | Entscheidung bleibt erreichbar | identische Request-ID und Parameter nach Resume erneut geliefert; Annahme erfolgreich | PASS | `04-pending-reconnect.jsonl` |
| Serverneustart, abgeschlossener Thread | Historie und Kontext dauerhaft | Thread gefunden und Marker korrekt beantwortet | PASS | `05-server-restart.jsonl` |
| Serververlust während Approval | offenes Approval dauerhaft wiederherstellen | kein Approval; alter Turn interrupted; neue Datei fehlt; Folgeturn möglich | FAIL | `08-pending-*.jsonl` |
| `command/exec`, Pipe und PTY | laufenden Prozess vor/nach Disconnect vergleichen | beide starten wegen Sandboxfehler keinen Sleep-Prozess | NOT TESTABLE | `06-process-owner.jsonl`, `02-baseline.jsonl` |
| `process/spawn`, Pipe / PTY, Disconnect | Prozess überlebt und lässt sich anbinden | beide PIDs beendet, neue Verbindung kennt Handle nicht | FAIL | `process-observations.jsonl`, `06-*.jsonl` |
| `process/spawn`, PTY, Serverneustart | Prozess/Handle dauerhaft | PID beendet, Handle unbekannt | FAIL | `process-observations.jsonl` |
| Agent-Hintergrundterminal-Persistenz | Agentprozess wiederfinden | normale Agent-Shell durch Sandbox blockiert, keine Aussage zur Persistenz | NOT TESTABLE | `01-basic.jsonl`, Schema `ThreadBackgroundTerminals*.ts` |
| Direktes FS / Watch | hostseitige Dateioperationen ohne Agentturn | read/write/list/metadata/watch funktionieren; Schreiben außerhalb Workspace, innerhalb Spike | PASS | `04-pending-reconnect.jsonl` |
| Git nur über App-Server-Transport | Diff/Status ohne zweiten Workspace-Dienst | `process/spawn` liefert Git-Status und Unified Diff | PASS | `09-direct-git.jsonl` |
| Credential-/Commit-Kontrolle | keine Credentials gespeichert, kein Commit | keine Treffer vorhandener Credentialwerte; kein isoliertes auth.json; git log bestätigt fehlende Commits | PASS | `security-audit.jsonl`, `environment.jsonl` |

Zusätzliche maschinelle Evidenzprüfungen: [verification.json](verification.json),
erzeugt durch [analyze.py](client/analyze.py). Alle 16 Prüfungen bestanden.
Sie überprüfen beobachtete Inhalte und Zustände; sie ersetzen keine Wiederholungs-/Lasttests.

## Protocol Findings

Der Client in [probe.py](client/probe.py) spricht direkt WebSocket/JSON. Er parst
keine Codex-Terminalausgabe. Nachrichten werden vor dem Versand bzw. nach Empfang als
JSONL mit UTC-Zeitstempel, Richtung und Verbindungsnamen protokolliert. Serverstderr
steht separat in `server.jsonl`. Tokenfelder und bekannte Credentialwerte werden vor
der Logausgabe redigiert. Das Original-Authfile wurde weder verlinkt noch kopiert.

Die offizielle [App-Server-Dokumentation](https://developers.openai.com/codex/app-server)
wurde für Transportframing und den externen Token-Login herangezogen. Maßgeblich für
Feldnamen und Ergebnisse sind die lokal generierten Schemas und die tatsächlichen Logs;
es wurde keine Gleichheit zwischen aktueller Online-Dokumentation und 0.154.0 unterstellt.

Minimaler beobachteter Ablauf:

1. Verbindung aufbauen; `initialize` mit `clientInfo` und Capabilities; `initialized` senden.
2. Einmal je neuem Server den vorhandenen Authzustand über `account/login/start` mit
   `chatgptAuthTokens` nur im Speicher bereitstellen. Reconnect zum selben Server benötigt
   in diesem Aufbau keinen erneuten Account-Login.
3. `thread/start` mit `cwd`, Modell, Sandbox, Approval-Policy; Response enthält `thread.id`,
   `sessionId`, Persistenzpfad, History-Modus, Modell, effektive Sandbox, Reviewer und
   `instructionSources` (hier leer).
4. `turn/start` mit `threadId` und `input: [{type:"text",text:...}]`; die Response bestätigt
   nur den gestarteten Turn, nicht seine erfolgreiche Durchführung.
5. `turn/started`, `item/started`, `item/agentMessage/delta`, `item/completed`,
   `thread/status/changed`, `thread/tokenUsage/updated`, `turn/completed` verarbeiten.

`turn/completed` kann `completed` oder `interrupted` enthalten. Ein Turn mit Modelltext
„Failed … sandbox …“ war trotzdem `completed`: Der Agent hat fertig geantwortet, die
Dateioperation aber nicht erfolgreich ausgeführt. Deshalb stets Itemstatus, Fehler und
tatsächliches Dateiergebnis getrennt auswerten. Beim Legacy-Modus enthielt das
Turn-Endevent hier leere Items mit `itemsView:notLoaded`; paginierte Endevents lieferten
eine Summary. Vollständigkeit nie allein aus einem Endevent ableiten.

Wichtige konkrete IDs:

| Zweck | Thread-ID / ergänzende IDs |
|---|---|
| Hauptthread, Marker | `01a0b2e9-b2c6-7d30-b138-ed759006b825` |
| Approval-Thread | `01a0b2ea-0251-7801-9bed-cdf29f16334e` |
| Reconnect-Approval | Turn `01a0b2ea-4175-71c3-ae80-31f84224ab86`, Request `2`, Item `exec-2182c44c-b69d-4f54-a54f-20472f6ac361` |
| Laufender paginierter Thread | `01a0b2eb-29f7-7e23-aa5c-7ef7440c64cd` |
| Laufender Testturn | `01a0b2eb-2a38-7453-b3a2-c11eff3a6bad` |
| Approval bei Serververlust | Thread `01a0b2ec-b864-7060-a127-2b5f55d1636e`, Turn `01a0b2ec-b916-70a0-8c60-04febdbf1c12` |

Weitere IDs stehen in `state.json`, `live-state.json`, `pending-restart-state.json` und
den Nachrichtenlogs. `thread.id` und `sessionId` waren hier gleich, sind aber getrennte
Protokollfelder und sollten nicht voneinander abgeleitet werden.

### Approvals

Approvals sind **Serverrequests mit `id`**, nicht bloße Benachrichtigungen.
`item/fileChange/requestApproval` enthält Thread-, Turn-, Item-ID, `startedAtMs`,
`reason` und `grantRoot`. Der konkrete Dateipfad und vorgeschlagene Inhalt stehen im
zugehörigen `fileChange`-Item, nicht vollständig im Approvalrequest selbst.

Antwort auf den tatsächlich empfangenen Request:

```json
{"id":2,"result":{"decision":"accept"}}
```

Für Ablehnung wurde `decline` verwendet. `serverRequest/resolved` bestätigte die
Auflösung; danach folgte das Item mit `completed` bzw. `declined`. Die abgelehnte
Datei wurde nicht erzeugt, und der Agent beendete den Turn regulär. Keine
`acceptForSession`-Freigabe und keine dauerhafte Policyänderung wurden verwendet.

Der erste Shell-Leseversuch erzeugte außerdem einen echten
`item/commandExecution/requestApproval` mit Befehl, cwd, Reason, geparsten
`commandActions`, vorgeschlagener Policyergänzung und `availableDecisions`. Dieser
Fallback wurde mit `cancel` beantwortet, nicht ausgeführt. Eine zweite Verbindung
konnte nach Resume diese offene Anfrage auflösen, während der ursprüngliche Client
noch verbunden war (`01-observer.jsonl`); dies ist ein separater Mehrclient-Befund.

## Reconnect Findings

### Thread Resume und dauerhafte Historie

`thread/list` findet die Threads; `thread/read` liefert Zustand ohne die gleiche
Live-Abonnementfunktion wie Resume. `thread/resume {threadId}` verbindet den neuen
Client mit dem bereits laufenden Thread oder lädt dessen gespeicherte Historie.
Threads und Turns sind in `codex-home/sessions/.../rollout-*.jsonl` und lokalen
SQLite-Dateien materialisiert. Der Client benötigt keinen eigenen Gesprächsspeicher
als Ersatz für diese Serverpersistenz; er braucht aber einen eigenen UI-Zustandsabgleich.

Der Marker `ORBIT-73-KIESEL` wurde in einem abgeschlossenen Turn gesetzt. Sowohl nach
Sockettrennung als auch nach Serverneustart wurde er korrekt beantwortet, ohne ihn
im Folgeprompt zu wiederholen. Er wurde nicht in Workspace-Dateien geschrieben.

`historyMode:"legacy"` konnte vollständige `thread.turns` liefern. Dabei wurden
User-/Agent-Item-IDs als `item-1`, `item-2`, ... rekonstruiert, abweichend von Live-IDs.
Beim laufenden Approval unterschieden sich sogar die rekonstruierten Itemzählungen
zwischen Read und Resume. Ein Legacy-Client darf daher nicht blind anhand dieser IDs
Live- und Historieneinträge zusammenführen oder globale Eindeutigkeit voraussetzen.

Der zweite Test verwendete `historyMode:"paginated"` sowie
`thread/resume {excludeTurns:true}`, `thread/turns/list {itemsView:"full"}` und
`thread/items/list`. `initialTurnsPage` fasst Resume und erste Historienseite zusammen.
Die getesteten abgeschlossenen User-/Agent-Items behielten hier ihre IDs aus dem
Livestream. Pagination-Cursor sind opaque; der Client sollte sie nicht interpretieren.
Mehrseitige große Historien wurden nicht getestet.

### Laufender Turn, Replay und Rekonstruktion

Nach Disconnect bei beginnender Ausgabe „1 … 400“ meldeten Read/Resume denselben
Thread als `active` und dieselbe Turn-ID als `inProgress`. Das Modell arbeitete weiter;
der neue Client erhielt weitere Deltas und das Turn-Ende. Kein neuer `turn/start`
war dafür erforderlich. Ein eigenes `turn/resume` wurde nicht benötigt.

Die erste Verbindung erhielt „1\n“, die zweite begann bei „8\n“. Die dazwischenliegenden
Deltas wurden nicht wiederholt. Die direkt nach Resume geladene vollständige Turnseite
enthielt zu diesem Zeitpunkt nur das User-Item, noch keinen vollständigen partiellen
Agenttext. Nach Abschluss enthielten Item-/Turnhistorie den exakten Text aller 400 Zeilen.
Damit sind Endinhalte rekonstruierbar; verlustfreier laufender Text und Eventchronologie
sind nicht nachgewiesen und im beobachteten Ablauf gerade nicht vorhanden.

Ein weiterer Turn wurde gestartet und die einzige Verbindung für 15 Sekunden getrennt.
Nach Resume war `OFFLINE-COMPLETED-27` mit Status `completed` in der Historie enthalten.
Es gab kein nachgeliefertes `turn/completed`, `item/completed` oder Textdelta für diesen
offline abgeschlossenen Turn. Die beobachteten Reconnect-Benachrichtigungen waren
`configWarning`, `remoteControl/status/changed`, `thread/goal/cleared`.
Status-Snapshots und Historienabfragen sind deshalb zwingend; Warten auf alte Endevents
würde eine UI im falschen „läuft noch“-Zustand festhalten.

„Vollständig rekonstruierbar“ gilt hier für geprüfte abgeschlossene Gesprächsinhalte,
Turnstatus und abgeschlossene Dateiänderungs-Items. Es gilt nicht für sämtliche Deltas,
Timingdetails, rohe Toolereignisse oder offene Vorschläge nach Serververlust.

### Offene Approvals

Beim Client-Disconnect blieb der Serverprozess bestehen. Vor Resume zeigten
`thread/list` und `thread/read` bereits `activeFlags:["waitingOnApproval"]`.
Read enthielt den offenen Dateivorschlag nicht vollständig, Resume dagegen das
`fileChange`-Item mit `status:"inProgress"`. Direkt nach der Resume-Response folgte
derselbe Approvalrequest mit ID `2`, unverändertem Item und Zeitstempel.
Die neue Verbindung antwortete erfolgreich; `approval-reconnected.txt` enthält exakt
`RECONNECTED-01\n`. Die offene Entscheidung ist damit bei diesem Clientverlust wieder
bedienbar und nicht bloß als abstrakter Wartezustand erkennbar.

Beim Serververlust galt das nicht: `SIGTERM` beendete den Server mit offenem Approval
nicht innerhalb des 10-Sekunden-Limits des Testclients. Der Harness sendete anschließend
`SIGKILL`. Dieser Fall ist deshalb ein **Serververlust nach begrenztem Shutdownversuch**,
kein Nachweis für einen vollständig graceful abgeschlossenen Shutdown.
Nach Neustart war der Thread `idle`, der frühere Turn `interrupted`, ohne
Abschlusszeit. Das offene `fileChange`-Item und der Approvalrequest waren nicht mehr
abrufbar. Die Datei fehlte. Ein Folgeturn konnte starten und beschrieb den abgebrochenen
Toolaufruf. Der Client darf alte Request-IDs nach Serverneustart nicht blind beantworten.

### Erforderlicher Client-State

Persistieren: Server-/Workspace-Zuordnung und `thread.id`; zusätzlich `sessionId` aus
dem Server übernehmen. Für die UI sind Turn-/Item-IDs, zuletzt geladene Seiten/Cursor,
Entwürfe und die Zuordnung offener Requests sinnvoll. Alte Approval-IDs sind kein
dauerhaftes Transaktionsjournal. Jede neue Verbindung braucht einen Handshake,
Resume/Subscription und anschließende Zustandshydrierung. Live-Nachrichten können schon
während der Abfragen eintreffen; Snapshot und Events müssen zusammengeführt werden.
Nach Abschluss ist der vollständige Itemtext maßgeblich, nicht die Verkettung eventuell
lückenhafter Deltas. Nach Serververlust unterbrochene Aktionen sichtbar markieren;
keine automatische Wiederholung von Dateioperationen aufgrund alter UI-Daten.

Nicht untersucht: ein verlorenes `turn/start`-Acknowledgement, Duplikatunterdrückung,
mehrfache schnelle Disconnects, Android-Prozesskill, TCP-/WSS-Netzwerkfehler oder
Transportauthentifizierung über Rechnergrenzen.

## Process Findings

`command/exec` ist ein eigenständiger sandboxgeschützter Aufruf ohne Thread/Turn.
Das Schema dokumentiert connection-scoped `processId`, PTY, Stdin, Resize, Terminate
und Outputdeltas; die finale Response kommt erst beim Prozessende. Beide Sleep-Versuche
mit Pipe und PTY endeten hier sofort mit Exitcode 1 aufgrund der Sandbox. Daraus lässt
sich **keine** empirische Aussage über die Lebensdauer erfolgreich gestarteter
`command/exec`-Prozesse nach Disconnect ableiten. Die ältere Quellcodehypothese bleibt
für diese API offen. Die Sandbox wurde nicht deaktiviert, um diesen Test zu erzwingen.

Diese Version besitzt zusätzlich `process/spawn`: laut lokalem Schema ein ausdrücklich
unsandboxierter Hostprozess. Der Start liefert sofort `{}`, danach kommen
`process/outputDelta` (Base64) und `process/exited`. Handles sind verbindungsgebunden.
Das wurde als eigene vorgesehene Host-API getestet, nicht als erfolgreicher Sandboxtest
ausgegeben. Die Prozesse druckten nur ihre PID und schliefen maximal 90 Sekunden.

| Fall | OS-PID | Vor Trennung | Eine Sekunde danach | Zugriff über neue Verbindung |
|---|---|---|---|---|
| Pipe, Clientverlust | 2579557 | aktiv | beendet | `no active process for process handle` |
| PTY, Clientverlust | 2579600 | aktiv | beendet | gleicher Fehler |
| PTY, Serververlust | 2579654 | aktiv | beendet | gleicher Fehler nach Neustart |

PIDs wurden aus strukturiert transportiertem Testprozess-Output gewonnen und mit
`/proc/PID/stat` geprüft; Zombies gelten nicht als laufend. Der Server blieb bei den
ersten beiden Tests nachweislich am Leben. Es wurden keine fremden Prozesse beendet.
Die Server wurden danach gestoppt; kein Test-Sleep blieb übrig.

`thread/backgroundTerminals/list`, `/terminate`, `/clean` existieren im Schema.
Agent-Hintergrundterminals sind damit eine weitere, getrennt zu untersuchende Kategorie.
Ein erfolgreiches solches Terminal wurde wegen der Sandbox nicht erzeugt. Die Befunde
für `process/spawn` dürfen nicht auf alle Agentprozesse verallgemeinert werden.

## Files / Diffs

Die beobachteten genehmigten Adds lieferten vor Ausführung `item/started` mit
`type:"fileChange"`, absolutem Pfad, `kind:{type:"add"}`, `diff` und Status. Für eine
neue Datei war `diff` hier der neue Dateiinhalt, kein vollständiger Git-Unified-Diff.
Nach Ausführung folgte das gleiche Item mit `completed`; bei Ablehnung `declined`.
Diese Informationen reichen für eine native Vorschau der **beobachteten Agentänderung**.
Die Approval-UI muss Request und Item miteinander verbinden.

`turn/diff/updated` ist im Schema vorhanden, wurde in diesen Läufen aber nicht empfangen.
Es sollte nicht als einzig verlässliche Datenquelle angenommen werden. Schemainformation
über Updates/Löschungen ist kein experimenteller Nachweis für deren Verhalten.

Für den aktuellen Workspacezustand stehen `fs/readDirectory`, `fs/getMetadata`,
`fs/readFile` und `fs/watch` bereit; read/write transportieren Dateiinhalt als Base64.
Watch lieferte `fs/changed` mit den geänderten Pfaden, nicht den Änderungen selbst.
`fs/createDirectory`, `fs/copy`, `fs/remove`, `fs/unwatch` sind ebenfalls im Schema;
die destruktiven Varianten wurden nicht ausprobiert. Watches sind verbindungsbezogen.

Git-Status und der tatsächliche Diff wurden sowohl lokal diagnostisch als auch rein
über `process/spawn` ermittelt. `git diff --no-index -- /dev/null approval-accepted.txt`
lieferte den korrekten Unified Diff; Exitcode 1 bedeutet hier vorhandene Unterschiede.
`git status --porcelain=v1` machte zudem die untracked Dateien sichtbar. Das ist Parsing
eines gezielt angeforderten Git-Formats, **kein Parsing der Codex-CLI**.

Fazit für eine Projektansicht: zusätzliche Dateisystem-/Git-Abfragen sind erforderlich,
um tatsächlichen Gesamtzustand, untracked Dateien und Änderungen außerhalb von
Agent-Patches darzustellen. Diese Abfragen können über bestehende App-Server-APIs laufen;
ein zusätzlicher Workspace-Daemon wurde dafür nicht benötigt. Der vorhandene Legacy-RPC
`gitDiffToRemote` wurde mangels Remote/Commit absichtlich nicht als universelle Lösung getestet.

## Security Findings

Die Trennung zwischen Agentberechtigungen und Client-/Hostberechtigungen ist deutlich:

- Agenttools verwenden Thread-/Turn-Sandbox und Approvals. Read-only erzeugte echte
  Datei-Approvals; Ablehnung verhinderte die konkrete Änderung.
- `fs/writeFile` hat keine Thread-ID und keine Thread-Approval-Policy. Es schrieb
  `results/direct-fs.txt` außerhalb des Thread-Workspaces, während ein Read-only-Thread
  geladen war, ohne Approval. Alle Ziele lagen weiterhin innerhalb des Spikes.
- `process/spawn` startete Hostprozesse ohne Codex-Sandbox und ohne Agent-Approval,
  auch auf einer neuen lokal verbundenen Instanz ohne Account-Login. Modell-Accountauth
  und Berechtigung, den lokalen Server anzusprechen, sind verschiedene Grenzen.
- Im Schema ist auch `thread/shellCommand` explizit als unsandboxierter Shellaufruf
  beschrieben. Dieser wurde nicht ausgeführt. `command/exec` dagegen besitzt eine
  eigene SandboxPolicy; sein Fehler darf nicht mit der Hostprozess-API vermischt werden.

Ein externer Client ist in diesem Aufbau ein **vertrauenswürdiger Steuerungsclient mit
Hostfunktionen**, kein durch `cwd` auf ein Repository eingeschränkter Nutzer. OS-Rechte
und eine separat gesicherte Transportgrenze bleiben relevant. Außerhalb des Spikes
wurden keinerlei Datei-RPCs getestet; ein umfassender Nachweis aller OS-Zugriffsrechte
wird daraus nicht abgeleitet.

Sicherheitskritisch für Android wären insbesondere frei wählbare FS-Pfade, direkte
Hostbefehle, Lösch-/Kopier-RPCs, Konfigurationsänderungen und die Beantwortung von Approvals.
Eine Agent-Sandbox allein schützt nicht vor einem kompromittierten autorisierten
Steuerungsclient. Remote-Auth, TLS und gegebenenfalls eine begrenzende Hostgrenze sind
Architekturthemen, aber in diesem Spike weder implementiert noch experimentell bewertet.
Es gab keinen öffentlich geöffneten Port, keine dauerhafte Freigabe und keinen Exploitversuch.

## Architecture Consequences

| Hypothese | Bewertung | Empirische Begründung |
|---|---|---|
| 1. Nativer Client kann sinnvoll direkt kommunizieren | **CONFIRMED** | unabhängiger Client steuert Threads, Turns, Events, Approvals und Recovery über strukturierte APIs; Android selbst nicht implementiert |
| 2. CLI-Textparsing ist nicht erforderlich | **CONFIRMED** | sämtliche Codex-Steuerung und Zustandsgewinnung über JSON-RPC; Logs sind Belege, keine CLI-Steuerungsquelle |
| 3. Codex-Fork ist nicht erforderlich | **CONFIRMED** | alle erfolgreichen Spike-Funktionen mit unverändertem installiertem Binary; Aussage auf diesen Funktionsumfang begrenzt |
| 4. Eigener Workspace-Daemon für v0.1 nicht erforderlich | **PARTIALLY CONFIRMED** | FS, Watch, Git/Hostprozesse verfügbar; dauerhafte Terminals, Remote-Trustgrenze und funktionierende Sandbox bleiben unbewiesen |
| 5. Android-Reconnect allein mit vorhandenen Funktionen robust | **PARTIALLY CONFIRMED** | Threads, aktive Turns und offene Approvals bei Clientverlust wieder erreichbar; kein Event-Replay, partielle Textlücke, eigene UI-Reconciliation nötig; Android-/Netzwerk-/Ack-Rennen nicht getestet |
| 6. Prozess-/Terminal-Persistenz benötigt möglicherweise Zusatzlösung | **CONFIRMED** | getestete Host-Pipe-/PTY-Prozesse sterben beim Disconnect; für dauerhaftes Terminal reicht dieser Mechanismus nicht; Agentterminal/command-exec bleiben offen |

Das größte Architektur-Risiko ist, „Reconnect“ mit verlustfreier Wiederherstellung aller
laufenden Aktivitäten gleichzusetzen: Gespräch, Approvalcallback, Eventstream und
Terminal haben unterschiedliche Lebensdauern. Zusätzlich ist der direkte Client eine
weitreichende Host-Trustgrenze. Ein Fork oder Workspace-Daemon folgt daraus derzeit nicht.

## Open Questions

1. Wie verhalten sich erfolgreich sandboxgestartete `command/exec`-PTYs und echte
   Agent-Hintergrundterminals beim Clientverlust? Die VM ließ den nötigen Start nicht zu.
2. Wie wird die UI bei wiederholten Disconnects zwischen Snapshot, Delta und
   Approvalantwort zuverlässig abgeglichen? Gibt es eine abrufbare vollständige
   Zwischenfassung des laufenden Agenttexts außerhalb der hier getesteten Abfragen?
3. Welche Duplikat-/Retrysemantik gilt bei verlorenem `turn/start`-Acknowledgement und
   bei Verbindungsabbruch unmittelbar während einer Approvalantwort?
4. Wie verhält sich Shutdown mit offenem Approval bei längerer Frist oder einem
   anderen vorgesehenen Shutdownpfad? Hier war nach 10 Sekunden SIGKILL erforderlich.
5. Welche minimale Remoteauth-/Hostisolation begrenzt den gewünschten Androidzugriff,
   ohne ungewollt allgemeine Hostkontrolle freizugeben?

## Recommended Next Experiment

**Genau ein nächster Versuch:** dasselbe Binary 0.154.0 auf einem Linux-Testhost mit
funktionierender regulärer Codex-Sandbox verwenden; dort einmal `command/exec` mit PTY,
PID-Ausgabe und 30-Sekunden-Sleep starten, nur den Client trennen und über eine neue
Verbindung den alten `processId` prüfen. OS-PID vor/nach der Trennung beobachten.
Keine Sandbox deaktivieren und keinen Daemon bauen. Dieser kleine Test schließt die
konkrete offene Quellcodehypothese zur `command/exec`-Lebensdauer, die diese VM nicht
prüfen konnte. Ein eigener Terminaldienst ist davor nicht begründet.

## Versuchscode, Grenzen und Abschluss

Dateien: `client/probe.py` (Transport), `suite.py` (Basis/Dateien/Approvals/Resume),
`processes.py`, `live_resume.py`, `pending_restart.py`, `direct_git.py`, `evidence.py`,
`analyze.py`. Exploration und endgültige Belege bleiben getrennt nachvollziehbar.
Die Skripte sind absichtlich auf dieses Wegwerfverzeichnis zugeschnitten; ein erneuter
vollständiger Lauf im bereits beschriebenen Workspace ist nicht idempotent.
`analyze.py` kann die gespeicherten Belege ohne Modellzugriff erneut prüfen.

Ein erster Pending-Restart-Lauf traf eine Harness-Startup-Race: ein alter Socketpfad
existierte vor Listenerbereitschaft, `connect` lieferte `ConnectionRefusedError`.
Der Client wurde auf tatsächliche Verbindungsbereitschaft korrigiert und der Test mit
einem neuen Thread wiederholt. Beide Versuche sind in den append-only Logs sichtbar;
die oben genannten IDs beziehen sich auf den vollständigen zweiten Lauf. Das ist kein
als Protokollfehler ausgegebenes Testergebnis.

Kein Commit, Push, produktives Repository, Codex-Fork, Androidprojekt oder Workspace-Daemon.
Keine Pakete/Systemdienste eingerichtet, keine produktiven Credentials verändert.
Alle vom Versuch gestarteten Server und überprüften Testprozesse sind beendet.
Ergebnisdateien, redigierte Logs, Schemas und isolierte Serverhistorien bleiben zur Prüfung erhalten.
