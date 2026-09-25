"""Offline artifact checks only; no Codex invocation or writes."""
import hashlib,json,re,sys
from collections import Counter
from pathlib import Path
# Run with the experiment directory as cwd (works with an inherited directory fd).
root=Path(".")
sets={name:json.loads((root/"evidence"/(name+".json")).read_text())
      for name in ("results","supplement","addendum")}
assert len(sets["results"])==258
assert len(sets["supplement"])==181
assert len(sets["addendum"])==8
rows=sum(sets.values(),[])
assert Counter(r["status"] for r in rows)=={"PASS":427,"FAIL":9,"UNRESOLVED":11}
assert all(r["status"] in {"PASS","FAIL","UNRESOLVED"} and r["layer"] for r in rows)
env=json.loads((root/"evidence/environment.json").read_text())
assert env["uid"]==996 and env["gid"]==986 and env["groups"]==[]
assert env["codex"]=="codex-cli 0.155.0"
assert env["security"]["NoNewPrivs"]=="1"
assert all(int(v,16)==0 for k,v in env["security"].items() if k.startswith("Cap"))
mapping=json.loads((root/"evidence/capability-map.json").read_text())
assert len(mapping)==167
primary={r["id"]:r for r in sets["results"]}
extra={r["id"]:r for r in sets["supplement"]}
for m in mapping:
 assert primary["D-RPC-"+m]["status"]=="PASS"
 assert primary["D-RPC-"+m]["detail"]["forwarded"]==0
 assert extra["MAP-"+m]["status"]=="PASS"
for i in range(1,7):
 assert primary["I-PROMPT-"+str(i)]["status"]=="UNRESOLVED"
 h=primary["I-PROMPT-"+str(i)]["detail"]["history"]
 assert all(t["status"]=="failed" for t in h["turns"])
 assert all("CODEXPAD_SPIKE_INTENTIONALLY_UNSET" in t["error"]["message"] for t in h["turns"])
for kind in ("THREAD","TURN"):
 ids=primary["L-UPSTREAM-"+kind+"-ID"]["detail"]["ids"]
 assert len(ids)==2 and len(set(ids))==2
for mode in ("before","after"):
 r=extra["F-ADAPTER-CRASH-"+mode]
 assert r["status"]=="PASS" and r["detail"]["exitcode"]==23
 assert not r["detail"]["backend_alive_after_3s"]
matrix=(root/"test-matrix.md").read_text()
assert len(re.findall(r"^\| RT-\d+",matrix,re.M))==16
manifest=json.loads((root/"evidence/artifact-hashes.json").read_text())
for file,expected in manifest.items():
 assert hashlib.sha256((root/file).read_bytes()).hexdigest()==expected,file
patterns=[rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
          rb"\beyJ[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\.[A-Za-z0-9_-]{15,}\b",
          rb"\bsk-(?:proj-)?[A-Za-z0-9_-]{24,}\b"]
for p in root.rglob("*"):
 if not p.is_file():continue
 assert p.suffix not in {".sqlite",".db",".wal"},str(p)
 data=p.read_bytes()
 assert not any(re.search(pattern,data) for pattern in patterns),str(p)
 assert len(data)<500_000,(str(p),"unexpectedly large artifact")
print("PASS: 447 evidence rows; 167-method denial coverage twice; non-root identity;")
print("credential-blocked agent cases; duplicate IDs; crash journals; 16 RT criteria;")
print(str(len(manifest))+" artifact hashes and limited secret-pattern scan.")
