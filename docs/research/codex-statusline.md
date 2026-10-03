# Statusline-Datenquellen

Recherche vor der Implementierung, 3. Oktober 2026. Lokales Codex: 0.160.0.
Quellabgleich mit dem Release `rust-v0.160.0`; kein Modellaufruf und kein
VPS-/Gerätelauf. Die alte absolute Restschätzung wird vollständig entfernt.

| Anzeige | Strukturierte Quelle | Grenzen |
| --- | --- | --- |
| Wochenlimit verbleibend | `account/rateLimits/read`, `account/rateLimits/updated`; Bucket `codex`, Fenster `primary` oder `secondary`, `usedPercent`, `windowDurationMins`, `resetsAt` | Accountweit, nicht pro Session. Mehrere Buckets nicht vermischen; API-Key-only kann keine ChatGPT-Limits liefern. |
| Kontext verbleibend | `thread/tokenUsage/updated`: `tokenUsage.last.totalTokens`, `modelContextWindow` | Kein fertiges Restprozentfeld und kein kontinuierlicher Tokenzähler. Die Codex-TUI berechnet selbst eine normalisierte Schätzung. Kumuliertes `total` ist nicht die Kontextbelegung. |
| Modell / Reasoning | `Thread.model`, `Thread.reasoningEffort` in Read/Snapshot; effektive Werte zusätzlich in Start/Resume | Thread-Konfiguration, keine vollständige Ausführungstelemetrie. `model/rerouted` bleibt turnbezogen. Katalogdefault ersetzt keine unbekannte Konfiguration. |
| Laufender Turn / Dauer | `turn/started`, `turn/completed`, Thread-History: Turn-ID, Status, `startedAt`, `completedAt`, `durationMs` | Zeitfelder nullable. Für laufende Dauer aktuelle Uhrzeit minus gemeldeter Start; ohne Start keine erfundene Zeit aus Empfangszeit oder UUID. Disconnect kennzeichnet letzten Stand. |

## Identische Kontextformel, keine eigene Ersatzschätzung

Codex `TokenUsage::percent_of_context_window_remaining` verwendet `B = 12000`:

```
W <= B: 0
sonst: round(clamp(100 * max(0, (W-B) - max(0, last.totalTokens-B)) / (W-B), 0, 100))
```

Diese Baseline ist eine Konstante der Codex-TUI, kein RPC-Feld. Die Anzeige
entspricht somit der TUI-Berechnung für dieselben gemeldeten Eingaben, nicht
einer serverseitig zugesicherten exakten Restkapazität. CodexPad übernimmt
diese Formel; die bisherige absolute Rechnung `window - used` entfällt.

Bewusste Grenze: Die TUI nimmt ohne Fenster/Usage teils 100 % an und kann auf
`config.model_context_window` zurückfallen. CodexPad zeigt bei fehlenden
Messwerten „unbekannt“. Kein geschätztes Modellfenster, keine Text-/Zeichen-
Tokenzählung, kein Statusline-Parsing. Bei Kompaktierung bleiben alte Werte
veraltet, bis ein passender neuer Usage-Stand vorliegt.

## Wochenfenster

Die TUI bevorzugt im Bucket `codex` das als weekly klassifizierte Fenster
(primary vor secondary); sie erkennt die Dauer mit einer Toleranz von
±5 % um 10080 Minuten. Sie fällt sonst auf secondary zurück, bezeichnet es
aber gemäß tatsächlicher Dauer etwa als monthly oder secondary usage.
CodexPad zeigt als Wochenlimit ausschließlich ein bestätigtes Wochenfenster;
ein unbekanntes/monatliches secondary wird nicht als Woche ausgegeben.
Rest = clamp(100 - usedPercent, 0, 100), gerundet wie die TUI. Der moderne
`rateLimitsByLimitId.codex` hat Vorrang; Legacy `rateLimits` nur ohne moderne
Map und nur für `codex` bzw. fehlende alte Bucket-ID. Fehlend/null ≠ 0 %.
Ein abgelaufenes Resetdatum wird als veraltet angezeigt, nicht lokal auf
100 % zurückgesetzt.

## Quellen

Laufzeitnachtrag vom selben Tag: Der VPS mit Codex **0.156.1** liefert beim
`turn/start` zunächst `startedAt: null`; der laufende History-Abgleich liefert
anschließend die echte Startzeit. Das Tablet zeigte damit `0:15` während des
laufenden Turns. Auch `completedAt` und `durationMs` wurden nach Abschluss
geliefert. Ein Codex-Upgrade war für die geprüften Werte nicht erforderlich.
Das reale Account-RPC lieferte das Wochenfenster in **primary** (10080 Minuten),
mit `secondary: null`; der Client wählte es korrekt. [Prüfnachweis](../verification-statusline-tablet.md).

- [Offizielle App-Server-Dokumentation](https://learn.chatgpt.com/docs/app-server): Token-Events, Account-RPCs, Limitschema, Turn-Lifecycle.
- [Offizielle `/statusline`-Dokumentation](https://learn.chatgpt.com/docs/developer-commands?surface=cli): Auswahl der Footer-Felder; kein Statusline-RPC.
- [TUI-Kontext- und Limitberechnung](https://github.com/openai/codex/blob/rust-v0.160.0/codex-rs/tui/src/chatwidget/status_controls.rs#L405).
- [Baseline und Kontextformel](https://github.com/openai/codex/blob/rust-v0.160.0/codex-rs/protocol/src/protocol.rs#L2419).
- [Statusline-Feldquellen und Wochenfensterauswahl](https://github.com/openai/codex/blob/rust-v0.160.0/codex-rs/tui/src/chatwidget/status_surfaces.rs#L768).
- [Fensterklassifikation](https://github.com/openai/codex/blob/rust-v0.160.0/codex-rs/tui/src/chatwidget/rate_limits.rs#L109).
- [Turn-Zeitfelder](https://github.com/openai/codex/blob/rust-v0.160.0/codex-rs/app-server-protocol/schema/typescript/v2/Turn.ts).
- [Token-Usage-Schema](https://github.com/openai/codex/blob/rust-v0.160.0/codex-rs/app-server-protocol/schema/typescript/v2/ThreadTokenUsage.ts).
