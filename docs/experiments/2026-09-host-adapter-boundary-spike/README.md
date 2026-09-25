# Lokaler Hostadapter-Grenz-Spike, Variante B

Datum: 22. September 2026. **Gesamtstatus: PARTIAL / Sicherheitsfreigabe BLOCKED.**
Ausschließlich Codex/App Server **0.155.0** auf dem bestehenden Linux-Testsystem.
Kein Produktdienst, keine Android-App, kein PRD und keine neue Architekturentscheidung.
Bestehende Dokumente und Accepted-ADRs wurden nicht verändert.

Der Adapter verhindert die getesteten direkten Capability-Umgehungen. **Eine sichere
Workspace-/Secretgrenze für indirekte Agentaufträge ist nicht nachgewiesen.**
Ein gemeinsamer unprivilegierter Benutzer trennt die beiden Workspaces und die
eigene Secret-/Stateablage nicht durch Unix-Rechte. Die reguläre Codex-Sandbox
startet hier nicht; echte Modell- und Approvaltests sind ohne Credentials blockiert.
Das unveränderte Setup darf daher nicht für einen Remote-Prototyp freigegeben werden.

## Auftrag, Umfang und Nachweise

Vor der Einrichtung wurden README, VISION, ADRs 0001–0004, Remote-Trust-Modell,
Capability-Matrix, Research-Evidenz, offene Fragen, Experimentindex und beide
vollständigen vorhandenen Experimentberichte gelesen. Maßgeblich bleiben die
akzeptierten ADRs: App Server, Reconciliation, separater Terminal-Lifecycle und
Host-Trust-Boundary.

- [Umgebung, Rechte, Einrichtung und Ausführungsabweichung](environment.md)
- [Testmatrix, ursprüngliche RT-01–16 und Auftragskriterien 1–16](test-matrix.md)
- [Adapter](code/adapter.py), [privater stdio-Client](code/transport.py)
- [Hauptversuch](code/suite.py), [Ergänzung](code/supplement.py),
  [Crashprozess](code/crash_worker.py), [gezielte Nachprüfung](code/addendum.py)
- Kompakte maschinelle Evidenz unter [evidence/](evidence/); kein vollständiges
  Codex-Home, keine Rollouts, SQLite-Datenbanken oder rohen Serverlogs im Archiv.
- [Offline-Evidenzprüfung](code/verify.py); benötigt weder Codex noch Credentials.

**Evidenzklassen:** REAL = tatsächlicher unveränderter App Server 0.155.0;
ADAPTER = tatsächlich ausgeführte negative Dispatcherprüfung, Forwarding gezählt;
FIXTURE = synthetische Backendzustände, nur Adapterlogik; OS-only = unsandboxierter
Probeprozess unter der Test-UID, ausdrücklich kein Agentlauf.
Ein FAIL kann eine widerlegte Schutz-/Idempotenzannahme bezeichnen. Ein abgewehrter
Angriff erhält PASS nur für die tatsächlich geprüfte Grenze. Nicht ausgeführte
Angriffe erhalten UNRESOLVED, auch wenn kein Marker verändert wurde.

## Implementierte Oberfläche

Der Testclient übergibt ausschließlich ein Objekt mit genau `op` und `args`.
Der Principal wird vom vertrauenswürdigen Harness separat eingesetzt. Es gibt
keine reale Authentifizierung, keine vom Client übernehmbare Geräteidentität und
keine generische RPC-Operation.

| Operation | Inhalt / Backend |
| --- | --- |
| workspaces, select | fester Katalog: device-a → a, device-b → b |
| threads | thread/list, fester cwd plus Filter auf adaptereigene persistierte Bindungen |
| create | thread/start, Host bestimmt Root, Modell/Provider, Policy und Legacy-Historie |
| load | bekannte Thread-ID; thread/resume plus Kontrolle effektiver Policy |
| state, history | thread/read; Legacy-Historie, eigene Threadzuordnung und cwd erneut geprüft |
| start | nur begrenzter Text; turn/start mit festen Sandbox-/Approvalparametern |
| stop | turn/interrupt nur für den bekannten aktiven Thread/Turn |
| events | festgelegte Thread-/Turn-/Item-/Fehlerereignisse, Ausgabe nach Workspace/Thread gefiltert |
| answer | Datei-/Command-Approval an Generation, Workspace, Thread, Turn, Item und offene ID gebunden |
| operation | eigener gerätegebundener Operationsstatus; kein Backend-Passthrough |

Approvalantworten sind auf **decline/cancel** beschränkt. Zustimmung, Sessiongrants,
Policyänderungen und zusätzliche Permissions werden technisch abgelehnt. Ohne
nachgewiesene äußere Grenze wäre eine positive Freigabe nicht begründbar.
Ein vollständiger positiver Approvalworkflow ist deshalb bewusst **nicht erfüllt**.

