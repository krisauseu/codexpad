# command/exec-Persistenz, 18. September 2026

Erhalten ist [pty-persistence.md](pty-persistence.md), der vollständige Bericht des separaten Ubuntu-24.04.5-VPS mit Codex 0.154.0.

Pipe und PTY starteten bei funktionierender regulärer Sandbox. Beide OS-Prozesse waren nach Clientverlust beendet, während derselbe App Server weiterlief. Das alte `processId` war auf der neuen Verbindung unbrauchbar; kein Reattach.

Die im Bericht erwähnten Pipe-/PTY-Logs, maschinellen PID-Auswertungen, Schemadateien und Testskripte waren in den kopierten Verzeichnissen nicht vorhanden. Dieser Befund ist deshalb durch den Bericht, nicht durch lokal erneut ausführbare Rohdatenprüfungen belegt. Keine Resultate oder Testskripte nachträglich als Originale rekonstruiert. Der Originalhash steht in [Spike-A-Provenienz](../2026-09-linux-protocol-spike/provenance.json).

Die Aussage betrifft `command/exec` bei Clientverlust. Sie belegt weder Agent-Hintergrundterminals noch alle Server-Shutdownvarianten. Eine konkrete Terminal-Persistenzlösung bleibt offen, siehe [ADR 0003](../../decisions/0003-terminal-lifecycle.md).
