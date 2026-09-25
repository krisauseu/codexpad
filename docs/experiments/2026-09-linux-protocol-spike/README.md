# Linux Protocol Spike, 18. September 2026

[Ergebnisbericht](protocol-spike.md), Codex 0.154.0 auf Ubuntu 24.04.4. Historischer Status PARTIAL, insbesondere wegen des Sandboxstartfehlers. [Spike B](../2026-09-command-exec-persistence/pty-persistence.md) beantwortet inzwischen die command/exec-Persistenzfrage. Agent-Hintergrundterminals bleiben offen.

## Erhaltene Evidenz

- [Originaler Transportclient](client/probe.py) und sieben kleine Skripte für Basislauf, Approvals, Resume, Serververlust, Hostprozesse, Git und Auswertung in `client/`. Unveränderter historischer Versuchscode, insgesamt 456 Zeilen.
- [verification.json](verification.json): ursprüngliche 16 bestandene Prüfungen samt damaligen Eventzählungen.
- [evidence.json](evidence.json): aus den vorhandenen Logs abgeleitete kompakte Evidenz. Enthält Approvalrequests zum Identitätsvergleich, Text vor/nach Disconnect und hydratisierten Endtext, Turn-Snapshots, Lifecycle-/Prozessbeobachtungen und Dateiinhalte bzw. deren Abwesenheit. Pro Ursprungslog bleiben Hash, Zeilenzahl und eingehende Methodenzählungen erhalten.
- [environment.json](environment.json) und [test-config.toml](test-config.toml): konkrete Laufzeit- und Sandboxkonfiguration ohne Credentials.
- [provenance.json](provenance.json): Herkunft und SHA-256 der originalen Berichte, Clients und Verifikation. Bei Berichten wurden nur Archivhinweise und interne Markdown-Links angepasst.

Keine vollständigen Rohlogs, Rollouts oder SQLite-Datenbanken archiviert. Die kompakte Evidenz reicht für die beschriebenen Vergleiche; sie ersetzt kein vollständiges Eventjournal. Aussagen über fehlende Ereignisse beruhen auf den bei Konsolidierung ermittelten Methodenzählungen. Datei-Abwesenheit ist ein erfasster Versuchszustand, kein neuer Live-Test.

## Offline prüfen

Vom Projektwurzelverzeichnis aus:

```sh
python3 docs/experiments/2026-09-linux-protocol-spike/verify-evidence.py
```

Der neue Offline-Prüfer liest ausschließlich Archivdaten und schreibt nichts. Die 16 Prüfungen wurden bei Konsolidierung auch mit der ursprünglichen Auswertungslogik gegen die noch vorhandenen Rohlogs und Workspace-Dateien geprüft.

Das historische `client/analyze.py` benötigt dagegen das frühere vollständige Verzeichnislayout. Es ist als ursprüngliche Auswertungslogik erhalten, nicht als direkt ausführbarer Archivprüfer.

## Schema

Acht unveränderte TypeScript-Schemaausschnitte dokumentieren `command/exec`, Write/Resize, `process/spawn`, FS-Schreiben und die drei Historienabfragen. Ihre Importabhängigkeiten sind nicht vollständig archiviert; sie sind Leseevidenz, kein kompilierbares SDK. [Schema-Hashes](schema-sha256.json) enthalten zusätzlich Hashes der beiden vollständigen JSON-Schemabündel. Die TS-Hashes wurden gegen das ursprüngliche Manifest geprüft. Alle Schemas stammen aus Spike A, nicht aus dem zweiten VPS.

## Grenzen einer Wiederholung

Die Skripte sind auf historische Linux-Pfade, Socket und Codex-Binary zugeschnitten. `probe.py` liest bei explizitem Auth-Login die damalige Host-Authdatei; im Archiv sind nur dieser Code und redigierte Befunde, keine Zugangsdaten. Nicht auf dem Dokumentationsverzeichnis ausführen. Eine spätere Wiederholung braucht einen frischen isolierten Linux-Workspace, eigene Runtimeverzeichnisse, geprüftes Binary und ausdrücklich vorbereitete Authentifizierung. Ablauf ursprünglich: `probe.py`, `suite.py`, `processes.py`, `live_resume.py`, `pending_restart.py`, `direct_git.py`, `evidence.py`, `analyze.py`. Nicht idempotent; keine Produktionsbibliothek.

Der Bericht nennt historische `logs/`-, `results/`- und `codex-home/`-Pfade. Diese sind bewusst nicht als vollständiges Laufzeitarchiv erhalten. Maßgeblicher aktueller Architekturstand: [Codex-Research](../../research/codex-integration.md) und [ADRs](../../decisions/README.md).
