# Evidenzprotokoll: Remote Trust Research

Datum: 18. September 2026. Beschränkt auf Dokumentationslektüre, lesende Versions-/Hilfebefehle, lokalen Schemaexport als Research-Artefakt und den vorhandenen Offline-Evidenzprüfer. Kein App Server gestartet, kein Login/Modellturn ausgeführt, keine Netzwerkverbindung zu einem Codex-Host eingerichtet, keine Installation, Git-Aktion oder System-/Dienständerung. Öffentliche Quellen wurden per HTTPS gelesen. Temporäre Schemaexporte lagen ausschließlich unter `/root/codexpad` und wurden nach Auswertung entfernt; dieses Protokoll hält Befunde und relevante Hashes fest.

## Gelesener Projektbestand

Vollständig gelesen: `README.md`, `VISION.md`, `docs/product/open-questions.md`, alle bestehenden Markdown-Dateien unter `docs/research/`, alle vier ADRs und Entscheidungsindex, Experimentindex, beide Experiment-READMEs und beide vollständigen Berichte. Keine weiteren Projekt-Markdown-/Text-/RST-/AsciiDoc-Dateien oder `AGENTS.md` wurden bei der Dateisuche gefunden. Die im Experimentindex erwähnten früheren Runtime-/Rohdaten sind nicht Bestandteil des heutigen Archivs.

Zusätzlich gelesen: die acht archivierten TypeScript-Schemaausschnitte, `environment.json`, `test-config.toml` und der Offline-Prüfer. Ausgeführt wurde ausschließlich:

```text
python3 -B docs/experiments/2026-09-linux-protocol-spike/verify-evidence.py
PASS: 16 archived evidence checks; original client/verification hashes and 8 schema excerpts match.
```

Das bestätigt Konsistenz der archivierten Befunde/Hashes, keine neue Ausführung der historischen Testfälle. Für Spike B fehlen weiterhin Rohdaten. Bestehende ADRs und Experimentartefakte wurden nicht geändert.

## Lokaler Stand

| Beobachtung | Ergebnis |
| --- | --- |
| `codex --version` | `codex-cli 0.155.0` |
| Aufrufpfad | `/root/.local/bin/codex` |
| aufgelöstes Binary | `/root/.codex/packages/standalone/releases/0.155.0-x86_64-unknown-linux-musl/bin/codex` |
| SHA-256 | `660e159a49e823ac8e5986cb238f73158ce4b957d40d9292f8de90862644b501` |
| lokale Hilfe | `codex app-server --help`, `generate-ts --help`, `generate-json-schema --help` |
| Schemaexport | `codex app-server generate-ts --experimental --out <Researchverzeichnis>` und ohne `--experimental` in dessen Unterverzeichnis `stable` |
| historischer Laufzeitstand | beide Spikes 0.154.0; abweichendes Binary, keine Übertragung der Laufzeitergebnisse auf 0.155.0 |

`<Researchverzeichnis>` war das mit `mktemp -d` innerhalb des Projekts angelegte `.remote-trust-schema-PBNC9z`. Der Export erzeugt Schnittstellendokumentation, startet keinen Listener und ist keine CodexPad-Implementierung. Keine generierten Bindings werden als SDK ins Projekt übernommen.

Die lokale App-Server-Hilfe nennt `stdio://`, `unix://`, `unix://PATH`, `ws://IP:PORT` und `off` als Listenerwerte. Sie nennt `--ws-auth capability-token` beziehungsweise `signed-bearer-token` sowie Tokenfile/Hash, Shared-Secret-Datei, Issuer, Audience und Clock-Skew-Optionen. Das beweist Flags, nicht funktionsfähige Remoteauth, TLS-Terminierung, Gerätewiderruf oder Scopes. Ein `wss://`-Clientziel aus der Online-Dokumentation ist insbesondere kein Nachweis eines nativen TLS-Listeners des lokalen Binaries.

### Relevante Typbefunde

Die folgenden Angaben sind Auszüge als Feldinventar, kein vollständiges Schema:

