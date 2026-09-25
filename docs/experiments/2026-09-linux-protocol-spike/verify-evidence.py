"""Offline archive verification; no Codex, imports from harness, network or writes."""
from pathlib import Path
import hashlib
import json

root = Path(__file__).resolve().parent
e = json.loads((root / 'evidence.json').read_text())
original = json.loads((root / 'verification.json').read_text())['checks']
files = e['files']
def counts(name):
    return e['source_logs'][name + '.jsonl']['incoming_method_counts']
checks = {
    'baseline_no_tool_turn_completed': 'ACK ORBIT-73-KIESEL' in e['final_texts']['02-baseline'],
    'approved_file_exists_with_exact_content': files['approval-accepted.txt']['text'] == 'ACCEPTED-01\n',
    'declined_file_absent': not files['approval-declined.txt']['exists'],
    'approval_replayed_identically': e['approval_original'] == e['approval_replayed'],
    'reconnected_approved_file_exact': files['approval-reconnected.txt']['text'] == 'RECONNECTED-01\n',
    'marker_after_reconnect': 'ORBIT-73-KIESEL' in e['final_texts']['03-reconnect'],
    'marker_after_server_restart': 'ORBIT-73-KIESEL' in e['final_texts']['05-server-restart'],
    'stream_gap_not_replayed': e['stream_before'] + e['stream_after'] != e['hydrated_text'],
    'full_400_line_response_recovered': e['hydrated_text'].splitlines() == [str(n) for n in range(1, 401)],
    'offline_turn_completed_and_recovered': any(t['status'] == 'completed' and any(i.get('text') == 'OFFLINE-COMPLETED-27' for i in t['items']) for t in e['offline_turns']),
    'offline_turn_completed_notification_not_replayed': counts('07-offline-completed').get('turn/completed', 0) == 0,
    'fs_outside_workspace_in_spike_exact': files['direct-fs.txt']['text'] == 'DIRECT-FS-01\n',
    'fs_watch_event': counts('04-pending-reconnect').get('fs/changed', 0) > 0,
    'server_restart_pending_file_absent': not files['approval-server-restart.txt']['exists'],
    'server_restart_pending_turn_interrupted': e['restart_turns'][0]['status'] == 'interrupted',
    'server_restart_no_approval_replay': not any(k.endswith('/requestApproval') for k in counts('08-pending-after-restart')),
}
assert checks == original and all(checks.values()), 'Archived checks differ or fail'
for entry in json.loads((root / 'provenance.json').read_text()):
    if 'sha256' in entry:
        path = root / ('client/' + Path(entry['archive']).name if '/client/' in entry['archive'] else Path(entry['archive']).name)
        assert hashlib.sha256(path.read_bytes()).hexdigest() == entry['sha256'], path.name
for path, expected in json.loads((root / 'schema-sha256.json').read_text())['hashes'].items():
    if path.endswith('.ts'):
        assert hashlib.sha256((root / 'schema' / Path(path).name).read_bytes()).hexdigest() == expected
print('PASS: 16 archived evidence checks; original client/verification hashes and 8 schema excerpts match.')
