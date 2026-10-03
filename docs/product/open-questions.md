# Offene Fragen

## Fortschreibung für den persönlichen Host, 3. Oktober 2026

Der ältere Fragekatalog unten bleibt mit seinem damaligen Versions-/Trustumfang
erhalten. [VPS-Inventar](../vps-inventory-2026-10-03.md),
[HOST_SETUP](../../HOST_SETUP.md) und ADR 0005/0006 konkretisieren den inzwischen
laufenden persönlichen Modus; keine allgemeine v0.1-/Mehrnutzerfreigabe.

| Frage | Persönlicher Betriebsstand / Rest |
| --- | --- |
| P3, S1 | Eigener Trusted Linux-Host, Single User; bevorzugt Pi 4 ARM64. Full Access/never/sudo sind ausdrückliche Betreiberentscheidung (Accepted ADR 0005). |
| C1/C2 | Historische VPS-Inventur vom 3. Oktober 2026: damaliger Dienst 0.160.0, stdio, keine `experimentalApi`-Capability; UserInput-Feature aktiv. Neuer Pi: zum Installationszeitpunkt aktuelle/unterstützte ARM64-Version bewusst auswählen, vollständiges Release samt passenden Helpern installieren; tatsächlichen Dienstpfad/Version protokollieren. ARM64-/Schemaabnahme noch offen. |
| C5/S4 | Host hält ChatGPT-Dateicredentials; Dienstkonto angemeldet. Frischer Pi-Device-Login noch praktisch zu testen; keine Accountcredentials in Android nötig. |
| R1/S2 | VPS: HTTPS/Caddy → Token-Adapter Loopback → stdio. Clienttoken in Keystore; Pi-Eingang soll privat sein. LAN-HTTP-Implementierung/Abnahme Proposed ADR 0006. |
| S3/S8 | Für diesen persönlichen Modus keine Workspace-/Secretisolation vor dem Trusted Agent gewünscht. CWD/Routen sind keine OS-Grenze. Restriktive frühere Spikekriterien bleiben für andere Trustmodelle offen. |
| A1/A3 | Kotlin/Compose, OkHttp JSON/Multipart/SSE, min 26/target 37 im Code. Kein SSH/WebSocket in der nativen API-Verbindung. LAN-NSC/URLpolicy und Android-17-Runtimeberechtigung fehlen. |
| S6/U3 | Gewöhnliche Shell-/Datei-/Netzwerk-Approvals nicht ans Tablet; fachliche Rückfragen separat. Drittanbieter-Sicherheitsmechanismen bleiben wirksam. |

Offen für Pi: reale ARM64-Tools/Helper, frischer Accountlogin, sudo/Netz-/Außenwrite,
Autostart/Reboot, Tablet-LAN-HTTP/HTTPS, Android-17-Permission, negative externe
Erreichbarkeit, SSE/History/Artefakte und repräsentative Dauerlast. Der folgende
historische Support-/Isolationskatalog wird dadurch nicht pauschal geschlossen.

Stand: 18. September 2026, nach Konsolidierung beider Linux-Spikes und Remote-Trust-Research. Laufzeitantworten gelten für den geprüften Umfang mit Codex 0.154.0; lokale Schema-/Hilfebefunde zu 0.155.0 sind getrennt dokumentiert. Der Produkt-Supportvertrag ist noch offen.

- **[V0]** zwingend vor v0.1 beantworten, sicherheitsrelevante Voraussetzungen vor dem entsprechenden verbundenen Prototyp. Eine bloße Annahme schließt die Frage nicht.
- **[PROTOTYP]** darf während eines begrenzten v0.1-Prototyps untersucht werden; Ergebnis vor einer verlässlichen Produktzusage erforderlich.
- **[SPÄTER]** bewusst verschoben; blockiert diesen Prototyp nicht.

## Durch die Spikes beantwortet

Evidenz: [A: Linux Protocol Spike](../experiments/2026-09-linux-protocol-spike/protocol-spike.md), [B: command/exec-Persistenz](../experiments/2026-09-command-exec-persistence/pty-persistence.md).

