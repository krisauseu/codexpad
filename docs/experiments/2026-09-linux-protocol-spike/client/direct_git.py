from probe import *

s=Server().start();c=None
try:
    c=Client('09-direct-git');c.init()
    # Test the documented host-process API itself; it is not a sandboxed agent tool.
    for handle,argv in [('git-status',['git','status','--porcelain=v1']),('git-diff',['git','diff','--no-index','--','/dev/null','approval-accepted.txt'])]:
        c.rpc('process/spawn',{'processHandle':handle,'command':argv,'cwd':str(WS),'timeoutMs':10000})
        r=c.wait(lambda m:m.get('method')=='process/exited' and m['params']['processHandle']==handle,timeout=15)
        record('direct-git-observations.jsonl',{'test':handle,'result':r})
    for name in ['approval-accepted.txt','approval-reconnected.txt']:
        c.rpc('fs/readFile',{'path':str(WS/name)})
    c.rpc('fs/getMetadata',{'path':str(WS/'approval-declined.txt')})
finally:
    if c and not c.closed:c.close()
    s.stop()
