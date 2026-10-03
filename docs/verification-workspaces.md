# Workspace-Verwaltung: Implementierung und echter Tablet-Test

Stand: 3. Oktober 2026. Lokales Projekt `/Users/kf/codexpad`, persönlicher VPS
`pad.feichti.dev`, Codex 0.156.1 und per USB verbundenes Honor YLE-W09.

Die App ist gebaut und auf dem Tablet installiert; die Server-Erweiterung ist auf
dem vorhandenen VPS aktiv. Der zuvor installierte Servercode entsprach exakt
Repository-HEAD. Vor dem Update wurden laufende Turns geprüft und der bisherige
Code unter `/opt/codexpad/server/codexpad_server.py.pre-workspaces-20261003`
gesichert. Keine bestehenden Workspace-Dateien wurden für das Update verändert.

## Geänderte / neue Dateien

| Datei | Änderung |
| --- | --- |
| `server/codexpad_server.py` | Workspace-Routen, Validierung, sichere Dateisystemaktionen, Thread-Schutz, lesendes Session-Inventar im bestehenden Adapter |
| `server/test_workspaces.py` | 15 HTTP-/Dateisystem-/Session-Regressionstests |
| `server/verify_workspace_deployment.py` | Explizite lesende VPS-Prüfung vor/nach Deployment, Dateihashes und Thread-Zuordnungen |
| `server/README.md` | API-Vertrag und Sicherheitsgrenzen |
| `android/app/src/main/java/dev/codexpad/network/CodexPadApi.kt` | Bestehender OkHttp-Transport um Create/Rename/Inspection/Delete und feste Fehlercodes ergänzt |
| `android/app/src/main/java/dev/codexpad/model/WorkspaceName.kt` | Clientseitige Namensprüfung |
| `android/app/src/main/java/dev/codexpad/ui/PadViewModel.kt` | Lade-/Erfolgs-/Fehlerzustände und Listenabgleich ohne automatische Mutationswiederholung |
| `android/app/src/main/java/dev/codexpad/ui/CodexPadApp.kt` | Einbindung der Workspace-Aktionen, Sperre konkurrierender Einstellungen/Refresh |
| `android/app/src/main/java/dev/codexpad/ui/WorkspaceOverview.kt` | Anlegen-Button, dezente Kontextmenüs, Namensdialog und bewusste Löschbestätigung |
| `android/app/src/test/java/dev/codexpad/WorkspaceTest.kt` | Namensregeln, API-Vertrag, Fehlerübersetzung, kein Retry bei Antwortverlust |
| `android/app/src/androidTest/java/dev/codexpad/WorkspaceTabletTestRunner.kt` | Echte UI-/VPS-Abnahme einschließlich bestehender und leerer Threads |
| `android/tools/contract_server.py` | Vorhandenes deterministisches Backend um vollständige/archivierte Inventarfilter erweitert |
| `docs/verification-workspaces.md` | Dieser Bericht |

## Technische Umsetzung

Create: `POST /workspaces {name}` legt mit exklusivem `mkdir` einen Ordner mit
Modus 0700 unmittelbar unter dem konfigurierten Root an. Eine neue `AGENTS.md`
wird nicht erzeugt. Bestehende Namen, auch Dateien oder Symlinks, werden niemals
überschrieben. Die App aktualisiert die Liste und bleibt in der Übersicht.

Rename: `POST /workspaces/{id}/rename {name}` verwendet ein atomares Rename ohne
Ersetzen eines vorhandenen Ziels (`renameat2` unter Linux, `renameatx_np` unter
macOS). Dateien, Unterordner, Rechte und vorhandene `AGENTS.md` bleiben erhalten.

