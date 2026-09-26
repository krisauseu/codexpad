# Interaktive Rückfragen: Codex 0.156.1

26. September 2026, Ausgangsstand `0bff16b98ad5bad889195f8a93e69f3d2c2e7766`.

## Tatsächlicher Vertrag

Auf dem Test-VPS liefert `codex --version` **0.156.1**. Mit diesem installierten
Binary wurde `codex app-server generate-ts --out /tmp/codexpad-user-input-schema`
ausgeführt, ohne Experimental-Export. Maßgeblich: `ServerRequest.ts`, `RequestId.ts`,
`v2/ToolRequestUserInput{Params,Question,Option,Answer,Response}.ts` und
`v2/ServerRequestResolvedNotification.ts`. Lokales PATH-Codex 0.139.0 wurde nicht
als 0.156.1-Nachweis verwendet oder ersetzt.

Der tatsächliche Methodenname ist **`item/tool/requestUserInput`**, nicht
`tool/requestUserInput`. Keine zusätzliche Client-RPC-Methode zur Antwort:

```json
{"id":0,"method":"item/tool/requestUserInput","params":{
  "threadId":"t","turnId":"u","itemId":"call",
  "questions":[{"id":"filename","header":"Filename","question":"Which filename?",
    "isOther":true,"isSecret":false,
    "options":[{"label":"alpha.txt","description":"Use alpha"},{"label":"beta.txt","description":"Use beta"}]}],
  "isBlocking":false,"autoResolutionMs":null
}}
{"id":0,"result":{"answers":{"filename":{"answers":["user-chosen.txt"]}}}}
{"method":"serverRequest/resolved","params":{"threadId":"t","requestId":0}}
```

Request-ID ist `string | number`; gleiche Zeichenfolge und Zahl werden getrennt
gehalten. Antwortzuordnung innerhalb der Response erfolgt über die Frage-ID.
`options` darf im Schema null sein, `isOther` erlaubt eine eigene Textantwort,
`isSecret` maskiert das Eingabefeld. Es gibt kein Mehrfachauswahl-Merkmal. Das
Array in `answers` ist kein Beleg für eine Mehrfachauswahl-UI: v1 sendet genau
einen String pro Frage. Mehrere Fragen werden zusammen beantwortet.

Der getaggte Quellcode, Commit `b412ff32c417f855c2b2d1581b77058eed87c84b`, erklärt
die Laufzeitvarianten (lokaler Audit-Checkout unter `/tmp`):

- `core/src/tools/handlers/request_user_input_spec.rs`: Das eingebaute Tool verlangt
  mindestens eine Option pro Frage und setzt `isOther=true`. Ein reiner Freitext-
  Modellaufruf ohne Optionen wird schon dort abgewiesen. Der echte Freitexttest
  nutzt daher die eigene Antwort; options=null ist nur mit Vertragsdaten geprüft.
- `tools/src/tool_config.rs` und `features/src/lib.rs`: Default-Modus benötigt
  `features.default_mode_request_user_input=true` (standardmäßig false). Der Adapter
  aktiviert ausschließlich dieses Feature als Prozessargument; keine globale
  Configänderung und keine Änderung der Approval-Policy.
- `core/src/tools/handlers/request_user_input.rs` und `session/mod.rs`: Plan-Modus
  markiert `isBlocking=true`, Default-Modus false. Das Tool wartet auf seine
  Antwort. `autoResolutionMs` ist deprecated und hier null; kein erfundener
  clientseitiger Ablauf-Timer.
- `app-server/src/request_processors/thread_lifecycle.rs`: Resume einer geladenen
  Session wiederholt offene Serverrequests mit derselben ID nach der Response.
  `thread/read`/History enthalten selbst keine offenen Callback-Requests.
- `app-server/src/bespoke_event_handling.rs`: Antwort löst den Serverrequest auf
  und übergibt `UserInputAnswer` an den vorhandenen Turn. Turnende/Abbruch beenden
  offene Requests. Eine Response hat keine eigene RPC-Bestätigung.
- `app-server/src/request_processors/turn_processor.rs`: `turn/start` nutzt
  `start_or_steer_turn`. Im echten Zusatztest blieb die nicht blockierende Frage
  offen und die zusätzliche Nachricht erhielt dieselbe Turn-ID. Deshalb bleibt
  der normale Composer bei solchen Fragen nutzbar; blockierende Fragen behalten
  die bisherige Sperre für laufende Turns. Kein neuer Queue-/Steer-Endpunkt.

Nicht unterstützt: Command-/File-/Permission-Approvals, MCP-Elicitation,
`request_user_input_async`-Agentnachrichten (anderer Vertrag), Policy-Umschaltung.
`approvalPolicy: "never"` und `dangerFullAccess` bleiben erhalten. Unbekannte
Serverrequests erhalten weiterhin einen JSON-RPC-Fehler, niemals einen Grant.

