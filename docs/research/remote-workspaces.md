# Entfernte Linux-Workspaces

> Betriebsfortschreibung 3. Oktober 2026: Der implementierte persönliche Pfad
> ist Android HTTPS/JSON/SSE → Caddy → Loopback-Python-Adapter → stdio-Codex.
> Der Pi übernimmt künftig dieselbe Hostrolle, mit privatem konfigurierbarem
> LAN-/Tailneteingang. [HOST_SETUP](../../HOST_SETUP.md) und
> [Ist-Inventar](../vps-inventory-2026-10-03.md) sind für Betrieb maßgeblich;
> die Variantenrecherche unten bleibt als historischer Entwurfsstand erhalten.

Stand: 18. September 2026. Konsolidierte Research mit begrenzten [ADRs](../decisions/README.md). Zwei Laufzeit-Spikes mit Codex 0.154.0 liegen vor. Der untersuchte Codex-Quellstand ist [`7498521d288b9b3b96ffba4eedf089d8d6e06a84`](https://github.com/openai/codex/tree/7498521d288b9b3b96ffba4eedf089d8d6e06a84). Ein Befund auf `main` bedeutet nicht automatisch, dass dieselbe Funktion in einem veröffentlichten CLI-Paket enthalten ist.

## Ergebnis

Ein eigener Workspace-Daemon ist derzeit keine notwendige Voraussetzung für CodexPad. Der aktuelle Codex App-Server bietet bereits strukturierte Dateioperationen, File Watching, Agent-Sessions und interaktive Prozesssteuerung. In [Spike A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md) wurden Dateioperationen, Watching und Git über vorhandene APIs ohne zusätzlichen Workspace-Daemon geprüft. Git lief über die unsandboxierte Host-API `process/spawn`. Die App-Server-Anbindung ist nun als primäre Agent-Schnittstelle akzeptiert. Sein experimenteller Status und die Lebensdauer einzelner Ressourcen verhindern jedoch die Schlussfolgerung, dass damit schon ein vollständiges, dauerhaft erreichbares Workspace-System vorliegt.

Die vier Varianten sind kombinierbar. SSH kann den Transport und die Host-Authentifizierung übernehmen, während Codex strukturierte Nachrichten liefert. Ein späterer kleiner Hilfsprozess könnte nur die tatsächlich fehlenden Workspace-Funktionen ergänzen. Die Produktabstraktionen sollten diese Kombination zulassen, ohne für jeden Agenten einen eigenen Dateizugriff zu verlangen.

## Experimenteller Umfang

Die [Protokollprüfung](../experiments/2026-09-linux-protocol-spike/protocol-spike.md) lief lokal über einen Unix-Socket, mit Experimental-Opt-in und einem Sandboxstartfehler. [Spike B](../experiments/2026-09-command-exec-persistence/pty-persistence.md) auf einem separaten VPS mit funktionierender Sandbox bestätigt: `command/exec`-Pipe und -PTY sterben beim Clientverlust; `processId` ist verbindungsgebunden, kein Reattach. Derselbe App Server lief weiter. Persistente Terminals benötigen eine separate Capability; `tmux`, eigener PTY-Service und andere serverseitige Sessionmodelle bleiben ungeprüfte Optionen.

Ein eigener allgemeiner Workspace-Daemon folgt daraus nicht. Für Remote-Android fehlen sichere Transportgrenze, minimale Host-Capabilities und Workspace-Isolation. Ein lokaler Unix-Socket-Test weist keine Remote-Authentifizierung oder WSS-Sicherheit nach.

## Dokumentierte Grundlagen

### SSH trägt Kanäle, keine Workspace-Semantik

Das SSH-Verbindungsprotokoll multiplexiert Kanäle über eine verschlüsselte Verbindung. Es beschreibt unter anderem Programmausführung, Subsysteme, PTY-Anforderung, Größenänderung, Signale und Exit-Status. Es definiert damit noch keinen Projektindex, keine Git-Ansicht und kein Wiederanfügen an eine zuvor getrennte Terminalsession. [RFC 4254, insbesondere Abschnitte 5 und 6](https://www.rfc-editor.org/rfc/rfc4254)

Ein SSH-Kanal mit PTY ist deshalb von einer persistenten Terminalsession zu unterscheiden. Ein zusätzlicher Prozessmanager kann die Lebensdauer entkoppeln. `tmux` ist ein bestehendes Beispiel für Sessions mit Attach/Detach; daraus folgt noch keine geeignete strukturierte API für native Prozesskarten oder einzelne Ausgabeevents. [tmux-Handbuch, Session-Lebensdauer und Control Mode](https://man.openbsd.org/tmux)

OpenSSH dokumentiert SFTP für Dateitransfers über SSH einschließlich wiederaufgenommener Transfers. Der Zustand bereits übertragener Teile muss dabei passen; daraus folgt keine automatische konfliktfreie Synchronisation eines Workspace. [SFTP-Handbuch](https://man.openbsd.org/sftp)

Für Git existiert eine explizit für Skripte vorgesehene Ausgabe. `git status --porcelain` ist im Gegensatz zur normalen Kurzansicht als stabiles Format dokumentiert; `-z` vermeidet die Mehrdeutigkeit von Dateinamen mit Zeilenumbrüchen. Strukturierte Git-Ausgabe zu verwenden ist etwas anderes als menschenlesbare Agent-CLI-Ausgabe zu parsen. [Git-Status-Dokumentation](https://git-scm.com/docs/git-status)

Linux `inotify` arbeitet nicht automatisch rekursiv und kann seine Eventqueue überlaufen lassen. Ein File-Watching-System muss daher auch neue Unterverzeichnisse, verlorene Events und erneutes Einlesen des aktuellen Zustands behandeln. Eine Ereignisliste allein ist kein zuverlässiger Dateisystemzustand. [Linux inotify-Dokumentation](https://man7.org/linux/man-pages/man7/inotify.7.html)

### Codex übernimmt bereits einen Teil der Workspace-Aufgaben

Der aktuelle App-Server beschreibt unter anderem `fs/readFile`, `fs/writeFile`, `fs/readDirectory` und Watching. `command/exec` unterstützt strukturierte Prozessausgabe sowie Schreiben auf stdin, PTY-Größenänderung und Terminierung. Die separat experimentellen `process/*`-Methoden laufen außerhalb der Codex-Sandbox. Diese Methoden müssen getrennt betrachtet werden. [Offizielle App-Server-Dokumentation](https://developers.openai.com/codex/app-server), [versionsgebundener App-Server-Quellcode](https://github.com/openai/codex/tree/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server)

Die offizielle Dokumentation bezeichnet den App-Server-Befehl und WebSocket-Transport als experimentell und nicht für Produktionslasten unterstützt. Gleichzeitig unterscheidet das Protokoll reguläre und durch `experimentalApi` freizuschaltende Methoden. Die Existenz einer regulären Methode ist somit keine pauschale Produktionsgarantie für den gesamten Transport. Nicht lokale WebSocket-Listener akzeptieren laut aktueller Dokumentation während des Rollouts standardmäßig unauthentifizierte Verbindungen. Authentifizierung muss ausdrücklich konfiguriert werden; ungeschütztes `ws://` gehört nur auf localhost beziehungsweise hinter eine SSH-Weiterleitung. [Offizielle App-Server-Dokumentation](https://developers.openai.com/codex/app-server)

Besonders relevant für Tablet-Verbindungsabbrüche: Der Upstream-Test `command_exec_process_ids_are_connection_scoped_and_disconnect_terminates_process` prüft, dass `command/exec`-Prozesskennungen an die Verbindung gebunden sind und ein Disconnect den Prozess beendet. Die Watch-Verwaltung entfernt Watches beim Schließen der Verbindung. Ein fortsetzbarer Agent-Thread garantiert deshalb kein weiterlaufendes Terminal und keinen Watch-Replay nach Reconnect. [Prozesstest](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server/tests/suite/v2/command_exec.rs), [Watch-Verwaltung](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server/src/fs_watch.rs)

Auch die Sicherheitsgrenze ist differenziert: Der aktuelle Dateiprozessor wählt die lokale Ausführungsumgebung und übergibt für die Dateioperationen `sandbox: None`. Die Einschränkungen eines Agent-Turns schützen somit nicht automatisch die direkten Datei-RPCs eines Clients. Betriebssystemrechte gelten weiterhin. Der Client und seine Verbindung benötigen das Vertrauen, das diesen direkten Host-Zugriff rechtfertigt. [Dateiprozessor](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server/src/request_processors/fs_processor.rs)

Upstream enthält außerdem `codex-app-server-daemon` für den Lebenszyklus von über SSH gestarteten Codex-Instanzen. Start und Bootstrap liefern JSON. Die README kennzeichnet auch diesen Vertrag als experimentell. Gemeinsam angeschlossene Clients teilen die beim Start geerbte Umgebung; eine Isolation pro Client besteht nicht. Updates können einen Neustart und damit eine Unterbrechung aktiver Arbeit auslösen. Das ist ein vorhandener Baustein für Remote-Betrieb, aber kein Beleg für Mehrbenutzerisolation oder verlustfreie Wiederaufnahme. [Daemon-README](https://github.com/openai/codex/blob/7498521d288b9b3b96ffba4eedf089d8d6e06a84/codex-rs/app-server-daemon/README.md)

## Variantenvergleich

Die Bewertungen der folgenden Tabelle sind Architekturhypothesen auf Basis der genannten Fähigkeiten, keine Messergebnisse.

| Variante | Dateisystem, Git und Prozesse | Vorteil | Aufwand und Grenze |
| --- | --- | --- | --- |
| 1. Direkte SSH-Steuerung | SFTP als Kandidat für Dateien; explizite Git-Kommandos; SSH-Exec und PTY | Bestehende Linux-Zugänge nutzbar, wenig zusätzliches Host-Deployment | Viele Aufrufe über hohe Latenz; sichere Argumentübergabe; eigene Zustandslogik; kein Agent-Eventprotokoll durch SSH allein |
| 2. SSH mit strukturierten Hilfsprozessen | Versionierte Nachrichten über stdin/stdout, etwa JSONL; Hilfsprozess bündelt Watch-, Git- und Dateioperationen | SSH übernimmt Zugang und Verschlüsselung; fachliche Events können native Ansichten versorgen | Installation und Versionierung des Helfers; Framing und Backpressure; Lebensdauer über SSH hinaus ausdrücklich zu lösen |
| 3. Kleiner eigener Daemon über HTTPS/WebSocket | Eigene API für Dateien, Git, PTYs, Sessions und Transfer | Volle Kontrolle über Projektgrenzen, Wiederaufnahme und mehrere Agenten | Größter Pflegeaufwand: Auth, TLS, Updates, persistenter Zustand, Rechte, Ressourcenlimits und Protokollkompatibilität |
| 4. Codex App-Server direkt | Vorhandene Datei-RPCs, Watching, strukturierte Agent-Events und PTY-Kommandos; Git gegebenenfalls über strukturierte Kommandos | Wenig eigene Infrastruktur; kein Fork notwendig; bereits passende Client-Schnittstelle | Experimenteller Vertrag, Codex-Abhängigkeit, Ressourcen mit Verbindungslaufzeit, direkte Host-Rechte und unklare Vollständigkeit für große Workspaces |

Variante 4 kann für eine Exploration über Variante 1 transportiert werden. Variante 2 muss nicht bedeuten, Codex-Funktionen neu zu implementieren. Ein Helfer wäre erst durch eine belegte Lücke gerechtfertigt. Variante 3 bleibt eine Option, falls Anforderungen an andere Agenten, dauerhafte Prozesse oder Workspace-Isolation dies später verlangen.

## Querschnittsfragen und mögliche Verträge

### Dateien, Diffs und große Repositories

Hypothese: Linux bleibt die maßgebliche Quelle für Projektinhalt und Git-Zustand. Die Android-App hält einen begrenzten Cache für zuletzt gelesene Dateien, Verzeichnislisten und Diffs. Remote-Dateien bekommen eine stabile Workspace-Identität plus einen relativen Pfad; ein lokaler Cachepfad ist keine Projektidentität.

Ein Cache braucht erkennbare Aktualität. Vor einem Schreibvorgang wären eine Versionskennung oder ein Inhaltsvergleich sinnvoll, damit ein zwischenzeitlicher Agent-Edit nicht überschrieben wird. Symlinks, Umbenennungen, ausführbare Bits, Binärdateien, sehr große Dateien und Pfade außerhalb der gewählten Projektwurzel sind offene Vertragsfragen. SFTP-Unterstützung allein beantwortet diese Fragen nicht.

Für große Repositories sollte die Exploration Kosten für Verzeichnisabrufe, Git-Status, Diffs und erste Dateianzeige getrennt messen. Lazy Loading, ignorierte Build-Verzeichnisse und begrenzte Diff-Größen sind plausible Maßnahmen, aber noch keine festgelegte Umsetzung. File Watching dient als Invalidierungshinweis. Nach Reconnect oder erkanntem Eventverlust muss ein Zustandsabgleich möglich sein.

Dateitransfer benötigt eigene Grenzen. Ob kleine Dateien über RPC und große Dateien über SFTP oder einen separaten Transferweg gehen, bleibt offen. Eine einzelne WebSocket-Verbindung darf durch eine große Datei weder Approvals noch interaktive Terminaleingaben unbenutzbar machen. Backpressure und Priorisierung sind Anforderungen an die Exploration.

### Reconnect und Session Resume

Thread/Conversation, Turn, Live Event Stream, Approval, App-Server-Prozess, `command/exec`, PTY und OS-Prozess besitzen unterschiedliche Lebenszyklen. Hinzu kommen Transport und lokale UI. Die [Lifecycle-Matrix](codex-integration.md#reconnect-ist-mehr-als-session-resume) trennt Clientverlust vom Serververlust. Laufende Modell-Turns und ein offenes Datei-Approval waren bei Clientverlust rekonstruierbar. Serververlust unterbrach den wartenden Turn und verlor das offene Approval; abgeschlossene Historie blieb verfügbar.

Eine mögliche Reconnect-Sequenz wäre Authentifizierung, Versionsabgleich, erneutes Lesen des Thread-Zustands, erneute Einrichtung von Watches und Aktualisierung der sichtbaren Dateien sowie des Git-Status. Verpasste Live-Deltas und Endevents wurden in Spike A nicht vollständig nachgeliefert. Der Client muss Snapshot/Historie mit neuen Live-Events abgleichen und abgeschlossene vollständige Inhalte gegenüber lokaler Deltaverkettung priorisieren. Das ist die Entscheidung aus [ADR 0002](../decisions/0002-state-reconciliation.md). Bereits gesendete Mutationen dürfen nach einem Timeout nicht blind wiederholt werden: Der Server kann sie ausgeführt haben, obwohl die Antwort verloren ging.

Offen bleibt vor allem, welche Arbeit bei gesperrtem Tablet weiterlaufen soll. Ein weiterlaufender Agent, ein manueller Testlauf und ein interaktives Terminal können unterschiedliche Anforderungen haben. Der aktuelle `command/exec`-Disconnect-Vertrag ist dafür eine konkrete Einschränkung. Eine persistente Terminalschicht wäre ein eigenständiger Bedarf, nicht automatisch Teil von Thread-Resume.

### Authentifizierung und Berechtigungen

Workspace-Zugang und Agent-Provider-Zugang sind zwei verschiedene Beziehungen. SSH-Schlüssel oder ein Workspace-Token legitimieren Host-Zugriff; Codex-Credentials legitimieren den Agenten gegenüber seinem Anbieter. Die App sollte Provider-Secrets nicht ohne Bedarf übertragen oder duplizieren. Der genaue Anmeldeablauf gehört zur Codex-Exploration.

Als Hypothese für den ersten privaten Einsatz ist ein explizit bestätigter SSH-Hostschlüssel mit einem benutzerspezifischen Linux-Konto einfacher zu begrenzen als ein öffentlich erreichbarer Daemon. Das ersetzt keine Prüfung der Android-SSH-Bibliothek. Schlüsselprüfung darf bei einem Verbindungsproblem nicht automatisch abgeschaltet werden.

Für HTTPS/WebSocket wären unter anderem Token-Rotation, Widerruf, TLS-Terminierung und serverseitige Autorisierung nötig. Agent-Approvals sind kein Ersatz für Client-Authentifizierung. [Spike A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md) bestätigte direktes FS-Schreiben außerhalb des Thread-Workspaces ohne Approval sowie unsandboxierte Hostprozesse. [ADR 0004](../decisions/0004-client-host-trust-boundary.md) behandelt den Steuerungsclient daher als Host-Trust-Boundary; Agent-Sandbox und `cwd` erzwingen keine ausreichende Grenze für Remotezugriff. Ebenso begrenzt ein ausgewähltes Projekt in der UI nicht automatisch die Host-Rechte des Dateiprozessors.

### Mehrere Projekte und kurzlebige Container

Hypothese: Workspace-ID, Host-Ziel und Projektwurzel sollten getrennte Werte sein. Mehrere Projekte können denselben Host teilen; ein neu gestarteter Container kann dieselbe logische Projektidentität mit neuer Verbindungsadresse besitzen.

Parallelität wirft Fragen zu Git-Worktrees, Dateikonflikten und Prozesszuordnung auf. Eine UI-Zuordnung zu einem Projekt ist keine Betriebssystemisolation. Der gemeinsame Daemon und dessen geerbte Umgebung dürfen insbesondere nicht versehentlich als Isolation zwischen fremden Benutzern betrachtet werden.

Container bleiben eine spätere Option. Vorher wäre zu klären, wo Repository, Thread-Historie, Prozesslogs und Credentials nach dem Ende des Containers liegen. Automatische Provisionierung, Image-Pflege, Kostensteuerung und Mehrbenutzerbetrieb gehören nicht zur ersten Discovery-Phase.

## Vor v0.1 zu klären

- Remote-Transportauthentifizierung, Host-Key-/Zertifikatsprüfung und TLS/WSS bzw. ein geschützter Tunnel; noch keine Auswahl.
- Minimale Android-Host-Capabilities und tatsächlich erzwungene Workspace-Isolation.
- Gepinnte unterstützte Codex-Version, erforderliche experimentelle APIs und Upgradevertrag. 0.154.0 ist der Messstand, noch keine Supportpolitik.
- Retry-/Idempotenzsemantik bei verlorenem `turn/start`-Acknowledgement sowie Approvalantwort-/Disconnect-Rennen.
- Kleinster Tablet-Workflow und dessen Terminalbedarf. Falls Persistenz erforderlich ist, muss ihre Lösung vor Aufnahme dieser Capability nachgewiesen werden.

Dateigrößen, Git-Aktualität und UI-Reconciliation bei wiederholten Disconnects können innerhalb eines begrenzten Prototyps untersucht werden. Manuelles Schreiben braucht zuvor eine Konfliktstrategie. Priorisierung und weitere Produktfragen stehen in [Offene Fragen](../product/open-questions.md).

## Genau nächster Schritt

Der begrenzte Research-Auftrag ist inzwischen als [Remote-Vertrauensmodell](remote-trust-model.md) mit [Capability-Matrix und Hostrechten](host-capabilities.md) dokumentiert. Er untersucht den engeren Kernworkflow ohne allgemeines Terminal/Dateimanagement; ein begrenzender Adapter kann eine Sicherheitsaufgabe erfüllen, auch wenn kein zusätzlicher allgemeiner Workspace-Daemon für Funktionen nötig ist. Lokales 0.155.0-Schema und aktuelle Dokumentation sind dabei ausdrücklich von den 0.154.0-Laufzeitbefunden getrennt.

Genau nächster technischer Schritt ist der dort beschriebene lokale Capability-Grenz-Spike unter Non-root, zunächst ohne Remote-Listener. Transporttechnik und konkrete Workspace-Isolation bleiben offen; kein Remotezugang oder Dienst wurde im Research eingerichtet.
