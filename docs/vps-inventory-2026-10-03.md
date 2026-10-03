# VPS-Referenzinstallation: lesende Bestandsaufnahme

Stand: 3. Oktober 2026, ca. 11:45–12:10 Europe/Berlin. Dies ist ein
Zeitpunktbefund, keine Migration und kein Last-/ARM64-Nachweis.
Kanonischer zukünftiger Aufbau: [HOST_SETUP.md](../HOST_SETUP.md).

Versionsklarstellung: Alle Versions-/Pfadangaben hier beschreiben ausschließlich
die Momentaufnahme vom 3. Oktober 2026. Der damalige CodexPad-Dienst verwendete
0.160.0. Der aufgefundene globale 0.156.1-Altbestand war ungenutzt im
CodexPad-Betrieb und gehörte nicht zum Dienst; 0.156.1 läuft heute nirgendwo mehr
und ist keine aktuelle, unterstützte oder zu installierende CodexPad-Version.
Auch 0.160.0 ist keine automatische Sollversion für neue Pi-Hosts. Dort die zum
Installationszeitpunkt bewusst ausgewählte aktuelle/unterstützte ARM64-Version
als vollständiges Release samt passenden Helpern installieren und abnehmen.

## Herkunft und Abweichungen

`/root/codexpad` ist eine ältere Dokumentations-/Serverkopie ohne `.git` und ohne
Android. `/root/codexpad-live` steht auf `0bff16b`; dort liegt zusätzlich eine
unversionierte frühere Compact-Verifikation. Der neuere Git-Checkout
`/root/codexpad-update-1186b7c9` steht auf
`1186b7c9e1d3ba464168814a4a434e7266b8a15a`. Dieser Commit ist auch der Inhalt von
`/opt/codexpad/RELEASE_COMMIT` und die Basis dieser Dokumentationsarbeit.
Dokumentationsbranch: `docs/raspberry-pi-host`, separater Worktree
`/root/codexpad-host-docs`. Kein vorhandener Checkout wurde zurückgesetzt.

**Der Release-Marker beschreibt den deployed Code nicht vollständig.**
`/opt/codexpad/server/codexpad_server.py` enthält zusätzlich den authentifizierten
GET `/account/rate-limits` → `account/rateLimits/read`, Broadcast der
`account/updated`-/`account/rateLimits/updated`-Events an alle SSE-Subscriber und
eine threadunabhängige Overflow-Notification. Die öffentliche Codedifferenz ist
als [Referenzpatch](reference-vps/2026-10-03-adapter.patch) archiviert. Sie wird
hier nicht auf dem VPS oder im Produktcode angewendet. Für exakte Reproduktion
kann sie auf einem frischen Host in einer Staging-Kopie angewendet werden.
Der Stand der tatsächlich auf dem Tablet installierten APK wurde mangels
angeschlossenem Tablet nicht neu erhoben; Androidbefunde stammen aus dem Repository.

| Artefakt | SHA-256 zum Aufnahmezeitpunkt |
| --- | --- |
| deployed Adapter | `e1adbfcf4b4982d4cc3219deffc1424fc69a9fd5c6f42a2da604b7f8edf5406e` |
| Adapter aus Basiscommit | `14d16a07e06793e7f0b078d6f08beb78a9124663f9cb09d5bb2623849ad2e067` |
| tatsächlich verwendetes Codex-Binary | `12eb3e81114588aca3b7998f4f19e8997b056aca08e57a7ca7c8a3ec8c652aad` |
| dessen `codex-code-mode-host` | `37cab1584302611e9936902219640ab5e7a79fcfccd2504c6e85ea8cb97d0e10` |

## OS, Konten und Runtime

Ubuntu **24.04.5 LTS**, Kernel **6.8.0-139-generic**, CPU/Userspace **x86_64**.
Python **3.12.3**, Caddy **2.11.4**, Node **22.23.2**.
Rund 3,8 GiB RAM und 511 MiB Swap; Root-Dateisystem 49 GiB, 92 % belegt,
ca. 4 GiB frei. Dies ist ein beobachteter Speicherengpass, keine Pi-Mindestgröße.