| Frage / ursprüngliche IDs | Antwort und Grenze | Beleg |
| --- | --- | --- |
| Direkter unabhängiger Client, CLI-Parsing/Fork erforderlich? | Strukturierter Client funktioniert ohne CLI-Textparsing und ohne Fork für den geprüften Umfang. | A, Protocol Findings |
| C1/C2: Existiert ein konkret getesteter API-Stand? | 0.154.0 mit Experimental-Opt-in ist geprüft. Supportpolitik und minimale reguläre API-Menge bleiben offen. | A, Environment |
| C3: Thread/Historie nach Clientverlust und Neustart? | Abgeschlossene Historie und Gesprächskontext wiederherstellbar. | A, Reconnect Findings |
| C3: Laufender Modell-Turn nach Clientverlust? | Gleiche Turn-ID, weiterlaufende Ausgabe und Abschluss beobachtet. Keine Aussage über beliebige Agenttools. | A, laufender Turn |
| C3/C4: Event-Replay und offene Datei-Approvals? | Kein vollständiger Replay; Endinhalte hydratisierbar. Offenes Datei-Approval nach Resume mit identischer Request-ID erneut beantwortbar. | A, Reconnect Findings |
| C3: Offenes Approval nach Serververlust? | Im SIGTERM-/SIGKILL-Test verloren, alter Turn `interrupted`. Kein Nachweis eines vollständig graceful Shutdowns. | A, offene Approvals |
| R1: FS, Watch und Git ohne eigenen Workspace-Daemon? | Für kleine geprüfte Fälle ja; Git über unsandboxiertes `process/spawn`. Remoteabsicherung bleibt offen. | A, Files / Diffs |
| R2: Ist command/exec eine persistente Terminalschicht? | Nein. Pipe und PTY samt OS-Prozess endeten bei Clientverlust; altes Handle unbrauchbar, Server blieb aktiv. | B, Test Matrix |
| S3: Begrenzt die Agent-Sandbox direkte Client-RPCs? | Nicht automatisch. FS-Schreiben außerhalb Thread-Workspace ohne Approval und unsandboxierte Hostprozesse beobachtet. OS-Rechte bleiben maßgeblich. | A, Security Findings |

C3 ist damit für diese Testfälle beantwortet und aus der offenen Liste entfernt. Restfälle erhalten präzisere IDs C8–C10. Architekturfolgen stehen in [ADRs 0001–0004](../decisions/README.md).

## Durch den Remote-Trust-Research eingegrenzt

[Trust-Modell](../research/remote-trust-model.md), [Capability-Matrix](../research/host-capabilities.md) und [Versions-/Quellenevidenz](../research/remote-trust-evidence.md) dokumentieren jetzt S1–S4/R1 auf Entwurfsebene. Kein Remotezugang oder Sicherheitsnachweis wurde dadurch eingerichtet. Für den vorgegebenen Kernablauf sind allgemeines Terminal, Host-Dateimanager, freie Prozess-/Dateimutationen und neue Host-Workspaces nicht erforderlich. Ein gesicherter direkter App-Server-Zugang begrenzt die RPC-Rechte nicht; auch erlaubte Thread-/Turn-Methoden benötigen Parameter- und Objektkontrollen. Variante B mit begrenzendem Adapter ist der nächste Untersuchungsgegenstand, keine akzeptierte Architektur.

## Product

- P1 [V0] Welcher einzelne Ablauf belegt den Nutzen: neue Aufgabe bis Diff-Review oder bestehende Arbeit überwachen? Nachweis: ein konkretes Nutzerszenario und beobachtbares Erfolgskriterium.
- P2 [V0] Genügen Code-/Diff-Lesen und Agent-Steuerung, oder sind manuelles Editieren und Git-Schreibaktionen erforderlich?
- P3 [V0] Wird ein selbst verwalteter Linux-Host vorausgesetzt? Welche Einrichtung ist der ersten Zielgruppe zumutbar?
- P4 [SPÄTER] Welche Nachfrage besteht jenseits des eigenen Einsatzes? Interviews vor einer breiten Produkt-Roadmap.
- P5 [SPÄTER] Werden mehrere Hosts, Teams, Container und gehostete Workspaces eigene Produktmodi?

## Android

- A1 [V0] Welches Referenztablet, welche Mindestversion und welcher Target-SDK-Stand werden getestet?
- A2 [PROTOTYP] Wie bleiben Navigation und Sessionidentität nach Rotation, Split-Screen und Prozessverlust erhalten?
- A3 [PROTOTYP] Welche SSH-/WebSocket-Bibliothek funktioniert auf dem Referenzgerät samt Kryptoprovider und Schlüsselzugriff? Dokumentation allein genügt nicht.
- A4 [V0] Welche Hintergrundzusage macht v0.1 tatsächlich? Aktive App, begrenztes Nachladen oder Push bei gesperrtem Display?
- A5 [SPÄTER] Ist ein DocumentsProvider für externe Apps nützlich, oder reicht Import/Export per SAF?
- A6 [SPÄTER] Welche lokale ARM64-Ausführung erfüllt Android-Verteilung, Energiegrenzen und sichere Prozessisolation?

## Codex Integration

