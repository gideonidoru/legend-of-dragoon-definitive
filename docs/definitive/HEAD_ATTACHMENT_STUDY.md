# Full-character head attachment study

Checked 2026-10-10. The private, connectivity-checked Haschel head has been placed on the unchanged original combat body in three affine fit studies. This advances the [local reconstruction](LOCAL_MODEL_RECONSTRUCTION.md) from an isolated portrait to full-character proportion and attachment review. **All three fits remain unaccepted.** Side views reveal that the retained original rear strips detach from the reshaped hair. The face, hair, neck, body materials and native integration still need work before this can become a game feature.

## Source and construction

Source tooling was inspected at project commit `8c6223a47f5ddd9eed96ad8caef79b542523d7fd`. The unchanged original reference contains 17 independent rigid parts and 12 sampled keyframes from Haschel combat animation `0`. Main head part 7 has 147 triangles; parts 8 and 9 each have two triangles. Their separate animated transforms are preserved rather than inferring a skeleton or deleting inconvenient pieces.

Each private GLB contains two scenes. Scene 0 preserves the complete original reference. Scene 1 shares the original body meshes, clones the original part nodes and channels, and replaces only the main head mesh with the [12,240-triangle candidate](MESH_TOPOLOGY_REFINEMENT.md) under its original part transform. The new head's vertex attributes, UVs, indices and embedded texture remain unchanged. Its placement uses a separate parent matrix.

The basis is a proper rotation with positive determinant: candidate `(x,y,z)` maps to source `(y,-z,-x)`. Uniform scaling is derived from the original main head's 172-unit width. The reference root retains its `(x,-y,-z)` conversion and 0.01 authoring scale. Those units do not establish physical character height.

| Trial | Placement rule | Status |
| --- | --- | --- |
| Width and top | Match original head width, top and front bounds with uniform scale | Development comparison; rear attachment and neck unfinished |
| Trial neck anchor | Same width scale, using manually estimated candidate/source attachment landmarks | Landmark correspondence remains unaccepted |
| Fuller head | Increase width scale by 8%, keeping the same estimated attachment correspondence | Proportion trial; no preferred/default selection |

The two landmarks are deliberate authoring estimates, not a proven anatomical neck registration. A zero residual at an estimated landmark would establish only the chosen affine correspondence. None of these trials changes the installed game, published installer, Faithful mode or runtime mod selection.

## Independent placement verification

A separate verifier decodes GLB accessors and sampled quaternion transforms without the fit builder or Trimesh's scene loader. It compares every original scene part and all 16 unchanged candidate parts with source TMD vertices and decoded animation records at all 12 sample keys. It also compares the reconstructed head's placement with the original head transform composed with the recorded affine fit.

| Check | Result for each of the three fits |
| --- | --- |
| Original binary prefix, original nodes/meshes/accessors and sampled channels | Exact |
| Original/candidate body primitive-pose checks | 924 |
| Original/candidate body corner-pose comparisons | 41,148 |
| Maximum source corner discrepancy after authoring scale | `2.46024e-7` authoring units |
| Maximum retained rear-piece corner discrepancy | `5.78461e-8` authoring units |
| Reconstructed head placement checks | All 12 sample keys pass |
| Maximum reconstructed head placement discrepancy across fits | `1.22529e-8` authoring units |
| Numerical comparison tolerance | `2e-6` authoring units |
| Official Khronos Validator `2.0.0-dev.3.10` | 0 errors, 0 warnings, 0 hints, 1 info |

The informational validator result concerns the unchanged original body atlas's 1024×704 dimensions. Its nearest/clamped sampler remains unchanged. The previous isolated-head GLB's zero-info result still applies to that file, not these combined references.

Three deliberate corruptions are rejected even after their outer GLB hashes are refreshed: changed candidate root scale, changed cloned animation target and changed head attachment translation. This guards against treating a content hash as placement proof.

The 12 keys belong to one original sampled motion. These checks do **not** cover the rest of the animation catalog, continuous native interpolation, skin deformation, face animation, native rendering or game-format loading.

## Visual evidence and decision

Thirty-six full-body renders cover source plus three trials at keys 0, 6 and 11 and camera angles 0°, 60° and 180°. Twelve upper-body renders compare all four versions at key 0. Camera and framing are shared across compared versions. A common depth buffer handles head/body occlusion in the offline preview.

The source body uses its original per-face palettes, unlit colors and conventional visibility mask. The new head uses the baked color with diffuse preview shading. Consequently, this is an authoring comparison rather than a controlled native lighting comparison. It does not reproduce PS1 projection, native STP/primitive blending, engine interpolation or game capture.

