#!/usr/bin/env python3
"""Headless runtime bootstrap fixtures: immutable URLs, digests, private staging and safe upgrades."""
from pathlib import Path
import hashlib
import io
import os
import re
import subprocess
import tarfile
import tempfile

repo = Path(__file__).resolve().parents[1]
source = (repo / 'delivery/installer/bootstrap-java').read_text()
assert 'downloads/latest' not in source and 'VERSION=25.0.4.10.1' in source
pins = {
    ('Linux', 'x86_64'): ('linux-x64', 'bin/java', 'a45f1d385da8221fe1225dde5b6afa8e932a6acb93571b55eff4a39a2ebe7378'),
    ('Darwin', 'arm64'): ('macosx-aarch64', 'Contents/Home/bin/java', 'ee05ff1a6a459be34c4e504a206b0cae7c77e42c58424a08408b76c451bdd4ef'),
    ('Darwin', 'x86_64'): ('macosx-x64', 'Contents/Home/bin/java', '6ec9249a53330e0bc8b0ddd928e890a9880eebbcb214ae92bc8363932df8424f'),
}
for (platform, machine), (artifact_platform, relative, official_digest) in pins.items():
    assert official_digest in source
    for corrupt in (False, True):
        with tempfile.TemporaryDirectory(prefix='definitive-runtime-pin-') as directory:
            root = Path(directory); tools = root / 'tools'; tools.mkdir()
            payload = b"#!/bin/sh\necho 'java.specification.version = 25' >&2\necho 'java.vendor.version = Corretto-25.0.4.10.1' >&2\n"
            archive = io.BytesIO()
            with tarfile.open(fileobj=archive, mode='w:gz') as tar:
                entry = tarfile.TarInfo('jdk/' + relative); entry.mode = 0o755; entry.size = len(payload)
                tar.addfile(entry, io.BytesIO(payload))
            asset = root / 'archive.tar.gz'; asset.write_bytes(archive.getvalue())
            digest = hashlib.sha256(asset.read_bytes()).hexdigest()
            # Controlled payload seam; production URL/digests above remain independently asserted.
            fixture = re.sub(r'EXPECTED=[a-f0-9]{64}', 'EXPECTED=' + digest, source)
            script = root / 'bootstrap'; script.write_text(fixture)
            def tool(name, code):
                path = tools / name; path.write_text('#!/usr/bin/env python3\n' + code); path.chmod(0o755)
            tool('uname', f"import sys\nprint({platform!r} if sys.argv[1] == '-s' else {machine!r})\n")
            tool('java', 'raise SystemExit(1)\n')
            tool('curl', '''import os,sys,shutil
from pathlib import Path
args=sys.argv[1:];urls=[a for a in args if a.startswith('https://')]
assert urls == [os.environ['FIXTURE_URL']]
assert '--connect-timeout' in args and '--max-time' in args
path=Path(args[args.index('--output')+1])
if os.environ['FIXTURE_CORRUPT']=='1':path.write_bytes(b'corrupt')
else:shutil.copyfile(os.environ['FIXTURE_ASSET'],path)
''')
            storage = root / 'runtime'; storage.mkdir()
            old = storage / 'runtime-jdk25'; old.mkdir(); (old / 'sentinel').write_text('preserve an older runtime')
            env = dict(os.environ, JAVA_HOME='', DEFINITIVE_MANAGED_JAVA='1', PATH=str(tools)+':'+os.environ['PATH'],
                       FIXTURE_URL=f'https://corretto.aws/downloads/resources/25.0.4.10.1/amazon-corretto-25.0.4.10.1-{artifact_platform}.tar.gz',
                       FIXTURE_ASSET=str(asset), FIXTURE_CORRUPT=str(int(corrupt)))
            run = subprocess.run(['/bin/bash', str(script), str(storage)], env=env, capture_output=True, text=True, timeout=10)
            target = storage / 'runtime-jdk25.0.4.10.1'
            assert (old / 'sentinel').read_text() == 'preserve an older runtime'
            assert not list(storage.glob('.java-download.*'))
            if corrupt:
                assert run.returncode != 0 and not target.exists() and 'checksum mismatch' in run.stderr, run.stderr
            else:
                assert run.returncode == 0 and run.stdout.strip() == str(target / relative), run.stderr
                assert (target / 'definitive-runtime.sha256').read_text().startswith(digest + '  ')
                # Reuse the reviewed version offline; never download on every launch.
                env['FIXTURE_CORRUPT'] = '1'
                reused = subprocess.run(['/bin/bash', str(script), str(storage)], env=env, capture_output=True, text=True, timeout=10)
                assert reused.returncode == 0 and reused.stdout == run.stdout, reused.stderr
            print('PASS', platform, machine, 'reject corruption' if corrupt else 'verified install and offline reuse')
