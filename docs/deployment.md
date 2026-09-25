# Persönlicher HTTPS-Betrieb auf pad.feichti.dev

Stand: 25. September 2026. Lokale Implementierung und Vorlagen; **noch kein DNS-/VPS-Deployment**.

Android → `https://pad.feichti.dev` → Caddy → `127.0.0.1:8765` → privater
`codex app-server --stdio`-Kindprozess. Ein persönliches Token, keine Nutzerverwaltung.
Vorlagen: [`deploy/`](../deploy/). Der ältere Inhalt von `betrieb.md` beschreibt ein
anderes Produkt und ist keine CodexPad-Betriebsanleitung.

## Exakt nächster Deployment-Schritt: lesender VPS-Preflight

Zunächst SSH-Ziel und öffentliche IPv4/gegebenenfalls IPv6 des vorgesehenen VPS
festlegen. Dann mit dem vorhandenen SSH-Zugang ausschließlich den Bestand prüfen:

```sh
ssh <VPS-SSH-Ziel> 'id; uname -a; python3 --version; command -v codex; codex --version; command -v caddy; systemctl --version; systemctl status codexpad caddy --no-pager; ss -ltnp'
```

Prüfen: Python 3.12+, systemd, Codex-Version (bisheriger Protokollnachweis: 0.156.1),
Caddy 2, bestehender Dienst/Port 8765, bestehende Caddy-Sites, gewähltes Dienstkonto,
Workspace-Speicherort und freier HTTPS-Port. Nicht blind einen vorhandenen Dienst oder
eine bestehende Caddyfile überschreiben. Erst danach folgen die einmaligen Änderungen.
Ein bereits vorhandener Codex-Login eines anderen Linux-Kontos ist nicht automatisch
für das neue Dienstkonto gültig.

## Einmalige VPS-Einrichtung

Die Vorlage verwendet `/opt/codexpad` (Code, root-owned), `/var/lib/codexpad`
(Codex-Accountzustand) und `/srv/codexpad/workspaces` (direkte Unterordner = Projekte).
Für einen bestehenden dedizierten persönlichen Dienstbenutzer können Pfade und User
bewusst angepasst werden. Nicht unter root betreiben.

1. Python, Caddy 2 und die mit diesem API-Protokoll geprüfte Codex-CLI installieren.
   Die CLI muss für den Dienst über `/usr/local/bin:/usr/bin:/bin` erreichbar sein.
   Falls sie in einem anderen Pfad liegt, `Environment=PATH=...` anpassen.
2. Falls das Konto noch nicht besteht:

   ```sh
   sudo useradd --system --user-group --home-dir /var/lib/codexpad --create-home --shell /usr/sbin/nologin codexpad
   sudo install -d -o codexpad -g codexpad -m 0700 /var/lib/codexpad /srv/codexpad /srv/codexpad/workspaces
   sudo install -d -o root -g root -m 0755 /opt/codexpad
   sudo install -d -o root -g root -m 0700 /etc/codexpad
   ```

3. Den geprüften lokalen Stand nach `/opt/codexpad` übertragen, ohne `.git`, `.env`,
   `.local`, Buildausgaben oder Secrets. Es genügen `server/` und `deploy/` für den
   Betrieb. Code und Service-Datei bleiben root-owned und für `codexpad` lesbar.
   Projektverzeichnisse bewusst unter dem Workspace-Root anlegen/übertragen und
   dem Dienstkonto zugänglich machen. Keine Systemverzeichnisse als Workspace wählen.
4. Unter dem Dienstkonto Codex anmelden, z. B.:

   ```sh
   sudo -u codexpad env HOME=/var/lib/codexpad /usr/local/bin/codex login --device-auth
   sudo -u codexpad env HOME=/var/lib/codexpad /usr/local/bin/codex login status
   ```

   Den Pfad dem Preflight anpassen. Codex-Accountauth ist unabhängig vom Pad-Token.
5. **Einmalig** das Token auf dem VPS generieren und direkt in eine ausschließlich
   root-lesbare Konfigurationsdatei schreiben. Dieser Befehl druckt kein Token,
   übergibt es nicht als Prozessargument und verweigert das Überschreiben:

   ```sh
   sudo python3 - <<'PY'
   import os, secrets
   path = '/etc/codexpad/server.env'
   fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
   with os.fdopen(fd, 'w') as out:
       out.write('CODEXPAD_ACCESS_TOKEN=' + secrets.token_urlsafe(48) + '\n')
       out.write('CODEXPAD_WORKSPACE_ROOT=/srv/codexpad/workspaces\nCODEXPAD_PORT=8765\n')
   PY
   ```

   Das Token über einen privaten, vom Betreiber kontrollierten Weg in den
   Passwortmanager und in das verdeckte Android-Eingabefeld übertragen. Nicht in
   Chat, Tickets, Shell-History, Screenshots, Buildparameter oder Diagnoseausgaben
   kopieren. `server.env.example` bleibt absichtlich ohne verwendbares Token.
