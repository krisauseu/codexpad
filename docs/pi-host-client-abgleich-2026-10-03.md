# Pi-Host: Synchronisierung mit dem lokalen Client

> Fortschreibung nach der Abnahme am 3. Oktober 2026: Raspberry Pi 4,
> Ubuntu 26.04.1 LTS ARM64 und echtes Android-Tablet sind über privates LAN
> End-to-End bestätigt. [Host-/Mac-Status](verification-pi-host-2026-10-03.md),
> [Android-/Trusted-LAN-Prüfbericht](../android/VERIFICATION-TRUSTED-LAN.md).
> Der folgende Text beschreibt die Vorbereitung vor dieser Abnahme.

Stand: 3. Oktober 2026. Analyse und Vorbereitung; keine Client-/Serveränderung,
kein Release, Commit oder Push, keine Verbindung zum Pi oder VPS ausgeführt.
Der [Mac-Testplan](pi-host-testplan.md) enthält die spätere Abnahme.

Integrationsfortschreibung vom 3. Oktober 2026: Die vorbereiteten Dokumente sind
auf `integrate/pi-host-docs` auf Basis von `main@90a5bc8` zusammengeführt.
Produktcode und v0.1.1-Dokumentation bleiben erhalten. HOST_SETUP installiert
jetzt den bewusst festgelegten Integrationscommit; der Referenzpatch bleibt
ausschließlich historische Evidenz und wird nicht erneut angewendet. Die
folgenden Analysebefunde und damaligen nächsten Schritte bleiben als datierter
Vorbereitungsstand erhalten; der Integrationsschritt ist damit erledigt.

## Git-Stand und Vergleichsgrundlage

Vor Beginn war `/Users/kf/codexpad` sauber auf `main`, Commit
`90a5bc8448461242e9c168907eb916e63f172045`, Tag `v0.1.1`, gleich `origin/main`.
Die letzten fünf Commits waren `90a5bc8`, `b98538a`, `827d774`, `b4ef835`,
`1186b7c`. Origin verwendet HTTPS (`https://github.com/krisauseu/codexpad.git`)
für Fetch und Push; es ist dasselbe Repository wie die angegebene SSH-URL.
Die Remote-URL wurde nicht geändert.

`git fetch origin` war erfolgreich. Der neu geholte Remote-Branch und der
ausgecheckte Trackingbranch `docs/raspberry-pi-host` zeigen exakt auf
`cb60ffd2a9f8b65c7b11abb43ff040daa2fa8e35`. Dieser einzelne Commit enthält
die angekündigten 16 Dateien. Die vorherige lokale Arbeit wurde nicht verändert.

**Der Dokumentationsbranch ist kein Nachfolger des lokalen v0.1.1-Stands.**
Gemeinsame Basis ist `1186b7c9e1d3ba464168814a4a434e7266b8a15a`;
`main` hat vier zusätzliche Commits, der Dokumentationsbranch einen.
Ein Vergleich `main..docs/raspberry-pi-host` zeigt deshalb auch ältere
Client-/Serverdateien und fehlende v0.1.1-Dateien. Das sind keine 16 neuen
Produktänderungen. Der Branchwechsel ist für das Lesen sicher, ein blindes
Ersetzen von `main` durch diesen Branch wäre keine geeignete Integration.
Keine Zusammenführung oder Konfliktauflösung wurde versucht.

Der folgende Clientbefund verwendet ausdrücklich `main@90a5bc8`, gelesen
über `git show`/`git grep`. Einstellungen, Manifeste, SSE-Parser,
ArtifactCard, TransferPolicy und Deploymentvorlagen sind in beiden Ständen
identisch. API und Session haben auf `main` zusätzliche v0.1.1-Statuslogik.
Keine APK aus dem älteren Dokumentationsbranch als v0.1.1 bauen/installieren.

## Vollständig gelesene Grundlage

