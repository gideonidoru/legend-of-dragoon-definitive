# Coherent body finish study

Checked 2026-10-10 against source baseline `19b3f57959e5b37bda50dd3ee2276a5c3a0471c4` and retained working tooling. The [moving-root study](NATIVE_ATTACHMENT_BINDING.md) established a better head/strip authoring boundary. This follow-up makes the body a more coherent companion to that head: selected limb/clothing surfaces, fixed joint regions, preserved arm volume, and a separately labelled material/light comparison. It is private authoring work, not an installed character replacement or accepted final art.

## Geometry choices and rejected work

The original source model has 17 independently transformed parts. A color-ID diagnostic identifies forearms 0/1, upper arms 13/14, torso 2, skirt 10 and upper legs 15/16. These are visual authoring labels; they do not establish gameplay or hierarchy ownership.

The existing offline [`refine_part`](../../scripts/refine-model-surfaces.py) now accepts optional `fixed_vertices`. These are bounded, distinct integer indices. It retains their original coordinates and the straight edges between them, then reports original and new midpoint indices to preserve through the next iteration. This matters for closed limbs: an open-border detector cannot identify a joint surface on a closed mesh. The helper does not infer a skeleton or change a source-part pivot.

Two new original fixtures cover a closed joint edge and its descendants over two iterations, input isolation, and invalid/duplicate/excessive indices, including scalar and multidimensional arrays. The surface suite passes all **10 cases**. An additional private control compares the published and modified default algorithms across all 17 parts: geometry, face UVs, palette identities and colors remain exact, and input vertices remain untouched. Authored joint constraints are opt-in; existing default calls are unchanged apart from additive report metadata.

Three private trials use two iterations, strength 0.65 and crease threshold 95°:

| Trial | Finding |
| --- | --- |
| Unconstrained smoothing | Rounds forms but loses roughly 24% of forearm volume and 20% of upper-arm volume; joint ends move. Held. |
| Explicit joint regions | Preserves source end/collar/waist/shoulder cohorts and their subdivided descendants; arm volume still decreases. |
| Joint regions plus arm volume balance | Expands only free arm vertices radially in local X/Z while retaining Y and all fixed points. Closed arm volumes match their source values. |

Joint cohorts use explicit source-local Y thresholds and garment shoulder thresholds, recorded in the private builder. They are authoring choices, not automatic anatomical registration. The volume balance applies only to the four closed, consistently wound arm meshes. Expansion factors are 1.10737/1.10572 for forearms and 1.07971/1.08049 for upper arms. The four volume ratios agree with 1 within `1e-12`. A matched total volume does not prove correct anatomy or preserve every cross-section.

The skirt exposes a separate issue: the original has 69 detected nonadjacent triangle overlaps; the refined trials raise that to 123 or 150. Those counts are not equivalent per-face scores because subdivision changes face counts, and some original layered overlaps may be intentional. The advanced comparison/export retains the original skirt rather than treating this trial as an accepted reconstruction. Its source overlaps remain; no whole-character collision-free claim is made.

The seven modified parts—0/1/2/13/14/15/16—pass finite/nondegenerate geometry, duplicate-face, edge-incidence, connected-fan and tested nonadjacent intersection checks. Broadphase AABBs discard separated pairs before the retained intersection test. Shared-vertex pairs and inter-part collisions are excluded. Garments with open borders receive no enclosed-volume claim.

## Animated interchange and correspondence

The advanced body retains the reconstructed head and bound rear strips, the original reference scene/assets, the unchanged skirt, and all 21 recorded candidate clips from the previous study. Only seven candidate body mesh references change. Their UVs, palettes and original colors interpolate through subdivision; no new texture is added to this geometry export.

Independent GLB decoding checks position/color/UV correspondence, bounded indices, unit preview normals and metadata preservation. Across **496 recorded source-method poses**, it compares **8,189,952 body corners**. Maximum world-placement error is `1.13118e-6` in the existing convenience-scaled authoring units, below `2e-5`. The 0.01 authoring scale remains unmeasured physical size. A separate **50,592 original joint-point comparison** finds zero difference between the private candidate and source points under the same recorded transforms. Float32 interchange and quaternion conversion have their own measured error; these are not live GPU or gameplay checks.

