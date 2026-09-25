# Modell-/Reasoning-/Kontext-Slice: echtes USB-Tablet

25. September 2026, ca. 16:54–17:05 Uhr Europe/Berlin.

## Stand und Durchführung

- Repository vor Beginn sauber, HEAD `38886ae65b6ed84c2a9631c7067bc22330d37c3e`.
- ADB: `AWDVBB6428000843`, Modell `YLE_W09`, Produkt `YLE-W09EEA`, Android 16,
  USB-Status `device` (autorisiert).
- `android/build-local.sh`: `assembleDebug`, `testDebugUnitTest`, `lintDebug`
  erfolgreich. 26 Tests erfasst, 25 bestanden, ein opt-in Python-Vertragstest
  ohne gestartete lokale Gegenstelle übersprungen; keine Fehler. Vorhandene
  Build-Ausgaben wurden von Gradle teilweise inkrementell wiederverwendet.
- Sandbox blockierte zunächst die lokalen ADB-/Gradle-Sockets. Die freigegebenen
  Aufrufe außerhalb der Sandbox funktionierten; kein App-Fehler.
- `adb -s AWDVBB6428000843 install -r android/app/build/outputs/apk/debug/app-debug.apk`:
  `Success`. Paket `dev.codexpad`, Version `0.1.0`, versionCode `1`, Debug.
  Keine Deinstallation, kein `pm clear`, keine neuen Zugangsdaten.
- SHA-256 der lokalen APK **und der tatsächlich installierten `base.apk`**:
  `cdce8d2dbc2a6a42ca8fff0c0380fea3f2354e67ff532164a2183aab46f47ddd`.
- App verbindet sich mit den erhaltenen Einstellungen direkt zu
  `https://pad.feichti.dev`; keine ADB-Reverse-Weiterleitung eingerichtet.
- Bestehender VPS-Release gemäß Ausgangsstand des Auftrags; History bestätigt
  Codex `0.156.1`. Kein Deployment, Dienstneustart, Update oder Eingriff in
  Serverkonfiguration, Codex oder ADRs.

Bedienung und Beobachtung erfolgten durch ADB-Touches und native
UI-Automator-Hierarchien. Keine Umgehung von `FLAG_SECURE`. Zusätzlich wurden
`GET /models`, Thread-/History-GETs, ein SSE-GET und ausgewählte Felder des
Rollout-Logs über SSH ausschließlich lesend geprüft. Das Auth-Token blieb dabei
auf dem VPS und wurde weder ausgegeben noch lokal gespeichert. Keine Tokens
aus dem Tablet extrahiert.

Ein eigener Thread im Workspace `testprojekt` wurde genau einmal über die App
angelegt: `01a0d911-8fde-7af2-85f2-5b623c73dfff`. Alle neuen Turns stammen aus
der Tablet-App, mit ausdrücklichem Verbot von Tools und Dateiänderungen.
Keine mutierenden HTTP-Requests außerhalb der App, keine blinden Wiederholungen.

## Ergebnisse auf dem Gerät

