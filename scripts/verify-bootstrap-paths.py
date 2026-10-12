#!/usr/bin/env python3
"""Exercise actual bootstrap path guards without network, windows or a JVM."""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser()
parser.add_argument('--source-ref')
options = parser.parse_args()
repo = Path(__file__).resolve().parents[1]
failures = []
for kind, name in [('portable', 'delivery/Install-Definitive.sh'), ('runtime', 'delivery/installer/bootstrap-java')]:
    source = subprocess.check_output(['git', 'show', f'{options.source_ref}:{name}'], cwd=repo, text=True) if options.source_ref else (repo / name).read_text()
    for scenario in ['parent-link', 'link-before-dotdot', 'root-link', 'ordinary']:
        with tempfile.TemporaryDirectory(prefix='definitive-bootstrap-path-') as directory:
            root = Path(directory).resolve()
            home = root / 'home'; home.mkdir()
            outside = root / 'outside'; outside.mkdir()
            (outside / 'owner-note').write_text('preserve me')
            tools = root / 'tools'; tools.mkdir()
            def tool(name, body):
                path = tools / name
                path.write_text('#!/bin/bash\n' + body + '\n'); path.chmod(0o755)
            tool('curl', 'touch "$FIXTURE_DOWNLOAD"; exit 22')
            tool('java', 'exit 1')
            tool('zenity', 'cat >/dev/null; exit 1')
            if scenario == 'parent-link':
                (home / '.cache').symlink_to(outside, target_is_directory=True)
                cache = home / '.cache/legend-of-dragoon-definitive'
            elif scenario == 'root-link':
                (home / '.cache').mkdir()
                (home / '.cache/legend-of-dragoon-definitive').symlink_to(outside, target_is_directory=True)
                cache = home / '.cache/legend-of-dragoon-definitive'
            elif scenario == 'link-before-dotdot':
                (home / 'linked').symlink_to(outside, target_is_directory=True)
                cache = home / 'linked/../cache'
            else:
                cache = home / '.cache/legend-of-dragoon-definitive'
            # Change only the portable cache location; leave all path guards/startup code intact.
            if kind == 'portable':
                isolated = source.replace('CACHE="$HOME/.cache/legend-of-dragoon-definitive"', 'CACHE="' + str(cache) + '"')
                assert isolated != source
                args = ['/bin/bash', str(root / 'entry.sh')]
            else:
                isolated = source
                args = ['/bin/bash', str(root / 'entry.sh'), str(cache)]
            (root / 'entry.sh').write_text(isolated)
            before = sorted((str(p.relative_to(outside)), p.read_bytes()) for p in outside.rglob('*') if p.is_file())
            env = dict(os.environ, HOME=str(home), JAVA_HOME='', DEFINITIVE_MANAGED_JAVA='1', PATH=str(tools)+':'+os.environ['PATH'], FIXTURE_DOWNLOAD=str(root/'download'))
            run = subprocess.run(args, env=env, capture_output=True, text=True, timeout=10)
            try:
                assert run.returncode != 0, 'Fixture download failure incorrectly succeeded'
                if scenario == 'ordinary':
                    assert (root/'download').exists(), 'Healthy path did not reach the fixture download'
                else:
                    assert not (root/'download').exists(), 'Linked path reached download'
                    assert 'real' in run.stderr, 'Missing path rejection diagnostic'
                    assert before == sorted((str(p.relative_to(outside)), p.read_bytes()) for p in outside.rglob('*') if p.is_file()), 'External bytes changed'
                    assert sorted(p.name for p in outside.iterdir()) == ['owner-note'], 'External directory changed'
                    if scenario == 'link-before-dotdot': assert not (root/'cache').exists(), 'Traversed linked path before rejecting it'
                print('PASS', kind, scenario)
            except AssertionError as failure:
                failures.append(f'{kind}/{scenario}: {failure}')
                print('FAIL', failures[-1])
if failures: raise SystemExit('\n'.join(failures))
print('PASS: actual bootstraps reject linked lexical ancestors before writes/downloads; ordinary controls retained')
