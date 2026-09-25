# CodexPad Single-User-Server

Python 3.12+, `codex` im PATH und angemeldeter Codex-Account erforderlich. Keine
Python-Pakete nötig. [HTTPS/systemd/Caddy-Deployment](../docs/deployment.md).

Der Server bindet ausschließlich Loopback, standardmäßig `127.0.0.1:8765`.
`CODEXPAD_WORKSPACE_ROOT` (Default `~/projects`) stellt direkte Unterordner als
Workspaces bereit; `CODEXPAD_PORT` ändert für lokale Tests nur den Port.
Der interne `codex app-server --stdio`-Kindprozess hat keinen Netzwerklistener.

`CODEXPAD_ACCESS_TOKEN` ist zwingend: 43–512 URL-sichere Zeichen, generiert aus
mindestens 32 Zufallsbytes (empfohlen `secrets.token_urlsafe(48)`). Kein Auth-Bypass
für Debug. Alle Routen einschließlich SSE verlangen `Authorization: Bearer …`;
fehlendes/falsches Token ergibt 401 vor Backendzugriff. Nur `GET /health` ist frei:
200 `{"status":"ok"}` oder 503 `{"status":"unavailable"}`, ohne Codex-Metadaten.
Das Token wird vor Kindprozessstart aus der Umgebung entfernt. Kein Requestlogging.

## Lokaler Start

Token aus einer privaten, nicht versionierten Konfiguration als Environment laden
oder im Terminal verdeckt eingeben (kein Token als Kommandozeilenargument):

```sh
mkdir -p /tmp/codexpad-workspaces/beispiel
python3 - <<'PYTHON'
import getpass, os
os.environ['CODEXPAD_ACCESS_TOKEN'] = getpass.getpass('Zugriffstoken: ')
os.environ['CODEXPAD_WORKSPACE_ROOT'] = '/tmp/codexpad-workspaces'
os.execvp('python3', ['python3', '-B', 'server/codexpad_server.py'])
PYTHON
```

Android-Debug-Einstellungen: `http://127.0.0.1:8765` und dasselbe Token.
Für ADB-Reverse bleibt `adb reverse tcp:8765 tcp:8765` möglich.

## Tests

```sh
python3 -B -m unittest discover -s server -p 'test_*.py' -v
```

Lokale Handler-/Auth-Tests ohne Codex oder Modellzugriff. Der Kotlin/Python-
Vertragstest prüft zusätzlich authentifizierte POSTs, SSE, Reconnect und History;
Aufruf in [Android README](../android/README.md).

`server/smoke.py` ist weiterhin ein **echter Modelltest**, der einen Thread/Turn
anlegt. Er liest dasselbe Token aus `CODEXPAD_ACCESS_TOKEN` und verwendet es auch
für SSE. Erst nach eingerichtetem Server bewusst ausführen; kein Token in Argumente
oder HTTP-URLs schreiben. Historische Ergebnisse stehen im
[ursprünglichen Spike](../docs/experiments/2026-09-server-vertical-slice/README.md).
