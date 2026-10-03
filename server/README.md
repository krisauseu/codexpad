# CodexPad Single-User-Server

Neue Hosts: [kanonische HOST_SETUP-Anleitung](../HOST_SETUP.md), Ziel Pi 4 ARM64.
Der Referenz-VPS läuft am 3. Oktober 2026 mit Codex **0.160.0**, eigenem
PATH-Drop-in und [dokumentiertem Code-Overlay](../docs/vps-inventory-2026-10-03.md).
Full Access/never und administratives sudo sind das persönliche Betriebsmodell
gemäß ADR 0005. Direkte LAN-Bindung ist im aktuellen Adapter nicht konfigurierbar;
HOST_SETUP dokumentiert private Proxy-/HTTPS-Wege und die noch fehlende Android-LAN-Umsetzung.

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

## Workspace-Verwaltung

Alle Aktionen nutzen dieselbe authentifizierte HTTP-API und den bestehenden
App-Server-Adapter. Der konfigurierte Workspace-Root ist auf dem VPS
`/srv/codexpad/workspaces/`.

| Route | Anfrage / Antwort |
| --- | --- |
| `POST /workspaces` | `{"name":"beispiel"}` → 201, `workspace: {id, name}` |
| `POST /workspaces/{id}/rename` | `{"name":"neuer-name"}` → 200, `workspace: {id, name}` |
| `GET /workspaces/{id}/inspection` | 200, `files`, `directories`, `threads`, `inspection` |
| `POST /workspaces/{id}/delete` | `{"confirmation":"beispiel","inspection":"…"}` → 200, `{}` |

Namen werden getrimmt: 1–80 Unicode-Zeichen, höchstens 200 UTF-8-Bytes;
Buchstaben/Ziffern sowie Leerzeichen, `_`, `-` und einzelne Punkte.
Das erste Zeichen muss Buchstabe/Ziffer sein, ein Punkt am Ende und `..` sind
verboten. Slash, Backslash, Steuerzeichen, Shell-Zeichen und Prozent-Escapes
sind damit ausgeschlossen. Fremde JSON-Felder werden abgewiesen. Quellen-IDs
müssen bereits exakt dem gültigen Namen entsprechen.

Create verwendet exklusives `mkdir` mit Modus 0700. Rename verwendet
`renameat2(RENAME_NOREPLACE)` unter Linux bzw. `renameatx_np(RENAME_EXCL)` unter
macOS. Es wird keine `AGENTS.md` angelegt oder verändert. Dateisystemoperationen
arbeiten relativ zu einem geöffneten Root-Verzeichnis und folgen keinen Symlinks.
Delete zählt Dateien/Links und Unterordner; die Bestätigung bindet sich an deren
Metadaten-Fingerprint. Geänderter Inhalt verlangt eine neue Bestätigung.
Das symlinksichere `shutil.rmtree` entfernt ausschließlich den bestätigten Ordner;
Mounts im Workspace sind gesperrt. Keine Shell-Aufrufe mit Workspace-Namen.

**Workspaces mit Threads können weder umbenannt noch gelöscht werden.** Codex
speichert absolute CWDs und weitere absolute Verlaufspfade. `thread/resume.cwd`
ist keine atomare Migration dieser Daten. Die Prüfung umfasst alle Quellen,
Provider, paginierten und archivierten Threads, Unterverzeichnisse und den
Fresh-Cache. Zusätzlich liest der bestehende Adapter ausschließlich die ersten
`session_meta`-Zeilen unter `$CODEX_HOME/sessions` und `archived_sessions`, weil
Codex 0.156.1 leere persistierte Threads bei `thread/list` auslässt. Unlesbare
Metadaten oder Backendfehler blockieren Änderungen. Es werden keine Codex-
Sessiondateien oder Datenbanken verändert oder gelöscht.

Die vorhandene Thread-Liste ergänzt solche ausgelassenen interaktiven Sessions
per regulärem `thread/read`, ohne archivierte oder Subagent-Sessions anzuzeigen.
Workspace-Mutationen und die vorhandene Thread-Anlage teilen einen Lock.
Bekannte Fehler liefern feste `code`-Werte; Android übersetzt ausschließlich
diese erlaubten Werte, ohne beliebige Servertexte oder Tokens anzuzeigen.

[Implementierung, Tests und Tablet-Nachweis](../docs/verification-workspaces.md).

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

Normale Nachrichten sind auf **12.000 Unicode-Zeichen** begrenzt, bei JSON und
Multipart gleichermaßen. Überlange Nachrichten liefern HTTP 400 mit
`code=message_too_long`; Inhalte werden niemals gekürzt oder getrimmt. Nur für
Turn-Anfragen erlaubt der JSON-Parser bis zu 160 KiB, auch für Unicode-Escapes.
Andere JSON-Endpunkte und Antworten auf Rückfragen behalten ihre bisherigen Grenzen.

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

