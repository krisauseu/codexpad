# AppArmor-gezielter Non-root-Sandbox-Probe (2026-09-24)

## Rollback vor Host-Änderungen

Vor Beginn existierten weder `/opt/codexpad-sandbox-test` noch
`/etc/apparmor.d/opt.codexpad-sandbox-test.bwrap`. Es werden keine Sysctls oder
sonstigen Systemkonfigurationen geändert. Nach jedem Versuch lässt sich der
ursprüngliche Hostzustand mit folgenden Befehlen als root wiederherstellen:

```sh
apparmor_parser -R /etc/apparmor.d/opt.codexpad-sandbox-test.bwrap
unlink /etc/apparmor.d/opt.codexpad-sandbox-test.bwrap
unlink /opt/codexpad-sandbox-test/bin/codex-resources/bwrap
unlink /opt/codexpad-sandbox-test/bin/codex
rmdir /opt/codexpad-sandbox-test/bin/codex-resources \
  /opt/codexpad-sandbox-test/bin /opt/codexpad-sandbox-test
systemctl reload apparmor.service
```

Falls das Profil noch nicht angelegt oder geladen wurde, die erste Zeile
überspringen. Die Testinstallation enthält keine Credentials und kein
Codex-HOME. Der ursprüngliche Cross-Workspace-Spike bleibt unberührt.

## Ausgangszustand

- Ubuntu 24.04.5; AppArmor geladen; `unprivileged_userns` in enforce.
- `kernel.apparmor_restrict_unprivileged_userns = 1`.
- Gesicherter Startfehler: `apparmor="DENIED" operation="open"`
  `profile="unprivileged_userns" comm="bwrap"`
  `name="proc/15127/uid_map" requested_mask="wr" denied_mask="wr"`.
- Separater `unshare`-Test: `capability=21 capname="sys_admin"` verweigert.

## Native Artefakte und Testinstallation

`codexpad-test` benutzte das npm-Symlink
`/home/codexpad-test/.npm-global/bin/codex` auf den Node-Wrapper
`.../@openai/codex/bin/codex.js`. Dieser startet das native ELF aus dem
Plattformpaket. Die direkt ausgeführte native Binary meldete
`codex-cli 0.156.1`. Das gebündelte bwrap meldete bei `--version` nur
`bubblewrap built for Codex`; ein eigenes numerisches bwrap-Release ist aus
diesem Artefakt nicht belegbar. Paketversion und SHA-256 identifizieren die
Kopie eindeutig.

| Artefakt | Quelle | root-owned Testziel | SHA-256 |
| --- | --- | --- | --- |
| Codex | `/home/codexpad-test/.npm-global/lib/node_modules/@openai/codex/node_modules/@openai/codex-linux-x64/vendor/x86_64-unknown-linux-musl/bin/codex` | `/opt/codexpad-sandbox-test/bin/codex` | `0b2e9301d6100dddda3b9d5c80ebaeaa3a2f1962388f2f36f6b96a9f08b1f33f` |
| bwrap | `/home/codexpad-test/.npm-global/lib/node_modules/@openai/codex/node_modules/@openai/codex-linux-x64/vendor/x86_64-unknown-linux-musl/codex-resources/bwrap` | `/opt/codexpad-sandbox-test/bin/codex-resources/bwrap` | `77360cb751ccedc5971391444ac86a8a33c15b04d6b4a6fe45f5d25496e62c4c` |

Beide Quellen gehörten `codexpad-test:codexpad-test` und hatten Modus `0775`;
der Symlink und seine Eltern waren durch diesen Benutzer änderbar. Die zwei
Kopien und sämtliche Test-Elternverzeichnisse unter `/opt` gehörten
`root:root` und hatten Modus `0755`. Die Profil-Datei gehörte `root:root`
mit `0644`. Der Benutzer konnte die Testkopien direkt ausführen
(`codex --version` erfolgreich), aber nicht verändern. Es wurden keine
Credentials, kein HOME, keine Wrapper oder sonstige Paketdateien kopiert.

