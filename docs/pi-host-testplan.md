# Raspberry-Pi-Abnahme vom Mac

Vorbereitet am 3. Oktober 2026 aus [HOST_SETUP](../HOST_SETUP.md),
[VPS-Inventar](vps-inventory-2026-10-03.md) und dem tatsächlichen Projektcode.
Noch nichts auf einem Remotehost ausgeführt. SSH-Alias: `pi-codexpad`,
aktuelle IPv4: `172.16.16.39`. Das Benutzerkonto/der SSH-Schlüssel ist noch
vom Betreiber einzutragen; der Alias ist keine automatisch vorhandene DNS-Adresse.

Lesende Kontrolle zuerst. Modell-/Datei-/Restart-/Reboottests sind unten eigene
spätere Schritte auf dem eingerichteten Pi, keine jetzt auszuführende Provisionierung.
Token, auth.json und komplette Environments nicht in Diagnoseausgaben übernehmen.
Journale und History enthalten ggf. private Inhalte; nur lokal prüfen/redigieren.

Installationsgrundlage ist der bewusst festgelegte Commit des Integrationsbranches
`integrate/pi-host-docs` auf aktuellem `main` (v0.1.1), später dessen integrierter
Stand auf `main`. Der historische Referenzpatch wird nicht erneut angewendet.

## 1. SSH und Plattform (lesend, vor und nach Installation)

```bash
ssh -G pi-codexpad | awk '$1 ~ /^(hostname|user|port|identityfile)$/ {print}'
ssh -o BatchMode=yes -o ConnectTimeout=10 pi-codexpad 'uname -a'
ssh pi-codexpad 'cat /etc/os-release; uname -m; getconf LONG_BIT; python3 --version'
ssh pi-codexpad 'id; uptime; free -h; df -h / /var/lib /srv'
```

Hostkey vor dem ersten Zugriff über einen vertrauenswürdigen Weg prüfen;
kein `StrictHostKeyChecking=no`. Bei erstmaligem Hostkeydialog zuerst interaktiv
`ssh pi-codexpad` verwenden; BatchMode ist die spätere Automationsprobe.
Erwartung: richtiger Pi/Account, ARM64 `aarch64`, 64-bit-Userspace, Python ≥3.12,
ausreichend Speicher. Kein vorgegebenes OS allein aus der IP ableiten.

Frischer Host: zusätzlich nur auf Existenz prüfen, bestehende Dateien nicht ersetzen:

```bash
ssh pi-codexpad 'sudo test ! -e /etc/codexpad/server.env'
ssh pi-codexpad 'test ! -e /etc/systemd/system/codexpad.service'
ssh pi-codexpad 'test ! -e /opt/codexpad/server/codexpad_server.py'
```

Jeder Fehlschlag bedeutet vorhandenen Bestand klären. sudo darf interaktiv
`ssh -t pi-codexpad` benötigen. `sudo -n` in folgenden Automationsproben setzt
entsprechende Rechte des Administrationsaccounts voraus; ein Passwortfehler ist
kein CodexPad-Fehler und rechtfertigt keine Änderung seiner sudoers.

## 2. Services, Ports und Prozesse (nach Installation, lesend)

```bash
ssh pi-codexpad 'systemctl is-enabled codexpad; systemctl is-active codexpad'
ssh pi-codexpad 'systemctl status codexpad --no-pager'
ssh pi-codexpad 'systemctl show codexpad -p MainPID -p User -p Group -p WorkingDirectory -p FragmentPath -p DropInPaths -p Restart -p KillMode -p PrivateTmp -p ProtectSystem -p ProtectHome -p NoNewPrivileges'
ssh pi-codexpad 'sudo ss -lntup'
ssh pi-codexpad 'ps -eo user,pid,ppid,comm --forest'
```

Erwartung: Python als Hauptprozess, genau sein privates Codex-stdio-Kind;
weitere Helper/Plugins sind möglich. Backend ausschließlich `127.0.0.1:8765`.
Keine Codex-TCP-Freigabe; `PrivateTmp=yes`, keine sudo-/Schreibblockaden.
Nicht aus `active` allein auf ein lebendes Codex-Kind schließen.