- C1 [V0] Welche Codex-Version wird unterstützt, wie wird das Schema gepinnt und wie werden inkompatible Upgrades erkannt? 0.154.0 ist der Laufzeitreferenzstand; lokal ist 0.155.0 installiert. Aktuelle Webseite und lokale Typen widersprechen sich bei Pagination/Lesebeschränkungen; Funktionsnachweis pro Version nötig.
- C2 [V0] Funktioniert die jetzt dokumentierte minimale Capability-Menge samt Parameterbeschränkungen auf der gewählten Version? Der Schemaexport ohne Opt-in ist noch kein Laufzeitnachweis. Welche geprüfte Historienvariante trägt Reconciliation, und braucht sie Experimental-Felder?
- C4 [V0] Welche Retry-/Idempotenzsemantik gilt bei verlorenem `turn/start`-Acknowledgement und Verbindungsabbruch während einer Approvalantwort? Doppelte Turns, stale Request-IDs und doppelte Zustimmung ausschließen; Testfälle und sichere UI-Reaktion festhalten.
- C5 [V0] Welcher dokumentierte Login funktioniert auf einem headless Linux-Host mit Tablet-Browser und dem vorgesehenen Kontotyp?
- C6 [PROTOTYP] Welche minimale Provider-Abstraktion hält Codex-Typen aus der UI heraus, ohne erforderliche Fähigkeiten zu verstecken?
- C7 [SPÄTER] Wann lohnt ein zweiter Agent-Adapter? Erst mit einem konkreten Provider die Abstraktion verallgemeinern.
- C8 [PROTOTYP] Wie werden Snapshot und Live-Events bei wiederholten Disconnects zusammengeführt, ohne doppelte oder fehlende UI-Inhalte? Legacy- und paginierte IDs, Cursor und unvollständigen laufenden Text gezielt prüfen.
- C9 [PROTOTYP] Wie unterscheiden sich Netzfehler, Android-Prozessverlust und geordneter App-Server-Shutdown? Der bisherige Approval-Verlusttest erzwang nach zehn Sekunden SIGKILL.
- C10 [SPÄTER, sofern nicht im Workflow benötigt] Welche Lebensdauer haben echte Agent-Hintergrundterminals? Weder Spike A noch B beantwortet diese eigene Prozesskategorie.

## Remote Workspace

- R1 [V0] Welcher gesicherte Transport und welche minimale Host-Capability-Grenze tragen die vorhandenen App-Server-Funktionen? FS/Watch/Git verlangen für den geprüften Umfang keinen Zusatzdaemon; SSH-/WSS-Integration und Autorisierung sind nicht nachgewiesen.
- R2 [V0] Benötigt der kleinste Workflow überhaupt ein persistentes manuelles Terminal? Falls ja, muss eine separate Persistenzlösung mit Sessionbesitz, Puffer, Reattach, Beenden und Sicherheit untersucht werden. `command/exec` ist ausgeschlossen; tmux, PTY-Service und andere Sessionmodelle bleiben offene Optionen.
- R3 [V0] Was ist die Workspace-Identität: Host, Root, Git-Worktree und Agent-Session? Wie verhindert die UI Verwechslungen?
- R4 [PROTOTYP] Welche Dateigrößen, Repository-Größen und Netzbedingungen gehören zum ersten Nachweis? Messwerte vor endgültiger Transportentscheidung.
- R5 [PROTOTYP] Wie werden Git-Status, unversionierte Dateien, Index und Agent-Diffs auseinandergehalten und nach Reconnect aktualisiert?
- R6 [V0, falls Schreiben enthalten] Wie verhindern Versionsprüfung, atomare Writes und Konfliktanzeige verlorene Änderungen durch Agent und Nutzer?
- R7 [SPÄTER] Wie funktionieren unterbrochene große Transfers, Suchindex, Container-Lebensdauer und mehrere parallele Projekte?

## Security

