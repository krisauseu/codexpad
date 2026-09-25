import json,uuid
from pathlib import Path
import suite
from suite import *
assert os.getuid()==996
suite.ROWS.clear()
run=uuid.uuid4().hex
r=RPC();a=Adapter(r,RUNTIME/("addendum-"+run+".sqlite"))
try:
 t=call(a,"device-a","create",workspace="a",operation="create")["id"]
 for op,p in [("create",dict(workspace="a",operation="bad-create",cwd=str(BASE/"workspace-b"))),
              ("load",dict(workspace="a",thread=t,path=str(BASE/"private-area"))),
              ("history",dict(workspace="a",thread=t,cursor="foreign"))]:
  denied(a,"device-a",op,p,"D-EXTRA-"+op)
 old=r.call;seen={}
 def drop(m,p,**kw):
  result=old(m,p,**kw)
  if m=="turn/start":seen.update(result);raise ConnectionError("lost after accepted turn/start")
  return result
 r.call=drop
 p=dict(workspace="a",thread=t,text="ARTIFICIAL-LOST-TURN",operation="lost-turn")
 capture(lambda:call(a,"device-a","start",**p))
 r.call=old;r.drain(.1);n=len(r.sent)
 retry=capture(lambda:call(a,"device-a","start",**p))
 check("L-TURN-BACKEND",retry.get("error","").startswith("UnknownOutcome") and len(r.sent)==n,
       "REAL+FAULT",{"executed_turn":seen["turn"]["id"],"retry":retry,"forwarded":len(r.sent)-n,
                    "history":call(a,"device-a","history",workspace="a",thread=t)})
finally:r.close();a.db.close()
f=Fake();a=Adapter(f,RUNTIME/("addendum-fixtures-"+run+".sqlite"))
a.db.execute("INSERT INTO bindings VALUES ('a-thread','a')");a.db.commit()
a.active["a-thread"]="a-turn"
old=f.call
def lost(m,p,**kw):old(m,p,**kw);raise ConnectionError("stop ack lost")
f.call=lost
p=dict(workspace="a",thread="a-thread",turn="a-turn",operation="lost-stop")
capture(lambda:call(a,"device-a","stop",**p));f.call=old;n=len(f.sent)
retry=capture(lambda:call(a,"device-a","stop",**p))
check("L-STOP-BACKEND",retry.get("error","").startswith("UnknownOutcome") and len(f.sent)==n,"FIXTURE",retry)
a.ingest({"method":"item/started","params":{"threadId":"a-thread","turnId":"a-turn","item":{"id":"item"}}})
a.ingest({"id":77,"method":"item/commandExecution/requestApproval","params":{"threadId":"a-thread","turnId":"a-turn","itemId":"item"}})
key=next(iter(a.pending))
p=dict(workspace="a",thread="a-thread",turn="a-turn",item="item",approval=key,decision="accept",operation="accept-command")
denied(a,"device-a","answer",p,"A-COMMAND-ACCEPT")
a.ingest({"method":"serverRequest/resolved","params":{"threadId":"a-thread","requestId":77}})
denied(a,"device-a","answer",dict(p,decision="decline",operation="after-resolved"),"A-RESOLVED")
# No generic server-request response or permissions grant operation exists.
denied(a,"device-a","answer",dict(p,decision="decline",operation="extra",permissions="all"),"A-EXTRA-PERMISSIONS")
a.db.close()
Path("evidence/addendum.json").write_text(json.dumps(suite.ROWS,indent=2))
print({"rows":len(suite.ROWS),"statuses":[r["status"] for r in suite.ROWS]})