Nur den gewählten Eingang prüfen:

```bash
# LAN-Caddy oder lokales HTTPS:
ssh pi-codexpad 'systemctl is-enabled caddy; systemctl status caddy --no-pager'
ssh pi-codexpad 'sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile'
# Alternativ Tailscale HTTPS:
ssh pi-codexpad 'systemctl status tailscaled --no-pager; sudo tailscale serve status'
```

LAN-HTTP nach HOST_SETUP: nur `172.16.16.39:8876`, Reverseproxy
`127.0.0.1:8765`, Bodylimit 23 MB, `flush_interval -1`. Kein Wildcardlistener,
Funnel oder Routerforwarding. Für HTTPS tatsächliche Adresse/Bindung notieren.
Router/UPnP/IPv6 separat prüfen; lokale `ss`-Ausgabe beweist keine externe Grenze.

## 3. Wirkliches Dienstbinary, Login und Policy (lesend)

Der globale Shell-PATH ist kein Versionsnachweis. Der folgende Block ermittelt
das direkte Codex-Kind des systemd-Hauptprozesses und erfasst dessen Binary,
Paketmetadaten und ausführbare Nachbarn als Helperkandidaten.
Die Ausgabe enthält ausschließlich relevante Policywerte, keine Environments.
Vorher die für diesen Host bewusst ausgewählte Version, das verifizierte
vollständige Releaseasset samt Digest und seine benötigten Helper im
Installationsprotokoll festhalten. Bei anderem Paketlayout die Prüfpunkte
an dessen dokumentierte Struktur anpassen; keine historischen Versionsnummern
oder Helper aus einem anderen Release übernehmen.

```bash
ssh -T pi-codexpad 'sudo -n python3 -' <<'PY'
import hashlib, json, os, pathlib, subprocess, tomllib
main = int(subprocess.check_output(['systemctl', 'show', 'codexpad', '-p', 'MainPID', '--value']))
assert main > 0, 'Dienst hat keinen Hauptprozess'
children = pathlib.Path(f'/proc/{main}/task/{main}/children').read_text().split()
matches = []
for pid in children:
    args = pathlib.Path(f'/proc/{pid}/cmdline').read_bytes().decode().split('\0')
    if 'app-server' in args and '--stdio' in args:
        matches.append((pid, args))
assert len(matches) == 1, 'Genau ein direktes stdio-App-Server-Kind erwartet'
pid, args = matches[0]
binary = pathlib.Path(os.readlink(f'/proc/{pid}/exe'))
print('main_pid', main, 'codex_pid', pid, 'binary', binary)
release = binary.parent.parent.resolve()
package_file = release / 'codex-package.json'
package = json.loads(package_file.read_text())
print('release_dir', release, 'package_version', package['version'], 'package_target', package['target'])
print('package_sha256', hashlib.sha256(package_file.read_bytes()).hexdigest())
# Alle ausführbaren Paketnachbarn inventarisieren; benötigte Helper anhand
# des bewusst ausgewählten vollständigen Releases auf Vollständigkeit prüfen.
helpers = sorted(p for p in binary.parent.iterdir() if p != binary and p.is_file() and os.access(p, os.X_OK))
for p in (binary, *helpers):
    assert p.is_file() and os.access(p, os.X_OK), 'Binary/Helper fehlt'
    assert p.resolve().is_relative_to(release), 'Binary/Helper verweist außerhalb des Releases'
    print(subprocess.check_output(['file', str(p)], text=True).strip())
    print('sha256', hashlib.sha256(p.read_bytes()).hexdigest())
for cmd in ([str(binary), '--version'], [str(binary), 'login', 'status']):
    subprocess.run(['sudo', '-n', '-u', 'codexpad', '--', 'env',
        'HOME=/var/lib/codexpad', 'CODEX_HOME=/var/lib/codexpad/.codex',
        'PATH=' + str(binary.parent) + ':/usr/local/bin:/usr/bin:/bin', *cmd], check=True)
assert {'sandbox_mode="danger-full-access"', 'approval_policy="never"',
        'features.default_mode_request_user_input=true'}.issubset(args), 'Prozesspolicy weicht ab'
for value in args:
    if value.startswith(('sandbox_mode=', 'approval_policy=', 'features.default_mode_request_user_input=')):
        print('process_policy', value)
config = tomllib.loads(pathlib.Path('/var/lib/codexpad/.codex/config.toml').read_text())
print('config_sandbox', config.get('sandbox_mode'))
print('config_approval', config.get('approval_policy'))
print('config_user_input', config.get('features', {}).get('default_mode_request_user_input'))
PY
ssh pi-codexpad 'sudo -u codexpad sudo -n id -u'
ssh pi-codexpad 'sudo visudo -cf /etc/sudoers.d/codexpad'
```

