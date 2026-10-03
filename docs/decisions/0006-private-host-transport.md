# ADR 0006: Konfigurierbarer privater LAN-/Tailscale-Transport

Datum: 3. Oktober 2026. Status: **Proposed** für die technische LAN-Umsetzung.
Die private Netzwerkgrenze und freie Wahl der Hostadresse sind Betreiberanforderungen;
Androidänderung und Pi-Transportabnahme fehlen noch.

## Kontext und Evidenz

Android nutzt OkHttp, HTTP-JSON/Multipart/Downloads und SSE; Adresse und Token
sind zur Laufzeit konfigurierbar. HTTPS ist frei wählbar, HTTP im Validator
derzeit nur für Debug-Loopback/Emulator. Das Releasemanifest blockiert Cleartext.
Der Adapter bindet fest `127.0.0.1`; eine Bind-Environmentvariable existiert nicht.
Der VPS verwendet Caddy HTTPS → Loopback HTTP → stdio-Codex.
[Ist-Nachweis](../vps-inventory-2026-10-03.md),
[Einrichtung und Android-Folgeänderung](../../HOST_SETUP.md#10-netzwerk-und-android-verbindung).

## Vorschlag

Adapter und App-Server-Lifecycle unverändert lassen. Private HTTPS-Endpunkte
(z. B. Tailscale Serve) bleiben möglich. Für reines Trusted-LAN-HTTP Android
Network Security Configuration und URLvalidierung um explizite persönliche
HTTP-Freigabe erweitern, ohne Hostadresse/Token hart zu codieren. LAN-IP,
Tailscale-IP/-Name und HTTPS-Endpunkt bleiben konfigurierbar. Für beliebige
IP-Adressen genügt keine statische NSC-Domainliste; Plattformcleartextfreigabe
und begrenzte App-URLpolicy sind getrennt nötig.

Ein optionaler lokaler Proxy bindet ausschließlich eine konkrete LAN- oder
Tailnetadresse und behält Bearer-Auth/SSE. Kein externer VPS oder öffentlicher
Reverseproxy nötig. Direkte Adapterbindung wäre eine spätere getestete Option;
kein pauschales `0.0.0.0`/`[::]`, keine öffentliche Portweiterleitung/Funnel.
HTTPS-Zertifikatsprüfung und Redirect-/Retryregeln bleiben erhalten.
Bei HTTP ist das persönliche Bearer-Token im lokalen Netz unverschlüsselt;
diese Trustentscheidung gilt ausschließlich für den gewählten privaten Zugang.

Target SDK ist bereits 37. Auf Android 17 `ACCESS_LOCAL_NETWORK` deklarieren
und vor direktem LAN-Zugriff als Runtimeberechtigung behandeln; Ablehnung/Widerruf
sichtbar machen. Das betrifft auch HTTPS im LAN, keine Codex-Approvalfrage.
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
