# ADR 0005: Persönlicher Trusted Host mit Full Access ohne Codex-Approvals

Datum: 3. Oktober 2026. Status: **Accepted**, aufgrund der ausdrücklichen
Produktvorgabe des Betreibers. Geltung: persönlicher Single-User-CodexPad-Host,
einschließlich des praktisch getesteten Raspberry Pi; keine Mehrnutzer-Sicherheitsfreigabe.

## Kontext und Evidenz

Die datierte VPS-Inventur vom 3. Oktober 2026 belegte Codex 0.160.0 als
stdio-App-Server-Kind des damaligen Python-Adapters. Das ist historische Evidenz,
keine Sollversion für neue Hosts. Für den Pi eine zum Installationszeitpunkt
aktuelle/unterstützte ARM64-Version bewusst auswählen, das vollständige Release
samt seinen benötigten Helpern installieren und den tatsächlichen Dienstpfad
abnehmen. Prozessargumente sowie `thread/start`/`turn/start` setzen
Full Access und never. Das Dienstkonto hat `NOPASSWD: ALL`;
systemd verhindert sudo/System-/Home-Schreibzugriff nicht.
[Lesender Ist-Nachweis](../vps-inventory-2026-10-03.md).
Der Betreiber verlangt dasselbe Modell absichtlich für den persönlichen Pi.
Die Entscheidung basiert auf diesem gewählten Vertrauen, nicht auf einem
erfolgreichen Sandbox-/Workspace-Isolationsnachweis.

## Entscheidung

`sandbox_mode = "danger-full-access"`, `approval_policy = "never"` gelten im
normalen CodexPad-Betrieb. Codex darf das ganze Hostdateisystem, Shell und
Netzwerk nutzen, Software installieren/konfigurieren und Projekte erstellen,
ändern, bauen und starten. Das dedizierte Dienstkonto darf über passwortloses
sudo administrieren. Gewöhnliche Shell-/Datei-/Netzwerk-/Permission-Approvals
werden nicht an Android geschickt. Fachliche Rückfragen sind davon getrennt.
Externe Sicherheitsmechanismen und zwingende Drittanbieter-Authentifizierung
bleiben wirksam; unbekannte Serverrequests werden nicht automatisch genehmigt.

Die alternative eingeschränkte Agent-Sandbox mit Tablet-Approvalworkflow ist
für diesen persönlichen Betriebsmodus nicht gewählt. Full Access wird nicht
als Kompatibilitätsworkaround für einen Sandboxfehler begründet.

**Die Netzwerkgrenze ist unabhängig:** App Server bleibt stdio ohne Listener,
Adapter standardmäßig Loopback. Ein Control-Eingang ist authentifiziert und
auf LAN/Tailscale bzw. einen privaten Tunnel begrenzt. Keine versehentliche
Internetfreigabe, kein ungeschützter Wildcardlistener mit öffentlichem Forwarding.
Der bisherige öffentlich TLS-/Bearer-geschützte VPS wird durch diese
Dokumentationsarbeit nicht verändert. Private Pi-Transporte stehen in ADR 0006.

## Beziehung zu bestehenden ADRs und Folgen

ADR 0001–0003 bleiben unverändert. ADR 0004s zentrale Trust-Boundary bleibt:
Agentrechte schützen den Steuerungskanal nicht. **Explizit ersetzt** wird für
diesen persönlichen Modus die Annahme seiner Konkretisierung vom 25. September,
dass Dienstkonto/systemd den Agenten auf begrenzte Hostrechte einschränken und
eine Agent-Sandbox erhalten werden soll. Es wird keine Workspace-/Secret-
Isolation vor dem Trusted Agent behauptet. Tokenbesitz ermöglicht durch
Agentaufträge Hostadministration; root-owned Code und getrennte Credentialorte
sind organisatorische Grenzen, keine Abschirmung vor einem Agenten mit sudo.

Die früheren restriktiven Sicherheits-Spikes bleiben gültige historische Befunde
für andere Trustmodelle. Sie werden nicht nachträglich als Full-Access-Abnahme
gezählt. [HOST_SETUP](../../HOST_SETUP.md) beschreibt die bewusste Einrichtung
und reproduzierbare Smoke-Tests; [Pi 4/Ubuntu 26.04.1 ARM64](../verification-pi-host-2026-10-03.md)
ist mit echtem Android-Tablet End-to-End abgenommen, einschließlich Full Access
und sudo UID 0. Codex 0.160.0 ist die datierte Prüfversion.
Bei Mehrnutzerbetrieb, untrusted Hosts, öffentlicher Bereitstellung oder
geändertem Betreibervertrauen eine neue Entscheidung mit geeigneter Isolation
und Authentifizierung treffen; diese ADR nicht stillschweigend übertragen.
