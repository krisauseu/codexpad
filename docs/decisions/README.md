# Architekturentscheidungen

Stand: 3. Oktober 2026. Die ursprünglichen vier Entscheidungen stützen sich auf
die [Linux-Spikes](../experiments/README.md). ADR 0005 ergänzt die ausdrückliche
persönliche Trusted-Host-Produktentscheidung; ADR 0006 bereitet privaten Pi-Transport vor.

| ADR | Status | Entscheidung |
| --- | --- | --- |
| [0001](0001-app-server-interface.md) | Accepted | App Server als primäre Agent-Schnittstelle |
| [0002](0002-state-reconciliation.md) | Accepted | Reconciliation statt vollständigem Event-Replay |
| [0003](0003-terminal-lifecycle.md) | Accepted | Terminal-Lifecycle getrennt vom Agent-Lifecycle |
| [0004](0004-client-host-trust-boundary.md) | Accepted | Steuerungsclient als Host-Trust-Boundary |
| [0005](0005-personal-trusted-host.md) | Accepted für persönlichen Single-User-Host | Full Access, never, Hostadministration; Netzwerkgrenze separat |
| [0006](0006-private-host-transport.md) | Proposed | Konfigurierbares privates LAN-HTTP / HTTPS / Tailscale |

Accepted gilt für den jeweiligen Geltungsbereich. Der persönliche HTTPS-/Bearer-
VPS existiert; allgemeine Mehrnutzer-/Workspace-Isolation und Terminalpersistenz
sind weiterhin offen. Der Pi ist noch nicht praktisch abgenommen; LAN-Androidänderung
ist Proposed. ADR 0005 ist eine Betreiberentscheidung, kein Isolationsnachweis.

Weitere ADRs fortlaufend nummerieren. Datum, Status, Kontext, Evidenz, Entscheidung,
Alternativen, Folgen und Anlass zur erneuten Prüfung festhalten. Ersetzte
Entscheidungsanteile ausdrücklich kennzeichnen und historischen Text erhalten.
Der aktuelle Auftrag ergänzt Betriebsdokumentation, ohne den bestehenden VPS
oder Produktcode umzubauen.
