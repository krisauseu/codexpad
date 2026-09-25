from probe import *

def obs(test,**kw):
    record('pending-restart-observations.jsonl',{'test':test,**kw});print(test,json.dumps(clean(kw))[:1500],flush=True)

s=Server().start();c=None
try:
    c=Client('08-pending-before-restart');c.init(auth=True)
    tid=start_thread(c,sandbox='read-only',historyMode='paginated')
    t=turn(c,tid,'Use apply_patch to create approval-server-restart.txt containing RESTART-PENDING-01. Request normal file-change approval. Do not use shell commands. If denied, stop.')
    req=c.wait(lambda m:m.get('method')=='item/fileChange/requestApproval' and m['params']['turnId']==t,timeout=70)
    obs('pending_before_restart',threadId=tid,turnId=t,request=req)
    s.stop();c.s.close();s=Server().start()
    c=Client('08-pending-after-restart');c.init(auth=True)
    c.rpc('thread/read',{'threadId':tid})
    r=c.rpc('thread/resume',{'threadId':tid,'excludeTurns':True,'initialTurnsPage':{'limit':10,'itemsView':'full'}})
    obs('resume_after_restart',response=r)
    c.rpc('thread/items/list',{'threadId':tid,'turnId':t,'limit':100})
    time.sleep(3)
    requests=[m for m in c.messages if m.get('method','').endswith('/requestApproval')]
    obs('pending_after_restart',requests=requests,file_exists=(WS/'approval-server-restart.txt').exists())
    # An old request id may collide after restart: never send a stale acceptance.
    t2=turn(c,tid,'Do not create any file or retry any tool. In one sentence report whether the earlier requested file change completed, based on the conversation. No tools.')
    obs('followup_completed',event=completed(c,tid,t2))
    c.rpc('thread/turns/list',{'threadId':tid,'itemsView':'full','limit':10})
    (ROOT/'results/pending-restart-state.json').write_text(json.dumps({'threadId':tid,'pendingTurnId':t,'requestId':req['id'],'followupTurnId':t2},indent=2))
finally:
    if c and not c.closed:c.close()
    s.stop()
