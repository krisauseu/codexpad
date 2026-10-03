# Trusted-LAN-HTTP – 3. Oktober 2026

Arbeitsbasis: `integrate/pi-host-docs@f507476`, enthält den aktuellen lokalen
`main@90a5bc8` (v0.1.1). Ausgangscheckout sauber. Pi-Clientabgleich und
`docs/pi-host-testplan.md` berücksichtigt. Keine Hoständerung, kein Commit,
Push, Merge, Release oder Versionssprung während der hier dokumentierten
Implementierungs-/Tablet-Abnahmephase. Der anschließende Abschluss steht unten.

## Geänderte Android-Dateien

Unter `app/src/main/`:

- `AndroidManifest.xml`
- `res/xml/network_security_config.xml` (neu)
- `java/dev/codexpad/settings/ConnectionSettings.kt`
- `java/dev/codexpad/settings/SettingsStore.kt`
- `java/dev/codexpad/network/CodexPadApi.kt`
- `java/dev/codexpad/network/LocalNetworkPolicy.kt` (neu)
- `java/dev/codexpad/ui/PadViewModel.kt`
- `java/dev/codexpad/ui/CodexPadApp.kt`
- `java/dev/codexpad/ui/ArtifactCard.kt`

Außerdem `app/src/debug/AndroidManifest.xml`; unter
`app/src/test/java/dev/codexpad/` `ConnectionSettingsTest.kt`,
`LocalNetworkPolicyTest.kt` (neu), `ApiTest.kt`, `ArtifactTest.kt`,
`TransferTest.kt`, `WorkspaceTest.kt`, `PythonContractTest.kt`; unter
`app/src/androidTest/java/dev/codexpad/` `KeystoreTestRunner.kt` und
`UserInputTestRunner.kt`. Bestehende lokale Transportfixtures geben ihre
Loopback-HTTP-Ausnahme jetzt ausdrücklich an. `README.md` beschreibt das neue Verbindungsverhalten; diese Prüfnotiz ist neu.

## Verhalten

HTTPS bleibt unabhängig vom LAN-HTTP-Schalter zulässig. HTTP für LAN benötigt
eine bewusst aktivierte Option und eine geparste IPv4-Adresse aus RFC1918:
10/8, 172.16/12 oder 192.168/16. Öffentliche IPv4-Adressen und DNS-Namen werden
auch mit aktiviertem Schalter abgelehnt. Die bisherigen Debug-Ausnahmen für
Loopback und den Android-Emulator bleiben verfügbar; Release nutzt sie nicht.
IPv6 erhält keine neue HTTP-Freigabe; HTTPS bleibt unverändert möglich.

Die Option ist standardmäßig aus. Gespeicherte Zustimmung enthält die exakte
normalisierte HTTP-Basisadresse (Schema, Host und Port). Eine Adressänderung
setzt die UI-Zustimmung zurück und verlangt weiterhin erneute Token-Eingabe.
HTTPS speichert keine LAN-HTTP-Zustimmung. AES-256-GCM, Android Keystore,
frische IVs und URL als AAD bleiben erhalten. Es wurden keine echten Tokens
beschafft, ausgelesen oder ausgegeben. Einstellungs-/Tokenprüfungen erfolgten ohne Screenshots
und mit ausgeschlossenen Passwort-/Eingabeinhalten. Ein Screenshot ausschließlich
des geöffneten 19-Byte-Smoke-Artefakts bestätigte dessen Reader-Anzeige.

NSC erlaubt Cleartext auf Plattformebene, da statische Android-XML-Regeln
keine frei konfigurierbaren CIDR-Netze oder Runtime-Zustimmung ausdrücken
können. Der Validator greift beim Laden, Speichern, Testen und zusätzlich im
API-Konstruktor. Die NSC allein autorisiert deshalb keinen HTTP-Endpunkt.
System-Trustanker und OkHttp-TLS-/Hostnameprüfung bleiben die Defaults;
Redirects, Transportretry, Bearer-Auth, Parser, Timeouts, Multipart,
Downloads und History-Reconciliation behalten ihren bisherigen Vertrag.

`ACCESS_LOCAL_NETWORK` steht im Manifest. Ab API 37 wird der aktuelle Grant
vor jeder Anfrage geprüft: explizite LAN-Ziele, DNS-Ergebnisse sowie die
Adresse einer bestehenden OkHttp-Verbindung. Auch lokale IPv6-Adressen
(Site-/Link-local und ULA) werden für die Permission berücksichtigt; daraus
folgt keine IPv6-HTTP-Freigabe. Laufende SSE-/Download-Lesevorgänge prüfen
zusätzlich den Grant. Im Vordergrund und bei Rückkehr wird er erneut geprüft;
Sessions pausieren bei fehlendem Grant. Eine verständliche Meldung und ein
expliziter Berechtigungsbutton erscheinen. Keine automatischen Promptschleifen.
Eine vor dem Versand blockierte Mutation wird nicht als unbekannter Ausgang
markiert. Ältere APIs fragen die neue Runtime-Permission nicht ab.

## Verifikation

