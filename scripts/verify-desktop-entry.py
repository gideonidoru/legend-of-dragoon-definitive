#!/usr/bin/env python3
"""Exercise the exact saved shortcut with changing current releases and real hashes.
No network, GUI, personal cache or game is touched. Native KDE trust UX needs a Deck.
"""
from pathlib import Path
import argparse
import ctypes
import ctypes.util
import json
import os
import subprocess
import tempfile
import time
from desktop_bootstrap import LATEST, desktop_entry

repo=Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--entries-dir',type=Path,default=repo/'delivery')
entries=parser.parse_args().entries_dir;text=(entries/'Install-Definitive.desktop').read_text()
assert text==desktop_entry(), 'Shortcut must be the reviewed stable latest-release bootstrap'
command=next(line[5:] for line in text.splitlines() if line.startswith('Exec='))
url=LATEST+'Install-Definitive.sh'

def fixture(root, failure='none', version='A'):
    tools=root/'tools';tools.mkdir();stage=root/'stage';stage.mkdir();home=root/'home'
    (home/'.cache/legend-of-dragoon-definitive').mkdir(parents=True)
    payload='#!/bin/bash\nprintf fixture-'+version+' > "$FIXTURE_RESULT"\n'
    def tool(name,code):
        p=tools/name;p.write_text('#!/usr/bin/env python3\n'+code);p.chmod(0o755)
    tool('mktemp','import os\nprint(os.environ["FIXTURE_STAGE"])\n')
    tool('mkdir','import sys\nassert sys.argv[1]=="-p" and sys.argv[2].endswith("/.cache/legend-of-dragoon-definitive")\n')
    tool('mv','import os,sys\nassert sys.argv[1]=="-fT"\nos.replace(sys.argv[2],sys.argv[3])\n')
    tool('tee','import os,sys\nfrom pathlib import Path\ndata=sys.stdin.read();Path(sys.argv[-1]).write_text(data);Path(os.environ["FIXTURE_LOG"]).write_text(data);sys.stdout.write(data)\n')
    tool('curl','''import hashlib,json,os,sys
from pathlib import Path
args=sys.argv[1:];record=Path(os.environ['FIXTURE_ARGS'])
calls=json.loads(record.read_text()) if record.exists() else []
calls.append(args);record.write_text(json.dumps(calls))
urls=[a for a in args if a.startswith('https://')]
assert len(urls)==1 and urls[0] in (os.environ['FIXTURE_URL'],os.environ['FIXTURE_SUMS_URL']), 'Lost or stale download URL'
assert '--proto' in args and args[args.index('--proto')+1]=='=https'
assert '--proto-redir' in args and args[args.index('--proto-redir')+1]=='=https'
assert '--max-filesize' in args and args[args.index('--max-filesize')+1]=='1048576'
if os.environ['FIXTURE_FAILURE']=='download':print('curl: (22) fixture download failure',file=sys.stderr);sys.exit(22)
name='SHA256SUMS' if urls[0]==os.environ['FIXTURE_SUMS_URL'] else 'Install-Definitive.sh'
dest=Path(args[args.index('--output')+1]).resolve()
assert dest==Path(os.environ['FIXTURE_STAGE']).resolve()/name, 'Merged output/URL argument'
payload=os.environ['FIXTURE_PAYLOAD']
if name=='SHA256SUMS':
    digest=hashlib.sha256(payload.encode()).hexdigest()
    if os.environ['FIXTURE_FAILURE']=='checksum':digest='0'*64
    if os.environ['FIXTURE_FAILURE']=='release-race' and len(calls)==1:digest=hashlib.sha256(b'prior release').hexdigest()
    filename='other-script.sh' if os.environ['FIXTURE_FAILURE']=='missing-checksum' else 'Install-Definitive.sh'
    dest.write_text(digest+'  '+filename+'\\n'+'0'*64+'  unrelated-file\\n')
else:dest.write_text(payload)
''')
    return dict(os.environ,HOME=str(home),PATH=str(tools)+':'+os.environ['PATH'],FIXTURE_STAGE=str(stage),
                FIXTURE_RESULT=str(root/'result'),FIXTURE_ARGS=str(root/'curl-args.json'),FIXTURE_LOG=str(root/'log'),
                FIXTURE_URL=url,FIXTURE_SUMS_URL=LATEST+'SHA256SUMS',FIXTURE_PAYLOAD=payload,FIXTURE_FAILURE=failure)