## CodexPad-Vertrag und Reconciliation

`thread.pendingRequests` wird bei GET Thread, GET History und im SSE-Snapshot
ergänzt. Ein Eintrag enthält eine undurchsichtige `id`, `threadId`, `turnId`,
`itemId`, `questions`, `isBlocking`, `autoResolutionMs`, `status` (`pending` oder
`answering`). Die App-Server-RPC-ID bleibt intern. SSE `codexpad/requests/changed`
und `serverRequest/resolved` stoßen den vorhandenen History-Abgleich an. Sie sind
Hinweise, kein notwendiges Event-Replay; zusätzlich heilt der bestehende Poll.

```http
POST /threads/{threadId}/requests/{opaqueRequestId}/answer
Authorization: Bearer …
Content-Type: application/json

{"answers":{"filename":{"text":"mein-name.txt"},"variant":{"option":"Blau"}}}
```

Jede Frage genau einmal; entweder `text` oder `option`. Optionen müssen exakt
einem gelieferten Label entsprechen. Freitext ist bei fehlenden Optionen oder
`isOther=true` zulässig. Nichtleerer Text, maximal 4096 Zeichen je Antwort, gesamter
JSON-Body maximal 16 KiB. Erfolgreiches Schreiben der Response: 202 `{}`;
endgültige Auflösung aus dem bestehenden Lifecycle. Ungültiger Body: 400;
unbekannte/fremde ID: 404; bereits beanspruchte/aufgelöste ID: 409. Bearer-Prüfung
vor Backendzugriff; frisches Thread-Read prüft Workspace-Zugehörigkeit und Liveness.

Der Adapter hält nur Callbacks seiner bestehenden stdio-Verbindung im Speicher:
RPC-Korrelation, Fragen und Status. Ein Lock beansprucht die Antwort vor dem
Schreiben; auch ein partiell fehlgeschlagener Write wird nicht wiederholt.
Auflösung entfernt den Frageninhalt; eine kleine Korrelation bleibt als Tombstone
für 409 und Resume-Deduplizierung. Keine Speicherung der abgegebenen Antwort.
Turnende, thread/closed und stdio-Ende schließen offene Einträge; terminale History
bereinigt zusätzlich. Keine zweite Agent-State-Maschine und keine Datenbank.

Android-Reconnect beeinträchtigt den langlebigen Adapter nicht. Neue HTTP-/SSE-
Clients laden dieselben offenen Fragen aus dem Snapshot; Resume-Replays öffnen
beantwortete IDs nicht erneut. Bei Adapterneustart endet auch dessen eigener
App-Server-Prozess: alte Callbacks sind nicht mehr beantwortbar. Zufällige öffentliche
IDs verhindern, dass wiederverwendete numerische RPC-IDs alte Antworten annehmen.
Historische Tool-Texte werden niemals zu offenen Rückfragen umgedeutet.

Android zeigt Karten in der scrollbar bleibenden History, ohne Warnungs-/Approval-
Wording. Neue offene Karten beginnen am Fragetext. Modell-Auswahl für die nächste
Nachricht ist währenddessen ausgeblendet, bestehende Entwürfe bleiben erhalten.
Radiozeilen sind semantisch als Einzelauswahl markiert; eigene Texteingabe hebt
die Auswahl auf. Aufgelöste Karten verschwinden aus dem aktiven Eingabestatus.

Vor POST wird die Request-ID im SavedStateHandle vorgemerkt; Doppeltaps bleiben
auch nach Rotation/üblicher Zustandswiederherstellung gesperrt. Bei Antwortverlust
kein Retry: zuerst History/State-Abgleich. Ist die Anfrage dort noch pending,
kann der Benutzer über „Status prüfen / offene Antwort erneut bearbeiten“ bewusst
wieder bearbeiten. `answering` wird nie automatisch wieder freigegeben.
Frageentwürfe werden nur in der Composition gehalten, nicht dauerhaft gespeichert;
insbesondere gibt es kein Saved-State-Speichern geheimer Texte.

## Verifikation

Ein erster direkter echter Protokolllauf:
Thread `01a0dd5c-1b09-7f83-ad12-8dba0ec670ac`,
Turn `01a0dd5c-1b5e-7cd3-ae24-aa70fb73fea0`.
Modellfrage mit `isBlocking=false`, `autoResolutionMs=null`, zwei Optionen und
`isOther=true`. Resume duplizierte die Karte nicht. Antwort `user-chosen.txt`,
`serverRequest/resolved` für numerische ID 0, finales `user-chosen.txt` im selben
abgeschlossenen Turn.

