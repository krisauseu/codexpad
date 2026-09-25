# ADR 0003: Terminal-Lifecycle getrennt vom Agent-Lifecycle

Datum: 18. September 2026. Status: Accepted für die Trennung; Ersatztechnologie offen.

## Kontext und Evidenz

[Spike B](../experiments/2026-09-command-exec-persistence/pty-persistence.md) startete Pipe und PTY über `command/exec` erfolgreich mit regulärer Sandbox auf Ubuntu 24.04.5 und Codex 0.154.0. Beim Verlust der Ursprungsverbindung endeten beide OS-Prozesse, während der App Server aktiv blieb. `processId` war auf der neuen Verbindung nicht verwendbar; kein Reattach. Der Bericht ist erhalten, seine Rohdaten wurden nicht mitgeliefert.

[Spike A](../experiments/2026-09-linux-protocol-spike/protocol-spike.md) zeigte dagegen einen weiterlaufenden Modell-Turn nach Clientverlust. Dort endeten außerdem getestete `process/spawn`-Prozesse beim Disconnect. Das ist keine allgemeine Aussage über alle Agent-Hintergrundprozesse.

## Entscheidung

Agent-/Thread-Lifecycle und Terminal-Lifecycle sind getrennte Verantwortlichkeiten. `command/exec` wird nicht als Persistenzschicht für Terminals über Client-Disconnects verwendet. Ein persistentes Terminal ist bei entsprechendem Produktbedarf eine separate zukünftige Capability.

## Folgen und erneute Prüfung

Thread-Resume darf keinen Terminal-Reattach vortäuschen. Ob v0.1 ein persistentes Terminal braucht, ist offen. tmux, eigener PTY-Service und andere serverseitige Sessionmodelle sind spätere Untersuchungsoptionen; keine davon ist ausgewählt oder implementiert. Bei verbindlichem Terminalbedarf oder geändertem Upstream-Lifecycle erneut prüfen. Die Technologieentscheidung braucht eine eigene Evidenz und gegebenenfalls einen vorgeschlagenen ADR.