Adapter und sein Codex laufen als `codexpad`, UID 995, primäre GID 985,
keine Zusatzgruppen; Home `/var/lib/codexpad`, Login-Shell `/usr/sbin/nologin`.
`/etc/sudoers.d/codexpad` (root:root, 0440) enthält
`codexpad ALL=(ALL:ALL) NOPASSWD: ALL`. Damit ist das Konto absichtlich
administrationsfähig. Caddy läuft als `caddy`. Die rootseitige Codex-CLI und
der rootseitige verwaltete Codex-Daemon sind separate Arbeitswerkzeuge, keine
Komponenten des CodexPad-Dienstes. Root ist zusätzlich in Gruppe `docker`.

Die CodexPad-Unit hat `UMask=0077`, `PrivateTmp=true`, `KillMode=control-group`,
`Restart=on-failure`, fünf Sekunden Restartverzögerung und 15 Sekunden Stoplimit.
`ProtectSystem=no`, `ProtectHome=no`, `NoNewPrivileges=no`; es gibt keine
systemd-Schreibsperre für System-/Home-Dateien. `PrivateTmp` vermittelt einen
eigenen `/tmp`-Namensraum, keine Codex-Sandbox. Hostadministration ist per sudo
möglich; `/tmp`-Dateien anderer Dienste sind nicht automatisch dieselben Dateien.

## Codex-Installation, Konfiguration und Authentifizierung

Der **damals inventarisierte Dienst verwendete 0.160.0**, vollständiges Standalone-Release:

```text
/opt/codexpad/codex/current
  -> releases/0.160.0-x86_64-unknown-linux-musl
     bin/codex
     bin/codex-code-mode-host
     codex-package.json, codex-resources/, codex-path/
```

`/proc/<App-Server-PID>/exe` bestätigt dieses Binary. Beide Binaries sind statische
64-bit-x86-64-ELFs. Das Drop-in
`/etc/systemd/system/codexpad.service.d/codex-path.conf` setzt
`PATH=/opt/codexpad/codex/current/bin:/usr/local/bin:/usr/bin:/bin`.
Als **historischer, für CodexPad ungenutzter globaler Altbestand** zeigte
`/usr/local/bin/codex` zum Inventurzeitpunkt dagegen auf
`/usr/local/lib/codex/0.156.1-x86_64-unknown-linux-musl/bin/codex`.
Der laufende CodexPad-Dienst verwendete dieses Binary ausdrücklich nicht.
Ein bloßes `codex --version` in einer Administratorshell war deshalb kein
Dienstversionsnachweis. Aus diesem historischen Fund folgt kein aktueller Betrieb.

Effektives Home: `/var/lib/codexpad/.codex`; `CODEX_HOME` ist beim Dienst **nicht
gesetzt**, der Default folgt aus `HOME=/var/lib/codexpad`.
`config.toml` enthält nur projektbezogene Trust-Einträge, keine ausgewählten
Profile, kein fest vorgegebenes Modell und keine Full-Access-Schlüssel.
Die effektive Policy kommt aus dem Adapter:

```sh
codex app-server --stdio \
  -c 'sandbox_mode="danger-full-access"' \
  -c 'approval_policy="never"' \
  -c 'features.default_mode_request_user_input=true'
```

`initialize` sendet `clientInfo`, **kein** `experimentalApi:true`, anschließend
`initialized`. `thread/start` setzt `approvalPolicy=never` und
`sandbox=danger-full-access`; `turn/start` setzt `approvalPolicy=never` und
`sandboxPolicy.type=dangerFullAccess`, auch nach Resume bestehender Threads.
Normale Shell-/Datei-/Netzwerk-Approvals werden nicht ans Tablet vermittelt.
Nur `item/tool/requestUserInput` wird als fachliche Rückfrage verwaltet;
andere Serverrequests erhalten `Unsupported server request`. Drittanbieter-
Authentifizierung und externe Regeln sind damit nicht aufgehoben.