# Portable setup's JVM lives in a shared cache, outside the installation root.
# The recorded executable must support the first offline Play without another download.
with tempfile.TemporaryDirectory(prefix='definitive-runtime-offline-') as directory:
    root = Path(directory); install = root / 'install'; install.mkdir(); tools = root / 'tools'; tools.mkdir()
    external = root / 'cache/runtime-jdk25.0.4.10.1/bin/java'; external.parent.mkdir(parents=True)
    external.write_text("#!/bin/sh\necho 'java.vendor.version = Corretto-25.0.4.10.1' >&2\n"); external.chmod(0o755)
    (install / '.java-path').write_text(str(external) + '\n')
    for name in ('java', 'curl'):
        path = tools / name; path.write_text('#!/bin/sh\nexit 91\n'); path.chmod(0o755)
    env = dict(os.environ, JAVA_HOME='', PATH=str(tools)+':'+os.environ['PATH'], DEFINITIVE_MANAGED_JAVA='0')
    run = subprocess.run(['/bin/bash', str(repo / 'delivery/installer/bootstrap-java'), str(install)], env=env, capture_output=True, text=True, timeout=10)
    assert run.returncode == 0 and run.stdout.strip() == str(external), run.stderr
    assert not (install / 'runtime-jdk25.0.4.10.1').exists()
    print('PASS recorded external reviewed runtime is reused for first offline launch')
for receipt_kind in ('verified', 'missing', 'linked', 'forced-upgrade'):
    with tempfile.TemporaryDirectory(prefix='definitive-runtime-migration-') as directory:
        root = Path(directory); install = root / 'install'; install.mkdir(); tools = root / 'tools'; tools.mkdir()
        legacy = root / 'cache/runtime-jdk25/bin/java'; legacy.parent.mkdir(parents=True)
        legacy.write_text("#!/bin/sh\necho 'java.specification.version = 25' >&2\necho 'java.vendor.version = Corretto-25.0.0.36.2' >&2\n"); legacy.chmod(0o755)
        (install / '.java-path').write_text(str(legacy) + '\n')
        receipt = legacy.parent.parent / 'definitive-runtime.sha256'
        if receipt_kind != 'missing':
            receipt.write_text('0' * 64 + '  amazon-corretto-25-x64-linux-jdk.tar.gz\n')
        if receipt_kind == 'linked':
            retained = root / 'retained-receipt'; receipt.rename(retained); receipt.symlink_to(retained)
        for name in ('java', 'curl'):
            path = tools / name; path.write_text('#!/bin/sh\nexit 91\n'); path.chmod(0o755)
        env = dict(os.environ, JAVA_HOME='', PATH=str(tools)+':'+os.environ['PATH'],
                   DEFINITIVE_MANAGED_JAVA='1' if receipt_kind == 'forced-upgrade' else '0')
        run = subprocess.run(['/bin/bash', str(repo / 'delivery/installer/bootstrap-java'), str(install)], env=env, capture_output=True, text=True, timeout=10)
        if receipt_kind == 'verified':
            assert run.returncode == 0 and run.stdout.strip() == str(legacy), run.stderr
        else:
            assert run.returncode == 91 and not run.stdout.strip(), (run.returncode, run.stderr)
        assert legacy.exists() and not list(install.glob('.java-download.*'))
        print('PASS legacy offline migration', receipt_kind)
print('PASS: all pinned runtime platforms, safe verified legacy migration and temporary cleanup')
