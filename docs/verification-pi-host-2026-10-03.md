# Raspberry-Pi-Host: abgeschlossene Abnahme am 3. Oktober 2026

Datierter Betriebsnachweis der erfolgreichen Host-, Mac- und Android-Abnahme.
Die Host-/Mac-Ergebnisse wurden vom Betreiber für diesen Abschluss dokumentiert;
die einzelnen Tabletbelege stehen im [Android-Prüfbericht](../android/VERIFICATION-TRUSTED-LAN.md).
Diese Dokumentation führt keine neue Remote-Abnahme oder Hoständerung aus.
[Reproduzierbarer Testplan](pi-host-testplan.md), [kanonische Einrichtung](../HOST_SETUP.md).

## Getesteter Aufbau

```text
HONOR YLE-W09 / Android 16 / API 36
    → privates LAN
    → Raspberry Pi 4 / Ubuntu 26.04.1 LTS / ARM64 (AArch64)
    → Caddy
    → CodexPad Python-Adapter
    → Codex App Server über stdio
    → lokale Tools / Workspaces / OpenAI
```

Hostbasis: `f5074765412ff5c651f84866150038972f533bec` auf
`integrate/pi-host-docs`. Codex **0.160.0** zum Zeitpunkt dieser Abnahme;
keine feste zukünftige Sollversion. Android enthielt die anschließend
implementierte Trusted-LAN-Unterstützung.

Dienstbenutzer `codexpad`, ChatGPT-Anmeldung, `danger-full-access`,
`approval_policy=never`, passwortloses sudo und systemd. Der Backendlistener
bindet nur `127.0.0.1:8765`; lokaler Caddy stellt den privaten Eingang
`http://172.16.16.39:8876` bereit. Der Codex App Server hat keinen TCP-Eingang.

Auf dem vorbereiteten, weitgehend frischen Pi konnte Codex die dokumentierte
Einrichtung in rund fünf Minuten durchführen. Voraussetzungen: 64-Bit-Linux,
SSH, passwortloses administratives sudo, Codex beziehungsweise seine
Installationsmöglichkeit, ChatGPT-/Codex-Anmeldung und Netzwerkzugang.
Dies ist eine Beobachtung dieser Abnahme, keine garantierte Installationszeit.

## Bestanden

| Bereich | Ergebnis |
| --- | --- |
| ARM64-Installation | Binary, vollständiges Standalone-Release, Helper und Ressourcen; SHA-256-Abgleich des installierten Releases. |
| Account und Rechte | ChatGPT-Login unter `codexpad`, Full Access, `never`, passwortloses sudo bis UID 0, Root-Schreibtest. |
| Betrieb | systemd, Caddy, ausschließlich privater Backendlistener, Health und Authentifizierung. |
| Codex-Workflow | Modellturn, SSE, Disconnect/Reconnect, vollständige History und Artefaktdownload. |
| Mac im LAN | `/health`, `/workspaces`, `/models`, `/account/rate-limits` jeweils HTTP 200; geschützte Routen ohne oder mit falschem Token HTTP 401. |
| Android im LAN | Direkte Pi-Verbindung, Workspace-/Modellliste, Rate-Limits, Modellturn, ARM64, sudo UID 0, keine gewöhnlichen Approval-Karten. |
| Fachliche Rückfrage | `requestUserInput`, Auswahl und Freitext im selben Turn. |
| Recovery und Dateien | Hintergrund/Vordergrund, WLAN-Unterbrechung, Reconnect, vollständige History, keine Doppelstarts; Artefaktdownload, Speichern, Öffnen und identischer Hash zum Pi. |

Dateien, Upload/Download und Artefakte gehören zum bestätigten persönlichen
Workflow. Konkrete Tabletbelege und automatisierte Transfer-/Vertragstests
sind im [Android-Prüfbericht](../android/VERIFICATION-TRUSTED-LAN.md) abgegrenzt;
daraus folgt keine Abnahme sämtlicher Dateitypen oder Größen-/Lastgrenzen am Pi.

## Verbleibende Prüfungen und bewusste Grenzen

Keine Blocker für den aktuellen Betrieb:

- Android-17-/API-37-Systemdialog mangels API-37-Gerät noch nicht praktisch getestet;
  Permission implementiert und automatisiert geprüft.
- IPv6-HTTP bewusst gesperrt; LAN-HTTP überträgt Token und Inhalte unverschlüsselt.
- DHCP-Reservierung noch offen.
- Externe Router-/IPv6-Erreichbarkeitsprüfung noch offen; die lokale Bindung
  ersetzt diese Prüfung nicht.
- Reboot-Test sowie Last-/Dauerlasttest noch offen.
- Backup/Restore und VPS-Migration noch offen.

Keine rückwirkende Änderung historischer Research-/Spike-Ergebnisse und keine
allgemeine Mehrnutzer-, Sicherheits- oder Leistungsgarantie.
