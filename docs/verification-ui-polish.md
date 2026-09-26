# Android UI-/UX-Polish · 26.09.2026

Ausgangsstand: `880d1d7e3338ffa40bb83914b39b567953249387`.
Nur Android-Darstellung, opt-in-Gerätetest und Dokumentation geändert. Keine
Server-, API-, Session-, Upload-/Download- oder Navigationslogik verändert.
Kein VPS-Abgleich und kein Dienstneustart nötig.

## Sichtbare Änderungen

- `PadTheme.kt`: zusammenhängende Grün-/Salbei-Palette statt gemischter
  Material-Defaults, abgestufte helle Surfaces, einheitliche Rundungen,
  kräftigere Titel und 17-sp-Chattext mit 27-sp-Zeilenhöhe.
- `CodexPadApp.kt`: begrenzte Tablet-Inhaltsbreite, bessere Übersichtshierarchie,
  freundlichere Leerzustände, dezente IDs und lesbare Status-Badges. Benutzertexte
  rechts und getönt, Codex auf heller Surface, System-/Kompaktierung zurückhaltend.
  Turn-Grenzen als feine Trennlinien. Fehlermeldungen mit eigener Surface.
- Composer als zusammenhängende Karte; weniger redundante Metainformationen,
  Zeichenanzeige erst nahe dem Limit, kompakte Anhangsvorschau mit beschrifteten
  Entfernen-Aktionen. Senden/Anhängen mindestens 52 dp. Stoppen oben beim Status,
  wodurch die Rückfrage mehr Platz bekommt.
- `UserInputCard.kt`: freundliche Oberfläche statt Warnanmutung, ganze
  Auswahlzeilen anklickbar, ausgewählte Option klar eingefärbt, Antwort-Button
  mindestens 48 dp. Auf dem Referenztablet passen zwei Optionen, eigenes
  Antwortfeld und Button im Querformat in den verfügbaren Bereich.
- `ArtifactCard.kt`: Typkachel, Dateiname und Größe getrennt, klare Öffnen- und
  Speichern-Aktionen mit mindestens 48 dp, Ladeindikator.
- `ActivityCard.kt`: ruhigere Outline-Surface und großzügigerer Detail-Header;
  Paging und begrenzte Ausgabehöhe unverändert.

Das bisherige feste helle Erscheinungsbild bleibt bestehen. Keine zusätzliche
Dark-Mode-Implementierung und keine neue UI-Bibliothek.

## Lokale Prüfungen

`android/build-local.sh`: assembleDebug, testDebugUnitTest und lintDebug erfolgreich.
JUnit: 47 Fälle, 46 bestanden, 1 opt-in-Python-Vertragstest ohne lokale Gegenstelle
übersprungen; 0 Fehler. Lint ohne Fehler; Hinweise zu neueren Abhängigkeiten/AGP und UseKtx bleiben außerhalb dieses Polish-Passes. `git diff --check` sauber.

## Echter USB-Tablet-Lauf

Honor YLE_W09, Android 16, 3000 × 1920 im Querformat. Debug-APK erfolgreich mit
`adb install -r` aktualisiert. Vorher-/Nachher-Screenshots tatsächlich angesehen;
Bildbelege lokal unter `android/.local/polish/` (ignoriert, keine Gesprächsinhalte
im Git-Repository).

Echte bestehende HTTPS-Verbindung unverändert verwendet. Ein eigener neuer
`testprojekt`-Thread: `01a0de7b-4321-72d3-9231-f799c12faa1a`.
Modell-/Effort-Auswahl über die App-Dialoge: GPT-6-Luna / low. Kein Zugriffstoken
exportiert, keine Verbindungseinstellung gespeichert oder ersetzt.