Delete: `GET /workspaces/{id}/inspection` zählt Dateien/Verknüpfungen, Unterordner
und Threads. Der Dialog benennt die dauerhafte Löschung des Ordners samt Inhalt.
Erst `POST /workspaces/{id}/delete {confirmation, inspection}` führt sie aus.
Name, Metadaten-Fingerprint und Thread-Zuordnung werden erneut geprüft; geänderte
Dateien oder neue Threads verhindern die Löschung. `shutil.rmtree` arbeitet mit
Root-Verzeichnisdeskriptor und Symlinkschutz. Verknüpfungsziele außerhalb des
Ordners und globale Codex-/Sessiondaten werden nicht gelöscht. Mounts sind gesperrt.

Validierung auf Client und Server: Namen trimmen; 1–80 Unicode-Zeichen,
höchstens 200 UTF-8-Bytes; Buchstaben/Ziffern, Leerzeichen, `_`, `-` und einzelne
Punkte. Erster Buchstabe/Ziffer erforderlich; kein abschließender Punkt, kein
`..`, Slash, Backslash, Steuerzeichen, Prozent-Escape oder Shell-Zeichen.
Quellen-IDs werden nicht stillschweigend normalisiert. Fremde JSON-Felder sind
verboten. Alle Mutationen arbeiten descriptor-relativ im Root und verwenden keine
Shell-Kommandos. Ein gemeinsamer Lock serialisiert Mutationen und Thread-Anlage.

Android nutzt die vorhandene API, Authentifizierung, Coroutine- und OkHttp-Logik.
Touch-Ziele sind mindestens 48 dp groß. Kontextaktionen und Löschdialog sind
getrennt vom normalen Tap zum Öffnen. Während einer Aktion sind konkurrierende
Aktionen gesperrt. Bei Erfolg und unbestätigten Antworten wird die Liste neu
gelesen; entfernte Einträge werden vorher invalidiert. Bekannte Fehlercodes werden
übersetzt, beliebige Serverfehlertexte und Tokens nicht angezeigt.

## Behandlung bestehender Threads

Threads sind über absolute `cwd`-Pfade zugeordnet. Weitere absolute Pfade stehen
in Verlauf, Tool-Ausgaben und Ergebnisreferenzen. Die installierte Schnittstelle
bietet zwar einen `thread/resume.cwd`-Override, aber keine atomare Migration aller
solcher Referenzen. **Rename und Delete werden deshalb bei bestehenden Threads
verständlich blockiert.** Keine Rollout-Dateien oder SQLite-Datenbanken werden
umgeschrieben; keine Threads werden pauschal archiviert oder gelöscht.

Die Prüfung umfasst alle Provider und Quellen, paginierte und archivierte Threads,
Fresh-Cache, Unterverzeichnisse und CWDs durch Symlinks. Auch eine Wiederverwendung
von Namen mit noch vorhandenen Thread-Referenzen ist gesperrt.

Beim Bestandsvergleich zeigte sich eine bestehende Codex-Eigenheit: `thread/list`
liefert leere persistierte Threads nach einem Neustart nicht, auch nicht mit
`useStateDbOnly`. Der bestehende App-Server-Adapter liest daher zusätzlich die
ersten `session_meta`-Zeilen der Rollouts unter dem effektiven Codex-Home. Diese
rein lesenden Referenzen sichern auch leere und archivierte Sessions ab. Die UI
ergänzt fehlende interaktive Threads über das reguläre `thread/read`; Subagent-
und archivierte Sessions bleiben in der normalen Übersicht ausgeblendet.
Nicht vollständig prüfbare Metadaten oder Backendfehler blockieren Änderungen.

## Durchgeführte Tests

- 34 Python-Tests insgesamt bestanden, auf macOS und auf dem tatsächlichen Linux-VPS.
- 15 davon prüfen Workspace-Create, Trimmen, keine Templates, Duplikate,
  ungültige Namen/Traversal, Rename mit Datei-/AGENTS-Erhalt, vorhandene Zielnamen,
  atomaren No-Replace-Schutz, Delete, Bestätigung, zwischenzeitliche Änderungen,
  Symlinkgrenzen, Thread-Schutz, Archiv/Fresh/Nested-CWD, leere persistierte
  Sessions, Pagination, Backendfehler, Auth und konkurrierende Anlagen.
