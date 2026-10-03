# CodexPad Android 0.2.0

Host-Einrichtung und praktisch bestätigter Pi-Betrieb: [HOST_SETUP](../HOST_SETUP.md#10-netzwerk-und-android-verbindung).
Stand 3. Oktober 2026: Direktes privates LAN-HTTP ist mit ausdrücklicher,
an die Serveradresse gebundener Freigabe verfügbar. Target SDK bleibt 37;
`ACCESS_LOCAL_NETWORK` ist ab Android 17 mit Runtimeprüfung angebunden.
Die Systemberechtigung ist von fachlichen Codex-Rückfragen getrennt.
[Implementierung und Pi-Tablet-Abnahme](VERIFICATION-TRUSTED-LAN.md).

Nativer persönlicher Single-User-Client für die vorhandene CodexPad-HTTP-API. Workspaces → Threads → Thread-Detail sowie Verbindungseinstellungen. Keine zusätzlichen Hostfähigkeiten. [Build- und Tablet-Nachweis](VERIFICATION.md).

## Lokaler Build

Auf diesem Mac sind Android Studio, dessen JBR 25.0.3, Android SDK 37.0 und Build Tools 36.0.0 vorhanden. Das Script verwendet das mitgelieferte Java und das SDK unter `~/Library/Android/sdk`, sofern `JAVA_HOME`/`ANDROID_HOME` nicht gesetzt sind. Gradle-Caches liegen standardmäßig unter `android/.local/gradle` (ignoriert).

```sh
cd /Users/kf/codexpad
android/build-local.sh
```

Führt `:app:assembleDebug :app:testDebugUnitTest :app:lintDebug` aus. APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Erster Lauf benötigt Internet für Gradle/Google Maven/Maven Central. Der eingecheckte Gradle-Wrapper prüft den Distributions-SHA-256. Android Studio: Ordner `android/` öffnen, Gradle-JDK auf das mitgelieferte JBR setzen und SDK 37 installieren, falls auf einem anderen Mac noch nicht vorhanden. Alternativ mit JDK 17+ (hier tatsächlich Java 25 getestet) und konfiguriertem SDK `./gradlew` aufrufen.

Gepinnt: AGP 9.3.3, Gradle 9.5.0, AGPs eingebautes Kotlin 2.2.10 samt Compose-Compiler 2.2.10; Compose BOM 2026.09.00, Activity 1.13.0, Lifecycle 2.11.0, Coroutines 1.10.2, OkHttp 4.12.0. minSdk 26 (Android 8), compile/targetSdk 37 (Android 17), Java/Kotlin-Bytecode 17. Keine Preview-Abhängigkeiten. Versionsgrundlagen: [AGP 9.3](https://developer.android.com/build/releases/agp-9-3-0-release-notes), [Compose BOM](https://developer.android.com/develop/ui/compose/bom), [Activity](https://developer.android.com/jetpack/androidx/releases/activity), [Lifecycle](https://developer.android.com/jetpack/androidx/releases/lifecycle).

## Persönlicher Release

`v0.1.0` wurde als Debug-/Test-APK veröffentlicht. Seit `v0.1.1` verwenden Releases
den nicht debuggable Release-Build und das vorhandene lokale Android-Testzertifikat
für datenbewahrende Updates der bisherigen Installation.
Die aktuelle Version ist `0.2.0` mit `versionCode` 3; Release-Asset: `CodexPad-0.2.0.apk`.

```sh
android/build-local.sh :app:assembleRelease :app:lintRelease -Pcodexpad.testSignedRelease=true
```

Ohne diese explizite Eigenschaft bleibt der Release-Build unsigniert. Keystore und
APKs werden nicht eingecheckt. Das ist weiterhin ein persönlicher Test-Release;
eine separate Produktionssignierung ist noch nicht eingerichtet.

Der gezielte Realgerätetest verwendet gespeicherte Verbindungseinstellungen und
einen ausdrücklich übergebenen bestehenden Prüfthread im Workspace `testprojekt`.
Er startet genau einen kurzen echten Turn, prüft die sichtbare Statuszeile sowie
erneutes Öffnen und Reconnect; er exportiert keine Zugangsdaten:

```sh
android/build-local.sh :app:assembleDebug :app:assembleDebugAndroidTest -Pcodexpad.testRunner=dev.codexpad.StatuslineTabletTestRunner
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e thread <bestehende-Prüfthread-ID> dev.codexpad.test/dev.codexpad.StatuslineTabletTestRunner
```

## Verbindungseinstellungen

Die Standardadresse ist `https://pad.feichti.dev`. Unter **Einstellungen** lassen sich
Server-URL und Zugriffstoken ändern. Ohne Token öffnet sich dieser Bereich automatisch.
**Verbindung testen** prüft die aktuellen Eingaben ohne sie zu speichern: erst eine
authentifizierte Workspace-Abfrage, dann den minimalen Backend-Healthstatus. Fehler
unterscheiden abgelehntes Token, DNS, TLS, Erreichbarkeit und Timeout. **Speichern**
übernimmt die Verbindung und öffnet die Workspace-Liste. Unbestätigte POSTs werden
weiterhin nie automatisch wiederholt.

Das gespeicherte Token wird nie zurück ins Eingabefeld geladen; ein leeres Feld behält
es bei. Eine andere Serveradresse verlangt eine erneute Token-Eingabe. Neue Eingaben
sind vollständig maskiert, ohne Anzeige-Button, ohne Saved-State-Persistenz und ohne
Klartext-Logging. Screenshots der App sind möglich; vor dem Teilen sichtbare Gesprächsinhalte prüfen.
Android Keystore hält einen AES-256-Schlüssel; app-private Preferences enthalten nur
AES-GCM-Ciphertext, zufälligen IV, URL und deren nicht geheime LAN-HTTP-Freigabe. Die URL ist als AAD an das Token gebunden.
Keystore-Zugriffe laufen auf einem IO-Dispatcher. Bei Schlüsselverlust neu eingeben;
kein Klartext-Fallback. Backup und Device-Transfer bleiben abgeschaltet.

HTTPS bleibt für alle Server zulässig. Für private IPv4-Adressen aus `10/8`,
`172.16/12` oder `192.168/16` kann **Privates LAN über HTTP erlauben** bewusst
aktiviert werden, zum Beispiel für `http://172.16.16.39:8876`. Die Option ist
standardmäßig aus und gilt nur für die normalisierte Serveradresse einschließlich
Schema und Port. Ein Adresswechsel setzt die Zustimmung zurück. Öffentliche IPs
und DNS-Namen erhalten keine HTTP-Freigabe; IPv6-HTTP bleibt im regulären LAN-Modus gesperrt.
HTTP überträgt Token und Inhalte unverschlüsselt und ist nur für ein
vertrauenswürdiges privates LAN gedacht. Debug erlaubt außerdem HTTP für
`127.0.0.1`, `localhost`, `::1` und Emulatorhost `10.0.2.2`. Für die bisherige Entwicklung
`http://127.0.0.1:8765` in den Einstellungen eintragen und ADB-Reverse setzen:

```sh
adb reverse tcp:8765 tcp:8765
```

Ein vorhandener lokaler SSH-Tunnel zum VPS kann wie bisher dahinter liegen. Auch Debug
braucht das Server-Token. Die LAN-Freigabe steht auch im Release-Client zur Verfügung. URLs mit eingebetteten
Zugangsdaten, Query, Fragment oder zusätzlichem Pfad werden abgelehnt; Redirects werden
nie verfolgt. `-Pcodexpad.serverUrl=...` ändert bei Bedarf nur den Build-Default, nie ein
Token und nie bereits gespeicherte Einstellungen.

Ab Android 17 ist vor LAN-Verkehr zusätzlich der aktuelle Grant für
`ACCESS_LOCAL_NETWORK` erforderlich, auch für private HTTPS-Ziele und per DNS
aufgelöste LAN-Adressen. Bei fehlender Berechtigung erscheint eine verständliche
Meldung mit einem expliziten Berechtigungsbutton. Ablehnung/Widerruf sperren auch
Sessions, Reconnect, SSE und Transfers; automatische Permission-Prompts gibt es
nicht. Ältere Android-Versionen bleiben ohne diese Runtimeanforderung. Der echte
API-37-Systemdialog ist mangels API-37-Gerät noch nicht praktisch getestet;
die erfolgreiche Pi-Abnahme lief auf HONOR YLE-W09, Android 16/API 36.

Die statische Network Security Configuration ermöglicht Cleartext technisch;
die URLpolicy entscheidet zusätzlich beim Laden, Speichern und Erstellen jedes
API-Clients. TLS verwendet weiterhin System-Trustanker und Hostnameprüfung.

[Direkter HTTPS-Betrieb und einmalige VPS-/DNS-Schritte](../docs/deployment.md).

## Struktur

```text
app/src/main/java/dev/codexpad/
  MainActivity.kt          Activity und Compose-Einstieg
  model/Models.kt          kleine Anzeigeobjekte und JSON-Parser
  network/CodexPadApi.kt   HTTP, Timeouts, abbrechbares SSE, keine POST-Retries
  network/LocalNetworkPolicy.kt  aktuelle LAN-Permission für alle Transports
  network/SseParser.kt     SSE-Zeilenframing
  data/Timeline.kt         History plus vorläufige Live-Items
  data/ThreadSession.kt    Vordergrundverbindung und Wiederabgleich
  ui/PadViewModel.kt       Navigation, Listen, Entwurf und Sendestatus
  ui/CodexPadApp.kt        Compose-Screens und Einstellungen
  settings/               URL-/Tokenprüfung und Android-Keystore-Speicher
app/src/test/              kleine Logik-/HTTP-Tests
tools/                    lokale Vertragstest-Gegenstelle
```

Ein ViewModel mit SavedStateHandle hält Workspace-/Thread-Auswahl, Entwurf und unbestätigte Mutation über Rotation bzw. reguläre Android-Zustandswiederherstellung. Keine Datenbank, kein DI- oder Navigationsframework. Beim Hintergrundwechsel wird SSE abgebrochen; bei Rückkehr vollständiger Abgleich. Prozessverlust führt nie zu automatischem Wiederholen eines Auftrags. Android-Saved-State ist kein dauerhaftes Transaktionsjournal, insbesondere nicht nach Force-stop.

## Implementierter Vertrag

Maßgeblich ist `server/codexpad_server.py`, nicht ein hypothetisches REST-Schema.

| Route | Verwendete Felder |
| --- | --- |
| `GET /health` | `status` (`ok` oder `unavailable`); keine Backend-Metadaten |
| `GET /models` | vollständiger schmaler Modellkatalog mit RPC-Selektor und modellabhängigen Efforts |
| `GET /workspaces` | `workspaces[]`: `id`, `name` |
| `GET /workspaces/:id/threads` | `threads[]`: `id`, `preview`, `status.type` |
| `POST /workspaces/:id/threads` | Request `{}`, Response `thread` |
| `GET /threads/:id` | `thread`, insbesondere ID und Status |
| `GET /threads/:id/history` | `thread.turns[]`: `id`, `status`, `error.message`, `items` |
| `POST /threads/:id/turns` | JSON für Text oder Multipart mit `message`, optional `model`/`effort`, `image`/`file`; Response `turn` |
| `POST /threads/:id/turns/:turnId/interrupt` | Request `{}`, HTTP 202 bestätigt nur RPC; Ende aus SSE/History |
| `GET /threads/:id/events` | SSE `snapshot` mit `thread`; SSE `event` mit `method`, `params` |

User-Items: `type=userMessage`, `content[]` mit `type=text`, `text`. Agent-Items: `type=agentMessage`, `text`. Sonstige Itemtypen erscheinen als kleine Typ-/Statusangabe, nicht als erfundene Textantwort. `preview` ist Vorschautext, kein behaupteter Titel; fehlt er, wird die ID angezeigt. IDs werden als URL-Pfadsegmente kodiert.

Normale Prompts akzeptieren bis zu 12.000 Unicode-Zeichen. Der Composer zeigt ab
10.251 Zeichen den Zähler, oberhalb des Limits eine Fehlermeldung und sperrt Senden.
ViewModel, HTTP-Client und Server prüfen dasselbe Limit; Inhalte einschließlich
Rand-Leerzeichen und Zeilenumbrüchen bleiben unverändert. Der SAF-Dateipicker und
die Upload-Prüfung erlauben zusätzlich UTF-8 `.html`/`.htm` mit `text/html`;
die vorhandenen Größen- und Workspace-Grenzen bleiben bestehen. Keine HTML-Vorschau.

### Kleine kompatible Serverkorrektur

Beim Clientanschluss festgestellt: Der bestehende Router zerlegt den URL-Pfad, dekodiert jedoch keine Prozentkodierung. Workspaces mit Leerzeichen, Umlauten oder `#` stehen im Katalog, sind über einen regulären HTTP-Client aber nicht auswählbar. Die notwendige kompatible Korrektur dekodiert jedes bereits getrennte Segment genau einmal (`urllib.parse.unquote`). Die API und Workspace-Zuordnung bleiben gleich. Ein lokaler Vertragstest verwendet ausdrücklich `demo space-ä`.

## SSE und Reconnect

1. Threadzustand per GET lesen, vollständige History laden, dann SSE öffnen. Erst nach dessen initialem Snapshot gilt der Screen als verbunden und erlaubt neue Turns.
2. Der Server registriert vor seinem SSE-Snapshot bereits den Subscriber. Dieser zweite Snapshot schließt das Zeitfenster zwischen den ersten GETs und dem Live-Abonnement.
3. `item/agentMessage/delta` wird pro Turn/Live-Item verkettet. `item/started` und `item/completed` ergänzen/ersetzen Live-Items. `turn/started` zeigt einen laufenden Turn. `turn/completed`, `thread/status/changed` und `error` lösen einen History-Abgleich aus. Unbekannte Events werden ignoriert; `codexpad/overflow` erzwingt Reconnect.
4. Abgeschlossene History ersetzt den gesamten Turn und entfernt dessen Live-Items. Ältere queued Events und verspätete POST-Acknowledgements dürfen abgeschlossene Turns nicht wieder öffnen. Kein Merge anhand der möglicherweise wechselnden Legacy-Item-IDs.
5. Bei laufenden Turns ersetzt ein Live-Ausschnitt die entsprechenden partiellen History-Items nach Typ. Snapshot-Präfixe werden niemals mit möglicherweise überlappenden Deltas zusammengeklebt. Solange unvollständig, ist Agenttext ausdrücklich als Live-Ausschnitt gekennzeichnet.
6. Nach EOF/Netzfehler/Overflow Backoff 1/2/4/8/15 Sekunden; erneut Snapshot → History → SSE. Alte Live-Fragmente werden beim erfolgreichen neuen Abgleich verworfen. Heartbeats werden gelesen, aber nicht als Inhalt angezeigt. Nach Aufbau gilt ein 45-Sekunden-Lesetimeout (Server-Heartbeat: 15 Sekunden).
7. Ergänzend History-Abgleich alle 5 Sekunden bei laufenden Turns, sonst alle 15 Sekunden, ausschließlich im Vordergrund. Das heilt fehlende Endevents und erkennt einen ausgefallenen App Server trotz weiterlaufender HTTP-Heartbeats.

POSTs haben weder automatische Transport-Retries noch Redirect-Following. Bei unbestätigtem Turnstart bleiben Entwurf und Warnung erhalten; erst nach bewusstem Prüfen des Verlaufs wird Senden wieder möglich. Auch Threadanlage wird bei verlorenem Ergebnis nicht automatisch wiederholt. Die App behauptet keine Exactly-once-Garantie.

## Turn stoppen

Ein nativer „Stoppen“-Button verwendet die konkrete laufende Turn-ID aus dem
abgeglichenen Zustand. Ohne Live-Verbindung oder bei mehrdeutiger Turn-ID bleibt er
gesperrt. Nach Betätigung erscheint „Wird gestoppt …“; der exakte Ziel-Turn wird vor
HTTP im SavedStateHandle vorgemerkt. Doppeltippen und automatische Wiederholungen
sind gesperrt, auch nach einer verlorenen Antwort oder Zustandswiederherstellung.
Nur ein terminaler History-/SSE-Snapshot dieses Turns löst die Vormerkung auf:
`interrupted`, `completed` und `failed` sind gleichermaßen gültige Endzustände.
Ein HTTP-Fehler bleibt als unbestätigter Abbruch sichtbar, bis der Abgleich das Ende
feststellt. Ein neuerer Turn wird niemals als Ersatz für den ursprünglichen gestoppt.

## Grenzen / Folgepunkte

- Tablet-Lauf gegen lokale Vertragsfixtures und [historischer Interrupt-/Fortsetzungs-Lauf gegen VPS mit Codex 0.156.1](../docs/verification-interrupt.md) erfolgreich. Dies ist kein aktueller Codex-Betriebs-/Supportstand; Pi 4/Ubuntu 26.04.1 ARM64 wurde mit Codex 0.160.0 separat [End-to-End abgenommen](../docs/verification-pi-host-2026-10-03.md); neue Versionen erneut prüfen. Gezielter HTTP-Antwortverlust und exaktes Turn-Ende-Rennen sind automatisiert mit Testgegenstellen geprüft.
- Nur Nutzung bei offener App, keine Push-/Hintergrundzusage. Kein Terminal, Dateimanager, Git-UI, Editor, Approval-UI (die vorhandene API besitzt diese Endpunkte nicht).
- Textdarstellung ohne Markdown-Engine; Bilder als Vorschau im Composer und Typmarker in der History, noch ohne dauerhafte History-Bildvorschau. Tool-Items nur Typ/Status.
- Vollständige Legacy-History ohne Pagination; für sehr lange Unterhaltungen noch nicht optimiert.
- Vorschau plus ID statt eigenem Threadtitel. Titel/Renaming ist ein UX-Folgepunkt, kein neues Serverfeld.
- Referenztablet: HONOR YLE-W09, Android 16/API 36. Hardwaretastatur, TalkBack, sehr große Schrift und Split-Screen bleiben gesonderte Geräteprüfungen.
- ADR 0004 konkretisiert den Single-User-HTTPS-/Token-Vertrag; die übrigen ADRs bleiben unverändert.

## Lokale Vertragstests ohne Codex-Account

```sh
python3 -B -m unittest discover -s server -p 'test_*.py' -v
android/build-local.sh
```

Für den Kotlin/Python-Integrationstest in einem lokalen Terminal ein ausschließlich
flüchtiges Testtoken erzeugen und beide Prozesse mit derselben Umgebung starten:

```sh
export CODEXPAD_ACCESS_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(48))')"
python3 -B android/tools/contract_server.py &
fixture_pid=$!
trap 'kill "$fixture_pid"; unset CODEXPAD_ACCESS_TOKEN' EXIT
CODEXPAD_CONTRACT_URL=http://127.0.0.1:18765 android/build-local.sh :app:testDebugUnitTest
```

Kein `set -x` oder Environment-Dump verwenden. Die Gegenstelle bindet nur Loopback,
importiert den echten Python-Handler und ersetzt ausschließlich das interne Codex-Backend.
Ohne `CODEXPAD_CONTRACT_URL` werden die beiden Python-Vertragstests übersprungen. Keine Modellaufrufe,
keine echten Projekte und kein VPS-Zugriff. Der Vertragstest prüft Token-Header auch
für POST/SSE, History, Disconnect, Reconnect und Fortsetzen.

## Keystore auf einem Gerät prüfen

`android/build-local.sh :app:assembleDebug :app:assembleDebugAndroidTest`, dann beide
APKs auf ein lokales Testgerät installieren. Der Instrumentation-Runner nutzt eigene
Test-Preferences und einen eigenen Keystore-Alias, ohne gespeicherte Benutzerzugänge
anzufassen. Das zufällige Testtoken entsteht auf dem Gerät und verlässt es nicht.

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb install -r android/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e phase write dev.codexpad.test/dev.codexpad.KeystoreTestRunner
adb shell am force-stop dev.codexpad
adb shell am instrument -w -e phase read dev.codexpad.test/dev.codexpad.KeystoreTestRunner
```

Beide Phasen müssen `PASS` melden. Die zweite Phase prüft Entschlüsselung nach
Prozessneustart sowie URL-Manipulation und entfernt anschließend die Testdaten.
[Prüfnachweis des HTTPS-Slices](../docs/verification-https.md).

## Modellwahl, Reasoning und Kontext

Die Composer-Chips öffnen native Auswahldialoge. Modellname und Beschreibung sowie
Efforts stammen aus `GET /models`; „Katalogdefault“ ist keine Aussage über den Thread.
Die kompakte Statusanzeige verwendet ausschließlich nullable `Thread.model` und
`Thread.reasoningEffort` aus dem Serverabgleich. Fehlende Werte bleiben unbekannt.
Eine Auswahl ist lokal für die nächste Nachricht vorgemerkt und verwerfbar. Ein
Modellwechsel erhält einen kompatiblen Effort oder wählt Katalogdefault/ersten
unterstützten Wert. Ohne Auswahl enthält der POST keine Overrides. Auswahl und
bestätigter Thread-Zustand bleiben getrennt; ein erfolgreicher POST leert die
verbrauchte Vormerkung, aktualisiert aber niemals selbst die Konfigurationsanzeige.
Danach liest der bestehende Abgleich den tatsächlichen Zustand. Bei Antwortverlust
bleibt der bestehende unbestätigte Sendevorgang ohne automatische Wiederholung.
Vormerkungen leben im ViewModel (Rotation), nicht dauerhaft über Prozessverlust.

Kontextrest in Prozent stammt aus `thread/tokenUsage/updated`: `last.totalTokens`
und positives `modelContextWindow`, berechnet mit der Codex-TUI-Formel samt
12.000-Token-Baseline. Die bisherige absolute Restschätzung entfällt vollständig.
Fehlende Werte heißen „unbekannt“. Pause, Reconnect und Overflow markieren bekannte
Usage als „veraltet“; weder History noch SSE-Snapshot machen sie aktuell. Erst ein
neues Usage-Event einschließlich Resume-Replay tut das. Reroutes werden pro Turn
als Laufzeitumleitung angezeigt und verändern Modell/Effort des Threads nicht.

Das accountweite Wochenlimit stammt aus `GET /account/rate-limits` sowie den
globalen `account/rateLimits/updated`-Events. Nur ein Wochenfenster des Buckets
`codex` wird angezeigt; Rest = 100 − usedPercent. Der Client liest beim Öffnen/
Reconnect und alle 60 Sekunden erneut. Fehler beeinflussen den Chat nicht und
kennzeichnen den letzten bekannten Wert als veraltet. Ein Reset wird nicht
lokal angenommen. `account/updated` verwirft alte Kontolimits und liest neu.

Nur eindeutig laufende Turns mit gültigem gemeldetem `startedAt` zeigen ihre Dauer,
ohne Turn-ID. Die Uhr aktualisiert sich jede Sekunde während sichtbarer, verbundener
Anzeige. Idle, terminale Turns und fehlende Startzeiten zeigen keinerlei Turn-Text;
offline bleibt der letzte Stand sichtbar. History/Events übernehmen außerdem `completedAt` und `durationMs`.
Keine Rekonstruktion aus UUID oder Empfangszeit.

Die manuelle Kontextkomprimierung ist aus der Oberfläche entfernt. Automatische
Codex-Komprimierung und die bestehenden History-/Lifecycle-Abgleiche bleiben erhalten.

[Quellen, Codex-Formel und API-Grenzen](../docs/research/codex-statusline.md).

[Prüfnachweis und Grenzen dieses Slices](../docs/verification-model-context.md).

## Ergebnisdateien

Terminale Turns können Datei-Karten für PDF, PNG/JPEG/WebP und TXT/Markdown/HTML zeigen.
„Öffnen“ lädt authentifiziert in den privaten Cache und übergibt eine FileProvider-
URI mit temporärem Lesegrant an die Android-App-Auswahl. Markdown und HTML werden zum Öffnen
als Text angeboten. „Speichern“ lädt zunächst vollständig, öffnet dann Androids
Storage Access Framework und schreibt ausschließlich an das gewählte Ziel;
MIME-Type und Dateiname bleiben erhalten.
Keine zusätzlichen Storage-Berechtigungen. Details und echte Geräteabnahme:
[verification-artifacts.md](../docs/verification-artifacts.md).

## Rückfragen im laufenden Turn

Offene `item/tool/requestUserInput`-Fragen erscheinen als eigene Karten im Chat:
Freitext, Einzelauswahl und optional eigene Antwort. Sie kommen aus
`thread.pendingRequests` in History/SSE-Snapshots und bleiben nach erneutem Öffnen
sichtbar. Aufgelöste Karten verschwinden; alte IDs/Doppeltaps werden nicht erneut
weitergeleitet. Bei unbestätigtem POST wird zuerst der Zustand abgeglichen, niemals
automatisch nochmals gesendet. Der Composer kann bei `isBlocking=false` zusätzliche
Nachrichten an denselben Turn schicken. Keine Approval-UI und keine Policyänderung.

[Historischer 0.156.1-Vertrag, API und damaliger Tablet-Nachweis](../docs/verification-user-input.md),
keine aktuelle Versions-/Supportempfehlung; neuer Host separat abzunehmen.
Opt-in-Gerätetest: `:app:assembleDebugAndroidTest
-Pcodexpad.testRunner=dev.codexpad.UserInputTestRunner`. Er benötigt einen isolierten
echten Server auf Loopback-Port 18766 und die app-private `files/input-test.json`
mit `url`/temporärem `token`. Er verursacht zwei Modellturns und eine Ergebnisdatei;
keine automatische Ausführung im normalen Build. Bestehende verschlüsselte
Verbindungseinstellungen werden nach dem Test wiederhergestellt.


## Tablet-Polish

Der vorhandene Material-3-Stil nutzt eine gemeinsame ruhige Grün-/Surface-Palette,
einheitliche Rundungen und besser abgestufte Schriftgrößen. Benutzertexte, Codex,
Tool-Aktivitäten, Ergebnisdateien und Rückfragen sind visuell getrennt. Der Composer
fasst Modellwahl, Anhänge und Eingabe zusammen; „Stoppen“ bleibt in der Statuszeile
sichtbar. Navigation, Transport und das bisherige feste helle Erscheinungsbild bleiben
unverändert. [Sicht- und Bediennachweis auf dem USB-Tablet](../docs/verification-ui-polish.md).