USB-Tablet YLE_W09, Android 16, `AWDVBB6428000843`. Separater temporärer Server mit
installiertem Codex 0.156.1, Python 3.12.3, Workspace unter `/tmp`, Loopback-Port
18766, SSH-Tunnel und ADB-Reverse. Normaler MainActivity-/CodexPadApp-/ThreadSession-
Pfad, echte Modellaufrufe, keine eingespeisten Rückfragefixtures. Testverbindung
nur temporär; verschlüsselte Produktionseinstellungen auf dem Tablet anschließend
bytegleich wiederhergestellt. Produktionstoken blieb auf dem Tablet; Produktivdienst,
Caddy und Dienstrechte unangetastet.

Der opt-in `UserInputTestRunner` startet A/B, bedient Textfeld/Radiozeile/Antwortbutton
über Android Accessibility, schließt/öffnet bei A die Activity und den Thread neu,
tippt doppelt und prüft anschließend die erledigte ID auf 409. Der erste Layoutlauf
deckte das Abschneiden des Fragenanfangs durch Tail-Scroll auf; korrigiert und
anschließend erfolgreich wiederholt. Ein abgebrochener SSH-Tunnel wurde mit
Keepalive neu aufgebaut, ohne den Backendprozess zu ersetzen.

Finaler Tablet-Thread: `01a0dd6c-2973-7261-807c-d042c1734388`.

| Test | Turn | Ergebnis |
| --- | --- | --- |
| A – Freitext als eigene Antwort | `01a0dd6c-2a3e-77c0-828f-957b6ac5e953` | `tablet-eigener-name.txt`, Inhalt exakt `INPUT-TEXT-OK` (13 Bytes), derselbe Turn completed |
| B – Einzelauswahl | `01a0dd6c-9340-73f2-8f78-1662d6b72aef` | Radioauswahl Blau, finale Antwort `AUSWAHL-OK Blau`, derselbe Turn completed |

Bei A: Activity geschlossen, frischer ThreadSession-/History-/SSE-Aufbau, dieselbe
offene Request-ID `f3f0530e917e9300ad4e33b3246ed572` wieder sichtbar. Zusätzliche
Nachricht über den normalen Composer ging an denselben Turn; die Frage blieb offen.
Beide Tests: Doppeltap, anschließend keine aktive Karte, POST auf die erledigte
Request-ID ergibt 409. Finale Screenshots visuell geprüft: Fragetext, Optionen,
Textfeld und Antwortbutton auf dem Referenztablet sichtbar. Ergebnisdatei separat
über die authentifizierte Artefaktroute gelesen, SHA-256
`6ee60b3c2e76613351c37714ca4d708a13e1bffc8a1c4386d8e4f1f0d209d396`.
Tablet-Verbindung nach dem Test wieder `https://pad.feichti.dev`.
Final installierte Debug-APK: SHA-256
`4da604f1ca3b62cc0b16026bdc72f94c222e959e434948d3807ecd39a0f2054c`.
Neuer Server: SHA-256
`6d97fcade4b0356abf61448f22b957f339ae7c34958789fb04da06095cbcccae`.
Temporärer Testserver/Tunnel beendet, temporäres Token entfernt. Produktionsdienst
weiter aktiv, lokales `/health` ok; Live-Serverdatei unverändert mit SHA-256
`a45bea2c1a0db558056fe7a5e4e717edda386a2cbe534fbb7a8ddbce637eab3e`.
Der Produktionsabgleich erfolgt separat anhand des gepushten Commits.

Lokale Tests: 19 Python-Tests; Android-Build, Lint und **47 JVM-Tests, 0 übersprungen**, einschließlich
Kotlin/Python-Vertragstest. Neue gezielte Prüfungen: Thread-Bindung, Auth, falsche
Optionen und Frage-IDs, parallele Antworten, Write-Verlust ohne erneute Weiterleitung,
String-/Zahl-IDs, Resume-Deduplizierung, History-Cleanup, neue Prozess-IDs, mehrere
Fragen und reiner Freitext. JVM prüft Antwortformat/Auth, verlorene HTTP-Antwort,
Doppeltap, Wiederherstellung der Sendesperre, Poll-Heilung nach fremder Auflösung
und bewusstes erneutes Bearbeiten nach frischem Abgleich.

Bekannte Grenzen: Kein Mehrfachauswahl-UI, kein dauerhaftes Antwortjournal, kein
Fortsetzen alter Callbacks nach App-Server-Neustart, keine Push-/Hintergrundzusage.
Reiner Freitext ohne Optionen und `isSecret` sind nicht mit einem echten Modellrequest
auf dem Tablet nachgewiesen. Netzverlust unmittelbar nach POST ist gezielt mit
Testgegenstellen geprüft, nicht als physischer USB-Kabelabriss. Kein Beweis für
Exactly-once über Prozessneustarts; stattdessen keine Wiederholung alter IDs.
