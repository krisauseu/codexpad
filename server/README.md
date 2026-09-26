# CodexPad Single-User-Server

Python 3.12+, `codex` im PATH und angemeldeter Codex-Account erforderlich. Keine
Python-Pakete nötig. [HTTPS/systemd/Caddy-Deployment](../docs/deployment.md).

Der Server bindet ausschließlich Loopback, standardmäßig `127.0.0.1:8765`.
`CODEXPAD_WORKSPACE_ROOT` (Default `~/projects`) stellt direkte Unterordner als
Workspaces bereit; `CODEXPAD_PORT` ändert für lokale Tests nur den Port.
Der interne `codex app-server --stdio`-Kindprozess hat keinen Netzwerklistener.
Für den persönlichen Test-VPS startet er mit `sandbox_mode=danger-full-access`
und `approval_policy=never`. Jeder neue Thread und Turn erhält dieselbe Policy;
der Dienst benötigt das vollständige Codex-Standalone-Release inklusive
`codex-code-mode-host` und die in `deploy/` dokumentierten Hostrechte.

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

## Turn-Abbruch

`POST /threads/{threadId}/turns/{turnId}/interrupt` akzeptiert ausschließlich `{}`.
Nach Authentifizierung prüft ein frisches `thread/read` mit Turns den konfigurierten
Workspace und den konkret angeforderten laufenden Turn. Kein Fresh-Cache-Fallback,
kein Resume und keine Ersetzung durch einen neueren Turn. Fehlende/beendete Turns
liefern 409; Rennen nach dem Read entscheidet Codex anhand derselben Turn-ID.
Die einzige Mutation ist das reguläre `turn/interrupt {threadId, turnId}` ohne
Experimental-Opt-in. HTTP 202 mit `{}` bestätigt nur die RPC-Antwort; der endgültige
Zustand kommt über bestehende SSE-Notifications und History. Der bestehende
60-Sekunden-RPC-Timeout kann einen unklaren Ausgang ergeben und löst keinen Retry aus.

Der [Gerätetest gegen Codex 0.156.1](../docs/verification-interrupt.md) belegt
Interrupt, History/Reconnect, Fortsetzung und Abweisung einer alten Turn-ID.

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

## Modellkatalog und Turn-Overrides

`GET /models` ist authentifiziert und sammelt alle sichtbaren `model/list`-Seiten.
Antwort: `models[]` mit `id`, `model` (RPC-Selektor), `displayName`, `description`,
`isDefault`, `supportedReasoningEfforts`, `defaultReasoningEffort`. Wiederholte
Cursor brechen mit 502 ab. Kein generischer RPC-Passthrough.

`POST /threads/{id}/turns` akzeptiert neben `message` optionale `model`/`effort`.
Explizites null, leere Strings, Nicht-Strings, Werte über 256 Zeichen und fremde
Felder werden mit 400 abgewiesen. Bei Overrides wird der aktuelle Katalog gelesen:
Modell muss dessen `model`-Selektor entsprechen; Effort muss in genau dessen
`supportedReasoningEfforts` vorkommen. Ohne Modell-Override dient das gelesene
Thread-Modell zur Effort-Prüfung; unbekannter Zustand wird nicht durch den
Katalogdefault ersetzt. Ohne Overrides wird kein Katalog benötigt.
Overrides gehen ausschließlich an `turn/start`, nie an Resume oder Settings-RPCs.
Der App Server entscheidet weiterhin über tatsächliche Verfügbarkeit und Anwendung;
Thread-/History-Readback ist autoritativ. Usage bleibt beim vorhandenen SSE-Vertrag.

## Bild- und Textdateien an Turns

Reine Textnachrichten verwenden weiter JSON. Für Anhänge akzeptiert derselbe
authentifizierte `POST /threads/{id}/turns` `multipart/form-data` mit `message`,
optional `model`/`effort`, bis zu vier Teilen namens `image` und zwei Teilen namens
`file`. Bilder: PNG, JPEG oder WebP, höchstens 5 MiB je Bild; Signatur und MIME
müssen zusammenpassen. Der gesamte HTTP-Body ist auf 21 MiB begrenzt; Caddy lässt
23 MB bis zu dieser Serverprüfung durch. Keine Clientpfade werden verwendet.

Bilder liegen mit zufälligem Namen und Modus 0600 im privaten
`CODEXPAD_UPLOAD_ROOT` (Default `~/uploads` des Dienstkontos). Der Server übergibt
deren absoluten Pfad als `localImage` zusammen mit optionalem Text in **einem**
`turn/start`. Unvollständige Schreibversuche werden entfernt. Angenommene Bilder
bleiben erhalten, weil Codex die Pfade im Verlauf referenziert; ein verlaufsbewusster
Löschmechanismus und dauerhafte Bildvorschauen sind noch nicht implementiert.

`.txt` und `.md` werden mit MIME `text/plain` oder `text/markdown` und maximal
64 KiB pro Datei angenommen. Sie müssen gültiges UTF-8 ohne NUL-Zeichen enthalten.
Der Server liest sie direkt und gibt Dateiname und Inhalt als gekennzeichnete
`text`-Inputs an denselben Turn weiter. Andere Dateitypen bleiben abgewiesen.
