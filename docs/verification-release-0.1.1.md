# CodexPad 0.1.1: ruhige Statuszeile und Release-Abnahme

3. Oktober 2026, Europe/Berlin.

## Änderungen und Versionsgrundlage

GitHub und Remote-Tags wurden vorab gelesen: letzter und einziger Release
`v0.1.0`, Pre-Release vom 26. September 2026, Tag auf `44116ed`, Asset
`CodexPad-0.1.0-debug.apk`. Nächste Patchversion `0.1.1`, `versionCode` von 1
auf 2 erhöht; der persönliche Pre-Release-Status bleibt erhalten.

Relevante Commits seit diesem Tag:

- `c630098`: Workspaces in der App anlegen, umbenennen und nach Bestandsprüfung
  mit Bestätigung löschen; Schutzprüfung berücksichtigt vorhandene Sessions.
- `1186b7c`: HTML-Texttransfers und Nachrichtenlimit von 12.000 Zeichen.
- `b4ef835`: strukturierte kompakte Codex-Statusanzeige mit Modell, Effort,
  verbleibendem Kontext, Wochenlimit und Turn-Laufzeit; Account-Refresh/Reconnect.
- `827d774`: Nachweis des bereits erfolgten Statusline-VPS-Deployments.
- `b98538a`: Kontext-Button und ausschließlich dafür vorhandenen ViewModel-Aufruf
  entfernt; Idle ohne Turn-Text, laufende Dauer ohne ID und nur mit gültigem
  strukturiertem Startzeitpunkt. Terminale Zustände einschließlich interrupted,
  cancelled und stopped entfernen die Dauer sofort. Sekundentakt endet mit dem
  Turn; die Uhr für Wochenlimit-Ablauf bleibt aktiv. Gezielte Regressionstests,
  Realgerätetestrunner und Release-Metadaten ergänzt.

Codex-Kontextformel, Account-Limit-Semantik, automatische Komprimierung und
HTTP-/SSE-Protokoll bleiben unverändert. Serverdateien wurden nicht geändert.

## Lokale Prüfungen

- `python3 -B -m unittest discover -s server -p 'test_*.py'`: **39 bestanden**.
- `:app:testDebugUnitTest` mit lokaler Python-Vertragsfixture, gemeinsamem
  flüchtigem Testtoken und `CODEXPAD_CONTRACT_URL`: **63 bestanden, 0 Fehler,
  0 übersprungen**. Darunter Kontext-/Wochenlimitwerte und deren Aktualisierung,
  Accountfehler ohne Chatverlust, Stoppen, History, SSE und Reconnect.
- Neue Statusprüfungen: Idle ohne Text/Dauer; aktiver Turn mit Dauer; alle
  terminalen Zustände entfernen Dauer trotz altem active-Threadstatus;
  verspätetes Start-Event reaktiviert nichts; null/negative/zukünftige Startzeit
  und mehrdeutige Turns erfinden keine Laufzeit; History heilt Offline-Abschluss.
- `:app:assembleDebug`, `:app:assembleDebugAndroidTest`, `:app:assembleRelease`,
  `:app:lintDebug`, `:app:lintRelease`: erfolgreich. Beide Lint-Varianten ohne
  Fehler; sieben vorhandene Hinweise (sechs Versionsupdates, ein UseKtx).
  Der letzte Lint-Lauf umfasst auch den finalen Gerätetestrunner.
- `git diff --check`: erfolgreich.
- Finale APK: `apksigner verify --verbose --print-certs` erfolgreich (v2),
  `zipalign -c -P 16 4` erfolgreich; Paket `dev.codexpad`, Version `0.1.1`,
  Code `2`, minSdk 26, targetSdk 37, **nicht debuggable**.

## USB-Tablet gegen den vorhandenen VPS

