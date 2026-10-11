#!/usr/bin/env python3
"""Account for every battle stage and static source without hiding dynamic gaps."""
import argparse
from collections import Counter
import importlib.util
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('battle_presentation_audit', ROOT / 'scripts/verify-envhd-battle-presentation.py')
verify = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verify)


def audit(files):
    production = ROOT / 'integrations/envhd/production'
    names = ['battle-material-artwork.json', 'battle-material-runtime-artwork.json', 'battle-skies.json',
             'battle-stage-family-ledger.json', 'battle-presentation-artwork-v1.json']
    snapshots = {n: verify.battle.terrain.bounded_read(production / n, 32 * 1024 * 1024) for n in names}
    inputs = {n: json.loads(data) for n, data in snapshots.items()}
    history = inputs['battle-material-artwork.json']
    selected = inputs['battle-material-runtime-artwork.json']
    skies = inputs['battle-skies.json']
    dynamic = inputs['battle-stage-family-ledger.json']
    scenes, masters, empty = verify.battle.census(files)
    if history['scenes'] != scenes or history['emptyModelSlots'] != empty:
        raise ValueError('Battle presentation census changed')
    verified = verify.preflight(files, production / 'battle-presentation-artwork-v1.json')
    candidates = {c['candidate']['decodedRgbaSha256'] for c in verified['verified']}
    stages = []
    for scene in scenes:
        atlas = next((s for s in selected['scenes'] if s['modelSha256'] == scene['modelSha256']), None)
        statuses = Counter(m['status'] for m in scene['materials'])
        stages.append(dict(stage=scene['stage'], modelSha256=scene['modelSha256'], timSha256=scene['timSha256'],
            materialBindingCount=len(scene['materials']), sourceStatuses=dict(statuses),
            selectedStaticBindings=atlas['hdBindings'] if atlas else 0,
            atlasSize=atlas['atlasSize'] if atlas else None,
            authoredNewPresentationMasters=sorted({m['decodedRgbaSha256'] for m in scene['materials']
                if m.get('decodedRgbaSha256') in candidates}),
            nativeSceneAccepted=False, finalBeautyAccepted=False))
    assets = []
    for record in history['assets']:
        key = record['decodedRgbaSha256']
        if key not in masters:
            raise ValueError('Battle presentation source disappeared')
        status = ('new-bespoke-awaiting-independent-review' if key in candidates else
                  'existing-bespoke-development-selection' if record['reviewStatus'] == 'intent-revision-needed' else
                  'neural-baseline-needs-presentation-review')
        assets.append(dict(decodedRgbaSha256=key, sourceSize=record['sourceSize'], bindings=record['bindings'],
                           intent=record['review']['intent'], presentationStatus=status,
                           nativeSceneAccepted=False, finalBeautyAccepted=False))
    for n, data in snapshots.items():
        if verify.battle.terrain.bounded_read(production / n, 32 * 1024 * 1024) != data:
            raise ValueError('Battle presentation input changed during audit')
    return dict(schema=1, scope='Whole battle presentation workstream; material bindings, unique images and stage states are different units.',
        goalComplete=False, allBattlePresentationAccepted=False,
        sourceControls={n: verify.battle.terrain.digest(v) for n, v in snapshots.items()},
        counts=dict(nonemptyModelStages=len(scenes), emptyModelSlots=len(empty),
                    materialBindings=sum(len(s['materials']) for s in scenes),
                    protectedPilotBindings=history['protectedPilotBindings'],
                    selectedNewStaticBindings=selected['runtimeSelectedBindings'],
                    selectedNewStaticMasters=selected['runtimeSelectedMasters'],
                    previouslySelectedPaintedMasters=selected.get('paintedSelectedMasters', 0),
                    newBespokeCandidates=len(candidates),
                    newlySelectedBespokeMasters=selected.get('presentationSelectedMasters', 0),
                    baselineMastersStillNeedingPresentationReview=len(assets)-len(candidates)-selected.get('paintedSelectedMasters', 0),
                    pendingCandidateReviews=len(candidates),
                    dynamicOrContextBindingHolds=dynamic['summary']['animationOrContextBindings'],
                    incompletePaletteBindingHolds=dynamic['summary']['separateIncompletePaletteBindings'],
                    uniqueDecodedSkyArtwork=skies['uniqueDecodedArtwork'],
                    skySourceVariants=skies['total'],
                    completeDynamicImageJobDenominator=None,
                    nativeSceneAccepted=0, finalBeautyAccepted=0),
        engineConstraints=['Preserve scene-authored live lighting and source STP/discard/visible-black.',
                           'Reuse existing source-bound 4x atlas, 32-pixel gutter, HD mip/anisotropic filtering and first-visit prewarming.',
                           'Static repaint selection must preserve all model/TIM/page/CLUT/UV mappings, atlas sizes and selected coverage.',
                           'Do not freeze animated surfaces, palette effects, counterattack darkening, script uploads or inherited VRAM states.',
                           'Use stage view/seam/attack camera tests and physical Deck frame-time/residency evidence before final acceptance.'],
        stages=stages, staticSources=assets)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--files', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    result = audit(args.files)
    # Metadata contains hashes, binding geometry and honest status, never source pixels/words/scripts.
    verify.battle.write_json(args.output, result)
    print(json.dumps(result['counts'], indent=2))
