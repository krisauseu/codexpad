# Manuelle Thread-Kompaktierung – lokale Prüfung

Datum: 2026-09-25. Lokal in `/Users/kf/codexpad`; keine Commits, kein Push,
kein Deployment und kein neuer Tablet-/VPS-Lauf. Die bestehende Prüfung gegen
Codex 0.156.1 wird dadurch nicht als Compact-Gerätenachweis erweitert.

## Implementierung

- `POST /threads/{threadId}/compact`, Body exakt `{}`. Bestehende Bearer-Auth
  vor Routing; autoritativer `thread/read` mit History und Workspace-Prüfung,
  kein Fallback auf den Fresh-Cache. Aktive/nicht terminale Turns führen zu 409.
- Einzige neue Mutation: reguläres `thread/compact/start { threadId }`.
  HTTP 202 mit `{}` bestätigt nur Annahme, keinen erfolgreichen Abschluss.
  Kein Experimental-Opt-in, kein RPC-/Config-Passthrough.
- Native Aktion im Menü **Kontext** oben im Thread. Kurze Erklärung zur
  Zusammenfassung des Agentkontexts. Während normaler Turns, offline,
  bei unbestätigtem Senden/Interrupt oder ungeklärtem Compact deaktiviert.
- Vor HTTP wird die Menge bisheriger Turn-IDs im bestehenden SavedStateHandle
  vorgemerkt. Ein atomarer Session-Zustandswechsel verhindert Doppelauslösung.
  Währenddessen sind neue Nachrichten gesperrt. Modell-/Effort-Auswahl und
  deren RPC-Vertrag bleiben unverändert.
- `contextCompaction` erhält einen expliziten `isCompaction`-Marker und eine
  kurze deutsche Darstellung. Der bestehende Timeline-Merge verarbeitet weiter
  `item/started` und `item/completed`; keine Gesprächsnachricht wird gelöscht
  oder lokal gekürzt. Kein eigener Tool-/Diff-Renderer.

## Zustandsabgleich

Unmittelbar nach Auslösung: „Kontext wird komprimiert …“. Ein neuer strukturierter
Kompaktierungs-Turn aus SSE oder History identifiziert den Vorgang. Historische
Kompaktierungen vor der Auslösung und andere neue normale Turns bestätigen ihn
nicht. Sobald die Turn-ID bekannt ist, wird kein anderer Turn als Ersatz gewählt.

Item-Start/-Ende lösen History-Abgleich aus; Item-Ende allein ist kein Erfolg.
Die vorhandenen Abgleiche bei `turn/completed`, Statuswechsel, Fehler und das
Polling bleiben maßgeblich. Der terminale History-/SSE-Snapshot des zugeordneten
Turns bestätigt `completed`, `failed` oder `interrupted`. Ein später HTTP-Fehler
kann einen bestätigten Endzustand nicht zurücknehmen.

Bei Antwortverlust, Pause, Streamverlust oder fehlender zuordenbarer Evidenz steht
„Ausgang unbekannt“. **Zustand abgleichen** liest ausschließlich; es startet keine
weitere Kompaktierung. Polling läuft auch bei ungeklärtem Compact im kurzen
Intervall (nach dem bereits laufenden Poll-Intervall). Reconnect verwendet weiterhin
Snapshot → History → SSE-Snapshot. Der gespeicherte Vorgang wird dabei abgeglichen,
nie erneut gepostet. Auch ein vollständig während der Unterbrechung abgeschlossener
Compact wird anhand neuer strukturierter History erkannt.

## Kontextanzeige

- Vorher: unverändert `last.totalTokens` und `modelContextWindow` aus Usage-Events.
- Ab Start: bisherige Werte bleiben erhalten und heißen „veraltet“; fehlende Werte
  bleiben „unbekannt“. Keine Nullsetzung oder geschätzte Compact-Ersparnis.
- Während Compact: echte neue Usage-Werte dürfen übernommen werden, bleiben aber
  bis zur bestätigten Beendigung als veraltet markiert.
- Nach Erfolg: ohne neuen Usage-Stand bleibt die Anzeige veraltet/unbekannt.
  Ein Usage-Event für den Compact-Turn, das kurz vor dem terminalen History-Abgleich
  eintrifft, wird mit diesem Abgleich aktuell. Andernfalls wartet die Anzeige auf
  das nächste Usage-Event für den Compact-Turn oder einen späteren Turn.
- Resume-Replay mit einer Turn-ID aus dem Zustand vor Compact wird nicht als
  aktueller Post-Compact-Stand ausgegeben. Pause/Reconnect verwirft die lokale
  Usage-Turn-Zuordnung; History allein frischt dann keine Usage auf.

## Ausgeführte Prüfungen

