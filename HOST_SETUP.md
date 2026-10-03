# CodexPad Host einrichten

Stand: 3. Oktober 2026. Kanonische Anleitung für einen **frischen persönlichen
Linux-Host**. Ein Host betreibt Adapter, Codex App Server, lokale Werkzeuge und
autoritative Workspaces; Android ist der Steuerungsclient. Der Raspberry Pi 4
soll funktional die Rolle des abschaltenden VPS übernehmen.

Die Befehle dieses Dokuments sind **nicht auf dem laufenden Referenz-VPS
auszuführen**. Dort wurde ausschließlich Bestand erhoben:
[VPS-Inventar mit Architektur, Dateien und Versionsdrift](docs/vps-inventory-2026-10-03.md).
Diese Anleitung ist aus der funktionierenden Installation abgeleitet; sie ist
noch keine bestätigte Raspberry-Pi-End-to-End-Abnahme.
Der [Clientabgleich](docs/pi-host-client-abgleich-2026-10-03.md) hält die bereits
geklärten Befunde fest; der [Mac-Testplan](docs/pi-host-testplan.md) beschreibt
die spätere Abnahme des integrierten v0.1.1-Stands.

## 1. Plattform und Voraussetzungen

Ziel: Raspberry Pi 4, **64-bit-OS und ARM64-Userspace**, headless, dauerhaft im LAN.
Bevorzugt Raspberry Pi OS Lite 64-bit mit Python mindestens 3.12; alternativ
Ubuntu Server 24.04 ARM64 oder ein geeignetes Debian-basiertes 64-bit-System.
Debian-12-/ältere Pi-Images mit Python 3.11 erfüllen den dokumentierten
Python-3.12+-Baseline nicht; ein passendes neueres Image wählen.
Die [Raspberry-Pi-Anleitung](https://www.raspberrypi.com/documentation/computers/getting-started.html)
beschreibt Lite/headless-Einrichtung. In Imager ein eigenes Administratorkonto,
SSH-Schlüssel und Netzwerk einrichten; kein Desktop nötig.

Erforderlich: sudo-Administrationszugang, Git-Repositoryzugang, ausgehendes DNS/HTTPS,
gültiger Codex-/ChatGPT-Account, Android-App und ein privat übertragbares Pad-Token.
Ethernet/DHCP-Reservierung ist für dauerhafte Adressen sinnvoll. RAM-Kapazität,
SD-/SSD-Größe, Netzteil und Kühlung nach Projektlast wählen; 4–8 GB RAM und SSD
sind eine Planungsempfehlung, keine vermessene Mindestanforderung.
Kein lokales LLM, Android SDK, Docker oder Desktop gehört zur Host-Baseline.

| Komponente | ARM64-Einstufung | Nachweis / noch offen |
| --- | --- | --- |
| Pi 4 mit Lite-64-bit / Ubuntu ARM64 | sicher kompatible Plattform | ARM64-OS vorgesehen; tatsächlich installiertes Image/Python prüfen |
| Python-Adapter, stdlib, systemd, Git, sudo, curl | sicher ARM64-verfügbar | keine pip-Abhängigkeiten; Adapterablauf auf Pi noch abzunehmen |
| Bewusst ausgewähltes aktuelles/unterstütztes Codex-Release und seine benötigten Helper | ARM64-Paket zum Installationszeitpunkt prüfen; Laufzeit noch zu testen | vollständiges offizielles Release samt verifiziertem Digest; Binary und Helper aus demselben Release |
| Workspace-Rename über `ctypes`/libc `renameat2`, descriptor-relative FS | wahrscheinlich kompatibel | Linux-ABI/Kernel; No-Replace- und sichere Delete-Tests auf Pi erforderlich |
| Caddy / Tailscale, optional | ARM64 verfügbar, gewählter Transport noch zu testen | Distributionspaket/Installation, TLS und SSE auf Pi prüfen |
| Node/MCP-Plugins, optionale Projekttoolchains | wahrscheinlich / einzeln noch zu testen | benötigt nur bei tatsächlich verwendeten Erweiterungen; native npm-Pakete gesondert prüfen |
| Android APK auf Android-Tablet | von Host-CPU unabhängig | nicht auf dem Pi bauen müssen; LAN-Policy/SDK-37-Berechtigung noch offen |
| aktuelle x86_64-Codex-Binaries und `/opt/node-v22.23.2-linux-x64` | inkompatibel als native Pi-Binaries | ARM64 neu installieren; kein Kopieren der VPS-Binaries |

Die datierte VPS-Inventur dokumentiert ausschließlich den damaligen Dienststand,
keine Sollversion für neue Hosts. Zum Installationszeitpunkt ein aktuelles/
unterstütztes offizielles ARM64-Release bewusst auswählen, dessen vollständiges
Paket und Digests prüfen und die Auswahl protokollieren. Verfügbarkeit allein
belegt keine App-Server-/Login-/Toolkompatibilität auf dem Pi; dafür gilt die Abnahme.
Unbekannte native Projektabhängigkeiten bleiben offen.

## 2. Verbindliches Trust-/Permission-Modell

CodexPad ist ein **persönliches Single-User-System auf einem Trusted Host**:

```toml
sandbox_mode = "danger-full-access"
approval_policy = "never"
```

Der Codex-Prozess darf das gesamte Hostdateisystem erreichen, Shell und Netzwerk
nutzen, Pakete installieren/konfigurieren sowie Projekte bauen/starten. Ein
dediziertes Konto mit `NOPASSWD: ALL` ermöglicht erforderliche Rootaktionen über
`sudo -n`. Full Access hebt selbst keine Unix-Dateirechte auf; sudo stellt den
bewussten administrativen Zugriff her. **Gewöhnliche Codex-Shell-, Datei- und
Netzwerk-Approvals dürfen nicht ans Tablet geschickt werden.**
Fachliche Rückfragen (`requestUserInput`) bleiben möglich. Externe
Sicherheitsmechanismen, OAuth-/Drittanbieter-Authentifizierung und zwingende
Systemdialoge bleiben wirksam.

Das ist eine Produktentscheidung, keine vorübergehende Sandbox-Reparatur.
Workspace-Katalog/CWD-Prüfungen bilden keine OS-Isolation; der Tokeninhaber und
der Agent besitzen das Vertrauen des Betreibers. Kein Mehrnutzerbetrieb.
[ADR 0005](docs/decisions/0005-personal-trusted-host.md) hält das ausdrücklich fest;
die App-Server-Schnittstelle (ADR 0001), Reconciliation (0002), Terminaltrennung
(0003) und Netzwerk-Trust-Boundary (0004) bleiben erhalten.

**Die Netzwerkgrenze bleibt separat streng:** App Server nur stdio, Adapter nur
Loopback. Ein optionaler Eingang bindet explizit LAN/Tailscale. Kein öffentliches
Portforwarding, kein Tailscale Funnel und kein ungeschütztes `0.0.0.0`/`[::]`.
Auch eine private Bindung braucht das Bearer-Token.

## 3. Lesender Preflight, Pakete und Repository

Auf dem neuen Host in Bash als Administrator arbeiten. Vor einer Einrichtung
mit vorhandenen CodexPad-Dateien anhalten und erst deren Zustand klären:

```bash
set -euo pipefail
cat /etc/os-release
uname -m
getconf LONG_BIT
python3 --version
sudo systemctl status codexpad --no-pager || true
sudo ss -lntup
test ! -e /etc/codexpad/server.env
test ! -e /etc/systemd/system/codexpad.service
test ! -e /opt/codexpad/server/codexpad_server.py
```

Erwartung auf Pi: `aarch64`, `64`. Die drei `test`-Befehle müssen erfolgreich
sein; bei Fehler keine folgenden Installationsblöcke ausführen.
Die Setupblöcke in derselben Bash aus dem Repository-Root ausführen; Variablen
wie `CODEXPAD_CODEX_BIN` werden in späteren Schritten wiederverwendet. Nach
einem Fehler erst dessen Ursache klären und die benötigten Variablen neu setzen.

```bash
sudo apt-get update
sudo apt-get install --yes python3 git curl ca-certificates sudo tar file
python3 -c 'import sys; assert sys.version_info >= (3, 12), "Python 3.12+ erforderlich"'
```

Repository mit dem eigenen bereits autorisierten Zugang klonen. Beispiel für
öffentlich/anderweitig autorisiertes HTTPS; bei privatem Repo den vorhandenen
SSH-/Credentialmanager verwenden, keinen Token in die URL einbauen:

```bash
git clone https://github.com/krisauseu/codexpad.git codexpad
cd codexpad
git fetch origin integrate/pi-host-docs
git switch --detach origin/integrate/pi-host-docs
git status --short
test -z "$(git status --porcelain)"
CODEXPAD_RELEASE_COMMIT=$(git rev-parse HEAD)
git merge-base --is-ancestor 90a5bc8448461242e9c168907eb916e63f172045 "$CODEXPAD_RELEASE_COMMIT"
printf '%s\n' "$CODEXPAD_RELEASE_COMMIT"
```

Checkout muss diese Anleitung, `server/`, `deploy/` und `docs/reference-vps/`
enthalten. Bis zur Zusammenführung ist `integrate/pi-host-docs` der vorbereitete
Integrationsbranch auf aktuellem `main`; danach kann dessen integrierter Stand
auf `main` bewusst gewählt werden. Den ausgegebenen vollständigen Commit vor
Installation prüfen und protokollieren. Abschnitt 7 installiert genau diesen
Commit mit dem bereits integrierten Adapterverhalten. Der alte Dokumentationsbranch
und sein Basiscommit sind keine Installationsquelle. Kein `latest` oder blindes
`git pull` für eine Erstabnahme.

## 4. Bewusst ausgewähltes Codex-Release installieren

Zum Installationszeitpunkt die bewusst ausgewählte aktuelle/unterstützte
Codex-Version für ARM64 verwenden; keine Versionsnummer aus dem VPS-Inventar
automatisch übernehmen. Vollständiges **Codex-Paket**, einschließlich aller
von dieser Version benötigten Helper wie `codex-code-mode-host` und Ressourcen,
installieren. Dienstbinary und Helper müssen aus demselben Release stammen.
Auswahl, offizielles Asset, Digest und erforderliche Helper vorab protokollieren.

Der folgende Ablauf setzt das Paketlayout `codex-package.json` und `bin/`
voraus. Die vorhandene [Installroutine](deploy/install-codex-standalone.sh)
verlangt derzeit zusätzlich `bin/codex-code-mode-host` und ändert globale
Symlinks. Vor Einsatz prüfen, ob das gewählte Release zu diesem Layout passt;
bei Abweichung anhalten und einen passenden Installationsweg separat prüfen,
keine Helper aus einem anderen Release ergänzen. Node/npm ist für diesen
nativen Standalone-Installationsweg nicht erforderlich. Ausschließlich auf dem
frischen Zielhost benutzen. Der Dienst-PATH wird ausdrücklich auf dieses Release
gesetzt; eine zusätzliche globale CLI ist keine Versionsreferenz.

```bash
CODEXPAD_DOWNLOAD_DIR=$(mktemp -d)
case "$(uname -m)" in
  aarch64|arm64)
    CODEXPAD_CODEX_TARGET=aarch64-unknown-linux-musl ;;
  x86_64)
    CODEXPAD_CODEX_TARGET=x86_64-unknown-linux-musl ;;
  *) echo 'Nur ARM64 oder x86_64 unterstützt' >&2; exit 1 ;;
esac
read -r -p 'Bewusst ausgewählte Codex-Version: ' CODEXPAD_CODEX_VERSION
read -r -p 'Offizielle HTTPS-URL des vollständigen Pakets für dieses Target: ' CODEXPAD_CODEX_URL
read -r -p 'Für dieses Asset verifizierter SHA-256: ' CODEXPAD_CODEX_SHA256
[[ "$CODEXPAD_CODEX_VERSION" =~ ^[A-Za-z0-9._-]+$ ]]
[[ "$CODEXPAD_CODEX_URL" == https://* ]]
[[ "$CODEXPAD_CODEX_SHA256" =~ ^[a-fA-F0-9]{64}$ ]]
curl --fail --location --proto '=https' --tlsv1.2 \
  "$CODEXPAD_CODEX_URL" \
  --output "$CODEXPAD_DOWNLOAD_DIR/codex.tar.gz"
printf '%s  %s\n' "$CODEXPAD_CODEX_SHA256" "$CODEXPAD_DOWNLOAD_DIR/codex.tar.gz" | sha256sum --check -
mkdir "$CODEXPAD_DOWNLOAD_DIR/release"
tar -xzf "$CODEXPAD_DOWNLOAD_DIR/codex.tar.gz" -C "$CODEXPAD_DOWNLOAD_DIR/release"
test -f "$CODEXPAD_DOWNLOAD_DIR/release/codex-package.json"
test -x "$CODEXPAD_DOWNLOAD_DIR/release/bin/codex-code-mode-host"
python3 - "$CODEXPAD_DOWNLOAD_DIR/release/codex-package.json" "$CODEXPAD_CODEX_VERSION" "$CODEXPAD_CODEX_TARGET" <<'PY'
import json, sys
from pathlib import Path
package = json.loads(Path(sys.argv[1]).read_text())
assert package['version'] == sys.argv[2], 'Releaseversion stimmt nicht mit Auswahl überein'
assert package['target'] == sys.argv[3], 'Releasetarget stimmt nicht mit Host überein'
PY
file "$CODEXPAD_DOWNLOAD_DIR/release/bin/codex"
sudo sh deploy/install-codex-standalone.sh "$CODEXPAD_DOWNLOAD_DIR/release"
CODEXPAD_CODEX_BIN="/usr/local/lib/codex/${CODEXPAD_CODEX_VERSION}-${CODEXPAD_CODEX_TARGET}/bin"
"$CODEXPAD_CODEX_BIN/codex" --version
```

Jeder Download-/Checksum-/Installfehler muss vor dem nächsten Befehl geklärt
werden. Erwartung: Ausgabe entspricht der bewusst ausgewählten Version,
ELF AArch64 auf Pi. Das Archivlayout vor Extraktion prüfen; dieser Ablauf erwartet
Paketdateien direkt an der Wurzel. Alle releaseabhängig benötigten Helper prüfen,
nicht nur das hier von der bestehenden Routine verlangte Beispiel.
Installierte Dateien mit den Hashes aus dem verifizierten vollständigen Paket
vergleichen, auch wenn die Routine ein vorhandenes Zielverzeichnis wiederverwendet.
Ausgewählte/installierte Version, Releaseasset/Digest, Binary-Pfad und Helper samt
Hashes im Host-Abnahmeprotokoll festhalten. Anschließend ist der tatsächlich
gestartete Binary-Pfad des systemd-Dienstes maßgeblich. Die x86-64-Binaryhashes
im historischen Inventar gelten nicht für ARM64.

## 5. Konto, Pfade, Workspaces und Hostrechte

```bash
sudo useradd --system --user-group --home-dir /var/lib/codexpad \
  --create-home --shell /usr/sbin/nologin codexpad
sudo install -d -o codexpad -g codexpad -m 0700 \
  /var/lib/codexpad /var/lib/codexpad/.codex /var/lib/codexpad/uploads \
  /srv/codexpad /srv/codexpad/workspaces /srv/codexpad/workspaces/host-smoke
sudo install -d -o root -g root -m 0755 /opt/codexpad
sudo install -d -o root -g root -m 0700 /etc/codexpad
sudo install -o root -g root -m 0440 deploy/codexpad-sudoers /etc/sudoers.d/codexpad
sudo visudo -cf /etc/sudoers.d/codexpad
sudo -u codexpad sudo -n id -u
```

Letzte Ausgabe muss `0` sein. Bei vorhandenem Konto erst Home/Gruppen prüfen,
nicht erneut mit anderen Parametern anlegen. Keine Docker-/sudo-Gruppen nötig.
Ein eigener Workspace ist ein direkter realer Unterordner von
`/srv/codexpad/workspaces`. Bereits vorhandene Pfade mit Threads nicht umbenennen:
Codex speichert absolute CWD-/Upload-/Ergebnisreferenzen. Diese Einrichtung
kopiert keine privaten VPS-Projekte, Sessions oder Credentials.

## 6. Codex konfigurieren und anmelden

Auf dem frischen Dienstkonto die Datei exklusiv anlegen:

```bash
sudo -u codexpad python3 - <<'PY'
from pathlib import Path
path = Path('/var/lib/codexpad/.codex/config.toml')
with path.open('x') as out:
    out.write('sandbox_mode = "danger-full-access"\n')
    out.write('approval_policy = "never"\n')
    out.write('[features]\ndefault_mode_request_user_input = true\n')
path.chmod(0o600)
PY
sudo -u codexpad env HOME=/var/lib/codexpad \
  CODEX_HOME=/var/lib/codexpad/.codex \
  "$CODEXPAD_CODEX_BIN/codex" login --device-auth
sudo -u codexpad env HOME=/var/lib/codexpad \
  CODEX_HOME=/var/lib/codexpad/.codex \
  "$CODEXPAD_CODEX_BIN/codex" login status
```

Browser-/Device-Code-Ablauf als Betreiber abschließen; Codes/Credentials nicht
in Chat oder Git kopieren. Headless-Device-Auth hängt von Kontoeinstellungen ab.
Wenn nicht verfügbar, den offiziellen [Headless-Login](https://learn.chatgpt.com/docs/auth)
über kontrollierten Browser/SSH-Callback verwenden. Keine selbst erfundene
OAuth-Integration. Optionaler API-Key-Login ist ein anderes Abrechnungs-/Authmodell,
nicht der hier beobachtete ChatGPT-Referenzbetrieb; den Key nur verdeckt über
stdin/geschützten Store einrichten, nie in Unit, APK oder Repository.

Kein Profil notwendig; kein Modell hart pinnen. Der Adapter liest `model/list`
und Android erlaubt Modell-/Effort-Auswahl je Turn. Login und Pad-Token sind
unabhängig. Codex-Credentials verbleiben beim Hostkonto; üblicher Dateistore
`$CODEX_HOME/auth.json` (0600), ggf. konfigurierte Credential-Store-Alternative.
Projekt-/Profil-/Managed-Configs bei späteren Änderungen auf Policykonflikte prüfen.
Die expliziten Adapterargumente und Thread-/Turn-Overrides sichern dieselbe
Full-Access-/Never-Policy. Siehe [offizielle Konfiguration](https://learn.chatgpt.com/docs/config-file/config-basic).

## 7. Adaptercode aus dem integrierten v0.1.1-Stand bereitstellen

Nur versionierten Betriebs-Code extrahieren, keine `.git`, Homes, `.env`, Caches
oder Buildausgaben kopieren. Dieser Block installiert den in Abschnitt 3
festgelegten Integrationscommit, einschließlich der aktuellen Deploymentvorlagen:

```bash
CODEXPAD_STAGE=$(mktemp -d)
test -n "$CODEXPAD_RELEASE_COMMIT"
git archive "$CODEXPAD_RELEASE_COMMIT" server deploy | tar -x -C "$CODEXPAD_STAGE"
printf '%s  %s\n' \
  e1adbfcf4b4982d4cc3219deffc1424fc69a9fd5c6f42a2da604b7f8edf5406e \
  "$CODEXPAD_STAGE/server/codexpad_server.py" | sha256sum --check -
sudo cp -a "$CODEXPAD_STAGE/server" "$CODEXPAD_STAGE/deploy" /opt/codexpad/
sudo chown -R root:root /opt/codexpad/server /opt/codexpad/deploy
sudo chmod -R a+rX /opt/codexpad/server /opt/codexpad/deploy
sudo python3 - "$CODEXPAD_RELEASE_COMMIT" <<'PY'
import sys
from pathlib import Path
Path('/opt/codexpad/RELEASE_COMMIT').write_text(sys.argv[1] + '\n')
PY
```

Der [Referenzpatch](docs/reference-vps/2026-10-03-adapter.patch) bleibt unverändert
als historische Evidenz der damaligen VPS-Abweichung erhalten. Der aktuelle
`main`-Produktstand enthält bereits genau diesen Adapterhash und das Verhalten
für Rate-Limit-Route, accountweite SSE-Events und Overflow. **Patch nicht erneut
anwenden**, keine alte Adapterdatei übernehmen und keinen Overlaymarker erzeugen.
Bei einem später bewusst gewählten Produktcommit dessen Adapterhash separat prüfen;
der hier angegebene Hash belegt den v0.1.1-Integrationsstand.
Python nutzt nur die Standardbibliothek. Optional vorhandene Codex-Plugins/Skills
müssen separat installiert, angemeldet und auf ARM64 geprüft werden. Der
Browserless-Pluginprozess des VPS benötigt Node und Drittanbieterzugang; er ist
keine Baseline-Voraussetzung der HTTP-/Thread-/Turn-API. Seine x64-Node-Installation
nicht übernehmen. Drittanbieter-Scopes, Credentialorte und Pluginversionen in einem
privaten Hostprotokoll dokumentieren; keine Plugin-Homes ins Git kopieren.

## 8. Environment und persönliches Pad-Token

Einmalig erzeugen; erneuter Aufruf verweigert das Überschreiben:

```bash
sudo python3 - <<'PY'
import os, secrets
path = '/etc/codexpad/server.env'
fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
with os.fdopen(fd, 'w') as out:
    out.write('CODEXPAD_ACCESS_TOKEN=' + secrets.token_urlsafe(48) + '\n')
    out.write('CODEXPAD_WORKSPACE_ROOT=/srv/codexpad/workspaces\n')
    out.write('CODEXPAD_UPLOAD_ROOT=/var/lib/codexpad/uploads\n')
    out.write('CODEXPAD_PORT=8765\n')
    out.write('CODEX_HOME=/var/lib/codexpad/.codex\n')
PY
```

| Variable | Zweck |
| --- | --- |
| `CODEXPAD_ACCESS_TOKEN` | zufälliges URL-sicheres Bearer-Token, 43–512 Zeichen; zwingend |
| `CODEXPAD_WORKSPACE_ROOT` | direkte Projektordner, autoritative Dateien |
| `CODEXPAD_UPLOAD_ROOT` | dauerhafte private Bildanhänge; Pfade im Verlauf |
| `CODEXPAD_PORT` | Backendport, weiterhin ausschließlich Loopback |
| `HOME` | Diensthome, in Unit gesetzt |
| `CODEX_HOME` | hier explizit gesetzt; VPS nutzt gleichwertigen Default aus HOME |
| `PATH` | gepinnte Codex-Version plus System-/optional Toolbinpfade |
| `PYTHONUNBUFFERED` | unmittelbare Journalausgabe |

**Es gibt keine `CODEXPAD_BIND`-Variable** im aktuellen Code.
Token ausschließlich über einen privat kontrollierten Weg in Passwortmanager
und verdecktes Android-Eingabefeld übertragen. Keine Ausgabe in Diagnose,
Shell-History, Prozessargumenten oder Screenshot; kein `set -x`/Environmentdump.
Serverdatei root:root 0600, Verzeichnis 0700. Der Adapter entfernt das Token vor
Codex-Start aus der Umgebung. Ein Agent mit sudo kann dennoch auf Hostsecrets
zugreifen; das Trust-Modell setzt keine Isolation vor diesem Agenten voraus.

## 9. systemd und privater Codex App Server

```bash
sudo install -o root -g root -m 0644 /opt/codexpad/deploy/codexpad.service \
  /etc/systemd/system/codexpad.service
sudo install -d -m 0755 /etc/systemd/system/codexpad.service.d
printf '[Service]\nEnvironment=PATH=%s:/usr/local/bin:/usr/bin:/bin\n' "$CODEXPAD_CODEX_BIN" | \
  sudo tee /etc/systemd/system/codexpad.service.d/codex-path.conf > /dev/null
sudo systemd-analyze verify /etc/systemd/system/codexpad.service
sudo systemctl daemon-reload
sudo systemctl enable --now codexpad
sudo systemctl status codexpad --no-pager
curl --fail --silent http://127.0.0.1:8765/health
sudo ss -ltnp 'sport = :8765'
```

Erwartung: `ok`, ausschließlich `127.0.0.1:8765`. Die bestehende Unit verwaltet
den Python-Hauptprozess; der Adapter startet **genau seinen eigenen**
`codex app-server --stdio` mit den in Abschnitt 6 beschriebenen `-c`-Overrides,
initialisiert das JSONL-Protokoll und beendet das Kind bei SIGTERM.
Keinen zweiten Daemon, CLI-Textparser, öffentlichen Codex-WebSocket oder
`app-server proxy` als Ersatz einrichten. [App-Server-Protokoll](https://learn.chatgpt.com/docs/app-server).
Kein `experimentalApi:true` nötig für den derzeitigen Adapter; das Feature
`default_mode_request_user_input` bleibt aktiv. Kompatibilität bei Upgrades prüfen.

`PrivateTmp=true` ist der VPS-Baseline entsprechend aktiv, keine Agent-Sandbox.
Keine `ProtectSystem=strict`, `ProtectHome=true` oder `NoNewPrivileges=true`
ergänzen, die die gewünschten Schreib-/sudo-Rechte verhindern würden.
Der Benutzer ist zunächst unprivilegiert, administrative Kommandos verwenden sudo.

## 10. Netzwerk und Android-Verbindung

**Heute im Code:** Android OkHttp → HTTPS/JSON/Multi­part/SSE/Downloads → Caddy →
HTTP Loopback → Adapter → stdio App Server. URL und Token sind in Einstellungen
änderbar; `BuildConfig.SERVER_URL` ist nur der überschreibbare Default.
Redirects und automatische POST-Retries sind deaktiviert. Keine externe
VPS-/Reverseproxy-Abhängigkeit der fachlichen API.

**Direktes `http://192.168.x.x:8765` funktioniert heute nicht:**
Adapter bindet fest Loopback; `ConnectionSettings.kt` akzeptiert HTTP nur in
Debug für `127.0.0.1`, `localhost`, `::1`, `10.0.2.2`. Releasemanifest blockiert
Cleartext, Debug erlaubt ihn zwar grundsätzlich, aber der URL-Validator blockiert
LAN-Adressen weiterhin. Eine Build-Default-Änderung allein reicht nicht.
Die folgende LAN-Variante ist vorbereitet/documentiert, **noch nicht implementiert
oder praktisch abgenommen**, siehe [ADR 0006](docs/decisions/0006-private-host-transport.md).

### A. Gegenwärtig möglicher privater Zugang: Tailscale HTTPS

Optionaler Weg ohne öffentlichen VPS/Proxy, ohne Android-Cleartextänderung:
Tailscale auf Pi und Tablet installieren und mit demselben privaten Tailnet
verbinden. In Tailnet-Regeln nur die eigenen Geräte für diesen Host zulassen;
HTTPS/MagicDNS für Serve aktivieren. Tailscale benötigt seinen Controlplane-/
Loginzugang; es ist eine optionale Dienstabhängigkeit und kein rein offline LAN.
Offizielle [Linuxinstallation](https://tailscale.com/download/linux) und
[Serve](https://tailscale.com/docs/features/tailscale-serve).

```bash
curl --fail --location --proto '=https' https://tailscale.com/install.sh \
  --output /tmp/codexpad-tailscale-install.sh
less /tmp/codexpad-tailscale-install.sh
sudo sh /tmp/codexpad-tailscale-install.sh
sudo tailscale up
sudo tailscale serve --bg http://127.0.0.1:8765
sudo tailscale serve status
```

Die **ausgegebene HTTPS-URL** in Android-Einstellungen eintragen, Pad-Token
neu eingeben, „Verbindung testen“, „Speichern“. Serve bleibt tailnetintern;
**kein `tailscale funnel`**. SSE/Uploads/Downloads und Reconnect auf dem Pi testen.
Falls Target-SDK-37-Netzwerkregeln die konkrete VPN-/Privatroute erfassen, ist
zusätzlich die unten beschriebene Android-Runtimeberechtigung nötig.

### B. Reines LAN über HTTP, späterer minimaler Android-Schritt

Für beliebige konfigurierbare IPs unterstützt Android Network Security Configuration
keine CIDR-Whitelist. Zwei geeignete Varianten:

1. Bei bekanntem dauerhaftem DNS-Namen oder einer festen IP ein
   `<domain-config cleartextTrafficPermitted="true">` nur für diesen Host
   mit `<domain includeSubdomains="false">codexpad-pi.lan</domain>` und
   `<base-config cleartextTrafficPermitted="false"/>` verwenden. Für andere
   IPs ist eine passende Konfiguration/Neubuild erforderlich.
2. Für frei konfigurierbare LAN-/Tailscale-IP-Adressen eine ausdrückliche
   persönliche „Trusted LAN HTTP“-Option in den Verbindungseinstellungen
   implementieren. Dazu eine Network Security Configuration mit
   `base-config cleartextTrafficPermitted="true"` und ergänzende URL-Prüfung:
   HTTP nur mit bewusst aktivierter, an die konkrete URL gebundener Option
   für validierte private IPv4-/IPv6-/Tailnetziele akzeptieren. Keine pauschale
   Freigabe beliebiger öffentlicher HTTP-Hosts, keine Regex für nur `192.168`.
   HTTPS bleibt weiterhin akzeptiert; es braucht keinen Trust-all-TLS-Client.

Beispiel der Plattformdatei für Variante 2, **kein vorhandenes Repositoryfeature**:

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- android/app/src/main/res/xml/network_security_config.xml -->
<network-security-config>
    <base-config cleartextTrafficPermitted="true" />
</network-security-config>
```

Das `<application>` erhält `android:networkSecurityConfig="@xml/network_security_config"`.
Main-/Debugmanifest zusammen prüfen; mit NSC entscheidet die XML-Policy.
Zusätzlich `normalizeServerUrl`, Laden/Speichern in `SettingsStore`, ViewModel
und deren Tests anpassen: bestehender `allowLocalHttp=BuildConfig.DEBUG`-Pfad
erlaubt nur Loopback und genügt nicht. HTTP-Bearer ist im LAN unverschlüsselt;
diese Wahl muss ausdrücklich zum vertrauten lokalen Netz passen.
[Android Network Security Configuration](https://developer.android.com/privacy-and-security/security-config).

Der Adapter kann unverändert Loopback behalten. Ein **lokaler** Proxy ist eine
kleine Betriebsoption, keine externe VPS-Abhängigkeit. Erst nach Androidänderung
auf dem frischen Pi z. B. Caddy installieren und ausschließlich an seine feste
LAN-IP binden. IP im nächsten Block am Prompt eingeben, nicht hardcoden:

```bash
sudo apt-get install --yes caddy
read -r -p 'Feste LAN-IPv4 des Pi: ' CODEXPAD_LAN_IP
python3 - "$CODEXPAD_LAN_IP" <<'PY'
import ipaddress, sys
a = ipaddress.IPv4Address(sys.argv[1])
assert a in ipaddress.IPv4Network('10.0.0.0/8') or a in ipaddress.IPv4Network('172.16.0.0/12') or a in ipaddress.IPv4Network('192.168.0.0/16')
PY
sudo test ! -e /etc/caddy/Caddyfile.codexpad-lan
printf 'http://%s:8876 {\n bind %s\n request_body {\n max_size 23MB\n }\n reverse_proxy 127.0.0.1:8765 {\n flush_interval -1\n }\n}\n' \
  "$CODEXPAD_LAN_IP" "$CODEXPAD_LAN_IP" | sudo tee /etc/caddy/Caddyfile.codexpad-lan > /dev/null
sudo caddy validate --config /etc/caddy/Caddyfile.codexpad-lan --adapter caddyfile
```

Auf einem frischen Host den Distributions-Default durch **diese geprüfte lokale
Site** ersetzen (vorher keine weiteren Sites konfigurieren):

```bash
sudo cp -a /etc/caddy/Caddyfile /etc/caddy/Caddyfile.before-codexpad
sudo install -o root -g root -m 0644 /etc/caddy/Caddyfile.codexpad-lan /etc/caddy/Caddyfile
sudo systemctl enable --now caddy
sudo systemctl reload caddy
sudo ss -lntup
curl --fail --silent "http://$CODEXPAD_LAN_IP:8876/health"
```

Android-URL dann `http://<eingegebene-IP>:8876`. Router-Portweiterleitung/UPnP
und IPv6-Erreichbarkeit prüfen; der LAN-Listener muss ausschließlich diese IP
haben. Falls statt Proxy künftig direkt gebunden werden soll, einen expliziten
validierten Bind-Parameter im Adapter implementieren und testen; `CODEXPAD_PORT`
ändert die Bind-Adresse nicht. Nie einfach Loopback durch `0.0.0.0` ersetzen.

### C. Optional HTTPS im LAN

Lokales Caddy/anderer TLS-Endpunkt mit expliziter LAN-Bindung und für Android
vertrauenswürdiger Zertifikatskette ist ebenfalls möglich. Bei privater CA müssen
CA-Installation und ggf. begrenzte NSC-Trustanker bewusst eingerichtet werden;
Android vertraut Nutzer-CAs nicht automatisch wie System-CAs. Keine deaktivierte
Zertifikatsprüfung. Öffentlich vertrauenswürdiges Zertifikat kann z. B. per
DNS-01 ohne öffentliche Control-Portfreigabe ausgestellt werden; konkreter
DNS-Provider/ACME-Client ist noch nicht ausgewählt. Der VPS-Caddyblock mit
`pad.feichti.dev` ist eine historische Alternative, kein Pi-Default.

### Android 17 / SDK 37: lokale Netzwerkberechtigung

Das Projekt verwendet **compileSdk=37, targetSdk=37**, nicht erst einen
zukünftigen Target-Bump. Im Mainmanifest steht derzeit nur `INTERNET`, kein
`ACCESS_LOCAL_NETWORK`, und es gibt keinen zugehörigen Runtimeablauf.
Nach der aktuellen [Android-LAN-Dokumentation](https://developer.android.com/privacy-and-security/local-network-permission)
ist für direkte lokale Verbindungen unter Android 17 mit Target 37+ die
Deklaration `android.permission.ACCESS_LOCAL_NETWORK` und deren Runtime-Anfrage
vor Zugriff erforderlich; Ablehnung/Widerruf müssen sichtbar behandelt werden.
Auf älteren Android-Versionen den neuen Permissionrequest per API-Level absichern.
Android 16 kann seine LAN-Einschränkung per Kompatibilitätsflag opt-in testen;
die dortige temporäre `NEARBY_WIFI_DEVICES`-Regel nicht mit SDK-37-Verhalten verwechseln.
Dies gilt auch für HTTPS im LAN und ist getrennt von Cleartext sowie Codex-Approvals.
Eine zwingende Android-Systemberechtigung darf nicht durch Codex-`never` umgangen werden.

## 11. Vollständiger Smoke-Test auf dem neuen Host

Die folgenden **mutierenden** Tests wurden auf dem Referenz-VPS bewusst nicht
ausgeführt. Erst auf dem frisch eingerichteten Pi und in `host-smoke` durchführen.
Bis dahin kein „Pi funktioniert vollständig“-Status.

1. Version/Policy/Netzwerk verifizieren:

   ```bash
   "$CODEXPAD_CODEX_BIN/codex" --version
   sudo -u codexpad sudo -n id -u
   sudo systemctl is-enabled codexpad
   sudo systemctl status codexpad --no-pager
   curl --fail --silent http://127.0.0.1:8765/health
   curl --silent --output /dev/null --write-out '%{http_code}\n' http://127.0.0.1:8765/workspaces
   sudo ss -lntup
   ps -eo user,pid,ppid,args | rg 'codexpad_server|codex app-server'
   ```

   Erwartung: Das tatsächlich vom CodexPad-Dienst gestartete ARM64-Codex-Binary
   entspricht der für diesen Host bewusst installierten Version; Binary und
   benötigte Helper stammen aus demselben vollständigen Release. Version,
   tatsächlichen Binary-Pfad und Helper samt Hashes ermitteln/protokollieren
   (siehe [Mac-Testplan](docs/pi-host-testplan.md#3-wirkliches-dienstbinary-login-und-policy-lesend)).
   Zusätzlich UID `0` per sudo, enabled/active, `ok`, `401`, Backend
   nur Loopback, App-Server-stdio-Argumente Full Access/never. Kein offener
   Codex-Port. Keine Paket-/Adminrechte bloß aus der TOML behaupten.

2. Authentifizierte reine Reads ohne Tokenausgabe:

   ```bash
   sudo python3 - <<'PY'
   import json, urllib.request
   from pathlib import Path
   env = dict(line.split('=', 1) for line in Path('/etc/codexpad/server.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
   for route in ('/workspaces', '/models'):
       req = urllib.request.Request('http://127.0.0.1:8765' + route,
           headers={'Authorization': 'Bearer ' + env['CODEXPAD_ACCESS_TOKEN']})
       with urllib.request.urlopen(req, timeout=60) as response:
           data = json.load(response)
           print(route, response.status, {k: len(v) for k, v in data.items() if isinstance(v, list)})
   PY
   ```

3. Repositorytests gegen isolierte Fixtures, ohne Produktionsaccount/Modell:

   ```bash
   python3 -B -m unittest discover -s server -p 'test_*.py' -v
   ```

   Insbesondere Auth/Symlinks, Workspace-Rename ohne Ersetzen,
   `shutil.rmtree.avoids_symlink_attacks`, UserInput und Artefakte müssen auf
   ARM64 bestehen. Fixtures binden eigene lokale Ports und nutzen Tempverzeichnisse.

4. Echter Modell-/SSE-/Reconnecttest ausschließlich im isolierten Smoke-Workspace.
   Das vorhandene `server/smoke.py` nimmt sonst den ersten angebotenen Workspace;
   unten erzwingt der Wrapper `host-smoke` ohne Änderung am laufenden Adapter:

   ```bash
   sudo python3 - <<'PY'
   import importlib.util, os, sys
   from pathlib import Path
   env = dict(line.split('=', 1) for line in Path('/etc/codexpad/server.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
   os.environ['CODEXPAD_ACCESS_TOKEN'] = env['CODEXPAD_ACCESS_TOKEN']
   spec = importlib.util.spec_from_file_location('smoke', '/opt/codexpad/server/smoke.py')
   smoke = importlib.util.module_from_spec(spec)
   spec.loader.exec_module(smoke)
   original = smoke.request
   def request(path, body=None):
       result = original(path, body)
       if path == '/workspaces':
           result['workspaces'] = [w for w in result['workspaces'] if w['id'] == 'host-smoke']
       return result
   smoke.request = request
   sys.argv = ['smoke.py', 'http://127.0.0.1:8765']
   smoke.main()
   PY
   ```

   Erwartung: Live-Textdelta, absichtlicher SSE-Disconnect, neuer Snapshot,
   vollständiger Endtext `CODEXPAD-RECONNECT-OK`, genau der ursprüngliche Turn.
   Dieser Test verbraucht Modellkontingent und legt dauerhafte History an.

5. In Android private URL/Token einstellen; „Verbindung testen“/„Speichern“.
   `host-smoke` öffnen, neuen Thread erstellen. Auftrag:

   ```text
   Führe in diesem Smoke-Workspace eine Shellprüfung aus: id; uname -m;
   sudo -n id -u; teste einen HTTPS-GET auf https://example.com ohne Credentials.
   Schreibe mit sudo ausschließlich /var/tmp/codexpad-host-smoke.txt mit dem
   Inhalt HOST-FULL-ACCESS-OK und lies die Datei zurück. Erstelle im vom Adapter
   angegebenen Ergebnisordner eine UTF-8-Datei smoke.txt mit demselben Inhalt.
   Verändere keine anderen Hostdateien und starte keine weiteren Dienste.
   ```

   Erwartung: ARM64, sudo UID 0, ausgehendes Netz, Außenwrite und Ergebnisdownload
   erfolgreich; **keine** gewöhnliche Command-/File-/Permission-Approvalkarte.
   Ergebnis am Host prüfen: `sudo cat /var/tmp/codexpad-host-smoke.txt`.
   Eine fachliche Rückfrage separat testen und beantworten; sie darf weiter
   erscheinen. Erforderliche Drittanbieter-Logins separat behandeln.

6. App während eines längeren Textturns in Hintergrund/WLAN aus-an bringen,
   zurückkehren: gleicher Turn, History vollständig, kein erneutes `turn/start`.
   Interrupt, Folgeturn, Modell-/Effortwahl, Anhänge und Artefaktdownload testen.
   Nach **abgeschlossenem** Turn `sudo systemctl restart codexpad` und danach
   einen Pi-Reboot durchführen. Dienst/Version/privaten Listener, History und
   neue Turns prüfen. Offene Rückfragen werden nach Serververlust nicht als
   fortsetzbare historische Callbacks behandelt. Android 17: LAN-Berechtigung
   gewähren, verweigern und widerrufen; HTTPS bleibt möglich, fremdes öffentliches
   HTTP bleibt abgelehnt. Netzwerkextern darf der Control-Endpunkt nicht erreichbar sein.

7. Lastprobe mit einem eigenen repräsentativen Build über längere Zeit;
   `free -h`, `df -h`, `journalctl -u codexpad` und auf Pi gegebenenfalls
   `vcgencmd get_throttled` prüfen. RAM/OOM, Temperatur/Throttling, Storagewachstum,
   Zeit bis erstem Modelltoken und SSE-Stabilität notieren. Keine hypothetische
   Pi-Leistungsgarantie. Einen echten Paketinstall-/Dienststartauftrag erst
   gezielt in der neuen Testumgebung prüfen; sudo UID 0 allein belegt dessen
   gesamten Ablauf noch nicht.

## 12. Start, Stop, Restart, Logs und Updates

```bash
sudo systemctl start codexpad
sudo systemctl stop codexpad
sudo systemctl restart codexpad
sudo systemctl status codexpad --no-pager
sudo journalctl -u codexpad -n 100 --no-pager
sudo journalctl -u codexpad -f
sudo journalctl -u caddy -n 100 --no-pager
```

Nur den tatsächlich installierten Proxy verwalten; für Tailscale:
`sudo tailscale serve status`, zum Abschalten `sudo tailscale serve reset`.
Adapterlogs enthalten Startversion/Pfade und Codex-stderr; HTTP-Requestlogging
ist deaktiviert. Keine Caddy-Access-/Debug-/Credentiallogs aktivieren. Codex-Logs
liegen zusätzlich unter `$CODEX_HOME/log` und versionsabhängigen SQLite-Dateien;
sie können private Inhalte enthalten, vor Weitergabe prüfen/redigieren.

Updates bewusst nach abgeschlossenen Turns und privatem Backup durchführen:
neuen Repositorycommit in separatem Checkout prüfen, Tests ausführen,
Code in Staging bereitstellen und erst dann `/opt/codexpad/server` ersetzen und
Dienst neu starten. Releasecommit und tatsächlich installierte Dateihashes notieren;
den historischen Referenzpatch nicht erneut anwenden.
Für Codex neue Version/architekturspezifisches vollständiges Paket samt Digest
prüfen, parallel installieren, PATH-Drop-in gezielt ändern, `daemon-reload`,
Restart und vollständigen Smoke-Test. Keine automatische Versionrotation.
Rollback: vorherigen Code/PATH wiederherstellen und Restart; gespeicherten
Codex-State nicht eigenmächtig downgraden, Formatkompatibilität vorher prüfen.
Stop/Restart beendet Kindprozesse und kann laufende Turns unterbrechen.

Backup separat und verschlüsselt: Workspaces, dauerhafte Uploads, Diensthome/
Codex-State, lokale Konfiguration/Token, ggf. Proxy-State. SQLite/WAL nicht
inkonsistent live zusammensammeln; für konsistente Sicherung Dienst im Wartungsfenster
stoppen. Restore auf den **gleichen absoluten Pfaden** praktisch testen. Eine
spätere VPS-Datenmigration ist ein eigener Auftrag, keine Einrichtungshandlung
dieser Anleitung. Tokenwechsel: geschützte Datei ändern, Dienstrestart beendet
bestehende SSE, neues Token in Android speichern. Keine OpenAI-Rotation nötig.

## 13. Troubleshooting und Automatisierungsgrenze

| Symptom | Prüfung / Abhilfe auf dem neuen Host |
| --- | --- |
| Dienst startet nicht | Journal; Tokenformat 43–512 URL-sichere Zeichen, Pfade/Dateimodi, Python-Version, Kontoauth |
| Shellversion weicht von gewählter Dienstversion ab | explizites Dienst-PATH/Drop-in und `/proc/<Kind-PID>/exe` prüfen; eine zusätzliche globale CLI ist keine Versionsreferenz |
| `Exec format error` | x64-Binary auf ARM64 bzw. 32-bit-OS; komplettes passendes Release installieren |
| Helper fehlt / Code Mode startet nicht | alle von der gewählten Version benötigten Helper wie `codex-code-mode-host` und Ressourcen aus demselben vollständigen Release prüfen, einschließlich Pfaden und ausführbaren Rechten |
| `/health` ok, Agent scheitert | Health prüft nur lebendes Kind, nicht Modelllogin/Toolrechte; Auth und tatsächlichen Turn prüfen |
| 503 bei weiter aktivem Adapter | Codex-Kind ausgefallen; Hauptprozess wird dadurch nicht automatisch restarted; nach Fehlerklärung `systemctl restart codexpad` |
| HTTP-LAN-URL wird abgelehnt | aktueller URL-Validator/Mainmanifest; Abschnitt 10B ist noch umzusetzen, Default-URL ändern reicht nicht |
| HTTP erlaubt, Verbindung trotzdem blockiert | Loopback/Proxybindung, WLAN-Clientisolation, Routing, Firewall, Target-37-LAN-Runtimeberechtigung |
| 401 | richtiges Pad-Token, URL-Bindung im Keystore, anderer Server verlangt erneute Token-Eingabe; OpenAI-Login behebt keine Pad-Auth |
| TLS-/Zertifikatsfehler | Uhrzeit/DNS/Zertifikatskette/Trustanker; nie Zertifikatsprüfung abschalten oder HTTP-Redirect erwarten |
| SSE hängt | Proxyflush/Timeouts; Snapshot/History neu laden; verpasste Events werden nicht vollständig replayed |
| gewünschte Shellaktion erzeugt Approval | Adapter-Start/Thread-/Turnpolicy, Managed-/Projektconfig und Drittanbietermechanismus getrennt prüfen; keine Tablet-Approval-UI als Behelf |
| sudo fragt nach Passwort | sudoers/visudo und `NoNewPrivileges` prüfen; bewusstes `NOPASSWD`-Modell wiederherstellen |
| Workspace fehlt / Rename gesperrt | direkter Unterordner, kein Symlink, Dienstrechte; Threads mit absoluten CWDs blockieren Rename/Delete absichtlich |
| Plugin scheitert | separater Drittanbieterlogin, ARM64-Node/native Pakete, kein x64-Pfad; Basis-HTTP funktioniert ohne dieses Plugin |

Ein monolithisches `setup-host.sh` wird noch nicht eingeführt: Netzwerkweg und
Android-LAN-Vertrag benötigen praktische Abnahme. Der historische VPS hatte einen
nicht im Release-Marker enthaltenen Overlay; dessen Verhalten ist im aktuellen
Produktstand bereits integriert. Blindes Reprovisionieren könnte die
funktionierende Referenz überschreiben. Die vorhandene Standalone-Installroutine,
gepinnten Download-Digests, Unit und expliziten frischen Setupblöcke liefern
bereits reproduzierbare Schritte. Token-/Configanlage verweigert Überschreiben;
der gesamte Ablauf ist keine idempotente Bestandsmigration. Nach Pi-Abnahme kann
eine Provisionierung mit Fresh-Host-Guard, Versionsprüfung, atomarem Install und
getrenntem Auth-/Netzwerkschritt daraus abgeleitet werden.