Erwartung: Das tatsächlich vom CodexPad-Dienst gestartete ARM64-Codex-Binary
entspricht der für diesen Host bewusst installierten Version; Binary und
benötigte Helper stammen aus demselben vollständigen Release. CLI-Ausgabe,
Paketversion/-target, tatsächlichen Binary-Pfad und Helper samt Hashes mit dem
Installationsprotokoll und den Dateien des verifizierten Releasepakets vergleichen.
Eine gemeinsame Verzeichnislage oder Paketmetadaten allein beweisen diese
Herkunft nicht; fehlende releaseabhängig erforderliche Helper sind ein Fehler.
Keinen Helpernamen allein aufgrund eines historischen Release voraussetzen.
Zusätzlich ELF AArch64, ChatGPT-Login unter `codexpad`,
Policy Full Access/never/UserInput, sudo UID 0.
Auf dem neuen Pi stehen die Policywerte auch explizit in config.toml.
Projekt-/Managed-Policies können zusätzlich wirken; ein echter Toolturn ist
der Nachweis der effektiven Ausführung. ARM64-Hashes separat protokollieren,
nicht mit den x86-Binaryhashes des VPS vergleichen.

## 4. Code, Workspacepfade und Logs (lesend)

```bash
ssh pi-codexpad 'cat /opt/codexpad/RELEASE_COMMIT; sha256sum /opt/codexpad/server/codexpad_server.py'
ssh pi-codexpad 'test ! -e /opt/codexpad/REFERENCE_OVERLAY'
ssh pi-codexpad 'sudo stat -c "%U:%G %a %n" /etc/codexpad /etc/codexpad/server.env /var/lib/codexpad /var/lib/codexpad/.codex /var/lib/codexpad/.codex/auth.json /var/lib/codexpad/uploads /srv/codexpad/workspaces'
ssh pi-codexpad 'sudo -u codexpad find /srv/codexpad/workspaces -mindepth 1 -maxdepth 1 -type d -printf "%f\n"'
ssh pi-codexpad 'sudo journalctl -u codexpad -n 100 --no-pager'
```

Referenzadapterhash: `e1adbfcf4b4982d4cc3219deffc1424fc69a9fd5c6f42a2da604b7f8edf5406e`.
Den exakt geprüften Integrationscommit installieren und dessen Marker mit der
tatsächlich installierten Datei abgleichen. Keine Basis-plus-Overlay-Installation.
Bei anderer Codex-Credential-Storewahl ist fehlendes auth.json gesondert einzuordnen.
Tokenfile root:root 0600, Diensthome/.codex/uploads/Workspace-Root 0700.
Keine Ausgabe des Tokenfiles oder von auth.json. `host-smoke` muss existieren.
Nur reale direkte Workspaceordner anbieten; absolute Pfade nicht umbenennen.
Proxyjournal bei Problemen separat `journalctl -u caddy` bzw. `-u tailscaled`.

## 5. API über SSH und über den wirklichen Mac-Netzweg

Zunächst Backend ohne Token:

```bash
ssh pi-codexpad 'curl --fail --silent --show-error --max-time 10 http://127.0.0.1:8765/health'
ssh pi-codexpad 'curl --silent --show-error --max-time 10 --output /dev/null --write-out "%{http_code}\n" http://127.0.0.1:8765/workspaces'
```

