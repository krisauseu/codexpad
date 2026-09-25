"""Bounded local experiment, deliberately not a deployable service.

Principal is supplied by the trusted harness, never read from client JSON.
One serial dispatcher; persistent mutation journal; no generic RPC operation.
No approval can expand rights: this spike exposes decline/cancel only.
"""
import hashlib, json, os, re, sqlite3, threading, uuid
from transport import BASE, POLICY

class Denied(Exception): pass
class UnknownOutcome(Exception): pass

FIELDS = {
 "workspaces": set(), "select": {"workspace"},
 "threads": {"workspace"}, "create": {"workspace","operation"},
 "load": {"workspace","thread"}, "state": {"workspace","thread"},
 "history": {"workspace","thread"}, "events": {"workspace","thread"},
 "start": {"workspace","thread","text","operation"},
 "stop": {"workspace","thread","turn","operation"},
 "answer": {"workspace","thread","turn","item","approval","decision","operation"},
 "operation": {"operation"},
}
MUTATIONS = {"create","start","stop","answer"}
UPSTREAM = {"initialize","thread/list","thread/start","thread/read","thread/resume",
            "thread/turns/list","turn/start","turn/interrupt"}
EVENTS = {"thread/status/changed","turn/started","turn/completed","item/started",
          "item/completed","item/agentMessage/delta","turn/diff/updated","error"}
APPROVALS = {"item/commandExecution/requestApproval","item/fileChange/requestApproval"}
ID = re.compile(r"^[A-Za-z0-9_-]{1,100}$")

