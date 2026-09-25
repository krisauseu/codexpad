#!/bin/sh
# Install a complete Codex standalone release, including its sibling helpers.
set -eu

if [ "$(id -u)" -ne 0 ] || [ "$#" -ne 1 ]; then
    echo "Usage (as root): $0 /path/to/standalone/release" >&2
    exit 2
fi

source_dir=$(realpath "$1")
for path in codex-package.json bin/codex bin/codex-code-mode-host; do
    if [ ! -f "$source_dir/$path" ]; then
        echo "Incomplete Codex release: missing $path" >&2
        exit 1
    fi
done

release=$(python3 - "$source_dir/codex-package.json" <<'PY'
import json, re, sys
package = json.load(open(sys.argv[1], encoding="utf-8"))
name = package["version"] + "-" + package["target"]
if not re.fullmatch(r"[A-Za-z0-9._-]+", name):
    raise SystemExit("Invalid Codex release name")
print(name)
PY
)
destination="/usr/local/lib/codex/$release"
if [ ! -e "$destination" ]; then
    install -d -m 0755 "$destination"
    cp -a "$source_dir/." "$destination/"
    chown -R root:root "$destination"
fi

ln -sfn "$destination/bin/codex" /usr/local/bin/codex
ln -sfn "$destination/bin/codex-code-mode-host" /usr/local/bin/codex-code-mode-host
/usr/local/bin/codex --version
