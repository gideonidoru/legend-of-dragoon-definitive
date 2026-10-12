#!/usr/bin/env python3
"""Exercise the real runtime bootstrap offline with isolated JVM stand-ins."""
from pathlib import Path
import os
import fcntl
import subprocess
import tempfile

repo = Path(__file__).resolve().parents[1]
source = repo / 'delivery/installer/bootstrap-java'
for scenario in ('empty-receipt', 'newline-less-receipt', 'oversized-receipt', 'linked-root'):
    with tempfile.TemporaryDirectory(prefix='definitive-runtime-recovery-') as directory:
        root = Path(directory).resolve()
        cache = root / 'cache'; cache.mkdir()
        tools = root / 'tools'; tools.mkdir()
        def executable(path, body):
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text('#!/bin/bash\n' + body + '\n'); path.chmod(0o755)
        executable(tools / 'uname', 'if [[ $1 == -s ]]; then echo Darwin; else echo arm64; fi')
        executable(tools / 'curl', 'echo forbidden-download >&2; exit 97')
        java = cache / 'runtime-jdk25.0.4.10.1/Contents/Home/bin/java'
        executable(java, "echo '    java.specification.version = 25' >&2; echo '    java.vendor.version = Corretto-25.0.4.10.1' >&2")
        if scenario == 'empty-receipt': (cache / '.java-path').write_bytes(b'')
        elif scenario == 'newline-less-receipt': (cache / '.java-path').write_text(str(java))
        elif scenario == 'oversized-receipt': (cache / '.java-path').write_text('X' * 5000)
        supplied = cache
        if scenario == 'linked-root':
            supplied = root / 'linked-cache'; supplied.symlink_to(cache, target_is_directory=True)
        env = dict(os.environ, JAVA_HOME='', PATH=str(tools) + ':' + os.environ['PATH'], DEFINITIVE_MANAGED_JAVA='0')
        result = subprocess.run(['/bin/bash', str(source), str(supplied)], env=env, capture_output=True, text=True, timeout=5)
        if scenario == 'linked-root':
            assert result.returncode != 0 and 'real runtime storage' in result.stderr, result
        else:
            assert result.returncode == 0 and result.stdout.strip() == str(java), result
        assert 'forbidden-download' not in result.stderr, 'Offline recovery unexpectedly attempted a download'
        assert java.is_file()
        lock = cache / '.java-bootstrap-lock'
        if scenario == 'linked-root':
            assert not lock.exists(), 'Rejected linked cache must not create a lock'
        else:
            # Startup recovery intentionally acquires the persistent inode even
            # when a usable runtime is cached. Existence is not a held lease.
            assert lock.is_file() and not lock.is_symlink() and lock.stat().st_mode & 0o077 == 0
            with lock.open('rb') as stream:
                fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
                fcntl.flock(stream.fileno(), fcntl.LOCK_UN)
        print('PASS', scenario)
print('PASS: malformed/truncated receipt falls back offline, linked cache rejected')
