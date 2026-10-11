# Authored skin experiment

**Unaccepted development work. Haschel only. No normal package, main or release selection.** Accepted Dart remains unchanged. The owner must approve the full nine-character set before this head experiment becomes installed content.

A registered paint over an inferred head can retain an older chin or beard on surfaces that the frontal camera cannot see. Compressing that inferred neck also folded the underside. This study replaces the inferred facial/jaw/neck surface with a continuous authored surface and a uniform skin base. It retains selected custom hair geometry and uses one registered frontal facial painting. Ears have their own authored surfaces and bounded profile paint; profile eyes, noses, mouths and chins are excluded.

This is a useful geometry experiment, not a finished character. The ears still need a more natural root and texture transition; the lateral jaw/hair edge and exposed neck skin need refinement. Haschel's before/after body comparisons remain private because they contain original game textures. The other seven unaccepted heads remain in `head-program-v4`; this does not claim all-party coverage.

## Reproduce

The experiment reads custom public authoring inputs in `head-program-v4/haschel/authoring`. It does not read game files. A local environment needs NumPy, SciPy, Pillow, trimesh, xatlas and OpenCV. These are authoring dependencies, not installer or game dependencies.

```sh
python experiments/charhd-authored-skin/build-haschel-study.py \
  --output /path/to/new/haschel-authored-study
```

The output must be new. `source-receipt.json` records custom input and tool hashes. `frontal-skin-baker.py` rejects a nonuniform base: inherited facial paint cannot enter this skin-base pass. Frontal ear regions are excluded and filled from neighboring custom paint before the separate profile-ear pass. Five frontal feature regions remain locked during that pass. The numerical receipts are not an artistic acceptance decision.

The builder exports seam-smoothed normals in `head-smoothed.glb`. For private original-body fitting, use `scripts/fit-party-heads.py` with the stored authored crown/chin/native frame controls and a mesh hash updated for that exact output. Full actor controls created by that tool must stay outside Git. Only custom head parts, UV bindings, textures, GLBs and metadata are copied here.

## Limits and verification

The current fitted head contains 16,210 triangles and a 2048 × 2048 opaque atlas. That is larger than the previous 6,000-triangle draft; neither a Steam Deck performance budget nor visual acceptance is established. Do not select it automatically or infer gameplay readiness from the loader checks.

The field and battle bindings preserve their original crown/chin heights, native bone/anatomical frames and finite stored pose samples. Original body geometry and selected runtime resources are untouched. The initial 4096 × 2048 atlas was rejected by the loader pixel budget. It was repacked into the existing 2048 × 2048 limit; no engine limit was raised. The 24 Haschel CPU loader combinations passed: field/battle, indexed/RGBA/mapped material routes, ModelsHD off/on and candidate off/on. Source data remained unchanged and texture dimensions/neutral detail colours matched. The isolated probe logged a missing default font but completed its geometry checks. Existing CharHD tests were up-to-date, not freshly rerun. The public builder reproduced the current geometry/atlas byte-for-byte; a nonuniform base was rejected before output. Native gameplay, animation appearance and Steam Deck validation remain outstanding.

Custom authoring and geometry are public under the project's existing license/attribution. Original game binaries, texture pixels, full actor controls and comparison screenshots are excluded.
