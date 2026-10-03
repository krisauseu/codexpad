# CodexPad

> Fortschreibung 3. Oktober 2026: Für den inzwischen implementierten persönlichen
> Single-User-Betrieb gelten [HOST_SETUP](HOST_SETUP.md) und
> [ADR 0005](docs/decisions/0005-personal-trusted-host.md): Trusted Host mit
> Full Access/never/administrativem sudo, keine gewöhnlichen Tablet-Approvals.
> Pi 4 ARM64 ist das nächste Hostziel, noch nicht praktisch abgenommen.
> Die folgende Vision und Research-Reihenfolge bleiben ihr historischer Stand;
> sie definieren kein abweichendes Permissionmodell für den persönlichen Modus.

Arbeitstitel. Stand: 18. September 2026. Produktvision und Architekturhypothese, kein PRD. Erste begrenzte [ADRs](docs/decisions/README.md) liegen vor; die Implementierungsarchitektur ist noch nicht vollständig entschieden.

## Problem und Vision

Ein Android-Tablet kann sich gut für das Formulieren von Entwicklungsaufgaben, Lesen von Code und Prüfen von Änderungen eignen. Die eigentliche Entwicklungsumgebung verlangt jedoch oft Linux-Werkzeuge, lange laufende Prozesse und mehr Rechenleistung. Eine Terminal-App lässt sich dafür verwenden, bildet Agent-Sessions, Approvals, Dateien und Reviews aber nicht als zusammenhängenden nativen Arbeitsablauf ab. Das ist die zu überprüfende Problemhypothese, noch kein Ergebnis von Nutzerinterviews.

CodexPad soll eine native Android-Oberfläche für agentisches Softwareentwickeln auf Tablets werden. Nutzer öffnen einen Workspace, geben einem Agenten eine Aufgabe, verfolgen seine Arbeit und prüfen Änderungen. Dateien, Diffs, Prozesszustände und notwendige Entscheidungen erscheinen als bedienbare Android-Ansichten. Git, Tests, Builds und Agenten laufen zunächst auf einem entfernten Linux-System.

Das Projekt ist ein Open-Source-Nebenprojekt ohne Deadline. Es darf wochenlang ruhen. Dokumentierte Annahmen, überprüfbare Quellen und begründete Entscheidungen sind Teil seiner Arbeitsweise. Der Name ist noch keine Markenentscheidung; eine Projektlizenz ist noch nicht gewählt.

## Erste Zielgruppe und Kern-Use-Case

Zunächst erfahrene Entwickler, die ein Android-Tablet und Zugang zu einem eigenen Linux-Rechner oder Server haben. Sie können einen Remote-Host einrichten und möchten bestehende Projekte vom Tablet aus bearbeiten oder begleiten. Vollständig betreutes Hosting und ein Einstieg ohne Entwicklungskenntnisse sind vorerst keine Voraussetzung.

Ein erster vollständiger Arbeitsablauf könnte so aussehen:

1. Einen vertrauten Host und ein vorhandenes Git-Projekt öffnen.
2. Dem Agenten eine begrenzte Aufgabe stellen.
3. Fortschritt, Tool-Aufrufe und nötige Freigaben nachvollziehen.
4. Änderungen und Testergebnis prüfen, Rückfragen stellen oder Arbeit stoppen.
5. Die App verlassen und später mit erkennbarem, aktuellem Zustand zurückkehren.

„Fertig“ bedeutet dabei nicht nur eine abschließende Agent-Nachricht. Der Nutzer muss erkennen können, welche Dateien verändert wurden und welche Prüfungen tatsächlich liefen. Ob v0.1 bereits direktes Editieren oder Git-Schreiboperationen braucht, bleibt offen.

## Produktprinzipien

