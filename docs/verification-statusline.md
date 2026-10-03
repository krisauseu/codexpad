# Prüfnachweis: kompakte Session-Statusanzeige

3. Oktober 2026. Vor Codeänderungen wurden die offizielle Dokumentation und die
Statusline-Implementierung von Codex 0.160.0 gelesen. [Quellen, Formel und
API-Grenzen](research/codex-statusline.md).

Die bisherige eigene Kontextanzeige und ihre absolute Restschätzung sind
entfernt. Der Composer zeigt Modell/Effort und eine kompakte Zeile, etwa
`Kontext 89 % frei · Woche 94 % frei · Turn … · 1:05`. Nur gemeldete
Nutzungsdaten und die Codex-TUI-Kontextformel werden verwendet. Es gibt keine
Zeichen-/Token- oder Modellfensterschätzung und kein Parsen gerenderten Texts.

Account-RPC und globale Notifications ergänzen die bestehende Thread-SSE-
Anbindung. Initialer Read, Reconnect und 60-Sekunden-Poll aktualisieren Limits;
ein neueres Event gewinnt gegen ältere laufende RPCs. Accountlesefehler
unterbrechen die Unterhaltung nicht. Abgelaufene Resetdaten und Offline-
Stände werden gekennzeichnet. Fehlende Wochenfenster oder Turn-Startzeiten
bleiben unbekannt. Nullable Turn-Zeitfelder werden aus History und Lifecycle
übernommen; terminale Events verhindern eine weiterlaufende Turn-Anzeige.

## Prüfungen

- Python: 39 Tests erfolgreich, darunter authentifizierter Account-RPC,
  unveränderte strukturierte Antwort, globale Verteilung und Pufferüberlauf.
- Android: 59 Tests erfolgreich ohne Skips mit lokaler Python-HTTP/SSE-Fixture
  und flüchtigem Testtoken. Enthalten sind der echte produktive HTTP-Handler,
  Account-Limit-Read, Resume-Usage-Replay und Reconnect.
- Nach den letzten Anpassungen an Rechenreihenfolge und asynchronen Account-
  Refresh erneut Debug-APK, lokale Unit-Tests und Lint erfolgreich. Dieser Lauf
  ohne Fixture überspringt die zwei expliziten HTTP/SSE-Vertragstests.
- Goldene Kontextwerte für Codex-Baseline, fehlende Fenster, Überbelegung;
  richtige Limit-Buckets, weekly in primary/secondary, Rundung, fehlende Werte,
  abgelaufener Reset, kein Verwechseln mit Monatsfenstern.
- Sessiontests: globale Events, Limitlesefehler ohne Chatverlust, Reconnect,
  verspäteter Read nach neuerem Event; bestehende Kompaktierungsprüfungen grün.
- Turntests: Server-Startzeit, laufende Dauer, fehlende Zeit, Completion und
  verspätetes Start-Event; kein Fallback auf Empfangszeit oder UUID.
- `git diff --check` erfolgreich. Kein alter `ContextUsage`-Typ oder absolute
  Restschätzung mehr im produktiven Android-Code.

## Grenzen

Nachtrag: [VPS-Deployment und echter Tablet-Lauf](verification-statusline-tablet.md)
am selben Tag erfolgreich. Die folgenden Grenzen beschreiben den vorherigen
lokalen Prüflauf.

Die Laufzeitprüfungen verwenden deterministische Backends. Kein neuer echter
Codex-Modellturn, VPS-Deployment oder Tablet-Test. Die Änderung ist lokal
gebaut und geprüft, aber noch nicht auf Server oder Tablet ausgerollt.
Die Kontextprozentanzeige bleibt semantisch die von Codex selbst berechnete
normalisierte Schätzung; das App-Server-Protokoll liefert keinen exakten
Restprozentwert. Baseline/Rechenverhalten sind an den geprüften Codex-Release
gebunden und bei einem späteren relevanten Upstreamwechsel erneut abzugleichen.
