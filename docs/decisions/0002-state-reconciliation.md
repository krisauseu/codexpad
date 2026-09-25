# ADR 0002: Reconciliation statt Event-Replay voraussetzen

Datum: 18. September 2026. Status: Accepted.

## Kontext und Evidenz

[Spike A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md) zeigte eine Lücke zwischen Textdelta 1 und 8 nach Reconnect. Ein offline abgeschlossener Turn lieferte kein nachträgliches Endevent. Vollständiger Endtext und Turnstatus waren über die Historie abrufbar. Offene Datei-Approvals wurden beim Clientverlust erneut zugestellt, beim getesteten Serververlust dagegen nicht.

## Entscheidung

Live-Events dienen der unmittelbaren Darstellung. Nach Reconnect muss der Client autoritativen Server-State und Historie laden und Snapshot sowie neue Live-Events zusammenführen. Abgeschlossener vollständiger Inhalt ersetzt eine lückenhafte lokale Deltaverkettung. Wir setzen kein vollständiges Eventjournal voraus.

Die Alternative, lokale Deltas nur durch Replay zu ergänzen, widerspricht dem beobachteten Verhalten. Thread, Turn, Approval und Verbindung erhalten getrennte Zustände. Serververlust darf nicht als gewöhnlicher Client-Reconnect behandelt werden.

## Folgen und erneute Prüfung

UI-Reconciliation muss mit Events während des Ladens, wechselnden Legacy-Item-IDs und unbekanntem Zwischenstand umgehen. Mutierende Requests werden bei verlorener Antwort nicht blind wiederholt. Konkreter Mergealgorithmus, Idempotenz und Approval-Rennen bleiben offen; dieser ADR behauptet dafür keine Lösung. Bei geändertem Historien-/Replayvertrag und bei den ausstehenden Disconnect-Rennentests erneut prüfen.
