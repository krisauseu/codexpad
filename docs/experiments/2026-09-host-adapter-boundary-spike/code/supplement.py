import json,os,re,signal,subprocess,time
from pathlib import Path
import suite
from suite import *
assert os.getuid() == 996, 'refuse any test outside dedicated non-root UID'
suite.ROWS.clear()
# Full method enumeration, with a nonempty assertion (initial regex expected an unquoted key).
f=Fake();a=Adapter(f,RUNTIME/"map.sqlite")
methods=sorted(set(re.findall(r'"method": "([^"]+)"',(RUNTIME/"schema/ClientRequest.ts").read_text())))
assert len(methods)>100
mapping={m:("internal fixed construction" if m in UPSTREAM else "deny") for m in methods}
mapping["thread/turns/list"]="deny in final adapter; diagnostic only"
Path("evidence/capability-map.json").write_text(json.dumps(mapping,indent=2))
for m in methods:denied(a,"device-a",m,{},"MAP-"+m)
check("MAP-COVERAGE",len(methods)==len(mapping),"SCHEMA+ADAPTER",{"methods":len(methods)})
# Malformed envelopes and parameter types never reach the trusted RPC.
for name,req in [("rpc-envelope",{"method":"fs/readFile","params":{}}),
 ("principal-override",{"op":"workspaces","args":{},"device":"device-b"}),
 ("array",[]),("bad-args",{"op":"select","args":{"workspace":["a"]}})]:
 n=len(f.sent)
 try:a.handle("device-a",req);ok=False
 except Denied:ok=len(f.sent)==n
 check("D-ENVELOPE-"+name,ok,"ADAPTER",{"forwarded":len(f.sent)-n})
start=time.monotonic();a.revoke("device-a")
denied(a,"device-a","workspaces",{},"D-REVOKE-NEW")
check("D-REVOKE-TIMING",time.monotonic()-start<5,"FIXTURE",{"seconds":time.monotonic()-start})
a.db.close()

# Real process crash before dispatch / after backend response and before confirmation.
for mode in ("before","after"):
 p=subprocess.run(["/usr/bin/python3",str(RUNTIME/"code/crash_worker.py"),mode],timeout=10)
 detail=json.loads((RUNTIME/("crash-"+mode+".json")).read_text())
 pid=detail["backend_pid"]
 def alive():
  try:return Path("/proc/"+str(pid)+"/stat").read_text().split()[2]!="Z"
  except FileNotFoundError:return False
 deadline=time.monotonic()+3
 while alive() and time.monotonic()<deadline:time.sleep(.05)
 survived=alive()
 if survived:os.kill(pid,signal.SIGTERM)
 fake=Fake();restarted=Adapter(fake,RUNTIME/("crash-"+mode+".sqlite"))
 retry=capture(lambda:call(restarted,"device-a","create",workspace="a",operation="crash-operation"))
 check("F-ADAPTER-CRASH-"+mode,p.returncode==23 and retry.get("error","").startswith("UnknownOutcome") and not fake.sent,
       "REAL process+ADAPTER journal",{"exitcode":p.returncode,"upstream_accepted":detail["result"] is not None,
       "backend_pid":pid,"backend_alive_after_3s":survived,"retry":retry})
 restarted.db.close()

# State/read policy and emitted event filtering against foreign backend objects.
f=Fake();a=Adapter(f,RUNTIME/"extra-fixtures.sqlite")
a.db.execute("INSERT INTO bindings VALUES ('own','a')")
a.db.execute("INSERT INTO bindings VALUES ('foreign','b')");a.db.commit()
a.ingest({"method":"item/agentMessage/delta","params":{"threadId":"foreign","turnId":"foreign-turn",
           "itemId":"foreign-item","delta":"ARTIFICIAL-DEVICE-B-DATA"}})
a.ingest({"method":"item/agentMessage/delta","params":{"threadId":"own","turnId":"own-turn",
           "itemId":"own-item","delta":"ARTIFICIAL-DEVICE-A-DATA"}})
got=call(a,"device-a","events",workspace="a",thread="own")
check("D-FOREIGN-EVENT", "DEVICE-B" not in json.dumps(got) and "DEVICE-A" in json.dumps(got),
      "FIXTURE",got)
try:a._verify({"cwd":str(BASE/"workspace-b")},"a");ok=False
except Denied:ok=True
check("D-POLICY-REVALIDATE",ok,"FIXTURE","mismatched backend context rejected")
# The same per-device mutation ID with a changed text is rejected before forwarding.
a.db.execute("INSERT INTO ops VALUES ('device-a','used','different-hash','confirmed','{}')");a.db.commit()
denied(a,"device-a","start",dict(workspace="a",thread="own",text="changed",operation="used"),"D-MUTATION-PAYLOAD")
a.db.close()

# A real interrupt request/response, followed by a replay, on a credential-failed turn.
# This cannot establish semantics of stopping a live model; keep that explicit.
r=RPC()
try:
 t=r.call("thread/start",{"cwd":str(BASE/"workspace-a"),"historyMode":"legacy"})["thread"]["id"]
 turn=r.call("turn/start",{"threadId":t,"input":[{"type":"text","text":"ARTIFICIAL-STOP","text_elements":[]}]})["turn"]["id"]
 first=capture(lambda:r.call("turn/interrupt",{"threadId":t,"turnId":turn}))
 second=capture(lambda:r.call("turn/interrupt",{"threadId":t,"turnId":turn}))
 r.drain(.1)
 history=capture(lambda:r.call("thread/read",{"threadId":t,"includeTurns":True}))
 record("L-STOP-UPSTREAM","UNRESOLVED","REAL",{"first":first,"replay":second,"history":history,
       "limit":"no active model or tool; cannot claim general idempotency"})
finally:r.close()

# Direct adapter attacks left the predefined target markers unchanged.
expected={
 BASE/"workspace-a/allowed-a.txt":"ALLOWED-A-ARTIFICIAL\n",
 BASE/"workspace-b/allowed-b.txt":"ALLOWED-B-ARTIFICIAL\n",
 BASE/"private-area/dummy-secret.txt":"ARTIFICIAL-MODEL-SECRET-NOT-A-CREDENTIAL\n"}
check("D-TARGET-MARKERS",all(p.read_text()==v for p,v in expected.items()),"OS verification",
      {"sha256":{str(p):digest(p) for p in expected},
       "agent_outside_marker_absent":not (BASE/"private-area/agent-write-marker.txt").exists()})
Path("evidence/supplement.json").write_text(json.dumps(suite.ROWS,indent=2))
print({"methods":len(methods),"rows":len(suite.ROWS),"counts":{s:sum(x["status"]==s for x in suite.ROWS) for s in ["PASS","FAIL","UNRESOLVED"]}})