Front, side, rear and the three-pose contact sheet were inspected. The face is clearly more articulated at the full-character scale, but its detail exposes the coarse body materials and shapes. The shorter rounded hair silhouette leaves original animated rear strips visibly separated in side views. Preserving their transforms is mathematically correct and artistically insufficient. The inferred neck termination and scalp/strip relationship need an authored solution across views and motions.

**Hold all fits from native activation.** Use the width/top study as a consistent working comparison, without selecting it as the final proportion or default. Refine the complete head/hair attachment assembly before expanding the style to other characters.

## Private reproduction and identities

The runs used the existing private authoring environment: Mac Studio `Mac17,14`, Apple M5 Max, macOS 27.0.1, Python 3.12.0, NumPy 1.26.4, Pillow 10.1.0 and Trimesh 4.0.5. Dependency checking passes. Source reader/render functions were called directly in this recorded environment; the separate CLI's Pillow 12.3.0 / NumPy 2.5.1 version gate was not invoked. No new neural inference, paid service, cloud upload or runtime dependency was required.

Private round 40 retains `inspect-parts.py`, `fit-head.py`, `render-fit.py`, `verify-fit.py`, `pose-contact.py`, `check-rejections.py`, the validator runner, actual dependency lock, source helper hashes, exact commands, reports and output hashes. Reproduction requires the owner's private source/reference files and a fresh output directory; the helpers retain machine-specific paths and refuse to overwrite evidence. System Arial was used for review labels and is not bundled.

| Private artifact | Bytes | SHA256 |
| --- | ---: | --- |
| Original source reference | 241,580 | `f416e5678351fee70d82696136e8e484c36e7fef84badf3422a51806b78fb0eb` |
| Connectivity-checked isolated head | 1,232,012 | `ecc6ee37587fed1d8b24f862f061f52c9841a44bc0708901e98c114740b28ef1` |
| Width/top two-scene reference | 1,480,944 | `78424a607dc2e4f89a633ad8693643c898c3d9908050c6a753ccab36faaeb2f5` |
| Trial-anchor two-scene reference | 1,480,952 | `17a9a30a0536c206e78785f2485a7f6e8a21ec79a02c2fe486c1b65dcdda812d` |
| Fuller-head two-scene reference | 1,480,948 | `21cb741e0038a2f8afcb95a81065df253897533dcba5dba506a67fc8c5fecacb` |
| Complete private fit/review evidence manifest | — | `02e0c4f6e0a76e1459fd61b04dbe7bc6cee6ffcde93ccd3d2dbe51261b178dbb` |

Images, meshes, retail source data, checkpoints, local runtimes and private reproduction helpers remain outside Git. Public documentation contains scope, decisions and hashes only.

## Next work and Deck cost

1. Author clean hair clumps, ends and rear-strip roots as one coherent design. Preserve the source's independently animated pieces where useful, but verify any revised attachment mapping across the relevant motion catalog; do not hide the problem by deleting those pieces.
2. Fit a controlled neck/collar boundary and compare proportions against the approved concept and original head in front/profile/back views. Replace fragmented UV packing with coherent, completely covered visible regions.
3. Bring selected torso/limb/cloth shapes and skin, fabric and metal materials toward the same deliberate style, with pinned joint boundaries and preserved garment markings. Review at actual playing size against Skurfa backgrounds.
4. Prove the native geometry/material contract, lighting, masks/blending, occlusion, interpolation and resource lifetime before accepting an installed replacement. Follow [native integration planning](NATIVE_MATERIAL_INTEGRATION.md) and the [Deck protocol](DECK_TEST_PLAN.md).

The combined candidate contains 12,884 triangles versus the source's 791. The isolated head has 10,928 UV-split vertices; its positions/normals/UVs and 32-bit indices total 496,576 decoded bytes before driver/engine overhead. A 1024² RGBA8 base-color texture would add 4 MiB before mipmaps. These are authoring/storage estimates, not measured Deck residency or frame-time cost. File size, triangle count and CPU reconstruction timing cannot select a Deck default.

Keep reconstruction offline so a reviewed asset can use the existing render path. Measure cold/warm loads, residency, transition peaks, p95/p99 frame times, shared-memory/power cost, suspend and input behavior on physical hardware. Faithful fallback and community comparison remain acceptance requirements; neither the clearer face nor successful interchange validation establishes commercial readiness.
