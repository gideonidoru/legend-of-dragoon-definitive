#!/usr/bin/env python3
"""Metadata-only, bounded stage-local animation discovery; never select artwork.

Retail sequences/pixels are read privately. Public output contains identities,
consumer contracts and hashes only. A complete isolated stage cycle is not proof
that encounter scripts, darkening, other uploads or gameplay are covered.
"""
import argparse
import collections
import gzip
import hashlib
import importlib.util
import json
import math
import os
import struct
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('battle_census', ROOT / 'scripts/batch-envhd-battle-materials.py')
battle = importlib.util.module_from_spec(spec)
spec.loader.exec_module(battle)
MAX_TICKS = 200000
MAX_SEQUENCE_WORDS = 4096
SCRATCH = (960, 256)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def json_digest(value):
    return digest(json.dumps(value, sort_keys=True, separators=(',', ':')).encode())


def method_body(path, signature):
    """Extract an exact current source method for hashing / CPU differential tests."""
    text = path.read_text()
    start = text.index(signature)
    left = text.index('{', start)
    # The audited methods have no brace-bearing literals/comments. Fail on drift
    # rather than silently substituting a different implementation.
    depth = 1
    for right in range(left + 1, len(text)):
        depth += (text[right] == '{') - (text[right] == '}')
        if not depth:
            return text[start:right + 1]
    raise ValueError('Unterminated native method')


def sequences(data):
    """Same first -1 sentinel as CContainerSubfile2, including disabled slots."""
    extra, = battle.take(data, 8, '<I')
    if not extra:
        return []
    if extra < 12:
        raise ValueError('Animation table overlaps container header')
    result = []
    for slot in range(10):
        offset, = battle.take(data, extra + slot * 4, '<I')
        if offset < 40:
            raise ValueError('Animation sequence overlaps pointer table')
        values = []
        for i in range(MAX_SEQUENCE_WORDS):
            word, = battle.take(data, extra + offset + i * 2, '<h')
            values.append(word)
            if word == -1:
                break
        else:
            raise ValueError('Unterminated native animation')
        if values[0] == -1:
            continue
        validate_sequence(values)
        result.append(dict(slot=slot, values=values,
                           sequenceSha256=digest(struct.pack('<' + 'h' * len(values), *values))))
    return result