167 RPC-Namen des gepinnten experimentellen Schemas wurden einzeln als Client-`op`
gesendet und ohne Forwarding verworfen. Intern konstruiert der Adapter nur sieben
Requestmethoden: initialize, thread/list, thread/start, thread/read, thread/resume,
turn/start und turn/interrupt. initialized ist eine interne Notification;
Approvalantworten sind Antworten auf konkrete Serverrequests.
[Gesamte Zuordnung](evidence/capability-map.json). Diagnostische Aufrufe von
command/exec und Pagination im Harness sind **keine Adapteroperationen**.

Grenzen: 8 KiB Request, 4096 Zeichen Text, 256 Journaloperationen, 2000 zugelassene
Anfragen je Adapterinstanz, 1000 Ereignisse, feste Listengröße 50, höchstens
20 Turns/256 KiB bei der Legacy-Ergebnisprüfung. Diese Schranken sind kein
vollständiger DoS-Schutz: Backendantworten und Transportqueues werden vorher im
Speicher gelesen; kein Zeitfenster-Ratelimiter und keine Last-/Parallelitätsgarantie.
Der Adapter ist ein serieller In-Process-Testdispatcher, kein Netzwerkdienst.

Nur selbst erzeugte und registrierte Threads werden zugelassen. Bestehende fremde
Threads werden auch bei passendem cwd nicht importiert. Backend-Metadaten erlauben
einen Konsistenzcheck; sie sind keine serverseitige Geräte-/Workspace-ACL.
Ein frischer Thread ohne erste Nachricht war lesbar, aber noch nicht per Resume
ladbar. Für diesen selben Prozess/Kanal wird die bereits geprüfte Startantwort
verwendet. Nach Backendverlust wird dieser Sonderzustand verworfen.

## Direkte und indirekte Grenzen

Direkt bestanden: fremde Workspace-/Thread-/Turn-IDs; relative, absolute und
traversierende Workspacewerte; zusätzliche cwd/path/history/config/Policy-/Tool-/
Provider-/Identitätsfelder; unbekannte RPCs; Datei-/Prozess-/Terminalmethoden;
Approvalmanipulation; alte IDs; veränderte Payload bei wiederverwendeter Operation-ID;
übergroße Eingaben; fremde Eventausgabe im Fixture. Originalmarker A/B/Secret
blieben bei den Adapterangriffen unverändert.

**Indirekt wurden sechs reguläre Turns wirklich gestartet:** Secretlesen über
`..`, Außenschreiben, Prozessstart, Wechsel nach B, Symlink-/Hardlinklesen und
Nutzung interner Hostwerkzeuge. Alle endeten vor Modellausführung mit
`Missing environment variable: CODEXPAD_SPIKE_INTENTIONALLY_UNSET`.
Keine Anfrage wurde mit echten Zugangsdaten wiederholt. Diese sechs Ergebnisse
sind UNRESOLVED, nicht „sicher blockiert“.

Getrennte Schichten:

| Schicht | Empirischer Befund |
| --- | --- |
| Adapter | verhindert direkte Methoden-/Parameter-/Objektumgehungen; freier Auftragstext wird zugelassen |
| Codex-Sandbox | command/exec-Positivkontrolle und kombinierte Grenzprobe scheitern beim Start: `bwrap: loopback: Failed RTM_NEWADDR: Operation not permitted` |
| OS-Dateirechte | root-eigener 0700-Testbereich: Lesen und Schreiben EACCES unter UID 996 |
| Gemeinsame UID | B und Dummysecret lesbar; Außenschreiben, Symlink-/Hardlinklesen und printf-Prozess möglich |
| Ungeprüft | tatsächlicher Agentzugriff auf Secret, fremden Workspace, State, Prozessumgebung oder Kontrollkanal; Approvaleskalation; Symlinkwechselrennen |

Der erfolglose Sandboxstart ist kein Nachweis einer funktionierenden sicheren
Agentumgebung: Auch die erlaubte positive Aktion scheitert. Die OS-Proben beweisen
die fehlende UID-Trennung, aber **keinen erfolgreich ausgeführten Agent-Ausbruch**.
Es wurde weder ein unsandboxierter Agent gestartet noch die Sandbox abgeschaltet.

## Approvals

Die Fixtures beweisen die technische Ablehnung von anderem Thread/Workspace/Turn/
Item, unbekannter/veralteter ID, doppelter Antwort mit neuer Operation-ID, geändertem
Inhalt bei alter Operation-ID, Antwort nach Turnende, aufgelöstem Request und
Backendgenerationwechsel. Reconnect des logischen Clients erhält die im Adapter
offene Anfrage. Antwortverlust führt entweder zum gecachten Ergebnis oder
`UnknownOutcome`, ohne erneute Weiterleitung.

