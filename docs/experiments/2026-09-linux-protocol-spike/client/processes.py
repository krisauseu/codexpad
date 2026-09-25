from probe import *

def obs(test,**kw):
    record('process-observations.jsonl',{'test':test,**kw});print(test,json.dumps(kw),flush=True)
def alive(pid):
    p=Path('/proc')/str(pid)/'stat'
    return p.exists() and p.read_text().split(') ')[1][0]!='Z'
def spawn(c,handle,tty=False):
    code='import os,time; print("SPIKE_PID="+str(os.getpid()),flush=True); time.sleep(90)'
    r=c.rpc('process/spawn',{'processHandle':handle,'command':['/usr/bin/python3','-u','-c',code],'cwd':str(WS),'tty':tty,'streamStdin':True,'streamStdoutStderr':True,'timeoutMs':100000})
    if 'error' in r:obs('spawn_error',response=r);return None
    m=c.wait(lambda m:m.get('method')=='process/outputDelta' and m['params']['processHandle']==handle,timeout=10)
    output=base64.b64decode(m['params']['deltaBase64']).decode()
    pid=int(re.search(r'SPIKE_PID=(\d+)',output)[1]);obs('spawn',handle=handle,pid=pid,tty=tty,alive=alive(pid));return pid

s=Server().start();c=None;owned=[]
try:
    c=Client('06-process-owner');c.init()
    # Keep command/exec's sandbox intact: failures are evidence, not grounds for bypass.
    for tty in [False,True]:
        r=c.rpc('command/exec',{'processId':'exec-'+str(tty),'command':['/bin/sleep','30'],'cwd':str(WS),'tty':tty,'streamStdin':True,'streamStdoutStderr':True,'timeoutMs':35000})
        obs('command_exec',tty=tty,response=r)
    for tty in [False,True]:
        handle='disconnect-'+str(tty);pid=spawn(c,handle,tty)
        if pid is None:continue
        owned.append(pid)
        c.close();time.sleep(1)
        obs('after_disconnect',handle=handle,pid=pid,alive=alive(pid),server_alive=s.p.poll() is None)
        c=Client('06-reconnect-'+str(tty));c.init()
        r=c.rpc('process/writeStdin',{'processHandle':handle,'deltaBase64':base64.b64encode(b'\n').decode()})
        obs('reattach_attempt',response=r)
        c.rpc('command/exec/write',{'processId':'exec-'+str(tty),'deltaBase64':''})
    pid=spawn(c,'restart-pty',True)
    if pid:owned.append(pid)
    # Server terminates while client and PTY still connected.
    s.stop();time.sleep(1)
    obs('after_server_stop',pid=pid,alive=alive(pid) if pid else None)
    c.s.close();s=Server().start();c=Client('06-after-server-restart');c.init()
    obs('restart_reattach',response=c.rpc('process/writeStdin',{'processHandle':'restart-pty','deltaBase64':''}))
finally:
    if c and not c.closed:c.close()
    s.stop()
    # Only positively identified test children, never broad process matching.
    for pid in owned:
        if alive(pid):
            cmd=(Path('/proc')/str(pid)/'cmdline').read_bytes()
            if b'SPIKE_PID=' in cmd:
                os.kill(pid,15);obs('cleanup_test_child',pid=pid)
