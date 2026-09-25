"""Collect bounded read-only local evidence; never dump credentials/config wholesale."""
from probe import *
import platform

commands=[['/usr/bin/uname','-a'],[BIN,'--version'],[BIN,'app-server','--help'],[BIN,'app-server','generate-json-schema','--help'],[BIN,'app-server','generate-ts','--help'],['git','status','--short'],['git','diff','--','sample.py'],['git','diff','--no-index','--','/dev/null','approval-accepted.txt'],['git','log','--oneline']]
for cmd in commands:
    r=subprocess.run(cmd,cwd=WS,text=True,capture_output=True)
    record('environment.jsonl',{'command':cmd,'returncode':r.returncode,'stdout':r.stdout,'stderr':r.stderr})
record('environment.jsonl',{'binary_realpath':str(Path('/home/kf/.local/bin/codex').resolve()),'binary_sha256':hashlib.sha256(Path(BIN).read_bytes()).hexdigest(),'os_release':Path('/etc/os-release').read_text(),'python':platform.python_version(),'test_config':(ROOT/'codex-home/config.toml').read_text(),'auth_source_mtime':Path('/home/kf/.codex/auth.json').stat().st_mtime})
manifest={str(p.relative_to(ROOT)):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted((ROOT/'schema').rglob('*')) if p.is_file()}
(ROOT/'results/schema-sha256.json').write_text(json.dumps(manifest,indent=2))
# Report only counts/paths if a secret is detected, never the bytes.
d=json.loads(Path('/home/kf/.codex/auth.json').read_text())
secrets=[v.encode() for v in d.get('tokens',{}).values() if isinstance(v,str) and len(v)>20]
hits=[]
for p in ROOT.rglob('*'):
    if p.is_file() and not p.is_symlink():
        content=p.read_bytes()
        if any(secret in content for secret in secrets):hits.append(str(p.relative_to(ROOT)))
record('security-audit.jsonl',{'credential_value_matches':hits,'isolated_auth_file_exists':(ROOT/'codex-home/auth.json').exists(),'symlinks':[str(p.relative_to(ROOT)) for p in ROOT.rglob('*') if p.is_symlink()]})
print(json.dumps({'credential_value_matches':hits,'schema_files':len(manifest)}))
