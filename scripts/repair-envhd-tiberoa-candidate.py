#!/usr/bin/env python3
"""Reproduce the pending 4x stair painting without selecting a runtime material."""
import argparse
import importlib.util
import io
import json
from pathlib import Path

import numpy as np
from PIL import Image

spec = importlib.util.spec_from_file_location('audit', Path(__file__).with_name('audit-envhd-tiberoa-source.py'))
audit = importlib.util.module_from_spec(spec)
spec.loader.exec_module(audit)


def repair(source, raw):
    if len(raw) > 32 * 1024 * 1024 or not raw.startswith(b'\x89PNG\r\n\x1a\n'):
        raise ValueError('Expected bounded generated PNG')
    with Image.open(io.BytesIO(raw)) as generated:
        if generated.mode not in ('RGB', 'RGBA') or max(generated.size) > 4096 or generated.width * generated.height > 4000000:
            raise ValueError('Generated texture exceeds authoring bounds')
        if abs(generated.width / generated.height - source.width / source.height) / (source.width / source.height) > .01:
            raise ValueError('Generated canvas aspect changed')
        if generated.mode == 'RGBA' and not np.all(np.asarray(generated)[:, :, 3] == 255):
            raise ValueError('Expected opaque generated stair surface')
        raw_size = list(generated.size)
        rgb = np.asarray(generated.convert('RGB').resize((source.width * 4, source.height * 4), Image.Resampling.LANCZOS))
    result, _ = audit.terrain.preserve_stp(source, rgb)
    actual = np.asarray(result)
    original = np.repeat(np.repeat(np.asarray(source), 4, axis=0), 4, axis=1)
    if not np.array_equal(actual[:, :, 3], original[:, :, 3]):
        raise ValueError('Source STP changed')
    if not np.array_equal(np.all(actual == 0, axis=2), np.all(original == 0, axis=2)):
        raise ValueError('Source discard coverage changed')
    receipt = dict(rawSize=raw_size, rawSha256=audit.terrain.digest(raw), targetSize=list(result.size),
                   scale=4, sourceStpExact=True, sourceDiscardAndVisibleBlackExact=True,
                   edgeAveraging=False, method='built-in-imagegen-source-referenced-lanczos-4x-pinned-stp-repair',
                   runtimeSelected=False, nativeAccepted=False, finalAccepted=False)
    return result, receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--input', required=True, type=Path)
    parser.add_argument('--input-sha256', required=True)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    raw = audit.terrain.bounded_read(args.input, 32 * 1024 * 1024)
    if audit.terrain.digest(raw) != args.input_sha256:
        raise ValueError('Generated input identity changed')
    source, contract = audit.audit(args.files)
    result, receipt = repair(source, raw)
    encoded = io.BytesIO()
    result.save(encoded, format='PNG')
    data = encoded.getvalue()
    receipt.update(outputSha256=audit.terrain.digest(data), decodedRgbaSha256=contract['decodedRgbaSha256'],
                   modelSha256=contract['modelSha256'], bankSha256=contract['bankSha256'],
                   recipeSha256=audit.terrain.digest(Path(__file__).read_bytes()),
                   auditScriptSha256=contract['auditScriptSha256'], decoderSha256=contract['decoderSha256'])
    # Refuse to overwrite previous authoring history.
    with args.output.open('xb') as stream:
        stream.write(data)
    with args.output.with_suffix('.repair.json').open('x') as stream:
        stream.write(json.dumps(receipt, indent=2) + '\n')
    print(json.dumps(receipt))


if __name__ == '__main__':
    main()
