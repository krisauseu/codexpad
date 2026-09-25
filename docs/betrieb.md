# Betrieb — Zettelruhe (self-hosted Solo)

Für den optionalen gemeinsamen Cloud-Betrieb nach v1.0.0 gilt zusätzlich
[Instanzkontext](instance-context.md). Die folgenden Compose-/Caddy-Anleitungen
betreffen weiterhin ausschließlich Self-Hosting. Dessen öffentliche PB-Routen
werden nicht in Cloud übernommen. TP-002 hat keinen bestehenden Betrieb verändert.

Praxishinweise für eine Instanz mit **Instanz-Eigentümer:in**, optional weiteren Nutzer:innen (Mitgliedschaft je Firma) und einer oder mehreren Firmen (Session wechselt die aktive Firma).  
Stack: Caddy + Next.js + PocketBase (SQLite), Named Volume `zettelruhe_pb_data` (ADR-0007).  
Dieses Repo liefert **eine** Instanz (ein Next, ein PocketBase). Mandanten-Provisioning, Stripe und Subdomain-Verwaltung gehören nicht hierher (ADR-0030).

---

## 1. Was gehört zum System

| Komponente | Inhalt | Persistenz |
|------------|--------|------------|
| **PocketBase** | Auth, Collections, Dateien (Belege, PDFs, E-Rechnungen) | Volume `zettelruhe_pb_data` → `/pb_data` |
| **Next.js** | UI, Domain-Logik, Session, Jobs, Exporte | Stateless (keine App-DB) |
| **Caddy** | Reverse Proxy Port 80 | Stateless |
| **`.env`** | Secrets und URLs | Host-Dateisystem (nicht im Volume) |

**Ein Backup des PB-Volumes sichert die Fachdaten.**  
`.env` und ggf. TLS-Zertifikate separat sichern (nicht im Volume).

---

## 2. Backup

### Was

1. **Pflicht:** Named Volume `zettelruhe_pb_data` (SQLite `data.db` + `storage/` Dateien)
2. **Empfohlen:** `.env` (ohne sie startet die App mit anderen Secrets → Sessions/Superuser inkonsistent)
3. **Optional:** Compose-/Caddy-Config, falls angepasst

### Wann

| Rhythmus | Empfehlung |
|----------|------------|
| Täglich | Automatisiert (cron / Backup-Tool des Hosts) |
| Vor Updates | Manuell vor `docker compose pull` / Image-Rebuild |
| Nach Importen | Nach großem CSV-/Bank-Import einmal extra |

Aufbewahrung: mindestens so lang wie steuerliche Aufbewahrungsfristen der Belege (lokal festlegen, siehe Verfahrensdokumentation).

### Wie (tar aus dem Volume)

Stack idealerweise **kurz stoppen** (konsistentes SQLite), oder Offline-Kopie des Volumes erzeugen:

```bash
# Optional: Stack stoppen für ruhige SQLite-Datei
docker compose stop next caddy
# PocketBase kann mitlaufen; sauberer: alles stoppen
docker compose stop

docker run --rm \
  -v zettelruhe_pb_data:/data:ro \
  -v "$(pwd)/backups":/backup \
  alpine \
  tar czf "/backup/pb_data-$(date +%Y%m%d-%H%M).tar.gz" -C /data .

docker compose start
```

Volume-Pfad prüfen:

```bash
docker volume inspect zettelruhe_pb_data
```

### Was nicht reicht

- Nur `data.db` ohne `storage/` → Belegdateien und PDFs fehlen
- Snapshot der Next-Container-Layer → enthält keine Fachdaten
- Git-Repo / `.env.example` → keine Produktivdaten

---

## 3. Restore

1. Stack stoppen: `docker compose down` (Volume **nicht** mit `-v` löschen, außer bewusst)
2. Volume leeren bzw. frisches Volume anlegen (nur wenn Restore ins leere Volume):

```bash
docker compose down
# Achtung: löscht alle aktuellen PB-Daten
docker volume rm zettelruhe_pb_data
docker volume create zettelruhe_pb_data

docker run --rm \
  -v zettelruhe_pb_data:/data \
  -v "$(pwd)/backups":/backup:ro \
  alpine \
  sh -c 'cd /data && tar xzf /backup/pb_data-YYYYMMDD-HHMM.tar.gz'
```

3. `.env` vom Backup-Zeitpunkt wiederherstellen (gleiche `SESSION_SECRET` / Superuser, sofern möglich)
4. `docker compose up -d`
5. Login prüfen; Stichprobe Beleg-PDF und Journal

