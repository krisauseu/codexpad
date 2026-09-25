# Android-Plattform: native Tablet-Oberfläche

Stand und Quellenabruf: **18. September 2026**. Research, keine Stack- oder Produktentscheidung. Die verlinkten Primärquellen wurden für diese Notiz gelesen; Bibliotheken wurden weder installiert noch auf einem Gerät erprobt. „Fakt“ bezeichnet dokumentiertes Verhalten, „Hypothese“ eine Folgerung für CodexPad, „offen“ einen noch notwendigen Nachweis. API-/Bibliotheksversionen müssen vor einem Prototyp erneut geprüft werden.

## Ergebnis

**Hypothese:** Kotlin und Jetpack Compose sind eine geeignete Basis für das native Cockpit. Die größten Risiken liegen weniger bei gewöhnlichen Tablet-Layouts als bei langlebigen Verbindungen, zuverlässiger Wiederaufnahme, Editor-/Terminalqualität und der Semantik entfernter Dateien. Agenten und Builds sollten unabhängig vom Lebenszyklus der Android-App auf dem Host weiterlaufen. Das ist ein technischer Grund für Remote-first.

Ein eigener `DocumentsProvider` ist eine mögliche spätere Systemintegration, aber keine Voraussetzung für einen nativen Dateibaum. Ein nativer Client muss entfernte Dateien nicht als lokales POSIX-Dateisystem ausgeben. Auch ein nativer Android-View innerhalb einer Compose-Oberfläche ist mit Native-first vereinbar.

## Kotlin, Compose und adaptive Fenster

