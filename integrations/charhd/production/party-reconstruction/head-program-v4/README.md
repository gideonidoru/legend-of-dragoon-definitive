# Party heads · registered surface correction

**Public development assets. Not visually accepted or selected for installation.**
The owner must approve all nine heads before this experiment enters main or a
release. Dart's existing accepted resources remain unchanged; this directory
contains sixteen field/battle bindings for the other eight characters.

The rejected Haschel screenshot reproduces without any original actor geometry:
unregistered high-resolution facial paint overlays a differently sized inferred
face. The correction uses a coherent neural base and one landmark-registered
frontal face replacement. Ear paint is confined to authored ear volumes. Profile
and rear passes transfer hair/ear detail while keeping five facial feature
regions unchanged. They never add profile eyes, mouths or chins to the front.

The inferred base still contains its own facial interpretation outside the
visible frontal replacement. Occluded lower-jaw paint can remain visible in a
quarter/profile view. One registered front source and zero added profile-face
layers therefore do not prove that the whole head has one coherent face.

The set still fails the full artistic gate. Haschel's lower jaw was relaxed in
a bounded region without clipping faces or changing UV topology, but profile
skin/sideburn transitions remain rough. Rose's original fringe and Meru's native
ponytail still need a coherent solution. Several inferred rear/underside surfaces
need cleanup. These are geometry/texture inspection candidates, not release art.

## Contents and reproduction

Each character stores custom geometry, a 2K custom texture, source-bound head
packs, UV bindings and field/battle fit receipts. `authoring` stores hash-bound
paint targets, custom source paintings and their available generation receipts,
front/profile correspondence controls and filtered original-size fit controls.
The original game's full actor geometry, texture pixels and comparison renders
are excluded. `source-audit.json` contains source metadata/checksums only.

For a character other than Haschel, use `head-paint-four-views-v1.png` in both
bakes. Haschel uses `head-paint-frontal-v1.png` then `head-paint-profile-v1.png`.
With NumPy, SciPy, Pillow and trimesh in a local Python environment:

```sh
python scripts/bake-registered-head-paint.py \
  --mesh <character>/authoring/paint-target.glb \
  --base <character>/authoring/coherent-basecolour.png \
  --paint <character>/authoring/head-paint-four-views-v1.png \
  --controls <character>/authoring/front-registration.json \
  --output /path/to/new/front-bake

python scripts/bake-head-surface-views.py \
  --mesh <character>/authoring/front-registered.glb \
  --base <character>/authoring/front-registered-basecolour.png \
  --paint <character>/authoring/head-paint-four-views-v1.png \
  --controls <character>/authoring/profile-registration.json \
  --output /path/to/new/surface-bake

python scripts/fit-party-heads.py --character <character-name> \
  --files /path/to/local/files \
  --mesh <character>/authoring/registered-head.glb \
  --landmarks <character>/authoring/native-landmarks.json \
  --output /path/to/new/private-fit
```

Resolve `<character>` beneath this directory. Bake receipts pin input/control
hashes and tool versions; changing an input requires an explicit new authoring
revision. Fit outputs also include original-body controls for private inspection:
copy only custom parts, UV bindings, fitted head GLBs, texture and fit receipt
into public assets. The normal package does not read this development registry.

Haschel's authoring chain includes `scripts/sculpt-head-neck.py`, then
`scripts/smooth-head-jaw.py`. Stored controls and intermediate custom meshes
allow the jaw positions/topology/UVs to reproduce exactly. That numerical result
is not an anatomical verdict. Some earlier Haschel source prompts could not be
recovered; the corresponding paint receipt records this provenance gap.

## Verified checkpoint

- Sixteen head bindings: finite vertices/normals, 2K textures, checked UV/asset
  hashes, authored original crown/chin heights and original pose frames.
- All 192 actual-source CPU construction combinations passed: eight characters,
  field/battle, indexed/RGBA/mapped materials, ModelsHD off/on and candidate
  off/on. Exactly one intended head is painted; source tables stay unchanged.
- Twelve accepted Dart parts and all nineteen selected runtime files unchanged.
- Fifteen CharHD unit tests passed. The draft explicit hair-cutout contract also
  passed windowless field/battle GPU pixel checks on Apple M5 Max.
- Supported full build passed using the existing video source archive verified
  against the FMV lock. Its initial network fetch failed; that archive supplied
  the same pinned bytes through the supported `fmvHdAssets` property.
- Private matched lossless comparisons cover all nine heads and original bodies,
  field/battle, both quarter/profile sides, rear and final stored pose. They are
  offline renders. They do not show a full-body CharHD upgrade for every actor.

No native gameplay, broad third-party mod or physical Steam Deck validation is
claimed. See the [artistic audit](../../../../../docs/definitive/PARTY_HEADS_ARTISTIC_AUDIT.md).
The [face registration checkpoint](../../../../../docs/definitive/CHARHD_FACE_REGISTRATION_AUDIT.md)
records the remaining anatomy defects. Optional [animated hair development](../hair-program-v1/README.md)
preserves Rose/Meru native motion and remains outside normal runtime selection.
Custom deliverables are public under the project asset policy; preserve AGPL v3,
original character attribution and the existing TripoSR MIT/provenance notices.
No discs, original data, saves, credentials, model weights or local runtimes ship.
