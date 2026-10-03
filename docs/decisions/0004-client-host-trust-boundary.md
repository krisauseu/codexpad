# ADR 0004: App-Server-Client als Host-Trust-Boundary behandeln

Datum: 18. September 2026. Status: Accepted. Konkretisiert am 25. September 2026 für persönlichen Single-User-HTTPS-Zugriff.

## Kontext und Evidenz

[Spike A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md) schrieb mit `fs/writeFile` ohne Approval außerhalb des Thread-Workspaces, aber innerhalb des Testverzeichnisses, während ein Read-only-Thread geladen war. Die eigene Host-API `process/spawn` startete unsandboxiert, auch ohne Modell-Accountlogin auf einer neuen lokalen Instanz. Es wurde kein Remotezugriff geprüft und kein vollständiger OS-Rechteumfang vermessen.

## Entscheidung

Ein direkt verbundener App-Server-Steuerungsclient erhält Hostfähigkeiten, die nicht automatisch durch die Agent-Sandbox begrenzt sind. Remotezugriff darf deshalb nicht allein mit deren Sicherheit begründet werden. Transport-Clientauthentifizierung, Modell-Accountauth und Autorisierung der Host-/Workspace-Fähigkeiten sind getrennte Grenzen. `cwd` und eine eingeschränkte Dateiansicht sind keine Isolation.

Die Alternative, Agent-Approvals als ausreichenden Schutz aller Client-RPCs zu betrachten, widerspricht dem Experiment.

## Folgen und erneute Prüfung

Vor einer Remote-Android-Verbindung sind minimale Host-Capabilities, gesicherter Transport, Authentifizierung und tatsächlich erzwungene Workspace-Grenzen festzulegen und zu prüfen. SSH, TLS/WSS, ein eingeschränktes Linux-Konto, äußere Isolation oder ein begrenzender Dienst sind damit noch nicht gewählt. Bei konkretem Transportentwurf, geändertem RPC-Rechteumfang oder Mehrnutzerbedarf erneut prüfen.


## Konkretisierung: persönlicher Remote-Slice (25. September 2026)

Android spricht HTTPS mit Caddy, Caddy ausschließlich mit der CodexPad-API auf
`127.0.0.1:8765`. `codex app-server --stdio` bleibt interner Kindprozess ohne
öffentlichen Listener. Ein langes zufälliges Bearer-Token authentifiziert sämtliche
fachlichen API-Routen einschließlich SSE; nur ein minimaler `/health`-Status ist
öffentlich. Der Server startet ohne gültig konfigurierte Zugangsdaten nicht.

Das Token liegt serverseitig in einer root-lesbaren Environment-Datei außerhalb des
Repositories und wird nicht an den Codex-Kindprozess vererbt. Android speichert es
AES-GCM-verschlüsselt mit einem Android-Keystore-Schlüssel und bindet es an die
Serveradresse. Der Client folgt keinen Redirects; unverschlüsseltes HTTP ist nur für
lokale Debug-Verbindungen erlaubt. Kein Secret gehört in APK, Logs oder UI-Ausgaben.

Das dedizierte Linux-Dienstkonto und die systemd-Dateisystemeinschränkungen begrenzen
den Betrieb. Tokenbesitz gewährt Zugriff auf alle angebotenen persönlichen Workspaces;
CWD-Prüfungen sind weiterhin keine OS-Isolation. Es werden keine zusätzlichen Host-RPCs
exponiert. Authentifizierung ersetzt weder die Agent-Sandbox noch die private
Codex-Accountanmeldung. Mehrnutzerbetrieb ist ausdrücklich nicht Teil dieser Entscheidung.
[Deployment und verbleibende VPS-Abnahme](../deployment.md).

## Explizite Fortschreibung: persönlicher Trusted Host (3. Oktober 2026)

[ADR 0005](0005-personal-trusted-host.md) ersetzt für den ausdrücklich gewählten
persönlichen Single-User-Betrieb die oben stehende Annahme einer Einschränkung
des Agenten durch Dienstkonto/systemd und Agent-Sandbox: Full Access, never und
passwortloses sudo sind gewollt und am VPS beobachtet. Die zentrale Entscheidung
dieses ADR zur Host-Trust-Boundary bleibt unverändert. Historischer Text und
Isolations-Spikes werden nicht umgedeutet. Der Pi-Control-Eingang soll privat
bleiben; [ADR 0006](0006-private-host-transport.md) schlägt dafür konfigurierbares
LAN-HTTP bzw. privates HTTPS vor. LAN-Androidänderung/Abnahme stehen noch aus.
