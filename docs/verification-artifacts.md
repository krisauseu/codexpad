# Ergebnisdateien: echter VPS-/Tablet-Nachweis

26. September 2026. Implementierung ab `a79bf63`, Ausgangsstand `09be4ef`.

## Evidenz und Entscheidung

Auf dem tatsächlichen Test-VPS meldet `codex --version` **0.156.1**. Vor Änderungen
wurden vorhandene Histories gelesen: Thread `01a0d965-210f-7380-817f-290b8ac0d912`
enthält erfolgreiche `fileChange`-Items mit absolutem Workspace-Pfad und `kind`.
Thread `01a0d8b6-5f2a-7073-8803-4719743ab3d5` enthält für die per Shell erzeugte und
geänderte `agent-runtime-probe.txt` ausschließlich `commandExecution`. Deren
`command` ist kein strukturiertes Ergebnisdateiverzeichnis und wird nicht geparst.
Die bereits geprüften `item/fileChange/patchUpdated`-SSE-Daten ersetzen keine
vollständige Ergebnisdateierkennung. Lokal bleibt PATH-Codex 0.139.0 unverändert;
der Laufzeitnachweis stammt ausdrücklich vom VPS.

Daher zwei kleine Quellen: erfolgreiche `fileChange.changes[].path`/Move-Ziele und
pro neuem Turn ein vom Adapter festgelegter Ergebnisordner. Die exakte separate
Input-Anweisung wird in Codex' strukturierter User-Message-History gespeichert.
Der Server rekonstruiert daraus den zugeordneten Ordner, ohne Agententext nach
Pfaden zu durchsuchen. Auch nach Prozessneustart bleiben beide Quellen erhalten.
Android erhält ausschließlich strukturierte `turn.artifacts`-Metadaten. Die
technische Input-Anweisung wird in Nachricht und Thread-Vorschau ausgeblendet.

## Live-Ablauf

- YLE_W09, Android 16, USB-Gerät `AWDVBB6428000843`.
- App-Verbindung unverändert `https://pad.feichti.dev`, kein Token aus dem Tablet
  extrahiert, kein ADB-Reverse und kein Tunnel verwendet.
- Vier echte, nacheinander gestartete Codex-Turns über die authentifizierte
  CodexPad-API, Workspace `testprojekt`, neuer Thread
  `01a0dd41-1b14-7e63-92b4-21b4fdde8879`. Keine Ergebnisdatei manuell angelegt.

| Datei | Turn-ID | MIME | Bytes | Erkennung | Tablet |
| --- | --- | --- | ---: | --- | --- |
| codexpad-test.txt | 01a0dd41-1b7e-7cb3-b9dc-261a5768b5e9 | text/plain | 16 | apply_patch/fileChange | Karte, Öffnen, CODEXPAD-TXT-OK im lokalen Textviewer lesbar |
| codexpad-test.md | 01a0dd41-4aa8-7d43-9e1c-1fd6652410bc | text/markdown | 27 | apply_patch/fileChange | Karte, Öffnen als text/plain, Überschrift und CODEXPAD-MD-OK lesbar |
| codexpad-test.png | 01a0dd41-79d7-78b2-b7f2-c2441d0083f5 | image/png | 168 | Ergebnisordner; nur commandExecution | Karte, Galerie zeigt blaues Quadrat |
| codexpad-test.pdf | 01a0dd41-b8f6-7df3-b797-35b3025b2324 | application/pdf | 596 | Ergebnisordner; nur commandExecution | Karte, Google-Drive-PDF-Viewer zeigt CODEXPAD-PDF-OK |

PDF außerdem über **Speichern → Android ACTION_CREATE_DOCUMENT → Downloads**
abgelegt. Die App schreibt ausschließlich die vom System zurückgegebene URI.
Anschließender lesender ADB-Hashvergleich bestätigt bytegleiche Ausgabe:
`7c8aca9ba0f80d261094bf44ffd44fbd7fe0f6cd45d74e4b5d213d3f687fadf2`.

Alle vier Dateien separat über echtes HTTPS abgerufen: 200, korrekter MIME,
Dateiname und Länge. Ohne Bearer jeweils 401; dieselbe ID unter einem anderen
bestehenden Thread jeweils 404. Traversal/absoluter Pfad als kodierte ID sowie eine
unbekannte 64-stellige ID: 404. Wiederholte History gleich. Anschließender
Dienstneustart: dieselben vier IDs, Metadaten und Downloadhashes wieder vorhanden.
Android rekonnektierte; nach APK-Update und App-Neustart wurde der Thread erneut
geöffnet und die PDF-Karte aus History wieder angezeigt. Kein Live-only-Zustand.

## Lokale Prüfungen und Deployment

- 15 Python-Tests erfolgreich; Artefakttest prüft zusätzlich Symlink-/Hardlink-
  Abweisung, Eltern-Symlink, ersetzte/gelöschte Datei, falschen Bildinhalt,
  Größenlimit, gesperrte Secret-Namen, History und Thread-Bindung.
- APK-Build und Lint erfolgreich.
- 44 JVM-Tests erfolgreich, **0 übersprungen**, einschließlich laufender
  Kotlin/Python-Vertragsgegenstelle. Neue Tests prüfen History-Reconciliation,
  Legacy-Kompatibilität, Auth-Header, Downloadbytes, Redirect- und Größenabweisung.
- Gepushten Server erst nach lokalem Test und Commit installiert. Live-Datei
  entsprach vorher bytegleich `09be4ef`, obwohl RELEASE_COMMIT noch älter war.
  Sicherung: `/opt/codexpad/server/codexpad_server.py.pre-artifacts-20260926`.
- Server-SHA-256:
  `a45bea2c1a0db558056fe7a5e4e717edda386a2cbe534fbb7a8ddbce637eab3e`.
- Installierte finale Debug-APK-SHA-256:
  `3aaf4f623977c7f3099c3625030f51ad326b3a20bdfc6cbf168b55cdceef0bd1`.
- Keine Änderung an Codex, Token, Caddy oder Dienstrechten nötig.

## Bewusste v1-Grenzen

64 MiB pro Datei, höchstens 128 Kandidaten pro Turn. Flacher Ergebnisordner,
keine Bildvorschau in der Karte (Bild öffnet extern), kein eigener PDF-/Markdown-
Renderer. JPEG und WebP sind im Vertrag unterstützt, aber nicht in diesem
vier Dateien umfassenden echten Gerätelauf erzeugt worden. SAF-Speichern wurde
mit PDF geprüft; die anderen Typen nutzen denselben Mechanismus.

Keine rückwirkende Erkennung beliebiger alter Shell-Ausgaben. Ignoriert Codex den
Ergebnisordner und erzeugt kein erfolgreiches fileChange, fehlt die Karte. Kein
historisches Dateiarchiv: derselbe Pfad liefert seinen aktuellen Inhalt. Die
konservative Pfadsperre kann auch harmlose Dateien mit „config“, „token“ usw. im
Namen ausschließen. Signatur-/UTF-8-Prüfung ist keine vollständige Formatprüfung
oder inhaltliche Secret-Erkennung. Downloads liegen temporär im privaten Cache;
bei weiteren Downloads werden mehr als einen Tag alte Cacheordner aufgeräumt.
