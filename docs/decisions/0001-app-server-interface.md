# ADR 0001: Codex App Server als primäre Agent-Schnittstelle

Datum: 18. September 2026. Status: Accepted. Geltung: derzeitiger experimentell geprüfter Funktionsumfang.

## Kontext und Evidenz

[Spike A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md) steuerte das unveränderte Codex 0.154.0 mit unabhängigem Python-Client über strukturierte RPCs. Threads, Turns, Datei-Approvals und Historienabrufe funktionierten. Experimental-Opt-in war aktiv; normale agentische Shell-Arbeit war dort durch einen Sandboxstartfehler eingeschränkt.

## Entscheidung

CodexPad verwendet den App Server als primäre Agent-Schnittstelle. Für den geprüften Umfang sind weder menschenlesbares CLI-Textparsing noch ein Codex-Fork erforderlich. Der Client verarbeitet Requests, Responses, Events und Serverrequests strukturiert.

Alternativen waren TUI-Parsing, direkte Core-Einbettung und Batch-Anbindung über `codex exec --json`. Letztere bleibt eine mögliche Job-Schnittstelle, deckt aber den hier untersuchten interaktiven Clientablauf nicht als bevorzugter Weg ab.

## Folgen und erneute Prüfung

Codex bleibt unverändert. Der Adapter braucht eine explizite Versions-/Capability-Prüfung. API-Stabilität, experimenteller Transport, minimale RPC-Menge und Remoteauth bleiben offen. Diese Entscheidung wählt weder Transport noch Android-Stack oder Supportversion. Bei Codex-Upgrades oder einer konkret fehlenden Capability erneut prüfen.
