# Statusanzeige: VPS und angeschlossenes Tablet

3. Oktober 2026, Europe/Berlin. Implementierungscommit `b4ef835`, nach
erfolgreichen lokalen Build-/Vertragstests auf `origin/main` gepusht.

## Deployment

- SSH-Ziel `codexpad-vps` mit vorhandenem Key; Dienst vor dem Update aktiv,
  Codex 0.156.1. Kein Codex-Upgrade notwendig.
- `/opt/codexpad` ist ein Deploymentverzeichnis ohne Git-Checkout. Die bisherige
  produktive Serverdatei entsprach SHA-256-genau dem vorherigen Commit `1186b7c`.
- Vor dem Neustart keine aktiven Threads gemeldet. Vorhandene Threadzahlen:
  ideen 6, kurzfragen 4, projekt 1, testprojekt 16, unterhaltungen 3.
- Neue Serverdatei und Server-README per SSH/SCP installiert, Python-Syntax
  geprüft; bisherige Serverdatei als
  `/opt/codexpad/server/codexpad_server.py.pre-status-b4ef835` gesichert.
- `systemctl restart codexpad`; Dienst danach aktiv, Backend-Health `ok`.
  Keine Änderung an Token, Caddy, Dienstkonfiguration oder Workspace-Dateien
  durch das Deployment.
- Produktive Server-SHA-256:
  `e1adbfcf4b4982d4cc3219deffc1424fc69a9fd5c6f42a2da604b7f8edf5406e`.
- Echter strukturierter `/account/rate-limits`-Read: Bucket `codex`, **primary**
  mit `windowDurationMins: 10080`, zunächst `usedPercent: 6`; secondary null.
  Tablet zeigte korrekt Woche 94 % frei. Später nach den Testturns 93 %.
  Keine Token-Ausgabe oder Extraktion von Android-Zugangsdaten.

## Tablet

- USB-Gerät `AWDVBB6428000843`, YLE_W09. Debug-APK per `adb install -r`
  aktualisiert; App-Daten und gespeicherter Zugang erhalten.
- Build- und installierte APK haben denselben SHA-256:
  `4a8bc11a219a1e5e0171600b4ee09d31568f7b04c242d9ca345bed2eead1206c`.
- Vorhandene Workspaces und Unterhaltung geöffnet, `Live verbunden` bestätigt.
  Vor dem VPS-Update Woche unbekannt, danach Kontext 95 % frei / Woche 94 % frei.
- Separater echter Prüfthread im vorhandenen `testprojekt`:
  `01a100fd-9fd3-7d02-b7f4-7d5b5964e41a`, gpt-6.1-sol, medium.
  Zwei kurze Turns mit `sleep 20` bzw. `sleep 25` und ausschließlich
  vereinbarten Antwortmarkern; beide completed.
- Während des zweiten Turns auf dem Tablet sichtbar:
  `gpt-6.1-sol · medium` und
  `Kontext 97 % frei · Woche 93 % frei · Turn 01a100ff · 0:15`.
  Stoppen-Aktion und Live-Verbindung vorhanden.
- Nach Abschluss `CODEXPAD-STATUSLINE-LIVE-OK`, Kontext 96 % frei,
  Woche 93 % frei, `Kein laufender Turn`. Keine weiterlaufende Dauer.
- Echte Turn-History lieferte Start-/Endzeiten und `durationMs` von 35969 und
  33635. `turn/start.startedAt` zunächst null: bis zum strukturierten
  History-Abgleich wird keine lokale Ersatzstartzeit erfunden.
- Keine ADB-Reverse-Weiterleitung aktiv. Tablet-Prüfdatei auf `/sdcard` entfernt;
  lokale Rohbelege unter ignoriertem `android/.local/statusline-tablet/`.
  App bleibt installiert und im abgeschlossenen Prüfthread geöffnet.

Keine umfassende TalkBack-/Schriftgrößen-/Rotationsprüfung in diesem Lauf.
Der Test belegt die echten Werte mit Codex 0.156.1, nicht sämtliche Provider
oder künftige Upstreamversionen. Der Prüfthread bleibt für den Nutzer erhalten.
