#!/usr/bin/env python3
"""Check the shipped Exec text, visible failures and native Linux parsing using strict stand-ins.
No network, GUI, personal cache or game is touched. KDE terminal/trust UX needs a real Deck.
"""
from pathlib import Path
import ctypes
import ctypes.util
import hashlib
import json
import os
import re
import subprocess
import tempfile
import time

repo = Path(__file__).resolve().parents[1]
desktop = repo/'delivery/Install-Definitive.desktop'
text = desktop.read_text()
command = next(line[5:] for line in text.splitlines() if line.startswith('Exec='))
tag_match = re.search(r'^TAG=([A-Za-z0-9._-]+)$', (repo/'delivery/Install-Definitive.sh').read_text(), flags=re.M)
assert tag_match, 'Portable entry point needs a valid release tag'
url = f'https://github.com/gideonidoru/legend-of-dragoon-definitive/releases/download/{tag_match[1]}/Install-Definitive.sh'
digest = hashlib.sha256((repo/'delivery/Install-Definitive.sh').read_bytes()).hexdigest()
assert digest in command, 'Desktop checksum must match the shipped setup script'


def fixture(root, bad_download=False, bad_checksum=False):
    tools = root/'tools'; tools.mkdir()
    stage = root/'stage'; stage.mkdir()
    payload = '#!/bin/bash\nprintf fixture-success > "$FIXTURE_RESULT"\n'
    def tool(name, code):
        path=tools/name; path.write_text('#!/usr/bin/env python3\n'+code); path.chmod(0o755)
    tool('mktemp', 'import os\nprint(os.environ["FIXTURE_STAGE"])\n')
    tool('mkdir', '# Only the fixed cache mkdir is intercepted; the fixture already exists.\nimport sys\nassert sys.argv[1] == "-p" and sys.argv[2].endswith("/.cache/legend-of-dragoon-definitive")\n')
    tool('tee', 'import os,sys\nfrom pathlib import Path\ndata=sys.stdin.read();Path(os.environ["FIXTURE_LOG"]).write_text(data);sys.stdout.write(data)\n')
    tool('curl', '''import json,os,sys
from pathlib import Path
args=sys.argv[1:];Path(os.environ['FIXTURE_ARGS']).write_text(json.dumps(args))
urls=[a for a in args if a.startswith('https://')]
if not urls:
    print('curl: (2) no URL specified',file=sys.stderr);sys.exit(2)
assert urls == [os.environ['FIXTURE_URL']], 'Download URL was changed or merged with another argument'
if os.environ['FIXTURE_BAD_DOWNLOAD']=='1':
    print('curl: (22) fixture download failure',file=sys.stderr);sys.exit(22)
dest=Path(args[args.index('--output')+1]).resolve()
assert dest == Path(os.environ['FIXTURE_STAGE']).resolve()/'install.sh', 'Output argument was changed or escaped incorrectly'
dest.write_text(os.environ['FIXTURE_PAYLOAD'])
''')
    tool('sha256sum', '''import os,sys
from pathlib import Path
if '--check' in sys.argv:
    data=sys.stdin.read();assert os.environ['FIXTURE_DIGEST'] in data
else:
    assert sys.argv[1:] == ['install.sh']
assert (Path(os.environ['FIXTURE_STAGE'])/'install.sh').read_text()==os.environ['FIXTURE_PAYLOAD']
if os.environ['FIXTURE_BAD_CHECKSUM']=='1':
    if '--check' in sys.argv:sys.exit(1)
    print('0'*64+'  install.sh')
else:print(os.environ['FIXTURE_DIGEST']+'  install.sh')
''')
    return dict(os.environ, PATH=str(tools)+':'+os.environ['PATH'],
                FIXTURE_STAGE=str(stage), FIXTURE_RESULT=str(root/'result'), FIXTURE_ARGS=str(root/'curl-args.json'),
                FIXTURE_LOG=str(root/'log'), FIXTURE_URL=url, FIXTURE_DIGEST=digest, FIXTURE_PAYLOAD=payload,
                FIXTURE_BAD_DOWNLOAD=str(int(bad_download)), FIXTURE_BAD_CHECKSUM=str(int(bad_checksum)))


for failure in ('none', 'download', 'checksum'):
    with tempfile.TemporaryDirectory(prefix='definitive-desktop-shell-') as temp:
        root=Path(temp)
        env=fixture(root, failure=='download', failure=='checksum')
        run=subprocess.run(['/bin/bash','-c',command], env=env, input='', capture_output=True, text=True, timeout=10)
        assert (root/'curl-args.json').exists(), run.stderr+run.stdout
        arguments=json.loads((root/'curl-args.json').read_text())
        assert url in arguments, 'BUG: copied Exec loses its URL: '+run.stderr+run.stdout
        if failure=='none':
            assert run.returncode==0, run.stderr+run.stdout
            assert (root/'result').read_text()=='fixture-success'
        else:
            assert run.returncode != 0 and not (root/'result').exists(), 'Failed verification launched the installer'
            assert 'Press Enter' in run.stdout and 'desktop-bootstrap.log' in run.stdout, 'Failure did not provide a visible hold and diagnostic location'
            log=(root/'log').read_text()
            assert ('fixture download failure' if failure=='download' else 'checksum mismatch') in log, 'Failure details were not persisted'
print('PASS: literal shell copy, exact URL argument, and retained download/checksum failures')
assert 'Terminal=true' in text.splitlines(), 'Desktop bootstrap must provide a visible terminal'
assert all(char not in command.removeprefix('/bin/bash -c "').removesuffix('"') for char in ('\\', '"', '$', '`', '%')), 'Exec must not require nested escaping or field-code expansion'

if os.uname().sysname != 'Linux':
    print('Native Desktop Entry parser: Linux only; terminal/trust behavior remains a Deck check.')
    raise SystemExit(0)
gio=ctypes.CDLL(ctypes.util.find_library('gio-2.0'))
gio.g_desktop_app_info_new_from_filename.argtypes=[ctypes.c_char_p]
gio.g_desktop_app_info_new_from_filename.restype=ctypes.c_void_p
gio.g_app_info_launch.argtypes=[ctypes.c_void_p,ctypes.c_void_p,ctypes.c_void_p,ctypes.c_void_p]
gio.g_app_info_launch.restype=ctypes.c_int
with tempfile.TemporaryDirectory(prefix='definitive-desktop-native-') as temp:
    root=Path(temp); env=fixture(root); os.environ.update(env)
    # Preserve the real Exec, but suppress a real terminal in this headless parser fixture.
    entry=root/'fixture.desktop';entry.write_text(text.replace('Terminal=true','Terminal=false'))
    app=gio.g_desktop_app_info_new_from_filename(str(entry).encode())
    assert app, 'Native Linux parser rejected the installer shortcut'
    assert gio.g_app_info_launch(app,None,None,None), 'Native shortcut launch failed'
    deadline=time.monotonic()+10
    while not ((root/'result').exists() and (root/'log').exists()) and time.monotonic()<deadline:time.sleep(0.1)
    assert (root/'result').read_text()=='fixture-success', 'Native Exec arguments did not launch the verified stand-in'
    assert (root/'log').exists(), 'Native bootstrap pipeline did not finish and retain its output'
    assert url in json.loads((root/'curl-args.json').read_text()), 'Native parser lost the URL argument'
print('PASS: native Linux Desktop Entry parsing with the exact Exec and no terminal/window')
