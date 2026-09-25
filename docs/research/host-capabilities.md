# Minimale Client-Capabilities und Hostrechte

Stand: 18. September 2026. Begleitdokument zum [Remote-Vertrauensmodell](remote-trust-model.md). Research und vorgeschlagene Beschränkungen, keine implementierte API. E1/E2 bezeichnen die archivierten 0.154.0-Spikes; L den [lokalen Schema-/Hilfebefund 0.155.0](remote-trust-evidence.md). Keine automatische Übertragung zwischen Versionen.

## Capability-Matrix

„Benötigt“ bezieht sich ausschließlich auf den beauftragten minimalen Workflow, nicht auf eine endgültige Produktdefinition. **Ja** bedeutet fachlich erforderlich; bei alternativen RPCs muss nur ein nachweislich funktionierender Weg gewählt werden. **Unklar** benennt zusätzlichen Bedarf oder ungeklärte Upstream-Eignung. „Direkt“ meint ungefilterten RPC-Zugriff auf einen App Server mit normalen Hostrechten. **Nein** schließt die Vermittlung einer begrenzten fachlichen Funktion nicht aus. Eine Methode im regulären Schema ist keine Sicherheitsfreigabe.

| Capability / RPC-Gruppe | Benötigt v0.1 | Zweck | Host-Risiko | Direkt exponierbar? | Einschränkung / Adaptervermittlung | Beleg |
| --- | --- | --- | --- | --- | --- | --- |
| Hostseitiger Workspace-Katalog und Auswahl | ja | bestehende freigegebene Projekte zeigen | Pfadoffenlegung, falsche Projektzuordnung | kein generischer Hostzugriff | feste Registry: Geräteprincipal → Workspace-ID → zugelassener Root/Backend; nur Anzeigen/Auswählen | Workflow; F |
| `project/list`, `project/read` | unklar als technische Quelle | Upstream-Projektmetadaten/Katalog | fremde Roots/Metadaten; keine belegte ACL | nein | in 0.155.0 experimentelles Schema; nur autorisierte Projektion; Registry bleibt Autorität | L, keine Laufzeitprüfung |
| `initialize`, `initialized` | ja intern | Protokoll-/Capability-Handshake | Opt-in erweitert Protokoll, keine Authentifizierung | nur nach Transportauth, im Entwurf hostseitig | feste Capabilities, Versionsprüfung, keine vom Gerät gewählte Experimental-Freischaltung | E1, L |
| `thread/list` | ja | vorhandene Threads finden | Übersicht anderer Projekte, History-Metadaten | nein | hostseitiger Filter plus Autorisierung jedes Resultats; Cursor/Page-Limits; fremde IDs weiterhin sperren | E1, L |
| `thread/read` | ja | Snapshot/Status, ggf. Legacy-Historie | vollständige private Gespräche und Toolinhalte | nein | gebundene Thread-ID; nur autorisierte Daten, begrenzte Historienmenge | E1, L |
| `thread/resume` | ja | vorhandenen Thread laden, Live-Events/Approvals wieder anbinden | Overrides, fremde Rollouts, restaurierte Tools/Konfiguration | nein | nur bekannte Thread-ID; Backend-/Workspace-Bindung und effektive Policy prüfen; `path`/`history` und freie Overrides ablehnen | E1, archiviertes Resume-Schema, L |
| `thread/start` | ja | neuen Thread in bestehendem Projekt anlegen | beliebiger cwd, Konfig-/Tool-/Berechtigungswechsel | nein | Host bestimmt Root und Policy; persistent; Textworkflow ohne dynamische Tools; zugewiesene Thread-ID speichern | E1, L |
| `turn/start` | ja | Nachricht senden / neuen Turn beginnen | Agent kann Code ausführen; Sandbox-/Provider-/Environment-Overrides | nein | nur autorisierte Thread-ID und begrenzter Textinput; feste Berechtigungen; Operationskorrelation; keine stillen Retries | E1, L |
| `turn/interrupt` | ja für kontrollierbares Stoppen | gezielt laufende Arbeit abbrechen | fremde Arbeit stoppen | nein | exakte Thread-/Turn-ID und Eigentümerschaft; Status danach neu lesen | bestehende Vision, E1-Schnittstelle, L; Laufzeittest offen |
| Thread-/Turn-/Item-Notifications | ja | Fortschritt, Streaming, Status, Fehler, Resultate | Datenabfluss über ungefilterte Events; untrusted Renderinhalt | nur autorisierte Projektion | `thread/status/changed`, `turn/started`, `turn/completed`, `item/started`, `item/completed`, `item/agentMessage/delta`; Fehler/Warnungen darstellen; Quoten/Backpressure | E1 |
| `thread/turns/list`, `thread/items/list`, `initialTurnsPage` | ja als Historienfähigkeit; konkrete Form unklar | abgeschlossenen Inhalt nach Reconnect laden | fremde Historien, große Antworten | nein | nur zugeordnete IDs, begrenzte Seiten; `itemsView` explizit; Versionskonflikt prüfen, Legacy-Alternative nur nach Nachweis | E1, L; Onlineabweichung |
| `item/commandExecution/requestApproval`, `item/fileChange/requestApproval` + Antworten | ja | erlaubte Agentaktionen beurteilen | unsandboxierte/erweiterte Aktion, dauerhaftes Grant, Antwort auf fremde Anfrage | nein | aktuelle Anfrage + Item binden; nur unter harter Betreibergrenze; einmalige Zustimmung/Ablehnung/Abbruch; keine Session-/Regelerweiterung | E1, L |
| `serverRequest/resolved` | ja | offenen Request aus UI entfernen/abgleichen | fälschliche Erfolgsmeldung | nur zugeordnete Events | als Auflösung, nicht als Beweis erfolgreicher Datei-/Toolaktion behandeln | E1 |
| Permission-Approvals (`item/permissions/requestApproval`) | unklar als benötigte Interaktion | Tool fordert zusätzliche Datei-/Netzrechte | erweitert Ausführung über geplante Grenze | nein | sicher erkennen; standardmäßig keine Zusatzrechte gewähren; nur geprüftes Antwortschema; kein Sessiongrant | L; nicht in E1 geprüft |
| `item/tool/requestUserInput` / Benutzerrückfragen | unklar für den gewählten Agentmodus | strukturierte Rückfrage beantworten | Inhalt/Requests können verwechselt werden; blockierter Turn | nein | erlaubte Formantwort, gebunden an aktuelle Anfrage; unbekannte Typen sichtbar blockieren/abbrechen; keine automatische Freigabe | L; kein eigener Spike |
| `fileChange`-/`commandExecution`-Items, ggf. `turn/diff/updated` | ja | Agentänderungen und relevante Ergebnisse zeigen | sensitive Pfade/Inhalte; Diff mit aktuellem Gitzustand verwechseln | nur autorisierte Projektion | Itemtyp/-status auswerten; abgeschlossene Historie maßgeblich; Turn-Diff nur ergänzend | E1; Turn-Diff dort nicht empfangen |
| Begrenztes Lesen betroffener Dateien, intern ggf. `fs/readFile`, `fs/getMetadata` | unklar | tatsächliches Dateiergebnis zusätzlich prüfen | beliebige Secret-Lektüre, Symlinks, große/binäre Daten | nein | nur bei belegter UX-Lücke hinzufügen; relatives Objekt unter Root, sichere Öffnung, Größen-/Dateitypgrenzen; kein Pfad-Passthrough | E1, L; F |
| `fs/readDirectory`, Suche, `fs/watch`, `fs/unwatch` | nein für diesen Kern | allgemeiner Dateibaum, Dateisuche, Live-Dateiansicht | Hostinventar, Datenabfluss, Watch-/Speicherlast | nein | spätere separate Lesecapability; Reconciliation statt Watch-Replay | E1, L |
| `fs/writeFile`, `fs/createDirectory`, `fs/remove`, `fs/copy` | nein | manuelles Schreiben/Dateimanagement | freie Writes/Löschen/Kopieren unter Hostrechten | nein | vollständig sperren; Agent-Patches sind ein anderer, begrenzter Ausführungspfad | E1, L |
| `command/exec` und Write/Resize/Terminate | nein | eigenständige Kommandos/PTY | frei wählbarer Befehl und Sandbox-/Envparameter | nein | kein Terminal voraussetzen; keine Ersatzroute für Gitansicht | E1, E2, L |
| `process/spawn`, `process/writeStdin`, `process/resizePty`, `process/kill` | nein | allgemeine Hostprozesse | ausdrücklich unsandboxierte Ausführung | nein | vollständig sperren; kein verstecktes „nur Git“-Command-Passthrough | E1, L |
| `thread/shellCommand` | nein | nutzerinitiierter Shellbefehl | laut Schnittstelle unsandboxiert trotz Threadbezug | nein | vollständig sperren | E1-Schemabefund, L |
| Git-Gesamtstatus/-Diff | nein für Agentänderungsanzeige; unklar bei erweitertem Review | Index/untracked/fremde Änderungen sehen | Shell, Git-Konfiguration, Hooks/Helper, Datenabfluss | nein | später eigene feste Leseoperation; keine beliebigen Argumente/Env/externen Diff-Helper; Agent-Diff nicht als Gesamtstatus ausgeben | E1; F |
| `thread/fork`, Review, Archivieren/Löschen, Rollback/Revert, Queue/Goals/Metadaten | nein | erweiterte Sessionverwaltung | zusätzliche Arbeit, Historyverlust/Manipulation | nein | zunächst sperren; keine Wildcard `thread/*` | E1-Schnittstelle, L |
| Accountstatus / `account/read` | unklar als UI-Komfort | Modellzugang als bereit/nicht bereit anzeigen | Account-/Personendaten, mögliche Refreshwirkung | nicht roh | Host prüft lokal; nur minimales Bereitschaftssignal, kein Token/Accountdump | bestehende Codex-Research; L |
| Accountlogin/-logout, Tokenbereitstellung, Remote-Control/Pairing, Config-/Plugin-/Skill-/MCP-/Environment-Mutationen | nein | Host-/Integrationsadministration | neue Ausführungspfade, Credentialwechsel, Persistenz, weitere Remotezugänge | nein | Betreiberaufgabe; Default-Deny auch für nach Upgrades neue Methoden | L |
| `model/list`, Permission-/Feature-/Configdiagnose | nein bei festem Modell/Profil | Auswahl und Diagnose | Metadaten, Offenlegung von Hostkonfiguration | nicht roh | falls erforderlich kuratierte Auswahl; Host setzt erlaubte Werte | L; F |
| Projekt-/Workspace-Anlage, Import, Verschieben/Löschen (`project/*` mutierend) | nein | neue Host-/Projektorganisation | zusätzliche Pfade/Threads/Metadaten und Seiteneffekte | nein | vorhandene vom Betreiber freigegebene Workspaces genügen; Projektobjektanlage nicht mit Verzeichnisanlage gleichsetzen | Workflow, L |