Vorhandene Authentifizierung: ChatGPT, dateibasierter Store
`/var/lib/codexpad/.codex/auth.json` (codexpad:codexpad, 0600), kein vorhandener
API-Key-Wert. `codex login status` meldete „Logged in using ChatGPT“; der Aufruf
mit dem historischen globalen Altbinary meldete bei einer einmaligen
Inventurdiagnose zusätzlich einen stale-temp-Cleanup-Warnhinweis; dies war
kein Dienstbetrieb mit diesem Binary. Kein Login/Logout oder Credentialwechsel ausgeführt. Die Datei
wurde ausschließlich strukturell geprüft, keine Credentialwerte übernommen.
Ein neues Konto muss auf dem späteren Host unter **dem Dienstbenutzer** angemeldet
werden. `/root/.codex/config.toml`/`auth.json` gehören zum getrennten Root-Account;
dessen Config enthält Modell-, Reasoning-, Projekt- und TUI-Einstellungen.
`/etc/codex/managed_config.toml` und `/etc/codex/requirements.toml` sind nicht vorhanden.

Dienstvariablen: `HOME`, `PATH`, `PYTHONUNBUFFERED`, `CODEXPAD_WORKSPACE_ROOT`,
`CODEXPAD_PORT`, `CODEXPAD_ACCESS_TOKEN`. Systemd vererbt außerdem `LANG`, `USER`,
`LOGNAME`, `INVOCATION_ID`, `JOURNAL_STREAM`, `STATE_DIRECTORY`, `SYSTEMD_EXEC_PID`
und `MEMORY_PRESSURE_WATCH`/`MEMORY_PRESSURE_WRITE`.
`CODEXPAD_ACCESS_TOKEN` fehlt nachweislich in der Startumgebung des Codex-Kindes;
der Adapter entfernt es vor `Popen`. Keine `OPENAI_API_KEY`- oder Proxyvariable
in dieser Prozessumgebung gefunden. Das ist keine Secretisolation gegenüber dem
Trusted Agent mit sudo.

## Tatsächliche Kommunikationskette

```text
Android (Kotlin/Compose, OkHttp; konfigurierbare URL + Token)
     | HTTPS:443, JSON / Multipart / Dateidownload / SSE
     v
Caddy: pad.feichti.dev (TLS-Terminierung)
     | HTTP:127.0.0.1:8765, Authorization unverändert, SSE flush_interval=-1
     v
Python CodexPad-Adapter (systemd codexpad.service)
     | bidirektionales JSONL über private stdin/stdout-Pipes
     v
Codex App Server 0.160.0 (Kindprozess desselben Dienstes)
     | lokale Tools / Helper / Workspaces / optionale MCP-Plugins
     +--> OpenAI (ausgehende verschlüsselte Modell-/Account-Verbindung)
```

| Verbindung / Richtung | Protokoll, Port | Authentifizierung / Verschlüsselung | Zuständigkeit |
| --- | --- | --- | --- |
| Android → Caddy, Antworten/Events zurück | HTTPS TCP 443; JSON-GET/POST, Multipart, SSE, Download | Zertifikatsprüfung durch OkHttp; persönlicher Bearer auf fachlichen Routen | Android-Verbindungseinstellungen, Caddy TLS |
| Caddy → Adapter, Stream zurück | HTTP TCP 127.0.0.1:8765 | derselbe Bearer, lokal unverschlüsselt | Python prüft Token; Caddy puffert SSE nicht |
| Adapter ↔ App Server | JSON-RPC-artiges JSONL, stdio, kein Port | private Prozesspipes, OS-Konto; kein eigener Netzwerk-Authhandshake | Adapter startet/initialisiert/steuert das Kind |
| Codex → Tools/Workspaces | Prozessaufrufe/Dateisystem; kein fester Port | Dienstkonto, bei Administration sudo; Full Access | Codex plus lokale Tools |
| Codex → OpenAI | ausgehend TLS, typischerweise TCP 443 | hostseitiger ChatGPT-Login; konkrete einzelne Providerverbindungen nicht paketweise mitgeschnitten | Codex verwaltet Modellzugriff/Tokenrefresh |
| Codex → optionales Browserless-MCP | lokale Node-stdio-Prozesse; weitere externe Requests pluginabhängig | Drittanbieter-Credentials getrennt, nicht inventarisiert als Werte | optionale Codex-Plugininstallation |

Kein Android-WebSocket, kein direktes App-Server-TCP und kein SSH in der nativen
API-Verbindung. Caddy bietet zusätzlich HTTP:80 für Redirect/ACME sowie UDP:443
für HTTP/3; OkHttp 4.12.0 verwendet hier HTTP über TCP/TLS, kein eigener QUIC-Client.
Die zulässige Android-Standardadresse ist ein Default, keine feste Bindung.

