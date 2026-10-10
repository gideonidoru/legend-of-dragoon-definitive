# Haschel clothing, gloves and boots

October 10 private follow-up to the [continuous arm corrective](ELBOW_SURFACE_STUDY.md). The lower trouser legs, boots and gloves still used original geometry in that comparison. This study improves their silhouettes while retaining the recognizable costume. It remains outside Git and is **held for contact and final art review**.

## Construction and source preservation

Two treatments refine six rigid parts: boots 3/4, gloves 5/6 and lower trousers 11/12. Each uses two constrained subdivision steps, with strengths 0.55/0.85 and crease thresholds 125°/155°. Original joint and sole anchors remain fixed. UVs, CLUT selection and corner colors follow their original face interpolation; material discontinuities stay separate. New triangles retain the source lighting, color, texture and translucency bits while clearing the original quad marker. Normals are regenerated for the changed surfaces in the GPU comparison.

Both treatments have closed, consistently wound local surfaces, valid connectivity, nondegenerate triangles and no tested nonadjacent self-intersections. These checks do not establish adjacent folds, contacts between parts or collision with the world. They add 2,940 triangles to the preceding character: **20,446 total**. The diagnostic's expanded vertex attributes total 7,851,264 bytes; the RGBA atlas remains 19,922,944 bytes. These are payload sizes, not measured Deck cost, runtime residency or memory peaks.

The initial treatments restore trouser volume but narrow the gloves. Neighboring-part checks reveal additional crossings at the knees and wrists. A second construction keeps the whole source-linear lower-trouser regions at local Y ≤70 and Y ≥220, with smooth displacement falloff across the intervening cloth body. Volume restoration affects only that free region. Gloves retain all original wrist vertices at Y ≤20 and restore their source signed volume; complete cap faces within that region stay source-shaped. This is an authored geometric constraint, not a new animation or cloth simulation.

## Verified local evidence

| Check | Initial treatment | Joint correction |
| --- | --- | --- |
| Saved geometry and source attributes | Twelve saved surfaces verified against their hashes; all 392 source-triangle instances across the two treatments retain expected descendant UV/CLUT/colors | Same coverage, with protected joint regions and restored glove volume |
| Recorded rigid placement | 5,952 pose/part checks; 59,520 direct source-pin comparisons | 5,952 pose/part checks; 65,472 direct source-pin comparisons |
| Whole protected regions | Not claimed | 2,952,192 source-corner placements verify protected trouser regions and complete wrist cap faces under recorded matrices; maximum error zero |
| GPU captures | 48 images across eight view cases; all input, PNG and decoded-pixel hashes verified | Same 48-case comparison and identity checks |
| Original material controls | Eight indexed-to-nearest views pass identical coverage and at most one channel step across 738,694 covered samples; strong-material coverage matches | Same controls and coverage |
| Unchanged character data | 103,860 vertex rows per candidate retain exact values; projection, lighting, VRAM and atlas remain exact | Same invariant |
| Prior capture registration | All 32 copied original-control and preceding-character PNGs reproduce identically | Same invariant |

Rigid placement covers seven recorded motions and three timing routes, 496 samples per part. It verifies finite placement, valid rigid matrices and source anchors; it is not a repeated full-body collision audit or proof of every game animation. Protected-region expectations come directly from original source-triangle barycentric corners, independently of the corrective's falloff calculation.

The same private Python 3.12/NumPy 1.26.4/Pillow 10.1 environment, Java 25 and hidden Apple M5 Max OpenGL 4.1 harness are used. Source vertex/geometry shaders are unchanged; the private fragment atlas-lookup prototype remains the one recorded in the elbow study. No engine behavior, shipped asset, dependency or installer release changes.

## Contacts remain held

A separate triage checks four captured native pose states and six neighboring pairs: trousers/thighs, trousers/boots and gloves/continuous arms. Child triangles map back to their parent source triangle so subdivision alone does not multiply the reported source-face pairs. Existing source overlaps are retained as the baseline. Counts describe tested surface crossings, not penetration depth, visible defects or distinct gameplay problems; they repeat across the four states.

| Pair group | Added source-face pairs, initial restrained / rounded | Added pairs after joint correction, restrained / rounded |
| --- | --- | --- |
| Both knee joins | 28 / 32 | 0 / 0 |
| Both ankle joins | 1 / 5 | 1 / 4 |
| Both wrist/arm joins | 142 / 190 | 35 / 53 |

The correction clears the added knee crossings in this triage and reduces wrist changes substantially. Wrist and ankle results still need localization, depth/visibility review and repair where necessary. A smooth screenshot or a passing per-part audit does not clear those results. Full movement/contact coverage and native actor integration remain open.

## Art and painted-world comparison

The initial and corrected captures show rounder trouser bulges and glove outlines, with the existing painted costume markings retained. The corrected gloves retain more of the original fist volume. Collar transitions, cloth joins, shin/boot detail and individually authored finger forms remain unfinished. Neither treatment has final art or community approval.

A private offline comparison places the unedited character capture beside Skurfa's pinned `cut14/background.png`, with 160/240/320-pixel capture display heights. The background is contained without cropping or color filters. This is a manually staged screen-space reference with fixed model lighting: it omits native camera placement, foreground masks, contact shadows, reflections and game occlusion. It does not prove scene integration or actual in-game scale. The view makes the next visual requirement clear: scene-matched lighting and contact treatment are needed to connect the actor to the enhanced painted environment.

The comparison's six pose selectors, two backdrop treatments, three preview sizes, three viewport widths and keyboard selection pass hidden offline browser checks. Both captured layouts were inspected. This validates that private comparison page, not physical Deck touch or controller behavior.

## Private records and next work

The sibling directories `Definitive-private-texture-pilot/haschel-garment-surface-round66` and `haschel-garment-contact-round67` retain source/build hashes, saved-data checks, joint-region checks, contact triage, GPU manifests and evidence summaries. The private tooling directories with the corresponding round names retain the authoring and verification commands/logs. Derived geometry, source matrices, artwork comparisons and private helpers remain excluded from Git.

The first initial-treatment preparation stopped on a nested loader lookup; the next GPU gate rejected the first candidate's saved float byte order. Both terminal failures and partial outputs were retained. New inputs were generated in a fresh versioned directory, with finite/bounded big-endian data checked before successful capture. The first background comparison also failed image-load QA because a template substitution altered an embedded image string; a new version uses an exact script placeholder and passes the browser checks. These are private study preparation failures, not installer or shipped-engine failures.

Next localize the remaining wrist/ankle contacts without dropping failing motions or reverting the visible cloth refinement. Then review motion sequences and world lighting before the [native ownership/material integration](NATIVE_MATERIAL_INTEGRATION.md). Preserve whole-actor Faithful fallback. Measure frame times, memory/upload peaks and power on physical Deck hardware, and obtain community comparisons before selecting defaults.