| Prüfung | Ergebnis |
| --- | --- |
| Start, Workspace-/Thread-Liste, Refresh | Erfolgreich, Screenshots geprüft |
| Modell-/Reasoning-Dialoge | Auswahl und bestätigter Threadzustand geprüft |
| Anhangsvorschau | PNG + TXT, Entfernen und erneutes Hinzufügen erfolgreich |
| Upload | Echter Multipart-Upload; Codex bestätigt `UPLOAD-POLISH-OK` und dunkel türkisgrünes Bild |
| Rückfrage | Echtes request_user_input, Option `polish-beta.txt` ausgewählt, beantwortet, Karte aufgelöst |
| Download/Speichern | Echter Kartenbutton und Android-Speicherdialog; `/sdcard/Download/polish-beta.txt`, 16 Bytes, erwarteter Marker |
| Öffnen | Echter Kartenbutton, Honor-App-Auswahl, HTML-Anzeige zeigt den Marker |
| Normale Nachricht / Reconnect | Rückkehr aus Dateianzeige, Antwort `POLISH-NACHRICHT-OK`, Threadliste erneut geladen |
| Stoppen | Neuer Testturn über sichtbaren Button unterbrochen; Status `interrupted` bestätigt |
| Kompaktierung | Über Kontextmenü ausgelöst; Phase `completed`, sichtbarer Kompaktierungseintrag |
| Health / Einstellungen | „Verbunden · Token akzeptiert · Codex bereit“, danach wieder live verbunden |
| Toolkarten / lange Ausgaben | Bestehender `ToolCardsTestRunner` mit neuem Theme: PASS für 2500 Zeilen, Paging, MCP/Dynamic-Fehler, mehrere Dateipfade und begrenzte Diff-Höhe |
| Anhangs-Picker / Eingabefokus | Dokumentenauswahl geöffnet/zurück; Eingabefeld und vorhandene Tastaturleiste bedienbar |

Die erste Sichtprüfung zeigte eine unnötig hohe Rückfragekarte. Kopf verdichtet
und Stoppen nach oben verschoben; korrigiertes Layout erneut geprüft. Der Test
wurde im selben Thread fortgesetzt. Zwei weitere Testabbrüche betrafen ausschließlich
die Erwartung an Honors Dateiauswahl: fehlender Titel und unsichtbares Zero-width-
Zeichen im Label „HTML-Anzeige“. Der Runner berücksichtigt dies jetzt.
Der fortgesetzte Workflow und die separate Kontrollphase melden jeweils PASS.

Der opt-in-Runner `PolishTabletTestRunner` nutzt die vorhandene Tablet-Verbindung,
erzeugt reale Modellturns und eine Testdatei. Er läuft **nicht** im normalen Build.
Die Anhänge werden über reale FileProvider-URIs an denselben ViewModel-Einstieg
wie beim Picker übergeben; die Picker-Oberfläche wurde separat geöffnet/geprüft.
Das ist kein automatisierter Nachweis einer kompletten Dateiauswahl im Systempicker.

```sh
android/build-local.sh :app:assembleDebugAndroidTest -Pcodexpad.testRunner=dev.codexpad.PolishTabletTestRunner
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w dev.codexpad.test/dev.codexpad.PolishTabletTestRunner
# Separate Bedienprüfung im neuesten eindeutig markierten Polish-Testthread:
adb shell am instrument -w -e phase controls dev.codexpad.test/dev.codexpad.PolishTabletTestRunner
```

## Grenzen

- Markdown bleibt wie bisher Klartext; keine neue Render-Engine.
- Der echte Download-Lauf verwendet TXT. PDF-/Bildkarten nutzen dieselbe
  verfeinerte Darstellung, wurden in diesem Pass nicht erneut extern geöffnet.
- Die am Tablet konfigurierte kompakte Tastaturleiste wurde geprüft; keine
  zusätzliche Abnahme aller Bildschirmtastaturmodi, sehr großer Schrift oder
  TalkBack. Keine Systemeinstellungen dafür verändert.
- Testthread und gespeicherte Testdatei bleiben als nachvollziehbarer Nachweis erhalten.