for failure,version in [('none','A'),('none','B'),('release-race','B'),('download','B'),('checksum','B'),('missing-checksum','B'),('linked-log','B'),('blocked-log','B')]:
    with tempfile.TemporaryDirectory(prefix='definitive-desktop-shell-') as temp:
        root=Path(temp);env=fixture(root,failure,version)
        if failure=='linked-log':
            (root/'victim').write_text('external file retained')
            (root/'home/.cache/legend-of-dragoon-definitive/desktop-bootstrap.log').symlink_to(root/'victim')
        if failure=='blocked-log':
            blocked=root/'home/.cache/legend-of-dragoon-definitive/desktop-bootstrap.log';blocked.mkdir();(blocked/'protected.txt').write_text('external directory retained')
        run=subprocess.run(['/bin/bash','-c',command],env=env,input='',capture_output=True,text=True,timeout=10)
        calls=json.loads((root/'curl-args.json').read_text())
        if failure in ('none','release-race','linked-log','blocked-log'):
            assert run.returncode==0,run.stdout+run.stderr
            assert (root/'result').read_text()=='fixture-'+version
            assert all(any(endpoint in args for args in calls) for endpoint in [url,LATEST+'SHA256SUMS'])
            if failure=='release-race':assert len(calls)==4,'Concurrent release transition did not retry a fresh checksum/script pair'
            if failure=='linked-log':assert (root/'victim').read_text()=='external file retained'
            if failure=='blocked-log':
                assert (blocked/'protected.txt').read_text()=='external directory retained'
                assert 'Could not save the startup log' in run.stdout
        else:
            assert run.returncode!=0 and not (root/'result').exists(),'Unverified installer was launched'
            assert 'Press Enter' in run.stdout and 'desktop-bootstrap.log' in run.stdout
            assert ('fixture download failure' if failure=='download' else 'checksum mismatch') in (root/'log').read_text()
        assert not (root/'stage').exists(),'Bootstrap left its temporary files'
        print('PASS: saved shortcut',failure,version)
assert 'Terminal=true' in text.splitlines()
assert all(c not in command.removeprefix('/bin/bash -c "').removesuffix('"') for c in ('\\','"','$','`','%'))
print('PASS: the identical saved Desktop fetches changing latest releases, checks real SHA256, retries publication races and never executes bad/missing checksums')
if os.uname().sysname!='Linux':
    print('Native Desktop Entry parser: Linux only; terminal/trust UX remains a Deck check.');raise SystemExit(0)
gio=ctypes.CDLL(ctypes.util.find_library('gio-2.0'))
gio.g_desktop_app_info_new_from_filename.argtypes=[ctypes.c_char_p];gio.g_desktop_app_info_new_from_filename.restype=ctypes.c_void_p
gio.g_app_info_launch.argtypes=[ctypes.c_void_p,ctypes.c_void_p,ctypes.c_void_p,ctypes.c_void_p];gio.g_app_info_launch.restype=ctypes.c_int
with tempfile.TemporaryDirectory(prefix='definitive-desktop-native-') as temp:
    root=Path(temp);os.environ.update(fixture(root,version='B'))
    entry=root/'fixture.desktop';entry.write_text(text.replace('Terminal=true','Terminal=false'))
    app=gio.g_desktop_app_info_new_from_filename(str(entry).encode());assert app,'Native Linux parser rejected shortcut'
    assert gio.g_app_info_launch(app,None,None,None),'Native shortcut launch failed'
    deadline=time.monotonic()+10
    while not ((root/'result').exists() and (root/'log').exists()) and time.monotonic()<deadline:time.sleep(0.1)
    assert (root/'result').read_text()=='fixture-B'
    assert (root/'log').exists()
    calls=json.loads((root/'curl-args.json').read_text());assert any(url in args for args in calls)
print('PASS: native Linux Desktop Entry parsing of exact Exec, without a terminal/window')