## Dienste, Listener und Lifecycle

Systemunits `codexpad.service` und `caddy.service` sind aktiv und enabled.
Adapter-WorkingDirectory `/opt/codexpad`, Start
`/usr/bin/python3 -B /opt/codexpad/server/codexpad_server.py`.
SIGTERM durchläuft die Adapterbereinigung: Kind terminieren, bis fünf Sekunden
warten, danach ggf. kill. Die systemd-Controlgroup erfasst auch Helper/Plugins.
Das Kind wird bei eigenem Ausfall **nicht automatisch neu gestartet**;
`/health` wird 503, während der Adapter weiterlaufen kann. Dann muss der Dienst
gezielt neu gestartet werden. `Restart=on-failure` überwacht den Hauptprozess.
Androidverlust beendet das langlebige Adapterkind nicht; SSE-Snapshots/History
rekonstruieren Zustand. Keine Terminal-Persistenztechnologie eingerichtet (ADR 0003).

Listener: Adapter **127.0.0.1:8765**, Caddy-Admin **127.0.0.1:2019**,
Caddy öffentlich **TCP 80/443, UDP 443**, SSH **IPv4/IPv6 TCP 22**.
Daneben eine rootseitige Codex-CLI auf einem kurzlebigen Loopback-Port und der
Root-Codex-Daemon auf Unix-Sockets; sie sind nicht Teil des Androidpfads.
Mehrere Browserless-Node-Kinder unter `codexpad` sind sichtbar.

Docker/containerd laufen, `docker ps` zeigt keine laufenden Container; CodexPad
verwendet kein Compose. tmux/screen sind installiert, keine Prozesse und keine
Root-Sessions gefunden. Tailscale und Nginx sind nicht im PATH bzw. als aktive
Units vorhanden. Root-user-services zeigen keinen CodexPad-Dienst; es gibt
keinen aktiven User-Manager für das nologin-Dienstkonto. System-Cron, cron.d,
Spool und Timer enthalten keinen erkannten CodexPad-Autostart/Job. Keine
CodexPad-Startdatei in init.d/profile.d gefunden; `/root/.bashrc` enthält
Codex-Bezüge, ist aber nicht am systemd-Start beteiligt.

`ufw` ist nicht installiert. Lesendes `nft list ruleset` zeigte Docker-NAT/
Forwarding-Regeln, keine allgemeine INPUT-Firewall für CodexPad. Eine
Provider-Firewall/Routergrenze konnte lokal nicht geprüft werden. **TLS + Bearer
sind derzeit die Grenze des öffentlich erreichbaren Caddy-Endpunkts**; Loopback
schützt nur das Backend. Die Pi-Vorgabe ist ein privater Eingang ohne öffentliche
Portweiterleitung, siehe ADR 0005/0006. Keine Firewallregel verändert.

## Betriebsdateien und State