## Profil und schrittweiser Befund

Die vollständige zuletzt geladene Fassung liegt in
`2026-09-apparmor-nonroot-probe.profile`. Sie war ein enforce-Profil nur für
`/opt/codexpad-sandbox-test/bin/codex-resources/bwrap`, mit
`flags=(attach_disconnected)` und `userns,`; das globale
`unprivileged_userns`-Profil wurde nicht verändert. Die folgenden Freigaben
wurden jeweils einzeln nach erneutem Test und dem genannten Fehler ergänzt:

| Freigabe | Konkreter Anlass |
| --- | --- |
| `/proc/sys/kernel/overflowuid r` | bwrap konnte `overflowuid` nicht lesen. |
| `/proc/sys/kernel/overflowgid r` | nächster Lese-Denial auf `overflowgid`. |
| `capability sys_admin` | Audit-Denial beim Erzeugen des User-Namespace. |
| `network unix dgram` | Audit-Denial `create`, AF_UNIX/DGRAM bei Loopback-Vorbereitung. |
| `network netlink raw` | Audit-Denial `create`, NETLINK_ROUTE-Socket. |
| `capability net_admin` | `RTM_NEWADDR` schlug fehl; Audit-Denial `net_admin`. |
| `/proc/[0-9]*/uid_map rw` | Audit-Denial `wr` beim UID-Mapping. |
| `/proc/[0-9]*/setgroups rw` | Audit-Denial `wr` für `setgroups`. |
| `/proc/[0-9]*/gid_map rw` | Audit-Denial `wr` beim GID-Mapping. |
| `mount options=(rw silent rslave) /` | bwrap konnte `/` nicht `rslave` setzen; Audit zeigte auch `silent`. |
| `mount fstype=tmpfs ... -> /tmp/` | tmpfs-Mount auf `/tmp/` verweigert. |
| `/tmp/newroot/ w` | `mkdir` für `newroot` verweigert. |
| `mount options=(rw rbind) /tmp/newroot/ -> /tmp/newroot/` | `newroot`-Bind verweigert. |
| `/tmp/oldroot/ w` | `mkdir` für `oldroot` verweigert. |
| `pivot_root oldroot=/tmp/oldroot/ /tmp/` | erstes `pivot_root` verweigert. |
| `mount fstype=tmpfs ... -> /newroot/` | tmpfs-Mount auf `/newroot/` verweigert. |
| `mount options=(rw rbind) /oldroot/ -> /newroot/` | Host-Root-Bind im neuen Mount-Namespace verweigert. |
| `/proc/[0-9]*/mountinfo r` | bwrap konnte `/proc/self/mountinfo` nicht lesen. |
| `mount options=(ro nosuid nodev remount bind silent relatime) -> /newroot/` | Read-only-Remount von `/newroot/` verweigert. |
| `/newroot/dev/ w` und `mount fstype=tmpfs ... -> /newroot/dev/` | zuerst `mkdir`, dann tmpfs-Mount für das neue `/dev` verweigert. |
| `/newroot/dev/{null,zero,full,random,urandom,tty} w` | jeweils eigener Create-Denial für die sechs Geräte-Platzhalter. |
| `mount options=(rw rbind) /oldroot/dev/{null,zero,full,random,urandom,tty} -> /newroot/dev/{...}` | jeweils eigener Bind-Mount-Denial; Audit verlangte `rbind`. Die tatsächlichen Profilregeln sind sechs einzelne Pfade, keine Brace-Wildcard. |
| `/newroot/dev/{stdin,stdout,stderr,fd,core} w` | jeweils eigener Symlink-Create-Denial. |
| `/newroot/dev/shm/ w`, `/newroot/dev/pts/ w` | jeweilige `mkdir`-Denials. |
| `mount fstype=devpts ... -> /newroot/dev/pts/` | devpts-Mount verweigert. |
| `/newroot/dev/ptmx w` | Symlink-Create-Denial. |
| `mount fstype=tmpfs ... -> /newroot/tmp/codex-daemon-1000/` | Codex-Daemon-tmpfs-Mount verweigert. |
| `mount options=(ro nosuid nodev remount bind silent relatime) -> /newroot/tmp/codex-daemon-1000/` | Read-only-Remount dort verweigert. |
| `mount fstype=proc ... -> /newroot/proc/` | proc-Mount verweigert. |
| `mount options=(rw silent rprivate) /oldroot/` | bwrap konnte alte Root nicht `rprivate` setzen. |
| `umount /oldroot/` | Unmount der alten Root verweigert. |
| `/ r` | bwrap konnte `/` nicht öffnen. |