- [README](../README.md), [HOST_SETUP](../HOST_SETUP.md), [VISION](../VISION.md)
- [Offene Fragen](product/open-questions.md)
- [Remote Workspaces](research/remote-workspaces.md),
  [Codex-Integration](research/codex-integration.md),
  [Android-Plattform](research/android-platform.md)
- [VPS-Inventar](vps-inventory-2026-10-03.md)
- [ADR 0004](decisions/0004-client-host-trust-boundary.md),
  [ADR 0005](decisions/0005-personal-trusted-host.md),
  [ADR 0006](decisions/0006-private-host-transport.md)
- [Referenzpatch](reference-vps/2026-10-03-adapter.patch), außerdem Deployment,
  Server-README, Unit, Caddyvorlage und relevanter Client-/Adapter-/Smoke-Code

Historische Researchtexte sind durch die Fortschreibungen eingeordnet.
Für den persönlichen Pi gelten HOST_SETUP und Accepted ADR 0005;
ADR 0006 ist für die technische LAN-Umsetzung weiterhin Proposed.

## Tatsächlicher Hostvertrag

```text
Android → HTTPS/Caddy → Python 127.0.0.1:8765
        → privater codex app-server --stdio → Tools/Workspaces/OpenAI
```

Der App Server ist ein langlebiges Kind von `codexpad.service`, kein weiterer
Netzwerkdienst. Android nutzt weder SSH noch einen Codex-WebSocket.
SSH vom Mac ist eine zusätzliche Betriebskontrolle.

- Dienstaccount `codexpad`, Home `/var/lib/codexpad`, Codex-State `.codex`,
  Workspaces `/srv/codexpad/workspaces`, dauerhafte Bilder
  `/var/lib/codexpad/uploads`, Code `/opt/codexpad/server`.
- Die historische Inventur vom 3. Oktober 2026 belegte beim damaligen VPS-Dienst
  **0.160.0** über PATH-Drop-in, keine Sollversion für den Pi. Der damalige
  globale Altbestand gehörte nicht zum CodexPad-Dienst und ist heute irrelevant.
  Zum Installationszeitpunkt die bewusst ausgewählte aktuelle/unterstützte
  ARM64-Version als vollständiges Release samt allen benötigten Helpern wie
  `codex-code-mode-host` installieren. Dienstbinary und Helper aus demselben
  Release; tatsächlichen systemd-Binary-Pfad, Version und Helper protokollieren.
  x64-Binaries und VPS-Nodepfade sind nicht übertragbar.
- `danger-full-access` und `never` gelten bei Prozessstart, Threadstart und
  Turnstart. Passwortloses sudo ist bewusst erforderlich. `PrivateTmp=true`
  ist keine Agent-Sandbox. Keine zusätzlichen systemd-Sperren einführen,
  die dieses Modell verhindern. Fachliches `requestUserInput` bleibt erhalten.
- Pad-Bearer und hostseitiger ChatGPT-Login sind getrennt. Fachliche Routen,
  SSE und Downloads sind authentifiziert; nur `/health` ist frei.
  Health belegt ein lebendes Kind, keinen erfolgreichen Login/Modellturn.
- Der Adapter bindet fest Loopback. `CODEXPAD_PORT` ist nur der Backendport;
  `CODEXPAD_BIND` existiert nicht. Pi-Eingang privat, keine öffentliche
  Weiterleitung. HOST_SETUP nennt LAN-Caddy `172.16.16.39:8876` als passend
  konkretisierbare Variante; Backend bleibt `127.0.0.1:8765`.
- Ein Kindprozessausfall kann `/health=503` erzeugen, während systemd den
  Python-Hauptprozess noch als aktiv sieht. Kein automatischer Kindrestart.
- Absolute Workspace-/Upload-/Ergebnispfade sind Bestandteil der Historie.
  VPS-State-Migration ist ein eigener Schritt; SQLite/WAL nicht live kopieren.

