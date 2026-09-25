from probe import *

def obs(test,**kw):
    record('live-observations.jsonl',{'test':test,**kw});print(test,json.dumps(clean(kw))[:1200],flush=True)

s=Server().start();c=None
try:
    c=Client('07-active-owner');c.init(auth=True)
    tid=start_thread(c,historyMode='paginated')
    t=turn(c,tid,'Without tools, output the integers from 1 through 400, one per line. This intentionally long response tests connection loss. Start immediately.')
    c.wait(lambda m:m.get('method')=='item/agentMessage/delta' and m['params']['turnId']==t,timeout=70)
    c.close();time.sleep(.4)
    d=Client('07-active-reconnect');d.init();c=d
    read=c.rpc('thread/read',{'threadId':tid})
    r=c.rpc('thread/resume',{'threadId':tid,'excludeTurns':True})
    obs('active_resume',threadId=tid,turnId=t,read=read,resume=r)
    page=c.rpc('thread/turns/list',{'threadId':tid,'itemsView':'full','limit':10})
    obs('active_turn_page',response=page)
    try:
        end=completed(c,tid,t,timeout=100);obs('active_completed',event=end)
    except TimeoutError:obs('active_completion_not_received')
    c.rpc('thread/turns/list',{'threadId':tid,'itemsView':'full','limit':10})
    c.rpc('thread/items/list',{'threadId':tid,'turnId':t,'limit':100})
    # Complete an entire turn while disconnected: missing terminal event vs hydrated state.
    t2=turn(c,tid,'Reply only OFFLINE-COMPLETED-27. No tools.')
    c.close();time.sleep(15)
    c=Client('07-offline-completed');c.init()
    r=c.rpc('thread/resume',{'threadId':tid,'excludeTurns':True,'initialTurnsPage':{'limit':10,'itemsView':'full'}})
    obs('offline_completed_resume',turnId=t2,response=r)
    c.rpc('thread/items/list',{'threadId':tid,'turnId':t2,'limit':100})
    time.sleep(2)
    obs('replayed_methods',methods=[m.get('method') for m in c.messages if 'method'in m])
    (ROOT/'results/live-state.json').write_text(json.dumps({'threadId':tid,'streamingTurnId':t,'offlineTurnId':t2},indent=2))
finally:
    if c and not c.closed:c.close()
    s.stop()