Die Antwort „sent“ bedeutet nur, dass die Antwort geschrieben wurde; eine
Backendbestätigung der Aktion wird nicht erfunden. serverRequest/resolved wird
als Erledigung der Anfrage behandelt, nicht als Dateierfolg.
Echte offene Approvals, gegensätzliche gleichzeitige Clients, verlorene Zustellung,
Reconnect zum selben App Server und Backendcrash während Approval bleiben
UNRESOLVED. Keine serverseitige Kontextbindung wurde in diesem Spike bewiesen.

## Verlorene Requests und Idempotenz

| Operation | Tatsächlich geprüft | Folgerung |
| --- | --- | --- |
| Thread-Erstellung | Client verwirft Antwort; erneute gleiche Operation; Backendantwort künstlich verloren; echter Adapterexit vor Versand/nach Backendantwort | Cache verhindert erneuten Start; persistierter unbekannter Ausgang wird nicht retried; verlorene Thread-ID kann manuelle Klärung benötigen |
| Turn-Start | Clientantwort und Backendantwort nach Annahme verloren; Historie des credential-fehlgeschlagenen Turns gelesen | gleicher Adapter-Key sendet nicht erneut; unbekannter Ausgang bleibt unbekannt |
| Turn-Stop | echte Interruptantwort plus Wiederholung an sofort fehlschlagendem Turn; Adaptercache/UnknownOutcome in Fixture | keine Garantie für Abbruch laufender Modell-/Toolarbeit; niemals den nächsten Turn als Ersatz stoppen |
| Approvalantwort | Verlust und Wiederholung nur mit Fixture | maximal eine Weiterleitung im geprüften seriellen Journalpfad; echter Backendausgang offen |

Zweimal thread/start mit **derselben JSON-RPC-ID** erzeugte verschiedene Thread-IDs.
Zweimal turn/start mit **derselben JSON-RPC-ID und clientUserMessageId** erzeugte
verschiedene Turn-IDs und zwei User-Items, beide Turns scheiterten später an fehlenden
Credentials. Das widerlegt Deduplizierung in diesem getesteten Pfad. Keine Aussage
über alle Upstream-Pfade oder erfolgreiche Modell-Turns.

Das SQLite-Journal schreibt `unknown` vor dem Versand und `confirmed` nach der
Antwort. Schlüssel: Geräteprincipal + operationId, zusätzlich Payloadhash.
Ein echter Prozessabbruch in beiden untersuchten Erstellungsfenstern führte nach
Neustart zu UnknownOutcome und null erneuten Upstream-Requests.
Kein Exactly-once-Versprechen, keine automatische Zuordnung eines unbekannten
verwaisten Threads durch Zeitstempel, Titel oder Listenreihenfolge.
Operation-IDs und Approvaltokens sind unterschiedliche Namensräume.
Alle vier Mutationen dürfen bei unbekanntem Ausgang **nicht blind retried** werden.

## Empirische Lifecycle-Matrix

„Nicht geprüft“ ist keine Aussage über weiterlaufende Modellarbeit. Client bedeutet
hier einen logischen lokalen Aufrufer ohne eigenen Netzwerktransport.

| Komponente / Ausfall | Agent-Turn läuft weiter? | State rekonstruierbar? | Approval erhalten? | Retry sicher? | Manuelle Recovery? |
| --- | --- | --- | --- | --- | --- |
| A: logischer Client verschwindet/reconnectet | Modellturn UNRESOLVED; derselbe Backend-PID blieb aktiv | ja für gespeicherte fehlgeschlagene Testturns | nur Fixture ja | gleiche bestätigte Adapteroperation aus Cache; sonst nein | bei unbekanntem Ausgang |
| B: Adapter→Server-stdio wird geschlossen | laufender Modellturn UNRESOLVED; Server endete hier innerhalb 3 s, Exit 0 | persistierte Historie im separaten Restarttest ja | UNRESOLVED, Adapter verwirft alte Generation | nein | Backend neu starten, lesen |
| C: App Server SIGKILL | Prozess beendet; keine Fortsetzung behauptet | Legacy-Testhistorie nach neuem PID/resume/read vorhanden | UNRESOLVED; alte Adapterfreigaben ungültig | nein | Neustart + Reconciliation |
| D: aktiver Modellturn bei Clientverlust | UNRESOLVED: kein Modellzugang | UNRESOLVED für vollständiges Agentergebnis | UNRESOLVED | kein turn/start als Resume | Folgeexperiment |
| E: tatsächlicher Adapterprozess endet | Backend endete in beiden stdio-Crashfällen; Modellturn UNRESOLVED | Journalfenster überlebt; unbekannte Mutation erkennbar | echte offene Anfrage UNRESOLVED | kein automatischer Retry | unbekannte Erstellungsoperation klären |
| F: Host-/Workspace-Ausfall | nicht untersucht | nicht untersucht | nicht untersucht | keine Aussage | kein Scope-Ausbau |

