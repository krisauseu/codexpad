from probe import *
from collections import Counter

def rows(name):
    return [json.loads(x) for x in (ROOT/'logs'/f'{name}.jsonl').read_text().splitlines()]
def msgs(name):return [r['message'] for r in rows(name) if 'message' in r]
def finals(name):
    return [m['params']['item'] for m in msgs(name) if m.get('method')=='item/completed' and m['params']['item']['type']=='agentMessage' and m['params']['item'].get('phase')=='final_answer']
def result(name,rid):return next(m['result'] for m in msgs(name) if m.get('id')==rid and 'result'in m)

baseline=msgs('02-baseline');old=msgs('03-reconnect');new=msgs('04-pending-reconnect')
original=next(m for m in old if m.get('method')=='item/fileChange/requestApproval' and m['id']==2)
replayed=next(m for m in new if m.get('method')=='item/fileChange/requestApproval')
owner_text=''.join(m['params']['delta'] for m in msgs('07-active-owner') if m.get('method')=='item/agentMessage/delta')
new_text=''.join(m['params']['delta'] for m in msgs('07-active-reconnect') if m.get('method')=='item/agentMessage/delta')
offline=result('07-offline-completed','07-offline-completed-2')['initialTurnsPage']['data']
full=next(i['text'] for t in offline for i in t['items'] if i['type']=='agentMessage' and i.get('text','').startswith('1\n2\n'))
checks={
 'baseline_no_tool_turn_completed':any(i['text']=='ACK ORBIT-73-KIESEL' for i in finals('02-baseline')),
 'approved_file_exists_with_exact_content':(WS/'approval-accepted.txt').read_text()=='ACCEPTED-01\n',
 'declined_file_absent':not (WS/'approval-declined.txt').exists(),
 'approval_replayed_identically':original==replayed,
 'reconnected_approved_file_exact':(WS/'approval-reconnected.txt').read_text()=='RECONNECTED-01\n',
 'marker_after_reconnect':any(i['text']=='ORBIT-73-KIESEL' for i in finals('03-reconnect')),
 'marker_after_server_restart':any(i['text']=='ORBIT-73-KIESEL' for i in finals('05-server-restart')),
 'stream_gap_not_replayed':owner_text+new_text!=full,
 'full_400_line_response_recovered':full.splitlines()==[str(n) for n in range(1,401)],
 'offline_turn_completed_and_recovered':any(t['status']=='completed' and any(i.get('text')=='OFFLINE-COMPLETED-27' for i in t['items']) for t in offline),
 'offline_turn_completed_notification_not_replayed':not any(m.get('method')=='turn/completed' for m in msgs('07-offline-completed')),
 'fs_outside_workspace_in_spike_exact':(ROOT/'results/direct-fs.txt').read_text()=='DIRECT-FS-01\n',
 'fs_watch_event':any(m.get('method')=='fs/changed' for m in new),
 'server_restart_pending_file_absent':not (WS/'approval-server-restart.txt').exists(),
 'server_restart_pending_turn_interrupted':result('08-pending-after-restart','08-pending-after-restart-4')['initialTurnsPage']['data'][0]['status']=='interrupted',
 'server_restart_no_approval_replay':not any(m.get('method','').endswith('/requestApproval') for m in msgs('08-pending-after-restart')),
}
summary={'checks':checks,'event_counts':{},'stream':{'before_disconnect':owner_text,'after_reconnect_prefix':new_text[:80],'hydrated_characters':len(full)}}
for p in sorted((ROOT/'logs').glob('*.jsonl')):
    if p.name in ['server.jsonl']:continue
    counts=Counter()
    for n,line in enumerate(p.read_text().splitlines(),1):
        r=json.loads(line);m=r.get('message',{})
        if r.get('direction')=='in' and 'method'in m:counts[m['method']]+=1
    if counts:summary['event_counts'][p.name]=dict(counts)
(ROOT/'results/verification.json').write_text(json.dumps(summary,indent=2))
print(json.dumps(checks,indent=2))
assert all(checks.values()),'Evidence verification failed'