**Test der Wiederherstellung** mindestens einmal pro Jahr (oder nach Host-Wechsel) dokumentieren.

---

## 4. Secrets & ENV

Siehe `.env.example`. **Niemals** Beispielwerte in Produktion belassen.

| Variable | Hinweis |
|----------|---------|
| `SESSION_SECRET` | ≥ 32 Zufallszeichen; Wechsel loggt alle Sessions aus |
| `PB_SUPERUSER_*` | Nur Betrieb/Schema (`/_/`), **nicht** App-Login |
| `APP_URL` | Öffentliche URL **ohne** trailing slash (`https://buchhaltung.example.de`) |
| `PB_URL` | In Compose: `http://pocketbase:8090` (intern) |
| `SMTP_*` | Optional; ohne Host läuft die App, Versand meldet de-DE-Hinweis |
| `EVATR_URL` | Optional; Default `https://api.evatr.vies.bzst.de/app`. USt-IdNr.-Prüfung braucht ausgehenden HTTPS-Zugang zum BZSt. Kein API-Key, kein Zertifikat. |

Erzeugen z. B.:

```bash
openssl rand -base64 48   # SESSION_SECRET
```

`.env` liegt **nicht** im Git (`.gitignore`). Rechte am Host einschränken (`chmod 600 .env`).

---

## 5. Session & CSRF (light)

- App-Session: httpOnly-Cookie `zettelruhe_session`, Signatur HMAC (`SESSION_SECRET`), `SameSite=Lax`
- `Secure`-Flag nur wenn `APP_URL` mit `https://` beginnt
- TTL: 14 Tage ab Token-Ausstellung; fester JWT-Ablauf ohne Aktivitätsmessung
- Login/Setup: klassischer Form-POST auf Route-Handler (zuverlässig hinter Reverse-Proxy)
- Server Actions: Next prüft Origin; Host aus `APP_URL` muss zur erreichbaren URL passen (Build/Compose, siehe README)
- Finanz-Writes nur serverseitig über Next (ADR-0006) — nicht mit Client-PB-Rechten auf Journal/Belege

**Empfehlungen Self-hosted**

- Produktion hinter HTTPS. `APP_URL` ohne trailing slash; beginnt sie mit `https://`, setzt die App das Secure-Cookie.
- Next↔PocketBase intern im Docker-Netz (`PB_URL=http://pocketbase:8090`). Das ist unabhängig vom öffentlichen Eingang.
- Eingehendes TLS (Browser → App) ist **nicht** dasselbe wie ausgehendes HTTPS zum BZSt (eVatR). Ohne ausgehenden Zugang ist die Klick-Prüfung nicht ehrlich testbar.
- Starke Passwörter für Eigentümer:in und Superuser. Superuser ≠ App-Login.

**HTTPS / Caddy (ADR-0023)**

Lokal: Caddy im Compose, HTTP Port 80, Repo-`Caddyfile` ohne TLS. Unverändert `docker compose up`.

Server: **Caddy nativ auf dem Host**. Let’s Encrypt (ACME), Host `app.zettelruhe.de`. Host-Caddy terminiert TLS und proxied auf `127.0.0.1:3000` (Next) und `127.0.0.1:8090` (PocketBase). Compose-Caddy startet dort nicht. Overlay: `docker-compose.server.yml`. Site-Block: `deploy/Caddyfile.host`.

`/_/` bleibt über denselben Host erreichbar (explizit). Wer den Admin nicht im Netz will, muss das am Host nachziehen (VPN / Allowlist) — dieser Schnitt tut das nicht.

Schritte auf dem Server:

1. DNS `app.zettelruhe.de` → dieser Rechner. Ports 80 und 443 frei (kein anderes Caddy/nginx auf 80).
2. Caddy auf dem Host installieren; `deploy/Caddyfile.host` als Site-Block (oder ganze Datei, wenn Caddy nur diese Instanz bedient). Optional ACME-Mail im globalen Block.
3. `.env`: `APP_URL=https://app.zettelruhe.de` (kein Slash am Ende). `PB_URL=http://pocketbase:8090`. Next **neu bauen** (Build-Arg `APP_URL` für Server Actions).
4. `docker compose -f docker-compose.yml -f docker-compose.server.yml up -d --build`  
   (Compose-Plugin ≥ 2.24 wegen `!override` auf den Next-Ports; sonst bliebe `:3000` öffentlich.)