| Prüfung | Ergebnis / Nachweis |
| --- | --- |
| Schmale Route, leeres Body, exaktes reguläres RPC | Python HTTP-Test mit echtem Handler bestanden |
| Auth vor Body/Backend, Workspace-Grenze, aktiver Turn, kein Cache-Fallback | Python-Tests bestanden |
| Ruhender Thread → manuelles Compact → bestätigter Abschluss | Kotlin-Session-Test und Kotlin/Python-Vertragstest bestanden |
| Zweite Auslösung bei laufendem/unklarem Compact | Kein zweiter POST; Session- und HTTP-Transporttests bestanden |
| Strukturiertes Item, Item-Ende ohne vorzeitigen Erfolg, Turn-Ende | `CompactionTest` bestanden |
| Sichtbare alte History erhalten | Exakter Vergleich alter Turns in Session und HTTP-Vertragstest bestanden |
| Usage nicht lokal erfunden, alte Replay-Werte veraltet | `CompactionTest` bestanden |
| Neue Usage nach Compact und Usage unmittelbar vor Turn-Ende | Werte/Rest/Freshness aus echten Fixture-Events geprüft |
| Verlorene HTTP-Antwort | OkHttp sendet nur einmal; Session bleibt ungeklärt und gesperrt |
| Pause, Saved-State-Restoration, Reconnect während/nach Compact | Auflösung durch Serverhistory ohne erneuten POST bestanden |
| Verlorenes Endevent | Periodischer History-Abgleich heilt den Zustand |
| Fehler/Unterbrechung, spätes HTTP-Fehler-Rennen | Terminale History bleibt maßgeblich; alle drei Endzustände geprüft |
| Alter Compact und fremder neuer normaler Turn | Bestätigen den aktuellen Versuch nicht |
| Normaler Turn nach Compact | Echter Kotlin-HTTP-Client gegen Python-Handler; danach Interrupt erfolgreich |
| Interrupt, Modell-/Effort-Logik | Bestehende Session-, API-, Modell- und Integrationstests weiterhin bestanden |

Ausgeführt:

```sh
python3 -B -m unittest discover -s server -p 'test_*.py' -q
android/build-local.sh
```

Zusätzlich vollständiger Android-Build mit aktiver `android/tools/contract_server.py`
auf einem temporären Loopback-Port und `CODEXPAD_CONTRACT_URL`; beide Prozesse mit
demselben flüchtig erzeugten `CODEXPAD_ACCESS_TOKEN`. Die Fixture importiert den
echten Python-Handler und ersetzt ausschließlich das Codex-Backend. Kein echter
Modellaufruf. Reproduktion entsprechend dem Abschnitt „Lokale Vertragstests ohne
Codex-Account“ in `android/README.md`.

Ergebnisse:

- **13 Python-Tests bestanden.**
- **34 Kotlin-Tests bestanden, 0 übersprungen**, einschließlich Python-Vertragstest
  und 7 gezielter Compact-Tests.
- `:app:assembleDebug`, `:app:testDebugUnitTest`, `:app:lintDebug`: **BUILD SUCCESSFUL**.
- Lint: **0 Fehler**, 7 Hinweise in unveränderten Dependency-/Settings-Dateien.
- `git diff --check`: bestanden.

Die Sandbox blockiert Loopback-Sockets für Gradle und HTTP-Fixtures. Die erfolgreichen
Läufe erfolgten mit freigegebener lokaler Ausführung außerhalb dieser Socket-Sandbox.

## Geänderte Dateien

Produktionscode:

- `server/codexpad_server.py`
- `android/app/src/main/java/dev/codexpad/data/Compaction.kt` (neu)
- `android/app/src/main/java/dev/codexpad/data/ThreadSession.kt`
- `android/app/src/main/java/dev/codexpad/model/Models.kt`
- `android/app/src/main/java/dev/codexpad/network/CodexPadApi.kt`
- `android/app/src/main/java/dev/codexpad/ui/PadViewModel.kt`
- `android/app/src/main/java/dev/codexpad/ui/CodexPadApp.kt`

Prüfung/Dokumentation:

- `server/test_auth.py`
- `android/tools/contract_server.py`
- `android/app/src/test/java/dev/codexpad/CompactionTest.kt` (neu)
- `android/app/src/test/java/dev/codexpad/ApiTest.kt`
- `android/app/src/test/java/dev/codexpad/ThreadSessionTest.kt`
- `android/app/src/test/java/dev/codexpad/PythonContractTest.kt`
- `docs/verification-compact.md` (neu)

## Offene Grenzen / Git

- Reale manuelle Kompaktierung gegen Codex 0.156.1 und native Darstellung auf dem
  Tablet sind in diesem lokalen Slice noch nicht neu geprüft. Die Lifecycle-Fixtures
  bilden den im vorhandenen Capability-Audit beschriebenen Vertrag ab.
- Das RPC liefert keine Vorgangs-ID. Ohne neuen strukturierten Compact-Turn und
  ohne bekannte Ziel-Turn-ID bleibt ein verlorener/abgelehnter Aufruf konservativ
  unbekannt und gesperrt; ein ruhender Snapshot beweist keine Nichtausführung.
  Es gibt bewusst keinen „blind erneut versuchen“-Button. Mehrere externe neue
  Kompaktierungen können nicht sicher einer lokalen Anfrage zugeordnet werden.
- SavedStateHandle deckt Rotation und reguläre Android-Zustandswiederherstellung
  ab, ist aber kein dauerhaftes Journal nach Force-stop/gelöschten App-Daten.
- Kein serverseitiger Idempotenzschlüssel oder globaler Mutex gegen andere Clients;
  der Busy-Check ist eine Momentaufnahme. App Server bleibt für Rennen zuständig.
- Keine ADR-, Modell-/Effort-, Account-/Rate-Limit- oder Deployment-Änderungen.
- 11 getrackte Dateien geändert, 3 neue Slice-Dateien ungetrackt. Die bereits vor
  Beginn ungetrackte `docs/verification-model-context-tablet.md` bleibt unberührt.
  Nichts gestaged, committet oder gepusht.
