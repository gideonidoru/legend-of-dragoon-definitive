# Character remaster direction

The owner reviewed the Haschel and Meru comparisons and judged the existing texture passes too subtle. The selected direction is to **modernize the original designs with much clearer faces, richer materials and smoother models**. The previous successful pixel/layout checks are still useful compatibility evidence; they are not artistic acceptance. Neither higher texture resolution nor a larger triangle count satisfies this requirement by itself.

The owner further defined the target as a **2026 AA game with a slick, intentional retro style**. The [neural visual strategy](NEURAL_VISUAL_STRATEGY.md) prioritizes neural-assisted reconstruction baked into coherent assets, paired with measured native rendering. Real-time generative rendering remains research rather than a promised Deck feature. [Editable original-model references](MODEL_REFERENCE_EXPORT.md) are available privately for both inspected characters. The [first local Haschel head reconstruction](LOCAL_MODEL_RECONSTRUCTION.md) now has a [12,240-triangle connectivity-checked candidate](MESH_TOPOLOGY_REFINEMENT.md). Its [full-character attachment study](HEAD_ATTACHMENT_STUDY.md) verifies all 12 sampled transforms in three private fits and exposes detached rear strips that require art correction. Earlier reductions were held after stronger geometry checks found defects. Hair, neck and UVs need correction; art acceptance, the broader animation catalog and native replacement loading remain open.

## What changed in the investigation

`scripts/refine-model-surfaces.py` now makes a separate, private geometry feasibility study. It triangulates using the original quad order and subdivides eligible rigid parts with Loop weights. Open borders and sharp creases remain fixed; face-local UV/color attributes and palette identities remain separate. The original part order and sampled keyframe transforms are retained. Degenerate or non-manifold parts remain unchanged and are reported explicitly. Two study iterations, finite parameters, per-part bounds and a whole-model triangle budget are enforced.

The tool emits offline images and a source-bound report. It does not write a replacement TMD, activate a mod, generate final normals, change engine behavior or claim native animation equivalence. The report uses a distinct `definitive-private-surface-study-1` pipeline so the existing original-geometry inspection contract cannot silently accept changed geometry.

Eight original synthetic fixtures cover pinned joint boundaries and creases, subdivision/quad order, unchanged input data, material seams, untextured faces, duplicate-face/disconnected-vertex-fan/degenerate rejection, parameter/reference validation, short-animation view selection and part counts. They join the twenty-three existing visual checks in CI. No retail assets or model weights are needed by those fixtures. Independent review identified disconnected vertex fans/duplicate faces that the initial edge-only guard missed, and lost camera selections in short animations. The guard now validates a connected disk/boundary fan at each referenced vertex, rejects duplicates, and retains all three camera selections with unique view-indexed filenames. Original regression fixtures cover these corrections.

## Private rounds 22–23

Round 22 contains the stronger material packs; round 23 contains the final surface reports after the independent review corrections. The same previously inspected combat models and animation 0 were used. Stronger 4× Illustration texture candidates use strength 1.0 with the earlier original-head palette selection. The tool and bin/parameter hashes remain pinned. Python and the independent Java reader accept both complete material packs, including 3,668,992 material texels checked for STP/discard/visible black and the preserved original regions. This is material validity, not approval of the stronger reconstruction.

| Sample | Original triangles after quad split | Surface-study triangles | Original / study part count | Unchanged unsafe part | Atlas RGBA allocation |
| --- | ---: | ---: | --- | --- | ---: |
| Haschel | 791 | 10,451 | 17 / 17 | 7, overlapping faces | 11 MiB |
| Meru | 806 | 10,211 | 22 / 22 | 8, overlapping faces | 9.125 MiB |

At keyframes 0, 6 and 11 with three camera angles, the stronger texture-only controls have zero missing/extra coverage pixels. The surface study deliberately changes the silhouette: Haschel loses 4,460–5,608 coverage pixels and gains 3–16; Meru loses 3,391–5,739 and gains 10–65 in these 384×448 offline views. That shrinkage is evidence of a tradeoff, not a quality improvement score. Camera/framing is shared across the original, texture-only and surface images.

Source/output manifests are retained privately, outside Git:

| Private record | SHA256 |
| --- | --- |
| Haschel stronger material manifest | `46c079da6cfd2b7fbb57d107b41588aafe122453cd767fe648d935bb8200386a` |
| Meru stronger material manifest | `ce1e51c4ca6040a5300bff3590dd253b13d694c776c215a0fec79f4743583dc9` |
| Haschel surface report | `dc8199e1e1c7a11e1900c46ecc17231105df983d7c86e167db9843d9c58d54cc` |
| Meru surface report | `bd4fa3b42f404a94c378a14571db5b13278c82fcf7ea6be39c99f96289612207` |

The geometry result is more visible, but automatic subdivision narrows limbs/clothing and leaves the overlapping head parts unchanged. The stronger textures smooth patterns more aggressively without reconstructing the missing facial design. Neither candidate is selected for installation or the default preset. Faithful remains the original route.

## Next visible increment

1. Prioritize a deliberately reconstructed head and face, matching the original proportions, hair, expression, colors and recognizable design. Retain source-part pivots and original animation inputs. The [rear attachment refinement](REAR_ATTACHMENT_REFINEMENT.md) checks seven available motions. Its [moving-root follow-up](NATIVE_ATTACHMENT_BINDING.md) verifies an authoring boundary in 496 actual-source-method poses and an animated interchange; deliberate hair/neck correction and native rendering remain open. Use the delivered private source references to develop a coherent multi-view candidate before defining a native import format.
2. Remodel selected torso/limb/cloth surfaces with controlled silhouettes, seams and joint boundaries; do not apply subdivision indiscriminately. The [body finish study](BODY_FINISH_STUDY.md) now preserves authored joint regions, balances closed arm volume, retains the held skirt source and verifies an animated private interchange. Its material/light comparison is clearer, but uniform skin, shoulder form and playing-size/native acceptance still need work. Texture painting must make skin, fabric and metal read distinctly at normal 1280×800 playing distance, alongside the existing HD backgrounds.
3. Establish the [native material seam](NATIVE_MATERIAL_INTEGRATION.md), compare an original nearest control first, then the reconstructed candidate. Verify actual game lighting, STP/blending, normals, occlusion, seams, sampled animation and resource teardown. Offline pose images alone do not establish these behaviors.
4. Measure decoded/uploaded/resident memory, p95/p99 frametime and power on a real Deck. Prefer precomputed assets to per-frame neural inference. Set asset budgets from those measurements, not the theoretical APU capability or these offline triangle counts.

Acceptance requires a clearly visible improvement at playing size, close-up fidelity to the original design, reliable animation/transitions, measured Deck cost and player/community feedback. The owner-approved direction is not community approval. Model coverage beyond these two combat samples, authored replacement artwork, native integration and physical performance remain open.

## Reproduce privately

Generate a source-bound packed comparison with the existing pinned `pack-model-materials.py` tool; stronger texture processing is an experiment, not the default. Then supply the unchanged extracted model, matching TIM and original animation:

```sh
python3 scripts/refine-model-surfaces.py \
  --model /PRIVATE/characters/haschel/models/combat/32 \
  --tim /PRIVATE/characters/haschel/textures/combat \
  --animation /PRIVATE/characters/haschel/models/combat/0 \
  --packed-candidate /PRIVATE/haschel-strong-curated \
  --iterations 2 --output /PRIVATE/haschel-surface-study
```

The pinned Pillow/NumPy versions are in `scripts/requirements-visual.txt`. All generated reports and game-derived images stay outside the checkout. The public alpha engine/installer payloads remain unchanged.
