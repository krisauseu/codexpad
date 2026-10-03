# CodexPad

**CodexPad** ist ein nativer Android-Client für einen eigenen Codex-Host.
Codex läuft auf einem Linux-Rechner im eigenen Netzwerk oder auf einem VPS.
Android steuert Threads, Turns, Modelle, Dateien, Rückfragen und Artefakte;
der eigentliche Codex-Prozess, die Tools und Workspaces laufen auf dem Host.
Der CodexPad Python-Adapter steuert dafür einen unveränderten **Codex App Server**.

> Das Projekt befindet sich in aktiver Entwicklung und ist auf persönlichen Single-User-Betrieb ausgelegt.

## Praktisch getestete Umgebungen

- **Linux-VPS:** ursprüngliche Referenzinstallation mit Android, HTTPS, Caddy
  und Codex App Server. [Deployment](docs/deployment.md).
- **Raspberry Pi 4 mit Ubuntu 26.04.1 LTS, ARM64/AArch64:** vollständige
  End-to-End-Abnahme am 3. Oktober 2026 mit einem **HONOR YLE-W09**, Android 16,
  API 36. **Android → privates LAN → Pi → CodexPad → Codex funktioniert praktisch.**

Auf dem Pi bestätigt: vollständiges ARM64-Release samt Helpern und Ressourcen,
SHA-256-Abgleich, ChatGPT-Login als Dienstbenutzer `codexpad`, Modellturn,
Full Access, passwortloses sudo bis UID 0, systemd, lokaler Caddy,
Authentifizierung, SSE, Reconnect, vollständige History, `requestUserInput`
mit Auswahl/Freitext und Datei-/Upload-/Download-/Artefaktworkflow.
Der Backendlistener bleibt auf `127.0.0.1:8765`; der getestete private
LAN-Eingang war `http://172.16.16.39:8876`.

**Codex 0.160.0** gehört zu dieser datierten Pi-Abnahme und ist keine feste
Sollversion für spätere Installationen.
[Host- und Mac-Abnahme](docs/verification-pi-host-2026-10-03.md),
[Android-/Trusted-LAN-Prüfbericht](android/VERIFICATION-TRUSTED-LAN.md).

## Schnellstart auf einem eigenen Linux-Host

1. Frisches 64-Bit-Linux bereitstellen (Python mindestens 3.12).
2. SSH-Zugang und passwortloses sudo für den administrierenden Benutzer einrichten.
3. Codex installieren und mit ChatGPT/Codex anmelden; Netzwerkzugang sicherstellen.
4. Dieses Repository klonen.
5. Codex [HOST_SETUP.md](HOST_SETUP.md) lesen und auf dem frischen Host ausführen lassen;
   den [Pi-Abnahmeplan](docs/pi-host-testplan.md) zur Prüfung verwenden.
6. Die Android-App mit dem Host verbinden, Token eingeben, Verbindung testen und speichern.

Die ausführliche Host-Anleitung macht die Einrichtung reproduzierbar und prüfbar.
Ein Agent wie Codex kann sie weitgehend automatisch umsetzen. In der praktischen
Abnahme konnte Codex auf einem vorbereiteten, weitgehend frischen Raspberry Pi
die dokumentierte Einrichtung innerhalb von **rund fünf Minuten** durchführen.
Das ist eine beobachtete Dauer unter diesen Voraussetzungen, keine garantierte
Installationszeit. Ein Nutzer muss die ausführliche Anleitung nicht Schritt für
Schritt manuell abarbeiten.

Mit 64-Bit-Linux, SSH, sudo, installiertem Codex beziehungsweise einer
Installationsmöglichkeit, Anmeldung und Netzwerkzugang konnte Codex selbstständig
Pakete installieren, das Repository klonen, den Dienstbenutzer anlegen, Codex
für den Dienst vorbereiten, systemd und Caddy konfigurieren, das Token erzeugen,
Dienste starten und Tests ausführen. [HOST_SETUP.md](HOST_SETUP.md) bleibt die
kanonische technische Anleitung.