Erwartung: JSON `status=ok`, dann 401. Authentifizierte Backendprobe liest
das Token ausschließlich auf dem Pi; nur Status und Anzahlen gehen an den Mac:

```bash
ssh -T pi-codexpad 'sudo -n python3 -' <<'PY'
import json, urllib.request
from pathlib import Path
env = dict(line.split('=', 1) for line in Path('/etc/codexpad/server.env').read_text().splitlines()
           if '=' in line and not line.startswith('#'))
for route in ('/workspaces', '/models', '/account/rate-limits'):
    req = urllib.request.Request('http://127.0.0.1:8765' + route,
        headers={'Authorization': 'Bearer ' + env['CODEXPAD_ACCESS_TOKEN']})
    with urllib.request.urlopen(req, timeout=65) as response:
        data = json.load(response)
        print(route, response.status, {k: len(v) for k, v in data.items() if isinstance(v, list)})
PY
```

Der dritte Read prüft gezielt den bereits integrierten v0.1.1-Vertrag. Health ohne Token
belegt weder diesen Vertrag noch Accountauthentifizierung.

Vom Mac direkt über den **gewählten** Eingang, ohne SSH-Tunnel:

```bash
# Erst nach Einrichtung des LAN-Proxys; alternativ dessen echte HTTPS-URL setzen.
CODEXPAD_PI_URL=http://172.16.16.39:8876
curl --fail --silent --show-error --max-time 10 "$CODEXPAD_PI_URL/health"
curl --silent --show-error --max-time 10 --output /dev/null --write-out '%{http_code}\n' "$CODEXPAD_PI_URL/workspaces"
```

Für HTTPS kein `curl -k`, keine Redirectverfolgung. Negativprobe auch auf SSE
und Artefaktroute mit einer später bekannten Smoke-Thread-/Artefakt-ID: ohne
und mit falschem Token immer 401 vor Objektprüfung. Beispiel ohne Token:

```bash
read -r 'CODEXPAD_SMOKE_THREAD?Smoke-Thread-ID: '
read -r 'CODEXPAD_SMOKE_ARTIFACT?Smoke-Artefakt-ID: '
curl --silent --show-error --max-time 10 --output /dev/null --write-out '%{http_code}\n' "$CODEXPAD_PI_URL/threads/$CODEXPAD_SMOKE_THREAD/events"
curl --silent --show-error --max-time 10 --output /dev/null --write-out '%{http_code}\n' "$CODEXPAD_PI_URL/threads/$CODEXPAD_SMOKE_THREAD/artifacts/$CODEXPAD_SMOKE_ARTIFACT"
```

`read` hier ist zsh-Syntax auf dem Mac. Für eine authentifizierte Probe ohne
Token in Prozessargumenten/History das folgende Python verwenden. Es liest
Workspace/Modelle/Rate-Limits und optional einen **vorhandenen** Smoke-Thread,
zwei SSE-Verbindungen sowie eine Artefaktdatei. Keine Turns/Uploads werden angelegt.
Die SSE-Route lädt per `thread/resume` Threadzustand ins laufende Backend;
dieser GET ist deshalb keine Garantie völlig unveränderten In-Memory-Zustands.