Stdio koppelt in diesem konkreten Aufbau Adapter- und Backendlebensdauer. Das ist
keine Widerlegung des 0.154.0-WebSocket-Befunds zum Clientverlust. Ein unabhängiger
App-Server-Lifecycle wäre separat zu untersuchen, kein hier implementierter Daemon.
SIGKILL und beobachtetes geordnetes Ende nach stdio-EOF sind getrennte Versuche;
kein graceful Shutdown mit offener Approval wurde behauptet.

## Versionsabgleich

0.154.0 bleibt unveränderte historische Evidenz in
[Spike A](../2026-09-linux-protocol-spike/protocol-spike.md) und
[Spike B](../2026-09-command-exec-persistence/pty-persistence.md).

- 0.155.0: frischer Thread vor erster Nachricht: Resume „no rollout found“ und
  Pagination „not materialized yet“ beobachtet.
- 0.155.0: unmittelbar nach Turnstart einmal `list_turns is not supported yet`
  ([frühe Probe](evidence/credential-stop.json)); spätere paginierte
  turns/list-, read- und resume-Abfragen erfolgreich (V-PAGINATED-*).
  Timing-/Materialisierungsabhängigkeit ist eine plausible Erklärung, kein
  bestätigter Upstream-Vertrag. **Keine pauschale Paginationregression behauptet.**
  Finale Adapterfläche verwendet den separat bestätigten Legacy-Pfad.
- 0.154.0-Spike B hatte eine funktionierende Sandbox als root; 0.155.0 hier
  Non-root-Startfehler. Andere UID/Umgebung: kein kausaler Versionsregressionsbeweis.
- 0.154.0-Live-Modell-/Approvalbefunde werden nicht als neue 0.155.0-PASS übernommen.
- JSON-RPC-/clientUserMessageId-Duplikate sind neue begrenzte 0.155.0-Laufzeitevidenz.

Die [offizielle App-Server-Dokumentation](https://learn.chatgpt.com/docs/app-server)
wurde ergänzend mit dem Skill OpenAI Docs gelesen. Runtimeaussagen dieses Berichts
stammen aus dem gepinnten lokalen Binary; aktuelle Webdokumentation ersetzt sie nicht.

## Architekturfolge und exakt nächster Schritt

Bestätigt: Variante B kann eine kleine fachliche Oberfläche mit Default-Deny,
Objekt-/Parameterbindung und Requestjournal vor dem App Server durchsetzen.
Metadaten-/Fehlerturnbetrieb benötigt keine Rootrechte und keine Modellcredentials
beim simulierten Client. Transportauth und Capabilitykontrolle bleiben getrennt.

Widerlegt: „eine gemeinsame Non-root-UID isoliert A/B/Secret“ und
„JSON-RPC-ID/clientUserMessageId macht Wiederholung automatisch idempotent“ im
geprüften Fehlerturnpfad. Stdio besitzt im geprüften Aufbau keinen vom
Adapterprozess unabhängigen Backend-Lifecycle.

**Offen/blockierend:** echte Agentgrenze, funktionierende Non-root-Sandbox,
Secret-/State-/IPC-Abschirmung, positive Approvals, Modell-Lifecycle und Recovery
während tatsächlicher Arbeit. B als vollständige sichere Architektur ist weder
bestätigt noch generell widerlegt; dieser konkrete Aufbau erfüllt den Nachweis
nicht. C wurde weder implementiert noch akzeptiert.

**Genau nächstes Experiment:** Auf einem bereits vorbereiteten Linux-Testhost mit
funktionierender Non-root-Sandbox dasselbe gepinnte 0.155.0-Binary samt Bubblewrap
unter dedizierter UID einmal über privaten stdio-Kanal ausführen. Credentialfreies
command/exec-Probeprogramm aus sandbox_suite verwenden: positiver Read/Write in A,
Read/Write in B und Dummysecretbereich, Symlink/Hardlink und harmloser Prozessmarker.
OS- und Dateiergebnisse vergleichen. Keine Kernel-, Firewall- oder
Produktkonfiguration ändern; bei erneutem Sandboxstartfehler stoppen.
Das ist ein Ausführungsgrenztest, kein Ersatz für den später weiterhin notwendigen
echten Turn-/Approvaltest. Keine Produktimplementierung beginnen.
