#!/usr/bin/env python3
"""Verify real startup scripts serialize work and recover after forced termination.

All downloads and windows are stand-ins; only isolated fixture process groups are killed.
"""
from pathlib import Path
import argparse
import os
import signal
import subprocess
import sys
import tempfile
import time

parser = argparse.ArgumentParser()
parser.add_argument('--source-ref', help='Compare an earlier Git revision without changing the checkout')
options = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
failures = []

for kind, source_name, lock_name in (
    ('portable', 'delivery/Install-Definitive.sh', '.portable-lock'),
    ('java', 'delivery/installer/bootstrap-java', '.java-bootstrap-lock'),
):
    source = subprocess.check_output(['git', 'show', f'{options.source_ref}:{source_name}'], cwd=repo, text=True) if options.source_ref else (repo / source_name).read_text()
    for scenario in ('whole-group-kill', 'parent-kill', 'legacy-directory', 'linked-lock'):
        with tempfile.TemporaryDirectory(prefix='definitive-lock-fixture-') as directory:
            root = Path(directory)
            tools = root / 'tools'; tools.mkdir()
            cache = root / 'cache'; cache.mkdir()
            def tool(name, body):
                path = tools / name
                path.write_text('#!' + sys.executable + '\n' + body)
                path.chmod(0o755)
            tool('java', 'import sys\nsys.exit(1)\n')
            tool('zenity', 'import sys\nsys.stdin.read()\n')
            tool('curl', """import os,sys,time
from pathlib import Path
Path(os.environ['FIXTURE_STARTED']).write_text('download reached')
if os.environ['FIXTURE_HOLD']=='yes':time.sleep(30)
print('fixture reached download and failed',file=sys.stderr)
sys.exit(22)
""")
            if kind == 'portable':
                isolated = source.replace('CACHE="$HOME/.cache/legend-of-dragoon-definitive"', 'CACHE="' + str(cache) + '"')
                assert isolated != source, 'Cache isolation seam changed'
                args = ['/bin/bash', str(root / 'entry.sh')]
            else:
                isolated = source
                args = ['/bin/bash', str(root / 'entry.sh'), str(cache)]
            (root / 'entry.sh').write_text(isolated)
            lock = cache / lock_name
            env = dict(os.environ, PATH=str(tools) + ':' + os.environ['PATH'], JAVA_HOME='',
                       FIXTURE_STARTED=str(root / 'started'), FIXTURE_HOLD='yes')
            retry_env = dict(env, FIXTURE_STARTED=str(root / 'retry-started'), FIXTURE_HOLD='no')
            process = None
            try:
                if scenario in ('legacy-directory', 'linked-lock'):
                    retained = root / 'protected'; retained.write_text('retained')
                    if scenario == 'legacy-directory':
                        lock.mkdir(); (lock / 'owner-note').write_text('retained')
                    else:lock.symlink_to(retained)
                    result = subprocess.run(args, env=retry_env, capture_output=True, text=True, timeout=5)
                    assert result.returncode != 0 and not (root / 'retry-started').exists(), 'Unexpected existing lock allowed a download'
                    assert retained.read_text() == 'retained'
                    if scenario == 'legacy-directory':assert (lock / 'owner-note').read_text() == 'retained'
                    else:assert lock.is_symlink()
                else:
                    process = subprocess.Popen(args, env=env, stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True)
                    deadline = time.monotonic() + 5
                    while not (root / 'started').exists() and time.monotonic() < deadline and process.poll() is None:time.sleep(.01)
                    assert (root / 'started').exists(), 'Fixture download did not begin'
                    for attempt in range(3):
                        concurrent = subprocess.run(args, env=retry_env, capture_output=True, text=True, timeout=5)
                        assert concurrent.returncode != 0 and not (root / 'retry-started').exists(), 'Concurrent setup bypassed a live lock'
                    if scenario == 'parent-kill':
                        os.kill(process.pid, signal.SIGKILL); process.wait(timeout=5)
                        concurrent = subprocess.run(args, env=retry_env, capture_output=True, text=True, timeout=5)
                        assert concurrent.returncode != 0 and not (root / 'retry-started').exists(), 'Orphaned active download lost serialization'
                    os.killpg(process.pid, signal.SIGKILL); process.communicate(timeout=5)
                    retry = subprocess.run(args, env=retry_env, capture_output=True, text=True, timeout=5)
                    assert (root / 'retry-started').exists() and not lock.is_dir(), 'Killed setup left a permanent retry block'
                    assert retry.returncode != 0, 'Fixture download failure was reported as success'
                    assert lock.is_file() and lock.stat().st_mode & 0o077 == 0, 'New lock must be a private regular file'
                print('PASS', kind, scenario, flush=True)
            except AssertionError as failure:
                failures.append(f'{kind}/{scenario}: {failure}')
                print('FAIL', failures[-1], flush=True)
            finally:
                if process is not None:
                    try:os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError:pass
                    process.communicate(timeout=5)
if failures:raise SystemExit('\n'.join(failures))
print('PASS: both real bootstraps, concurrent exclusion, orphan exclusion, forced-kill retry, legacy/link preservation')