- `testDebugUnitTest`: 69 Tests erfasst, 67 bestanden, zwei optionale
  Python-Vertragstests zunächst übersprungen; null Fehler.
- Anschließend beide `PythonContractTest`-Tests mit flüchtigem Testtoken und
  ausschließlich lokalem Handlerfixture bestanden. Insgesamt alle 69 Fälle
  ausgeführt und bestanden; keine Modellaufrufe für diese Vertragstests.
- Neue Tests: RFC1918 einschließlich 172.15/16/31/32, öffentliches HTTP,
  deaktivierte Zustimmung, IPv6-HTTP, URL-Vertrauenswechsel, API <37,
  granted/denied/revoked, aufgelöste private HTTPS-Ziele und zentrale Sperre
  für Health, Listen, History, Turn, Multipart, SSE und Download.
- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`: erfolgreich.
  Lint meldet keinen Fehler. Der Hinweis `InsecureBaseConfiguration` ist die
  bewusst benötigte breite Plattform-Cleartextfreigabe; die App-Policy bleibt
  maßgeblich. Weitere Hinweise betreffen Dependency-Updates und `UseKtx`.
- Reales Tablet: `YLE-W09`, Android 16, API 36; einziges verbundenes ADB-Ziel.
- Debug-App und Test-APK mit `adb install -r` erfolgreich aktualisiert;
  keine Deinstallation, Benutzerkonfiguration erhalten.
  Tatsächlich installiert: `dev.codexpad`, DEBUGGABLE, v0.1.1,
  versionCode 2, targetSdk 37, LAN-Permission deklariert.
- Keystore-Instrumentation `write` und `read` in getrennten Prozessen: PASS.
  Separate Testpreferences/-schlüssel; echte Benutzerzugänge unberührt.
  LAN-Zustimmung, verschlüsselte Persistenz, Neustart, HTTPS, URLwechsel und
  URL-Manipulationsschutz geprüft.
- Vorhandene HTTPS-Konfiguration lud nach dem Update ihre Workspace-Liste.
- Tablet-UI: `http://172.16.16.39:8876` eingetragen; mit Schalter AUS beim
  Verbindungstest abgelehnt. Schalter aktiviert; erneute Token-Eingabe für
  die neue Adresse wird verlangt. Anschließend gab der Benutzer das Pi-Token manuell ein, bestätigte den
  erfolgreichen Verbindungstest und speicherte die neue Konfiguration.
  Workspace, Modellkatalog und Rate-Limit-Status wurden direkt auf dem Tablet
  gegen den Pi geprüft (unter anderem GPT-6.1-Sol und Wochenlimitstatus).

## Pi-Tablet-End-to-End-Abnahme

Alle folgenden Tests erfolgten im vorhandenen `host-smoke`, neuer Thread
`01a101c7-a311-7a40-809e-2e4b7cc92037`:

| Prüfung | Ergebnis |
| --- | --- |
| Verbindung | Pi-URL mit gespeicherter Zustimmung und manuell eingegebenem Token erfolgreich. |
| Datenzugriff | Workspace `host-smoke`, Modellkatalog und Wochenlimitstatus erreichbar. |
| Modellturn | Vollständig abgeschlossen, Antwort `CODEXPAD-PI-TABLET-OK.` sichtbar; das Modell übernahm den Satzpunkt des Testprompts. Keine gewöhnliche Approval. |
| Full Access | `aarch64`, Dienst-UID 999, `sudo -n id -u` = 0, HTTPS-GET = 200. Root-Smoke-Datei und UTF-8-Artefakt jeweils exakt 19 Byte `HOST-FULL-ACCESS-OK`. Keine Approvalkarte oder Unterbrechung. |
| Fachliche Rückfrage | Eigene Rückfragekarte mit ALPHA/BETA und Freitext. Auswahl angeklickt, dann Freitext `TABLET_FREITEXT_OK` über Antworten gesendet. Native Toolantwort und finaler Modelltext bestätigten dieselbe Antwort im selben Turn. |
| Reconnect | Laufender Turn, Hintergrund und fünf Sekunden WLAN-Unterbrechung, danach vollständige History mit Zahlen 1–80 und `CODEXPAD-PI-TABLET-RECONNECT-OK`. |
| Korrigierte Vordergrundprobe | Weiterer längerer Turn; bestehende Activity mit SINGLE_TOP zurückgeholt, während der Turn noch lief. Direkt wieder Live verbunden, gleicher Turn. Vollständige 60-zeilige Antwort mit `CODEXPAD-PI-TABLET-STREAM-OK`, am Ende Bereit. |
| Artefakt | `smoke.txt` erschien als Karte. Authentifizierter Download und SAF-Speichern in `/sdcard/Download/codexpad-pi-tablet-smoke-20261003.txt` erfolgreich. 19 Byte, Hash identisch zum Pi; Öffnen über FileProvider in Honor Dokumente zeigte `HOST-FULL-ACCESS-OK`. |

