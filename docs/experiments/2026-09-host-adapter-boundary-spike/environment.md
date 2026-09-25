# Umgebung und Ausführung

22.09.2026. Alle für die Ergebnisse verwendeten Sicherheits-/Capabilitytests:
Unix-Benutzer **cpboundary**, UID **996**, primäre GID **986** (cpboundary),
keine Zusatzgruppen. Systemkonto mit /usr/sbin/nologin und ohne angelegtes Passwort.
Keine sudo-/Docker-/Administrationsgruppen zugewiesen. Im normalen Prozesspfad
zusätzlich NoNewPrivs=1 und CapInh/Prm/Eff/Bnd/Amb jeweils 0.
[Maschinelles Inventar](evidence/environment.json).

| Merkmal | Wert |
| --- | --- |
| OS | Ubuntu 24.04.5 LTS, x86_64 |
| Kernel | 6.8.0-139-generic |
| Python | 3.12.3, ausschließlich Standardbibliothek |
| Codex | codex-cli 0.155.0 |
| App Server | Unterkommando desselben Binaries; keine separat bestimmbare Releaseversion |
| Binary SHA-256 | 660e159a49e823ac8e5986cb238f73158ce4b957d40d9292f8de90862644b501 |
| Quelle | /root/.codex/packages/standalone/releases/0.155.0-x86_64-unknown-linux-musl/bin/codex |
| Ausführungskopie | /tmp/codexpad-boundary-20260922/bin/codex |
| Transport | private stdin/stdout-Pipes, kein TCP-/öffentlicher Listener |
| Modell | künstlicher Name spike-no-model; kein Modell ausgeführt |
| Provider | spike-disabled; absichtlich fehlende Umgebungsvariable, kein API-Key gesetzt |
| Testauth | nur Principalzeichenketten im Harness; keine echten Gerätecredentials |

Die Umgebung wird mit env -i komplett neu aufgebaut. HOME, CODEX_HOME, TMPDIR,
XDG_CONFIG_HOME und PATH zeigen auf das Testlayout bzw. Systemprogramme.
PYTHONDONTWRITEBYTECODE=1. Keine Hostauthdatei, kein persönliches config.toml,
SSH-Agent, Proxy, OpenAI-Token oder API-Key übernommen. Im Code wird kein
Login-/Account-RPC aufgerufen. Der künstliche Provider zeigt ausschließlich auf
127.0.0.1:9; fehlende Credentialvariable stoppt vor Modellkontakt. Kein Listener dort.
[Exakte Konfiguration](test-config.toml).

## Pfade und Rechte

Basis B = /tmp/codexpad-boundary-20260922.

| Pfad | Eigentümer:Gruppe | Modus | Zweck |
| --- | --- | --- | --- |
| B | root:root | 0755 | unveränderbarer gemeinsamer Elternpfad |
| B/bin und B/bin/codex-resources | root:root | 0755 | isolierte ausführbare Ressourcen |
| B/bin/codex, .../bwrap | root:root | 0755 | unveränderte Kopien, keine Setuidbits |
| B/home | cpboundary:cpboundary | 0700 | leeres separates Home |
| B/runtime | cpboundary:cpboundary | 0700 | Codekopie, Journale, temporärer Codex-State |
| B/workspace-a, B/workspace-b | cpboundary:cpboundary | 0700 | getrennte Testverzeichnisse, gleiche UID |
| .../allowed-a.txt, .../allowed-b.txt | cpboundary:cpboundary | 0600 | künstliche Marker |
| B/private-area | cpboundary:cpboundary | 0700 | außerhalb A/B, absichtlich gleiche UID |
| .../dummy-secret.txt | cpboundary:cpboundary | 0600 | ARTIFICIAL-MODEL-SECRET-NOT-A-CREDENTIAL |
| B/os-private | root:root | 0700 | echte Unix-Rechtegrenze zur Test-UID |
| .../dummy-secret.txt | root:root | 0600 | ARTIFICIAL-OS-SECRET-NOT-A-CREDENTIAL |
| neues Experimentverzeichnis | cpboundary:cpboundary | 0700 | Berichte, Code und kompakte Evidenz |

