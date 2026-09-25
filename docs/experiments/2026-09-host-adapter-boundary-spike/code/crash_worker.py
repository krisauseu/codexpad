"""Real adapter process exits in a durable journal window; no model invoked."""
import json, os, sys
from transport import RPC,RUNTIME
from adapter import Adapter
mode=sys.argv[1]
rpc=RPC()
a=Adapter(rpc,RUNTIME/("crash-"+mode+".sqlite"))
original=rpc.call
def crash(method,params,**kw):
 if method=="thread/start":
  result=original(method,params,**kw) if mode=="after" else None
  (RUNTIME/("crash-"+mode+".json")).write_text(json.dumps({"backend_pid":rpc.p.pid,"result":result}))
  os._exit(23)
 return original(method,params,**kw)
rpc.call=crash
a.handle("device-a",{"op":"create","args":{"workspace":"a","operation":"crash-operation"}})
raise AssertionError("crash point missed")
