from probe import *

state=json.loads((ROOT/'results/state.json').read_text())
def save(**kw):
    state.update(kw);(ROOT/'results/state.json').write_text(json.dumps(state,indent=2))
def obs(test,**kw):
    record('observations.jsonl',{'test':test,**kw});print(test,json.dumps(clean(kw))[:900],flush=True)
def finish(c,tid,t,decision=None):
    r=completed(c,tid,t,decision=decision);obs('turn_complete',threadId=tid,turnId=t,status=r['params']['turn']['status']);return r
def reconnect(c,label,tid):
    c.close();time.sleep(1)
    d=Client(label);d.init()
    d.rpc('thread/list',{'limit':30})
    d.rpc('thread/read',{'threadId':tid,'includeTurns':True})
    r=d.rpc('thread/resume',{'threadId':tid})
    obs('resume',label=label,result=r)
    return d

s=Server().start();c=None
try:
    c=Client('02-baseline');c.init(auth=True)
    # An actual sandboxed operation, with no privilege fallback.
    r=c.rpc('command/exec',{'command':['/bin/cat','sample.py'],'cwd':str(WS),'sandboxPolicy':{'type':'readOnly','networkAccess':False}})
    obs('sandbox_read',response=r)
    tid=start_thread(c);save(main=tid)
    t=turn(c,tid,'Remember the conversation-only marker ORBIT-73-KIESEL. Reply exactly ACK ORBIT-73-KIESEL. Do not use tools.');finish(c,tid,t)
    # Native file change in ordinary workspace-write policy.
    t=turn(c,tid,'Use apply_patch to add exactly the comment # CODEXPAD-SPIKE-EDIT-01 at the end of sample.py. Do not run shell commands or request elevated permissions. If the tool fails, report failure and stop.');finish(c,tid,t)
    c.rpc('fs/readFile',{'path':str(WS/'sample.py')})
    diff=subprocess.run(['git','diff','--','sample.py'],cwd=WS,capture_output=True,text=True)
    record('git-diff.jsonl',{'command':['git','diff','--','sample.py'],'stdout':diff.stdout,'returncode':diff.returncode})
    # Same-server completed-thread recovery and contextual follow-up.
    c=reconnect(c,'03-reconnect',tid)
    t=turn(c,tid,'What was the conversation-only marker I asked you to remember? Reply with that marker only. Do not use tools.');finish(c,tid,t)
    save(main_turn=t)
    # Read-only thread forces a deliberate file-change approval, not a sandbox failure fallback.
    at=start_thread(c,sandbox='read-only');save(approval=at)
    t=turn(c,at,'Use apply_patch to create approval-accepted.txt containing ACCEPTED-01. This is a harmless controlled approval test. Ask for the normal file-change approval if required; do not use shell commands. If denied, stop.');save(approval_turn=t)
    try:
        req=c.wait(lambda m:m.get('method')=='item/fileChange/requestApproval' and m['params']['turnId']==t,timeout=50)
        obs('approval_accept_request',request=req)
        c.send({'id':req['id'],'result':{'decision':'accept'}})
        finish(c,at,t)
    except TimeoutError:
        obs('file_approval_unavailable',turnId=t)
        c.rpc('turn/interrupt',{'threadId':at,'turnId':t})
    # Denial using a second patch.
    t=turn(c,at,'Use apply_patch to create approval-declined.txt containing DECLINED-01. Request the normal approval if needed. Do not use shell commands. If declined, do not retry; finish by reporting the denial.')
    try:
        req=c.wait(lambda m:m.get('method')=='item/fileChange/requestApproval' and m['params']['turnId']==t,timeout=50)
        c.send({'id':req['id'],'result':{'decision':'decline'}});finish(c,at,t)
        obs('approval_decline',file_exists=(WS/'approval-declined.txt').exists())
    except TimeoutError:
        obs('file_decline_unavailable',turnId=t);c.rpc('turn/interrupt',{'threadId':at,'turnId':t})
    # Deliberately disconnect the only client with a pending approval.
    t=turn(c,at,'Use apply_patch to create approval-reconnected.txt containing RECONNECTED-01. Request the normal approval if needed. Do not use shell commands. If declined, stop.')
    try:
        req=c.wait(lambda m:m.get('method')=='item/fileChange/requestApproval' and m['params']['turnId']==t,timeout=50)
        save(pending_request=req,pending_turn=t)
        c=reconnect(c,'04-pending-reconnect',at)
        try:
            replay=c.wait(lambda m:m.get('method')=='item/fileChange/requestApproval',timeout=8)
            obs('approval_replay',original=req,replayed=replay)
            c.send({'id':replay['id'],'result':{'decision':'accept'}})
            finish(c,at,t)
        except TimeoutError:
            obs('no_approval_replay');c.rpc('turn/interrupt',{'threadId':at,'turnId':t})
    except TimeoutError:
        obs('pending_approval_unavailable',turnId=t);c.rpc('turn/interrupt',{'threadId':at,'turnId':t})
    # Direct host FS operation with a read-only thread present, outside workspace but within spike.
    c.rpc('fs/watch',{'watchId':'watch-probe','path':str(WS)})
    c.rpc('fs/writeFile',{'path':str(ROOT/'results/direct-fs.txt'),'dataBase64':base64.b64encode(b'DIRECT-FS-01\n').decode()})
    c.rpc('fs/readFile',{'path':str(ROOT/'results/direct-fs.txt')})
    c.rpc('fs/readDirectory',{'path':str(WS)})
    c.rpc('fs/getMetadata',{'path':str(WS/'sample.py')})
    c.rpc('fs/writeFile',{'path':str(WS/'fs-watch.txt'),'dataBase64':base64.b64encode(b'WATCH-01\n').decode()})
    time.sleep(1)
    c.rpc('fs/unwatch',{'watchId':'watch-probe'})
    # Server restart, persistent history and contextual recall.
    c.close();s.stop();s=Server().start()
    c=Client('05-server-restart');c.init(auth=True)
    c.rpc('thread/list',{'limit':30})
    c.rpc('thread/read',{'threadId':tid,'includeTurns':True})
    c.rpc('thread/resume',{'threadId':tid})
    t=turn(c,tid,'Repeat the conversation-only marker from our earlier conversation, exactly. No tools.');finish(c,tid,t)
    save(suite_done=True)
finally:
    if c and not c.closed:c.close()
    s.stop()
