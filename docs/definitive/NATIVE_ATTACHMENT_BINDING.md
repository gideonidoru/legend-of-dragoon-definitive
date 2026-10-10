# Moving-root attachment study

Checked 2026-10-10 against baseline `07cfb063ed7e213e3812e37def99eca08456b4f7` plus retained working tooling. The [rear-strip refinement](REAR_ATTACHMENT_REFINEMENT.md) improved Haschel's resting fit but exposed moving roots. This private follow-up binds both root endpoints to the reconstructed head while retaining each strip's independent swing rotation. It passes the inspected animation and geometry checks and now has an independently decoded animated authoring export. Native replacement loading and artistic acceptance remain open.

## Actual source animation methods

The test-only [`NativePoseProbe`](../../src/test/java/legend/definitive/models/NativePoseProbe.java) constructs a texture-free `Model124` and calls the existing loader and animation methods without a mesh, script, renderer or window. It records finite, row-major 3×4 part transforms in source units. Three profiles inspect the seven available compatible motions:

| Profile | Source call | Recorded poses across 62 keys |
| --- | --- | ---: |
| `combat-2` | `Models.animateModel(model, 2)` | 124 |
| `combat-4` | `Models.animateModel(model, 4)` | 248 |
| `animation-apply-2` | `TmdAnimationFile.apply(model, tick)` | 124 |

This is **496 sampled poses**, not a live gameplay capture. The probe seeds the loader's initial pose and uses a fresh model per profile. It does not simulate scripts, effects, animation interruptions, world placement, every interpolation setting or an entire battle lifecycle.

The methods have different cursor/interpolation conventions. The four-frame combat path also updates its quaternion progressively with `nlerp`; assuming a direct halfway rotation between two keys would give a different result. Original synthetic fixtures exercise these distinctions, part order and translations. Six Java cases also cover immutable snapshots, bounded malformed input, complete-file output, retention of existing files/links and rejection of checkout output. The probe remains in test sources and is absent from the built game jar.

## Binding and measured result

[`bind-rigid-attachments.py`](../../scripts/bind-rigid-attachments.py) provides a bounded positions-only authoring helper. The input is a strip in its original local coordinates plus anchor/swing bind poses. Both root vertices receive weight 1; a smooth transition reaches weight 0 at 32% of the strip's eight-station length. At each pose:

- Root vertices follow their fixed head-local bind positions.
- The strip's original rotation remains the tail rotation. Its translation is replaced so the root centroid follows the head.
- Intermediate vertices blend those two placements; weight-0 tail vertices preserve relative vectors and distances under the original swing rotation.

This deliberately changes absolute strip translation. It does not preserve all original motion unchanged, weld the mesh to the scalp, generate normals or solve collisions. Proper rigid transforms are required; scale, reflection, shear and nonfinite poses are rejected. Seven original Python fixtures cover nonzero pivots, rotating roots, tail distances, translation compensation, mutation isolation and malformed inputs.

Both round-45 strips were checked in all 496 poses: **992 strip-pose checks** and 65,472 nonadjacent triangle-pair checks. Pairs sharing a vertex are excluded. No duplicate faces, degenerate triangles, nonmanifold edges, disconnected vertex fans or tested nonadjacent intersections were detected.

| Maximum distance from the intended head-relative root target | Source authoring units |
| --- | ---: |
| Previous rigid placement | 39.7912 |
| Translate the centroid only | 1.31401 |
| Bind both root vertices | 0 in the double-precision authoring calculation |

The former largest-drift sample is motion 14, `combat-4`, tick 5; the largest centroid-only root-line error is motion 5, `combat-2`, tick 5. These distances use the intended root positions, **not** the previous unsigned nearest-scalp metric; the two tables must not be compared as if they measured the same thing.

Relative tail-rotation error stays below `2.274e-13` source units. Edge-length ratios across the inspection range from 0.919957 to 1.170536; the blend introduces local deformation rather than preserving every edge length. Minimum triangle area is 36.0080 square source units. Those results do not establish signed scalp/body collision freedom, continuous behavior outside the sampled poses or production skinning quality.

## Animated interchange and visual review

The private glTF candidate uses two small two-joint skins: the existing head transform and each strip's compensated swing transform. Its original reference scene, original source nodes/assets and original source animation clip remain exact. The earlier cloned candidate clip is replaced with **21 separate candidate clips**, representing the three recorded profiles for each motion. Each clip uses STEP snapshots with one authoring second per tick; this is a browsing convention, not gameplay timing.

Independent accessor/joint decoding compares **15,872 strip vertices and 401,760 unchanged body vertices** against the recorded source poses and authoring calculation. Maximum errors are `1.50637e-6` for strip positions, `8.89413e-7` for root targets and `1.08010e-6` for body positions, in convenience-scaled authoring units. The declared comparison tolerance is `2e-5`; the root's 0.01 scale is unmeasured physical size. Float32 positions, inverse bind matrices and quaternion conversion explain why the interchange comparison is not exactly zero.