Mac-Gegenkontrolle: exakt fünf eindeutige `task_started` und dieselben fünf
`task_complete`, keine doppelten Starts. Alle Turnkontexte verwenden
`danger-full-access` und `never`. Die native `request_user_input`-Toolantwort
enthält den Freitext und gehört zum Rückfrage-Turn. Dienste `codexpad` und
Caddy weiterhin aktiv; MainPID unverändert 27020. Keine Secrets ausgelesen.

Turn-IDs:

1. Modell: `01a101c8-d016-7742-bc8d-a8fef8d54357`
2. Full Access: `01a101cb-14ad-7b50-931b-aeb47491c0b6`
3. Rückfrage: `01a101d2-7a1e-7cc0-b5a3-2132c2d5c65a`
4. Reconnect: `01a101d5-dbb8-7b63-ab62-d15ed419af0b`
5. Korrigierte Reconnect-/Streamingprobe: `01a101da-52f5-7721-b6a0-0a8674afb8c4`

Artefakt/Speicherdatei SHA-256:
`1ca9f98c9b415a21875f45b20db20e0056efff6caea6c645be0f7d48b12dfd8c`.

Die mit sudo geschriebene `/var/tmp/codexpad-host-smoke.txt` liegt wegen des
bereits bestehenden systemd-`PrivateTmp` in der Dienst-Mountnamespace.
Lesende Prüfung über `/proc/<MainPID>/root/var/tmp/...`: UID 0 und korrekter
Inhalt. Artefakt unter dem vorgegebenen Ergebnisordner: UID 999 und identischer
Inhalt. Hostkonfiguration unverändert.

Der erste ADB-Aufruf zum Vordergrundwechsel öffnete versehentlich eine zweite
MainActivity. Diese zusätzliche Ansicht wurde geschlossen. Danach wurde die
Probe mit `--activity-single-top` korrekt wiederholt; die ursprüngliche Activity
blieb erhalten, eine einzige MainActivity ist am Ende aktiv, WLAN ist wieder an.
Dies war ein Fehler im Testaufruf. Kein App-/Hostumbau dafür vorgenommen.
Die Tastatur-Autokorrektur veränderte Teile englischer Prüftexte; die sichtbaren
Prompt-/Antwortwerte wurden geprüft, keine fehlenden Marker erfunden.

## Verbleibende Grenze

Die tatsächlichen Android-17-Dialoge einschließlich Ablehnung und Widerruf
können auf diesem API-36-Tablet nicht praktisch abgenommen werden; hierfür
liegen automatisierte Policy-/Transporttests vor. IPv6-HTTP bleibt bewusst
nicht freigegeben. LAN-HTTP bleibt unverschlüsselt und verlangt ausdrücklich
eine vertrauenswürdige private Umgebung. Keine offenen Appfehler aus dieser
Abnahme. Keine Hosttests außerhalb der angeforderten Tablet-Smoke-Abnahme,
keine Hoständerungen, kein Commit/Push während dieser Abnahmephase, kein Merge
und kein Release. Der nachfolgende Dokumentationsabschluss umfasst Commit/Push.


## Erneute Abschlussprüfung vor Commit, 3. Oktober 2026

Die vorhandenen Android-Änderungen wurden für den Dokumentationsabschluss
beibehalten; keine zusätzliche Featurearbeit oder Refaktorierung.

- `CODEXPAD_CONTRACT_URL=http://127.0.0.1:18765 android/build-local.sh :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`:
  BUILD SUCCESSFUL. Fixture mit flüchtigem Testtoken, ohne Codex-/Modell-/VPS-Zugriff.
- JUnit-XML: **69 Tests, 0 Fehler, 0 Fehlschläge, 0 übersprungen**;
  beide Python-Vertragstests in diesem Lauf enthalten.
- Debug-App und Test-APK erfolgreich mit `adb install -r` auf dem verbundenen
  HONOR YLE-W09 aktualisiert; bestehende Benutzerkonfiguration erhalten.
- Keystore-Runner `write` und `read` erneut in getrennten Prozessen: **PASS**.
  Ausschließlich eigene Testpreferences/-schlüssel; keine Benutzerzugänge ausgelesen.
- `lintDebug`: **0 Fehler, 8 Warnungen**, darunter die dokumentierte
  `InsecureBaseConfiguration` sowie Dependency-/KTX-Hinweise.
- `python3 -B -m unittest discover -s server -p 'test_*.py' -v`:
  **39 Tests bestanden**.
- Aktuelles `origin/main` nach Fetch: `90a5bc8448461242e9c168907eb916e63f172045`;
  keine neuen main-Commits seit der Integrationsbasis `f507476`.
- Gesamter Integrationsdiff einschließlich neuer Dateien auf Secretmuster,
  verdächtige Tokenliterale und versehentlich enthaltene Credential-/Builddateien
  geprüft: keine Befunde. APKs, Keystores, lokale Logs und Testcaches bleiben ignoriert.
- Host-/Mac-Nachweis und verbleibende nicht blockierende Prüfungen:
  [Pi-Abnahmestatus](../docs/verification-pi-host-2026-10-03.md).

Keine erneute Pi-Provisionierung oder neue Remote-Modellabnahme in diesem
Abschlusslauf. Historische Tablet-/Hostbelege oben behalten ihren Prüfzeitpunkt.