## Verbindung und Trusted LAN HTTP

**HTTPS bleibt Standard.** Die Option **Privates LAN über HTTP erlauben** ist
standardmäßig aus. Sie verlangt ausdrückliche Zustimmung und akzeptiert nur
RFC1918-IPv4-Adressen (`10/8`, `172.16/12`, `192.168/16`). Vertrauen gilt für
Schema, Host und Port; ein URL-Wechsel setzt es zurück. Öffentliches HTTP,
HTTP über DNS-Namen und IPv6-HTTP bleiben für den regulären LAN-Zugang gesperrt.
Die lokalen Debug-Ausnahmen sind in der [Android-Anleitung](android/README.md#verbindungseinstellungen) beschrieben.

**Über LAN-HTTP werden Bearer-Token und Inhalte unverschlüsselt übertragen.**
Diese Option ist für das ausdrücklich vertraute private Netzwerk gedacht.
Android-17-/API-37-LAN-Permission samt Runtimeprüfung ist implementiert;
der echte Systemdialog wurde mangels API-37-Gerät noch nicht praktisch getestet.
[Verhalten, Tests und Tablet-Abnahme](android/VERIFICATION-TRUSTED-LAN.md).

Der persönliche Trusted Host verwendet `sandbox_mode="danger-full-access"`,
`approval_policy="never"` und passwortloses sudo. Gewöhnliche Codex-Approvals
gehen nicht ans Tablet; fachliche Rückfragen bleiben bedienbar. Netzwerkgrenze
und Token schützen den Steuerungszugang separat.
[ADR 0005](docs/decisions/0005-personal-trusted-host.md),
[ADR 0006](docs/decisions/0006-private-host-transport.md).

## Aktueller Stand

Aktuelle Version: [CodexPad v0.2.0](https://github.com/krisauseu/codexpad/releases/tag/v0.2.0),
mit installierbarer `CodexPad-0.2.0.apk` als Release-Asset.

Der Kernworkflow ist auf dem Linux-VPS über HTTPS und auf dem Raspberry Pi
über direktes privates LAN mit echtem Android-Tablet bestätigt.

Der native Kotlin-/Jetpack-Compose-Client unterstützt unter anderem:

- Remote-Workspaces
- Thread-Liste und neue Threads
- vollständige Gesprächshistorie
- Streaming von laufenden Antworten über SSE
- Reconnect und serverbasierten Zustandsabgleich
- Starten und gezieltes Stoppen laufender Turns
- Modellwahl und modellabhängige Reasoning-Efforts
- Anzeige der Kontextnutzung
- Darstellung von Agent- und Tool-Aktivitäten
- Command-, MCP-, Dynamic-Tool- und File-Change-Karten
- Bild- und Dateianhänge an neue Nachrichten
- Ergebnisdateien wie PDF, Bilder, Text und Markdown
- authentifiziertes Öffnen und Herunterladen erzeugter Artefakte
- sichere Speicherung der Server-Zugangsdaten über Android Keystore
- direkten HTTPS-Zugriff und ausdrücklich freigegebenes privates LAN-HTTP
- fachliche Rückfragen mit Auswahl und Freitext (`requestUserInput`)

Die datierten Prüfberichte dokumentieren den getesteten Umfang auf VPS und Raspberry Pi; historische Spikes behalten ihre damaligen Versionsgrenzen.

## Architektur

```text
┌───────────────────────────┐
│      Android-Tablet       │
│                           │
│  CodexPad                 │
│  Kotlin + Jetpack Compose │
└─────────────┬─────────────┘
              │
              │ HTTPS / Trusted LAN HTTP + Bearer Token
              │ REST + SSE
              ▼
┌───────────────────────────┐
│      CodexPad Server      │
│          Python           │
│                           │
│  Workspace-/Thread-API    │
│  State Reconciliation     │
│  Artifact-Zugriff         │
└─────────────┬─────────────┘
              │
              │ strukturierte RPCs
              ▼
┌───────────────────────────┐
│     Codex App Server      │
│                           │
│ Threads · Turns · Tools   │
│ Models · History · Events │
└─────────────┬─────────────┘
              │
              ▼
┌───────────────────────────┐
│      Linux Workspace      │
│                           │
│ Git · Dateien · Toolchain │
│ Build · Tests · Commands  │
└───────────────────────────┘
```

Der Codex App Server ist die primäre Agent-Schnittstelle. Codex selbst wird dafür nicht verändert oder geforkt.

Live-Events werden für die unmittelbare Darstellung verwendet. Nach Verbindungsabbrüchen verlässt sich CodexPad jedoch nicht auf vollständiges Event-Replay, sondern gleicht den Client erneut mit dem autoritativen Serverzustand und der Historie ab.

## Remote-Betrieb

Die ursprüngliche VPS-Referenzinstallation verwendet:

```text
Android
   │
   ▼
https://pad.feichti.dev
   │
   ▼
Caddy
   │
   ▼
CodexPad Python Server
   │
   ▼
codex app-server --stdio
   │
   ▼
Remote Workspaces
```

Der öffentliche HTTPS-Endpunkt ist über ein persönliches Bearer-Token geschützt. Das Token wird auf Android verschlüsselt mit einem Schlüssel aus dem Android Keystore gespeichert.

Codex-Accountauthentifizierung und CodexPad-Clientauthentifizierung sind voneinander getrennt.

Details:

- [HTTPS-Deployment mit systemd und Caddy](docs/deployment.md)
- [Android-Verbindung und Credential-Speicherung](android/README.md)

## Android-App

Die Android-App befindet sich unter [`android/`](android/).

Technischer Stand:

- Kotlin
- Jetpack Compose
- native Android-Oberfläche
- minSdk 26
- compile/targetSdk 37
- HTTP/SSE über OkHttp
- Android Keystore für das Zugriffstoken
- Storage Access Framework für Ergebnisdateien
- keine WebView
- keine lokale Datenbank erforderlich

Build:

```sh
cd /pfad/zu/codexpad
android/build-local.sh
```

Die Debug-APK befindet sich anschließend unter:

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

Weitere Informationen:

[Android-App: Build, API, Reconnect und Tablet-Test](android/README.md)

## Was CodexPad bewusst noch nicht ist

CodexPad ist derzeit **keine vollständige Tablet-IDE**.

Insbesondere gibt es aktuell noch keinen:

- integrierten Codeeditor
- vollständigen Dateimanager
- manuellen Terminal-Client
- Git-Client
- Diff-/Review-Workflow auf IDE-Niveau
- Approval-UI für sämtliche Codex-Fähigkeiten
- Multi-User- oder Multi-Tenant-Betrieb
- Push-/Hintergrundbetrieb für abgeschlossene Agent-Aufgaben
- lokalen Codex-Betrieb direkt auf Android

Diese Funktionen sind nicht grundsätzlich ausgeschlossen. Der aktuelle Schwerpunkt liegt jedoch auf einem guten **agent-first Workflow** statt auf der Nachbildung einer Desktop-IDE auf einem Tablet.

## Projektidee

Das Tablet soll nicht selbst der Entwicklungsrechner sein.

Stattdessen bleiben Repository, Toolchain und Rechenleistung auf einem Linux-System. Dadurch kann CodexPad auch mit großen Projekten, bestehenden Entwicklungsumgebungen oder leistungsfähigeren Hosts arbeiten, ohne diese Umgebung auf Android replizieren zu müssen.

Die Oberfläche konzentriert sich auf den eigentlichen Arbeitsablauf:

```text
Workspace auswählen
        ↓
Thread öffnen oder erstellen
        ↓
Aufgabe formulieren
        ↓
Agent arbeitet auf dem Remote-Host
        ↓
Fortschritt und Tools beobachten
        ↓
Ergebnisse und Artefakte prüfen
        ↓
weiterarbeiten
```

Langfristig soll diese Architektur nicht zwingend auf Codex beschränkt bleiben. Weitere Agent-Provider oder lokale Ausführungsvarianten sind denkbar, sobald dafür ein konkreter Anwendungsfall besteht.

## Sicherheit und Vertrauensmodell

Der Codex App Server stellt mächtige Host-Fähigkeiten bereit. Eine Agent-Sandbox allein ist deshalb **keine Sicherheitsgrenze für einen direkt verbundenen Client**.

CodexPad behandelt Serverzugriff, Agent-Authentifizierung und Host-/Workspace-Berechtigungen daher als getrennte Sicherheitsgrenzen.

Der aktuelle Aufbau ist ausdrücklich ein persönliches Single-User-System auf einem kontrollierten Host. Multi-User-, fremde Repository- und Hosting-Szenarien benötigen zusätzliche Isolation und Autorisierung.

Siehe:

- [ADR 0004: Client/Host Trust Boundary](docs/decisions/0004-client-host-trust-boundary.md)
- [Remote Trust Model](docs/research/remote-trust-model.md)
- [Client Capabilities und Hostrechte](docs/research/host-capabilities.md)

## Entscheidungen und technische Forschung

CodexPad entstand aus mehreren gezielten Linux- und Android-Spikes. Die Ergebnisse bleiben dokumentiert, auch wenn Teile der ursprünglichen offenen Fragen inzwischen durch den laufenden Prototyp beantwortet wurden.

Wichtige Architekturentscheidungen:

- [ADR 0001 – Codex App Server als primäre Agent-Schnittstelle](docs/decisions/0001-app-server-interface.md)
- [ADR 0002 – Reconciliation statt Event-Replay](docs/decisions/0002-state-reconciliation.md)
- [ADR 0003 – Terminal-Lifecycle getrennt vom Agent-Lifecycle](docs/decisions/0003-terminal-lifecycle.md)
- [ADR 0004 – App-Server-Client als Host-Trust-Boundary](docs/decisions/0004-client-host-trust-boundary.md)

Weitere Hintergrunddokumentation:

- [Vision](VISION.md)
- [Interaktive Rückfragen: Vertrag und Tablet-Nachweis](docs/verification-user-input.md)
- [Codex-Integration](docs/research/codex-integration.md)
- [Android-Plattform](docs/research/android-platform.md)
- [Remote-Workspaces](docs/research/remote-workspaces.md)
- [Offene Fragen](docs/product/open-questions.md)
- [Archivierte Experimente](docs/experiments/README.md)
- [Server-Vertical-Slice](docs/experiments/2026-09-server-vertical-slice/README.md)

## Repository

```text
android/        nativer Android-Client
server/         CodexPad HTTP-/SSE-Server
deploy/         systemd-/Caddy- und Deployment-Dateien
docs/
  decisions/    Architecture Decision Records
  experiments/  technische Spikes und Nachweise
  product/      Produktfragen und Grenzen
  research/     Hintergrundrecherche
VISION.md       langfristiges Zielbild
```

## Status

**Stand: 3. Oktober 2026**

VPS- und Pi-End-to-End-Betrieb sind praktisch bestätigt. Der Pi wurde mit
Ubuntu 26.04.1 LTS ARM64 und Codex 0.160.0 abgenommen; diese Version ist ein
datierter Nachweis. Bei neuen Hosts die gewählte Version samt vollständigem
Release und Helpern prüfen und den tatsächlichen Dienstbinary-Pfad protokollieren.
Historische Build-/Tablet-/Spike-Nachweise behalten ihre jeweilige Versionsgrenze.

Offen, ohne den aktuellen Betrieb zu blockieren: echter Android-17-/API-37-
Permissiondialog, DHCP-Reservierung, externe Router-/IPv6-Erreichbarkeitsprüfung,
Reboot, Last-/Dauerlast sowie Backup/Restore/VPS-Migration. IPv6-HTTP bleibt
bewusst gesperrt; LAN-HTTP bleibt unverschlüsselt.

Die nächsten Entwicklungsschritte betreffen den Entwicklungsworkflow und die
Tablet-UX. CodexPad bleibt ein experimentelles Projekt.