Khronos Validator `2.0.0-dev.3.10` reports zero errors, warnings or hints. Three informational notices describe two retained, unused earlier strip meshes and the original 1024×704 atlas. The first export was held for warnings about non-root skinned nodes and zero-weight joint indices; a fresh corrected export retains the rejected file and report for comparison.

Six side/rear comparison boards cover the resting control and both worst measured samples, with common framing/depth composition and the original body. These were visually inspected. The moving-root correction holds its intended boundary, but reconstructed hair ends, neck/collar alignment and coarse body/clothing still need deliberate modelling and material work. Preview head shading and original unlit body colors do not reproduce native lighting, STP/blending, projection or GPU skinning.

## Native seam and next work

This export is an authoring interchange, **not an implemented glTF importer or game feature**. Existing model parts are rigid and cache GPU objects. The practical next experiment is a separately owned optional attachment mesh with a small head/strip deformation boundary, evaluated after the existing part transforms. It must rebuild correct normals, preserve material/translucency semantics, update only its own resources and restore the original rigid parts as a whole on unsupported or invalid input. Avoid mutating shared source packets, containers or cached original meshes. The [material integration audit](NATIVE_MATERIAL_INTEGRATION.md) covers related texture ownership and renderer boundaries.

Before activation, test native pose parity, frame transitions/interruptions, scalp/body contact, lighting, occlusion and teardown. Then measure memory, p95/p99 frame time and power on a physical Deck. No measured Deck cost, native import, gameplay improvement or community preference follows from these offline checks. Both strips retain 16 vertices/14 triangles and reuse their previous material/UVs; the full character remains 12,908 triangles. Additional joint/weight data and per-frame work are still unmeasured on Deck.

The next art pass should sharpen recognizable hair forms and the neck/collar transition, then bring body silhouettes and skin/fabric/metal materials toward the clearer face. Inspect at 1280×800 playing size as well as close-up. Faithful mode and the published alpha remain the fallback; no candidate becomes a default through this study.

## Reproduction and evidence

Run the explicit supported task from the checkout with Java 25 and the existing Gradle wrapper, supplying private extracted animation inputs and a new output filename in an existing private folder:

```sh
./gradlew --offline nativePoseProbe \
  -PposeAnimation=/PRIVATE/characters/haschel/models/combat/0 \
  -PposeOutput=/PRIVATE/study/motion-0.json
python3 scripts/test-attachment-binding.py
./gradlew --offline build
```

The Gradle task pins the source-root output guard. It bounds input/output, stages a complete report and publishes without overwriting an existing path or link. Publication can fail on a filesystem without hard-link support; it must remain an explicit error rather than leaving a partial report. This authoring task is opt-in, and is never run by installation, launch or update. Python fixture dependencies are pinned in `scripts/requirements-visual.txt`; CI includes the new synthetic binding suite.

Private authoring uses the existing Mac Studio/M5 Max/macOS 27.0.1 environment, Python 3.12.0, NumPy 1.26.4, Pillow 10.1.0 and Trimesh 4.0.5. The dependency check passes. No new weights, inference, cloud service, game launch or engine behavior change is involved. The source readers are reused directly in that recorded authoring environment. Local Java 25/Gradle 9.1.0 `build --offline` passes: 77 delivery cases, 75 passed, zero failures, two headless-window cases deferred. This does not replace real Deck acceptance.

| Private artifact | Bytes | SHA256 |
| --- | ---: | --- |
| Corrected animated authoring export | 1,913,524 | `40deba0d68ee25199375b40a8a11b5aae03412782dc5fe533d327539a705d26e` |
| 992 strip-pose audit | 717,859 | `b3925de1c927aabcb6cdaebf7c953af8888c814157f14d93aa20ba501e7e43b3` |
| Independent skin/body decode report | 5,403 | `5dd3426165e447e39665f28494776e33ef43e5752945e8e230546578812546e8` |
| Corrected evidence manifest, 112 listed files | 20,602 | `c230e23ce214e2a6ab9c6215a298a6d311155c9e7c7d5681ea79ebfb434d481d` |

Retained private evidence contains exact commands, source/report hashes, source-method snapshots, versioned helpers, dependency lock, logs, exports, validator reports and comparison images. The recorded Java probe was later hardened with bounded stream reads and root-output validation; its interpolation code is unchanged and both file versions are retained. A helper-hash label and an initial retention-log reference were corrected in fresh manifests without altering geometry results. The first synthetic Java compile failed on method spacing before tests ran; corrected source and both logs are retained. All retail-derived images, meshes, motion reports, local tools and runtimes remain outside Git. Public changes consist of test/offline tooling, its CI fixture and documentation.
