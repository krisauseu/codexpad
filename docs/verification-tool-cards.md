# Android Tool-, Command- und Dateiänderungs-Karten

25. September 2026. Lokal in `/Users/kf/codexpad`, Ausgangs-HEAD
`8fb3393384db0a6d0acd7e3f8e440962945d827a`. Kein Commit und kein Push.
Bereits vorhanden waren Änderungen am Tablet-Layout in `CodexPadApp.kt` und
`docs/verification-compact-tablet.md`; beide bleiben erhalten.

## Implementierung und Vertrag

Keine Serveränderung, neue Route oder RPC-Erweiterung. Grundlage sind die bereits
transportierten App-Server-Items und Notifications. Feldformen wurden zusätzlich
mit dem lokal vorhandenen Codex-0.156.1-Schema aus dem Capability-Audit geprüft:
`ThreadItem`, `FileChangePatchUpdatedNotification`, `FileUpdateChange`,
`PatchChangeKind`, `McpToolCallResult`, `DynamicToolCallOutputContentItem`.

- `commandExecution`: Command, optionales cwd, Status, aggregierte Ausgabe,
  Exit-Code und Dauer in Millisekunden. Fehlende Ausgabe ist „Nicht vorhanden“,
  leere Ausgabe „Leer“. Ein vorhandener Exit-Code ungleich 0 wird als Fehler markiert.
- `mcpToolCall`: Server/Tool, formatierte Argumente, Text-/Ressourcen-Inhalte,
  `structuredContent`, Fehler und aufklappbare rohe Resultdetails. Ein fehlendes
  Resultat wird bei abgeschlossenem Status ausdrücklich als fehlend bezeichnet.
- `dynamicToolCall`: Namespace/Tool, Argumente, `contentItems` und das nullable
  `success`. Fehlendes `success` bleibt unbekannt. Bild-/Audioinhalte werden
  beschriftet, Base64 nicht in den normalen Kartenkörper übernommen.
- `fileChange`: Pfade, add/delete/update und optionales `move_path`, Status und
  einzelne Diffs. Gestartete Änderungen sind nicht als angewandt bestätigt.
  Jeder aufgeklappte Dateieintrag kennzeichnet den historischen Item-Diff als
  **keinen aktuellen Git-Arbeitsbaum**.

Karten starten eingeklappt und öffnen per Tap. Fehler, laufende und übrige
Zustände haben unterschiedliche Beschriftung/Farbe im bestehenden Material-Design.
Details sind auf 360 dp Höhe begrenzt und vertikal scrollbar. Text wird in
Abschnitten von höchstens 8000 UTF-16-Zeichen dargestellt, Dateien in Gruppen von
zehn, jeweils mit Navigation. Dadurch wird nicht die gesamte große Ausgabe in
einem einzelnen Textlayout vermessen. Die Timeline, Textkarten und bestehenden
Scroll-to-latest-/Tablet-Bedienelemente werden weiterverwendet.

## Reconciliation

Es gibt weiterhin nur das bestehende Timeline-Modell mit History und Live-Items.
Die typisierte Aktivität ist Teil von `Message`, kein zusätzlicher Store.

- `item/started` und `item/completed` ersetzen den vollständigen Itemzustand;
  `completed` entfernt zuvor vorhandene Live-Supplemente auch dann, wenn das
  finale Ausgabe-/Resultfeld fehlt. Ein verspätetes started öffnet abgeschlossene
  Aktivitäten nicht erneut.
- Aktivitäten werden anhand ihrer Item-ID zusammengeführt. Ein zweiter Command
  entfernt nicht mehr andere Command-Items desselben Turns. Die bestehende
  Legacy-Textbehandlung bleibt erhalten.
- `item/commandExecution/outputDelta` ergänzt ausschließlich einen vorhandenen,
  noch nicht abgeschlossenen Command. Der Live-Ausschnitt ist vom letzten
  Snapshot-Aggregat getrennt. Sind beide vorhanden, werden sie separat mit einem
  Überlappungshinweis angezeigt; sie werden niemals blind aneinandergehängt.