Die Kataloganzeige ist erforderlich, ein bestimmtes Upstream-Katalog-RPC nicht. Das Schema 0.155.0 kennt Projektobjekte mit Roots; deren Existenz belegt weder die Liste tatsächlich zugelassener Hostverzeichnisse noch eine Workspace-Sicherheitsgrenze. Der frühere Spike enthält keinen Projektkatalogtest. Ein kleiner vom Betreiber bereitgestellter Katalog vermeidet die Annahme, allgemeines Host-Browsing sei zum Start nötig.

Ergebnisanzeige kann im Kern auf Agent-Items und rekonstruierter Historie beruhen. Sie muss diese ausdrücklich als beobachtete Agentänderungen ausweisen. Die Beobachtung eines genehmigten Adds in E1 belegt noch keine korrekte Darstellung aller Updates, Deletes, Renames oder Binäränderungen. Benötigt das Produktszenario den **aktuellen vollständigen** Workspacezustand, wird daraus eine zusätzliche begrenzte Lesefunktion; daraus folgt kein allgemeines Terminal.

## Parametergrenzen sind Teil der Capability

Vorschlag: Android erhält fachliche Operationen mit Workspace-/Thread-IDs und Text, keinen JSON-RPC-Tunnel mit Methodenfilter. Der Host baut vollständige Upstream-Requests aus bekannten Feldern auf. Unbekannte Methoden und zusätzliche sicherheitsrelevante Eingabefelder werden abgelehnt; nicht durchgereicht. Der genaue Wire-Vertrag bleibt Aufgabe des Spikes.

