# Archivierte Experimente

Neuer Implementierungsspike: [Lokaler Server-Vertical-Slice (September 2026)](2026-09-server-vertical-slice/README.md) mit Codex 0.156.1, SSE und Reconnect-Nachweis.

Konsolidiert am 18. September 2026. Beide Experimente verwendeten Codex/App Server 0.154.0; keine Wiederholung der Live-Versuche auf diesem Mac.

| Experiment | Ergebnis | Erhaltene Belege |
| --- | --- | --- |
| [Linux Protocol Spike](2026-09-linux-protocol-spike/README.md) | Strukturierter Client, Datei-Approvals und Recovery bestätigt; kein vollständiger Replay; Shell-Sandbox auf erstem Host blockiert | Bericht, acht kleine originale Python-Skripte, ursprüngliche und kompakte maschinelle Evidenz, Offline-Prüfer, Umgebungsdaten, acht Schemaausschnitte und Hashes |
| [command/exec-Persistenz](2026-09-command-exec-persistence/README.md) | Reguläre Sandbox erfolgreich; Pipe und PTY bei Clientverlust beendet, kein Reattach | Vollständiger Bericht; erwähnte Rohdaten und Testskripte wurden nicht mitgeliefert |

Historische Berichte behalten ihre damaligen offenen Fragen. Spike B schließt daraus die command/exec-Frage, nicht die Frage nach Agent-Hintergrundterminals. Der aktuelle Stand steht in [Research](../research/codex-integration.md), [offenen Fragen](../product/open-questions.md) und [ADRs](../decisions/README.md).

## Bestandsaufnahme und Auswahl

Vor Änderungen wurden README, VISION, alle drei Research-Dokumente, offene Fragen, Entscheidungsablage und beide vollständigen Berichte gelesen. Inventar: `codex-home/` mit 6.823 regulären Dateien, 95.071.113 Nutzdatenbytes, etwa 108 MB belegtem Dateisystemplatz; `codexpad-spike/` mit 38 regulären Dateien, 81.799 Nutzdatenbytes, etwa 188 KB belegt. Es gab in diesen Kopien keine Unix-Sockets, Symlinks oder erkannten ELF-/Mach-O-Binaries und keine separate Package-/Release-Installation. Die Binary-Pfade in den Berichten bezeichnen die früheren Linux-Hosts.

| Klassifikation | Bestand | Behandlung |
| --- | --- | --- |
| KEEP | Bestehende Projekt-/Research-Dokumentation | Beibehalten und gezielt aktualisiert |
| KEEP AS EVIDENCE | Beide Berichte, kleine Clients, verification.json, ausgewählte Schemaausschnitte | Unter den beiden Experimentverzeichnissen gesichert; Herkunft und Hashes dokumentiert |
| SUMMARIZE THEN DELETE | Protokoll-/Beobachtungslogs, Umgebung, Testdateien, große Schemabündel und ursprüngliches Hashmanifest | Kompakte Evidenz, Konfiguration, Zählungen und ausgewählte Hashes erhalten; vollständige Kopien entfernt |
| DELETE | Caches, temporärer Plugin-Checkout samt Git-Metadaten, Runtime-State, Locks, kopierte Skills, Testworkspace samt bereits vorhandener .git, __pycache__, .DS_Store | Nach Sicherung der Belege entfernt |
| SENSITIVE – DELETE | `codex-home/logs_2.sqlite` und `codex-home/logs_2.sqlite-wal` mit JWT-Funden; übrige Session-/State-/Shell-Snapshot-Daten vorsorglich als private Laufzeitdaten | Keine Übernahme; mit vollständigem kopiertem Codex-Home entfernt |

Die beiden kopierten Wurzelverzeichnisse wurden nach erfolgreicher Evidenzprüfung vollständig entfernt. Keine ganze Codex-Installation oder vollständiges CODEX_HOME bleibt im Projekt. Die früher mitkopierten Test-/Plugin-Git-Verzeichnisse wurden mit entfernt; es wurde kein Git-Repository angelegt.

## Secret- und Credential-Prüfung

Die Prüfung las reguläre Dateien einschließlich SQLite/WAL binär und suchte nach Auth-/Key-/Credential-Dateinamen, JWTs, API-Key-/Private-Key-Mustern und zugewiesenen Credentialwerten. Decodierbare JWTs wurden in den beiden oben genannten Runtime-Logdateien gefunden und als sensitiv behandelt. Ihre Werte, Claims oder Accountdaten wurden nicht ausgegeben oder archiviert. Keine `auth.json` und kein privater SSH-Schlüssel waren in den Kopien vorhanden. Weitere Patternkandidaten lagen im verworfenen Plugin-Checkout, unter anderem Beispiel-/Testcode; ihre Echtheit war für die vollständige Entfernung nicht erforderlich.

Der historische Security-PASS im Bericht A bezog sich auf dessen damaligen Test und bekannte Credentialwerte. Er ist kein Beleg für Secretfreiheit des später kopierten Codex-Homes. Der jetzige Fund wird deshalb getrennt dokumentiert.

Nach der Bereinigung wurden sämtliche verbleibenden Projektdateien erneut geprüft. Keine offensichtlichen Credentialwerte oder Credentialdateien gefunden. Auth-Feldnamen und ein historischer Auth-Dateipfad im Testclient sind Code-/Protokollreferenzen, keine Secrets. Die Musterprüfung ist keine Garantie gegen beliebig kodierte oder unbekannte Secretformate. Die Bereinigung betrifft ausschließlich dieses Projekt, nicht ursprüngliche Hosts oder Backups.