- S1 [V0] Welches Vertrauensmodell gilt? Zunächst eigener Host/eigener Nutzer oder untrusted Repositories und andere Nutzer? Benötigte Isolation ausdrücklich benennen.
- S2 [V0] Wie werden Host-Key bzw. Zertifikat geprüft, Credentials gespeichert und bei Verlust widerrufen? Keine automatische Annahme geänderter Host-Identitäten.
- S3 [V0] Welche minimalen Host-Capabilities bekommt Android, und welche serverseitige Grenze erzwingt Workspace-Isolation auch für direkte FS-/Host-RPCs? Die unzureichende Agent-Sandbox-Grenze ist beantwortet; die konkrete Begrenzung ist offen.
- S4 [V0] Wo verbleiben OpenAI-Credentials, und welche Aktionen erfordern lokale Biometrie? Darf ein gesperrtes Gerät automatisch reconnecten?
- S5 [V0] Welche Inhalte dürfen Cache, Logs, Android-Backups und Sperrbildschirm enthalten?
- S6 [V0] Wie reagiert die UI auf unbekannte Approval-Arten, veraltete Requests und manipulierte Repository-/Tool-Inhalte? Untrusted Inhalt darf keine UI-Freigabe vortäuschen.
- S7 [SPÄTER] Welche zusätzlichen Isolationsebenen benötigen Container- und Mehrnutzerbetrieb?
- S8 [V0] Welche technische Grenze verhindert indirekten Hostzugriff über Agent-Prompts, Buildskripte, Approvalzustimmung und gespeicherte Thread-/Repositorykonfiguration? Methoden-Allowlist allein genügt nicht; Secretlesen und Zugriff auf interne Backend-Sockets ausdrücklich testen.
- S9 [V0] Wie beendet Gerätewiderruf bestehende Remote-Sitzungen ohne Providerrotation oder Agentabbruch, und wie werden Adapterverlust und Backend-Generation sicher erkannt? Request-IDs allein sind keine dauerhafte Approvalidentität.

## UX

- U1 [PROTOTYP] Welche Ansichten stehen nebeneinander, welche nacheinander? Einen Ablauf mit Touch und Hardwaretastatur prüfen.
- U2 [PROTOTYP] Wie unterscheiden sich „läuft“, „wartet auf dich“, „offline“, „Zustand unbekannt“, „fehlgeschlagen“ und „abgeschlossen“?
- U3 [PROTOTYP] Zeigt jede Freigabe Host, Workspace, Vorgang und Gültigkeitsumfang verständlich an?
- U4 [PROTOTYP] Reicht ein nativer Diff-/Codebetrachter für das erste Szenario? Falls Terminal nötig: Unicode, Resize, Ctrl-C, Fokus und IME nachweisen.
- U5 [PROTOTYP] Bleibt der Ablauf bei großer Schrift und mit Screenreader bedienbar?
- U6 [SPÄTER] Wie werden Drag & Drop, externe Dokumente, mehrere Fenster und Dateikonflikte später integriert?

## Open Source / Licensing

- L1 [V0] Welche Lizenz soll das eigene Projekt erhalten? Bis dahin keine Veröffentlichung als bereits fertig lizenziertes Produkt behaupten.
- L2 [V0] Welche konkreten Dateien/Libraries werden eingebettet oder mitverteilt, und welche Notice-, Quellcode- oder Austauschbarkeitsanforderungen entstehen daraus? Besonders Terminal-/Editor-Kandidaten prüfen.
- L3 [V0] Wird Codex separat installiert oder mitgeliefert? Davon hängen Update- und Weiterverteilungspflichten ab.
- L4 [SPÄTER, vor öffentlichem Namen] Ist „CodexPad“ als Produktname geeignet und rechtlich nutzbar? Keine implizite OpenAI-Zugehörigkeit behaupten.
- L5 [SPÄTER, vor Distribution] Welche Dienstbedingungen, App-Store-/Binärverteilungsregeln und Datenschutzanforderungen gelten für das dann konkrete Modell?

## Reihenfolge für den Wiedereinstieg

1. Den dokumentierten Kernworkflow für einen separat beauftragten lokalen Capability-Grenz-Spike verwenden: C1/C2, S3/S8 und Non-root-Betrieb zuerst, danach C4/S9 und Recovery. [Genauer Spikeumfang](../research/remote-trust-model.md#genau-empfohlener-nächster-technischer-spike).
2. P1–P3 und A4 produktseitig konkretisieren. R2 ist für den hier untersuchten Workflow nicht erforderlich; bei späterer Aufnahme eines manuellen Terminals separat untersuchen.
3. Vor verbundenem v0.1 tatsächlichen Transport/Remoteauth, Android-Credentialspeicherung, Login C5 und die übrigen V0-Voraussetzungen nachweisen. Der lokale Spike ersetzt diese Nachweise nicht.

S2 umfasst explizit Remote-Clientauthentifizierung, TLS/WSS bzw. gesicherten Tunnel, Identitätsprüfung, Rotation und Widerruf. Android-Bibliothekstests A3 dürfen im Prototyp erfolgen; die Sicherheitsgrenze darf dabei nicht stillschweigend entfallen. Für C8 muss ein Prototyp unbekannten oder unvollständigen Zustand sichtbar lassen, bis der Abgleich geprüft ist.

Ein Ergebnis erhält Datum, Quelle oder Experimentnachweis, betroffene Frage-ID und Konsequenz. Weitere Lösungsentscheidungen erst nach Nachweis als ADR akzeptieren. Noch kein v0.1-PRD.