- `ThreadStartParams`: regulär u. a. `cwd`, `approvalPolicy`, `approvalsReviewer`, `sandbox`, `config`, `modelProvider`, Instruktionsfelder, `ephemeral`; experimenteller Export zusätzlich u. a. `runtimeWorkspaceRoots`, `permissions`, `historyMode`, `projectId`, `environments`, `dynamicTools`, `selectedCapabilityRoots`.
- `ThreadResumeParams`: experimentell `path`, `history`, Root-/Permissions-/Config-Overrides und `initialTurnsPage`. Dokumentierte Typsemantik: Bei nicht laufendem Thread haben History und nichtleerer Rolloutpfad Vorrang vor Thread-ID; bei aktivem Thread wird ein Pfad als Konsistenzprüfung behandelt. Der archivierte 0.154.0-Typ enthält dieselbe sicherheitsrelevante Vorrangbeschreibung.
- `TurnStartParams`: `threadId`, `input`, `clientUserMessageId`, optional `toolOutput`; Overrides u. a. für `cwd`, Sandbox, Approvalreviewer/-policy, Modell/Providerkontext über Threadkonfiguration; experimentell zusätzlich Roots, Permissions, Environments, Zusatzkontext und Collaboration-Einstellungen. Nicht alle Thread-Felder sind Turn-Felder; keine freie Durchleitung beider Typen.
- `UserInput`: Text und weitere Varianten, darunter `image`, `localImage`, `audio`, `localAudio`, `skill`, `mention`. Lokale Varianten und Skill/Mention enthalten `path`. Text enthält `text_elements`; ein späterer Adapter muss auch strukturierte Textmetadaten begrenzen.
- `CommandExecutionApprovalDecision`: `accept`, `acceptForSession`, Execpolicy-/Networkpolicy-Amendments, `decline`, `cancel`. Dateiapproval: `accept`, `acceptForSession`, `decline`, `cancel`. `PermissionsRequestApprovalResponse` enthält Berechtigungen und Scope. Daraus folgt keine Erlaubnis, diese Varianten vollständig remote anzubieten.
- `SandboxPolicy`: lokale Varianten `dangerFullAccess`, `readOnly` mit Netzwerkfeld, `externalSandbox`, `workspaceWrite` mit Schreibroots/Netzwerk/TMP-Ausnahmen. In diesem exportierten Typ fehlen die online beschriebenen Felder `access` und `readOnlyAccess`.
- `ThreadListParams.cwd` ist ein optionaler exakter Filter; der Typ enthält auch `projectId`. Ein Filter ist keine ACL. `useStateDbOnly` kann einen Scan-/Repair-Pfad vermeiden; „Leserpc“ ist daher eine fachliche Kategorie, keine Garantie null interner Writes.

Im Export **ohne** Experimental-Opt-in enthalten: `initialize`, `thread/start`, `/resume`, `/list`, `/read`, `/turns/list`, `/items/list`, `turn/start`, `turn/interrupt`, FS-Methoden und `command/exec` samt Steuerung. `project/*` und `process/*` fehlen dort und sind im experimentellen Export enthalten. Die Feldmengen unterscheiden sich. Dies ist eine Schemafilter-Beobachtung, keine Funktionsprobe ohne Opt-in.

### Idempotenz und Projektkatalog

`TurnStartParams` und `TurnSteerParams` enthalten optional `clientUserMessageId`, aber keinen erläuterten dauerhaften Retry-/Deduplizierungsvertrag. `TurnSteerParams.expectedTurnId` ist ausdrücklich eine Vorbedingung auf den aktiven Turn. `ThreadStartParams` enthält keinen ausgewiesenen Idempotenzschlüssel. `project/create`, `project/import` und eine Account-Credit-Operation haben dagegen `idempotencyKey`-Felder. Ihr Vorhandensein wird nicht auf andere Methoden übertragen. Die Backend-Verarbeitung und Persistenz von `clientUserMessageId` wurden nicht umfassend untersucht; Idempotenz bleibt offen.

Experimentelle Projekt-RPCs: `project/list`, `/read`, `/create`, `/import`, `/update`, `/move`, `/delete`. `ProjectListParams` enthält Cursor, Limit und Sortierung. `ProjectReadParams` enthält `projectId`; `Project` besitzt ID, Name, Roots, Metadaten und Zeit-/Sortierinformationen; ein Root ist ein absoluter Pfad. Keine dieser Typangaben belegt Dateisystemisolation, autorisierte Workspaceauswahl, Host-Verzeichnisanlage oder funktionierende Laufzeitunterstützung.

### Hashes ausgewählter lokaler Exporte

Pfade relativ zum jeweiligen Schemaexport. Hashes erlauben bei gleichem Binary einen späteren Vergleich; die vollständigen Schemaexporte sind nicht archiviert.

| Export / Datei | SHA-256 |
| --- | --- |
| experimentell: `ClientRequest.ts` | `38c6718f0f5aada55187788547df6756189035c6e23d6dd6f2d5b0f5d6828416` |
| regulär: `ClientRequest.ts` | `17daaf8ded077e5afb9dede2a31cf6f392243a9316666f0a7dc26269dd39fc8c` |
| experimentell: `v2/ThreadStartParams.ts` | `7a3fddbb0cf0585c52edbf19e3a1f6e691681f18ab509f7abfa416da7f0ac824` |
| experimentell: `v2/ThreadResumeParams.ts` | `e4f64c88205dbba2bf87635d12b9c5803dd6f19b84b5682a71df7fdcf87862fb` |
| experimentell: `v2/TurnStartParams.ts` | `85713b9158fa110ac20b63e0ec6f76faff8872402d62531f14172cfc5b1eacf7` |
| experimentell: `v2/SandboxPolicy.ts` | `f5a4b24c0de4945e77c1573306ae4cb98a29103a6c64ee23264dc9d35a977e85` |
| experimentell: `v2/ProjectListParams.ts` | `cba737c63a3edb6876e05ab3a9d9ce40e1a113c0fb570d8769ef91896efc3e50` |
| experimentell: `v2/Project.ts` | `9d63a6da9ac0ae06003e5ee3bee9285208cb78c3ee0873020b7571f5697d35db` |
| experimentell: `v2/UserInput.ts` | `20bb8914250b88e4831123fed101a7ea2336efab4c2b13fd6336651ff0c65808` |