- **Native-first.** Android-Navigation, Touch, Tastatur, Fenstergrößen, Auswahl, Accessibility und Systemintegration bestimmen die Bedienung. Kotlin/Compose ist der erste technische Kandidat. Native Android-Views können spezialisierte Editor-/Terminalaufgaben übernehmen.
- **Remote-first.** Der Linux-Workspace besitzt die autoritativen Dateien und die Ausführungsumgebung. Android zeigt Zustand und steuert Arbeit. Reconnect und sichtbare veraltete Daten sind grundlegende Produktzustände.
- **Agent-first.** Aufgabe, Fortschritt, Entscheidung und Review bilden den Hauptablauf. Dateieditor und Terminal unterstützen diesen Ablauf. Eine vollständige klassische IDE mit Plugin-Ökosystem ist kein Startziel.
- **Nachvollziehbare Kontrolle.** Host, Projekt, laufende Arbeit und Umfang von Freigaben bleiben sichtbar. Eine verlorene Verbindung darf nicht wie eine erfolgreiche Aktion aussehen.
- **Kleine, überprüfbare Schritte.** Erst die riskanten Integrationsannahmen untersuchen, dann einen Stack festlegen. Entscheidungen erhalten Kontext und einen Anlass zur erneuten Prüfung.

## Weshalb keine verkleinerte Desktop-IDE

Ein Tablet wird in wechselnden Fenstergrößen mit Touch, Hardwaretastatur oder Bildschirmtastatur genutzt. Permanenter Dateibaum, mehrere Editorgruppen, Konsole und Agent-Chat würden um dieselbe Fläche konkurrieren. CodexPad soll jeweils die aktuelle Aufgabe priorisieren: Auftrag schreiben, Approval beurteilen, Diff lesen oder Prozess untersuchen. Große Fenster können mehrere Ansichten kombinieren; kleine Fenster führen schrittweise durch denselben Ablauf.

Native-first bedeutet deshalb mehr als eine native Hülle um einen Web-Editor. Für Codeanzeige und Terminal müssen passende Komponenten noch erprobt werden. Deren Funktionsumfang darf die gesamte Navigation nicht vorgeben.

## Remote Execution als Produktvorteil

Der Nutzer kann ein leichtes Tablet mit einem leistungsfähigen Linux-Rechner, einer vorhandenen Toolchain oder später einem kurzlebigen Container verbinden. Rechenleistung und Speicher des Hosts lassen sich unabhängig vom Tablet wählen. Projekte müssen nicht vor jeder Nutzung vollständig kopiert werden.

Diese Trennung könnte außerdem lange Agent-Aufgaben vom Android-Lebenszyklus entkoppeln. Sie entsteht allerdings nicht automatisch durch einen WebSocket: Der Host muss die erforderliche Lebensdauer tatsächlich gewährleisten. Die Linux-Spikes mit Codex 0.154.0 bestätigen einen weiterlaufenden Modell-Turn bei Clientverlust, während erfolgreich gestartete `command/exec`-Prozesse mit und ohne PTY beim Disconnect endeten. Persistente Unterhaltung, laufender Agent-Turn und Entwicklerterminal sind deshalb getrennt zu betrachten. [Codex-Recherche](docs/research/codex-integration.md)

Der Preis sind Netzwerkabhängigkeit, Host-Einrichtung und ein bewusst gestalteter Umgang mit Latenz und Verbindungsausfällen. Offline könnten bereits gelesene Dateien und Ergebnisse verfügbar sein, ohne neue Remote-Ausführung zu versprechen.

## Architekturhypothese

```text
Native Android-App
    │
    ├── Workspace-Abstraktion
    │      ├── Remote Workspace
    │      └── später Local Workspace
    │
    └── Agent-Abstraktion
           ├── Codex-Adapter
           └── später weitere Agent-Adapter

Agent-Session ── gehört zu ── Workspace + Ausführungsumgebung
Remote-Adapter ── nutzen ── gesicherten Transport zum Host
```

Die Trennung beschreibt Verantwortlichkeiten, keine bereits definierten Interfaces, Services oder Deployment-Einheiten. Workspace meint Dateien, Git und Ausführungskontext; Agent meint Aufgaben, Verlauf, Ereignisse und Entscheidungen. Die Session muss wissen, auf welchem Workspace und Host sie arbeitet. Die beiden Abstraktionen sind daher nicht unabhängig voneinander.