Der VPS-Marker `1186b7c` unterschlägt den Overlay für `/account/rate-limits`,
accountweite SSE-Events und threadunabhängigen Overflow. Lokal verifiziert:
**Der Adapter auf `main@90a5bc8` hat bereits exakt den inventarisierten Hash**
`e1adbfcf4b4982d4cc3219deffc1424fc69a9fd5c6f42a2da604b7f8edf5406e`.
Der Patch ist dort integriert; nicht nochmals anwenden.
Die damals gelesene HOST_SETUP-Fassung reproduzierte Basiscommit plus Patch
in Staging. Die integrierte Anleitung verwendet jetzt den aktuellen Produktstand
als geprüften Repositorycommit ohne erneute Patchanwendung.

## Android-Abgleich (Code auf main@90a5bc8)

Die Zeilenangaben beziehen sich auf diesen Commit, nicht auf den älteren Checkout.
Pfade unter `android/app/src/main/java/dev/codexpad/` sind unten verkürzt.

| Prüffrage | Codebefund | Konsequenz für Pi |
| --- | --- | --- |
| 1. Serveradresse | `ui/CodexPadApp.kt:403` Einstellungen; `ui/PadViewModel.kt:87` Kandidat, `:98` Test, `:122` Speichern; `settings/SettingsStore.kt:29` Laden | Laufzeitkonfigurierbar. Gradle `codexpad.serverUrl`/`BuildConfig.SERVER_URL` ist nur Default, aktuell `https://pad.feichti.dev`; gespeicherte URL gewinnt. |
| 2. Nur HTTPS? | `settings/ConnectionSettings.kt:8` akzeptiert HTTPS; HTTP nur mit `allowLocalHttp` für `127.0.0.1`, `localhost`, `::1`, `10.0.2.2` | Release nur HTTPS; Debug zusätzlich diese lokalen Ziele. `http://172.16.16.39:8876` wird in beiden Builds abgelehnt. |
| 3. HTTP-Blockaden | Validator wird bei Laden und Test/Speichern mit `BuildConfig.DEBUG` verwendet. Mainmanifest `usesCleartextTraffic=false`, Debugmanifest überschreibt auf true. | Zwei unabhängige Ebenen. Ein anderer Build-Default oder Debugmanifest allein reicht nicht. |
| 4. NSC vorhanden? | Keine `network_security_config.xml`, kein Manifestverweis; vorhandene XML betreffen Backup und FileProvider. | LAN-Freigabe als eigener kleiner Schritt erforderlich. |
| 5. SSE | `network/CodexPadApi.kt:152` OkHttp `callbackFlow`, Bearer, `Accept: text/event-stream`, zeilenweiser UTF-8-Parser. Keine Gesamtdeadline, nach Aufbau 45 s Lesetimeout; Serverheartbeat 15 s. | Proxy muss sofort flushen, Content-Type erhalten und lange Streams zulassen. Parser ignoriert Kommentare/id/retry; kein Event-Replay. |
| 6. Token | `network/CodexPadApi.kt:50` zentraler Bearer-Header, auch Multipart/SSE/Download. SettingsStore AES-256-GCM/Keystore, URL als AAD, kein Token in APK/Query. | Neue URL verlangt erneute Token-Eingabe. HTTP überträgt den Bearer unverschlüsselt. OpenAI-Credentials bleiben auf Pi. |
| 7. Upload/Download | SAF `OpenMultipleDocuments`, ContentResolver und begrenztes Einlesen im ViewModel; Multipart am Turnendpoint. Artefakt-GET lädt in privaten Cache, prüft Länge/MIME, Öffnen über FileProvider, Speichern über `CreateDocument`. | Kein DownloadManager-/Browserweg mit verlorener Auth, keine Pfad-/Transportumstellung nötig. Proxy muss Content-Length/MIME und Bodylimit erhalten. |
| 8. Host/Port/Transport | OkHttp baut Routen mit `addPathSegment` relativ zur gewählten Basis. Explizite HTTPS-Ports möglich. Basis ohne Userinfo, Query, Fragment oder Unterpfad. Redirects und Transportretry deaktiviert. | Kein festes VPS-Ziel im API-Code, kein fixer Clientport. Proxy muss API an `/` anbieten; HTTP→HTTPS-Redirect genügt nicht. `pi-codexpad` ist nur Mac-SSH-Alias, kein Android-DNS-Name. |
| 9. SDK/LAN | compile/target 37, min 26. Mainmanifest nur INTERNET; kein ACCESS_LOCAL_NETWORK, Runtime-Request oder spezifischer Fehlerzustand. | Unter Android 17 direkte LAN-Sockets zusätzlich erlaubnispflichtig, auch HTTPS. Aktuell drohen generische Timeouts/Netzfehler. Tablet-OS und tatsächliche APK separat erfassen. |
| 10. Beide URL-Arten | Vorhandene Transport-/Transferlogik kann HTTP und HTTPS; Freigabe fehlt im Einstellungs-/Plattformvertrag. | Validator, SettingsStore, ViewModel/UI, NSC/Manifeste und LAN-Permission gezielt erweitern; bestehende SSE-/Turnlogik beibehalten. |