class Adapter:
 def __init__(self, rpc, db):
  assert os.getuid() != 0
  self.rpc, self.lock = rpc, threading.RLock()
  self.db=sqlite3.connect(db)
  self.db.executescript("""
   PRAGMA journal_mode=WAL; PRAGMA synchronous=FULL;
   CREATE TABLE IF NOT EXISTS bindings(thread TEXT PRIMARY KEY,workspace TEXT);
   CREATE TABLE IF NOT EXISTS ops(device TEXT,id TEXT,hash TEXT,state TEXT,result TEXT,
                                  PRIMARY KEY(device,id));
  """)
  self.registry={"a":str(BASE/"workspace-a"),"b":str(BASE/"workspace-b")}
  self.devices={"device-a":"a","device-b":"b"}
  self.revoked=set()
  self.generation=uuid.uuid4().hex
  self.pending={}
  self.fresh=set()
  self.active={}
  self.items=set()
  self.buffer=[]
  self.offset=0
  self.count=0

 def _binding(self, tid, ws):
  row=self.db.execute("SELECT workspace FROM bindings WHERE thread=?",(tid,)).fetchone()
  if row != (ws,): raise Denied("thread binding")
 def _project_thread(self,t):
  return {k:t[k] for k in ("id","status","historyMode") if k in t}
 def _verify(self,r,ws):
  if (r.get("cwd") != self.registry[ws] or r.get("runtimeWorkspaceRoots") != [self.registry[ws]]
      or r.get("sandbox") != POLICY or r.get("approvalPolicy") != "on-request"
      or r.get("approvalsReviewer") != "user" or r.get("modelProvider") != "spike-disabled"):
   raise Denied("effective policy mismatch")
  if r["thread"].get("cwd") != self.registry[ws]: raise Denied("thread cwd mismatch")
 def _read(self,tid,ws):
  r=self.rpc.call("thread/read",{"threadId":tid,"includeTurns":False})["thread"]
  if r.get("cwd") != self.registry[ws]: raise Denied("backend cwd mismatch")
  return self._project_thread(r)

 def ingest(self,m):
  """Trusted backend side only. Not an operation in the client dispatcher."""
  method=m.get("method"); p=m.get("params",{})
  tid=p.get("threadId")
  row=self.db.execute("SELECT workspace FROM bindings WHERE thread=?",(tid,)).fetchone()
  if not row: return
  if method=="turn/started":
   self.active[tid]=p["turn"]["id"]
  if method=="turn/completed":
   if self.active.get(tid)==p["turn"]["id"]: self.active.pop(tid,None)
   self.pending={k:v for k,v in self.pending.items() if v["thread"]!=tid or v["turn"]!=p["turn"]["id"]}
  if method=="item/started":
   self.items.add((tid,p["turnId"],p["item"]["id"]))
  if method in APPROVALS and "id" in m:
   turn,item=p.get("turnId"),p.get("itemId")
   if self.active.get(tid)!=turn or (tid,turn,item) not in self.items:return
   # De-duplicate redispatch within the same backend generation.
   if any(v["raw"]==m["id"] for v in self.pending.values()):return
   key=uuid.uuid4().hex
   self.pending[key]=dict(raw=m["id"],thread=tid,turn=turn,item=item,workspace=row[0],
                          generation=self.generation,method=method)
  if method=="serverRequest/resolved":
   self.pending={k:v for k,v in self.pending.items() if v["raw"]!=p.get("requestId")}
  if method in EVENTS:
   if len(self.buffer)>=1000: raise Denied("event limit; reconcile required")
   self.buffer.append((row[0],tid,{"type":method,"data":p}))
 def pump(self):
  self.rpc.drain(.01)
  new=self.rpc.messages[self.offset:]
  self.offset=len(self.rpc.messages)
  for m in new:self.ingest(m)
 def invalidate_backend(self):
  self.generation=uuid.uuid4().hex
  self.pending.clear();self.active.clear();self.items.clear();self.buffer.clear();self.fresh.clear()
 def revoke(self,device):
  self.revoked.add(device)

 def handle(self,device,request):
  with self.lock:
   if device not in self.devices or device in self.revoked:raise Denied("principal")
   if not isinstance(request,dict) or set(request)!={"op","args"}:raise Denied("envelope")
   op,p=request["op"],request["args"]
   if not isinstance(op,str) or op not in FIELDS:raise Denied("operation")
   if not isinstance(p,dict) or set(p)!=FIELDS[op]:raise Denied("fields")
   if len(json.dumps(request).encode())>8192:raise Denied("size")
   if any(not isinstance(v,str) for v in p.values()):raise Denied("type")
   for k,v in p.items():
    if k not in {"text","decision"} and not ID.fullmatch(v):raise Denied("identifier")
   if "text" in p and not 1<=len(p["text"])<=4096:raise Denied("text size")
   if "workspace" in p and p["workspace"]!=self.devices[device]:raise Denied("workspace")
   if "thread" in p:self._binding(p["thread"],p["workspace"])
   self.count+=1
   if self.count>2000:raise Denied("session request budget")
   if op=="operation":
    row=self.db.execute("SELECT state,result FROM ops WHERE device=? AND id=?",(device,p["operation"])).fetchone()
    if not row:raise Denied("unknown operation")
    return {"state":row[0],"result":json.loads(row[1]) if row[1] else None}
   # Journal checked before current turn/approval validation: cached decisions never resend.
   digest=hashlib.sha256(json.dumps(request,sort_keys=True).encode()).hexdigest()
   if op in MUTATIONS:
    key=(device,p["operation"])
    row=self.db.execute("SELECT hash,state,result FROM ops WHERE device=? AND id=?",key).fetchone()
    if row:
     if row[0]!=digest:raise Denied("operation reused with different payload")
     if row[1]!="confirmed":raise UnknownOutcome("reconcile; never automatically resend")
     return json.loads(row[2])
    if self.db.execute("SELECT count(*) FROM ops").fetchone()[0]>=256:raise Denied("journal limit")
   self.pump()
   if op=="stop" and self.active.get(p["thread"])!=p["turn"]:raise Denied("inactive or foreign turn")
   if op=="answer":
    a=self.pending.get(p["approval"])
    if not a or any(a[k]!=p[k] for k in ("workspace","thread","turn","item")):raise Denied("approval context")
    if a["generation"]!=self.generation or self.active.get(p["thread"])!=p["turn"]:raise Denied("stale approval")
    if p["decision"] not in {"decline","cancel"}:raise Denied("permission expansion disabled")
   if op in MUTATIONS:
    self.db.execute("INSERT INTO ops VALUES (?,?,?,?,NULL)",(*key,digest,"unknown"))
    self.db.commit() # Unknown is durable BEFORE sending anything upstream.
   try:
    result=self._dispatch(op,p)
    if op in MUTATIONS:
     self.db.execute("UPDATE ops SET state='confirmed',result=? WHERE device=? AND id=?",
                     (json.dumps(result),*key))
     self.db.commit()
    return result
   except Exception:
    # Intentional uncertainty, even if backend may not have executed.
    raise

 def _dispatch(self,op,p):
  ws=p.get("workspace");tid=p.get("thread")
  if op=="workspaces":return {"workspaces":[]} # principal projection in handle
  if op=="select":return {"workspace":ws}
  if op=="threads":
   r=self.rpc.call("thread/list",{"cwd":self.registry[ws],"limit":50})
   result=[]
   for t in r["data"]:
    row=self.db.execute("SELECT workspace FROM bindings WHERE thread=?",(t["id"],)).fetchone()
    if row==(ws,) and t.get("cwd")==self.registry[ws]:result.append(self._project_thread(t))
   return {"threads":result,"truncated":r.get("nextCursor") is not None}
  if op=="create":
   r=self.rpc.call("thread/start",{"cwd":self.registry[ws],"runtimeWorkspaceRoots":[self.registry[ws]],
        "sandbox":"workspace-write","approvalPolicy":"on-request","approvalsReviewer":"user",
        "historyMode":"legacy","model":"spike-no-model","modelProvider":"spike-disabled",
        "ephemeral":False})
   self._verify(r,ws)
   self.db.execute("INSERT INTO bindings VALUES (?,?)",(r["thread"]["id"],ws));self.db.commit()
   self.fresh.add(r["thread"]["id"])
   return self._project_thread(r["thread"])
  if op=="load":
   if tid in self.fresh:return self._read(tid,ws)
   r=self.rpc.call("thread/resume",{"threadId":tid})
   self._verify(r,ws)
   return self._project_thread(r["thread"])
  if op=="state":return self._read(tid,ws)
  if op=="history":
   self._read(tid,ws)
   r=self.rpc.call("thread/read",{"threadId":tid,"includeTurns":True})["thread"]
   if r.get("cwd")!=self.registry[ws]:raise Denied("history cwd")
   if len(json.dumps(r))>262144 or len(r.get("turns",[]))>20:raise Denied("history limit")
   return {"thread":tid,"turns":r.get("turns",[])}
  if op=="start":
   self._read(tid,ws)
   # Revalidate effective policy rather than trusting an old list cwd.
   if tid not in self.fresh:
    r=self.rpc.call("thread/resume",{"threadId":tid});self._verify(r,ws)
   r=self.rpc.call("turn/start",{"threadId":tid,"input":[{"type":"text","text":p["text"],"text_elements":[]}],
                "cwd":self.registry[ws],"sandboxPolicy":POLICY,"approvalPolicy":"on-request",
                "approvalsReviewer":"user","clientUserMessageId":p["operation"]})
   self.fresh.discard(tid)
   self.active[tid]=r["turn"]["id"]
   return r
  if op=="stop":return self.rpc.call("turn/interrupt",{"threadId":tid,"turnId":p["turn"]})
  if op=="events":
   events=[e for w,t,e in self.buffer if w==ws and t==tid]
   self.buffer=[x for x in self.buffer if x[0]!=ws or x[1]!=tid]
   approvals=[dict(approval=k,**{f:v[f] for f in ("thread","turn","item")},decisions=["decline","cancel"])
              for k,v in self.pending.items() if v["workspace"]==ws and v["thread"]==tid]
   return {"events":events,"approvals":approvals}
  if op=="answer":
   a=self.pending.pop(p["approval"])
   self.rpc.send({"id":a["raw"],"result":{"decision":p["decision"]}})
   return {"sent":True,"effect":"unknown; reconcile item and turn"}
  raise Denied("operation")

 # Catalog must be filtered using the authenticated principal.
 _handle=handle
 def handle(self,device,request):
  result=self._handle(device,request)
  if request["op"]=="workspaces":return {"workspaces":[self.devices[device]]}
  return result
