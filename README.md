# CodexPad

CodexPad ist der Arbeitstitel eines experimentellen Open-Source-Projekts. Der lokale Server-Vertical-Slice ist nachgewiesen; ein erster nativer Android-Client für den persönlichen Single-User-Einsatz liegt unter [`android/`](android/README.md).

Untersucht wird eine native Android-Tablet-Oberfläche für agentisches Softwareentwickeln. Projekte und Entwicklungswerkzeuge sollen zunächst auf entfernten Linux-Systemen liegen. Codex ist der erste untersuchte Agent; weitere Provider und spätere lokale Ausführung bleiben mögliche Erweiterungen.

## Dokumentation

- [HTTPS-Deployment: systemd, Token und Caddy](docs/deployment.md)
- [Vision](VISION.md)
- [Android-App: Build, API, Reconnect und Tablet-Test](android/README.md)
- [Codex-Integration](docs/research/codex-integration.md)
- [Android-Plattform](docs/research/android-platform.md)
- [Remote-Workspaces](docs/research/remote-workspaces.md)
- [Minimales Remote-Vertrauensmodell](docs/research/remote-trust-model.md)
- [Client-Capabilities und Hostrechte](docs/research/host-capabilities.md)
- [Offene Fragen und Voraussetzungen vor v0.1](docs/product/open-questions.md)
- [Archivierte Linux-Spikes](docs/experiments/README.md)
- [Lokaler Server-Vertical-Slice](docs/experiments/2026-09-server-vertical-slice/README.md) und [Startanleitung](server/README.md)
- [Entscheidungsablage](docs/decisions/README.md)
- [Wiedereinstieg und nächste Exploration](docs/research/README.md)

Stand: 25. September 2026. Kotlin/Compose-Client mit Workspace-Liste, Thread-Liste, History, Turnstart, SSE und Reconnect-Abgleich. Debug-APK lokal gebaut; Build-/Testnachweise und Grenzen stehen in der Android-Anleitung. Der maßgebliche Python-Server wurde zuvor mit Codex App Server 0.156.1 geprüft. Bearer-Authentifizierung, Android-Verbindungseinstellungen mit Keystore-Speicher sowie systemd-/Caddy-Vorlagen für `pad.feichti.dev` sind lokal implementiert. ADR 0004 konkretisiert diese Transportentscheidung. Der echte VPS-Transporttest bleibt der nächste Schritt; es wurde kein öffentlicher Zugang eingerichtet. Die früheren Research-Befunde, Vision und die übrigen Accepted ADRs bleiben als historischer Entscheidungsstand erhalten; deren damalige Explorationsreihenfolge blockiert diesen ausdrücklich beauftragten persönlichen Client-Prototyp nicht.