Die geschützte Datei wurde bei der Einrichtung erzeugt. Die unprivilegierte
Inventarisierung kann nur ihren Elternpfad statten; ihre Rechte stammen aus
dem protokollierten Setup. Read/Write-Proben erhielten EACCES.

A/link-secret → B/private-area/dummy-secret.txt; A/link-b → B/workspace-b/allowed-b.txt.
A/hardlink-b verweist auf denselben Inode wie allowed-b.txt. Hier bezeichnet das
erste „B/“ in den Pfadangaben die Testbasis, nicht Workspace B.
Symlinks und Hardlink wurden als UID 996 erzeugt. Kein produktiver Dateibaum
wurde als Testziel verwendet. Alle Angriffsschreibziele lagen in dieser Testbasis.

**OS erzwungen:** fehlende Privilegien, rootgeschützter Dummybereich, keine Änderung
der root-eigenen Binary. **Nicht durch UID-Rechte getrennt:** A, B, private-area,
Home, eigener Codex-State und Adapterjournal. 0700/0600 trennen andere Benutzer,
aber keine Prozesse derselben UID. Das ist eine gezielte Negativkontrolle.
Die Geräte-/Workspace-/Thread-/Operationbindung wird ausschließlich im Adapter
erzwungen. Eine Agent-Lesegrenze ist damit nicht vorhanden.

## Einrichtung, Grenzen und Abweichungen

Root war zur Kontoeinrichtung, zum Anlegen/Chown des Testlayouts und zum Kopieren
der unveränderbaren Codex-Ressourcen nötig. Die Kontoanlage verändert zwangsläufig
die OS-Kontendatenbanken; dies ist die ausdrückliche Einrichtungs-Ausnahme des
Auftrags. Kein Dienst, sudoer-Eintrag, Firewall-, SSH-, TLS- oder Kernelsetting wurde
angelegt/geändert. Kein Paket installiert. Keine Git-Kommandos durch den Harness.

Die erste Binarykopie enthielt den mitgelieferten Bubblewrap-Helfer nicht.
Die erste Sandboxprobe endete deshalb mit Exit 101 „bubblewrap unavailable“.
Der Helfer wurde aus genau derselben Releaseinstallation in das Testlayout kopiert.
Danach reproduzierte die eigentliche Sandboxprobe Exit 1 mit
„loopback: Failed RTM_NEWADDR: Operation not permitted“. Kein Versuch, dies mit
privilegiertem Normalbetrieb oder Systemänderungen zu umgehen.
Beide Befunde bleiben getrennt in preflight.json und sandbox-preflight.json.

Ein erster Versuch, nach dem UID-Wechsel die Capability-Bounding-Set zu senken,
scheiterte schon in setpriv; kein Test begann. Korrigierter Launcher setzt UID/GID,
leert Gruppen und senkt Capabilities in einem Übergang. Die danach gestarteten
Programme laufen unprivilegiert. Der rootseitige Supervisor führt keine regulären
Tests aus.

**Verworfener privilegierter Harnesslauf:** Bei einer späteren Shell-Verkettung
stand der zweite Pythonaufruf versehentlich außerhalb des Privileg-Drop-Launchers.
Die OS-Proben dieses Fehlversuchs liefen als root; dabei wurde allein die
rootgeschützte künstliche Dummydatei mit SHOULD-NOT-WRITE überschrieben.
Die RPC-Konstruktorprüfung stoppte vor dem Start eines App Servers.
Das war eine Abweichung von der Auftragsgrenze, kein gültiger Test.
Alle dabei überschriebenen Ergebnis-/Umgebungsbelege wurden verworfen und unter
UID 996 neu erzeugt. Die künstliche root-Datei wurde auf den ursprünglichen Wert
zurückgesetzt; weder reale Credentials noch Produktdaten waren betroffen.
suite.py prüft nun bereits vor jeder Probe exakt UID 996; RPC/Adapter prüfen
zusätzlich Non-root. Der endgültige Hauptlauf umfasst 258 Prüfzeilen und ist
durch environment.json als UID 996 belegt. Der Vorfall wird nicht als PASS gezählt.