| Eingabefamilie | Minimale Regel | Warum |
| --- | --- | --- |
| `cwd`, `runtimeWorkspaceRoots`, `projectId`, `environments` | aus Hostzuordnung ableiten; keine freie Clientwahl | sonst Wechsel von Root/Ausführungskontext |
| `sandbox`, `sandboxPolicy`, `permissions`, Approvalpolicy/-reviewer | Hostpolicy bestimmt Obergrenze, beim Resume erneut validieren | erlaubte Methode kann sonst Vollzugriff oder anderen Reviewer wählen |
| `config`, `baseInstructions`, `developerInstructions`, dynamische Tools/Capability-Roots, Collaboration-Overrides | nicht als Remote-Parameter anbieten | allgemeine Konfiguration kann neue Fähigkeiten/Provider/Tools aktivieren |
| Resume-`path`, `history` | nicht anbieten; nur autorisierte gespeicherte ID | archiviertes Schema beschreibt Vorrang vor der ID bei nicht laufendem Thread |
| `input` | zunächst Text; keine lokalen Bilder/Audio-/Skill-/Mentionpfade, beliebigen URLs oder Tooloutputs | weitere Varianten eröffnen zusätzliche Hostlese-/Netz-/Werkzeugpfade |
| Modell/Provider, Aufwand, Service-Tier | zunächst fest oder kleine Host-Allowlist | Credentialrouting, Kosten und Ausführungsprofil sind Hostentscheidung |
| IDs/Cursor/Page-Limits | Objektbindung prüfen, Größen begrenzen; opaque Cursor nicht als Autorisierung verwenden | Kenntnis einer ID reicht nicht; clientgewählte Listenfilter können entfallen |
| Approvaldecision/-scope | nur tatsächlich angebotene und hostseitig erlaubte Entscheidung | `acceptForSession`, Exec-/Network-Policyänderung und zusätzliche Permissions sind andere Capabilities |

