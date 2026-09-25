# Minimales Remote-Vertrauensmodell für CodexPad v0.1

Stand: 18. September 2026. **Research abgeschlossen; technische Sicherheitsnachweise ausstehend.** Kein PRD, keine Implementierung und keine neue Architekturentscheidung. [ADRs 0001–0004](../decisions/README.md) gelten unverändert. Die hier formulierten Einschränkungen sind Anforderungen und Architekturfolgerungen, keine bereits vorhandenen CodexPad-Funktionen.

## Ergebnis und Geltungsbereich

Für den vorgegebenen Workflow genügt eine kleine fachliche Oberfläche: freigegebene bestehende Workspaces auswählen, zugehörige Threads lesen/starten/resumieren, Turns senden und beobachten, begrenzte Approvals beantworten und Ergebnisse rekonstruieren. Allgemeine Datei-, Shell-, Prozess-, Konfigurations- und Administrations-RPCs gehören nicht dazu. Die [Capability-Matrix](host-capabilities.md#capability-matrix) beschreibt die einzelnen Gruppen.

**Folgerung aus ADR 0004:** Ein gesicherter Tunnel zum unveränderten App Server allein erfüllt das geforderte eingeschränkte Remote-Vertrauensmodell nicht. Er authentifiziert den Zugang, reduziert aber nicht die dahinter verfügbaren Hostfähigkeiten. Ein begrenzender Adapter ist deshalb der stärkste Kandidat für den nächsten Nachweis. Er ist noch keine ausgewählte Architektur und genügt ohne wirksame Begrenzung der Agent-Ausführung ebenfalls nicht.

Arbeitsannahme für diese Untersuchung: ein eigener Linux-Host, ein vertrauenswürdiger Betreiber, bestehende ausdrücklich freigegebene Projekte und ein möglicherweise vollständig kompromittierter Android-Client. Andere Projekte, Host-Secrets und Betriebszustand sind Schutzgüter. Repository-Dateien, Modelltext und Toolausgaben sind keine Autorisierungsquelle. Fremde lokale Benutzer werden nicht automatisch als vertrauenswürdig behandelt; Host-root und Kernelkompromittierung liegen außerhalb des behaupteten Schutzes. Multi-Tenant-Hosting ist kein v0.1-Ziel.

Eine kompromittierte App darf sämtliche regulären Remote-Credentials benutzen und beliebige Nachrichten konstruieren. Auf Android-Biometrie, UI-Auswahl oder gutes Verhalten des Modells darf die Hostgrenze nicht angewiesen sein. Wer Agent-Aufträge und erlaubte Approvals senden darf, kann innerhalb dieser Rechte Schaden verursachen, Projektinhalte lesen/verändern und Modellkosten auslösen. Das minimale Modell begrenzt den Schaden; es verspricht keine Unversehrtheit freigegebener Projekte.

## Evidenz und Versionen

Die gesamte vorhandene Projektdokumentation einschließlich beider vollständiger Spike-Berichte und aller ADRs wurde vor diesen Schlussfolgerungen gelesen. Quellen und lokale Prüfung sind in [Research-Evidenz](remote-trust-evidence.md) nachvollziehbar erfasst.

| Kürzel | Evidenz | Aussagegrenze |
| --- | --- | --- |
| E1 | [Spike A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md), archivierte Typen und Offline-Verifikation | 0.154.0; echte Modell-/Approval-/Reconnect-Befunde; Sandboxstart für Shell scheiterte; keine Remoteauth |
| E2 | [Spike B](../experiments/2026-09-command-exec-persistence/pty-persistence.md) | 0.154.0; command/exec-Pipe/PTY bei Clientverlust beendet; nur Bericht erhalten |
| L | [Lokaler Schnittstellenbefund](remote-trust-evidence.md#lokaler-stand) | installiertes 0.155.0; Hilfe und Schemaexport mit/ohne Experimental-Felder; kein Server-/Agent-Lauf |
| D | [Aktuelle App-Server-Dokumentation](https://learn.chatgpt.com/docs/app-server) | bewegliche Dokumentation, kein versionsgebundener Laufzeitnachweis |
| F | Folgerungen in diesem Dokument | vorgeschlagene Anforderungen, noch zu testen |

**Nicht vermischen:** E1 konnte paginierte Threads und Historienabrufe nutzen. D beschreibt derzeit paginierte Erstellung und bestimmte Lese-/Resume-Pfade als noch nicht unterstützt. L enthält weiterhin entsprechende Methoden/Typen, auch Historienmethoden im Export ohne Experimental-Opt-in. Daraus folgt weder eine bestätigte Regression noch eine Laufzeitgarantie für 0.155.0. Vor einem verbundenen Prototyp muss genau dessen Version mit dem benötigten Historienmodus geprüft werden. D nennt außerdem eingeschränkten Lesezugriff in `SandboxPolicy`; das lokale 0.155.0-Schema enthält diese beschriebenen Lesefelder nicht. Keine darauf aufbauende Secret-Isolation voraussetzen. [D](https://learn.chatgpt.com/docs/app-server), [L](remote-trust-evidence.md#dokumentationsabweichungen)

## Vertrauensgrenzen und Identitäten

Die folgende Zeichnung beschreibt Verantwortlichkeiten der Adapteroption, keine beschlossene Deployment-Technik:

```text
Android: nicht vertrauenswürdiger Auftraggeber, gerätespezifische Remote-Identität
    │ verschlüsselter, gegenseitig identifizierter Zugang
    ▼
CodexPad-Hostadapter: Remote-Authentifizierung + Capability-/Objekt-Autorisierung
    │ privater lokaler Kanal, eigener Verbindungs- und Request-Lifecycle
    ▼
Codex App Server: privilegierter Steuerungsclient-Vertrag, Thread-/Turn-Persistenz
    ├── Modell-Authentifizierung ──► OpenAI / gewählter Provider
    └── Agent / Tools ──► technisch begrenzte Ausführungsumgebung
                             ├── ausgewählter Workspace
                             └── explizite Laufzeit-/Toolchain-Ressourcen

Host-Betriebssystem: erzwingt UID-, Datei-, Prozess- und ggf. Namespace-/MAC-Grenzen
Host-Betreiber: verwaltet Zuordnungen, Grenzen und Credentials außerhalb Androids
```

| Beziehung | Authentifizierte Identität | Wer autorisiert was? | Credential / Zuständigkeit |
| --- | --- | --- | --- |
| Android → Remote-Endpunkt | eigenständiges Gerät, einem Betreiber und erlaubten Workspaces zugeordnet | Adapter prüft jede Operation und Ziel-ID; Transport prüft zunächst nur Zugang | gerätespezifischer Schlüssel/Token auf Android; Verifier/öffentlicher Schlüssel und Widerruf am Host |
| Host → Android | bestimmter Host/Service | Android akzeptiert nur verifizierte Serveridentität | bestätigter SSH-Hostkey oder korrekt validiertes TLS-Zertifikat; Austausch nicht automatisch akzeptieren |
| Adapter → App Server | lokaler vertrauenswürdiger Steuerungsprozess | Adapter legt RPC und Parameter fest; App Server führt seinen Vertrag aus | privater Unix-Socket mit Zugriffsrechten oder vom Host besessene stdio-Verbindung; etwaiger interner Token nur hostseitig |
| App Server → Modellanbieter | eigener Modell-/OpenAI-Account | Provider autorisiert Modellnutzung; keine Autorisierung des Android-Hostzugangs | API-Key oder Codex-verwaltete OAuth-Tokens ausschließlich auf dem Host |
| Agent → Workspace/OS | eingeschränkter Prozesskontext, keine Android-Identität | Kernelgrenze plus Codex-Policy; Adapter darf keine Erweiterung außerhalb des Betreiberlimits genehmigen | normalerweise kein eigenes Remote-Credential; nur notwendige Prozessumgebung |
| Betreiber → Hostkonfiguration | lokal administrierender Eigentümer | richtet freigegebene Roots, Dienstidentitäten, Providerlogin und Widerruf ein | vom Android-Remote-Schlüssel getrennte administrative Autorität |

`clientInfo` im Initialize-Handshake, Thread-ID, `projectId` und `cwd` sind keine Identitätsbeweise. Eine UUID ist keine Berechtigung. Ein Adapter muss eingehende Anfragen **und ausgehende** Historien, Events und Approvalrequests anhand der tatsächlichen Zuordnung filtern. Eine ungefilterte Subscription kann Daten anderer Projekte verraten, auch wenn deren Mutationen gesperrt sind. [E1, L; F]

### Credential-Regeln für den Entwurf

- Android benötigt nur seinen gerätespezifischen Remote-Zugang und Material zur Serverprüfung. Modell-/OpenAI-API-Keys, OAuth-Access-/Refresh-Tokens, Host-Adminschlüssel, Token-Signiergeheimnisse, CA-Privatschlüssel und interne App-Server-Zugänge dürfen dort ausdrücklich nicht liegen.
- Providerlogin erfolgt zunächst durch den Betreiber auf dem Host. Ein späterer Browser-/Device-Code-Assistent wäre eine zusätzliche, gesondert zu prüfende Capability; keine Weiterleitung von `account/login/start`, Logout oder Tokenrefresh an Android voraussetzen. Ein Status wie „Modellzugang bereit/Anmeldung am Host nötig“ reicht fachlich.
- Der Adapter braucht zum Vermitteln keine Kopie der Provider-Secrets. Seine Identität und ausführbaren Dateien, Workspace-Zuordnungen und Remote-Widerrufsdaten dürfen nicht durch den Agenten veränderbar sein. Dass Codex auf seine eigenen Tokens zugreifen muss, gibt Agent-Tools keinen notwendigen Zugriff auf dieselben Dateien oder geerbte Umgebungsvariablen.
- Codex-State und Logs sind sensitiv: Bei der früheren Archivbereinigung wurden JWTs in Runtime-Logs gefunden. Daher weder komplettes `CODEX_HOME` noch Logs über eine Dateiansicht freigeben. Eine Redaktionsroutine allein ersetzt keine Lesebeschränkung. [Archivbefund](../experiments/README.md#secret--und-credential-prüfung)
- Gerätewiderruf muss neue Verbindungen **und bestehende autorisierte Sitzungen** sperren. Entzug eines einzelnen Geräts soll weder Providerrotation noch Neustart des Agenten erfordern. Zeitpunkt und maximale Durchsetzungsfrist müssen im Spike messbar sein. Widerruf nimmt bereits ausgeführte Dateiänderungen nicht zurück.

Android Keystore kann Schlüssel nicht exportierbar speichern; eine kompromittierte App kann unter Umständen dennoch Schlüsseloperationen auslösen. Das stützt den hier angenommenen Angreifer mit gültigen Credentials. Geräte-/Algorithmusunterstützung und Biometrie beim Reconnect bleiben zu testen. [Android Keystore](https://developer.android.com/privacy-and-security/keystore)

## Workspace-Isolation: geeignete Mechanismen und Grenzen

„Workspace“ bezeichnet im Entwurf eine vom Host verwaltete ID mit zugelassenem Root, Ausführungskontext und zulässigen Thread-Zuordnungen. Ein Projektkatalog kann diese Zuordnung beschreiben, sie aber nicht selbst erzwingen. Entscheidend ist sowohl der direkte Clientpfad als auch der indirekte Weg `turn/start → Agent → Tool/Buildskript`.

| Mechanismus | Was er technisch leisten kann | Was er nicht allein leistet / offene Prüfung |
| --- | --- | --- |
| UI-Auswahl, `cwd`, Thread-/Projektfilter | fachliche Zuordnung und relative Pfadauflösung | keine Zugriffssperre; absolute Pfade und andere RPCs bleiben möglich [E1, L] |
| Unix-Dateirechte | Kernel prüft UID/Gruppen und Verzeichnisdurchquerung | ein gemeinsamer Entwicklerbenutzer behält alle eigenen Zugriffe; Rechte sind keine RPC-Allowlist [P1] |
| Eigener unprivilegierter Unix-Benutzer | trennt Dienst von privaten Dateien und Prozessen anderer Identitäten, soweit Rechte passen | eigenes Home, gemeinsam beschreibbare Verzeichnisse und allgemein lesbare Dateien bleiben erreichbar; derselbe Benutzer für mehrere Workspaces trennt diese nicht automatisch [P1; F] |
| POSIX ACLs | gezielte zusätzliche Rechte auf vorhandene Projekte ohne pauschalen Eigentümerwechsel | additive Freigaben, Masken und Default-ACLs müssen geprüft werden; kein allgemeines Deny-System und keine Prozess-/RPC-Grenze [P2] |
| Container-/Namespace-Kombination | eigener Mount-/PID-/Netzkontext; nur gewählte Roots einbinden, Laufzeitbasis read-only | ein Namespace allein ist kein vollständiger Container; breite Hostmounts, Docker-Socket, Hostnetz, privilegierter Modus oder geteilte Secrets können Schutz aufheben; Linux-Kernel bleibt geteilt [P3; F] |
| Begrenzender Adapter | Geräte-/Workspace-/Thread-Autorisierung, Parameteraufbau, Approval-Deckel, Ausgabe- und Ressourcenlimits | schützt nur vermittelte Wege; Agenttools, Repository-Hooks und ein direkt erreichbarer App Server können ihn umgehen [E1, L; F] |
| Landlock / verpflichtende Zugriffskontrolle | zusätzliche vom Prozess nicht einfach aufhebbare Zugriffsbeschränkung; Landlock ist unprivilegiert nutzbar | Kernel-/ABI-Abdeckung, erlaubte Operationen und Prozessbaum prüfen; keine automatische CodexPad-Integration [P4] |
| Sichere pfadrelative Dateioperationen | `openat2` kann Auflösung unter einem Root und Symlink-/Mountbeschränkungen erzwingen | schützt nur Aufrufe, die tatsächlich so ausgeführt werden; kein Schutz, wenn anschließend ein anderer Prozess denselben String erneut auflöst [P5; F] |

Primärquellen: [P1: Linux-Pfadauflösung und Rechte](https://man7.org/linux/man-pages/man7/path_resolution.7.html), [P2: ACL-Modell](https://man7.org/linux/man-pages/man5/acl.5.html), [P3: Namespaces](https://man7.org/linux/man-pages/man7/namespaces.7.html), [P4: Landlock](https://docs.kernel.org/userspace-api/landlock.html), [P5: openat2](https://man7.org/linux/man-pages/man2/openat2.2.html).

**Folgerungen für die Prüfung:** Ein Stringpräfix oder „realpath prüfen, dann Pfad weiterreichen“ reicht wegen Symlinkwechseln und Rennen nicht. Ein späterer Dateilesepfad muss auch `..`, absolute Pfade, Symlinks in Elternverzeichnissen, Magic Links, Mounts, Hardlinks sowie Umbenennungen behandeln. Hardlinks referenzieren denselben Inode; reine Pfadnormalisierung beweist dafür keine inhaltliche Trennung. Die Testumgebung braucht geeignete Rechte/Mountgrenzen oder eine explizite Ablehnung solcher Projektlayouts.

Die Zulassung eines einzelnen Projekts muss auch Zugriffe auf ein zweites, sonst freigegebenes Projekt verhindern, sofern keine ausdrückliche Mehrprojekt-Capability gilt. „Dienst hat Zugriff auf A und B, Turn hat cwd A“ genügt nicht. Ein getrenntes Backend/UID je Workspace und eine Mount-/MAC-Grenze sind prüfbare Optionen. Mehrere Threads desselben Projekts erfordern hingegen nicht automatisch mehrere OS-Benutzer.

Agent-Approvals dürfen die äußere Grenze nicht erweitern. Insbesondere muss ein kompromittiertes Android auch durch `accept`, Sessionfreigaben oder Permission-Anfragen weder einen unsandboxierten Hostbefehl noch Zugriff auf Provider-Secrets gewinnen. Shelltext zuverlässig zu beurteilen ist kein Ersatz für eine OS-Grenze. Kann die Plattform eine Zustimmung nur durch Aufhebung der benötigten Isolation erfüllen, muss dieser Vorgang im minimalen Workflow abgelehnt werden. [E1, L; F]

Noch offen ist, welche Kombination auf dem Zielhost funktioniert: Die beiden Spikes hatten unterschiedliche Sandbox-Ergebnisse; B lief als root und beweist keinen Non-root-Betrieb. `no_new_privs` kann Privileggewinn durch `execve` begrenzen, ist aber selbst keine Dateisystemisolation. Kein Verfahren wird hier ausgewählt oder am Host aktiviert. [E1, E2; Linux no_new_privs](https://docs.kernel.org/userspace-api/no_new_privs.html)

## Remote-Transport und Authentifizierung

Bewertungen sind auf diesen Workflow bezogene Folgerungen; keine Android- oder Remote-Messungen. Verschlüsselung schützt den Kanal. Clientauthentifizierung identifiziert den Teilnehmer. Capability-Autorisierung entscheidet, welche Operation er auf welchem Objekt ausführen darf. Keine dieser drei Aufgaben ersetzt die anderen.

| Option | Angriffsoberfläche / Clientauthentifizierung | Credentials und Widerruf | Android / v0.1-Komplexität | Vertrauensgrenze und Host-Capabilities |
| --- | --- | --- | --- | --- |
| Direkter App Server | gesamter erreichbarer RPC-Vertrag; dokumentierte WS-Bearer-Authentifizierung muss ausdrücklich eingeschaltet werden | Remote-Bearer getrennt von Providerauth; geräteweiser Widerruf und laufende Sitzungen nicht nachgewiesen | eigener WS-Client plausibel; wenig Vermittlungscode, erheblicher Sicherheitsnachweis | voller Steuerungsclient; Name `capability-token` belegt keine RPC-/Workspace-Scopes |
| SSH-Tunnel | SSH-Endpunkt plus weitergeleiteter Dienst; Hostkeyprüfung und gerätespezifischer Schlüssel | Public Key am Host, Private Key am Gerät; Schlüsselentzug plus Behandlung vorhandener SSH-Verbindungen nötig | Android-SSH-/Keystore-Integration offen; moderat bei bestehendem SSH-Betrieb | Forwardingbeschränkung begrenzt Ziele, nicht RPCs; normaler Shell-/SFTP-Zugang mit demselben Schlüssel würde Adaptergrenze umgehen |
| TLS/WSS + eigene Clientauth | TLS-Endpunkt, HTTP-Upgrade und Authlogik; Serverzertifikat plus gerätespezifischer Token | TLS-Privatschlüssel hostseitig; Geräteverifier/Widerruf im Dienst; bestehende WS-Sitzungen mit erfassen | native TLS-/WS-Integration plausibel; Zertifikats- und Pairingbetrieb zusätzlich | hinter TLS bleibt volle RPC-Fläche, sofern kein Adapter autorisiert |
| mTLS | TLS-Endpunkt verlangt Clientzertifikat; Anwendung muss Zertifikatsidentität zuordnen | Geräteschlüssel/Zertifikat; CA-Schlüssel nicht auf Gerät; Sperrliste/Registrierung und bestehende Sitzungen behandeln | KeyChain/Keystore-Anbindung zu testen; höherer Ausgabe-/Erneuerungsaufwand | stärkere Clientidentifikation, aber keine automatische fachliche Rechteprüfung |
| VPN / Overlay | Netzmitgliedschaft und erreichbare Endpunkte; zusätzlich Verwaltungs-/ggf. Relayfläche | Peer-/Geräteschlüssel und ggf. Overlay-Konto; Gerät entfernen und aktive Erreichbarkeit prüfen | Android-VPN möglich; bestehendes Netz kann Aufwand reduzieren, neues Netz erhöht Betriebsumfang | Netzwerk-ACL ist keine RPC-Autorisierung; ein zugelassener Peer darf nicht dadurch Vollzugriff erhalten |
| CodexPad-Hostadapter | eigener kleiner Dienst plus interner Codex-Kanal; benötigt einen der gesicherten Transporte | Geräteidentität → Widerruf → fachliche Rechte; interne Secrets bleiben hostseitig | zusätzlicher Autorisierungs-/Recovery-Code; größte eigene Prüfverantwortung | kann Methoden, Parameter, Objektzugriffe und Approvalumfang begrenzen; Agent-/OS-Grenze zusätzlich nötig |

Quellen zu Mechanismen: [App-Server-Transport](https://learn.chatgpt.com/docs/app-server), [OpenSSH-Konfiguration](https://man.openbsd.org/sshd_config), [SSH-Schlüsseloptionen](https://man.openbsd.org/sshd.8), [TLS 1.3, insbesondere Clientauthentifizierung in Abschnitt 4.4](https://www.rfc-editor.org/rfc/rfc8446), [WireGuard-Protokoll](https://www.wireguard.com/protocol/), [Android VPN](https://developer.android.com/develop/connectivity/vpn). Diese Quellen wählen keine Bibliothek für CodexPad.

Für eine spätere SSH-Option müssen Session-/Exec-/PTY-/SFTP-, Agent-, X11-, Remote- und beliebige Zielweiterleitungen tatsächlich ausgeschlossen sein. OpenSSH bietet dafür verschiedene Kontrollen wie `MaxSessions`, `PermitOpen` und Key-Restriktionen; ihre Kombination und versionsabhängige Wirkung sind zu testen, hier nicht konfiguriert. Eine Forwarding-Key-Konfiguration darf nicht versehentlich Shellzugang behalten. Ein bloßer `ForceCommand` ist kein Beleg für geschlossene übrige Kanäle.

TLS 1.3 erlaubt 0-RTT mit Replay-Risiken; mutierende CodexPad-Operationen sollen im Entwurf keine Early Data verwenden. Zertifikatsprüfung und eigener Clienttoken bleiben auch bei einem privaten Netz relevante, getrennte Entscheidungen. Ein TLS-Terminierer muss die verifizierte Identität fälschungssicher an den Adapter übergeben; vom Client gesetzte Identitätsheader dürfen das nicht ersetzen. [TLS 1.3 §8](https://www.rfc-editor.org/rfc/rfc8446); F

Ein privater Unix-Socket ist als interne Grenze präziser beschränkbar als die Annahme, jeder Loopback-Client sei vertrauenswürdig. Socket und Elternverzeichnis gehören außerhalb beschreibbarer Agent-Workspaces. Agentprozesse dürfen den internen Kanal weder als Pfad noch über geerbte Deskriptoren erreichen. Ein zweiter Listener, Proxy-/Daemonzugang oder SSH-Forward zum Backend würde die Adapterautorisierung umgehen. [ADR 0004; F]

## Minimale Hostidentität und Hostrechte

Ein unprivilegierter, dedizierter Dienstkontext ist der zu prüfende Ausgangspunkt. Ein normaler Entwickleraccount ist bequem, besitzt aber regelmäßig mehr private Dateien und Credentials als nötig. Ein root-Dienst ist aus keiner untersuchten Workflow-Capability begründet. Die [Ressourcenmatrix](host-capabilities.md#hostressourcen-und-os-rechte) unterscheidet Workspace, persistenten Codex-State, Credentials, Runtime und Toolchain.

Normale Ausführung braucht ausgewählte Projektdateien, private State-/Runtimeverzeichnisse, das Codex-Binary und die freigegebene Toolchain sowie ausgehenden Modellzugang. Sie braucht keine Hostadministration, keinen Docker-Daemon-Socket, kein sudo, keine Benutzerverwaltung und keine freie Geräte-/Mountverwaltung. Einrichtung eines Kontos oder einer äußeren Isolation kann einmalige Administration verlangen; daraus entsteht keine Rootanforderung für den Betrieb. Der derzeitige Research als root ist keine Deploymentempfehlung.

Single User bedeutet ein berechtigter Betreiber, nicht „alle Prozesse derselben UID sind voneinander isoliert“. Wenn Adapter, Agent und Provider-Credentialdatei uneingeschränkt unter derselben UID erreichbar sind, besteht keine durch Unix-Rechte erzwungene Secretgrenze. Der Nachweis muss deshalb Lesezugriffe, Prozessumgebung, Steuerungssockets und gegebenenfalls Prozessinspektion umfassen. Nur Schreibzugriffe außerhalb des Workspace zu sperren reicht nicht.

Späterer Multi-User-Betrieb würde zusätzlich unabhängige Principals, getrennte Backend-/Credential-/Statebereiche, Ausgabefilterung, Ressourcenquoten und klare Eigentümerschaft für Threads/Approvals benötigen. Ein gemeinsamer App Server und `cwd`-Filter werden hier ausdrücklich nicht als Mandantenisolation gewertet. Keine Mehrnutzerfähigkeit aus v0.1 ableiten.

## Verlorene Requests, Reconciliation und Approval-Rennen

JSON-RPC-IDs korrelieren Antworten. Die Spezifikation definiert daraus keine dauerhafte Duplikatunterdrückung. L enthält `clientUserMessageId` für `turn/start` und `turn/steer`, aber in den untersuchten Typen keinen dokumentierten Exactly-once-Vertrag. Andere RPCs mit ausdrücklich benanntem `idempotencyKey` begründen keinen solchen Vertrag für Turns. [JSON-RPC §4–5](https://www.jsonrpc.org/specification), [L](remote-trust-evidence.md#idempotenz-und-projektkatalog)

| Operation | Bei verlorener Antwort | Sicherer Entwurf / verbleibende Lücke |
| --- | --- | --- |
| `thread/start` | Thread könnte angelegt sein, ID fehlt | nicht erneut starten; Operationsstatus/zugehöriges Ergebnis recherchieren; Titel/Zeitstempel allein sind kein sicherer Duplikatbeweis |
| `turn/start` | Turn könnte laufen, fertig sein oder Eingabe aufgenommen haben | Thread/Turns/Items abgleichen; keine neue Ausführung aus Timeout ableiten; Message-ID als Korrelationskandidat testen |
| `turn/steer` (nicht Kernumfang) | Text könnte schon in aktiven Turn aufgenommen sein | nicht blind wiederholen; `expectedTurnId` ist Vorbedingung, keine Idempotenzgarantie |
| Approvalantwort | Entscheidung könnte wirken, Bestätigung fehlen | Status und tatsächlich offenen Serverrequest abgleichen; keine neue oder gecachte Zustimmung automatisch senden |
| `turn/interrupt` | Abbruch könnte erfolgt sein oder noch laufen | nur denselben autorisierten Thread/Turn prüfen; erneute Entscheidung anhand aktuellen Zustands, nie den nächsten Turn abbrechen |
| `thread/resume` | Subscription/Laden und Overrides sind mehr als passives Lesen | im Entwurf nur gespeicherte ID mit hostseitig fixierter Policy; erneutes Anbinden nach Handshake, keine freien Overrides und keine erfundene alte Approvalantwort |
| Datei-/Prozess-/Konfigurationsmutationen, Projektanlage, Review/Fork | eigene Seiteneffekte, teils neue Arbeit | außerhalb Minimalumfang; auch später nicht blind wiederholen |

Ein Adapter könnte eine gerätespezifische `operationId`, normalisierten Payload-Hash, Workspace-/Thread-Zuordnung und Zustände `angenommen`, `an Backend gesendet`, `bestätigt`, `Ausgang unbekannt` dauerhaft speichern. Gleiche ID mit anderem Inhalt muss scheitern. Das verhindert wiederholte Weiterleitung **nur in den durch das Journal abgedeckten Fällen**. Ein Absturz zwischen Backend-Ausführung und Journalbestätigung bleibt ohne Backend-Idempotenz/verlässliche Zuordnung zweideutig. Kein Exactly-once-Versprechen; im Zweifel Ungewissheit anzeigen und nicht automatisch neu ausführen. Das Journal ist ein Vorschlag, kein vorhandenes Protokollfeature.

Approvalzustand muss mindestens an Backend-Generation, Thread, Turn, Item und tatsächlich offenen Upstream-Request gebunden werden. Die Generation ist eine vom späteren Hostdienst zu verwaltende Sicherheitsidentität, kein hier belegtes Codex-Feld. Bei unklarem Backendwechsel konservativ alte Freigaben entwerten. Nach demselben Server-Reconnect kann ein erneut zugestellter Request erneut angezeigt werden; derselbe numerische Requestwert nach Serverneustart beweist hingegen nichts. E1 zeigte sowohl erneute Zustellung als auch die Möglichkeit, von einem zweiten Client aus zu antworten. Parallelantworten müssen deshalb am Adapter genau einem noch offenen Vorgang zugeordnet und serialisiert werden.

`serverRequest/resolved` bedeutet nicht automatisch erfolgreiche Toolausführung; endgültigen Itemstatus und Ergebnis gesondert prüfen. Ein Approval kann beantwortet oder durch anderen Lifecycle-Fortschritt erledigt worden sein. Ein als `completed` beendeter Turn kann dennoch eine fehlgeschlagene Dateioperation beschreiben. [E1]

Beim Androidverlust bleibt der Hostbesitzer der Backendverbindung und des App-Server-Prozesses bestehen. Reconnect umfasst Authprüfung, Versionsprüfung, Resume/Subscription, Thread-/Turn-Snapshot und Historie; währenddessen eintreffende Events werden abgeglichen. Vollständige Endinhalte ersetzen lückenhafte Deltas. Der partielle Live-Text darf bis dahin als unvollständig markiert bleiben. Bei Backendverlust wird kein weiterlaufender Turn behauptet: E1 verlor im SIGTERM-/SIGKILL-Fall das offene Approval und rekonstruierte `interrupted`. Adapterverlust ist eine dritte Fehlerklasse und separat zu testen. [ADR 0002](../decisions/0002-state-reconciliation.md)

## Drei Architekturvarianten

### A: Android → gesicherter Tunnel → App Server

- **Grenzen/Oberfläche:** SSH oder entsprechend geschützter Netzwerkzugang; danach vollständige zugängliche App-Server-RPCs. OS-Benutzer begrenzt den Hostumfang, nicht die Methoden.
- **Credentials/Rechte:** Geräteschlüssel bzw. Transporttoken; Providerlogin auf Host; Backend benötigt Workspace-/State-/Toolchain-Rechte. SSH muss separat eingeschränkt sein.
- **Vorteile:** wenig eigener Hostcode; funktionaler Agentablauf durch E1 plausibel.
- **Nachteile/Risiken:** `fs/writeFile`, `process/spawn`, `thread/shellCommand` und Overrides bleiben Angriffspfade. Ein Tunnel löst ADR 0004 nicht. Selbst ein Container würde allgemeine Prozessausführung innerhalb dieser Umgebung weiterhin exponieren.
- **Fehlende Experimente:** effektive RPC-Autorisierung des konkreten Backends, Credentialwiderruf, Non-root-Isolation. Ohne neue belastbare Capability-Grenze ist A für die hier verlangten Abnahmekriterien **nicht ausreichend**; sie bleibt die Vergleichsbasis, keine zulässige Abkürzung.

### B: Android → gesicherter Transport → begrenzender Hostadapter → privater App Server

- **Grenzen/Oberfläche:** eigene fachliche Allowlist gemäß Matrix, private Backendverbindung, hartes Ausführungslimit für Agenttools. Geeignete UID-/Dateirechte-/Sandbox-/MAC-Kombination noch offen.
- **Credentials/Rechte:** gerätespezifische Transportidentität am Adapter; Provider-Credentials nur im Backendbereich. Adapter braucht nur Registry/Autorisierungsstate und Kanalzugang, nicht pauschal sämtliche Projektdateien.
- **Vorteile:** erlaubt minimale Oberfläche, unabhängigen Gerätewiderruf, hostseitigen Lifecycle und Requestzuordnung; kein allgemeiner Workspace-Daemon nötig.
- **Nachteile/Risiken:** eigener sicherheitskritischer Vermittlungscode; Parameter-, Event- und Approvalfilter können lückenhaft sein. Derselbe UID-/Dateibaum kann Credentials oder Backend-Socket für Agenttools erreichbar lassen.
- **Fehlende Experimente:** vollständiger negativer Capability-Test, indirekter Ausbruch über Agent/Approval, Secret-Lesegrenze, Non-root-Sandbox und Crashfenster. **Bevorzugter Gegenstand des nächsten Spikes**, keine Architekturfreigabe.

### C: Android → begrenzender Hostadapter → getrennte Workspace-Ausführungsumgebungen

- **Grenzen/Oberfläche:** gleiche kleine Remote-API wie B; zusätzlich pro Workspace oder Sicherheitsdomäne getrennte Backend-/State-/Mount-/Prozesskontexte, etwa durch Container/Namespaces oder getrennte OS-Identitäten.
- **Credentials/Rechte:** Gerätecredentials am Adapter; Modellzugang je Backend oder kontrolliert bereitgestellt. Keine pauschale Einbindung privater Homes oder eines privilegierten Host-Steuerungssockets in die Agentumgebung.
- **Vorteile:** kann Cross-Workspace-Schäden und gemeinsame Betriebsressourcen stärker begrenzen; klarere Vorbereitung auf unterschiedliche Eigentümer.
- **Nachteile/Risiken:** höherer Lifecycle-, Storage-, Credential- und Toolchain-Aufwand; gemeinsame Kernelgrenze; Non-root-/Namespace-Unterstützung und innere Codex-Sandbox nicht automatisch kompatibel.
- **Fehlende Experimente:** Minimalmounts, Token-Lesesicherheit, Persistenz bei Backendneustart, Isolation A/B, Betrieb ohne Root. Nur vertiefen, wenn B die erforderlichen Grenzen nicht nachweist oder mehrere unabhängige Sicherheitsdomänen verlangt werden.

## Prüffähige Abnahmekriterien für einen späteren Spike

Alle Kriterien sind **offen**, sofern nicht ausdrücklich ein begrenzter historischer Teilnachweis genannt ist. Tests verwenden Wegwerfprojekte A/B, Dummy-Secrets und Markerdateien; keine Angriffe auf reale Hostdaten. Nicht ausführbare Sicherheitstests sind BLOCKED, keine PASS-Ergebnisse.

| ID | Test / beobachtbares Erfolgskriterium |
| --- | --- |
| RT-01 | Mit gültigen Gerätecredentials sämtliche nicht erlaubten RPCs und unbekannte Methoden senden: definierte Ablehnung, keine Weiterleitung und keine Seiteneffekte. Besonders `process/*`, `command/exec*`, `thread/shellCommand`, FS-Mutationen, Config/Login/Remote-Control/Projektmutationen. |
| RT-02 | Erlaubte Start-/Resume-/Turn-Aufrufe um fremde IDs, `cwd`, `path`, `history`, Sandbox-/Permission-/Environment-/Tool-Overrides ergänzen: Ablehnung; keine Policyänderung, keine fremden Historien oder Events. |
| RT-03 | Gültiger Client versucht direkte und agentvermittelte Writes außerhalb A, einschließlich B, privater State-/Secret-/Socketpfade, absoluter Pfade, `..`, Symlinkwechsel und Hardlinks: Zielmarker unverändert; erlaubte Änderung in A gelingt. Nicht nur Fehlertexte, sondern Dateiergebnisse prüfen. |
| RT-04 | Beliebiger direkter Hostprozessstart scheitert; alternativ über Prompt, Buildskript und gefälschtes/echtes Approval angeforderter Prozess bleibt innerhalb ausdrücklich freigegebener Agent-Ausführung. Keine Außenmarker, kein privilegierter Host-/Backendzugang. |
| RT-05 | Dummy-Modellsecret und interne Steuerungscredentials sind weder über RPC/Historie noch durch Agentlesen, Toolumgebung oder erreichbare Kontrollsockets auslesbar. Keine echten Secrets als Testmarker verwenden. |
| RT-06 | Zwei Workspace-/Principal-Zuordnungen testen: fremde Thread-/Turn-/Item-/Approval-ID, Cursor und Subscription liefern keine Daten und keine Wirkung; eine bloße Änderung des Listenfilters gewährt keinen Zugriff. |
| RT-07 | Androidverbindung während aktiven Modell-Turns schließen: Backendprozess bleibt identisch, Turn wird ohne erneutes `turn/start` fortgesetzt oder abgeschlossen. E1 ist Teilnachweis; neuer Adapter-/Transportpfad bleibt zu prüfen. |
| RT-08 | Nach offline abgeschlossenem Turn Snapshot/Historie laden: korrekter Endstatus, vollständiger geprüfter Endtext und Datei-Items trotz fehlendem Endevent; keine doppelten Nachrichten. Laufende Textlücke sichtbar. Mehrseitige Historie gesondert prüfen. |
| RT-09 | Antwort auf `thread/start`/`turn/start` gezielt nach Backendannahme verlieren; gleiche Operation erneut einreichen und Adapter in kritischen Journalfenstern beenden: kein automatischer zweiter Thread/Turn; unentscheidbare Fälle sichtbar als unbekannt. |
| RT-10 | Approval vor/bei/nach Antwort trennen, zwei Clients gegensätzlich antworten lassen, bereits erledigte und unbekannte IDs senden: maximal eine wirksame Entscheidung pro offener Anfrage; keine veraltete Zustimmung für neue Vorgänge. Außenfreigaben bleiben auch bei gültigem `accept` gesperrt. |
| RT-11 | Backendverlust mit offenem Approval und separat Adapterverlust erzeugen: alte Freigaben ungültig, Zustand autoritativ neu lesen, keine erfundene Fortsetzung und keine automatische Wiederholung. Graceful Shutdown und erzwungener Verlust getrennt ausweisen. |
| RT-12 | Ein Gerät widerrufen: neue Verbindungen sofort abweisen, bestehende Sitzung innerhalb vorab festgelegter Frist sperren (Spike-Ziel höchstens 5 Sekunden); zweites Gerät bleibt gültig, Provider-Credentials unverändert, laufender Turn wird durch reinen Zugangswiderruf nicht beendet. |
| RT-13 | Falscher Hostkey/Zertifikat, fehlende/ungültige/abgelaufene Credentials und nicht authentifizierter Upgrade scheitern vor fachlichen Operationen. Bei SSH auch Shell/SFTP/Exec und unerlaubte Weiterleitungen negativ prüfen; bei TLS keine mutierenden 0-RTT-Anfragen. |
| RT-14 | Androidspeicher, Backups und redigierte Protokolle enthalten keine Modell-/OpenAI-Credentials; Remote-Identität und Modell-Identität sind unabhängig nachweisbar. |
| RT-15 | Gesamter Normalablauf einschließlich Agentänderung und Reconnect unter Non-root-UID ohne sudo/Host-Admin-Capabilities erfolgreich; notwendige Lese-/Schreib-/Netzressourcen vollständig inventarisiert. |
| RT-16 | Alle verfügbaren Methoden des gepinnten Schemas sind einer Allow-/Denyentscheidung zugeordnet; unbekannte Felder in Mutationen scheitern. Größen-/Raten-/Parallelitätsgrenzen verhindern unbegrenzte Warteschlangen; ein übergroßer Request erweitert keine Rechte. |

RT-03 erlaubt nur eng definierte dienstinterne Persistenz-/Runtime-Schreibvorgänge außerhalb A, deren Ziel der Host bestimmt. Sie dürfen nicht als frei adressierbare Android- oder Agent-Dateioperation zugänglich sein. RT-04 verbietet nicht jeden Linux-Prozess: ein Coding-Agent benötigt gegebenenfalls explizit zugelassene Tool-/Buildausführung **in der eingeschränkten Umgebung**. Der genaue Umfang dieser Agent-Capability ist vor dem Test festzuhalten; unsandboxierte freie Hostausführung ist nicht darunter zu verstecken.

## Genau empfohlener nächster technischer Spike

**Ein lokaler „Capability-Grenze unter kompromittiertem Client“-Spike für Variante B**, separat zu beauftragen; hier nicht begonnen. Auf einem vorbereiteten Wegwerf-Linux-Testhost mit funktionsfähiger Non-root-Sandbox einen minimalen Testadapter ausschließlich über privaten lokalen Kanal betreiben. Zwei bestehende Testworkspaces, zwei simulierte Geräteidentitäten und Dummy-Secrets verwenden. Keine Android-App, kein Terminaldienst und zunächst kein Remote-Listener erforderlich.

1. Binary/Schema pinnen. Bei 0.155.0 zuerst Legacy-/Paginationfähigkeit und `clientUserMessageId` prüfen; 0.154.0 bleibt historische Vergleichsevidenz. Backend-Login separat hostseitig vorbereiten.
2. Nur Katalog, Thread-/Turn-Kern, State/History, erlaubte Approvals und Ergebnisanzeige vermitteln. Negativtests RT-01–06 und Non-root-Test RT-15 zuerst: direkte **und indirekte** Umgehungspfade einschließen. Bei fehlender harter Grenze keine Remote-Freigabe.
3. Denselben begrenzten Aufbau für Antwortverlust, parallele Approvals, Android-/Adapter-/Backendverlust und simulierten Gerätewiderruf verwenden: RT-07–12 und RT-16. Kein Exactly-once-Vertrag aus einem einzelnen erfolgreichen Lauf ableiten.

Ergebnisartefakt: gepinnte Capability-/Parameter-Allowlist, effektive Rechte-/Pfadliste, redigierte Requests/Responses, Marker-/Prozessbeobachtungen und PASS/FAIL/BLOCKED je Kriterium. RT-13 am realen Transport und RT-14 am Android-Gerät bleiben ausdrücklich nachfolgende Voraussetzungen vor einer Remote-Android-Freigabe. Der lokale Spike entscheidet, ob B tragfähig ist oder C weiter untersucht werden muss; er entscheidet noch nicht SSH versus WSS/mTLS/VPN.