- `item/fileChange/patchUpdated` ersetzt die gesamte `changes`-Liste des aktuellen
  Items, einschließlich Pfaden, Änderungsarten und Diffs. Der Status bleibt dabei
  erhalten. Kein deprecated FileChange-Outputdelta und kein Turn-Diff als Ersatz.
- Jeder History-Abgleich bevorzugt vollständige bekannte Aktivitätsitems auch
  während eines laufenden Turns. Ein terminales History-Item wird durch queued
  starts/completions/deltas nicht zurückgesetzt. Terminale Turns verdrängen wie
  bisher den gesamten Live-Turn.
- Reconnect/SSE-Snapshot verwirft die alten Live-Daten. Nachfolgende Deltas können
  das im Snapshot enthaltene laufende Item ergänzen, ohne ein erneutes started
  vorauszusetzen. Ein Delta ohne bekanntes Item wird nicht zu einem erfundenen
  Item; der nächste reguläre History-Abgleich heilt den fehlenden Start.
- Ohne Replay-Offsets lässt sich überlappender Live-Output nicht zuverlässig zu
  einem lückenlosen Stream zusammensetzen. Deshalb bleibt er ein beschrifteter
  Ausschnitt. Fehlende Events werden über History geheilt, nicht durch angenommene
  Replay-Vollständigkeit. Ein terminaler Turn mit unvollständigem Itemabschluss
  erhält einen Hinweis statt einer weiterhin behaupteten laufenden Ausführung.

## Prüfungen

`android/build-local.sh` mit `CODEXPAD_CONTRACT_URL` gegen den unveränderten
lokalen `android/tools/contract_server.py`: **41 Tests, 0 Fehler, 0 übersprungen**.
`assembleDebug` und `lintDebug` erfolgreich. Gradle-/ADB-Sockets benötigten die
Ausführung außerhalb der Dateisystem-Sandbox. Der temporäre Fixture-Server wurde
anschließend beendet. Keine neue Serverarchitektur oder externen Dienste.

| Prüfung | Nachweis |
| --- | --- |
| Laufender/abgeschlossener Command; Ausgabe, Exit-Code, Dauer | ActivityTest und USB-Renderer |
| Fehlende/partielle Ausgabe; finales Item ersetzt Deltas | Parser-/Timeline-Tests; fehlende Ausgabe auch USB |
| MCP erfolgreich, MCP-Fehler und fehlendes Resultat | ActivityTest; Ergebnis und Fehler auch USB |
| Dynamic-Tool, false/null success, Text-/Bildinhalt | ActivityTest; true/null und Text auch USB |
| FileChange mit einem/mehreren Pfaden, Umbenennung | ActivityTest; mehrere Pfade auch USB |
| Patch-Update ersetzt Pfade/Diff, startet keinen Erfolgsstatus | ActivityTest, einschließlich verspätetem Update nach Abschluss |
| Reconnect während Command läuft | Neuer ThreadSessionTest mit Disconnect, zweitem Snapshot und neuem Delta |
| Abschluss während Verbindung ohne Abschluss-Event | Neuer ThreadSessionTest: reguläres Polling übernimmt finale History, kein POST |
| History bei aktivem Turn, mehrere Commands, verspätete Events | ActivityTest |
| Normale Text-Turns und Legacy-Reconciliation | Bestehende Timeline-/Sessiontests; lokaler HTTP-Vertragstest; USB |
| Compact, Interrupt, Modell-/Reasoning-Funktionen | Bestehende Unit-/API-Tests und echter lokaler HTTP-/SSE-Vertragstest; keine Regression |
| Lange Ausgabe/Diff auf Tablet | USB-Renderer mit 2500 Zeilen à über 100 Zeichen, Scroll und Abschnittswechsel; Kartenbereich überlappt unteren Platzhalter nicht |

Der USB-Test ist ein kleiner separater Android-Instrumentation-Runner ohne neue
Testbibliotheken. Er zeigt die echten `MessageCard`-/`ActivityCard`-Composables
mit deterministischen lokalen Daten in der MainActivity. Er verwendet öffentliche
Accessibility-Aktionen für Tap/Scroll und prüft Text und Grenzen. Er schreibt
keine Einstellungen und löst keine Tools oder Servermutationen aus. `FLAG_SECURE`
bleibt aktiv; keine Screenshots oder Umgehung der Screenshot-Sperre.