Quellen: [archiviertes 0.154.0-Resume-Schema](../experiments/2026-09-linux-protocol-spike/schema/ThreadResumeParams.ts), lokales 0.155.0-Start-/Turn-/Resume-/Input-/Approval-Schema, dokumentiert in [L](remote-trust-evidence.md). Diese Tabelle ist eine daraus abgeleitete Einschränkung und behauptet keinen eingebauten CodexPad-Filter.

Auch ohne diese Parameter kann freier Text den Agenten zu schädlichen Operationen auffordern. Die sichere Eigenschaft muss deshalb aus tatsächlich erzwungenen Dateisystem-/Prozessrechten folgen. Approvalzustimmung ist keine Ausnahme von dieser Obergrenze. Bereits bestehende Threads dürfen erst übernommen werden, nachdem effektive Policy, Toolkonfiguration und Workspacebindung geprüft sind; ein passender `cwd` im Listenresultat reicht nicht.

## Hostressourcen und OS-Rechte

Diese Liste ist eine minimale Ressourcenklassifikation, keine vollständig gemessene Syscall-/Pfad-Allowlist. Exakte Laufzeitpfade hängen von Binary, Toolchain, Authbackend und Zielhost ab. Weder ein Dienstkonto noch Mounts wurden angelegt.

| Ressource | Für minimalen Betrieb benötigter Zugriff | Wer braucht ihn? | Was ist nicht daraus abzuleiten? |
| --- | --- | --- | --- |
| Codex-Binary, mitgelieferte Ressourcen/Sandboxhelfer, Laufzeitbibliotheken | lesen/ausführen | App Server und erforderliche Kindprozesse | Android/Agent darf Binary oder Startskripte nicht überschreiben; Updates sind Betreiberaufgabe |
| ausdrücklich gewählter Workspace | lesen, Agentänderungen schreiben; ggf. freigegebene Builds/Tests ausführen | eingeschränkte Agentumgebung | keine übrigen Projekte, kein privates Home, keine Rootrechte |
| Repository-Instruktionen und lokale Konfiguration | Inhalte lesen, effektive Konfigquellen kontrollieren | Codex | Repository darf keine Host-Autorisierungsquelle werden; schreibbare Hooks/Plugins/Config bei späterem Laden berücksichtigen |
| privates `CODEX_HOME` / Codex-State | Sessions/Rollouts, SQLite samt Nebendateien, notwendige Logs/Caches schreiben/lesen | Backend | kein allgemeiner Remote-Dateizugriff; Agent muss nicht alle diese Daten lesen oder verändern können |
| Modellcredential-Speicher | Providerlogin lesen, bei verwaltetem OAuth ggf. erneuern/persistieren | Codex/Authkomponente | kein Credentialbedarf des Android-Clients; `auth.json` und Keyring sind alternative Speicherwege, nicht beide zwingend |
| Adapter-Registry, Remote-Widerruf, Operationsjournal | intern lesen/schreiben entsprechend Rolle | Hostadapter/Betreiber | nicht über Agentdateioperationen erreichbar; keine vom Gerät frei adressierbaren Statepfade |
| private Runtime/TMP-/XDG-Verzeichnisse | Locks, temporäre Dateien, IPC | jeweilige Dienst-/Agentrolle | keine pauschale Schreibfreigabe für Host-`/tmp`; Umfang/Trennung im Spike messen |
| Unix-Socket oder stdio-Kanal zum App Server | nur zwischen autorisierten Hostkomponenten | Adapter/Backend | nicht im Agent-Workspace, nicht als Deskriptor an Tools vererben; kein weiterer frei erreichbarer Listener |
| Toolchain, Zertifikats-/DNS-/Systemlaufzeitdaten | erforderliche Systemdateien read-only, gewählte Programme ausführbar | Backend bzw. Sandbox | genaue Liste offen; keine universelle Annahme, nur der Workspace müsse lesbar sein |
| Netz zum Modellanbieter | ausgehende autorisierte Verbindungen | Backend | kein allgemeiner Netzzugang von Agenttools; keine OpenAI-Tokens als Tool-Environment |
| Paketregistries, Git-Remotes, SSH-Agent, Containerdaemon, zusätzliche MCP-Dienste | nicht für den beschriebenen Kern erforderlich | ggf. spätere explizite Erweiterung | vorhandene Entwicklercredentials und Hostdienste nicht automatisch vererben |
| Remote-Listener und Dienst-Lifecycle | nur gewählter Zugang, unabhängig vom Androidsocket | Hostadapter/Betreiber | keine privilegierten Ports, Systemdienstverwaltung oder Firewallrechte für die laufende Codex-Instanz erforderlich |

