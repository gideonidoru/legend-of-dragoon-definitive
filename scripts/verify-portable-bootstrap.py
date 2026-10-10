#!/usr/bin/env python3
"""Exercise the real portable bootstrap with isolated cache, download/runtime/UI stand-ins."""
from pathlib import Path
import hashlib
import io
import json
import os
import re
import shlex
import subprocess
import tempfile
import zipfile

source = (Path(__file__).resolve().parents[1] / 'delivery/Install-Definitive.sh').read_text()
for failure in ('none', 'download', 'checksum', 'runtime', 'java', 'mktemp', 'linked-log'):
    with tempfile.TemporaryDirectory(prefix='definitive-portable-check-') as directory:
        root = Path(directory)
        tools = root / 'tools'
        tools.mkdir()
        if failure == 'linked-log':
            (root / 'cache').mkdir()
            (root / 'victim').write_text('external file retained')
            (root / 'cache/installer-bootstrap.log').symlink_to(root / 'victim')
        def tool(name, code):
            file = tools / name
            file.write_text('#!/usr/bin/env python3\n' + code)
            file.chmod(0o755)
        java = root / 'fake-java'
        java.write_text('#!/bin/bash\n[[ "${FIXTURE_FAILURE}" != java ]] || { echo "fixture Java startup failure" >&2; exit 37; }\nprintf success > "${FIXTURE_RESULT}"\n')
        java.chmod(0o755)
        archive = io.BytesIO()
        with zipfile.ZipFile(archive, 'w') as payload:
            payload.writestr('bootstrap-java', '#!/bin/bash\n[[ "${FIXTURE_FAILURE}" != runtime ]] || { echo "fixture runtime failure" >&2; exit 8; }\nprintf "%s\\n" "${FIXTURE_JAVA}"\n')
            payload.writestr('definitive-manager.jar', 'not executed')
        asset = root / 'payload.zip'
        asset.write_bytes(archive.getvalue())
        digest = hashlib.sha256(asset.read_bytes()).hexdigest()
        tool('curl', '''import os,sys,shutil
from pathlib import Path
args=sys.argv[1:]
urls=[a for a in args if a.startswith('https://')]
assert len(urls)==1 and urls[0].endswith('/Definitive-Installer.zip')
assert '--connect-timeout' in args and '--max-time' in args and '--speed-time' in args
if os.environ['FIXTURE_FAILURE']=='download':
    print('fixture download failure',file=sys.stderr);sys.exit(22)
dest=Path(args[args.index('--output')+1])
if os.environ['FIXTURE_FAILURE']=='checksum':dest.write_bytes(b'corrupt')
else:shutil.copyfile(os.environ['FIXTURE_ASSET'],dest)
''')
        if failure == 'mktemp':
            tool('mktemp', "import sys\nprint('fixture temporary storage failure',file=sys.stderr)\nsys.exit(1)\n")
        tool('zenity', '''import os,sys
from pathlib import Path
if '--progress' in sys.argv:
    assert '--pulsate' not in sys.argv
    Path(os.environ['FIXTURE_PROGRESS']).write_text(sys.stdin.read())
elif '--error' in sys.argv:Path(os.environ['FIXTURE_ERROR']).write_text(' '.join(sys.argv))
else:raise AssertionError(sys.argv)
''')
        script = source.replace('CACHE="$HOME/.cache/legend-of-dragoon-definitive"', 'CACHE=' + shlex.quote(str(root / 'cache')))
        assert script != source, 'Isolated cache seam changed'
        script, count = re.subn(r'^EXPECTED=.*$', 'EXPECTED=' + digest, script, flags=re.M)
        assert count == 1
        entry = root / 'install.sh'
        entry.write_text(script)
        env = dict(os.environ, PATH=str(tools) + ':' + os.environ['PATH'], FIXTURE_FAILURE=failure,
                   FIXTURE_RESULT=str(root / 'result'), FIXTURE_JAVA=str(java), FIXTURE_ASSET=str(asset),
                   FIXTURE_PROGRESS=str(root / 'progress'), FIXTURE_ERROR=str(root / 'error'))
        result = subprocess.run(['/bin/bash', str(entry)], env=env, capture_output=True, text=True, timeout=10)
        assert not (root / 'cache/.portable-lock').is_dir(), 'Failure left a directory lock'
        if failure in ('none', 'linked-log'):
            assert result.returncode == 0 and (root / 'result').read_text() == 'success', result.stderr
            if failure == 'linked-log':assert (root / 'victim').read_text() == 'external file retained'
            progress = (root / 'progress').read_text()
            assert all(value in progress.splitlines() for value in ('5', '35', '55', '90', '100'))
            assert 'Checking the installer checksum' in progress and 'Preparing Java 25' in progress
        else:
            assert result.returncode != 0 and not (root / 'result').exists(), 'Failure started the manager'
            if failure == 'mktemp': assert 'fixture temporary storage failure' in result.stderr
            elif failure == 'java': assert 'fixture Java startup failure' in result.stderr
            else:
                assert (root / 'error').exists(), 'Bootstrap failure had no error screen'
                log = (root / 'cache/installer-bootstrap.log').read_text()
                assert {'download': 'fixture download failure', 'checksum': 'checksum mismatch', 'runtime': 'fixture runtime failure'}[failure] in log
runtime_source = Path(__file__).resolve().parents[1] / 'delivery/installer/bootstrap-java'
with tempfile.TemporaryDirectory(prefix='definitive-java-lock-check-') as directory:
    root = Path(directory)
    tools = root / 'tools'; tools.mkdir()
    for name, code in {'mktemp': 'echo fixture temporary storage failure >&2; exit 1', 'java': 'exit 1'}.items():
        file = tools / name; file.write_text('#!/bin/bash\n' + code + '\n'); file.chmod(0o755)
    env = dict(os.environ, PATH=str(tools) + ':' + os.environ['PATH'], JAVA_HOME='')
    for attempt in range(2):
        result = subprocess.run(['/bin/bash', str(runtime_source), str(root / 'runtime')], env=env, capture_output=True, text=True, timeout=10)
        assert result.returncode != 0 and 'fixture temporary storage failure' in result.stderr
        assert not (root / 'runtime/.java-bootstrap-lock').is_dir(), 'Early Java setup failure blocked retry'
print('PASS: portable bootstrap success, phase progress, download/checksum/runtime/startup/temporary-storage failures and both bootstrap lock cleanup')
