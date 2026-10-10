# Rear-strip attachment refinement

Checked 2026-10-10 at source tooling commit `50ce33386b68580aa91c36b71d14dfdb8ce045ac`. The [full-character head study](HEAD_ATTACHMENT_STUDY.md) exposed original rear strips floating beside the reshaped hair. Private follow-up trials now improve the resting fit and extend inspection to **seven available compatible combat motions, 62 sampled keys**. A raised-arm sample still exposes a root seam. The result is a working art comparison, not an accepted replacement or installed game feature.

## Trials and rejection evidence

All trials preserve the original reference scene, original body/head binary prefix, sampled animation channels and samplers, and all node metadata except the two candidate rear meshes. They reuse the original strip material and texture. The head and its placement remain unchanged from the width/top study. Only the candidate geometry of parts 8 and 9 changes.

| Trial | Method | Decision |
| --- | --- | --- |
| Shifted roots | Translate each original four-vertex strip to an estimated nearby scalp point | Useful comparison; width endpoints and moving roots remain imperfect |
| First surface fit, round 42 | Project strip edges separately onto an inferred dark rear-head region | Held: portions of a strip disappear into the hair |
| Normal-clearance fit, round 43 | Offset those points along interpolated surface normals | Rejected: part 8 has an intersection between nonadjacent triangles 10 and 13 |
| Smooth centreline, round 44 | Fit a quadratic curve through three surface points and retain original width | Nonadjacent intersection check passes, but root endpoints remain too far from the intended surface |
| Revised roots and centreline, round 45 | Project the root endpoints, then blend their width toward the original width along the curve | Cleaner resting comparison; movement/root binding remains unaccepted |

The final trial has eight stations, 16 vertices and 14 triangles per strip. Its centreline uses three fitted points; nominal source-length fraction is 0.72. UV/color interpolation follows that fraction, so it crops the original strip's final 28% rather than preserving its full tail. Root and tail surface clearances are 0.8 and 4 source authoring units. Those choices are disclosed design experiments, not an approved silhouette or measured physical distance.

The surface region is a heuristic: mean RGB below 80, RGB range below 30, and positive head-local Z, yielding 6,223 eligible triangles in the reconstructed head. This is not semantic identification of hair, anatomical registration or a signed collision test. Nearest points and interpolated normals on inferred hair cannot substitute for deliberate modelling.

## Verified geometry and motion scope

Independent GLB decoding verifies the appended geometry against private NPZ records and preserves all original sampled part transforms. Both revised strips pass nonzero-area, duplicate-face, edge-incidence and connected-vertex-fan checks. Each has 16 boundary edges. All 66 nonadjacent triangle pairs per strip are checked with no detected intersection in round 45. Pairs sharing a vertex are excluded; this is not an exhaustive self-contact or scalp/body collision proof.

Four original intersection fixtures cover coplanar overlap/separation, a crossing surface and parallel separation. A fifth probe detects the actual rejected round-43 crossing. The verifier deliberately records rejected variants; a valid GLB is not grounds to accept their art or geometry.

The two revised rear meshes retain their original rigid animation channels. At all 12 animation-0 keys, decoded float32 placement agrees with the private mesh coordinates within `3.51657e-8` authoring units, below the `2e-6` comparison tolerance. All four rounds' two-scene GLBs pass official Khronos Validator `2.0.0-dev.3.10` with zero errors, warnings or hints and the same one informational original-atlas dimension notice.

The read-only source inventory resolves the available directory's 35 map slots to seven compatible stored motion files plus the model. The seven motions contain 62 sample keys. Their identities, aliases and measurements are retained privately. This establishes the available data inspected; it does not identify each motion's gameplay purpose, prove every animation used by the game is present, or reproduce native timing/interpolation. The exported GLBs still contain animation 0 only; the other motions are inspected from source records.