Belege für Codex-State, IPC und Toolverhalten: E1/E2 und [bestehende Codex-Recherche](codex-integration.md#authentifizierung-und-secrets). Die Trennung der benötigten Zugriffe zwischen Rollen ist ein Sicherheitsentwurf, kein bereits bestätigtes Deployment.

Normale Unix-Rechte oder ACLs können den Zugriff des Dienstes auf bestehende Projekte gewähren; sämtliche zusätzlichen Gruppenrechte gehören in den Nachweis. Ein Dienstkonto, das alle Projekte lesen/schreiben darf, hat noch keine harte Trennung zwischen seinen Turns. Eine getrennte Identität pro Sicherheitsdomäne oder zusätzliche Ausführungsisolation bleibt zu prüfen. Die Kernelmechanismen und Grenzen stehen im [Trust-Modell](remote-trust-model.md#workspace-isolation-geeignete-mechanismen-und-grenzen).

### Minimalität und verbleibende Voraussetzungen

- **Erforderliche Capability:** Agent-Ausführung innerhalb der vom Betreiber festgelegten Grenze. Ob der erste konkrete Ablauf Shell-/Buildtools benötigt und welche Ressourcen diese erhalten, muss vor dem Spike explizit sein; freie Hostausführung ist dafür nicht erforderlich.
- **Komfort/später:** manuelles Terminal, Dateimanager, Git-Schreiboperationen, Paketinstallation, neue Workspaces, Providerlogin vom Tablet, Deployment und Hostupdates.
- **Betrieb ohne Root:** gewünschte Eigenschaft, noch kein experimenteller Nachweis dieser Research. Der Schemaexport als root testet keine Sandbox. Der Non-root-Spike muss normale Dateiänderung, Toolausführung soweit freigegeben, Credentialsicherheit und Recovery zusammen nachweisen.
- **Single User:** eigene Provideridentität kann hostseitig bereitgestellt sein; Geräte bleiben getrennt widerrufbar. **Multi User:** Zuordnung und Isolation auch für Histories, Approvals, Logs, Providerkonten und Quoten; gemeinsamer `CODEX_HOME` darf nicht stillschweigend mehrere fremde Sicherheitsdomänen vereinen.
