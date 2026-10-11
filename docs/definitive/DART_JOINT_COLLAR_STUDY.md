# Dart articulated surface study — October 11, 2026

The owner endorsed the latest nine-character artwork direction and asked us to continue. That establishes a visual target; it does not approve the static sculpts as finished animated actors. This checkpoint addresses the failed rigid body partitions without changing the accepted Dart head, the nineteen selected CharHD resource files, engine behavior, main or a release.

## Original-design controls

The original normal-form `Char_Dart_2.png`, original field model `SECT/DRGN21.BIN/101/0` and original combat model `characters/dart/models/combat/32` were inspected together with the new artwork and its actual GLB. Their hashes are in the existing [reference catalog](../../integrations/charhd/production/party-reconstruction/original-art-reference-catalog.json), [source audit](../../integrations/charhd/production/party-reconstruction/source-audit.json) and [native reference audit](../../integrations/charhd/production/party-reconstruction/NATIVE-REFERENCE-AUDIT-original-design-v3.json). Original pixels and meshes remain outside Git.

| Detail | Observed status | Consequence for model authoring |
| --- | --- | --- |
| Chest and abdominal armor | New artwork restores the major segmentation visible in the original illustration. Exact rivet and overlap construction is unresolved. | Keep pieces separate; an inferred smooth rear mass is not a completed torso. |
| Arm asymmetry | Original field and battle controls have distinct arm construction. The illustration's bare/leather right arm must not silently replace battle armor. | Fit each form separately. This study targets field parts 11/0 and 12/1, and combat parts 12/0 and 13/1. |
| Belts and hardware | Artwork restores recognizable stacked belts and the ring-ended closure. Exact hardware placement and occluded rear paths are unresolved. | Preserve the source-bound waist silhouette and inspect close views before promotion. |
| Lower legs | Original field uses pointed wraps; original combat uses broad cuffs. New artwork follows the illustration/field treatment. | Author separate boot adaptations. |
| Rear plate construction | Native rear controls show a defined red back plate and separated lower cloth/belts. The new sculpt has inflated, fused rear regions. | Reconstruct plate outlines and thickness deliberately. This joint study does not repair the torso. |
| Sword and hands | Illustration and native combat weapon presentations differ. Native grip depends on the original hand and weapon transforms. | Preserve the equipped weapon and contact anchors. This study changes no hands or weapons. |
| Face, hair and scale | New artwork supplies the preferred finish; the accepted head remains selected. | Preserve accepted bytes and fit the body to that head. |

This is a focused Dart detail audit, not nine completed costume audits or an owner-approved topology lock. The collar cut lines below are engineering controls, not accepted garment boundaries. No new full-body inference was performed.

## Construction and results

The [custom joint surfaces](../../integrations/charhd/production/party-reconstruction/dart/joint-collar-v1/VALIDATION.json) use closed sections of the pinned original limbs as collar guides. Each strip has 32 circumferential samples, nine longitudinal stations and 512 triangles. Its two open collar rings are intentional attachment boundaries; it is not a closed full arm or actor.

The existing offline linear blend collapses a hand-authored circular midpoint to zero radius under a half-turn. A first curved attempt preserved area but still produced inward-facing triangles in sharp native bends. The revised method interpolates rotation along a circular-arc Hermite centerline and normalizes each section to its authored endpoint area interpolation. Both supplied collar positions remain exact. The armored combat elbow needs a longer blend span; source-specific stations are recorded explicitly.

A second check found that the other combat elbow's narrow span passed stored keys but folded in two between-key samples. Moving its forearm collar from local station 55 to 85 resolves those observed failures. The receipt preserves both rejected sample identities and the revised source-specific stations; neither is treated as a finished armor cut line.

- Ten independent authored fixture tests pass, including a half-turn, a 120-degree bend, exact endpoints under distinct frames, common world motion, all three local axes, strip topology, mutation isolation and malformed inputs.
- Four interpolation fixtures pass, including short-arc rotation, normalized blending rather than spherical interpolation, preserved endpoint frames and invalid-input rejection.
- Both field elbows pass ten stored idle keyframes each.
- Both combat elbows pass all 62 stored keyframes across animations 0, 1, 5, 10, 11, 14 and 29: 124 checks total.
- Another 384 independent between-key samples pass at fractions 0.25, 0.5 and 0.75. These use normalized short-arc quaternion blending and linear translation; they do not execute the game animator or reproduce its stateful high-refresh quaternion updates.
- Across all 528 source-driven geometry samples: zero endpoint position error before export, relative section-area error below `2e-15`, positive triangle areas and zero triangles failing the local radial-facing check.
- Exported custom GLBs are reloaded and checked for unchanged faces and position error below `1e-4` native units. Floating-point export evidence is separate from exact pre-export collars.

The [matched comparison](../../integrations/charhd/production/party-reconstruction/dart/joint-collar-v1/collar-comparison.jpg) uses the actual custom strip at combat animation 5, stored keyframe 1. Its straight-interpolation control has 53.3% of the endpoint-average area at the middle section; the curved study retains 100.0%. Both use identical posed collars and camera. The control is an offline comparison, **not** a screenshot of released game geometry.

These checks do not establish absence of self-intersections, armor collisions or complete actor gaps. They do not test actual animator ticks, stateful high-refresh interpolation, native rendering, combat gameplay, other animations or Steam Deck performance. The study has no final skin/armor paint or CharHD material binding. It has not been fitted to the new full-body sculpt.

## Reproduce locally

Use the isolated authoring environment with NumPy, scipy, trimesh and Pillow. No model inference, dependency download or game launch is necessary. Supply owned extracted game files only to the private probe; its output must be a new directory outside this checkout.

```sh
python experiments/dart-modelshd-charhd/test-joint-collar-surface.py
python experiments/dart-modelshd-charhd/test-collar-probe.py
python scripts/test-attachment-binding.py
python scripts/verify-party-authoring.py
python experiments/dart-modelshd-charhd/probe-native-collars.py \
  --files /path/to/owned/extracted/files \
  --output /path/to/new/private/collar-probe
python experiments/dart-modelshd-charhd/render-collar-study.py \
  --mesh /path/to/new/private/collar-probe/combat-elbow-12-0/motion-5-key-1.glb \
  --output /path/to/new/private/collar-probe/comparison.jpg \
  --font /path/to/Arial.ttf
```

The public checkpoint contains four custom strip GLBs, a custom-only comparison and bounded receipts. Original actor geometry, textures and animation arrays are excluded. None of these experimental files enter the normal CharHD JAR or installer.

## Next model checkpoint

1. Build the complete Dart arm around the tested collar span: explicit upper-arm surface, separate armor, elbow interior and original hand contact. Inspect bends and collisions using the actual actor, not just a strip.
2. Establish a texture-independent geometry binding with source-face material lineage. Test CharHD, alternate character textures, ModelsHD on/off and faithful fallback before selecting resources.
3. Fit corrected field and battle bodies separately, including rear plates and distinct boots; preserve the accepted head and original weapon anchors.
4. Compare the complete result with original controls in representative native animations, including interpolated ticks. Then extend the method to the other eight characters and request owner approval for promotion.
