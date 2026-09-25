"""Disposable synchronous JSON-RPC client, WebSocket over Unix socket, stdlib only."""
import base64
import datetime
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import socket
import struct
import subprocess
import threading
import time

ROOT = Path('/home/kf/codexpad-spike')
WS = ROOT / 'workspace'
BIN = '/home/kf/.codex/packages/standalone/releases/0.154.0-x86_64-unknown-linux-musl/bin/codex'
SOCK = ROOT / 'app-server.sock'
SECRETS = []

def clean(v):
    if isinstance(v, dict):
        return {k: '[REDACTED]' if k.lower() in {'accesstoken','access_token','refresh_token','id_token','apikey','openai_api_key','chatgptaccountid','account_id','email','authorization'} else clean(x) for k,x in v.items()}
    if isinstance(v, list): return [clean(x) for x in v]
    if isinstance(v, str):
        for secret in SECRETS:
            if secret: v = v.replace(secret, '[REDACTED]')
        return re.sub(r'eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+', '[REDACTED_JWT]', v)
    return v

def record(name, value):
    with (ROOT/'logs'/name).open('a') as f:
        f.write(json.dumps(clean({'time':datetime.datetime.now(datetime.timezone.utc).isoformat(), **value}),ensure_ascii=False)+'\n')

class Server:
    def start(self):
        (ROOT/'tmp').mkdir(exist_ok=True)
        env = dict(os.environ, CODEX_HOME=str(ROOT/'codex-home'), TMPDIR=str(ROOT/'tmp'), XDG_CACHE_HOME=str(ROOT/'tmp/cache'), XDG_DATA_HOME=str(ROOT/'tmp/data'))
        for k in ['CODEX_THREAD_ID','CODEX_SESSION_ID']: env.pop(k,None)
        self.p = subprocess.Popen([BIN,'app-server','--listen','unix://'+str(SOCK)],cwd=WS,env=env,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        for stream,label in [(self.p.stdout,'stdout'),(self.p.stderr,'stderr')]:
            threading.Thread(target=self.drain,args=(stream,label),daemon=True).start()
        for _ in range(100):
            if self.p.poll() is not None: raise RuntimeError('server exited')
            try:
                with socket.socket(socket.AF_UNIX,socket.SOCK_STREAM) as ready:
                    ready.connect(str(SOCK))
                break
            except (FileNotFoundError,ConnectionRefusedError):time.sleep(.1)
        else:raise TimeoutError('server listener not ready')
        record('lifecycle.jsonl',{'event':'server_start','pid':self.p.pid})
        return self
    def drain(self,stream,label):
        for line in iter(stream.readline,b''): record('server.jsonl',{'stream':label,'line':line.decode(errors='replace').rstrip()})
    def stop(self):
        self.p.terminate()
        try: self.p.wait(timeout=10)
        except subprocess.TimeoutExpired: self.p.kill(); self.p.wait()
        record('lifecycle.jsonl',{'event':'server_stop','pid':self.p.pid,'returncode':self.p.returncode})

class Client:
    def __init__(self,label):
        self.label=label; self.n=0; self.messages=[]; self.cv=threading.Condition(); self.lock=threading.Lock(); self.closed=False
        self.s=socket.socket(socket.AF_UNIX,socket.SOCK_STREAM); self.s.connect(str(SOCK))
        key=base64.b64encode(os.urandom(16)).decode()
        self.s.sendall(('GET / HTTP/1.1\r\nHost: localhost\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Key: '+key+'\r\n\r\n').encode())
        header=b''
        while not header.endswith(b'\r\n\r\n'): header+=self.s.recv(1)
        assert b' 101 ' in header, header
        expected=base64.b64encode(hashlib.sha1((key+'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest())
        assert expected in header
        threading.Thread(target=self.reader,daemon=True).start()
    def exact(self,n):
        b=b''
        while len(b)<n:
            part=self.s.recv(n-len(b))
            if not part: raise EOFError()
            b+=part
        return b
    def frame(self,data,op=1):
        mask=os.urandom(4); n=len(data)
        head=bytes([0x80|op,0x80|n]) if n<126 else bytes([0x80|op,0x80|126])+struct.pack('!H',n) if n<65536 else bytes([0x80|op,0x80|127])+struct.pack('!Q',n)
        with self.lock: self.s.sendall(head+mask+bytes(x^mask[i%4] for i,x in enumerate(data)))
    def reader(self):
        fragments=b''
        try:
            while True:
                a,b=self.exact(2); op=a&15; n=b&127
                if n==126:n=struct.unpack('!H',self.exact(2))[0]
                if n==127:n=struct.unpack('!Q',self.exact(8))[0]
                mask=self.exact(4) if b&128 else None
                data=self.exact(n)
                if mask:data=bytes(x^mask[i%4] for i,x in enumerate(data))
                if op==8:break
                if op==9:self.frame(data,10);continue
                if op==10:continue
                fragments+=data
                if not a&128:continue
                m=json.loads(fragments); fragments=b''
                record(self.label+'.jsonl',{'direction':'in','message':m})
                with self.cv:self.messages.append(m);self.cv.notify_all()
        except (EOFError,OSError):pass
        finally:
            self.closed=True
            with self.cv:self.cv.notify_all()
    def send(self,m):
        record(self.label+'.jsonl',{'direction':'out','message':m})
        self.frame(json.dumps(m).encode())
    def request(self,method,params):
        self.n+=1; rid=self.label+'-'+str(self.n)
        self.send({'id':rid,'method':method,'params':params});return rid
    def wait(self,predicate,timeout=60,start=0):
        end=time.monotonic()+timeout
        with self.cv:
            while True:
                for m in self.messages[start:]:
                    if predicate(m):return m
                start=len(self.messages)
                if self.closed: raise EOFError('connection closed')
                remaining=end-time.monotonic()
                if remaining<=0:raise TimeoutError(self.label)
                self.cv.wait(remaining)
    def response(self,rid,timeout=60):return self.wait(lambda m:m.get('id')==rid and 'method' not in m,timeout)
    def rpc(self,method,params,timeout=60):return self.response(self.request(method,params),timeout)
    def init(self,auth=False):
        r=self.rpc('initialize',{'clientInfo':{'name':'codexpad_spike','version':'0.1'},'capabilities':{'experimentalApi':True,'requestAttestation':False}})
        self.send({'method':'initialized','params':{}})
        if auth:
            d=json.loads(Path('/home/kf/.codex/auth.json').read_text())['tokens']
            SECRETS.extend(v for v in d.values() if isinstance(v,str))
            r=self.rpc('account/login/start',{'type':'chatgptAuthTokens','accessToken':d['access_token'],'chatgptAccountId':d['account_id']})
            assert 'result' in r,r
        return r
    def close(self):
        record(self.label+'.jsonl',{'event':'intentional_transport_disconnect'})
        self.s.shutdown(socket.SHUT_RDWR);self.s.close()

def start_thread(c,**kw):
    p={'cwd':str(WS),'model':'gpt-6-astra','approvalPolicy':'on-request','approvalsReviewer':'user','sandbox':'workspace-write','developerInstructions':'This is an isolated protocol experiment. Only access task files under /home/kf/codexpad-spike. Do not use agents, network, skills, git commits or external services. Follow the exact small user test; do not add extra changes.','historyMode':'legacy',**kw}
    r=c.rpc('thread/start',p)
    if 'error' in r: raise RuntimeError(r)
    return r['result']['thread']['id']

def turn(c,tid,prompt):
    r=c.rpc('turn/start',{'threadId':tid,'input':[{'type':'text','text':prompt}],'effort':'low'})
    if 'error' in r:raise RuntimeError(r)
    return r['result']['turn']['id']

def completed(c,tid,turnid,timeout=150,decision=None):
    end=time.monotonic()+timeout; handled=set()
    while time.monotonic()<end:
        for m in list(c.messages):
            if m.get('method','').endswith('/requestApproval') and m.get('params',{}).get('turnId')==turnid and m['id'] not in handled and decision:
                c.send({'id':m['id'],'result':{'decision':decision}});handled.add(m['id'])
            if m.get('method')=='turn/completed' and m['params']['turn']['id']==turnid:return m
        time.sleep(.1)
    raise TimeoutError('turn '+turnid)

if __name__=='__main__':
    s=Server().start()
    try:
        c=Client('01-basic');c.init(auth=True)
        tid=start_thread(c)
        t=turn(c,tid,'Read sample.py using a tool and explain it in one sentence. Remember this conversation-only marker: ORBIT-73-KIESEL. Do not write it to any file.')
        result=completed(c,tid,t)
        record('observations.jsonl',{'test':'basic','threadId':tid,'turnId':t,'completed':result})
        print(json.dumps({'threadId':tid,'turnId':t,'status':result['params']['turn']['status']}),flush=True)
        (ROOT/'results/state.json').write_text(json.dumps({'basic':tid}))
        c.close()
    finally:s.stop()