- 50 Android-Tests bestanden, einschließlich des aktivierten Kotlin/Python-
  Vertragstests für POST, SSE, History, Disconnect, Reconnect und Fortsetzen;
  kein übersprungener Test im abschließenden Lauf.
- Debug-App und Test-APK gebaut und installiert. Lint: 0 Fehler, 7 bestehende
  Hinweise zu Abhängigkeiten/SettingsStore. `git diff --check` bestanden.

Reproduzierbarer Tablet-Aufruf nach Build/Installation des passenden Runners:

```sh
android/build-local.sh :app:assembleDebug :app:assembleDebugAndroidTest \
  -Pcodexpad.testRunner=dev.codexpad.WorkspaceTabletTestRunner
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.codexpad.test/dev.codexpad.WorkspaceTabletTestRunner
```

## Ergebnis auf dem echten Tablet

Der abschließende Lauf gegen den finalen Serverstand bestand vollständig:

1. Vorhandene Workspaces angezeigt.
2. Leeren Namen und `../escape` über gesperrten Speichern-Button abgefangen.
3. Temporären Workspace `tablet-workspace-1791013722832` über den Dialog angelegt,
   mit getrimmtem Namen und ohne automatische Navigation.
4. Duplikat über denselben Dialog versucht: Server lehnt ab, verständliche Meldung.
5. Workspace geöffnet, leere Thread-Übersicht geprüft und zurückgekehrt.
6. Über Kontextmenü nach `tablet-workspace-1791013722832-renamed` umbenannt;
   alter Eintrag verschwunden, neuer vorhanden.
7. Löschdialog geöffnet und abgebrochen: Ordner erhalten. Erneut geöffnet und
   bewusst bestätigt: Ordner gelöscht, Liste aktualisiert, kein Testeintrag übrig.
8. Je einen bestehenden Thread in `kurzfragen`, `ideen`, `projekt`, `testprojekt`
   über die App geöffnet: History und SSE-Resume erfolgreich.
9. Zusätzlich den zuvor ausgelassenen leeren Thread in `ideen` nach Serverneustart
   sichtbar gefunden und erfolgreich geöffnet/resumed.
10. Rename-Versuche in allen vier bestehenden Workspaces serverseitig gesperrt;
    Delete-Dialog zeigt vorhandene Threads und einen deaktivierten Löschbutton.

Es wurden keine Modellaufträge in vorhandenen Threads gestartet. Die Prüfung
belegt Verlauf, Zuordnung und SSE-Resume; sie behauptet keinen neuen Modellturn.

Der abschließende VPS-Vergleich bestätigte unveränderte SHA-256-Dateihashes,
Dateinamen, Rechte und Links für **alle fünf** vorhandenen Workspaces einschließlich
`unterhaltungen`. Alle zuvor sichtbaren 25 Thread-IDs sind erhalten und ihrem
bisherigen absoluten CWD zugeordnet. Zwei ältere leere Sessions in `testprojekt`
sind zusätzlich sichtbar geworden; ihre alten Erstellungszeiten und leeren
Verläufe wurden geprüft. Aktuelle sichtbare Anzahl: `ideen` 4, `kurzfragen` 3,
`projekt` 1, `testprojekt` 16, `unterhaltungen` 3. Kein temporärer Ordner blieb übrig.

## Bekannte Einschränkungen

- Rename/Delete sind bei Threads bewusst gesperrt, einschließlich leerer oder
  archivierter Sessions. Eine vollständige Migration der absoluten Referenzen ist
  nicht implementiert.
- Die ergänzende Session-Metadatenprüfung ist gegen Codex 0.156.1 geprüft.
  Unlesbare oder unbekannte Header verhindern Änderungen statt Threads zu übersehen.
- Die API-Serialisierung synchronisiert CodexPad-Aufrufe; sie ist keine Sperre
  für unabhängige administrative Prozesse auf dem Host.
- Keine Template-Auswahl und keine automatisch erstellte `AGENTS.md`.