def validate_sequence(values):
    if len(values) < 7 or len(values) % 2 != 1 or len(values) > MAX_SEQUENCE_WORDS:
        raise ValueError('Malformed native sequence length')
    if values[-1] != -1 or -1 in values[:-1] or any(not -32768 <= v <= 32767 for v in values):
        raise ValueError('Unsupported native sentinel/short')
    x, y, width, height = values[:4]
    if x < 0 or y < 0 or width < 4 or width % 4 or height < 1 or x + width // 4 > 1024 or y + height > 512:
        raise ValueError('Native animation outside VRAM')
    if SCRATCH[0] + width // 4 > 1024 or SCRATCH[1] + height > 512:
        raise ValueError('Native scratch outside VRAM')
    if battle.overlaps([x, y, width // 4, height], [*SCRATCH, width // 4, height]):
        raise ValueError('Scratch aliases image: separate ordered word replay required')
    for at in range(4, len(values) - 1, 2):
        word, step = values[at:at + 2]
        # Native permits signed words; negative duration/steps need their own
        # proof and cannot be normalized to an unsigned delay or row rotation.
        if word < 0 or step < 0 or step // 16 > height:
            raise ValueError('Unsupported negative duration/step or over-height copy')


def initial_state(values):
    return (values[5], 0, 1, 0)  # _622, _64a, _65e, source row offset


def advance(values, state):
    """Exact positive-short subset of applyBattleStageTextureAnimations.

    Commands are returned in GPU execution order, not enqueue order. The source
    slot is queued ascending; the entire z=1 list is later executed backwards.
    """
    counter, elapsed, instruction, rotation = state
    x, y, width, height = values[:4]
    width //= 4
    at = 4 + (instruction - 1) * 2
    word, step = values[at:at + 2]
    direction, duration = word & 1, word >> 1
    if step & 15 and counter & 15:
        counter -= 1
        if counter == 0:
            counter, step = step, 16
        else:
            step = 0
    elapsed += 1
    if elapsed >= duration:
        elapsed = 0
        instruction = instruction + 1 if values[at + 2] != -1 else 1
    commands = []
    if step:
        rows = step // 16
        remainder = height - rows
        if direction == 0:
            commands = [(x, y, 960, 256 + rows, width, remainder),
                        (x, y + remainder, 960, 256, width, rows),
                        (960, 256, x, y, width, height)]
        else:
            commands = [(x, y + rows, 960, 256, width, remainder),
                        (x, y, 960, 256 + remainder, width, rows),
                        (960, 256, x, y, width, height)]
        rotation = (rotation + (rows if direction == 0 else -rows)) % height
    return (counter, elapsed, instruction, rotation), commands


def cycle(values, limit=MAX_TICKS):
    validate_sequence(values)
    states, seen, state = [], {}, initial_state(values)
    while state not in seen:
        if len(states) >= limit:
            raise ValueError('Native cycle exceeds discovery budget; retain hold')
        seen[state] = len(states)
        states.append(state)
        state, _ = advance(values, state)
    transient = seen[state]
    return dict(states=states, transient=transient, period=len(states) - transient,
                repeatState=state)


def state_at(current, tick):
    if tick >= len(current['states']):
        tick = current['transient'] + (tick - current['transient']) % current['period']
    return current['states'][tick]


def selection(rect):
    x, y, width, height = rect
    if width < 1 or height < 1 or x < 0 or y < 0 or x + width > 1024 or y + height > 512:
        raise ValueError('Dependency outside VRAM')
    return np.s_[y:y + height, x:x + width]


def read_audit(written, rect):
    area = selection(rect)
    return dict(vramRect=list(rect), words=rect[2] * rect[3],
                uninitializedWords=int(np.count_nonzero(~written[area])))


def decode_crop(vram, page, clut, uv_rect):
    """Native indexed words, full CLUT, /31 RGB and STP; exact texel edges."""
    divisor = 4 if page >> 7 & 3 == 0 else 2
    bits = 16 // divisor
    u0, v0, width, height = uv_rect
    us = np.arange(u0, u0 + width)
    vs = np.arange(v0, v0 + height)
    words = vram[(page & 16) * 16 + vs[:, None], (page & 15) * 64 + us[None, :] // divisor]
    indices = (words >> ((us[None, :] % divisor) * bits)) & ((1 << bits) - 1)
    palette = vram[clut >> 6, (clut & 63) * 16:(clut & 63) * 16 + (1 << bits)]
    values = palette[indices].astype(np.uint32)
    return np.stack(((values & 31) * 255 // 31, ((values >> 5) & 31) * 255 // 31,
                     ((values >> 10) & 31) * 255 // 31, (values >> 15) * 255), axis=2).astype(np.uint8)


def pixel_hash(rgba):
    return digest(struct.pack('<II', rgba.shape[1], rgba.shape[0]) + rgba.tobytes())


def consumer_contracts(data):
    model_offset, = battle.take(data, 0, '<I')
    parts, = battle.take(data, model_offset + 8, '<I')
    base = model_offset + 12
    contracts = collections.defaultdict(list)
    for part in range(parts):
        packets, count = battle.take(data, base + part * 28 + 16, '<II')
        position = base + packets
        for face in range(count):
            header, = battle.take(data, position, '<I')
            mode, size = header >> 24, (header >> 8 & 255) * 4
            if mode & 4:
                clut, = battle.take(data, position + 6, '<H')
                raw_page, = battle.take(data, position + 10, '<H')
                uv = [[data[position + 4 + i * 4], data[position + 5 + i * 4]] for i in range(4 if mode & 8 else 3)]
                contracts[raw_page & 0x19f, clut].append(dict(part=part, face=face, uv=uv,
                    abr=(raw_page >> 5) & 3, semiTransparent=bool(mode & 2), primitiveMode=mode))
            position += 4 + size
    return contracts


def binding_cycle(row, slots, base_vram, written, image_rect, faces):
    material = row['material']
    sample = material['sampledVramBounds']
    pad, palette = row['paddedSourceVramBounds'], row['paletteVramBounds']
    audits = {key: read_audit(written, rect) for key, rect in [('sampledImage', sample), ('conservativePadding', pad), ('fullPalette', palette)]}
    if any(a['uninitializedWords'] for a in audits.values()):
        raise ValueError('Additional uninitialized binding source; retain hold')
    dependencies = [s for s in slots if any(battle.overlaps(rect, s['rect']) for rect in (sample, pad, palette))]
    if [s['slot'] for s in dependencies] != [s['slot'] for s in row['intersectingAnimationSlots']]:
        raise ValueError('Fresh dependency identities differ from worklist')
    transient = max((s['cycle']['transient'] for s in dependencies), default=0)
    period = math.lcm(*(s['cycle']['period'] for s in dependencies))
    if transient + period > MAX_TICKS:
        raise ValueError('Consumer joint cycle exceeds discovery budget; retain hold')
    page, clut = material['page'], material['clut']
    divisor = 4 if page >> 7 & 3 == 0 else 2
    px, py = (page & 15) * 64, (page & 16) * 16
    u0, v0, u1, v1 = material['uvBounds']
    rect = image_rect
    left, top = max((rect['x'] - px) * divisor, u0 - battle.PADDING), max(rect['y'] - py, v0 - battle.PADDING)
    right, bottom = min((rect['x'] + rect['w'] - px) * divisor, u1 + battle.PADDING + 1), min(rect['y'] + rect['h'] - py, v1 + battle.PADDING + 1)
    context_uv = [left, top, right - left, bottom - top]
    sample_uv = [u0, v0, u1 - u0 + 1, v1 - v0 + 1]
    # Check page/UV/crop translation against the prior complete TIM/CLUT decoder
    # before factoring any animation. That decoder keeps the original upload
    # rectangle, including image/CLUT overlaps and the full palette requirement.
    original = battle.terrain.decode_material(base_vram, written, dict(image=image_rect), page, clut)
    origin_u, origin_v = (rect['x'] - px) * divisor, rect['y'] - py
    prior_crop = original.crop([left - origin_u, top - origin_v, right - origin_u, bottom - origin_v])
    if not np.array_equal(np.asarray(prior_crop), decode_crop(base_vram, page, clut, context_uv)):
        raise ValueError('Initial native UV/crop/RGBA interpretation differs from source decoder')
    source = base_vram.copy()
    frame_cache, frames, schedule, state_digest = {}, {}, [], hashlib.sha256()
    for tick in range(transient + period):
        rotations = tuple(state_at(s['cycle'], tick)[3] for s in dependencies)
        if rotations not in frame_cache:
            for s, rotation in zip(dependencies, rotations):
                area = selection(s['rect'])
                source[area] = np.roll(base_vram[area], rotation, axis=0)
            rgba = decode_crop(source, page, clut, context_uv)
            sampled_rgba = decode_crop(source, page, clut, sample_uv)
            conservative_hash = digest(source[selection(pad)].astype('<u2').tobytes())
            palette_hash = digest(source[selection(palette)].astype('<u2').tobytes())
            frame = dict(contextRgbaSha256=pixel_hash(rgba), sampledRgbaSha256=pixel_hash(sampled_rgba),
                         conservativePaddedWordsSha256=conservative_hash, fullPaletteWordsSha256=palette_hash)
            key = json_digest(frame)
            frames[key] = frame
            frame_cache[rotations] = key
        key = frame_cache[rotations]
        # RLE retains initial waits, transients and each source frame's exact
        # eligible stage-render invocation duration. It is not wall-clock FPS.
        if schedule and schedule[-1]['frame'] == key and tick != transient:
            schedule[-1]['ticks'] += 1
        else:
            schedule.append(dict(firstTick=tick, ticks=1, frame=key))
        for s in dependencies:
            state_digest.update(struct.pack('>5i', s['slot'], *state_at(s['cycle'], tick)))
    by_part = collections.defaultdict(list)
    for face in faces:
        by_part[str(face['part'])].append(face['face'])
    sampled_frames = {f['sampledRgbaSha256'] for f in frames.values()}
    family_contract = dict(contextSize=[context_uv[2], context_uv[3]], transientTicks=transient,
                           periodTicks=period, frames=frames, schedule=schedule)
    return dict(stage=row['stage'], page=page, clut=clut, modelSha256=row['modelSha256'],
                timSha256=row['timSha256'], parts=material['parts'], faces=material['faces'],
                faceIndicesByPart=dict(by_part), texturedFaceContractSha256=json_digest(faces),
                nativeBlendModes=sorted({(f['abr'], f['semiTransparent']) for f in faces}),
                uvBounds=material['uvBounds'], sourceContextUvRect=context_uv,
                paddedSourceVramBounds=pad, paletteVramBounds=palette, sourceReadAudit=audits,
                dependencySlots=[s['slot'] for s in dependencies],
                paddingOnly='sampled-image/animated-rectangle' not in row['dependencyTags'],
                familySha256=json_digest(family_contract),
                controllerCycleSha256=state_digest.hexdigest(), **family_contract,
                distinctContextFrameCount=len({f['contextRgbaSha256'] for f in frames.values()}),
                distinctSampledFrameCount=len(sampled_frames),
                distinctFullPaletteCount=len({f['fullPaletteWordsSha256'] for f in frames.values()}),
                isolatedStageCycleExplained=True, fullEncounterContractClosed=False,
                runtimeSelected=False, holdCleared=False,
                remainingContracts=['encounter-script-and-effect-upload-interference',
                                    'counterattack-darkening-parameter-and-transition-provenance',
                                    'renderer-presentation-timing-and-lifetime',
                                    'independent-art-and-gameplay-acceptance'])


def discover(files, worklist):
    if worklist['censusScriptSha256'] != digest((ROOT/'scripts/batch-envhd-battle-materials.py').read_bytes()):
        raise ValueError('Pinned census code changed; reconcile worklist first')
    if worklist['sourceBattleLedgerSha256'] != digest((ROOT/'integrations/envhd/production/battle-material-artwork.json').read_bytes()):
        raise ValueError('Pinned source battle ledger changed; reconcile worklist first')
    if worklist['summary']['animationOrContextBindings'] != 350:
        raise ValueError('Unexpected reviewed worklist; reconcile before discovery')
    rows = [r for r in worklist['bindings'] if r['material']['status'] == 'held-native-texture-animation']
    source_ledger = json.loads((ROOT/'integrations/envhd/production/battle-material-artwork.json').read_text())
    expected = {(s['stage'], m['page'], m['clut']): (s, m) for s in source_ledger['scenes']
                for m in s['materials'] if m['status'] == 'held-native-texture-animation'}
    row_keys = [(r['stage'], r['material']['page'], r['material']['clut']) for r in rows]
    if len(set(row_keys)) != len(rows) or set(row_keys) != set(expected):
        raise ValueError('Duplicate, missing or additional held consumer binding')
    for key, row in zip(row_keys, rows):
        scene, material = expected[key]
        if row['material'] != material or any(row[field] != scene[field] for field in
               ('modelSourceFile', 'modelSha256', 'timSourceFile', 'timSha256', 'textureAnimations', 'hasClutAnimation')):
            raise ValueError('Worklist consumer/source identity differs from reviewed ledger')
    ledger, stage_rows, differential_cases = [], [], []
    # Prioritize padding-only work while preserving the ledger's source identities.
    stages = sorted({r['stage'] for r in rows}, key=lambda stage: (not any(r['stage'] == stage and
                     'sampled-image/animated-rectangle' not in r['dependencyTags'] for r in rows), stage))
    for stage in stages:
        consumers = [r for r in rows if r['stage'] == stage]
        first = consumers[0]
        model = battle.terrain.bounded_read(files / first['modelSourceFile'])
        tim = battle.terrain.bounded_read(files / first['timSourceFile'])
        if digest(model) != first['modelSha256'] or digest(tim) != first['timSha256']:
            raise ValueError('Original source identity changed')
        fresh, _, = battle.scene(model, tim, stage)
        for row in consumers:
            if row['material'] not in fresh['materials'] or row['textureAnimations'] != fresh['textureAnimations']:
                raise ValueError('Material/source rectangle no longer matches worklist')
        if fresh['hasClutAnimation']:
            raise ValueError('CLUT sequences require separate replay')
        vram, written, tims = battle.terrain.upload_bank([(0, tim)])
        faces = consumer_contracts(model)
        slots = []
        for sequence in sequences(model):
            values = sequence['values']
            rect = [values[0], values[1], values[2] // 4, values[3]]
            current = cycle(values)
            source_audit = read_audit(written, rect)
            if source_audit['uninitializedWords']:
                raise ValueError('Uninitialized animation image')
            slots.append(dict(**sequence, rect=rect, cycle=current))
            differential_cases.append(dict(stage=stage, slot=sequence['slot'], values=values,
                ticks=len(current['states']) + 1, sourceWords=vram[selection(rect)].copy()))
        if [{k:s[k] for k in ('slot', 'rect', 'sequenceSha256')} for s in slots] != fresh['textureAnimations']:
            raise ValueError('Native sequence identity differs from census')
        for i, slot in enumerate(slots):
            if any(battle.overlaps(slot['rect'], other['rect']) for other in slots[i + 1:]):
                raise ValueError('Overlapping animations require joint ordered VRAM replay')
            if any(battle.overlaps(slot['rect'], [*SCRATCH, other['rect'][2], other['rect'][3]]) for other in slots):
                raise ValueError('Animation aliases another slot scratch')
        for row in sorted(consumers, key=lambda r: ('sampled-image/animated-rectangle' in r['dependencyTags'], r['material']['page'], r['material']['clut'])):
            if any(battle.overlaps(dep, [*SCRATCH, s['rect'][2], s['rect'][3]]) for dep in
                   [row['material']['sampledVramBounds'], row['paddedSourceVramBounds'], row['paletteVramBounds']] for s in slots):
                raise ValueError('Consumer reads shared scratch; retain native')
            contract = faces[row['material']['page'], row['material']['clut']]
            if len(contract) != row['material']['faces']:
                raise ValueError('Face consumer count drift')
            ledger.append(binding_cycle(row, slots, vram, written, tims[0]['image'], contract))
        stage_rows.append(dict(stage=stage, modelSourceFile=first['modelSourceFile'], modelSha256=digest(model),
            timSourceFile=first['timSourceFile'], timSha256=digest(tim),
            uploadOrder=[dict(kind=kind, vramRect=[tims[0][kind][k] for k in ('x', 'y', 'w', 'h')]) for kind in ('image', 'palette')],
            slots=[dict(slot=s['slot'], rect=s['rect'], sequenceSha256=s['sequenceSha256'],
                instructionCount=(len(s['values']) - 5) // 2, initialState=list(initial_state(s['values'])),
                transientTicks=s['cycle']['transient'], periodTicks=s['cycle']['period'],
                repeatedState=list(s['cycle']['repeatState']), sentinel='first-short-minus-one; loop-to-instruction-one',
                sourceReadAudit=read_audit(written, s['rect'])) for s in slots],
            jointControllerTransientTicks=max(s['cycle']['transient'] for s in slots),
            jointControllerPeriodTicks=math.lcm(*(s['cycle']['period'] for s in slots)),
            enqueueSlotOrder=[s['slot'] for s in slots], executionSlotOrder=[s['slot'] for s in reversed(slots)],
            animationRectsPairwiseDisjoint=True, animationRectsDisjointFromAllScratch=True,
            consumerRectsDisjointFromAllScratch=True,
            scratchReadContract='Each copying slot completely writes its scratch rectangle before the copy back; zero-row copies retain native no-op behavior. Initial scratch contents are unconsumed by these stage bindings.',
            sourceInitializedForAllAnimationReads=True))
    if len(ledger) != 350:
        raise ValueError('Incomplete binding census')
    sources = [
        ('src/main/java/legend/game/combat/Battle.java', 'public void applyBattleStageTextureAnimations('),
        ('src/main/java/legend/game/combat/Battle.java', 'public void FUN_800ec86c('),
        ('src/main/java/legend/game/combat/Battle.java', 'public void renderBattleStage('),
        ('src/main/java/legend/game/combat/Battle.java', 'public void loadStageTim('),
        ('src/main/java/legend/game/combat/Battle.java', 'public void backupStageClut('),
        ('src/main/java/legend/game/combat/Battle.java', 'public FlowControl scriptCopyVram('),
        ('src/main/java/legend/game/types/CContainer.java', 'public CContainer(final String name, final FileData data, final int subfileSize)'),
        ('src/main/java/legend/game/types/CContainerSubfile2.java', 'public CContainerSubfile2('),
        ('src/main/java/legend/core/gpu/GpuCommandCopyVramToVram.java', 'public GpuCommandCopyVramToVram('),
        ('src/main/java/legend/core/gpu/Gpu.java', 'private void tick()'),
        ('src/main/java/legend/core/gpu/Gpu.java', 'public void queueCommand(final int z,'),
        ('src/main/java/legend/core/gpu/Gpu.java', 'public void copyVramToVram('),
        ('src/main/java/legend/game/Graphics.java', 'public static void endFrame()'),
        ('src/main/java/legend/game/combat/SEffe.java', 'public static void applyScreenDarkening('),
    ]
    summary = dict(animationOrContextBindings=len(ledger), isolatedStageCyclesExplained=len(ledger),
        paddingOnlyBindings=sum(r['paddingOnly'] for r in ledger),
        paddingOnlyWithConstantSampledPixels=sum(r['paddingOnly'] and r['distinctSampledFrameCount'] == 1 for r in ledger),
        paddingOnlyWithChangingContextPixels=sum(r['paddingOnly'] and r['distinctContextFrameCount'] > 1 for r in ledger),
        paddingOnlyWithConstantContextPixels=sum(r['paddingOnly'] and r['distinctContextFrameCount'] == 1 for r in ledger),
        stages=len(stage_rows), activeSlotReferences=sum(len(s['slots']) for s in stage_rows),
        distinctSequenceHashes=len({s['sequenceSha256'] for stage in stage_rows for s in stage['slots']}),
        isolatedConsumerFamilies=len({r['familySha256'] for r in ledger}),
        isolatedContextImageHashes=len({f['contextRgbaSha256'] for r in ledger for f in r['frames'].values()}),
        isolatedControllerStates=sum(c['ticks'] - 1 for c in differential_cases),
        additionalUninitializedImageOrPaletteBindings=0,
        fullEncounterContractsClosed=0, runtimeSelected=0, holdsCleared=0,
        completeEnvhdAnimationImageJobDenominator=None,
        separateIncompletePaletteBindings=1)
    result = dict(schema=1, scope='Complete isolated stage-local positive-short sequence cycles for all 350 held battle bindings; hashes/metadata only. External encounter state remains unresolved; not an artwork job denominator or runtime selection.',
        sourceSelectionCheckpoint=worklist['sourceSelectionCheckpoint'],
        sourceWorklistSha256=json_digest(worklist), paddingSourceTexels=battle.PADDING,
        sourceDependencies=[dict(path=str(path.relative_to(ROOT)), sha256=digest(path.read_bytes())) for path in
                            [ROOT/'scripts/batch-envhd-battle-materials.py', ROOT/'scripts/batch-envhd-terrain.py']],
        timingUnit='eligible renderBattleStage invocation; pauses when stage render guard is false; no wall-clock FPS claim',
        nativeQueueContract='slots enqueue ascending at z=1; GPU executes z descending, each queue LIFO; therefore copying slots execute descending. Graphics.endFrame resets setMaskBit=false/drawPixels=ALWAYS at z=3 before stage z=1 copies in the normal frame path.',
        sourceMethods=[dict(path=path, signature=signature, bodySha256=digest(method_body(ROOT/path, signature).encode())) for path, signature in sources],
        externalStateLimitations=['scriptCopyVram, other TIM/effect uploads and GPU queue interleaving not enumerated per encounter',
            'applyScreenDarkening uploads parameterized 64x16 words at [448,240] from the stage backup; native fallback must preserve those transitions',
            'native startFrame uploads prior endFrame copy results; renderer frame/animation presentation and lifetime need independent integration validation',
            'isolated frame hash dedup does not authorize shared ownership, static replacement, generation or final art acceptance'],
        summary=summary, stages=stage_rows, bindings=ledger)
    return result, differential_cases


def write_new(path, value):
    # Never overwrite a reviewed ledger, symlink or source. New checkpoints are
    # explicitly named by the caller and written only after all discovery passes.
    with path.open('x') as output:
        output.write(json.dumps(value, indent=2) + '\n')


def publish_metadata(result, output, details):
    """Keep full hash schedules versioned in bounded per-stage metadata shards.

    These shards contain no original words, sequence payloads or image pixels.
    Deterministic gzip avoids duplicating hundreds of thousands of hashes in the
    readable summary; each expanded shard is independently hash-bound there.
    """
    if output.exists() or output.is_symlink() or details.exists() or details.is_symlink():
        raise ValueError('Refusing to replace an existing family checkpoint')
    if not details.resolve().is_relative_to(output.parent.resolve()):
        raise ValueError('Metadata shards must be children of the ledger directory')
    primary = {k:v for k,v in result.items() if k != 'bindings'}
    primary['schema'] = 2
    primary['bindings'], primary['stageMetadataShards'] = [], []
    encoded = {}
    for stage in sorted({r['stage'] for r in result['bindings']}):
        rows = [r for r in result['bindings'] if r['stage'] == stage]
        full = dict(schema=1, scope='Derived frame hashes and timing metadata only; no original pixels, VRAM words or sequence instructions.',
                    stage=stage, bindings=[dict(page=r['page'], clut=r['clut'],
                        familySha256=r['familySha256'], frames=r['frames'], schedule=r['schedule']) for r in rows])
        data = json.dumps(full, sort_keys=True, separators=(',', ':')).encode() + b'\n'
        compressed = gzip.compress(data, mtime=0)
        name = f'stage-{stage:03d}.json.gz'
        encoded[name] = compressed
        reference = dict(path=str(details.relative_to(output.parent)/name), encoding='gzip-json',
                         sha256=digest(compressed), expandedJsonSha256=digest(data),
                         bytes=len(compressed), expandedBytes=len(data))
        primary['stageMetadataShards'].append(dict(stage=stage, **reference))
        for row in rows:
            summary = {k:v for k,v in row.items() if k not in ('frames', 'schedule')}
            summary.update(frameTableSha256=json_digest(row['frames']),
                           frameScheduleSha256=json_digest(row['schedule']),
                           metadataShard=reference['path'])
            primary['bindings'].append(summary)
    details.mkdir()
    created = []
    try:
        for name, data in encoded.items():
            with (details/name).open('xb') as artifact:
                identity = os.fstat(artifact.fileno())
                created.append((details/name, identity.st_dev, identity.st_ino))
                artifact.write(data)
        write_new(output, primary)
    except Exception:
        # Only files created exclusively by this invocation are removed. A
        # concurrent writer cannot be adopted or overwritten by recovery.
        for artifact, device, inode in created:
            try:
                current = artifact.lstat()
                if not artifact.is_symlink() and (current.st_dev, current.st_ino) == (device, inode):
                    artifact.unlink()
            except FileNotFoundError:
                pass
        try:
            details.rmdir()
        except OSError:
            pass
        raise
    return primary


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', type=Path, required=True)
    parser.add_argument('--worklist', type=Path, default=ROOT/'integrations/envhd/production/battle-native-discovery-worklist.json')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--details-dir', type=Path, help='Publish readable summary plus complete, compressed per-stage hash/timing metadata')
    parser.add_argument('--java-home', type=Path, required=True, help='Headless Java compiler/runtime for source-extracted CPU differential verification')
    args = parser.parse_args()
    if args.output.resolve().is_relative_to(args.files.resolve()) or (args.details_dir and args.details_dir.resolve().is_relative_to(args.files.resolve())):
        raise ValueError('Do not write into original extraction')
    worklist = json.loads(args.worklist.read_text())
    result, cases = discover(args.files, worklist)
    verify_spec = importlib.util.spec_from_file_location('native_replay', ROOT/'scripts/verify-envhd-battle-native-replay.py')
    verify = importlib.util.module_from_spec(verify_spec)
    verify_spec.loader.exec_module(verify)
    result['cpuDifferentialVerification'] = verify.verify(cases, args.java_home)
    result['sourceWorklistFileSha256'] = digest(args.worklist.read_bytes())
    result['discoveryScriptSha256'] = digest(Path(__file__).read_bytes())
    result['nativeReplayVerifierSha256'] = digest((ROOT/'scripts/verify-envhd-battle-native-replay.py').read_bytes())
    if args.details_dir:
        publish_metadata(result, args.output, args.details_dir)
    else:
        write_new(args.output, result)
    print(json.dumps(result['summary'], indent=2))


if __name__ == '__main__':
    main()