**Fakt:** Google empfiehlt Compose, Material 3 Adaptive, Window Size Classes und mehrteilige Layouts. Maßgeblich ist die aktuelle Fenstergröße, nicht eine feste Einteilung „Tablet versus Telefon“. Die Oberfläche muss Größenänderungen, Hoch-/Querformat und Multi-Window verarbeiten. Für Apps mit Target API 36 ignoriert Android 16 bestimmte Orientierungs-, Seitenverhältnis- und Größenbeschränkungen auf großen Displays; ein starr auf Landscape zugeschnittenes Design ist deshalb keine belastbare Grundlage. [Adaptive Empfehlungen](https://developer.android.com/develop/adaptive-apps/guides/adaptive-dos-and-donts), [Compose Adaptive Apps](https://developer.android.com/develop/ui/compose/build-adaptive-apps)

**Fakt:** Desktop Windowing ergänzt frei skalierbare Fenster und mehrere App-Instanzen; die Android-Dokumentation beschreibt hierfür eigene Interaktionen und seit Android 15 Manifest-/Drag-and-drop-Unterstützung. Die Verfügbarkeit auf einem konkreten Tablet ist damit noch nicht nachgewiesen. [Desktop Windowing](https://developer.android.com/develop/adaptive-apps/guides/support-desktop-windowing)

**Hypothese:** Große Fenster zeigen Projekt-/Sessionnavigation, Agentverlauf und eine Detailfläche für Diff, Datei oder Terminal. Kleine Fenster zeigen dieselben Aufgaben nacheinander. Keine feste Miniaturausgabe eines Desktop-IDE-Layouts. Ein Fensterzustand darf keine zweite Agent-Session unabsichtlich erzeugen.

**Offen vor v0.1:** Mindest-Androidversion und reales Referenzgerät; Navigation bei Split-Screen und Bildschirmtastatur; Wiederherstellung nach Activity-/Prozessverlust; zuerst ein oder mehrere App-Fenster. Eine konkrete Compose-Version wird bewusst noch nicht festgelegt.

## Tastatur, Zeiger und Drag & Drop

**Fakt:** Compose unterstützt Tastaturereignisse und Modifier-Kombinationen; Textkomponenten bringen Standardaktionen mit. Eigene Shortcuts benötigen bewusstes Event-Handling und Fokusmanagement. [Keyboard Actions](https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/commands)

**Hypothese:** „Neue Aufgabe“, „Datei suchen“, „Diff öffnen“, „Terminal fokussieren“ und „Abbrechen“ brauchen Shortcuts und sichtbare Touch-Alternativen. Terminal-Tasten wie Ctrl-C dürfen nicht pauschal von globalen Shortcuts verschluckt werden. Deutsche Layouts, AltGr, externe Tastaturen, IME, Tab-Reihenfolge und Screenreader sind Teil des späteren UX-Nachweises.

**Fakt:** Compose bietet `dragAndDropSource` und `dragAndDropTarget`, auch für Datenaustausch zwischen Apps über `ClipData`. Beim Empfang externer Inhalte müssen Berechtigungen und Datentypen berücksichtigt werden. [Drag and Drop](https://developer.android.com/develop/ui/compose/touch-input/user-interactions/drag-and-drop)

**Hypothese:** Eine Datei auf eine Agent-Aufgabe ziehen könnte deren Referenz oder einen Upload auslösen. Diese beiden Operationen müssen unterscheidbar sein: Ein URI vom Tablet ist kein für den Remote-Agenten unmittelbar lesbarer Pfad. Große Uploads benötigen Fortschritt, Abbruch und Fehlerzustand.

## Dateien: SAF, DocumentsProvider und Remote-Semantik

**Fakt:** Das Storage Access Framework lässt Nutzende einzelne Dokumente oder Verzeichnisbäume freigeben. Zugriff läuft über Dokument-URIs und Provider, nicht zwingend über lokale Pfade. Persistierbare URI-Rechte sind möglich; Android schränkt die Auswahl bestimmter Wurzel-/App-Verzeichnisse ein. [SAF-Zugriff](https://developer.android.com/training/data-storage/shared/documents-files)

**Fakt:** Ein `DocumentsProvider` kann lokale oder Cloud-Dateien anbieten. Er besitzt stabile Dokument-IDs, Wurzeln und pro Dokument gemeldete Fähigkeiten, beispielsweise Schreiben oder Löschen. Die physische Speicherung ist unabhängig von der dargestellten Hierarchie. [SAF-Modell](https://developer.android.com/guide/topics/providers/document-provider), [Provider implementieren](https://developer.android.com/guide/topics/providers/create-document-provider), [API-Referenz](https://developer.android.com/reference/android/provider/DocumentsProvider)

**Hypothese:** Intern sollten Workspace-ID, relativer Pfad, Versionskennung und Fähigkeiten die Datei identifizieren. Die native Dateiansicht spricht mit der Workspace-Abstraktion. SAF dient zuerst Import/Export; ein eigener Provider könnte später Dateien in anderen Android-Apps verfügbar machen. Damit würde CodexPad jedoch auch Zugriffsrechte, stabile IDs, temporäre Nichterreichbarkeit und Schreibabschluss für externe Clients verantworten.

**Nicht gleichsetzen:** Dokument-URI, lokaler Cache und autoritative Remote-Datei sind drei verschiedene Dinge. Ein Provider ersetzt weder Git noch einen Remote-Watcher und verspricht keine vollständige POSIX-Semantik. Der Nutzer kann Dateien dennoch nativ öffnen, suchen und vergleichen.

**Offen vor schreibendem Prototyp:** Atomisches Speichern, Konfliktprüfung anhand einer Version bzw. eines Hashs, Symlinks und Ausbruch aus dem Projektwurzelpfad, Dateinamen/Encoding, große und binäre Dateien, Berechtigungen, Umbenennen, parallele Änderungen durch Agent und Mensch. Eine automatisch synchronisierte Vollkopie großer Repositories ist keine vorausgesetzte Lösung.

## Credentials und Biometrie

**Fakt:** Android Keystore schützt kryptographische Schlüssel und kann ihre Verwendung an Nutzerautorisierung binden. Hardwarebindung und StrongBox hängen von Gerät und Algorithmus ab und sind überprüfbar, nicht garantiert. Nicht exportierbare Schlüssel können über Signatur-/Verschlüsselungsoperationen benutzt werden. [Android Keystore](https://developer.android.com/privacy-and-security/keystore)

**Fakt:** `BiometricPrompt` unterstützt unterschiedliche Authentikatorstärken und Gerätecredentials sowie die Bindung an kryptographische Operationen. Ein biometrischer Dialog allein ist nicht automatisch eine Verschlüsselung gespeicherter Tokens. [Biometrische Authentifizierung](https://developer.android.com/identity/sign-in/biometric-auth)

**Hypothese:** App-eigene Tokens oder importierte SSH-Schlüssel werden verschlüsselt gespeichert; ein Keystore-Schlüssel schützt das Material. Alternativ kann eine SSH-Bibliothek direkt mit einem nicht exportierbaren Signaturschlüssel arbeiten, sofern Algorithmus und Adapter das erlauben. Das muss praktisch geprüft werden. Zugriffstoken und private Schlüssel gehören weder in Logs noch in unverschlüsselte Backups.

**Offen vor v0.1:** Schlüsselimport oder gerätegebundene Erzeugung; unterstützte SSH-Algorithmen; Host-Key-Prüfung beim ersten Kontakt und bei Änderungen; Wiederherstellung nach Geräteverlust; Trennung von Transportcredentials und Agent-/OpenAI-Credentials. Biometrische Freigabe pro Verbindung kollidiert mit unbeaufsichtigtem Reconnect: ausdrücklich festlegen, welche Nutzung im gesperrten Zustand zulässig ist.

## Hintergrundarbeit, Verbindung und Benachrichtigungen

**Fakt:** WorkManager ist für verlässlich eingeplante, persistente Arbeit vorgesehen. Das ist ein anderes Modell als ein durchgehend interaktiver Transport. [WorkManager](https://developer.android.com/develop/background-work/background-tasks/persistent)

**Fakt:** Doze und App Standby beschränken Netzwerkzugriff und Hintergrundausführung; Doze verschiebt auch WorkManager-Jobs. Google empfiehlt FCM für zeitnahe nutzersichtbare Nachrichten im Ruhezustand. Eine eigene dauerhaft offene Verbindung ist keine allgemeine Zustellgarantie. [Doze und App Standby](https://developer.android.com/training/monitoring-device-state/doze-standby)

**Fakt:** Foreground Services haben Voraussetzungen und typspezifische Grenzen. Für Apps mit entsprechendem Target begrenzt Android 15 unter anderem `dataSync` im Hintergrund auf insgesamt sechs Stunden innerhalb von 24 Stunden. Ein beliebiger Agent-WebSocket darf deshalb nicht einfach als unbegrenzter `dataSync`-Dienst modelliert werden. [FGS-Typen](https://developer.android.com/develop/background-work/services/fgs/service-types), [FGS-Zeitlimits](https://developer.android.com/develop/background-work/services/fgs/timeout)

**Fakt:** Android 13+ verwendet für normale Benachrichtigungen die Runtime-Berechtigung `POST_NOTIFICATIONS`; Nutzende können die Zustellung verweigern. [Notification Permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)

**Hypothese:** Solange die Oberfläche aktiv ist, streamt sie Ereignisse. Bei Rückkehr lädt sie den autoritativen Serverzustand samt Historie und gleicht ihn mit Live-Events ab. Ein verlustfreies Nachladen fehlender Events darf sie nicht voraussetzen. Die Linux-Spikes belegen weiterlaufende Modell-Turns und ein erneut zugestelltes Datei-Approval bei Clientverlust, aber keine Persistenz offener Approvals bei Serververlust. `command/exec`-Prozesse einschließlich PTY endeten beim Clientverlust. [Experimenteller Stand und Lifecycle-Matrix](codex-integration.md#reconnect-ist-mehr-als-session-resume). Das ist noch kein Nachweis auf Android. WorkManager eignet sich etwa für begrenzte Nachsynchronisation, nicht als Agentprozess.

**Offen vor v0.1:** Verspricht der erste Prototyp Meldungen nur bei offener App oder auch bei ausgeschaltetem Display? FCM würde zusätzliche Infrastruktur und einen Google-Dienst einführen; eine selbst gehostete/Google-freie Alternative ist gesondert zu untersuchen. Polling und ein SSH-Tunnel liefern im Hintergrund keine gleichwertige Sofortgarantie. Benachrichtigungen sollten keine Quelltexte oder Secrets im Sperrbildschirm preisgeben; Approvals bleiben bei fehlender Zustellung offen, nicht implizit genehmigt.

## WebSocket und SSH: Bibliothekskandidaten

**Fakt:** Ktor Client dokumentiert WebSockets mit Nachrichten, Ping-Konfiguration und verschiedenen Engines; nicht jede Engine unterstützt denselben Umfang, und bei OkHttp wird Ping über die Engine konfiguriert. [Ktor WebSockets](https://ktor.io/docs/client-websockets.html)

**Hypothese:** Ein Kotlin-Transport kann strukturierte Agent-Events über WSS oder einen SSH-geschützten Kanal übertragen. Heartbeats erkennen Verbindungsprobleme; sie ersetzen weder Replay noch Resume noch Android-Lifecycle-Behandlung. Reconnect benötigt begrenztes Backoff, Auth-Erneuerung und eine fachliche Zustandsprüfung.

| Kandidat | Nachgewiesen | Noch zu prüfen |
| --- | --- | --- |
| SSHJ | Java-SSH mit SFTP, Shell-/Command-Kanälen, Forwarding und Host-Key-Verifikation; Apache-2.0. Das Projekt warnt vor Terrapin bei Versionen bis 0.37.0. | Aktuelle Android-Kompatibilität, Kryptoprovider, Speicher-/Threadbedarf, Keystore-Signaturadapter, PTY und Netzwechsel auf dem Zielgerät. |
| mwiede/JSch | Gepflegter JSch-Fork mit aktualisierten Algorithmen; BSD-artige Lizenz laut Lizenzdatei. | Android-/Providerkombination, Key-Import, Host-Key-UX, PTY-/SFTP-Verhalten und Integration nicht exportierbarer Schlüssel. |
| HTTPS/WSS statt eingebettetem SSH | Mit Ktor als Kotlin-Client dokumentierbar; SSH kann gegebenenfalls außerhalb der App durch ein privates Netz ersetzt werden. | Serverauthentifizierung, Pairing, Zertifikate, zusätzlicher Hostdienst und Betrieb. |

Quellen: [SSHJ Repository](https://github.com/hierynomus/sshj), [JSch Repository](https://github.com/mwiede/jsch), [JSch Lizenz](https://github.com/mwiede/jsch/blob/master/LICENSE.txt). Diese Tabelle ist eine Prüfliste, keine Dependency-Freigabe. Java-Unterstützung allein beweist keine Funktionsfähigkeit aller Algorithmen auf Android.

## Terminal, PTY und Editor

**Fakt:** Termux trennt unter anderem `terminal-emulator` und `terminal-view` vom übrigen App-Code. Die Repository-Lizenz ist GPLv3-only, nennt aber Apache-2.0-Code in den Terminalbibliotheken als Ausnahme. **Daraus folgt keine pauschale Apache-Lizenz für beliebigen Termux-Code.** Vor Übernahme sind die konkreten Dateien, Änderungen und transitiven Teile zu prüfen. [Termux](https://github.com/termux/termux-app), [Lizenz und Ausnahmen](https://github.com/termux/termux-app/blob/master/LICENSE.md), [Terminal Emulator](https://github.com/termux/termux-app/tree/master/terminal-emulator), [Terminal View](https://github.com/termux/termux-app/tree/master/terminal-view)

**Hypothese:** Die PTY läuft auf Linux, Android emuliert Bildschirm und Eingabe. Benötigt werden Terminalgrößenänderung, Escape-Sequenzen, Unicode, Scrollback, Auswahl, IME und kontrollierter Umgang mit Clipboard-Sequenzen. Ein Log-Textfeld ist kein interaktives Terminal. Agent-Toolausgabe und ein frei bedienbares Terminal sind fachlich getrennte Ansichten.

**Fakt:** Sora Editor ist eine native Android-Editorbibliothek mit inkrementellem Highlighting, Undo/Redo, Suche, Diagnosemarkern sowie TextMate-/Tree-sitter-Unterstützung; die Lizenz lautet LGPL-2.1-or-later. [Sora Editor](https://github.com/Rosemoe/sora-editor)

**Fakt:** Compose kann klassische Android-Views über `AndroidView` integrieren. Eine geeignete View-Komponente erfordert somit keinen Wechsel zu einer WebView. [Views in Compose](https://developer.android.com/develop/ui/compose/migrate/interoperability-apis/views-in-compose)

**Offen:** Für v0.1 zunächst lesbarer Code/Diff oder vollwertiges Editieren? Sora ist ein Kandidat, kein getesteter Stack. Vor Einbindung sind LGPL-Pflichten bei APK-Verteilung, abhängige Grammatiken, Wartbarkeit, Accessibility, große Dateien und Tastatur-/IME-Verhalten zu prüfen. Native Parser-Bibliotheken erhöhen zudem ABI-/Packaging-Aufwand: Android unterstützt Geräte mit 16-KB-Speicherseiten und verlangt entsprechende Kompatibilität nativer Libraries. [16-KB-Seiten](https://developer.android.com/guide/practices/page-sizes)

## Offline und Cache

**Fakt:** Androids Offline-first-Leitfaden unterscheidet lokale und Netzwerkdatenquellen und behandelt Warteschlangen, Synchronisation und Konflikte. Lokaler Lesezugriff kann verfügbar bleiben, obwohl Netzschreibzugriffe nicht möglich sind. [Offline-first](https://developer.android.com/topic/architecture/data-layer/offline-first)

**Hypothese:** Zuerst gezielt Sessionübersichten, zuletzt gelesene Dateien und Diffs cachen; sichtbar mit Aktualitätszeitpunkt und Workspace-Version. Ein Remote-first-Produkt kann offline lesbar sein, ohne offline Agenten oder Builds auszuführen. Vorläufig keine automatischen Offline-Commits oder erneut abgesendeten Shell-Kommandos. Eine Prompt-Warteschlange braucht explizite Sendesemantik, da erneut ausgeführte Aufgaben reale Seiteneffekte haben können.

**Offen vor v0.1:** Cacheumfang und Verschlüsselung, Löschung beim Entfernen eines Hosts, Quoten, Umgang mit Secrets und Konfliktanzeige. Für große Repositories nur geöffnete Dateien laden; Suche/Git möglichst beim Host ausführen. Leistungswerte sind noch zu messen.

## Spätere lokale Linux-Execution auf ARM64

**Fakt:** Termux bietet eine Android-Terminal-/Linux-Umgebung mit eigenem Paketökosystem. Sein README warnt vor Android-Prozessbeschränkungen und beendetem Hintergrund- oder CPU-intensivem Arbeiten. Das genaue Verhalten hängt von Android-Version und Gerät ab; die dort genannten historischen Grenzwerte werden hier nicht als universelle aktuelle Garantie übernommen. [Termux README](https://github.com/termux/termux-app)

**Fakt:** PRoot-Distro nutzt PRoot zur Ausführung von Linux-Userspace ohne echtes Root. Laut eigener Dokumentation kosten abgefangene Syscalls Leistung; echte Kernel-Namespaces, cgroups und vollständige Init-/Containerfunktionen fehlen. PRoot ist Pfad-/Systemaufrufvermittlung, keine mit Docker gleichzusetzende Sicherheitsisolation. [PRoot-Distro, Limitations](https://github.com/termux/proot-distro#limitations)

**Fakt:** Android schränkt seit Target API 29 das Ausführen von Dateien im beschreibbaren App-Home ein. Daher lässt sich eine spätere App mit aktuellem Target nicht ohne Prüfung durch „Linux-Binaries herunterladen und ausführen“ realisieren. [Android 10, Execution Permission](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission)

**Hypothese:** ARM64 vermeidet bei passenden ARM64-Binaries CPU-Emulation, beseitigt aber weder Android-ABI-/Kernelunterschiede noch Prozess-, Dateisystem- und Hintergrundgrenzen. Eine separate Termux-Integration könnte weniger Einbettungsaufwand bedeuten, hätte aber eigene Installations-, Berechtigungs- und Lifecycle-Verträge. Codex-Sandboxfunktionen und Toolchains müssen für jeden Ansatz separat geprüft werden; das Aufweichen der Sandbox wäre keine stille Kompatibilitätslösung.

**Offen für später:** Unterstützte Toolchains und native npm-Module, echte Isolation untrusted Repositories, Energie-/Wärmebudget, Binärverteilung, Play-/alternative Distributionswege, Lizenzpflichten und ARM64-Codex-Laufzeit. Keine Aussage dieser Recherche belegt, dass eine komplette lokale, sicher isolierte Linux-Entwicklungsumgebung auf gewöhnlichen Android-Tablets bereits verfügbar wäre.

## Nächste Android-Exploration nach separater Beauftragung

Ein kleines UI- und Lifecycle-Experiment wäre sinnvoll, sobald die strukturierte Remote-Anbindung nachgewiesen ist: eine Session mit Eventliste und Approval-Zustand anzeigen, App in den Hintergrund legen bzw. Prozess beenden, wieder öffnen und Zustand konsistent rekonstruieren. Dazu Split-Screen, Hardwaretastatur und verweigerte Notification-Berechtigung testen. Editor, kompletter Dateiprovider und lokale Linux-Laufzeit würden diesen Nachweis unnötig vergrößern. In dieser Phase wurde nichts davon implementiert.