`.txt`, `.md`, `.html` und `.htm` werden mit MIME `text/plain`, `text/markdown`
oder `text/html` und maximal 64 KiB pro Datei angenommen. Sie müssen gültiges UTF-8 ohne NUL-Zeichen enthalten.
Der Server liest sie direkt und gibt Dateiname und Inhalt als gekennzeichnete
`text`-Inputs an denselben Turn weiter. Andere Dateitypen bleiben abgewiesen.
HTML wird ausschließlich als Text übertragen, ohne Ausführung oder Vorschau.

## Ergebnisdateien (v1)

`GET /threads/{threadId}/history` und der SSE-Snapshot ergänzen jeden terminalen
Turn um `artifacts: [{id, name, mimeType, size, turnId}]`. Pfade bleiben intern.
`GET /threads/{threadId}/artifacts/{artifactId}` liefert mit demselben Bearer-Token
Dateibytes, Content-Type, Content-Length und UTF-8 Content-Disposition; keine
Download-URLs mit Token, keine Redirects. Maximal **64 MiB pro Datei**, 128 Kandidaten
pro Turn. PDF, PNG, JPEG, WebP sowie UTF-8 TXT/Markdown/HTML; Endung und Signatur bzw.
Textinhalt werden gemeinsam geprüft. Keine vollständige Formatvalidierung.

Erkennung: erfolgreiche strukturierte `fileChange`-Items (inklusive Move-Ziel).
Codex 0.156.1 meldet Shell-/Python-Dateiausgaben dagegen nicht als `fileChange`.
Deshalb ergänzt der Adapter jeden neuen Turn um einen separaten Text-Input, der
Ergebnisdateien ausdrücklich nach `codexpad-results/<Thread-Hash>/<Zufalls-ID>/`
im aktuellen Workspace bestellt. Die exakte Anweisung bleibt in der Codex-History;
sie ordnet diesen flachen Ordner dauerhaft dem Turn zu. Keine Auswertung von
Agentenprosa oder Shell-Befehlen. Bestehende Shell-Ergebnisse außerhalb solcher
Ordner werden nicht rückwirkend geraten. Hält Codex die Anweisung nicht ein und
liefert auch kein `fileChange`, erscheint die Datei nicht als Artefakt.

Artefakt-IDs sind stabile SHA-256-Identitäten aus Thread, Workspace, Turn und
relativem Pfad. Sie sind keine Berechtigung: jeder Download prüft erneut Auth,
Thread-CWD, History-Mitgliedschaft, Typ und Dateigrenzen. Keine frei wählbaren
Downloadpfade. Descriptor-relative `O_NOFOLLOW`-Auflösung aller Pfadkomponenten;
Symlinks, Hardlinks, Nicht-Dateien, versteckte Pfade sowie konservativ benannte
Secret-/Konfigurationspfade bleiben ausgeschlossen. Ergebnisordner sind bewusst
zum Veröffentlichen bestimmt; der Agent darf dort keine Secrets ablegen.

Die Antwort beschreibt den aktuellen Dateiinhalt, kein historisches Dateiarchiv.
Gelöschte/ungültige Dateien verschwinden beim History-Abgleich; Änderung am selben
Pfad behält die ID. Keine Datenbank, kein Workspace-Vollscan, kein Watcher, keine
zusätzliche Serverkonfiguration. Der normale Reconciliation-Pfad liefert die Karten
auch nach Reconnect und Serverneustart. Android öffnet über begrenzten App-Cache
und FileProvider-Lesegrant; bewusstes Speichern nutzt ACTION_CREATE_DOCUMENT.

## Interaktive Rückfragen

Codex 0.156.1: `item/tool/requestUserInput` für Freitext/eigene Antwort und
Einzelauswahl. Der Adapter aktiviert `features.default_mode_request_user_input`
für den normalen Modus; `approvalPolicy: "never"` bleibt unverändert. Keine
Command-/File-/Permission-Approval-UI oder allgemeine Grants.

Thread/History/SSE-Snapshot enthalten `thread.pendingRequests`. Authentifiziertes
`POST /threads/{threadId}/requests/{id}/answer` mit
`{"answers":{"questionId":{"text":"Antwort"}}}` bzw. `{"option":"Label"}`
sendet genau eine validierte Antwort pro Frage an denselben laufenden Turn.
202 bestätigt das Schreiben, `serverRequest/resolved`/History die Auflösung;
400 bei ungültiger Antwort, 404 bei unbekannter/fremder ID, 409 bei erledigter ID.
Keine automatische Wiederholung bei unklarem Mutationsausgang.

Offene Callbacks bleiben über Android-Reconnect im langlebigen Adapter erhalten;
Snapshots und periodischer Abgleich ersetzen ein Event-Replay. Ein App-Server-
Neustart beendet alte Callbacks; sie werden nicht aus historischen Texten erzeugt.
[Vertrag, echte Tablet-Tests und Grenzen](../docs/verification-user-input.md).