YLE_W09, USB/ADB `AWDVBB6428000843`. Debug-APK und danach finale Release-APK
jeweils mit `adb install -r` installiert. App-Daten und verschlüsselter Zugang
blieben erhalten; kein Token exportiert. Keine ADB-Reverse-Weiterleitung.

`StatuslineTabletTestRunner` meldet **PASS** für alle zehn beauftragten Schritte:
vorhandene Unterhaltung öffnen, kein Kontext-Button, ruhiges Idle, kurzer echter
Turn, sichtbare Dauer, vollständiger Abschluss, Dauer entfernt, kein
„Kein laufender Turn“, Workspace/Unterhaltung erneut öffnen, Live-Verbindung.
Außerdem authentifizierte Aktion „Verbindung testen“ erfolgreich und Reconnect
nach Einstellungen geprüft. Screenshots zusätzlich visuell kontrolliert.

- Vorhandener Workspace `testprojekt`, Thread
  `01a100fd-9fd3-7d02-b7f4-7d5b5964e41a`.
- Genau ein neuer echter Prüfturn:
  `01a1011a-20e9-7bd3-ac80-378ab3d74c00`, gpt-6.1-sol / medium,
  ausschließlich `sleep 15`, Abschluss `CODEXPAD-QUIET-RELEASE-OK`.
  History: `startedAt=1791019852`, `durationMs=23400`, completed.
- Aktiv sichtbar: `Kontext 96 % frei · Woche 93 % frei · 0:01`.
- Nach Abschluss und erneutem Öffnen:
  `gpt-6.1-sol · medium`, `Kontext 96 % frei · Woche 93 % frei`,
  `Live verbunden`; keine Dauer und kein Idle-Turn-Text.
- Nach Update auf die finale **Release-APK** dieselbe vorhandene Unterhaltung,
  vollständige History, Modell/Effort, Kontext/Woche und Live-SSE erneut geprüft.
  Installierte `base.apk` zurückgelesen: SHA-256 identisch zum Release-Asset.

Kein VPS-Deployment oder Dienstneustart: dieser Patch betrifft ausschließlich
Android. Die Tablet-Prüfung verwendet den bereits produktiven HTTPS-VPS.

## Release-Artifact

- Tag: `v0.1.1`; [GitHub-Release CodexPad 0.1.1](https://github.com/krisauseu/codexpad/releases/tag/v0.1.1).
- Asset: [CodexPad-0.1.1.apk](https://github.com/krisauseu/codexpad/releases/download/v0.1.1/CodexPad-0.1.1.apk).
- Lokal: `android/app/build/outputs/releases/CodexPad-0.1.1.apk`.
- SHA-256: `903b4ff2af5b593bf8dce38b2f9e7c3dc9855014502b71150b87ba98dde55774`.
- Release-Build aus Implementierungscommit `b98538a`,
  `-Pcodexpad.testSignedRelease=true`; der Tag enthält zusätzlich diesen Nachweis.
- Testzertifikat SHA-256:
  `66eb6428baefcb18358486bc0dd253f04e23511cc90736e8c1d0fbc7523fe855`.
  Dasselbe vorhandene lokale Zertifikat ermöglicht datenbewahrende Updates.
  Keystore und APK sind nicht eingecheckt.

## Grenzen

Persönlicher experimenteller Pre-Release mit Android-Testzertifikat; separate
Produktionssignierung fehlt weiterhin. Kein Langzeit-, TalkBack-, Rotations-
oder Multi-Provider-Test. cancelled/failed/stopped und fehlende Startzeiten
wurden deterministisch geprüft; auf dem Tablet wurde wie beauftragt ein normaler
Turn abgeschlossen. Zwei anfängliche Runner-Versuche scheiterten vor dem Senden
an UI-Synchronisierung; nach Fokus-/Draft-Abgleich lief der Test erfolgreich.
Lokale Logs/Screenshots bleiben ausschließlich unter ignoriertem
`android/.local/quiet-release/`; temporäre Gerätedateien wurden entfernt.