## Dokumentationsabweichungen

Die am Recherchetag gelesene [App-Server-Seite](https://learn.chatgpt.com/docs/app-server) leitet sich weiterhin aus der bisherigen Entwickler-URL ab. Gelesen wurden insbesondere Protocol, Lifecycle/API overview, Start/resume, Turns/Sandbox read access und Approvals. Ihre relevanten Abweichungen sind keine Widerlegung der archivierten Experimente:

| Thema | 0.154.0-Evidenz | lokales 0.155.0-Schema | aktuelle Webseite | Konsequenz |
| --- | --- | --- | --- | --- |
| paginierte Historie | E1 erfolgreich, kleine Testfälle | Typen/Methoden weiter vorhanden; Historienmethoden auch regulär exportiert | paginierte Erstellung und bestimmte Folgezustände als nicht unterstützt beschrieben | versionsgebundener Funktionsnachweis nötig; keine Regression behaupten |
| eingeschränkter Lesezugriff | kein Secret-Isolationsnachweis | `SandboxPolicy` ohne `access`/`readOnlyAccess` | beschreibt diese Felder | keine Feldunterstützung oder Vertraulichkeitsgrenze annehmen |
| Projektkatalog | kein Test/kein archivierter Katalogvertrag | experimentelle Projekt-RPCs | gelesener API-Überblick reicht nicht als Betriebsvertrag | optionale Datenquelle; Registry/ACL separat |
| Default-/Experimental-Reife | E1 mit Opt-in | unterschiedliche Methoden- und Feldmengen | Historienmethoden als experimentell bezeichnet | Exportfilter und Runtimegating gesondert prüfen |

Ergänzend wurden die offiziellen getaggten [TurnStart-Typen 0.154.0](https://github.com/openai/codex/blob/rust-v0.154.0/codex-rs/app-server-protocol/schema/typescript/v2/TurnStartParams.ts) und [0.155.0](https://github.com/openai/codex/blob/rust-v0.155.0/codex-rs/app-server-protocol/schema/typescript/v2/TurnStartParams.ts) sowie Start/Resume unter 0.155.0 gelesen. Die dort eingecheckten regulären Typen sind schmaler als der lokale experimentelle Export; fehlende experimentelle Felder sind kein Versionsdelta-Beweis. Der Versuch, eine vermutete Upstream-Processor-Datei abzurufen, ergab HTTP 404 und lieferte keine Evidenz für Deduplizierung.

## Externe Primärquellen und Grenzen

Die Mechanismusquellen sind direkt an den jeweiligen Aussagen in [Trust-Modell](remote-trust-model.md) verlinkt: Linux-man-pages zu Pfadauflösung, ACLs, Namespaces und `openat2`; Kernel-Dokumentation zu Landlock und `no_new_privs`; OpenSSH-Handbücher; TLS 1.3; JSON-RPC; Android Keystore/VPN und WireGuard-Protokoll. Keine Sekundärquellen waren erforderlich. Dokumentierte Plattformfähigkeit ist kein Android-Gerätetest oder Nachweis einer CodexPad-Konfiguration.

Die allgemeine aktuelle Codex-Security-Seite behandelt inzwischen das Security-Produkt und verweist für Sandbox/Approvals auf [Agent approvals & security](https://learn.chatgpt.com/docs/agent-approvals-security); dort wurden die Abschnitte zu Sandbox/Approval und Netzwerk gelesen. Eine zunächst versuchte allgemeine Authentifizierungs-URL war nicht abrufbar; Credentialaussagen stützen sich auf vorhandene Projektquellen und App-Server-Accountvertrag, nicht auf diesen fehlgeschlagenen Abruf.

Der Skill OpenAI Docs beeinflusste Quellenwahl und die Trennung aktueller offizieller Dokumentation von lokalen Versionsbefunden. Die vom Auftrag priorisierten Projekt-/Schnittstellenquellen und zusätzliche Protokoll-/Kernelprimärquellen bleiben maßgeblich. Kein Skill führte zu Änderungen außerhalb der Forschungsdokumentation.