Eine anfängliche Schemaextraktion erwartete einen unquoted Methodenschlüssel
und erfasste keine Namen. Das wurde bemerkt, durch die korrekte Extraktion plus
Mindestanzahl-Assertion ersetzt und mit **167 Methoden einzeln erneut geprüft**.
Die vollständige finale Hauptsuite sowie der separate Methodennachweis enthalten
die korrigierte Abdeckung. Keine leere Liste wurde als vollständige Abdeckung gewertet.

## Wiederholung

Keine unkontrollierte Ausführung der historischen 0.154.0-Skripte. Der neue Code ist
auf das isolierte Layout zugeschnitten, kein Installationsprogramm. Nicht in einem
produktiven Workspace ausführen. Frische Testbasis und identische UID/Version
verwenden; supplement.py/crash_worker.py erwarten frische benannte Journale.
Das Schema vorher mit dem Testbinary unter der Test-UID exportieren.

Referenz für den Privilegwechsel; jeder gesamte Shellblock muss dahinter stehen:

```sh
env -i HOME=/tmp/codexpad-boundary-20260922/home \
  CODEX_HOME=/tmp/codexpad-boundary-20260922/runtime/codex-home \
  TMPDIR=/tmp/codexpad-boundary-20260922/runtime/tmp \
  XDG_CONFIG_HOME=/tmp/codexpad-boundary-20260922/runtime/xdg \
  PATH=/tmp/codexpad-boundary-20260922/bin:/usr/bin:/bin \
  PYTHONDONTWRITEBYTECODE=1 \
  setpriv --reuid=996 --regid=986 --clear-groups --no-new-privs \
    --bounding-set=-all --inh-caps=-all --ambient-caps=-all bash
```

Betrieb von Adapter/App Server benötigt danach keine Privilegien. Die Pythonmodule
werden aus runtime/code importiert; Ergebnisse relativ zum zuvor geöffneten
Experiment-Arbeitsverzeichnis geschrieben. /root selbst blieb 0700; keine ACL oder
Traversalberechtigung wurde erweitert. Die rootseitige Arbeitsverzeichniswahl
ermöglicht das Schreiben im eigens cpboundary zugewiesenen Experimentverzeichnis,
öffnet aber keinen Zugriff auf andere /root-Inhalte.

## Abschluss und Aufbewahrung

Alle Test-App-Server und Testprozesse beendet; nur der kurzlebige Auditprozess
selbst war beim Prozessinventar aktiv. Kein Listener verbleibt.
Codex-Home, Runtime-Logs, Schema-Vollausgabe, SQLite/WAL, Caches und temporäre
Codekopien wurden nach der Evidenzprüfung unprivilegiert entfernt. Ausnahme:
Ein root-eigener __pycache__ aus dem verworfenen Harnesslauf konnte von UID 996
nicht entfernt werden und bleibt unter runtime/code/__pycache__ zurück (nur
kompilierter Versuchscode, keine Runtime-/Credentialdaten). Keine zusätzliche
Rootaktion zur Bereinigung ausgeführt. Exaktes Restinventar: [cleanup.json](evidence/cleanup.json).
Die root-eigene isolierte Installation (Codex 269339072 Bytes plus Bubblewrap),
das gesperrte Testkonto und die kleinen Dummyworkspaces bleiben dokumentiert unter
der Testbasis stehen; sie sind **nicht im Projektarchiv**. Weitere privilegierte
Systembereinigung wurde nicht eigenmächtig durchgeführt.
Keine kompletten Codex-Homes, Tokens, JWTs oder realen Secrets archiviert.