```bash
python3 - "$CODEXPAD_PI_URL" <<'PY'
import getpass, hashlib, json, sys, time, urllib.error, urllib.parse, urllib.request
class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None
base = sys.argv[1].rstrip('/')
opener = urllib.request.build_opener(NoRedirect())
token = getpass.getpass('Pi-Pad-Token (verdeckt): ')
def open_route(path, auth=True, sse=False):
    headers = {'Authorization': 'Bearer ' + token} if auth else {}
    if sse:
        headers['Accept'] = 'text/event-stream'
    return opener.open(urllib.request.Request(base + path, headers=headers), timeout=150)
for path in ('/health', '/workspaces', '/models', '/account/rate-limits'):
    with open_route(path) as response:
        data = json.load(response)
        print(path, response.status, {k: len(v) for k, v in data.items() if isinstance(v, list)})
# Auth muss vor Objektprüfung greifen, auch bei unbekannter Thread-/Artefakt-ID.
for path in ('/workspaces', '/threads/auth-negative-probe/events',
             '/threads/auth-negative-probe/artifacts/auth-negative-probe'):
    for headers in ({}, {'Authorization': 'Bearer ' + 'x' * 64}):
        try:
            with opener.open(urllib.request.Request(base + path, headers=headers), timeout=10):
                raise AssertionError('Unauthentifizierter Zugriff unerwartet erfolgreich')
        except urllib.error.HTTPError as error:
            assert error.code == 401, '401 vor Objektprüfung erwartet'
    print('auth_negative_ok', path)
# Heredoc belegt stdin; nicht input() verwenden.
with open('/dev/tty') as tty:
    print('Vorhandene Smoke-Thread-ID (leer: Ende): ', end='', flush=True)
    thread_id = tty.readline().strip()
    if thread_id:
        path = '/threads/' + urllib.parse.quote(thread_id, safe='')
        with open_route(path + '/history') as response:
            thread = json.load(response)['thread']
        assert thread['id'] == thread_id
        print('history_turns', len(thread.get('turns', [])))
        # Stream bewusst schließen und neu öffnen, ohne turn/start.
        for attempt in (1, 2):
            with open_route(path + '/events', sse=True) as response:
                assert response.headers.get('Content-Type', '').startswith('text/event-stream')
                seen_snapshot, heartbeats = False, 0
                event, lines = '', []
                deadline = time.monotonic() + 40
                while time.monotonic() < deadline:
                    raw = response.readline()
                    assert raw, 'SSE unerwartet beendet'
                    line = raw.decode('utf-8').rstrip('\r\n')
                    if line.startswith(':'):
                        heartbeats += 1
                    elif line.startswith('event:'):
                        event = line[6:].strip()
                    elif line.startswith('data:'):
                        lines.append(line[5:].lstrip())
                    elif not line:
                        if lines and event == 'snapshot':
                            snapshot = json.loads('\n'.join(lines))['thread']
                            assert snapshot['id'] == thread_id
                            seen_snapshot = True
                        event, lines = '', []
                    if seen_snapshot and heartbeats >= 2:
                        break
                assert seen_snapshot and heartbeats >= 2, 'Snapshot/Heartbeats fehlen'
                print('sse_attempt', attempt, 'snapshot_ok', seen_snapshot, 'heartbeats', heartbeats)
        print('Artefakt-ID aus diesem Smoke-Thread (leer: Ende): ', end='', flush=True)
        artifact_id = tty.readline().strip()
        if artifact_id:
            items = [a for t in thread.get('turns', []) for a in t.get('artifacts', [])]
            artifact = next(a for a in items if a['id'] == artifact_id)
            with open_route(path + '/artifacts/' + urllib.parse.quote(artifact_id, safe='')) as response:
                length = int(response.headers['Content-Length'])
                assert 0 < length <= 64 * 1024 * 1024
                assert response.headers['Content-Type'] == artifact['mimeType']
                assert length == artifact['size']
                digest, size = hashlib.sha256(), 0
                while chunk := response.read(65536):
                    size += len(chunk)
                    assert size <= 64 * 1024 * 1024
                    digest.update(chunk)
                assert size == length
                print('artifact_bytes', size, 'sha256', digest.hexdigest())
PY
```

Artefaktbytes werden nur gehasht, nicht ausgegeben oder gespeichert.
Mit vom Agenten erstellter Smoke-Datei auf Pi vergleichen (`sha256sum` ihres
tatsächlichen Ergebnisordnerpfads). Bei laufenden Events ohne Heartbeats kann
der ruhige SSE-Test warten; daher einen abgeschlossenen Smoke-Thread verwenden.
Diese Probe belegt Snapshot/Framing/Reconnect und Bytevertrag, noch keinen
weiterlaufenden Modellturn oder Android-Lifecycle.

## 6. Isolierte Vertragsprüfungen auf ARM64 (später, lokale Testfixtures)