Transfers: bis vier PNG/JPEG/WebP à 5 MiB und zwei UTF-8 TXT/MD/HTML/HTM
à 64 KiB; Nachrichten 12.000 Unicode-Zeichen. Adapterbody 21 MiB,
Caddy 23 MB. Ergebnisdateien bis 64 MiB; kein Transfer-Resume.
Bilder bleiben auf Host erhalten, Textanhänge gehen als Textinputs an Codex.
Artefakte sind turngebundene aktuelle Dateien, kein historisches Dateiarchiv.

Reconnect in `data/ThreadSession.kt:203`: Snapshot/History vor SSE,
neuer initialer SSE-Snapshot, Poll 5 s bei Arbeit/15 s sonst, Backoff 1–15 s.
Vordergrundbindung über `repeatOnLifecycle(STARTED)`; keine Background-Pushzusage.
Mutationen werden nicht automatisch wiederholt. v0.1.1 liest zusätzlich
Rate-Limits periodisch und verarbeitet accountweite Events; der inventarisierte
Adapter unterstützt das. Unpatched `1186b7c` hätte diese Statusroute nicht.

## Gezielter späterer LAN-Schritt, noch nicht implementiert

Für die feste IP kann NSC eine eng begrenzte Hostfreigabe erhalten. Für frei
konfigurierbare private IPs braucht es gemäß HOST_SETUP eine ausdrückliche,
an die normalisierte URL gebundene Trusted-LAN-HTTP-Option plus App-URLpolicy;
NSC hat keine CIDR-Regeln. `172.16.16.39` liegt im privaten `172.16.0.0/12`.
Private Adressen vollständig parsen, nicht nur `192.168` erkennen; IPv6,
Tailnet und ggf. Namensauflösung/DNS-Wechsel bewusst behandeln. Eine globale
Cleartextfreigabe darf nicht ohne diese App-Prüfung wirksam werden.

Die Option beim Laden/Test/Speichern konsistent behandeln und bei URLwechsel
erneut verlangen. TLS-Prüfung, URL-AAD, Auth auf allen Routen und deaktivierte
Redirects/POST-Retries erhalten. Permissionprüfung muss auch wiederhergestellte
Sessions, Reconnect und Transfers abdecken; nicht nur den Testbutton.
Unter Android 17 Manifestdeklaration plus Runtime-Grant/Denial/Revocation;
ältere Geräte per API-Level absichern. LAN-Erlaubnis und Cleartext sind getrennt.

