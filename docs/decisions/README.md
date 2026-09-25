# Architekturentscheidungen

Stand: 18. September 2026. Vier begrenzte Entscheidungen stützen sich auf die [Linux-Spikes](../experiments/README.md). Es gibt noch keine vollständige Implementierungsarchitektur.

| ADR | Status | Entscheidung |
| --- | --- | --- |
| [0001](0001-app-server-interface.md) | Accepted | App Server als primäre Agent-Schnittstelle |
| [0002](0002-state-reconciliation.md) | Accepted | Reconciliation statt vollständigem Event-Replay |
| [0003](0003-terminal-lifecycle.md) | Accepted | Terminal-Lifecycle getrennt vom Agent-Lifecycle |
| [0004](0004-client-host-trust-boundary.md) | Accepted | Steuerungsclient als Host-Trust-Boundary |

Accepted gilt nur für die jeweilige begrenzte Entscheidung. Remoteauth, Transport, konkrete Workspace-Isolation und Terminal-Persistenztechnologie sind weiterhin offen. Dafür wird kein Accepted-Status vorweggenommen. Neue Lösungsentwürfe bleiben Proposed, bis die nötigen Belege vorliegen.

Weitere ADRs fortlaufend nummerieren. Datum, Status, Kontext, Evidenz, Entscheidung, Alternativen, Folgen und Anlass zur erneuten Prüfung festhalten. Ersetzte Entscheidungen erhalten. Die aktuelle Phase bleibt Research und Dokumentation, ohne Android-Anwendung, eigenen Serverdienst oder v0.1-PRD.