| Pfad | Funktion / Berechtigungen |
| --- | --- |
| `/opt/codexpad/server/` | deployed Python-Code, Smoke-/Prüfdateien und ältere Backupkopien; Adapter root:root 0644, Eltern 0755 |
| `/opt/codexpad/deploy/` | deployed Install-/Unit-/Proxyvorlagen; tatsächliche Units in `/etc` sind maßgeblich |
| `/opt/codexpad/android/` | vorhandene Android-Artefakte, für Hostruntime nicht erforderlich |
| `/opt/codexpad/RELEASE_COMMIT` | Basismarker, oben beschriebene Drift beachten |
| `/opt/codexpad/codex/` | vollständiges Release + `current`-Symlink |
| `/etc/systemd/system/codexpad.service` und `.service.d/codex-path.conf` | Autostart und tatsächlich verwendetes Codex-PATH |
| `/etc/codexpad/server.env` | root:root 0600, Eltern 0700; Token, Workspace-Root, Port; niemals committen |
| `/etc/sudoers.d/codexpad` | bewusster passwortloser Host-Adminzugriff |
| `/etc/caddy/Caddyfile` | TLS-Site, Reverseproxy, 23-MB-Bodylimit, keine Accesslogs |
| `/var/lib/caddy/` | Caddy-State/Zertifikate; caddy:caddy 0750; nicht ins Repository |
| `/var/lib/codexpad/` | codexpad:codexpad 0700, Home/Service-State |
| `/var/lib/codexpad/.codex/` | derzeit 0775, durch 0700-Home nach außen geschützt; Zielvorgabe 0700 |
| `.codex/config.toml`, `auth.json`, `AGENTS.md` | Konfiguration, Credentials, persönliche Anweisungen; Inhalte nicht kopiert |
| `.codex/sessions/`, ggf. `archived_sessions/` | Rollouts, absolute Workspace-CWDs; Adapter liest nur Ownershipheader ergänzend |
| `.codex/state_5.sqlite`, `thread_history_1.sqlite`, `queue_1.sqlite`, `goals_1.sqlite`, `memories_1.sqlite`, `logs_2.sqlite` mit vorhandenen WAL/SHM | Codex-eigener State; keine eigene CodexPad-Datenbank, keine manuelle Formatmigration |
| `.codex/log/`, `tmp/`, `cache/`, `shell_snapshots/`, `thread-writer-locks/`, `models_cache.json`, `installation_id` | Codex-Logs/Runtime/Cache, versionsabhängig |
| `.codex/plugins/`, `skills/` | persönliche Erweiterungen; Browserless sichtbar, separat neu einzurichten |
| `/var/lib/codexpad/uploads/` | private dauerhafte Bildanhänge, 0700, Verlauf referenziert absolute Pfade |
| `/srv/codexpad/workspaces/` | 0700; fünf direkte Projektordner, Inhalte/Namen hier nicht kopiert |
| `codexpad-results/` im jeweiligen Workspace | turngebundene Ergebnisdateien, keine separate Datenbank |
| systemd-Journal | Adapterstart/Codex-stderr; keine HTTP-Request-/Header-/Bodylogs |

## Verifikation und bewusste Grenzen

Lesend: OS/Konten, Prozessbaum, tatsächlicher Binarypfad, ELF/Version/Hilfe,
Unit/Drop-in/Dateimodi, Listener/Unix-Sockets, Container-/Cron-/Timerinventar,
redigierte Environment-/Configstruktur, Codevergleich und Hashes.
Lokal `GET /health` → 200 `ok`, `GET /workspaces` ohne Token → 401;
mit geschütztem Hosttoken `GET /workspaces` → 200 (fünf Einträge),
`GET /models` → 200 (acht Modelle). Keine Projekttexte/Modellkataloge ausgegeben.
Das sind Funktionsproben der bestehenden API, kein neuer Modellturn.

Kein Dienstrestart, Paketinstall/-update, Loginwechsel, Firewalleingriff,
neuer Workspace/Thread/Turn, Disconnect-/Shutdownexperiment oder Migration.
Keine neue Tablet-/öffentliche HTTPS-Abnahme, kein Kernel-/Pi-Lasttest.
Historische Testberichte mit 0.154/0.155/0.156.1 werden nicht in einen vollständigen
0.160.0-Nachweis umgedeutet. ARM64-Abnahme und weitere Grenzen stehen in HOST_SETUP.

Dokumentationsverifikation im separaten Worktree: 37 vorhandene Python-Tests
bestanden auf x86_64 mit isolierten HTTP-/Backend-Fixtures; ebenso 37 Tests in
einer temporären Kopie mit angewendetem Referenzpatch. Der Patch erzeugt exakt
den oben genannten deployed Adapterhash. Der erste temporäre Testlauf hatte
die Android-Vertragsfixture nicht mitkopiert und scheiterte beim Import; nach
Vervollständigung der isolierten Kopie bestand der vollständige Lauf.
17 Bash-Blöcke samt eingebetteten Python-Heredocs und die beispielhafte Android-
XML wurden syntaktisch geprüft, neue lokale Dokumentationslinks geprüft und
`git diff --check` bestanden. Geänderte Dateien enthalten keinen Treffer der
bekannten Hosttoken-/Accountcredentials. Die Setupbefehle selbst wurden nicht
auf dem VPS ausgeführt. Abschlusskontrolle: derselbe Adapter-PID 577456,
active/running, unveränderter deployed Codehash und weiterhin lokales Health `ok`.