6. Unit installieren und zunächst nur lokal prüfen:

   ```sh
   sudo install -o root -g root -m 0644 /opt/codexpad/deploy/codexpad.service /etc/systemd/system/codexpad.service
   sudo systemd-analyze verify /etc/systemd/system/codexpad.service
   sudo systemctl daemon-reload
   sudo systemctl enable --now codexpad
   sudo systemctl status codexpad --no-pager
   curl --fail --silent http://127.0.0.1:8765/health
   curl --silent --output /dev/null --write-out '%{http_code}\n' http://127.0.0.1:8765/workspaces
   sudo ss -ltnp 'sport = :8765'
   ```

   Erwartung: `{"status":"ok"}`, dann `401`, Listener ausschließlich
   `127.0.0.1:8765`. Bei fehlendem/ungültigem Token startet der Dienst nicht.
   Keine Firewallfreigabe für 8765 oder einen Codex-App-Server-Port erstellen.

`CODEXPAD_WORKSPACE_ROOT` ist konfigurierbar. Bei einem Root außerhalb
`/srv/codexpad` zusätzlich über `systemctl edit codexpad` dessen Pfad in
`[Service] ReadWritePaths=/absoluter/pfad` ergänzen und Dateirechte prüfen. Die Unit
schützt Systemverzeichnisse und Home-Verzeichnisse; `/var/lib/codexpad`, `/srv/codexpad`
und temporärer Speicher bleiben beschreibbar. Diese Einschränkung ist keine
Mandantenisolation. Die Agent-Sandbox muss auf dem konkreten VPS weiterhin funktionieren;
sie wird bei Fehlern nicht pauschal abgeschaltet.

## DNS und Caddy aktivieren

1. DNS-A-Record `pad.feichti.dev` auf die bestätigte VPS-IPv4 setzen. AAAA nur setzen,
   wenn IPv6 tatsächlich bis Caddy funktioniert; einen veralteten AAAA korrigieren.
2. Eingehend TCP 80 und 443 für Caddy freigeben (Host-/Provider-Firewall). SSH-Zugang
   erhalten. Caddy benötigt ausgehend DNS und HTTPS für die Zertifikatsausstellung.
3. Den Site-Block aus `deploy/Caddyfile` in die vorhandene Caddy-Konfiguration aufnehmen
   bzw. als eigene Datei importieren. Die gesamte Konfiguration prüfen, dann laden:

   ```sh
   sudo caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile
   sudo systemctl reload caddy
   curl --fail --silent https://pad.feichti.dev/health
   curl --silent --output /dev/null --write-out '%{http_code}\n' https://pad.feichti.dev/workspaces
   ```

   Erwartung: vertrauenswürdiges HTTPS, minimaler Health-Status und `401` ohne Token.
   Caddy verwaltet Zertifikate automatisch. `Authorization` wird unverändert an
   CodexPad weitergereicht; SSE wird sofort weitergegeben. Keine Access-Logs für diese
   Site, kein `debug`, kein `log_credentials`, keine Header-/Body-Dumps aktivieren.
   Bei bestehenden globalen Log-Regeln die Site ausdrücklich davon ausschließen.
4. Android-APK installieren/aktualisieren, Einstellungen öffnen, `https://pad.feichti.dev`
   und Token eingeben → **Verbindung testen** → **Speichern**. Test prüft sowohl
   authentifizierten Workspace-Zugriff als auch Backend-Health. Danach bei entferntem
   ADB-Reverse und beendetem Mac-Tunnel über WLAN oder Mobilfunk einen Thread öffnen,
   einen Turn senden, Live-Text, Hintergrund/Rückkehr und Reconnect prüfen. Ein
   erfolgreicher `/health`-Abruf allein ist keine vollständige Abnahme.

## Updates, Tokenwechsel und Betrieb

- Code austauschen, lokal getestete Version dokumentieren, `systemctl restart codexpad`.
  `KillMode=control-group` beendet auch Kindprozesse; der HTTP-Server räumt beim SIGTERM
  seinen stdio-Kindprozess auf. Laufende Turns können dabei unterbrochen werden.
- Tokenwechsel: neues zufälliges Token kontrolliert in der geschützten Datei setzen,
  Dienst **neu starten** (beendet auch bestehende SSE-Verbindungen), neues Token in
  Android speichern. Es gibt genau ein gültiges Token, keine Übergangsfrist.
- Secrets bleiben außerhalb des Repositories. Die Unit liest die root-owned Datei;
  der Server entfernt das Token aus `os.environ`, bevor er Codex startet. Es bleibt
  zur Prüfung im Speicher des API-Prozesses. Kein Schutzversprechen gegenüber root
  oder kompromittierten Prozessen desselben Dienstkontos.
- Keine HTTP-Request-/Header-/Query-Logs im Python-Server. Fehlertexte enthalten keine
  Requestdaten. Codex-stderr wird auf das aktuelle Token redigiert. Beim Support keine
  Konfigurations- oder Umgebungsdumps erzeugen.
- Backup: Workspaces und Codex-Zustand separat und verschlüsselt sichern; Server-Token
  privat verwahren. Android-Backups/Device-Transfer sind deaktiviert. Verlorener
  Keystore-Schlüssel erfordert erneute Token-Eingabe, es gibt keinen Klartext-Fallback.
- Tokeninhaber kann alle konfigurierten persönlichen Workspaces steuern. Workspace-
  Katalog und Thread-CWD-Prüfung beschränken die angebotenen Routen, bilden aber keine
  OS-Sandbox. Keine fremden Nutzer/Workloads auf dieses Token-Modell aufsetzen.

Referenzen: [Android Keystore](https://developer.android.com/privacy-and-security/keystore),
[Caddy reverse_proxy und Streaming](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy),
[systemd.exec](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html).
