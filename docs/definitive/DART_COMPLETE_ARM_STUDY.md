# Dart complete-arm construction study — October 11, 2026

The owner asked us to continue after the nine-character artwork and articulated collar checkpoint. This follow-up joins Dart's upper and lower arm surfaces around both field and battle elbows. It is a continuity construction prototype; it has **not passed the visual gate** and is not selected by CharHD, the installer, main or a release.

The four [custom arm GLBs and bounded receipt](../../integrations/charhd/production/party-reconstruction/dart/complete-arm-v1/VALIDATION.json) are public authoring deliverables. Original actor controls, extracted models/textures, animation arrays and full-actor comparison pixels stay private. The accepted nineteen runtime resources, including Dart's accepted head, are unchanged.

## What changed

The previous open strips could not demonstrate a joined arm. This prototype clips the actual audited upper/lower surfaces at the tested section planes, retains every corner of both native section contours and adds shared angular samples. Every cut edge is split at those samples and shares exactly one vertex with its elbow surface. The shared edges have exactly two triangle owners; merely overlapping an approximate ring would leave hidden T-junctions.

The remaining surfaces follow their original rigid part transforms. Existing separate trim and original shoulder/wrist attachment borders are retained. We do not fill unrelated original borders or replace separately attached pauldrons, gloves or swords. Original triangle barycentric lineage survives clipping and is checked by rebuilding the retained positions. That is an authoring control, **not** a final CharHD texture binding: the new elbow has no approved paint or source-material assignment, and the GLB is a clay review asset.

| Modified arm | Shared ring vertices | Complete upper/lower arm triangles | Remaining source borders | Nonmanifold edges |
| --- | ---: | ---: | ---: | ---: |
| Field 11/0 | 48 | 926 | 4 | 0 |
| Field 12/1 | 48 | 926 | 4 | 0 |
| Combat 12/0 | 47 | 908 | 16 | 0 |
| Combat 13/1 | 48 | 942 | 4 | 0 |

These are joined upper/lower arm assemblies, **not closed full actors**. The glove/weapon source geometry, part transforms and contacts are unchanged in the private full-actor review. That review uses original actor controls around the modified arms, not a fitted version of the new full-body sculpt or an accepted-head gameplay test.

## Evidence and the failed visual gate

All four arms pass 528 selected original stored-pose/independent between-key samples: shared collar error below `1e-6` native units, positive complete-arm triangle areas, no local radial-facing reversals, bounded original-triangle lineage error and finite geometry. Custom GLB round trips retain the faces and stay within `1e-4` native units. The receipt pins source and tool hashes and records every sample; the animation scope is unchanged from the [collar study](DART_JOINT_COLLAR_STUDY.md).

An initial absolute-determinant intersection scan incorrectly counted joined-edge/near-parallel contacts as penetrations. Its 163 combat contacts are **rejected diagnostic evidence**, not confirmed armor collisions. The corrected diagnostic uses a scale-normalized determinant and excludes triangles sharing a mesh vertex. Six independent authored fixtures check true crossings at multiple scales, disjoint triangles, shared vertices, endpoint contacts, coplanar exclusions and malformed/budgeted inputs. It detects zero proper noncoplanar crossings between the new elbow and retained arm surfaces in the 528 samples.

That diagnostic excludes coplanar overlaps, adjacent triangles, pure edge/endpoint contacts, other actor parts and full containment. It is not comprehensive collision validation. The independent quaternion samples do not execute native animator ticks or its stateful high-refresh interpolation.

Private matched full-actor clay views at combat motions 0, 5 and 14 expose the remaining visual issue: the bend is too bulky, while the retained upper/forearm surfaces still have the original coarse shapes. Joining them solves the construction gap; it does not make the model AA quality. A privately tested eased-rotation variant did not resolve the finish and is not selected or published as a new candidate. **Do not copy this template to the other eight characters as finished art.**

Ten additional authored arm fixtures verify welded topology, exact single-vertex collar attachment, retained rigid geometry, clipped material lineage, boundary sample insertion, rejection of missing section corners, cut-through-vertex triangulation, input isolation and malformed poses/meshes. The six crossing fixtures are separate from these ten arm fixtures.

## Reproduce

Use the isolated authoring Python environment with NumPy, scipy and trimesh. No game launch, inference, dependency download or runtime modification is needed. Supply owned extracted files only to the private probe, with a new output directory outside the checkout.

```sh
python experiments/dart-modelshd-charhd/test-complete-arm-surface.py
python experiments/dart-modelshd-charhd/test-surface-crossing-probe.py
python experiments/dart-modelshd-charhd/probe-complete-arms.py \
  --files /path/to/owned/extracted/files \
  --output /path/to/new/private/complete-arm-review
```

The probe exits nonzero on a bounded numerical/crossing failure and retains its report. A zero exit does not constitute visual acceptance. None of these experimental resources enters the normal CharHD JAR.

## Next useful art work

Author dedicated upper-arm and forearm shapes from the original-design controls, with separate rigid armor and a deliberate narrower flex region. Use the welded representation to inspect the complete silhouette and original grip through motion, rather than spending more time polishing isolated seam metrics. Then establish final source-face/material ownership, CharHD/alternate texture compatibility and native animated rendering. Only a visibly successful complete Dart actor should set the production template for the other eight characters. Owner approval remains required before main/release selection; gameplay and physical Steam Deck performance remain unvalidated.