Reproduzieren:

```sh
android/build-local.sh :app:assembleDebug :app:assembleDebugAndroidTest \
  -Pcodexpad.testRunner=dev.codexpad.ToolCardsTestRunner
adb -s AWDVBB6428000843 install -r android/app/build/outputs/apk/debug/app-debug.apk
adb -s AWDVBB6428000843 install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s AWDVBB6428000843 shell am instrument -w dev.codexpad.test/dev.codexpad.ToolCardsTestRunner
```

Die optionale Gradle-Property wählt nur den Instrumentation-Runner; ohne Property
bleibt der bisherige Keystore-Runner eingestellt.

## Tablet und Installation

USB-Gerät `AWDVBB6428000843`, Modell `YLE_W09`, Android 16, Querformat
3000 × 1920 Pixel. Installation per `adb install -r`: **Success**, keine
Deinstallation, kein `pm clear`, keine Datenlöschung oder neuen Zugangsdaten.

Finale Debug-APK:
`android/app/build/outputs/apk/debug/app-debug.apk`

SHA-256 der lokalen APK und der auf dem Gerät installierten `base.apk`:
`184c3b96aca6a45c0e4159797df44346e4c62d64c25ad821f62da117d5d47c58`

Der USB-Renderer meldet auch gegen die endgültig installierte APK PASS für Commands, Ergebnis-/Fehlerkarten, Dynamic Tools,
mehrere Dateipfade, lange Ausgabe/Diffs und normale Textkarten.

Zusätzlicher Smoke-Test der normalen App mit erhaltenen Einstellungen:
Workspace `testprojekt` lädt von `https://pad.feichti.dev`. Bestehender Thread
`01a0d932-f6a5-7e82-95e0-c8a632e92eff` zeigt „Live verbunden“, `idle`, die
Kontextkomprimierung und `CODEXPAD-COMPACT-FOLLOWUP-OK`, konfigurierte
`gpt-6-astra`, Kontextwerte und den unveränderten Composer/Modellbereich. Das
Kontextmenü bietet „Kontext komprimieren“ aktiviert an; kein neuer Compact wurde
ausgelöst. Bestehende Threads wurden nur gelesen.

## Grenzen

- Kein neuer realer MCP-/Dynamic-/Dateiänderungs-Aufruf auf dem VPS für diese
  Prüfung. Tablet-Karten sind deterministische Renderer-Nachweise; Transport und
  Reconnect sind separat durch Contract-/Sessiontests belegt.
- Compact/Interrupt/Modellwechsel wurden in diesem Slice nicht erneut als
  mutierende VPS-Aktionen ausgeführt. Ihre Regressionstests bestanden; vorhandene
  Text-/Compact-History und Bedienelemente wurden zusätzlich am Tablet geprüft.
- Keine binäre Medienwiedergabe, Syntaxhervorhebung, Diff-Statistik oder Git-Abfrage.
  Unbekannte Itemtypen behalten die generische Typ-/Statusdarstellung.
- Angezeigte Strings sind seitenweise begrenzt; der vollständige Serverinhalt
  bleibt im Item im Speicher. Keine neue Persistenz oder Streaming-Dateiablage.
- Keine Vollprüfung von TalkBack, Portrait oder sämtlichen Schriftgrößen.

## Geänderte Dateien / Git

Neu: `Activity.kt`, `ActivityCard.kt`, `ActivityTest.kt`, `ToolCardsTestRunner.kt`
und diese Verifikation. Geändert: `Models.kt`, `Timeline.kt`, `CodexPadApp.kt`,
`ThreadSessionTest.kt`, `android/app/build.gradle.kts`.

Die bereits vorgefundenen Änderungen an `CodexPadApp.kt` bleiben Teil des
Arbeitsbaums; `docs/verification-compact-tablet.md` bleibt unverändert untracked.
Keine Server-/ADR-Änderungen. `git diff --check` sauber; nichts gestaged,
committet oder gepusht.