5. `sudo systemctl reload caddy` (oder Äquivalent)
6. Smoke: `curl -sSI https://app.zettelruhe.de/health` — Zertifikat ohne Browser-Ausnahme. Login: Cookie `Secure`.

TLS-Zertifikate liegen bei Host-Caddy (nicht im PB-Volume) — separat sichern bzw. ACME neu ausstellen lassen.

---

## 6. Healthcheck

| Endpoint | Zweck |
|----------|--------|
| `GET /health` (Next, über Caddy) | Liveness der App; optional PB-Erreichbarkeit |
| PocketBase `GET /api/health` | Intern im Compose-Healthcheck |

```bash
curl -sS http://localhost/health
# Server: curl -sSI https://app.zettelruhe.de/health
# {"ok":true,"service":"zettelruhe",...}
```

Compose: Services `next` und `pocketbase` haben Healthchecks (siehe `docker-compose.yml`).

`/health` antwortet auch bei `ok:false` mit HTTP 200. Der Compose-Next-Check
prüft den HTTP-Status, nicht das JSON-Ergebnis. Für einen Betriebs-Smoke den
Antwortinhalt mit `ok`, `env` und `pocketbase` prüfen; ein erfolgreicher HEAD-Request
belegt nur die Erreichbarkeit.

Die Warnung, dass PocketBase-Superuser-Zugangsdaten wie Platzhalter wirken,
ist eine Teilstring-Heuristik auf bekannte Beispielmuster. Eine erfolgreiche
PocketBase-Anmeldung entkräftet sie nicht, weil auch ein Beispielwert technisch
gültig sein kann. Bei einer Warnung die Werte ohne Ausgabe exakt gegen die
versionierte Beispielkonfiguration vergleichen. Ein Treffer ist als eigener
Sicherheitsbefund zu behandeln und kontrolliert zu rotieren; `/health` rotiert
nichts. Ein Wert, der nur zufällig ein erkanntes Fragment enthält, kann einen
Fehlalarm auslösen. Schwache Werte ohne bekanntes Fragment werden nicht erkannt.

---

## 7. Updates

1. Backup Volume + `.env`
2. `git pull` (oder Image-Tags anpassen)
3. `docker compose build --pull && docker compose up -d`
4. Migrationen laufen beim PB-Start (`pb_migrations`)
5. Smoke: Login, Übersicht, eine Liste, Export-Download

Seit TP-022 lädt das PocketBase-Image Finanz-Hooks aus `/pb/pb_hooks`. Next und
PocketBase gehören deshalb gemeinsam in ein Update. Fehlt die interne Operation,
bricht die Anwendung Beleg-, Rechnungs- und Kassenfestschreibungen sowie
Kassenstornos mit einem
Installationsfehler ab; seit der Releasevorbereitung betrifft dies auch
Zahlungen, Bankmatching, Rechnungsstorno und wiederkehrende Erzeugung; sie fällt nicht auf die früheren getrennten
Schreibrequests zurück. Alte und neue Anwendungsversionen dürfen nicht parallel
auf dasselbe PocketBase-Volume schreiben. Vor dem ersten Produktivdeployment
der TP-022-Stufen wurde der Bestand laut Betreiberbestätigung vom 2026-09-07
lokal, auf dem Test-VPS und auf dem Produktions-VPS geprüft. Für einen weiteren
Zielbestand mit älteren Daten ist der Rechnungsbestand ausschließlich lesend auf
doppelte ursprüngliche Journale, unvollständige Abschlüsse, Nummerndubletten,
Journalrückverweise, Firmen-/Quellenbezüge und vorhandene PDF-Dateireferenzen zu
prüfen. Für die Kassenstufe sind zusätzlich
negative chronologische Zwischenstände, Kassen- und Journalnummerndubletten,
zurückliegende Zähler sowie unvollständige oder firmenfremde
Original-/Stornopaare read-only zu prüfen. Ein lokaler oder Test-VPS-Smoke
ersetzt diese Audits nicht.

---

### Lokale Releaseabnahme vom 2026-09-10