Im vollständigen, geprüften Repositorycheckout auf dem Pi ausführen:

```bash
python3 -B -m unittest discover -s server -p 'test_*.py' -v
```

Mac kann dies später per `ssh pi-codexpad 'cd <tatsächlicher Checkout>; ...'`
anstoßen; Checkoutpfad noch ermitteln, nicht `/root/...` vom VPS übernehmen.
`server/test_auth.py` benötigt `android/tools/contract_server.py` relativ zum
Repositoryroot. **Nicht einfach in der nur mit server/deploy installierten
`/opt/codexpad`-Kopie laufen lassen**, dort fehlt diese Fixture nach HOST_SETUP.
Eine isolierte vollständige Testkopie desselben Integrationscommits mit dessen
Adapterdatei und passender Fixture verwenden. Keine Tests gegen Produktionsstate.
Auth/Symlinks, `renameat2`/No-Replace, symlinksicheres rmtree, Transfers und
UserInput müssen auf ARM64 bestehen. Testergebnis/Commit/Hash dokumentieren.

## 7. Modell, Dateien und Android (später, mutierend nur in host-smoke)

Nach erfolgreichem Preflight den Wrapper aus **HOST_SETUP Abschnitt 11.4**
vom Mac über `ssh -T pi-codexpad 'sudo -n python3 -'` ausführen.
Er setzt das Token nur remote und filtert die Workspaceantwort auf `host-smoke`.
`server/smoke.py` allein nimmt sonst den ersten Workspace. Der vorhandene Test
erzeugt genau einen Thread/Turn, kappt SSE nach Textdelta, verbindet neu und
prüft `CODEXPAD-RECONNECT-OK` im vollständigen Verlauf mit derselben Turn-ID.
Er verbraucht Modellkontingent und hinterlässt History. Nicht automatisch
wiederholen, wenn ein Fehler einen unbekannten Turnstartausgang lässt.
Dieser lokale Backendtest muss durch die Mac-Proxyprobe und Tabletprobe ergänzt werden.

| Tabletprobe | Erwartung und Mac-Gegenkontrolle |
| --- | --- |
| Gerätebasis | OS/API und APK-versionCode/versionName notieren, ggf. `adb shell getprop ro.build.version.sdk` und `adb shell dumpsys package dev.codexpad` lokal prüfen. Keine Build-/APK-Version aus dem Hostinventar ableiten. |
| Verbindung | Echte Pi-URL und Token eingeben, Test+Speichern; Workspace-/Threadliste. `/health` und auth-Reads vom Mac parallel. |
| Full Access | Smokeauftrag aus HOST_SETUP 11.5: `id`, ARM64, `sudo -n id -u`, HTTPS-GET und begrenzter Write `/var/tmp/codexpad-host-smoke.txt`; Ergebnisdatei im angegebenen Turnordner. Keine gewöhnliche Approvalkarte; `ssh pi-codexpad 'sudo cat /var/tmp/codexpad-host-smoke.txt'` bestätigt `HOST-FULL-ACCESS-OK`. |
| Fachliche Rückfrage | RequestUserInput separat auslösen, Auswahl/Freitext beantworten, gleicher Turn; offene Rückfrage nach Android-Reconnect rekonstruieren. |
| Anhänge | Kleine bekannte PNG/JPEG/WebP und TXT/MD/HTML/HTM, Unicode, Größen-/Anzahlgrenzen; denselben Turn prüfen, Bilder persistent, HTML nur Text. Fehlversuche dürfen keinen doppelten Turn erzeugen. |
| Dateien | Smoke-TXT sowie PDF/Bild öffnen und per SAF speichern. Mac-Artefaktprobe vergleicht MIME/Länge/Hash. Kein Token an externe Anzeigeapp. |
| Reconnect | Längeren Turn starten, Hintergrund/Vordergrund und WLAN aus/an, auch komplett offline beenden lassen. IDs/History vom Mac vor/nachher vergleichen, kein zweites turn/start. |
| Steuerung | Exakten Turn stoppen, Folgeturn, Modell/Effort und v0.1.1-Limitstatus prüfen; manuelle Kompaktierung ist in v0.1.1 aus der Oberfläche entfernt. Keine Änderung der bestehenden Retryregeln. |
| LAN-Permission | Auf Android 17 erlauben/ablehnen/widerrufen, erneute Rückkehr/Settings/Transfer. Direkte LAN-HTTP- und HTTPS-Verbindungen betroffen. Auf älterem OS kein API-37-Dialog. |
| Negativtransport | LAN-HTTP ohne explizite Freigabe abgelehnt; öffentliches HTTP abgelehnt; ungültiges TLS abgelehnt; kein Redirect, keine Tokenübernahme bei anderer URL. |

