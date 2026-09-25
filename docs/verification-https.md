# Prüfnachweis: persönlicher HTTPS-/Token-Slice

25. September 2026, lokal in `/Users/kf/codexpad`. Keine DNS- oder VPS-Änderungen,
kein echter Modellturn. Das Repository hatte zu Beginn bereits sämtliche
Projektdateien als unversionierte Dateien; dieser Auftrag erstellt keinen Commit.

## Erfolgreiche Prüfungen

| Prüfung | Ergebnis |
| --- | --- |
| `python3 -B -m unittest discover -s server -p 'test_*.py' -v` | 9 Tests erfolgreich |
| `:app:testDebugUnitTest` mit lokaler Python-Fixture und flüchtigem Token | 15 Tests, 0 Fehler, 0 übersprungen |
| `:app:assembleDebug` / `:app:assembleRelease` | Erfolgreich; Release-APK noch unsigniert |
| `:app:lintDebug` / `:app:lintRelease` | Je 0 Fehler, 7 Hinweise: 6 zu neueren Abhängigkeiten, 1 KTX-Editor-Vorschlag; explizites `commit()` prüft hier bewusst den Speichererfolg |
| `:app:assembleDebugAndroidTest` | Erfolgreich |
| Android-Keystore auf YLE_W09, Android 16 | Schreiben/Lesen, kein Klartext in Preferences, frischer IV, Lesen nach Prozessneustart, URL-Manipulation abgelehnt; Testdaten danach entfernt |
| Finaler Debug-Build auf Tablet | APK aktualisiert, Kaltstart erfolgreich; Einstellungen, Standard-URL, leerer Passwort-Input und fehlendes Token per UI-Hierarchie geprüft |
| Zusammengeführte Android-Manifeste | Release-Cleartext aus, Debug-Cleartext an; Backups aus |
| Caddy 2.10-Alpine im lokalen Docker-Container ohne Netzwerk | `caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile`: gültige Konfiguration |

Die Python-Sicherheitstests prüfen fehlende/falsche/doppelte Authorization-Header auf
allen fachlichen Routen und unbekannten Methoden, Abweisung vor Backend-/Bodyzugriff,
keine Query-Authentifizierung oder Requestlogs, minimalen Healthstatus inklusive
Backendausfall, Tokenwechsel und Startverweigerung ohne Token. Ein simulierter
`codex`-Prozess belegt stdio-Start, fehlende Token-Vererbung und Kindprozessende bei
SIGTERM. Die Tests benötigen lokal erlaubte Loopback-Sockets.

Die Android-Tests prüfen Header auf allen acht Endpunkten einschließlich POST/SSE,
keine Tokenweitergabe bei Redirect, keine Ausgabe zurückgespiegelter Tokens in
Fehlern, URL-/Tokenvalidierung und lokale Debug-Ausnahmen. Der Vertragstest nutzt den
echten Python-Handler mit simuliertem Backend und belegt authentifizierten Stream,
Abbruch, Reconnect, vollständige History und Fortsetzen. Die bestehenden Timeline-
und Session-Regressionstests bleiben erfolgreich.

Der Keystore-Runner verwendet einen eigenen Alias und eigene Test-Preferences;
Benutzereinstellungen werden nicht überschrieben. Sein Token wird zufällig auf dem
Gerät erzeugt, nicht in der APK hinterlegt oder ausgegeben. Die aktuelle Debug-APK
bleibt auf dem Tablet installiert. Die temporäre UI-Dump-Datei wurde vom Tablet
entfernt. Reproduzierbare Befehle stehen in der [Android-Anleitung](../android/README.md).

## Noch auf dem Zielsystem zu prüfen

Kein laufendes Linux-systemd-Deployment getestet; die Unit muss auf dem VPS mit
`systemd-analyze verify` und einem echten Start/Stop geprüft werden. Keine öffentliche
Zertifikatsausstellung und kein Android→öffentliches HTTPS→Caddy→Codex-End-to-End-Lauf.
Diese Abnahme folgt nach [VPS-Preflight und Deployment](deployment.md). Lokale Tests
ersetzen auch nicht den Check der Codex-Anmeldung und Agent-Sandbox des Dienstkontos.

## In diesem Slice geänderte oder neue Dateien

| Bereich | Dateien |
| --- | --- |
| Server | [codexpad_server.py](../server/codexpad_server.py), [smoke.py](../server/smoke.py), [test_auth.py](../server/test_auth.py), [README.md](../server/README.md) |
| Android-Build/Manifeste | [build.gradle.kts](../android/app/build.gradle.kts), [main/AndroidManifest.xml](../android/app/src/main/AndroidManifest.xml), [debug/AndroidManifest.xml](../android/app/src/debug/AndroidManifest.xml) |
| Android-Verbindung | [CodexPadApi.kt](../android/app/src/main/java/dev/codexpad/network/CodexPadApi.kt), [ConnectionSettings.kt](../android/app/src/main/java/dev/codexpad/settings/ConnectionSettings.kt), [SettingsStore.kt](../android/app/src/main/java/dev/codexpad/settings/SettingsStore.kt) |
| Android-UI/Zustand | [MainActivity.kt](../android/app/src/main/java/dev/codexpad/MainActivity.kt), [PadViewModel.kt](../android/app/src/main/java/dev/codexpad/ui/PadViewModel.kt), [CodexPadApp.kt](../android/app/src/main/java/dev/codexpad/ui/CodexPadApp.kt), [ThreadSession.kt](../android/app/src/main/java/dev/codexpad/data/ThreadSession.kt) |
| Android-Tests | [ApiTest.kt](../android/app/src/test/java/dev/codexpad/ApiTest.kt), [PythonContractTest.kt](../android/app/src/test/java/dev/codexpad/PythonContractTest.kt), [ConnectionSettingsTest.kt](../android/app/src/test/java/dev/codexpad/ConnectionSettingsTest.kt), [KeystoreTestRunner.kt](../android/app/src/androidTest/java/dev/codexpad/KeystoreTestRunner.kt), [contract_server.py](../android/tools/contract_server.py) |
| Betriebsvorlagen | [codexpad.service](../deploy/codexpad.service), [server.env.example](../deploy/server.env.example), [Caddyfile](../deploy/Caddyfile) |
| Dokumentation | [Root-README](../README.md), [Android-README](../android/README.md), [bisheriger Android-Nachweis](../android/VERIFICATION.md), [ADR 0004](decisions/0004-client-host-trust-boundary.md), [deployment.md](deployment.md), [dieser Nachweis](verification-https.md) |