Frische Installation und Upgrade/Restore von `f48be21` wurden ausschließlich mit
einem neu erzeugten synthetischen Bestand geprüft. Eigene zufällige Volumes,
Netzwerke, Loopbackports und Zugangsdaten; keine echten Backups und kein VPS-Zugriff.
Datenbankrecords und Datei-SHA-256 blieben im Restore identisch.
Der aktuelle Stand braucht keine neue Schema- oder Datenkorrekturmigration.
Bei Altbeständen mit früheren Zahlungsfehlern insbesondere unvollständige
Steuerstaffeljournale und Original-/Stornopaare lesend prüfen. Der automatische
Nachzug lehnt erkannte Teiljournale ab und meldet Fehler im Serverlog; er ist
keine Bestandsreparatur.
[Reproduzierbarer Ablauf](entwicklung.md#sicher-prüfen-entwickeln-und-betreiben),
[Ergebnisse und Grenzen](issues/release-abnahme-2026-09-10.md).

### Reverse Charge bei Updates

Der RC-Stand ergänzt `1730002800_reverse_charge.js` für Beleg-/Journaldaten und
`1730002900_kontakt_ausgabensteuerstandard.js` für Lieferantenvorschläge. Next und
PocketBase gemeinsam auf denselben vereinbarten Commit bringen; der generierte
RC-Kern gehört zu den PB-Hooks. `node scripts/build-rc-hook.mjs --check` prüft
seine Übereinstimmung mit der Quelle. Die öffentliche Freigabe ist im Code aktiv,
kein ENV-Schalter. Vor Migration gilt weiterhin das Backup-Verfahren oben.

Die lokale Abnahme ist in [reverse-charge-umsetzung.md](reverse-charge-umsetzung.md)
dokumentiert. Für einen VPS-Lauf Commit, Datum, Zielumgebung, Migrationen und
Ergebnisse gesondert festhalten. RC-Beleg speichern/neu laden/festschreiben,
Steuerperiode in der UStVA, Journal-CSV und die erwartete DATEV-Ablehnung prüfen.
Testdaten und reale Buchhaltung gemäß [Sicher prüfen](entwicklung.md#sicher-prüfen-entwickeln-und-betreiben)
trennen. Der Dokumentationsabgleich vom 2026-09-10 führt kein Update aus.

---

## 8. Jobs & SMTP

- In-Process-Scheduler im Next-Container (ADR-0010), ohne überlappende Ticks im selben Prozess
- Lockübernahme/Freigabe atomar; Lease fünf Minuten, keine laufende Verlängerung.
  Fälligkeitsdatum und Entwurf committen gemeinsam, auch bei Leaseübernahme.
- Alle Firmen und fälligen Vorlagen werden paginiert; höchstens zwölf Nachholperioden
  je Vorlage und Aufruf. Jobs erzeugen weiterhin nur Entwürfe, keinen Versand.
- Abschalten: `JOBS_DISABLED=true`
- Intervall: `JOB_TICK_INTERVAL_MS` (min. 60000)
- SMTP optional; ohne Konfiguration bleiben Mail-Buttons mit de-DE-Hinweis

---

## 9. GoBD / Exporte (Verweis)

- Festschreibung, Journal, Verfahrensdoku: [`verfahrensdokumentation.md`](./verfahrensdokumentation.md)
- DATEV light / Journal-CSV / Belegarchiv: UI **Export** (`/app/export`)
- DATEV light lehnt RC-Zeiträume vollständig ab; Journal-CSV enthält die RC-Daten.
- Belegarchiv: höchstens 256 MiB summierte Dateinutzlast, Abbruch bei fehlender Datei.
- Keine GoBD- oder DATEV-Zertifizierung (ADR-0004)

## 10. Funktionstests

- Meilenstein 1 (Happy Path + Backup/Restore): [`funktionstest-m1.md`](./funktionstest-m1.md)
- Meilenstein 2 (M2-Keile + Server-Nachtest): [`funktionstest-m2.md`](./funktionstest-m2.md)

---

_Dokumentationsstand: 2026-09-10, Codebasis `0842832`. Dies ist kein Nachweis eines aktuellen Deployments oder Restore-Tests._

## SEC-001: lokaler Sicherheitskandidat, 2026-09-14

[Prüfbericht und reproduzierbare Kandidaten](security/SEC-001/README.md).
Die Prüfung betrifft neue synthetische Datenvolumes. Der Rückfall verwendet
das ursprüngliche Backup mit alten Hooks und altem Next, keinen Schema-Downgrade
der aktualisierten Datenbank. Vor einer Übernahme Zielbackup, exakten Commit,
Image-/Config-Digests und passendes AGPL-Quellangebot separat freigeben.
Der lokale Nachweis aktualisiert weder Cloud-`core.lock.json` noch den VPS.