| Prüfung | Beobachtung / Ergebnis |
| --- | --- |
| Start, Verbindung, erhaltene Daten | Workspace lädt; Threadliste enthält die bisherigen Threads. Alter Interrupt-Testthread `01a0d8f5-638c-7ca0-9230-e0a55fca37fe` lässt sich öffnen, zeigt `CODEXPAD-RESUME-OK`, `idle`, Live-Verbindung und Usage. Bestehende Threads wurden nicht beschrieben. |
| Modellanzeige aus Serverzustand | Neuer Thread zeigt `gpt-6-astra · Reasoning unbekannt`; GET bestätigt `model=gpt-6-astra`, `reasoningEffort=null`. Spätere Readbacks siehe Turntabelle. |
| Echter Modellkatalog | Picker zeigt alle sieben Modelle einschließlich Namen, Beschreibungen und Astra-Katalogdefault, übereinstimmend mit authentifiziertem `GET /models`: GPT-6-Astra/Sol/Luna, GPT-5.6-Sol/Terra/Luna, GPT-5.5. |
| Modellabhängige Efforts | Astra-Picker zeigt `low, medium, high, xhigh, max, ultra`; Luna zeigt `low, medium, high, xhigh, max`, kein `ultra`. Beide Listen stimmen exakt mit dem echten Katalog überein. Sol `low` wurde separat ausgewählt. |
| Inkompatibler Wechsel | Astra und anschließend `ultra` lokal vorgemerkt; Wechsel zu Luna setzt Effort auf dessen gültigen Katalogdefault `medium`. Dafür kein Turn gestartet. |
| Vormerkung bleibt lokal | UI zeigt ausdrücklich „Auswahl für nächste Nachricht · vorgemerkt“ und Luna/medium, während „Konfiguriert“ Astra/unbekannt bleibt. Ein zusätzlicher Thread-GET vor Senden bestätigt unverändert Astra/null. Nach erfolgreichem Senden verschwindet die Vormerkung. |
| Ohne Override | Erster Turn erfolgreich; UI vor Senden „ohne Override“, Rollout Astra/null. |
| Modellwechsel als Override | Luna/medium erfolgreich, später Sol/low erfolgreich. Modellwechsel und explizite Effortauswahl praktisch belegt. |
| Nur Modell, ohne Effortfeld | Über diese UI mit dem echten Katalog nicht separat auslösbar: `selectModel` wählt immer einen unterstützten Effort, alle gelieferten Modelle haben Efforts. Kein Feature ergänzt und kein Ersatz-POST außerhalb der App gesendet. Der bestehende, in diesem Lauf bestandene `ApiTest` prüft den Requestfall Modell ohne Effort gegen MockWebServer; dies ist kein Tablet-Nachweis. |
| Autoritativer Readback | Nach den abgeschlossenen Turns stimmen UI, HTTP-History und persistierter `turn_context` für Astra/null, Luna/medium und Sol/low überein. |
| Reroute überschreibt Konfiguration nicht | Kein echtes `model/rerouted` auf dem Tablet beobachtet oder künstlich auf dem VPS ausgelöst. Der bestandene bestehende Test `usageReplayFreshnessAndRerouteNeverChangeConfiguration` deckt die Trennung ab; kein neuer praktischer Reroute-Nachweis. |
| Fehlende Werte | Im leeren Thread: verwendet, Fenster und Rest jeweils „unbekannt“. Fehlender Reasoning-Wert ebenfalls „unbekannt“, kein erfundener Katalogdefault als Threadzustand. |
| Kontextberechnung und Schätzung | Letzter statt kumulierter Verbrauch und explizite Bezeichnung „Rest (Schätzung)“ bestätigt; Zahlen unten. |
| Disconnect / veraltet | App per Home in den Hintergrund, Tablet-WLAN vorübergehend ausgeschaltet, App wieder geöffnet. UI meldet Verbindungsverlust und zeigt die vorherigen Zahlen mit „Kontext · veraltet“. |
| Reconnect / Replay | WLAN wieder eingeschaltet. Nach regulärem Backoff zeigt App ohne neuen Turn wieder „Live verbunden“ und dieselbe Usage ohne „veraltet“. Separater lesender SSE-Anschluss liefert echtes `thread/tokenUsage/updated` mit diesen Werten. History und Modell bleiben erhalten. |
| Normale Hintergrund/Rückkehr | Zusätzlich nach Interrupt bei aktivem WLAN per Home und `am start` geprüft: Live-Verbindung, idle, Luna/medium, erhaltene History und aktuelle Usage. |
| Interrupt | Sichtbarer Zustand `active`/`inProgress`, Stoppen genau einmal betätigt, danach idle und Senden wieder verfügbar. HTTP-History bestätigt konkret `interrupted`; anschließender Sol/low-Turn erfolgreich. |

## Turns und autoritativer Endzustand