| Maximum unsigned root-endpoint distance to the selected candidate surface | Animation 0, 12 keys | Available seven motions, 62 keys |
| --- | ---: | ---: |
| Previous head fit with original strips | 20.1978 | 21.4443 |
| Shifted-root comparison | 4.05354 | 7.13617 |
| Revised root/centreline comparison | 2.15974 | 6.10065 |

These are source authoring units and unsigned nearest-region distances, not welded-root, collision or performance acceptance. The broader motion inspection exposes a larger residual than the initial resting-motion check. The worst revised sample is stored motion 5, key 2; side/rear renders show the remaining seam while retaining the source's strip swing. Removing the strips would conceal the issue and lose the original animated detail.

## Visual review and remaining work

Each round renders source, previous head fit, shifted roots and the surface trial at keys 0/6/11 and 0°/60°/180°, with shared framing and depth composition. Round 45 adds the worst measured sample's side/rear views. Resting side/rear views, the rejected partially hidden fit, and the worst revised sample were inspected. The revised strips read more coherently on the new hair at rest. Their connection and motion still need a deliberate binding solution.

Next, define a head-relative attachment boundary while preserving the separately animated strips' swing. Compare a small root region following the head with rebaked translation compensation or an authored deformation boundary; prove the selected approach against source controls, available motions and native interpolation before activation. Keep the original motion/reference intact for comparison. This is a proposed authoring/import seam, not an existing engine feature.

The subsequent [moving-root binding study](NATIVE_ATTACHMENT_BINDING.md) now records 496 poses through the actual source animation methods, checks both bound roots and independent tail rotation, and delivers a private animated interchange verified by independent joint decoding. It remains authoring evidence; native replacement rendering and artistic acceptance are still open.

Then refine the hair ends, neck/collar boundary and UV coverage, and bring the body materials toward the same intentional style. Verify native normals, lighting, STP/blending, occlusion, resource lifetime and fallback. Physical Deck memory/frame-time/power, suspend/input behaviour and community preference remain separate gates under the [Deck protocol](DECK_TEST_PLAN.md).

The revised strips add 24 triangles to the previous full-character study, yielding 12,908 total candidate triangles. Their decoded position/color/UV/index arrays increase by 1,056 bytes compared with the two original strips, with no new texture. This is an array-storage calculation, not measured GPU residency or Deck performance. The head's texture/geometry cost remains as recorded in the previous study.

## Reproduction and private evidence

The private runs use the existing Mac Studio / M5 Max / macOS 27.0.1 authoring environment, Python 3.12.0, NumPy 1.26.4, Pillow 10.1.0 and Trimesh 4.0.5. No new model weights, neural inference, cloud upload, game launch or runtime dependency is involved. The source readers are called directly in that recorded environment, as in the previous study.

Private round 45 retains all four rounds' output hashes, geometry/format reports, source-motion identities, exact commands, versioned builders/renderers, dependency lock, retained decoder/compositor and diagnostic logs. The initial helper failed on NumPy tuple indexing before exporting; it was corrected into a fresh folder. The worst-pose helper initially failed on a string-prefix truncation before rendering; the corrected helper selects its prefix through the AST. Neither failed helper produced an accepted asset. Reproduction requires private source inputs and fresh output paths; nothing runs as an installer/game feature.

| Private artifact | Bytes | SHA256 |
| --- | ---: | --- |
| Shifted-root two-scene reference | 1,482,976 | `cad2d40c55c6e57cd206a863724f3e0e2822f9cf57e7e67d5f86be380f688ff6` |
| Revised ribbon two-scene reference, round 45 | 1,484,060 | `b87b78aab25675a636f59dea0870a88ab61345f3b35c222bc1e7a3c67452943e` |
| Four-round private evidence manifest, 305 listed files | — | `d8f3a156ac1583804d65c99945a808c622bd065076f94964debbc21ea287ebd4` |

All images, meshes, retail-derived data, local runtimes and private helpers remain outside Git. Public changes are documentation only. Faithful mode and the checked alpha installation remain unchanged; no candidate is selected as a shipped default.
