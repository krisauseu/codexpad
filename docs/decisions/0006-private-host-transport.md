# ADR 0006: Konfigurierbarer privater LAN-/Tailscale-Transport

Datum: 3. Oktober 2026. Status: **Proposed; LAN-Implementierung und Pi-/Tablet-Abnahme abgeschlossen**.
Die weiter unten definierten ergänzenden Abnahmekriterien (echter API-37-Dialog,
Reboot und externe Erreichbarkeit) bleiben offen; sie blockieren den aktuellen Betrieb nicht.
[Hostnachweis](../verification-pi-host-2026-10-03.md),
[Android-Prüfbericht](../../android/VERIFICATION-TRUSTED-LAN.md).

## Kontext und Evidenz

Android nutzt OkHttp, HTTP-JSON/Multipart/Downloads und SSE; Adresse und Token
sind zur Laufzeit konfigurierbar. HTTPS bleibt Standard. Trusted-LAN-HTTP ist
standardmäßig aus und verlangt ausdrückliche Zustimmung für eine literale
RFC1918-IPv4-Adresse, gebunden an Schema/Host/Port; URL-Wechsel setzt sie zurück.
Öffentliches HTTP, DNS-Namen über HTTP und IPv6-HTTP bleiben im LAN-Modus gesperrt.
Debug-Loopback-/Emulatorausnahmen bleiben separat. Die Plattform-NSC erlaubt
Cleartext technisch; App-URLpolicy begrenzt jeden API-Client zusätzlich.
Der Adapter bindet fest `127.0.0.1`; eine Bind-Environmentvariable existiert nicht.
Der VPS verwendet Caddy HTTPS → Loopback HTTP → stdio-Codex.
[Ist-Nachweis](../vps-inventory-2026-10-03.md),
[Einrichtung und Android-Folgeänderung](../../HOST_SETUP.md#10-netzwerk-und-android-verbindung).

## Implementierter LAN-Weg und optionale Transporte

Adapter und App-Server-Lifecycle unverändert lassen. Private HTTPS-Endpunkte
(z. B. Tailscale Serve) bleiben möglich. Für reines Trusted-LAN-HTTP auf Android
sind Network Security Configuration und URLvalidierung um explizite persönliche
HTTP-Freigabe erweitert, ohne Hostadresse/Token hart zu codieren. Private
RFC1918-LAN-IP und HTTPS-Endpunkt bleiben konfigurierbar. Tailscale-IP/-Name
werden über HTTPS genutzt; HTTP erlaubt keine CGNAT-Adressen aus `100.64/10`.
Für beliebige IP-Adressen genügt keine statische NSC-Domainliste; Plattformcleartextfreigabe
und begrenzte App-URLpolicy sind getrennt nötig.

Ein optionaler lokaler Proxy bindet ausschließlich eine konkrete LAN- oder
Tailnetadresse und behält Bearer-Auth/SSE. Kein externer VPS oder öffentlicher
Reverseproxy nötig. Direkte Adapterbindung wäre eine spätere getestete Option;
kein pauschales `0.0.0.0`/`[::]`, keine öffentliche Portweiterleitung/Funnel.
HTTPS-Zertifikatsprüfung und Redirect-/Retryregeln bleiben erhalten.
Bei HTTP ist das persönliche Bearer-Token im lokalen Netz unverschlüsselt;
diese Trustentscheidung gilt ausschließlich für den gewählten privaten Zugang.

Target SDK ist bereits 37. `ACCESS_LOCAL_NETWORK` ist deklariert und wird
auf Android 17 vor direktem LAN-Zugriff als Runtimeberechtigung behandelt.
Grant/Ablehnung/Widerruf sind automatisiert geprüft.
Der echte API-37-Systemdialog ist mangels passendem Gerät noch nicht abgenommen.
Die Permission betrifft auch HTTPS im LAN, keine Codex-Approvalfrage.
[Android LAN-Berechtigung](https://developer.android.com/privacy-and-security/local-network-permission).

## Alternativen, Abnahme und Folgen

Nur öffentliches HTTPS am bisherigen VPS würde dessen Abschaltung nicht lösen.
Nur Tailscale HTTPS ist mit der vorhandenen URLpolicy vereinbar,
wäre aber eine Pflichtabhängigkeit für ausschließlich lokales LAN.
Ein öffentlicher App-Server-WebSocket erweitert die Trust-Boundary ohne Bedarf.
Ein lokaler Proxy erlaubt LAN-Bindung ohne Änderung des aktuellen Adapters;
ein kleiner direkter Bind-Parameter wäre alternativ möglich.

Accepted erst nach Androidimplementierung und Pi-/Tablet-Nachweis: konfigurierbare
IP/HTTPS-URL, Auth auf allen fachlichen Routen inklusive SSE/Downloads,
TLS-Trust, Cleartextgrenzen, Android-17-Permission-Grant/Denial/Revocation,
App-Reconnect ohne Turnretry, Autostart und negative externe Erreichbarkeit.
Keine Transportänderung auf dem Referenz-VPS. Full Access gemäß ADR 0005
wird dafür nicht eingeschränkt. Bei Netzwerk-/Target-SDK-/Transportänderungen
erneut prüfen.
