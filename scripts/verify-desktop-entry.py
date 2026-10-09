#!/usr/bin/env python3
"""Exercise the real Linux Desktop Entry parser with harmless stand-in download tools."""
from pathlib import Path
import ctypes
import ctypes.util
import os
import tempfile
import time

if os.uname().sysname != 'Linux':
    print('Desktop Entry native fixture: Linux only (not a physical KDE test).')
    raise SystemExit(0)
root = Path(__file__).resolve().parents[1]
gio = ctypes.CDLL(ctypes.util.find_library('gio-2.0'))
gio.g_desktop_app_info_new_from_filename.argtypes = [ctypes.c_char_p]
gio.g_desktop_app_info_new_from_filename.restype = ctypes.c_void_p
gio.g_app_info_launch.argtypes = [ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p]
gio.g_app_info_launch.restype = ctypes.c_int
with tempfile.TemporaryDirectory(prefix='definitive-desktop-fixture-') as temporary:
    folder = Path(temporary)
    result = folder / 'result'
    # No network, game, GUI or real checksum bypass: these tools exist only in this private fixture PATH.
    curl = folder / 'curl'
    curl.write_text('''#!/bin/bash
while [ "$#" -gt 0 ]; do
  if [ "$1" = --output ]; then shift; dest=$1; fi
  shift
done
printf '#!/bin/bash\\nprintf fixture-success > "$FIXTURE_RESULT"\\n' > "$dest"
''')
    checksum = folder / 'sha256sum'
    checksum.write_text('#!/bin/bash\ncat >/dev/null\nexit 0\n')
    curl.chmod(0o755); checksum.chmod(0o755)
    os.environ['PATH'] = str(folder) + ':' + os.environ['PATH']
    os.environ['FIXTURE_RESULT'] = str(result)
    app = gio.g_desktop_app_info_new_from_filename(str(root / 'delivery/Install-Definitive.desktop').encode())
    assert app, 'Native Linux parser rejected the installer shortcut'
    assert gio.g_app_info_launch(app, None, None, None), 'Native shortcut launch failed'
    deadline = time.monotonic() + 10
    while not result.exists() and time.monotonic() < deadline:
        time.sleep(0.1)
    assert result.read_text() == 'fixture-success', 'Quoted shell arguments did not survive Desktop Entry parsing'
print('PASS: Linux Desktop Entry parsing and harmless bootstrap invocation')