| Turn-ID | Auswahl beim Senden | Ergebnis |
| --- | --- | --- |
| `01a0d913-2ab2-7601-aeca-9bd31bb1f106` | Ohne Override, Astra / null | `completed`, `TABLET-DEFAULT-OK` |
| `01a0d915-54be-7c61-959f-b31db73bbea8` | `gpt-6-luna` / `medium` nach inkompatiblem Wechsel | `completed`, `TABLET-LUNA-MEDIUM-OK` |
| `01a0d917-51e6-7b90-b265-74a98d8df828` | Ohne neue Overrides; konfiguriert Luna / medium | `interrupted`, User- und Reasoning-Item, keine Tool-/Datei-Items |
| `01a0d918-c6d1-72d2-9271-06bd8ea2df37` | `gpt-6-sol` / explizit `low` | `completed`, `TABLET-SOL-LOW-RESUME-OK` |

Finaler History-Readback: genau diese vier Turns, Thread `idle`, Modell
`gpt-6-sol`, Effort `low`. Der unterbrochene Turn bleibt unterbrochen und wurde
nicht durch einen neuen Turn ersetzt. Die App bleibt im abgeschlossenen
Prüfthread geöffnet, WLAN ist wieder aktiviert.

## Kontext und Replay im Detail

| Stand | `last.totalTokens` / Anzeige verwendet | `total.totalTokens` | `modelContextWindow` / Fenster | Rest (Schätzung) |
| --- | ---: | ---: | ---: | ---: |
| Nach Default-Turn | 18988 | 18988 | 258400 | 239412 |
| Nach Luna/medium | 22762 | 41750 | 258400 | 235638 |
| Nach Sol/low | 26838 | 68588 | 258400 | 231562 |

Die persistierten Token-Count-Einträge bestätigen diese Zahlen. Besonders der
zweite und vierte Turn unterscheiden letzten Verbrauch klar vom kumulierten.
Der zusätzliche SSE-GET nach Reconnect lieferte:

```json
{
  "method": "thread/tokenUsage/updated",
  "params": {
    "threadId": "01a0d911-8fde-7af2-85f2-5b623c73dfff",
    "turnId": "01a0d915-54be-7c61-959f-b31db73bbea8",
    "tokenUsage": {
      "total": {"totalTokens": 41750},
      "last": {"totalTokens": 22762},
      "modelContextWindow": 258400
    }
  }
}
```

Auszug mit nur den relevanten Feldern. Der Replay-Stand nach dem Interrupt
gehörte weiterhin zum letzten abgeschlossenen Luna-Turn; keine neuen Zahlen
wurden für den abgebrochenen Turn erfunden.

## Grenzen und Abschluss

- Kein reproduzierbarer Fehler im neuen Android-Slice gefunden; daher keine
  Codekorrektur, kein zweiter Build und keine kosmetischen Änderungen.
- Für den laufenden App-Prozess enthielt der gefilterte AndroidRuntime-Error-Log
  keine Crashmeldung. Das ist keine vollständige Telemetrie aller HTTP-/SSE-Aufrufe.
- Keine Paketaufzeichnung der verschlüsselten App-Requests. Payload-Varianten
  werden durch UI-Zustand, bestehenden Clientcode/Tests und tatsächlichen
  Server-Readback belegt, nicht durch einen TLS-Mitschnitt.
- Reiner Modell-Override ohne Effort und echtes Reroute bleiben die oben
  ausdrücklich genannten Tablet-Testgrenzen. Ebenso wurde kein künstlich
  verzögertes/unterdrücktes Usage-Replay erzeugt: Die Abfolge „stale bis Usage,
  nicht schon durch History/Snapshot“ ist zusätzlich im bestandenen Sessiontest
  abgesichert; ihr kurzer Zwischenzustand bei funktionierendem Replay wurde
  nicht framegenau am Gerät gemessen.
- Verlorene POST-Antwort, Overflow, exaktes Interrupt-Ende-Rennen, Prozessverlust,
  TalkBack, Schriftgrößen und sämtliche weiteren Modellkombinationen wurden
  nicht neu praktisch getestet. Der kurze Zustand „Wird gestoppt …“ war beim
  nächsten UI-Dump bereits durch den abgeglichenen Endzustand ersetzt.
- Kein Commit und kein Push. Einzige Repositoryänderung dieses Laufs ist diese
  Dokumentation; Buildausgaben sind ignoriert. Temporäre UI-Dump-Datei auf dem
  Gerät nach Abschluss entfernt.