Google-Primärquellen, am 3. Oktober 2026 geprüft:
[LAN-Permission](https://developer.android.com/privacy-and-security/local-network-permission)
(Stand der Seite 2. Oktober; SDK 37/Android 17, Android 16 nur Opt-in) und
[Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)
(Cleartext und begrenzte Trustanker). HTTPS mit privater CA braucht bewusst
konfiguriertes Vertrauen; keine Zertifikatsprüfung abschalten. Die konkrete
VPN-/Tailnetroute praktisch auf dem Tablet prüfen.

## A. Bereits geklärt

- Tatsächliche stdio-Architektur, Dienstaccount, Pfade und Token-/Accounttrennung.
- Full Access/never/sudo als akzeptiertes persönliches Betriebsmodell.
- Historischer VPS-Dienstbinary-Pfad und reproduzierbarer Adapteroverlay;
  tatsächliches Dienstbinary ist die Referenz, keine zusätzliche globale CLI.
- Aktueller main-Adapter entspricht bytegenau der inventarisierten VPS-Datei.
- Kein externer VPS für den privaten Pi-Eingang notwendig; Clientadresse frei
  konfigurierbar, API-/SSE-/Transfervertrag passt grundsätzlich.

## B. Auf dem Raspberry Pi praktisch zu validieren

- ARM64-OS/Python 3.12+, vollständige Binaries/Helper und echte Toolausführung.
- Login unter Dienstkonto, sudo/Policy, ausgehendes Netz und Modellzugriff.
- systemd, Kindlifecycle, privater Listener, SSH, Reboot und History.
- ARM64-Filesystemtests, SSE/Proxy/Reconnect, Transfers und reale Dauerlast.
- RAM/OOM, Temperatur/Throttling, Storage, optionale ARM64-Plugins;
  externe Nichterreichbarkeit und spätere Backup-/Restore-Abnahme.

## C. Android-/LAN-Themen

- LAN-HTTP-Option/URLvalidierung und NSC/Manifest fehlen.
- ACCESS_LOCAL_NETWORK mit Runtimeablauf fehlt trotz Target 37.
- TLS/DNS/VPN, URLwechsel mit erneuter Token-Eingabe und beide Buildtypen testen.
- Tablet-OS/APK erfassen; Transfers, fachliche Rückfragen und Reconnect gegen Pi
  abnehmen. SDK-37-Systemberechtigung bleibt unabhängig von Codex-`never`.

## Konkrete nächste Schritte

1. Mac-SSH-Alias `pi-codexpad` für `172.16.16.39` mit tatsächlichem Adminkonto/
   Schlüssel einrichten, Hostkey prüfen; lesenden Preflight aus dem Testplan.
2. Die 16 Dokumentationsänderungen gezielt mit aktuellem main integrieren;
   v0.1.1-Code bewahren, Dokumentationskonflikte prüfen. Dies wurde noch nicht getan.
3. Geeignetes 64-bit-OS/Python bestätigen; frischen Host nach HOST_SETUP mit
   der zum Installationszeitpunkt bewusst ausgewählten aktuellen/unterstützten
   ARM64-Codex-Version als vollständigem Release samt passenden Helpern,
   Dienstkonto, Policy, sudo, Login, Token und Unit aufbauen.
   Adaptercommit und tatsächlich installierten Hash explizit notieren. Nicht unbemerkt ältere Dateien
   aus dem Dokumentationsbranch über aktuelle Produktdateien installieren.
4. Zuerst Backend über SSH kontrollieren. Für unveränderte Client-URLpolicy ist
   privates HTTPS möglich; LAN-HTTP benötigt den oben beschriebenen kleinen
   Android-Schritt. LAN-Permission unter Android 17 betrifft beide Wege.
5. Mac-/Tablet-Abnahme mit `host-smoke`, erst danach produktive Projekte nutzen.
   Bis zur VPS-Abschaltung separat entscheiden, welche Projekte/History/Uploads
   gesichert und migriert werden sollen; gleichbleibende absolute Pfade beachten.