`deny capability setpcap`, `deny capability sys_ptrace` und
`deny /proc/[0-9]*/fd/ r` wurden zusätzlich als explizite Nichtfreigaben
eingetragen. Diese Denials waren bis dahin nicht startkritisch. Ein zunächst
fälschlich auf `/newroot/dev/devpts/` gerichteter Pfad wurde anhand des
Auditnamens zu `/newroot/dev/pts/` korrigiert. Eine anfängliche
`mount ... (rw rslave)`-Regel wurde um das protokollierte `silent` ergänzt.

## Letzter Minimaltest und Stop-Bedingung

Als `codexpad-test` im Testworkspace wurde ausschließlich ausgeführt:

```sh
env HOME=/home/codexpad-test PATH=/usr/bin:/bin \
  /opt/codexpad-sandbox-test/bin/codex sandbox linux -- /bin/pwd
```

Ergebnis: Exit 1, `bwrap: pivot_root(/newroot): Permission denied`.
`/bin/pwd` wurde nicht ausgeführt. Daher wurde auch kein Dateizugriff im
Workspace versucht. Kein Cross-Workspace-Test fand statt.

Nach `net_admin` verschwand der Loopback-Fehler und der Ablauf erreichte die
UID/GID-Mappings sowie zahlreiche Mounts; das belegt die erfolgreiche
Einrichtung des Loopback-Schritts im Versuch, nicht einen vollständig
gestarteten Sandbox-Netzwerkpfad. Die Audit-Spur protokolliert weiter
Read-only-Remount-Denials auf mehreren Host-Mounts unter `/newroot/dev/` und
`/newroot/run/`, einen `mkdir`-Denial auf `/newroot/bin/` sowie
`dac_read_search` und `dac_override`. Die dynamischen Docker- und
Run-Mounts des Hosts machen eine reproduzierbare, pfadgenaue Freigabe des
zweiten bwrap-Pfads schwierig; AppArmor unterdrückte unter der Last zudem
Audit-Callbacks. Die Sicherheitswirkung weiterer Regeln wäre so nicht mehr
zuverlässig nachvollziehbar. Die beiden DAC-Capabilities wurden nicht
freigegeben. Dies erfüllt die vorgegebene Stop-Bedingung.

Die letzten 100 einschlägigen Kernel-Auditzeilen stehen in
`2026-09-apparmor-nonroot-probe.audit.log`. Dieses Log ist ein Ausschnitt;
wegen unterdrückter Callbacks keine vollständige Denial-Liste.

## Endzustand

Der dokumentierte Rollback wurde ausgeführt: Profil entladen und entfernt,
Testinstallation entfernt, AppArmor-Konfiguration neu geladen. Beide
Host-Testpfade sind nicht mehr vorhanden. AppArmor ist weiterhin aktiv,
`unprivileged_userns` weiterhin geladen und
`kernel.apparmor_restrict_unprivileged_userns = 1`. Die ursprüngliche
Non-root-npm-Installation blieb unverändert.

**Ergebnis: weiterhin BLOCKED.**