Für LAN-HTTP sind vorher die im [Clientabgleich](pi-host-client-abgleich-2026-10-03.md)
genannten Androidänderungen nötig. HTTPS passt bereits zur URLpolicy;
Android-17-LAN-Permission bleibt ein eigener offener Schritt.

## 8. Restart, Reboot, Last und externe Grenze (später geplant)

Erst nach abgeschlossenem Turn und dokumentierten IDs/Dateihashes:

```bash
ssh pi-codexpad 'sudo systemctl restart codexpad'
# Nach neuem Health=ok Schritte 2–5 wiederholen; History/Dateihashes vergleichen.
ssh pi-codexpad 'sudo reboot'
# Nach Wiederkehr:
ssh -o ConnectTimeout=10 pi-codexpad 'uptime; systemctl is-active codexpad'
```

Neue PIDs, aber gleiche abgeschlossene History/Artefakte; Autostart und
neuer Turn möglich. Serverneustart ist von bloßem SSE-Disconnect getrennt:
alte Rückfragecallbacks werden nicht aus History wiederbelebt.
Kein absichtliches Killen des Codex-Kindes in der Erstabnahme.

Repräsentativen eigenen Build in einem Testprojekt über längere Zeit beobachten:

```bash
ssh pi-codexpad 'free -h; df -h / /var/lib /srv; uptime'
ssh pi-codexpad 'command -v vcgencmd >/dev/null && vcgencmd measure_temp && vcgencmd get_throttled'
ssh pi-codexpad 'sudo journalctl -k --since "1 hour ago" --no-pager'
ssh pi-codexpad 'sudo journalctl -u codexpad --since "1 hour ago" --no-pager'
```

Builddauer, Zeit bis erstem Token, SSE-Stabilität, RAM/OOM, freie Bytes,
Temperatur/Throttling und Fehler protokollieren. Fehlendes vcgencmd auf Ubuntu
ist kein Dienstfehler. Paketinstall-/Dienststartfähigkeit erst mit gezieltem
Testauftrag auf dem neuen Host prüfen, nicht aus UID 0 allein behaupten.

Von einem tatsächlich externen Netz die eigenen öffentlichen Routeradressen
und relevanten IPv4/IPv6-Ports ohne Token prüfen. Die private Adresse
`172.16.16.39` aus Mobilfunk zu testen beweist allein keine fehlende öffentliche
Weiterleitung. Router/UPnP, öffentliche IPv6-Bindung und ggf. Tailnetregeln prüfen.
Erwartung: kein öffentlicher CodexPad-Control-Eingang; kein Funnel.

## Abnahmeprotokoll

Für jede Stufe Datum/Zeit, Pi-OS/Architektur, installierter Codecommit/Adapterhash,
bewusst ausgewählte und tatsächlich ermittelte Codex-Version, Releaseasset/Digest,
tatsächlicher Dienstbinary-Pfad, benötigte Helper samt Pfaden/Hashes,
Transport/Port, Android-OS/APK und
PASS/FAIL/offen festhalten. Nur IDs, Status, Anzahlen und Smoke-Dateihashes
in teilbare Evidenz übernehmen. Gegenprobe Mac↔Pi und Tablet↔Pi getrennt abhaken.
VPS-Backup/Migration/Restore anschließend separat planen; dessen Abschaltung
ist kein Anlass, funktionierende Projekte oder Credentials ungeprüft zu kopieren.