Maximum local float32 position error is `1.48262e-5` source units; normalized UV and color errors remain below `3e-8`. Added area-weighted body normals have unit-length error below `4.08e-8`. They use the opposite source triangle winding for this preview. Existing unlit reference materials ignore them; their presence does not prove native normal generation, smooth-light compatibility or PBR behavior.

The export passes Khronos Validator `2.0.0-dev.3.10`: zero errors, warnings or hints, with the same three informational notices for retained unused earlier strip meshes and the original atlas dimensions. The full candidate has **18,068 triangles**, including the 12,240-triangle head. Its seven body primitives contain 16,512 exported face corners and 198,144 bytes of added normal arrays. These are decoded/interchange storage figures, not GPU residency, per-frame cost or Deck performance.

## Material, lighting and playing-size review

The private renderer produces 48 individual views and 12 comparison boards: resting and raised-arm recorded poses, 0°/60°/180°, full-body and upper-body framing. All columns share depth composition, camera and bounds. The final column is deliberately a separate variable: it combines the existing stronger 4× curated material atlas with a smooth-light preview and a uniform warm skin base. It does not isolate geometry alone.

The material study reuses the source-bound round-22 pack, original per-face palette mapping and discard/STP masks. Skin base is RGB 216/144/104, with 0.70 ambient/0.30 directional preview lighting; other surfaces use 0.86/0.14. This is not a measured material response, native renderer or new PBR asset. The animated geometry export retains the nearest source atlas and original body colors; it does **not** contain the final column's paint/light appearance.

Selected resting front/side and raised-arm rear boards, plus the 160/320-pixel downsampled frames, were visually inspected. The clothing is clearer and the limb surfaces are cleaner, while the geometry-only difference becomes subtle at smaller size. Skin looks too uniform relative to the textured face, pinned shoulder forms remain angular, and hair/neck transitions still need work. This is progress toward the owner's modern retro direction, not the commercial finish gate. No candidate is selected as a default.

The playing-size board uses Lanczos-resampled authoring frames, not a 1280×800 native Deck capture or evidence of temporal anti-aliasing. Next, deliberately refine shoulder/skin forms, match complexion/material response to the face, and correct the hair/neck boundaries. Then prove native material/normal/mesh ownership, lighting, STP/blending, occlusion, transitions and teardown before physical Deck measurements and community review. The [native material audit](NATIVE_MATERIAL_INTEGRATION.md) and [Deck protocol](DECK_TEST_PLAN.md) remain applicable. Faithful mode and the checked alpha remain unchanged.

## Reproduction and retained evidence

The public API extension is exercised without game files:

```sh
python3 scripts/test-surface-refinement.py
```

Use the pinned fixture dependencies in `scripts/requirements-visual.txt`. Private authoring directly reuses the recorded Python 3.12.0/NumPy 1.26.4/Pillow 10.1.0 environment and the existing source readers. No new weights, inference, cloud service, game launch, engine behavior change or runtime dependency is involved. Private reproduction requires the matching extracted model/TIM, nearest control pack, stronger material pack, prior head/binding assets and a fresh output directory. Exact builder/verifier/renderer commands, thresholds and dependencies are retained privately.

| Private artifact | Bytes | SHA256 |
| --- | ---: | --- |
| Animated body geometry interchange | 3,054,340 | `e3a5ea81ce9f2bedacafb2a362cec9968668eb7a7e4c7491a44e8e2b5fd434d7` |
| Body evidence manifest, 131 listed files | — | `5c43db6ce0eed309243a4a8869e1b33cb886a6882e9ce8cfc3d0a083b9e7bbb1` |

The first renderer and export-verification prefix helpers failed before producing output because their temporary execution contexts lacked `__file__`; corrected contexts and both logs are retained. The evidence includes held trials and source-overlap findings rather than silently discarding them. All meshes, images, retail-derived reports, local helpers and runtimes remain outside Git. Public changes are the optional offline constraint API, its original fixtures, and documentation.
