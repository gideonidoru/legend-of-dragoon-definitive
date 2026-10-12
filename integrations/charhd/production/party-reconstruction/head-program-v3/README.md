# Party head alignment study · v3

**Superseded development study. Not ready for visual acceptance or release.**
The current registered-surface correction is [v4](../head-program-v4/README.md).
None of this directory is selected by the normal CharHD package. Accepted Dart
remains in `integrations/charhd/runtime-assets` unchanged. The owner must approve
all nine characters before promotion to main or release.

The shared reconstruction runner now defaults to `--paint-mode neural`: one
coherent surface texture, without an unregistered illustration blended over it.
`--paint-mode legacy-projection` exists only to reproduce the old experiment.
Do not use it for new party heads. Historical accepted Dart resources are not
rebaked by this change.

Each character has a neutral frontal authoring image and a separate paint input.
These are illustrations, not evidence of runtime quality. `authoring-v3` stores
custom meshes, the coherent 2K surface bake, hash-bound facial registration,
original-size fitting controls and receipts. `candidate-v3` stores custom-only
field/battle head packs, UV bindings, 2K paint and editable fitted heads. Full
original actor controls and original-body comparison pixels stay outside Git.
The Haschel target includes an experimental jaw cleanup; it is not an accepted
anatomical model. Some early Haschel illustration prompts were not retained;
their receipts state that provenance gap explicitly.

## Reproduce the registered paint

Use the local Python authoring environment with NumPy, trimesh, Pillow and SciPy.
From the checkout, substitute a character directory for `<character>`:

```sh
python scripts/bake-registered-head-paint.py \
  --mesh integrations/charhd/production/party-reconstruction/head-program-v3/<character>/authoring-v3/paint-target.glb \
  --base integrations/charhd/production/party-reconstruction/head-program-v3/<character>/authoring-v3/coherent-head-basecolour.png \
  --paint integrations/charhd/production/party-reconstruction/head-program-v3/<character>/head-paint-four-views-v1.png \
  --controls integrations/charhd/production/party-reconstruction/head-program-v3/<character>/authoring-v3/paint-registration.json \
  --output /path/to/new/study
```

For Haschel use `head-paint-frontal-v1.png` as the paint input. The tool checks
input hashes, matches authored eye/nose/mouth/chin landmarks, rejects a folding
facial mapping and replaces frontal facial paint completely. It never adds
profile eyes, ears or jaws to another surface. Unobserved surfaces retain the
coherent base texture. Hair transitions can blend outside the protected facial
region; that does not establish artistic quality.

To refit the custom head, run `scripts/fit-party-heads.py` with the locally
extracted original files, `authoring-v3/registered-head.glb` and
`authoring-v3/native-landmarks.json`. Use a new private output directory because
that step also produces unchanged full-actor comparison controls. Only its
allowlisted custom parts, UVs, paint, fitted head GLBs and receipt belong here.
The fit retains the source anatomical frame and native bone pose. Crown/chin
are 3D points; pitch contributes to their posed vertical span. The frontal
framing anchor is selected before posing, rather than using neutral bounds on
an already rotated face. Rejected infinite neck clipping is refused.

## Evidence and remaining failures

- Eight coherent 6,000-triangle base reconstructions completed offline. The
  registered Haschel cleanup has 5,774 triangles; the other seven retain 6,000.
- Sixteen source-bound head fits match the authored original crown/chin heights.
  Their native pose frames are orthonormal and preserve original bone rotations.
- Registered front paint has one facial source. All eight sampled facial warp
  grids have positive determinants; interpolation reproduces authored anchors.
- Rose's paint reproduces pixel-for-pixel from these stored portable inputs.
- The current source probe passes 192 CPU construction combinations across
  indexed, RGBA and mapped texture routes, with ModelsHD and CharHD on/off.
  Normal CharHD resources remain selected separately. This is not gameplay.
- Matched lossless head/body renders exist privately for all nine characters,
  including the exact accepted Dart head as a benchmark: 198 comparisons.
- **Still fail:** Rose's retained native fringe and Meru's retained native
  ponytail overlap the generated hair. Do not suppress those pieces separately
  unless ownership of the entire head family can be applied atomically.
- **Still fail:** some napes, underside jaws, ear transitions and hair surfaces
  are rough. A pleasant front portrait does not meet the multi-angle gate.
- Field model UV/CLUT coordinates were checked in isolation; actual scene
  presentation, native animation and Steam Deck behavior remain unverified.

Custom code/assets retain the project's AGPL v3 terms. TripoSR is MIT licensed;
its pinned source, model and dependency provenance is in reconstruction receipts
and the existing reconstruction documentation. No model weights or authoring
runtimes are included. Preserve upstream/community attribution. Publication
permission is separate from visual acceptance and gameplay/Deck validation.
