# Recherche und Wiedereinstieg

Aktuelle Ergänzung: [Statusline-Datenquellen und Grenzen](codex-statusline.md)
(3. Oktober 2026, Codex 0.160.0).

Stand: 18. September 2026, nach Konsolidierung zweier Linux-Spikes und begrenztem Remote-Trust-Research.

## Lesereihenfolge

1. [Vision](../../VISION.md) für Produktabsicht und Grenzen.
2. [Experimente](../experiments/README.md) für Messstand, Belege und Archivgrenzen.
3. [Codex](codex-integration.md) für Quellrecherche, experimentelle Befunde und Lifecycle-Matrix.
4. [Remote-Vertrauensmodell](remote-trust-model.md), [Capability-Matrix/Hostrechte](host-capabilities.md) und [Research-Evidenz](remote-trust-evidence.md) für den aktuellen minimalen Workflow; [Remote-Workspaces](remote-workspaces.md) für die breitere ursprüngliche Exploration.
5. [ADRs](../decisions/README.md) und [offene Fragen](../product/open-questions.md) für Entscheidungen und verbleibende Voraussetzungen.
6. [Android](android-platform.md) für weiterhin ungeprüfte Plattform- und Komponentenkandidaten.

Die ursprüngliche Quellrecherche ist auf einen Commit fixiert. Die späteren Experimente verwendeten Codex 0.154.0 auf zwei Linux-Hosts, jeweils lokal über Unix-Socket. Direkter strukturierter Zugriff, Approval-Accept/Decline, Thread-Recovery und unvollständiger Event-Replay sind belegt. Die zweite Prüfung bestätigt das Ende von command/exec-Pipe und -PTY bei Clientverlust trotz lebendem Server.

Kein Android-Gerätetest, Remoteauth-/WSS-Nachweis oder Lasttest liegt vor. Die Scripts sind historische Wegwerf-Testclients, keine Implementierungsbasis. Für Spike B ist nur der Bericht vorhanden. Alle Archive enthalten ausgewählte Forschungsbelege, keine vollständige Runtime oder Credentials.

Der anschließende Research las den gesamten Dokumentationsbestand, prüfte das lokale Binary 0.155.0 samt regulärem/experimentellem Schema und aktuelle Primärquellen. Die 16 archivierten Evidenzchecks bestanden erneut. Keine Laufzeitprüfung von 0.155.0: insbesondere die widersprüchlichen Angaben zur Pagination und zu eingeschränktem Lesezugriff bleiben offen. Ein Methodenfilter allein genügt nicht; Parameter, Approvals, Ausgabefilter und indirekte Agentzugriffe müssen begrenzt werden. Kein neuer ADR wurde angelegt.

## Genau nächster Schritt

Einen separat beauftragten lokalen [Capability-Grenz-Spike für Variante B](remote-trust-model.md#genau-empfohlener-nächster-technischer-spike) auf einem Wegwerfhost mit Non-root-Sandbox durchführen: minimale Adapteroberfläche gegen einen simulierten kompromittierten Client, zwei Workspaces und Dummy-Secrets prüfen; danach Antwortverlust, Approval-Rennen, Gerätewiderruf und getrennte Client-/Adapter-/Backendverluste untersuchen.

Zunächst kein Remote-Listener und keine Android-App nötig. Die konkrete Transportauthentifizierung und Android-Credentialspeicherung bleiben anschließend vor Remote-Freigabe nachzuweisen. Der hier eingegrenzte Workflow benötigt kein allgemeines manuelles Terminal; ADR 0003 bleibt unverändert. Bei späteren Umsetzungsschritten Schema und Plattformstände erneut prüfen.
