# CodexPad

**CodexPad** ist ein experimenteller nativer Android-Client für agentisches Softwareentwickeln auf einem entfernten Linux-System.

Die App verbindet sich mit einem persönlichen CodexPad-Server, der einen unveränderten **Codex App Server** steuert. Projekte, Toolchain und Agent laufen auf dem Remote-Host; das Android-Tablet dient als native Arbeitsoberfläche für Unterhaltungen, laufende Agent-Aufgaben, Modellsteuerung und Ergebnisse.

> Das Projekt befindet sich in aktiver Entwicklung und ist derzeit auf einen persönlichen Single-User-Betrieb ausgelegt.

## Aktueller Stand

CodexPad läuft inzwischen als durchgängiger Android → HTTPS → Linux → Codex-Workflow auf einem persönlichen VPS.

Der native Kotlin-/Jetpack-Compose-Client unterstützt unter anderem:

- Remote-Workspaces
- Thread-Liste und neue Threads
- vollständige Gesprächshistorie
- Streaming von laufenden Antworten über SSE
- Reconnect und serverbasierten Zustandsabgleich
- Starten und gezieltes Stoppen laufender Turns
- Modellwahl und modellabhängige Reasoning-Efforts
- Anzeige der Kontextnutzung
- manuelle Kontextkompaktierung
- Darstellung von Agent- und Tool-Aktivitäten
- Command-, MCP-, Dynamic-Tool- und File-Change-Karten
- Bild- und Dateianhänge an neue Nachrichten
- Ergebnisdateien wie PDF, Bilder, Text und Markdown
- authentifiziertes Öffnen und Herunterladen erzeugter Artefakte
- sichere Speicherung der Server-Zugangsdaten über Android Keystore
- direkten HTTPS-Zugriff auf einen persönlichen Remote-Server

Der aktuelle Prototyp wurde auf einem echten Android-Tablet sowie gegen einen persönlichen VPS mit Codex App Server getestet.

## Architektur

```text
┌───────────────────────────┐
│      Android-Tablet       │
│                           │
│  CodexPad                 │
│  Kotlin + Jetpack Compose │
└─────────────┬─────────────┘
              │
              │ HTTPS + Bearer Token
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

Der derzeit getestete persönliche Aufbau verwendet:

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

**Stand: 26. September 2026**

Der Kernworkflow läuft auf einem persönlichen Android-Tablet gegen einen echten Remote-Linux-Host.

Der Schwerpunkt der nächsten Entwicklungsschritte liegt nicht mehr auf dem grundsätzlichen Nachweis der Android-/Codex-Verbindung, sondern auf dem Ausbau des eigentlichen Entwicklungsworkflows und der Tablet-UX.

CodexPad ist weiterhin ein experimentelles Projekt und noch keine fertige allgemeine Entwicklungsumgebung.
