# Party face registration: development checkpoint

2026-10-11. The owner identified two differently scaled faces painted onto Haschel, with similar failures across the party. This is a rejected visual result. Build success did not make that result acceptable.

## Corrected draft

`head-program-v4` replaces facial texels from one explicitly registered frontal source. Side passes paint bounded hair and ears and lock both eyes, nose, mouth and chin. They do not transfer another profile face. Registration inputs, authored landmarks, paint controls and receipts are versioned with each custom head. Accepted Dart remains unchanged.

The preview helper previously defaulted to the superseded v2 study. It now defaults to v4, matching the verifier. This prevents a default preview from inadvertently selecting the older paint. Ordinary CharHD packaging still selects only accepted Dart resources.

Original crown/chin scale and native bone/anatomical pose frames are verified for all sixteen field/battle entries. These controls preserve the reference dimensions and pose; they do **not** prove natural anatomy, a seamless neck or good animation.

## What the artistic audit still rejects

| Character | Current assessment | Remaining work |
|---|---|---|
| Dart | Accepted head benchmark, unchanged | Retain the accepted result through every candidate/probe |
| Rose | Frontal face no longer stacked | Resolve duplicate fringe and field neck/back-hair transition |
| Lavitz | Single registered frontal face | Refine rear jaw/neck transition in field and battle |
| Shana | Single registered frontal face | Repair neck/collar gap and braid transition without changing reference scale |
| Haschel | Single registered frontal face | Rebuild jaw underside/neck attachment; remove stray lower-jaw paint |
| Albert | Single registered frontal face | Refine collar/neck and rear hair transition at the original head size |
| Meru | Single registered frontal face | Repair jaw/neck paint and bow/ponytail overlap; smooth segmented hair |
| Kongol | Single registered frontal face | Check jaw-to-muscular-neck continuity across stored poses |
| Miranda | Single registered frontal face | Refine jaw/neck and shoulder/hair transitions across poses |

Haschel tests separating the original generated shape, neck compression, lower-jaw paint and bounded cuts were reviewed privately. Stronger compression created folds; a capped local cut produced visible planes. Those results were rejected and are not the selected public draft. Continued work should use an authored jaw/neck surface and evaluate it on the body, rather than accepting another successful bake as an anatomical fix.

`hair-program-v1` adds eight explicit source-bound painted hair resources for Rose and Meru. It retains native positions, normals, animation pivots and source weights. It is a compatibility study with visible geometry/transition defects, not finished hair reconstruction. Exact custom paint provenance, verification and limitations are documented in that directory.

## Current evidence

On a Mac Studio (Mac17,14), M5 Max, macOS 27.0.1 build 26A434, Corretto Java 25+36 and Gradle 9.1.0:

```sh
./gradlew charHdTests charHdPartyCandidateProbe \
  -PcharFiles="$GAME_FILES" \
  -PcharPartyRoot="$EXPLICIT_COMPOSITE_STUDY" \
  -PcharReport="$EXPLICIT_COMPOSITE_STUDY/CPU-CONSTRUCTION.json" \
  --offline --no-daemon
```

The explicit construction fixture contains v4 head packs plus the eight optional hair entries; its manifest is not a normal runtime selection. The game loader completed 192 combinations, with one head per actor and additional hair only where requested. Texture dimensions, cutout flags, neutral custom detail colours and unchanged source packets/positions were checked. CharHD's fifteen tests passed with no failures/errors/skips. The isolated fixture logged a missing default font; this did not prevent the model checks.

`scripts/verify-party-head-preview.py` passed for the sixteen heads, accepted Dart's twelve parts, nineteen unchanged normal runtime files and three invalid resource cases. `scripts/verify-party-hair-details.py` passed for all eight additional parts, exact binding/geometry preservation and explicit alpha behavior. Rose fringe silhouette overlap was 0.9958655566 in the offline rest-pose comparison.

Private comparisons retain original body geometry/materials and use matching original part poses and cameras. They are offline renders, not captured gameplay, and do not demonstrate all-nine body texture upgrades. Original comparison pixels remain outside Git.

## Acceptance gate

The front-face duplication is corrected in the v4 draft, but the overall party model set is **not review or release ready** while the defects above remain visible. Main, normal installation and releases must remain on accepted assets. Final owner review must cover all nine characters, both field and battle, front/quarter/profile/rear views, body proportions and neck attachment, followed by native motion/material checks and separate Steam Deck validation.

Public, versioned custom draft work is approved. Original game binaries/data, private source controls and original screenshots are not part of these deliverables. Public availability does not waive the visual acceptance gate.
