#!/usr/bin/env python3
"""Authoring safety controls; these do not approve the reference candidate's art."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
from unittest.mock import patch

repo = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('font_author', repo/'scripts/build-uihd-original-font.py')
author = importlib.util.module_from_spec(spec); spec.loader.exec_module(author)
passed = 0
with tempfile.TemporaryDirectory(prefix='uihd-font-author-') as temporary:
    root = Path(temporary).resolve()
    source = root/'source/gfx/fonts'; source.mkdir(parents=True)
    for name in ['default.json','default.png']:
        (source/name).write_bytes((repo/'gfx/fonts'/name).read_bytes())
    def rejected(output, message):
        global passed
        try: author.build(root/'source', output)
        except ValueError as failure: assert message in str(failure), failure
        else: raise AssertionError('Unsafe candidate was accepted')
        assert not output.exists() or output.is_symlink()
        passed += 1
    outside = root/'outside'; outside.mkdir(); (outside/'note').write_text('preserve')
    linked = root/'linked'; linked.symlink_to(outside, target_is_directory=True)
    rejected(linked/'candidate', 'linked')
    rejected(linked/'../candidate', 'linked')
    broken = root/'broken'; broken.symlink_to(root/'absent', target_is_directory=True)
    rejected(broken, 'linked')
    assert sorted(p.name for p in outside.iterdir()) == ['note'] and (outside/'note').read_text() == 'preserve'
    assert not (root/'candidate').exists()
    original_metadata = (source/'default.json').read_bytes()
    (source/'default.json').write_bytes(original_metadata+b' ')
    rejected(root/'wrong-source', 'pins')
    (source/'default.json').write_bytes(original_metadata)
    metadata=json.loads(original_metadata); key=next(k for k,v in metadata['glyphs'].items() if v['texH']>0)
    metadata['glyphs'][key].update(texU=0,texW=253)
    oversized=json.dumps(metadata).encode(); (source/'default.json').write_bytes(oversized)
    # An approved synthetic source pin reaches placement, rather than only failing identity.
    with patch.object(author,'SOURCE_METADATA_SHA256',hashlib.sha256(oversized).hexdigest()):
        rejected(root/'oversized', 'bounded atlas')
    (source/'default.json').write_bytes(original_metadata)
    output=root/'write-failure'; real_open=Path.open
    def fail_write(path, *args, **kwargs):
        if path.name=='original-hd.json' and args and args[0]=='xb': raise OSError('fixture disk failure after glyph writes')
        return real_open(path,*args,**kwargs)
    with patch.object(Path,'open',fail_write):
        try: author.build(root/'source',output)
        except OSError as failure: assert 'fixture disk failure' in str(failure)
        else: raise AssertionError('Write failure unexpectedly succeeded')
    assert not output.exists(); passed += 1
    ordinary=root/'ordinary'; author.build(root/'source',ordinary)
    ledger=json.loads((ordinary/'source-comparison.json').read_text())
    assert ledger['glyphCount']==139 and ledger['logicalMetricsChanged']==0 and not ledger['runtimeSelected']
    before={p.relative_to(ordinary).as_posix():p.read_bytes() for p in ordinary.rglob('*') if p.is_file()}
    assert len(before)==142
    try: author.build(root/'source',ordinary)
    except ValueError as failure: assert 'fresh' in str(failure)
    else: raise AssertionError('Reviewed output was overwritten')
    assert before=={p.relative_to(ordinary).as_posix():p.read_bytes() for p in ordinary.rglob('*') if p.is_file()}; passed+=1
    # After each file's single read, later source changes must not corrupt its recorded identity.
    real_read=Path.read_bytes; seen={}
    def mutate_after_read(path):
        result=real_read(path)
        if path.parent==source and path.name in ['default.json','default.png']:
            seen[path.name]=seen.get(path.name,0)+1;path.write_bytes(b'changed after read')
        return result
    with patch.object(Path,'read_bytes',mutate_after_read): author.build(root/'source',root/'read-once')
    assert seen=={'default.json':1,'default.png':1}
    assert (root/'read-once/source-comparison.json').read_bytes()==before['source-comparison.json'];passed+=1
print(f'PASS: {passed} font authoring safety controls; no art/runtime/device acceptance')
