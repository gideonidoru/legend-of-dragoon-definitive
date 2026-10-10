# Editable original-model references

The next visual milestone is one unmistakably modernized character, with a clearer face, controlled smoother forms and richer materials that fit the painted backgrounds. The [modern retro strategy](NEURAL_VISUAL_STRATEGY.md) explains where neural-assisted authoring and runtime rendering fit. This export is preparation for that work, not finished character art.

## Delivered scope

[`export-model-reference.py`](../../scripts/export-model-reference.py) reads an unchanged bounded model, matching standard keyframe file and an independently source-bound **nearest** palette atlas. It writes a self-contained private GLB and manifest. Original rigid parts, local vertex coordinates, part order and local pivot origins are retained. Per-face corners are duplicated to retain UV/color/material seams. Palette groups, source face IDs, original vertex IDs and raw face colors are recorded in primitive metadata. Quads use the existing `(0,1,2)` / `(1,3,2)` triangle order. No subdivision, inferred bone hierarchy, skin weights or automatic face reconstruction is added.

The format uses [glTF 2.0](https://registry.khronos.org/glTF/specs/2.0/glTF-2.0.html). One root applies `(x,y,z) → (x,-y,-z)` and a 0.01 meter authoring scale; source-local data remains unchanged. This scale is a convenience, not a measured character height. Part rotations use XYZW quaternions for the `Rz * Ry * Rx` order in the engine's [`Keyframe0c`](../../src/main/java/legend/game/types/Keyframe0c.java). The included animation is explicitly an inspection sequence: one second per source keyframe with STEP interpolation. It does not claim native playback speed, hierarchy or interpolation equivalence.

Materials use the [unlit glTF extension](https://github.com/KhronosGroup/glTF/tree/main/extensions/2.0/Khronos/KHR_materials_unlit), nearest sampling, clamping and double-sided reference surfaces. All-zero engine texels become discarded opacity; other engine texels, including visible black, become opaque. Original STP and primitive blend modes are **not** reproduced as glTF opacity. Raw face colors are retained in metadata; displayed vertex colors are clipped to glTF's range. Source normals are not exported. Consequently, this preview cannot establish native lighting, exact color response, occlusion or transparency behavior.

## Verification

Five original synthetic fixtures cover independently decoded GLB source corners and sampled quaternion transforms, palette/UV/color seams, original quad order, visible black/STP/discard conversion, short-animation timing and source identity rejection before output. They join the existing 31 visual fixtures in CI. These tests use no retail art or neural weights.

All 36 visual fixtures and the workflow linter passed locally for this increment. Independent Standards and Spec reviews found no actionable issues; the Spec reviewer also reran the five new fixtures. Those reviews did not independently repeat private vendor research or the external format validator. Engine code and public installer payloads are unchanged.

Private round 25 references were produced from the previously inspected Haschel/Meru combat model 32, animation 0 and unchanged 2× nearest atlas controls:

| Sample | Parts | Keyframes | Decoded corner/pose samples | GLB bytes | GLB SHA256 |
| --- | ---: | ---: | ---: | ---: | --- |
| Haschel | 17 | 12 | 22,716 | 241,580 | `f416e5678351fee70d82696136e8e484c36e7fef84badf3422a51806b78fb0eb` |
| Meru | 22 | 12 | 22,896 | 246,556 | `657126ba1bbd1e81b08bc656870ccaf7084edf3f30fc3db5a845a26960ab5fb9` |

An independent binary decoder and quaternion reconstruction checked every exported source corner at all twelve sampled poses against the existing source inspector. Maximum position difference was below `8 × 10⁻⁷` authoring meters, attributable to float32 storage. That is source-sample consistency, not native animated gameplay proof.

Both files also passed the official [Khronos glTF Validator](https://github.com/KhronosGroup/glTF-Validator), version `2.0.0-dev.3.10`, with **zero errors, zero warnings and zero hints**. Each retains one informational notice for the non-power-of-two atlas; nearest/clamped sampling is explicit. The validator archive was fetched from the official repository-linked npm package, checked against its registry SHA512 integrity, and retained with its Apache 2.0 license/notices outside Git. Archive SHA256: `4e03dbdc3bc0d1342afd5d2d7ae341a7e3474502ccb872b02dbbaddaee0fdec6`. No assets were uploaded to an online validator. Blender opening/editing has not been tested on this host.

## Private reproduction and editing

Use the versions in [`requirements-visual.txt`](../../scripts/requirements-visual.txt). Generate or select the exact-source nearest pack with the existing palette packer, then:

```sh
python3 scripts/export-model-reference.py \
  --model /PRIVATE/characters/haschel/models/combat/32 \
  --tim /PRIVATE/characters/haschel/textures/combat \
  --animation /PRIVATE/characters/haschel/models/combat/0 \
  --packed-control /PRIVATE/haschel-nearest-control \
  --output /PRIVATE/haschel-source-reference
```

Choose a new output folder outside the checkout; existing output is not overwritten. The GLB is bounded to 64 MiB. The manifest pins model, TIM, animation, engine atlas, exporter and output hashes plus dependency versions and part inventory. Source-derived GLBs, textures and reports remain private.

Import the reference into a glTF-capable authoring tool. Keep the original object as a reference and work on a separate candidate. Preserve named source-part origins and joint boundaries initially; assess face shape, hair, eyes, silhouette and costume details across the sample poses. Do not treat the reference as a skinned rig or the STEP sequence as final animation. New authored geometry will need suitable normals and a separately specified native replacement/import contract before it can run in the game. That contract and the first visibly reconstructed face are still open.