Die Recherche verändert die Ausgangshypothese an einer wichtigen Stelle: Codex App-Server bietet inzwischen selbst Dateioperationen, Watching und PTY-/Prozesssteuerung. Ein erster Adapter könnte somit Workspace- und Agent-Fähigkeiten über dieselbe Verbindung bereitstellen. Ein eigener Workspace-Daemon ist keine feststehende Voraussetzung. Provider-spezifische Fähigkeiten und Einschränkungen müssen trotzdem sichtbar bleiben. Eine künstliche Gleichheit aller künftigen Agenten wäre ebenso voreilig wie direkte Codex-Typen in jeder UI-Ansicht. [Codex](docs/research/codex-integration.md), [Remote-Varianten](docs/research/remote-workspaces.md)

Ein unveränderter Codex App-Server ist nach den Linux-Spikes die primäre Agent-Schnittstelle. Reconnect setzt einen Abgleich mit dem Serverzustand voraus. Der geschützte Remote-Transport und die Begrenzung direkter Hostfähigkeiten sind als Nächstes zu klären. Welche ergänzenden Workspace-Funktionen nötig sind, entscheidet der konkrete Workflow. Weder SSH noch ein eigener HTTPS-Daemon sind endgültig gewählt.

## Bewusst außerhalb einer ersten Version

Keine vollständige VS-Code-Kompatibilität, kein Extension-Marktplatz, keine eigene Agent-Engine und kein Codex-Fork. Ebenso vorerst keine lokale Linux-Distribution, keine garantierte Offline-Ausführung, kein Multi-Tenant-Hosting und kein gemeinsames Echtzeit-Editieren. Eine umfassende Sprachserver-/Debugger-IDE ist nicht Voraussetzung des ersten Agent-Workflows.

Diese Research-Phase enthält überhaupt keine Anwendung, Serverimplementierung, API oder installierten Anwendungsdependencies.

## Spätere Möglichkeiten

Weitere Agent-Provider, mehrere Hosts und Projekte, kurzlebige Container-Workspaces, System-Dateiintegration per DocumentsProvider und bessere Offline-Lesefunktionen könnten folgen. Ebenso denkbar sind tieferes Editieren, gezieltes Git-Staging und verlässliche Hintergrundmeldungen.

Local Execution auf ARM64-Android bleibt eine getrennte Forschungsrichtung. PRoot-/Termux-artige Ansätze können Linux-Userspace ermöglichen, liefern aber nicht automatisch vollständige Linux-Kompatibilität, Docker-Isolation oder eine geeignete Codex-Sandbox. Lokale Ausführung muss dieselben Produktverträge erfüllen oder ihre geringeren Fähigkeiten ausdrücklich melden. [Android-Recherche](docs/research/android-platform.md)

## Größte Unsicherheiten

1. Wie werden die nachgewiesenen Codex-Funktionen versionsfest und bei Request-/Approval-Rennen zuverlässig verwendet?
2. Welche Arbeit muss nach Netzverlust weiterlaufen, und wer besitzt ihre Lebensdauer?
3. Wie werden parallele Dateiänderungen, große Repositories und veraltete Diffs sicher dargestellt?
4. Welche Terminal-/Editor-Komponenten erfüllen native Bedienung, Accessibility und passende Lizenzbedingungen?
5. Welche Hintergrundmeldungen sind ohne zusätzliche zentrale Infrastruktur realistisch?
6. Ist der Ablauf für Tablet-Nutzer wertvoll genug, um Host-Einrichtung und Remote-Komplexität zu rechtfertigen?

Die konkretisierten Fragen und v0.1-Voraussetzungen stehen in [Offene Fragen](docs/product/open-questions.md). Die Architekturzeichnung beantwortet diese verbleibenden Fragen nicht.
